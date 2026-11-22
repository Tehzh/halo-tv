package app.orchard.tv.wallpaper

import androidx.media3.common.PlaybackException
import org.junit.Assert.*
import org.junit.Test

class AerialPlaybackTest {
    @Test fun android51RequiresVendorDecoderAndReported4K30() {
        assertTrue(acceptsAerialSdr(22, "OMX.MTK.VIDEO.DECODER.HEVC", true))
        assertFalse(acceptsAerialSdr(22, "OMX.MTK.VIDEO.DECODER.HEVC", false))
        for (codec in listOf("OMX.google.hevc.decoder", "OMX.vendor.HEVC.sw.decoder", "unknown")) {
            assertFalse(codec, acceptsAerialSdr(22, codec, true))
        }
    }

    @Test fun manualSdrDoesNotRequireHdrAndFallsBackWithoutHevc() {
        val oldWithHevc = setOf(AerialQuality.Hd, AerialQuality.Uhd)
        assertEquals(AerialQuality.Uhd, resolveAerialQuality(AerialQuality.Uhd, oldWithHevc))
        assertEquals(AerialQuality.Uhd, resolveAerialQuality(AerialQuality.Auto, oldWithHevc))
        assertEquals(AerialQuality.Hd, resolveAerialQuality(AerialQuality.Uhd, setOf(AerialQuality.Hd)))
        assertEquals(AerialQuality.Hd, resolveAerialQuality(AerialQuality.Hd, oldWithHevc))
        assertEquals(AerialQuality.DolbyVision, resolveAerialQuality(AerialQuality.Auto, AerialQuality.entries.toSet() - AerialQuality.Auto))
    }

    @Test fun decoderFailureCanDropUhdToHdButIoIsNotDecoderFailure() {
        assertTrue(aerialDecoderError(PlaybackException.ERROR_CODE_DECODER_INIT_FAILED))
        assertTrue(aerialDecoderError(PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED))
        assertFalse(aerialDecoderError(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT))
        assertEquals(AerialQuality.Hd, AerialFormats.lower(AerialQuality.Uhd))
    }

    @Test fun legacyTrialOnlyChangesTheExplicitSdrSelection() {
        val hd = setOf(AerialQuality.Hd)
        assertTrue(isUhdAerialOutput(3840, 2160))
        assertTrue(isUhdAerialOutput(4096, 2160))
        assertFalse(isUhdAerialOutput(3840, 1080))
        assertFalse(isUhdAerialOutput(1920, 1080))
        assertEquals(AerialQuality.Uhd, resolveAerialQuality(AerialQuality.Uhd, hd, manualSdrTrial = true))
        for (choice in listOf(AerialQuality.Auto, AerialQuality.Hd, AerialQuality.DolbyVision)) {
            assertEquals(AerialQuality.Hd, resolveAerialQuality(choice, hd, manualSdrTrial = true))
        }
    }

}
