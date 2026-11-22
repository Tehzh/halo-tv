package app.orchard.tv.island

import java.io.ByteArrayInputStream
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IslandTasksTest {
    @Test fun transferProgressIsBoundedAndOnlySavedFilesGetAResult() = runBlocking {
        var now = 500L
        val events = IslandTransferEvents { now }
        assertNull(events.begin(IslandTask.Video))
        var shown: IslandProgress? = null
        val observation = launch(start = CoroutineStart.UNDISPATCHED) {
            events.observe().collect { shown = it }
        }
        val transfer = checkNotNull(events.begin(IslandTask.Video))
        val bytes = transfer.track(ByteArrayInputStream(ByteArray(400)), 400)
        val chunk = ByteArray(100)
        assertEquals(100, bytes.read(chunk))
        yield()
        assertEquals(IslandProgress(IslandTask.Video, 25), shown)
        bytes.read(chunk)
        yield()
        assertEquals(25, shown?.percent)
        now += 300
        bytes.read(chunk)
        yield()
        assertEquals(75, shown?.percent)
        transfer.finish(false)
        yield()
        assertNull(shown)

        checkNotNull(events.begin(IslandTask.Photos)).finish(true)
        yield()
        assertEquals(IslandProgress(IslandTask.Photos, finishedAt = now), shown)
        assertEquals(1L, shown?.remaining(now + 5_999))
        assertEquals(0L, shown?.remaining(now + 6_000))
        observation.cancelAndJoin()
    }

    @Test fun transfersDoNotReplayAcrossDesktopVisitsOrReplaceNewerWork() = runBlocking {
        val events = IslandTransferEvents { 0L }
        val first = launch(start = CoroutineStart.UNDISPATCHED) { events.observe().collect {} }
        val stale = checkNotNull(events.begin(IslandTask.Video))
        first.cancelAndJoin()
        var shown: IslandProgress? = null
        val second = launch(start = CoroutineStart.UNDISPATCHED) { events.observe().collect { shown = it } }
        stale.finish(true)
        yield()
        assertNull(shown)
        val photos = checkNotNull(events.begin(IslandTask.Photos))
        val video = checkNotNull(events.begin(IslandTask.Video))
        photos.finish(true)
        yield()
        assertEquals(IslandProgress(IslandTask.Video), shown)
        video.finish(true)
        yield()
        assertEquals(IslandTask.Video, shown?.task)
        second.cancelAndJoin()
    }

    @Test fun updateCompletionRequiresAnObservedDownloadAndExpires() {
        val updates = IslandUpdateProgress()
        assertNull(updates.onReady(1))
        assertEquals(IslandProgress(IslandTask.Update, 50), updates.onDownload(0.5f))
        updates.reset()
        assertNull(updates.onReady(2))
        updates.onDownload(0.9f)
        val saved = updates.onReady(10_000)
        assertEquals(IslandProgress(IslandTask.Update, finishedAt = 10_000), saved)
        assertEquals(0L, saved?.remaining(16_000))
        assertEquals(saved, preferredTask(IslandProgress(IslandTask.Video, 55), saved))
        assertEquals(IslandTask.Photos, preferredTask(IslandProgress(IslandTask.Photos, finishedAt = 10_001), saved)?.task)
    }

    @Test fun eligibilityRequiresUncoveredActiveHome() {
        fun home(vararg flags: Boolean) = islandOnHome(flags[0], flags[1], flags[2], flags[3], flags[4], flags[5])
        val active = booleanArrayOf(true, true, false, false, false, true)
        assertTrue(home(*active))
        active.indices.forEach { index ->
            val hidden = active.copyOf().apply { this[index] = !this[index] }
            assertFalse(home(*hidden))
        }
    }

    @Test fun failedOrForeignSessionFallsBackToChosenApp() {
        var calls = 0
        assertTrue(openIslandMusicAction("music.app", "other.app", { error("must not run") }, { calls++; true }))
        assertTrue(openIslandMusicAction("music.app", "music.app", { error("cancelled") }, { calls++; true }))
        assertTrue(openIslandMusicAction("music.app", "music.app", { true }, { error("must not run") }))
        assertFalse(openIslandMusicAction("music.app", null, { true }, { false }))
        assertEquals(2, calls)
    }
}
