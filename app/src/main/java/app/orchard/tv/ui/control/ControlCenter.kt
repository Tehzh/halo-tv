package app.orchard.tv.ui.control

import androidx.compose.ui.focus.onFocusChanged
import app.orchard.tv.ui.components.smoothBackground

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.launch
import android.view.KeyEvent as NativeKeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.CompositionLocalProvider
import app.orchard.tv.ui.components.LocalPressableEnabled
import androidx.compose.ui.layout.layout
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.animation.core.snap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.Image
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import androidx.compose.runtime.remember
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import app.orchard.tv.guard.GuardHealth
import app.orchard.tv.guard.GuardSetup
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.orchard.tv.guard.HomeGuardService
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.orchard.tv.CcPage
import app.orchard.tv.BuildConfig
import app.orchard.tv.HomeViewModel
import app.orchard.tv.Overlay
import app.orchard.tv.R
import app.orchard.tv.apps.SourceLooks
import app.orchard.tv.settings.AerialSettings
import app.orchard.tv.settings.Effects
import app.orchard.tv.settings.Restricted
import app.orchard.tv.settings.RestrictedKind
import app.orchard.tv.ui.home.kidsTimeLine
import app.orchard.tv.system.HisenseLaserPanel
import app.orchard.tv.system.HomeStatus
import app.orchard.tv.system.LaunchFallback
import app.orchard.tv.system.NetKind
import app.orchard.tv.system.SourceKind
import app.orchard.tv.system.Sounds
import app.orchard.tv.system.OutputDevice
import app.orchard.tv.system.SystemProbe
import app.orchard.tv.ui.components.Pressable
import app.orchard.tv.ui.components.consumeTaps
import app.orchard.tv.ui.components.dismissOnTap
import app.orchard.tv.ui.components.liquidGlass
import app.orchard.tv.ui.wallpaper.DonationDialog
import app.orchard.tv.ui.home.MenuRow
import app.orchard.tv.ui.home.VpnBadge
import app.orchard.tv.ui.home.dateText
import app.orchard.tv.ui.home.timeText
import app.orchard.tv.ui.theme.Glyph
import app.orchard.tv.ui.theme.GlyphIcon
import app.orchard.tv.ui.theme.LocalReducedMotion
import app.orchard.tv.ui.theme.Motion
import app.orchard.tv.ui.theme.Palette
import app.orchard.tv.ui.theme.Type
import app.orchard.tv.update.ReleaseNotes
import app.orchard.tv.update.Updates
import app.orchard.tv.update.PhoneUpdate
import app.orchard.tv.wallpaper.PhoneServer
import java.time.LocalDateTime

/**
 * tvOS Control Center: a glass panel that drops in from the top-right corner over a blurred Home.
 * Inputs open as a second page inside the same panel, the way tvOS shows audio routes.
 */
