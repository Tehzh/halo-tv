package app.orchard.tv.update

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import java.security.MessageDigest

/** Read current signers, never signing history. Some TV parsers only populate the legacy field. */
internal object ApkSigners {
    const val RELEASE_CERT = "3ba29454f5977999ccf76bd129b1087d3eea25c2f0a8370441d4fab1ccaa9198"
    @Suppress("DEPRECATION")
    val flags: Int get() = PackageManager.GET_SIGNATURES or
        (if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else 0)

    @Suppress("DEPRECATION")
    fun current(info: PackageInfo?): Set<String>? {
        val modern = if (Build.VERSION.SDK_INT >= 28) runCatching { info?.signingInfo?.apkContentsSigners }.getOrNull() else null
        val certificates = modern?.takeIf { it.isNotEmpty() } ?: info?.signatures
        return certificates?.takeIf { it.isNotEmpty() }?.map { fingerprint(it) }?.toSet()
    }

    fun installed(context: Context): Set<String>? = current(read { flags ->
        context.packageManager.getPackageInfo(context.packageName, flags)
    })

    @Suppress("DEPRECATION")
    fun read(load: (Int) -> PackageInfo?): PackageInfo? {
        val preferred = runCatching { load(flags) }.getOrNull()
        if (current(preferred) != null || Build.VERSION.SDK_INT < 28) return preferred
        // Only retry when signing data is absent, never to override a real certificate mismatch.
        val legacy = runCatching { load(PackageManager.GET_SIGNATURES) }.getOrNull()
        return legacy?.takeIf { current(it) != null } ?: preferred
    }

    fun fingerprint(signature: Signature): String = MessageDigest.getInstance("SHA-256")
        .digest(signature.toByteArray()).joinToString("") { "%02x".format(java.util.Locale.ROOT, it) }

    /** Unknown certificates always fail closed; a renamed/re-signed APK is never accepted. */
    fun error(incoming: Set<String>?, installed: Set<String>?): String? = when {
        incoming.isNullOrEmpty() || installed.isNullOrEmpty() -> "signature_unavailable"
        incoming != setOf(RELEASE_CERT) -> "wrong_signature"
        installed != incoming -> "installed_signature_mismatch"
        else -> null
    }
}
