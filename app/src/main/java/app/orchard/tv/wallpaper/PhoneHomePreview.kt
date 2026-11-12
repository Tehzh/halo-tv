package app.orchard.tv.wallpaper

import android.app.Activity
import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.PixelCopy
import androidx.annotation.RequiresApi
import java.io.ByteArrayOutputStream
import java.lang.ref.WeakReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/** A phone-requested still of our own visible Home. Never records another application's window. */
internal object PhoneHomePreview {
    private var owner = WeakReference<Activity>(null)
    private var visible: (() -> Boolean)? = null
    @Volatile private var cached: ByteArray? = null
    @Volatile private var cachedAt = 0L
    @Volatile private var cachedWallpaper = ""
    private val gate = kotlinx.coroutines.sync.Mutex()

    fun bind(activity: Activity, canCapture: () -> Boolean) {
        owner = WeakReference(activity)
        visible = canCapture
    }
    fun unbind(activity: Activity) {
        if (owner.get() === activity) { owner.clear(); visible = null; cached = null }
    }
    suspend fun capture(): ByteArray? {
        if (Build.VERSION.SDK_INT < 26 || !gate.tryLock()) return null
        try {
            if (cachedWallpaper == Wallpapers.selectedId && SystemClock.elapsedRealtime() - cachedAt < 5_000) return cached
            val image = withTimeoutOrNull(1_500) { withContext(Dispatchers.Main) { copyHome() } } ?: return null
            return withContext(Dispatchers.IO) {
                try {
                    val out = ByteArrayOutputStream()
                    image.compress(Bitmap.CompressFormat.JPEG, 86, out)
                    out.toByteArray().also { cached = it; cachedWallpaper = Wallpapers.selectedId; cachedAt = SystemClock.elapsedRealtime() }
                } finally { image.recycle() }
            }
        } finally { gate.unlock() }
    }
    @RequiresApi(26)
    private suspend fun copyHome(): Bitmap? {
        val activity = owner.get() ?: return null
        if (activity.isDestroyed || visible?.invoke() != true || !activity.window.decorView.isShown) return null
        val target = Bitmap.createBitmap(640, 360, Bitmap.Config.ARGB_8888)
        return suspendCancellableCoroutine { continuation ->
            try {
                PixelCopy.request(activity.window, target, { result ->
                    if (result == PixelCopy.SUCCESS && continuation.isActive) continuation.resume(target)
                    else { target.recycle(); if (continuation.isActive) continuation.resume(null) }
                }, Handler(Looper.getMainLooper()))
            } catch (_: Exception) { target.recycle(); if (continuation.isActive) continuation.resume(null) }
        }
    }
}
