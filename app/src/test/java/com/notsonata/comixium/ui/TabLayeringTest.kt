package com.notsonata.comixium.ui

import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The shell keeps every visited tab composed and hides the inactive ones (INVISIBLE). Whatever
 * order the tabs were visited in, the visible one must get the touches.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TabLayeringTest {
    @get:Rule val compose = createComposeRule()

    private fun clicksOnVisibleTab(visibleComposedFirst: Boolean): Pair<Int, Int> {
        var visibleClicks = 0
        var hiddenClicks = 0
        compose.setContent {
            Box(Modifier.size(200.dp).testTag("root")) {
                val tabs = listOf(true, false).let { if (visibleComposedFirst) it else it.reversed() }
                tabs.forEach { visible ->
                    AndroidView(
                        factory = { ctx ->
                            SwipeRefreshLayout(ctx).apply {
                                addView(View(ctx).apply { setOnClickListener { if (visible) visibleClicks++ else hiddenClicks++ } })
                            }
                        },
                        // Same layering rule as ComixiumApp.
                        modifier = Modifier.fillMaxSize().zIndex(if (visible) 1f else 0f),
                        update = { it.visibility = if (visible) View.VISIBLE else View.INVISIBLE },
                    )
                }
            }
        }
        compose.onNodeWithTag("root").performClick()
        compose.waitForIdle()
        return visibleClicks to hiddenClicks
    }

    @Test fun visibleTabComposedFirstGetsTouches() = assertEquals(1 to 0, clicksOnVisibleTab(visibleComposedFirst = true))

    @Test fun visibleTabComposedLastGetsTouches() = assertEquals(1 to 0, clicksOnVisibleTab(visibleComposedFirst = false))
}
