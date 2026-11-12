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
        runCatching { GuardSetup.ensureEnabled(context) }
            .onFailure { DiagLog.e("boot: initial guard restore failed", it) }
        if (action == Intent.ACTION_LOCKED_BOOT_COMPLETED) return
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
        // JMGO S60 (Amlogic T968): Settings' "Startup Source" has the TV open that input itself at
        // power-on, seconds before BOOT_COMPLETED (2026-11-07: the input at 08:00:43, Aerial at
        // 08:00:51, the input then finished). Coming to the front here covered it. Home Guard leaves
        // the input's player alone, and Home still brings Aerial back on this TV.
        if (!handled && GuardSetup.startsOnSignalSource(context)) {
            DiagLog.i("boot: the TV starts on a signal source; leaving it")
            return
        }
        if (!handled && MainActivity.leftForAnApp) {
            DiagLog.i("boot: an app opened from Aerial is in front; leaving it")
            return
        }
        if (!handled) runCatching {
            context.startActivity(MainActivity.bringBackIntent(context))
        }.onFailure { DiagLog.e("boot: could not open Home", it) }

        // A boot broadcast can arrive before the vendor's accessibility startup has settled.
        // Keep the broadcast alive for one bounded retry, including the asynchronous rebind.
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_SECURE_SETTINGS) != PackageManager.PERMISSION_GRANTED ||
            !GuardSetup.isUserUnlocked(context) || SystemClock.elapsedRealtime() - started > 2_000L
        ) return
        Thread.sleep(2_500L)
        if (HomeGuardService.isRunning) return
        DiagLog.i("boot: guard unbound after startup; retrying")
        GuardSetup.ensureEnabled(context)
        if (!HomeGuardService.isRunning && SystemClock.elapsedRealtime() - started < 6_000L) {
            GuardSetup.rebindIfStalled(context)
            Thread.sleep(1_700L)
        }
    }
}
