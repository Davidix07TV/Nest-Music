package com.nestmusic.music.lossless.ratelimit

import com.nestmusic.music.lossless.FlacLogger
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Token-bucket rate limiter with circuit breaker per source.
 * Used to avoid hammering Qobuz APIs and to self-throttle on 429s.
 */
class AggregatorRateLimiter(
    private val clock: Clock = SystemClock,
) {
    private data class Bucket(
        var tokens: Double,
        var lastRefillMs: Long,
        var blockedUntilMs: Long = 0L,
        var consecutiveFailures: Int = 0,
        var wasCircuitBroken: Boolean = false,
        var totalAcquires: Long = 0,
        var totalRateLimited: Long = 0,
        val rateLimitTimestamps: MutableList<Long> = mutableListOf(),
    )

    data class Config(
        val tokensPerSecond: Double = 0.125,
        val burstCapacity: Double = 3.0,
        val backoff429Ms: Long = 5 * 60_000L,
        val circuitBreakAfter: Int = 3,
        val circuitBreakDurationMs: Long = 30 * 60_000L,
        val rateLimitTripsBreaker: Boolean = true,
    )

    data class RateLimitState(
        val tokensAvailable: Double,
        val msUntilNextToken: Long,
        val isCircuitBroken: Boolean,
        val msUntilUnblock: Long,
        val recentFailures: Int,
    )

    interface Clock { fun nowMs(): Long }
    object SystemClock : Clock { override fun nowMs() = System.currentTimeMillis() }

    private val logger = FlacLogger("RateLimiter")
    private val mutex = Mutex()
    private val buckets = mutableMapOf<String, Bucket>()
    private val configs = mutableMapOf<String, Config>()
    private val defaultConfig = Config()

    private val _circuitResetEvents = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val circuitResetEvents: SharedFlow<String> = _circuitResetEvents

    fun configure(sourceId: String, config: Config) { configs[sourceId] = config }

    suspend fun acquire(sourceId: String): Boolean {
        var waitMs = 0L
        var earlyResult: Boolean? = null
        var resetEventId: String? = null
        mutex.withLock {
            val bucket = bucketFor(sourceId)
            val cfg = configFor(sourceId)
            val now = clock.nowMs()
            if (now < bucket.blockedUntilMs) return false
            resetEventId = consumeCircuitResetIfDue(sourceId, bucket, now)
            refill(bucket, cfg, now)
            if (bucket.tokens >= 1.0) {
                bucket.tokens -= 1.0
                bucket.totalAcquires++
                earlyResult = true
            } else {
                waitMs = msToNextToken(bucket, cfg)
            }
        }
        resetEventId?.let { _circuitResetEvents.tryEmit(it) }
        earlyResult?.let { return it }
        if (waitMs > 0) delay(waitMs)
        return acquire(sourceId)
    }

    suspend fun reportSuccess(sourceId: String) {
        mutex.withLock { bucketFor(sourceId).consecutiveFailures = 0 }
    }

    suspend fun reset(sourceId: String) {
        mutex.withLock {
            val bucket = bucketFor(sourceId)
            val cfg = configFor(sourceId)
            bucket.blockedUntilMs = 0L
            bucket.consecutiveFailures = 0
            bucket.tokens = cfg.burstCapacity
            bucket.lastRefillMs = clock.nowMs()
            bucket.wasCircuitBroken = false
        }
        _circuitResetEvents.tryEmit(sourceId)
    }

    suspend fun reportRateLimited(sourceId: String) {
        mutex.withLock {
            val bucket = bucketFor(sourceId)
            val cfg = configFor(sourceId)
            val now = clock.nowMs()
            bucket.blockedUntilMs = now + cfg.backoff429Ms
            bucket.totalRateLimited++
            bucket.rateLimitTimestamps.add(now)
            bucket.rateLimitTimestamps.removeAll { it < now - 60_000L }
            if (bucket.rateLimitTimestamps.size >= 5) {
                val newRate = (cfg.tokensPerSecond / 2.0).coerceAtLeast(1.0 / 60.0)
                if (newRate < cfg.tokensPerSecond) {
                    configs[sourceId] = cfg.copy(tokensPerSecond = newRate)
                    logger.i("$sourceId 429 x5 in 60s; halving rate to $newRate")
                    bucket.rateLimitTimestamps.clear()
                }
            }
            if (cfg.rateLimitTripsBreaker) {
                bucket.consecutiveFailures++
                maybeTripCircuitBreaker(bucket, cfg)
            }
        }
    }

    suspend fun reportFailure(sourceId: String) {
        mutex.withLock {
            val bucket = bucketFor(sourceId)
            val cfg = configFor(sourceId)
            bucket.consecutiveFailures++
            maybeTripCircuitBreaker(bucket, cfg)
        }
    }

    suspend fun stateOf(sourceId: String): RateLimitState {
        var resetEventId: String? = null
        val state = mutex.withLock {
            val bucket = bucketFor(sourceId)
            val cfg = configFor(sourceId)
            val now = clock.nowMs()
            refill(bucket, cfg, now)
            resetEventId = consumeCircuitResetIfDue(sourceId, bucket, now)
            RateLimitState(
                tokensAvailable = bucket.tokens,
                msUntilNextToken = if (bucket.tokens >= 1.0) 0L else msToNextToken(bucket, cfg),
                isCircuitBroken = now < bucket.blockedUntilMs,
                msUntilUnblock = (bucket.blockedUntilMs - now).coerceAtLeast(0L),
                recentFailures = bucket.consecutiveFailures,
            )
        }
        resetEventId?.let { _circuitResetEvents.tryEmit(it) }
        return state
    }

    private fun bucketFor(sourceId: String): Bucket =
        buckets.getOrPut(sourceId) { Bucket(tokens = configFor(sourceId).burstCapacity, lastRefillMs = clock.nowMs()) }

    private fun configFor(sourceId: String): Config = configs[sourceId] ?: defaultConfig

    private fun refill(bucket: Bucket, cfg: Config, now: Long) {
        val elapsedSec = (now - bucket.lastRefillMs) / 1000.0
        if (elapsedSec <= 0) return
        bucket.tokens = (bucket.tokens + elapsedSec * cfg.tokensPerSecond).coerceAtMost(cfg.burstCapacity)
        bucket.lastRefillMs = now
    }

    private fun msToNextToken(bucket: Bucket, cfg: Config): Long {
        val needed = (1.0 - bucket.tokens).coerceAtLeast(0.0)
        return (needed / cfg.tokensPerSecond * 1000.0).toLong().coerceAtLeast(1L)
    }

    private fun maybeTripCircuitBreaker(bucket: Bucket, cfg: Config) {
        if (bucket.consecutiveFailures >= cfg.circuitBreakAfter) {
            bucket.blockedUntilMs = clock.nowMs() + cfg.circuitBreakDurationMs
            bucket.wasCircuitBroken = true
            bucket.consecutiveFailures = 0
        }
    }

    private fun consumeCircuitResetIfDue(sourceId: String, bucket: Bucket, now: Long): String? {
        if (bucket.wasCircuitBroken && now >= bucket.blockedUntilMs) {
            bucket.wasCircuitBroken = false
            return sourceId
        }
        return null
    }
}
