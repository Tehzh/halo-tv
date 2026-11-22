package app.orchard.tv.lite

import android.content.Context
import android.view.KeyEvent
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class PerformanceSettingsNavigationTest {
    @Test fun performanceCategoryIsReachableAndKeepsExistingPreference() {
        val context = RuntimeEnvironment.getApplication()
        Ui.init(context)
        LiteSettings::class.java.getDeclaredField("prefs").apply { isAccessible = true }.set(LiteSettings, null)
        LiteSettings.init(context)
        val before = LiteSettings.autoCleanIdle
        try {
            LiteSettings.chooseAutoCleanIdle(false)
            val panel = Personalize(context, close = {}, hiddenCount = 0, onWallpaper = {}, onLayout = {}, onHidden = {}, onHomeLock = {}, onBing = {}, onAbout = {}, onSaverNow = {}, onPin = {})
            fun key(code: Int) { panel.onKey(KeyEvent(KeyEvent.ACTION_DOWN, code)) }
            repeat(5) { key(KeyEvent.KEYCODE_DPAD_DOWN) }
            assertEquals(5, panel.position.first)
            assertEquals(context.getString(R.string.halo_cat_performance), (panel.focused as MenuRow).text)
            key(KeyEvent.KEYCODE_DPAD_RIGHT)
            assertEquals(7, panel.position.first)
            key(KeyEvent.KEYCODE_DPAD_CENTER)
            panel.onKey(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER))
            assertTrue(LiteSettings.autoCleanIdle)
            assertTrue(context.getSharedPreferences("settings", Context.MODE_PRIVATE).getBoolean("auto_clean_idle", false))
            key(KeyEvent.KEYCODE_DPAD_LEFT)
            assertEquals(5, panel.position.first)
            key(KeyEvent.KEYCODE_DPAD_DOWN)
            assertEquals(6, panel.position.first)
        } finally { LiteSettings.chooseAutoCleanIdle(before) }
    }
}
