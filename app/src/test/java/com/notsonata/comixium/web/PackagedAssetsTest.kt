package com.notsonata.comixium.web

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Loads the real packaged assets, so a renamed or mangled asset fails here instead of silently on a phone. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PackagedAssetsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test fun blocklistLoadsFromAssets() {
        val b = AdBlocker { context.assets.open(WebEngine.HOSTS_ASSET) }.apply { load() }
        assertTrue("expected the full list, got ${b.size}", b.size > 50_000)
        assertTrue(b.isBlockedHost("whos.amung.us"))
        assertFalse(b.isBlockedHost("comix.ws"))
    }

    @Test fun pageScriptAndStylesheetExist() {
        val js = context.assets.open("inject/comixium.js").bufferedReader().readText()
        val css = context.assets.open("inject/comixium.css").bufferedReader().readText()
        assertTrue(js.contains("__COMIXIUM_CSS__"))
        assertTrue(css.contains(".topnav"))
    }

    @Test fun downloadCollectorScriptExists() {
        val js = context.assets.open("inject/collect.js").bufferedReader().readText()
        assertTrue(js.contains("ComixiumCollector"))
        assertTrue(js.contains(".rpage-page"))
    }
}
