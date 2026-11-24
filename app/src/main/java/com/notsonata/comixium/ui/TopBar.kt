package com.notsonata.comixium.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.notsonata.comixium.ui.icons.ComixiumWordmark
import com.notsonata.comixium.ui.icons.Lucide
import com.notsonata.comixium.ui.theme.LocalComixColors
import com.notsonata.comixium.ui.theme.Poppins
import com.notsonata.comixium.web.AuthState

/**
 * Native replacement for the site's sticky header: same height (56dp), colours and icon style,
 * but with Android back navigation and actions wired to native screens.
 */
@Composable
fun ComixTopBar(
    title: String,
    showBack: Boolean,
    showBrand: Boolean,
    auth: AuthState,
    onBack: () -> Unit,
    /** Null hides the search icon (e.g. on Browse, which has its own search box). */
    onSearch: (() -> Unit)?,
    onLogin: () -> Unit,
    onNotifications: () -> Unit,
    onProfile: () -> Unit,
    /** Shown on series pages. */
    onShare: (() -> Unit)? = null,
) {
    val c = LocalComixColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .background(c.bg)
            .windowInsetsPadding(WindowInsets.statusBars)
            .height(56.dp)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showBack) {
            BarIcon(Lucide.ArrowLeft, "Back", onBack)
        } else {
            Spacer(Modifier.width(12.dp))
        }
        // Title changes crossfade quickly instead of popping.
        AnimatedContent(
            targetState = if (showBrand && !showBack) null else title,
            transitionSpec = { fadeIn(tween(140, delayMillis = 40)) togetherWith fadeOut(tween(90)) },
            contentAlignment = Alignment.CenterStart,
            modifier = Modifier.weight(1f),
            label = "top-bar-title",
        ) { shownTitle ->
            if (shownTitle == null) {
                ComixiumWordmark(height = 24.dp)
            } else {
                Text(
                    shownTitle,
                    style = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.SemiBold, fontSize = 17.sp),
                    color = c.textEmphasis,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = if (showBack) 4.dp else 0.dp),
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(0.dp), verticalAlignment = Alignment.CenterVertically) {
            if (onSearch != null) BarIcon(Lucide.Search, "Search", onSearch)
            if (onShare != null) BarIcon(Lucide.Share, "Share", onShare)
            when (auth) {
                AuthState.LOGGED_IN -> {
                    BarIcon(Lucide.Bell, "Notifications", onNotifications)
                    BarIcon(Lucide.User, "Profile", onProfile)
                }
                else -> {
                    Spacer(Modifier.width(4.dp))
                    Button(
                        onClick = onLogin,
                        shape = RoundedCornerShape(6.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = c.accent, contentColor = c.accentInk),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp),
                        modifier = Modifier.height(36.dp),
                    ) {
                        Text("Log in", style = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.SemiBold, fontSize = 13.sp))
                    }
                    Spacer(Modifier.width(8.dp))
                }
            }
        }
    }
}

@Composable
fun BarIcon(icon: ImageVector, description: String, onClick: () -> Unit) {
    val c = LocalComixColors.current
    IconButton(onClick = onClick) {
        Icon(icon, contentDescription = description, tint = c.text2, modifier = Modifier.size(20.dp))
    }
}
