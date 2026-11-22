package app.orchard.tv.ui.home

/** Failed/black copies are not proof that a film is permanently unreadable. */
internal class SurfaceCopyHealth {
    private var misses = 0
    private var retryAt = 0L
    var readable: Boolean? = null; private set

    fun canTry(now: Long): Boolean = now >= retryAt

    fun result(good: Boolean, now: Long) {
        if (good) { misses = 0; retryAt = 0; readable = true }
        else if (++misses >= 3) {
            readable = false
            retryAt = now + 30_000L
            misses = 0
        }
    }
}
