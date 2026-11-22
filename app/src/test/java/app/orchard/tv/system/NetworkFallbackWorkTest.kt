package app.orchard.tv.system

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NetworkFallbackWorkTest {
    @Test fun activeDefaultDoesNotEnumerateFallback() {
        var calls = 0
        assertEquals("default", currentOrFallback("default") { calls++; "fallback" })
        assertEquals(0, calls)
    }
    @Test fun absentDefaultUsesFallbackExactlyOnce() {
        var calls = 0
        assertEquals("fallback", currentOrFallback<String>(null) { calls++; "fallback" })
        assertEquals(1, calls)
        assertNull(currentOrFallback<String>(null) { null })
    }
}
