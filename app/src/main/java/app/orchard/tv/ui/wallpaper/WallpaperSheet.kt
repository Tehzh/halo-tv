package app.orchard.tv.ui.wallpaper

import androidx.compose.ui.draw.drawBehind

import android.app.Activity
import android.os.Build
import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.drop
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.type
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.viewinterop.AndroidView
import app.orchard.tv.wallpaper.LocalSaverSelection
import app.orchard.tv.wallpaper.ScreensaverVideos
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import app.orchard.tv.R
import app.orchard.tv.settings.AerialSettings
import app.orchard.tv.settings.ClockHours
import app.orchard.tv.settings.Restricted
import app.orchard.tv.settings.RestrictedKind
import app.orchard.tv.settings.FocusStyle
import app.orchard.tv.settings.UiSounds
import app.orchard.tv.settings.Effects
import app.orchard.tv.settings.HomeRefreshRate
import app.orchard.tv.system.Sounds
import app.orchard.tv.ui.components.settingsFrost
import app.orchard.tv.ui.components.applySettingsFrost
import app.orchard.tv.ui.components.liquidGlass
import app.orchard.tv.ui.components.Pressable
import app.orchard.tv.ui.components.consumeTaps
import app.orchard.tv.ui.components.dismissOnTap
import app.orchard.tv.ui.theme.Glyph
import app.orchard.tv.ui.theme.GlyphIcon
import app.orchard.tv.ui.theme.LocalReducedMotion
import app.orchard.tv.ui.theme.Motion
import app.orchard.tv.ui.theme.Palette
import app.orchard.tv.ui.theme.Type
import app.orchard.tv.wallpaper.PhoneServer
import app.orchard.tv.wallpaper.AerialFormats
import app.orchard.tv.wallpaper.AerialQuality
import app.orchard.tv.wallpaper.Aerials
import app.orchard.tv.wallpaper.ScreensaverMode
import app.orchard.tv.system.SystemScreensaver
import app.orchard.tv.system.DisplayRefreshRate
import app.orchard.tv.wallpaper.Wallpaper
import app.orchard.tv.wallpaper.WallpaperKind
import app.orchard.tv.wallpaper.Wallpapers
import app.orchard.tv.wallpaper.OnlineRotation
import app.orchard.tv.wallpaper.BingRotationStatus
import app.orchard.tv.ui.home.customKidsTime
import app.orchard.tv.weather.Weather
import app.orchard.tv.weather.WeatherKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.roundToInt

/**
 * Wallpaper selection occupies the screen; the other personalisation settings retain their sheet.
 * The QR code opens the phone page for sending photos and videos.
 */
