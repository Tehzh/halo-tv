package app.orchard.tv.feedback

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.orchard.tv.HomeViewModel
import app.orchard.tv.Overlay
import app.orchard.tv.announcements.Announcements
import app.orchard.tv.ui.components.liquidGlass
import app.orchard.tv.wallpaper.PhoneServer

@Composable
fun FeedbackHost(vm: HomeViewModel) {
    if (vm.overlay != Overlay.Feedback || !vm.resumed || vm.screensaverVisible) return
    val context = LocalContext.current
    val density = LocalDensity.current.density
    val back = { vm.overlay = Overlay.ControlCenter() }
    Dialog(onDismissRequest = back, properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)) {
        AndroidView(modifier = Modifier.width(810.dp).height(460.dp).liquidGlass(24.dp, translucent = true), factory = {
            FeedbackView(it, density,
                phone = { PhoneServer.start(context) }, qr = { PhoneServer.qr(it, 4) },
                notices = {
                    val notice = Announcements.unread()
                    if (notice != null) { Announcements.presented(notice); vm.overlay = Overlay.Announcement(notice, true) }
                    else { Announcements.check(force = true); android.widget.Toast.makeText(context, app.orchard.tv.R.string.halo_ann_none, android.widget.Toast.LENGTH_SHORT).show() }
                }, close = back)
        })
    }
}
