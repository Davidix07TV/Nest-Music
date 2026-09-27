package com.nestmusic.music.lossless.qbdlx

import com.nestmusic.music.lossless.FlacConfig
import com.nestmusic.music.lossless.FlacKvStore
import java.util.concurrent.ConcurrentHashMap

private data class PoolEntry(val token: String, val country: String, val appId: String)

/**
 * Manages Qobuz token pool: bundled set of token:country[:appId] pairs plus optional user-pasted token.
 * Implements sticky primary selection, dead-token cooldown (circuit-breaker style), and signing resolution.
 */
class QbdlxCredentialStore(
    private val config: FlacConfig,
    private val kvStore: FlacKvStore,
    private val poolProvider: QbdlxPoolProvider,
) : QbdlxSigningResolver {

    private val pastedTokenKey = "pasted_token"
    private val pinnedTokenKey = "pinned_token"

    internal var primaryAppId: String = ""
    internal var primaryAppSecret: String = ""
    internal var appSecretsRaw: String = ""

    private suspend fun ensureConfigLoaded() {
        if (primaryAppId.isEmpty()) {
            primaryAppId = config.qbdlxAppId()
            primaryAppSecret = config.qbdlxAppSecret()
            appSecretsRaw = config.qbdlxAppSecretsRaw()
        }
    }

    private suspend fun appSecretMap(): Map<String, String> {
        ensureConfigLoaded()
        val map = LinkedHashMap<String, String>()
        map[primaryAppId] = primaryAppSecret
        appSecretsRaw.split(",").forEach { pair ->
            val i = pair.indexOf(':')
            if (i > 0) {
                val appId = pair.take(i).trim()
                val secret = pair.substring(i + 1).trim()
                if (appId.isNotEmpty() && secret.isNotEmpty()) map[appId] = secret
            }
        }
        return map
    }

    override suspend fun signingFor(token: String): QbdlxSigning {
        ensureConfigLoaded()
        val appId = poolAppId(token) ?: primaryAppId
        val secret = appSecretMap()[appId] ?: return QbdlxSigning(primaryAppId, primaryAppSecret)
        return QbdlxSigning(appId, secret)
    }

    private suspend fun poolAppId(token: String): String? = pool().firstOrNull { it.token == token }?.appId

    private var overriddenPoolRaw: String? = null
    internal var poolRaw: String?
        get() = overriddenPoolRaw
        set(value) { overriddenPoolRaw = value }

    internal suspend fun resolvePoolRaw(): String = overriddenPoolRaw ?: poolProvider.rawPool()

    internal var clock: () -> Long = { System.currentTimeMillis() }

    private val deadUntil = ConcurrentHashMap<String, Long>()
    private val lastFailedAt = ConcurrentHashMap<String, Long>()

    @Volatile
    private var activePrimary: String? = null

    private suspend fun pool(): List<PoolEntry> {
        ensureConfigLoaded()
        return resolvePoolRaw().split(",")
            .mapNotNull { entry ->
                val e = entry.trim().ifEmpty { return@mapNotNull null }
                val parts = e.split(":")
                when (parts.size) {
                    1 -> PoolEntry(parts[0], "", primaryAppId)
                    2 -> PoolEntry(parts[0], parts[1], primaryAppId)
                    else -> PoolEntry(
                        token = parts.dropLast(2).joinToString(":"),
                        country = parts[parts.size - 2],
                        appId = parts.last(),
                    )
                }
            }
    }

    private fun isDead(token: String): Boolean {
        val until = deadUntil[token] ?: return false
        if (clock() < until) return true
        deadUntil.remove(token)
        return false
    }

    private suspend fun pastedToken(): String? = kvStore.get(pastedTokenKey)?.takeIf { it.isNotBlank() }
    suspend fun pinnedToken(): String? = kvStore.get(pinnedTokenKey)?.takeIf { it.isNotBlank() }

    suspend fun setPinnedToken(token: String?) {
        val t = token?.trim()
        if (t.isNullOrEmpty()) kvStore.put(pinnedTokenKey, null) else kvStore.put(pinnedTokenKey, t)
    }

    suspend fun activeToken(): String? {
        pastedToken()?.let { if (!isDead(it)) return it }
        pinnedToken()?.let { p ->
            if (!isDead(p) && pool().any { it.token == p }) return p
        }
        activePrimary?.let { if (!isDead(it)) return it }
        val next = pool().map { it.token }
            .filter { !isDead(it) }
            .sortedWith(compareBy({ lastFailedAt[it] ?: 0L }, { it.hashCode() }, { it }))
            .firstOrNull() ?: return null
        activePrimary = next
        return next
    }

    suspend fun tokensForRegion(country: String?): List<String> {
        val live = pool().filter { !isDead(it.token) }
        val sorted = if (country.isNullOrBlank()) live
        else live.sortedByDescending { it.country.equals(country, ignoreCase = true) }
        return sorted.map { it.token }.take(MAX_REGION_TRIES)
    }

    fun markDead(token: String) {
        val now = clock()
        deadUntil[token] = now + DEAD_COOLDOWN_MS
        lastFailedAt[token] = now
        if (token == activePrimary) activePrimary = null
    }

    fun recordAlive(token: String) {
        deadUntil.remove(token)
        lastFailedAt.remove(token)
    }

    suspend fun setPastedToken(token: String?) {
        val t = token?.trim()
        if (!t.isNullOrEmpty()) recordAlive(t)
        if (t.isNullOrEmpty()) kvStore.put(pastedTokenKey, null) else kvStore.put(pastedTokenKey, t)
    }

    suspend fun allDead(): Boolean {
        val pasted = pastedToken()
        val poolTokens = pool().map { it.token }
        if (poolTokens.isEmpty() && pasted == null) return true
        pasted?.let { if (!isDead(it)) return false }
        return poolTokens.all { isDead(it) }
    }

    companion object {
        const val MAX_REGION_TRIES = 3
        const val DEAD_COOLDOWN_MS = 60_000L
    }
}
