package app.orchard.tv.island

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import app.orchard.tv.ui.home.GridMetrics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class IslandPlacementTest {
    private fun place(position: IslandPosition, clock: List<Rect> = emptyList(), dockTop: Float = 395f): Rect? =
        placeIsland(position, 960f, 83f, 877f, dockTop, 67f, 134f, clock)

    @Test fun middleUsesGreetingCenterAndKeepsHorizontalCenterWhenClockGetsWide() {
        val greeting = Rect(83f, 56f, 143f, 78f)
        val time = Rect(83f, 80f, 240f, 125f)
        val date = Rect(252f, 102f, 465f, 125f)
        val centered = checkNotNull(place(IslandPosition.Center, listOf(greeting, time, date)))
        assertEquals(480f, centered.center.x, 0f)
        assertEquals(greeting.center.y, centered.center.y, 0f)

        val largeTime = Rect(83f, 78f, 410f, 155f)
        val safe = checkNotNull(place(IslandPosition.Center, listOf(greeting, largeTime)))
        assertEquals(480f, safe.center.x, 0f)
        assertFalse(safe.overlaps(largeTime))
        assertTrue(safe.bottom + 6f <= largeTime.top)
    }

    @Test fun sideAnchorsFollowActualDockTilesForEachSizeAndPartialRows() {
        for (size in 0..2) {
            for (columns in 4..6) {
                val metrics = GridMetrics(960.dp, 540.dp, columns, oneRow = true, plateNames = false,
                    peek = false, plate = true, largeClock = false, clockRight = false, dockSize = size)
                val count = columns - 1
                val right = (metrics.slot(count - 1, onTop = true).first + metrics.tileW).value
                val belowGear = 134f
                val upper = placeIsland(IslandPosition.TopRight, 960f, metrics.margin.value, right,
                    metrics.topRowY.value, 67f, belowGear, emptyList())
                val lower = placeIsland(IslandPosition.LowerLeft, 960f, metrics.margin.value, right,
                    metrics.topRowY.value, 67f, belowGear, emptyList())
                assertEquals(right, checkNotNull(upper).right, 0.001f)
                assertEquals(belowGear, upper.top, 0f)
                assertEquals(metrics.margin.value, checkNotNull(lower).left, 0.001f)
                assertTrue(lower.height > upper.height)
                assertTrue(lower.width > upper.width)
            }
        }
    }

    @Test fun rightCanMoveBelowARightClockAndLeftClearsLargeTextAndDock() {
        val rightClock = Rect(585f, 143f, 876f, 252f)
        val upper = checkNotNull(place(IslandPosition.TopRight, listOf(rightClock)))
        assertEquals(877f, upper.right, 0f)
        assertTrue(upper.top >= rightClock.bottom + 6f)

        val weather = Rect(83f, 188f, 460f, 232f)
        val lower = checkNotNull(place(IslandPosition.LowerLeft, listOf(weather), dockTop = 291f))
        assertEquals(83f, lower.left, 0f)
        assertTrue(lower.top >= weather.bottom + 10f)
        assertTrue(lower.bottom + 10f <= 291)
        assertTrue(lower.height >= 34f)
        assertNull(place(IslandPosition.LowerLeft, listOf(weather), dockTop = 275f))
    }

    @Test fun differentTitleWidthsKeepTheirAnchorsAndMaterialTracksTheDock() {
        for (position in IslandPosition.entries) {
            val short = checkNotNull(placeIsland(position, 960f, 83f, 877f, 380f, 67f, 70f, emptyList(), Size(110f, 44f)))
            val long = checkNotNull(placeIsland(position, 960f, 83f, 877f, 380f, 67f, 70f, emptyList(), Size(420f, 60f)))
            assertTrue(short.width < long.width)
            when (position) {
                IslandPosition.Center -> assertEquals(short.center.x, long.center.x, 0f)
                IslandPosition.TopRight -> assertEquals(short.right, long.right, 0f)
                IslandPosition.LowerLeft -> assertEquals(short.left, long.left, 0f)
            }
        }
        assertEquals(IslandMaterial.Frosted, IslandMaterial.fromDock(0))
        assertEquals(IslandMaterial.Liquid, IslandMaterial.fromDock(1))
        assertEquals(IslandMaterial.Plain, IslandMaterial.fromDock(2))
    }

    @Test fun additionalLeftLiftKeepsTheAnchorAndClearsTheClock() {
        fun lower(lift: Float, clock: List<Rect> = emptyList()) =
            checkNotNull(placeIsland(IslandPosition.LowerLeft, 960f, 86f, 875f, 397f, 67f, 70f,
                clock, Size(185f, 52f), lowerLeftLift = lift))
        val aboveTitle = lower(0f)
        val aboveDock = lower(28f)
        assertEquals(28f, aboveTitle.top - aboveDock.top, 0f)
        assertEquals(aboveTitle.left, aboveDock.left, 0f)
        val tallClock = Rect(86f, 67f, 365f, 323f)
        val constrained = lower(28f, listOf(tallClock))
        assertEquals(tallClock.bottom + 10f, constrained.top, 0f)
        assertFalse(tallClock.overlaps(constrained))
    }

    @Test fun wideStatusClockIsKeptAboveTheRightIslandAndPositionRestores() {
        val status = Rect(96f, 30f, 850f, 58f)
        assertNotNull(placeIsland(IslandPosition.TopRight, 960f, 83f, 877f, 390f, 67f, 70f, listOf(status)))
        assertEquals(IslandPosition.LowerLeft, IslandPosition.restore(null))
        for (position in IslandPosition.entries) assertEquals(position, IslandPosition.restore(position.id))
        val oneIcon = placeIsland(IslandPosition.TopRight, 960f, 83f, 238f, 390f, 67f, 70f, emptyList())
        assertEquals(238f, checkNotNull(oneIcon).right, 0f)
    }
}
