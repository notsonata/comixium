package com.notsonata.comixium.ui

import android.graphics.BitmapFactory
import android.text.format.Formatter
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.notsonata.comixium.offline.ChapterDownloads
import com.notsonata.comixium.offline.ChapterLink
import com.notsonata.comixium.offline.DownloadStore
import com.notsonata.comixium.offline.SeriesWalk
import com.notsonata.comixium.ui.icons.Lucide
import com.notsonata.comixium.ui.theme.LocalComixColors
import com.notsonata.comixium.ui.theme.Poppins
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val Body = TextStyle(fontFamily = Poppins, fontSize = 12.sp)
private val RowTitle = TextStyle(fontFamily = Poppins, fontSize = 14.sp, fontWeight = FontWeight.Medium)

/**
 * More → Downloads: what's downloading (chapters and series downloads), the download settings and
 * the space used. The downloaded chapters themselves are in Library → Downloaded.
 */
@Composable
fun DownloadsScreen(downloads: ChapterDownloads, onOpenDownloaded: () -> Unit) {
    val c = LocalComixColors.current
    val context = LocalContext.current
    val prefs = downloads.prefs
    val version = downloads.version
    val summary by produceState<Pair<Int, Long>?>(null, version) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val chapters = downloads.store.allChapters().count { it.complete }
                chapters to downloads.store.bytes()
            }.getOrDefault(0 to 0L)
        }
    }
    var confirmDeleteAll by remember { mutableStateOf(false) }
    fun size(b: Long) = Formatter.formatShortFileSize(context, b)

    LazyColumn(Modifier.fillMaxSize().background(c.bg).padding(horizontal = 16.dp)) {
        val waiting = downloads.waitingFor
        val busy = downloads.queue.isNotEmpty() || downloads.walks.isNotEmpty()
        if (busy && waiting != null) item {
            Row(
                Modifier.fillMaxWidth().padding(top = 16.dp).clip(RoundedCornerShape(12.dp)).background(c.surface).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Lucide.WifiOff, null, tint = c.text2, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Text(waiting, style = Body.copy(fontSize = 13.sp), color = c.text2)
            }
        }
        if (downloads.walks.isNotEmpty()) {
            item { SectionLabel("SERIES", Modifier.padding(start = 4.dp, top = 16.dp, bottom = 8.dp)) }
            items(downloads.walks, key = { "w" + it.id }) { w -> WalkRow(downloads, w) }
        }
        if (downloads.queue.isNotEmpty()) {
            item { SectionLabel("CHAPTERS", Modifier.padding(start = 4.dp, top = 16.dp, bottom = 8.dp)) }
            items(downloads.queue, key = { "q" + it.request.link.chapterId }) { item -> QueueRow(downloads, item, downloads.store) }
        }
        if (busy) item {
            Text(
                "Keep Comixium open while chapters are prepared. Their pages keep downloading while you read.",
                style = Body, color = c.text3, modifier = Modifier.padding(start = 4.dp, top = 8.dp),
            )
        } else item {
            Column(Modifier.fillMaxWidth().padding(top = 32.dp, bottom = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)).background(c.surface), contentAlignment = Alignment.Center) {
                    Icon(Lucide.Download, null, tint = c.accent, modifier = Modifier.size(24.dp))
                }
                Spacer(Modifier.height(12.dp))
                Text("Nothing downloading", style = RowTitle.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold), color = c.textEmphasis)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Download a chapter with the button beside it, or many from a series page's Download button.",
                    style = Body.copy(fontSize = 13.sp), color = c.text2, textAlign = TextAlign.Center,
                )
            }
        }
        item {
            Section("DOWNLOADED") {
                val s = summary
                LinkRow(
                    Lucide.Bookmark, "Downloaded chapters",
                    subtitle = if (s == null) "In Library" else "${s.first} chapter${if (s.first == 1) "" else "s"} · ${size(s.second)} · in Library",
                    onClick = onOpenDownloaded,
                )
            }
        }
        item {
            Section("SETTINGS") {
                ToggleRow(Lucide.Message, "Download comments", "Read a chapter's comments offline too", prefs.comments, prefs::updateComments)
                ToggleRow(Lucide.Trash, "Delete after reading", "Once a downloaded chapter is read to the end and closed", prefs.deleteAfterRead, prefs::updateDeleteAfterRead)
                ToggleRow(Lucide.WifiOff, "Only on Wi-Fi", "Wait for Wi-Fi before downloading", prefs.wifiOnly, prefs::updateWifiOnly)
            }
        }
        item {
            Section("STORAGE") {
                LinkRow(
                    Lucide.Trash, "Delete all downloads",
                    subtitle = summary?.let { "${size(it.second)} on this phone" },
                    danger = true, chevron = false,
                ) { confirmDeleteAll = true }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
    if (confirmDeleteAll) {
        ConfirmDelete("Delete every downloaded chapter?", onConfirm = { confirmDeleteAll = false; downloads.deleteAll() }, onDismiss = { confirmDeleteAll = false })
    }
}

@Composable
private fun WalkRow(downloads: ChapterDownloads, w: SeriesWalk) {
    val c = LocalComixColors.current
    val what = when {
        w.problem.isNotEmpty() -> w.problem
        w.limit > 0 -> "${w.saved} of ${w.limit} chapters"
        else -> "${w.saved} chapter${if (w.saved == 1) "" else "s"} so far, up to the latest"
    }
    val now = when (val s = w.step) {
        SeriesWalk.Step.First -> "Finding the first chapter"
        is SeriesWalk.Step.Find -> "Looking up Ch.${s.number}"
        is SeriesWalk.Step.Peek -> "Checking what comes next"
        is SeriesWalk.Step.Wait -> "Preparing Ch.${s.link.number}"
        SeriesWalk.Step.Done -> ""
    }
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(12.dp)).background(c.surface)
            .padding(start = 14.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(w.seriesTitle.ifBlank { w.seriesSlug }, style = RowTitle, color = c.textEmphasis, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val groups = if (w.groups.isEmpty()) "" else " · " + w.groups.joinToString(", ")
            Text(what + groups, style = Body, color = if (w.problem.isNotEmpty()) c.danger else c.text3, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (w.problem.isEmpty() && now.isNotEmpty()) Text(now, style = Body, color = c.text3)
        }
        if (w.problem.isNotEmpty() && !w.isDone) TextButton(onClick = { downloads.retryWalk(w) }) { Text("Retry", color = c.accent) }
        IconButton(onClick = { downloads.cancelWalk(w) }) { Icon(Lucide.X, "Stop", tint = c.text3, modifier = Modifier.size(18.dp)) }
    }
}

@Composable
private fun QueueRow(downloads: ChapterDownloads, item: ChapterDownloads.Item, store: DownloadStore) {
    val c = LocalComixColors.current
    val r = item.request
    val title = r.seriesTitle.ifBlank { store.seriesTitle(r.link.seriesSlug).orEmpty() }.ifBlank { r.link.seriesSlug }
    val label = r.label.ifBlank { "Ch.${r.link.number}" }
    val state = item.state
    Column(Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(12.dp)).background(c.surface).padding(start = 14.dp, end = 4.dp, top = 8.dp, bottom = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("$title · $label", style = RowTitle, color = c.textEmphasis, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    when (state) {
                        ChapterDownloads.State.Queued -> listOf("Waiting", r.group).filter { it.isNotBlank() }.joinToString(" · ")
                        is ChapterDownloads.State.Preparing -> if (state.total > 0) "Finding pages · ${state.found} of ${state.total}" else "Opening the chapter"
                        is ChapterDownloads.State.Fetching -> "Downloading · ${state.done} of ${state.total} pages"
                        is ChapterDownloads.State.Failed -> state.why
                    },
                    style = Body,
                    color = if (state is ChapterDownloads.State.Failed) c.danger else c.text3,
                )
            }
            if (state is ChapterDownloads.State.Failed) {
                TextButton(onClick = { downloads.retry(item) }) { Text("Retry", color = c.accent) }
            }
            IconButton(onClick = { downloads.cancel(item) }) { Icon(Lucide.X, "Cancel", tint = c.text3, modifier = Modifier.size(18.dp)) }
        }
        val progress = when (state) {
            is ChapterDownloads.State.Preparing -> if (state.total > 0) 0.1f * state.found / state.total else 0f
            is ChapterDownloads.State.Fetching -> 0.1f + 0.9f * state.done / state.total.coerceAtLeast(1)
            else -> null
        }
        if (progress != null) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp, end = 10.dp).height(3.dp).clip(RoundedCornerShape(2.dp)),
                color = c.accent, trackColor = c.surface2, drawStopIndicator = {},
            )
        }
    }
}

