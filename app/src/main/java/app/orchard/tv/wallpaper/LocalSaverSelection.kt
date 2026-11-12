package app.orchard.tv.wallpaper

import android.content.Context

/** Stable media IDs only: never duplicate uploaded files or hold decoded media in preferences. */
object LocalSaverSelection {
    private fun prefs(context: Context) = context.getSharedPreferences("local_saver_selection", Context.MODE_PRIVATE)
    fun single(context: Context, video: Boolean) = prefs(context).getBoolean(if (video) "video_single" else "photo_single", false)
    fun selected(context: Context, video: Boolean): String? = prefs(context).getString(if (video) "video_id" else "photo_id", null)
    fun chooseMode(context: Context, video: Boolean, single: Boolean) {
        prefs(context).edit().putBoolean(if (video) "video_single" else "photo_single", single).apply()
    }
    fun choose(context: Context, video: Boolean, id: String) {
        prefs(context).edit().putString(if (video) "video_id" else "photo_id", id)
            .putBoolean(if (video) "video_single" else "photo_single", true).apply()
    }
    /** A removed selection safely uses one remaining item; an empty library is handled by its caller. */
    fun <T> pick(items: List<T>, single: Boolean, selected: String?, id: (T) -> String): List<T> =
        if (!single) items else listOfNotNull(items.firstOrNull { id(it) == selected } ?: items.firstOrNull())
    fun <T> filter(context: Context, video: Boolean, items: List<T>, id: (T) -> String) =
        pick(items, single(context, video), selected(context, video), id)
}
