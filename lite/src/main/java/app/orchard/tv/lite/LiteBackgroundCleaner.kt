package app.orchard.tv.lite

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.provider.Settings
import java.net.NetworkInterface

/** Same cached-user-process policy as the full app, with a pre-Lollipop network probe. */
internal object LiteBackgroundCleaner {
    enum class Outcome { Done, Unsupported, Protected, Failed }
    data class Result(val outcome: Outcome, val requested: Int = 0, val before: Long = 0)

    fun available(context: Context): Long = ActivityManager.MemoryInfo().also {
        (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(it)
    }.availMem

    @Suppress("DEPRECATION")
    fun clean(context: Context): Result {
        if (Build.VERSION.SDK_INT >= 34) return Result(Outcome.Unsupported)
        return runCatching {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val vpn = if (Build.VERSION.SDK_INT >= 21) cm.allNetworks.any {
                cm.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
            } else cm.allNetworkInfo.orEmpty().any { it.type == 17 && it.isConnected } ||
                NetworkInterface.getNetworkInterfaces().toList().any {
                    it.isUp && (it.name.startsWith("tun") || it.name.startsWith("ppp") || it.name.startsWith("ipsec"))
                }
            val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            if (vpn || audio.isMusicActive) return Result(Outcome.Protected)
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val pm = context.packageManager
            val protected = pm.queryIntentServices(Intent("android.net.VpnService"), 0)
                .mapNotNull { it.serviceInfo?.packageName }.toMutableSet()
            Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
                ?.substringBefore('/')?.let { protected.add(it) }
            val processes = am.runningAppProcesses.orEmpty()
            val active = processes.filter { it.importance < ActivityManager.RunningAppProcessInfo.IMPORTANCE_BACKGROUND }
                .flatMap { it.pkgList.orEmpty().asIterable() }.toSet()
            val cached = processes.filter { it.importance >= ActivityManager.RunningAppProcessInfo.IMPORTANCE_BACKGROUND }
                .flatMap { it.pkgList.orEmpty().asIterable() }.toSet()
            val before = available(context)
            val count = cached.count { name ->
                if (name == context.packageName || name in protected || name in active) return@count false
                val info = runCatching { pm.getApplicationInfo(name, 0) }.getOrNull() ?: return@count false
                if (info.uid == context.applicationInfo.uid || info.flags and
                    (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0) return@count false
                runCatching { am.killBackgroundProcesses(name) }.isSuccess
            }
            Result(Outcome.Done, count, before)
        }.getOrElse { Result(Outcome.Failed) }
    }
}
