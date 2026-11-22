package app.orchard.tv.lite

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/** A timeout does not imply the surrounding request made no changes. */
internal class MainThreadTimeout(val started: Boolean) : IllegalStateException("main thread timeout")

/** Cancels a task that has not started; never reports an executing task as cancelled. */
internal object MainThreadCall {
    private const val PENDING = 0
    private const val RUNNING = 1
    private const val FINISHED = 2
    private const val CANCELLED = 3

    fun <T> run(post: (Runnable) -> Boolean, timeoutMs: Long, block: () -> T): T {
        val state = AtomicInteger(PENDING)
        val done = CountDownLatch(1)
        val result = AtomicReference<Result<T>?>(null)
        val task = Runnable {
            if (state.compareAndSet(PENDING, RUNNING)) {
                result.set(runCatching(block))
                state.set(FINISHED)
                done.countDown()
            }
        }
        if (!post(task)) {
            state.compareAndSet(PENDING, CANCELLED)
            throw MainThreadTimeout(started = false)
        }
        try {
            if (!done.await(timeoutMs, TimeUnit.MILLISECONDS)) {
                if (state.compareAndSet(PENDING, CANCELLED)) throw MainThreadTimeout(started = false)
                if (state.get() != FINISHED) throw MainThreadTimeout(started = true)
            }
        } catch (e: InterruptedException) {
            val cancelled = state.compareAndSet(PENDING, CANCELLED)
            Thread.currentThread().interrupt()
            throw MainThreadTimeout(started = !cancelled)
        }
        return result.get()!!.getOrThrow()
    }
}
