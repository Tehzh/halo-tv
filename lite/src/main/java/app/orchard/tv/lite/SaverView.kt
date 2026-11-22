package app.orchard.tv.lite

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.VideoView
import app.orchard.tv.lite.Ui.textPx
import kotlin.random.Random

/**
 * The screensaver, in the full app's look (ui/home/Screensaver.kt), over everything with Home
 * hidden beneath it.
 *
 * Photos and online pictures reuse the same slideshow, with still images on low-end devices.
 * Local videos use the system player. Missing or unplayable media falls back to the flip clock.
 *
 * The time shows over the films and photos when Personalize says so.
 */
class SaverView(context: Context, mode: ScreensaverMode, private val wallpaper: Bitmap?) : FrameLayout(context) {
    private val handler = Handler(Looper.getMainLooper())
    private val time = context.label("", 33.8f, 600, 0xEBFFFFFF.toInt(), spacingEm = 0.05f)
    private val caption = TextView(context)
    private val shade = Shade(context)
    private var video: VideoView? = null
    private var films: List<String> = emptyList()
    private var next = 0
    private var failures = 0
    @Volatile private var released = false
    /** Films from the phone page, played from a file rather than through the proxy. */
    private val photos = mode == ScreensaverMode.Photos || mode == ScreensaverMode.OnlinePictures
    private var flipView: FlipClockView? = null
    private val showTime = LiteSettings.screensaverClock && photos

    private val pictures = when (mode) {
        ScreensaverMode.Photos -> Wallpapers.saverPictures()
        ScreensaverMode.OnlinePictures -> Wallpapers.onlineSaverPictures()
        else -> emptyList()
    }
    private val frames = arrayOfNulls<ImageView>(2)
    private var front = 0
    private var kenBurns: android.animation.Animator? = null
    private val loader = java.util.concurrent.Executors.newSingleThreadExecutor()
    /** No picture after [STALL_MS]: a slow network or a film the decoder never starts. */
    private val stalled = Runnable { fail() }

    private var driftSlot = Long.MIN_VALUE
    private val tick = object : Runnable {
        override fun run() {
            if (released) return
            time.text = timeText(context)
            flipView?.let {
                val now = java.util.Date()
                val digits = java.text.SimpleDateFormat(if (LiteSettings.flipClock24Hour) "HHmm" else "hhmm", java.util.Locale.ROOT).format(now)
                val date = java.text.DateFormat.getDateInstance(java.text.DateFormat.FULL).format(now)
                val weather = Weather.line(context)?.let { w -> listOfNotNull(w.place, "${w.celsius}°", w.detail).joinToString(" · ") }
                val lunar = if (LiteSettings.showLunarDate) Lunar.text(java.util.Calendar.getInstance()) else null
                val second = java.text.SimpleDateFormat("ss", java.util.Locale.ROOT).format(now)
                it.setContents(digits, listOfNotNull(date, lunar, weather).joinToString("    ·    "),
                    if (LiteSettings.flipClockSeconds) second else null, LiteSettings.flipClockSecondsStyle)
            }
            val now = System.currentTimeMillis()
            // Every two minutes the words wander a little.
            if (now / 120_000 != driftSlot) {
                driftSlot = now / 120_000
                if (flipView != null) {
                    flipView?.translationX = Ui.px((Random.nextFloat() - 0.5f) * 12f)
                    flipView?.translationY = Ui.px((Random.nextFloat() - 0.5f) * 8f)
                } else drift()
            }
            val interval = if (flipView != null && LiteSettings.flipClockSeconds) 1_000L else 60_000L
            handler.postDelayed(this, interval - now % interval + 50)
        }
    }

    fun refreshTime() {
        handler.removeCallbacks(tick)
        tick.run()
    }

