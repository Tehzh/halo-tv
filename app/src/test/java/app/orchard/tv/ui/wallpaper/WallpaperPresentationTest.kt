package app.orchard.tv.ui.wallpaper

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.orchard.tv.Overlay
import app.orchard.tv.R
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w960dp-h540dp-land-xhdpi")
class WallpaperPresentationTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun homeExitKeepsItsContentAndReentryFocusUsesTheNewDestination() {
        val host = Robolectric.buildActivity(ComponentActivity::class.java)
        val overlay = mutableStateOf<Overlay>(Overlay.WallpaperPicker())
        try {
            compose.runOnUiThread {
                host.setup().get().setContent {
                    val visible = overlay.value is Overlay.WallpaperPicker || overlay.value == Overlay.Wallpaper
                    val picker = rememberPresentedWallpaperPicker(visible, overlay.value as? Overlay.WallpaperPicker)
                    AnimatedVisibility(visible, exit = fadeOut(tween(180))) {
                        if (picker != null) key(picker) {
                            var tab by remember { mutableStateOf(picker.initialCategory) }
                            WallpaperCategorySidebar(tab, visible, picker.initialCategory) { tab = it }
                        } else BasicText("HaloTV settings")
                    }
                }
            }
            fun label(id: Int) = host.get().getString(id)
            val still = label(R.string.category_static)
            val dynamic = label(R.string.category_dynamic)
            compose.onNodeWithText(still).assertIsFocused()
            compose.onNodeWithText(dynamic).performClick()
            compose.mainClock.autoAdvance = false
            compose.runOnUiThread { overlay.value = Overlay.None }
            compose.mainClock.advanceTimeBy(65)
            compose.onNodeWithText(dynamic).assertExists()
            compose.onNodeWithText("HaloTV settings").assertDoesNotExist()
            compose.mainClock.advanceTimeBy(250)
            compose.mainClock.autoAdvance = true
            compose.runOnIdle { overlay.value = Overlay.WallpaperPicker() }
            compose.onNodeWithText(still).assertIsFocused()
            compose.runOnIdle { overlay.value = Overlay.WallpaperPicker(localVideos = true) }
            compose.onNodeWithText(dynamic).assertIsFocused()
            compose.runOnIdle { overlay.value = Overlay.WallpaperPicker(localVideos = false) }
            compose.onNodeWithText(still).assertIsFocused()
            compose.runOnIdle { overlay.value = Overlay.Wallpaper }
            compose.onNodeWithText("HaloTV settings").assertExists()
        } finally {
            compose.mainClock.autoAdvance = true
            compose.runOnUiThread { host.close() }
        }
    }
}
