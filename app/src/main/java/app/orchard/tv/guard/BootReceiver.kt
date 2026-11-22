package app.orchard.tv.guard

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.SystemClock
import androidx.core.content.ContextCompat
import app.orchard.tv.MainActivity
import app.orchard.tv.system.DiagLog
import kotlin.concurrent.thread

/**
 * After a real reboot: restore Home Guard, and if the firmware still hands Home to its own
 * launcher, open Aerial. Starting an activity from a receiver needs the "display over other apps"
 * permission on Android 10+, which tools/install.sh grants.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Exported for the boot broadcasts only; anything else could use it to pull Aerial forward.
        val action = intent.action ?: return
        if (action !in setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_LOCKED_BOOT_COMPLETED,
                Intent.ACTION_MY_PACKAGE_REPLACED, Intent.ACTION_USER_UNLOCKED)) return
        // Off the main thread: putting the guard back is settings, AccountManager and sync calls
        // into system_server, and the broadcast's timeout counts every one. XGIMI Z6X (Android 8)
        // sends LOCKED_BOOT_COMPLETED again when the screen locks, while the screensaver's film
        // decodes and the system answers slowly; the receiver ran out of time there and the TV
        // reported Aerial as not responding (issue #614).
        val pending = goAsync()
        val start = SystemClock.elapsedRealtime()
        thread(name = "AerialBoot") {
            try {
                onBoot(context, action, start)
            } catch (e: Exception) {
                DiagLog.e("boot recovery failed", e)
            } finally {
                pending.finish()
                // In the diagnostics: how long the slow side of #614 takes on the TV at hand.
                DiagLog.i("${action.substringAfterLast('.')} handled in ${SystemClock.elapsedRealtime() - start} ms")
            }
        }
    }

    private fun onBoot(context: Context, action: String, started: Long) {
        if (action != Intent.ACTION_LOCKED_BOOT_COMPLETED) GuardBootWatch.startSession(context)
        if (action != Intent.ACTION_LOCKED_BOOT_COMPLETED) AdbRecoveryWatch.startSession(context)
        runCatching { GuardSetup.ensureEnabled(context) }
            .onFailure { DiagLog.e("boot: initial guard restore failed", it) }
        val restoredAt = SystemClock.elapsedRealtime()

        if (action == Intent.ACTION_LOCKED_BOOT_COMPLETED) return
        if (action == Intent.ACTION_MY_PACKAGE_REPLACED) app.orchard.tv.update.UpdateReturn.returnHome(context)
        GuardSetup.rescanIfMissing(context)
        if (action == Intent.ACTION_MY_PACKAGE_REPLACED || action == Intent.ACTION_USER_UNLOCKED) {
            // Repair the service after update/unlock without pulling the viewer out of an app.
            GuardSetup.rebindIfStalled(context)
            return
        }
        // Also when Aerial is the default Home: some firmwares start their own launcher at boot
        // regardless (Skyworth/Coolita 8R145, issue #43).
        // A guard that is already running looks at what is in front before taking Home: by the time
        // this arrives, an HDMI-CEC source may already have switched the TV to its input. It still
        // has to look: on Xiaomi MiTV-MTEQ0 (Android 10) it is bound ~20 s before BOOT_COMPLETED,
        // never sees PatchWall's windows, and the take-over at bind time did not stick (issue #154).

        val handled = HomeGuardService.onBootCompleted()
        if (!handled) startOverConfirmedStock(context)

        // A boot broadcast can arrive before the vendor's accessibility startup has settled.
        // Keep the broadcast alive for one bounded retry, including the asynchronous rebind.
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_SECURE_SETTINGS) != PackageManager.PERMISSION_GRANTED ||
            !GuardSetup.isUserUnlocked(context)
        ) return
        val wait = BootRecoveryTiming.retryDelay(started, restoredAt, SystemClock.elapsedRealtime()) ?: return
        if (wait > 0) Thread.sleep(wait)
        if (HomeGuardService.onBootCompleted()) return
        startOverConfirmedStock(context)
        DiagLog.i("boot: guard unbound after startup; retrying")
        GuardSetup.ensureEnabled(context)
        if (!HomeGuardService.isRunning && SystemClock.elapsedRealtime() - started <= BootRecoveryTiming.MAX_BROADCAST_MS - BootRecoveryTiming.REBIND_WAIT_MS
        ) {
            GuardSetup.rebindIfStalled(context)
            val remaining = BootRecoveryTiming.MAX_BROADCAST_MS - (SystemClock.elapsedRealtime() - started)
            if (remaining > 0) Thread.sleep(minOf(BootRecoveryTiming.REBIND_WAIT_MS, remaining))
        }
    }

    companion object {
        private var startedHomeAtBoot = false

        @Synchronized
        private fun startOverConfirmedStock(context: Context) {
            if (startedHomeAtBoot) return
            val front = runCatching { HomeGuardService.confirmedUnboundBootStock(context) }.getOrNull() ?: return
            if (HomeGuardService.isRunning || !GuardForegroundRecovery.canShowHome(context)) return
            // Claim before starting the activity; repeated BOOT_COMPLETED deliveries cannot
            // bring it to the front twice, even if the firmware rejects the first start.
            startedHomeAtBoot = true
            DiagLog.i("boot: confirmed system Home ${front.first}/${front.second} before guard bound")
            runCatching { context.startActivity(MainActivity.bringBackIntent(context)) }
                .onFailure { DiagLog.e("boot: could not open Home", it) }
        }
    }

}

/** Wait past GuardSetup's 5 s bind grace without holding a boot receiver beyond its 8.5 s budget. */
internal object BootRecoveryTiming {
    private const val BIND_WAIT_MS = 5_100L
    const val REBIND_WAIT_MS = 1_700L
    const val MAX_BROADCAST_MS = 8_500L

    fun retryDelay(started: Long, restoredAt: Long, now: Long): Long? {
        if (restoredAt < started || now < restoredAt) return null
        val wait = (restoredAt + BIND_WAIT_MS - now).coerceAtLeast(0L)
        return wait.takeIf { now - started + it + REBIND_WAIT_MS <= MAX_BROADCAST_MS }
    }
}
