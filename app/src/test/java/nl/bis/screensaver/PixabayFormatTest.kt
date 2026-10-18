package nl.bis.screensaver

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PixabayFormatTest {
    private fun videos(vararg sizes: Pair<String, Pair<Int, Int>>) = JSONObject().apply {
        sizes.forEach { (name, size) ->
            put(name, JSONObject().put("url", "https://cdn/$name.mp4").put("width", size.first).put("height", size.second))
        }
    }

    @Test
    fun `bij 4K de 1080p-versie onder small`() {
        val best = pixabayFormat(
            videos("large" to (3840 to 2160), "medium" to (2560 to 1440), "small" to (1920 to 1080), "tiny" to (1280 to 720)),
        )
        assertEquals("https://cdn/small.mp4", best?.getString("url"))
    }

    @Test
    fun `bij een 1080p-opname de grootste`() {
        val best = pixabayFormat(videos("large" to (1920 to 1080), "medium" to (1280 to 720), "small" to (960 to 540)))
        assertEquals("https://cdn/large.mp4", best?.getString("url"))
    }

    @Test
    fun `staande video's vallen af`() {
        assertNull(pixabayFormat(videos("large" to (2160 to 3840), "small" to (1080 to 1920), "tiny" to (720 to 1280))))
    }

    @Test
    fun `te klein valt af`() {
        assertNull(pixabayFormat(videos("small" to (960 to 540), "tiny" to (640 to 360))))
    }
}
