package app.orchard.tv.wallpaper

import android.content.Context
import android.graphics.Point
import android.hardware.display.DisplayManager
import app.orchard.tv.settings.AerialSettings
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.os.Build
import android.view.Display
import android.view.WindowManager
import androidx.annotation.RequiresApi
import androidx.core.content.getSystemService
import org.json.JSONObject
import java.util.Locale

/**
 * Apple TV's aerial screensaver films. Aerial ships only the catalogue (assets/aerials.json, built
 * from Apple's public resources-16.tar): addresses and place names. The films themselves stream from
 * Apple's servers while the screensaver runs, the way the Apple TV plays them.
 */
class Aerial(
    val id: String,
    val label: String,
    val category: String,
    /** 1080p H.264, about 10 Mbps: plays everywhere. */
    val url: String,
    /** 4K SDR HEVC, about 12 Mbps. */
    val uhd: String,
    /** 4K Dolby Vision (profile 5, HEVC 10-bit), about 24 Mbps. */
    val dolbyVision: String,
    /** (second, caption) pairs: tvOS changes the place name as the camera moves on. */
    val captions: List<Pair<Int, String>>,
    val preview: String = "",
) {
    fun captionAt(seconds: Int): String = captions.lastOrNull { it.first <= seconds }?.second ?: label

    fun url(quality: AerialQuality): String = when (quality) {
        AerialQuality.DolbyVision -> dolbyVision
        AerialQuality.Uhd -> uhd
        else -> url
    }
}

/** Which of Apple's files the screensaver streams. Auto takes the best this TV can play. */
enum class AerialQuality(val key: String) {
    Auto("auto"), Hd("1080p"), Uhd("4k"), DolbyVision("dolby_vision");

    companion object {
        fun of(key: String?) = entries.firstOrNull { it.key == key } ?: Auto
    }
}

/**
 * What this TV's decoders and display can play, asked once. A TV's UI is usually 1080p whatever
 * its panel (Xiaomi MiTV-MFTR0 reports a single 1920×1080 mode), so the panel cannot be asked;
 * the decoders can. 4K SDR costs about what the 1080p H.264 files do (12 against 10 Mbps), so a
 * TV that decodes it gets it. Dolby Vision needs a decoder for profile 5 at 4K and a display that
 * lists Dolby Vision: Apple's HDR files are profile 5, which has no HDR10 base layer to fall back
 * on, and decoded by anything else they come out green and purple.
 */
object AerialFormats {
    private data class Capabilities(val formats: Set<AerialQuality>, val vendorHevc: Boolean)

    @Volatile private var probed: Capabilities? = null
    @Volatile private var legacyTrialFailure: String? = null

    private fun capabilities(context: Context): Capabilities = probed ?: synchronized(this) {
        probed ?: probe(context).also { probed = it }
    }

    fun supported(context: Context): Set<AerialQuality> = capabilities(context).formats

    fun hasLegacySdrTrial(context: Context): Boolean {
        val sdk = Build.VERSION.SDK_INT
        if (sdk !in 21..23) return false
        val caps = capabilities(context)
        if (!caps.vendorHevc || AerialQuality.Uhd in caps.formats) return false
        return runCatching {
            context.getSystemService<DisplayManager>()?.displays?.any { display ->
                val size = Point()
                @Suppress("DEPRECATION")
                display.getRealSize(size)
                isUhdAerialOutput(size.x, size.y) ||
                    (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && display.mode.let {
                        isUhdAerialOutput(it.physicalWidth, it.physicalHeight)
                    })
            } == true
        }.getOrDefault(false)
    }

    fun resetLegacyTrial() { legacyTrialFailure = null }

    /** A failed manual trial stays capped across wallpaper and screensaver until selected again. */
    fun recordLegacyFailure(context: Context, chosen: AerialQuality, playing: AerialQuality, reason: String): Boolean {
        if (chosen != AerialQuality.Uhd || playing != AerialQuality.Uhd || !hasLegacySdrTrial(context)) return false
        legacyTrialFailure = reason
        return true
    }

    fun legacyTrialStatus(context: Context): String = when {
        !hasLegacySdrTrial(context) -> "unavailable"
        legacyTrialFailure != null -> "fallback:$legacyTrialFailure"
        else -> "manual_available"
    }

    fun policyReason(context: Context, chosen: AerialQuality, actual: AerialQuality): String? = when {
        chosen == AerialQuality.Uhd && hasLegacySdrTrial(context) ->
            if (actual == AerialQuality.Uhd) "legacy_4k_trial" else legacyTrialFailure ?: "decoder_capability"
        chosen == AerialQuality.Auto || chosen == actual -> null
        else -> "decoder_capability"
    }

    /** A TV with incomplete capabilities may try SDR only when the user selects it. */
    fun resolve(context: Context, chosen: AerialQuality): AerialQuality =
        resolveAerialQuality(chosen, supported(context),
            chosen == AerialQuality.Uhd && hasLegacySdrTrial(context) && legacyTrialFailure == null)

