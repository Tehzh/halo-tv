package app.orchard.tv.lite

import org.junit.Assert.*
import org.junit.Test

class LiteReleasePolicyTest {
    @Test fun comparesNumericVersions() {
        assertTrue(LiteReleasePolicy.compare("3.0", "2.1.4") > 0)
        assertTrue(LiteReleasePolicy.compare("3.10", "3.9") > 0)
        assertEquals(0, LiteReleasePolicy.compare("3.0", "3.0.0"))
    }

    @Test fun sameVisibleVersionRequiresHigherInternalCode() {
        assertTrue(LiteReleasePolicy.newer("3.0", 32, "3.0", 31))
        assertFalse(LiteReleasePolicy.newer("3.0", 31, "3.0", 31))
        assertFalse(LiteReleasePolicy.newer("3.0", null, "3.0", 31))
        assertFalse(LiteReleasePolicy.newer("2.1.4", 900, "3.0", 31))
        assertTrue(LiteReleasePolicy.newer("3.0.1", null, "3.0", 31))
    }

    @Test fun onlyAcceptsLiteAssetsFromHaloReleaseSources() {
        assertTrue(LiteReleasePolicy.trustedPackage("https://halo-tv.pages.dev/updates/HaloTV-3.0-lite.apk"))
        assertTrue(LiteReleasePolicy.trustedPackage("https://github.com/Null9797/halo-tv/releases/download/v3.0/HaloTV-3.0-lite.apk"))
        assertTrue(LiteReleasePolicy.liteName("aerial-universal-3.0-lite.apk"))
        assertFalse(LiteReleasePolicy.trustedPackage("https://github.com/Null9797/halo-tv/releases/download/v3.0/HaloTV-3.0-guard.apk"))
        assertFalse(LiteReleasePolicy.trustedPackage("https://github.com/another/repo/releases/download/v3.0/HaloTV-3.0-lite.apk"))
        assertFalse(LiteReleasePolicy.liteName("another-app-lite.apk"))
    }

    @Test fun rejectsHttpCredentialsAndUnexpectedHosts() {
        assertFalse(LiteReleasePolicy.trustedPackage("http://halo-tv.pages.dev/updates/HaloTV-3.0-lite.apk"))
        assertFalse(LiteReleasePolicy.trustedPackage("https://user:pass@halo-tv.pages.dev/updates/HaloTV-3.0-lite.apk"))
        assertFalse(LiteReleasePolicy.trustedPackage("https://halo-tv.pages.dev.evil.example/updates/HaloTV-3.0-lite.apk"))
        assertFalse(LiteReleasePolicy.trustedPackage("https://halo-tv.pages.dev:444/updates/HaloTV-3.0-lite.apk"))
    }

    @Test fun redirectsOnlyToHttpsGithubReleaseStorage() {
        assertTrue(LiteReleasePolicy.trustedRedirect("https://release-assets.githubusercontent.com/123/file.apk?token=abc"))
        assertTrue(LiteReleasePolicy.trustedRedirect("https://objects.githubusercontent.com/123/file.apk"))
        assertFalse(LiteReleasePolicy.trustedRedirect("https://example.com/file.apk"))
        assertFalse(LiteReleasePolicy.trustedRedirect("http://release-assets.githubusercontent.com/file.apk"))
    }
}
