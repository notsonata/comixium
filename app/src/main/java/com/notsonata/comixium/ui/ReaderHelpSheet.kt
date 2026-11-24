package com.notsonata.comixium.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.notsonata.comixium.ui.icons.Lucide
import com.notsonata.comixium.ui.theme.JetBrainsMono
import com.notsonata.comixium.ui.theme.LocalComixColors
import com.notsonata.comixium.ui.theme.Poppins
import com.notsonata.comixium.web.ReaderPrefs

/** Opened from the reader's help button, in place of the site's keyboard-shortcut list. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderHelpSheet(reader: ReaderPrefs, onChanged: () -> Unit, onDismiss: () -> Unit) {
    val c = LocalComixColors.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = c.surface,
        contentColor = c.textEmphasis,
    ) {
        TouchControls(reader, onChanged, Modifier.navigationBarsPadding())
    }
}

/** Gesture list styled like the site's shortcut dialog (label left, key chips right), then options. */
@Composable
fun TouchControls(reader: ReaderPrefs, onChanged: () -> Unit, modifier: Modifier = Modifier) {
    val c = LocalComixColors.current
    Column(modifier.verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
        Text(
            "Touch controls",
            style = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.SemiBold, fontSize = 20.sp),
            color = c.textEmphasis,
            modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 12.dp),
        )
        Gesture("Show / hide controls", "double tap")
        Gesture("Previous / next page", "tap left", "tap right", note = "Paged mode. Sides swap in right-to-left")
        if (reader.tapEdges) Gesture("Scroll up / down", "tap left", "tap right", note = "Long strip, one screen per tap")
        Gesture("Turn pages / scroll", "swipe")
        if (reader.pinchZoom) Gesture("Zoom in", "pinch")
        Gesture("Jump through the chapter", "progress bar", note = "The thin bar on the left edge: tap or drag it")
        if (reader.volumeKeys) Gesture("Next / previous", "vol −", "vol +")
        Gesture("Save or share a page", "hold")
        Gesture("Back to the series", "back")

        SectionLabel("OPTIONS", Modifier.padding(start = 20.dp, top = 20.dp, bottom = 8.dp))
        Column(Modifier.padding(horizontal = 12.dp).clip(RoundedCornerShape(12.dp)).background(c.bg2)) {
            ToggleRow(
                Lucide.Hand, "Tap edges to scroll",
                subtitle = "Long strip: tap the left or right edge to move a screen",
                checked = reader.tapEdges, onChange = { reader.updateTapEdges(it); onChanged() },
            )
            ToggleRow(
                Lucide.ZoomIn, "Pinch to zoom", subtitle = "On chapter pages",
                checked = reader.pinchZoom, onChange = { reader.updatePinchZoom(it); onChanged() },
            )
            ToggleRow(
                Lucide.Volume, "Volume keys turn pages", subtitle = "Down = next, up = previous",
                checked = reader.volumeKeys, onChange = reader::updateVolumeKeys,
            )
            ToggleRow(
                Lucide.Expand, "Full screen", subtitle = "Hide the status and navigation bars",
                checked = reader.immersive, onChange = reader::updateImmersive,
            )
            ToggleRow(
                Lucide.Sun, "Keep screen on", subtitle = "While a chapter is open",
                checked = reader.keepScreenOn, onChange = reader::updateKeepScreenOn,
            )
        }
    }
}

@Composable
private fun Gesture(label: String, vararg chips: String, note: String? = null) {
    val c = LocalComixColors.current
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = 20.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = TextStyle(fontFamily = Poppins, fontSize = 15.sp), color = c.text)
            if (note != null) Text(note, style = TextStyle(fontFamily = Poppins, fontSize = 12.sp), color = c.text3)
        }
        Spacer(Modifier.width(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            chips.forEach { chip ->
                Text(
                    chip,
                    style = TextStyle(fontFamily = JetBrainsMono, fontSize = 12.sp),
                    color = c.textEmphasis,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(c.surface3)
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                )
            }
        }
    }
    Spacer(Modifier.height(2.dp))
}
