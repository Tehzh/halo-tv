package app.orchard.tv.settings

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** Who the restricted Home is for. Both show only the apps chosen and let nothing be rearranged. */
enum class RestrictedKind(val key: String) {
    /** Time limits and breaks, other apps kept away. */
    Kids("kids"),

    /** Bigger tiles with every name under them; no limits unless chosen. */
    Elders("elders");

    companion object {
        fun of(key: String?) = entries.firstOrNull { it.key == key } ?: Kids
    }
}

/** Why the restricted Home is locked, if it is. */
sealed interface KidsLock {
    /** A break after [RestrictedRules.breakEvery] minutes of watching, until [untilMs] (wall clock). */
    data class Break(val untilMs: Long) : KidsLock {
        /** A quiz waits for an answer, with no timed countdown. */
        fun remainingSeconds(now: Long): Long? =
            if (untilMs == Long.MAX_VALUE) null else ((untilMs - now) / 1000).coerceAtLeast(0)
    }

    /** The day's minutes are used up. */
    data object DayUsed : KidsLock

    /** Outside the hours the TV may be watched: [RestrictedRules.startMinute] to [RestrictedRules.endMinute]. */
    data object Late : KidsLock
}

/**
 * The limits chosen; 0 is "none" for the day's minutes, [endMinute] and [breakEvery]. Any number
 * of minutes and any time of day: the TV offers a few of each, the phone page any (#133).
 */
data class RestrictedRules(
    val weekdayMinutes: Int = 0,
    val weekendMinutes: Int = 0,
    /** The TV may be watched from this minute of the day... */
    val startMinute: Int = KidsTime.DEFAULT_START,
    /** ...up to this one; 0 for all day. Always after [startMinute] (see [valid]). */
    val endMinute: Int = 0,
    /** Minutes of watching before a break of [breakMinutes]. */
    val breakEvery: Int = 0,
    val breakMinutes: Int = KidsTime.BREAK_MINUTES,
) {
    val any: Boolean get() = weekdayMinutes > 0 || weekendMinutes > 0 || endMinute > 0 || breakEvery > 0

    /** Within what the TV can keep: a day at most, a window that ends after it starts. */
    val valid: Boolean get() = weekdayMinutes in 0..KidsTime.DAY && weekendMinutes in 0..KidsTime.DAY &&
        startMinute in 0 until KidsTime.DAY && (endMinute == 0 || endMinute in startMinute + 1 until KidsTime.DAY) &&
        breakEvery in 0..KidsTime.DAY && breakMinutes in 1..120
}

/** What has been watched: kept per day, in wall-clock and watching milliseconds. */
data class KidsUsage(
    val usedMs: Long = 0,
    /** Watching since the last break, or since the TV last sat idle for a break's length. */
    val continuousMs: Long = 0,
    /** A break runs until then (wall clock); 0 for none. */
    val breakUntil: Long = 0,
    /** A parent allowed watching until then whatever the limits say (wall clock); 0 for none. */
    val allowUntil: Long = 0,
)

/**
 * The time rules, apart from the clock and the TV so that they can be tested: whether the TV is
 * locked now, and how long watching can go on before it is.
 */
object KidsTime {
    const val DAY = 24 * 60

    /** Where the window starts unless the phone page says otherwise: 6:00. */
    const val DEFAULT_START = 6 * 60
    const val BREAK_MINUTES = 5

    /** "7:05" for a minute of the day. */
    fun clock(minute: Int) = "%d:%02d".format(minute / 60, minute % 60)

    fun limitMinutes(rules: RestrictedRules, day: LocalDate): Int =
        if (day.dayOfWeek == DayOfWeek.SATURDAY || day.dayOfWeek == DayOfWeek.SUNDAY) rules.weekendMinutes else rules.weekdayMinutes

    private fun late(rules: RestrictedRules, at: LocalDateTime): Boolean {
        if (rules.endMinute <= 0) return false
        val m = at.hour * 60 + at.minute
        return m < rules.startMinute || m >= rules.endMinute
    }

    /** The lock the rules give at [now], ignoring a parent's allowance. */
    private fun ruleLock(rules: RestrictedRules, usage: KidsUsage, now: Long, zone: ZoneId): KidsLock? {
        val at = Instant.ofEpochMilli(now).atZone(zone).toLocalDateTime()
        if (late(rules, at)) return KidsLock.Late
        val limit = limitMinutes(rules, at.toLocalDate())
        if (limit > 0 && usage.usedMs >= limit * 60_000L) return KidsLock.DayUsed
        if (usage.breakUntil > now) return KidsLock.Break(usage.breakUntil)
        return null
    }

