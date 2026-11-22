package app.orchard.tv.guard

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.os.UserManager
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class BootReceiverRecoveryTest {


    @Test
    fun retryWaitsPastInitialRestoreGraceAndLeavesTimeForRebind() {
        val started = 100_000L
        val restored = 100_200L
        val now = 100_500L
        val delay = BootRecoveryTiming.retryDelay(started, restored, now)!!
        assertTrue(now + delay - restored >= 5_000L)
        assertTrue(now + delay - started + BootRecoveryTiming.REBIND_WAIT_MS <= BootRecoveryTiming.MAX_BROADCAST_MS)
        assertEquals(4_800L, delay)
    }

    @Test
    fun slowInitialRestoreDoesNotExtendTheReceiverBudget() {
        assertNull(BootRecoveryTiming.retryDelay(100_000L, 102_000L, 102_100L))
    }

    @Test
    fun elapsedGraceNeedsNoExtraSleepButLateBroadcastCannotRebind() {
        assertEquals(0L, BootRecoveryTiming.retryDelay(100_000L, 100_100L, 105_500L)!!)
        assertNull(BootRecoveryTiming.retryDelay(100_000L, 100_100L, 107_000L))
    }

    @Test
    fun inconsistentMonotonicSamplesCannotScheduleANegativeWait() {
        assertNull(BootRecoveryTiming.retryDelay(100_000L, 99_000L, 100_100L))
        assertNull(BootRecoveryTiming.retryDelay(100_000L, 100_100L, 100_000L))
    }
}
