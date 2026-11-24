package com.notsonata.comixium.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.notsonata.comixium.ui.icons.Lucide
import com.notsonata.comixium.ui.theme.LocalComixColors
import com.notsonata.comixium.web.AppPrefs
import com.notsonata.comixium.web.ReaderPrefs

/**
 * The app's own settings, one level below More: reader, tapping, volume keys, controls, home page,
 * look and feel, network and storage. The site's settings (theme, content filter) stay on More.
 */
@Composable
fun SettingsScreen(
    reader: ReaderPrefs,
    app: AppPrefs,
    domain: String,
    adBlockOn: Boolean,
    blockedCount: Int,
    /** Current cache size, already formatted ("12 MB"), or null while it's being measured. */
    cacheSize: String?,
    clearing: Boolean,
    onPagePrefsChanged: () -> Unit,
    onMobileOptimizations: (Boolean) -> Unit,
    onAdBlock: (Boolean) -> Unit,
    onDomain: (String) -> Unit,
    onClearCache: () -> Unit,
) {
    val c = LocalComixColors.current
    Column(
        Modifier.fillMaxSize().background(c.bg).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Section("READER") {
            ToggleRow(
                Lucide.Expand, "Full screen", subtitle = "Hide the status and navigation bars while reading",
                checked = reader.immersive, onChange = { reader.updateImmersive(it); onPagePrefsChanged() },
            )
            ToggleRow(
                Lucide.Sun, "Keep screen on", subtitle = "While a chapter is open",
                checked = reader.keepScreenOn, onChange = { reader.updateKeepScreenOn(it); onPagePrefsChanged() },
            )
            ToggleRow(
                Lucide.ZoomIn, "Pinch to zoom", subtitle = "On chapter pages",
                checked = reader.pinchZoom, onChange = { reader.updatePinchZoom(it); onPagePrefsChanged() },
            )
            ToggleRow(
                Lucide.Download, "Long-press image menu", subtitle = "Save, share or copy a page",
                checked = reader.longPressMenu, onChange = { reader.updateLongPressMenu(it); onPagePrefsChanged() },
            )
        }
        Section("TAPPING") {
            ToggleRow(
                Lucide.Hand, "Tap to turn pages", subtitle = "Paged mode: tap the edges",
                checked = reader.tapPaged, onChange = { reader.updateTapPaged(it); onPagePrefsChanged() },
            )
            ToggleRow(
                Lucide.Hand, "Tap to scroll", subtitle = "Long strip: tap the edges to move a screen",
                checked = reader.tapEdges, onChange = { reader.updateTapEdges(it); onPagePrefsChanged() },
            )
            Choice("Tap zones", null, listOf("sides" to "Left & right", "vertical" to "Top & bottom"), reader.tapLayout) {
                reader.updateTapLayout(it); onPagePrefsChanged()
            }
            Choice("Tap zone size", null, listOf("small" to "Small", "medium" to "Medium", "large" to "Large"), reader.tapZone) {
                reader.updateTapZone(it); onPagePrefsChanged()
            }
            ToggleRow(
                Lucide.Refresh, "Invert tap zones", subtitle = "Left / top goes forward",
                checked = reader.invertTaps, onChange = { reader.updateInvertTaps(it); onPagePrefsChanged() },
            )
            Choice(
                "Show controls with", "A tap in the middle of the page",
                listOf("double" to "Double tap", "single" to "Single tap", "off" to "Off"), reader.controlsTap,
            ) { reader.updateControlsTap(it); onPagePrefsChanged() }
        }
        Section("VOLUME KEYS") {
            ToggleRow(
                Lucide.Volume, "Volume keys turn pages", subtitle = "Uses your reader scroll distance",
                checked = reader.volumeKeys, onChange = { reader.updateVolumeKeys(it); onPagePrefsChanged() },
            )
            ToggleRow(
                Lucide.Refresh, "Invert volume keys", subtitle = "Volume up goes forward",
                checked = reader.invertVolume, onChange = { reader.updateInvertVolume(it); onPagePrefsChanged() },
            )
        }
        Section("READER CONTROLS") {
            ReaderControlsSettings(reader, onChanged = onPagePrefsChanged)
        }
        Section("HOME PAGE") {
            ToggleRow(
                Lucide.Bell, "Announcements",
                subtitle = if (app.homeAnnouncements) "Tap ✕ on one to dismiss it" else "Hidden. Turning on brings back dismissed ones",
                checked = app.homeAnnouncements, onChange = { app.updateHomeAnnouncements(it); onPagePrefsChanged() },
            )
            ToggleRow(
                Lucide.Share, "Share bar", subtitle = "The row of share buttons on the home page",
                checked = app.homeShareBar, onChange = { app.updateHomeShareBar(it); onPagePrefsChanged() },
            )
        }
        Section("LOOK & FEEL") {
            ToggleRow(
                Lucide.Hand, "Mobile optimizations",
                subtitle = if (app.mobileOptimizations) "Touch-friendly fixes to the site's layout" else "Off: the site's own mobile layout",
                checked = app.mobileOptimizations, onChange = onMobileOptimizations,
            )
            ToggleRow(
                Lucide.Undo, "Back animation",
                subtitle = if (app.backAnimation) "The page shrinks and follows your back swipe" else "Off: Back changes the page at once",
                checked = app.backAnimation, onChange = app::updateBackAnimation,
            )
            Choice(
                "Back animation speed", "How quickly the page settles after a back swipe",
                listOf("fast" to "Fast", "normal" to "Normal", "slow" to "Slow"), app.backSpeed,
                enabled = app.backAnimation, onSelect = app::updateBackSpeed,
            )
        }
        Section("NETWORK") {
            ToggleRow(
                Lucide.Shield, "Block ads",
                subtitle = if (adBlockOn) "$blockedCount blocked this session" else "Off: the site's popunders and banners will show",
                checked = adBlockOn, onChange = onAdBlock,
            )
            DomainRow(domain, onDomain)
        }
        Section("STORAGE") {
            val size = when {
                clearing -> "Clearing…"
                cacheSize == null -> "Measuring…"
                else -> "$cacheSize used"
            }
            LinkRow(Lucide.Trash, "Clear cache", subtitle = "$size. Keeps your login.", chevron = false) {
                if (!clearing) onClearCache()
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}
