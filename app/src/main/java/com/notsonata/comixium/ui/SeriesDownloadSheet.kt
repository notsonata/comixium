package com.notsonata.comixium.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.notsonata.comixium.ui.theme.LocalComixColors
import com.notsonata.comixium.ui.theme.Poppins
import com.notsonata.comixium.web.WebEngine

private enum class Start { UNREAD, FIRST, FROM }

/**
 * What to download from a series: where to start (unread, the first chapter, or a chapter number),
 * how many, and which scanlation groups. Each chapter number is downloaded once, from the first
 * ticked group that has it, so a 30-chapter series listed 90 times (three groups) gives 30 chapters.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SeriesDownloadSheet(
    offer: WebEngine.SeriesDownloadOffer,
    onStart: (groups: List<String>, startNumber: String?, limit: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val c = LocalComixColors.current
    var start by remember { mutableStateOf(if (offer.current.isNotBlank()) Start.UNREAD else Start.FIRST) }
    var fromNumber by remember { mutableStateOf("") }
    /** 0 = all. */
    var limit by remember { mutableStateOf(0) }
    var customLimit by remember { mutableStateOf("") }
    val picked = remember { mutableStateListOf<String>() }
    val label = TextStyle(fontFamily = Poppins, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    val body = TextStyle(fontFamily = Poppins, fontSize = 14.sp)
    val small = TextStyle(fontFamily = Poppins, fontSize = 12.sp)

    val startNumber: String? = when (start) {
        Start.UNREAD -> offer.current
        Start.FIRST -> null
        Start.FROM -> fromNumber.trim().takeIf { it.toDoubleOrNull() != null }
    }
    val amount = if (limit == -1) customLimit.toIntOrNull()?.coerceIn(1, 5000) else limit
    val ready = (start != Start.FROM || startNumber != null) && amount != null

    @Composable
    fun Choice(on: Boolean, title: String, subtitle: String?, onClick: () -> Unit, trailing: @Composable (() -> Unit)? = null) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick)
                .semantics { role = Role.RadioButton }.heightIn(min = 44.dp).padding(end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = on, onClick = onClick, colors = RadioButtonDefaults.colors(selectedColor = c.accent, unselectedColor = c.text3))
            Column(Modifier.weight(1f)) {
                Text(title, style = body, color = c.textEmphasis)
                if (subtitle != null) Text(subtitle, style = small, color = c.text3)
            }
            trailing?.invoke()
        }
    }

    @Composable
    fun NumberBox(value: String, placeholder: String, onValue: (String) -> Unit) {
        Box(
            Modifier.width(72.dp).height(36.dp).clip(RoundedCornerShape(8.dp)).background(c.surface2)
                .border(1.dp, c.surface3, RoundedCornerShape(8.dp)).padding(horizontal = 10.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (value.isEmpty()) Text(placeholder, style = body, color = c.text4)
            BasicTextField(
                value = value,
                onValueChange = { v -> onValue(v.filter { it.isDigit() || it == '.' }.take(7)) },
                singleLine = true,
                textStyle = body.copy(color = c.textEmphasis),
                cursorBrush = SolidColor(c.accent),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Download chapters") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (offer.title.isNotBlank()) Text(offer.title, style = small, color = c.text3, modifier = Modifier.padding(bottom = 8.dp))
                Text("START", style = label, color = c.text3, modifier = Modifier.padding(top = 4.dp, bottom = 2.dp))
                if (offer.current.isNotBlank()) {
                    Choice(start == Start.UNREAD, "Unread", "From Ch.${offer.current}, where you left off", { start = Start.UNREAD })
                }
                Choice(start == Start.FIRST, "All", "From the first chapter", { start = Start.FIRST })
                Choice(start == Start.FROM, "From a chapter", null, { start = Start.FROM }) {
                    NumberBox(fromNumber, "Ch.", { fromNumber = it; start = Start.FROM })
                }

                Text("HOW MANY", style = label, color = c.text3, modifier = Modifier.padding(top = 12.dp, bottom = 6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(0 to "Up to the latest", 5 to "5", 10 to "10", 25 to "25").forEach { (n, text) ->
                        Pill(text, limit == n) { limit = n }
                    }
                    Pill(if (limit == -1 && customLimit.isNotEmpty()) customLimit else "Other", limit == -1) { limit = -1 }
                }
                if (limit == -1) {
                    Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        NumberBox(customLimit, "15", { customLimit = it.filter(Char::isDigit) })
                        Spacer(Modifier.width(8.dp))
                        Text("chapters", style = body, color = c.text2)
                    }
                }

                if (offer.groups.isNotEmpty()) {
                    Text("SCANLATORS", style = label, color = c.text3, modifier = Modifier.padding(top = 14.dp, bottom = 2.dp))
                    Text(
                        if (picked.isEmpty()) "Any group. Tick groups in the order you prefer them."
                        else "Each chapter comes from the first ticked group that has it; chapters none of them have are skipped.",
                        style = small, color = c.text3, modifier = Modifier.padding(bottom = 4.dp),
                    )
                    offer.groups.forEach { g ->
                        val rank = picked.indexOf(g)
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                                .clickable { if (rank >= 0) picked.remove(g) else picked.add(g) }
                                .semantics { role = Role.Checkbox }
                                .heightIn(min = 40.dp).padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                Modifier.size(22.dp).clip(RoundedCornerShape(6.dp))
                                    .background(if (rank >= 0) c.accent else c.surface2)
                                    .border(1.dp, if (rank >= 0) c.accent else c.surface3, RoundedCornerShape(6.dp)),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (rank >= 0) Text("${rank + 1}", style = small.copy(fontWeight = FontWeight.Bold), color = c.accentInk)
                            }
                            Spacer(Modifier.width(12.dp))
                            Text(g, style = body, color = c.textEmphasis, modifier = Modifier.weight(1f))
                        }
                    }
                }
                Text(
                    "Chapters are prepared one at a time while Comixium is open; their pages keep downloading as you read.",
                    style = small, color = c.text3, modifier = Modifier.padding(top = 12.dp),
                )
            }
        },
        confirmButton = {
            TextButton(enabled = ready, onClick = { onStart(picked.toList(), startNumber, amount ?: 0) }) {
                Text("Download", color = if (ready) c.accent else c.text4)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = c.text2) } },
        containerColor = c.surface,
        titleContentColor = c.textEmphasis,
        textContentColor = c.text2,
    )
}

@Composable
private fun Pill(text: String, on: Boolean, onClick: () -> Unit) {
    val c = LocalComixColors.current
    Box(
        Modifier.height(34.dp).clip(RoundedCornerShape(999.dp))
            .background(if (on) c.accent else c.surface2)
            .clickable(onClick = onClick)
            .semantics { role = Role.RadioButton }
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = TextStyle(fontFamily = Poppins, fontSize = 13.sp, fontWeight = FontWeight.Medium), color = if (on) c.accentInk else c.text)
    }
}
