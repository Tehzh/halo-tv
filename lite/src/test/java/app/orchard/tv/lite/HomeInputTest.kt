package app.orchard.tv.lite

import android.content.ComponentName
import android.graphics.Bitmap
import android.view.KeyEvent
import android.view.View
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class HomeInputTest {
    private val context = RuntimeEnvironment.getApplication()
    private class Host : HomeView.Host {
        var launched = mutableListOf<String>()
        var menus = mutableListOf<String>()
        var center = 0
        override fun launch(app: AppEntry) { launched.add(app.key) }
        override fun openFolder(folder: AppEntry) {}
        override fun openMenu(app: AppEntry, tile: View) { menus.add(app.key) }
        override fun openControlCenter() { center++ }
        override fun openWallpaperSettings() {}
        override fun openNetworkSettings() {}
        override fun startSaver() {}
        override fun moved(key: String, target: String) {}
        override fun inGrid(grid: Boolean) {}
    }
    @Before fun setup() { Ui.init(context); LiteSettings.init(context) }
    private fun home(host: Host): HomeView {
        val h = HomeView(context, host)
        h.setApps((0..1).map { i -> AppEntry("pkg.$i", "App $i", ComponentName("pkg.$i", "pkg.$i.Home"), false, true, Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)) })
        return h
    }
    private fun HomeView.key(action: Int, code: Int) { onKey(KeyEvent(action, code)) }
    @Test fun menuOnTileOpensItsMenuForAllIconVisibilityModes() {
        for (mode in 0..2) {
            LiteSettings.chooseSettingsIconVisibility(mode)
            val host = Host(); val home = home(host)
            home.key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MENU); home.key(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MENU)
            assertEquals(listOf("pkg.0"), host.menus)
            assertEquals(0, host.center)
        }
    }
    @Test fun directionBetweenConfirmDownAndUpDoesNotLaunchNewTile() {
        val host = Host(); val home = home(host)
        home.key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER)
        home.key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT)
        home.key(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER)
        assertEquals(emptyList<String>(), host.launched)
    }
    @Test fun pointerClickCancelsAnEarlierConfirm() {
        val host = Host(); val home = home(host)
        home.key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER)
        val tap = HomeView::class.java.getDeclaredMethod("tapped", Int::class.javaPrimitiveType, Boolean::class.javaPrimitiveType).apply { isAccessible = true }
        tap.invoke(home, 1, false)
        home.key(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER)
        assertEquals(listOf("pkg.1"), host.launched)
    }
    @Test fun orphanOrMovedMenuReleaseDoesNotOpenAMenu() {
        val host = Host(); val home = home(host)
        home.key(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MENU)
        home.key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MENU)
        home.key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT)
        home.key(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MENU)
        assertEquals(emptyList<String>(), host.menus)
        assertEquals(0, host.center)
    }
    @Test fun menuOnPillOpensControlCenter() {
        val host = Host(); val home = home(host)
        home.key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_UP)
        home.key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MENU); home.key(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MENU)
        assertEquals(1, host.center)
        assertEquals(emptyList<String>(), host.menus)
    }

    @Test fun resizingKeepsTheSelectedAppAndCancelsAnOldConfirmPress() {
        val host = Host(); val home = home(host)
        home.key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT)
        for (size in listOf(0, 2, 1)) {
            home.key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER)
            LiteSettings.chooseDockSize(size)
            home.applySettings()
            home.key(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER)
            assertEquals(emptyList<String>(), host.launched)
        }
        home.key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER)
        home.key(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER)
        assertEquals(listOf("pkg.1"), host.launched)
    }
}
