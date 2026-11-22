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

/** Static first-open welcome, shared with API-14 Lite; no timers, players or animation. */
class HaloWelcomeView(context: Context, private val completed: () -> Unit) : View(context) {
    companion object {
        private const val PREFS = "first_run_guide"
        // Completing a previous HaloTV welcome also completes onboarding after an upgrade.
        private const val COMPLETE = "welcome_light_completed"
        fun shouldShow(context: Context): Boolean {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            if (prefs.getBoolean(COMPLETE, false)) return false
            // Artwork changes must not put a confirmation screen between a restored guard and
            // Home. Only use this installation's completed 3.x welcome, never another package's
            // data or a permission grant; a genuinely new installation still sees the welcome.
            if (prefs.getBoolean("welcome3_completed", false)) {
                prefs.edit().putBoolean(COMPLETE, true).apply()
                return false
            }
            return true
        }
    }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private var backgroundArt: Bitmap? = null
    private var handwriting: Bitmap? = null
    private var logo: Bitmap? = null
    private var scale = 1f
    private var ox = 0f
    private var oy = 0f
    private val full = RectF(0f, 0f, 960f, 540f)
    private val logoRect = RectF(46f, 30f, 102f, 86f)
    private val helloRect = RectF(20f, 94f, 371f, 211f)
    private val button = RectF(46f, 330f, 334f, 383f)
    private val pressedButton = RectF(49f, 332f, 331f, 381f)
    private val focusRect = RectF(43f, 327f, 337f, 386f)
    private val regular = Typeface.create("sans-serif", Typeface.NORMAL)
    private val bold = Typeface.create("sans-serif", Typeface.BOLD)
    private val script = Typeface.create("cursive", Typeface.ITALIC)
    private val buttonFill = LinearGradient(0f, 330f, 0f, 383f,
        0xFF142354.toInt(), 0xFF030D42.toInt(), Shader.TileMode.CLAMP)
    private var armed = false
    private var finishing = false
    private val ink = 0xFF07113C.toInt()
    @Suppress("DEPRECATION")
    private val chinese = resources.configuration.locale.language == "zh"
    private val headline = context.getString(R.string.welcome_light_title)
    private val subtitle = context.getString(R.string.welcome_light_subtitle)
    private val start = context.getString(R.string.welcome_light_start)
    private val remote = context.getString(R.string.welcome_light_remote)
    private val credits = context.getString(R.string.welcome_light_credit)
    private val disclaimer = context.getString(R.string.welcome3_disclaimer)
    private val greeting = context.getString(R.string.welcome_light_hello)

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        isClickable = true
        if (Build.VERSION.SDK_INT >= 29) isForceDarkAllowed = false
        contentDescription = listOf(greeting, headline, subtitle, credits, start, disclaimer).joinToString(". ")
        if (Build.VERSION.SDK_INT >= 16) importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        loadArt()
        requestFocus()
    }

    private fun loadArt() {
        if (backgroundArt == null) backgroundArt = decode("brand-light.jpg", Bitmap.Config.ARGB_8888)
        if (handwriting == null && chinese) handwriting = decode("hello.png", Bitmap.Config.ARGB_8888)
        if (logo == null) logo = decode("logo.png", Bitmap.Config.ARGB_8888)
    }

    private fun decode(name: String, config: Bitmap.Config): Bitmap? = runCatching {
        context.assets.open("welcome/$name").use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inPreferredConfig = config })
        }
    }.getOrNull()

    override fun onDetachedFromWindow() {
        armed = false
        backgroundArt?.recycle()
        backgroundArt = null
        handwriting?.recycle()
        handwriting = null
        logo?.recycle()
        logo = null
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        scale = min(w / 960f, h / 540f)
        ox = (w - 960f * scale) / 2f
        oy = (h - 540f * scale) / 2f
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(0xFFEAE7FA.toInt())
        canvas.save()
        canvas.translate(ox, oy)
        canvas.scale(scale, scale)
        paint.style = Paint.Style.FILL
        paint.shader = null
        paint.alpha = 255
        backgroundArt?.let { canvas.drawBitmap(it, null, full, paint) }
        paint.alpha = 255
        logo?.let { canvas.drawBitmap(it, null, logoRect, paint) }
        text(canvas, "HaloTV", 115f, 64f, 25f, true)

        // Keep the complete calligraphic greeting visible from the very first frame.
        if (handwriting != null) canvas.drawBitmap(handwriting!!, null, helloRect, paint)
        else text(canvas, greeting, 48f, 182f, 80f, false, maxWidth = 355f, typeface = script)
        text(canvas, headline, 51f, 239f, 42f, true, maxWidth = 433f)
        text(canvas, subtitle, 51f, 285f, 25f, false, maxWidth = 440f)

        paint.color = 0xFF0A1644.toInt()
        paint.shader = buttonFill
        paint.alpha = 255
        val face = if (armed) pressedButton else button
        canvas.drawRoundRect(face, 27f, 27f, paint)
        paint.shader = null
        if (hasFocus()) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 3f
            paint.color = 0xFFFFFFFF.toInt()
            paint.alpha = 255
            canvas.drawRoundRect(focusRect, 31f, 31f, paint)
            paint.style = Paint.Style.FILL
        }
        text(canvas, start, 190f, 366f, 27f, true, 0xFFFFFFFF.toInt(), 256f, Paint.Align.CENTER)
        text(canvas, remote, 51f, 412f, 17f, false, maxWidth = 400f)
        // Exactly the same left baseline as the headline, supporting text and remote hint.
        text(canvas, credits, 51f, 485f, 13f, false, maxWidth = 858f)
        text(canvas, disclaimer, 51f, 504f, 13f, false, maxWidth = 858f)
        canvas.restore()
    }

    private fun text(c: Canvas, value: String, x: Float, y: Float, size: Float, heavy: Boolean,
                     color: Int = ink, maxWidth: Float = 850f,
                     align: Paint.Align = Paint.Align.LEFT, typeface: Typeface? = null) {
        paint.shader = null
        paint.style = Paint.Style.FILL
        paint.typeface = typeface ?: if (heavy) bold else regular
        paint.textSize = size
        paint.color = color
        paint.alpha = 255
        paint.textAlign = align
        val measured = paint.measureText(value)
        if (measured > maxWidth) paint.textSize = size * maxWidth / measured
        c.drawText(value, x, y, paint)
    }

    override fun onFocusChanged(gain: Boolean, direction: Int, old: android.graphics.Rect?) {
        super.onFocusChanged(gain, direction, old)
        if (!gain) armed = false
        invalidate()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val confirm = event.keyCode == KeyEvent.KEYCODE_DPAD_CENTER || event.keyCode == KeyEvent.KEYCODE_ENTER ||
            event.keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER || event.keyCode == KeyEvent.KEYCODE_BUTTON_A
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
        val saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(COMPLETE, true).commit()
        if (!saved) { finishing = false; return true }
        super.performClick()
        sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_CLICKED)
        completed()
        return true
    }
}
