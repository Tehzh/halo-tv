package app.orchard.tv.guard

/** Retry a due lock when a service reconnects or permission changes, without a window/launch storm. */
internal class KidsDelivery {
    data class Access(val binding: Int, val overlay: Boolean, val launch: Boolean)
    private var access: Access? = null
    private var next = 0L
    private var attempts = 0
    private var reminded = false
    private var requestedHome = false
    var state = "not_due"
        private set

    fun clear(state: String) {
        access = null
        next = 0L
        attempts = 0
        reminded = false
        requestedHome = false
        this.state = state
    }

    fun home() {
        access = null
        next = 0L
        attempts = 0
        state = "on_home"
    }

    fun suspended() {
        access = null
        next = 0L
        attempts = 0
        requestedHome = false
        state = "screen_off"
    }

    fun attempt(
        now: Long, current: Access, quiz: Boolean, visible: Boolean,
        overlay: () -> String, launch: () -> String, remind: () -> String,
    ) {
        if (visible) { state = "overlay_attached"; return }
        if (access != current) {
            access = current
            next = 0L
            attempts = 0
            requestedHome = false
        }
        if (now < next) return
        next = now + if (attempts < 2) 2_000L else 10_000L
        attempts = (attempts + 1).coerceAtMost(3)
        val layer = if (quiz) overlay() else "not_quiz"
        if (quizWindowAccepted(layer)) {
            if (layer.startsWith("pending:")) next = now + 1_000L
            state = layer
            return
        }
        val request = when {
            !current.launch -> "background_launch_unavailable"
            requestedHome -> "waiting_for_home"
            else -> { requestedHome = true; launch() }
        }
        val notification = if (!reminded && request != "activity_requested") {
            reminded = true
            remind()
        } else null
        state = "$layer/$request" + (notification?.let { "/$it" } ?: if (reminded) "/already_reminded" else "")
    }

    fun nextCheck(now: Long): Long = (next - now).coerceIn(1_000L, 10_000L)
}

internal fun quizWindowAccepted(route: String): Boolean =
    route.startsWith("attached:") || route.startsWith("pending:")

internal fun watchedSinceLastTick(previouslyWatching: Boolean, intervalMs: Long, longestTick: Long): Long =
    if (previouslyWatching && intervalMs in 1..longestTick) intervalMs else 0L
