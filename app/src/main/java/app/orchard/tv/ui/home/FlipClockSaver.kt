package app.orchard.tv.ui.home

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import app.orchard.tv.settings.AerialSettings
import app.orchard.tv.R
import app.orchard.tv.weather.Weather
import app.orchard.tv.weather.WeatherKind
import java.time.LocalDateTime
import java.util.Locale

/** Clean Your TV's large, unboxed flip digits and its stationary one-line date and weather. */
@Composable
fun FlipClockSaver(now: LocalDateTime) {
    val hour = if (AerialSettings.flipClock24Hour) now.hour else (now.hour % 12).let { if (it == 0) 12 else it }
    val time = String.format(Locale.ROOT, "%02d%02d", hour, now.minute)
    val reading = Weather.now
    val weather = if (reading != null) {
        val condition = when (reading.kind) {
            WeatherKind.Clear -> R.string.weather_clear
            WeatherKind.PartlyCloudy -> R.string.weather_partly_cloudy
            WeatherKind.Cloudy -> R.string.weather_cloudy
            WeatherKind.Fog -> R.string.weather_fog
            WeatherKind.Rain -> R.string.weather_rain
            WeatherKind.Snow -> R.string.weather_snow
            WeatherKind.Thunder -> R.string.weather_thunder
        }
        listOfNotNull(Weather.place, "${reading.celsius}°", stringResource(condition)).joinToString(" · ")
    } else Weather.place.orEmpty()
    val lunar = Lunar.text(now.toLocalDate())?.let { stringResource(R.string.lunar_date_value, it) }
    val info = listOfNotNull(dateText(now), lunar, weather.takeIf { it.isNotBlank() }).joinToString("    ·    ")
    val drift = saverDrift(now)
    Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black).clipToBounds()) {
        AndroidView(
            factory = { FlipClockView(it) },
            update = { it.setContents(time, info, AerialSettings.flipClockSeconds) },
            modifier = Modifier.fillMaxSize().graphicsLayer {
                translationX = drift.x.toPx()
                translationY = drift.y.toPx()
            },
        )
    }
}

