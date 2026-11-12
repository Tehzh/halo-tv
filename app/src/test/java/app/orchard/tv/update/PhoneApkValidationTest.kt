package app.orchard.tv.update

import android.app.Application
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.Signature
import android.os.Bundle
import app.orchard.tv.BuildConfig
import java.io.File
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Exercises the upload policy with TV-style PackageManager results, not APK cryptographic parsing. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [27, 30, 34])
@Suppress("DEPRECATION")
class PhoneApkValidationTest {
    private val app: Application = RuntimeEnvironment.getApplication()
    private val apk get() = File(app.cacheDir, "policy-test.apk")
    private fun certificate() = Signature(javaClass.getResourceAsStream("/halo-release-cert.der")!!.use { it.readBytes() })
    private fun incoming(variant: String = BuildConfig.FLAVOR, code: Int = BuildConfig.VERSION_CODE + 1) = PackageInfo().apply {
        packageName = app.packageName
        versionName = "3.0.5"
        versionCode = code
        signatures = arrayOf(certificate())
        applicationInfo = ApplicationInfo().apply {
            minSdkVersion = 21
            metaData = Bundle().apply { putString("app.halotv.update.variant", variant) }
        }
    }
    @Before fun prepare() {
        apk.writeBytes(byteArrayOf(0))
        val installed = app.packageManager.getPackageInfo(app.packageName, 0)
        installed.signatures = arrayOf(certificate())
        shadowOf(app.packageManager).installPackage(installed)
    }
    private fun check(info: PackageInfo): Updates.ApkCheck {
        shadowOf(app.packageManager).setPackageArchiveInfo(apk.path, info)
        return Updates.validatePhoneApk(app, apk)
    }
    @Test fun legitimateHigherVersionOfSameEditionIsAccepted() {
        assertEquals("3.0.5", check(incoming()).version)
    }
    @Test fun sameVersionCannotBeUploadedAsAnUpdate() {
        assertEquals("not_newer", check(incoming(code = BuildConfig.VERSION_CODE)).error)
    }
    @Test fun otherEditionShowsEditionErrorBeforeVersionError() {
        val other = if (BuildConfig.FLAVOR == "guard") "standard" else "guard"
        assertEquals("wrong_variant", check(incoming(other, BuildConfig.VERSION_CODE)).error)
    }
    @Test fun missingSigningDataIsNotReportedAsWrongReleaseSignature() {
        assertEquals("signature_unavailable", check(incoming().apply { signatures = null }).error)
    }
    @Test fun differentlySignedApkIsNeverAccepted() {
        assertEquals("wrong_signature", check(incoming().apply { signatures = arrayOf(Signature(byteArrayOf(1,2))) }).error)
    }
    @Test fun differentlySignedInstalledCopyHasDistinctError() {
        val installed = app.packageManager.getPackageInfo(app.packageName, 0)
        installed.signatures = arrayOf(Signature(byteArrayOf(1,2)))
        shadowOf(app.packageManager).installPackage(installed)
        assertEquals("installed_signature_mismatch", check(incoming()).error)
    }
    @Test fun foreignPackageIsRejectedEvenWithKnownCertificate() {
        assertEquals("wrong_app", check(incoming().apply { packageName = "another.app" }).error)
    }
    @Test fun buildLabelIdentifiesEdition() {
        assertTrue(Updates.buildLabel.contains(BuildConfig.VERSION_NAME))
        assertTrue(Updates.buildLabel.endsWith(if (BuildConfig.FLAVOR == "guard") "Guard" else "Standard"))
    }
}
