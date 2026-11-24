package com.notsonata.comixium.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.notsonata.comixium.ui.icons.Lucide
import com.notsonata.comixium.ui.theme.JetBrainsMono
import com.notsonata.comixium.ui.theme.LocalComixColors
import com.notsonata.comixium.ui.theme.Poppins
import com.notsonata.comixium.web.RecentSearches

/** Native search: a field styled like the site's header search, with recent searches below. */
@Composable
fun SearchOverlay(recent: RecentSearches, onSubmit: (String) -> Unit, onClose: () -> Unit) {
    val c = LocalComixColors.current
    val field = rememberTextFieldState()
    val query = field.text.toString()
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    Column(Modifier.fillMaxSize().background(c.bg).windowInsetsPadding(WindowInsets.statusBars).imePadding()) {
        Row(Modifier.fillMaxWidth().height(56.dp).padding(end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            BarIcon(Lucide.ArrowLeft, "Close search", onClose)
            Row(
                Modifier
                    .weight(1f)
                    .height(40.dp)
                    .background(c.surface, RoundedCornerShape(6.dp))
                    .padding(start = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Lucide.Search, null, tint = c.text3, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) {
                        Text("Search any title...", style = TextStyle(fontFamily = Poppins, fontSize = 14.sp), color = c.text3)
                    }
                    BasicTextField(
                        state = field,
                        lineLimits = TextFieldLineLimits.SingleLine,
                        textStyle = TextStyle(fontFamily = Poppins, fontSize = 14.sp, color = c.textEmphasis),
                        cursorBrush = SolidColor(c.accent),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        onKeyboardAction = { if (query.isNotBlank()) onSubmit(query.trim()) },
                        modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    )
                }
                if (query.isNotEmpty()) {
                    IconButton(onClick = { field.clearText() }, modifier = Modifier.size(36.dp)) {
                        Icon(Lucide.X, "Clear", tint = c.text3, modifier = Modifier.size(16.dp))
                    }
                } else {
                    Spacer(Modifier.width(12.dp))
                }
            }
        }
        LazyColumn(Modifier.fillMaxSize()) {
            if (recent.items.isNotEmpty()) {
                item {
                    Row(
                        Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SectionLabel("RECENT", Modifier.weight(1f))
                        Text(
                            "Clear",
                            style = TextStyle(fontFamily = Poppins, fontSize = 12.sp),
                            color = c.text3,
                            modifier = Modifier.clickable { recent.clear() }.padding(8.dp),
                        )
                    }
                }
                items(recent.items.toList(), key = { it }) { q ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onSubmit(q) }.padding(start = 16.dp, end = 4.dp).height(48.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Lucide.Clock, null, tint = c.text3, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(14.dp))
                        Text(
                            q,
                            style = TextStyle(fontFamily = Poppins, fontSize = 14.sp),
                            color = c.text,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { recent.remove(q) }) {
                            Icon(Lucide.X, "Remove", tint = c.text4, modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }
            item {
                Row(
                    Modifier.fillMaxWidth().clickable { onSubmit("") }.padding(horizontal = 16.dp).height(52.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Lucide.Compass, null, tint = c.accent, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(14.dp))
                    Text("Browse all titles", style = TextStyle(fontFamily = Poppins, fontSize = 14.sp), color = c.accent, modifier = Modifier.weight(1f))
                    Icon(Lucide.ChevronRight, null, tint = c.accent, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

/** Small uppercase monospace label, like the site's ".mono" section heads ("BROWSE", "THEME"). */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    val c = LocalComixColors.current
    Text(
        text,
        style = TextStyle(fontFamily = JetBrainsMono, fontSize = 11.sp, letterSpacing = 1.2.sp),
        color = c.text3,
        modifier = modifier,
    )
}
