package app.orchard.tv.ui.components

import android.app.Dialog
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.view.Gravity
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory
import kotlin.math.roundToInt

// One opaque texture shared by settings pages and native pickers. No wallpaper sampling or live blur.
private val frost by lazy {
    createBitmap(160, 90).also { bitmap ->
        Canvas(bitmap).apply {
            drawColor(0xFF172336.toInt())
            val paint = Paint()
            fun light(x: Float, y: Float, radius: Float, color: Int) {
                paint.shader = RadialGradient(x, y, radius, color, color and 0x00FFFFFF, Shader.TileMode.CLAMP)
                drawPaint(paint)
            }
            light(29f, -7f, 115f, 0xFF40536A.toInt())
            light(128f, 30f, 91f, 0xB04C536E.toInt())
            light(105f, 95f, 84f, 0x883E4966.toInt())
            light(3f, 83f, 80f, 0xCC0D1729.toInt())
        }
    }
}

private val frostImage by lazy { frost.asImageBitmap() }

internal fun Modifier.settingsFrost(corner: Dp = 0.dp): Modifier {
    val surface = if (corner > 0.dp) clip(RoundedCornerShape(corner)) else this
    return surface.drawWithCache {
        val target = IntSize(size.width.roundToInt(), size.height.roundToInt())
        onDrawBehind { drawImage(frostImage, dstSize = target, filterQuality = FilterQuality.Low) }
    }
}

/** Keep the platform's input, navigation and dialog sizing while sharing the settings material. */
internal fun Dialog.applySettingsFrost() {
    val window = window ?: return
    val decor = window.decorView
    val padding = intArrayOf(decor.paddingLeft, decor.paddingTop, decor.paddingRight, decor.paddingBottom)
    val background = RoundedBitmapDrawableFactory.create(context.resources, frost).apply {
        gravity = Gravity.FILL
        cornerRadius = 18f * context.resources.displayMetrics.density
        setFilterBitmap(true)
    }
    window.setBackgroundDrawable(background)
    decor.setPadding(padding[0], padding[1], padding[2], padding[3])
}
