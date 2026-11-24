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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.notsonata.comixium.ui.icons.Lucide
import com.notsonata.comixium.ui.theme.LocalComixColors
import com.notsonata.comixium.ui.theme.Poppins
import com.notsonata.comixium.web.Site

/** Building blocks shared by the More and Settings screens. */

@Composable
internal fun Section(label: String, content: @Composable ColumnScope.() -> Unit) {
    val c = LocalComixColors.current
    SectionLabel(label, Modifier.padding(start = 4.dp, top = 16.dp, bottom = 8.dp))
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.surface), content = content)
}

@Composable
internal fun LinkRow(
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
internal fun DomainRow(domain: String, onDomain: (String) -> Unit) {
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
internal fun IconBadge(icon: ImageVector, tint: Color) {
    val c = LocalComixColors.current
    Box(Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(c.surface2), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(16.dp))
    }
}
