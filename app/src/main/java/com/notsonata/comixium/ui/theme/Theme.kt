package com.notsonata.comixium.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

@Composable
fun ComixiumTheme(colors: ComixColors = ComixColors(), content: @Composable () -> Unit) {
    val base = if (colors.isLight) lightColorScheme() else darkColorScheme()
    val scheme = base.copy(
        primary = colors.accent,
        onPrimary = colors.accentInk,
        primaryContainer = colors.accent2,
        onPrimaryContainer = colors.accentInk,
        secondary = colors.accent2,
        onSecondary = colors.accentInk,
        secondaryContainer = colors.surface3,
        onSecondaryContainer = colors.textEmphasis,
        background = colors.bg,
        onBackground = colors.text,
        surface = colors.bg,
        onSurface = colors.textEmphasis,
        surfaceVariant = colors.surface,
        onSurfaceVariant = colors.text2,
        surfaceContainerLowest = colors.bg,
        surfaceContainerLow = colors.bg2,
        surfaceContainer = colors.surface,
        surfaceContainerHigh = colors.surface2,
        surfaceContainerHighest = colors.surface3,
        outline = colors.text4,
        outlineVariant = colors.surface3,
        error = colors.danger,
    )
    CompositionLocalProvider(LocalComixColors provides colors) {
        MaterialTheme(colorScheme = scheme, typography = ComixTypography, content = content)
    }
}
