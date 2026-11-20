package com.nestmusic.music.ui.player

import androidx.media3.common.PlaybackException
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackErrorTest {
    @Test
    fun `403 and age messages classify as age restricted`() {
        assertEquals(PlaybackErrorKind.AGE_RESTRICTED, classifyPlaybackError("Response code: 403", 0))
        assertEquals(PlaybackErrorKind.AGE_RESTRICTED, classifyPlaybackError("Sign in to confirm your age", 0))
        assertEquals(
            PlaybackErrorKind.AGE_RESTRICTED,
            classifyPlaybackError("anything", PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS),
        )
    }

    @Test
    fun `cancelled job classifies as transient, other errors stay other`() {
        assertEquals(PlaybackErrorKind.JOB_CANCELLED, classifyPlaybackError("Playback job was cancelled", 0))
        assertEquals(PlaybackErrorKind.OTHER, classifyPlaybackError("network timeout", 0))
    }
}
