package app.orchard.tv.lite

import android.view.View
import android.view.ViewGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w960dp-h540dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AboutPageFitTest {
    @Test fun completeAboutContentFitsOnePageInEverySupportedLanguage() {
        for (language in listOf("en", "zh-rCN", "ru", "uk", "kk")) {
            RuntimeEnvironment.setQualifiers("$language-w960dp-h540dp-land-mdpi")
            val context = RuntimeEnvironment.getApplication()
            Ui.init(context)
            val page = HaloInfoSheet(context, {}, support = false)
            page.measure(View.MeasureSpec.makeMeasureSpec(Ui.screenW, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(Ui.screenH, View.MeasureSpec.EXACTLY))
            page.layout(0, 0, Ui.screenW, Ui.screenH)
            val content = page.scroller as ViewGroup
            assertTrue("$language: ${content.measuredHeight} > ${page.viewport}", content.measuredHeight <= page.viewport)
            assertEquals("All four notice cards must remain present", 4,
                (0 until content.childCount).count { content.getChildAt(it) is ReadingCard })
        }
    }
}
