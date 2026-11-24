package com.notsonata.comixium

import android.graphics.Color
import android.os.Bundle
import android.webkit.CookieManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.notsonata.comixium.ui.WebTabView
import com.notsonata.comixium.ui.theme.ComixiumTheme
import com.notsonata.comixium.web.WebTab

class MainActivity : ComponentActivity() {

    private val engine get() = (application as ComixiumApplication).engine
    private val tabs = mutableListOf<WebTab>()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val home = WebTab("home", "/", engine).also { tabs += it }
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
                BackHandler(enabled = home.canGoBack || home.dialogOpen) { home.handleBack() }
                Box(Modifier.fillMaxSize().background(colors.bg)) {
                    WebTabView(
                        tab = remember { home },
                        engine = engine,
                        modifier = Modifier.windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.ime)),
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        tabs.forEach { it.onResume() }
    }

    override fun onPause() {
        tabs.forEach { it.onPause() }
        CookieManager.getInstance().flush()
        super.onPause()
    }

    override fun onDestroy() {
        tabs.forEach { it.detach(applicationContext) }
        super.onDestroy()
    }
}
