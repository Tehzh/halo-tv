package app.orchard.tv.lite

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class PhoneLanNameTest {
    @Test fun acceptsRecognizableLanNames() {
        listOf("eth0", "wlan0", "wifi0", "en0").forEach { assertTrue(it, PhoneServer.isPhysicalLanName(it)) }
    }
    @Test fun choosesPhysicalIpv4AndNeverFallsThroughToVirtualInterface() {
        fun ip(value: String) = java.net.InetAddress.getByName(value)
        assertTrue(PhoneServer.selectLanAddress(listOf("tun0" to ip("10.8.0.1"), "wlan0" to ip("192.168.1.20"))) == "192.168.1.20")
        assertTrue(PhoneServer.selectLanAddress(listOf("wlan0" to ip("169.254.1.1"), "tun0" to ip("10.8.0.1"), "ap0" to ip("192.168.43.1"))) == null)
        assertTrue(PhoneServer.selectLanAddress(listOf("wlan0" to ip("198.18.0.1"))) == null)
    }
    @Test fun addressChangeSelectsTheNewPhysicalIpv4() {
        val old = listOf("wlan0" to java.net.InetAddress.getByName("192.168.1.20"))
        val changed = listOf("wlan0" to java.net.InetAddress.getByName("192.168.1.21"))
        assertTrue(PhoneServer.selectLanAddress(old) == "192.168.1.20")
        assertTrue(PhoneServer.selectLanAddress(changed) == "192.168.1.21")
    }
    @Test fun rejectsKnownTunnelAndHotspotNames() {
        listOf("tun0", "ppp0", "lo", "ap0", "wifi-aware0", "p2p0").forEach { assertFalse(it, PhoneServer.isPhysicalLanName(it)) }
    }
}
