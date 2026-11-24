package com.notsonata.comixium.web

import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit

/** App-wide page options applied by the page script (see setPrefs in comixium.js). */
class AppPrefs(private val prefs: SharedPreferences) {
    /** Touch-friendly fixes to the site's layout (comixium.css, html[data-cx-mobile]). Off = the site's own mobile web. */
    var mobileOptimizations by mutableStateOf(prefs.getBoolean(KEY_MOBILE, true))
        private set
    var homeAnnouncements by mutableStateOf(prefs.getBoolean(KEY_ANNOUNCE, true))
        private set
    var homeShareBar by mutableStateOf(prefs.getBoolean(KEY_SHARE, true))
        private set

    /** Set when announcements are switched back on, so ones dismissed with ✕ return too. */
    private var clearDismissed = false

    fun updateMobileOptimizations(on: Boolean) { mobileOptimizations = on; prefs.edit { putBoolean(KEY_MOBILE, on) } }
    fun updateHomeAnnouncements(on: Boolean) {
        if (on && !homeAnnouncements) clearDismissed = true
        homeAnnouncements = on
        prefs.edit { putBoolean(KEY_ANNOUNCE, on) }
    }
    fun updateHomeShareBar(on: Boolean) { homeShareBar = on; prefs.edit { putBoolean(KEY_SHARE, on) } }

    fun pageScriptFields(): String {
        val clear = clearDismissed
        clearDismissed = false
        return "mobile:$mobileOptimizations,announce:$homeAnnouncements,share:$homeShareBar" + if (clear) ",clearDismissed:true" else ""
    }

    private companion object {
        const val KEY_MOBILE = "mobile_optimizations"
        const val KEY_ANNOUNCE = "home_announcements"
        const val KEY_SHARE = "home_share_bar"
    }
}
