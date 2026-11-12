package app.orchard.tv.ui.home

/** Cancellation must not recycle a bitmap until the renderer has finished writing into it. */
internal class PendingCapture(private val recycle: () -> Unit) {
    private var finished = false
    private var discarded = false
    private var disposed = false

    @Synchronized fun complete() { finished = true; recycleIfUnused() }
    @Synchronized fun discard() { discarded = true; recycleIfUnused() }
    private fun recycleIfUnused() {
        if (finished && discarded && !disposed) { disposed = true; recycle() }
    }
}
