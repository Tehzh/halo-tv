package app.orchard.tv.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import app.orchard.tv.system.DiagLog
import app.orchard.tv.ui.home.unreadable

/** A stale first-frame callback must never uncover a newly recreated video surface. */
internal class WallpaperReturnGate {
    private var epoch = 0L
    var covered = true; private set
    var frameReady = false; private set
    fun cover(): Long { covered = true; frameReady = false; return ++epoch }
    fun token(): Long = epoch
    fun rendered(): Long { frameReady = true; return epoch }
    fun reveal(token: Long): Boolean {
        if (token != epoch || !frameReady || !covered) return false
        covered = false
        return true
    }
    fun expire(token: Long): Boolean {
        if (token != epoch || !covered) return false
        covered = false
        return true
    }
}

/**
 * Native still-frame cover for a returning Activity. Compose's retained display list may still
 * contain a transparent video hole before its first resumed frame. Window visibility callbacks
 * cover that hole synchronously, without waiting for Compose or keeping a decoder in background.
 * The bitmap is shared with Home's existing backdrop; no second full-screen copy is allocated.
 */
internal class WallpaperReturnShield(
    context: Context,
    content: View,
    private val keepPosterUntilFirstFrame: Boolean = false,
    private val protectInitialSurface: Boolean = false,
) : FrameLayout(context) {
    private val gate = WallpaperReturnGate()
    private val picture = ImageView(context).apply {
        scaleType = ImageView.ScaleType.CENTER_CROP
        isFocusable = false
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    private var initialSurfacePending = protectInitialSurface
    private var frame: Bitmap? = null
    private var windowVisible = false
    private var expiry: Runnable? = null

    init {
        isFocusable = false
        if (initialSurfacePending) picture.setBackgroundColor(Color.BLACK)
        addView(content, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(picture, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    fun fallback(bitmap: Bitmap?) {
        if (bitmap === frame || bitmap?.isRecycled == true) return
        // Do not replace the last good backdrop with a black/green vendor-plane capture.
        if (bitmap != null && unreadable(bitmap) != null) return
        frame = bitmap
        picture.setImageBitmap(bitmap)
    }

    fun awaitingInitialFrame(): Boolean = initialSurfacePending

    fun cover() {
        gate.cover()
        expiry?.let(::removeCallbacks)
        picture.animate().cancel()
        if (protectInitialSurface && !initialSurfacePending) picture.setBackgroundColor(Color.TRANSPARENT)
        picture.alpha = 1f
        picture.visibility = View.VISIBLE
        scheduleExpiry()
    }

    private fun scheduleExpiry() {
        expiry?.let(::removeCallbacks)
        if (!windowVisible || !isAttachedToWindow || !gate.covered) return
        val token = gate.token()
        expiry = Runnable {
            // Some old SurfaceViews show a stale hardware buffer while their decoder starts.
            // Keep a real poster on those outputs until Media3 submits a frame.
            if (initialSurfacePending) {
                DiagLog.i("local wallpaper startup cover held; poster=${frame != null}")
                return@Runnable
            }
            if (keepPosterUntilFirstFrame && frame != null) return@Runnable
            if (windowVisible && isAttachedToWindow && gate.expire(token)) {
                picture.animate().cancel()
                picture.visibility = View.INVISIBLE
                DiagLog.i("wallpaper return cover expired; leaving Home visible")
            }
        }.also { postDelayed(it, 1_500L) }
    }

    fun renderedFrame() {
        val token = gate.rendered()
        if (initialSurfacePending) {
            // Older hardware compositors can present a buffer later than the player submits it.
            if (!windowVisible || !isAttachedToWindow) return
            postDelayed({ revealAfterCommit(token) }, INITIAL_SETTLE_MS)
        } else revealAfterCommit(token)
    }

    private fun revealAfterCommit(token: Long) {
        // Media3 reports a submitted buffer; give SurfaceFlinger its next commit before dropping
        // the cover. This is a one-shot handoff, never an idle animation or a polling loop.
        postOnAnimation {
            postOnAnimation {
                if (windowVisible && isAttachedToWindow && gate.reveal(token)) {
                    expiry?.let(::removeCallbacks)
                    if (initialSurfacePending) {
                        initialSurfacePending = false
                        DiagLog.i("local wallpaper startup cover released; poster=${frame != null}")
                    }
                    // Blend only this one handoff, not the whole UI/video layer. A hard cut from
                    // the retained frame to the new decoder buffer looked like a flash.
                    picture.animate().alpha(0f).setDuration(140).withEndAction {
                        if (gate.token() == token && !gate.covered) {
                            picture.visibility = View.INVISIBLE
                            if (protectInitialSurface) picture.setBackgroundColor(Color.TRANSPARENT)
                        }
                    }.start()
                }
            }
        }
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        val visible = visibility == View.VISIBLE
        if (windowVisible && !visible) cover()
        windowVisible = visible
        // Some TVs submit the first buffer before making the returning window visible.
        // Preserve that readiness rather than invalidating its sole first-frame callback.
        if (visible && gate.frameReady) renderedFrame()
        if (visible) scheduleExpiry() else expiry?.let(::removeCallbacks)
    }

    private companion object {
        const val INITIAL_SETTLE_MS = 250L
    }

    override fun onDetachedFromWindow() {
        cover()
        expiry?.let(::removeCallbacks)
        super.onDetachedFromWindow()
    }
}