@Composable
fun ControlCenter(
    visible: Boolean,
    page: CcPage,
    vm: HomeViewModel,
    now: LocalDateTime,
    onOpen: (android.content.Intent) -> Unit,
    onClose: () -> Unit,
    /** Draw the closed panel once, nearly transparent, so that its first opening is not its first draw (HomeScreen). */
    warm: Boolean = false,
    /** Home is on its top row: no tiles under the panel, only the wallpaper (see liquidGlass). */
    onShelf: Boolean = false,
) {
    val reducedMotion = LocalReducedMotion.current
    BackHandler(enabled = visible) {
        when (page) {
            CcPage.Main -> onClose()
            CcPage.Inputs -> vm.overlay = Overlay.ControlCenter(CcPage.Main)
            CcPage.InputSettings -> vm.overlay = Overlay.ControlCenter(CcPage.Inputs)
            CcPage.Update, CcPage.Setup -> vm.overlay = Overlay.ControlCenter(CcPage.Main)
        }
    }

    LaunchedEffect(visible) { if (visible) Updates.checkIfStale(maxAgeMs = Updates.CONTROL_CENTER_MAX_AGE_MS) }

    // Keep one composed main panel while Home is active. Closing removes its placement and
    // interaction, not its text layout and remembered resources. No hidden polling or decoder.
    var prepared by remember { mutableStateOf(false) }
    var warming by remember { mutableStateOf(false) }
    LaunchedEffect(visible, vm.resumed, vm.screensaverVisible, vm.lastInputAt) {
        warming = false
        if (visible) prepared = true
        else if (!vm.resumed || vm.screensaverVisible) {
            prepared = false
            warming = false
        } else if (!prepared) {
            delay(1800)
            prepared = true
            warming = true
            repeat(3) { withFrameNanos { } }
            warming = false
        }
    }
    val panelAlpha = animateFloatAsState(
        if (visible) 1f else 0f,
        if (reducedMotion) snap() else tween(if (visible) 160 else 130, easing = Motion.EaseOut),
        label = "cc-opacity",
    )
    val panelPlaced by remember(visible, warming) {
        derivedStateOf { visible || warming || panelAlpha.value > 0f }
    }
    // One draw instead of a 45% Home veil plus a second 28% full-screen fade layer.
    if (panelPlaced && !warming) {
        Canvas(Modifier.fillMaxSize().then(if (visible) Modifier.dismissOnTap(onClose) else Modifier)) {
            drawRect(Color.Black, alpha = 0.604f * panelAlpha.value)
        }
    }
    Box(Modifier.fillMaxSize().padding(top = 24.dp, end = 32.dp), contentAlignment = Alignment.TopEnd) {
        if ((prepared || visible) && vm.resumed && !vm.screensaverVisible) {
            CompositionLocalProvider(LocalPressableEnabled provides visible) {
                Column(
                    Modifier.width(420.dp)
                        .layout { measurable, constraints ->
                            val placeable = measurable.measure(constraints)
                            if (panelPlaced) {
                                layout(placeable.width, placeable.height) { placeable.place(0, 0) }
                            } else layout(0, 0) { }
                        }
                        .graphicsLayer {
                            alpha = if (warming && !visible) 0.01f else panelAlpha.value
                            // Cache the group only during its short opacity transition. Once opaque,
                            // Auto stops using the extra framebuffer while the video moves behind it.
                            compositingStrategy = CompositingStrategy.Auto
                        }
                        .then(if (visible) Modifier.consumeTaps() else Modifier)
                        .liquidGlass(24.dp, seeThroughFilm = onShelf, translucent = true, snapshotKey = visible),
                ) {
                    AnimatedContent(
                        targetState = if (visible) page else CcPage.Main,
                        transitionSpec = {
                            if (reducedMotion) (fadeIn(tween(1)) togetherWith fadeOut(tween(1)))
                                .using(SizeTransform { _, _ -> tween(0) })
                            else (fadeIn(tween(130, easing = Motion.EaseOut)) togetherWith fadeOut(tween(110, easing = Motion.EaseOut)))
                                .using(SizeTransform { _, _ -> tween(0) })
                        }, label = "cc-page",
                    ) { p ->
                        Box(Modifier.padding(16.dp)) {
                            when (p) {
                                CcPage.Main -> if (Restricted.active) KidsPage(vm, now, visible)
                                    else MainPage(vm, now, takeFocus = visible, poll = visible, onOpen = onOpen)
                                CcPage.Inputs -> InputsPage(vm, visible, onOpen)
                                CcPage.InputSettings -> InputSettingsPage(vm, visible)
                                CcPage.Update -> UpdatePage(vm, visible)
                                CcPage.Setup -> SetupPage(vm, visible, onOpen)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Control Center in Kids and Elders mode (Restricted): the time, volume and brightness, the day's
 * time left, and leaving the mode with the PIN. No inputs, network, settings or Personalize.
 */
@Composable
private fun KidsPage(vm: HomeViewModel, now: LocalDateTime, takeFocus: Boolean) {
    val first = remember { FocusRequester() }
    val context = LocalContext.current
    val laser = remember { HisenseLaserPanel.usable(context) }
    val showsVolume = remember { OutputDevice.showsVolume(context) }
    val showsBrightness = remember { OutputDevice.showsBrightness() }
    PollBrightness(vm, laser, enabled = takeFocus)
    LaunchedEffect(takeFocus) {
        if (!takeFocus) return@LaunchedEffect
        withFrameNanos { }
        runCatching { first.requestFocus() }
    }
    Column {
        BasicText(timeText(now), style = Type.SmallClock.copy(fontSize = Type.LargeTitle.fontSize * 1.05f))
        BasicText(dateText(now), style = Type.Callout)
        kidsTimeLine()?.let {
            Spacer(Modifier.height(6.dp))
            BasicText(it, style = Type.Callout.copy(color = Palette.SecondaryLabel))
        }
        Spacer(Modifier.height(12.dp))
        // A box or stick has neither a panel nor, with its volume fixed, a sound level of its own (#1307).
        if (showsVolume) {
            VolumeRow(vm, first)
            Spacer(Modifier.height(6.dp))
        }
        if (showsBrightness) BrightnessRow(vm, remember { FocusRequester() }, laser)
        Spacer(Modifier.height(6.dp))
        MenuRow(
            Glyph.Shield,
            stringResource(if (Restricted.kind == RestrictedKind.Kids) R.string.cc_kids_leave else R.string.cc_elders_leave),
            onClick = vm::leaveKidsMode,
        )
    }
}

@Composable
private fun MainPage(vm: HomeViewModel, now: LocalDateTime, takeFocus: Boolean, poll: Boolean, onOpen: (android.content.Intent) -> Unit) {
    var donationOpen by remember { mutableStateOf(false) }
    var noticeOpen by remember { mutableStateOf(false) }
    var colorOpen by remember { mutableStateOf(false) }
    var performanceOpen by remember { mutableStateOf(false) }
    var powerConfirmOpen by remember { mutableStateOf(false) }
    if (takeFocus && donationOpen) DonationDialog { donationOpen = false }
    if (takeFocus && noticeOpen) HaloNoticeDialog { noticeOpen = false }
    if (takeFocus && colorOpen) DesktopColorDialog { colorOpen = false }
    if (takeFocus && performanceOpen) PerformanceDialog { performanceOpen = false }
    val context = LocalContext.current
    if (takeFocus && powerConfirmOpen) ControlPopup(onDismiss = { powerConfirmOpen = false }) {
        Column(Modifier.width(360.dp).consumeTaps().liquidGlass(20.dp, translucent = true).padding(20.dp)) {
            BasicText(stringResource(R.string.halo_power), style = Type.Headline)
            Spacer(Modifier.height(12.dp))
            BasicText(stringResource(R.string.halo_power_warning), style = Type.Body)
            Spacer(Modifier.height(14.dp))
            ControlMenuRow(Glyph.Power, stringResource(R.string.halo_power_continue), onClick = {
                powerConfirmOpen = false
                if (!HomeGuardService.showPowerMenu()) Toast.makeText(context, R.string.halo_power_unavailable, Toast.LENGTH_LONG).show()
            })
            ControlMenuRow(Glyph.ChevronLeft, stringResource(android.R.string.cancel), onClick = { powerConfirmOpen = false })
        }
    }
    val laser = remember { HisenseLaserPanel.usable(context) }
    val showsVolume = remember { OutputDevice.showsVolume(context) }
    val showsBrightness = remember { OutputDevice.showsBrightness() }
    PollBrightness(vm, laser, enabled = poll)
    val scope = rememberCoroutineScope()
    val first = remember { FocusRequester() }
    val volumeFocus = remember { FocusRequester() }
    val brightnessFocus = remember { FocusRequester() }
    LaunchedEffect(takeFocus) {
        if (!takeFocus) return@LaunchedEffect
        withFrameNanos { }
        runCatching { first.requestFocus() }
    }

    var cleaning by remember { mutableStateOf(false) }
    val cleanBackground: () -> Unit = {
        if (!cleaning) scope.launch {
            cleaning = true
            try {
                val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                fun available(): Long = ActivityManager.MemoryInfo().also(am::getMemoryInfo).availMem
                val before = withContext(Dispatchers.IO) { available() }
                val result = withContext(Dispatchers.IO) { app.orchard.tv.system.BackgroundCleaner.clean(context) }
                val message = when (result.outcome) {
                    app.orchard.tv.system.BackgroundCleaner.Outcome.Unsupported -> context.getString(R.string.home_clean_unsupported)
                    app.orchard.tv.system.BackgroundCleaner.Outcome.Protected -> context.getString(R.string.home_clean_protected)
                    app.orchard.tv.system.BackgroundCleaner.Outcome.Failed -> context.getString(R.string.home_clean_failed)
                    app.orchard.tv.system.BackgroundCleaner.Outcome.Busy -> context.getString(R.string.home_clean_busy)
                    app.orchard.tv.system.BackgroundCleaner.Outcome.Done -> if (result.requested == 0) context.getString(R.string.home_clean_none) else {
                        delay(1200)
                        val changeMb = withContext(Dispatchers.IO) { (available() - before) / (1024 * 1024) }
                        val delta = if (changeMb >= 0) "+$changeMb" else "$changeMb"
                        context.getString(if (result.limitedVisibility) R.string.home_clean_limited else R.string.home_clean_result, result.requested, delta)
                    }
                }
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            } finally { cleaning = false }
        }
    }
    Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
        Spacer(Modifier.height(5.dp))
        WallpaperFeatureCard(focusRequester = first) { vm.overlay = Overlay.WallpaperPicker }
        Spacer(Modifier.height(5.dp))
        ControlMenuRow(Glyph.Switches, stringResource(R.string.cc_halo_settings), onClick = { vm.overlay = Overlay.Wallpaper })
        app.orchard.tv.announcements.AnnouncementEntry(vm)
        Spacer(Modifier.height(7.dp))
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.075f)).padding(vertical = 5.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            RoundButton(Glyph.Input, stringResource(R.string.cc_inputs)) {
                vm.overlay = Overlay.ControlCenter(CcPage.Inputs)
            }
            RoundButton(when (vm.network.kind) { NetKind.Ethernet -> Glyph.Ethernet; NetKind.Offline -> Glyph.Offline; else -> Glyph.Wifi }, stringResource(R.string.cc_network)) {
                vm.whenUnlocked {
                    val pages = SystemProbe.networkSettingsIntents(context, ethernet = vm.network.kind == NetKind.Ethernet)
                    LaunchFallback.arm(pages.drop(1))
                    onOpen(pages.first())
                }
            }
            RoundButton(Glyph.Gear, stringResource(R.string.cc_system_settings)) { vm.whenUnlocked { onOpen(SystemProbe.settingsIntent(context)) } }
            RoundButton(Glyph.Trash, stringResource(R.string.home_clean_background), onClick = cleanBackground)
            RoundButton(Glyph.Power, stringResource(R.string.halo_power)) { powerConfirmOpen = true }
        }
        Spacer(Modifier.height(6.dp))
        val colorLabel = when (AerialSettings.clockTextColor) {
            1 -> R.string.clock_dark_gray
            2 -> R.string.clock_black
            else -> R.string.clock_white
        }
        val performanceLabel = when (AerialSettings.effects) {
            Effects.Lite -> R.string.st_effects_lite
            Effects.Full -> R.string.st_effects_full
            Effects.Auto -> R.string.st_effects_auto
        }
        if (showsVolume || showsBrightness) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                if (showsVolume) Box(Modifier.weight(1f)) { VolumeRow(vm, volumeFocus) }
                if (showsBrightness) Box(Modifier.weight(1f)) { BrightnessRow(vm, brightnessFocus, laser) }
            }
            Spacer(Modifier.height(5.dp))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f)) {
                ControlMenuRow(Glyph.TextAppearance, stringResource(R.string.clock_text_color), onClick = { colorOpen = true }, trailing = stringResource(colorLabel))
            }
            Box(Modifier.weight(1f)) {
                ControlMenuRow(Glyph.Performance, stringResource(R.string.st_effects), onClick = { performanceOpen = true }, trailing = stringResource(performanceLabel))
            }
        }

        Spacer(Modifier.height(7.dp))
        Spacer(Modifier.height(2.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f)) { ControlMenuRow(Glyph.Kids, stringResource(R.string.halo_kids_mode), onClick = { vm.toggleRestricted(RestrictedKind.Kids) }) }
            Box(Modifier.weight(1f)) { ControlMenuRow(Glyph.Elders, stringResource(R.string.halo_elders_mode), onClick = { vm.toggleRestricted(RestrictedKind.Elders) }) }
        }
        Spacer(Modifier.height(5.dp))
        // What the TV opens when switched on: easy to forget, and puzzling when it happens.
        vm.bootTargetLabel()?.let { name ->
            ControlMenuRow(
                Glyph.Power,
                stringResource(R.string.cc_boot),
                onClick = { vm.overlay = Overlay.ControlCenter(CcPage.InputSettings) },
                trailing = "$name ›",
            )
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f)) { ControlMenuRow(Glyph.Coffee, stringResource(R.string.donate_title), onClick = { donationOpen = true }) }
            Box(Modifier.weight(1f)) { ControlMenuRow(Glyph.Info, stringResource(R.string.halo_notice_title), onClick = { noticeOpen = true }) }
        }
        Spacer(Modifier.height(5.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f)) {
                ControlMenuRow(Glyph.Shield, stringResource(R.string.cc_setup_title),
                    onClick = { vm.overlay = Overlay.ControlCenter(CcPage.Setup) }, trailing = "›")
            }
            Box(Modifier.weight(1f)) {
                val label = if (Updates.available == null) R.string.halo_update_manage else R.string.halo_update_new
                ControlMenuRow(Glyph.Download, stringResource(label),
                    onClick = { vm.overlay = Overlay.ControlCenter(CcPage.Update) }, trailing = "›")
            }
        }

        Spacer(Modifier.height(8.dp))
        val (dot, text) = when (vm.homeStatus) {
            HomeStatus.Default -> Palette.Green to stringResource(R.string.cc_home_default)
            HomeStatus.Guarded -> Palette.Green to stringResource(R.string.cc_home_guarded)
            HomeStatus.NotDefault -> Palette.Orange to stringResource(R.string.cc_home_not_default)
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 19.5.dp, bottom = 8.dp)) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(dot))
            Spacer(Modifier.width(8.dp))
            BasicText(text, style = Type.Caption, modifier = Modifier.weight(1f))
            BasicText(Updates.buildLabel, style = Type.Caption.copy(color = Palette.TertiaryLabel), modifier = Modifier.padding(end = 14.dp))
        }
    }
}

