package com.notsonata.comixium.ui

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewClientCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.notsonata.comixium.offline.ChapterDownloads
import com.notsonata.comixium.offline.ChapterLink
import com.notsonata.comixium.offline.DownloadStore
import com.notsonata.comixium.offline.OfflinePage
import com.notsonata.comixium.ui.icons.Lucide
import com.notsonata.comixium.ui.theme.LocalComixColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.FileInputStream

/**
 * Reads a downloaded chapter with no connection: its images in a long strip (see [OfflinePage]),
 * with a bar on tap for going back and to the previous or next downloaded chapter.
 */
@Composable
fun OfflineReader(downloads: ChapterDownloads, link: ChapterLink, onOpen: (ChapterLink) -> Unit, onClose: () -> Unit) {
    val c = LocalComixColors.current
    val store = downloads.store
    var chapter by remember(link) { mutableStateOf<DownloadStore.Chapter?>(null) }
    var siblings by remember(link) { mutableStateOf<List<DownloadStore.Chapter>>(emptyList()) }
    var seriesTitle by remember(link) { mutableStateOf("") }
    var loaded by remember(link) { mutableStateOf(false) }
    LaunchedEffect(link) {
        withContext(Dispatchers.IO) {
            chapter = runCatching { store.chapter(link) }.getOrNull()
            // Oldest first, so "next" is the next number up.
            siblings = runCatching { store.chapters(link.seriesSlug).filter { it.complete }.reversed() }.getOrDefault(emptyList())
            seriesTitle = store.seriesTitle(link.seriesSlug).orEmpty()
        }
        loaded = true
    }
    val index = siblings.indexOfFirst { it.link == link }
    val prev = siblings.getOrNull(index - 1)?.takeIf { index > 0 }
    val next = siblings.getOrNull(index + 1)?.takeIf { index >= 0 }
    var page by remember(link) { mutableIntStateOf(0) }
    var total by remember(link) { mutableIntStateOf(0) }
    var barShown by remember(link) { mutableStateOf(true) }
    var shownAt by remember(link) { mutableStateOf(System.currentTimeMillis()) }
    val currentNext by rememberUpdatedState(next)

    Box(Modifier.fillMaxSize().background(c.bg)) {
        val ch = chapter
        if (loaded && ch != null) {
            val html = remember(ch, next) {
                val files = store.pageFiles(link).map { it.name }
                OfflinePage.html(
                    files = files,
                    sizes = ch.sizes,
                    background = cssColor(c.bg),
                    startPage = ch.lastPage,
                    endText = "End of ${ch.label}",
                    nextLabel = next?.label,
                )
            }
            key(link) { AndroidView(
                factory = { ctx -> offlineWebView(ctx, downloads, link, html, c.bg) { msg ->
                    when (msg.optString("t")) {
                        "page" -> {
                            page = msg.optInt("page")
                            total = msg.optInt("total")
                            if (page > 0) downloads.saveLastPage(link, page)
                            if (barShown && System.currentTimeMillis() - shownAt > 1500) barShown = false
                        }
                        "tap" -> { barShown = !barShown; shownAt = System.currentTimeMillis() }
                        "go" -> currentNext?.let { onOpen(it.link) }
                    }
                } },
                onRelease = { it.destroy() },
                modifier = Modifier.fillMaxSize(),
            ) }
        } else if (loaded) {
            Text("This chapter isn't downloaded any more.", color = c.text2, modifier = Modifier.align(Alignment.Center).padding(24.dp))
        }

        AnimatedVisibility(barShown || !loaded || ch == null, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.TopCenter)) {
            Row(
                Modifier.fillMaxWidth().background(c.bg.copy(alpha = 0.94f)).windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onClose) { Icon(Lucide.ArrowLeft, "Back", tint = c.textEmphasis) }
                Column(Modifier.weight(1f)) {
                    Text(seriesTitle.ifBlank { "Downloaded chapter" }, color = c.textEmphasis, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val sub = listOfNotNull(ch?.label, if (total > 0) "$page / $total" else null, "Offline").joinToString(" · ")
                    Text(sub, color = c.text2, fontSize = 12.sp, maxLines = 1)
                }
            }
        }
        AnimatedVisibility(barShown && ch != null, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.BottomCenter)) {
            Row(
                Modifier.fillMaxWidth().background(c.bg.copy(alpha = 0.94f)).windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = { prev?.let { onOpen(it.link) } }, enabled = prev != null) {
                    Text(prev?.let { "‹ ${it.label}" } ?: "No earlier download", color = if (prev != null) c.text else c.text4)
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { next?.let { onOpen(it.link) } }, enabled = next != null) {
                    Text(next?.let { "${it.label} ›" } ?: "No later download", color = if (next != null) c.accent else c.text4)
                }
            }
        }
    }
    // The bar starts shown, then gets out of the way.
    LaunchedEffect(link, barShown) {
        if (barShown) { delay(3000); if (System.currentTimeMillis() - shownAt >= 2900) barShown = false }
    }
}