@Composable
fun WallpaperSheet(
    visible: Boolean,
    wallpaperOnly: Boolean = false,
    onDismiss: () -> Unit,
    hiddenCount: Int,
    onHiddenApps: () -> Unit,
    onHomeLock: (on: Boolean) -> Unit,
    onStartScreensaver: () -> Unit,
    onRefreshRateChanged: () -> Unit,
    onPin: (on: Boolean) -> Unit,
    /** Kids and Elders mode's setup (behind the PIN). */
    onKids: () -> Unit,
    /** The sheet is all the way in (true) or not: Home need not draw under it then ([SheetCovers]). */
    onSettled: (Boolean) -> Unit = {},
    /** Draw the closed sheet once, nearly clear, so that its first opening is not its first draw (HomeScreen). */
    warm: Boolean = false,
) {
    val context = LocalContext.current
    var wallpaperCategory by remember { mutableStateOf(0) }
    var settingsCategory by remember { mutableStateOf(0) }
    var phoneUrl by remember { mutableStateOf<String?>(null) }
    var qr by remember { mutableStateOf<ImageBitmap?>(null) }
    val recentPrefs = remember { context.getSharedPreferences("halo_settings_navigation", android.content.Context.MODE_PRIVATE) }
    var recentCategory by remember { mutableStateOf(recentPrefs.getInt("recent_category", -1)) }
    var confirmReset by remember { mutableStateOf(false) }

    LaunchedEffect(visible) {
        if (!visible || wallpaperOnly) return@LaunchedEffect
        snapshotFlow {
            listOf(AerialSettings.oneRowHome, AerialSettings.homeColumns, AerialSettings.peekNextRow,
                AerialSettings.plateStyle, AerialSettings.compactDockCorners, AerialSettings.showAllNames, AerialSettings.settingsIconPosition,
                AerialSettings.settingsIconVisibility, AerialSettings.focusStyle, AerialSettings.clockTextColor,
                AerialSettings.clockPosition, AerialSettings.clockSizeMode, AerialSettings.clockHours, AerialSettings.showHomeGreeting, AerialSettings.showHomeTime, AerialSettings.showHomeDate, AerialSettings.showLunarDate,
                AerialSettings.showWeather, AerialSettings.showDeviceCapacity, AerialSettings.uiSounds, AerialSettings.effects)
        }.drop(1).collect {
            recentCategory = settingsCategory
            recentPrefs.edit().putInt("recent_category", settingsCategory).apply()
        }
    }
    if (confirmReset) {
        Dialog(onDismissRequest = { confirmReset = false }) {
            Column(Modifier.width(360.dp).settingsFrost(20.dp).padding(22.dp)) {
                BasicText(stringResource(R.string.halo_reset_confirm), style = Type.Headline)
                Spacer(Modifier.height(12.dp))
                BasicText(stringResource(R.string.halo_reset_hint), style = Type.Caption.copy(color = Palette.SecondaryLabel))
                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ActionChip(stringResource(R.string.halo_reset_cancel), Glyph.ChevronLeft) { confirmReset = false }
                    ActionChip(stringResource(R.string.halo_reset_action), Glyph.Check) {
                        when (settingsCategory) {
                            0 -> { AerialSettings.chooseClockTextColor(0); AerialSettings.chooseSettingsIconPosition(1); AerialSettings.chooseSettingsIconVisibility(0) }
                            1 -> { AerialSettings.chooseOneRowHome(true); AerialSettings.choosePlateStyle(1); AerialSettings.choosePeekNextRow(false); AerialSettings.chooseHomeColumns(5); AerialSettings.chooseCompactDockCorners(false); AerialSettings.chooseShowAllNames(false) }
                            2 -> { AerialSettings.chooseClockPosition(0); AerialSettings.chooseClockSizeMode(0); AerialSettings.chooseClock24Hour(true); AerialSettings.chooseShowHomeGreeting(true); AerialSettings.chooseShowHomeTime(true); AerialSettings.chooseShowHomeDate(true); AerialSettings.chooseShowLunarDate(false); AerialSettings.chooseShowWeather(true); AerialSettings.chooseShowDeviceCapacity(true) }
                            4 -> { AerialSettings.chooseFocusStyle(FocusStyle.LiquidSlide); AerialSettings.chooseUiSounds(UiSounds.Aerial); Sounds.prepare() }
                            6 -> AerialSettings.chooseEffects(Effects.Auto)
                        }
                        confirmReset = false
                    }
                }
            }
        }
    }

    val reducedMotion = LocalReducedMotion.current
    BackHandler(enabled = visible, onBack = onDismiss)

    // The phone page listens all along (PhoneServer.listen); the address is read again each time,
    // as the TV's may have changed.
    LaunchedEffect(visible) {
        if (!visible) return@LaunchedEffect
        while (isActive) {
            val url = withContext(Dispatchers.IO) { runCatching { PhoneServer.start(context) }.getOrNull() }
            if (url != phoneUrl || url != null && qr == null) {
                phoneUrl = url
                qr = url?.let { withContext(Dispatchers.Default) { PhoneServer.qr(it).asImageBitmap() } }
            }
            delay(5_000)
        }
    }

    AnimatedVisibility(visible = visible, enter = fadeIn(tween(if (reducedMotion) 1 else 180, easing = Motion.EaseOut)), exit = fadeOut(tween(if (reducedMotion) 1 else 140, easing = Motion.EaseOut)), modifier = Modifier.fillMaxSize()) {
        // A tap above the sheet closes it, back to Control Center as Back does.
        Box(Modifier.fillMaxSize().dismissOnTap(onDismiss).background(Color.Black.copy(alpha = 0.25f)))
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(if (reducedMotion) 1 else 160, easing = Motion.EaseOut)),
            exit = fadeOut(tween(if (reducedMotion) 1 else 130, easing = Motion.EaseOut)),
        ) {
            Box(Modifier.fillMaxSize().consumeTaps().settingsFrost()) {
                Column(Modifier.fillMaxSize().padding(start = 30.dp, end = 28.dp, top = 24.dp, bottom = 24.dp)) {
                    val scope = rememberCoroutineScope()
                    val scroll = rememberScrollState()
                    if (true) {
                        Row(Modifier.fillMaxWidth().height(58.dp), verticalAlignment = Alignment.Top) {
                            BasicText(stringResource(if (wallpaperOnly) R.string.home_wallpaper_screensaver else R.string.wp_title), style = Type.LargeTitle.copy(fontWeight = FontWeight.Bold), modifier = Modifier.weight(1f))
                            if (!wallpaperOnly && recentCategory in 0..7 && recentCategory != 3) ActionChip(stringResource(R.string.halo_recent), Glyph.Calendar) {
                                settingsCategory = recentCategory
                                scope.launch { scroll.scrollTo(0) }
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth().weight(1f)) {
                val showWallpaper = wallpaperOnly
                // How Home looks, in the order you see it: the picture behind everything, how Home
                // is laid out over it, what replaces it when idle, then the weather and the PIN.
                // Settings sit in tvOS-style grouped rows; each group's note follows the focused row.
                if (wallpaperOnly) {
                    WallpaperCategorySidebar(wallpaperCategory) { index ->
                        wallpaperCategory = index
                        scope.launch { scroll.scrollTo(0) }
                    }
                    Spacer(Modifier.width(12.dp))
                } else {
                    SettingsSidebar(settingsCategory, recentCategory, onRecent = {
                        if (recentCategory in 0..7 && recentCategory != 3) { settingsCategory = recentCategory; scope.launch { scroll.scrollTo(0) } }
                    }) { index ->
                        if (settingsCategory != index) {
                            settingsCategory = index
                            scope.launch { scroll.scrollTo(0) }
                        }
                    }
                    Spacer(Modifier.width(18.dp))
                }
                Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(scroll).latestBringIntoView(scroll)) {
                    if (showWallpaper) {
                        if (wallpaperCategory != 2) {
                            WallpaperRows(videosOnly = wallpaperCategory == 1, sourcesOnly = wallpaperCategory == 3, initialFocus = wallpaperOnly)
                            if (wallpaperCategory == 0 && Wallpapers.selectedId == Wallpapers.ROTATE_PHOTOS) Group { note ->
                                Choices(
                                    R.string.st_photo_every, null, note,
                                    AerialSettings.PHOTO_MINUTES.map { minutes ->
                                        minutes to when {
                                            minutes == AerialSettings.DAY_MINUTES -> stringResource(R.string.st_photo_every_day)
                                            minutes >= 60 -> stringResource(R.string.kids_h, minutes / 60)
                                            else -> stringResource(R.string.kids_m, minutes)
                                        }
                                    },
                                    AerialSettings.photoMinutes, { AerialSettings.choosePhotoMinutes(it); Wallpapers.rotateIfDue() }, first = true,
                                )
                            }
                            if (wallpaperCategory == 1 && Wallpapers.moves()) Group { note ->
                                Choices(R.string.st_play, stringResource(R.string.st_play_hint), note,
                                    listOf(false to R.string.st_play_still, true to R.string.st_play_always),
                                    AerialSettings.wallpaperAlwaysMoves, AerialSettings::chooseWallpaperAlwaysMoves, first = true)
                                if (Wallpapers.isVideo()) {
                                    Choices(R.string.video_sound, stringResource(R.string.video_sound_hint), note,
                                        listOf(true to R.string.video_muted, false to R.string.video_unmuted),
                                        AerialSettings.videoWallpaperMuted, AerialSettings::chooseVideoWallpaperMuted)
                                    Choices(R.string.video_fit, stringResource(R.string.video_fit_hint), note,
                                        listOf(false to R.string.video_fill, true to R.string.video_contain),
                                        AerialSettings.videoWallpaperFit, AerialSettings::chooseVideoWallpaperFit)
                                }
                            }
                        }
                        if (wallpaperCategory != 2) Spacer(Modifier.height(24.dp))
                    } else {
                    if (settingsCategory == 6) {
                    Group { note ->
                        Choices(R.string.st_effects, stringResource(R.string.st_effects_hint), note,
                            listOf(Effects.Lite to R.string.st_effects_lite, Effects.Auto to R.string.st_effects_auto, Effects.Full to R.string.st_effects_full),
                            AerialSettings.effects, AerialSettings::chooseEffects, first = true)
                        Choices(R.string.halo_auto_clean, null, note,
                            listOf(false to R.string.st_off, true to R.string.st_on),
                            AerialSettings.autoCleanIdle, AerialSettings::chooseAutoCleanIdle)
                        BasicText(stringResource(R.string.halo_auto_clean_note), style = Type.Caption.copy(color = Palette.SecondaryLabel), modifier = Modifier.padding(12.dp))
                    }
                    }
                    if (settingsCategory == 0 || settingsCategory == 1) {
                    // What is on Home and where; then the clock; then how the remote feels.
                    Group { note ->
                        if (settingsCategory == 1) Choices(
                            R.string.st_layout, stringResource(R.string.st_home_layout_hint), note,
                            listOf(true to R.string.st_home_one_row, false to R.string.st_home_standard),
                            AerialSettings.oneRowHome, AerialSettings::chooseOneRowHome, first = true,
                        )
                        if (settingsCategory == 1) {
                        // Two rows show the second row whole; only one row has a peek to hide.
                        if (AerialSettings.oneRowHome) Choices(
                            R.string.st_peek, stringResource(R.string.st_peek_hint), note,
                            listOf(true to R.string.st_peek_show, false to R.string.st_peek_hide),
                            AerialSettings.peekNextRow, AerialSettings::choosePeekNextRow,
                        )
                        Choices(
                            R.string.st_columns, stringResource(R.string.st_columns_hint), note,
                            AerialSettings.HOME_COLUMNS.map { it to it.toString() },
                            AerialSettings.homeColumns, AerialSettings::chooseHomeColumns,
                        )
                        }
                        if (settingsCategory == 1) Choices(
                            R.string.st_plate, stringResource(R.string.st_plate_hint), note,
                            listOf(0 to R.string.st_plate_glass, 1 to R.string.plate_liquid, 2 to R.string.st_plate_none),
                            AerialSettings.plateStyle, AerialSettings::choosePlateStyle,
                        )
                        if (settingsCategory == 1) Choices(
                            R.string.halo_dock_corners, null, note,
                            listOf(false to R.string.halo_dock_corners_round, true to R.string.halo_dock_corners_compact),
                            AerialSettings.compactDockCorners, AerialSettings::chooseCompactDockCorners,
                        )
                        if (settingsCategory == 0) Choices(
                            R.string.clock_text_color, null, note,
                            listOf(0 to R.string.clock_white, 1 to R.string.clock_dark_gray, 2 to R.string.clock_black),
                            AerialSettings.clockTextColor, AerialSettings::chooseClockTextColor,
                        )
                        if (settingsCategory == 1) Choices(
                            R.string.st_names, stringResource(R.string.st_names_hint), note,
                            listOf(false to R.string.st_names_focused, true to R.string.st_names_all),
                            AerialSettings.showAllNames, AerialSettings::chooseShowAllNames,
                        )
                        if (settingsCategory == 0) Choices(
                            R.string.settings_icon_position, null, note,
                            listOf(0 to R.string.settings_icon_left, 1 to R.string.settings_icon_right, 2 to R.string.settings_icon_upper_right),
                            AerialSettings.settingsIconPosition, AerialSettings::chooseSettingsIconPosition,
                        )
                        if (settingsCategory == 0) Choices(
                            R.string.settings_icon_visibility, stringResource(R.string.settings_icon_fade_hint), note,
                            listOf(0 to R.string.settings_icon_always, 1 to R.string.settings_icon_fade, 2 to R.string.settings_icon_hidden),
                            AerialSettings.settingsIconVisibility, AerialSettings::chooseSettingsIconVisibility,
                        )
                        if (settingsCategory == 1) Choices(
                            R.string.st_home_lock, null, note,
                            listOf(false to R.string.st_off, true to R.string.st_on),
                            AerialSettings.homeLocked, onHomeLock,
                        )
                        // Apps taken off Home are kept with the rest of how Home looks.
                        if (settingsCategory == 1) SettingRow(R.string.st_hidden_apps, stringResource(R.string.st_hidden_apps_hint), note) {
                            ActionChip(
                                if (hiddenCount == 0) stringResource(R.string.st_hidden_none) else stringResource(R.string.st_hidden_count, hiddenCount),
                                Glyph.ChevronRight,
                                onHiddenApps,
                            )
                        }
                    }
                    }
                    if (settingsCategory == 2) {
                    Group { note ->
                        Choices(
                            R.string.clock_bar, null, note,
                            listOf(0 to R.string.st_clock_left, 1 to R.string.st_clock_right, 2 to R.string.clock_center, 3 to R.string.clock_status),
                            AerialSettings.clockPosition, AerialSettings::chooseClockPosition, first = true,
                        )
                        Choices(
                            R.string.clock_bar_size, null, note,
                            listOf(0 to R.string.clock_default, 1 to R.string.st_clock_large, 2 to R.string.clock_bold),
                            AerialSettings.clockSizeMode, AerialSettings::chooseClockSizeMode,
                        )
                        Choices(R.string.halo_desktop_clock_format, null, note,
                            listOf(ClockHours.System to R.string.st_clock_hours_system, ClockHours.H24 to R.string.halo_clock_24, ClockHours.H12 to R.string.halo_clock_12),
                            AerialSettings.clockHours, AerialSettings::chooseClockHours)
                        val switches = listOf(true to R.string.st_on, false to R.string.st_off)
                        Choices(R.string.halo_show_home_greeting, null, note, switches, AerialSettings.showHomeGreeting, AerialSettings::chooseShowHomeGreeting)
                        Choices(R.string.halo_show_home_time, null, note, switches, AerialSettings.showHomeTime, AerialSettings::chooseShowHomeTime)
                        Choices(R.string.halo_show_home_date, null, note, switches, AerialSettings.showHomeDate, AerialSettings::chooseShowHomeDate)
                        Choices(
                            R.string.show_lunar_date, null, note,
                            listOf(true to R.string.st_on, false to R.string.st_off),
                            AerialSettings.showLunarDate, AerialSettings::chooseShowLunarDate,
                        )
                        Choices(
                            R.string.wp_weather, null, note,
                            listOf(true to R.string.st_on, false to R.string.st_off),
                            AerialSettings.showWeather, AerialSettings::chooseShowWeather,
                        )
                        SettingRow(R.string.wp_weather_city, null, note) { WeatherCityButton() }
                        Choices(
                            R.string.st_device_capacity, stringResource(R.string.st_device_capacity_hint), note,
                            listOf(true to R.string.st_on, false to R.string.st_off),
                            AerialSettings.showDeviceCapacity, AerialSettings::chooseShowDeviceCapacity,
                        )
                    }
                    }
                    if (settingsCategory == 4) {
                    Group { note ->
                        Choices(
                            R.string.st_focus_style,
                            stringResource(if (AerialSettings.focusStyle == FocusStyle.HighContrast) R.string.st_focus_high_hint else R.string.st_focus_standard_hint),
                            note,
                            listOf(FocusStyle.HighContrast to R.string.st_focus_high, FocusStyle.Standard to R.string.st_focus_standard, FocusStyle.LiquidSlide to R.string.focus_liquid_slide),
                            AerialSettings.focusStyle, AerialSettings::chooseFocusStyle, first = true,
                        )
                        Choices(
                            R.string.st_sounds,
                            stringResource(if (AerialSettings.uiSounds == UiSounds.Tv) R.string.st_sounds_tv_hint else R.string.st_sounds_hint),
                            note,
                            listOf(UiSounds.Tv to R.string.st_sounds_tv, UiSounds.Aerial to R.string.st_sounds_aerial, UiSounds.Soft to R.string.sound_soft, UiSounds.Crystal to R.string.sound_crystal, UiSounds.Off to R.string.st_off),
                            AerialSettings.uiSounds, { AerialSettings.chooseUiSounds(it); Sounds.prepare() }, reserveCheck = true,
                        )

                    }
                    }

                    }
                    if (showWallpaper && wallpaperCategory == 2) {
                    Group { note ->
                        SettingRow(
                            R.string.ss_kind,
                            when {
                                Wallpapers.screensaver == ScreensaverMode.Videos -> stringResource(R.string.ss_videos_hint)
                                Wallpapers.screensaver == ScreensaverMode.System -> stringResource(R.string.ss_system_hint)
                                Wallpapers.screensaverFollows -> stringResource(R.string.ss_follow_hint)
                                AerialSettings.backStartsScreensaver -> stringResource(R.string.ss_back_hint)
                                else -> null
                            },
                            note, first = true,
                        ) { ScreensaverChoice() }
                        if (Wallpapers.screensaver == ScreensaverMode.Photos || Wallpapers.screensaver == ScreensaverMode.Videos) {
                            LocalSaverControls(Wallpapers.screensaver == ScreensaverMode.Videos, note)
                        }
                        if (Wallpapers.screensaver == ScreensaverMode.FlipClock) {
                        Choices(R.string.halo_clock_format, null, note,
                            listOf(true to R.string.halo_clock_24, false to R.string.halo_clock_12),
                            AerialSettings.flipClock24Hour, AerialSettings::chooseFlipClock24Hour)
                        Choices(R.string.halo_flip_seconds, null, note,
                            listOf(true to R.string.st_on, false to R.string.st_off),
                            AerialSettings.flipClockSeconds, AerialSettings::chooseFlipClockSeconds)
                        }
                        // Only the qualities this TV can play; a 1080p-only TV has nothing to choose.
                        val qualities = remember { AerialFormats.supported(context) }
                        if (Wallpapers.screensaver == ScreensaverMode.Aerial && qualities.size > 1) {
                            Choices(
                                R.string.ss_quality, stringResource(R.string.ss_quality_hint), note,
                                listOf(AerialQuality.Auto to R.string.ss_quality_auto, AerialQuality.Hd to R.string.ss_quality_hd) +
                                    listOf(AerialQuality.Uhd to R.string.ss_quality_uhd, AerialQuality.DolbyVision to R.string.ss_quality_dv)
                                        .filter { it.first in qualities },
                                AerialSettings.aerialQuality, AerialSettings::chooseAerialQuality,
                            )
                        }
                        // The clock screensaver is its clock, and the others show none.
                        if (Wallpapers.screensaver == ScreensaverMode.Aerial || Wallpapers.screensaver == ScreensaverMode.Photos) Choices(
                            R.string.ss_show_time, stringResource(R.string.ss_show_time_hint), note,
                            listOf(true to R.string.st_on, false to R.string.st_off),
                            AerialSettings.screensaverClock, AerialSettings::chooseScreensaverClock,
                        )
                        // The firmware's screensaver starts on the firmware's timer: Aerial's delay
                        // did nothing there, and Xiaomi's 1 minute came before the 5 chosen (issue #675).
                        if (Wallpapers.screensaver == ScreensaverMode.System) SettingRow(R.string.ss_delay, null, note) {
                            val delay = remember { SystemScreensaver.firmwareDelayMs(context) }
                            BasicText(
                                when {
                                    delay == null -> stringResource(R.string.ss_delay_system)
                                    delay == 0L -> stringResource(R.string.ss_delay_system_off)
                                    else -> stringResource(R.string.ss_delay_system_minutes, ((delay + 30_000) / 60_000).coerceAtLeast(1).toInt())
                                },
                                style = Type.Body.copy(color = Palette.SecondaryLabel),
                                modifier = Modifier.padding(end = 8.dp),
                            )
                        } else Choices(
                            R.string.ss_delay, null, note,
                            AerialSettings.screensaverChoices.map {
                                it to if (it == AerialSettings.NEVER) stringResource(R.string.ss_never) else stringResource(R.string.ss_minutes, it)
                            },
                            AerialSettings.screensaverMinutes, AerialSettings::chooseScreensaverMinutes,
                        )
                        Choices(
                            R.string.ss_back, stringResource(R.string.ss_back_choice_hint), note,
                            listOf(true to R.string.ss_back_on, false to R.string.ss_back_off),
                            AerialSettings.backStartsScreensaver, AerialSettings::chooseBackStartsScreensaver,
                        )
                    }

                    }
                    if (!wallpaperOnly && settingsCategory == 5) {
                    Spacer(Modifier.height(2.dp))
                    Group(stringResource(R.string.st_pin_hint)) { note ->
                        SettingRow(R.string.st_pin, null, note, first = true) {
                            // The last row that takes focus: bring the note under it into view too.
                            PinChoice(onPin, onFocused = { scope.launch { scroll.animateScrollTo(scroll.maxValue) } })
                        }
                        SettingRow(R.string.st_kids, stringResource(R.string.st_kids_hint), note) {
                            ActionChip(
                                when {
                                    !Restricted.enabled -> stringResource(R.string.st_off)
                                    Restricted.kind == RestrictedKind.Kids -> stringResource(R.string.kids_kind_kids)
                                    else -> stringResource(R.string.kids_kind_elders)
                                },
                                Glyph.ChevronRight,
                                onKids,
                            )
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                    }
                    if (!wallpaperOnly && settingsCategory in listOf(0, 1, 2, 4, 6)) {
                        Spacer(Modifier.height(8.dp))
                        ActionChip(stringResource(R.string.halo_reset_action), Glyph.ChevronLeft) { confirmReset = true }
                        Spacer(Modifier.height(28.dp))
                    }
                    if (!wallpaperOnly && settingsCategory == 7) {
                        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.075f)).padding(18.dp)) {
                            BasicText(stringResource(R.string.halo_notice_title), style = Type.Title)
                            Spacer(Modifier.height(12.dp))
                            BasicText(stringResource(R.string.halo_notice_body), style = Type.Body.copy(lineHeight = 22.sp))
                            Spacer(Modifier.height(14.dp))
                            BasicText(stringResource(R.string.halo_free_notice), style = Type.Body.copy(lineHeight = 22.sp))
                            Spacer(Modifier.height(14.dp))
                            BasicText(stringResource(R.string.bing_source_notice), style = Type.Body.copy(lineHeight = 22.sp))
                            Spacer(Modifier.height(10.dp))
                            BasicText(stringResource(R.string.aerial_source_about), style = Type.Body.copy(lineHeight = 22.sp))
                        }
                    }
                }
                Spacer(Modifier.width(if (wallpaperOnly) 28.dp else 18.dp))
                // Keep phone setup anchored while switching or scrolling categories.
                SettingsPreviewPanel(phoneUrl, qr, 0.dp, 0.dp, compact = wallpaperOnly)
                    }
                }
            }
        }
    }
}


