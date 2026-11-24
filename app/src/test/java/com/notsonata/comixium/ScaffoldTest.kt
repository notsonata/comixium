package com.notsonata.comixium

import com.notsonata.comixium.ui.theme.ComixColors
import org.junit.Assert.assertEquals
import org.junit.Test

class ScaffoldTest {
    @Test
    fun defaultPaletteMatchesSiteMainTheme() {
        val c = ComixColors()
        assertEquals(0xFF22282AL.toInt(), c.bg.value.shr(32).toInt())
        assertEquals(0xFF66E8FAL.toInt(), c.accent.value.shr(32).toInt())
    }
}
