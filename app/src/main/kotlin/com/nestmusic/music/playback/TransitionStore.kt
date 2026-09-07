/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.nestmusic.music.playback

import android.content.Context
import timber.log.Timber
import java.io.File

/**
 * Persists per-pair transition overrides: custom DJ-style transitions defined
 * between two specific consecutive tracks (outgoing [Transition.prevId] ->
 * incoming [Transition.nextId]).
 *
 * Overrides are independent from the global "crossfade" setting: once a
 * transition is saved for a pair, the service will mix that pair even if the
 * global crossfade is off, and the user-chosen duration/style always win over
 * the global defaults for that pair.
 *
 * Storage is a plain text file (one transition per line, fields separated by
 * "|||" which cannot occur in YouTube video ids) so no database schema
 * change is required.
 */
object TransitionStore {
    data class Transition(
        val prevId: String,
        val nextId: String,
        val durationMs: Long,
        val style: MixStyle,
    )

    const val MIN_DURATION_MS = 2_000L
    const val MAX_DURATION_MS = 15_000L

    private const val FILE_NAME = "transition_overrides.txt"
    private const val SEPARATOR = "|||"
    private const val TAG = "TransitionStore"

    @Volatile
    private var file: File? = null

    private val lock = Any()
    private val transitions = LinkedHashMap<String, Transition>()
    private var loaded = false

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
            f.readLines().forEach { line ->
                val parts = line.split(SEPARATOR)
                if (parts.size != 4) return@forEach
                val style = runCatching { MixStyle.valueOf(parts[3]) }.getOrNull() ?: return@forEach
                val durationMs = parts[2].toLongOrNull() ?: return@forEach
                transitions[key(parts[0], parts[1])] =
                    Transition(parts[0], parts[1], durationMs, style)
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to load transition overrides")
            transitions.clear()
        }
    }

    private fun persistLocked() {
        val f = file ?: return
        try {
            f.bufferedWriter().use { writer ->
                transitions.values.forEach { t ->
                    writer.write(key(t.prevId, t.nextId))
                    writer.write(SEPARATOR)
                    writer.write(t.durationMs.toString())
                    writer.write(SEPARATOR)
                    writer.write(t.style.name)
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
        val clamped = transition.copy(
            durationMs = transition.durationMs.coerceIn(MIN_DURATION_MS, MAX_DURATION_MS),
        )
        synchronized(lock) {
            transitions[key(clamped.prevId, clamped.nextId)] = clamped
            persistLocked()
        }
    }

    /** Removes the transition for the pair, if any. */
    fun remove(prevId: String, nextId: String): Boolean {
        val removed = synchronized(lock) {
            val removed = transitions.remove(key(prevId, nextId)) != null
            if (removed) persistLocked()
            removed
        }
        if (removed) {
            Timber.tag(TAG).d("Removed transition $prevId -> $nextId")
        }
        return removed
    }

    /** All saved transitions (snapshot). */
    fun all(): List<Transition> = synchronized(lock) { transitions.values.toList() }
}
