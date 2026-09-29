/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.nestmusic.music.ui.screens.settings.integrations

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavController
import com.nestmusic.music.R
import com.nestmusic.music.api.SpotifySession
import com.nestmusic.music.constants.SpotifySpDcKey
import com.nestmusic.music.ui.component.IconButton
import com.nestmusic.music.ui.utils.backToMain
import com.nestmusic.music.utils.rememberPreference
import timber.log.Timber

private const val TAG = "SpotifyLogin"

/** Where Spotify lands the browser after a successful sign-in. */
private val STATUS_URL =
    Regex("^https://accounts\\.spotify\\.com/(?:[^/]+/)?status(?:\\?.*)?$")

/**
 * Hosts whose cookie jar can carry the session, in the order they should be
 * searched. The login jar is non-empty from the first page load, so the order
 * only matters as a tie-break — every jar is inspected either way.
 */
private val SESSION_HOSTS = listOf(
    "https://accounts.spotify.com",
    "https://open.spotify.com",
)

/**
 * Plain mobile Chrome user agent. The WebView default carries `; wv`, which
 * Spotify's anti-bot challenge reads as an embedded browser and answers with a
 * page that never renders; every known-good sp_dc harvester strips it.
 */
private const val MOBILE_CHROME_UA =
    "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/132.0.0.0 Mobile Safari/537.36"

/**
 * In-app sign-in for Spotify: the user types their email and password on the
 * official page, and only the resulting `sp_dc` session cookie is kept — the
 * credentials themselves never reach the app.
 */
@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpotifyLoginScreen(navController: NavController) {
    val context = LocalContext.current
    val (_, onSpDcChange) = rememberPreference(SpotifySpDcKey, defaultValue = "")
    var isCompletingLogin by remember { mutableStateOf(false) }

    /** Extracts sp_dc from the shared cookie jars; true when one was found. */
    fun captureSessionCookie(): Boolean {
        val cookieManager = CookieManager.getInstance()
        val spDc = SpotifySession.extractSpDc(SESSION_HOSTS.map { cookieManager.getCookie(it) })
        if (spDc == null) {
            Timber.tag(TAG).d("no sp_dc in any session jar yet")
            return false
        }
        Timber.tag(TAG).d("captured sp_dc session, length=%d", spDc.length)
        onSpDcChange(spDc)
        return true
    }

    fun completeLogin(onClose: () -> Unit) {
        if (isCompletingLogin) return
        captureSessionCookie()
        isCompletingLogin = true
        onClose()
    }

    val webView = remember {
        WebView(context).apply {
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String?) {
                    if (isCompletingLogin) return
                    val isStatusPage = url != null && STATUS_URL.matches(url)
                    val isPlayerPage = url?.startsWith("https://open.spotify.com/") == true
                    if (isStatusPage || isPlayerPage) {
                        if (captureSessionCookie()) {
                            isCompletingLogin = true
                            navController.navigateUp()
                        }
                    }
                }
            }
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                userAgentString = MOBILE_CHROME_UA
                useWideViewPort = true
                loadWithOverviewMode = true
                setSupportZoom(true)
                builtInZoomControls = true
                displayZoomControls = false
            }
            // reCAPTCHA during the login flow runs on cross-site resources; with
            // third-party cookies blocked (the WebView default) it cannot build a
            // session and the page stays stuck on a blank challenge.
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            loadUrl("https://accounts.spotify.com/login")
        }
    }
    DisposableEffect(Unit) {
        onDispose { webView.destroy() }
    }

    AndroidView(
        factory = { webView },
        modifier = Modifier.fillMaxSize(),
    )

    TopAppBar(
        title = { Text(stringResource(R.string.spotify_log_in)) },
        navigationIcon = {
            IconButton(
                onClick = { completeLogin(navController::navigateUp) },
                onLongClick = { completeLogin(navController::backToMain) },
            ) {
                Icon(
                    painterResource(R.drawable.arrow_back),
                    contentDescription = null,
                )
            }
        },
    )

    BackHandler {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            completeLogin(navController::navigateUp)
        }
    }
}