/** Wallpaper navigation stays fixed while the selected category scrolls independently. */
@Composable
private fun WallpaperCategorySidebar(selected: Int, onSelect: (Int) -> Unit) {
    val categories = listOf(R.string.category_static to Glyph.Photo, R.string.category_dynamic to Glyph.Play, R.string.category_saver to Glyph.Screen) +
        if (Wallpapers.items.any { it.kind == WallpaperKind.Source }) listOf(R.string.category_sources to Glyph.Input) else emptyList()
    Column(Modifier.width(116.dp).fillMaxHeight().clip(RoundedCornerShape(16.dp))
        .background(Color.White.copy(alpha = 0.06f)).padding(6.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        categories.forEachIndexed { index, (title, icon) ->
            Pressable(onClick = { onSelect(index) }, focusedScale = 1f,
                modifier = Modifier.fillMaxWidth().onFocusChanged { if (it.isFocused) onSelect(index) }) { state ->
                val highlighted = selected == index || state.focused
                val foreground = if (highlighted) Palette.OnFocus else Palette.Label
                Row(Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(12.dp))
                    .background(if (highlighted) Palette.Focus else Color.Transparent).padding(horizontal = 7.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    GlyphIcon(icon, foreground, 16.dp)
                    BasicText(stringResource(title), style = Type.Caption.copy(color = foreground), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun WallpaperSourceCard(title: String, subtitle: String, imageId: String?, online: Boolean, onClick: () -> Unit) {
    val thumbnail by produceState<ImageBitmap?>(initialValue = null, imageId) {
        value = imageId?.let { id -> withContext(Dispatchers.IO) { Wallpapers.thumbnail(id)?.asImageBitmap() } }
    }
    Pressable(onClick = onClick, focusedScale = 1.015f, modifier = Modifier.fillMaxWidth()) { state ->
        val shape = RoundedCornerShape(16.dp)
        Box(Modifier.fillMaxWidth().height(114.dp).clip(shape)
            .background(if (online) Color(0xFF344E80) else Color(0xFF315B54))
            .then(if (state.focused) Modifier.border(2.dp, Color.White, shape) else Modifier.border(1.dp, Color.White.copy(alpha = 0.18f), shape))) {
            thumbnail?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
            Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color(0xD9102039), Color(0x40102039)))))
            Column(Modifier.align(Alignment.CenterStart).padding(start = 22.dp, end = 52.dp)) {
                BasicText(title, style = Type.Title.copy(fontWeight = FontWeight.SemiBold, color = Color.White), maxLines = 1)
                Spacer(Modifier.height(4.dp))
                BasicText(subtitle, style = Type.Caption.copy(color = Color.White.copy(alpha = 0.9f)), maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Box(Modifier.align(Alignment.CenterEnd).padding(end = 16.dp).size(30.dp).clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.25f)), contentAlignment = Alignment.Center) {
                GlyphIcon(Glyph.ChevronRight, Color.White, 18.dp)
            }
        }
    }
}

/** Categories stay visible while the selected settings scroll in the middle column. */
@Composable
private fun SettingsSidebar(selected: Int, recent: Int, onRecent: () -> Unit, onSelect: (Int) -> Unit) {
    val labels = listOf(R.string.halo_cat_appearance, R.string.halo_cat_apps, R.string.settings_category_clock,
        R.string.home_wallpaper_screensaver, R.string.halo_cat_remote, R.string.halo_cat_family,
        R.string.halo_cat_performance, R.string.halo_cat_about)
    val glyphs = listOf(Glyph.Home, Glyph.Folder, Glyph.Calendar, Glyph.Photo, Glyph.Switches, Glyph.Kids, Glyph.Performance, Glyph.Info)
    Column(Modifier.width(200.dp).fillMaxHeight()
        .clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.06f)).padding(8.dp)) {
        labels.forEachIndexed { index, title ->
            if (index == 3) return@forEachIndexed
            Pressable(
                onClick = { onSelect(index) }, focusedScale = 1f,
                modifier = Modifier.fillMaxWidth().onFocusChanged { if (it.isFocused) onSelect(index) },
            ) { state ->
                val bg = when { state.focused -> Palette.Focus; selected == index -> Color.White.copy(alpha = 0.18f); else -> Color.Transparent }
                val fg = if (state.focused) Palette.OnFocus else Palette.Label
                Row(Modifier.fillMaxWidth().height(45.dp).clip(RoundedCornerShape(12.dp)).background(bg).padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlyphIcon(glyphs[index], fg, 18.dp)
                    BasicText(stringResource(title), style = Type.Caption.copy(color = fg), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.height(5.dp))
        }

    }
}

/** Phone setup stays available without rendering a second desktop and wallpaper. */
@Composable
private fun SettingsPreviewPanel(url: String?, qr: ImageBitmap?, topInset: androidx.compose.ui.unit.Dp,
    bottomInset: androidx.compose.ui.unit.Dp, compact: Boolean) {
    Box(Modifier.width(if (compact) 196.dp else 282.dp).fillMaxHeight().padding(top = topInset, bottom = bottomInset),
        contentAlignment = Alignment.Center) {
        Column(Modifier.fillMaxWidth().fillMaxHeight().clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.07f)).padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            BasicText(stringResource(R.string.wp_phone_title), style = Type.Headline.copy(fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.Center), modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(18.dp))
            if (!compact) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)) {
                    Box(Modifier.size(108.dp).clip(RoundedCornerShape(10.dp)).background(Color.White).padding(7.dp), contentAlignment = Alignment.Center) {
                        if (qr != null) Image(qr, null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit, filterQuality = FilterQuality.None)
                        else GlyphIcon(Glyph.Offline, Color.DarkGray, 28.dp)
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        val features = listOf(stringResource(R.string.halo_phone_scan)) + stringResource(R.string.halo_phone_features).split('\n')
                        features.forEach { feature ->
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Box(Modifier.size(13.dp).clip(CircleShape).background(Palette.Label), contentAlignment = Alignment.Center) {
                                    GlyphIcon(Glyph.Check, Color(0xFF1B293C), 9.dp)
                                }
                                BasicText(feature, style = Type.Caption.copy(color = Palette.Label, fontSize = 10.sp), maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            } else {
                Box(Modifier.size(88.dp).clip(RoundedCornerShape(10.dp)).background(Color.White).padding(7.dp), contentAlignment = Alignment.Center) {
                    if (qr != null) Image(qr, null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit, filterQuality = FilterQuality.None)
                    else GlyphIcon(Glyph.Offline, Color.DarkGray, 28.dp)
                }
                Spacer(Modifier.height(12.dp))
                BasicText(stringResource(R.string.halo_phone_features), style = Type.Caption.copy(color = Palette.Label, lineHeight = 19.sp))
            }
            Spacer(Modifier.height(18.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.2f)))
            Spacer(Modifier.height(14.dp))
            BasicText(stringResource(R.string.halo_phone_tips), style = Type.Headline.copy(fontWeight = FontWeight.SemiBold, textAlign = androidx.compose.ui.text.style.TextAlign.Center), modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            if (url == null) {
                BasicText(stringResource(R.string.wp_phone_offline), style = Type.Caption.copy(color = Palette.Label, fontSize = 11.sp, lineHeight = 17.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center), modifier = Modifier.fillMaxWidth())
            } else {
                listOf(Glyph.Wifi to R.string.halo_phone_same_network, Glyph.Globe to R.string.halo_phone_browser).forEach { (glyph, title) ->
                    Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
                        GlyphIcon(glyph, Palette.Label, 16.dp)
                        BasicText(stringResource(title), style = Type.Caption.copy(color = Palette.Label, fontSize = 11.sp, lineHeight = 15.sp), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            if (url != null) {
                Spacer(Modifier.height(12.dp))
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.12f)).padding(10.dp), contentAlignment = Alignment.Center) {
                    BasicText(url.removePrefix("http://"), style = Type.Caption.copy(color = Palette.Label, fontSize = 11.sp),
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/**
 * Every wallpaper in one row, in the order most people want them: the moving aerial, Bing's photo
 * of the day, the owner's own photos, then a few of the built-in pictures and "Follow App". Past
 * daily photos and the rest of the built-ins are left out unless one of them is the current
 * choice. The focused card's note is under the row, as under a group of settings.
 */
@Composable
private fun WallpaperRows(videosOnly: Boolean, sourcesOnly: Boolean = false, initialFocus: Boolean = true) {
    val items = Wallpapers.items
    val selected = Wallpapers.selectedId
    var preview by remember { mutableStateOf<Wallpaper?>(null) }
    var aerialLibrary by remember { mutableStateOf(false) }
    var bingLibrary by remember { mutableStateOf(false) }
    var showLocal by remember(videosOnly, sourcesOnly) { mutableStateOf(false) }
    val first = remember { FocusRequester() }
    val dailyTitle = stringResource(R.string.wp_daily_latest)
    val rotateTitle = stringResource(R.string.wp_photos_rotate)
    val videosTitle = stringResource(R.string.wp_videos_rotate)
    val list = remember(items, selected, dailyTitle, rotateTitle, videosTitle, videosOnly, sourcesOnly) {
        buildList {
if (sourcesOnly) {
                addAll(items.filter { it.kind == WallpaperKind.Source })
            } else if (videosOnly) {
                val videos = items.filter { it.kind == WallpaperKind.Video && it.id != Wallpapers.VIDEOS }
                if (videos.size > 1 || selected == Wallpapers.VIDEOS) add(Wallpaper(Wallpapers.VIDEOS, WallpaperKind.Video, videosTitle))
                addAll(videos)
            } else {
                val photos = items.filter { it.kind == WallpaperKind.Photo && it.id != Wallpapers.BING_ROTATION }
                if (photos.size > 1) add(Wallpaper(Wallpapers.ROTATE_PHOTOS, WallpaperKind.Photo, rotateTitle))
                addAll(photos)
                addAll(items.filter { it.kind == WallpaperKind.Builtin && it.id == selected })
                addAll(items.filter { it.kind == WallpaperKind.Auto })
            }
        }
    }
    val takeInitialFocus = initialFocus && sourcesOnly && list.isNotEmpty()
    LaunchedEffect(takeInitialFocus, list.firstOrNull()?.id) {
        if (takeInitialFocus && list.isNotEmpty()) {
            withFrameNanos { }
            runCatching { first.requestFocus() }
        }
    }
    var note by remember { mutableStateOf<String?>(null) }
    // Bound the grid inside the settings scroll container so off-screen thumbnails stay lazy.
    // Padding leaves room for the 8% focus scale on every edge.
    val gridRows = ((list.size + 2) / 3).coerceIn(1, 3)
    if (!sourcesOnly) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth().padding(3.dp)) {
            WallpaperSourceCard(
                title = stringResource(if (videosOnly) R.string.wallpaper_online_video else R.string.halo_online_wallpaper_entry),
                subtitle = stringResource(if (videosOnly) R.string.halo_source_online_video_note else R.string.halo_source_bing_note),
                imageId = if (videosOnly) Wallpapers.LIVE else Wallpapers.LATEST_DAILY,
                online = true,
                onClick = { if (videosOnly) aerialLibrary = true else bingLibrary = true },
            )
            WallpaperSourceCard(
                title = stringResource(if (videosOnly) R.string.wallpaper_local_video else R.string.wallpaper_local_photo),
                subtitle = stringResource(if (videosOnly) R.string.halo_source_local_video_note else R.string.halo_source_local_photo_note),
                imageId = list.firstOrNull()?.id,
                online = false,
                onClick = { showLocal = !showLocal },
            )
        }
        Spacer(Modifier.height(10.dp))
    }
    if (showLocal || sourcesOnly) {
    if (list.isEmpty()) {
        BasicText(stringResource(if (videosOnly) R.string.wallpaper_empty_video else R.string.wallpaper_empty_photo),
            style = Type.Caption.copy(color = Palette.SecondaryLabel))
    }

    if (list.isNotEmpty()) LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxWidth().height((gridRows * 128 + 16).dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(start = 10.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
    ) {
        items(list, key = { it.id }) { wp ->
            val hint = wallpaperNote(wp)
            WallpaperCard(wp, if (takeInitialFocus && wp === list.first()) first else null,
                onFocused = { note = hint }, onClick = { if (wp.kind == WallpaperKind.Live) aerialLibrary = true else Wallpapers.select(wp.id) },
                onLongClick = { if (wp.kind == WallpaperKind.Live) aerialLibrary = true else preview = wp })
        }
    }
    }
    if (bingLibrary) BingLibrary { bingLibrary = false }
    preview?.let { WallpaperPreview(it) { preview = null } }
    if (videosOnly && !sourcesOnly) {
        if (aerialLibrary) AerialLibrary { aerialLibrary = false }
    }
    Box(Modifier.height(42.dp).padding(start = 4.dp, top = 8.dp)) {
        BasicText(listOfNotNull(note, stringResource(R.string.wallpaper_hold_manage)).joinToString("  ·  "),
            style = Type.Caption.copy(color = Palette.TertiaryLabel), maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/** Browse the existing Apple aerial catalogue, shared with AerialViews, without copying its code. */
@Composable
private fun AerialLibrary(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val films = remember { Aerials.all(context) }
    var chosen by remember { mutableStateOf<app.orchard.tv.wallpaper.Aerial?>(null) }
    val gridState = rememberLazyGridState()
    val applyFocus = remember { FocusRequester() }
    val cardFocus = remember(films) { films.associate { it.id to FocusRequester() } }
    var lastFocusedFilmId by remember { mutableStateOf<String?>(null) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        GalleryImmersive()
        Column(Modifier.fillMaxSize().settingsFrost().padding(horizontal = 28.dp, vertical = 22.dp)) {
            LibraryHeader(R.string.aerial_library, R.string.aerial_source_notice, onDismiss)
            Spacer(Modifier.height(12.dp))
            RotationChoices(video = true, category = "")
            Spacer(Modifier.height(12.dp))
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                LazyVerticalGrid(columns = GridCells.Fixed(3), state = gridState,
                    modifier = Modifier.weight(1f).fillMaxHeight(), horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(5.dp)) {
                    itemsIndexed(films, key = { _, film -> film.id }) { index, film ->
                        Pressable(onClick = { chosen = film }, focusRequester = cardFocus.getValue(film.id), focusedScale = 1.02f,
                            onKey = { event ->
                                if (event.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_DPAD_RIGHT && chosen != null && galleryRowEndsAt(index, films.size, 3)) {
                                    if (event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount == 0) runCatching { applyFocus.requestFocus() }; true
                                } else false
                            }) { state ->
                            LaunchedEffect(state.focused) { if (state.focused) lastFocusedFilmId = film.id }
                            val thumbnail = aerialThumbnail(film.preview, gridState, film.id)
                            LibraryThumbnail(thumbnail, state.focused, chosen?.id == film.id, video = true)
                        }
                    }
                }
                Column(Modifier.width(320.dp).fillMaxHeight().clip(RoundedCornerShape(18.dp)).background(Color(0xFF202E47)).verticalScroll(rememberScrollState()).padding(14.dp)) {
                    Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(12.dp)).background(Color.Black), contentAlignment = Alignment.Center) {
                        chosen?.let { film -> key(film.id) {
                            AndroidView(factory = { ctx -> android.widget.VideoView(ctx).apply {
                                isFocusable = false; isFocusableInTouchMode = false; setVideoPath(film.url)
                                setOnPreparedListener { it.isLooping = true; it.setVolume(0f, 0f); start() }; clearFocus()
                            } }, modifier = Modifier.fillMaxSize(), onRelease = { it.stopPlayback() })
                        } } ?: BasicText(stringResource(R.string.aerial_library_choose), style = Type.Caption)
                    }
                    Spacer(Modifier.height(12.dp))
                    val film = chosen
                    if (film != null) {
                        BasicText(film.captions.firstOrNull()?.second ?: film.label, style = Type.Headline, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(5.dp))
                        BasicText(stringResource(R.string.aerial_creator_unknown), style = Type.Caption.copy(color = Palette.SecondaryLabel))
                        Spacer(Modifier.height(12.dp))
                        LibraryAction(stringResource(R.string.wallpaper_apply), applyFocus, onKey = { event ->
                            if (event.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_DPAD_LEFT) {
                                if (event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount == 0)
                                    cardFocus[lastFocusedFilmId ?: film.id]?.let { runCatching { it.requestFocus() } }; true
                            } else false
                        }) { Wallpapers.chooseAerial(film.id); OnlineRotation.markVideoApplied(); onDismiss() }

                    }
                }
            }
        }
    }
}

@Composable
private fun GalleryImmersive() {
    val window = (LocalView.current.parent as? DialogWindowProvider)?.window
    SideEffect {
        if (window != null) {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            WindowInsetsControllerCompat(window, window.decorView).hide(WindowInsetsCompat.Type.systemBars())
        }
    }
}

@Composable
private fun RotationChoices(video: Boolean, category: String) {
    val context = LocalContext.current
    val minutes = if (video) OnlineRotation.videoMinutes else OnlineRotation.bingMinutes
    fun set(value: Int) { if (video) OnlineRotation.chooseVideoMinutes(value) else OnlineRotation.chooseBingMinutes(value, category) }
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        Chip(stringResource(R.string.rotation_off), minutes == 0, compact = true) { set(0) }
        Chip(stringResource(R.string.rotation_daily), minutes == 1440, compact = true) { set(1440) }
        Chip(stringResource(R.string.rotation_home_return), minutes == -1, compact = true) { set(-1) }
        val custom = minutes > 0 && minutes != 1440
        Chip(if (custom) stringResource(R.string.rotation_custom_value, minutes) else stringResource(R.string.rotation_custom), custom, compact = true) {
            customKidsTime(context, context.getString(R.string.rotation_custom_minutes), minutes.takeIf { it > 0 } ?: 60, false) { set(it) }
        }
    }
}

@Composable
private fun LibraryCategory(label: String, chosen: Boolean, onClick: () -> Unit) {
    Pressable(onClick = onClick, modifier = Modifier.fillMaxWidth(), focusedScale = 1.02f) { state ->
        Box(Modifier.fillMaxWidth().height(38.dp).clip(RoundedCornerShape(10.dp))
            .background(if (state.focused) Palette.Focus else if (chosen) Color.White.copy(alpha = 0.16f) else Color.Transparent)
            .padding(horizontal = 12.dp), contentAlignment = Alignment.CenterStart) {
            BasicText(label, style = Type.Body.copy(color = if (state.focused) Palette.OnFocus else Palette.Label), maxLines = 1)
        }
    }
}

@Composable
private fun LibraryHeader(title: Int, notice: Int, onDismiss: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            BasicText(stringResource(title), style = Type.LargeTitle)
            Spacer(Modifier.height(3.dp))
            BasicText(stringResource(notice), style = Type.Caption.copy(color = Palette.SecondaryLabel), maxLines = 2)
        }
        Chip(stringResource(R.string.donate_close), on = false, onClick = onDismiss)
    }
}

@Composable
private fun LibraryThumbnail(image: ImageBitmap?, focused: Boolean, selected: Boolean, video: Boolean = false) {
    val shape = RoundedCornerShape(12.dp)
    Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(shape).background(Color(0xFF2C4058))
        .border(if (focused) 2.dp else 1.dp, if (focused) Color.White else Color.White.copy(alpha = 0.12f), shape), contentAlignment = Alignment.Center) {
        image?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
        if (focused) Box(Modifier.fillMaxSize().border(2.dp, Color.White, shape))
        if (video) Box(Modifier.align(Alignment.BottomEnd).padding(7.dp).size(25.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.55f)), contentAlignment = Alignment.Center) {
            GlyphIcon(Glyph.Play, Color.White, 13.dp)
        }
        if (selected) Box(Modifier.align(Alignment.TopEnd).padding(7.dp).size(22.dp).clip(CircleShape).background(Palette.Focus), contentAlignment = Alignment.Center) {
            GlyphIcon(Glyph.Check, Palette.OnFocus, 13.dp)
        }
    }
}

@Composable
private fun LibraryAction(label: String, focusRequester: FocusRequester?, enabled: Boolean = true,
    onKey: ((androidx.compose.ui.input.key.KeyEvent) -> Boolean)? = null, onClick: () -> Unit) {
    Pressable(onClick = onClick, modifier = Modifier.fillMaxWidth(), enabled = enabled, focusRequester = focusRequester, onKey = onKey, focusedScale = 1.02f) { state ->
        Box(Modifier.fillMaxWidth().height(38.dp).clip(RoundedCornerShape(12.dp))
            .background(if (state.focused) Palette.Focus else Color.White.copy(alpha = if (enabled) 0.13f else 0.05f)), contentAlignment = Alignment.Center) {
            BasicText(label, style = Type.Body.copy(color = if (state.focused) Palette.OnFocus else Palette.Label), maxLines = 1)
        }
    }
}

private val aerialThumbSlots = Semaphore(3)
private val thumbCleanup = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.IO)

/** A visible card owns its request and bitmap; leaving the grid cancels and releases both. */
@Composable
private fun aerialThumbnail(url: String, grid: LazyGridState, key: String, fallback: (() -> ByteArray)? = null): ImageBitmap? {
    val context = LocalContext.current.applicationContext
    val visible by remember(grid, key) { derivedStateOf { grid.layoutInfo.visibleItemsInfo.any { it.key == key } } }
    var image by remember(url) { mutableStateOf<ImageBitmap?>(null) }
    val scope = rememberCoroutineScope()
    DisposableEffect(url, visible) {
        val lock = Any()
        var connection: HttpURLConnection? = null
        var disposed = false
        val allowed = url.startsWith("https://sylvan.apple.com/") || url.startsWith("https://ygn-bing-wallpaper.pages.dev/thumbnails/")
        if (!visible || !allowed) image = null
        val job = if (visible && allowed) scope.launch {
            app.orchard.tv.system.CompatibleHttp.init(context)
            var decoded: android.graphics.Bitmap? = null
            // A transient timeout must not leave a visible tile blank for the whole visit.
            repeat(2) { attempt ->
                if (decoded == null) {
                    if (attempt > 0) kotlinx.coroutines.delay(700)
                    decoded = withContext(Dispatchers.IO) { aerialThumbSlots.withPermit {
                        runCatching {
                            val c = app.orchard.tv.system.CompatibleHttp.open(url)
                            synchronized(lock) { if (disposed) { c.disconnect(); return@runCatching null }; connection = c }
                            try {
                                c.connectTimeout = 8_000; c.readTimeout = 12_000
                                c.setRequestProperty("User-Agent", "HaloTV/3.0")
                                check(c.responseCode == 200)
                                val bytes = c.inputStream.use { input ->
                                    val out = java.io.ByteArrayOutputStream(); val buffer = ByteArray(16_384)
                                    while (true) {
                                        kotlinx.coroutines.currentCoroutineContext().ensureActive()
                                        val n = input.read(buffer); if (n < 0) break
                                        require(out.size() + n <= 8_000_000); out.write(buffer, 0, n)
                                    }
                                    out.toByteArray()
                                }
                                decodeLibraryThumbnail(bytes)
                            } finally { c.disconnect(); synchronized(lock) { connection = null } }
                        }.getOrNull()
                    } }
                    kotlinx.coroutines.currentCoroutineContext().ensureActive()
                }
            }
            if (decoded == null && fallback != null) decoded = withContext(Dispatchers.IO) {
                aerialThumbSlots.withPermit { runCatching { decodeLibraryThumbnail(fallback()) }.getOrNull() }
            }
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            image = decoded?.asImageBitmap()
        } else null
        onDispose {
            val active = synchronized(lock) { disposed = true; connection.also { connection = null } }
            job?.cancel()
            if (active != null) thumbCleanup.launch { active.disconnect() }
            image = null
        }
    }
    return image
}

internal fun decodeLibraryThumbnail(bytes: ByteArray): android.graphics.Bitmap? {
    if (bytes.size < 12) return null
    // A captive portal may answer HTTP 200 with HTML rather than an image.
    val jpeg = bytes[0] == 0xff.toByte() && bytes[1] == 0xd8.toByte() && bytes[2] == 0xff.toByte()
    val png = bytes[0] == 0x89.toByte() && String(bytes, 1, 3, Charsets.US_ASCII) == "PNG"
    val webp = String(bytes, 0, 4, Charsets.US_ASCII) == "RIFF" && String(bytes, 8, 4, Charsets.US_ASCII) == "WEBP"
    if (!jpeg && !png && !webp) return null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (bounds.outWidth / sample > 640 || bounds.outHeight / sample > 400) sample *= 2
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply {
        inSampleSize = sample; inPreferredConfig = android.graphics.Bitmap.Config.RGB_565
    })
}

