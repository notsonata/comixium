package com.notsonata.comixium.web

/** Parses the colour formats the site's CSS variables use into ARGB ints. */
object CssColor {
    private val RGB = Regex("^rgba?\\(\\s*([\\d.]+)[\\s,]+([\\d.]+)[\\s,]+([\\d.]+)(?:\\s*[,/]\\s*([\\d.]+%?))?\\s*\\)$")

    fun parse(value: String?): Int? {
        val v = value?.trim()?.lowercase() ?: return null
        if (v.startsWith("#")) return parseHex(v.substring(1))
        RGB.matchEntire(v)?.let { m ->
            val (r, g, b) = (1..3).map { m.groupValues[it].toFloat().toInt().coerceIn(0, 255) }
            val aRaw = m.groupValues[4]
            val a = when {
                aRaw.isEmpty() -> 255
                aRaw.endsWith("%") -> (aRaw.dropLast(1).toFloat() * 2.55f).toInt()
                else -> (aRaw.toFloat() * 255f).toInt()
            }.coerceIn(0, 255)
            return argb(a, r, g, b)
        }
        return null
    }

    private fun parseHex(h: String): Int? {
        if (!h.all { it in '0'..'9' || it in 'a'..'f' }) return null
        return when (h.length) {
            3 -> argb(255, dup(h[0]), dup(h[1]), dup(h[2]))
            4 -> argb(dup(h[3]), dup(h[0]), dup(h[1]), dup(h[2]))
            6 -> argb(255, h.substring(0, 2).toInt(16), h.substring(2, 4).toInt(16), h.substring(4, 6).toInt(16))
            8 -> argb(h.substring(6, 8).toInt(16), h.substring(0, 2).toInt(16), h.substring(2, 4).toInt(16), h.substring(4, 6).toInt(16))
            else -> null
        }
    }

    private fun dup(c: Char) = "$c$c".toInt(16)

    private fun argb(a: Int, r: Int, g: Int, b: Int) = (a shl 24) or (r shl 16) or (g shl 8) or b
}
