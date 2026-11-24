package com.notsonata.comixium.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.notsonata.comixium.ui.theme.ComixColors
import com.notsonata.comixium.ui.theme.ComixiumTheme
import com.notsonata.comixium.ui.theme.withSiteTheme
import com.notsonata.comixium.web.AuthState
import com.notsonata.comixium.web.ReaderPrefs
import com.notsonata.comixium.web.RecentSearches
import com.notsonata.comixium.web.WebError
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Renders the native chrome on the JVM so it can be reviewed without a device. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h915dp-xhdpi")
class ChromeScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private val light = ComixColors().withSiteTheme(
        mapOf("bg" to "#f8f8f7", "bg2" to "#f2f2f1", "surface" to "#ebebeb", "surface2" to "#e0e1e2", "surface3" to "#d3d6d7",
            "textEmphasis" to "#2b383c", "text" to "#4f656d", "text2" to "#5f747c", "text3" to "#74868d", "text4" to "#a3afb4",
            "accent" to "#1c6fb0", "accent2" to "#045798", "accentInk" to "#ffffff"),
        isLight = true,
    )

    private fun readerPrefs() = ReaderPrefs(
        ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("reader", Context.MODE_PRIVATE),
    )

    private fun shot(name: String, colors: ComixColors = ComixColors(), content: @Composable () -> Unit) {
        compose.setContent { ComixiumTheme(colors) { content() } }
        compose.onRoot().captureRoboImage("screenshots/$name.png")
    }

    @Composable
    private fun Frame(colors: ComixColors, top: @Composable () -> Unit, tab: AppTab, body: @Composable () -> Unit) {
        Column(Modifier.fillMaxSize().background(colors.bg)) {
            top()
            Box(Modifier.fillMaxWidth().weight(1f)) { body() }
            ComixBottomBar(tab) {}
        }
    }

    @Test fun homeLoggedOut() = shot("home_logged_out") {
        Frame(ComixColors(), {
            ComixTopBar("", showBack = false, showBrand = true, auth = AuthState.LOGGED_OUT, {}, {}, {}, {}, {}, {})
        }, AppTab.HOME) {}
    }

    @Test fun titlePageLoggedIn() = shot("title_logged_in") {
        Frame(ComixColors(), {
            ComixTopBar("Return of the Blossoming Blade", showBack = true, showBrand = false, auth = AuthState.LOGGED_IN, {}, {}, {}, {}, {}, {})
        }, AppTab.BROWSE) {}
    }

    @Test fun moreLoggedOut() = shot("more_logged_out") {
        Frame(ComixColors(), {
            ComixTopBar("More", showBack = false, showBrand = false, auth = AuthState.LOGGED_OUT, {}, {}, {}, {}, {}, {})
        }, AppTab.MORE) {
            MoreScreen(AuthState.LOGGED_OUT, "comix.ws", true, 37, {}, {}, {}, {}, {}, {}, {}, readerPrefs(), {})
        }
    }

    @Test fun moreLoggedInLightTheme() = shot("more_logged_in_light", light) {
        Frame(light, {
            ComixTopBar("More", showBack = false, showBrand = false, auth = AuthState.LOGGED_IN, {}, {}, {}, {}, {}, {})
        }, AppTab.MORE) {
            MoreScreen(AuthState.LOGGED_IN, "comix.to", false, 0, {}, {}, {}, {}, {}, {}, {}, readerPrefs(), {})
        }
    }

    @Test fun libraryLoggedOut() = shot("library_login_prompt") {
        Frame(ComixColors(), {
            ComixTopBar("Library", showBack = false, showBrand = false, auth = AuthState.LOGGED_OUT, {}, {}, {}, {}, {}, {})
        }, AppTab.LIBRARY) { LoginPrompt(AppTab.LIBRARY, {}, {}) }
    }

    @Test fun search() {
        val prefs = ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("t", Context.MODE_PRIVATE)
        val recent = RecentSearches(prefs).apply { add("solo leveling"); add("return of the blossoming blade"); add("villainess") }
        shot("search") { SearchOverlay(recent, {}, {}) }
    }

    @Test fun offline() = shot("error_offline") {
        ErrorPane(WebError("https://comix.ws/", -2, "net::ERR_NAME_NOT_RESOLVED"), online = false, onRetry = {}, otherDomain = "comix.to", onTryDomain = {})
    }

    @Test fun siteDown() = shot("error_site_down") {
        ErrorPane(WebError("https://comix.ws/", 522, "Origin timed out", isHttp = true), online = true, onRetry = {}, otherDomain = "comix.to", onTryDomain = {})
    }
}
