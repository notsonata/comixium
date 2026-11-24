package com.notsonata.comixium.ui

import android.text.format.Formatter
import android.widget.Toast
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
import androidx.compose.foundation.layout.systemBars
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

/** Android's predictive-back progress curve: responsive at first, then decelerating. */
private val BACK_EASING = CubicBezierEasing(0.1f, 0.1f, 0f, 1f)

/**
 * The app shell: native top bar, bottom tabs, search and More screens around the site's pages.
 * Each bottom tab keeps its own WebView and history; the More tab opens pages on its own stack.
 */

@Composable
fun ComixiumApp(engine: WebEngine) {
    Box(Modifier.fillMaxSize()) { AppContent(engine) }
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
    val moreScroll = rememberScrollState()
    /** Tab to go back to once a login started from its "log in" prompt succeeds. */
    var returnAfterLogin by rememberSaveable { mutableStateOf<AppTab?>(null) }
    val visited = rememberSaveable(saver = listSaver(), init = { mutableStateListOf(AppTab.HOME) })

    fun webTab(t: AppTab): WebTab = engine.tab(t.name, t.startPath)
    val moreTab = engine.tab(MORE_PAGE, null)
    val auth = engine.auth

    val current: WebTab? = when {
        selected == AppTab.MORE -> if (morePageOpen) moreTab else null
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
                t == AppTab.MORE -> { morePageOpen = false; settingsOpen = false }
                else -> webTab(t).goRoot()
            }
            return
        }
        selected = t
        if (t !in visited) visited += t
    }

    /** The More page was opened only to show the site's settings sheet; leave it when the sheet closes. */
    var moreSheet by rememberSaveable { mutableStateOf(false) }
    var moreSheetShown by remember { mutableStateOf(false) }
    LaunchedEffect(moreTab.dialogOpen, moreSheet) {
        if (!moreSheet) return@LaunchedEffect
        if (moreTab.dialogOpen) moreSheetShown = true
        else if (moreSheetShown) { moreSheetShown = false; moreSheet = false; morePageOpen = false }
    }

    fun openInMore(path: String) {
        moreTab.open(path)
        morePageOpen = true
        if (AppTab.MORE !in visited) visited += AppTab.MORE
        selected = AppTab.MORE
    }

    /** The site's theme and content filter sheet, opened from More over a page in the More stack. */
    fun openSiteSettings() {
        if (moreTab.isCreated && !moreTab.isReader) moreTab.siteAction("openSettings")
        else moreTab.openWithAction("/", "openSettings")
        moreSheet = true
        moreSheetShown = false
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
        if (current?.handleBack() == true) return true
        if (selected == AppTab.MORE && morePageOpen) { morePageOpen = false; moreSheet = false; return true }
        if (selected == AppTab.MORE && settingsOpen) { settingsOpen = false; return true }
        if (selected != AppTab.HOME) { selected = AppTab.HOME; return true }
        return false
    }

    val canHandleBack = searchOpen || current?.canGoBack == true || current?.dialogOpen == true ||
        (selected == AppTab.MORE && (morePageOpen || settingsOpen)) || selected != AppTab.HOME
    // Predictive back (Android 14+): the page follows the finger 1:1 (shrinks a little and slides
    // toward the swipe), then settles on release (speed from settings) or springs back on cancel.
    // With the back animation off there's no preview: Back just changes the page on release.
    val backAnim = remember { Animatable(0f) }
    var backFromLeft by remember { mutableStateOf(true) }
    val backScope = rememberCoroutineScope()
    PredictiveBackHandler(enabled = canHandleBack) { events ->
        try {
            // Closing a popup or search isn't a page change: no preview for those.
            val preview = engine.appPrefs.backAnimation && !searchOpen && current?.dialogOpen != true
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

    val imeVisible = WindowInsets.isImeVisible
    Scaffold(
        containerColor = c.bg,
        contentWindowInsets = WindowInsets.systemBars,
        topBar = {
            if (!reader) {
                val atRoot = current?.isAtRoot ?: true
                val title = when {
                    moreSheet && selected == AppTab.MORE && morePageOpen -> "Theme & content filter"
                    selected == AppTab.MORE && settingsOpen && current == null -> "Settings"
                    current == null -> selected.label
                    else -> current.displayTitle.ifBlank { if (atRoot) selected.label else "" }
                }
                ComixTopBar(
                    title = title,
                    showBack = if (current == null) selected == AppTab.MORE && settingsOpen
                    else current.canGoBack || (selected == AppTab.MORE && morePageOpen),
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
                val visible = t == selected && current != null
                WebTabView(webTab(t), engine, Modifier.fillMaxSize().zIndex(if (visible) 1f else 0f), visible = visible)
            }
            if (AppTab.MORE in visited && (moreTab.isCreated || morePageOpen)) {
                val visible = selected == AppTab.MORE && morePageOpen
                WebTabView(moreTab, engine, Modifier.fillMaxSize().zIndex(if (visible) 1f else 0f), visible = visible)
            }
            if (selected == AppTab.MORE && !morePageOpen) Box(Modifier.fillMaxSize().zIndex(2f)) {
                if (settingsOpen) {
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
                        scroll = moreScroll,
                    )
                }
            }
            if (selected.needsLogin && auth == AuthState.LOGGED_OUT) Box(Modifier.fillMaxSize().zIndex(2f)) {
                val from = selected
                LoginPrompt(
                    tab = from,
                    onLogin = { returnAfterLogin = from; siteAction("openLogin") },
                    onRegister = { returnAfterLogin = from; siteAction("openRegister") },
                )
            }
        }
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
