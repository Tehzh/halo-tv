package app.orchard.tv.lite

import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import android.provider.Settings
import java.security.MessageDigest

/** As the full app's FocusStyle: High Contrast adds the white frame (the default on D-pad remotes). */
enum class FocusStyle(val key: String) {
    Standard("standard"), HighContrast("high_contrast");

    companion object {
        fun of(key: String?) = entries.firstOrNull { it.key == key } ?: HighContrast
    }
}

/** As the full app's UiSounds: the TV's own key sounds, Aerial's, or none. */
enum class UiSounds(val key: String) {
    Tv("tv"), Aerial("aerial"), Off("off");

    companion object {
        fun of(key: String?) = entries.firstOrNull { it.key == key }
    }
}

/** 24- or 12-hour time, or as the TV is set; the full app's ClockHours (issues #1324 #1044). */
enum class ClockHours(val key: String) {
    System("system"), H24("24"), H12("12");

    companion object {
        fun of(key: String?) = values().firstOrNull { it.key == key } ?: System
    }
}

/**
 * Lite's preferences, under the full app's names and defaults (settings/AerialSettings.kt), so the
 * two read alike and the phone page's settings mean the same. Main thread only.
 */
object LiteSettings {
    private const val PREFS = "settings"
    private lateinit var prefs: SharedPreferences

    private var chosenFocusStyle = FocusStyle.HighContrast
    /** As chosen; always the white frame in Elders mode (Kids), as in the full app. */
    val focusStyle: FocusStyle get() = if (Kids.active && Kids.kind == KidsKind.Elders) FocusStyle.HighContrast else chosenFocusStyle
    var oneRowHome = true; private set
    var peekNextRow = false; private set
    /** Apps a row on Home and in folders, as the full app's: four, five or six. */
    private var chosenColumns = 5
    /** As chosen; [Kids.COLUMNS] in Kids and Elders mode. */
    val homeColumns: Int get() = if (Kids.active) Kids.COLUMNS else chosenColumns
    var homePlate = true; private set
    var compactDockCorners = false; private set
    private var chosenAllNames = false
    /** As chosen; every name in Elders mode. */
    val showAllNames: Boolean get() = chosenAllNames || (Kids.active && Kids.kind == KidsKind.Elders)
    var clockRight = false; private set
    /** 0 left, 1 right, 2 center, 3 compact status bar. */
    var clockPosition = 0; private set
    /** Guard's default / large / extra large clock sizes. */
    var clockSizeMode = 0; private set
    /** 0 white, 1 black, 2 dark gray. */
    var desktopTextColor = 0; private set
    /** 0 always visible, 1 fade after five seconds, 2 hidden except a brief explicit wake. */
    var settingsIconVisibility = 0; private set
    var largeClock = true; private set
    var showDeviceCapacity = true; private set
    var clockHours = ClockHours.System; private set
    /** No app menus on Home or in folders, as the full app's homeLocked (issues #717 #1013 #1106). */
    var homeLocked = false; private set
    /** How often "My Photos, in turn" moves on, in minutes; the full app's photoMinutes (#1314). */
    var photoMinutes = DAY_MINUTES; private set
    const val DAY_MINUTES = 1440
    val PHOTO_MINUTES = listOf(DAY_MINUTES, 60, 15, 5, 1)
    var showHomeGreeting = true; private set
    var showHomeTime = true; private set
    var showHomeDate = true; private set
    var flipClock24Hour = true; private set
    var flipClockSeconds = false; private set
    var showWeather = true; private set
    var showLunarDate = false; private set
    var screensaverClock = true; private set
    var backStartsScreensaver = true; private set
    var uiSounds = UiSounds.Tv; private set
    var screensaverMinutes = 5; private set
    var bootApp: String? = null; private set

    const val NEVER = 0
    val HOME_COLUMNS = 4..6
    /** The full app's choices. */
    val screensaverChoices = listOf(2, 5, 10, 20, NEVER)