@Composable
private fun wallpaperNote(wp: Wallpaper): String? = when {
    wp.kind == WallpaperKind.Live -> stringResource(R.string.wp_live)
    wp.id == Wallpapers.VIDEOS -> stringResource(R.string.wp_videos_note)
    wp.kind == WallpaperKind.Video -> stringResource(R.string.wp_video_note)
    wp.kind == WallpaperKind.Source -> stringResource(R.string.wp_source_note)
    wp.id == Wallpapers.LATEST_DAILY -> stringResource(R.string.wp_daily_note)
    wp.id == Wallpapers.ROTATE_PHOTOS -> stringResource(R.string.wp_photos_rotate_note)
    wp.kind == WallpaperKind.Photo -> Wallpapers.photoCredit(wp.id) ?: stringResource(R.string.wp_photo_note)
    wp.kind == WallpaperKind.Auto -> stringResource(R.string.wp_auto_note)
    wp.kind == WallpaperKind.Daily -> wp.caption
    else -> null
}

@Composable
private fun WallpaperCard(wp: Wallpaper, focusRequester: FocusRequester?, onFocused: () -> Unit, onClick: () -> Unit, onLongClick: () -> Unit) {
    val selected = Wallpapers.selectedId == wp.id
    val previewVersion = if (wp.kind == WallpaperKind.Live || wp.kind == WallpaperKind.Video) Wallpapers.pictureVersion else 0
    val thumb by produceState<ImageBitmap?>(null, wp.id, Wallpapers.items, previewVersion) {
        value = withContext(Dispatchers.Default) { Wallpapers.thumbnail(wp.id)?.asImageBitmap() }
    }
    Pressable(onClick = onClick, onLongClick = onLongClick, focusRequester = focusRequester, focusedScale = 1.06f) { state ->
        LaunchedEffect(state.focused) { if (state.focused) onFocused() }
        Column(Modifier.width(168.dp)) {
            Box(
                Modifier
                    .size(168.dp, 94.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Palette.Fill)
                    .border(
                        if (state.focused || selected) 3.dp else 1.dp,
                        when {
                            state.focused -> Palette.Focus
                            selected -> Palette.Focus.copy(alpha = 0.55f)
                            else -> Palette.Hairline
                        },
                        RoundedCornerShape(12.dp),
                    ),
            ) {
                thumb?.let {
                    Canvas(Modifier.fillMaxSize()) {
                        drawImage(it, dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()), filterQuality = FilterQuality.Medium)
                    }
                }
                if (selected) {
                    Box(
                        Modifier.align(Alignment.TopEnd).padding(7.dp).size(22.dp).clip(CircleShape).background(Palette.Focus),
                        contentAlignment = Alignment.Center,
                    ) { GlyphIcon(Glyph.Check, Palette.OnFocus, 13.dp) }
                }
                // The aerial wallpaper moves; its card shows the last frame it stopped on.
                if (wp.kind == WallpaperKind.Live || wp.kind == WallpaperKind.Video) {
                    GlyphIcon(Glyph.Play, Color.White, 22.dp, Modifier.align(Alignment.BottomStart).padding(7.dp))
                }
                // An input's picture cannot be copied for a card: its mark over the dark picture.
                if (wp.kind == WallpaperKind.Source) {
                    GlyphIcon(Glyph.Hdmi, Color.White, 34.dp, Modifier.align(Alignment.Center))
                }
            }
            Spacer(Modifier.height(6.dp))
            BasicText(
                (if (wp.kind == WallpaperKind.Live) stringResource(R.string.aerial_library) else wp.title) + if (selected) "  ·  " + stringResource(R.string.wallpaper_current) else "", maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = Type.Caption.copy(color = if (state.focused) Palette.Label else Palette.SecondaryLabel),
            )
        }
    }
}

