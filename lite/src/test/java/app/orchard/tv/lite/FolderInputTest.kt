package app.orchard.tv.lite

import android.content.ComponentName
import android.graphics.Bitmap
import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class FolderInputTest {
    private val context = RuntimeEnvironment.getApplication()
    @Before fun setup() { Ui.init(context); LiteSettings.init(context) }
    @Test fun changingFolderFocusDuringConfirmDoesNotLaunchAnotherApp() {
        val apps = (0..1).map { i -> AppEntry("pkg.$i", "App $i", ComponentName("pkg.$i", "pkg.$i.Home"), false, true, Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)) }
        val launched = mutableListOf<String>()
        val folder = FolderView(context, {}, "f", "Folder", apps, { launched.add(it.key) }, { _, _ -> })
        folder.onKey(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER))
        folder.onKey(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT))
        folder.onKey(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER))
        assertEquals(emptyList<String>(), launched)
    }
}
