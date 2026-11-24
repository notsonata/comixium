package com.notsonata.comixium.web

import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit

/** App-side reader options (the site's own reader settings stay in the site). */
class ReaderPrefs(private val prefs: SharedPreferences) {
    var immersive by mutableStateOf(prefs.getBoolean(KEY_IMMERSIVE, true))
        private set
    var volumeKeys by mutableStateOf(prefs.getBoolean(KEY_VOLUME, true))
        private set
    var keepScreenOn by mutableStateOf(prefs.getBoolean(KEY_SCREEN_ON, true))
        private set
    var tapEdges by mutableStateOf(prefs.getBoolean(KEY_TAP_EDGES, true))
        private set
    var pinchZoom by mutableStateOf(prefs.getBoolean(KEY_PINCH_ZOOM, true))
        private set

    fun updateImmersive(on: Boolean) { immersive = on; prefs.edit { putBoolean(KEY_IMMERSIVE, on) } }
    fun updateVolumeKeys(on: Boolean) { volumeKeys = on; prefs.edit { putBoolean(KEY_VOLUME, on) } }
    fun updateKeepScreenOn(on: Boolean) { keepScreenOn = on; prefs.edit { putBoolean(KEY_SCREEN_ON, on) } }
    fun updateTapEdges(on: Boolean) { tapEdges = on; prefs.edit { putBoolean(KEY_TAP_EDGES, on) } }
    fun updatePinchZoom(on: Boolean) { pinchZoom = on; prefs.edit { putBoolean(KEY_PINCH_ZOOM, on) } }

    /** The options the page script applies itself (see setPrefs in comixium.js). */
    fun pageScriptCall(): String = "window.__comixium && window.__comixium.setPrefs({tapEdges:$tapEdges,pinchZoom:$pinchZoom})"

    private companion object {
        const val KEY_IMMERSIVE = "reader_immersive"
        const val KEY_VOLUME = "reader_volume_keys"
        const val KEY_SCREEN_ON = "reader_keep_screen_on"
        const val KEY_TAP_EDGES = "reader_tap_edges"
        const val KEY_PINCH_ZOOM = "reader_pinch_zoom"
    }
}
