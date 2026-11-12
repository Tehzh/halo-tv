package app.orchard.tv.wallpaper

import android.content.Context
import android.media.MediaMetadataRetriever
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowMediaMetadataRetriever
import java.io.File
import java.io.RandomAccessFile

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ScreensaverVideosTest {
    private val context: Context = RuntimeEnvironment.getApplication()
    private val root get() = File(context.filesDir, "screensaver-videos")

    private fun upload(): File = File.createTempFile("video", ".mov", context.cacheDir).apply {
        writeText("uploaded bytes")
        ShadowMediaMetadataRetriever.addMetadata(absolutePath, MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO, "yes")
        ShadowMediaMetadataRetriever.addMetadata(absolutePath, MediaMetadataRetriever.METADATA_KEY_DURATION, "2000")
    }

    private fun stored(id: String, bytes: Long = 1) = File(root, id).apply {
        mkdirs()
        File(this, "info.json").writeText(JSONObject().put("name", "scene.mov").toString())
        RandomAccessFile(File(this, "video.mp4"), "rw").use { it.setLength(bytes) }
    }

    @Test
    fun uploadIsPublishedWithItsNameAndBytesButDoesNotSelectIt() {
        val original = upload()
        val bytes = original.readText()
        val video = ScreensaverVideos.add(context, original, "../phone/scene.mov")
        assertNotNull(video)
        assertEquals("scene.mov", video!!.name)
        assertEquals(bytes, video.file.readText())
        assertEquals(listOf(video), ScreensaverVideos.all(context))
        assertFalse(root.listFiles().orEmpty().any { it.name.startsWith("import-") })
        assertFalse(context.getSharedPreferences("wallpaper", Context.MODE_PRIVATE).contains("screensaver"))
    }

    @Test
    fun rejectsEmptyFilesAudioAndUnknownExtensionsWithoutPublishing() {
        val empty = File.createTempFile("empty", ".mp4", context.cacheDir)
        assertNull(ScreensaverVideos.add(context, empty, "empty.mp4"))
        val audio = upload()
        ShadowMediaMetadataRetriever.addMetadata(audio.absolutePath, MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO, "no")
        assertNull(ScreensaverVideos.add(context, audio, "audio.mov"))
        assertNull(ScreensaverVideos.add(context, upload(), "script.html"))
        assertTrue(ScreensaverVideos.all(context).isEmpty())
    }

    @Test
    fun rejectsOversizedUploadBeforeCopyingAndPreservesTheLibrary() {
        val old = stored("a".repeat(32))
        val large = upload()
        RandomAccessFile(large, "rw").use { it.setLength(ScreensaverVideos.MAX_FILE_BYTES + 1) }
        assertNull(ScreensaverVideos.add(context, large, "large.mov"))
        assertEquals(listOf(old.name), ScreensaverVideos.all(context).map { it.id })
    }

    @Test
    fun aFullLibraryHasNoRoomAndDoesNotEvictExistingFilms() {
        repeat(64) { stored(it.toString().padStart(32, '0')) }
        assertEquals(0L, ScreensaverVideos.room(context))
        assertNull(ScreensaverVideos.add(context, upload(), "one-more.mp4"))
        assertEquals(64, ScreensaverVideos.all(context).size)
    }

    @Test
    fun roomLeavesHalfTheFreeSpaceAndNeverPassesTheFileCap() {
        val free = File(context.filesDir, "screensaver-videos").apply { mkdirs() }.usableSpace
        val room = ScreensaverVideos.room(context)
        assertTrue(room <= free / 2 && room <= ScreensaverVideos.MAX_FILE_BYTES)
    }

    @Test
    fun smallUploadsCanFitBelowOneGiBWithoutUsingTheLast256MiB() {
        val mib = 1024L * 1024
        assertEquals(128 * mib, ScreensaverVideos.uploadBudget(384 * mib, 0))
        assertEquals(0L, ScreensaverVideos.uploadBudget(256 * mib, 0))
        assertEquals(64 * mib, ScreensaverVideos.uploadBudget(384 * mib, 256 * mib))
    }

    @Test
    fun brokenOrOversizedStreamsLeaveNothingBehind() {
        val short = "short".toByteArray().inputStream()
        assertNull(ScreensaverVideos.receive(context, short, 100, "cut.mp4"))
        val unread = "never read".toByteArray().inputStream()
        assertNull(ScreensaverVideos.receive(context, unread, ScreensaverVideos.MAX_FILE_BYTES + 1, "huge.mp4"))
        assertEquals(10, unread.available())
        assertTrue(ScreensaverVideos.all(context).isEmpty())
        assertFalse(root.listFiles().orEmpty().any { it.name.startsWith("import-") })
    }

    @Test
    fun posterUploadRejectsInvalidTargetsAndMalformedData() {
        val film = stored("f".repeat(32))
        val data = byteArrayOf(1, 2, 3)
        assertFalse(ScreensaverVideos.savePoster(context, "../outside", data.inputStream(), data.size.toLong()))
        assertFalse(ScreensaverVideos.savePoster(context, film.name, data.inputStream(), 2L * 1024 * 1024 + 1))
        assertFalse(ScreensaverVideos.savePoster(context, film.name, data.inputStream(), data.size.toLong()))
        assertFalse(File(film, "poster.jpg").exists())
        assertTrue(File(film, "video.mp4").exists())
    }

    @Test
    fun posterUploadStoresAValidSmallJpegForTheMatchingVideo() {
        val film = stored("9".repeat(32))
        val bytes = javaClass.getResourceAsStream("/poster-test.jpg")!!.use { it.readBytes() }
        assertTrue(ScreensaverVideos.savePoster(context, film.name, bytes.inputStream(), bytes.size.toLong()))
        assertTrue(File(film, "poster.jpg").readBytes().contentEquals(bytes))
        assertTrue(File(film, "video.mp4").exists())
    }

    @Test
    fun phonePageOffersFlipClockOnlyOnce() {
        val page = context.assets.open("web/index.html").bufferedReader().use { it.readText() }
        assertEquals(1, Regex("data-mode=\"flip_clock\"").findAll(page).count())
        assertFalse(page.contains("data-mode=\"clock\""))
    }

    @Test
    fun partialAndMalformedImportsAreNotPlayable() {
        val good = stored("a".repeat(32))
        stored("b".repeat(32)).resolve("info.json").writeText("broken")
        stored("import-" + "c".repeat(32))
        stored("d".repeat(32)).resolve("video.mp4").delete()
        assertEquals(listOf(good.name), ScreensaverVideos.all(context).map { it.id })
    }

    @Test
    fun deletionAcceptsOnlyLibraryIdsAndLeavesOtherOwnerFilesAlone() {
        val photo = File(context.filesDir, "keep.jpg").apply { writeText("owner photo") }
        assertFalse(ScreensaverVideos.delete(context, "../keep.jpg"))
        assertTrue(photo.exists())
        val film = stored("e".repeat(32))
        assertTrue(ScreensaverVideos.delete(context, film.name))
        assertTrue(photo.exists())
        assertFalse(ScreensaverVideos.delete(context, film.name))
    }
}
