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

    /** The predictive-back preview (page shrinks and follows the swipe). App-only, not sent to pages. */
    var backAnimation by mutableStateOf(prefs.getBoolean(KEY_BACK_ANIM, true))
        private set
    /** How fast the page settles after a back swipe: one of [BACK_SPEEDS]. */
    var backSpeed by mutableStateOf(prefs.getString(KEY_BACK_SPEED, null)?.takeIf { it in BACK_SPEEDS } ?: "normal")
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
    fun updateBackAnimation(on: Boolean) { backAnimation = on; prefs.edit { putBoolean(KEY_BACK_ANIM, on) } }
    fun updateBackSpeed(v: String) {
        if (v !in BACK_SPEEDS) return
        backSpeed = v
        prefs.edit { putString(KEY_BACK_SPEED, v) }
    }

    /** Milliseconds the page takes to settle once a back swipe is released. */
    val backSettleMillis: Int get() = when (backSpeed) { "fast" -> 60; "slow" -> 220; else -> 90 }

    fun pageScriptFields(): String {
        val clear = clearDismissed
        clearDismissed = false
        return "mobile:$mobileOptimizations,announce:$homeAnnouncements,share:$homeShareBar" + if (clear) ",clearDismissed:true" else ""
    }

    companion object {
        val BACK_SPEEDS = listOf("fast", "normal", "slow")
        private const val KEY_BACK_ANIM = "back_animation"
        private const val KEY_BACK_SPEED = "back_speed"
        private const val KEY_MOBILE = "mobile_optimizations"
        private const val KEY_ANNOUNCE = "home_announcements"
        private const val KEY_SHARE = "home_share_bar"
    }
}
