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

    @Test fun rejectsUnknownKeysAndValues() {
        val p = prefs()
        assertFalse(p.apply("nope", true))
        assertFalse(p.apply("tapZone", "huge"))
        assertFalse(p.apply("tapPaged", "yes"))
    }

    @Test fun pageScriptGetsEverySetting() {
        val fields = prefs().pageScriptFields()
        listOf("tapEdges", "tapPaged", "tapLayout", "tapZone", "invertTaps", "controlsTap", "pinchZoom", "longPress",
            "immersive", "keepOn", "volume", "layout", "x:", "y:", "size").forEach { assertTrue(it, fields.contains(it)) }
    }
}