private fun cssColor(color: Color): String = "#%06X".format(0xFFFFFF and color.toArgb())

@SuppressLint("SetJavaScriptEnabled")
private fun offlineWebView(
    ctx: android.content.Context,
    downloads: ChapterDownloads,
    link: ChapterLink,
    html: String,
    bg: Color,
    onMessage: (JSONObject) -> Unit,
): WebView {
    val store = downloads.store
    val loader = WebViewAssetLoader.Builder()
        .addPathHandler(OfflinePage.PREFIX) { path ->
            val (slug, id, file) = OfflinePage.parsePath(path) ?: return@addPathHandler null
            if (file == "index.html") return@addPathHandler WebResourceResponse("text/html", "utf-8", ByteArrayInputStream(html.toByteArray()))
            // Only this chapter's page files, by their exact names.
            val link = store.chapters(slug).firstOrNull { it.link.chapterId == id }?.link ?: return@addPathHandler null
            val f = store.pageFiles(link).firstOrNull { it.name == file } ?: return@addPathHandler null
            WebResourceResponse(mimeFor(file), null, FileInputStream(f))
        }
        .build()
    val wv = WebView(ctx)
    wv.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    wv.setBackgroundColor(bg.toArgb())
    wv.settings.apply {
        javaScriptEnabled = true
        domStorageEnabled = false
        allowFileAccess = false
        allowContentAccess = false
        blockNetworkLoads = false // the page itself comes from the asset loader, never the network
        setSupportZoom(true)
        builtInZoomControls = true
        displayZoomControls = false
        useWideViewPort = true
        loadWithOverviewMode = false
        cacheMode = WebSettings.LOAD_NO_CACHE
        mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
    }
    if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
        WebViewCompat.addWebMessageListener(wv, "ComixiumOffline", setOf(OfflinePage.ORIGIN)) { _, message, _, isMainFrame, _ ->
            if (isMainFrame) message.data?.let { runCatching { JSONObject(it) }.getOrNull() }?.let(onMessage)
        }
    }
    wv.webViewClient = object : WebViewClientCompat() {
        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
            loader.shouldInterceptRequest(request.url)
                // Anything else would need the network: refuse it, so this page works the same offline.
                ?: WebResourceResponse("text/plain", "utf-8", 404, "Not found", emptyMap(), ByteArrayInputStream(ByteArray(0)))

        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = true
    }
    wv.loadUrl(OfflinePage.url(link))
    return wv
}

private fun mimeFor(name: String): String = when (name.substringAfterLast('.').lowercase()) {
    "webp" -> "image/webp"
    "jpg", "jpeg" -> "image/jpeg"
    "png" -> "image/png"
    "gif" -> "image/gif"
    "avif" -> "image/avif"
    else -> "application/octet-stream"
}