@Composable
private fun WallpaperFeatureCard(focusRequester: FocusRequester, onClick: () -> Unit) {
    Pressable(onClick = onClick, focusRequester = focusRequester, focusedScale = 1.015f, modifier = Modifier.fillMaxWidth()) { state ->
        val shape = RoundedCornerShape(14.dp)
        Box(Modifier.fillMaxWidth().height(60.dp).clip(shape).background(Color(0xFF253B5B))) {
            Image(painterResource(R.drawable.halo_control_wallpaper), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = if (state.focused) 0.1f else 0.25f)))
            Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GlyphIcon(Glyph.Photo, Color.White, 20.dp)
                BasicText(stringResource(R.string.home_wallpaper_screensaver), style = Type.Body.copy(color = Color.White), modifier = Modifier.weight(1f))
                GlyphIcon(Glyph.ChevronRight, Color.White, 18.dp)
            }
            if (state.focused) Box(Modifier.fillMaxSize().border(2.dp, Color.White, shape))
        }
    }
}

/** Individual frosted rows echo the wallpaper-tinted glass without another live blur. */
@Composable
private fun ControlMenuRow(
    glyph: Glyph,
    text: String,
    onClick: () -> Unit,
    trailing: String? = null,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
) {
    MenuRow(glyph, text, onClick, modifier = modifier.padding(vertical = 2.dp)
        .clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.075f)),
        focusRequester = focusRequester, trailing = trailing, rowHeight = 40.dp)
}

