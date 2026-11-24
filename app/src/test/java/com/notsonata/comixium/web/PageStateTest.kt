package com.notsonata.comixium.web

import com.notsonata.comixium.ui.theme.ComixColors
import com.notsonata.comixium.ui.theme.withSiteTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PageStateTest {
    @Test fun parsesSnapshot() {
        val s = PageState.parse(
            """{"t":"state","url":"https://comix.ws/","title":"Comix","reader":false,"auth":"out","dialog":true,
               "tone":"light","theme":{"bg":"#f8f8f7","accent":"#1c6fb0","text":""}}"""
        )!!
        assertEquals(AuthState.LOGGED_OUT, s.auth)
        assertTrue(s.dialogOpen)
        assertTrue(s.isLightTone)
        assertEquals(mapOf("bg" to "#f8f8f7", "accent" to "#1c6fb0"), s.theme)
        val c = ComixColors().withSiteTheme(s.theme, s.isLightTone)
        assertEquals(0xFFF8F8F7.toInt(), c.bg.value.shr(32).toInt())
        assertEquals(ComixColors().text, c.text) // blank value keeps default
        assertTrue(c.isLight)
    }

    @Test fun ignoresOtherMessages() {
        assertNull(PageState.parse("""{"t":"other"}"""))
        assertNull(PageState.parse("not json"))
    }
}
