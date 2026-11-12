package app.orchard.tv.lite

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.RectF
import android.os.Build
import android.text.TextPaint
import android.view.Gravity
import android.view.KeyEvent
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout

/** Native, scrollable support/contact page; code zoom is allocated only when requested. */
class HaloInfoSheet(context: Context, close: () -> Unit, support: Boolean) : Sheet(context, close) {
    private var detail: Sheet? = null
    override fun onKey(e: KeyEvent): Boolean = detail?.onKey(e) ?: super.onKey(e)
    init {
        setBackgroundColor(0xFF1D2A3A.toInt())
        if (Build.VERSION.SDK_INT >= 29) isForceDarkAllowed = false
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.pxi(14f), Ui.pxi(12f), Ui.pxi(14f), Ui.pxi(18f))
        }
        val window = Viewport(context)
        addView(window, FrameLayout.LayoutParams(Ui.pxi(884f), Ui.screenH - Ui.pxi(40f), Gravity.CENTER))
        window.addView(content, FrameLayout.LayoutParams(-1, -2))
        scroller = content; viewport = Ui.screenH - Ui.pxi(40f)
        fun addText(target: LinearLayout, id: Int, size: Float, top: Float = 0f, weight: Int = 500, color: Int = Ui.WHITE) {
            target.addView(context.label(context.getString(id), size, weight, color).apply {
                setSingleLine(false); ellipsize = null
            }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = Ui.pxi(top) })
        }
        if (support) {
            val top = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            val message = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
            addText(message, R.string.donate_title, 25f, weight = 600)
            addText(message, R.string.donate_subtitle, 17f, top = 10f, weight = 600)
            addText(message, R.string.donate_description, 14f, top = 12f)
            addText(message, R.string.donate_hint, 12f, top = 10f, color = Ui.SECONDARY)
            top.addView(message, LinearLayout.LayoutParams(0, -2, 1f).apply { rightMargin = Ui.pxi(20f) })
            fun donation(resource: Int, label: Int) {
                val stack = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }
                stack.addView(ImageView(context).apply {
                    setImageBitmap(decodeCode(context, resource, Ui.pxi(150f)))
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    setBackgroundColor(-1); contentDescription = context.getString(label)
                }, LinearLayout.LayoutParams(Ui.pxi(150f), Ui.pxi(150f)))
                stack.addView(context.label(context.getString(label), 14f, 600).apply { gravity = Gravity.CENTER },
                    LinearLayout.LayoutParams(-1, -2).apply { topMargin = Ui.pxi(7f) })
                top.addView(stack, LinearLayout.LayoutParams(Ui.pxi(164f), -2).apply { leftMargin = Ui.pxi(12f) })
            }
            donation(R.raw.donate_wechat, R.string.donate_wechat)
            donation(R.drawable.donate_alipay, R.string.donate_alipay)
            content.addView(top)
            addText(content, R.string.halo_contact_heading_email, 13f, top = 20f, weight = 600)
            addText(content, R.string.lite_contact_enlarge, 11f, top = 5f, color = Ui.SECONDARY)
            val contacts = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
            val controls = listOf(
                Triple(R.drawable.lite_contact_wechat, R.string.lite_contact_wechat, context.getString(R.string.lite_contact_scan)),
                Triple(R.drawable.lite_contact_douyin, R.string.lite_contact_douyin, "eyubaobao777"),
                Triple(R.drawable.lite_contact_xiaohongshu, R.string.lite_contact_xhs, "11350972266"),
            ).mapIndexed { index, (resource, title, account) ->
                val bitmap = decodeCode(context, resource, 512)
                ContactCode(context, bitmap, context.getString(title), account).apply {
                    action = { zoomCode(bitmap, context.getString(title), account) }
                    contacts.addView(this, LinearLayout.LayoutParams(0, Ui.pxi(116f), 1f).apply { if (index > 0) leftMargin = Ui.pxi(8f) })
                }
            }
            content.addView(contacts, LinearLayout.LayoutParams(-1, -2).apply { topMargin = Ui.pxi(10f) })
            row(*controls.toTypedArray())
        } else {
            addText(content, R.string.halo_notice_title, 27f, weight = 600)
            listOf(R.string.halo_notice_body, R.string.halo_free_notice, R.string.bing_source_notice).forEach { id ->
                val card = ReadingCard(context, context.getString(id))
                content.addView(card, LinearLayout.LayoutParams(-1, -2).apply { topMargin = Ui.pxi(12f) }); row(card)
            }
        }
        val back = Chip(context, context.getString(R.string.halo_notice_close), { false }).apply { action = close }
        content.addView(back, LinearLayout.LayoutParams(-2, -2).apply { topMargin = Ui.pxi(16f) })
        row(back); focusFirst()
    }

    private fun zoomCode(bitmap: Bitmap?, title: String, account: String) {
        if (bitmap == null || detail != null) return
        val sheet = object : Sheet(context, { detail?.let { removeView(it) }; detail = null }) {}
        sheet.setBackgroundColor(0xFF1D2A3A.toInt())
        val column = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }
        column.addView(context.label(title, 23f, 600).apply { gravity = Gravity.CENTER })
        column.addView(ImageView(context).apply {
            setImageDrawable(bitmap?.let { android.graphics.drawable.BitmapDrawable(resources, it).apply { setFilterBitmap(false) } }); scaleType = ImageView.ScaleType.FIT_CENTER; setBackgroundColor(-1)
        }, LinearLayout.LayoutParams(Ui.pxi(320f), Ui.pxi(320f)).apply { topMargin = Ui.pxi(14f) })
        column.addView(context.label(account, 16f, 500).apply { gravity = Gravity.CENTER },
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = Ui.pxi(10f) })
        val back = Chip(context, context.getString(R.string.halo_notice_close), { false }).apply {
            action = { removeView(sheet); detail = null }
        }
        column.addView(back, LinearLayout.LayoutParams(-2, -2).apply { topMargin = Ui.pxi(14f) })
        sheet.addView(column, FrameLayout.LayoutParams(-1, -1)); sheet.row(back); sheet.focusFirst()
        detail = sheet; addView(sheet, FrameLayout.LayoutParams(-1, -1))
    }
}

