package app.orchard.tv.lite

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class CleanerProtectedComponentsTest {
    @Test fun preservesAllValidServicesAndIgnoresInvalidEntries() {
        assertEquals(setOf("a.one", "b.two"), LiteBackgroundCleaner.componentPackages("a.one/.Service:b.two/b.two.Listener::broken"))
        assertEquals(emptySet<String>(), LiteBackgroundCleaner.componentPackages(null))
    }
}