@Composable
private fun WallpaperPreview(wp: Wallpaper, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val selected = Wallpapers.selectedId == wp.id
    val deletable = wp.kind == WallpaperKind.Photo && wp.id.startsWith("photo:") ||
        wp.kind == WallpaperKind.Video && wp.id.startsWith(Wallpapers.VIDEO_PREFIX)
    val picture by produceState<ImageBitmap?>(null, wp.id) {
        value = withContext(Dispatchers.IO) { Wallpapers.previewPicture(wp.id)?.asImageBitmap() }
    }
    val video by produceState<String?>(null, wp.id) {
        if (wp.id.startsWith(Wallpapers.VIDEO_PREFIX))
            value = withContext(Dispatchers.IO) { Wallpapers.videoFilms(wp.id).firstOrNull()?.file?.absolutePath }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.width(650.dp).settingsFrost(24.dp).padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            BasicText(wp.title + if (selected) "  ·  " + stringResource(R.string.wallpaper_current) else "", style = Type.Headline)
            Wallpapers.photoCredit(wp.id)?.let { credit ->
                BasicText(credit, style = Type.Caption.copy(color = Palette.SecondaryLabel), maxLines = 4, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(12.dp))
            Box(Modifier.width(560.dp).height(260.dp).clip(RoundedCornerShape(12.dp)).background(Color.Black), contentAlignment = Alignment.Center) {
                if (video != null) {
                    AndroidView(factory = { ctx -> android.widget.VideoView(ctx).apply {
                        setVideoPath(video)
                        setOnPreparedListener { it.isLooping = true; it.setVolume(0f, 0f); start() }
                    } }, modifier = Modifier.fillMaxSize(), onRelease = { it.stopPlayback() })
                } else picture?.let { Image(it, contentDescription = wp.title, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit) }
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Chip(stringResource(R.string.wallpaper_apply), on = selected) { Wallpapers.select(wp.id); onDismiss() }
                if (deletable) Chip(stringResource(R.string.wallpaper_delete), on = false) {
                    android.app.AlertDialog.Builder(context).setTitle(R.string.wallpaper_delete_confirm)
                        .setMessage(wp.title).setNegativeButton(android.R.string.cancel, null)
                        .setPositiveButton(R.string.wallpaper_delete) { _, _ ->
                            if (wp.kind == WallpaperKind.Photo) Wallpapers.deletePhoto(wp.id)
                            else Wallpapers.deleteVideo(wp.id.removePrefix(Wallpapers.VIDEO_PREFIX))
                            onDismiss()
                        }.show().applySettingsFrost()
                }
                Chip(stringResource(R.string.donate_close), on = false, onClick = onDismiss)
            }
        }
    }
}

