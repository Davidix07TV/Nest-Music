/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.nestmusic.music.spotify

import java.util.Base64
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import timber.log.Timber

/** What Spotify hands back once the authorization code has been redeemed. */
data class SpotifyToken(
    val accessToken: String,
    val refreshToken: String?,
    val expiresInSeconds: Long,
)

sealed class SpotifyAuthException(message: String, cause: Throwable? = null) :
    Exception(message, cause) {
    class Denied(val reason: String) : SpotifyAuthException("Authorization denied: $reason")
    class StateMismatch : SpotifyAuthException("OAuth state mismatch")
    class NoBrowser : SpotifyAuthException("No browser available")
    class ExchangeFailed(detail: String) : SpotifyAuthException("Token exchange failed: $detail")
}

/**
 * Spotify sign-in that does not need a cookie and does not need devtools.
 *
 * The previous flow typed the credentials into a WebView and scraped
 * `sp_dc` from its cookie jar. That is not reachable on a phone twice over:
 * the login page is behind reCAPTCHA Enterprise, whose
 * `requestStorageAccess()` is refused by Android WebView (the grant needs a
 * user prompt a WebView never shows), and a cookie copied out of a phone
 * browser is not something a person can do. A Custom Tab runs the real
 * browser, so the sign-in completes there — but its cookie store belongs to
 * the browser and no API lets the app read it. The redirect back to us is
 * the only thing that crosses the boundary, so this uses it.
 *
 * Authorization Code + PKCE, public client: no secret is shipped, the
 * verifier never leaves the device.
 */
object SpotifyAuth {
    private const val TAG = "SpotifyAuth"

    const val AUTHORIZE_URL = "https://accounts.spotify.com/authorize"
    const val TOKEN_URL = "https://accounts.spotify.com/api/token"
    const val REDIRECT_URI = "nestmusic://spotify/callback"

    /**
     * Read-only, and only what a sign-in is for. `user-read-private` and
     * `user-read-email` identify the account; the playlist scopes are there
     * for a supported playlist import to replace the scraped one.
     */
    const val SCOPES = "user-read-private user-read-email playlist-read-private"

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val client by lazy {
        HttpClient(CIO) {
            install(HttpTimeout) {
                requestTimeoutMillis = 20_000
                connectTimeoutMillis = 10_000
                socketTimeoutMillis = 20_000
            }
            expectSuccess = false
        }
    }

    /** The code verifier, and the S256 challenge derived from it. */
    internal data class PkcePair(val verifier: String, val challenge: String)

    /**
     * java.util.Base64, not android.util: the JDK one is URL-safe and
     * unpadding by construction and, unlike the Android one, runs under a
     * plain JUnit test without Robolectric standing in for it.
     */
    private val encoder: Base64.Encoder = Base64.getUrlEncoder().withoutPadding()

    internal fun generatePkcePair(random: SecureRandom = SecureRandom()): PkcePair {
        val bytes = ByteArray(64)
        random.nextBytes(bytes)
        val verifier = encoder.encodeToString(bytes)
        val challenge = encoder.encodeToString(
            MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(StandardCharsets.UTF_8)),
        )
        return PkcePair(verifier, challenge)
    }

    internal fun generateState(random: SecureRandom = SecureRandom()): String {
        val bytes = ByteArray(24)
        random.nextBytes(bytes)
        return encoder.encodeToString(bytes)
    }

    /**
     * The URL the browser is sent to. Pure, so the escaping is testable
     * without a browser: a redirect URI containing `://` has to survive
     * round-tripping through Spotify's query parser intact.
     */
    internal fun authorizeUrl(clientId: String, state: String, challenge: String): String =
        buildString {
            append(AUTHORIZE_URL)
            append("?response_type=code")
            append("&client_id=").append(enc(clientId))
            append("&redirect_uri=").append(enc(REDIRECT_URI))
            append("&scope=").append(enc(SCOPES))
            append("&state=").append(enc(state))
            append("&code_challenge_method=S256")
            append("&code_challenge=").append(enc(challenge))
        }

    /** The outcome of the redirect back into the app. */
    internal data class Callback(val code: String?, val state: String?, val error: String?)

    /** Parses `nestmusic://spotify/callback?…` without touching Android. */
    internal fun parseCallback(uri: String): Callback = Callback(
        code = param(uri, "code"),
        state = param(uri, "state"),
        error = param(uri, "error"),
    )

    private fun param(uri: String, name: String): String? =
        uri.substringAfter('?', "")
            .split('&')
            .firstOrNull { it.startsWith("$name=") }
            ?.substringAfter('=')
            ?.takeIf { it.isNotEmpty() }
            ?.let { runCatching { URLDecoder.decode(it, StandardCharsets.UTF_8.name()) }.getOrDefault(it) }

    /** The token payload, or null when the body carries no access token. */
    internal fun parseTokenResponse(body: String): SpotifyToken? {
        val obj = runCatching { json.parseToJsonElement(body) as? JsonObject }.getOrNull() ?: return null
        val access = (obj["access_token"] as? JsonPrimitive)?.contentOrNull
            ?: return null
        return SpotifyToken(
            accessToken = access,
            refreshToken = (obj["refresh_token"] as? JsonPrimitive)?.contentOrNull,
            expiresInSeconds = (obj["expires_in"] as? JsonPrimitive)?.contentOrNull?.toLongOrNull() ?: 0L,
        )
    }

    suspend fun exchangeCode(
        clientId: String,
        code: String,
        verifier: String,
    ): SpotifyToken {
        val response = client.submitForm(
            url = TOKEN_URL,
            formParameters = Parameters.build {
                append("grant_type", "authorization_code")
                append("code", code)
                append("redirect_uri", REDIRECT_URI)
                append("client_id", clientId)
                append("code_verifier", verifier)
            },
        ) { header("Accept", "application/json") }

        val body = response.bodyAsText()
        if (response.status != HttpStatusCode.OK) {
            Timber.tag(TAG).w("token exchange: HTTP %s", response.status)
            throw SpotifyAuthException.ExchangeFailed(
                runCatching { (json.parseToJsonElement(body) as? JsonObject)?.get("error")?.toString() }
                    .getOrNull() ?: response.status.toString()
            )
        }
        return parseTokenResponse(body)
            ?: throw SpotifyAuthException.ExchangeFailed("response carried no access_token")
    }

    private fun enc(value: String): String = java.net.URLEncoder.encode(value, StandardCharsets.UTF_8.name())
}
