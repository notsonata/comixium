package com.notsonata.comixium.ui

import android.text.format.Formatter
import android.widget.Toast
import android.view.ViewGroup
import androidx.compose.ui.viewinterop.AndroidView
import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import android.app.Activity
import android.view.WindowManager
import com.notsonata.comixium.ui.theme.LocalComixColors
import com.notsonata.comixium.web.AuthState
import com.notsonata.comixium.web.Downloads
import com.notsonata.comixium.web.Site
import com.notsonata.comixium.web.WebEngine
import com.notsonata.comixium.web.WebTab
import java.net.URLEncoder

private const val MORE_PAGE = "MORE_PAGE"
private const val DOWNLOADED_PAGE = "DOWNLOADED_PAGE"

/** Android's predictive-back progress curve: responsive at first, then decelerating. */
private val BACK_EASING = CubicBezierEasing(0.1f, 0.1f, 0f, 1f)

/**
 * The app shell: native top bar, bottom tabs, search and More screens around the site's pages.
 * Each bottom tab keeps its own WebView and history; the More tab opens pages on its own stack.
 */

@Composable
fun ComixiumApp(engine: WebEngine) {
    Box(Modifier.fillMaxSize()) {
        // The page that prepares chapter downloads (see PageCollector): drawn, but under the app's screens.
        AndroidView(
            factory = { engine.downloads.collector.host.also { (it.parent as? ViewGroup)?.removeView(it) } },
            modifier = Modifier.fillMaxSize(),
        )
        AppContent(engine)
    }
}

