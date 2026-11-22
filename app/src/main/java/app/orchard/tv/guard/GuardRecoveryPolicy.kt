package app.orchard.tv.guard

import android.content.ComponentName
import android.content.Context
import android.provider.Settings

/** Durable user intent, separate from the service list that OEM boot code may erase. */
object GuardRecoveryPolicy {
    private const val PREFS = "system"
    private const val KEY = "halo_guard_auto_recovery"

    @Synchronized
    fun enabled(context: Context): Boolean {
        if (!GuardSetup.isUserUnlocked(context)) return false
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.contains(KEY)) return prefs.getBoolean(KEY, false)
        // Upgrade existing enabled guards only; a secure-settings grant alone is not user intent.
        val me = ComponentName(context, HomeGuardService::class.java)
        val configured = runCatching {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
                .orEmpty().split(':').any {
                    val saved = ComponentName.unflattenFromString(it)
                    saved == me || saved?.packageName == context.packageName &&
                        saved.className == "${context.packageName}.guard.HomeGuardService"
                }
        }.getOrDefault(false)
        if (configured) prefs.edit().putBoolean(KEY, true).commit()
        return configured
    }

    fun optedOut(context: Context): Boolean {
        if (!GuardSetup.isUserUnlocked(context)) return true
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.contains(KEY) && !prefs.getBoolean(KEY, false)
    }

    @Synchronized
    fun setEnabled(context: Context, enabled: Boolean) {
        if (!GuardSetup.isUserUnlocked(context)) return
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY, enabled).commit()
        if (!enabled) {
            GuardBootWatch.stop()
            GuardRuntimeWatch.stop()
        }
    }
}
