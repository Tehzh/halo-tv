@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package app.orchard.tv.ui.home

import androidx.media3.common.C
import androidx.media3.common.ColorInfo
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import app.orchard.tv.wallpaper.ScreensaverVideo
import app.orchard.tv.system.DeviceProfile
import android.os.Build
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers
import org.junit.Rule
import org.junit.Assert.*
import org.junit.Test
import org.junit.rules.TemporaryFolder

@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [28])
class LocalVideoSurfacePolicyTest {
    @get:Rule val temp = TemporaryFolder()
    private fun sdr() = Format.Builder().setSampleMimeType(MimeTypes.VIDEO_H264)
        .setCodecs("avc1.640028").setWidth(1920).setHeight(1080).build()
    @Test fun reportedAvcHighEightBit1080pIsEligible() {
        assertTrue(LocalVideoSurfacePolicy.safeSdr(sdr()))
    }
    @Test fun fourKHevcHighTenAndUnknownFormatsKeepOriginalOutput() {
        assertFalse(LocalVideoSurfacePolicy.safeSdr(sdr().buildUpon().setWidth(3840).setHeight(2160).build()))
        assertFalse(LocalVideoSurfacePolicy.safeSdr(sdr().buildUpon().setSampleMimeType(MimeTypes.VIDEO_H265).build()))
        assertFalse(LocalVideoSurfacePolicy.safeSdr(sdr().buildUpon().setCodecs("avc1.6E0028").build()))
        assertFalse(LocalVideoSurfacePolicy.safeSdr(Format.Builder().build()))
    }
    @Test fun explicitHdrTransfersCannotUseCompatibilityOutput() {
        for (transfer in listOf(C.COLOR_TRANSFER_ST2084, C.COLOR_TRANSFER_HLG)) {
            val color = ColorInfo.Builder().setColorTransfer(transfer).build()
            assertFalse(LocalVideoSurfacePolicy.safeSdr(sdr().buildUpon().setColorInfo(color).build()))
        }
    }
    @Test fun rotatedFilesRetainTheExistingRotationAndCropPath() {
        assertFalse(LocalVideoSurfacePolicy.safeSdr(sdr().buildUpon().setRotationDegrees(90).build()))
    }
    @Test @Config(sdk = [30])
    fun builtInWorkaroundIsLimitedToReportedDeviceHardwareAndOs() {
        val context = RuntimeEnvironment.getApplication()
        ReflectionHelpers.setStaticField(Build::class.java, "MODEL", "MiTV-ASTR0")
        ReflectionHelpers.setStaticField(Build::class.java, "HARDWARE", "amlogic")
        DeviceProfile.setOwn(context, null as String?)
        assertTrue(LocalVideoSurfacePolicy.candidate(context))
        ReflectionHelpers.setStaticField(Build::class.java, "HARDWARE", "other")
        assertFalse(LocalVideoSurfacePolicy.candidate(context))
        ReflectionHelpers.setStaticField(Build::class.java, "MODEL", "MiTV-MFTR0")
        ReflectionHelpers.setStaticField(Build::class.java, "HARDWARE", "amlogic")
        DeviceProfile.setOwn(context, null as String?)
        assertFalse(LocalVideoSurfacePolicy.candidate(context))
    }
    @Test fun ownDeviceProfileCanExplicitlyChooseOrDeclineTheWorkaround() {
        val context = RuntimeEnvironment.getApplication()
        DeviceProfile.setOwn(context, """{"localVideoTexture":true}""")
        assertTrue(LocalVideoSurfacePolicy.candidate(context))
        DeviceProfile.setOwn(context, "{}")
        assertFalse(LocalVideoSurfacePolicy.candidate(context))
    }
    @Test fun realLocalMp4MetadataIsReadWithoutOpeningADecoder() {
        val file = temp.newFile("sdr.mp4")
        file.writeBytes(javaClass.getResourceAsStream("/local-sdr-test.mp4")!!.use { it.readBytes() })
        assertTrue(LocalVideoSurfacePolicy.choose(listOf(ScreensaverVideo("sdr", "SDR", file))))
        assertTrue("A repeated selection may use the metadata cache", LocalVideoSurfacePolicy.choose(listOf(ScreensaverVideo("sdr", "SDR", file))))
    }
    @Test fun corruptedAndMixedPlaylistsFallBackAndChangedFilesAreRechecked() {
        val file = temp.newFile("changed.mp4")
        file.writeBytes(javaClass.getResourceAsStream("/local-sdr-test.mp4")!!.use { it.readBytes() })
        val video = ScreensaverVideo("changed", "SDR", file)
        assertTrue(LocalVideoSurfacePolicy.choose(listOf(video)))
        val bad = temp.newFile("bad.mp4").apply { writeBytes(ByteArray(32)) }
        assertFalse(LocalVideoSurfacePolicy.choose(listOf(video, ScreensaverVideo("bad", "Bad", bad))))
        file.writeBytes(ByteArray(12))
        assertFalse(LocalVideoSurfacePolicy.choose(listOf(video)))
        assertFalse(LocalVideoSurfacePolicy.choose(emptyList()))
    }
}