@Composable
private fun PerformanceDialog(onDismiss: () -> Unit) {
    val focus = remember { FocusRequester() }
    ControlPopup(onDismiss) {
        Column(Modifier.width(320.dp).consumeTaps().liquidGlass(20.dp, translucent = true).padding(16.dp)) {
            BasicText(stringResource(R.string.st_effects), style = Type.Headline, modifier = Modifier.padding(14.dp))
            listOf(Effects.Lite to R.string.st_effects_lite, Effects.Auto to R.string.st_effects_auto,
                Effects.Full to R.string.st_effects_full).forEach { (mode, label) ->
                val active = AerialSettings.effects == mode
                MenuRow(if (active) Glyph.Check else Glyph.Info, stringResource(label), onClick = {
                    AerialSettings.chooseEffects(mode)
                    onDismiss()
                }, focusRequester = if (active) focus else null, trailing = if (active) "✓" else null)
            }
            BasicText(stringResource(R.string.st_effects_hint), style = Type.Caption.copy(color = Palette.SecondaryLabel),
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
        }
    }
    LaunchedEffect(Unit) { withFrameNanos { }; runCatching { focus.requestFocus() } }
}

/** The same persisted text-color preference, accessible without leaving Control Center. */
@Composable
private fun DesktopColorDialog(onDismiss: () -> Unit) {
    val first = remember { FocusRequester() }
    ControlPopup(onDismiss) {
        Column(Modifier.width(300.dp).consumeTaps().liquidGlass(20.dp, translucent = true).padding(16.dp)) {
            BasicText(stringResource(R.string.clock_text_color), style = Type.Headline, modifier = Modifier.padding(14.dp))
            listOf(R.string.clock_white, R.string.clock_dark_gray, R.string.clock_black).forEachIndexed { index, label ->
                MenuRow(if (index == AerialSettings.clockTextColor) Glyph.Check else Glyph.Info, stringResource(label), onClick = {
                    AerialSettings.chooseClockTextColor(index)
                    onDismiss()
                }, focusRequester = if (index == AerialSettings.clockTextColor) first else null,
                    trailing = if (index == AerialSettings.clockTextColor) "✓" else null)
            }
        }
    }
    LaunchedEffect(Unit) { withFrameNanos { }; runCatching { first.requestFocus() } }
}

@Composable
private fun ControlPopup(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().dismissOnTap(onDismiss), contentAlignment = Alignment.Center) { content() }
    }
}

/** Full-screen notice so the complete attribution stays readable from a TV. */
@Composable
private fun HaloNoticeDialog(onDismiss: () -> Unit) {
    val focus = remember { FocusRequester() }
    val ink = Palette.Label
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Row(Modifier.fillMaxSize().liquidGlass(0.dp, translucent = true)) {
            Column(Modifier.fillMaxHeight().width(275.dp).background(Color(0x33101B2A)).padding(38.dp)) {
                Image(painterResource(R.drawable.halo_tv_icon), null, Modifier.size(72.dp))
                Spacer(Modifier.height(46.dp))
                BasicText("Halo TV", style = Type.LargeTitle.copy(color = Color.White))
                Spacer(Modifier.height(16.dp))
                BasicText(stringResource(R.string.halo_notice_title), style = Type.Body.copy(color = Color(0xFFBFD1E4)))
                Spacer(Modifier.weight(1f))
                BasicText(stringResource(R.string.cc_about_attribution), style = Type.Caption.copy(color = Color(0xFFA4BAD1)))
            }
            BoxWithConstraints(Modifier.weight(1f).fillMaxHeight()) {
                val contentHeight = maxHeight
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).focusRequester(focus).focusable()) {
                    Column(Modifier.fillMaxWidth().heightIn(min = contentHeight).padding(horizontal = 34.dp, vertical = 28.dp),
                        verticalArrangement = Arrangement.SpaceEvenly) {
                        BasicText(stringResource(R.string.halo_notice_title), style = Type.LargeTitle.copy(color = ink))
                        listOf(R.string.halo_notice_body, R.string.halo_free_notice, R.string.bing_source_notice, R.string.aerial_source_about).forEach { id ->
                            Box(Modifier.fillMaxWidth().padding(vertical = 5.dp).clip(RoundedCornerShape(14.dp)).background(Palette.Fill).padding(horizontal = 20.dp, vertical = 20.dp)) {
                                BasicText(stringResource(id), style = Type.Body.copy(color = ink, fontSize = 17.sp, lineHeight = 25.sp))
                            }
                        }
                    }
                }
            }
        }
    }
    LaunchedEffect(Unit) { withFrameNanos { }; runCatching { focus.requestFocus() } }
}

/**
 * Download an advertised release, or receive a signed update over the local network.
 */
@Composable
private fun UpdatePage(vm: HomeViewModel, takeFocus: Boolean) {
    val context = LocalContext.current
    val first = remember { FocusRequester() }
    var manualOpen by remember { mutableStateOf(false) }
    val manualFocus = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    val pending = PhoneUpdate.ready
    LaunchedEffect(takeFocus) {
        if (!takeFocus) return@LaunchedEffect
        withFrameNanos { }
        runCatching { first.requestFocus() }
    }
    DisposableEffect(Unit) { onDispose { Updates.resetInstall() } }
    val canInstall = remember { Updates.canInstall(context) }
    val latest = Updates.available
    val state = Updates.install
    Column(Modifier.heightIn(max = 485.dp).verticalScroll(rememberScrollState()).padding(3.dp)) {
        MenuRow(
            Glyph.ChevronLeft, stringResource(R.string.halo_update_manage),
            onClick = { vm.overlay = Overlay.ControlCenter(CcPage.Main) },
        )
        MenuRow(Glyph.Globe, stringResource(
            if (Updates.checkState == Updates.Check.Checking) R.string.halo_update_checking else R.string.halo_update_check
        ), focusRequester = first, onClick = { Updates.checkIfStale(force = true) })
        if (canInstall) {
            MenuRow(Glyph.Download, stringResource(R.string.halo_auto_update),
                trailing = stringResource(if (Updates.autoDownload) R.string.st_on else R.string.st_off),
                onClick = { Updates.chooseAutoDownload(!Updates.autoDownload) })
            BasicText(stringResource(R.string.halo_auto_update_hint), style = Type.Caption,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp))
        }
        if (canInstall && latest != null) {
            MenuRow(
                Glyph.Download,
                when (state) {
                    is Updates.Install.Downloading -> stringResource(R.string.cc_update_downloading, (state.fraction * 100).toInt())
                    Updates.Install.Ready -> stringResource(R.string.halo_update_downloaded_install)
                    Updates.Install.Waiting -> stringResource(R.string.cc_update_waiting)
                    else -> stringResource(R.string.halo_update_online)
                },
                onClick = { Updates.install(context) },
            )
        }
        if (canInstall) {
            MenuRow(
                if (pending == null) Glyph.Cast else Glyph.Download,
                stringResource(if (pending == null) R.string.halo_update_manual else R.string.halo_update_phone_received),
                focusRequester = manualFocus, onClick = { manualOpen = true }, trailing = "›",
            )
        }
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            BasicText(stringResource(R.string.halo_installed_build, Updates.buildLabel), style = Type.Caption)
            if (latest != null) BasicText(stringResource(R.string.cc_update_available, latest), style = Type.Caption)
            val result = when (Updates.checkState) {
                Updates.Check.Success -> if (latest == null) R.string.halo_update_current else null
                Updates.Check.Failed -> R.string.halo_update_check_failed
                else -> null
            }
            result?.let { BasicText(stringResource(it), style = Type.Caption) }
            updateStatus(state)?.let { BasicText(stringResource(it), style = Type.Caption) }
        }
        Updates.notes.takeIf { it.isNotEmpty() }?.let { notes ->
            UpdateNotes(notes)
        }
    }
    if (takeFocus && manualOpen) {
        PhoneUpdateDialog(onDismiss = {
            manualOpen = false
            scope.launch {
                withFrameNanos { }
                runCatching { manualFocus.requestFocus() }
            }
        })
    }
}

