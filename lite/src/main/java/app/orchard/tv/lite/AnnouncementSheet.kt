package app.orchard.tv.lite

import android.content.Context
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.KeyEvent
import android.widget.FrameLayout
import android.widget.LinearLayout

/** Native, D-pad-scrollable notices; uses the existing frozen desktop glass, never another blur. */
class AnnouncementSheet(context: Context, val notice: RemoteAnnouncement, close: () -> Unit) : Sheet(context, close) {
    private val body = AnnouncementBody(context, notice.body)
    private val panel = GlassBox(context, 24f)
    private var expanded = false
    private fun panelSize() = LayoutParams(
        minOf(Ui.screenW - Ui.pxi(48f), Ui.pxi(if (expanded) 900f else 760f)).coerceAtLeast(1),
        (Ui.screenH - Ui.pxi(if (expanded) 52f else 92f)).coerceAtLeast(Ui.pxi(200f)), Gravity.CENTER)
    init {
        addView(View(context).apply { setBackgroundColor(0x65000000) }, LayoutParams(-1, -1))
        addView(panel, panelSize())
        val col = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.pxi(22f), Ui.pxi(20f), Ui.pxi(22f), Ui.pxi(20f))
        }
        panel.addView(col, LayoutParams(-1, -1))
        val progress = context.label("", 13f, 500, Ui.SECONDARY)
        col.addView(progress)
        body.onProgress = { percent ->
            val label = context.getString(R.string.halo_ann_progress, percent)
            if (progress.text.toString() != label) progress.text = label
        }
        col.addView(context.label(notice.title, 25f, 600).apply {
            setSingleLine(false); maxLines = 2; ellipsize = TextUtils.TruncateAt.END
        },
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = Ui.pxi(10f) })
        col.addView(context.label(context.getString(R.string.halo_ann_read_hint), 13f, 400, Ui.SECONDARY),
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = Ui.pxi(8f) })
        col.addView(body, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = Ui.pxi(10f) })
        row(body)
        val actions = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        col.addView(actions, LinearLayout.LayoutParams(-1, -2).apply { topMargin = Ui.pxi(10f) })
        fun button(label: Int, action: () -> Unit): MenuRow {
            val b = MenuRow(context, Glyph.Info, context.getString(label), plate = true).apply { this.action = action }
            actions.addView(b, LinearLayout.LayoutParams(0, -2, 1f).apply { rightMargin = Ui.pxi(6f) })
            return b
        }
        val full = button(R.string.halo_ann_fullscreen) {}
        full.action = {
            expanded = !expanded
            full.text = context.getString(if (expanded) R.string.halo_ann_compact else R.string.halo_ann_fullscreen)
            full.invalidate()
            panel.layoutParams = panelSize()
            focusFirst()
        }
        val later = button(R.string.halo_ann_later) { Announcements.later(notice); close() }
        val ack = button(R.string.halo_ann_ack) { Announcements.acknowledge(notice); close() }
        row(full, later, ack)
        focusFirst()
    }
    override fun onKey(e: KeyEvent): Boolean {
        if (focused === body && e.action == KeyEvent.ACTION_DOWN) {
            val direction = when (e.keyCode) {
                KeyEvent.KEYCODE_DPAD_UP -> -1
                KeyEvent.KEYCODE_DPAD_DOWN -> 1
                else -> 0
            }
            if (direction != 0 && (body.page(direction) || e.repeatCount > 0)) return true
            if (e.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) { focusFirst(1); return true }
        }
        if (e.keyCode == KeyEvent.KEYCODE_BACK && e.action == KeyEvent.ACTION_UP) Announcements.later(notice)
        return super.onKey(e)
    }
}
