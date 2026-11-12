package app.orchard.tv.lite

import android.content.Context
import android.graphics.Canvas
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint

/** Readable paragraphs participate in D-pad scrolling without adding a second menu. */
internal class ReadingCard(context: Context, private val body: String) : Control(context) {
    override val focusScale = 1f
    private val text = TextPaint(Ui.text(13f, 400)).apply { color = Ui.WHITE }
    private var layout: StaticLayout? = null
    private var lineWidth = 0
    private val pad = Ui.pxi(14f)

    @Suppress("DEPRECATION")
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val contentWidth = (width - pad * 2).coerceAtLeast(1)
        if (contentWidth != lineWidth) {
            lineWidth = contentWidth
            layout = StaticLayout(body, text, contentWidth, Layout.Alignment.ALIGN_NORMAL, 1.15f, Ui.px(2f), false)
        }
        setMeasuredDimension(width, (layout?.height ?: 0) + pad * 2)
    }

    override fun onDraw(canvas: Canvas) {
        tint.draw(canvas, Shapes.mask(width, height, Ui.px(14f)), 0f, 0f, Ui.FILL)
        if (focusedLook) tint.draw(canvas, Shapes.band(width, height, Ui.px(14f), Ui.px(1.5f)), 0f, 0f, Ui.FOCUS)
        canvas.save(); canvas.translate(pad.toFloat(), pad.toFloat()); layout?.draw(canvas); canvas.restore()
    }
}
