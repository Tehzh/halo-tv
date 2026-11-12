package app.orchard.tv.ui.home

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.accessibility.AccessibilityEvent
import app.orchard.tv.R
import kotlin.math.min

/** Shared, single-frame native welcome. No timers, players or live blur, also safe on API 14. */
class HaloWelcomeView(context: Context, private val completed: () -> Unit) : View(context) {
    companion object {
        private const val PREFS = "first_run_guide"
        private const val COMPLETE = "welcome3_completed"
        fun shouldShow(context: Context): Boolean =
            !context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(COMPLETE, false)
    }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private var backgroundArt: Bitmap? = null
    private var scale = 1f
    private var ox = 0f
    private var oy = 0f
    private val button = RectF(294f, 410f, 666f, 462f)
    private var armed = false
    private var finishing = false
    private val bold = Typeface.create("sans-serif", Typeface.BOLD)
    private val regular = Typeface.create("sans-serif", Typeface.NORMAL)
    private val ink = 0xFF071D37.toInt()

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        isClickable = true
        // Keep the supplied navy artwork and white text unchanged under Android auto-darkening.
        if (Build.VERSION.SDK_INT >= 29) isForceDarkAllowed = false
        contentDescription = listOf(context.getString(R.string.welcome3_eyebrow), "HaloTV 3.0",
            context.getString(R.string.welcome3_tagline), context.getString(R.string.welcome3_credit),
            context.getString(R.string.welcome3_thanks), context.getString(R.string.welcome3_start),
            context.getString(R.string.welcome3_disclaimer)).joinToString(". ")
        if (Build.VERSION.SDK_INT >= 16) importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (backgroundArt == null) backgroundArt = context.assets.open("welcome_horizon.jpg").use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.RGB_565
            })
        }
        requestFocus()
    }

    override fun onDetachedFromWindow() {
        armed = false
        backgroundArt?.recycle()
        backgroundArt = null
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        scale = min(w / 960f, h / 540f)
        ox = (w - 960f * scale) / 2f
        oy = (h - 540f * scale) / 2f
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(0xFF020C21.toInt())
        canvas.save()
        canvas.translate(ox, oy)
        canvas.scale(scale, scale)
        paint.shader = null
        paint.style = Paint.Style.FILL
        paint.alpha = 255
        backgroundArt?.let { canvas.drawBitmap(it, null, RectF(0f, 0f, 960f, 540f), paint) }
        label(canvas, R.string.welcome3_eyebrow, 198f, 22f)
        paint.shader = LinearGradient(0f, 216f, 0f, 273f,
            intArrayOf(0xFFFFFFFF.toInt(), 0xFFB5E7FF.toInt()), null, Shader.TileMode.CLAMP)
        text(canvas, "HaloTV 3.0", 272f, 74f, true)
        paint.shader = null
        label(canvas, R.string.welcome3_tagline, 309f, 26f)
        label(canvas, R.string.welcome3_credit, 352f, 16f, 0xFFBACFE7.toInt())
        label(canvas, R.string.welcome3_thanks, 375f, 16f, 0xFFBACFE7.toInt())
        // A pre-baked halo is unnecessary: a few nested rounded strokes render only on focus/press.
        if (hasFocus()) {
            for (outset in 18 downTo 1) {
                paint.color = 0x0650B8FF
                val glow = RectF(button).apply { inset(-outset.toFloat(), -outset.toFloat()) }
                canvas.drawRoundRect(glow, 35f, 35f, paint)
            }
        }
        paint.alpha = 255
        val rect = RectF(button).apply { if (armed) inset(3f, 2f) }
        paint.shader = LinearGradient(0f, rect.top, 0f, rect.bottom,
            0xFFCEF0FF.toInt(), 0xFF91D8FF.toInt(), Shader.TileMode.CLAMP)
        canvas.drawRoundRect(rect, 27f, 27f, paint)
        paint.shader = null
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.2f
        paint.color = 0xFFE6F9FF.toInt()
        canvas.drawRoundRect(rect, 27f, 27f, paint)
        paint.style = Paint.Style.FILL
        text(canvas, context.getString(R.string.welcome3_start), 445f, 24f, true, ink)
        label(canvas, R.string.welcome3_disclaimer, 501f, 14f, 0xFFABC5E1.toInt())
        canvas.restore()
    }

    private fun label(c: Canvas, id: Int, y: Float, size: Float, color: Int = 0xFFF0F6FF.toInt()) =
        text(c, context.getString(id), y, size, false, color)

    private fun text(c: Canvas, value: String, y: Float, size: Float, heavy: Boolean,
                     color: Int = 0xFFF0F6FF.toInt()) {
        paint.typeface = if (heavy) bold else regular
        paint.textSize = size
        paint.color = color
        paint.textAlign = Paint.Align.CENTER
        val measured = paint.measureText(value)
        if (measured > 850f) paint.textSize = size * 850f / measured
        c.drawText(value, 480f, y, paint)
    }

    override fun onFocusChanged(gain: Boolean, direction: Int, old: android.graphics.Rect?) {
        super.onFocusChanged(gain, direction, old)
        if (!gain) armed = false
        invalidate()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val confirm = event.keyCode in intArrayOf(KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_BUTTON_A)
        if (confirm) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) { armed = true; invalidate() }
            if (event.action == KeyEvent.ACTION_UP) {
                val activate = armed && !event.isCanceled
                armed = false; invalidate()
                if (activate) performClick()
            }
            return true
        }
        if (event.keyCode == KeyEvent.KEYCODE_BACK || event.keyCode in KeyEvent.KEYCODE_DPAD_UP..KeyEvent.KEYCODE_DPAD_RIGHT) return true
        return super.dispatchKeyEvent(event)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val hit = button.contains((event.x - ox) / scale, (event.y - oy) / scale)
        when (event.action) {
            MotionEvent.ACTION_DOWN -> { armed = hit; invalidate(); return true }
            MotionEvent.ACTION_MOVE -> { if (!hit) armed = false; invalidate(); return true }
            MotionEvent.ACTION_UP -> { val activate = armed && hit; armed = false; invalidate(); if (activate) performClick(); return true }
            MotionEvent.ACTION_CANCEL -> { armed = false; invalidate(); return true }
        }
        return true
    }

    override fun performClick(): Boolean {
        if (finishing) return true
        finishing = true
        // One tiny preference write before leaving avoids replay after an immediate power loss.
        val saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(COMPLETE, true).commit()
        if (!saved) { finishing = false; return true }
        super.performClick()
        sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_CLICKED)
        completed()
        return true
    }
}
