package app.orchard.tv.ui.home

/** Give transient decoder failures a bounded retry before abandoning a playlist entry. */
internal class VideoPlaybackRecovery {
    private var failures = 0

    fun frameRendered() { failures = 0 }

    /** True for the first two consecutive failures; false on the third. */
    fun retryCurrent(): Boolean {
        failures++
        if (failures < 3) return true
        failures = 0
        return false
    }
}
