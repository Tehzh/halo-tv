package app.orchard.tv.lite

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GithubFeedbackTest {
    @Test fun personalFieldsAreRedactedAndSizeIsBounded() {
        val clean=GithubFeedback.sanitize("password=hello pin:1234 ssid=mywifi test@example.com aa:bb:cc:dd:ee:ff /storage/emulated/0/private.jpg https://x.test/secret fe80::1234:5678%wlan0 " + "a".repeat(200000))
        for(s in listOf("hello","1234","mywifi","test@example.com","aa:bb:cc:dd:ee:ff","private.jpg","x.test","fe80","wlan0"))assertFalse(s,clean.contains(s))
        assertTrue(clean.length<=80000)
    }
}
