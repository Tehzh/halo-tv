package app.orchard.tv.ui.home

import android.graphics.BitmapShader
import android.graphics.ComposeShader
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.RadialGradient
import android.graphics.Shader
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.Dp
import app.orchard.tv.ui.components.smoothMask
import kotlin.math.roundToInt

/** Clear focus optics: no white fill or outline. Reuse the artwork's continuous-corner mask. */
internal fun Modifier.liquidFocusLens(corner: Dp, pulse: () -> Float): Modifier = drawWithCache {
    val width = size.width
    val height = size.height
    val mask = BitmapShader(smoothMask(width.roundToInt().coerceAtLeast(1), height.roundToInt().coerceAtLeast(1), corner.toPx()),
        Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
    fun masked(shader: Shader) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.shader = ComposeShader(shader, mask, PorterDuff.Mode.DST_IN)
    }
    // Localized specular reflections leave the logo and its colours unchanged in the centre.
    val upperLight = masked(RadialGradient(width * .12f, -height * .20f, width * .52f,
        0x22ffffff, 0x00ffffff, Shader.TileMode.CLAMP))
    val lowerLight = masked(RadialGradient(width * .96f, height * 1.08f, width * .42f,
        0x0effffff, 0x00ffffff, Shader.TileMode.CLAMP))
    val sweepShader = LinearGradient(-width * .13f, 0f, width * .13f, height,
        intArrayOf(0x00ffffff, 0x30ffffff, 0x00ffffff), floatArrayOf(0f, .5f, 1f), Shader.TileMode.CLAMP)
    val sweep = masked(sweepShader)
    val shift = Matrix()
    onDrawBehind {
        drawIntoCanvas { canvas ->
            canvas.nativeCanvas.drawRect(0f, 0f, width, height, upperLight)
            canvas.nativeCanvas.drawRect(0f, 0f, width, height, lowerLight)
            val progress = pulse().coerceIn(0f, 1f)
            if (progress > .001f) {
                shift.setTranslate(width * (1.4f * (1f - progress) - .2f), 0f)
                sweepShader.setLocalMatrix(shift)
                sweep.alpha = (progress * 150f).toInt()
                canvas.nativeCanvas.drawRect(0f, 0f, width, height, sweep)
            }
        }
    }
}
