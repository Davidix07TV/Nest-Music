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
}
