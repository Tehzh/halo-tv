package app.orchard.tv.announcements

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import app.orchard.tv.R
import org.junit.After
import org.junit.Assert.*
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
class AnnouncementReaderTest {
    @get:Rule val compose = createEmptyComposeRule()
    private var host: ActivityController<ComponentActivity>? = null
    private var acknowledged = 0
    private var snoozed = 0
    @After fun close() { compose.runOnUiThread { host?.close() } }

    private fun show(body: String) {
        compose.runOnUiThread {
            host = Robolectric.buildActivity(ComponentActivity::class.java).setup()
            host!!.get().setContent {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    AnnouncementReader(RemoteAnnouncement("reader", 1, "更新与使用提醒", body,
                        detailsUrl = "https://halo-tv.pages.dev/help/update-and-setup.html"),
                        { snoozed++ }, { acknowledged++ })
                }
            }
        }
        compose.waitForIdle()
    }

    private fun key(key: Key) {
        compose.onNode(isFocused(), useUnmergedTree = true).performKeyInput { pressKey(key) }
        compose.waitForIdle()
    }

    private fun scroll() = compose.onNodeWithTag("announcement-body").fetchSemanticsNode()
        .config[SemanticsProperties.VerticalScrollAxisRange]

    @Test fun remoteReadsAllTextAndReturnsFromActionsWithoutDismissing() {
        show((1..45).joinToString("\n\n") { "第 $it 段：请通过 ADB 安装 HaloTV，并核对电视版本。" })
        compose.onNodeWithTag("announcement-body").assertIsFocused()
        assertEquals(0f, scroll().value())
        key(Key.DirectionDown)
        assertTrue(scroll().value() > 0f)
        var turns = 0
        while (scroll().value() < scroll().maxValue()) {
            key(Key.DirectionDown)
            assertTrue(++turns < 80)
            compose.onNodeWithTag("announcement-body").assertIsFocused()
        }
        compose.onNodeWithTag("announcement-progress").assertTextEquals("阅读进度：100%")
        key(Key.DirectionDown)
        compose.onNodeWithTag("announcement-body").assertIsNotFocused()
        key(Key.DirectionUp)
        compose.onNodeWithTag("announcement-body").assertIsFocused()
        key(Key.DirectionUp)
        assertTrue(scroll().value() < scroll().maxValue())
        assertEquals(0, acknowledged)
        assertEquals(0, snoozed)
    }

    @Test fun fullscreenIsLocalAndLeavesTextFocusable() {
        show("完整安装指令。\n".repeat(80))
        key(Key.DirectionDown)
        key(Key.DirectionRight)
        key(Key.DirectionCenter)
        compose.onNodeWithText(host!!.get().getString(R.string.halo_ann_compact)).assertExists()
        compose.onNodeWithTag("announcement-body").assertIsFocused()
        assertTrue(scroll().value() > 0f)
        assertEquals(0, acknowledged)
        assertEquals(0, snoozed)
        key(Key.DirectionRight)
        key(Key.DirectionRight)
        key(Key.DirectionRight)
        key(Key.DirectionCenter)
        assertEquals(1, acknowledged)
    }

    @Test fun shortNoticeDoesNotTrapFocusInTheReadingArea() {
        show("短公告")
        assertEquals(0f, scroll().maxValue())
        key(Key.DirectionDown)
        compose.onNodeWithTag("announcement-body").assertIsNotFocused()
        key(Key.DirectionUp)
        compose.onNodeWithTag("announcement-body").assertIsFocused()
    }
}