@Composable
private fun SectionTitle(title: Int) {
    BasicText(stringResource(title), style = Type.Headline, modifier = Modifier.padding(start = 4.dp))
    Spacer(Modifier.height(10.dp))
}

/** Optional support for the Halo TV derivative. Payment is entirely outside the app. */
@Composable
internal fun DonationDialog(onDismiss: () -> Unit) {
    val firstContactFocus = remember { FocusRequester() }
    var preview by remember { mutableStateOf<Pair<Int, String>?>(null) }
    preview?.let { (id, title) ->
        Dialog(onDismissRequest = { preview = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Box(Modifier.fillMaxSize().dismissOnTap { preview = null }, contentAlignment = Alignment.Center) {
                val context = LocalContext.current
                val bitmap = remember(id) { BitmapFactory.decodeResource(context.resources, id).asImageBitmap() }
                Column(Modifier.width(340.dp).consumeTaps().liquidGlass(18.dp, translucent = true).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    BasicText(title, style = Type.Headline)
                    Image(bitmap, title, Modifier.size(285.dp).padding(top = 8.dp).background(Color.White), contentScale = ContentScale.Fit, filterQuality = FilterQuality.High)
                }
            }
        }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        GalleryImmersive()
        Row(Modifier.fillMaxSize().liquidGlass(0.dp, translucent = true)) {
            Column(Modifier.width(275.dp).fillMaxHeight().background(Color(0x33101B2A)).padding(38.dp)) {
                Image(painterResource(R.drawable.onboarding_null_avatar), null, Modifier.size(52.dp).clip(CircleShape), contentScale = ContentScale.Crop)
                Spacer(Modifier.height(30.dp))
                BasicText("Null_", style = Type.LargeTitle.copy(color = Color.White))
                Spacer(Modifier.height(22.dp))
                BasicText(stringResource(R.string.donate_description), style = Type.Body.copy(color = Color.White))
                Spacer(Modifier.height(12.dp))
                BasicText(stringResource(R.string.donate_hint), style = Type.Caption.copy(color = Color(0xFFBFD1E4)))
                Spacer(Modifier.weight(1f))
                BasicText(stringResource(R.string.cc_about_attribution), style = Type.Caption.copy(color = Color(0xFFA4BAD1)))
            }
            BoxWithConstraints(Modifier.weight(1f).fillMaxHeight()) {
              val contentHeight = maxHeight
              Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
               Column(Modifier.fillMaxWidth().heightIn(min = contentHeight).padding(horizontal = 32.dp, vertical = 24.dp), verticalArrangement = Arrangement.SpaceEvenly) {
                val ink = Palette.Label
                BasicText(stringResource(R.string.donate_title), style = Type.LargeTitle.copy(color = ink))
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf(R.drawable.donate_wechat to R.string.donate_wechat, R.drawable.donate_alipay to R.string.donate_alipay).forEach { (id, title) ->
                        val label = stringResource(title)
                        val context = LocalContext.current
                        val bitmap = remember(id) { BitmapFactory.decodeResource(context.resources, id).asImageBitmap() }
                        Column(Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(Palette.Fill).padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            BasicText(label, style = Type.Body.copy(color = ink))
                            Image(bitmap, label, Modifier.size(184.dp).background(Color.White), contentScale = ContentScale.Fit, filterQuality = FilterQuality.High)
                            BasicText(stringResource(R.string.donate_hint), style = Type.Caption.copy(color = ink), maxLines = 2)
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                BasicText(stringResource(R.string.halo_contact_heading_email), style = Type.Caption.copy(color = ink))
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(Triple(R.drawable.contact_wechat_clean, "微信", "扫码联系"), Triple(R.drawable.contact_douyin_clean, "抖音", "eyubaobao777"), Triple(R.drawable.contact_xiaohongshu_clean, "小红书", "11350972266")).forEach { (id, label, detail) ->
                        val context = LocalContext.current
                        val bitmap = remember(id) { BitmapFactory.decodeResource(context.resources, id).asImageBitmap() }
                        Box(Modifier.weight(1f)) {
                            Pressable(onClick = { preview = id to label }, focusRequester = if (id == R.drawable.contact_wechat_clean) firstContactFocus else null, focusedScale = 1.02f) { state ->
                                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Palette.Fill)
                                    .border(if (state.focused) 2.dp else 0.dp, ink, RoundedCornerShape(12.dp)).padding(5.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Image(bitmap, label, Modifier.size(104.dp).background(Color.White), contentScale = ContentScale.Fit, filterQuality = FilterQuality.High)
                                    Column(Modifier.padding(start = 5.dp)) {
                                        BasicText(label, style = Type.Caption.copy(color = ink))
                                        BasicText(detail, style = Type.Caption.copy(color = ink), maxLines = 1)
                                    }
                                }
                            }
                        }
                    }
                }
               }
              }
            }
        }
    }
    LaunchedEffect(Unit) { withFrameNanos { }; runCatching { firstContactFocus.requestFocus() } }
}

@Composable
private fun ContactCode(imageId: Int, label: String, onClick: () -> Unit) {
    val context = LocalContext.current
    val bitmap = remember(imageId) { BitmapFactory.decodeResource(context.resources, imageId).asImageBitmap() }
    Pressable(onClick = onClick, focusedScale = 1.03f) { state ->
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.border(if (state.focused) 2.dp else 0.dp, Color.White, RoundedCornerShape(8.dp))) {
        Image(bitmap, label, modifier = Modifier.size(84.dp).clip(RoundedCornerShape(8.dp)).background(Color.White), contentScale = ContentScale.Fit, filterQuality = FilterQuality.High)
        BasicText(label, style = Type.Caption)
    }
    }
}

@Composable
private fun DonationCode(imageId: Int, labelId: Int) {
    val context = LocalContext.current
    val bitmap = remember(imageId) { BitmapFactory.decodeResource(context.resources, imageId).asImageBitmap() }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Image(bitmap, contentDescription = stringResource(labelId),
            contentScale = ContentScale.Fit, filterQuality = FilterQuality.None,
            modifier = Modifier.size(168.dp).clip(RoundedCornerShape(8.dp)).background(Color.White))
        Spacer(Modifier.height(8.dp))
        BasicText(stringResource(labelId), style = Type.Body)
    }
}

/**
 * A card of setting rows, as in tvOS Settings, with one note under it: the focused row's, or
 * [note] when the row has none or focus is elsewhere. The note keeps two lines of room so the
 * sheet does not shift as focus moves.
 */
