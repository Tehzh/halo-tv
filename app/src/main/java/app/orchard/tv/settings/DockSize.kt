package app.orchard.tv.settings

/** Shared Home geometry; copied into native Lite without UI or platform dependencies. */
object DockSize {
    const val DEFAULT = 1
    fun valid(value: Int) = value in 0..2
    fun normalize(value: Int) = if (valid(value)) value else DEFAULT
    fun scale(value: Int) = when (value) { 0 -> 0.9f; 2 -> 1.1f; else -> 1f }
    fun gap(columns: Int, value: Int) = (when (columns) { 4 -> 34f; 6 -> 24f; else -> 28f }) * scale(value)
    fun tileWidth(screenWidth: Float, columns: Int, value: Int): Float =
        (screenWidth - 172f - gap(columns, DEFAULT) * (columns - 1)) / columns * scale(value)
    fun margin(screenWidth: Float, value: Int) = (screenWidth - (screenWidth - 172f) * scale(value)) / 2f
}