private class FlipClockView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG)
    private val digitTypeface = Typeface.create("sans-serif-black", Typeface.BOLD)
    private val textTypeface = Typeface.create("sans-serif", Typeface.NORMAL)
    private val digitBounds = RectF()
    private val topHalf = RectF()
    private val bottomHalf = RectF()
    private val metrics = Paint.FontMetrics()
    private val digits = Array(10) { it.toString() }
    private val sizes = FloatArray(10)
    private val offsets = FloatArray(10)
    private var measuredWidth = -1f
    private var measuredHeight = -1f
    private var fittedInfo = ""
    private var fittedInfoWidth = -1f
    private var fittedInfoSize = -1f
    private var infoTextSize = 0f
    private var secondText = ""
    private var current = "0000"
    private var previous = current
    private var initialized = false
    private var info = ""
    private var progress = 1f
    private var animator: ValueAnimator? = null

    private var showSeconds = false
    private val secondTick = object : Runnable {
        override fun run() {
            if (!showSeconds || !isAttachedToWindow || windowVisibility != VISIBLE) return
            val second = LocalDateTime.now().second
            secondText = if (second < 10) "0$second" else second.toString()
            invalidate()
            postDelayed(this, 1000 - System.currentTimeMillis() % 1000)
        }
    }
    override fun onAttachedToWindow() { super.onAttachedToWindow(); if (showSeconds) post(secondTick) }
    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        removeCallbacks(secondTick)
        if (visibility == VISIBLE && showSeconds) post(secondTick)
    }
    fun setContents(time: String, newInfo: String, seconds: Boolean) {
        if (showSeconds != seconds) {
            showSeconds = seconds
            removeCallbacks(secondTick)
            if (seconds) post(secondTick)
            invalidate()
        }
        if (!initialized) {
            current = time
            previous = time
            initialized = true
        } else if (time != current) {
            animator?.cancel()
            previous = current
            current = time
            progress = 0f
            animator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 620L
                interpolator = AccelerateDecelerateInterpolator()
                addUpdateListener { progress = it.animatedValue as Float; invalidate() }
                start()
            }
        } else if (info == newInfo) return
        info = newInfo
        invalidate()
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        removeCallbacks(secondTick)
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        // Match the original ClockView's 83% width and centered vertical rhythm.
        val cardSize = minOf(w * 0.22f, h * 0.42f, w * 0.90f / 2.14f)
        val infoSize = maxOf(18f, cardSize * 0.070f).coerceAtMost(h * 0.038f)
        val infoGap = cardSize * 0.13f
        val infoHeight = infoSize * 1.30f
        val startY = (h - cardSize - infoGap - infoHeight) * 0.5f
        val clockWidth = w * if (showSeconds) 0.74f else 0.83f
        val smallGap = w * 0.008f
        val middleGap = w * 0.052f
        val digitWidth = (clockWidth - middleGap - 2f * smallGap) / 4f
        val digitCenterY = startY + cardSize * 0.485f
        val digitHalfHeight = cardSize * 1.17f * 0.45f
        val top = (digitCenterY - digitHalfHeight).coerceAtLeast(0f)
        val bottom = digitCenterY + digitHalfHeight
        measureDigits(digitWidth, bottom - top)
        var x = (w - clockWidth) * 0.5f
        for (index in 0..3) {
            digitBounds.set(x, top, x + digitWidth, bottom)
            drawDigit(canvas, digitBounds, previous[index], current[index])
            x += digitWidth + if (index == 1) middleGap else smallGap
        }

        if (showSeconds) {
            paint.typeface = digitTypeface
            paint.color = Color.rgb(244, 244, 244)
            paint.textSize = cardSize * 0.24f
            paint.textAlign = Paint.Align.LEFT
            paint.getFontMetrics(metrics)
            canvas.drawText(secondText, x + w * 0.006f, bottom - metrics.descent, paint)
        }
        paint.typeface = textTypeface
        paint.textAlign = Paint.Align.CENTER
        paint.color = Color.rgb(158, 158, 158)
        if (fittedInfo != info || fittedInfoWidth != w || fittedInfoSize != infoSize) {
            fittedInfo = info
            fittedInfoWidth = w
            fittedInfoSize = infoSize
            paint.textSize = infoSize
            while (paint.measureText(info) > w * 0.86f && paint.textSize > 16f) paint.textSize -= 1f
            infoTextSize = paint.textSize
        }
        paint.textSize = infoTextSize
        paint.getFontMetrics(metrics)
        val infoCenterY = startY + cardSize + infoGap + infoHeight * 0.5f
        val baseline = infoCenterY - (metrics.ascent + metrics.descent) * 0.5f
        canvas.drawText(info, w * 0.5f, baseline, paint)
    }

    private fun drawDigit(canvas: Canvas, card: RectF, old: Char, next: Char) {
        val hingeY = card.centerY()
        if (old == next || progress >= 1f) {
            drawHalf(canvas, card, next, card)
        } else {
            drawFlip(canvas, card, old, next)
        }
        paint.color = Color.rgb(9, 9, 9)
        val seam = maxOf(3f, card.height() * 0.010f)
        canvas.drawRect(card.left, hingeY - seam * 0.5f, card.right, hingeY + seam * 0.5f, paint)
    }

    private fun drawFlip(canvas: Canvas, card: RectF, old: Char, next: Char) {
        val hingeY = card.centerY()
        val top = topHalf.apply { set(card.left, card.top, card.right, hingeY) }
        val bottom = bottomHalf.apply { set(card.left, hingeY, card.right, card.bottom) }
        if (progress < 0.5f) {
            drawHalf(canvas, card, next, top)
            drawHalf(canvas, card, old, bottom)
            drawHalf(canvas, card, old, top, (1f - progress * 2f).coerceAtLeast(0.02f))
        } else {
            drawHalf(canvas, card, next, top)
            drawHalf(canvas, card, old, bottom)
            drawHalf(canvas, card, next, bottom, ((progress - 0.5f) * 2f).coerceAtLeast(0.02f))
        }
    }

    private fun drawHalf(canvas: Canvas, card: RectF, digit: Char, clip: RectF, fold: Float = 1f) {
        canvas.save()
        canvas.clipRect(clip)
        if (fold != 1f) canvas.scale(1f, fold, card.centerX(), card.centerY())
        drawGlyph(canvas, card, digit)
        canvas.restore()
    }

    private fun drawGlyph(canvas: Canvas, card: RectF, digit: Char) {
        val index = digit - '0'
        paint.color = Color.rgb(244, 244, 244)
        paint.typeface = digitTypeface
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = sizes[index]
        canvas.drawText(digits[index], card.centerX(), card.centerY() + offsets[index], paint)
    }

    private fun measureDigits(width: Float, height: Float) {
        if (width == measuredWidth && height == measuredHeight) return
        measuredWidth = width
        measuredHeight = height
        paint.typeface = digitTypeface
        for (index in digits.indices) {
            paint.textSize = height * 1.20f
            while (paint.textSize > 2f && paint.measureText(digits[index]) > width * 0.98f) paint.textSize -= 2f
            sizes[index] = paint.textSize
            paint.getFontMetrics(metrics)
            offsets[index] = -(metrics.ascent + metrics.descent) * 0.5f
        }
    }
}