    init {
        setBackgroundColor(0xFF000000.toInt())
        keepScreenOn = true
        time.setShadowLayer(Ui.px(4f), 0f, 0f, 0x66000000)
        caption.apply {
            setTextColor(0xEBFFFFFF.toInt())
            textPx(19f)
            typeface = Ui.font(600, 32f)
            if (Build.VERSION.SDK_INT >= 21) letterSpacing = -0.01f
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            maxWidth = Ui.pxi(620f)
            includeFontPadding = false
            setShadowLayer(Ui.px(4f), 0f, 0f, 0x66000000)
        }
        when (mode) {
            // The owner's own films carry no caption, as in the full app.
            ScreensaverMode.Videos -> films = LocalSaverSelection.filter(context, true, ScreensaverVideos.all(context).shuffled()) { it.id }.map { it.file.toURI().toString() }
            else -> Unit
        }
        when {
            photos -> showPhotos()
            films.isNotEmpty() -> showFilms()
            else -> showFlipClock()
        }
        if (flipView == null) refreshTime()
    }

    private fun showFlipClock() {
        if (released || flipView != null) return
        handler.removeCallbacks(stalled)
        video?.apply {
            setOnPreparedListener(null)
            setOnCompletionListener(null)
            setOnErrorListener(null)
            stopPlayback()
        }
        video = null
        kenBurns?.cancel()
        kenBurns = null
        removeAllViews()
        flipView = FlipClockView(context).also {
            addView(it, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        }
        refreshTime()
    }

    private fun showFilms() {
        val v = VideoView(context)
        video = v
        addView(v, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT, Gravity.CENTER))
        addWords()
        v.setOnPreparedListener { mp ->
            if (released || video !== v) return@setOnPreparedListener
            failures = 0
            handler.removeCallbacks(stalled)
            mp.setVolume(0f, 0f)
            mp.start()
        }
        v.setOnCompletionListener { play() }
        v.setOnErrorListener { _: MediaPlayer, what: Int, extra: Int ->
            Log.i("AerialLite", "film failed: $what/$extra")
            fail()
            true
        }
        play()
    }

