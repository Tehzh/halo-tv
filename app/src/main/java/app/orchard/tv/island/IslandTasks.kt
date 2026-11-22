package app.orchard.tv.island

import android.os.SystemClock
import java.io.FilterInputStream
import java.io.InputStream
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

internal enum class IslandTask { Photos, Video, Update }

internal data class IslandProgress(
    val task: IslandTask,
    val percent: Int? = null,
    val finishedAt: Long? = null,
) {
    fun remaining(now: Long): Long? = finishedAt?.let { (it + RESULT_MS - now).coerceAtLeast(0L) }

    companion object {
        const val RESULT_MS = 6_000L
    }
}

internal fun preferredTask(transfer: IslandProgress?, update: IslandProgress?): IslandProgress? = when {
    transfer?.finishedAt != null && update?.finishedAt != null ->
        if (transfer.finishedAt >= update.finishedAt) transfer else update
    transfer?.finishedAt != null -> transfer
    update?.finishedAt != null -> update
    else -> transfer ?: update
}

internal class IslandUpdateProgress {
    private var downloading = false
    private var latest: IslandProgress? = null

    fun onDownload(fraction: Float?): IslandProgress {
        downloading = true
        return IslandProgress(IslandTask.Update, fraction?.let { (it * 100).toInt().coerceIn(0, 100) })
            .also { latest = it }
    }

    fun onReady(now: Long): IslandProgress? {
        if (downloading) latest = IslandProgress(IslandTask.Update, finishedAt = now)
        downloading = false
        return latest
    }

    fun reset() {
        downloading = false
        latest = null
    }
}

/** An upload gets a handle only while Home is listening. Handles never survive their observer. */
internal class IslandTransferEvents(private val now: () -> Long) {
    private class Observer {
        val updates = MutableStateFlow<IslandProgress?>(null)
        var task: Transfer? = null
    }

    private var observer: Observer? = null

    fun observe() = flow {
        val current = Observer()
        synchronized(this@IslandTransferEvents) { observer = current }
        try {
            emitAll(current.updates)
        } finally {
            synchronized(this@IslandTransferEvents) {
                if (observer === current) observer = null
            }
        }
    }

    @Synchronized
    fun begin(task: IslandTask): Transfer? {
        val current = observer ?: return null
        return Transfer(this, task).also {
            current.task = it
            current.updates.value = IslandProgress(task)
        }
    }

    @Synchronized
    private fun progress(transfer: Transfer, percent: Int?) {
        val current = observer?.takeIf { it.task === transfer } ?: return
        current.updates.value = IslandProgress(transfer.task, percent?.coerceIn(0, 100))
    }

    @Synchronized
    private fun finish(transfer: Transfer, saved: Boolean) {
        val current = observer?.takeIf { it.task === transfer } ?: return
        current.task = null
        current.updates.value = if (saved) IslandProgress(transfer.task, finishedAt = now()) else null
    }

    class Transfer internal constructor(
        private val owner: IslandTransferEvents,
        internal val task: IslandTask,
    ) {
        fun finish(saved: Boolean) = owner.finish(this, saved)

        fun track(input: InputStream, length: Long): InputStream {
            if (length <= 0) return input
            return object : FilterInputStream(input) {
                private var received = 0L
                private var lastReport = Long.MIN_VALUE

                override fun read(): Int = input.read().also { if (it != -1) report(1) }

                override fun read(bytes: ByteArray, offset: Int, count: Int): Int =
                    input.read(bytes, offset, count).also { if (it > 0) report(it) }

                private fun report(count: Int) {
                    received += count
                    val now = owner.now()
                    if (lastReport == Long.MIN_VALUE || now - lastReport >= 300L) {
                        lastReport = now
                        owner.progress(this@Transfer, (received.coerceAtMost(length) * 100 / length).toInt())
                    }
                }
            }
        }
    }
}

internal val islandTransfers = IslandTransferEvents(SystemClock::elapsedRealtime)
