package com.notsonata.comixium

import android.graphics.Color
import android.os.Bundle
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