@Composable
private fun AppContent(engine: WebEngine) {
    val c = LocalComixColors.current
    val context = LocalContext.current
    var selected by rememberSaveable { mutableStateOf(AppTab.HOME) }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var morePageOpen by rememberSaveable { mutableStateOf(false) }
    /** The native Settings page, one level below More (a web page opened from More sits above both). */
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    /** More → Downloads: what's downloading, and the download settings. */
    var downloadsOpen by rememberSaveable { mutableStateOf(false) }
    /** Library → Downloaded instead of the site's library; [downloadedSeries] is a series opened in it. */
    var libraryDownloaded by rememberSaveable { mutableStateOf(false) }
    var downloadedSeries by rememberSaveable { mutableStateOf<String?>(null) }
    /** A page (usually a downloaded chapter) open from Library → Downloaded, on its own tab. */
    var downloadedPageOpen by rememberSaveable { mutableStateOf(false) }
    val moreScroll = rememberScrollState()
    /** Tab to go back to once a login started from its "log in" prompt succeeds. */
    var returnAfterLogin by rememberSaveable { mutableStateOf<AppTab?>(null) }
    val visited = rememberSaveable(saver = listSaver(), init = { mutableStateListOf(AppTab.HOME) })

    fun webTab(t: AppTab): WebTab = engine.tab(t.name, t.startPath)
    val moreTab = engine.tab(MORE_PAGE, null)
    /** Downloaded chapters open here, from the phone even when online (see OfflineServer). */
    val downloadedTab = engine.tab(DOWNLOADED_PAGE, null, preferOffline = true)
    val inDownloaded = selected == AppTab.LIBRARY && libraryDownloaded
    /**
     * Theme & content filter: the site's settings dialog, styled as a full page (comixium.css) and
     * shown on the More stack. Leaving the page closes the dialog, and the dialog closing leaves the page.
     */
    var siteSettingsPage by rememberSaveable { mutableStateOf(false) }
    var siteSettingsShown by remember { mutableStateOf(false) }
    val auth = engine.auth

    val current: WebTab? = when {
        selected == AppTab.MORE -> if (morePageOpen) moreTab else null
        inDownloaded -> if (downloadedPageOpen) downloadedTab else null
        selected.needsLogin && auth == AuthState.LOGGED_OUT -> null
        else -> webTab(selected)
    }
    val reader = current?.isReader == true
    SideEffect { engine.activeTab = current }
    ReaderWindowEffects(
        immersive = reader && engine.reader.immersive,
        keepScreenOn = reader && engine.reader.keepScreenOn,
    )

    // Account pages depend on who is logged in: refresh them when that changes.
    LaunchedEffect(auth) {
        if (auth == AuthState.LOGGED_IN) {
            listOf(AppTab.LIBRARY, AppTab.HISTORY).map(::webTab).filter { it.isCreated }.forEach { t ->
                t.startPath?.let(t::open)
            }
            returnAfterLogin?.let { selected = it }
            returnAfterLogin = null
        }
    }

    // Links opened from other apps land on top of the Home tab's history.
    LaunchedEffect(engine.deepLinkPath) {
        val path = engine.deepLinkPath ?: return@LaunchedEffect
        engine.deepLinkPath = null
        searchOpen = false
        selected = AppTab.HOME
        webTab(AppTab.HOME).push(path)
    }

    fun select(t: AppTab) {
        if (t == selected) {
            when {
                t == AppTab.MORE -> {
                    if (siteSettingsPage && moreTab.dialogOpen) moreTab.js("window.__comixium && window.__comixium.closeDialog()")
                    siteSettingsPage = false
                    morePageOpen = false
                    settingsOpen = false
                    downloadsOpen = false
                }
                t == AppTab.LIBRARY && libraryDownloaded -> {
                    downloadedPageOpen = false
                    downloadedSeries = null
                }
                else -> webTab(t).goRoot()
            }
            return
        }
        selected = t
        if (t !in visited) visited += t
    }

    LaunchedEffect(moreTab.dialogOpen, siteSettingsPage) {
        if (!siteSettingsPage) return@LaunchedEffect
        if (moreTab.dialogOpen) siteSettingsShown = true
        else if (siteSettingsShown) { siteSettingsShown = false; siteSettingsPage = false; morePageOpen = false }
    }

    fun openInMore(path: String) {
        siteSettingsPage = false
        moreTab.open(path)
        morePageOpen = true
        if (AppTab.MORE !in visited) visited += AppTab.MORE
        selected = AppTab.MORE
    }

    /** More → Downloads. */
    fun openDownloads() {
        searchOpen = false
        siteSettingsPage = false
        morePageOpen = false
        settingsOpen = false
        downloadsOpen = true
        if (AppTab.MORE !in visited) visited += AppTab.MORE
        selected = AppTab.MORE
    }

    /** Library → Downloaded, optionally on one series. */
    fun openDownloaded(series: String? = null) {
        searchOpen = false
        libraryDownloaded = true
        downloadedSeries = series
        downloadedPageOpen = false
        if (AppTab.LIBRARY !in visited) visited += AppTab.LIBRARY
        selected = AppTab.LIBRARY
    }

    /** A page from Library → Downloaded (a downloaded chapter, or a series page). */
    fun openFromDownloaded(path: String, series: String?) {
        openDownloaded(series)
        downloadedTab.open(path)
        downloadedPageOpen = true
    }

    fun openSiteSettings() {
        if (moreTab.isCreated && !moreTab.isReader) moreTab.siteAction("openSettings")
        else moreTab.openWithAction("/", "openSettings")
        siteSettingsPage = true
        siteSettingsShown = false
        morePageOpen = true
        if (AppTab.MORE !in visited) visited += AppTab.MORE
        selected = AppTab.MORE
    }

    /** Site dialogs (login, settings) need a page with the site header; prefer the visible tab. */
    fun siteAction(name: String) {
        val target = current?.takeIf { !it.isReader } ?: run {
            selected = AppTab.HOME
            webTab(AppTab.HOME)
        }
        target.siteAction(name)
    }

    fun back(): Boolean {
        if (searchOpen) { searchOpen = false; return true }
        if (siteSettingsPage && selected == AppTab.MORE && morePageOpen) {
            // A view inside the settings (Content preferences) goes back to the main view first.
            if (moreTab.dialogSubpage.isNotEmpty()) return moreTab.handleBack()
            if (moreTab.dialogOpen) moreTab.js("window.__comixium && window.__comixium.closeDialog()")
            siteSettingsPage = false
            morePageOpen = false
            return true
        }
        if (current?.handleBack() == true) return true
        if (selected == AppTab.MORE && morePageOpen) { morePageOpen = false; return true }
        if (selected == AppTab.MORE && settingsOpen) { settingsOpen = false; return true }
        if (selected == AppTab.MORE && downloadsOpen) { downloadsOpen = false; return true }
        if (inDownloaded && downloadedPageOpen) { downloadedPageOpen = false; return true }
        if (inDownloaded && downloadedSeries != null) { downloadedSeries = null; return true }
        if (selected != AppTab.HOME) { selected = AppTab.HOME; return true }
        return false
    }

    val canHandleBack = searchOpen || current?.canGoBack == true || current?.dialogOpen == true ||
        (selected == AppTab.MORE && (morePageOpen || settingsOpen || downloadsOpen)) ||
        (inDownloaded && (downloadedPageOpen || downloadedSeries != null)) || selected != AppTab.HOME
    // Predictive back (Android 14+): the page follows the finger 1:1 (shrinks a little and slides
    // toward the swipe), then settles on release (speed from settings) or springs back on cancel.
    // With the back animation off there's no preview: Back just changes the page on release.
    val backAnim = remember { Animatable(0f) }
    var backFromLeft by remember { mutableStateOf(true) }
    val backScope = rememberCoroutineScope()
    PredictiveBackHandler(enabled = canHandleBack) { events ->
        try {
            // Closing a popup or search isn't a page change: no preview for those.
            val leavesSettingsPage = siteSettingsPage && current === moreTab && moreTab.dialogSubpage.isEmpty()
            val preview = engine.appPrefs.backAnimation && !searchOpen && (current?.dialogOpen != true || leavesSettingsPage)
            events.collect { e ->
                backFromLeft = e.swipeEdge == BackEventCompat.EDGE_LEFT
                if (preview) backAnim.snapTo(BACK_EASING.transform(e.progress))
            }
            back()
            backScope.launch { backAnim.animateTo(0f, tween(engine.appPrefs.backSettleMillis, easing = LinearOutSlowInEasing)) }
        } catch (e: CancellationException) {
            backScope.launch { backAnim.animateTo(0f, spring(dampingRatio = 1f, stiffness = Spring.StiffnessMediumLow)) }
            throw e
        }
    }
    val animatedBack = backAnim.value

    val downloadedTitle = remember(downloadedSeries) {
        downloadedSeries?.let { runCatching { engine.downloads.store.seriesTitle(it) }.getOrNull() ?: it }
    }
    /** The Library tab's sections (the site's library, or downloads) sit in the top bar at its top level. */
    val librarySections = selected == AppTab.LIBRARY && if (libraryDownloaded) downloadedSeries == null && !downloadedPageOpen
    else current == null || current.isAtRoot
    val imeVisible = WindowInsets.isImeVisible
    Scaffold(
        containerColor = c.bg,
        contentWindowInsets = WindowInsets.systemBars,
        topBar = {
            if (!reader) {
                val atRoot = current?.isAtRoot ?: true
                val title = when {
                    siteSettingsPage && selected == AppTab.MORE && morePageOpen ->
                        moreTab.dialogSubpage.ifBlank { "Theme & content filter" }
                    selected == AppTab.MORE && settingsOpen && current == null -> "Settings"
                    selected == AppTab.MORE && downloadsOpen && current == null -> "Downloads"
                    inDownloaded && current == null -> downloadedTitle ?: "Downloaded"
                    current == null -> selected.label
                    else -> current.displayTitle.ifBlank { if (atRoot) selected.label else "" }
                }
                ComixTopBar(
                    title = title,
                    showBack = if (current == null) (selected == AppTab.MORE && (settingsOpen || downloadsOpen)) || (inDownloaded && downloadedSeries != null)
                    else current.canGoBack || (selected == AppTab.MORE && morePageOpen) || (inDownloaded && downloadedPageOpen),
                    showBrand = selected == AppTab.HOME && current != null && atRoot,
                    auth = auth,
                    onBack = { back() },
                    onSearch = if (Site.pathOnly(current?.url).startsWith("/browse")) null else ({ searchOpen = true }),
                    onLogin = { siteAction("openLogin") },
                    onNotifications = { openInMore("/user?tab=notifs") },
                    onProfile = { openInMore("/user") },
                    onShare = current?.takeIf { Site.isTitleUrl(it.url) }?.let { t ->
                        { Downloads.shareText(context, t.displayTitle, listOf(t.displayTitle, t.url).filter { !it.isNullOrBlank() }.joinToString("\n")) }
                    },
                    sections = if (librarySections) listOf("Library", "Downloaded") else null,
                    selectedSection = if (libraryDownloaded) 1 else 0,
                    onSection = { i ->
                        libraryDownloaded = i == 1
                        downloadedSeries = null
                        downloadedPageOpen = false
                    },
                )
            }
        },
        bottomBar = {
            if (!reader && !imeVisible) ComixBottomBar(selected, ::select)
        },
    ) { padding ->
        // Every visited tab stays composed (hidden ones INVISIBLE) so pages survive tab switches.
        // The visible tab is explicitly layered above the hidden ones, and native screens above
        // all of them, so drawing and touch order never depend on the order tabs were visited.
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .imePadding()
                .graphicsLayer {
                    val p = animatedBack
                    if (p > 0f) {
                        val scale = 1f - 0.1f * p
                        scaleX = scale
                        scaleY = scale
                        translationX = (if (backFromLeft) 1f else -1f) * 16.dp.toPx() * p
                        shape = RoundedCornerShape(24.dp * p)
                        clip = true
                    }
                },
        ) {
            visited.filter { it != AppTab.MORE }.forEach { t ->
                val visible = t == selected && current != null && current === webTab(t)
                WebTabView(webTab(t), engine, Modifier.fillMaxSize().zIndex(if (visible) 1f else 0f), visible = visible, onDownloads = { openDownloaded() })
            }
            if (downloadedTab.isCreated || downloadedPageOpen) {
                val visible = inDownloaded && downloadedPageOpen
                WebTabView(downloadedTab, engine, Modifier.fillMaxSize().zIndex(if (visible) 1f else 0f), visible = visible, onDownloads = { downloadedPageOpen = false })
            }
            if (inDownloaded && !downloadedPageOpen) Box(Modifier.fillMaxSize().zIndex(2f)) {
                DownloadedScreen(
                    downloads = engine.downloads,
                    seriesSlug = downloadedSeries,
                    onSeries = { downloadedSeries = it },
                    onRead = { openFromDownloaded(it.path, it.seriesSlug) },
                    onOpenSeriesPage = { openFromDownloaded("/title/$it", it) },
                    onDownloadsPage = { openDownloads() },
                )
            }
            if (AppTab.MORE in visited && (moreTab.isCreated || morePageOpen)) {
                val visible = selected == AppTab.MORE && morePageOpen
                WebTabView(moreTab, engine, Modifier.fillMaxSize().zIndex(if (visible) 1f else 0f), visible = visible, onDownloads = { openDownloaded() })
                // Covers the page underneath until the settings page is up, so it never flashes by.
                if (visible && siteSettingsPage && !siteSettingsShown) {
                    Box(Modifier.fillMaxSize().zIndex(1.5f).background(c.bg), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(24.dp), color = c.accent, strokeWidth = 2.dp)
                    }
                }
            }
            if (selected == AppTab.MORE && !morePageOpen) Box(Modifier.fillMaxSize().zIndex(2f)) {
                if (downloadsOpen) {
                    DownloadsScreen(downloads = engine.downloads, onOpenDownloaded = { openDownloaded() })
                } else if (settingsOpen) {
                    var cacheSize by remember { mutableStateOf<String?>(null) }
                    var clearing by remember { mutableStateOf(false) }
                    val scope = rememberCoroutineScope()
                    LaunchedEffect(Unit) { cacheSize = Formatter.formatShortFileSize(context, engine.cacheBytes()) }
                    SettingsScreen(
                        reader = engine.reader,
                        app = engine.appPrefs,
                        domain = engine.domain,
                        adBlockOn = engine.adBlockEnabled,
                        blockedCount = engine.blockedCount,
                        cacheSize = cacheSize,
                        clearing = clearing,
                        onPagePrefsChanged = engine::pushPagePrefs,
                        onMobileOptimizations = engine::setMobileOptimizations,
                        onAdBlock = engine::updateAdBlock,
                        onDomain = engine::switchDomain,
                        onClearCache = {
                            clearing = true
                            scope.launch {
                                val freed = engine.clearCache()
                                val msg = if (freed < 1024) "Cache cleared, nothing to free"
                                else "Cache cleared, ${Formatter.formatShortFileSize(context, freed)} freed"
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                cacheSize = Formatter.formatShortFileSize(context, engine.cacheBytes())
                                clearing = false
                            }
                        },
                    )
                } else {
                    MoreScreen(
                        auth = auth,
                        onOpen = ::openInMore,
                        onLogin = { siteAction("openLogin") },
                        onRegister = { siteAction("openRegister") },
                        onSignOut = { siteAction("signOut") },
                        onSiteSettings = ::openSiteSettings,
                        onSettings = { settingsOpen = true },
                        onDownloads = { openDownloads() },
                        scroll = moreScroll,
                    )
                }
            }
            if (selected.needsLogin && auth == AuthState.LOGGED_OUT && !inDownloaded) Box(Modifier.fillMaxSize().zIndex(2f)) {
                val from = selected
                LoginPrompt(
                    tab = from,
                    onLogin = { returnAfterLogin = from; siteAction("openLogin") },
                    onRegister = { returnAfterLogin = from; siteAction("openRegister") },
                )
            }
        }
    }

    engine.downloadPrompt?.let { link ->
        DownloadPromptDialog(
            engine.downloads, link,
            onRead = { engine.downloadPrompt = null; openDownloaded(link.seriesSlug) },
            onOpenDownloads = { engine.downloadPrompt = null; openDownloads() },
            onDismiss = { engine.downloadPrompt = null },
        )
    }

    engine.seriesDownload?.let { offer ->
        SeriesDownloadSheet(
            offer,
            onStart = { groups, startNumber, limit ->
                engine.seriesDownload = null
                engine.downloads.startWalk(offer.slug, offer.title, offer.cover, groups, startNumber, limit)
                Toast.makeText(context, "Downloading ${offer.title.ifBlank { "chapters" }}. Progress is in More → Downloads.", Toast.LENGTH_LONG).show()
            },
            onDismiss = { engine.seriesDownload = null },
        )
    }

    if (searchOpen) {
        Box(Modifier.fillMaxSize().zIndex(3f).background(c.bg)) {
            SearchOverlay(
                recent = engine.recentSearches,
                onSubmit = { q ->
                    engine.recentSearches.add(q)
                    val path = if (q.isBlank()) "/browse" else "/browse?q=" + URLEncoder.encode(q, "UTF-8")
                    if (AppTab.BROWSE !in visited) visited += AppTab.BROWSE
                    selected = AppTab.BROWSE
                    webTab(AppTab.BROWSE).open(path)
                    searchOpen = false
                },
                onClose = { searchOpen = false },
            )
        }
    }
}

private fun listSaver() = androidx.compose.runtime.saveable.listSaver<androidx.compose.runtime.snapshots.SnapshotStateList<AppTab>, String>(
    save = { list -> list.map { it.name } },
    restore = { names -> mutableStateListOf(*names.mapNotNull { n -> AppTab.entries.firstOrNull { it.name == n } }.toTypedArray()) },
)

/** Reader pages: hide the status and navigation bars (swipe to peek) and keep the screen awake. */
@Composable
private fun ReaderWindowEffects(immersive: Boolean, keepScreenOn: Boolean) {
    val view = LocalView.current
    val window = (view.context as? Activity)?.window ?: return
    DisposableEffect(immersive) {
        val controller = WindowCompat.getInsetsController(window, view)
        if (immersive) {
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        }
        onDispose { if (immersive) controller.show(WindowInsetsCompat.Type.systemBars()) }
    }
    DisposableEffect(keepScreenOn) {
        if (keepScreenOn) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { if (keepScreenOn) window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
}
