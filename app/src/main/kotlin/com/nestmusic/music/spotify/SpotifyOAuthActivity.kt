/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.nestmusic.music.spotify

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout
import timber.log.Timber

/**
 * Catches the `nestmusic://spotify/callback` redirect and hands the code
 * back to whoever started the sign-in. Mirrors DiscordOAuthActivity: the
 * browser lives in another process, so the only thing that crosses back
 * into the app is this intent, and the pending sign-in waits on a deferred
 * until it arrives.
 */
class SpotifyOAuthActivity : Activity() {

    companion object {
        private const val TAG = "SpotifyAuth"

        @Volatile
        private var deferred: CompletableDeferred<SpotifyAuth.Callback>? = null

        fun newDeferred(): CompletableDeferred<SpotifyAuth.Callback> =
            CompletableDeferred<SpotifyAuth.Callback>().also { deferred = it }

        suspend fun awaitCallback(timeoutMs: Long = 180_000L): SpotifyAuth.Callback {
            val d = deferred ?: throw CancellationException("No pending authorization")
            return withTimeout(timeoutMs) { d.await() }
        }

        fun cancelPending() {
            deferred?.let { d ->
                if (!d.isCompleted) {
                    d.completeExceptionally(
                        CancellationException("Authorization cancelled by user")
                    )
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uri = intent?.data?.toString()
        if (uri == null) {
            Timber.tag(TAG).w("OAuthActivity: started with no redirect URI")
            cancelPending()
            finish()
            return
        }
        Timber.tag(TAG).d("OAuthActivity: redirect received")
        deferred?.complete(SpotifyAuth.parseCallback(uri))
        finish()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        val uri = intent?.data?.toString() ?: return
        deferred?.complete(SpotifyAuth.parseCallback(uri))
        finish()
    }
}
