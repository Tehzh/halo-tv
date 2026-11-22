package app.orchard.tv.announcements

import android.view.KeyEvent
import android.widget.Toast
import androidx.compose.foundation.focusable
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.orchard.tv.CcPage
import app.orchard.tv.HomeViewModel
import app.orchard.tv.Overlay
import app.orchard.tv.R
import app.orchard.tv.settings.Restricted
import app.orchard.tv.ui.components.liquidGlass
import app.orchard.tv.ui.home.MenuRow
import app.orchard.tv.ui.theme.Glyph
import app.orchard.tv.ui.theme.Type
import kotlinx.coroutines.delay

/** A callback-driven snapshot; no polling or work while the notice UI is absent. */
@Composable
internal fun announcementRevision(): Int {
    var revision by remember { mutableIntStateOf(0) }
    DisposableEffect(Unit) {
        val listener: () -> Unit = { revision++ }
        Announcements.observe(listener)
        onDispose { Announcements.removeObserver(listener) }
    }
    return revision
}

@Composable
fun AnnouncementEntry(vm: HomeViewModel) {
    announcementRevision()
    val context = LocalContext.current
    // Keep navigation stable; only the badge follows actionable unread revisions.
    val unread = Announcements.unread() != null
    Box(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        MenuRow(Glyph.Info, stringResource(R.string.halo_feedback_entry),
            modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.075f)),
            trailing = "›", rowHeight = 40.dp, onClick = {
                vm.whenUnlocked { vm.overlay = Overlay.Feedback }
            })
        if (unread) Box(Modifier.align(Alignment.TopEnd).padding(top = 6.dp, end = 7.dp)
            .size(6.dp).clip(androidx.compose.foundation.shape.CircleShape)
            .background(Color(0xFFFF453A)).testTag("announcement-unread-dot"))
    }
}

@Composable
fun AnnouncementHost(vm: HomeViewModel, windowFocused: Boolean) {
    val revision = announcementRevision()
    val overlay = vm.overlay
    LaunchedEffect(revision, vm.loaded, vm.resumed, vm.screensaverVisible, overlay, Restricted.active, windowFocused) {
        if (overlay is Overlay.Announcement) {
            if (!vm.resumed || vm.screensaverVisible || Restricted.active || !Announcements.contains(overlay.notice)) vm.overlay = Overlay.None
            return@LaunchedEffect
        }
        if (!vm.loaded || !vm.resumed || !windowFocused || vm.screensaverVisible || overlay != Overlay.None || Restricted.active || Announcements.popup() == null) return@LaunchedEffect
        // Let the returning desktop and first video frame settle before a new window takes focus.
        delay(1500)
        if (vm.resumed && windowFocused && !vm.screensaverVisible && vm.overlay == Overlay.None && !Restricted.active) {
            Announcements.popup()?.let { n -> Announcements.presented(n); vm.overlay = Overlay.Announcement(n) }
        }
    }
    val shown = overlay as? Overlay.Announcement ?: return
    if (!vm.resumed || vm.screensaverVisible || Restricted.active) return
    val n = shown.notice
    fun close() { vm.overlay = if (shown.fromControlCenter) Overlay.ControlCenter(CcPage.Main) else Overlay.None }
    fun later() { Announcements.later(n); close() }
    Dialog(onDismissRequest = ::later, properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.26f)), contentAlignment = Alignment.Center) {
            key(n.key) {
                AnnouncementReader(n, ::later, { Announcements.acknowledge(n); close() })
            }
        }
    }
}

/** An offline reader: remote notices are plain text, never an external-browser requirement. */
@Composable
internal fun AnnouncementReader(n: RemoteAnnouncement, onLater: () -> Unit, onAcknowledge: () -> Unit) {
    val bodyFocus = remember { FocusRequester() }
    val actionsFocus = remember { FocusRequester() }
    val scroll = rememberScrollState()
    var expanded by remember { mutableStateOf(false) }
    var bodyFocused by remember { mutableStateOf(false) }
    var viewport by remember { mutableIntStateOf(1) }
    // Track the complete navigation press so reaching the end on key-down cannot leak key-up.
    var heldKey by remember { mutableIntStateOf(KeyEvent.KEYCODE_UNKNOWN) }
    val percent = if (scroll.maxValue == 0) 100 else (scroll.value.toLong() * 100 / scroll.maxValue).toInt()
    Column(Modifier.width(if (expanded) 900.dp else 760.dp).height(if (expanded) 488.dp else 448.dp)
        .liquidGlass(24.dp, translucent = true).padding(22.dp)
        .onPreviewKeyEvent { event ->
            val e = event.nativeKeyEvent
            if (!bodyFocused && e.keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                if (e.action == KeyEvent.ACTION_DOWN) bodyFocus.requestFocus()
                true
            } else false
        }) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            BasicText(stringResource(R.string.halo_ann_entry), style = Type.Callout)
            BasicText(stringResource(R.string.halo_ann_progress, percent), style = Type.Callout,
                modifier = Modifier.testTag("announcement-progress"))
        }
        Spacer(Modifier.height(8.dp))
        BasicText(n.title, style = Type.LargeTitle, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(8.dp))
        BasicText(stringResource(R.string.halo_ann_read_hint), style = Type.Callout)
        Spacer(Modifier.height(10.dp))
        Column(Modifier.fillMaxWidth().weight(1f).testTag("announcement-body")
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .border(if (bodyFocused) 2.dp else 1.dp,
                Color.White.copy(alpha = if (bodyFocused) 0.85f else 0.18f), RoundedCornerShape(14.dp))
            .onSizeChanged { viewport = it.height }
            .focusRequester(bodyFocus).onFocusChanged { bodyFocused = it.isFocused }
            .onPreviewKeyEvent { event ->
                val e = event.nativeKeyEvent
                val direction = when (e.keyCode) {
                    KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_PAGE_DOWN -> 1
                    KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_PAGE_UP -> -1
                    else -> 0
                }
                if (e.action == KeyEvent.ACTION_UP && e.keyCode == heldKey) {
                    heldKey = KeyEvent.KEYCODE_UNKNOWN
                    true
                } else if (e.action == KeyEvent.ACTION_DOWN && direction != 0) {
                    heldKey = e.keyCode
                    val canScroll = if (direction > 0) scroll.value < scroll.maxValue else scroll.value > 0
                    if (canScroll) {
                        // Keep overlap for screenshots and avoid an animation/job queue on held keys.
                        scroll.dispatchRawDelta(direction * viewport * 0.75f)
                    } else if (direction > 0 && e.repeatCount == 0) {
                        actionsFocus.requestFocus()
                    }
                    true
                } else if (e.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    if (e.action == KeyEvent.ACTION_DOWN) { heldKey = e.keyCode; actionsFocus.requestFocus() }
                    true
                } else false
            }.focusable().verticalScroll(scroll).padding(14.dp)) {
            BasicText(n.body, style = Type.Body, modifier = Modifier.testTag("announcement-text"))
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f)) {
                MenuRow(Glyph.Info, stringResource(if (expanded) R.string.halo_ann_compact else R.string.halo_ann_fullscreen),
                    focusRequester = actionsFocus, onClick = { expanded = !expanded })
            }
            Box(Modifier.weight(1f)) { MenuRow(Glyph.Calendar, stringResource(R.string.halo_ann_later), onClick = onLater) }
            Box(Modifier.weight(1f)) { MenuRow(Glyph.Check, stringResource(R.string.halo_ann_ack), onClick = onAcknowledge) }
        }
    }
    LaunchedEffect(n.key, expanded) {
        withFrameNanos { }
        bodyFocus.requestFocus()
    }
}
