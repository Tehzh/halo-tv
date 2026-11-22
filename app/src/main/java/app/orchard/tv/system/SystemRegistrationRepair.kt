package app.orchard.tv.system

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import app.orchard.tv.dream.AerialDream
import app.orchard.tv.MainActivity
import app.orchard.tv.guard.GuardRecoveryPolicy
import app.orchard.tv.guard.GuardSetup

/** Repair dead registrations, never transfer another installed application's grants or data. */
internal object SystemRegistrationRepair {
    private const val LEGACY = "app.orchard.tv"
    private const val TAG = "HaloRegistrations"
    private const val LISTENERS = "enabled_notification_listeners"
    private val legacyClasses = setOf(
        "$LEGACY.guard.HomeGuardService", "$LEGACY.guard.RevivalListener",
        "$LEGACY.dream.AerialDream", "$LEGACY.dream.OrchardDream",
    )

    /** True only after a stale dream was replaced by this installed package and read back. */
    @Synchronized
    fun repair(context: Context): Boolean {
        if (!GuardSetup.isUserUnlocked(context) ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_SECURE_SETTINGS) != PackageManager.PERMISSION_GRANTED
        ) return false
        val resolver = context.contentResolver
        // Unknown/hidden/disabled packages are not proof of removal. QUERY_ALL_PACKAGES is part
        // of this launcher's manifest; only NameNotFound is accepted, never a Binder failure.
        val legacyAbsent by lazy {
            try { context.packageManager.getPackageInfo(LEGACY, 0); false }
            catch (_: PackageManager.NameNotFoundException) { true }
            catch (_: Exception) { false }
        }
        fun stale(value: String): Boolean {
            val c = ComponentName.unflattenFromString(value) ?: return false
            if (c.packageName == LEGACY && context.packageName != LEGACY && c.className in legacyClasses) return legacyAbsent
            // A script that substituted applicationId into both halves made nonexistent classes.
            return c.packageName == context.packageName && context.packageName != LEGACY &&
                c.className in setOf("${context.packageName}.guard.HomeGuardService",
                    "${context.packageName}.guard.RevivalListener", "${context.packageName}.dream.AerialDream")
        }
        // Preserve order, spelling and every unrelated service. Never switch accessibility or
        // notification access on merely because an old package used to have it.
        for (key in listOf(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, LISTENERS)) runCatching {
            val current = Settings.Secure.getString(resolver, key).orEmpty()
            val entries = current.split(':')
            val kept = entries.filterNot(::stale)
            if (kept.size != entries.size && Settings.Secure.getString(resolver, key).orEmpty() == current) {
                check(Settings.Secure.putString(resolver, key, kept.joinToString(":")))
                DiagLog.i("registrations: removed a dead service from $key; other services preserved")
            }
        }.onFailure { Log.w(TAG, "could not repair $key", it) }

        // Some TV/ADB setups save a vendor boot entry independently of Android's HOME role.
        // Repair only a removed launcher's exact entry after this guard was explicitly chosen.
        // Never replace an installed Aerial, another boot app, or a selected HDMI input.
        if (GuardRecoveryPolicy.enabled(context)) runCatching {
            val key = "boot_app_componentname"
            val current = Settings.System.getString(resolver, key).orEmpty()
            val old = ComponentName.unflattenFromString(current)
            if (old?.packageName == LEGACY && old.className == "$LEGACY.MainActivity" && legacyAbsent &&
                Settings.System.getString(resolver, key).orEmpty() == current) {
                val replacement = ComponentName(context, MainActivity::class.java).flattenToString()
                check(Settings.System.putString(resolver, key, replacement))
                check(Settings.System.getString(resolver, key) == replacement)
                DiagLog.i("registrations: repaired an unavailable vendor boot entry")
            }
        }.onFailure { Log.w(TAG, "could not repair vendor boot entry", it) }

        return runCatching {
            val key = "screensaver_components"
            val current = Settings.Secure.getString(resolver, key).orEmpty()
            // Do not take ownership from another selected dream in a multi-dream list.
            if (current.isBlank() || current.split(',').any { !stale(it.trim()) }) return@runCatching false
            val system = context.getSharedPreferences("wallpaper", Context.MODE_PRIVATE)
                .getString("screensaver", null) == "system"
            val mine = ComponentName(context, AerialDream::class.java).flattenToString()
            val replacement = if (system) SystemScreensaver.firmwareDream(context) else mine
            if (replacement.isNullOrBlank() || stale(replacement)) return@runCatching false
            if (Settings.Secure.getString(resolver, key).orEmpty() != current) return@runCatching false
            check(Settings.Secure.putString(resolver, key, replacement))
            if (Settings.Secure.getString(resolver, key) != replacement) return@runCatching false
            DiagLog.i("registrations: replaced an unavailable system dream with $replacement")
            !system
        }.onFailure { Log.w(TAG, "could not repair system dream", it) }.getOrDefault(false)
    }
}