    fun lock(rules: RestrictedRules, usage: KidsUsage, now: Long, zone: ZoneId = ZoneId.systemDefault()): KidsLock? =
        if (usage.allowUntil > now) null else ruleLock(rules, usage, now, zone)

    /**
     * How long from [now] watching can go on before a lock, if one is coming: the day's minutes
     * left, the latest hour, the next break, or the end of a parent's allowance when the rules
     * would lock then. Null when nothing will lock today.
     */
    fun untilLockMs(rules: RestrictedRules, usage: KidsUsage, now: Long, zone: ZoneId = ZoneId.systemDefault()): Long? {
        if (lock(rules, usage, now, zone) != null) return 0
        val at = Instant.ofEpochMilli(now).atZone(zone).toLocalDateTime()
        if (usage.allowUntil > now) {
            // Watching does not count against the limits meanwhile only in that nothing locks;
            // the minutes still add up, so look at the end of the allowance as it will be then.
            val end = usage.copy(usedMs = usage.usedMs + (usage.allowUntil - now), allowUntil = 0)
            return if (ruleLock(rules, end, usage.allowUntil, zone) != null) usage.allowUntil - now else null
        }
        val left = mutableListOf<Long>()
        val limit = limitMinutes(rules, at.toLocalDate())
        if (limit > 0) left += limit * 60_000L - usage.usedMs
        if (rules.endMinute > 0) {
            val cutoff = at.toLocalDate().atTime(rules.endMinute / 60, rules.endMinute % 60).atZone(zone).toInstant().toEpochMilli()
            left += cutoff - now
        }
        if (rules.breakEvery > 0) left += rules.breakEvery * 60_000L - usage.continuousMs
        return left.filter { it > 0 }.minOrNull()
    }

    /**
     * [usage] after [watchedMs] of watching (or [idleMs] of not watching) up to [now]: a break
     * starts once the continuous watching reaches the rules' length, and an idle stretch as long
     * as a break counts as one.
     */
    fun advance(rules: RestrictedRules, usage: KidsUsage, watchedMs: Long, idleMs: Long, now: Long): KidsUsage {
        var u = usage
        if (watchedMs > 0) u = u.copy(usedMs = u.usedMs + watchedMs, continuousMs = u.continuousMs + watchedMs)
        if (idleMs >= rules.breakMinutes * 60_000L) u = u.copy(continuousMs = 0)
        if (rules.breakEvery > 0 && u.continuousMs >= rules.breakEvery * 60_000L && u.allowUntil <= now) {
            u = u.copy(continuousMs = 0, breakUntil = now + rules.breakMinutes * 60_000L)
        }
        return u
    }
}

/**
 * Kids and Elders mode (issues #133 #604 #159 #863 #718 #1013): a Home with only the tiles chosen
 * here, nothing to rearrange, Control Center without settings, and for children optional time
 * limits. Starting it takes nothing; leaving it takes the parental PIN, and only until Aerial's
 * screensaver starts, the screen goes off or Aerial restarts ([suspended]).
 *
 * The owner's own Home (order, folders, hidden apps) is never touched: the mode keeps its own list.
 * Compose state, written on the main thread only; Home Guard reads it in the same process.
 */
object Restricted {
    private const val PREFS = "restricted"
    private var started = false
    private lateinit var app: Context

    /** Set up and turned on: Home starts in the mode unless a parent left it ([suspended]). */
    var enabled by mutableStateOf(false); private set
    var kind by mutableStateOf(RestrictedKind.Kids); private set

    /** Tile keys (AppEntry.key) shown in the mode, in Home's order. */
    var allowed by mutableStateOf<List<String>>(emptyList()); private set

    /**
     * The packages behind [allowed] (an input's player, a shortcut's app), kept so that Home Guard
     * knows them without Home's app list. Written by HomeViewModel whenever it arranges.
     */
    var allowedPackages: Set<String> = emptySet(); private set

    /** Packages that play the TV's inputs: kept away like apps, unless an input is allowed. */
    var sourcePlayers: Set<String> = emptySet(); private set

    /** Other apps, opened by the remote's keys, the voice assistant or an app, are sent back to Home. */
    var blockOthers by mutableStateOf(true); private set
    var rules by mutableStateOf(RestrictedRules()); private set
    var elderReminderMinutes by mutableIntStateOf(0); private set
    fun chooseElderReminder(minutes: Int) { elderReminderMinutes = minutes.coerceIn(0, 1440); prefs().edit { putInt("elder_reminder", elderReminderMinutes) } }

    /** A parent left the mode with the PIN; it comes back by itself (see [rearm]). Not kept. */
    var suspended by mutableStateOf(false); private set

    /** In the mode now. */
    val active: Boolean get() = enabled && !suspended && allowed.isNotEmpty()

