package app.orchard.tv.announcements

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AnnouncementFeedTest {
    private fun feed(extra: String = "") = """{"schema":1,"enabled":true,"announcements":[{"id":"migration","revision":1,"title":"Halo update","body":"Text only"$extra}]}"""
    private fun rejected(text: String) { assertTrue(runCatching { AnnouncementFeed.parse(text) }.isFailure) }

    @Test fun parsesTargetedPlainTextAndUtcDates() {
        val n = AnnouncementFeed.parse(feed(""", "kind":"important", "variants":["guard","lite"], "minVersion":"3.0", "maxVersion":"3.1", "minCode":1, "startsAt":"2026-11-11T00:00:00Z", "expiresAt":"2027-01-01T00:00:00Z"""" )).single()
        assertTrue(n.important); assertEquals("migration:1", n.key)
        assertEquals(setOf("guard", "lite"), n.variants)
        assertEquals(1794355200000L, n.startsAt)
        assertTrue(AnnouncementPolicy.applies(n, "guard", "3.0.1", 179L, n.startsAt))
        assertFalse(AnnouncementPolicy.applies(n, "standard", "3.0.1", 179L, n.startsAt))
    }
    @Test fun disabledFeedWithdrawsAllNotices() {
        assertTrue(AnnouncementFeed.parse("""{"schema":1,"enabled":false,"announcements":[]}""").isEmpty())
    }
    @Test fun rejectsOversizedMalformedOrUnsupportedFeed() {
        rejected("not json")
        rejected(feed().replace("\"schema\":1", "\"schema\":2"))
        rejected(" ".repeat(AnnouncementPolicy.MAX_BYTES + 1))
        rejected(feed().replace("Text only", "X".repeat(6001)))
    }
    @Test fun malformedTargetDoesNotBecomeAnAllUserNotice() {
        rejected(feed(""", "variants":["unknown"]"""))
        rejected(feed(""", "variants":"guard""""))
        rejected(feed(""", "minVersion":"bad""""))
        rejected(feed(""", "minVersion":"4.0", "maxVersion":"3.0""""))
    }
    @Test fun requiresUniqueRevisionKeysAndValidDates() {
        rejected(feed(""", "startsAt":"2026-02-30T00:00:00Z""""))
        rejected(feed(""", "startsAt":"2027-01-01T00:00:00Z", "expiresAt":"2026-01-01T00:00:00Z""""))
        val one = feed().substringAfter("[").substringBeforeLast("]")
        rejected("""{"schema":1,"enabled":true,"announcements":[$one,$one]}""")
    }
    @Test fun untrustedDetailsAreNotClickable() {
        assertNull(AnnouncementFeed.parse(feed(""", "detailsUrl":"https://evil.example/update.apk"""" )).single().detailsUrl)
        assertEquals("https://halo-tv.pages.dev/migration", AnnouncementFeed.parse(feed(""", "detailsUrl":"https://halo-tv.pages.dev/migration"""" )).single().detailsUrl)
    }
}