private fun decodeCode(context: Context, id: Int, target: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeResource(context.resources, id, bounds)
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= target && bounds.outHeight / (sample * 2) >= target) sample *= 2
    return BitmapFactory.decodeResource(context.resources, id, BitmapFactory.Options().apply {
        inSampleSize = sample; inPreferredConfig = Bitmap.Config.RGB_565; inScaled = false
    })
}

private class ContactCode(context: Context, private val code: Bitmap?, private val title: String, private val account: String) : Control(context) {
    private val text = TextPaint(Ui.text(13f, 600))
    private val small = TextPaint(Ui.text(10f, 400))
    override val focusScale = 1.025f
    override fun onDraw(canvas: Canvas) {
        tint.draw(canvas, Shapes.mask(width, height, Ui.px(14f)), 0f, 0f, Ui.FILL_STRONG)
        if (focusedLook) tint.draw(canvas, Shapes.band(width, height, Ui.px(14f), Ui.px(2f)), 0f, 0f, Ui.FOCUS)
        val pad = Ui.px(10f); val size = Ui.px(92f)
        bg.color = -1
        val box = RectF(pad, (height - size) / 2, pad + size, (height + size) / 2)
        canvas.drawRoundRect(box, Ui.px(6f), Ui.px(6f), bg)
        code?.let { box.inset(Ui.px(4f), Ui.px(4f)); canvas.drawBitmap(it, null, box, bitmapPaint) }
        val x = pad + size + Ui.px(10f)
        text.color = Ui.WHITE; small.color = Ui.SECONDARY
        canvas.drawText(title, x, baseline(text, height / 2f - Ui.px(12f)), text)
        canvas.drawText(fit(account, small, width - x - pad), x, baseline(small, height / 2f + Ui.px(12f)), small)
    }
}
