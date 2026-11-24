package com.notsonata.comixium.web

import java.net.URLDecoder

/** Turns the site's document title into something that fits a native top bar. */
object PageTitle {
    private val USER_TABS = mapOf(
        "titles" to "Library",
        "history" to "History",
        "notifs" to "Notifications",
        "collections" to "My collections",
        "uploads" to "Uploads",
        "edit" to "Edit profile",
        "settings" to "Account settings",
        "backup" to "Backup",
    )
    private val SUFFIXES = listOf(" - Comix", " | Comix", " · Comix", " — Comix")

    fun of(url: String?, rawTitle: String): String {
        val path = Site.pathOnly(url).trimEnd('/').ifEmpty { "/" }
        if (path == "/user") return USER_TABS[param(url, "tab")] ?: "Profile"
        val t = rawTitle.trim()
        val generic = t.isEmpty() || t == "Comix" || t.startsWith("Comix - ") || t.startsWith("http")
        if (!generic) return SUFFIXES.fold(t) { acc, s -> acc.removeSuffix(s) }
        return when {
            path == "/" || path == "/home" -> ""
            path.startsWith("/browse") -> param(url, "q")?.takeIf { it.isNotBlank() }?.let { "“$it”" } ?: "Browse"
            else -> ""
        }
    }

    fun param(url: String?, name: String): String? {
        val m = Regex("[?&]${Regex.escape(name)}=([^&#]*)").find(url ?: return null) ?: return null
        return runCatching { URLDecoder.decode(m.groupValues[1], "UTF-8") }.getOrNull()
    }
}