@Composable
private fun Group(note: String? = null, modifier: Modifier = Modifier, rows: @Composable ColumnScope.(note: (String?) -> Unit) -> Unit) {
    var focusedNote by remember { mutableStateOf<String?>(null) }
    var inside by remember { mutableStateOf(false) }
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .onFocusChanged { inside = it.hasFocus },
    ) { rows { focusedNote = it } }

}

/** One row: the setting's name on the left, its control on the right, a hairline above all but the first. */
@Composable
private fun SettingRow(label: Int, hint: String?, note: (String?) -> Unit, first: Boolean = false, control: @Composable RowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(bottom = 8.dp).clip(RoundedCornerShape(13.dp))
            .background(Color.White.copy(alpha = 0.075f)).height(64.dp)
            .padding(start = 12.dp, end = 7.dp)
            .onFocusChanged { if (it.hasFocus) note(hint) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.width(105.dp)) {
            BasicText(stringResource(label), style = Type.Body.copy(fontWeight = FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        control()
    }
}

/** A row of chips for one of a few values. */
@Composable
private fun <T> Choices(
    label: Int,
    hint: String?,
    note: (String?) -> Unit,
    options: List<Pair<T, Any>>,
    current: T,
    choose: (T) -> Unit,
    first: Boolean = false,
    reserveCheck: Boolean = false,
    available: (T) -> Boolean = { true },
) {
    val scope = rememberCoroutineScope()
    SettingRow(label, hint, note, first) {
        // The scroll viewport clips its children. Reserve room on every side for focus scaling,
        // and bring the padded bounds into view when moving through a long row with the remote.
        Row(Modifier.weight(1f, fill = false).horizontalScroll(rememberScrollState()).padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            options.forEach { (value, text) ->
                val bringIntoView = remember(value) { BringIntoViewRequester() }
                Box(Modifier.bringIntoViewRequester(bringIntoView)
                    .onFocusChanged { if (it.hasFocus) scope.launch { bringIntoView.bringIntoView() } }
                    .padding(horizontal = 5.dp, vertical = 4.dp)) {
                    Chip(
                        if (text is Int) stringResource(text) else text.toString(),
                        on = current == value,
                        enabled = available(value),
                        reserveCheck = reserveCheck,
                        compact = true,
                    ) { choose(value) }
                }
            }
        }
    }
}

/** Parental PIN on or off; either way goes through the PIN pad (issue #10). */
@Composable
private fun PinChoice(onPin: (on: Boolean) -> Unit, onFocused: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf(false to R.string.st_pin_off, true to R.string.st_pin_on).forEach { (on, label) ->
            Chip(stringResource(label), on = AerialSettings.pinSet == on, onFocused = onFocused) {
                if (AerialSettings.pinSet != on) onPin(on)
            }
        }
    }
}

@Composable
private fun WeatherCityButton() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val title = stringResource(R.string.weather_city_edit)
    val hint = stringResource(R.string.weather_city_hint)
    val failed = stringResource(R.string.weather_city_failed)
    Pressable(onClick = {
        val input = android.widget.EditText(context).apply { setSingleLine(); setText(Weather.place.orEmpty()); setSelectAllOnFocus(true); this.hint = hint }
        android.app.AlertDialog.Builder(context).setTitle(title).setView(input)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val city = input.text.toString().trim()
                if (city.isNotEmpty()) scope.launch {
                    val ok = withContext(Dispatchers.IO) { Weather.choose(city) }
                    if (!ok) android.widget.Toast.makeText(context, failed, android.widget.Toast.LENGTH_LONG).show()
                }
            }.show().applySettingsFrost()
    }) { state ->
        BasicText((Weather.place ?: stringResource(R.string.wp_weather_none)) + "  ›", style = Type.Body.copy(color = if (state.focused) Palette.Label else Palette.SecondaryLabel), modifier = Modifier.padding(8.dp))
    }
}

/** Where the weather is for and what it is now. The city is set on the phone page. */
@Composable
private fun WeatherValue() {
    val w = Weather.now
    val place = Weather.place
    val kind = w?.let {
        stringResource(
            when (it.kind) {
                WeatherKind.Clear -> R.string.wx_clear
                WeatherKind.PartlyCloudy -> R.string.wx_partly_cloudy
                WeatherKind.Cloudy -> R.string.wx_cloudy
                WeatherKind.Fog -> R.string.wx_fog
                WeatherKind.Rain -> R.string.wx_rain
                WeatherKind.Snow -> R.string.wx_snow
                WeatherKind.Thunder -> R.string.wx_thunder
            },
        )
    }
    BasicText(
        if (w != null) listOfNotNull(place, stringResource(R.string.weather_temperature, w.celsius) + " $kind").joinToString(" · ") else stringResource(R.string.wp_weather_none),
        style = Type.Body.copy(color = Palette.SecondaryLabel),
        modifier = Modifier.padding(end = 8.dp),
    )
}

/** A pill to pick one of a few options: a check on the chosen one, white when focused. */
@Composable
private fun Chip(label: String, on: Boolean, onFocused: (() -> Unit)? = null, enabled: Boolean = true, reserveCheck: Boolean = false, compact: Boolean = false, focusRequester: FocusRequester? = null, onKey: ((androidx.compose.ui.input.key.KeyEvent) -> Boolean)? = null, onClick: () -> Unit) {
    Pressable(onClick = onClick, enabled = enabled, focusRequester = focusRequester, onKey = onKey, focusedScale = 1.06f) { state ->
        if (onFocused != null) LaunchedEffect(state.focused) { if (state.focused) onFocused() }
        val bg by animateColorAsState(
            when {
                state.focused -> Palette.Focus
                on -> Palette.FillStrong
                else -> Palette.Fill
            },
            tween(140, easing = Motion.EaseOut), label = "chip-bg",
        )
        val fg = if (state.focused) Palette.OnFocus else Palette.Label
        Row(
            Modifier.clip(RoundedCornerShape(50)).background(bg).height(if (compact) 34.dp else 40.dp).padding(horizontal = if (compact) 9.dp else 20.dp)
                .then(if (enabled) Modifier else Modifier.graphicsLayer { alpha = 0.38f }),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (compact) 3.dp else 8.dp),
        ) {
            if (on && !reserveCheck) GlyphIcon(Glyph.Check, fg, if (compact) 10.dp else 14.dp)
            if (reserveCheck) {
                Box(contentAlignment = Alignment.Center) {
                    BasicText(label, modifier = Modifier.padding(horizontal = 10.dp), style = (if (compact) Type.Caption else Type.Body).copy(color = fg))
                    if (on) Box(Modifier.align(Alignment.CenterStart)) { GlyphIcon(Glyph.Check, fg, if (compact) 10.dp else 14.dp) }
                }
            } else BasicText(label, style = (if (compact) Type.Caption else Type.Body).copy(color = fg), maxLines = 1, softWrap = false)
        }
    }
}

