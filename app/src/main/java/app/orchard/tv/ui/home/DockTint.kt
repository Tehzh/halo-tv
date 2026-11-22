package app.orchard.tv.ui.home

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.abs

internal const val VIDEO_DOCK_FILL_ALPHA = .055f

internal const val NEUTRAL_DOCK_TINT = 0xffe6edf4.toInt()

/** Read only a few pixels of an already prepared wallpaper image, not the video surface. */
internal fun dockTintOf(frame: Bitmap): Int? {
    var r = 0
    var g = 0
    var b = 0
    var samples = 0
    for (row in 0 until 3) {
        // Weight the lower part of the screen, where the Dock actually lives.
        val y = ((frame.height - 1) * (.68f + row * .11f)).toInt()
        for (column in 1..8) {
            val x = ((frame.width - 1).toLong() * column / 9).toInt()
            val c = frame.getPixel(x, y)
            if (Color.alpha(c) < 224) continue
            r += Color.red(c)
            g += Color.green(c)
            b += Color.blue(c)
            samples++
        }
    }
    if (samples == 0) return null
    val mean = (r + g + b) / (3 * samples)
    // Preserve hue but discard most of the sample's brightness and saturation.
    // Black scenes must not turn the Dock into a dark solid stripe.
    fun channel(sum: Int, neutral: Int) = (neutral + (sum / samples - mean) * 0.34f)
        .toInt().coerceIn(174, 255)
    return Color.rgb(channel(r, 230), channel(g, 237), channel(b, 244))
}

internal fun dockTintChanged(current: Int, next: Int): Boolean =
    maxOf(abs(Color.red(current) - Color.red(next)), abs(Color.green(current) - Color.green(next)),
        abs(Color.blue(current) - Color.blue(next))) >= 7
