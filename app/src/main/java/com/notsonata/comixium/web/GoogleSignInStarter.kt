package com.notsonata.comixium.web

import android.webkit.CookieManager
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Starts comix's Google sign-in with a direct request instead of a WebView navigation, so comix's
 * sign-in cookie (which holds the OAuth state) lands in the WebView's cookie jar for certain, and
 * the app gets Google's page URL without loading it. Runs on a background thread.
 */
object GoogleSignInStarter {
    sealed interface Result {
        /** Google's page to open in the browser; the sign-in comes back on [returnHost]. */
        data class Ready(val authorizeUrl: String, val returnHost: String) : Result
        data class Failed(val why: String) : Result
    }

    /** [startHost]: the comix domain the user tapped the button on. [userAgent]: the WebView's. */
    fun run(startHost: String, userAgent: String): Result = try {
        start(startHost, userAgent)
    } catch (e: Exception) {
        // Never let this take the app down: the caller falls back to loading comix's start page itself.
        Result.Failed("${e.javaClass.simpleName}: ${e.message}")
    }

    private fun start(startHost: String, userAgent: String): Result {
        var host = startHost
        repeat(2) {
            val (code, location) = request(GoogleSignIn.startUrl(host), userAgent) ?: return Result.Failed("no response from $host")
            if (code !in 300..399 || location == null || !GoogleSignIn.isGoogleAuthorize(location)) return Result.Failed("HTTP $code from $host")
            val back = GoogleSignIn.returnHost(location) ?: return Result.Failed("unexpected return address")
            if (back == host) return Result.Ready(location, back)
            host = back // start again where Google sends the user back, so the cookie is on that domain
        }
        return Result.Failed("return domain keeps changing")
    }

    private fun request(url: String, userAgent: String): Pair<Int, String?>? {
        val cookies = CookieManager.getInstance()
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = false
            connectTimeout = 15_000
            readTimeout = 15_000
            setRequestProperty("User-Agent", userAgent)
            setRequestProperty("Accept", "text/html,application/xhtml+xml,*/*;q=0.8")
            cookies.getCookie(url)?.let { setRequestProperty("Cookie", it) } // incl. Cloudflare clearance
        }
        return try {
            val code = conn.responseCode
            conn.headerFields.entries.filter { it.key.equals("Set-Cookie", ignoreCase = true) }
                .flatMap { it.value }.forEach { cookies.setCookie(url, it) }
            cookies.flush()
            code to conn.getHeaderField("Location")
        } catch (e: IOException) {
            null
        } finally {
            conn.disconnect()
        }
    }
}