private class SeriesRow(val series: DownloadStore.Series, val chapters: Int, val unread: Int, val bytes: Long, val cover: ImageBitmap?)

/**
 * Library → Downloaded: downloaded series, and a series' chapters ([seriesSlug]). Chapters open in
 * the site's own reader, from the phone.
 */
@Composable
fun DownloadedScreen(
    downloads: ChapterDownloads,
    seriesSlug: String?,
    onSeries: (String) -> Unit,
    onRead: (ChapterLink) -> Unit,
    onOpenSeriesPage: (String) -> Unit,
    onDownloadsPage: () -> Unit,
) {
    val c = LocalComixColors.current
    val context = LocalContext.current
    val store = downloads.store
    val version = downloads.version
    val series by produceState<List<SeriesRow>?>(null, version) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                store.series().map { s ->
                    val chapters = store.chapters(s.slug).filter { it.complete }
                    SeriesRow(s, chapters.size, chapters.count { !it.read }, store.bytes(s.slug), store.coverFile(s.slug)?.let { thumb(it.path) })
                }.filter { it.chapters > 0 }
            }.getOrDefault(emptyList())
        }
    }
    val chapters by produceState<List<DownloadStore.Chapter>?>(null, version, seriesSlug) {
        value = if (seriesSlug == null) null else withContext(Dispatchers.IO) {
            runCatching { store.chapters(seriesSlug).filter { it.complete } }.getOrDefault(emptyList())
        }
    }
    var confirm by remember { mutableStateOf<Pair<String, () -> Unit>?>(null) }
    fun size(b: Long) = Formatter.formatShortFileSize(context, b)
    val inProgress = downloads.queue.size + downloads.walks.size

    LazyColumn(Modifier.fillMaxSize().background(c.bg).padding(horizontal = 16.dp)) {
        if (inProgress > 0 && seriesSlug == null) item {
            Row(
                Modifier.fillMaxWidth().padding(top = 12.dp).clip(RoundedCornerShape(12.dp)).background(c.surface)
                    .clickable(onClick = onDownloadsPage).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Lucide.Download, null, tint = c.accent, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Text("$inProgress downloading", style = RowTitle.copy(fontSize = 13.sp), color = c.text2, modifier = Modifier.weight(1f))
                Icon(Lucide.ChevronRight, null, tint = c.text4, modifier = Modifier.size(16.dp))
            }
        }
        if (seriesSlug == null) {
            val list = series
            if (list != null && list.isEmpty()) {
                item { EmptyDownloaded() }
            } else if (list != null) {
                item { Spacer(Modifier.height(10.dp)) }
                items(list, key = { "s" + it.series.slug }) { row ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(12.dp)).background(c.surface)
                            .clickable { onSeries(row.series.slug) }.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Cover(row.cover)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(row.series.title, style = RowTitle, color = c.textEmphasis, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            val unread = if (row.unread in 1 until row.chapters) " · ${row.unread} unread" else if (row.unread == 0) " · all read" else ""
                            Text("${row.chapters} chapter${if (row.chapters == 1) "" else "s"}$unread · ${size(row.bytes)}", style = Body, color = c.text3)
                        }
                        Icon(Lucide.ChevronRight, null, tint = c.text4, modifier = Modifier.size(16.dp))
                    }
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        } else {
            val list = chapters
            if (list != null) {
                item {
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { onOpenSeriesPage(seriesSlug) }) { Text("Series page", color = c.accent) }
                        Spacer(Modifier.weight(1f))
                        if (list.isNotEmpty()) {
                            TextButton(onClick = { confirm = "Delete every downloaded chapter of this series?" to { downloads.deleteSeries(seriesSlug) } }) {
                                Text("Delete all", color = c.danger)
                            }
                        }
                    }
                }
                if (list.isEmpty()) item {
                    Text("No chapters of this series are downloaded.", color = c.text2, modifier = Modifier.padding(4.dp))
                }
                items(list, key = { "c" + it.link.chapterId }) { ch ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(12.dp)).background(c.surface)
                            .clickable { onRead(ch.link) }.heightIn(min = 56.dp).padding(start = 14.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(ch.label, style = RowTitle, color = if (ch.read) c.text3 else c.textEmphasis)
                            val parts = listOf(ch.group, "${ch.pages} pages", size(ch.bytes), if (ch.read) "read" else "", if (!ch.recorded) "updating" else "")
                            Text(parts.filter { it.isNotBlank() }.joinToString(" · "), style = Body, color = c.text3, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        IconButton(onClick = { confirm = "Delete ${ch.label}?" to { downloads.delete(ch.link) } }) {
                            Icon(Lucide.Trash, "Delete ${ch.label}", tint = c.text3, modifier = Modifier.size(18.dp))
                        }
                    }
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
    confirm?.let { (question, action) ->
        ConfirmDelete(question, onConfirm = { confirm = null; action() }, onDismiss = { confirm = null })
    }
}

@Composable
private fun ConfirmDelete(question: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val c = LocalComixColors.current
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { Text(question) },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Delete", color = c.danger) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = c.text2) } },
        containerColor = c.surface,
        textContentColor = c.text,
    )
}

