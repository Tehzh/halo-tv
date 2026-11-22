package app.orchard.tv.system

import android.os.Handler

/**
 * Actual repeat-down events for a volume press already consumed by a window or key filter.
 * Some firmwares change the level before every event; others do so only for the first down.
 * Give late firmware changes time to arrive, then replace only an otherwise lost step. There
 * is no repeating timer here: a missing key-up cannot manufacture more volume changes.
 */
internal class ConsumedVolumeRepeats(
    private val handler: Handler,
    private val read: () -> Pair<Int, Boolean>?,
    private val step: (Int) -> Unit,
) {
    private var observed: Pair<Int, Boolean>? = null
    private var pending = false
    private var from: Pair<Int, Boolean>? = null
    private var direction = 0
    private val settle = Runnable {
        if (!pending) return@Runnable
        pending = false
        val now = read()
        if (now != null && now == from) step(direction)
        observed = read()
    }

    fun begin() {
        flush()
        observed = read()
    }

    /** Also called after a guard-generated step, which is not a firmware response. */
    fun noteOwnStep() { observed = read() }

    fun repeat(direction: Int) {
        // Read before settling an earlier event. A firmware change arriving during its grace
        // period must not become the baseline of a second, artificial step for this event.
        val now = read()
        val canCompare = now != null && observed != null
        val firmwareMoved = now != null && observed != null && now != observed
        if (firmwareMoved) cancel() else flush()
        val baseline = read()
        if (canCompare && !firmwareMoved && baseline != null) {
            from = baseline
            this.direction = direction
            pending = true
            handler.postDelayed(settle, 300L)
        }
        observed = baseline
    }

    private fun flush() {
        handler.removeCallbacks(settle)
        if (pending) settle.run()
    }

    fun cancel() {
        handler.removeCallbacks(settle)
        pending = false
        observed = null
    }
}
