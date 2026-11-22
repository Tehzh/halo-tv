package app.orchard.tv.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalContext
import app.orchard.tv.HomeViewModel
import app.orchard.tv.Overlay
import app.orchard.tv.settings.AerialSettings
import app.orchard.tv.system.BackgroundCleaner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withContext

/** Input timestamps reset the timer without invalidating the Home grid's composition. */
@Composable
internal fun IdleCleanup(vm: HomeViewModel) {
    val context = LocalContext.current.applicationContext
    LaunchedEffect(vm, context) {
        snapshotFlow {
            if (AerialSettings.autoCleanIdle && vm.resumed && vm.overlay == Overlay.None && !vm.screensaverVisible)
                vm.lastInputAt else null
        }.collectLatest { input ->
            if (input != null) {
                delay(120_000)
                withContext(Dispatchers.IO) { BackgroundCleaner.clean(context, allowFallback = false) }
            }
        }
    }
}