private fun updateStatus(state: Updates.Install): Int? = when (state) {
    Updates.Install.Ready -> R.string.halo_auto_update_ready
    Updates.Install.Waiting -> R.string.cc_update_waiting
    Updates.Install.Opened -> R.string.cc_update_opened
    Updates.Install.NeedsPermission -> R.string.halo_update_install_permission
    Updates.Install.NeedsStoragePermission -> R.string.cc_update_storage_permission
    is Updates.Install.Failed -> when (state.reason) {
        Updates.Failure.Download -> R.string.cc_update_failed_download
        Updates.Failure.Blocked -> R.string.cc_update_failed_blocked
        Updates.Failure.NotOfficial -> R.string.cc_update_failed_signature
        Updates.Failure.NotNewer -> R.string.cc_update_failed_older
    }
    else -> null
}

@Composable
private fun PhoneUpdateDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val focus = remember { FocusRequester() }
    var address by remember { mutableStateOf<String?>(null) }
    var qr by remember { mutableStateOf<ImageBitmap?>(null) }
    var addressLoaded by remember { mutableStateOf(false) }
    val ready = PhoneUpdate.ready
    LaunchedEffect(ready == null) {
        if (ready != null) return@LaunchedEffect
        val url = withContext(Dispatchers.IO) { PhoneServer.start(context) }
        address = url
        qr = url?.let { withContext(Dispatchers.Default) { PhoneServer.qr(it).asImageBitmap() } }
        addressLoaded = true
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().dismissOnTap(onDismiss).padding(18.dp), contentAlignment = Alignment.Center) {
            Column(
                Modifier.width(560.dp).heightIn(max = 460.dp).consumeTaps().liquidGlass(24.dp, translucent = true)
                    .verticalScroll(rememberScrollState()).padding(22.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    GlyphIcon(Glyph.Cast, Palette.Label, 22.dp)
                    BasicText(stringResource(R.string.halo_update_manual), style = Type.LargeTitle)
                }
                Spacer(Modifier.height(16.dp))
                if (ready == null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            UpdateStep("1", stringResource(R.string.halo_update_step_connect))
                            UpdateStep("2", stringResource(R.string.halo_update_shared_qr))
                            UpdateStep("3", stringResource(R.string.halo_update_step_install))
                        }
                        Column(
                            Modifier.width(160.dp).smoothBackground(Palette.Fill, 16.dp).padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            qr?.let { image ->
                                Box(Modifier.size(128.dp).clip(RoundedCornerShape(10.dp))
                                    .background(Color.White).padding(13.dp)) {
                                    Image(image, null, Modifier.fillMaxSize(), filterQuality = FilterQuality.None)
                                }
                            } ?: BasicText(
                                stringResource(if (addressLoaded) R.string.halo_update_phone_offline else R.string.halo_update_qr_loading),
                                style = Type.Caption.copy(textAlign = TextAlign.Center),
                            )
                            address?.let {
                                Spacer(Modifier.height(8.dp))
                                BasicText(it, style = Type.Caption.copy(textAlign = TextAlign.Center))
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    BasicText(stringResource(R.string.halo_update_phone_tv_only), style = Type.Callout.copy(color = Palette.SecondaryLabel))
                } else {
                    BasicText(stringResource(R.string.halo_update_phone_confirm, Updates.current, ready.version),
                        style = Type.Headline)
                    Spacer(Modifier.height(8.dp))
                    BasicText(stringResource(R.string.halo_update_phone_tv_only), style = Type.Callout.copy(color = Palette.SecondaryLabel))
                    Spacer(Modifier.height(10.dp))
                    MenuRow(Glyph.Check, stringResource(R.string.halo_update_phone_install), focusRequester = focus, onClick = {
                        PhoneUpdate.confirm(context, ready)
                        if (PhoneUpdate.ready == null) onDismiss()
                    })
                    updateStatus(Updates.install)?.let { BasicText(stringResource(it), style = Type.Caption) }
                    MenuRow(Glyph.Trash, stringResource(R.string.halo_update_discard), onClick = {
                        PhoneUpdate.discard(ready)
                        onDismiss()
                    })
                }
                Spacer(Modifier.height(6.dp))
                MenuRow(Glyph.ChevronLeft, stringResource(R.string.halo_update_phone_back),
                    focusRequester = if (ready == null) focus else null, onClick = onDismiss)
            }
        }
        LaunchedEffect(ready) {
            withFrameNanos { }
            runCatching { focus.requestFocus() }
        }
    }
}

@Composable
private fun UpdateStep(number: String, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.size(22.dp).clip(CircleShape).background(Palette.Fill), contentAlignment = Alignment.Center) {
            BasicText(number, style = Type.Caption.copy(color = Palette.Label))
        }
        BasicText(text, style = Type.Body.copy(lineHeight = 20.sp), modifier = Modifier.weight(1f))
    }
}

/**
 * What the update brings, release by release. The lines are not controls: focused (Down from the
 * install button), Up and Down scroll it a few lines at a time, and past either end focus moves on
 * as from any row. The panel has room for about ten lines under the install button and above the
 * QR code; a bar on the right shows how much more there is.
 */
