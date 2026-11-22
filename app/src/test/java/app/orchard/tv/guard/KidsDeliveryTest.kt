package app.orchard.tv.guard

import org.junit.Assert.*
import org.junit.Test

class KidsDeliveryTest {
    private val missing = KidsDelivery.Access(0, false, false)
    private val guard = KidsDelivery.Access(1, false, true)

    @Test fun blockedRouteRemindsOnceAndHomeKeepsTheLock() {
        val route = KidsDelivery()
        var notices = 0
        fun run(at: Long) = route.attempt(at, missing, true, false,
            { "application_overlay_not_granted" }, { error("unauthorized launch") }, { notices++; "toast_requested" })
        run(0)
        assertTrue(route.state.endsWith("toast_requested"))
        run(1_000)
        run(2_000)
        route.home()
        assertEquals("on_home", route.state)
        run(30_000)
        assertEquals(1, notices)
        route.clear("not_due")
        run(60_000)
        assertEquals(2, notices)
    }

    @Test fun pendingAccessibilityWindowDoesNotLaunchOrRemindWhileAttaching() {
        val route = KidsDelivery()
        route.attempt(0, guard, true, false, { "pending:accessibility" },
            { error("window is already being attached") }, { error("duplicate reminder") })
        assertEquals("pending:accessibility", route.state)
        route.attempt(100, guard, true, true, { error("window already attached") },
            { error("window already attached") }, { error("duplicate reminder") })
        assertEquals("overlay_attached", route.state)
    }

    @Test fun failedAttachmentRequestsHomeOnceThenRemindsAndCanRecoverOnRebind() {
        val route = KidsDelivery()
        var launches = 0
        var notices = 0
        fun run(at: Long, access: KidsDelivery.Access = guard, status: String = "accessibility_overlay_attach_timeout") =
            route.attempt(at, access, true, false, { status }, { launches++; "activity_requested" },
                { notices++; "notification_posted" })
        run(0, status = "pending:accessibility")
        run(1_000)
        assertEquals(1, launches)
        assertEquals(0, notices)
        run(3_000)
        assertEquals(1, launches)
        assertEquals(1, notices)
        run(3_001, guard.copy(binding = 2), "attached:accessibility")
        assertEquals("attached:accessibility", route.state)
    }

    @Test fun countsThePreviousForegroundIntervalWithoutChargingUnknownProcessSleep() {
        assertEquals(2_000L, watchedSinceLastTick(true, 2_000L, 20_000L))
        assertEquals(0L, watchedSinceLastTick(false, 2_000L, 20_000L))
        assertEquals(0L, watchedSinceLastTick(true, 90_000L, 20_000L))
    }
}
