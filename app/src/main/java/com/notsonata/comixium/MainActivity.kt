package com.notsonata.comixium

import android.graphics.Color
import android.os.Bundle
import android.view.KeyEvent
import android.webkit.CookieManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.notsonata.comixium.ui.ComixiumApp
import com.notsonata.comixium.ui.theme.ComixiumTheme

class MainActivity : ComponentActivity() {

    private val engine get() = (application as ComixiumApplication).engine

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val colors = engine.colors
            LaunchedEffect(colors.isLight) {
                val style = if (colors.isLight) {
                    SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                } else {
                    SystemBarStyle.dark(Color.TRANSPARENT)
                }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            ComixiumTheme(colors) {
                ComixiumApp(engine)
            }
        }
    }

    /** Volume keys step through a chapter (when enabled and a reader page is on screen). */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val code = event.keyCode
        if (code == KeyEvent.KEYCODE_VOLUME_DOWN || code == KeyEvent.KEYCODE_VOLUME_UP) {
            val tab = engine.activeTab
            if (engine.reader.volumeKeys && tab != null && tab.isReader) {
                // Consume both down and up so the system volume doesn't change; act on presses
                // and on every few repeats while held.
                if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount % 3 == 0) {
                    val dir = if (code == KeyEvent.KEYCODE_VOLUME_DOWN) 1 else -1
                    tab.js("window.__comixium && window.__comixium.readerStep($dir)")
                }
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onResume() {
        super.onResume()
        engine.createdTabs.forEach { it.onResume() }
    }

    override fun onPause() {
        engine.createdTabs.forEach { it.onPause() }
        CookieManager.getInstance().flush()
        super.onPause()
    }

    override fun onDestroy() {
        engine.createdTabs.forEach { it.detach(applicationContext) }
        super.onDestroy()
    }
}
