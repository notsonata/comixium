package com.notsonata.comixium.ui.theme

import androidx.compose.ui.graphics.Color
import com.notsonata.comixium.web.CssColor

/** Builds app colours from the CSS variables the page reported, keeping defaults for anything missing. */
fun ComixColors.withSiteTheme(vars: Map<String, String>, isLight: Boolean): ComixColors {
    fun pick(key: String, fallback: Color): Color =
        CssColor.parse(vars[key])?.let { Color(it) }?.takeIf { it.alpha > 0f } ?: fallback
    return copy(
        bg = pick("bg", bg),
        bg2 = pick("bg2", bg2),
        surface = pick("surface", surface),
        surface2 = pick("surface2", surface2),
        surface3 = pick("surface3", surface3),
        textEmphasis = pick("textEmphasis", textEmphasis),
        text = pick("text", text),
        text2 = pick("text2", text2),
        text3 = pick("text3", text3),
        text4 = pick("text4", text4),
        accent = pick("accent", accent),
        accent2 = pick("accent2", accent2),
        accentInk = pick("accentInk", accentInk),
        danger = pick("danger", danger),
        isLight = isLight,
    )
}
