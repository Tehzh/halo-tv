package app.orchard.tv.wallpaper

import java.io.File
import java.io.FileOutputStream

/** Publish complete cache files in one rename, so concurrent readers never see half a JSON/photo. */
internal fun File.replaceContents(bytes: ByteArray) {
    val temporary = File.createTempFile(name.padEnd(3, '_'), ".part", parentFile)
    try {
        FileOutputStream(temporary).use { output -> output.write(bytes); output.fd.sync() }
        check(temporary.renameTo(this)) { "Could not publish ${name}" }
    } finally {
        temporary.delete()
    }
}
