/*
 * Adapted from Nest Music (Nest Music) for Nest Music under GPL-3.0.
 */

package com.nestmusic.music.ui.component

private const val GlassMotionFreezeWindowNs = 500_000_000L
private const val GlassMotionUnstamped = Long.MIN_VALUE

/**
 * App-wide motion clocks that Liquid Glass consults during draw to hold its
 * last captured frame through fast sheet drags, tab folds, and list scrolls,
 * keeping animations at full 120Hz before immediately thawing on settle.
 */
object GlassMotion {
    val sheet: GlassMotionClock = GlassMotionClock()
    val nav: GlassMotionClock = GlassMotionClock()
    val scroll: GlassMotionClock = GlassMotionClock()

    fun anyFrozen(): Boolean = sheet.frozen() || nav.frozen() || scroll.frozen()
}

class GlassMotionClock(
    private val freezeWindowNs: Long = GlassMotionFreezeWindowNs,
    private val now: () -> Long = System::nanoTime,
) {
    @Volatile
    private var lastMotionAtNs = GlassMotionUnstamped

    fun moved() {
        lastMotionAtNs = now()
    }

    fun settled() {
        lastMotionAtNs = GlassMotionUnstamped
    }

    fun frozen(): Boolean {
        val at = lastMotionAtNs
        return at != GlassMotionUnstamped && now() - at < freezeWindowNs
    }
}
