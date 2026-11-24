package com.notsonata.comixium.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.notsonata.comixium.ui.theme.LocalComixColors
import com.notsonata.comixium.ui.theme.Poppins

@Composable
fun ComixBottomBar(selected: AppTab, onSelect: (AppTab) -> Unit) {
    val c = LocalComixColors.current
    Column {
        HorizontalDivider(thickness = 1.dp, color = c.surface)
        NavigationBar(
            containerColor = c.bg,
            tonalElevation = 0.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            AppTab.entries.forEach { tab ->
                val isSel = tab == selected
                NavigationBarItem(
                    selected = isSel,
                    onClick = { onSelect(tab) },
                    icon = { Icon(tab.icon, contentDescription = tab.label, modifier = Modifier.size(22.dp)) },
                    label = {
                        Text(
                            tab.label,
                            style = TextStyle(
                                fontFamily = Poppins,
                                fontSize = 11.sp,
                                fontWeight = if (isSel) FontWeight.SemiBold else FontWeight.Medium,
                            ),
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = c.accent,
                        selectedTextColor = c.accent,
                        indicatorColor = c.accent.copy(alpha = 0.14f),
                        unselectedIconColor = c.text3,
                        unselectedTextColor = c.text3,
                    ),
                )
            }
        }
    }
}
