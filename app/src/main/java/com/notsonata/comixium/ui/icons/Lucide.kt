package com.notsonata.comixium.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Outline icons in the style the site uses (Lucide, ISC licence): 24x24 grid, 2px round strokes.
 * Each icon is a list of SVG path strings; circles and rects are written as paths via helpers.
 */
object Lucide {
    private fun circle(cx: Float, cy: Float, r: Float) =
        "M${cx - r},${cy}a$r,$r 0 1,0 ${2 * r},0a$r,$r 0 1,0 ${-2 * r},0"

    private fun rect(x: Float, y: Float, w: Float, h: Float, rx: Float) =
        "M${x + rx},${y}h${w - 2 * rx}a$rx,$rx 0 0 1 $rx,$rx" +
            "v${h - 2 * rx}a$rx,$rx 0 0 1 ${-rx},$rx" +
            "h${-(w - 2 * rx)}a$rx,$rx 0 0 1 ${-rx},${-rx}" +
            "v${-(h - 2 * rx)}a$rx,$rx 0 0 1 $rx,${-rx}z"

    private fun icon(name: String, vararg paths: String, strokeWidth: Float = 2f): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            paths.forEach {
                addPath(
                    pathData = addPathNodes(it),
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = strokeWidth,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
        }.build()

    val House by lazy {
        icon(
            "house",
            "M15 21v-8a1 1 0 0 0-1-1h-4a1 1 0 0 0-1 1v8",
            "M3 10a2 2 0 0 1 .709-1.528l7-5.999a2 2 0 0 1 2.582 0l7 5.999A2 2 0 0 1 21 10v9a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z",
        )
    }
    val Compass by lazy {
        icon(
            "compass",
            circle(12f, 12f, 10f),
            "m16.24 7.76-1.804 5.411a2 2 0 0 1-1.265 1.265L7.76 16.24l1.804-5.411a2 2 0 0 1 1.265-1.265z",
        )
    }
    val Bookmark by lazy { icon("bookmark", "m19 21-7-4-7 4V5a2 2 0 0 1 2-2h10a2 2 0 0 1 2 2v16z") }
    val History by lazy {
        icon("history", "M3 12a9 9 0 1 0 9-9 9.75 9.75 0 0 0-6.74 2.74L3 8", "M3 3v5h5", "M12 7v5l4 2")
    }
    val Grid by lazy {
        icon("grid", rect(3f, 3f, 7f, 7f, 1f), rect(14f, 3f, 7f, 7f, 1f), rect(14f, 14f, 7f, 7f, 1f), rect(3f, 14f, 7f, 7f, 1f))
    }
    val Search by lazy { icon("search", circle(11f, 11f, 8f), "m21 21-4.3-4.3") }
    val Settings by lazy {
        icon(
            "settings",
            "M12.22 2h-.44a2 2 0 0 0-2 2v.18a2 2 0 0 1-1 1.73l-.43.25a2 2 0 0 1-2 0l-.15-.08a2 2 0 0 0-2.73.73l-.22.38a2 2 0 0 0 .73 2.73l.15.1a2 2 0 0 1 1 1.72v.51a2 2 0 0 1-1 1.74l-.15.09a2 2 0 0 0-.73 2.73l.22.38a2 2 0 0 0 2.73.73l.15-.08a2 2 0 0 1 2 0l.43.25a2 2 0 0 1 1 1.73V20a2 2 0 0 0 2 2h.44a2 2 0 0 0 2-2v-.18a2 2 0 0 1 1-1.73l.43-.25a2 2 0 0 1 2 0l.15.08a2 2 0 0 0 2.73-.73l.22-.39a2 2 0 0 0-.73-2.73l-.15-.08a2 2 0 0 1-1-1.74v-.5a2 2 0 0 1 1-1.74l.15-.09a2 2 0 0 0 .73-2.73l-.22-.38a2 2 0 0 0-2.73-.73l-.15.08a2 2 0 0 1-2 0l-.43-.25a2 2 0 0 1-1-1.73V4a2 2 0 0 0-2-2z",
            circle(12f, 12f, 3f),
        )
    }
    val User by lazy { icon("user", "M19 21v-2a4 4 0 0 0-4-4H9a4 4 0 0 0-4 4v2", circle(12f, 7f, 4f)) }
    val ArrowLeft by lazy { icon("arrow-left", "m12 19-7-7 7-7", "M19 12H5") }
    val X by lazy { icon("x", "M18 6 6 18", "m6 6 12 12") }
    val ChevronRight by lazy { icon("chevron-right", "m9 18 6-6-6-6") }
    val Bell by lazy {
        icon(
            "bell",
            "M10.268 21a2 2 0 0 0 3.464 0",
            "M3.262 15.326A1 1 0 0 0 4 17h16a1 1 0 0 0 .74-1.673C19.41 13.956 18 12.499 18 8A6 6 0 0 0 6 8c0 4.499-1.411 5.956-2.738 7.326",
        )
    }
    val Message by lazy { icon("message-square", "M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z") }
    val TrendingUp by lazy { icon("trending-up", "M22 7 13.5 15.5 8.5 10.5 2 17", "M16 7h6v6") }
    val Tag by lazy {
        icon(
            "tag",
            "M12.586 2.586A2 2 0 0 0 11.172 2H4a2 2 0 0 0-2 2v7.172a2 2 0 0 0 .586 1.414l8.704 8.704a2.426 2.426 0 0 0 3.42 0l6.58-6.58a2.426 2.426 0 0 0 0-3.42z",
            circle(7.5f, 7.5f, 0.5f),
        )
    }
    val Users by lazy {
        icon(
            "users",
            "M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2",
            circle(9f, 7f, 4f),
            "M22 21v-2a4 4 0 0 0-3-3.87",
            "M16 3.13a4 4 0 0 1 0 7.75",
        )
    }
    val Layers by lazy {
        icon(
            "layers",
            "M12.83 2.18a2 2 0 0 0-1.66 0L2.6 6.08a1 1 0 0 0 0 1.83l8.58 3.91a2 2 0 0 0 1.66 0l8.58-3.9a1 1 0 0 0 0-1.83Z",
            "m22 17.65-9.17 4.16a2 2 0 0 1-1.66 0L2 17.65",
            "m22 12.65-9.17 4.16a2 2 0 0 1-1.66 0L2 12.65",
        )
    }
    val Upload by lazy { icon("upload", "M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4", "m17 8-5-5-5 5", "M12 3v12") }
    val Shield by lazy {
        icon(
            "shield",
            "M20 13c0 5-3.5 7.5-7.66 8.95a1 1 0 0 1-.67-.01C7.5 20.5 4 18 4 13V6a1 1 0 0 1 1-1c2 0 4.5-1.2 6.24-2.72a1.17 1.17 0 0 1 1.52 0C14.51 3.81 17 5 19 5a1 1 0 0 1 1 1z",
        )
    }
    val Globe by lazy {
        icon("globe", circle(12f, 12f, 10f), "M12 2a14.5 14.5 0 0 0 0 20 14.5 14.5 0 0 0 0-20", "M2 12h20")
    }
    val Trash by lazy {
        icon("trash", "M3 6h18", "M19 6v14c0 1-1 2-2 2H7c-1 0-2-1-2-2V6", "M8 6V4c0-1 1-2 2-2h4c1 0 2 1 2 2v2")
    }
    val Info by lazy { icon("info", circle(12f, 12f, 10f), "M12 16v-4", "M12 8h.01") }
    val LogIn by lazy { icon("log-in", "m10 17 5-5-5-5", "M15 12H3", "M15 3h4a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2h-4") }
    val LogOut by lazy { icon("log-out", "M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4", "m16 17 5-5-5-5", "M21 12H9") }
    val UserPlus by lazy {
        icon("user-plus", "M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2", circle(9f, 7f, 4f), "M19 8v6", "M22 11h-6")
    }
    val Archive by lazy {
        icon("archive", rect(2f, 3f, 20f, 5f, 1f), "M4 8v11a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8", "M10 12h4")
    }
    val Palette by lazy {
        icon(
            "palette",
            "M12 22a1 1 0 0 1 0-20 10 9 0 0 1 10 9 5 5 0 0 1-5 5h-2.25a1.75 1.75 0 0 0-1.4 2.8l.3.4a1.75 1.75 0 0 1-1.4 2.8z",
            circle(13.5f, 6.5f, 0.5f), circle(17.5f, 10.5f, 0.5f), circle(6.5f, 12.5f, 0.5f), circle(8.5f, 7.5f, 0.5f),
        )
    }
    val Clock by lazy { icon("clock", circle(12f, 12f, 10f), "M12 6v6l4 2") }
    val Share by lazy {
        icon(
            "share-2",
            circle(18f, 5f, 3f), circle(6f, 12f, 3f), circle(18f, 19f, 3f),
            "m8.59 13.51 6.83 3.98", "m15.41 6.51-6.82 3.98",
        )
    }
    val Refresh by lazy {
        icon(
            "refresh-cw",
            "M3 12a9 9 0 0 1 9-9 9.75 9.75 0 0 1 6.74 2.74L21 8",
            "M21 3v5h-5",
            "M21 12a9 9 0 0 1-9 9 9.75 9.75 0 0 1-6.74-2.74L3 16",
            "M8 16H3v5",
        )
    }
    val WifiOff by lazy {
        icon(
            "wifi-off",
            "M12 20h.01", "M8.5 16.429a5 5 0 0 1 7 0", "M5 12.859a10 10 0 0 1 5.17-2.69",
            "M19 12.859a10 10 0 0 0-2.007-1.523", "M2 8.82a15 15 0 0 1 4.177-2.643",
            "M22 8.82a15 15 0 0 0-11.288-3.764", "m2 2 20 20",
        )
    }
}
