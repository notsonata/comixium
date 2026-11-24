package com.notsonata.comixium.web

import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit

/**
 * App-side reader options (the site's own reader settings stay in the site). They can be changed
 * in More, or in the reader's settings sheet, which sends each change here via [apply].
 */
class ReaderPrefs(private val prefs: SharedPreferences) {
    private fun bool(key: String, def: Boolean) = mutableStateOf(prefs.getBoolean(key, def))
    private fun str(key: String, def: String) = mutableStateOf(prefs.getString(key, def) ?: def)

    // Display
    var immersive by bool("reader_immersive", true)
        private set
    var keepScreenOn by bool("reader_keep_screen_on", true)
        private set
    var pinchZoom by bool("reader_pinch_zoom", true)
        private set
    var longPressMenu by bool("reader_long_press_menu", true)
        private set

    // Taps
    var tapPaged by bool("reader_tap_paged", true)
        private set
    var tapEdges by bool("reader_tap_edges", true)
        private set
    /** sides | vertical */
    var tapLayout by str("reader_tap_layout", "sides")
        private set
    /** small | medium | large */
    var tapZone by str("reader_tap_zone", "medium")
        private set
    var invertTaps by bool("reader_tap_invert", false)
        private set
    /** double | single | off: how a tap in the middle shows/hides the reader controls. */
    var controlsTap by str("reader_controls_tap", "double")
        private set

    // Volume keys
    var volumeKeys by bool("reader_volume_keys", true)
        private set
    var invertVolume by bool("reader_volume_invert", false)
        private set

    /** Reader controls: rail | bar; x left|center|right; y top|bottom; size normal|large. */
    var controlsLayout by str("reader_controls_layout", "rail")
        private set
    /** Control buttons switched off, comma-separated ids from [CONTROL_ITEMS]. */
    var controlsHide by str("reader_controls_hide", "")
        private set

    init {
        // "Minimal" used to be a layout; it is now the rail with only the chapter switcher and settings.
        if (controlsLayout == "minimal") {
            updateControlsLayout("rail")
            updateControlsHide("home,series,comments,chapters,autoscroll")
        }
    }

    fun isControlShown(id: String) = id !in controlsHide.split(',')
    fun setControlShown(id: String, shown: Boolean) {
        val hidden = controlsHide.split(',').filter { it.isNotEmpty() && it != id }.toMutableList()
        if (!shown) hidden += id
        updateControlsHide(hidden.joinToString(","))
    }
    var controlsX by str("reader_controls_x", "right")
        private set
    var controlsY by str("reader_controls_y", "top")
        private set
    var controlsSize by str("reader_controls_size", "normal")
        private set

    fun updateImmersive(on: Boolean) { immersive = on; save("reader_immersive", on) }
    fun updateKeepScreenOn(on: Boolean) { keepScreenOn = on; save("reader_keep_screen_on", on) }
    fun updatePinchZoom(on: Boolean) { pinchZoom = on; save("reader_pinch_zoom", on) }
    fun updateLongPressMenu(on: Boolean) { longPressMenu = on; save("reader_long_press_menu", on) }
    fun updateTapPaged(on: Boolean) { tapPaged = on; save("reader_tap_paged", on) }
    fun updateTapEdges(on: Boolean) { tapEdges = on; save("reader_tap_edges", on) }
    fun updateTapLayout(v: String) { tapLayout = v; save("reader_tap_layout", v) }
    fun updateTapZone(v: String) { tapZone = v; save("reader_tap_zone", v) }
    fun updateInvertTaps(on: Boolean) { invertTaps = on; save("reader_tap_invert", on) }
    fun updateControlsTap(v: String) { controlsTap = v; save("reader_controls_tap", v) }
    fun updateVolumeKeys(on: Boolean) { volumeKeys = on; save("reader_volume_keys", on) }
    fun updateInvertVolume(on: Boolean) { invertVolume = on; save("reader_volume_invert", on) }
    fun updateControlsLayout(v: String) { controlsLayout = v; save("reader_controls_layout", v) }
    fun updateControlsHide(v: String) { controlsHide = v; save("reader_controls_hide", v) }
    fun updateControlsX(v: String) { controlsX = v; save("reader_controls_x", v) }
    fun updateControlsY(v: String) { controlsY = v; save("reader_controls_y", v) }
    fun updateControlsSize(v: String) { controlsSize = v; save("reader_controls_size", v) }

    /** Volume keys as one setting: on | inverted | off. */
    val volumeMode: String get() = if (!volumeKeys) "off" else if (invertVolume) "inverted" else "on"

    /** Applies a change made in the reader's settings sheet. Returns false for unknown keys. */
    fun apply(key: String, value: Any?): Boolean {
        val b = value as? Boolean
        val s = value as? String
        when {
            key == "tapPaged" && b != null -> updateTapPaged(b)
            key == "tapEdges" && b != null -> updateTapEdges(b)
            key == "tapLayout" && s in listOf("sides", "vertical") -> updateTapLayout(s!!)
            key == "tapZone" && s in listOf("small", "medium", "large") -> updateTapZone(s!!)
            key == "invertTaps" && b != null -> updateInvertTaps(b)
            key == "controlsTap" && s in listOf("double", "single", "off") -> updateControlsTap(s!!)
            key == "volume" && s in listOf("on", "inverted", "off") -> { updateVolumeKeys(s != "off"); updateInvertVolume(s == "inverted") }
            key == "pinchZoom" && b != null -> updatePinchZoom(b)
            key == "longPress" && b != null -> updateLongPressMenu(b)
            key == "immersive" && b != null -> updateImmersive(b)
            key == "keepOn" && b != null -> updateKeepScreenOn(b)
            key == "layout" && s in listOf("rail", "bar") -> updateControlsLayout(s!!)
            key == "controlsHide" && s != null && s.split(',').all { it.isEmpty() || it in CONTROL_IDS } ->
                updateControlsHide(s.split(',').filter { it.isNotEmpty() }.joinToString(","))
            key == "x" && s in listOf("left", "center", "right") -> updateControlsX(s!!)
            key == "y" && s in listOf("top", "bottom") -> updateControlsY(s!!)
            key == "size" && s in listOf("normal", "large") -> updateControlsSize(s!!)
            else -> return false
        }
        return true
    }

    /** Everything the page script needs (see setPrefs in comixium.js). */
    fun pageScriptFields(): String =
        "tapEdges:$tapEdges,tapPaged:$tapPaged,tapLayout:'$tapLayout',tapZone:'$tapZone',invertTaps:$invertTaps," +
            "controlsTap:'$controlsTap',pinchZoom:$pinchZoom,longPress:$longPressMenu,immersive:$immersive," +
            "keepOn:$keepScreenOn,volume:'$volumeMode',layout:'$controlsLayout',x:'$controlsX',y:'$controlsY'," +
            "size:'$controlsSize',controlsHide:'$controlsHide'"

    companion object {
        /** Reader control buttons that can be switched off (the settings button always stays). */
        val CONTROL_ITEMS = listOf(
            "chapter" to "Chapter switcher", "home" to "Home", "series" to "Series page",
            "comments" to "Comments", "chapters" to "Chapter list", "autoscroll" to "Auto-scroll",
        )
        private val CONTROL_IDS = CONTROL_ITEMS.map { it.first }.toSet()
    }

    private fun save(key: String, v: Boolean) = prefs.edit { putBoolean(key, v) }
    private fun save(key: String, v: String) = prefs.edit { putString(key, v) }
}
