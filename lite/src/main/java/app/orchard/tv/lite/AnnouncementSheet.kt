package app.orchard.tv.lite

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.KeyEvent
import android.widget.FrameLayout
import android.widget.LinearLayout

/** Native, D-pad-scrollable notices; uses the existing frozen desktop glass, never another blur. */
class AnnouncementSheet(context: Context, val notice: RemoteAnnouncement, close: () -> Unit, open: (Intent) -> Unit) : Sheet(context, close) {
    private val body = AnnouncementBody(context, notice.body)
    init {
        addView(View(context).apply { setBackgroundColor(0x65000000) }, LayoutParams(-1, -1))
        val panel = GlassBox(context, 24f)
        val height = (Ui.screenH - Ui.pxi(60f)).coerceAtLeast(Ui.pxi(200f))
        addView(panel, LayoutParams(Ui.pxi(620f), height, Gravity.CENTER))
        val window = Viewport(context)
        panel.addView(window, LayoutParams(-1, -1))
        val col = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.pxi(22f), Ui.pxi(20f), Ui.pxi(22f), Ui.pxi(20f))
        }
        window.addView(col, LayoutParams(-1, -2)); scroller = col; viewport = height
        col.addView(context.label(context.getString(R.string.halo_ann_entry), 13f, 500, Ui.SECONDARY))
        col.addView(context.label(notice.title, 25f, 600).apply {
            setSingleLine(false); maxLines = 2; ellipsize = TextUtils.TruncateAt.END
        },
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = Ui.pxi(10f) })
        col.addView(body, LinearLayout.LayoutParams(-1, -2).apply { topMargin = Ui.pxi(12f) })
        row(body)
        fun button(label: Int, action: () -> Unit) {
            val b = MenuRow(context, Glyph.Info, context.getString(label), plate = true).apply { this.action = action }
            col.addView(b, LinearLayout.LayoutParams(-1, -2).apply { topMargin = Ui.pxi(7f) }); row(b)
        }
        notice.detailsUrl?.let { url -> button(R.string.halo_ann_details) { open(Intent(Intent.ACTION_VIEW, Uri.parse(url))); close() } }
        button(R.string.halo_ann_later) { Announcements.later(notice); close() }
        button(R.string.halo_ann_stop) { Announcements.acknowledge(notice); close() }
        button(R.string.halo_ann_ack) { Announcements.acknowledge(notice); close() }
        focusFirst()
    }
    override fun onKey(e: KeyEvent): Boolean {
        if (focused === body && e.action == KeyEvent.ACTION_DOWN) {
            val direction = when (e.keyCode) {
                KeyEvent.KEYCODE_DPAD_UP -> -1
                KeyEvent.KEYCODE_DPAD_DOWN -> 1
                else -> 0
            }
            if (direction != 0 && body.page(direction)) return true
        }
        if (e.keyCode == KeyEvent.KEYCODE_BACK && e.action == KeyEvent.ACTION_UP) Announcements.later(notice)
        return super.onKey(e)
    }
}
