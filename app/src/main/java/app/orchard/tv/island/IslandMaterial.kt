package app.orchard.tv.island

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import app.orchard.tv.ui.home.NEUTRAL_DOCK_TINT
import app.orchard.tv.ui.home.VIDEO_DOCK_FILL_ALPHA
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

internal enum class IslandMaterial { Frosted, Liquid, Plain;
    companion object {
        fun fromDock(style: Int): IslandMaterial = when (style) {
            0 -> Frosted
            1 -> Liquid
            else -> Plain
        }
    }
}

internal data class IslandSurface(
    val material: IslandMaterial = IslandMaterial.Plain,
    val screen: Size = Size.Zero,
    val frost: ImageBitmap? = null,
    val moving: Boolean = false,
    val dockTint: () -> Color = { Color(NEUTRAL_DOCK_TINT) },
)

internal fun Modifier.islandMaterial(surface: IslandSurface, area: Rect, focused: Boolean): Modifier = drawWithCache {
    val image = surface.frost?.takeIf { surface.screen.width > 0f && surface.screen.height > 0f }
    val source = image?.let {
        val sx = it.width / surface.screen.width
        val sy = it.height / surface.screen.height
        val left = (area.left * sx).roundToInt().coerceIn(0, it.width - 1)
        val top = (area.top * sy).roundToInt().coerceIn(0, it.height - 1)
        Rect(left.toFloat(), top.toFloat(),
            (area.right * sx).roundToInt().coerceIn(left + 1, it.width).toFloat(),
            (area.bottom * sy).roundToInt().coerceIn(top + 1, it.height).toFloat())
    }
    val dst = IntSize(size.width.roundToInt().coerceAtLeast(1), size.height.roundToInt().coerceAtLeast(1))
    val sheen = Brush.verticalGradient(
        listOf(Color.White.copy(alpha = .16f), Color.Transparent, Color.White.copy(alpha = .035f)),
        endY = (size.height * .95f).coerceAtLeast(1.dp.toPx()),
    )
    onDrawBehind {
        when (surface.material) {
            IslandMaterial.Plain -> drawRect(Color(0xCC18202A))
            IslandMaterial.Frosted, IslandMaterial.Liquid -> {
                val liquid = surface.material == IslandMaterial.Liquid
                if (image != null && source != null && !(liquid && surface.moving)) {
                    drawImage(image, IntOffset(source.left.toInt(), source.top.toInt()),
                        IntSize(source.width.toInt(), source.height.toInt()), dstSize = dst,
                        alpha = if (liquid) .23f else .84f, filterQuality = FilterQuality.Low)
                }
                // The shared film tint follows the Dock without taking another wallpaper frame.
                if (liquid && surface.moving) drawRect(surface.dockTint().copy(alpha = VIDEO_DOCK_FILL_ALPHA))
                drawRect(Color.Black.copy(alpha = if (liquid) .35f else .48f))
                if (liquid) drawRect(sheen)
            }
        }
        if (focused) drawRect(Color(0xFFF0F8FF).copy(alpha = .93f))
    }
}
