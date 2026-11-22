package app.orchard.tv.guard

import android.Manifest
import android.app.ActivityManager
import android.app.AppOpsManager
import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import android.service.notification.NotificationListenerService
import androidx.core.content.ContextCompat
import app.orchard.tv.BuildConfig
import app.orchard.tv.R
import app.orchard.tv.system.DiagLog
import app.orchard.tv.system.SystemProbe
import app.orchard.tv.system.SystemRegistrationRepair
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext

/** User-initiated setup only. Never grants shell privileges or changes another app's access. */
internal object GuardActivation {
    private val mutex = Mutex()
    private const val LISTENERS = "enabled_notification_listeners"

    data class Status(
        val health: GuardHealth,
        val listenerConnected: Boolean,
        val listenerUnsupported: Boolean,
        val overlay: Boolean?,
        val usage: Boolean?,
        val battery: Boolean?,
        val home: Boolean?,
    )

    enum class Step(val title: Int) {
        Restricted(R.string.halo_guard_restricted),
        Accessibility(R.string.cc_setup_guard),
        Notification(R.string.cc_setup_revival),
        Overlay(R.string.halo_setup_overlay),
        Usage(R.string.halo_setup_usage),
        Battery(R.string.halo_setup_battery),
        Autostart(R.string.halo_setup_autostart),
        Home(R.string.cc_setup_home),
    }

    fun read(context: Context): Status = Status(
        GuardHealth.read(context), RevivalListener.isConnected,
        listenerUnsupported(context),
        runCatching { Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(context) }.getOrNull(),
        runCatching {
            val ops = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            @Suppress("DEPRECATION")
            when (ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)) {
                AppOpsManager.MODE_ALLOWED -> true
                AppOpsManager.MODE_IGNORED, AppOpsManager.MODE_ERRORED -> false
                else -> ContextCompat.checkSelfPermission(context, Manifest.permission.PACKAGE_USAGE_STATS) == PackageManager.PERMISSION_GRANTED
            }
        }.getOrNull(),
        runCatching { Build.VERSION.SDK_INT < 23 ||
            (context.getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(context.packageName) }.getOrNull(),
        runCatching {
            context.packageManager.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY)
                ?.activityInfo?.packageName?.let { it == context.packageName }
        }.getOrNull(),
    )

    private fun listenerUnsupported(context: Context): Boolean = Build.VERSION.SDK_INT <= 29 &&
        runCatching { (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).isLowRamDevice }.getOrDefault(false)

    /** Only supported firmwares accept this WSS write. Read-back is approval, not proof of binding. */
    @Synchronized
    internal fun allowListener(context: Context): Boolean {
        if (!GuardSetup.recoveryAllowed(context) || listenerUnsupported(context)) return false
        return runCatching {
            val resolver = context.contentResolver
            val component = ComponentName(context, RevivalListener::class.java)
            val current = Settings.Secure.getString(resolver, LISTENERS).orEmpty()
            val entries = current.split(':').filter { it.isNotBlank() }
            if (entries.none { ComponentName.unflattenFromString(it) == component }) {
                if (Settings.Secure.getString(resolver, LISTENERS).orEmpty() != current) return@runCatching false
                check(Settings.Secure.putString(resolver, LISTENERS, (entries + component.flattenToString()).joinToString(":")))
            }
            val retained = Settings.Secure.getString(resolver, LISTENERS).orEmpty().split(':')
                .any { ComponentName.unflattenFromString(it) == component }
            if (retained && Build.VERSION.SDK_INT >= 24 && !RevivalListener.isConnected) {
                runCatching { NotificationListenerService.requestRebind(component) }
            }
            retained && GuardHealth.listenerAllowed(context)
        }.onFailure { DiagLog.e("setup: notification access requires system confirmation", it) }.getOrDefault(false)
    }

    /** Returns false if an existing operation is already in flight. Final UI uses a fresh read. */
    suspend fun repair(context: Context): Boolean {
        if (!mutex.tryLock()) return false
        try {
            val app = context.applicationContext
            withContext(Dispatchers.IO) {
                GuardRecoveryPolicy.setEnabled(app, true)
                // This also safely removes dead registrations; installed Aerial is preserved.
                runCatching { SystemRegistrationRepair.repair(app) }
                runCatching { GuardSetup.rescanIfMissing(app) }
                allowListener(app)
                GuardBootWatch.startSession(app)
                runCatching { GuardSetup.ensureEnabled(app) }
            }
            if (withContext(Dispatchers.IO) { GuardHealth.read(app).canRestore } && !HomeGuardService.isRunning) {
                delay(5500)
                withContext(Dispatchers.IO) { GuardSetup.rebindIfStalled(app) }
                delay(2000)
            }
            return true
        } finally { mutex.unlock() }
    }

    /** Each page is offered once per guided run. Denial cannot create a settings-launch loop. */
    fun needed(status: Status, visited: Set<Step>): List<Step> = Step.entries.filter { step ->
        step !in visited && when (step) {
            Step.Restricted -> status.health.restrictedSettingsBlocked == true
            Step.Accessibility -> status.health.enabled != true && !status.health.running
            Step.Notification -> !status.listenerUnsupported && !status.health.revivalAllowed
            Step.Overlay -> status.overlay != true
            Step.Usage -> status.usage != true
            Step.Battery -> status.battery != true
            Step.Autostart -> true // No portable API can prove OEM autostart/association approval.
            Step.Home -> BuildConfig.FLAVOR == "standard" && status.home != true
        }
    }

    fun intent(context: Context, step: Step): Intent? {
        val ownUri = Uri.parse("package:${context.packageName}")
        val intent = when (step) {
            Step.Restricted, Step.Autostart -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, ownUri)
            Step.Accessibility -> SystemProbe.guardSettingsIntent(context)
            Step.Notification -> SystemProbe.revivalSettingsIntent(context)
            Step.Overlay -> if (Build.VERSION.SDK_INT >= 23) Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, ownUri) else null
            Step.Usage -> Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
            Step.Battery -> if (Build.VERSION.SDK_INT >= 23) Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS) else null
            Step.Home -> {
                val role = if (Build.VERSION.SDK_INT >= 29) context.getSystemService(RoleManager::class.java) else null
                if (Build.VERSION.SDK_INT >= 29 && role != null && role.isRoleAvailable(RoleManager.ROLE_HOME))
                    role.createRequestRoleIntent(RoleManager.ROLE_HOME) else SystemProbe.homeSettingsIntent(context)
            }
        } ?: return null
        return runCatching {
            val info = context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo
                ?: return@runCatching null
            if (!info.exported || (info.permission != null && ContextCompat.checkSelfPermission(context, info.permission) != PackageManager.PERMISSION_GRANTED)) null
            else intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }.getOrNull()
    }
}
