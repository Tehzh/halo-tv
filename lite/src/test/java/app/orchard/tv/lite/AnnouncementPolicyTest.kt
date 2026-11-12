package app.orchard.tv.lite

import org.junit.Assert.*
import org.junit.Test

class AnnouncementPolicyTest {
    @Test fun numericVersionComparisonDoesNotOrderTenBeforeTwo() {
        assertTrue(AnnouncementPolicy.compare("3.10", "3.2") > 0)
        assertEquals(0, AnnouncementPolicy.compare("3.0.1", "3.0.1.0"))
    }
    @Test fun targetVersionBuildAndVariantMustAllMatch() {
        val n = RemoteAnnouncement("n", 1, "t", "b", variants = setOf("lite"), minVersion = "3.0", maxVersion = "3.0.1", minCode = 30, maxCode = 40)
        assertTrue(AnnouncementPolicy.applies(n, "lite", "3.0.1", 37, 0))
        assertFalse(AnnouncementPolicy.applies(n, "guard", "3.0.1", 37, 0))
        assertFalse(AnnouncementPolicy.applies(n, "lite", "2.1.4", 37, 0))
        assertFalse(AnnouncementPolicy.applies(n, "lite", "3.0.2", 37, 0))
        assertFalse(AnnouncementPolicy.applies(n, "lite", "3.0.1", 41, 0))
    }
    @Test fun scheduledAndExpiredNoticesAreNotEligible() {
        val n = RemoteAnnouncement("n", 1, "t", "b", startsAt = 100, expiresAt = 200)
        assertFalse(AnnouncementPolicy.applies(n, "lite", "3.0", 1, 99))
        assertTrue(AnnouncementPolicy.applies(n, "lite", "3.0", 1, 100))
        assertFalse(AnnouncementPolicy.applies(n, "lite", "3.0", 1, 200))
    }
    @Test fun feedRedirectCannotLeaveTheTwoOwnedLocations() {
        assertTrue(AnnouncementPolicy.trustedFeed("https://halo-tv.pages.dev/announcements.json"))
        assertTrue(AnnouncementPolicy.trustedFeed("https://raw.githubusercontent.com/Null9797/halo-tv/main/announcements.json"))
        for (s in listOf("http://halo-tv.pages.dev/announcements.json", "https://halo-tv.pages.dev.evil.example/announcements.json", "https://user:pass@halo-tv.pages.dev/announcements.json", "https://halo-tv.pages.dev/announcements.json?token=x", "https://raw.githubusercontent.com/other/repo/main/announcements.json")) assertFalse(s, AnnouncementPolicy.trustedFeed(s))
    }
    @Test fun detailsAreLimitedToOwnedHttpsPages() {
        assertTrue(AnnouncementPolicy.trustedDetails("https://halo-tv.pages.dev/help"))
        assertTrue(AnnouncementPolicy.trustedDetails("https://github.com/Null9797/halo-tv/issues/1"))
        assertFalse(AnnouncementPolicy.trustedDetails("javascript:alert(1)"))
        assertFalse(AnnouncementPolicy.trustedDetails("https://github.com/Null9797/halo-tv-fake"))
        assertFalse(AnnouncementPolicy.trustedDetails("https://halo-tv.pages.dev:8080/help"))
    }
    @Test fun detailsCannotNavigateOutsideTheProjectByNormalizingThePath() {
        for (path in listOf("../other", "%2e%2e/other", "issues/../../other")) {
            assertFalse(path, AnnouncementPolicy.trustedDetails("https://github.com/Null9797/halo-tv/$path"))
        }
    }
}
