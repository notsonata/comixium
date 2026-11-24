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
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AppPrefsTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private fun prefs() = AppPrefs(context.getSharedPreferences("app-test", Context.MODE_PRIVATE))

    @Test fun backAnimationDefaultsAndPersists() {
        val p = prefs()
        assertTrue(p.backAnimation)
        assertEquals("normal", p.backSpeed)
        assertEquals(90, p.backSettleMillis)
        p.updateBackAnimation(false)
        p.updateBackSpeed("slow")
        p.updateBackSpeed("warp") // unknown values are ignored
        val again = prefs()
        assertFalse(again.backAnimation)
        assertEquals("slow", again.backSpeed)
        assertTrue(again.backSettleMillis > 90)
        again.updateBackSpeed("fast")
        assertTrue(again.backSettleMillis < 90)
    }

    @Test fun backAnimationIsNotSentToPages() {
        assertFalse(prefs().pageScriptFields().contains("back"))
    }

    @Test fun cacheSizeCountsNestedFiles() {
        val dir = File(context.cacheDir, "size-test").apply { deleteRecursively(); mkdirs() }
        File(dir, "a").writeBytes(ByteArray(1000))
        File(dir, "sub/deeper").apply { mkdirs() }
        File(dir, "sub/deeper/b").writeBytes(ByteArray(2500))
        assertEquals(3500L, WebEngine.dirSize(dir))
        dir.deleteRecursively()
        assertEquals(0L, WebEngine.dirSize(dir))
    }
}
