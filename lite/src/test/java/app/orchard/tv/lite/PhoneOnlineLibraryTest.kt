package app.orchard.tv.lite

import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class PhoneOnlineLibraryTest {
    private val app = RuntimeEnvironment.getApplication()
    private val cache get() = File(app.cacheDir, "phone-bing-index.json")
    private val part get() = File(app.cacheDir, "phone-bing-index.json.part")
    private fun row(date: String = "20261117") = JSONObject().put("date", date)
        .put("thumbnail", "thumbnails/2026/11/$date.webp").put("title", "Test landscape")
    private fun catalog() = JSONArray().put(row()).toString()

    @Before @After fun cleanup() {
        if (cache.isDirectory) cache.deleteRecursively() else cache.delete()
        part.delete()
    }

    @Test fun emptyOrEntirelyUnusableCatalogDoesNotBecomeASuccessfulEmptyPage() {
        assertThrows(IllegalArgumentException::class.java) { PhoneOnlineLibrary.parseIndex("[]") }
        assertThrows(IllegalArgumentException::class.java) {
            PhoneOnlineLibrary.parseIndex(JSONArray().put(row().put("thumbnail", "../../private.jpg")).toString())
        }
    }

    @Test fun invalidRowsAndRepeatedDatesAreRemovedBeforeGridKeysAndPageCounts() {
        val raw = JSONArray().put("bad row").put(row()).put(row())
            .put(row("not-a-date")).put(row("20261116"))
        val result = PhoneOnlineLibrary.parseIndex(raw.toString())
        assertEquals(2, result.length())
        assertEquals("20261117", result.getJSONObject(0).getString("date"))
        assertEquals("20261116", result.getJSONObject(1).getString("date"))
    }

    @Test fun retryRefreshesAnEmptyFreshCacheInsteadOfKeepingItForSixHours() {
        cache.writeText("[]")
        var requests = 0
        val result = PhoneOnlineLibrary.index(app) { requests++; catalog().toByteArray() }
        assertEquals(1, requests)
        assertEquals(1, result.length())
        assertEquals(1, PhoneOnlineLibrary.parseIndex(cache.readText()).length())
        assertFalse(part.exists())
    }

    @Test fun retryReplacesAPartiallyWrittenCache() {
        cache.writeText("[{\"date\":")
        assertEquals(1, PhoneOnlineLibrary.index(app) { catalog().toByteArray() }.length())
        assertEquals(1, PhoneOnlineLibrary.parseIndex(cache.readText()).length())
    }

    @Test fun usableFreshCacheDoesNotStartAnUnnecessaryNetworkRequest() {
        cache.writeText(catalog())
        assertEquals(1, PhoneOnlineLibrary.index(app) { throw AssertionError("network must not run") }.length())
    }

    @Test fun cacheWriteFailureDoesNotHideAValidDownloadedCatalog() {
        assertTrue(cache.mkdir()) // A controlled write failure; never change host permissions.
        assertEquals(1, PhoneOnlineLibrary.index(app) { catalog().toByteArray() }.length())
        assertTrue(cache.isDirectory)
        assertFalse(part.exists())
    }

    @Test fun failedDownloadPreservesThePreviousCatalogAndCanBeRetried() {
        val before = catalog()
        cache.writeText(before)
        assertTrue(cache.setLastModified(0))
        assertThrows(IOException::class.java) { PhoneOnlineLibrary.index(app) { throw IOException("offline") } }
        assertEquals(before, cache.readText())
        assertFalse(part.exists())
        assertEquals(1, PhoneOnlineLibrary.index(app) { before.toByteArray() }.length())
    }

    @Test fun emptyDownloadedCatalogIsNotSavedAsANewCache() {
        assertThrows(IllegalArgumentException::class.java) { PhoneOnlineLibrary.index(app) { "[]".toByteArray() } }
        assertFalse(cache.exists())
        assertFalse(part.exists())
    }

    @Test fun invalidThumbnailsDoNotCreateEmptyPagesWithAnInflatedTotal() {
        val raw = JSONArray()
        repeat(25) { raw.put(row("20261117").put("thumbnail", "invalid")) }
        for (day in 1..25) raw.put(row("202611" + day.toString().padStart(2, '0')))
        cache.writeText(raw.toString())
        val first = PhoneOnlineLibrary.page(app, 0, "")
        val last = PhoneOnlineLibrary.page(app, 1, "")
        assertEquals(25, first.getInt("total"))
        assertEquals(24, first.getJSONArray("items").length())
        assertEquals(1, last.getJSONArray("items").length())
    }

    @Test fun clockCorrectionCannotKeepAFutureDatedCacheFreshForever() {
        cache.writeText(catalog())
        assertTrue(cache.setLastModified(System.currentTimeMillis() + 12 * 60 * 60_000L))
        var requests = 0
        PhoneOnlineLibrary.index(app) { requests++; catalog().toByteArray() }
        assertEquals(1, requests)
    }
}
