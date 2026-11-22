package app.orchard.tv.island

import android.content.ComponentName
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.unit.dp
import app.orchard.tv.R
import app.orchard.tv.apps.AppEntry
import app.orchard.tv.apps.TileArt
import app.orchard.tv.ui.wallpaper.SettingsSidebar
import app.orchard.tv.ui.wallpaper.latestBringIntoView
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "zh-w960dp-h540dp-land-mdpi")
@OptIn(ExperimentalTestApi::class)
class IslandSettingsTest {
    @get:Rule val compose = createEmptyComposeRule()
    private var host: ActivityController<ComponentActivity>? = null
    private lateinit var preferences: IslandPreferences

    @After fun close() { compose.runOnUiThread { host?.close() } }

    private fun openSettings() {
        compose.runOnUiThread {
            host = Robolectric.buildActivity(ComponentActivity::class.java).setup()
            val activity = host!!.get()
            preferences = IslandPreferences(activity)
            preferences.change { it.copy(enabled = true) }
            val bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888).asImageBitmap()
            val art = TileArt(bitmap, null, Rect.Zero, Color.Black, bitmap)
            val apps = listOf(
                AppEntry("com.netease.cloudmusic.tv", "网易云音乐",
                    ComponentName("com.netease.cloudmusic.tv", "Music"), true, true, art),
            )
            activity.setContent {
                var category by remember { mutableIntStateOf(8) }
                val island = remember { FocusRequester() }
                val remote = remember { FocusRequester() }
                val remoteRow = remember { FocusRequester() }
                val scroll = rememberScrollState()
                Row(Modifier.fillMaxSize().height(430.dp)) {
                    SettingsSidebar(category, -1, remote, remoteRow, onRecent = {}, islandFirstFocus = island) { category = it }
                    Column(Modifier.weight(1f).verticalScroll(scroll).latestBringIntoView(scroll)) {
                        if (category == 8) IslandSettings(preferences, apps, island)
                    }
                }
                LaunchedEffect(Unit) { withFrameNanos { }; island.requestFocus() }
            }
        }
        compose.waitForIdle()
    }

    private fun key(key: Key) {
        compose.onNode(isFocused(), useUnmergedTree = true).performKeyInput { pressKey(key) }
        compose.waitForIdle()
    }

    @Test fun remoteSelectsIslandPositionAndReturnsFocus() {
        openSettings()
        repeat(4) { key(Key.DirectionDown) }
        key(Key.DirectionCenter)
        val dialog = hasAnyAncestor(isDialog())
        val current = compose.onNode(dialog and isFocused() and hasAnyDescendant(hasText("左下")), useUnmergedTree = true)
        current.performKeyInput { pressKey(Key.DirectionUp) }
        compose.onNode(dialog and isFocused() and hasAnyDescendant(hasText("右上")), useUnmergedTree = true)
            .performKeyInput { pressKey(Key.DirectionCenter) }
        assertEquals(IslandPosition.TopRight, preferences.options.position)
        assertEquals(IslandPosition.TopRight, IslandPreferences(host!!.get()).options.position)
        compose.onNode(isDialog()).assertDoesNotExist()
        compose.onNode(isFocused() and hasAnyDescendant(hasText("灵动岛位置")), useUnmergedTree = true).assertExists()
    }

    @Test fun islandSettingsAppearBeforeAboutAndExplainPlayback() {
        openSettings()
        val activity = host!!.get()
        val island = compose.onAllNodesWithText(activity.getString(R.string.island_title), useUnmergedTree = true)
            .fetchSemanticsNodes().first { it.boundsInRoot.left < 200f }
        val about = compose.onNodeWithText(activity.getString(R.string.halo_cat_about), useUnmergedTree = true)
            .fetchSemanticsNode()
        org.junit.Assert.assertTrue(island.boundsInRoot.top < about.boundsInRoot.top)
        compose.onNodeWithText(activity.getString(R.string.island_playback_hint), substring = true).assertExists()
    }

    @Test fun musicAppCanBeSelectedWithRemoteAndSourceRowRemainsVisible() {
        openSettings()
        key(Key.DirectionDown)
        key(Key.DirectionDown)
        key(Key.DirectionCenter)
        compose.onNodeWithText("网易云音乐").assertExists()
        val dialogMusic = hasAnyAncestor(isDialog()) and isFocused() and hasAnyDescendant(hasText("网易云音乐"))
        compose.onNode(dialogMusic, useUnmergedTree = true).performKeyInput { pressKey(Key.DirectionCenter) }
        assertEquals("com.netease.cloudmusic.tv", preferences.options.musicPackage)
        compose.onNode(isDialog()).assertDoesNotExist()
        val source = host!!.get().getString(R.string.island_source)
        compose.onNode(isFocused() and hasAnyDescendant(hasText(source)), useUnmergedTree = true).assertExists()
        compose.onNodeWithText("网易云音乐").assertExists()
    }
}
