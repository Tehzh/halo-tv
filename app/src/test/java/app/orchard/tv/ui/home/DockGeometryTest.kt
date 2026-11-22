package app.orchard.tv.ui.home

import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class DockGeometryTest {
    private fun metrics(columns: Int, size: Int, oneRow: Boolean = true, names: Boolean = false,
                        plate: Boolean = true, peek: Boolean = false, height: Float = 540f) =
        GridMetrics(960.dp, height.dp, columns, oneRow, names, peek, plate, true, false, size)

    @Test fun defaultKeepsOriginalDimensionsAndInsets() {
        for (columns in 4..6) {
            val m = metrics(columns, 1)
            val expected = (960f - 172f - gridGap(columns).value * (columns - 1)) / columns
            assertEquals(expected, m.tileW.value, 0.0001f)
            assertEquals(86f, m.margin.value, 0f)
            assertEquals(60f, m.dockInset.value, 0f)
            assertEquals(60f, m.statusInset.value, 0f)
            assertEquals(86f, m.headerMargin.value, 0f)
            assertEquals(20f, m.platePadV.value, 0f)
            assertEquals(490f - expected * 9f / 16f, m.topRowY.value, 0.0001f)
        }
    }

    @Test fun dockAndIconsScaleTogetherStayCenteredAndKeepFocusOnScreen() {
        for (columns in 4..6) for (size in 0..2) {
            val m = metrics(columns, size)
            val scale = floatArrayOf(.9f, 1f, 1.1f)[size]
            val base = metrics(columns, 1)
            assertEquals(base.tileW.value * scale, m.tileW.value, .001f)
            assertEquals(840f * scale, (m.dockRight - m.dockInset).value, .001f)
            assertEquals(960f, (m.dockRight + m.dockInset).value, .001f)
            val first = m.slot(0, true).first.value
            val last = m.slot(columns - 1, true).first.value
            val focusOverhang = m.tileW.value * .13f / 2f + 3f
            assertTrue(first - focusOverhang > 0f)
            assertTrue(last + m.tileW.value + focusOverhang < 960f)
        }
    }

    @Test fun cornerInformationAndStatusGroupFollowTheirDockAnchorsAtEverySize() {
        for (columns in 4..6) for (size in 0..2) {
            val m = metrics(columns, size)
            val first = m.slot(0, true).first
            val last = m.slot(columns - 1, true).first + m.tileW
            assertEquals(first.value, m.headerMargin.value, .001f)
            assertEquals(last.value, 960f - m.headerMargin.value, .001f)
            assertEquals(m.dockInset.value, m.statusInset.value, .001f)
            if (size != 1) {
                assertNotEquals(86f, m.headerMargin.value, .001f)
                assertNotEquals(60f, m.statusInset.value, .001f)
            }
        }
    }

    @Test fun twoRowLayoutsAndAllNamesCanScrollWithoutLosingBottomRow() {
        for (height in listOf(540f, 720f)) for (columns in 4..6) for (size in 0..2)
            for (plate in listOf(false, true)) for (names in listOf(false, true)) {
                val m = metrics(columns, size, oneRow = false, names = names && plate, plate = plate, height = height)
                val lastBottom = m.slot(columns * 2 - 1, true).second.value + m.tileH.value - m.shelfLift.value
                assertTrue("size=$size columns=$columns names=$names", lastBottom + m.firstGap.value <= height - 29.9f)
                assertTrue(m.collapse.value > 0f)
                val next = m.scrollFor(2, 5).value
                assertTrue(next > 0f)
                assertTrue(m.slot(columns * 2, false).second.value - next >= 0f)
            }
    }
}
