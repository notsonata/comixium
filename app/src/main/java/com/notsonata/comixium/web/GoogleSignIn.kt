package com.notsonata.comixium.web

import java.net.URLDecoder

/**
 * Google refuses to sign in inside a WebView, so Comixium runs Google's part in the user's browser:
 *
 * 1. The site's "Continue with Google" opens [START_PATH]. comix sets its sign-in cookie and
 *    redirects to Google, with a return address (redirect_uri) on one of its domains.
 * 2. The app makes sure that started on the return address's domain (otherwise the cookie and the
 *    return wouldn't match) and sends the Google page to the default browser.
 * 3. Google sends the browser to the return address ([CALLBACK_PATH]). Android hands that link to
 *    Comixium (on Android 12+ the user allows the domain's links once), and the app opens it in its
 *    own WebView, which has the cookie from step 1, so comix signs the app in.
 *
 * Pure Kotlin: the decisions are here, the side effects in [WebTab] and the activity.
 */
object GoogleSignIn {
    const val START_PATH = "/api/v1/auth/google/redirect"
    const val CALLBACK_PATH = "/api/v1/auth/google/callback"

    /** comix's own start of the Google flow. */
    fun isStart(url: String?): Boolean = Site.isSiteUrl(url) && Site.pathOnly(url).trimEnd('/') == START_PATH

    /** Google sending the user back to comix (a link from the browser, after signing in). */
    fun isCallback(url: String?): Boolean = Site.isSiteUrl(url) && Site.pathOnly(url).trimEnd('/') == CALLBACK_PATH

    /** Google's sign-in page, as comix redirects to it. */
    fun isGoogleAuthorize(url: String?): Boolean =
        Site.hostOf(url) == "accounts.google.com" && Site.pathOnly(url).startsWith("/o/oauth2/")

    /** The comix domain Google will send the user back to (from redirect_uri), or null if it isn't one. */
    fun returnHost(authorizeUrl: String): String? {
        val query = authorizeUrl.substringAfter('?', "").substringBefore('#')
        val raw = query.split('&').firstOrNull { it.startsWith("redirect_uri=") }?.substringAfter('=') ?: return null
        val redirect = runCatching { URLDecoder.decode(raw, "UTF-8") }.getOrNull() ?: return null
        if (Site.schemeOf(redirect) != "https" || Site.pathOnly(redirect).trimEnd('/') != CALLBACK_PATH) return null
        return Site.hostOf(redirect)?.takeIf { Site.isSiteHost(it) }
    }

    fun startUrl(host: String): String = "https://$host$START_PATH"

    /** What a tab should do with a main-frame navigation during the Google flow. */
    sealed interface Step {
        /** Not part of the flow (or comix's start page, which loads normally). */
        data object Pass : Step
        /** comix started on a different domain than Google will return to: start again on [host]. */
        data class Restart(val host: String) : Step
        /** Hand Google's page to the browser; the sign-in comes back on [returnHost]. */
        data class OpenInBrowser(val url: String, val returnHost: String) : Step
    }

    /**
     * [startedOn] is the domain the current flow started on (the last [isStart] page this tab loaded),
     * [restarted] whether the tab already restarted it once (so a misconfigured site can't loop).
     */
    fun step(url: String, startedOn: String?, restarted: Boolean): Step {
        if (!isGoogleAuthorize(url)) return Step.Pass
        val back = returnHost(url) ?: return Step.Pass
        return if (back != startedOn && !restarted) Step.Restart(back) else Step.OpenInBrowser(url, back)
    }
}