    var pinSet = false; private set
    var pinArrows = false; private set
    private var pinHash: String? = null
    private var unlockedUntil = 0L
    private const val UNLOCK_MS = 10 * 60 * 1000L

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        chosenFocusStyle = FocusStyle.of(prefs.getString("focus_style", null))
        oneRowHome = prefs.getBoolean("one_row_home", true)
        peekNextRow = prefs.getBoolean("peek_next_row", false)
        chosenColumns = prefs.getInt("home_columns", 5).takeIf { it in HOME_COLUMNS } ?: 5
        compactDockCorners = prefs.getBoolean("compact_dock_corners", false)
        homePlate = prefs.getBoolean("home_plate", true)
        chosenAllNames = prefs.getBoolean("show_all_names", false)
        settingsIconVisibility = prefs.getInt("settings_icon_visibility", 0).coerceIn(0, 2)
        clockRight = prefs.getBoolean("clock_right", false)
        clockPosition = prefs.getInt("clock_position", prefs.getInt("halo_clock_position", if (clockRight) 1 else 0)).coerceIn(0, 3)
        desktopTextColor = prefs.getInt("clock_text_color", prefs.getInt("halo_text_color", 0)).coerceIn(0, 2)
        clockSizeMode = if (prefs.contains("clock_size_mode_v2")) prefs.getInt("clock_size_mode_v2", 0).coerceIn(0, 2) else (prefs.getInt("clock_size_mode", if (prefs.getBoolean("large_clock", true)) 1 else 0) - 1).coerceIn(0, 1)
        largeClock = true
        showDeviceCapacity = prefs.getBoolean("show_device_capacity", true)
        clockHours = ClockHours.of(prefs.getString("clock_hours", null))
        homeLocked = prefs.getBoolean("home_locked", false)
        photoMinutes = prefs.getInt("photo_minutes", DAY_MINUTES).takeIf { it in PHOTO_MINUTES } ?: DAY_MINUTES
        showHomeGreeting = prefs.getBoolean("show_home_greeting", true)
        showHomeTime = prefs.getBoolean("show_home_time", true)
        showHomeDate = prefs.getBoolean("show_home_date", true)
        flipClock24Hour = prefs.getBoolean("flip_clock_24_hour", true)
        flipClockSeconds = prefs.getBoolean("flip_clock_seconds", false)
        showWeather = prefs.getBoolean("show_weather", true)
        showLunarDate = prefs.getBoolean("show_lunar_date", false)
        screensaverClock = prefs.getBoolean("screensaver_clock", true)
        backStartsScreensaver = prefs.getBoolean("back_starts_screensaver", true)
        // As the full app: the TV's own sounds where its key sound switch is on, else Aerial's.
        uiSounds = UiSounds.of(prefs.getString("ui_sounds_kind", null)) ?: if (
            runCatching { Settings.System.getInt(context.contentResolver, Settings.System.SOUND_EFFECTS_ENABLED, 1) }.getOrDefault(1) == 1
        ) UiSounds.Tv else UiSounds.Aerial
        screensaverMinutes = prefs.getInt("screensaver_minutes", 5)
        bootApp = prefs.getString("boot_app", null)
        pinHash = prefs.getString("pin_hash", null)
        pinSet = pinHash != null
        pinArrows = prefs.getBoolean("pin_arrows", false)
    }

    private fun put(block: SharedPreferences.Editor.() -> Unit) = prefs.edit(action = block)

    fun chooseFocusStyle(v: FocusStyle) { chosenFocusStyle = v; put { putString("focus_style", v.key) } }
    fun chooseOneRowHome(v: Boolean) { oneRowHome = v; put { putBoolean("one_row_home", v) } }
    fun chooseHomeColumns(v: Int) { if (v in HOME_COLUMNS) { chosenColumns = v; put { putInt("home_columns", v) } } }
    fun choosePeekNextRow(v: Boolean) { peekNextRow = v; put { putBoolean("peek_next_row", v) } }
    fun chooseCompactDockCorners(v: Boolean) { compactDockCorners = v; put { putBoolean("compact_dock_corners", v) } }
    fun chooseHomePlate(v: Boolean) { homePlate = v; put { putBoolean("home_plate", v) } }
    fun chooseShowAllNames(v: Boolean) { chosenAllNames = v; put { putBoolean("show_all_names", v) } }
    fun chooseClockRight(v: Boolean) { clockRight = v; put { putBoolean("clock_right", v) } }
    fun chooseClockPosition(v: Int) { if (v in 0..3) { clockPosition = v; clockRight = v == 1; put { putInt("clock_position", v); putBoolean("clock_right", clockRight) } } }
    fun chooseSettingsIconVisibility(v: Int) { if (v in 0..2) { settingsIconVisibility = v; put { putInt("settings_icon_visibility", v) } } }
    fun chooseDesktopTextColor(v: Int) { if (v in 0..2) { desktopTextColor = v; put { putInt("clock_text_color", v) } } }
    fun chooseClockSizeMode(v: Int) { if (v in 0..2) { clockSizeMode = v; largeClock = true; put { putInt("clock_size_mode_v2", v); putBoolean("large_clock", true) } } }
    fun chooseShowDeviceCapacity(v: Boolean) { showDeviceCapacity = v; put { putBoolean("show_device_capacity", v) } }
    fun chooseLargeClock(v: Boolean) { largeClock = v; put { putBoolean("large_clock", v) } }
    fun chooseShowLunarDate(v: Boolean) { showLunarDate = v; put { putBoolean("show_lunar_date", v) } }
    fun choosePhotoMinutes(v: Int) { if (v in PHOTO_MINUTES) { photoMinutes = v; put { putInt("photo_minutes", v) } } }
    fun chooseHomeLocked(v: Boolean) { homeLocked = v; put { putBoolean("home_locked", v) } }
    fun chooseClockHours(v: ClockHours) { clockHours = v; put { putString("clock_hours", v.key) } }
    fun chooseShowHomeGreeting(v: Boolean) { showHomeGreeting = v; put { putBoolean("show_home_greeting", v) } }
    fun chooseShowHomeTime(v: Boolean) { showHomeTime = v; put { putBoolean("show_home_time", v) } }
    fun chooseShowHomeDate(v: Boolean) { showHomeDate = v; put { putBoolean("show_home_date", v) } }
    fun chooseFlipClock24Hour(v: Boolean) { flipClock24Hour = v; put { putBoolean("flip_clock_24_hour", v) } }
    fun chooseFlipClockSeconds(v: Boolean) { flipClockSeconds = v; put { putBoolean("flip_clock_seconds", v) } }
    fun chooseShowWeather(v: Boolean) { showWeather = v; put { putBoolean("show_weather", v) } }
    fun chooseScreensaverClock(v: Boolean) { screensaverClock = v; put { putBoolean("screensaver_clock", v) } }
    fun chooseBackStartsScreensaver(v: Boolean) { backStartsScreensaver = v; put { putBoolean("back_starts_screensaver", v) } }
    fun chooseUiSounds(v: UiSounds) { uiSounds = v; put { putString("ui_sounds_kind", v.key) } }
    fun chooseScreensaverMinutes(v: Int) { screensaverMinutes = v; put { putInt("screensaver_minutes", v) } }
    fun chooseBootApp(pkg: String?) { bootApp = pkg; put { putString("boot_app", pkg) } }

    /** Animations and the focused tile's shadow: with hardware acceleration, unless Effects says Lite. */
    fun full(hardware: Boolean): Boolean = hardware

    // ---- parental PIN (the full app's, issue #10) ----

    val locked: Boolean get() = pinSet && SystemClock.elapsedRealtime() > unlockedUntil

    fun unlock(pin: String): Boolean {
        if (hash(pin) != pinHash) return false
        unlockedUntil = SystemClock.elapsedRealtime() + UNLOCK_MS
        return true
    }

    fun relock() { unlockedUntil = 0L }

    /** Whether [pin] is the PIN, without unlocking anything: for Kids mode, as in the full app. */
    fun pinMatches(pin: String): Boolean = pinHash != null && hash(pin) == pinHash

    /** Sets the PIN, digits or [arrows] (U, D, L, R), or turns it off with null. */
    fun choosePin(pin: String?, arrows: Boolean = false) {
        pinHash = pin?.let(::hash)
        pinSet = pinHash != null
        pinArrows = pin != null && arrows
        // Locked from the start: a parent who sets the PIN and hands the remote over expects it
        // to hold at once, not after the next Home press (issue #718).
        unlockedUntil = 0L
        put { putString("pin_hash", pinHash); putBoolean("pin_arrows", pinArrows) }
    }

    /** The full app's hash, so a PIN reads the same in both. */
    private fun hash(pin: String): String =
        MessageDigest.getInstance("SHA-256").digest("aerial:$pin".toByteArray()).joinToString("") { "%02x".format(it) }
}
