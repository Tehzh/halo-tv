package app.orchard.tv.guard

import android.app.KeyguardManager
import android.content.Context
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.getSystemService

/** Stricter evidence for background recovery, where a historical foreground event is insufficient. */
internal object GuardForegroundRecovery {
    const val REBIND_LOOKBACK_MS = 30_000L
    const val HOME_EVENT_SLACK_MS = 250L
    const val HOME_DEADLINE_MS = 3_000L

    fun stock(
        front: UsageFront.Front?,
        now: Long,
        notBefore: Long,
        isStock: (String, String) -> Boolean,
    ): Pair<String, String>? = front
        ?.takeIf { !it.closed && it.at in notBefore..now && isStock(it.pkg, it.cls) }
        ?.let { it.pkg to it.cls }

    fun recentProcessRebind(sdk: Int, uptime: Long, bootWindow: Long, processAge: Long, updating: Boolean): Boolean =
        sdk >= 24 && uptime >= bootWindow && processAge in 0 until 60_000L && !updating

    fun updating(context: Context): Boolean = runCatching {
        val age = System.currentTimeMillis() - context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime
        age < 120_000L
    }.getOrDefault(false)

    fun canShowHome(context: Context): Boolean =
        GuardSetup.isUserUnlocked(context) &&
            context.getSystemService<PowerManager>()?.isInteractive == true &&
            context.getSystemService<KeyguardManager>()?.isKeyguardLocked != true &&
            runCatching { Settings.Global.getString(context.contentResolver, "tv_power_mode") != "QUICK" }
                .getOrDefault(false)
}
