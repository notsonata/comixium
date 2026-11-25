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
import com.notsonata.comixium.ui.CrashReportDialog
import com.notsonata.comixium.ui.ImageSheet
import android.content.pm.verify.domain.DomainVerificationManager
import android.content.pm.verify.domain.DomainVerificationUserState
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import android.content.pm.PackageManager
import com.notsonata.comixium.web.ActivityServices
import com.notsonata.comixium.web.BrowserApp
import com.notsonata.comixium.web.BrowserChoice
import com.notsonata.comixium.web.GoogleSignIn
import com.notsonata.comixium.web.LinkAccess
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
        val url = when (intent?.action) {
            Intent.ACTION_VIEW -> intent.dataString
            // A shared link (e.g. "Share" on the browser's page): the first comix URL in the text.
            // Some browsers share styled text rather than a plain string, so read it as CharSequence.
            Intent.ACTION_SEND -> intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()?.let { text ->
                Site.firstSiteUrl(text)
                    ?: run { Toast.makeText(this, "That isn't a comix link", Toast.LENGTH_SHORT).show(); null }
            }
            else -> null
        } ?: return
        when {
            GoogleSignIn.isCallback(url) -> engine.onGoogleCallback(url)
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
        if (savedInstanceState == null) {
            crashReport = CrashLog.pending(this)
            handleIntent(intent)
        }
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
        engine.onActivityResumed()
    }

    override fun linkAccess(host: String): LinkAccess {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return LinkAccess.ALLOWED // asks with a chooser instead
        val state = runCatching {
            getSystemService(DomainVerificationManager::class.java)?.getDomainVerificationUserState(packageName)
        }.getOrNull() ?: return LinkAccess.ALLOWED
        if (!state.isLinkHandlingAllowed) return LinkAccess.SWITCHED_OFF
        val wanted = setOf(host, "www.$host", host.removePrefix("www."))
        val ok = state.hostToStateMap.any { (h, s) ->
            h in wanted && (s == DomainVerificationUserState.DOMAIN_STATE_SELECTED || s == DomainVerificationUserState.DOMAIN_STATE_VERIFIED)
        }
        return if (ok) LinkAccess.ALLOWED else LinkAccess.DOMAIN_NOT_ADDED
    }

    override fun openInBrowser(url: String) {
        val browsers = installedBrowsers()
        val default = defaultBrowser()?.takeIf { pkg -> browsers.any { it.packageName == pkg } }
        when {
            default != null -> openInBrowserApp(url, default)
            browsers.size == 1 -> openInBrowserApp(url, browsers[0].packageName)
            browsers.isEmpty() -> Toast.makeText(this, "No browser found to sign in with Google", Toast.LENGTH_LONG).show()
            else -> engine.browserChoice = BrowserChoice(url, browsers)
        }
    }

    override fun openInBrowserApp(url: String, packageName: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE).setPackage(packageName)
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "Couldn't open the browser", Toast.LENGTH_LONG).show()
        }
    }

    /** Any web address, to find apps that open every link: browsers, not apps that claim certain sites. */
    private val anyWebPage = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com/")).addCategory(Intent.CATEGORY_BROWSABLE)

    private fun installedBrowsers(): List<BrowserApp> {
        val pm = packageManager
        val found = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(anyWebPage, PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_ALL.toLong()))
        } else {
            @Suppress("DEPRECATION") pm.queryIntentActivities(anyWebPage, PackageManager.MATCH_ALL)
        }
        return found.map { it.activityInfo.packageName to it }
            .filter { (pkg, _) -> pkg != packageName }
            .distinctBy { it.first }
            .map { (pkg, info) -> BrowserApp(pkg, info.loadLabel(pm).toString(), runCatching { info.loadIcon(pm) }.getOrNull()) }
            .sortedBy { it.label.lowercase() }
    }

    /** The default browser's package, or null if the user hasn't picked one (Android would ask). */
    private fun defaultBrowser(): String? {
        val pm = packageManager
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.resolveActivity(anyWebPage, PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong()))
        } else {
            @Suppress("DEPRECATION") pm.resolveActivity(anyWebPage, PackageManager.MATCH_DEFAULT_ONLY)
        }
        return info?.activityInfo?.packageName?.takeIf { it != "android" && it != packageName }
    }

    override fun linkDetails(): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return "Android ${Build.VERSION.RELEASE}: links open with a chooser"
        val state = runCatching {
            getSystemService(DomainVerificationManager::class.java)?.getDomainVerificationUserState(packageName)
        }.getOrNull() ?: return "Android didn't report link settings"
        val hosts = state.hostToStateMap.entries.sortedBy { it.key }.joinToString(" · ") { (h, s) ->
            h + ": " + when (s) {
                DomainVerificationUserState.DOMAIN_STATE_SELECTED -> "added"
                DomainVerificationUserState.DOMAIN_STATE_VERIFIED -> "verified"
                else -> "not added"
            }
        }
        return "Open supported links: ${if (state.isLinkHandlingAllowed) "on" else "off"} · $hosts"
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