    /** The shade, the time (when chosen) and the caption over the pictures. */
    private fun addWords() {
        addView(shade, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        if (showTime) addView(time, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.START).apply {
            topMargin = Ui.pxi(40f); leftMargin = Ui.pxi(56f)
        })
        addView(caption, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.START).apply {
            leftMargin = Ui.pxi(56f); bottomMargin = Ui.pxi(44f)
        })
    }

    private fun play() {
        if (released || films.isEmpty()) return
        val v = video ?: return
        val film = films[next % films.size]
        next++
        setCaption("")
        handler.removeCallbacks(stalled)
        handler.postDelayed(stalled, STALL_MS)
        v.setVideoURI(Uri.parse(film))
    }

    // ---- photos ----


    private fun showPhotos() {
        if (pictures.isEmpty()) { showFlipClock(); return }
        for (i in 0..1) {
            val f = ImageView(context).apply { scaleType = ImageView.ScaleType.CENTER_CROP; alpha = 0f }
            frames[i] = f
            addView(f, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        }
        addWords()
        nextPhoto()
    }

    /** The next picture, decoded off the main thread, faded in over the last. */
    private fun nextPhoto() {
        if (released || pictures.isEmpty()) return
        val wp = pictures[next % pictures.size]
        next++
        loader.execute {
            if (released) return@execute
            val b = runCatching { Wallpapers.picture(wp, Ui.screenW, Ui.screenH, whole = true) }.getOrNull()
            handler.post {
                // A decode may finish after Home removes this saver; never revive its timer or views.
                if (released) { b?.recycle(); return@post }
                if (b == null) {
                    failures++
                    if (failures >= pictures.size) { showFlipClock(); return@post }
                    handler.postDelayed(::nextPhoto, 1000); return@post
                }
                failures = 0
                val back = frames[front]
                front = 1 - front
                val f = frames[front] ?: run { b.recycle(); return@post }
                (f.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap?.takeIf { it !== wallpaper }?.recycle()
                f.setImageBitmap(b)
                setCaption(if (wp.kind == WallpaperKind.Daily) wp.title else "")
                val smooth = LiteSettings.full(isHardwareAccelerated)
                // Over the last picture, under the words.
                removeView(f)
                addView(f, 1, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
                if (smooth) {
                    // Ken Burns: from 1.04 to 1.12 over 20 s, drifting one way or the other.
                    val dir = if (Random.nextBoolean()) 1f else -1f
                    kenBurns?.cancel()
                    kenBurns = android.animation.ObjectAnimator.ofPropertyValuesHolder(
                        f,
                        android.animation.PropertyValuesHolder.ofFloat(View.SCALE_X, 1.04f, 1.12f),
                        android.animation.PropertyValuesHolder.ofFloat(View.SCALE_Y, 1.04f, 1.12f),
                        android.animation.PropertyValuesHolder.ofFloat(View.TRANSLATION_X, -dir * Ui.px(20f), dir * Ui.px(20f)),
                    ).apply { duration = 20_000; interpolator = android.view.animation.LinearInterpolator(); start() }
                    f.alpha = 0f
                    f.animate().alpha(1f).setDuration(2000).start()
                    back?.animate()?.alpha(0f)?.setDuration(2000)?.start()
                } else {
                    f.alpha = 1f
                    back?.alpha = 0f
                }
                if (pictures.size > 1) handler.postDelayed(::nextPhoto, PHOTO_MS)
            }
        }
    }

    /** One name after the other, as the full app's: the old fades out, then the new fades in. */
    private fun setCaption(text: String) {
        if (!caption.isHardwareAccelerated || caption.text.isNullOrEmpty()) { caption.text = text; return }
        caption.animate().alpha(0f).setDuration(400).setListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) {
                caption.animate().setListener(null)
                caption.text = text
                caption.animate().alpha(1f).setStartDelay(0).setDuration(600).start()
            }
        }).start()
    }

    /** The full app's saverDrift: up to 24 across and 14 down, eased over six seconds. */
    private fun drift() {
        val dx = Ui.px(24f * (Random.nextFloat() * 2 - 1))
        val dy = Ui.px(14f * (Random.nextFloat() * 2 - 1))
        for ((v, sign) in listOf(time to 1f, caption to -1f)) {
            if (v.isHardwareAccelerated) v.animate().translationX(dx).translationY(sign * dy).setDuration(6000).start()
            else { v.translationX = dx; v.translationY = sign * dy }
        }
    }

    private fun fail() {
        if (released || flipView != null) return
        handler.removeCallbacks(stalled)
        failures++
        if (failures >= 3) showFlipClock() else play()
    }


    fun release() {
        if (released) return
        released = true
        handler.removeCallbacksAndMessages(null)
        video?.apply {
            setOnPreparedListener(null); setOnCompletionListener(null); setOnErrorListener(null)
            stopPlayback()
        }
        video = null
        loader.shutdownNow()
        kenBurns?.cancel(); kenBurns = null
        caption.animate().setListener(null).cancel()
        time.animate().cancel()
        for (f in frames) f?.let { frame ->
            frame.animate().cancel()
            val bitmap = (frame.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
            frame.setImageDrawable(null)
            bitmap?.takeIf { it !== wallpaper && !it.isRecycled }?.recycle()
        }
    }

    /** Just enough shade under the words to read them over bright sky or snow. */
    private class Shade(context: Context) : View(context) {
        private val top = Paint()
        private val bottom = Paint()
        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            top.shader = LinearGradient(0f, 0f, 0f, h * 0.18f, 0x4D000000, 0, Shader.TileMode.CLAMP)
            bottom.shader = LinearGradient(0f, h * 0.7f, 0f, h.toFloat(), 0, 0x73000000, Shader.TileMode.CLAMP)
        }
        override fun onDraw(canvas: Canvas) {
            canvas.drawRect(0f, 0f, width.toFloat(), height * 0.18f, top)
            canvas.drawRect(0f, height * 0.7f, width.toFloat(), height.toFloat(), bottom)
        }
    }

    private companion object {
        const val STALL_MS = 20_000L
        const val PHOTO_MS = 18_000L
    }
}
