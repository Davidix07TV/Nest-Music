/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.nestmusic.music.listentogether

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class ListenTogetherServer(
    val name: String,
    val url: String,
    val location: String,
    val operator: String
)

/**
 * Servers offered in the Listen Together settings.
 *
 * The list is deliberately empty: Nest Music ships no built-in backend, so the user points the app
 * at a server they trust (Settings > Listen Together > Server URL). Running one is documented in
 * `deploy/metroserver/`.
 *
 * Do not re-add a third-party server here without checking its User-Agent policy first. Servers
 * can gate hosting by client: the upstream default only allowlists Metrolist and N-Zik, and the
 * policy example sorts everything else into an advert or rickroll tier.
 */
object ListenTogetherServers {
    private const val ServersJson = "[]"

    /**
     * Hosts that used to ship as a built-in default. A URL pointing at any of them is treated as
     * unset: the client is not welcome there, so keeping it would only strand installs that
     * predate the switch to a user-supplied server.
     */
    private val RetiredHosts = listOf("meowery.eu")

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Trims [url] and blanks it when it points at a retired host, so callers can treat a blank
     * value as "not configured".
     */
    fun normalize(url: String): String {
        val trimmed = url.trim()
        return if (RetiredHosts.any { trimmed.contains(it, ignoreCase = true) }) "" else trimmed
    }

    val servers: List<ListenTogetherServer> by lazy {
        json.decodeFromString(ServersJson)
    }

    /**
     * Empty when no server ships with the app. Callers treat blank as "not configured".
     */
    val defaultServerUrl: String
        get() = servers.firstOrNull()?.url.orEmpty()

    fun findByUrl(url: String): ListenTogetherServer? = servers.firstOrNull { it.url == url }
}
