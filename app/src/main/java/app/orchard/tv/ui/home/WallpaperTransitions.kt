package app.orchard.tv.ui.home

import app.orchard.tv.wallpaper.ScreensaverMode
import app.orchard.tv.wallpaper.Wallpapers

/** A non-video saver can cover a paused wallpaper without taking its decoder. */
internal fun saverRetainsWallpaper(mode: ScreensaverMode): Boolean = when (mode) {
    ScreensaverMode.Photos, ScreensaverMode.Bing, ScreensaverMode.Clock, ScreensaverMode.FlipClock -> true
    else -> false
}

/** Share only identical local playlists; a single selected saver must never play other uploads. */
internal fun localSaverCarriesWallpaper(wallpaper: String, videos: List<String>, single: Boolean, selected: String?): Boolean {
    if (videos.isEmpty()) return false
    val all = wallpaper == Wallpapers.VIDEOS
    val id = wallpaper.removePrefix(Wallpapers.VIDEO_PREFIX)
    if (!all && id !in videos) return false
    if (!single) return all || videos.size == 1
    // A missing selected saver falls back in the saver's own order. Only a sole item is unambiguous.
    val chosen = selected?.takeIf { it in videos } ?: videos.singleOrNull() ?: return false
    return if (all) videos.size == 1 else id == chosen
}

/** Idle-only mode waits on first entry/real input, but not when returning to an existing film. */
internal class WallpaperResumePolicy {
    private var rendered = false
    private var away = false
    fun frameRendered() { rendered = true }
    fun movingChanged(moving: Boolean): Boolean {
        if (!moving) { away = true; return false }
        return (away && rendered).also { away = false }
    }
}
