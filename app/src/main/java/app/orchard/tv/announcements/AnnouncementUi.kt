package app.orchard.tv.announcements

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.scrollBy
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalDensity
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
import kotlinx.coroutines.launch

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
    // Read time-based eligibility when the panel is composed, including on every reopening.
    // A read notice remains available until withdrawn/expired; an empty feed has no entry.
    if (Announcements.latest() == null) return
    val unread = Announcements.unread()
    Spacer(Modifier.height(5.dp))
    MenuRow(Glyph.Info, stringResource(if (unread != null) R.string.halo_ann_new else R.string.halo_ann_entry),
        modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.075f)), onClick = {
        val notice = Announcements.unread() ?: Announcements.latest()
        if (notice == null) {
            Announcements.check(force = true)
            Toast.makeText(context, R.string.halo_ann_none, Toast.LENGTH_SHORT).show()
        } else {
            Announcements.presented(notice)
            vm.overlay = Overlay.Announcement(notice, true)
        }
    })
}

@Composable
fun AnnouncementHost(vm: HomeViewModel, onOpen: (Intent) -> Unit, windowFocused: Boolean) {
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
    val first = remember(n.key) { FocusRequester() }
    val bodyScroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val step = with(LocalDensity.current) { 88.dp.toPx() }
    Dialog(onDismissRequest = ::later, properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.26f)), contentAlignment = Alignment.Center) {
            Column(Modifier.width(620.dp).heightIn(max = 464.dp).liquidGlass(24.dp, translucent = true).padding(24.dp)) {
                BasicText(stringResource(R.string.halo_ann_entry), style = Type.Callout)
                Spacer(Modifier.height(10.dp))
                BasicText(n.title, style = Type.LargeTitle, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(14.dp))
                Column(Modifier.weight(1f, fill = false).heightIn(max = 210.dp)
                    .verticalScroll(bodyScroll).onKeyEvent { event ->
                        val e = event.nativeKeyEvent
                        val amount = when (e.keyCode) {
                            android.view.KeyEvent.KEYCODE_DPAD_DOWN -> step
                            android.view.KeyEvent.KEYCODE_DPAD_UP -> -step
                            else -> return@onKeyEvent false
                        }
                        val canScroll = if (amount > 0) bodyScroll.value < bodyScroll.maxValue else bodyScroll.value > 0
                        if (canScroll && e.action == android.view.KeyEvent.ACTION_DOWN) scope.launch { bodyScroll.scrollBy(amount) }
                        canScroll
                    }.focusable().padding(4.dp)) {
                    BasicText(n.body, style = Type.Body)
                }
                Spacer(Modifier.height(12.dp))
                n.detailsUrl?.let { url ->
                    MenuRow(Glyph.Info, stringResource(R.string.halo_ann_details), onClick = {
                        onOpen(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    })
                    Spacer(Modifier.height(6.dp))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f)) { MenuRow(Glyph.Calendar, stringResource(R.string.halo_ann_later), onClick = ::later) }
                    Box(Modifier.weight(1f)) { MenuRow(Glyph.Check, stringResource(R.string.halo_ann_stop), onClick = { Announcements.acknowledge(n); close() }) }
                }
                Spacer(Modifier.height(6.dp))
                MenuRow(Glyph.Check, stringResource(R.string.halo_ann_ack), focusRequester = first, onClick = { Announcements.acknowledge(n); close() })
            }
        }
    }
    LaunchedEffect(n.key) { delay(100); runCatching { first.requestFocus() } }
}