    var usage by mutableStateOf(KidsUsage()); private set
    private var usageDay = ""

    /** The lock now, recomputed by KidsWatch; null while not locked or not [active]. */
    var lock by mutableStateOf<KidsLock?>(null)

    /** An app kept away by Home Guard, for Home to say so: its label (or package) and when. */
    var blocked by mutableStateOf<Pair<String, Long>?>(null)

    /** A warning for Home to show while it is in front (KidsWatch); counts up for each new one. */
    var warning by mutableStateOf<Pair<String, Long>?>(null)

    /** Columns on Home in the mode: bigger tiles. */
    const val COLUMNS = 4

    fun init(context: Context) {
        if (started) return
        started = true
        app = context.applicationContext
        val p = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        enabled = p.getBoolean("enabled", false)
        suspended = p.getBoolean("suspended", false)
        kind = RestrictedKind.of(p.getString("kind", null))
        allowed = p.getString("allowed_" + kind.key, p.getString("allowed", null))?.split('\n')?.filter { it.isNotEmpty() } ?: emptyList()
        allowedPackages = p.getStringSet("allowed_packages", null)?.toSet() ?: emptySet()
        sourcePlayers = p.getStringSet("source_players", null)?.toSet() ?: emptySet()
        blockOthers = p.getBoolean("block_others", true)
        rules = RestrictedRules(
            weekdayMinutes = p.getInt("weekday_minutes", 0),
            weekendMinutes = p.getInt("weekend_minutes", 0),
            startMinute = p.getInt("start_minute", KidsTime.DEFAULT_START),
            endMinute = p.getInt("end_minute", 0),
            breakEvery = p.getInt("break_every", 0),
            breakMinutes = p.getInt("break_minutes", KidsTime.BREAK_MINUTES),
        ).takeIf { it.valid } ?: RestrictedRules()
        elderReminderMinutes = p.getInt("elder_reminder", 0).coerceIn(0, 1440)
        quizRewardMax = p.getInt("quiz_reward_max", 10).coerceIn(1, 120)
        usageDay = p.getString("usage_day", "") ?: ""
        usage = KidsUsage(
            usedMs = p.getLong("used_ms", 0),
            continuousMs = p.getLong("continuous_ms", 0),
            breakUntil = p.getLong("break_until", 0),
            allowUntil = p.getLong("allow_until", 0),
        )
        rollDay(System.currentTimeMillis())
    }


    var quizRewardMax by mutableIntStateOf(10); private set
    fun chooseQuizRewardMax(minutes: Int) {
        quizRewardMax = minutes.coerceIn(1, 120)
        prefs().edit { putInt("quiz_reward_max", quizRewardMax) }
    }
    fun completeQuiz(reward: Int) {
        val now = System.currentTimeMillis()
        val cap = minOf(quizRewardMax, reward.coerceAtLeast(1), rules.breakEvery.coerceAtLeast(1))
        val remaining = limitToday(now).let { if (it == 0) cap else ((it * 60_000L - usage.usedMs).coerceAtLeast(0L) / 60_000L).toInt() }
        val awarded = minOf(cap, remaining)
        saveUsage(usage.copy(breakUntil = 0L, continuousMs = ((rules.breakEvery - awarded).coerceAtLeast(0)) * 60_000L, allowUntil = 0L))
        lock = lockNow(now)
    }

    private fun prefs() = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun chooseEnabled(on: Boolean) {
        enabled = on
        // Turned on in setup: the parent is still there; the mode starts with "Start" or by itself.
        suspended = on
        prefs().edit { putBoolean("enabled", on); putBoolean("suspended", on) }
    }

    fun chooseKind(k: RestrictedKind) {
        if (kind == k) return
        val p = prefs()
        p.edit { putString("allowed_" + kind.key, allowed.joinToString("\n")); putString("kind", k.key) }
        kind = k
        allowed = p.getString("allowed_" + k.key, "")?.split('\n')?.filter { it.isNotEmpty() } ?: emptyList()
        allowedPackages = emptySet()
        p.edit { putStringSet("allowed_packages", emptySet()) }
    }

    fun chooseAllowed(keys: List<String>) {
        allowed = keys
        prefs().edit { putString("allowed_" + kind.key, keys.joinToString("\n")) }
    }

    /** Save the other mode's list without switching an active mode. */
    fun chooseAllowedFor(k: RestrictedKind, keys: List<String>) {
        if (k == kind) chooseAllowed(keys)
        else prefs().edit { putString("allowed_" + k.key, keys.joinToString("\n")) }
    }

    fun allowedFor(k: RestrictedKind): List<String> = if (k == kind) allowed else
        prefs().getString("allowed_" + k.key, "")?.split('\n')?.filter { it.isNotEmpty() } ?: emptyList()