@Composable
private fun UpdateNotes(notes: List<ReleaseNotes>) {
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    var focused by remember { mutableStateOf(false) }
    val step = with(LocalDensity.current) { 64.dp.toPx() }
    val glow by animateFloatAsState(if (focused) 1f else 0f, tween(140, easing = Motion.EaseOut), label = "notes-focus")
    val scrollable = scroll.maxValue > 0
    Box(Modifier.fillMaxWidth()) {
        Box(Modifier.matchParentSize().graphicsLayer { alpha = glow }.smoothBackground(Palette.Fill, 12.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 164.dp)
                .onFocusChanged { focused = it.isFocused }
                .onKeyEvent { e ->
                    val down = when (e.nativeKeyEvent.keyCode) {
                        NativeKeyEvent.KEYCODE_DPAD_DOWN -> true
                        NativeKeyEvent.KEYCODE_DPAD_UP -> false
                        else -> return@onKeyEvent false
                    }
                    if (if (down) scroll.value >= scroll.maxValue else scroll.value <= 0) return@onKeyEvent false
                    if (e.type == KeyEventType.KeyDown) {
                        Sounds.step()
                        scope.launch { scroll.animateScrollBy(if (down) step else -step, spring(dampingRatio = 1f, stiffness = 500f)) }
                    }
                    true
                }
                .focusProperties { canFocus = scrollable }
                .focusable()
                .drawWithContent {
                    drawContent()
                    if (scroll.maxValue <= 0) return@drawWithContent
                    // The scroll bar, inside the right padding.
                    val track = size.height - 16.dp.toPx()
                    val thumb = (track * size.height / (size.height + scroll.maxValue)).coerceAtLeast(12.dp.toPx())
                    val top = 8.dp.toPx() + (track - thumb) * scroll.value / scroll.maxValue
                    drawRoundRect(
                        if (focused) Palette.SecondaryLabel else Palette.TertiaryLabel,
                        topLeft = Offset(size.width - 6.dp.toPx(), top),
                        size = Size(2.5.dp.toPx(), thumb),
                        cornerRadius = CornerRadius(1.25.dp.toPx()),
                    )
                }
                .verticalScroll(scroll)
                .padding(start = 14.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
        ) {
            notes.forEachIndexed { i, release ->
                if (i > 0) Spacer(Modifier.height(10.dp))
                BasicText(stringResource(R.string.cc_update_notes, release.version), style = Type.Callout.copy(color = Palette.Label))
                release.changes.forEach { line ->
                    Row(Modifier.padding(top = 4.dp)) {
                        BasicText("•", style = Type.Caption, modifier = Modifier.width(10.dp))
                        BasicText(line, style = Type.Caption, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

/**
 * For a TV where Aerial was installed from the APK alone (a USB stick, an app store): it opens, but
 * Home, power-on and wake still go to the stock launcher (issue #213). The settings pages that can
 * fix that on the TV itself, where the TV has them, and a QR code to the website's page with this
 * TV's install command, which does all of it.
 */
@Composable
private fun SetupPage(vm: HomeViewModel, takeFocus: Boolean, onOpen: (android.content.Intent) -> Unit) {
    val context = LocalContext.current
    val first = remember { FocusRequester() }
    LaunchedEffect(takeFocus) {
        if (!takeFocus) return@LaunchedEffect
        withFrameNanos { }
        runCatching { first.requestFocus() }
    }
    var health by remember { mutableStateOf(GuardHealth.read(context)) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) health = GuardHealth.read(context)
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val repairScope = rememberCoroutineScope()
    val guard = remember { SystemProbe.guardSettingsIntent(context) }
    val home = remember { SystemProbe.homeSettingsIntent(context) }
    val revival = remember { SystemProbe.revivalSettingsIntent(context) }
    var qr by remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(Unit) {
        val url = "https://github.com/Null9797/halo-tv/blob/main/INSTALL.md"
        qr = withContext(Dispatchers.Default) { PhoneServer.qr(url).asImageBitmap() }
    }
    Column(Modifier.heightIn(max = 470.dp).verticalScroll(rememberScrollState()).padding(3.dp)) {
        MenuRow(Glyph.ChevronLeft, stringResource(R.string.cc_setup_title), focusRequester = first, onClick = { vm.overlay = Overlay.ControlCenter(CcPage.Main) })
        BasicText(stringResource(R.string.cc_setup_body), style = Type.Caption, modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp))
        val status = when {
            health.running -> R.string.halo_guard_running
            health.enabled == true -> R.string.halo_guard_unbound
            health.enabled == null -> R.string.halo_guard_unknown
            else -> R.string.halo_guard_disabled
        }
        BasicText(stringResource(status), style = Type.Callout, modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp))
        BasicText(stringResource(if (health.canRestore) R.string.halo_guard_restore_allowed else R.string.halo_guard_restore_missing),
            style = Type.Caption, modifier = Modifier.padding(horizontal = 14.dp, vertical = 3.dp))
        if (health.canRestore && !health.running) MenuRow(Glyph.Shield, stringResource(R.string.halo_guard_repair), onClick = {
            vm.whenUnlocked {
                repairScope.launch {
                    runCatching { GuardSetup.ensureEnabled(context); GuardSetup.rebindIfStalled(context) }
                    delay(2200)
                    health = GuardHealth.read(context)
                }
            }
        })
        if (Build.VERSION.SDK_INT >= 33 && !health.running) MenuRow(Glyph.Info, stringResource(R.string.halo_guard_restricted), onClick = {
            vm.whenUnlocked { onOpen(android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                android.net.Uri.parse("package:${context.packageName}"))) }
        })
        guard?.let { MenuRow(Glyph.Shield, stringResource(R.string.cc_setup_guard), onClick = { vm.whenUnlocked { onOpen(it) } }, trailing = stringResource(R.string.cc_setup_guard_where) + " ›") }
        home?.let { MenuRow(Glyph.Check, stringResource(R.string.cc_setup_home), onClick = { vm.whenUnlocked { onOpen(it) } }, trailing = "›") }
        revival?.let { MenuRow(Glyph.Moon, stringResource(R.string.cc_setup_revival), onClick = { vm.whenUnlocked { onOpen(it) } }, trailing = stringResource(R.string.cc_setup_revival_where) + " ›") }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 14.dp)) {
            Box(Modifier.size(104.dp).clip(RoundedCornerShape(12.dp)).background(Color.White).padding(8.dp)) {
                qr?.let { Image(it, contentDescription = null, contentScale = ContentScale.Fit, filterQuality = FilterQuality.None, modifier = Modifier.fillMaxSize()) }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                BasicText(stringResource(R.string.cc_setup_scan), style = Type.Body)
                Spacer(Modifier.height(6.dp))
                BasicText(stringResource(R.string.cc_setup_scan_hint), style = Type.Caption)
            }
        }
    }
}

/** A round Control Center button: frosted at rest, white when focused. */
@Composable
private fun RoundButton(glyph: Glyph, label: String, focusRequester: FocusRequester? = null, onClick: () -> Unit) {
    Pressable(onClick = onClick, focusRequester = focusRequester, focusedScale = 1.06f) { state ->
        val bg by animateColorAsState(if (state.focused) Palette.Focus else Palette.Fill, tween(140, easing = Motion.EaseOut), label = "round-bg")
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(64.dp)) {
            Box(Modifier.size(48.dp).clip(CircleShape).drawBehind { drawRect(bg) }, contentAlignment = Alignment.Center) {
                GlyphIcon(glyph, if (state.focused) Palette.OnFocus else Palette.Label, 20.dp)
            }
            Spacer(Modifier.height(6.dp))
            BasicText(
                label, maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = Type.Caption.copy(color = if (state.focused) Palette.Label else Palette.SecondaryLabel, textAlign = TextAlign.Center),
            )
        }
    }
}

/**
 * A Control Center slider: focus it, then Left and Right change the level, OK does [onClick].
 * Shared by volume and brightness so both feel identical. On a touch screen a tap does [onClick]
 * and a sideways drag steps the level, [steps] steps across the width of the row.
 */
@Composable
private fun SliderRow(
    glyph: Glyph,
    level: Float,
    value: String,
    steps: Int,
    onStep: (Int) -> Unit,
    onClick: () -> Unit,
    focusRequester: FocusRequester? = null,
    modifier: Modifier = Modifier,
) {
    // Each step ticks, as focus does; a step past either end bumps instead. Read through state:
    // the drag handler below is made once and outlives this composition's values.
    val atLevel by rememberUpdatedState(level)
    val doStep by rememberUpdatedState(onStep)
    val step = { by: Int ->
        if ((by > 0 && atLevel >= 0.999f) || (by < 0 && atLevel <= 0.001f)) Sounds.limit() else Sounds.step()
        doStep(by)
    }
    val stepCount by rememberUpdatedState(steps.coerceAtLeast(1))
    Pressable(
        onClick = onClick,
        focusedScale = 1.03f,
        focusRequester = focusRequester,
        modifier = modifier.fillMaxWidth(),
        onKey = { e ->
            val code = e.nativeKeyEvent.keyCode
            if (code != NativeKeyEvent.KEYCODE_DPAD_LEFT && code != NativeKeyEvent.KEYCODE_DPAD_RIGHT) return@Pressable false
            if (e.type == KeyEventType.KeyDown) step(if (code == NativeKeyEvent.KEYCODE_DPAD_RIGHT) 1 else -1)
            true
        },
    ) { state ->
        val bg by animateColorAsState(if (state.focused) Palette.Focus else Palette.Fill, tween(140, easing = Motion.EaseOut), label = "slider-bg")
        val fg = if (state.focused) Palette.OnFocus else Palette.Label
        val fraction by animateFloatAsState(level, spring(dampingRatio = 1f, stiffness = 700f), label = "slider-level")
        Row(
            Modifier
                .fillMaxWidth()
                .pointerInput(Unit) {
                    // The level only moves in the steps the system takes, so the drag is counted
                    // out in steps rather than set to an exact point.
                    var travelled = 0f
                    detectHorizontalDragGestures(onDragStart = { travelled = 0f }) { change, dx ->
                        change.consume()
                        travelled += dx
                        val pitch = size.width.toFloat() / stepCount
                        while (travelled >= pitch) { step(1); travelled -= pitch }
                        while (travelled <= -pitch) { step(-1); travelled += pitch }
                    }
                }
                .smoothBackground({ bg }, 14.dp).height(40.dp).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            GlyphIcon(glyph, fg, 18.dp, level = level)
            Canvas(Modifier.weight(1f).height(6.dp)) {
                val r = CornerRadius(size.height / 2)
                drawRoundRect(fg.copy(alpha = 0.2f), cornerRadius = r)
                drawRoundRect(fg, size = Size(size.width * fraction, size.height), cornerRadius = r)
            }
            BasicText(
                value,
                style = Type.Headline.copy(color = fg, textAlign = TextAlign.End),
                modifier = Modifier.widthIn(min = 28.dp),
            )
        }
    }
}

@Composable
private fun VolumeRow(vm: HomeViewModel, focusRequester: FocusRequester, modifier: Modifier = Modifier) {
    val volume = vm.volume
    SliderRow(
        glyph = if (volume.muted) Glyph.SpeakerMuted else Glyph.Speaker,
        level = if (volume.muted) 0f else volume.fraction,
        value = if (volume.muted) stringResource(R.string.cc_muted) else volume.level.toString(),
        steps = volume.max,
        onStep = vm::adjustVolume,
        onClick = vm::toggleMute,
        focusRequester = focusRequester,
        modifier = modifier,
    )
}

/** Reads the level every 700 ms while the brightness row is on screen. Neither the DLP light
 * engine nor the picture service announces a change, and the TV's own slider can be moved with the
 * panel still up, so nothing else would refresh either.
 *
 * Only the on-screen copy polls: the closed panel draws this page once at 1 %, so that its first
 * opening is not its first draw (ControlCenter's warm path), and a LaunchedEffect runs from there
 * too — that copy would read the picture service for a panel nobody can see.
 */
@Composable
private fun PollBrightness(vm: HomeViewModel, laser: Boolean, enabled: Boolean) {
    val context = LocalContext.current
    LaunchedEffect(context, laser, enabled) {
        if (!enabled) return@LaunchedEffect
        val xgimi = withContext(Dispatchers.Default) { SystemProbe.xgimiBrightnessLevel() != null }
        if (!xgimi && !laser) return@LaunchedEffect
        // XGIMI's Boost is discovered once, before the first poll; it has no saying on a laser set.
        if (xgimi) vm.discoverBrightnessBoost()
        while (isActive) {
            delay(700)
            vm.refreshBrightness()
        }
    }
}

/**
 * Brightness where Aerial may change it; otherwise a row that opens the TV's picture settings, and
 * says so: a bare "Brightness" with no slider read as broken (Vidda 85V1F-S, FFalcon, issue #130).
 */
@Composable
private fun BrightnessRow(vm: HomeViewModel, focusRequester: FocusRequester, laser: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    // XGIMI (GMUI): the real brightness is the DLP light-engine level. It reads through the HAL
    // class (SystemProbe.xgimiBrightnessLevel) and, on this firmware, writes through it too, so the
    // row is a slider of the light engine's own levels (1..10) rather than a percentage of the
    // system setting that moves nothing. Poll the HAL while the row is on screen: the level can be
    // changed from the TV's own overlay, which does not pause Aerial, so no onResume or
    // screen_brightness observer ever fires for it.
    var xgimi by remember { mutableStateOf(false) }
    LaunchedEffect(context) { xgimi = withContext(Dispatchers.Default) { SystemProbe.xgimiBrightnessLevel() != null } }
    val level = vm.brightness
    if (level == null) {
        ControlMenuRow(
            Glyph.Sun, stringResource(R.string.cc_brightness_in_settings),
            onClick = {
                vm.overlay = Overlay.None
                SystemProbe.openPictureSettings(context)
            },
            trailing = "›",
            modifier = modifier,
            focusRequester = focusRequester,
        )
    } else {
        SliderRow(
            glyph = Glyph.Sun,
            level = level,
            // A laser set's level is 11 notches of its own, not a percentage of anything: show the
            // notch, the number the TV's own slider shows (4, not 40).
            value = when {
                vm.brightnessBoost == true -> "10+"
                laser -> Math.round(level * HisenseLaserPanel.LEVEL_MAX).toString()
                else -> Math.round(level * 100).toString()
            },
            // Brightness.adjust moves 5% a step, and stops at 1%; XGIMI steps its own levels and a
            // Hisense laser set its own 11 notches. Only the drag pitch follows this; a key is one
            // step either way.
            steps = when {
                laser -> HisenseLaserPanel.LEVEL_MAX
                xgimi -> SystemProbe.xgimiBrightnessMax
                else -> 20
            },
            onStep = vm::adjustBrightness,
            onClick = {},
            focusRequester = focusRequester,
            modifier = modifier,
        )
    }
}

@Composable
private fun InputsPage(vm: HomeViewModel, takeFocus: Boolean, onOpen: (android.content.Intent) -> Unit) {
    val context = LocalContext.current
    val first = remember { FocusRequester() }
    val back = remember { FocusRequester() }
    LaunchedEffect(takeFocus) {
        if (!takeFocus) return@LaunchedEffect
        withFrameNanos { }
        if (!runCatching { first.requestFocus() }.getOrDefault(false)) back.requestFocus()
        while (isActive) {
            vm.refreshSources()
            delay(if (vm.sources.any { it.kind != SourceKind.Usb }) 12_000L else 3_000L)
        }
    }
    Column(Modifier.heightIn(max = 470.dp).verticalScroll(rememberScrollState())) {
        MenuRow(Glyph.ChevronLeft, stringResource(R.string.cc_inputs), focusRequester = back,
            onClick = { vm.overlay = Overlay.ControlCenter(CcPage.Main) })
        Spacer(Modifier.height(8.dp))
        val shown = vm.shownSources
        if (vm.sources.none { it.kind != SourceKind.Usb }) {
            MenuRow(Glyph.Input, stringResource(R.string.cc_native_inputs), onClick = {
                if (SystemProbe.openInputPicker(context)) vm.overlay = Overlay.None
                else Toast.makeText(context, R.string.cc_no_inputs, Toast.LENGTH_SHORT).show()
            })
        }
        val connected = stringResource(R.string.input_connected)
        shown.forEachIndexed { index, source ->
            key(source.id) {
                MenuRow(
                    glyph = when (source.kind) {
                        SourceKind.Hdmi, SourceKind.DisplayPort -> Glyph.Hdmi
                        SourceKind.Tuner -> Glyph.Antenna
                        SourceKind.Av -> Glyph.Av
                        SourceKind.Usb -> Glyph.Usb
                        SourceKind.Other -> Glyph.Input
                    },
                    text = source.label,
                    // What the owner named the input on its tile, else what HDMI-CEC reports.
                    trailing = SourceLooks.of(source.id)?.let { it.name ?: it.device?.let { d -> stringResource(d.label) } }
                        ?: source.deviceName ?: if (source.connected) connected else null,
                    focusRequester = first.takeIf { index == 0 },
                    onClick = { onOpen(source.intent) },
                )
            }
        }
        // Switching is this page's one job; what Aerial does with the inputs is a page further in.
        if (vm.sources.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            MenuRow(
                Glyph.Gear, stringResource(R.string.cc_inputs_settings),
                onClick = { vm.overlay = Overlay.ControlCenter(CcPage.InputSettings) },
                // With every input taken out of the list, the way back in is the first row.
                focusRequester = if (shown.isEmpty()) first else null,
            )
        }
    }
}

/**
 * What Aerial does with the inputs: which ones are tiles on Home (any number) and which one the
 * TV starts on (one, or none). Kept apart from the list that switches inputs.
 */
@Composable
private fun InputSettingsPage(vm: HomeViewModel, takeFocus: Boolean) {
    val first = remember { FocusRequester() }
    LaunchedEffect(takeFocus) {
        if (!takeFocus) return@LaunchedEffect
        withFrameNanos { }
        runCatching { first.requestFocus() }
    }
    fun name(source: app.orchard.tv.system.TvSource) = source.deviceName?.let { "${source.label} · $it" } ?: source.label
    Column(Modifier.heightIn(max = 470.dp).verticalScroll(rememberScrollState())) {
        MenuRow(Glyph.ChevronLeft, stringResource(R.string.cc_inputs_settings), onClick = { vm.overlay = Overlay.ControlCenter(CcPage.Inputs) })

        val shown = vm.shownSources
        // Which inputs the list has: firmwares list ports the TV does not have (issue #279).
        Spacer(Modifier.height(10.dp))
        BasicText(stringResource(R.string.cc_inputs_list), style = Type.Callout, modifier = Modifier.padding(start = 14.dp, bottom = 6.dp))
        vm.sources.forEachIndexed { i, source ->
            val listed = source.id !in AerialSettings.hiddenSources
            ChoiceRow(
                text = name(source),
                selected = listed,
                focusRequester = if (i == 0) first else null,
                onClick = { vm.setSourceHidden(source, listed) },
            )
        }
        BasicText(
            stringResource(R.string.cc_inputs_list_hint),
            style = Type.Caption,
            modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 8.dp),
        )

        Spacer(Modifier.height(18.dp))
        BasicText(stringResource(R.string.cc_inputs_pin), style = Type.Callout, modifier = Modifier.padding(start = 14.dp, bottom = 6.dp))
        shown.forEach { source ->
            val pinned = source.id in AerialSettings.pinnedSources
            ChoiceRow(
                text = name(source),
                selected = pinned,
                focusRequester = null,
                onClick = { vm.setSourcePinned(source, !pinned) },
            )
        }
        BasicText(
            stringResource(R.string.cc_inputs_pin_hint),
            style = Type.Caption,
            modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 8.dp),
        )

        Spacer(Modifier.height(18.dp))
        BasicText(stringResource(R.string.cc_inputs_boot), style = Type.Callout, modifier = Modifier.padding(start = 14.dp, bottom = 6.dp))
        ChoiceRow(
            text = stringResource(R.string.cc_inputs_boot_none),
            selected = !AerialSettings.hasBootTarget,
            focusRequester = null,
            onClick = { vm.chooseBootTarget() },
        )
        // An app is chosen from its tile's menu; here it only shows, and "nothing" clears it.
        AerialSettings.bootApp?.let { pkg ->
            ChoiceRow(
                text = vm.entry(pkg)?.label ?: pkg,
                selected = true,
                focusRequester = null,
                onClick = {},
            )
        }
        shown.forEach { source ->
            ChoiceRow(
                text = name(source),
                selected = source.id == AerialSettings.bootSource,
                focusRequester = null,
                onClick = { vm.chooseBootTarget(sourceId = source.id) },
            )
        }
        BasicText(
            stringResource(R.string.cc_inputs_boot_hint),
            style = Type.Caption,
            modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 8.dp),
        )

        // A device switching itself on over HDMI-CEC takes the TV (issue #1020); off unless chosen.
        Spacer(Modifier.height(18.dp))
        BasicText(stringResource(R.string.cc_inputs_cec), style = Type.Callout, modifier = Modifier.padding(start = 14.dp, bottom = 6.dp))
        ChoiceRow(stringResource(R.string.st_on), AerialSettings.cecSwitch, null) { AerialSettings.chooseCecSwitch(true) }
        ChoiceRow(stringResource(R.string.st_off), !AerialSettings.cecSwitch, null) { AerialSettings.chooseCecSwitch(false) }
        BasicText(
            stringResource(R.string.cc_inputs_cec_hint),
            style = Type.Caption,
            modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 8.dp),
        )
    }
}

/** A row in a list of choices: the chosen one carries a check, as in tvOS settings. */
@Composable
private fun ChoiceRow(text: String, selected: Boolean, focusRequester: FocusRequester?, onClick: () -> Unit) {
    Pressable(onClick = onClick, focusRequester = focusRequester, focusedScale = 1.03f, modifier = Modifier.fillMaxWidth()) { state ->
        val bg by animateColorAsState(if (state.focused) Palette.Focus else Color.Transparent, tween(140, easing = Motion.EaseOut), label = "choice-bg")
        val fg = if (state.focused) Palette.OnFocus else Palette.Label
        Row(
            Modifier.fillMaxWidth().smoothBackground({ bg }, 12.dp).height(44.dp).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicText(text, style = Type.Body.copy(color = fg), modifier = Modifier.weight(1f))
            if (selected) GlyphIcon(Glyph.Check, fg, 16.dp)
        }
    }
}
