package app.orchard.tv.lite

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import java.io.File

/** No service or polling: only a confirmed APK replacement may reopen Lite's Home. */
internal object LiteUpdateReturn {
    private val bootId by lazy { runCatching { File("/proc/sys/kernel/random/boot_id").readText().trim() }.getOrNull() }
    private fun prefs(c: Context) = c.getSharedPreferences("halo_update_return", Context.MODE_PRIVATE)
    fun noteInstall(c: Context, target: Int) {
        if (target <= BuildConfig.VERSION_CODE) return
        prefs(c).edit().clear().putInt("from", BuildConfig.VERSION_CODE).putInt("target", target)
            .putLong("at", SystemClock.elapsedRealtime()).putString("boot", bootId).commit()
    }
    fun cancel(c: Context) { prefs(c).edit().clear().commit() }
    @Synchronized fun claim(c: Context): Boolean {
        val p = prefs(c)
        val from = p.getInt("from", -1)
        if (from < 0) return false
        val age = SystemClock.elapsedRealtime() - p.getLong("at", -1)
        val boot = p.getString("boot", null)
        if (age !in 0..15 * 60_000L || (boot != null && bootId != boot)) { cancel(c); return false }
        if (BuildConfig.VERSION_CODE <= from || BuildConfig.VERSION_CODE < p.getInt("target", Int.MAX_VALUE)) return false
        p.edit().remove("from").putLong("home_at", SystemClock.elapsedRealtime()).commit()
        c.getSharedPreferences("boot_open", Context.MODE_PRIVATE).edit()
            .putLong("elapsed", SystemClock.elapsedRealtime())
            .putLong("boot", System.currentTimeMillis() - SystemClock.elapsedRealtime()).commit()
        return true
    }
    fun keepHome(c: Context): Boolean {
        claim(c)
        val p = prefs(c)
        val at = p.getLong("home_at", -1)
        val boot = p.getString("boot", null)
        return at >= 0 && SystemClock.elapsedRealtime() - at in 0..30_000L && (boot == null || bootId == boot)
    }
}

class LiteUpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        @Suppress("DEPRECATION")
        val awake = if (Build.VERSION.SDK_INT >= 20) pm?.isInteractive else pm?.isScreenOn
        if (awake == false || !LiteUpdateReturn.claim(context)) return
        runCatching { context.startActivity(Intent(context, HomeActivity::class.java)
            .setAction(Intent.ACTION_MAIN).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)) }
    }
}
