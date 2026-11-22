package app.orchard.tv.update

import android.content.Context
import android.os.SystemClock
import android.os.PowerManager
import android.util.Log
import app.orchard.tv.BuildConfig
import app.orchard.tv.MainActivity
import app.orchard.tv.guard.GuardSetup
import app.orchard.tv.system.BootOpen

/** A confirmed self-update returns to Home, not the app/input uncovered when Android kills us. */
internal object UpdateReturn {
    private const val PREFS = "halo_update_return"
    private const val INSTALL_WINDOW = 15 * 60_000L
    private const val HOME_WINDOW = 30_000L
    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun noteInstall(context: Context, targetCode: Long) {
        if (targetCode <= BuildConfig.VERSION_CODE) return
        prefs(context).edit().clear().putInt("from", BuildConfig.VERSION_CODE)
            .putLong("target", targetCode).putLong("at", SystemClock.elapsedRealtime())
            .putString("boot", BootOpen.BOOT_ID).commit()
    }

    fun cancel(context: Context) { prefs(context).edit().clear().commit() }

    /** The installed version must actually have changed; Cancel and failed installs never qualify. */
    @Synchronized
    internal fun claim(context: Context): Boolean {
        if (!GuardSetup.isUserUnlocked(context)) return false
        val p = prefs(context)
        val from = p.getInt("from", -1)
        if (from < 0) return claimLegacyReplacement(context)
        if (p.getInt("claimed_code", -1) == BuildConfig.VERSION_CODE) return false
        val age = SystemClock.elapsedRealtime() - p.getLong("at", -1)
        val boot = p.getString("boot", null)
        if (age !in 0..INSTALL_WINDOW || (boot != null && BootOpen.BOOT_ID != boot)) {
            cancel(context); return false
        }
        if (BuildConfig.VERSION_CODE <= from || BuildConfig.VERSION_CODE.toLong() < p.getLong("target", Long.MAX_VALUE)) return false
        p.edit().remove("from").remove("target").putInt("claimed_code", BuildConfig.VERSION_CODE)
            .putLong("home_at", SystemClock.elapsedRealtime()).commit()
        // Persist that this boot's start target is handled; retain the user's configured target.
        BootOpen.noteOpened(context, openedTarget = false)
        return true
    }

    /** 3.0.5 and earlier already persisted this marker before handing an APK to Android. */
    private fun claimLegacyReplacement(context: Context): Boolean {
        val p = prefs(context)
        if (p.getInt("claimed_code", -1) == BuildConfig.VERSION_CODE) return false
        val installerAt = context.getSharedPreferences("system", Context.MODE_PRIVATE)
            .getLong("installer_opened_at", 0L)
        if (installerAt <= 0 || System.currentTimeMillis() - installerAt !in 0..INSTALL_WINDOW) return false
        // A cancelled installer has a lastUpdateTime older than its opening. A plain app start
        // therefore cannot use an old marker to bring Home over a video.
        val replacedAt = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime }.getOrDefault(0L)
        if (replacedAt < installerAt) return false
        p.edit().putInt("claimed_code", BuildConfig.VERSION_CODE)
            .putLong("home_at", SystemClock.elapsedRealtime()).putString("boot", BootOpen.BOOT_ID).commit()
        BootOpen.noteOpened(context, openedTarget = false)
        return true
    }

    fun keepHome(context: Context): Boolean {
        if (!GuardSetup.isUserUnlocked(context)) return false
        claim(context)
        val p = prefs(context)
        val at = p.getLong("home_at", -1)
        val boot = p.getString("boot", null)
        return at >= 0 && SystemClock.elapsedRealtime() - at in 0..HOME_WINDOW &&
            (boot == null || BootOpen.BOOT_ID == boot)
    }

    fun returnHome(context: Context) {
        // Never wake a sleeping TV for an update. The next Home start consumes the marker instead.
        if (!GuardSetup.isUserUnlocked(context)) return
        if ((context.getSystemService(Context.POWER_SERVICE) as? PowerManager)?.isInteractive == false) return
        if (!claim(context)) return
        runCatching {
            val home = MainActivity.bringBackIntent(context)
            context.startActivity(home)
        }
            .onFailure { Log.w("HaloUpdate", "Updated; Home will resume when the TV permits it", it) }
    }
}