    /**
     * What the aerial wallpaper plays: as chosen, but never Dolby Vision. A TV in Dolby Vision mode
     * shows the tiles over the film with Dolby's picture settings. A screensaver that carries the
     * wallpaper's film on plays this too (Screensaver), so the two are one, with no switch.
     */
    fun forWallpaper(context: Context): AerialQuality =
        resolve(context, AerialSettings.aerialQuality).let { if (it == AerialQuality.DolbyVision) AerialQuality.Uhd else it }

    /**
     * Whether a screensaver of aerials over the aerial wallpaper carries the wallpaper's film on, in
     * its format. Auto does: going to Dolby Vision from the wallpaper's SDR showed black for 0.9–3.5 s
     * (Xiaomi MiTV-MFTR0, one 4K decoder) and most TVs blink as they switch modes. Dolby Vision
     * chosen by hand is played whatever that costs, from the wallpaper's film and moment.
     */
    fun screensaverCarriesWallpaper(context: Context): Boolean =
        !(AerialSettings.aerialQuality == AerialQuality.DolbyVision && AerialQuality.DolbyVision in supported(context))

    /** What a screensaver of aerials starts in; [overWallpaper]: it begins from the aerial wallpaper's film. */
    fun forScreensaver(context: Context, overWallpaper: Boolean): AerialQuality =
        if (overWallpaper && screensaverCarriesWallpaper(context)) forWallpaper(context)
        else resolve(context, AerialSettings.aerialQuality)

    /** One step down, after a decoder failed on [quality]. */
    fun lower(quality: AerialQuality): AerialQuality = when (quality) {
        AerialQuality.DolbyVision -> AerialQuality.Uhd
        else -> AerialQuality.Hd
    }

    private fun probe(context: Context): Capabilities {
        val found = mutableSetOf(AerialQuality.Hd)
        val codecs = runCatching { MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.filter { !it.isEncoder } }.getOrDefault(emptyList())
        fun caps(mime: String) = codecs.mapNotNull { info ->
            runCatching {
                if (info.supportedTypes.any { it.equals(mime, ignoreCase = true) }) info.getCapabilitiesForType(mime) else null
            }.getOrNull()?.let { info to it }
        }
        // VideoCapabilities is available from API 21. In particular, a 4K HDMI mode says
        // nothing about whether this firmware can decode a 3840×2160 HEVC stream.
        val sdk = Build.VERSION.SDK_INT
        val hevc = caps(MediaFormat.MIMETYPE_VIDEO_HEVC)
        val vendorHevc = sdk in 21..23 && hevc.any { (codec, _) -> acceptsAerialSdr(sdk, codec.name, true) }
        val uhd = hevc.any { (codec, c) ->
            runCatching {
                val sizeAndRate = c.videoCapabilities?.areSizeAndRateSupported(3840, 2160, 30.0) == true
                acceptsAerialSdr(sdk, codec.name, sizeAndRate)
            }.getOrDefault(false)
        }
        if (uhd) found += AerialQuality.Uhd
        if (!uhd || Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return Capabilities(found, vendorHevc)
        val dvDecoder = caps(MediaFormat.MIMETYPE_VIDEO_DOLBY_VISION).any { (_, c) ->
            c.profileLevels.any {
                it.profile == MediaCodecInfo.CodecProfileLevel.DolbyVisionProfileDvheStn &&
                    it.level >= MediaCodecInfo.CodecProfileLevel.DolbyVisionLevelUhd30
            }
        }
        if (dvDecoder && supportsDolbyVisionDisplay(context)) found += AerialQuality.DolbyVision
        return Capabilities(found, vendorHevc)
    }

    @RequiresApi(Build.VERSION_CODES.N)
    @Suppress("DEPRECATION")
    private fun supportsDolbyVisionDisplay(context: Context): Boolean = runCatching {
        context.getSystemService<WindowManager>()?.defaultDisplay?.hdrCapabilities?.supportedHdrTypes
            ?.contains(Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION) == true
    }.getOrDefault(false)
}

object Aerials {
    @Volatile
    private var cache: List<Aerial>? = null

    fun all(context: Context): List<Aerial> = cache ?: synchronized(this) {
        cache ?: load(context).also { cache = it }
    }

    private fun load(context: Context): List<Aerial> {
        val zh = Locale.getDefault().language == "zh"
        val root = JSONObject(context.assets.open("aerials.json").bufferedReader().use { it.readText() })
        val arr = root.getJSONArray("videos")
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val caps = o.getJSONArray("captions")
            Aerial(
                id = o.getString("id"),
                label = o.getString("label"),
                category = o.getString("category"),
                url = o.getString("url"),
                uhd = o.optString("uhd").ifEmpty { o.getString("url") },
                dolbyVision = o.optString("dv").ifEmpty { o.getString("url") },
                preview = o.optString("preview"),
                captions = (0 until caps.length()).map { j ->
                    val c = caps.getJSONArray(j)
                    c.getInt(0) to (if (zh) c.getString(1) else c.getString(2)).replace(' ', ' ')
                },
            )
        }
    }
}
