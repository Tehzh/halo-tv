package app.orchard.tv.wallpaper

import androidx.annotation.OptIn
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import app.orchard.tv.system.DiagLog
import java.util.Locale

/** Lollipop/Marshmallow expose a vendor component name but lack isHardwareAccelerated. */
internal fun acceptsAerialSdr(sdk: Int, codecName: String, sizeAndRate: Boolean): Boolean {
    if (sdk < 21 || !sizeAndRate) return false
    if (sdk >= 24) return true // Preserve the existing capability check on these releases.
    val name = codecName.lowercase(Locale.ROOT)
    return name.startsWith("omx.") && !name.startsWith("omx.google.") &&
        !name.startsWith("omx.android.") && !name.startsWith("omx.ffmpeg.") &&
        !name.startsWith("omx.pv.") && !name.startsWith("omx.k3.ffmpeg.") &&
        ".sw." !in name && ".software." !in name && "soft" !in name && "ffmpeg" !in name
}

internal fun isUhdAerialOutput(width: Int, height: Int): Boolean = width >= 3840 && height >= 2160

internal fun resolveAerialQuality(
    chosen: AerialQuality, supported: Set<AerialQuality>, manualSdrTrial: Boolean = false,
): AerialQuality =
    when {
        manualSdrTrial && chosen == AerialQuality.Uhd -> AerialQuality.Uhd
        chosen == AerialQuality.Hd -> AerialQuality.Hd
        chosen != AerialQuality.Auto && chosen in supported -> chosen
        chosen == AerialQuality.Auto && AerialQuality.DolbyVision in supported -> AerialQuality.DolbyVision
        AerialQuality.Uhd in supported -> AerialQuality.Uhd
        else -> AerialQuality.Hd
    }

internal fun aerialDecoderError(error: Int): Boolean = error in
    PlaybackException.ERROR_CODE_DECODER_INIT_FAILED..PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED

/** A playlist kept across film changes may include a missing higher quality file. */
internal fun Aerial.fileQuality(request: AerialQuality): AerialQuality =
    if (url(request) == url) AerialQuality.Hd else request

/** Last attempted and last rendered format per player surface, with no URL or network identifiers. */
internal class AerialPlayback(private val surface: String) {
    private var selected = AerialQuality.Auto
    private var target = AerialQuality.Hd
    private var reason = "none"
    private var current: Pair<String, Int>? = null

    fun open(choice: AerialQuality, quality: AerialQuality, fallback: String?) {
        selected = choice
        target = quality
        reason = fallback ?: "none"
        current = null
        publish("awaiting_frame")
    }

    fun fallback(error: String) { reason = error; publish("retrying") }

    @OptIn(UnstableApi::class)
    fun frame(player: ExoPlayer, films: List<Aerial>) {
        val size = player.videoSize
        if (size.width <= 0 || size.height <= 0) return
        val id = player.currentMediaItem?.mediaId ?: return
        val source = films.firstOrNull { it.id == id }?.fileQuality(target) ?: target
        val mime = player.videoFormat?.sampleMimeType ?: "unknown"
        val value = "${source.key} ${size.width}x${size.height} $mime" +
            if (source != target) " (catalogue_missing_${target.key})" else ""
        val sample = value to player.currentMediaItemIndex
        if (sample == current) return
        current = sample
        publish(value)
    }

    private fun publish(actual: String) {
        val status = "selected=${selected.key} target=${target.key} actual=$actual reason=$reason"
        when (surface) {
            "wallpaper" -> wallpaperStatus = status
            else -> saverStatus = status
        }
        DiagLog.i("aerial $surface: $status")
    }

    companion object {
        @Volatile private var wallpaperStatus = "not_started"
        @Volatile private var saverStatus = "not_started"
        fun snapshot() = "wallpaper: $wallpaperStatus; saver: $saverStatus"
    }
}
