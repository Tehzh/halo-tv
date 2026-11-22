package app.orchard.tv.lite

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.*
import org.junit.Test

class MainThreadCallTest {
    @Test fun pendingTimedOutWorkNeverExecutesWhenQueueDrains() {
        var queued: Runnable? = null
        val effects = AtomicInteger()
        try {
            MainThreadCall.run(post = { queued = it; true }, timeoutMs = 1L) { effects.incrementAndGet() }
            fail("expected timeout")
        } catch (e: MainThreadTimeout) {
            assertFalse(e.started)
        }
        queued!!.run()
        assertEquals(0, effects.get())
    }

    @Test fun startedWorkIsReportedUnknownAndMayCompleteOnce() {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val effects = AtomicInteger()
        lateinit var thread: Thread
        try {
            MainThreadCall.run(post = { task ->
                thread = Thread(task).apply { start() }
                assertTrue(started.await(2, TimeUnit.SECONDS))
                true
            }, timeoutMs = 2L) {
                started.countDown()
                release.await(2, TimeUnit.SECONDS)
                effects.incrementAndGet()
            }
            fail("expected timeout")
        } catch (e: MainThreadTimeout) {
            assertTrue(e.started)
        } finally { release.countDown() }
        thread.join(2_000)
        assertEquals(1, effects.get())
    }

    @Test fun completedTaskCanReturnNull() {
        assertNull(MainThreadCall.run<String?>(post = { it.run(); true }, timeoutMs = 20L) { null })
    }

    @Test fun completedTaskReturnsValueAndRejectedPostDoesNotRun() {
        assertEquals(42, MainThreadCall.run(post = { it.run(); true }, timeoutMs = 20L) { 42 })
        var effect = false
        try {
            MainThreadCall.run(post = { false }, timeoutMs = 1L) { effect = true }
            fail("expected timeout")
        } catch (e: MainThreadTimeout) { assertFalse(e.started) }
        assertFalse(effect)
    }
}
