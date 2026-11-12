package app.orchard.tv.wallpaper

/** Separable edge-clamped blur. Rolling sums preserve the old pixels without rereading each kernel. */
internal object BoxBlur {
    fun apply(pixels: IntArray, width: Int, height: Int, radius: Int, passes: Int = 1) {
        require(width > 0 && height > 0 && width.toLong() * height == pixels.size.toLong())
        require(radius in 0..1024 && passes >= 0)
        if (radius == 0 || passes == 0) return
        val scratch = IntArray(pixels.size)
        repeat(passes) {
            pass(pixels, scratch, width, height, radius, horizontal = true)
            pass(scratch, pixels, width, height, radius, horizontal = false)
        }
    }

    private fun pass(src: IntArray, dst: IntArray, width: Int, height: Int, radius: Int, horizontal: Boolean) {
        val lines = if (horizontal) height else width
        val length = if (horizontal) width else height
        val stride = if (horizontal) 1 else width
        val count = radius * 2 + 1
        for (line in 0 until lines) {
            val base = if (horizontal) line * width else line
            var alpha = 0; var red = 0; var green = 0; var blue = 0
            for (offset in -radius..radius) {
                val c = src[base + offset.coerceIn(0, length - 1) * stride]
                alpha += c ushr 24; red += (c ushr 16) and 255; green += (c ushr 8) and 255; blue += c and 255
            }
            for (i in 0 until length) {
                dst[base + i * stride] = ((alpha / count) shl 24) or ((red / count) shl 16) or
                    ((green / count) shl 8) or (blue / count)
                if (i == length - 1) continue
                val before = src[base + (i - radius).coerceIn(0, length - 1) * stride]
                val after = src[base + (i + radius + 1).coerceIn(0, length - 1) * stride]
                alpha += (after ushr 24) - (before ushr 24)
                red += ((after ushr 16) and 255) - ((before ushr 16) and 255)
                green += ((after ushr 8) and 255) - ((before ushr 8) and 255)
                blue += (after and 255) - (before and 255)
            }
        }
    }
}
