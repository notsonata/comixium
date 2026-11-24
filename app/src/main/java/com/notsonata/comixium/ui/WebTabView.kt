package com.notsonata.comixium.ui

import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.notsonata.comixium.ui.theme.LocalComixColors
import com.notsonata.comixium.web.Site
import com.notsonata.comixium.web.WebEngine
import com.notsonata.comixium.web.WebError
import com.notsonata.comixium.web.WebTab

/** Hosts one tab's WebView, with a thin loading bar and a native error screen on top. */
@Composable
fun WebTabView(tab: WebTab, engine: WebEngine, modifier: Modifier = Modifier, visible: Boolean = true) {
    val context = LocalContext.current
    val colors = LocalComixColors.current
    // Hidden tabs stay composed (so their pages and scroll positions survive) but don't draw.
    Box(modifier.then(if (visible) Modifier.background(colors.bg) else Modifier)) {
        AndroidView(
            factory = {
                tab.attach(context)
                (tab.container.parent as? ViewGroup)?.removeView(tab.container)
                tab.container
            },
            update = {
                it.setColorSchemeColors(colors.accent.toArgb())
                it.setProgressBackgroundColorSchemeColor(colors.surface2.toArgb())
                tab.setBackgroundColor(colors.bg.toArgb())
                it.visibility = if (visible) android.view.View.VISIBLE else android.view.View.INVISIBLE
                tab.setShown(visible)
            },
            modifier = Modifier.fillMaxSize(),
        )
        if (!visible) return@Box
        val showProgress = tab.loading && tab.progress in 1..99
        val progress by animateFloatAsState(if (showProgress) tab.progress / 100f else 1f, tween(220), label = "load-progress")
        val progressAlpha by animateFloatAsState(if (showProgress) 1f else 0f, tween(if (showProgress) 90 else 260), label = "load-progress-alpha")
        if (progressAlpha > 0f) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(2.dp).align(Alignment.TopCenter).alpha(progressAlpha),
                color = colors.accent,
                trackColor = Color.Transparent,
                drawStopIndicator = {},
            )
        }
        tab.error?.let { err ->
            ErrorPane(
                error = err,
                online = engine.isOnline(),
                onRetry = tab::reload,
                otherDomain = Site.DOMAINS.firstOrNull { it != Site.hostOf(err.url) },
                onTryDomain = { d -> tab.loadUrl(Site.swapDomain(err.url, d)) },
            )
        }
    }
}

@Composable
internal fun ErrorPane(
    error: WebError,
    online: Boolean,
    onRetry: () -> Unit,
    otherDomain: String?,
    onTryDomain: (String) -> Unit,
) {
    val colors = LocalComixColors.current
    val host = Site.hostOf(error.url) ?: "the site"
    Column(
        Modifier.fillMaxSize().background(colors.bg).padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            if (online) "Can't reach $host" else "You're offline",
            style = MaterialTheme.typography.titleLarge,
            color = colors.textEmphasis,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            if (online) {
                if (error.isHttp) "The site answered with an error (${error.code}). It may be down for a moment."
                else error.description.ifBlank { "The connection failed." }
            } else {
                "Check your connection and try again."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = colors.text2,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 360.dp),
        )
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = onRetry,
                shape = RoundedCornerShape(6.dp),
                colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.accentInk),
            ) { Text("Retry") }
            if (online && otherDomain != null) {
                OutlinedButton(onClick = { onTryDomain(otherDomain) }, shape = RoundedCornerShape(6.dp)) {
                    Text("Try $otherDomain", color = colors.text)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "Error ${error.code}",
            style = MaterialTheme.typography.labelSmall,
            color = colors.text3,
        )
    }
}
