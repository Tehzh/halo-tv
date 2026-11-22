package app.orchard.tv.system

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class JmgoMtkPortTest {
    private val prefix = "com.mediatek.tvinput/.hdmi.HDMIInputService/"
    @Test fun prefersTheFirmwareLabelWhenPresent() {
        assertEquals("2", SystemProbe.jmgoMtkPort(prefix + "HW5", "HDMI 2"))
    }
    @Test fun fallsBackToMtkHardwareMapping() {
        assertEquals("1", SystemProbe.jmgoMtkPort(prefix + "HW5", "Input"))
        assertEquals("4", SystemProbe.jmgoMtkPort(prefix + "HW8", "Input"))
        assertNull(SystemProbe.jmgoMtkPort(prefix + "HW9", "Input"))
    }
    @Test fun doesNotMapOtherServices() {
        assertNull(SystemProbe.jmgoMtkPort("other/.hdmi.HDMIInputService/HW5", "HDMI 2"))
    }
}
