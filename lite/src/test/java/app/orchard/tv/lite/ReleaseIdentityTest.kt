package app.orchard.tv.lite

import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.Signature
import android.os.Bundle

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23, 30, 34])
@Suppress("DEPRECATION")
class ReleaseIdentityTest {
    private val app = RuntimeEnvironment.getApplication()
    private fun cert(name: String = "halo-independent-cert.der") = Signature(javaClass.getResourceAsStream("/" + name)!!.readBytes())
    private fun info(pkg: String = app.packageName, code: Int = BuildConfig.VERSION_CODE + 1, edition: String = BuildConfig.HALO_EDITION) = PackageInfo().apply {
        packageName = pkg; versionName = "3.2.4"; versionCode = code; signatures = arrayOf(cert())
        applicationInfo = ApplicationInfo().apply {
            packageName = pkg
            if (android.os.Build.VERSION.SDK_INT >= 24) minSdkVersion = 14
            metaData = Bundle().apply { putString("app.halotv.update.variant", edition) }
        }
    }
    @Before fun setup() { shadowOf(app.packageManager).installPackage(info(code = BuildConfig.VERSION_CODE)) }
    @Test fun officialSameEditionHigherVersionIsAccepted() { assertNull(ReleaseIdentity.error(app, info())) }
    @Test fun equalAndOlderVersionsAreRejected() {
        assertEquals("not_newer", ReleaseIdentity.error(app, info(code = BuildConfig.VERSION_CODE)))
        assertEquals("not_newer", ReleaseIdentity.error(app, info(code = BuildConfig.VERSION_CODE - 1)))
    }
    @Test fun anotherPackageIsRejectedEvenWithTheOfficialSigner() {
        for (pkg in listOf("app.orchard.tv", "app.orchard.tv.lite", "app.halotv.lite", "other.app")) {
            assertEquals("wrong_app", ReleaseIdentity.error(app, info(pkg)))
        }
    }
    @Test fun aDifferentEditionCannotReplaceTheCurrentOne() {
        assertEquals("wrong_variant", ReleaseIdentity.error(app, info(edition = "not-this-edition")))
    }
    @Test fun oldAndUnknownSignersAreRejectedForTheCurrentPackage() {
        assertEquals("wrong_signature", ReleaseIdentity.error(app, info().apply { signatures = arrayOf(cert("halo-release-cert.der")) }))
        assertEquals("wrong_signature", ReleaseIdentity.error(app, info().apply { signatures = arrayOf(Signature(byteArrayOf(1, 2, 3))) }))
    }
    @Test fun missingSignersNeverBypassVerification() {
        assertEquals("signature_unavailable", ReleaseIdentity.error(app, info().apply { signatures = null }))
    }
    @Test fun incorrectlySignedInstalledCopyIsDetected() {
        shadowOf(app.packageManager).installPackage(info(code = BuildConfig.VERSION_CODE).apply { signatures = arrayOf(cert("halo-release-cert.der")) })
        assertEquals("installed_signature_mismatch", ReleaseIdentity.error(app, info()))
    }
    @Test fun packageRequiringANewerAndroidIsRejected() {
        if (android.os.Build.VERSION.SDK_INT < 24) return
        assertEquals("unsupported", ReleaseIdentity.error(app, info().apply { applicationInfo!!.minSdkVersion = android.os.Build.VERSION.SDK_INT + 1 }))
    }
}
