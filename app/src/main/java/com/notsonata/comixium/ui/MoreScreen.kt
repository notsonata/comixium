package com.notsonata.comixium.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.notsonata.comixium.BuildConfig
import com.notsonata.comixium.ui.icons.Lucide
import com.notsonata.comixium.ui.theme.LocalComixColors
import com.notsonata.comixium.ui.theme.Poppins
import com.notsonata.comixium.web.AuthState
import com.notsonata.comixium.web.ReaderPrefs
import com.notsonata.comixium.web.Site

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
    domain: String,
    adBlockOn: Boolean,
    blockedCount: Int,
    onOpen: (String) -> Unit,
    onLogin: () -> Unit,
    onRegister: () -> Unit,
    onSignOut: () -> Unit,
    onSiteSettings: () -> Unit,
    onAdBlock: (Boolean) -> Unit,
    onDomain: (String) -> Unit,
    reader: ReaderPrefs,
    onClearCache: () -> Unit,
) {
    val c = LocalComixColors.current
    Column(
        Modifier.fillMaxSize().background(c.bg).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Section("ACCOUNT") {
            if (auth == AuthState.LOGGED_IN) {
                Row(Lucide.User, "Profile") { onOpen("/user") }
                Row(Lucide.Bell, "Notifications") { onOpen("/user?tab=notifs") }
                Row(Lucide.Layers, "My collections") { onOpen("/user?tab=collections") }
                Row(Lucide.Archive, "Backup") { onOpen("/user?tab=backup") }
                Row(Lucide.Settings, "Account settings") { onOpen("/user?tab=settings") }
                Row(Lucide.LogOut, "Sign out", danger = true, chevron = false, onClick = onSignOut)
            } else {
                Row(Lucide.LogIn, "Log in", subtitle = "Sync your library and reading progress", accent = true, onClick = onLogin)
                Row(Lucide.UserPlus, "Create an account", onClick = onRegister)
            }
        }
        Section("BROWSE") {
            BROWSE_SHORTCUTS.forEach { (label, icon, path) -> Row(icon, label) { onOpen(path) } }
        }
        Section("READER") {
            ToggleRow(
                Lucide.Expand, "Full screen",
                subtitle = "Hide the status and navigation bars while reading",
                checked = reader.immersive, onChange = reader::updateImmersive,
            )
            ToggleRow(
                Lucide.Volume, "Volume keys turn pages",
                subtitle = "Down = next, up = previous; uses your reader scroll distance",
                checked = reader.volumeKeys, onChange = reader::updateVolumeKeys,
            )
            ToggleRow(
                Lucide.Sun, "Keep screen on",
                subtitle = "While a chapter is open",
                checked = reader.keepScreenOn, onChange = reader::updateKeepScreenOn,
            )
            ToggleRow(
                Lucide.Hand, "Tap edges to scroll",
                subtitle = "Long strip: tap the left or right edge to move a screen",
                checked = reader.tapEdges, onChange = reader::updateTapEdges,
            )
            ToggleRow(
                Lucide.ZoomIn, "Pinch to zoom",
                subtitle = "On chapter pages",
                checked = reader.pinchZoom, onChange = reader::updatePinchZoom,
            )
        }
        Section("READER CONTROLS") {
            ReaderControlsSettings(reader, onChanged = {})
        }
        Section("SETTINGS") {
            Row(Lucide.Palette, "Theme & content filter", subtitle = "The site's own settings", onClick = onSiteSettings)
            ToggleRow(
                Lucide.Shield, "Block ads",
                subtitle = if (adBlockOn) "$blockedCount blocked this session" else "Off: the site's popunders and banners will show",
                checked = adBlockOn, onChange = onAdBlock,
            )
            DomainRow(domain, onDomain)
            Row(Lucide.Trash, "Clear cache", subtitle = "Frees space. Keeps your login.", chevron = false, onClick = onClearCache)
        }
        Section("ABOUT") {
            Row(Lucide.Info, "Comixium ${BuildConfig.VERSION_NAME}", subtitle = "An app for comix.ws. Not affiliated with the site.", chevron = false) {}
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun Section(label: String, content: @Composable ColumnScope.() -> Unit) {
    val c = LocalComixColors.current
    SectionLabel(label, Modifier.padding(start = 4.dp, top = 16.dp, bottom = 8.dp))
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.surface), content = content)
}

@Composable
private fun Row(
    icon: ImageVector,
    label: String,
    subtitle: String? = null,
    accent: Boolean = false,
    danger: Boolean = false,
    chevron: Boolean = true,
    onClick: () -> Unit,
) {
    val c = LocalComixColors.current
    val tint = when {
        danger -> c.danger
        accent -> c.accent
        else -> c.text2
    }
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 52.dp).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(icon, tint)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                label,
                style = TextStyle(fontFamily = Poppins, fontSize = 14.sp, fontWeight = FontWeight.Medium),
                color = if (danger) c.danger else if (accent) c.accent else c.textEmphasis,
            )
            if (subtitle != null) {
                Text(subtitle, style = TextStyle(fontFamily = Poppins, fontSize = 12.sp), color = c.text3)
            }
        }
        if (chevron) Icon(Lucide.ChevronRight, null, tint = c.text4, modifier = Modifier.size(16.dp))
    }
}

@Composable
internal fun ToggleRow(icon: ImageVector, label: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val c = LocalComixColors.current
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.heightIn(min = 52.dp).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(icon, c.text2)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = TextStyle(fontFamily = Poppins, fontSize = 14.sp, fontWeight = FontWeight.Medium), color = c.textEmphasis)
            Text(subtitle, style = TextStyle(fontFamily = Poppins, fontSize = 12.sp), color = c.text3)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = c.accentInk,
                checkedTrackColor = c.accent,
                uncheckedThumbColor = c.text3,
                uncheckedTrackColor = c.surface2,
                uncheckedBorderColor = c.surface3,
            ),
        )
    }
}

@Composable
private fun DomainRow(domain: String, onDomain: (String) -> Unit) {
    val c = LocalComixColors.current
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(Lucide.Globe, c.text2)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("Domain", style = TextStyle(fontFamily = Poppins, fontSize = 14.sp, fontWeight = FontWeight.Medium), color = c.textEmphasis)
            Text("Logins are separate per domain", style = TextStyle(fontFamily = Poppins, fontSize = 12.sp), color = c.text3)
        }
        Row(
            Modifier.clip(RoundedCornerShape(6.dp)).background(c.bg2).padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Site.DOMAINS.forEach { d ->
                val on = d == domain
                Box(
                    Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (on) c.surface3 else c.bg2)
                        .clickable { if (!on) onDomain(d) }
                        .padding(PaddingValues(horizontal = 10.dp, vertical = 6.dp)),
                ) {
                    Text(
                        d,
                        style = TextStyle(fontFamily = Poppins, fontSize = 12.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal),
                        color = if (on) c.textEmphasis else c.text3,
                    )
                }
            }
        }
    }
}

@Composable
private fun IconBadge(icon: ImageVector, tint: androidx.compose.ui.graphics.Color) {
    val c = LocalComixColors.current
    Box(Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(c.surface2), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(16.dp))
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
