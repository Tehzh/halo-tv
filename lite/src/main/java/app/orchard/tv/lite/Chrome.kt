package app.orchard.tv.lite

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Build
import android.text.format.DateFormat
import android.view.View
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The time as the full app shows it: 24- or 12-hour as chosen in Personalize, and with the setting
 * left to the TV, 24-hour unless the TV says 12 (TCL 65S515D, #15). No AM/PM, as in the full app.
 */
fun timeText(context: Context, millis: Long = System.currentTimeMillis()): String {
    val twelve = when (LiteSettings.clockHours) {
        ClockHours.H24 -> false
        ClockHours.H12 -> true
        ClockHours.System -> runCatching { android.provider.Settings.System.getString(context.contentResolver, android.provider.Settings.System.TIME_12_24) }.getOrNull() == "12"
    }
    return SimpleDateFormat(if (twelve) "h:mm" else "H:mm", Locale.getDefault()).format(Date(millis))
}

/** The date as the full app's dateText: the locale's month, day and weekday, the weekday spaced off. */
fun dateText(millis: Long = System.currentTimeMillis()): String {
    val locale = Locale.getDefault()
    val pattern = if (Build.VERSION.SDK_INT >= 18) {
        DateFormat.getBestDateTimePattern(locale, "MMMMdEEEE")
            .replace(Regex("(?<=[^\\s,，'])EEEE"), " EEEE")
            .replace(Regex("EEEE(?=[^\\s,，'])"), "EEEE ")
    } else when (locale.language) {
        "zh" -> "M月d日 EEEE"
        "en" -> "EEEE, MMMM d"
        else -> "EEEE, d MMMM"
    }
    return SimpleDateFormat(pattern, locale).format(Date(millis))
}

/** The weather beside the clock, once known: its glyph, the temperature, and the rest dimmed. */
class WeatherLine(val glyph: Glyph, val celsius: Int, val place: String?, val detail: String)

/**
 * The full app's StatusPill: top right, the network and Control Center's glyph, and the time once
 * Home has gone down into the grid. Up from the top row lands here; it opens Control Center.
 */
class StatusPill(context: Context) : View(context) {
    override fun hasOverlappingRendering() = false
    var selectedSegment = 2
        set(v) { field = v.coerceIn(0, 2); invalidate() }
    var focusedLook = false
        set(v) {
            if (field == v) return
            field = v
            invalidate()
            val s = 1f
            if (isHardwareAccelerated) animate().scaleX(s).scaleY(s).setDuration(160).start() else { scaleX = s; scaleY = s }
        }
    var showTime = false
        set(v) { if (field != v) { field = v; requestLayout(); invalidate() } }
    /** The network: Glyph.Wifi (with [level]), Glyph.Ethernet or Glyph.Offline. */
    var network = Glyph.Wifi
        set(v) { if (field != v) { field = v; invalidate() } }
    var level = 1f
        set(v) { if (field != v) { field = v; invalidate() } }
    /** A VPN is up: the full app's VpnBadge after the network glyph (issue #809). */
    var vpn = false
        set(v) { if (field != v) { field = v; requestLayout(); invalidate() } }

    private val text = Ui.text(15f, 600)
    private val bg = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val g = Ui.pxi(18f)
    private val badge = VpnBadge(context, 10f)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(Ui.pxi(40f), Ui.pxi(40f))
    }

    override fun onDraw(canvas: Canvas) {
        val diameter = Ui.px(34f)
        if (focusedLook) {
            bg.color = Ui.FOCUS
            rect.set((width - diameter) / 2f, (height - diameter) / 2f, (width + diameter) / 2f, (height + diameter) / 2f)
            canvas.drawRoundRect(rect, diameter / 2f, diameter / 2f, bg)
        }
        val glyph = Ui.pxi(19f)
        canvas.drawBitmap(Glyphs.image(Glyph.Gear, glyph, if (focusedLook) Ui.ON_FOCUS else Ui.WHITE),
            (width - glyph) / 2f, (height - glyph) / 2f, bitmapPaint)
    }

}

/** The full app's VpnBadge: "VPN" in a thin rounded frame, [size] its text size. */
class VpnBadge(context: Context, size: Float) {
    private val label = context.getString(R.string.net_vpn)
    private val text = Ui.text(size, 700)
    private val frame = Shapes.Tint()
    val width: Float = Ui.width(label, text, 0.04f) + Ui.px(8f)
    val height: Float = size * 1.2f * Ui.px(1f) + Ui.px(2f)

    /** Drawn from [x], centred on [centerY]; returns its width. */
    fun draw(canvas: Canvas, x: Float, centerY: Float, color: Int): Float {
        frame.draw(canvas, Shapes.band(Math.round(width), Math.round(height), Ui.px(4f), Ui.px(1.2f)), x, centerY - height / 2, color)
        text.color = color
        Ui.draw(canvas, label, x + Ui.px(4f), centerY - (text.descent() + text.ascent()) / 2, text, 0.04f)
        return width
    }
}

/** A [VpnBadge] as a plain view. */
class VpnBadgeView(context: Context, size: Float, private val color: Int) : View(context) {
    private val badge = VpnBadge(context, size)
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) = setMeasuredDimension(badge.width.toInt() + 1, badge.height.toInt() + 1)
    override fun onDraw(canvas: Canvas) { badge.draw(canvas, 0f, height / 2f, color) }
}
