package app.orchard.tv.lite

import android.content.Context
import android.graphics.Canvas
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint

/** A bounded reading area. Up/Down page through its text before leaving for another control. */
internal class AnnouncementBody(context: Context, private val body: String) : Control(context) {
    override val focusScale = 1f
    private val text = TextPaint(Ui.text(14f, 400))
    private val pad = Ui.pxi(14f)
    private var textLayout: StaticLayout? = null
    private var lineWidth = 0
    internal var offset = 0
        private set
    internal val maxOffset: Int
        get() = ((textLayout?.height ?: 0) - (measuredHeight - pad * 2)).coerceAtLeast(0)

    @Suppress("DEPRECATION")
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val contentWidth = (width - pad * 2).coerceAtLeast(1)
        if (lineWidth != contentWidth) {
            lineWidth = contentWidth
            textLayout = StaticLayout(body, text, contentWidth, Layout.Alignment.ALIGN_NORMAL, 1.2f, Ui.px(2f), false)
        }
        val height = minOf((textLayout?.height ?: 0) + pad * 2, Ui.pxi(180f))
        setMeasuredDimension(width, height)
        offset = offset.coerceAtMost(maxOffset)
    }

    /** A short overlap keeps the last line visible when moving to the next page. */
    fun page(direction: Int): Boolean {
        val step = (height - pad * 2 - Ui.pxi(24f)).coerceAtLeast(Ui.pxi(24f))
        val next = (offset + direction * step).coerceIn(0, maxOffset)
        if (next == offset) return false
        offset = next
        invalidate()
        return true
    }

    override fun onDraw(canvas: Canvas) {
        val radius = Ui.px(14f)
        tint.draw(canvas, Shapes.mask(width, height, radius), 0f, 0f, Ui.FILL)
        if (focusedLook) tint.draw(canvas, Shapes.band(width, height, radius, Ui.px(1.5f)), 0f, 0f, Ui.FOCUS)
        canvas.save()
        canvas.clipRect(pad, pad, width - pad, height - pad)
        canvas.translate(pad.toFloat(), (pad - offset).toFloat())
        textLayout?.draw(canvas)
        canvas.restore()
    }
}
