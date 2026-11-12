package app.orchard.tv.ui.home

import org.junit.Assert.assertEquals
import org.junit.Test

class PendingCaptureTest {
    @Test fun cancelledCopyWaitsForRendererBeforeRecycling() {
        var recycled = 0
        val capture = PendingCapture { recycled++ }
        capture.discard()
        assertEquals(0, recycled)
        capture.complete()
        assertEquals(1, recycled)
        capture.discard(); capture.complete()
        assertEquals(1, recycled)
    }
    @Test fun failedCopyIsFreedOnceWhenItsConsumerLeaves() {
        var recycled = 0
        val capture = PendingCapture { recycled++ }
        capture.complete()
        assertEquals(0, recycled)
        capture.discard(); capture.discard()
        assertEquals(1, recycled)
    }
    @Test fun successfulCopyBelongsToItsConsumer() {
        var recycled = 0
        PendingCapture { recycled++ }.complete()
        assertEquals(0, recycled)
    }
}
