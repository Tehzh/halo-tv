package app.orchard.tv.wallpaper

import org.junit.Assert.*
import org.junit.Test

class OnlineWallpaperRequestsTest {
    @Test fun lastExplicitIntentWinsRegardlessOfDownloadCompletionOrder() {
        val gate = OnlineWallpaperRequests()
        val a = gate.begin("online", 1)!!
        val b = gate.begin("online", 1)!!
        assertFalse(gate.accepts(a, "online", 1))
        assertTrue(gate.accepts(b, "online", 1))
        gate.finish(a)
        assertTrue(gate.accepts(b, "online", 1))
    }
    @Test fun changingTypeAndReturningToSameIdRejectsOldDownload() {
        val gate = OnlineWallpaperRequests()
        val request = gate.begin("online", 1)!!
        assertFalse(gate.accepts(request, "video", 2))
        assertFalse(gate.accepts(request, "online", 3))
    }
    @Test fun finishedOrCancelledIntentCannotCommitLater() {
        val gate = OnlineWallpaperRequests()
        val request = gate.begin("online", 1)!!
        gate.finish(request)
        assertFalse(gate.accepts(request, "online", 1))
    }
    @Test fun pendingManualChoiceDefersRotationButFailureDoesNotDisableIt() {
        val gate = OnlineWallpaperRequests()
        val manual = gate.begin("online", 1)!!
        assertNull(gate.begin("online", 1, explicit = false))
        gate.finish(manual)
        val rotation = gate.begin("online", 1, explicit = false)!!
        assertTrue(gate.accepts(rotation, "online", 1))
    }
    @Test fun laterManualChoiceInvalidatesAnAlreadyRunningRotation() {
        val gate = OnlineWallpaperRequests()
        val rotation = gate.begin("online", 1, explicit = false)!!
        val manual = gate.begin("online", 1)!!
        gate.finish(rotation)
        assertFalse(gate.accepts(rotation, "online", 1))
        assertTrue(gate.accepts(manual, "online", 1))
        assertNull(gate.begin("online", 1, explicit = false))
    }
}
