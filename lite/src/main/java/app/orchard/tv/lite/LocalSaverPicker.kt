package app.orchard.tv.lite

import android.content.Context
import android.graphics.BitmapFactory
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.LinearLayout
import java.io.File
import java.util.concurrent.Executors

/** Six small previews at a time; work stops when the selector closes. */
class LocalSaverPicker(context: Context, close: () -> Unit, private val video: Boolean) : Sheet(context, close) {
    private data class Item(val id: String, val name: String, val poster: File? = null)
    private val loader = Executors.newSingleThreadExecutor()
    private val col = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(Ui.pxi(20f), Ui.pxi(14f), Ui.pxi(20f), Ui.pxi(20f))
    }
    private var items = emptyList<Item>()
    private var page = 0
    @Volatile private var generation = 0
    init {
        setBackgroundColor(0xFF1D2A3A.toInt())
        addView(col, FrameLayout.LayoutParams(Ui.pxi(740f), -2, Gravity.CENTER))
        col.addView(context.label(context.getString(R.string.halo_saver_pick), 22f, 600))
        loader.execute {
            val found = if (video) ScreensaverVideos.all(context).map { Item(it.id, it.name, File(it.file.parentFile, "poster.jpg")) }
            else Wallpapers.items.filter { it.kind == WallpaperKind.Photo }.mapIndexed { i, it -> Item(it.id, context.getString(R.string.halo_saver_photo_number, i + 1)) }
            post { if (!loader.isShutdown) { items = found; build() } }
        }
    }
    private fun build() {
        generation++
        val token = generation
        clearRows(); col.removeAllViews()
        col.addView(context.label(context.getString(R.string.halo_saver_pick), 22f, 600))
        val back = MenuRow(context, Glyph.ChevronLeft, context.getString(R.string.halo_notice_close), plate = true).apply { action = close }
        col.addView(back); row(back)
        if (items.isEmpty()) col.addView(context.label(context.getString(R.string.halo_saver_empty), 14f, 400))
        items.drop(page * 6).take(6).chunked(3).forEach { group ->
            val line = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
            val controls = group.map { item ->
                Thumb(context, item.name, cardWidth = 218f, cardHeight = 112f) { LocalSaverSelection.selected(context, video) == item.id }.apply {
                    action = { LocalSaverSelection.choose(context, video, item.id); close() }
                    line.addView(this, LinearLayout.LayoutParams(-2, -2).apply { rightMargin = Ui.pxi(10f) })
                    val control = this
                    loader.execute {
                        if (token != generation || loader.isShutdown) return@execute
                        val picture = runCatching {
                            if (video) {
                                item.poster?.takeIf { it.isFile }?.let { file ->
                                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                                    BitmapFactory.decodeFile(file.path, bounds)
                                    var sample = 1
                                    while (bounds.outWidth / sample > 512 || bounds.outHeight / sample > 288) sample *= 2
                                    BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
                                }
                            } else Wallpapers.items.firstOrNull { it.id == item.id }?.let { Wallpapers.picture(it, 384, 216) }
                        }.getOrNull()
                        post {
                            if (token == generation && !loader.isShutdown) control.picture = picture
                            else picture?.recycle()
                        }
                    }
                }
            }
            col.addView(line, LinearLayout.LayoutParams(-1, -2).apply { topMargin = Ui.pxi(10f) }); row(*controls.toTypedArray())
        }
        val pages = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        val controls = ArrayList<Control>()
        if (page > 0) controls.add(Chip(context, "‹", { false }).apply { action = { page--; build() } })
        if ((page + 1) * 6 < items.size) controls.add(Chip(context, "›", { false }).apply { action = { page++; build() } })
        controls.forEach { pages.addView(it) }
        col.addView(pages); if (controls.isNotEmpty()) row(*controls.toTypedArray())
        unclip(); focusFirst(if (items.isEmpty()) 0 else 1)
    }
    override fun onDetachedFromWindow() { generation++; loader.shutdownNow(); super.onDetachedFromWindow() }
}
