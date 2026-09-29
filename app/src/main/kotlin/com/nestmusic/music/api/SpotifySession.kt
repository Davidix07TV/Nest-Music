/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.nestmusic.music.api

/**
 * The `sp_dc` session cookie of a Spotify web session, and how to pull it out
 * of the WebView's cookie jar.
 *
 * The login flow only ever keeps this cookie: it is what `SpotifyCanvas`
 * exchanges for a short-lived web-player token. The credentials typed on
 * accounts.spotify.com never reach the app.
 */
object SpotifySession {

    /**
     * One `sp_dc=<value>` pair, whatever else the jar carries.
     *
     * The cookie name is anchored to a separator: without it `sp_dc=` also
     * matches inside any name that ends that way (`__sp_dc`, `xsp_dc`), and the
     * first such hit wins.
     */
    private val SP_DC_COOKIE = Regex("(?:^|;\\s*)sp_dc=([^;]+)")

    /** The `; wv` token that marks a WebView, kept only when it is one. */
    private val EMBEDDED_MARKER = Regex(";\\s*wv\\b")

    /** The `Version/4.0` token, the other half of the same marker. */
    private val LEGACY_ENGINE_TOKEN = Regex("Version/4\\.0\\s*")

    /**
     * The `sp_dc` value from the first jar that actually carries one, or null.
     *
     * Every jar is searched. Picking the first *non-empty* jar instead — and
     * only then looking for `sp_dc` in it — skips every remaining host as soon
     * as the first one is non-empty, and the login jar on accounts.spotify.com
     * is always non-empty: it carries sp_tdid, ttae, __cf_bm and login_ticket
     * from the moment the login page loads. So that variant reports "no
     * session" while a valid `sp_dc` sits unread in the open.spotify.com jar,
     * and the login silently ends with nothing saved.
     *
     * @param cookieJars one `CookieManager.getCookie(url)` result per host.
     */
    internal fun extractSpDc(cookieJars: List<String?>): String? =
        cookieJars.asSequence()
            .mapNotNull { SP_DC_COOKIE.find(it.orEmpty())?.groupValues?.getOrNull(1) }
            .map { it.trim() }
            .firstOrNull { it.isNotEmpty() }

    /**
     * The WebView's own user agent with the embedded-browser markers removed.
     *
     * A WebView identifies itself with two tokens a real Chrome does not send:
     * `; wv` in the platform string and `Version/4.0`. Spotify's anti-bot
     * challenge reads them as "embedded browser" and answers with a page that
     * never renders, so they have to go.
     *
     * What must *not* change is the rest of the string. Hardcoding a desktop or
     * mobile Chrome UA instead leaves the real Chromium's Client Hints
     * (`Sec-CH-UA`, `Sec-CH-UA-Platform-Version`, …) contradicting the
     * declared user agent, which is exactly the inconsistency a bot detector
     * looks for — and it is a plain lie on any device whose System WebView is
     * not the version that was hardcoded. Deriving the agent from the engine
     * that will actually render the page keeps the two in agreement.
     */
    internal fun embeddedBrowserFreeUserAgent(rawUserAgent: String): String =
        rawUserAgent
            .replace(EMBEDDED_MARKER, "")
            .replace(LEGACY_ENGINE_TOKEN, "")
            .replace("\\s{2,}", " ")   // the two removals can leave a double space
            .trim()
}
