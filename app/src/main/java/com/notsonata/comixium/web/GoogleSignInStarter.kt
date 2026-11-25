package com.notsonata.comixium.web

import java.net.HttpURLConnection
import java.net.URL

/**
 * Starts comix's Google sign-in with a direct request instead of a WebView navigation, so the app
 * gets comix's sign-in cookie (which holds the OAuth state) for certain, and Google's page URL
 * without loading it. Network only, run on a background thread: the caller reads and stores the
 * WebView's cookies on the main thread.
 */
object GoogleSignInStarter {
    sealed interface Result {
        /** Google's page to open in the browser; the sign-in comes back on [returnHost]. [setCookies]: (url, Set-Cookie) to store. */
        data class Ready(val authorizeUrl: String, val returnHost: String, val setCookies: List<Pair<String, String>>) : Result
        data class Failed(val why: String) : Result
    }

    /**
     * [startHost]: the comix domain the user tapped the button on. [userAgent]: the WebView's.
     * [cookieHeader]: the Cookie header the WebView would send to a host's start URL (incl. Cloudflare clearance).
     */
    fun run(startHost: String, userAgent: String, cookieHeader: (String) -> String?): Result = try {
        start(startHost, userAgent, cookieHeader)
    } catch (e: Exception) {
        // Never let this take the app down: the caller falls back to loading comix's start page itself.
        Result.Failed("${e.javaClass.simpleName}: ${e.message}")
    }

    private fun start(startHost: String, userAgent: String, cookieHeader: (String) -> String?): Result {
        var host = startHost
        repeat(2) {
            val url = GoogleSignIn.startUrl(host)
            val res = request(url, userAgent, cookieHeader(host)) ?: return Result.Failed("no response from $host")
            val location = res.location
            if (res.code !in 300..399 || location == null || !GoogleSignIn.isGoogleAuthorize(location)) return Result.Failed("HTTP ${res.code} from $host")
            val back = GoogleSignIn.returnHost(location) ?: return Result.Failed("unexpected return address")
            if (back == host) return Result.Ready(location, back, res.setCookies.map { url to it })
            host = back // start again where Google sends the user back, so the cookie is on that domain
        }
        return Result.Failed("return domain keeps changing")
    }

    private class Response(val code: Int, val location: String?, val setCookies: List<String>)

    private fun request(url: String, userAgent: String, cookie: String?): Response? {
        val conn = URL(url).openConnection() as HttpURLConnection
        return try {
            conn.instanceFollowRedirects = false
            conn.connectTimeout = 15_000
            conn.readTimeout = 15_000
            conn.setRequestProperty("User-Agent", userAgent)
            conn.setRequestProperty("Accept", "text/html,application/xhtml+xml,*/*;q=0.8")
            if (cookie != null) conn.setRequestProperty("Cookie", cookie)
            val code = conn.responseCode
            val setCookies = conn.headerFields.entries.filter { it.key.equals("Set-Cookie", ignoreCase = true) }.flatMap { it.value }
            Response(code, conn.getHeaderField("Location"), setCookies)
        } catch (e: java.io.IOException) {
            null
        } finally {
            conn.disconnect()
        }
    }
}
