package app.orchard.tv.lite

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Toast
import org.json.JSONObject
import java.util.concurrent.Executors

/** Bounded pages, one worker, and no image requests after the sheet is closed. */
internal class BingSheet(context: Context, close: () -> Unit, private val applied: (String) -> Unit) : Sheet(context, close) {
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private val col = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(Ui.pxi(10f), Ui.pxi(6f), Ui.pxi(10f), Ui.pxi(8f))
    }
    @Volatile private var generation = 0
    private var page = 0
    private var selected = -1
    private var pageData: JSONObject? = null

    init {
        setBackgroundColor(0xFF142438.toInt())
        val area = Viewport(context)
        addView(area, FrameLayout.LayoutParams(Ui.pxi(848f), Ui.pxi(488f), Gravity.CENTER))
        area.addView(col, FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        scroller = col
        viewport = Ui.pxi(488f)
        load()
    }

    private fun line(text: String, size: Float, weight: Int = 500) = context.label(text, size, weight).apply {
        setSingleLine(false)
        maxLines = 2
    }

    private fun reset() {
        clearRows()
        col.removeAllViews()
        col.translationY = 0f
        col.addView(line(context.getString(R.string.lite_bing_title), 27f, 600))
        col.addView(line(context.getString(R.string.lite_bing_source), 12f))
    }

    private fun control(text: String, action: () -> Unit): Chip = Chip(context, text, { false }).apply { this.action = action }

    private fun load() {
        val request = ++generation
        reset()
        col.addView(line(context.getString(R.string.halo_saver_bing_loading), 16f))
        val index = page
        worker.execute {
            val data = runCatching { PhoneOnlineLibrary.page(context, index, "", 9) }.getOrNull()
            main.post {
                if (request != generation) return@post
                pageData = data
                renderGrid()
            }
        }
    }

    private fun renderGrid() {
        reset()
        val data = pageData
        val items = data?.optJSONArray("items")
        if (items == null || items.length() == 0) {
            col.addView(line(context.getString(R.string.halo_saver_bing_offline), 16f))
            val retry = control(context.getString(R.string.lite_retry), ::load)
            col.addView(retry); row(retry); focusFirst()
            return
        }
        val request = ++generation
        val cells = ArrayList<Pair<Thumb, JSONObject>>()
        val nav = LinearLayout(context)
        val links = mutableListOf<Control>()
        if (page > 0) links += control("‹  " + context.getString(R.string.lite_previous)) { page--; selected = -1; load() }
        if ((page + 1) * 9 < data.optInt("total")) links += control(context.getString(R.string.lite_next) + "  ›") { page++; selected = -1; load() }
        links.forEach { nav.addView(it) }
        col.addView(nav); if (links.isNotEmpty()) row(*links.toTypedArray())
        (0 until items.length()).chunked(3).forEach { indices ->
            val line = LinearLayout(context).apply { gravity = Gravity.CENTER_HORIZONTAL }
            val controls = indices.map { i ->
                val item = items.getJSONObject(i)
                Thumb(context, item.optString("title"), 248f, 98f) { false }.apply {
                    action = { selected = i; preview(item) }
                    line.addView(this, LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                        setMargins(Ui.pxi(8f), Ui.pxi(6f), Ui.pxi(8f), 0)
                    })
                    cells += this to item
                }
            }
            col.addView(line)
            row(*controls.toTypedArray())
        }
        focusFirst((if (links.isEmpty()) 0 else 1) + selected.coerceAtLeast(0) / 3, selected.coerceAtLeast(0) % 3)
        worker.execute {
            for ((view, item) in cells) {
                if (generation != request) break
                val primary = runCatching {
                    val bytes = if (android.os.Build.VERSION.SDK_INT < 18) {
                        PhoneOnlineLibrary.legacyThumbnail(item.getString("date"))
                    } else {
                        Net.bytes(item.getString("thumbnail"), THUMBNAIL_BYTES)
                    }
                    decodeThumbnail(bytes)
                }.getOrNull()
                if (generation != request || Thread.currentThread().isInterrupted) {
                    primary?.recycle()
                    break
                }
                val thumb = primary ?: runCatching {
                    decodeThumbnail(PhoneOnlineLibrary.legacyThumbnail(item.getString("date")))
                }.getOrNull()

                main.post { if (request == generation) view.picture = thumb else thumb?.recycle() }
            }
        }
    }

    private fun preview(item: JSONObject) {
        val request = ++generation
        reset()
        val back = control(context.getString(R.string.lite_back_to_list), ::renderGrid)
        col.addView(back); row(back); focusFirst()
        col.addView(line(item.optString("title"), 22f, 600))
        val image = android.widget.ImageView(context).apply { scaleType = android.widget.ImageView.ScaleType.FIT_CENTER }
        col.addView(image, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, Ui.pxi(258f)))
        val note = line(context.getString(R.string.halo_saver_bing_loading), 14f)
        col.addView(note)
        worker.execute {
            if (generation != request) return@execute
            val picture = runCatching {
                val (bytes, credit) = PhoneOnlineLibrary.picture(item.getString("date"))
                Wallpapers.onlinePicture(bytes)?.let { it to credit }
            }.getOrNull()
            main.post {
                if (generation != request) { picture?.first?.recycle(); return@post }
                if (picture == null) { note.text = context.getString(R.string.halo_saver_bing_offline); return@post }
                image.setImageBitmap(picture.first)
                note.text = picture.second.ifBlank { item.optString("credit") }
                val bar = LinearLayout(context)
                val actions = listOf(R.string.bing_apply).map { label ->
                    control(context.getString(label)) {
                        if (!image.isEnabled) return@control
                        image.isEnabled = false
                        val intent = Wallpapers.beginOnlineRequest()
                        worker.execute {
                            val temp = runCatching { Wallpapers.prepareOnline(picture.first) }.getOrNull()
                            main.post {
                                try {
                                if (generation != request) return@post
                                image.isEnabled = true
                                val id = temp?.let { Wallpapers.commitOnline(it, intent) }
                                if (id != null) applied(id)
                                else Toast.makeText(context, R.string.lite_bing_write_failed, Toast.LENGTH_SHORT).show()
                                } finally { temp?.delete(); Wallpapers.finishOnlineRequest(intent) }
                            }
                        }
                    }.also { bar.addView(it) }
                }
                col.addView(bar); row(*actions.toTypedArray())
            }
        }
    }

    companion object {
        private const val THUMBNAIL_BYTES = 4_000_000L

        internal fun thumbnailSample(width: Int, height: Int): Int {
            if (width <= 0 || height <= 0) return 0
            var sample = 1
            while (width / sample > 640 || height / sample > 360) sample *= 2
            return sample
        }

        internal fun decodeThumbnail(bytes: ByteArray): Bitmap? {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            val sample = thumbnailSample(bounds.outWidth, bounds.outHeight)
            if (sample == 0) return null
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.RGB_565
            })
        }
    }

    override fun onDetachedFromWindow() {
        generation++
        worker.shutdownNow()
        super.onDetachedFromWindow()
    }
}
