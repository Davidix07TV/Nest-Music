package com.nestmusic.music.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PracticeLoopStateTest {
    @Test
    fun `loop is inactive until both points are set`() {
        val state = PracticeLoopState()

        assertFalse(state.isActive)
        assertNull(state.lengthMs)

        assertTrue(state.setStart(1_000))
        assertFalse(state.isActive)
        assertNull(state.lengthMs)

        assertTrue(state.setEnd(3_000))
        assertTrue(state.isActive)
        assertEquals(2_000L, state.lengthMs)
    }

    @Test
    fun `end point needs a start point`() {
        val state = PracticeLoopState()

        assertFalse(state.setEnd(3_000))
        assertFalse(state.isActive)
    }

    @Test
    fun `section shorter than the minimum is rejected`() {
        val state = PracticeLoopState()
        state.setStart(1_000)

        assertFalse(state.setEnd(1_400))
        assertFalse(state.isActive)
    }

    @Test
    fun `moving the start past the end drops the end point`() {
        val state = PracticeLoopState()
        state.setStart(1_000)
        state.setEnd(2_000)

        assertTrue(state.setStart(1_800))
        assertNull(state.endMs)
        assertFalse(state.isActive)
    }

    @Test
    fun `target is only returned once playback passed the end point`() {
        val state = activeLoop()

        assertNull(state.loopTarget(2_999, nowMs = 0))
        assertEquals(1_000L, state.loopTarget(3_000, nowMs = 0))
    }

    @Test
    fun `late tick keeps the phase inside the section`() {
        val state = activeLoop()

        assertEquals(1_120L, state.loopTarget(3_120, nowMs = 0))
        // More than a full section late: wrap around instead of running backwards.
        assertEquals(1_120L, state.loopTarget(5_120, nowMs = 0))
    }

    @Test
    fun `manual seek past the tolerance is left alone`() {
        val state = activeLoop()

        assertNull(
            state.loopTarget(3_000L + PracticeLoopState.OVERSHOOT_TOLERANCE_MS + 1L, nowMs = 0),
        )
    }

    @Test
    fun `position reads are ignored right after the loop seeked`() {
        val state = activeLoop()
        state.onLoopSeekIssued(nowMs = 5_000)

        assertNull(state.loopTarget(3_050, nowMs = 5_000 + PracticeLoopState.SEEK_SETTLE_MS - 1))
        assertEquals(
            1_050L,
            state.loopTarget(3_050, nowMs = 5_000 + PracticeLoopState.SEEK_SETTLE_MS),
        )
    }

    @Test
    fun `clear drops points and progress`() {
        val state = activeLoop()
        assertEquals(0.5f, state.progress(2_000)!!, 0.0001f)

        state.clear()

        assertFalse(state.isActive)
        assertNull(state.lengthMs)
        assertNull(state.progress(2_000))
    }

    @Test
    fun `progress is clamped to the section`() {
        val state = activeLoop()

        assertEquals(0f, state.progress(500)!!, 0.0001f)
        assertEquals(1f, state.progress(9_000)!!, 0.0001f)
    }

    private fun activeLoop(): PracticeLoopState =
        PracticeLoopState().apply {
            setStart(1_000)
            setEnd(3_000)
        }
}
