package app.orchard.tv.ui.wallpaper

/** Two-column grid: a rightward exit is only available at the end of a populated row. */
internal fun aerialRowEndsAt(index: Int, count: Int): Boolean =
    index >= 0 && index < count && (index % 2 == 1 || index == count - 1)

/** Move to preview only after the rightmost populated cell of a grid row. */
internal fun galleryRowEndsAt(index: Int, count: Int, columns: Int): Boolean =
    columns > 0 && index >= 0 && index < count && (index % columns == columns - 1 || index == count - 1)
