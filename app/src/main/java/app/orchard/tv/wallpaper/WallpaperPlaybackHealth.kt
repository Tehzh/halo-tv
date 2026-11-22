package app.orchard.tv.wallpaper

/** A single delayed drop notification must not downgrade a newly attached surface. */
internal object WallpaperPlaybackHealth {
    fun shouldLower(autoQuality: Boolean, quality: AerialQuality, playingMs: Long, dropped: Int, rendered: Int): Boolean =
        autoQuality && quality != AerialQuality.Hd && playingMs >= 5_000 && dropped >= 40 &&
            rendered + dropped >= 120 && dropped.toLong() * 5 >= rendered.toLong() + dropped
}
