package app.orchard.tv.wallpaper

/** Keep ordinary cover decoding sharp, but bound extreme panoramas before allocating their pixels. */
internal fun coverSampleSize(width: Int, height: Int, targetWidth: Int, targetHeight: Int): Int {
    require(width > 0 && height > 0 && targetWidth > 0 && targetHeight > 0)
    var sample = 1
    while (width / (sample * 2L) >= targetWidth && height / (sample * 2L) >= targetHeight) sample *= 2
    val budget = maxOf(4_194_304L, targetWidth.toLong() * targetHeight * 2L)
    while ((width.toLong() + sample - 1) / sample * ((height.toLong() + sample - 1) / sample) > budget) sample *= 2
    return sample
}
