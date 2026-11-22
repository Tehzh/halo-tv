package app.orchard.tv.update

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import app.orchard.tv.BuildConfig
import java.security.MessageDigest

/** Updates may replace only this HaloTV package, edition and release signer. */
object ReleaseIdentity {
    @Suppress("DEPRECATION")
    fun signers(info: PackageInfo?): Set<String> {
        val modern = if (Build.VERSION.SDK_INT >= 28) runCatching { info?.signingInfo?.apkContentsSigners }.getOrNull() else null
        return (modern?.takeIf { it.isNotEmpty() } ?: info?.signatures)?.map { cert ->
            MessageDigest.getInstance("SHA-256").digest(cert.toByteArray())
                .joinToString("") { "%02x".format(java.util.Locale.ROOT, it) }
        }?.toSet().orEmpty()
    }

    @Suppress("DEPRECATION")
    private fun installed(context: Context): PackageInfo? = runCatching {
        val flags = PackageManager.GET_SIGNATURES or PackageManager.GET_META_DATA or
            (if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else 0)
        val preferred = context.packageManager.getPackageInfo(context.packageName, flags)
        if (signers(preferred).isNotEmpty() || Build.VERSION.SDK_INT < 28) preferred
        else context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES or PackageManager.GET_META_DATA)
    }.getOrNull()

    @Suppress("DEPRECATION")
    fun code(info: PackageInfo): Long = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()

    fun error(context: Context, next: PackageInfo): String? {
        if (next.packageName != context.packageName) return "wrong_app"
        val incoming = signers(next)
        val current = signers(installed(context))
        if (incoming.isEmpty() || current.isEmpty()) return "signature_unavailable"
        if (current != setOf(BuildConfig.HALO_CERT)) return "installed_signature_mismatch"
        if (incoming != current) return "wrong_signature"
        if (next.applicationInfo?.metaData?.getString("app.halotv.update.variant") != BuildConfig.HALO_EDITION) return "wrong_variant"
        if (Build.VERSION.SDK_INT >= 24 && (next.applicationInfo?.minSdkVersion ?: Int.MAX_VALUE) > Build.VERSION.SDK_INT) return "unsupported"
        if (code(next) <= BuildConfig.VERSION_CODE.toLong()) return "not_newer"
        return null
    }
}
