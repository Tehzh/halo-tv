package app.orchard.tv.island

import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import app.orchard.tv.R
import kotlin.math.abs

internal enum class IslandPosition(
    val id: String,
    val label: Int,
    val width: Float,
    val height: Float,
) {
    Center("center", R.string.island_position_center, 300f, 44f),
    TopRight("top_right", R.string.island_position_right, 274f, 42f),
    LowerLeft("lower_left", R.string.island_position_left, 336f, 52f);

    companion object {
        val Default = LowerLeft

        fun restore(id: String?): IslandPosition = entries.firstOrNull { it.id == id } ?: Default
    }
}

internal enum class IslandClockPart { Greeting, Time, Date, Weather, Capacity, Block }

/** The clock reports the actual text rectangles after layout, including the clock's scale. */
internal class IslandClockBounds {
    val areas = mutableStateMapOf<IslandClockPart, Rect>()

    fun Modifier.trackIslandClock(part: IslandClockPart): Modifier = onGloballyPositioned { coordinates ->
        val area = coordinates.findRootCoordinates().localBoundingBoxOf(coordinates, clipBounds = false)
        if (!area.isEmpty) areas[part] = area else areas.remove(part)
    }
}

internal fun islandTitleWidthLimit(position: IslandPosition, screenWidth: Float, dockLeft: Float, dockRight: Float): Float {
    val insideScreen = when (position) {
        IslandPosition.Center -> screenWidth - 24f
        IslandPosition.TopRight -> dockRight - 12f
        IslandPosition.LowerLeft -> screenWidth - dockLeft - 12f
    }
    return minOf(insideScreen, screenWidth * .50f)
}

/** Resolve in the 960 × 540 design coordinates. Keep the chosen horizontal anchor. */
internal fun placeIsland(
    position: IslandPosition,
    screenWidth: Float,
    dockLeft: Float,
    dockRight: Float,
    dockTop: Float,
    greetingCenter: Float,
    belowSettings: Float,
    clockParts: List<Rect>,
    wanted: Size = Size(position.width, position.height),
    lowerLeftLift: Float = 0f,
): Rect? {
    val maxWidth = islandTitleWidthLimit(position, screenWidth, dockLeft, dockRight)
    val width = wanted.width.coerceAtMost(maxWidth)
    if (width < 65f) return null
    val left = when (position) {
        IslandPosition.Center -> (screenWidth - width) / 2
        IslandPosition.TopRight -> dockRight - width
        IslandPosition.LowerLeft -> dockLeft
    }.coerceIn(12f, screenWidth - width - 12f)
    val right = left + width
    val overlapsX = clockParts.filter { it.left < right + 8f && it.right > left - 8f }
    val top = when (position) {
        IslandPosition.Center -> 12f
        IslandPosition.TopRight -> belowSettings
        IslandPosition.LowerLeft -> maxOf(12f, (overlapsX.maxOfOrNull { it.bottom } ?: 0f) + 10f)
    }
    val bottom = dockTop - 10f
    if (bottom - top < 34f) return null

    val bands = buildList {
        var floor = top
        for (area in overlapsX.sortedBy { it.top }) {
            val ceiling = minOf(bottom, area.top - 6f)
            if (ceiling > floor) add(floor to ceiling)
            floor = maxOf(floor, area.bottom + 6f)
        }
        if (bottom > floor) add(floor to bottom)
    }
    val height = minOf(wanted.height, bands.maxOfOrNull { it.second - it.first } ?: 0f)
    if (height < 34f) return null
    val requestedTop = when (position) {
        IslandPosition.Center -> greetingCenter - height / 2
        IslandPosition.TopRight -> top
        IslandPosition.LowerLeft -> bottom - height - lowerLeftLift
    }
    val y = bands.asSequence().filter { it.second - it.first >= height }
        .map { (start, end) -> requestedTop.coerceIn(start, end - height) }
        .minBy { abs(it - requestedTop) }
    return Rect(left, y, right, y + height)
}
