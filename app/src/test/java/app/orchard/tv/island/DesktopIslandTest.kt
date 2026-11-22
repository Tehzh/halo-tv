package app.orchard.tv.island

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.dp
import app.orchard.tv.R
import app.orchard.tv.settings.AerialSettings
import app.orchard.tv.ui.components.Pressable
import app.orchard.tv.ui.home.CleanStyleClock
import app.orchard.tv.ui.home.dateText
import java.time.LocalDateTime
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "zh-w960dp-h540dp-land-mdpi")
@OptIn(ExperimentalTestApi::class)
class DesktopIslandTest {
    @get:Rule val compose = createEmptyComposeRule()
    private var host: ActivityController<ComponentActivity>? = null
    private lateinit var preferences: IslandPreferences
    private var onHome = mutableStateOf(true)
    private val opened = mutableListOf<IslandTask>()
    private var settingsOpened = 0

    @After fun close() { compose.runOnUiThread { host?.close() } }

    private fun show() {
        val dock = FocusRequester()
        val settings = FocusRequester()
        val island = IslandFocus()
        compose.runOnUiThread {
            host = Robolectric.buildActivity(ComponentActivity::class.java).setup()
            val activity = host!!.get()
            preferences = IslandPreferences(activity)
            activity.setContent {
                Box(Modifier.fillMaxSize()) {
                    Pressable({}, Modifier.align(Alignment.BottomEnd).testTag("dock").focusProperties {
                        up = island.dockUp(settings)
                    }, focusRequester = dock) { BasicText("Dock") }
                    Pressable({ settingsOpened++ }, Modifier.align(Alignment.TopCenter).testTag("settings").focusProperties {
                        up = island.settingsUp()
                        down = island.settingsDown(dock)
                    }, focusRequester = settings) { BasicText("Settings") }
                    DesktopIsland(
                        onHome.value, preferences, island, settings, { dock.requestFocus() }, { opened += it },
                        Modifier.align(Alignment.TopEnd).testTag("island"),
                    )
                    LaunchedEffect(Unit) { withFrameNanos { }; dock.requestFocus() }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun text(id: Int) = compose.onNodeWithText(host!!.get().getString(id), useUnmergedTree = true)
    private fun dock() = compose.onNodeWithTag("dock")
    private fun island() = compose.onNodeWithTag("island")

    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test fun titleLengthChangesWidthAndWideTitlesUseMoreThanOneLine() {
        val title = mutableStateOf("Hi")
        var content = Size.Zero
        compose.runOnUiThread {
            host = Robolectric.buildActivity(ComponentActivity::class.java).setup()
            host!!.get().setContent {
                val size = islandContentSize(title.value, null, IslandPosition.TopRight, 22f, true, 330f)
                androidx.compose.runtime.SideEffect { content = size }
            }
        }
        compose.waitForIdle()
        val short = content
        compose.runOnIdle { title.value = "The long road home" }
        compose.waitForIdle()
        assertTrue("short=$short, longer=$content", content.width > short.width + 10f)
        val oneLine = content
        compose.runOnIdle { title.value = "The long road home, under the stars, a live recording from the night of the festival" }
        compose.waitForIdle()
        assertTrue(content.width <= 330f)
        assertTrue("one line=$oneLine, wrapped=$content", content.height > oneLine.height)
    }

    @Test fun clockReportsPlacedGreetingAndTracksLargerClockText() {
        val scale = mutableFloatStateOf(1f)
        val bounds = IslandClockBounds()
        compose.runOnUiThread {
            host = Robolectric.buildActivity(ComponentActivity::class.java).setup()
            host!!.get().setContent {
                Box(Modifier.fillMaxSize()) {
                    CleanStyleClock(LocalDateTime.of(2026, 11, 19, 8, 15),
                        modifier = Modifier.padding(start = 86.dp, top = 56.dp)
                            .graphicsLayer(scaleX = scale.floatValue, scaleY = scale.floatValue,
                                transformOrigin = TransformOrigin(0f, 0f)),
                        islandBounds = bounds)
                }
            }
        }
        compose.waitForIdle()
        val greeting = checkNotNull(bounds.areas[IslandClockPart.Greeting])
        assertEquals(86f, greeting.left, 1f)
        assertEquals(56f, greeting.top, 1f)
        compose.runOnIdle { scale.floatValue = 1.4f }
        compose.waitForIdle()
        val enlarged = checkNotNull(bounds.areas[IslandClockPart.Greeting])
        assertTrue(enlarged.height > greeting.height)
        assertEquals(greeting.top, enlarged.top, 1f)
    }

    @Test fun homeClockKeepsTheCombinedDateBelowTimeAndAlignsBothRows() {
        val now = LocalDateTime.of(2026, 11, 19, 22, 59)
        val alignment = mutableStateOf(Alignment.Start)
        val bounds = IslandClockBounds()
        lateinit var civilDate: String
        compose.runOnUiThread {
            host = Robolectric.buildActivity(ComponentActivity::class.java).setup()
            val activity = host!!.get()
            AerialSettings.init(activity)
            AerialSettings.chooseShowHomeTime(true)
            AerialSettings.chooseShowHomeDate(true)
            AerialSettings.chooseShowLunarDate(true)
            activity.setContent {
                civilDate = dateText(now)
                CleanStyleClock(now, Modifier.width(480.dp), alignment = alignment.value, scale = 1.4f, islandBounds = bounds)
            }
        }
        val lunarDate = host!!.get().getString(R.string.lunar_date_value, "十月十一")
        for ((align, edge) in listOf(
            Alignment.Start to { rect: Rect -> rect.left },
            Alignment.End to { rect: Rect -> rect.right },
            Alignment.CenterHorizontally to { rect: Rect -> rect.center.x },
        )) {
            compose.runOnIdle { alignment.value = align }
            compose.onNodeWithText("$civilDate · $lunarDate").assertExists()
            compose.runOnIdle {
                val time = bounds.areas.getValue(IslandClockPart.Time)
                val date = bounds.areas.getValue(IslandClockPart.Date)
                assertTrue(date.top >= time.bottom)
                assertEquals(edge(time), edge(date), 1f)
            }
        }
        compose.runOnIdle { AerialSettings.chooseShowLunarDate(false) }
        compose.onNodeWithText(civilDate).assertExists()
        compose.onNodeWithText("$civilDate · $lunarDate").assertDoesNotExist()
        compose.runOnIdle { AerialSettings.chooseShowHomeDate(false) }
        compose.onNodeWithText(civilDate).assertDoesNotExist()
    }

    @Test fun remoteFocusStaysOnDockUntilChosenAndReturnsWhenCapsuleIsDisabled() {
        show()
        island().assertDoesNotExist()
        compose.runOnIdle { preferences.change { it.copy(enabled = true) } }
        compose.waitForIdle()
        island().assertDoesNotExist()
        compose.runOnIdle { checkNotNull(islandTransfers.begin(IslandTask.Photos)).finish(true) }
        text(R.string.island_received_photos).assertExists()
        dock().assertIsFocused()
        dock().performKeyInput { pressKey(Key.DirectionUp) }
        val gear = compose.onNodeWithTag("settings")
        gear.assertIsFocused()
        gear.performKeyInput { pressKey(Key.DirectionCenter) }
        assertEquals(1, settingsOpened)
        assertTrue(opened.isEmpty())
        gear.performKeyInput { pressKey(Key.DirectionUp) }
        island().assertIsFocused()
        island().performKeyInput { pressKey(Key.DirectionDown) }
        gear.assertIsFocused()
        gear.performKeyInput { pressKey(Key.DirectionDown) }
        dock().assertIsFocused()
        dock().performKeyInput { pressKey(Key.DirectionUp) }
        gear.performKeyInput { pressKey(Key.DirectionUp) }
        island().performKeyInput { pressKey(Key.DirectionCenter) }
        assertEquals(listOf(IslandTask.Photos), opened)
        compose.runOnIdle { preferences.change { it.copy(enabled = false) } }
        island().assertDoesNotExist()
        dock().assertIsFocused()
        compose.runOnIdle { assertEquals(null, islandTransfers.begin(IslandTask.Photos)) }
    }

    @Test fun activeTransfersAreHiddenOffHomeAndNotReplayedOnReturn() {
        show()
        compose.runOnIdle { preferences.change { it.copy(enabled = true) } }
        compose.waitForIdle()
        lateinit var outgoing: IslandTransferEvents.Transfer
        compose.runOnIdle { outgoing = checkNotNull(islandTransfers.begin(IslandTask.Video)) }
        text(R.string.island_receiving_video).assertExists()
        compose.runOnIdle { onHome.value = false }
        island().assertDoesNotExist()
        compose.runOnIdle { outgoing.finish(true); onHome.value = true }
        island().assertDoesNotExist()
        dock().assertIsFocused()
    }
}
