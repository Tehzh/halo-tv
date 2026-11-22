package app.orchard.tv.guard

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import app.orchard.tv.MainActivity
import app.orchard.tv.R
import app.orchard.tv.dream.AerialDream
import app.orchard.tv.settings.AerialSettings
import app.orchard.tv.settings.KidsLock
import app.orchard.tv.settings.Restricted
import app.orchard.tv.settings.RestrictedKind
import app.orchard.tv.system.DiagLog
import app.orchard.tv.system.SystemProbe

/**
 * Kids and Elders mode while the TV is on, wherever it is (Restricted): counts the time spent in
 * apps and inputs (not on Aerial's Home or its screensaver), starts breaks, warns 5 and 1 minutes
 * before a lock, and brings Aerial back over whatever is in front once locked.
 *
 * Other apps are kept away by Home Guard as their windows come up (HomeGuardService). Without it,
 * where usage stats may be read, the app in front is looked at every few seconds instead; without
 * either, only Home itself is limited and the time is counted from Aerial's own pauses.
 *
 * Runs in Aerial's process on the main thread, started by Home and by Home Guard, whichever comes first.
 */
object KidsWatch {
    private const val TICK_MS = 10_000L

    /** Without Home Guard, how often usage stats are read for an app to keep away. */
    private const val USAGE_TICK_MS = 2_000L

    private val handler = Handler(Looper.getMainLooper())
    private var app: Context? = null
    private var lastTickAt = 0L
    private var previouslyWatching = false
    private val delivery = KidsDelivery()
    private var lastLock: KidsLock? = null
    private var lastDeliveryLog = ""
    private var reminderPosted = false
    @Volatile private var diagnostic = "not_started"

    private var idleMs = 0L

    /** The last warning's step (5 or 1 minutes), so each is said once; 0 when none is due. */
    private var warnedStep = 0
    private var elderWatchedMs = 0L
    private var elderReminderSetting = 0
    private var broughtBackAt = Long.MIN_VALUE / 2

    /** Aerial's Home is resumed (MainActivity): time there is not counted. */
    var homeInFront = false
        set(value) {
            if (field == value) return
            field = value
            poke()
        }

