package app.orchard.tv.guard

import android.os.SystemClock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26])
class StockVisitTest {
    @Test fun visitSurvivesPreferenceReadAndCanBeCleared() {
        val app = RuntimeEnvironment.getApplication()
        GuardSetup.noteStockVisit(app, SystemClock.elapsedRealtime() + 60_000)
        assertTrue(GuardSetup.stockVisitLeftMs(app) > 0)
        GuardSetup.noteStockVisit(app, null)
        assertEquals(0L, GuardSetup.stockVisitLeftMs(app))
    }

    @Test fun expiredVisitDoesNotResume() {
        val app = RuntimeEnvironment.getApplication()
        GuardSetup.noteStockVisit(app, SystemClock.elapsedRealtime() - 1)
        assertEquals(0L, GuardSetup.stockVisitLeftMs(app))
    }
}
