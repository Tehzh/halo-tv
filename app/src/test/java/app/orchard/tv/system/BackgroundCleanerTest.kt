package app.orchard.tv.system

import org.junit.Assert.assertEquals
import org.junit.Test

class BackgroundCleanerTest {
    private fun app(name: String, uid: Int = 10080, system: Boolean = false) =
        BackgroundCleaner.Candidate(name, uid, system)

    @Test fun manualCleanupStillFindsUserAppsWhenProcessListContainsOnlyHalo() {
        assertEquals(listOf("video"), BackgroundCleaner.select(
            listOf(app("video"), app("halo", 10070)), "halo", 10070,
            emptySet(), setOf("halo"), emptySet(), allowFallback = true))
    }

    @Test fun idleCleanupDoesNotGuessWhichInstalledAppsAreRunning() {
        assertEquals(emptyList<String>(), BackgroundCleaner.select(
            listOf(app("video")), "halo", 10070, emptySet(), emptySet(), emptySet(), allowFallback = false))
    }

    @Test fun networkHelpersInputServicesForegroundAndSharedUidAreNeverTargets() {
        assertEquals(listOf("cached"), BackgroundCleaner.select(
            listOf(app("vpn"), app("keyboard"), app("reader"), app("player"),
                app("system", system = true), app("halo-helper", 10070), app("cached"), app("cached")),
            "halo", 10070, setOf("vpn", "keyboard", "reader"), setOf("player"), setOf("cached"), true))
    }

    @Test fun observedActiveProcessWinsOverCachedProcessInSamePackage() {
        assertEquals(emptyList<String>(), BackgroundCleaner.select(listOf(app("player")),
            "halo", 10070, emptySet(), setOf("player"), setOf("player"), true))
    }

    @Test fun manualCleanupIsNotGloballyBlockedByStaleMusicOrUnknownNetwork() {
        assertEquals(false, BackgroundCleaner.shouldSkip(true, false, true))
        assertEquals(false, BackgroundCleaner.shouldSkip(true, true, true))
        assertEquals(false, BackgroundCleaner.shouldSkip(true, false, false))
    }

    @Test fun idleAutomationStillSkipsMusicAndUncertainNetwork() {
        assertEquals(true, BackgroundCleaner.shouldSkip(false, false, false))
        assertEquals(true, BackgroundCleaner.shouldSkip(false, true, true))
        assertEquals(false, BackgroundCleaner.shouldSkip(false, true, false))
    }
}
