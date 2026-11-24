package com.notsonata.comixium.web

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream

class AdBlockerTest {
    private fun blocker(vararg domains: String): AdBlocker {
        val bytes = ByteArrayOutputStream().also { out ->
            GZIPOutputStream(out).use { it.write(("# comment\n" + domains.joinToString("\n")).toByteArray()) }
        }.toByteArray()
        return AdBlocker { bytes.inputStream() }.apply { load() }
    }

    @Test fun matchesDomainAndSubdomains() {
        val b = blocker("ads.example.com", "tracker.net")
        assertTrue(b.isBlockedHost("ads.example.com"))
        assertTrue(b.isBlockedHost("x.y.tracker.net"))
        assertFalse(b.isBlockedHost("example.com"))
        assertFalse(b.isBlockedHost("nottracker.net"))
        assertTrue(b.isBlockedHost("araplhn.org")) // built-in extra
    }

    @Test fun neverBlocksSiteCaptchaOrImages() {
        val b = blocker("comix.ws", "cloudflare.com", "cozyhomegoods.site")
        assertFalse(b.shouldBlock("https://comix.ws/api/v1/user", "application/json", false))
        assertFalse(b.shouldBlock("https://challenges.cloudflare.com/turnstile/v0/api.js", "*/*", false))
        assertFalse(b.shouldBlock("https://pq4.cozyhomegoods.site/hi/abc", "image/avif,image/webp,*/*", false))
        assertTrue(b.shouldBlock("https://pq4.cozyhomegoods.site/x.js", "*/*", false))
    }

    @Test fun acceptsPlainText() {
        val b = AdBlocker { "ads.example.com\n".byteInputStream() }.apply { load() }
        assertTrue(b.isBlockedHost("x.ads.example.com"))
    }

    @Test fun disabledBlocksNothing() {
        val b = blocker("tracker.net").apply { enabled = false }
        assertFalse(b.shouldBlock("https://tracker.net/p.js", "*/*", false))
    }
}
