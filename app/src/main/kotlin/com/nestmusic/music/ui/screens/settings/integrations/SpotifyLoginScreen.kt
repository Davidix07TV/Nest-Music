/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.nestmusic.music.ui.screens.settings.integrations

import android.annotation.SuppressLint
import android.widget.Toast
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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
import com.nestmusic.music.LocalPlayerAwareWindowInsets
import com.nestmusic.music.R
import com.nestmusic.music.api.SpotifySession
import com.nestmusic.music.constants.SpotifySpDcKey
import com.nestmusic.music.ui.component.IconButton
import com.nestmusic.music.ui.utils.backToMain
import com.nestmusic.music.utils.rememberPreference
import timber.log.Timber

private const val TAG = "SpotifyLogin"

private const val LOGIN_URL = "https://accounts.spotify.com/login"

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
        // A sign-in that ends without a cookie used to return to the settings
        // silently, with the same "Log in" row still showing and nothing to say
        // why the credentials did not take. Say so.
        if (!captureSessionCookie()) {
            Toast.makeText(context, R.string.spotify_login_not_completed, Toast.LENGTH_LONG).show()
        }
        isCompletingLogin = true
        onClose()
    }

    // Built inside the AndroidView factory rather than in a remember{}: loadUrl()
    // on a WebView that is not attached to a window yet can fetch the document
    // and never paint a first frame, which shows up as a white page with no error
    // anywhere. LoginScreen (the YouTube sign-in) already creates its WebView this
    // way and renders, so this matches a pattern that already works in this app.
    var webView: WebView? = null

    AndroidView(
        modifier = Modifier
            .windowInsetsPadding(LocalPlayerAwareWindowInsets.current)
            .fillMaxSize(),
        factory = { webViewContext ->
            WebView(webViewContext).apply {
                webView = this
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String?) {
                        if (isCompletingLogin) return
                        val isStatusPage = url != null && STATUS_URL.matches(url)
                        val isPlayerPage = url?.startsWith("https://open.spotify.com/") == true
                        Timber.tag(TAG).d("page finished: %s", url)
                        if (isStatusPage || isPlayerPage) {
                            if (captureSessionCookie()) {
                                isCompletingLogin = true
                                navController.navigateUp()
                            }
                        }
                    }

                    // A white page currently carries no signal at all: the login SPA
                    // can fail in the network, over HTTP, or inside JS and all three
                    // look identical on screen. Log the main-frame failures and the
                    // console errors so the next report can name the real cause
                    // instead of guessing at it.
                    override fun onReceivedError(
                        view: WebView,
                        errorRequest: WebResourceRequest,
                        error: WebResourceError,
                    ) {
                        if (!errorRequest.isForMainFrame) return
                        // WebResourceError is not a Throwable, so this cannot go
                        // through Timber.e(Throwable, …) — description goes in the
                        // message instead.
                        Timber.tag(TAG).e(
                            "main frame failed to load: %s (%s, code %d)",
                            errorRequest.url,
                            error.description,
                            error.errorCode,
                        )
                    }

                    override fun onReceivedHttpError(
                        view: WebView,
                        errorRequest: WebResourceRequest,
                        errorResponse: WebResourceResponse,
                    ) {
                        if (!errorRequest.isForMainFrame) return
                        Timber.tag(TAG).e(
                            "main frame returned HTTP %d: %s",
                            errorResponse.statusCode,
                            errorRequest.url,
                        )
                    }
                }
                // Console messages arrive on WebChromeClient, not WebViewClient.
                webChromeClient = object : WebChromeClient() {
                    override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
                        if (consoleMessage.messageLevel() == ConsoleMessage.MessageLevel.ERROR) {
                            Timber.tag(TAG).w(
                                "console error: %s (%s:%d)",
                                consoleMessage.message(),
                                consoleMessage.sourceId(),
                                consoleMessage.lineNumber(),
                            )
                        }
                        return true
                    }
                }
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    // Take the engine's own user agent and drop only the two
                    // tokens that say "embedded browser". Hardcoding a Chrome
                    // version instead leaves the real Client Hints contradicting
                    // the declared agent — the engine that actually renders the
                    // page keeps sending its own version — and that mismatch is
                    // what a bot detector compares.
                    userAgentString = SpotifySession.embeddedBrowserFreeUserAgent(
                        WebSettings.getDefaultUserAgent(webViewContext),
                    )
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
                loadUrl(LOGIN_URL)
            }
        },
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
        if (webView?.canGoBack() == true) {
            webView?.goBack()
        } else {
            completeLogin(navController::navigateUp)
        }
    }
}
