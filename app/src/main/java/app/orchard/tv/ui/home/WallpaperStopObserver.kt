package app.orchard.tv.ui.home

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner

/** Decoder release runs in ON_STOP itself, even when Compose can no longer draw another frame. */
internal class WallpaperStopObserver(
    private val release: () -> Boolean,
    private val changed: () -> Unit,
) : LifecycleEventObserver {
    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
        if (event == Lifecycle.Event.ON_STOP && release()) changed()
    }
}
