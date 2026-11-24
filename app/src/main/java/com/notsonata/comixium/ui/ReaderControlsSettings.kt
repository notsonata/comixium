package com.notsonata.comixium.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.notsonata.comixium.ui.theme.LocalComixColors
import com.notsonata.comixium.ui.theme.Poppins
import com.notsonata.comixium.web.ReaderPrefs

/** Layout, position and size of the reader's floating controls. [onChanged] pushes them to the open chapter. */
@Composable
fun ReaderControlsSettings(reader: ReaderPrefs, onChanged: () -> Unit) {
    Column(Modifier.padding(vertical = 4.dp)) {
        Choice("Style", "Rail: buttons in a column. Bar: full width.", listOf("rail" to "Rail", "bar" to "Bar"), reader.controlsLayout) {
            reader.updateControlsLayout(it); onChanged()
        }
        ShownControls(reader, onChanged)
        Choice("Position", null, listOf("top" to "Top", "bottom" to "Bottom"), reader.controlsY) {
            reader.updateControlsY(it); onChanged()
        }
        Choice(
            "Side", if (reader.controlsLayout == "bar") "The bar always spans the full width" else null,
            listOf("left" to "Left", "center" to "Center", "right" to "Right"), reader.controlsX,
            enabled = reader.controlsLayout != "bar",
        ) { reader.updateControlsX(it); onChanged() }
        Choice("Size", null, listOf("normal" to "Normal", "large" to "Large"), reader.controlsSize) {
            reader.updateControlsSize(it); onChanged()
        }
    }
}

/** Tick the reader control buttons to show; the settings button always stays. */
@Composable
private fun ShownControls(reader: ReaderPrefs, onChanged: () -> Unit) {
    val c = LocalComixColors.current
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
        Text("Shown", style = TextStyle(fontFamily = Poppins, fontSize = 14.sp, fontWeight = FontWeight.Medium), color = c.textEmphasis)
        Text("The settings button always stays", style = TextStyle(fontFamily = Poppins, fontSize = 12.sp), color = c.text3)
        Spacer(Modifier.height(8.dp))
        ReaderPrefs.CONTROL_ITEMS.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { (id, label) ->
                    val on = reader.isControlShown(id)
                    Row(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (on) c.accent.copy(alpha = 0.12f) else c.bg)
                            .clickable { reader.setControlShown(id, !on); onChanged() }
                            .padding(horizontal = 10.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = on, onCheckedChange = null, modifier = Modifier.size(18.dp),
                            colors = CheckboxDefaults.colors(checkedColor = c.accent, checkmarkColor = c.accentInk, uncheckedColor = c.text3),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(label, style = TextStyle(fontFamily = Poppins, fontSize = 13.sp), color = if (on) c.accent else c.text2)
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
internal fun Choice(
    label: String,
    note: String?,
    options: List<Pair<String, String>>,
    selected: String,
    enabled: Boolean = true,
    onSelect: (String) -> Unit,
) {
    val c = LocalComixColors.current
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp).alpha(if (enabled) 1f else 0.45f)) {
        Text(label, style = TextStyle(fontFamily = Poppins, fontSize = 14.sp, fontWeight = FontWeight.Medium), color = c.textEmphasis)
        if (note != null) Text(note, style = TextStyle(fontFamily = Poppins, fontSize = 12.sp), color = c.text3)
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(c.bg).padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            options.forEach { (value, text) ->
                val on = value == selected
                Box(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (on) c.surface3 else c.bg)
                        .clickable(enabled = enabled && !on) { onSelect(value) }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text,
                        style = TextStyle(fontFamily = Poppins, fontSize = 13.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal),
                        color = if (on) c.textEmphasis else c.text3,
                    )
                }
            }
        }
    }
}
