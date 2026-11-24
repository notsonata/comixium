package com.notsonata.comixium.web

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CssColorTest {
    @Test fun formats() {
        assertEquals(0xFF22282A.toInt(), CssColor.parse("#22282a"))
        assertEquals(0xFF22282A.toInt(), CssColor.parse(" #22282A "))
        assertEquals(0xFFFFFFFF.toInt(), CssColor.parse("#fff"))
        assertEquals(0x800F172A.toInt(), CssColor.parse("#0f172a80"))
        assertEquals(0xFF66E8FA.toInt(), CssColor.parse("rgb(102, 232, 250)"))
        assertEquals(0xFF66E8FA.toInt(), CssColor.parse("rgb(102 232 250)"))
        assertEquals(0x7F000000, CssColor.parse("rgba(0,0,0,0.5)"))
        assertNull(CssColor.parse("var(--bg)"))
        assertNull(CssColor.parse(""))
        assertNull(CssColor.parse("#zzzzzz"))
    }
}
