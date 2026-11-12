package app.orchard.tv.system

import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.provider.Settings

/** Requests cached-process reclamation only; never force-stops apps or changes network settings. */
object BackgroundCleaner {
    enum class Outcome { Done, Unsupported, Protected, Failed, Busy }
    data class Result(val outcome: Outcome, val requested: Int = 0, val limitedVisibility: Boolean = false)
    internal data class Candidate(val name: String, val uid: Int, val system: Boolean)
    private val running = java.util.concurrent.atomic.AtomicBoolean(false)

    internal fun select(
        candidates: List<Candidate>, ownPackage: String, ownUid: Int,
        protected: Set<String>, active: Set<String>, cached: Set<String>, allowFallback: Boolean,
    ): List<String> = candidates.asSequence().filter {
        it.name != ownPackage && it.uid != ownUid && !it.system && it.name !in protected &&
            it.name !in active && (allowFallback || it.name in cached)
    }.map { it.name }.distinct().toList()

    internal fun shouldSkip(manual: Boolean, networkSafe: Boolean, musicActive: Boolean): Boolean =
        !manual && (!networkSafe || musicActive)

    @Suppress("DEPRECATION")
    fun clean(context: Context, allowFallback: Boolean = true): Result {
        if (Build.VERSION.SDK_INT >= 34) return Result(Outcome.Unsupported)
        if (!running.compareAndSet(false, true)) return Result(Outcome.Busy)
        try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return Result(Outcome.Failed)
            // Idle automation fails closed when transport capabilities cannot be enumerated.
            // Manual requests protect VPN packages individually instead of blocking every app.
            val networkSafe = runCatching {
                cm.allNetworks.none { network ->
                    val caps = cm.getNetworkCapabilities(network)
                    caps == null || caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
                }
            }.getOrDefault(false)
            val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            // Manual cleanup follows HaloTV 2.1.4: ask Android about user apps individually.
            // A vendor may report music active forever, or return null capabilities for an idle
            // transport. Neither should block the whole manual request. VPN/service packages
            // are still excluded below. Idle automation remains deliberately conservative.
            if (shouldSkip(allowFallback, networkSafe, audio.isMusicActive)) return Result(Outcome.Protected)
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val pm = context.packageManager
            val protected = pm.queryIntentServices(Intent("android.net.VpnService"), 0)
                .mapNotNull { it.serviceInfo?.packageName }.toMutableSet()
            fun protectComponents(value: String?) {
                value.orEmpty().split(':').forEach { ComponentName.unflattenFromString(it)?.packageName?.let(protected::add) }
            }
            protectComponents(Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD))
            protectComponents(Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES))
            protectComponents(Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners"))
            pm.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)
                ?.activityInfo?.packageName?.let(protected::add)
            val processes = am.runningAppProcesses.orEmpty()
            val active = processes.filter { it.importance < ActivityManager.RunningAppProcessInfo.IMPORTANCE_CACHED }
                .flatMap { it.pkgList.orEmpty().asIterable() }.toSet()
            val cached = processes.filter { it.importance >= ActivityManager.RunningAppProcessInfo.IMPORTANCE_CACHED }
                .flatMap { it.pkgList.orEmpty().asIterable() }.toSet()
            // Many Android 7+ TVs return only this UID here. An empty cached set does not mean
            // no other app is running. Manual cleanup may ask the OS about installed user apps;
            // killBackgroundProcesses itself only kills what Android considers reclaimable.
            // Idle cleanup remains conservative and uses only explicitly observed cached apps.
            val limited = processes.none { it.uid != context.applicationInfo.uid }
            val names = cached.toMutableSet()
            if (allowFallback) {
                // Use the installed user-application list, as in 2.1.4; launcher queries miss apps
                // hidden by OEM launchers and apps without a normal MAIN entry.
                pm.getInstalledApplications(0).mapTo(names) { it.packageName }
            }
            val candidates = names.mapNotNull { name ->
                val info = runCatching { pm.getApplicationInfo(name, 0) }.getOrNull() ?: return@mapNotNull null
                Candidate(name, info.uid, info.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0)
            }
            val selected = select(candidates, context.packageName, context.applicationInfo.uid, protected, active, cached, allowFallback)
            var requested = 0
            selected.forEach { name -> if (runCatching { am.killBackgroundProcesses(name) }.isSuccess) requested++ }
            DiagLog.i("background cleanup: visible=${processes.size} eligible=${selected.size} requested=$requested limited=$limited fallback=$allowFallback")
            return Result(if (selected.isNotEmpty() && requested == 0) Outcome.Failed else Outcome.Done, requested, limited)
        } catch (_: Exception) {
            return Result(Outcome.Failed)
        } finally {
            running.set(false)
        }
    }
}
