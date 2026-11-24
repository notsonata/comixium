package com.notsonata.comixium.web

import org.json.JSONObject

enum class AuthState { UNKNOWN, LOGGED_IN, LOGGED_OUT }

/** A snapshot the page script posts whenever something the app cares about changes. */
data class PageState(
    val url: String,
    val title: String,
    val isReader: Boolean,
    val auth: AuthState,
    val dialogOpen: Boolean,
    val isLightTone: Boolean,
    /** CSS variable name (without dashes, camelCase as in the script) -> raw CSS value. */
    val theme: Map<String, String>,
) {
    companion object {
        /** The message's "t" field ("state", "download", "pref"). */
        fun type(json: String): String? = runCatching { JSONObject(json).optString("t") }.getOrNull()

        fun parse(json: String): PageState? = runCatching {
            val o = JSONObject(json)
            if (o.optString("t") != "state") return null
            val t = o.optJSONObject("theme")
            val theme = buildMap {
                t?.keys()?.forEach { k -> t.optString(k).takeIf { it.isNotBlank() }?.let { put(k, it) } }
            }
            PageState(
                url = o.optString("url"),
                title = o.optString("title"),
                isReader = o.optBoolean("reader"),
                auth = when (o.optString("auth")) {
                    "in" -> AuthState.LOGGED_IN
                    "out" -> AuthState.LOGGED_OUT
                    else -> AuthState.UNKNOWN
                },
                dialogOpen = o.optBoolean("dialog"),
                isLightTone = o.optString("tone") == "light",
                theme = theme,
            )
        }.getOrNull()
    }
}

/** A file the page generated in memory (blob:/data: link) and asked us to save. */
class PageDownload(val name: String, val mime: String, val bytes: ByteArray) {
    companion object {
        fun parse(json: String): PageDownload? = runCatching {
            val o = JSONObject(json)
            if (o.optString("t") != "download") return null
            val data = java.util.Base64.getDecoder().decode(o.getString("data"))
            PageDownload(o.optString("name").ifBlank { "download" }, o.optString("mime").ifBlank { "application/octet-stream" }, data)
        }.getOrNull()
    }
}
