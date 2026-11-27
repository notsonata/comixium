package com.notsonata.comixium

import android.graphics.Color
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.notsonata.comixium.ui.ImageSheet
import com.notsonata.comixium.web.ActivityServices
import com.notsonata.comixium.web.Site
import android.view.KeyEvent
import android.webkit.CookieManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.notsonata.comixium.ui.ComixiumApp
import com.notsonata.comixium.ui.CrashReportDialog
import com.notsonata.comixium.ui.theme.ComixiumTheme

class MainActivity : ComponentActivity(), ActivityServices {

    private val engine get() = (application as ComixiumApplication).engine

    private var imageMenu by mutableStateOf<Pair<String, String>?>(null)
    /** The last crash's report, offered once on the next launch (see [CrashLog]). */
    private var crashReport by mutableStateOf<String?>(null)
    private var fileCallback: ValueCallback<Array<Uri>>? = null
    private val fileLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        fileCallback?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(res.resultCode, res.data))
        fileCallback = null
    }

    override fun chooseFiles(callback: ValueCallback<Array<Uri>>, params: WebChromeClient.FileChooserParams): Boolean {
        fileCallback?.onReceiveValue(null)
        fileCallback = callback
        return try {
            fileLauncher.launch(params.createIntent())
            true
        } catch (_: ActivityNotFoundException) {
            fileCallback = null
            false
        }
    }

    override fun onImageLongPress(imageUrl: String, pageTitle: String) {
        imageMenu = imageUrl to pageTitle
    }

    private fun handleIntent(intent: Intent?) {
        val url = intent?.takeIf { it.action == Intent.ACTION_VIEW }?.dataString ?: return
        if (Site.isSiteUrl(url)) engine.deepLinkPath = Site.pathOf(url)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        engine.services = this
        engine.downloads.collectors.forEach { it.retarget(this) }
        // Also after a restore: a crash while another app was in front brings the app back that way.
        crashReport = CrashLog.pending(this)
        if (savedInstanceState == null) handleIntent(intent)
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
                imageMenu?.let { (url, title) ->
                    ImageSheet(url, title, userAgent = engine.activeTab?.webView?.settings?.userAgentString) { imageMenu = null }
                }
                crashReport?.let { report ->
                    CrashReportDialog(report) {
                        crashReport = null
                        CrashLog.clear(this)
                    }
                }
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
                    val down = if (engine.reader.invertVolume) -1 else 1
                    val dir = if (code == KeyEvent.KEYCODE_VOLUME_DOWN) down else -down
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

    override fun onStart() {
        super.onStart()
        engine.downloads.setForeground(true)
    }

    override fun onStop() {
        engine.downloads.setForeground(false)
        super.onStop()
    }

    override fun onDestroy() {
        if (engine.services === this) {
            engine.services = null
            engine.downloads.collectors.forEach { it.retarget(null) }
        }
        fileCallback?.onReceiveValue(null)
        fileCallback = null
        engine.createdTabs.forEach { it.detach(applicationContext) }
        super.onDestroy()
    }
}
