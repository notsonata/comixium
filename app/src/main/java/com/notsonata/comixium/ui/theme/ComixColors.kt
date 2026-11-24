package com.notsonata.comixium.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * The site's CSS design tokens (`--bg`, `--surface`, `--accent`, ...). Defaults are the
 * comix.ws "main" theme; at runtime the values are replaced with whatever theme the
 * site itself is currently using, so native chrome and web content always agree.
 */
@Immutable
data class ComixColors(
    val bg: Color = Color(0xFF22282A),
    val bg2: Color = Color(0xFF262D30),
    val surface: Color = Color(0xFF2A3134),
    val surface2: Color = Color(0xFF323A3E),
    val surface3: Color = Color(0xFF3A4248),
    val textEmphasis: Color = Color(0xFFE8F3F6),
    val text: Color = Color(0xFFC8D4D8),
    val text2: Color = Color(0xFF97A5A9),
    val text3: Color = Color(0xFF69787D),
    val text4: Color = Color(0xFF45555A),
    val accent: Color = Color(0xFF66E8FA),
    val accent2: Color = Color(0xFF4ED0E2),
    val accentInk: Color = Color(0xFF082A30),
    val danger: Color = Color(0xFFEF4444),
    val isLight: Boolean = false,
)

val LocalComixColors = staticCompositionLocalOf { ComixColors() }
