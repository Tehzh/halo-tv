package app.orchard.tv.lite

import android.content.Context
import android.view.View
import android.widget.FrameLayout
import org.junit.Assert.*
import org.junit.Before
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class DockSizeSettingsTest {
    private val context = RuntimeEnvironment.getApplication()
    private val prefs get() = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private fun reset() { LiteSettings::class.java.getDeclaredField("prefs").apply { isAccessible = true }.set(LiteSettings, null) }
    @Before fun before() { reset(); prefs.edit().clear().commit() }
    @After fun after() { reset(); prefs.edit().clear().commit() }

    @Test fun oldInstallKeepsOriginalDockAndOtherSettings() {
        prefs.edit().putInt("home_columns", 4).putBoolean("compact_dock_corners", true).commit()
        LiteSettings.init(context)
        assertEquals(1, LiteSettings.dockSize)
        assertFalse(prefs.contains("dock_size"))
        assertEquals(171.5f, HomeView.TILE_W, .001f)
        assertTrue(LiteSettings.compactDockCorners)
    }

    @Test fun changedSizeSurvivesRestartAndUpdatesBothDockAndIcons() {
        LiteSettings.init(context)
        LiteSettings.chooseHomeColumns(5)
        val originalW = HomeView.TILE_W
        for (size in 0..2) {
            LiteSettings.chooseDockSize(size)
            reset(); LiteSettings.init(context)
            val scale = floatArrayOf(.9f, 1f, 1.1f)[size]
            assertEquals(size, LiteSettings.dockSize)
            assertEquals(originalW * scale, HomeView.TILE_W, .001f)
            assertEquals(840f * scale, 960f - 2 * (HomeView.TILE_MARGIN - HomeView.PLATE_PAD_H), .001f)
            assertEquals(86f, HomeView.MARGIN, 0f)
        }
        LiteSettings.chooseDockSize(-1); LiteSettings.chooseDockSize(99)
        assertEquals(2, LiteSettings.dockSize)
        prefs.edit().putInt("dock_size", -8).commit()
        reset(); LiteSettings.init(context)
        assertEquals(1, LiteSettings.dockSize)
    }

    @Test fun clockAndStatusInsetsFollowSizeChangesOnTheExistingHomeView() {
        Ui.init(context)
        LiteSettings.init(context)
        val home = HomeView(context, object : HomeView.Host {
            override fun launch(app: AppEntry) {}
            override fun openFolder(folder: AppEntry) {}
            override fun openMenu(app: AppEntry, tile: View) {}
            override fun openControlCenter() {}
            override fun openWallpaperSettings() {}
            override fun openNetworkSettings() {}
            override fun startSaver() {}
            override fun moved(key: String, target: String) {}
            override fun inGrid(grid: Boolean) {}
        })
        for (position in listOf(0, 1, 3, 2)) {
            LiteSettings.chooseClockPosition(position)
            for (size in listOf(0, 2, 1)) {
                LiteSettings.chooseDockSize(size)
                home.applySettings()
                home.measure(View.MeasureSpec.makeMeasureSpec(Ui.screenW, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(Ui.screenH, View.MeasureSpec.EXACTLY))
                home.layout(0, 0, Ui.screenW, Ui.screenH)
                val margin = Ui.pxi(HomeView.TILE_MARGIN)
                val clockParams = home.clock.layoutParams as FrameLayout.LayoutParams
                val clockMargin = if (position == 2) Ui.pxi(HomeView.MARGIN) else margin
                assertEquals(clockMargin, clockParams.leftMargin)
                assertEquals(clockMargin, clockParams.rightMargin)
                assertEquals(margin, (home.topRight.layoutParams as FrameLayout.LayoutParams).rightMargin)
                when (position) {
                    0, 3 -> assertEquals(margin, home.clock.left)
                    1 -> assertEquals(Ui.screenW - margin, home.clock.right)
                    2 -> assertEquals(Ui.screenW / 2f, (home.clock.left + home.clock.right) / 2f, 1f)
                }
            }
        }
    }

    @Test fun gearPlacementFollowsDockBoundsWithoutCoveringClock() {
        Ui.init(context)
        LiteSettings.init(context)
        assertEquals(1, LiteSettings.settingsIconPosition)
        val home = HomeView(context, object : HomeView.Host {
            override fun launch(app: AppEntry) {}
            override fun openFolder(folder: AppEntry) {}
            override fun openMenu(app: AppEntry, tile: View) {}
            override fun openControlCenter() {}
            override fun openWallpaperSettings() {}
            override fun openNetworkSettings() {}
            override fun startSaver() {}
            override fun moved(key: String, target: String) {}
            override fun inGrid(grid: Boolean) {}
        })
        for (gear in 0..2) {
            LiteSettings.chooseSettingsIconPosition(gear)
            reset()
            LiteSettings.init(context)
            assertEquals(gear, LiteSettings.settingsIconPosition)
            for (dock in 0..2) {
                LiteSettings.chooseDockSize(dock)
                for (clock in 0..3) {
                    LiteSettings.chooseClockPosition(clock)
                    home.applySettings()
                    home.measure(View.MeasureSpec.makeMeasureSpec(Ui.screenW, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(Ui.screenH, View.MeasureSpec.EXACTLY))
                    home.layout(0, 0, Ui.screenW, Ui.screenH)
                    val margin = Ui.pxi(HomeView.TILE_MARGIN)
                    if (gear == 0) {
                        assertEquals(margin, home.topRight.left)
                        if (clock == 0 || clock == 3) assertTrue(home.clock.top >= home.topRight.bottom)
                    } else {
                        assertEquals(Ui.screenW - margin, home.topRight.right)
                        if (clock == 1) assertTrue(home.clock.top >= home.topRight.bottom)
                    }
                }
            }
        }
    }

}
