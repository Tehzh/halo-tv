@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package app.orchard.tv.ui.home

import android.content.Context
import android.os.Build
import androidx.core.net.toUri
import androidx.media3.common.C
import androidx.media3.common.DataReader
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.FileDataSource
import androidx.media3.extractor.DefaultExtractorInput
import androidx.media3.extractor.DiscardingTrackOutput
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorOutput
import androidx.media3.extractor.PositionHolder
import androidx.media3.extractor.SeekMap
import androidx.media3.extractor.TrackOutput
import androidx.media3.extractor.mp4.Mp4Extractor
import androidx.media3.extractor.text.SubtitleParser
import app.orchard.tv.system.DeviceProfile
import app.orchard.tv.wallpaper.ScreensaverVideo
import java.io.File

/** Compatibility path for issue #29, not a global SurfaceView replacement. */
internal object LocalVideoSurfacePolicy {
    fun candidate(context: Context): Boolean = DeviceProfile.current(context).localVideoTexture &&
        (DeviceProfile.origin(context) == DeviceProfile.Origin.ThisTv ||
            Build.VERSION.SDK_INT == 30 && Build.HARDWARE == "amlogic")

    /** Confirmed non-DRM, 8-bit AVC only; HDR, 4K and unknown tracks keep SurfaceView. */
    internal fun safeSdr(format: Format): Boolean {
        val profile = Regex("avc[13]\\.([0-9a-fA-F]{2})").find(format.codecs.orEmpty())
            ?.groupValues?.get(1)?.toIntOrNull(16)
        val transfer = format.colorInfo?.colorTransfer
        return format.sampleMimeType == MimeTypes.VIDEO_H264 && profile in setOf(66, 77, 88, 100) &&
            format.width in 1..1920 && format.height in 1..1088 && format.rotationDegrees == 0 && format.drmInitData == null &&
            (transfer == null || transfer == Format.NO_VALUE || transfer == C.COLOR_TRANSFER_SDR || transfer == C.COLOR_TRANSFER_LINEAR)
    }

    private val cache = object : LinkedHashMap<String, Boolean>(64, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Boolean>?) = size > 64
    }

    /** Off main. Read metadata with Media3 only: no retriever or extra video decoder. */
    fun choose(files: List<ScreensaverVideo>): Boolean {
        if (files.isEmpty()) return false
        val until = System.nanoTime() + 1_500_000_000L
        return files.all { film ->
            if (Thread.currentThread().isInterrupted || System.nanoTime() >= until) return false
            val f = film.file
            val key = "${f.absolutePath}:${f.length()}:${f.lastModified()}"
            synchronized(cache) { cache[key] } ?: run {
                val safe = runCatching { probe(f)?.let(::safeSdr) == true }.getOrDefault(false)
                synchronized(cache) { cache[key] = safe }
                safe
            }
        }
    }

    /** Bounded MP4 metadata reads, including moov-at-end files. */
    private fun probe(file: File): Format? {
        var format: Format? = null
        var taken = false
        var bytes = 0
        val until = System.nanoTime() + 300_000_000L
        val extractor = Mp4Extractor(SubtitleParser.Factory.UNSUPPORTED, 0)
        extractor.init(object : ExtractorOutput {
            override fun track(id: Int, type: Int): TrackOutput = if (type == C.TRACK_TYPE_VIDEO && !taken) {
                taken = true
                object : TrackOutput by DiscardingTrackOutput() {
                    override fun format(f: Format) { format = f }
                }
            } else DiscardingTrackOutput()
            override fun endTracks() = Unit
            override fun seekMap(seekMap: SeekMap) = Unit
        })
        try {
            val seek = PositionHolder()
            var position = 0L
            repeat(4) {
                val source = FileDataSource()
                try {
                    source.open(DataSpec.Builder().setUri(file.toUri()).setPosition(position).build())
                    val reader = DataReader { buffer, offset, length ->
                        check(!Thread.currentThread().isInterrupted && System.nanoTime() < until && bytes < 2 * 1024 * 1024)
                        source.read(buffer, offset, minOf(length, 2 * 1024 * 1024 - bytes)).also { if (it > 0) bytes += it }
                    }
                    val input = DefaultExtractorInput(reader, position, file.length())
                    while (format == null) {
                        check(!Thread.currentThread().isInterrupted && System.nanoTime() < until)
                        val result = extractor.read(input, seek)
                        if (result == Extractor.RESULT_END_OF_INPUT) return format
                        if (result == Extractor.RESULT_SEEK) { position = seek.position; break }
                    }
                    if (format != null) return format
                } finally { source.close() }
            }
            return null
        } finally { extractor.release() }
    }
}
