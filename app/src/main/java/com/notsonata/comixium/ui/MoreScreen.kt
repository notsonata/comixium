package com.notsonata.comixium.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.notsonata.comixium.BuildConfig
import com.notsonata.comixium.ui.icons.Lucide
import com.notsonata.comixium.ui.theme.LocalComixColors
import com.notsonata.comixium.ui.theme.Poppins
import com.notsonata.comixium.web.AuthState

/** Shortcuts from the site's header "Browse" menu, in the same order. */
val BROWSE_SHORTCUTS = listOf(
    Triple("Trending Webtoon", Lucide.TrendingUp, "/browse?types=manhwa,manhua&sort=views_7d:desc"),
    Triple("Trending Manga", Lucide.TrendingUp, "/browse?types=manga&sort=views_7d:desc"),
    Triple("Popular Genres", Lucide.Tag, "/genres"),
    Triple("Popular Groups", Lucide.Users, "/groups/popular"),
    Triple("Top Uploaders", Lucide.Upload, "/uploaders"),
    Triple("Groups", Lucide.Users, "/groups"),
    Triple("Collections", Lucide.Layers, "/collections"),
    Triple("Community", Lucide.Message, "/community"),
)

@Composable
fun MoreScreen(
    auth: AuthState,
    onOpen: (String) -> Unit,
    onLogin: () -> Unit,
    onRegister: () -> Unit,
    onSignOut: () -> Unit,
    onSiteSettings: () -> Unit,
    onSettings: () -> Unit,
    onDownloads: () -> Unit = {},
    scroll: ScrollState = rememberScrollState(),
    version: String = BuildConfig.VERSION_NAME,
) {
    val c = LocalComixColors.current
    Column(
        Modifier.fillMaxSize().background(c.bg).verticalScroll(scroll).padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Section("ACCOUNT") {
            // Notifications sit behind the bell in the top bar; Backup is a tab on the profile page.
            if (auth == AuthState.LOGGED_IN) {
                LinkRow(Lucide.User, "Profile") { onOpen("/user") }
                LinkRow(Lucide.Layers, "My collections") { onOpen("/user?tab=collections") }
                LinkRow(Lucide.Settings, "Account settings") { onOpen("/user?tab=settings") }
                LinkRow(Lucide.LogOut, "Sign out", danger = true, chevron = false, onClick = onSignOut)
            } else {
                LinkRow(Lucide.LogIn, "Log in", subtitle = "Sync your library and reading progress", accent = true, onClick = onLogin)
                LinkRow(Lucide.UserPlus, "Create an account", onClick = onRegister)
            }
        }
        Section("OFFLINE") {
            LinkRow(Lucide.Download, "Downloads", subtitle = "Downloading, and download settings", onClick = onDownloads)
        }
        SectionLabel("BROWSE", Modifier.padding(start = 4.dp, top = 16.dp, bottom = 8.dp))
        BrowseGrid(onOpen)
        Section("SETTINGS") {
            LinkRow(Lucide.Palette, "Theme & content filter", subtitle = "The site's own settings", onClick = onSiteSettings)
            LinkRow(Lucide.Sliders, "App settings", subtitle = "Reader, tapping, controls, ads, storage", onClick = onSettings)
        }
        Section("ABOUT") {
            LinkRow(Lucide.Info, "Comixium $version", subtitle = "An app for comix.ws. Not affiliated with the site.", chevron = false) {}
        }
        Spacer(Modifier.height(24.dp))
    }
}

/** The site's Browse menu as a two-column grid of tiles, so More stays short. */
@Composable
private fun BrowseGrid(onOpen: (String) -> Unit) {
    val c = LocalComixColors.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BROWSE_SHORTCUTS.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pair.forEach { (label, icon, path) ->
                    Row(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(c.surface)
                            .clickable { onOpen(path) }
                            .heightIn(min = 52.dp)
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconBadge(icon, c.text2)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            label,
                            style = TextStyle(fontFamily = Poppins, fontSize = 13.sp, fontWeight = FontWeight.Medium),
                            color = c.textEmphasis,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/** Shown on Library/History when nobody is logged in (the site redirects those pages to home). */
@Composable
fun LoginPrompt(tab: AppTab, onLogin: () -> Unit, onRegister: () -> Unit) {
    val c = LocalComixColors.current
    Column(
        Modifier.fillMaxSize().background(c.bg).padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(56.dp).clip(RoundedCornerShape(14.dp)).background(c.surface), contentAlignment = Alignment.Center) {
            Icon(tab.icon, null, tint = c.accent, modifier = Modifier.size(26.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text(
            if (tab == AppTab.LIBRARY) "Your library lives in your account" else "Your reading history lives in your account",
            style = TextStyle(fontFamily = Poppins, fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
            color = c.textEmphasis,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Log in to see it here, synced with the site.",
            style = TextStyle(fontFamily = Poppins, fontSize = 13.sp),
            color = c.text2,
        )
        Spacer(Modifier.height(20.dp))
        androidx.compose.material3.Button(
            onClick = onLogin,
            shape = RoundedCornerShape(6.dp),
            colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = c.accent, contentColor = c.accentInk),
        ) { Text("Log in", style = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)) }
        Spacer(Modifier.height(8.dp))
        Text(
            "Create an account",
            style = TextStyle(fontFamily = Poppins, fontSize = 13.sp),
            color = c.accent,
            modifier = Modifier.clickable(onClick = onRegister).padding(8.dp),
        )
    }
}
