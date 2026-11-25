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
import android.content.pm.verify.domain.DomainVerificationManager
import android.content.pm.verify.domain.DomainVerificationUserState
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import com.notsonata.comixium.web.ActivityServices
import com.notsonata.comixium.web.GoogleSignIn
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
import com.notsonata.comixium.ui.theme.ComixiumTheme

class MainActivity : ComponentActivity(), ActivityServices {

    private val engine get() = (application as ComixiumApplication).engine

    private var imageMenu by mutableStateOf<Pair<String, String>?>(null)
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
        when {
            GoogleSignIn.isCallback(url) -> engine.googleCallbackUrl = url
            Site.isSiteUrl(url) -> engine.deepLinkPath = Site.pathOf(url)
        }
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
        engine.onActivityResumed()
    }

    override fun canOpenLinks(host: String): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true // asks with a chooser instead
        val state = getSystemService(DomainVerificationManager::class.java)
            ?.getDomainVerificationUserState(packageName) ?: return true
        if (!state.isLinkHandlingAllowed) return false
        return when (state.hostToStateMap[host]) {
            DomainVerificationUserState.DOMAIN_STATE_SELECTED, DomainVerificationUserState.DOMAIN_STATE_VERIFIED -> true
            else -> false
        }
    }

    override fun openInBrowser(url: String) {
        val view = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE)
        // The selector picks the default browser app, so no other app claims the link.
        val browser = Intent(view).apply { selector = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_BROWSER) }
        try {
            startActivity(browser)
        } catch (e: ActivityNotFoundException) {
            try {
                startActivity(view)
            } catch (e: ActivityNotFoundException) {
                Toast.makeText(this, "No browser found to sign in with Google", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun openLinkSettings() {
        val pkg = Uri.parse("package:$packageName")
        val links = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Intent(Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS, pkg) else null
        try {
            startActivity(links ?: Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg))
        } catch (e: ActivityNotFoundException) {
            runCatching { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg)) }
        }
    }

    override fun onPause() {
        engine.createdTabs.forEach { it.onPause() }
        CookieManager.getInstance().flush()
        super.onPause()
    }

    override fun onDestroy() {
        if (engine.services === this) engine.services = null
        fileCallback?.onReceiveValue(null)
        fileCallback = null
        engine.createdTabs.forEach { it.detach(applicationContext) }
        super.onDestroy()
    }
}
