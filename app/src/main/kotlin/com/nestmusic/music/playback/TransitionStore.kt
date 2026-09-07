/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.nestmusic.music.playback

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import java.io.File

/**
 * Persists per-pair transition overrides. The store intentionally lives outside
 * Room: transitions are playback preferences and do not change the app's
 * database schema.
 */
object TransitionStore {
    data class Transition(
        val prevId: String,
        val nextId: String,
        val durationMs: Long,
        val style: MixStyle = MixStyle.AUTO,
        val volumeCurve: MixVolumeCurve = MixVolumeCurve.LINEAR,
        val eqMode: MixEqMode = MixEqMode.NONE,
        val effect: MixEffect = MixEffect.NONE,
        /** Signed shift of the overlap centre, relative to the default centre. */
        val mixPointOffsetMs: Long = 0L,
    )

    const val MIN_DURATION_MS = 500L
    const val MAX_DURATION_MS = 12_000L

    private const val FILE_NAME = "transition_overrides.txt"
    private const val SEPARATOR = "|||"
    private const val TAG = "TransitionStore"

    @Volatile
    private var file: File? = null

    private val lock = Any()
    private val transitions = LinkedHashMap<String, Transition>()
    private var loaded = false

    private val _revision = MutableStateFlow(0L)
    /** Changes whenever an editor saves/removes a pair, so queue pills refresh. */
    val revision = _revision.asStateFlow()

    private fun key(prevId: String, nextId: String) = prevId + SEPARATOR + nextId

    /** Loads the store from disk. Safe to call multiple times. */
    fun init(context: Context) {
        synchronized(lock) {
            if (loaded) return
            file = context.applicationContext.filesDir.resolve(FILE_NAME)
            loadLocked()
            loaded = true
        }
    }

    private fun loadLocked() {
        transitions.clear()
        val f = file ?: return
        if (!f.exists()) return
        try {
            f.useLines { lines ->
                lines.forEach { line ->
                    val parts = line.split(SEPARATOR)
                    // Version 1: prev, next, duration, style.
                    if (parts.size == 4) {
                        val style = parseStyle(parts[3]) ?: return@forEach
                        val durationMs = parts[2].toLongOrNull() ?: return@forEach
                        transitions[key(parts[0], parts[1])] =
                            Transition(
                                prevId = parts[0],
                                nextId = parts[1],
                                durationMs = durationMs.coerceIn(MIN_DURATION_MS, MAX_DURATION_MS),
                                style = style,
                            )
                        return@forEach
                    }

                    // Version 2: prev, next, duration, style, volume, eq,
                    // effect, mix-point offset.
                    if (parts.size != 8) return@forEach
                    val style = parseStyle(parts[3]) ?: return@forEach
                    val volume = runCatching { MixVolumeCurve.valueOf(parts[4]) }.getOrNull()
                        ?: MixVolumeCurve.LINEAR
                    val eq = runCatching { MixEqMode.valueOf(parts[5]) }.getOrNull()
                        ?: MixEqMode.NONE
                    val effect = runCatching { MixEffect.valueOf(parts[6]) }.getOrNull()
                        ?: MixEffect.NONE
                    val durationMs = parts[2].toLongOrNull() ?: return@forEach
                    val offsetMs = parts[7].toLongOrNull() ?: 0L
                    transitions[key(parts[0], parts[1])] = normalized(
                        Transition(
                            prevId = parts[0],
                            nextId = parts[1],
                            durationMs = durationMs,
                            style = style,
                            volumeCurve = volume,
                            eqMode = eq,
                            effect = effect,
                            mixPointOffsetMs = offsetMs,
                        ),
                    )
                }
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to load transition overrides")
            transitions.clear()
        }
    }

    private fun parseStyle(value: String): MixStyle? =
        when (value) {
            // The first beta called equal-power "SMOOTH". Migrate it while
            // reading instead of making users lose their saved transitions.
            "SMOOTH" -> MixStyle.BLEND
            else -> runCatching { MixStyle.valueOf(value) }.getOrNull()
        }

    private fun normalized(transition: Transition): Transition {
        val durationMs = transition.durationMs.coerceIn(MIN_DURATION_MS, MAX_DURATION_MS)
        return transition.copy(
            durationMs = durationMs,
            mixPointOffsetMs = transition.mixPointOffsetMs.coerceIn(
                -durationMs / 2,
                durationMs / 2,
            ),
        )
    }

    private fun persistLocked() {
        val f = file ?: return
        try {
            f.bufferedWriter().use { writer ->
                transitions.values.forEach { transition ->
                    val t = normalized(transition)
                    writer.write(key(t.prevId, t.nextId))
                    writer.write(SEPARATOR)
                    writer.write(t.durationMs.toString())
                    writer.write(SEPARATOR)
                    writer.write(t.style.displayStyle().name)
                    writer.write(SEPARATOR)
                    writer.write(t.volumeCurve.name)
                    writer.write(SEPARATOR)
                    writer.write(t.eqMode.name)
                    writer.write(SEPARATOR)
                    writer.write(t.effect.name)
                    writer.write(SEPARATOR)
                    writer.write(t.mixPointOffsetMs.toString())
                    writer.newLine()
                }
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to persist transition overrides")
        }
    }

    /** Returns the saved transition for the pair, or null. */
    fun get(prevId: String, nextId: String): Transition? =
        synchronized(lock) { transitions[key(prevId, nextId)] }

    /** Creates or updates the transition for the pair. */
    fun save(transition: Transition) {
        val clamped = normalized(transition)
        synchronized(lock) {
            transitions[key(clamped.prevId, clamped.nextId)] = clamped
            persistLocked()
            _revision.value++
        }
    }

    /** Removes the transition for the pair, if any. */
    fun remove(prevId: String, nextId: String): Boolean {
        val removed = synchronized(lock) {
            val didRemove = transitions.remove(key(prevId, nextId)) != null
            if (didRemove) {
                persistLocked()
                _revision.value++
            }
            didRemove
        }
        if (removed) {
            Timber.tag(TAG).d("Removed transition $prevId -> $nextId")
        }
        return removed
    }

    /** All saved transitions (snapshot). */
    fun all(): List<Transition> = synchronized(lock) { transitions.values.toList() }
}
