package com.notsonata.comixium.ui

import android.widget.Toast
import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

import androidx.compose.foundation.background
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
                t == AppTab.MORE -> morePageOpen = false
                else -> webTab(t).goRoot()
            }
            return
        }
        selected = t
        if (t !in visited) visited += t
    }

    fun openInMore(path: String) {
        moreTab.open(path)
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
        if (selected == AppTab.MORE && morePageOpen) { morePageOpen = false; return true }
        if (selected != AppTab.HOME) { selected = AppTab.HOME; return true }
        return false
    }

    val canHandleBack = searchOpen || current?.canGoBack == true || current?.dialogOpen == true ||
        (selected == AppTab.MORE && morePageOpen) || selected != AppTab.HOME
    // Predictive back (Android 14+): the page shrinks slightly toward the swipe while the gesture
    // is in progress, then Back runs on release. Older versions just get the Back action.
    var backProgress by remember { mutableFloatStateOf(0f) }
    var backFromLeft by remember { mutableStateOf(true) }
    PredictiveBackHandler(enabled = canHandleBack) { events ->
        try {
            events.collect { e ->
                backProgress = e.progress
                backFromLeft = e.swipeEdge == BackEventCompat.EDGE_LEFT
            }
            back()
        } finally {
            backProgress = 0f
        }
    }
    val animatedBack by animateFloatAsState(backProgress, label = "predictive-back")

    val imeVisible = WindowInsets.isImeVisible
    Scaffold(
        containerColor = c.bg,
        contentWindowInsets = WindowInsets.systemBars,
        topBar = {
            if (!reader) {
                val atRoot = current?.isAtRoot ?: true
                val title = when {
                    current == null -> selected.label
                    else -> current.displayTitle.ifBlank { if (atRoot) selected.label else "" }
                }
                ComixTopBar(
                    title = title,
                    showBack = current != null && (current.canGoBack || (selected == AppTab.MORE && morePageOpen)),
                    showBrand = selected == AppTab.HOME && current != null && atRoot,
                    auth = auth,
                    onBack = { back() },
                    onSearch = { searchOpen = true },
                    onSettings = { siteAction("openSettings") },
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
                        val scale = 1f - 0.08f * p
                        scaleX = scale
                        scaleY = scale
                        translationX = (if (backFromLeft) 1f else -1f) * 24.dp.toPx() * p
                        shape = RoundedCornerShape(28.dp * p)
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
                MoreScreen(
                    auth = auth,
                    domain = engine.domain,
                    adBlockOn = engine.adBlockEnabled,
                    blockedCount = engine.blockedCount,
                    onOpen = ::openInMore,
                    onLogin = { siteAction("openLogin") },
                    onRegister = { siteAction("openRegister") },
                    onSignOut = { siteAction("signOut") },
                    onSiteSettings = { siteAction("openSettings") },
                    onAdBlock = engine::updateAdBlock,
                    onDomain = engine::switchDomain,
                    reader = engine.reader,
                    onClearCache = {
                        engine.clearCache()
                        Toast.makeText(context, "Cache cleared", Toast.LENGTH_SHORT).show()
                    },
                )
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

    if (engine.readerHelpOpen) {
        ReaderHelpSheet(
            reader = engine.reader,
            onChanged = engine::pushReaderPrefs,
            onDismiss = { engine.readerHelpOpen = false },
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