    fun toggleAllowed(key: String) = chooseAllowed(if (key in allowed) allowed - key else allowed + key)

    /** The packages behind the allowed tiles and every input's player; see [allowedPackages]. */
    fun notePackages(allowedPkgs: Set<String>, players: Set<String>) {
        if (allowedPkgs == allowedPackages && players == sourcePlayers) return
        allowedPackages = allowedPkgs
        sourcePlayers = players
        prefs().edit { putStringSet("allowed_packages", allowedPkgs); putStringSet("source_players", players) }
    }

    fun chooseBlockOthers(on: Boolean) {
        blockOthers = on
        prefs().edit { putBoolean("block_others", on) }
    }

    /**
     * New rules hold at once: time a parent allowed under the old ones ends with them. Kept, an
     * allowance until 21:00 left a window ending at 17:00, set from the phone at 19:20, doing
     * nothing (MiTV-MFTR0, 2026-11-02).
     */
    fun chooseRules(r: RestrictedRules) {
        if (r != rules && usage.allowUntil > 0) saveUsage(usage.copy(allowUntil = 0))
        rules = r
        prefs().edit {
            putInt("weekday_minutes", r.weekdayMinutes); putInt("weekend_minutes", r.weekendMinutes)
            putInt("start_minute", r.startMinute); putInt("end_minute", r.endMinute)
            putInt("break_every", r.breakEvery); putInt("break_minutes", r.breakMinutes)
        }
    }

    /** Into the mode, no PIN needed. */
    fun start() { suspended = false; prefs().edit { putBoolean("suspended", false) } }

    /** Out of it for now, once the PIN was right. */
    fun suspend() { suspended = true; prefs().edit { putBoolean("suspended", true) } }

    /** The mode comes back by itself: Aerial's screensaver started or the screen went off. */
    fun rearm() { }

    /** Turned off for good, from ADB when the PIN is forgotten (ConfigReceiver kids_off). */
    fun turnOff() = chooseEnabled(false).also { suspended = false }

    private fun dayOf(now: Long) = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalDate().toString()

    /** A new day starts with nothing watched; a break or an allowance from yesterday is over. */
    private fun rollDay(now: Long) {
        val day = dayOf(now)
        if (day == usageDay) return
        usageDay = day
        saveUsage(KidsUsage(allowUntil = usage.allowUntil.takeIf { it > now } ?: 0))
    }

    fun saveUsage(u: KidsUsage) {
        usage = u
        prefs().edit {
            putString("usage_day", usageDay)
            putLong("used_ms", u.usedMs); putLong("continuous_ms", u.continuousMs)
            putLong("break_until", u.breakUntil); putLong("allow_until", u.allowUntil)
        }
    }

    /** [watchedMs] more of watching, or [idleMs] of not watching, at [now]. */
    fun count(watchedMs: Long, idleMs: Long, now: Long) {
        rollDay(now)
        val next = if (kind == RestrictedKind.Kids) {
            var u = usage.copy(usedMs = usage.usedMs + watchedMs, continuousMs = usage.continuousMs + watchedMs)
            if (rules.breakEvery > 0 && u.breakUntil == 0L && u.continuousMs >= rules.breakEvery * 60_000L) {
                u = u.copy(breakUntil = Long.MAX_VALUE)
            }
            u
        } else KidsTime.advance(rules.copy(weekdayMinutes = 0, weekendMinutes = 0, endMinute = 0, breakEvery = 0), usage, watchedMs, idleMs, now)
        if (next != usage) saveUsage(next)
    }

    /** A parent allows watching for [minutes] more, or until the end of today with null. */
    fun allow(minutes: Int?) {
        val now = System.currentTimeMillis()
        rollDay(now)
        val until = if (minutes == null) {
            Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalDate().plusDays(1)
                .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        } else maxOf(now, usage.allowUntil) + minutes * 60_000L
        // A break being taken is over too; the watching starts counting toward the next one again.
        saveUsage(usage.copy(allowUntil = until, breakUntil = 0, continuousMs = 0))
        lock = null
    }

    fun lockNow(now: Long = System.currentTimeMillis()): KidsLock? {
        if (!active || kind == RestrictedKind.Elders) return null
        rollDay(now)
        return KidsTime.lock(rules, usage.copy(allowUntil = 0), now)
    }

    fun untilLockMs(now: Long = System.currentTimeMillis()): Long? =
        if (!active || kind == RestrictedKind.Elders) null else KidsTime.untilLockMs(rules, usage.copy(allowUntil = 0), now)

    /** The day's limit, in minutes; 0 for none. */
    fun limitToday(now: Long = System.currentTimeMillis()): Int =
        KidsTime.limitMinutes(rules, Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalDate())
}
