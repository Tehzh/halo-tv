package app.orchard.tv.lite

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class BitmapProcessingTest {
    @Test fun rollingBlurMatchesOriginalIncludingAlphaAndClampedEdges() {
        val random = Random(30)
        for ((w, h) in listOf(1 to 1, 1 to 9, 13 to 1, 3 to 7, 37 to 21)) {
            for (radius in listOf(0, 1, 7, 12)) {
                val original = IntArray(w * h) { random.nextInt() }
                for (passes in 0..3) {
                    val expected = original.copyOf()
                    repeat(passes) { reference(expected, w, h, radius) }
                    val actual = original.copyOf()
                    BoxBlur.apply(actual, w, h, radius, passes)
                    assertArrayEquals("${w}x${h}, radius=$radius, passes=$passes", expected, actual)
                }
            }
        }
    }

    @Test fun normalTvImagesRetainTheirOriginalCoverSampling() {
        assertEquals(1, coverSampleSize(1920, 1080, 1920, 1080))
        assertEquals(2, coverSampleSize(3840, 2160, 1920, 1080))
        assertEquals(1, coverSampleSize(2560, 1440, 1920, 1080))
        assertEquals(1, coverSampleSize(3840, 2160, 3840, 2160))
    }

    @Test fun unusuallyWideAndTallFilesCannotAllocateAnUnboundedBitmap() {
        for ((w, h) in listOf(80000 to 2000, 2000 to 80000, Int.MAX_VALUE to 1, 1 to Int.MAX_VALUE)) {
            val sample = coverSampleSize(w, h, 1920, 1080)
            val pixels = ((w.toLong() + sample - 1) / sample) * ((h.toLong() + sample - 1) / sample)
            assertTrue(pixels <= 4_194_304L)
        }
    }

    private fun reference(pixels: IntArray, w: Int, h: Int, radius: Int) {
        val tmp = IntArray(pixels.size)
        fun pass(src: IntArray, dst: IntArray, horizontal: Boolean) {
            for (line in 0 until if (horizontal) h else w) {
                for (i in 0 until if (horizontal) w else h) {
                    var a = 0; var r = 0; var g = 0; var b = 0; var count = 0
                    for (offset in -radius..radius) {
                        val j = (i + offset).coerceIn(0, (if (horizontal) w else h) - 1)
                        val c = src[if (horizontal) line * w + j else j * w + line]
                        a += c ushr 24; r += (c ushr 16) and 255; g += (c ushr 8) and 255; b += c and 255; count++
                    }
                    dst[if (horizontal) line * w + i else i * w + line] =
                        ((a / count) shl 24) or ((r / count) shl 16) or ((g / count) shl 8) or (b / count)
                }
            }
        }
        pass(pixels, tmp, true); pass(tmp, pixels, false)
    }
}
