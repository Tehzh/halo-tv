package app.orchard.tv.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView

/** A stale first-frame callback must never uncover a newly recreated video surface. */
internal class WallpaperReturnGate {
    private var epoch = 0L
    var covered = true; private set
    var frameReady = false; private set
    fun cover(): Long { covered = true; frameReady = false; return ++epoch }
    fun token(): Long = epoch
    fun rendered(): Long { frameReady = true; return epoch }
    fun reveal(token: Long): Boolean {
        if (token != epoch || !frameReady) return false
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
internal class WallpaperReturnShield(context: Context, content: View) : FrameLayout(context) {
    private val gate = WallpaperReturnGate()
    private val picture = ImageView(context).apply {
        scaleType = ImageView.ScaleType.CENTER_CROP
        isFocusable = false
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    private var frame: Bitmap? = null
    private var windowVisible = false

    init {
        isFocusable = false
        addView(content, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(picture, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    fun fallback(bitmap: Bitmap?) {
        if (bitmap === frame || bitmap?.isRecycled == true) return
        frame = bitmap
        picture.setImageBitmap(bitmap)
    }

    fun cover() {
        gate.cover()
        picture.visibility = View.VISIBLE
    }

    fun renderedFrame() {
        val token = gate.rendered()
        // Media3 reports a submitted buffer; give SurfaceFlinger its next commit before dropping
        // the cover. This is a one-shot handoff, never an idle animation or a polling loop.
        postOnAnimation {
            postOnAnimation {
                if (windowVisible && isAttachedToWindow && gate.reveal(token)) picture.visibility = View.INVISIBLE
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
    }

    override fun onDetachedFromWindow() {
        cover()
        super.onDetachedFromWindow()
    }
}
