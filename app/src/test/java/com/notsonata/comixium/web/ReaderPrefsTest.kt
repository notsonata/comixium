package com.notsonata.comixium.web

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ReaderPrefsTest {
    private fun prefs() = ReaderPrefs(
        ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("reader-test", Context.MODE_PRIVATE),
    )

    @Test fun changesFromTheSettingsSheetApplyAndPersist() {
        val p = prefs()
        assertTrue(p.apply("tapPaged", false))
        assertTrue(p.apply("tapZone", "large"))
        assertTrue(p.apply("controlsTap", "single"))
        assertTrue(p.apply("volume", "inverted"))
        assertTrue(p.apply("longPress", false))
        val again = prefs()
        assertFalse(again.tapPaged)
        assertEquals("large", again.tapZone)
        assertEquals("single", again.controlsTap)
        assertTrue(again.volumeKeys)
        assertTrue(again.invertVolume)
        assertFalse(again.longPressMenu)
        assertTrue(again.apply("volume", "off"))
        assertEquals("off", again.volumeMode)
    }

    @Test fun volumeKeysAreTwoSwitchesInTheSheet() {
        val p = prefs()
        assertTrue(p.apply("volumeKeys", true))
        assertTrue(p.apply("volumeInvert", true))
        assertEquals("inverted", p.volumeMode)
        assertTrue(p.apply("volumeKeys", false))
        assertTrue(prefs().invertVolume) // switching volume keys off keeps the invert choice
        assertEquals("off", prefs().volumeMode)
        val fields = prefs().pageScriptFields()
        assertTrue(fields, fields.contains("volumeKeys:false") && fields.contains("volumeInvert:true"))
    }

    @Test fun rejectsUnknownKeysAndValues() {
        val p = prefs()
        assertFalse(p.apply("nope", true))
        assertFalse(p.apply("tapZone", "huge"))
        assertFalse(p.apply("tapPaged", "yes"))
    }

    @Test fun pageScriptGetsEverySetting() {
        val fields = prefs().pageScriptFields()
        listOf("tapEdges", "tapPaged", "tapLayout", "tapZone", "invertTaps", "controlsTap", "pinchZoom", "longPress",
            "immersive", "keepOn", "volumeKeys", "volumeInvert", "layout", "x:", "y:", "size").forEach { assertTrue(it, fields.contains(it)) }
    }

    @Test fun controlsShownAndMinimalMigration() {
        val sp = ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("reader-test-2", Context.MODE_PRIVATE)
        sp.edit().putString("reader_controls_layout", "minimal").commit()
        val p = ReaderPrefs(sp)
        assertEquals("rail", p.controlsLayout)
        assertFalse(p.isControlShown("home"))
        assertTrue(p.isControlShown("chapter"))
        p.setControlShown("home", true)
        assertTrue(p.isControlShown("home"))
        assertTrue(p.apply("controlsHide", "comments,autoscroll"))
        assertFalse(p.isControlShown("comments"))
        assertFalse(p.apply("controlsHide", "settings"))
        assertFalse(p.apply("layout", "minimal"))
    }
}
