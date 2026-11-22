package app.orchard.tv.lite

import android.content.Context
import android.graphics.Canvas
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.view.MotionEvent
import android.view.ViewConfiguration

/** A bounded reading area. Up/Down page through its text before leaving for another control. */
internal class AnnouncementBody(context: Context, private val body: String) : Control(context) {
    override val focusScale = 1f
    private val text = TextPaint(Ui.text(16f, 400))
    private val pad = Ui.pxi(14f)
    private var textLayout: StaticLayout? = null
    private var lineWidth = 0
    internal var offset = 0
        private set
    internal val maxOffset: Int
        get() = ((textLayout?.height ?: 0) - (measuredHeight - pad * 2)).coerceAtLeast(0)
    var onProgress: (Int) -> Unit = {}
    private var touchY = 0f
    private var dragging = false
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    @Suppress("DEPRECATION")
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val contentWidth = (width - pad * 2).coerceAtLeast(1)
        if (lineWidth != contentWidth) {
            lineWidth = contentWidth
            textLayout = StaticLayout(body, text, contentWidth, Layout.Alignment.ALIGN_NORMAL, 1.2f, Ui.px(2f), false)
        }
        val height = resolveSize(minOf((textLayout?.height ?: 0) + pad * 2, Ui.pxi(180f)), heightMeasureSpec)
        setMeasuredDimension(width, height)
        offset = offset.coerceAtMost(maxOffset)
        reportProgress()
    }

    /** A short overlap keeps the last line visible when moving to the next page. */
    fun page(direction: Int): Boolean {
        val step = (height - pad * 2 - Ui.pxi(24f)).coerceAtLeast(Ui.pxi(24f))
        val next = (offset + direction * step).coerceIn(0, maxOffset)
        if (next == offset) return false
        offset = next
        invalidate()
        reportProgress()
        return true
    }

    private fun reportProgress() = onProgress(if (maxOffset == 0) 100 else (offset.toLong() * 100 / maxOffset).toInt())

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> { touchY = event.y; dragging = false; return true }
            MotionEvent.ACTION_MOVE -> {
                val dy = touchY - event.y
                if (dragging || kotlin.math.abs(dy) > touchSlop) {
                    dragging = true
                    offset = (offset + dy.toInt()).coerceIn(0, maxOffset)
                    touchY = event.y
                    invalidate(); reportProgress()
                }
                return true
            }
            MotionEvent.ACTION_UP -> { if (!dragging) performClick(); return true }
            MotionEvent.ACTION_CANCEL -> { dragging = false; return true }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean = super.performClick()

    override fun onDraw(canvas: Canvas) {
        val radius = Ui.px(14f)
        tint.draw(canvas, Shapes.mask(width, height, radius), 0f, 0f, Ui.FILL)
        if (focusedLook) tint.draw(canvas, Shapes.band(width, height, radius, Ui.px(2f)), 0f, 0f, Ui.FOCUS)
        canvas.save()
        canvas.clipRect(pad, pad, width - pad, height - pad)
        canvas.translate(pad.toFloat(), (pad - offset).toFloat())
        textLayout?.draw(canvas)
        canvas.restore()
        if (maxOffset > 0) {
            val available = (height - pad * 2).toFloat().coerceAtLeast(1f)
            val thumb = (available * available / (available + maxOffset)).coerceAtLeast(Ui.px(18f)).coerceAtMost(available)
            val top = pad + (available - thumb) * offset / maxOffset
            bg.color = Ui.SECONDARY
            rect.set(width - Ui.px(7f), top, width - Ui.px(4f), top + thumb)
            canvas.drawRoundRect(rect, Ui.px(1.5f), Ui.px(1.5f), bg)
        }
    }
}
