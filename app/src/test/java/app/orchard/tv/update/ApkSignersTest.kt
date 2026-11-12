package app.orchard.tv.update

import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [27, 30, 34])
@Suppress("DEPRECATION")
class ApkSignersTest {
    private fun release() = Signature(javaClass.getResourceAsStream("/halo-release-cert.der")!!.use { it.readBytes() })
    private fun legacyInfo() = PackageInfo().apply { signatures = arrayOf(release()) }
    @Test fun officialReleaseCertificateMatchesThePublishedPin() {
        val signers = ApkSigners.current(legacyInfo())
        assertEquals(setOf(ApkSigners.RELEASE_CERT), signers)
        assertNull(ApkSigners.error(signers, signers))
    }
    @Test fun missingSignaturesAreUnreadableNotMismatchedAndNeverAccepted() {
        assertEquals("signature_unavailable", ApkSigners.error(null, setOf(ApkSigners.RELEASE_CERT)))
        assertEquals("signature_unavailable", ApkSigners.error(setOf(ApkSigners.RELEASE_CERT), emptySet()))
    }
    @Test fun foreignApkIsRejectedEvenWhenTheInstalledSignerMatchesIt() {
        assertEquals("wrong_signature", ApkSigners.error(setOf("foreign"), setOf("foreign")))
    }
    @Test fun installedSignerMismatchHasItsOwnActionableResult() {
        assertEquals("installed_signature_mismatch", ApkSigners.error(setOf(ApkSigners.RELEASE_CERT), setOf("old")))
    }
    @Test fun multipleSignersCannotBypassTheReleasePin() {
        assertEquals("wrong_signature", ApkSigners.error(setOf(ApkSigners.RELEASE_CERT, "foreign"), setOf(ApkSigners.RELEASE_CERT)))
    }
    @Test @Config(sdk = [30]) fun tvParserMissingModernSigningInfoRetriesLegacyFlags() {
        val calls = mutableListOf<Int>()
        val result = ApkSigners.read { flags ->
            calls += flags
            if (flags == PackageManager.GET_SIGNATURES) legacyInfo() else PackageInfo()
        }
        assertEquals(2, calls.size)
        assertEquals(setOf(ApkSigners.RELEASE_CERT), ApkSigners.current(result))
    }
    @Test @Config(sdk = [30]) fun realMismatchDoesNotTriggerLegacyRetry() {
        var calls = 0
        val result = ApkSigners.read { calls++; PackageInfo().apply { signatures = arrayOf(Signature(byteArrayOf(1, 2, 3))) } }
        assertEquals(1, calls)
        assertEquals("wrong_signature", ApkSigners.error(ApkSigners.current(result), setOf(ApkSigners.RELEASE_CERT)))
    }
}
