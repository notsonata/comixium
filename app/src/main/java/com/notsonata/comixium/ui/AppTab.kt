package com.notsonata.comixium.ui

import androidx.compose.ui.graphics.vector.ImageVector
import com.notsonata.comixium.ui.icons.Lucide

enum class AppTab(val label: String, val startPath: String?, val needsLogin: Boolean = false) {
    HOME("Home", "/"),
    BROWSE("Browse", "/browse"),
    LIBRARY("Library", "/user?tab=titles", needsLogin = true),
    HISTORY("History", "/user?tab=history", needsLogin = true),
    MORE("More", null);

    val icon: ImageVector
        get() = when (this) {
            HOME -> Lucide.House
            BROWSE -> Lucide.Compass
            LIBRARY -> Lucide.Bookmark
            HISTORY -> Lucide.History
            MORE -> Lucide.Grid
        }
}
