package app.orchard.tv.guard

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/** An on-demand snapshot, not a new background poller or a permission grant. */
internal data class GuardHealth(val running: Boolean, val enabled: Boolean?, val canRestore: Boolean, val revivalAllowed: Boolean) {
    companion object {
        fun read(context: Context): GuardHealth {
            val me = ComponentName(context, HomeGuardService::class.java)
            val enabled = runCatching {
                val global = Settings.Secure.getInt(context.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 0)
                val services = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
                global == 1 && services.split(':').any { ComponentName.unflattenFromString(it) == me }
            }.getOrNull()
            return GuardHealth(HomeGuardService.isRunning, enabled,
                ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED,
                runCatching { NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName) }.getOrDefault(false))
        }
    }
}