    private val screenOff = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            // A parent who left the mode is gone once the TV sleeps.
            if (intent.action == Intent.ACTION_SCREEN_OFF) Restricted.rearm()
            poke()
        }
    }

    private val loop = Runnable { tickAndPlan() }

    fun start(context: Context) {
        if (app == null) {
            app = context.applicationContext
            AerialSettings.init(context)
            Restricted.init(context)
            val screen = IntentFilter(Intent.ACTION_SCREEN_OFF).apply { addAction(Intent.ACTION_SCREEN_ON) }
            ContextCompat.registerReceiver(app!!, screenOff, screen, ContextCompat.RECEIVER_NOT_EXPORTED)
        }
        poke()
    }

    /** The mode or its rules changed: look again now, and keep looking while it is on. */
    fun poke() {
        if (app == null) return
        handler.removeCallbacks(loop)
        handler.post(loop)
    }

    private fun tickAndPlan() {
        if (app == null) return
        tick()
        handler.removeCallbacks(loop)
        if (!Restricted.enabled) {
            lastTickAt = 0L
            previouslyWatching = false
            return
        }
        val lock = Restricted.lock
        val until = if (previouslyWatching && lock == null) Restricted.untilLockMs(System.currentTimeMillis()) else null
        val cadence = when {
            lock != null && !homeInFront && !QuizOverlay.visible -> delivery.nextCheck(SystemClock.elapsedRealtime()).let {
                if (it == 1_000L && !QuizOverlay.quizDue()) TICK_MS else it
            }
            watchesUsage() -> USAGE_TICK_MS
            else -> TICK_MS
        }
        handler.postDelayed(loop, minOf(cadence, until?.coerceAtLeast(250L) ?: cadence))
    }

    private fun tick() {
        val ctx = app ?: return
        val now = System.currentTimeMillis()
        val at = SystemClock.elapsedRealtime()
        val since = if (lastTickAt == 0L) 0L else (at - lastTickAt).coerceAtLeast(0L)
        lastTickAt = at
        val interactive = ctx.getSystemService<PowerManager>()?.isInteractive != false
        val watching = Restricted.active && interactive && !homeInFront && !AerialDream.showing &&
            Restricted.lock == null && !QuizOverlay.visible
        val watchedMs = if (Restricted.active) watchedSinceLastTick(previouslyWatching, since, 2 * TICK_MS) else 0L
        previouslyWatching = watching
        if (watching || watchedMs > 0L) {
            if (Restricted.kind == RestrictedKind.Elders) {
                val interval = Restricted.elderReminderMinutes
                if (interval != elderReminderSetting) { elderReminderSetting = interval; elderWatchedMs = 0L }
                elderWatchedMs += watchedMs
                if (interval > 0 && elderWatchedMs >= interval * 60_000L) {
                    elderWatchedMs = 0L
                    warn(ctx, ctx.getString(R.string.elder_rest_message))
                }
            }
            idleMs = 0
        } else idleMs += since
        if (Restricted.enabled) Restricted.count(watchedMs, idleMs, now)

        val lock = Restricted.lockNow(now)
        if (lock != lastLock) {
            DiagLog.i("kids: lock=${lockName(lock)}")
            delivery.clear("not_due")
            cancelReminder(ctx)
            lastLock = lock
        }
        Restricted.lock = lock
        if (lock != null) previouslyWatching = false
        val quiz = QuizOverlay.quizDue()
        if (!quiz || !interactive || homeInFront) QuizOverlay.dismiss()
        val binding = HomeGuardService.quizBinding()
        when {
            lock == null -> Unit
            homeInFront -> {
                delivery.home()
                cancelReminder(ctx)
            }
            !interactive || AerialDream.showing -> delivery.suspended()
            else -> {
                val overlay = QuizOverlay.allowed(ctx)
                val launchAllowed = binding != 0 || overlay || Build.VERSION.SDK_INT < 29
                delivery.attempt(at, KidsDelivery.Access(binding, overlay, launchAllowed), quiz,
                    QuizOverlay.visible,
                    overlay = {
                        val guard = HomeGuardService.showQuiz()
                        if (quizWindowAccepted(guard)) guard else {
                            val appOverlay = QuizOverlay.show(ctx, accessibility = false)
                            if (quizWindowAccepted(appOverlay)) appOverlay else "$guard+$appOverlay"
                        }
                    },
                    launch = {
                        val service = HomeGuardService.requestQuizHome()
                        if (service != null) service else runCatching {
                            ctx.startActivity(MainActivity.bringBackIntent(ctx))
                            "activity_requested"
                        }.getOrElse { "activity_${it.javaClass.simpleName}" }
                    },
                    remind = { remindToReturn(ctx, quiz) },
                )
                if (QuizOverlay.visible) cancelReminder(ctx)
            }
        }
        val dueIn = if (lock == null && watching) Restricted.untilLockMs(now) else null
        diagnostic = "active=${Restricted.active} watching=${watching && lock == null} due_in_ms=${dueIn ?: "none"} " +
            "lock=${lockName(lock)} quiz_due=$quiz guard_bound=${binding != 0} route=${delivery.state}"
        if (lock != null && delivery.state != lastDeliveryLog) DiagLog.i("kids: $diagnostic")
        lastDeliveryLog = delivery.state
        if (watching && lock == null) warnIfDue(ctx, now) else if (lock != null || !Restricted.active) warnedStep = 0
        if (watching && lock == null && watchesUsage()) keepAwayByUsage(ctx)
    }

    private const val REMINDER_CHANNEL = "kids_viewing_reminders"
    private const val REMINDER_ID = 0x484B

    private fun remindToReturn(ctx: Context, quiz: Boolean): String {
        val text = ctx.getString(if (quiz) R.string.kids_quiz_due_hint else R.string.kids_limit_due_hint)
        val posted = runCatching {
            val manager = ctx.getSystemService<NotificationManager>() ?: return@runCatching false
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) return@runCatching false
            if (!NotificationManagerCompat.from(ctx).areNotificationsEnabled()) return@runCatching false
            if (Build.VERSION.SDK_INT >= 26) {
                if (manager.getNotificationChannel(REMINDER_CHANNEL) == null) {
                    manager.createNotificationChannel(NotificationChannel(REMINDER_CHANNEL,
                        ctx.getString(R.string.kids_reminder_channel), NotificationManager.IMPORTANCE_HIGH))
                }
                if (manager.getNotificationChannel(REMINDER_CHANNEL)?.importance == NotificationManager.IMPORTANCE_NONE) {
                    return@runCatching false
                }
            }
            val pending = PendingIntent.getActivity(ctx, REMINDER_ID, MainActivity.bringBackIntent(ctx),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val notice = NotificationCompat.Builder(ctx, REMINDER_CHANNEL)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(ctx.getString(R.string.kids_reminder_channel))
                .setContentText(text).setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setPriority(NotificationCompat.PRIORITY_HIGH).setContentIntent(pending).setAutoCancel(true).build()
            manager.notify(REMINDER_ID, notice)
            true
        }.getOrDefault(false)
        if (posted) {
            reminderPosted = true
            return "notification_posted"
        }
        return runCatching {
            Toast.makeText(ctx, text, Toast.LENGTH_LONG).show()
            "toast_requested"
        }.getOrElse { "reminder_unavailable" }
    }

    private fun cancelReminder(ctx: Context) {
        if (!reminderPosted) return
        reminderPosted = false
        runCatching { ctx.getSystemService<NotificationManager>()?.cancel(REMINDER_ID) }
    }

    private fun warnIfDue(ctx: Context, now: Long) {
        val until = Restricted.untilLockMs(now)
        val step = when {
            until == null || until > 5 * 60_000L -> 0
            until > 60_000L -> 5
            else -> 1
        }
        if (step != 0 && (warnedStep == 0 || step < warnedStep)) {
            val minutes = ((until!! + 59_999L) / 60_000L).toInt().coerceAtLeast(1)
            val rules = Restricted.rules
            val usage = Restricted.usage
            val toBreak = rules.breakEvery > 0 && usage.allowUntil <= now &&
                rules.breakEvery * 60_000L - usage.continuousMs == until
            warn(ctx, ctx.getString(if (toBreak) R.string.kids_warn_break else R.string.kids_warn, minutes))
        }
        warnedStep = step
    }

    private fun warn(ctx: Context, text: String) {
        DiagLog.i("kids: warning \"$text\"")
        when {
            homeInFront -> Restricted.warning = text to SystemClock.uptimeMillis()
            HomeGuardService.showBanner(text) -> Unit
            else -> runCatching { Toast.makeText(ctx, text, Toast.LENGTH_LONG).show() }
        }
    }

    private fun bringBack(ctx: Context, why: String) {
        val now = SystemClock.uptimeMillis()
        if (now - broughtBackAt < 3_000) return
        broughtBackAt = now
        DiagLog.i("kids: bringing Aerial back, $why")
        if (HomeGuardService.bringAerialBack()) return
        runCatching { ctx.startActivity(MainActivity.bringBackIntent(ctx)) }
            .onFailure { DiagLog.i("kids: could not bring Aerial back: $it") }
    }

    private val appCache = HashMap<String, Boolean>()

    /**
     * Whether the mode keeps [pkg] off the screen: once locked every app, else whatever is not
     * allowed, when it is an app (it has a launcher entry), the TV's settings or an input's player.
     * Dialogs, keyboards, permission prompts and the system's own windows go through.
     */
    fun keepsAway(ctx: Context, pkg: String): Boolean {
        if (!Restricted.active || pkg == ctx.packageName || QuizOverlay.visible) return false
        if (Restricted.lock == null && (!Restricted.blockOthers || pkg in Restricted.allowedPackages)) return false
        if (pkg == keyboard(ctx)) return false
        return pkg in Restricted.sourcePlayers || SystemProbe.isSettingsApp(ctx, pkg) || appCache.getOrPut(pkg) {
            val pm = ctx.packageManager
            runCatching { pm.getLeanbackLaunchIntentForPackage(pkg) != null || pm.getLaunchIntentForPackage(pkg) != null }.getOrDefault(false)
        }
    }

    private fun keyboard(ctx: Context): String? =
        runCatching { Settings.Secure.getString(ctx.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD) }.getOrNull()
            ?.substringBefore('/')

    /** Home Guard said it kept [pkg] away: Home names it. */
    fun noteKeptAway(ctx: Context, pkg: String) {
        val label = runCatching { ctx.packageManager.getApplicationLabel(ctx.packageManager.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)
        DiagLog.i("kids: kept $pkg away")
        // Locked, the lock screen says why; naming the app would only add to it.
        if (Restricted.lock == null) Restricted.blocked = label to SystemClock.uptimeMillis()
    }

    /** Without Home Guard, usage stats say what is in front. */
    private fun watchesUsage(): Boolean =
        Restricted.active && (Restricted.blockOthers || Restricted.lock != null) && !HomeGuardService.isRunning &&
            !homeInFront && app?.let { HomeGuardService.usageStatsAllowed(it) } == true

    private fun keepAwayByUsage(ctx: Context) {
        val front = frontByUsage(ctx) ?: return
        if (!keepsAway(ctx, front)) return
        noteKeptAway(ctx, front)
        bringBack(ctx, "$front in front (usage stats)")
    }

    internal fun quizForegroundPackage(ctx: Context): String? {
        if (homeInFront) return null
        HomeGuardService.quizForegroundPackage()?.let { return it.takeUnless { pkg -> pkg == ctx.packageName } }
        if (!HomeGuardService.usageStatsAllowed(ctx)) return null
        return UsageFront.read(ctx, System.currentTimeMillis(), 60 * 60_000L)
            ?.takeUnless { it.closed || it.pkg == ctx.packageName }?.pkg
    }

    private fun frontByUsage(ctx: Context): String? = runCatching {
        if (Build.VERSION.SDK_INT < 22) return null
        val now = System.currentTimeMillis()
        val events = ctx.getSystemService<UsageStatsManager>()?.queryEvents(now - 60_000L, now) ?: return null
        val e = UsageEvents.Event()
        var last: String? = null
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            if (e.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) last = e.packageName
        }
        last
    }.getOrNull()

    /** Spelled out: R8 renames the classes. */
    private fun lockName(lock: KidsLock?) = when (lock) { is KidsLock.Break -> "break"; KidsLock.DayUsed -> "day used"; KidsLock.Late -> "late"; null -> "unlocked" }

    /** What the lock says, for the diagnostics. */
    fun diagnosticState(): String {
        val r = Restricted
        if (!r.enabled) return "Kids mode off"
        val lock = lockName(r.lock)
        return "Kids mode ${r.kind.key} active=${r.active} allowed=${r.allowed.size} block=${r.blockOthers} rules=${r.rules} " +
            "used=${r.usage.usedMs / 60_000} min continuous=${r.usage.continuousMs / 60_000} min lock=$lock; $diagnostic"
    }
}