/** A pill that does something rather than choosing a value: white when focused, a glyph after its label. */
@Composable
private fun ActionChip(label: String, glyph: Glyph, onClick: () -> Unit) {
    Pressable(onClick = onClick, focusedScale = 1.06f) { state ->
        val bg by animateColorAsState(if (state.focused) Palette.Focus else Palette.FillStrong, tween(140, easing = Motion.EaseOut), label = "action-bg")
        val fg = if (state.focused) Palette.OnFocus else Palette.Label
        Row(
            Modifier.clip(RoundedCornerShape(50)).drawBehind { drawRect(bg) }.height(40.dp).padding(start = 20.dp, end = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            BasicText(label, style = Type.Body.copy(color = fg))
            GlyphIcon(glyph, fg, 18.dp)
        }
    }
}

@Composable
private fun RowScope.ScreensaverChoice() {
    val scope = rememberCoroutineScope()
    Row(Modifier.weight(1f, fill = false).horizontalScroll(rememberScrollState()).padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        listOf(
            ScreensaverMode.Aerial to R.string.saver_online_video,
            ScreensaverMode.Photos to R.string.saver_local_photos,
            ScreensaverMode.Bing to R.string.halo_saver_bing,
            ScreensaverMode.FlipClock to R.string.ss_flip_clock,
            ScreensaverMode.Videos to R.string.saver_local_video,
        ).forEach { (mode, label) ->
            val bringIntoView = remember(mode) { BringIntoViewRequester() }
            Box(Modifier.bringIntoViewRequester(bringIntoView)
                .onFocusChanged { if (it.hasFocus) scope.launch { bringIntoView.bringIntoView() } }
                .padding(horizontal = 5.dp, vertical = 4.dp)) {
                Chip(stringResource(label), on = Wallpapers.screensaver == mode, compact = true) { Wallpapers.chooseScreensaver(mode) }
            }
        }

    }
}

@Composable
private fun PhoneCard(url: String?, qr: ImageBitmap?) {
    Column(Modifier.width(220.dp).padding(top = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(176.dp).clip(RoundedCornerShape(18.dp)).background(Color.White).padding(14.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (qr != null) {
                Image(qr, contentDescription = null, contentScale = ContentScale.Fit, filterQuality = FilterQuality.None, modifier = Modifier.fillMaxSize())
            } else {
                GlyphIcon(Glyph.Offline, Color.Black.copy(alpha = 0.35f), 40.dp)
            }
        }
        Spacer(Modifier.height(14.dp))
        BasicText(stringResource(R.string.wp_phone_title), style = Type.Headline)
        Spacer(Modifier.height(4.dp))
        BasicText(
            stringResource(if (url != null) R.string.wp_phone_body else R.string.wp_phone_offline),
            style = Type.Caption.copy(textAlign = androidx.compose.ui.text.style.TextAlign.Center),
        )
        if (url != null) {
            Spacer(Modifier.height(6.dp))
            // The whole address, token and all, to type into a computer's browser.
            BasicText(url.removePrefix("http://"), style = Type.Caption.copy(color = Palette.SecondaryLabel))
        }
    }
}

private data class BingEntry(val date: String, val title: String, val credit: String, val category: String, val thumbnail: String)
private data class BingOriginal(val url: String, val uhd: Boolean)
private const val BING_ARCHIVE = "https://ygn-bing-wallpaper.pages.dev/"

/** Read a bounded response from the archive or Bing; no arbitrary redirects to other hosts. */
private fun bingBytes(address: String, limit: Int): ByteArray {
    val uri = java.net.URI(address)
    require(uri.scheme == "https" && uri.host in setOf("ygn-bing-wallpaper.pages.dev", "www.bing.com", "cdn.bimg.cc"))
    val c = app.orchard.tv.system.CompatibleHttp.open(address)
    try {
        c.connectTimeout = 8000; c.readTimeout = 15000; c.instanceFollowRedirects = false
        c.setRequestProperty("User-Agent", "HaloTV/2.1 (https://github.com/Null9797/halo-tv)")
        check(c.responseCode == 200)
        require((c.getHeaderField("Content-Length")?.toLongOrNull() ?: 0L) <= limit)
        return c.inputStream.use { input ->
            val out = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(16384)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                require(out.size() + n <= limit)
                out.write(buffer, 0, n)
            }
            out.toByteArray()
        }
    } finally { c.disconnect() }
}

@Composable
private fun BingLibrary(onDismiss: () -> Unit) {
    app.orchard.tv.system.CompatibleHttp.init(LocalContext.current.applicationContext)
    var entries by remember { mutableStateOf<List<BingEntry>>(emptyList()) }
    var category by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<BingEntry?>(null) }
    var original by remember { mutableStateOf<BingOriginal?>(null) }
    var image by remember { mutableStateOf<ImageBitmap?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf(false) }
    var attempt by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()
    val grid = rememberLazyGridState()
    val applyFocus = remember { FocusRequester() }
    var lastFocus by remember { mutableStateOf<FocusRequester?>(null) }
    LaunchedEffect(attempt) {
        loading = true; error = false
        val result = withContext(Dispatchers.IO) { runCatching {
            val array = org.json.JSONArray(String(bingBytes(BING_ARCHIVE + "data/index.json", 4_000_000), Charsets.UTF_8))
            val dateFormat = Regex("[0-9]{8}")
            val thumbnailFormat = Regex("thumbnails/[0-9]{4}/[0-9]{2}/[0-9]{8}\\.webp")
            buildList {
                for (i in 0 until array.length()) {
                    val o = array.getJSONObject(i); val date = o.optString("date"); val thumb = o.optString("thumbnail")
                    if (date.matches(dateFormat) && thumb.matches(thumbnailFormat))
                        add(BingEntry(date, o.optString("title"), o.optString("copyright"), o.optString("category"), BING_ARCHIVE + thumb))
                }
            }
        } }
        result.onSuccess { entries = it }.onFailure { error = true }; loading = false
    }
    LaunchedEffect(selected?.date) {
        original = null; image = null; saveError = false
        val item = selected ?: return@LaunchedEffect
        val pair = withContext(Dispatchers.IO) { runCatching {
            val array = org.json.JSONArray(String(bingBytes(BING_ARCHIVE + "data/${item.date.take(4)}.json", 2_000_000), Charsets.UTF_8))
            val o = (0 until array.length()).asSequence().map { array.getJSONObject(it) }.first { it.optString("date") == item.date }
            val url = o.getString("url")
            val uri = java.net.URI(url); require(uri.scheme == "https" && uri.host in setOf("www.bing.com", "cdn.bimg.cc"))
            val bytes = bingBytes(url, 16_000_000)
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = 2 })
                ?: error("Invalid image")
            BingOriginal(url, o.optBoolean("uhd")) to bitmap.asImageBitmap()
        }.getOrNull() }
        if (pair == null) saveError = true else { original = pair.first; image = pair.second }
    }
    val filtered = remember(entries, category) { if (category.isEmpty()) entries else entries.filter { it.category == category } }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        GalleryImmersive()
        Column(Modifier.fillMaxSize().settingsFrost().padding(horizontal = 28.dp, vertical = 22.dp)) {
            LibraryHeader(R.string.bing_library, R.string.bing_source_notice, onDismiss)
            Spacer(Modifier.height(12.dp))
            RotationChoices(video = false, category = category)
            val status = when (OnlineRotation.bingStatus) {
                BingRotationStatus.Loading -> R.string.rotation_starting
                BingRotationStatus.Started -> R.string.rotation_started
                BingRotationStatus.Failed -> R.string.rotation_failed
                BingRotationStatus.Idle -> null
            }
            if (status != null) BasicText(stringResource(status), style = Type.Caption.copy(color = Palette.SecondaryLabel))
            Spacer(Modifier.height(10.dp))
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Column(Modifier.width(125.dp).fillMaxHeight().clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF1C2A42)).verticalScroll(rememberScrollState()).padding(7.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    LibraryCategory(stringResource(R.string.bing_all), category.isEmpty()) {
                        category = ""; selected = null; scope.launch { grid.scrollToItem(0) }
                    }
                    entries.map { it.category }.filter { it.isNotBlank() }.distinct().sorted().forEach { c ->
                        LibraryCategory(c, c == category) { category = c; selected = null; scope.launch { grid.scrollToItem(0) } }
                    }
                }
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    if (loading) BasicText(stringResource(R.string.bing_loading), style = Type.Caption)
                    if (error) {
                        BasicText(stringResource(R.string.bing_failed), style = Type.Caption)
                        Chip(stringResource(R.string.bing_retry), false) { attempt++ }
                    }
                    LazyVerticalGrid(columns = GridCells.Fixed(3), state = grid, modifier = Modifier.weight(1f).fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(5.dp)) {
                        itemsIndexed(filtered, key = { _, item -> item.date }) { index, item ->
                            val focus = remember(item.date) { FocusRequester() }
                            Pressable(onClick = { selected = item }, focusRequester = focus, focusedScale = 1.02f,
                                onKey = { event ->
                                    if (event.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_DPAD_RIGHT && original != null &&
                                        galleryRowEndsAt(index, filtered.size, 3)) {
                                        if (event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount == 0) runCatching { applyFocus.requestFocus() }; true
                                    } else false
                                }) { state ->
                                LaunchedEffect(state.focused) { if (state.focused) lastFocus = focus }
                                val thumb = aerialThumbnail(item.thumbnail, grid, item.date) { app.orchard.tv.wallpaper.PhoneOnlineLibrary.legacyThumbnail(item.date) }
                                LibraryThumbnail(thumb ?: image.takeIf { selected?.date == item.date }, state.focused, selected?.date == item.date)
                            }
                        }
                    }
                    Spacer(Modifier.height(5.dp))
                    Chip(stringResource(R.string.wp_daily_latest), on = Wallpapers.selectedId == Wallpapers.LATEST_DAILY, compact = true) {
                        Wallpapers.select(Wallpapers.LATEST_DAILY); onDismiss()
                    }
                }
                Column(Modifier.width(320.dp).fillMaxHeight().clip(RoundedCornerShape(18.dp)).background(Color(0xFF202E47)).verticalScroll(rememberScrollState()).padding(14.dp)) {
                    Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(12.dp)).background(Color.Black), contentAlignment = Alignment.Center) {
                        image?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit) }
                            ?: BasicText(stringResource(if (selected == null) R.string.bing_select else if (saveError) R.string.bing_failed else R.string.bing_loading), style = Type.Caption)
                    }
                    Spacer(Modifier.height(9.dp))
                    selected?.let { item ->
                        BasicText(item.title, style = Type.Headline, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(3.dp))
                        BasicText(item.credit.ifBlank { stringResource(R.string.bing_credit_unknown) },
                            style = Type.Caption.copy(color = Palette.SecondaryLabel))
                        if (saveError) BasicText(stringResource(R.string.bing_download_failed), style = Type.Caption)
                        Spacer(Modifier.height(8.dp))
                        val source = original
                        if (source != null) LibraryAction(stringResource(if (saving) R.string.bing_applying else R.string.bing_apply), applyFocus, enabled = !saving,
                            onKey = { event ->
                                if (event.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_DPAD_LEFT) {
                                    if (event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount == 0) runCatching { lastFocus?.requestFocus() }; true
                                } else false
                            }) {
                            saving = true; saveError = false
                            scope.launch {
                                val previous = Wallpapers.selectedId
                                val id = runCatching {
                                    val bytes = withContext(Dispatchers.IO) {
                                        val uhdUrl = source.url.replace("_1920x1080.jpg", "_UHD.jpg")
                                        if (source.uhd) runCatching { bingBytes(uhdUrl, 32_000_000) }.getOrElse { bingBytes(source.url, 16_000_000) }
                                        else bingBytes(source.url, 16_000_000)
                                    }
                                    if (Wallpapers.applyBingCached(bytes, item.credit, previous)) Wallpapers.BING_ROTATION else null
                                }.getOrNull()
                                saving = false
                                if (id == null) saveError = true else { OnlineRotation.markBingApplied(id, category, item.date); onDismiss() }
                            }
                        }
                        Spacer(Modifier.height(7.dp))
                        BasicText(stringResource(R.string.bing_cache_note), style = Type.Caption.copy(color = Palette.SecondaryLabel))
                    }
                }
            }
        }
    }

}

/** Selection is loaded only while this settings section is visible; no full-size images are retained. */
@Composable
private fun LocalSaverControls(video: Boolean, note: (String?) -> Unit) {
    val context = LocalContext.current
    var single by remember(video) { mutableStateOf(LocalSaverSelection.single(context, video)) }
    var selected by remember(video) { mutableStateOf(LocalSaverSelection.selected(context, video)) }
    var choosing by remember(video) { mutableStateOf(false) }
    val media by produceState<List<Pair<String, String>>>(emptyList(), video, Wallpapers.items) {
        value = withContext(Dispatchers.IO) {
            if (video) ScreensaverVideos.all(context).map { it.id to it.name }
            else Wallpapers.items.filter { it.kind == WallpaperKind.Photo && it.id != Wallpapers.BING_ROTATION }
                .mapIndexed { index, item -> item.id to context.getString(R.string.halo_saver_photo_number, index + 1) }
        }
    }
    Choices(R.string.halo_saver_playback, stringResource(R.string.halo_saver_missing_hint), note,
        listOf(false to R.string.halo_saver_all, true to R.string.halo_saver_single), single,
        { single = it; LocalSaverSelection.chooseMode(context, video, it); choosing = it })
    if (single) {
        SettingRow(R.string.halo_saver_pick, null, note) {
            ActionChip(media.firstOrNull { it.first == selected }?.second?.take(22)
                ?: stringResource(R.string.halo_saver_pick), Glyph.ChevronRight) { choosing = !choosing }
        }
        if (choosing) {
            if (media.isEmpty()) BasicText(stringResource(R.string.halo_saver_empty), style = Type.Caption)
            else LazyVerticalGrid(columns = GridCells.Fixed(3), modifier = Modifier.fillMaxWidth().height(170.dp),
                contentPadding = PaddingValues(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(media, key = { it.first }) { item ->
                    val image by produceState<ImageBitmap?>(null, item.first) {
                        if (!video) value = withContext(Dispatchers.IO) { Wallpapers.thumbnail(item.first)?.asImageBitmap() }
                    }
                    Pressable(onClick = { selected = item.first; LocalSaverSelection.choose(context, video, item.first) },
                        focusedScale = 1.02f) { focus ->
                        Column(Modifier.clip(RoundedCornerShape(10.dp))
                            .background(if (focus.focused) Palette.Focus else Palette.Fill).padding(8.dp)) {
                            image?.let { Image(it, null, modifier = Modifier.fillMaxWidth().height(62.dp), contentScale = ContentScale.Fit) }
                            BasicText((if (selected == item.first) "✓ " else "") + item.second,
                                style = Type.Caption.copy(color = if (focus.focused) Palette.OnFocus else Palette.Label), maxLines = 1,
                                overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}