@Composable
private fun EmptyDownloaded() {
    val c = LocalComixColors.current
    Column(Modifier.fillMaxWidth().padding(top = 64.dp, start = 16.dp, end = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(56.dp).clip(RoundedCornerShape(14.dp)).background(c.surface), contentAlignment = Alignment.Center) {
            Icon(Lucide.Download, null, tint = c.accent, modifier = Modifier.size(26.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text("No downloads yet", style = TextStyle(fontFamily = Poppins, fontSize = 16.sp, fontWeight = FontWeight.SemiBold), color = c.textEmphasis)
        Spacer(Modifier.height(6.dp))
        Text(
            "Download chapters from a series page: one with the button beside it, or many with the series' Download button. They open here without a connection, in the usual reader.",
            style = TextStyle(fontFamily = Poppins, fontSize = 13.sp), color = c.text2, textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun Cover(cover: ImageBitmap?) {
    val c = LocalComixColors.current
    Box(Modifier.width(44.dp).height(62.dp).clip(RoundedCornerShape(6.dp)).background(c.surface2), contentAlignment = Alignment.Center) {
        if (cover != null) Image(cover, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Icon(Lucide.Bookmark, null, tint = c.text4, modifier = Modifier.size(18.dp))
    }
}

/** A small copy of a cover, for the list. */
private fun thumb(path: String): ImageBitmap? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= 132) sample *= 2
    BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
}.getOrNull()

/** A page's download button on a chapter already downloaded, on its way, or failed: what to do with it. */
@Composable
fun DownloadPromptDialog(
    downloads: ChapterDownloads,
    link: ChapterLink,
    onRead: () -> Unit,
    onOpenDownloads: () -> Unit,
    onDismiss: () -> Unit,
) {
    val c = LocalComixColors.current
    val item = downloads.itemFor(link)
    val done = downloads.isDownloaded(link)
    val label = item?.request?.label?.ifBlank { null } ?: "Ch.${link.number}"
    val state = item?.state
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                when {
                    done -> "$label is downloaded"
                    state is ChapterDownloads.State.Failed -> "$label didn't download"
                    else -> "Downloading $label"
                },
            )
        },
        text = {
            Text(
                when {
                    done -> "It opens from the phone, with or without a connection."
                    state is ChapterDownloads.State.Failed -> state.why
                    state is ChapterDownloads.State.Fetching -> "${state.done} of ${state.total} pages so far."
                    state is ChapterDownloads.State.Preparing -> "Finding its pages. Keep Comixium open for a moment."
                    else -> "Waiting for the chapters before it."
                },
            )
        },
        confirmButton = {
            Row {
                when {
                    done -> {
                        TextButton(onClick = { downloads.delete(link); onDismiss() }) { Text("Delete", color = c.danger) }
                        TextButton(onClick = onRead) { Text("Downloaded", color = c.accent) }
                    }
                    item != null && state is ChapterDownloads.State.Failed -> {
                        TextButton(onClick = { downloads.cancel(item); onDismiss() }) { Text("Remove", color = c.text2) }
                        TextButton(onClick = { downloads.retry(item); onDismiss() }) { Text("Retry", color = c.accent) }
                    }
                    item != null -> {
                        TextButton(onClick = { downloads.cancel(item); onDismiss() }) { Text("Cancel download", color = c.danger) }
                        TextButton(onClick = onOpenDownloads) { Text("Downloads", color = c.accent) }
                    }
                    else -> TextButton(onClick = onDismiss) { Text("OK", color = c.accent) }
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close", color = c.text2) } },
        containerColor = c.surface,
        titleContentColor = c.textEmphasis,
        textContentColor = c.text2,
    )
}
