package app.orchard.tv.lite

import android.content.ContextWrapper
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23])
class LiteInstallerTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun oldInstallerReceivesAReadableExternalCopyWhilePendingStaysPrivate() {
        val privateCache = temporary.newFolder("private")
        val externalCache = temporary.newFolder("external")
        val context = object : ContextWrapper(RuntimeEnvironment.getApplication()) {
            override fun getCacheDir() = privateCache
            override fun getExternalCacheDir() = externalCache
        }
        val original = LiteUpdate.file(context).apply { writeBytes(byteArrayOf(1, 2, 3, 4)) }
        val uri = LiteUpdate.installerUri(context)
        assertEquals("file", uri.scheme)
        val copy = File(uri.path!!)
        assertTrue(copy.canonicalPath.startsWith(externalCache.canonicalPath + File.separator))
        assertArrayEquals(original.readBytes(), copy.readBytes())
        assertTrue(original.isFile)
        assertFalse(File(copy.parentFile, "HaloTV-Lite.part").exists())
        original.writeBytes(byteArrayOf(5, 6))
        LiteUpdate.installerUri(context)
        assertArrayEquals(original.readBytes(), copy.readBytes())
        assertEquals(1, copy.parentFile!!.listFiles()!!.size)
    }

    @Test fun inaccessibleExternalCacheNeverFallsBackToSharingPrivateFile() {
        val context = object : ContextWrapper(RuntimeEnvironment.getApplication()) {
            override fun getExternalCacheDir(): File? = null
        }
        assertTrue(runCatching { LiteUpdate.installerUri(context) }.isFailure)
    }

    @Test
    @Config(sdk = [28])
    fun modernInstallerUsesOnlyTheGrantedContentUri() {
        val context = object : ContextWrapper(RuntimeEnvironment.getApplication()) {
            override fun getExternalCacheDir(): File? = error("No external storage needed")
        }
        val uri = LiteUpdate.installerUri(context)
        assertEquals("content", uri.scheme)
        assertEquals(context.packageName + ".updates", uri.authority)
        assertEquals("/pending.apk", uri.path)
    }
}
