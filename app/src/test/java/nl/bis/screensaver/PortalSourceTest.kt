package nl.bis.screensaver

import org.junit.Assert.assertEquals
import org.junit.Test

class PortalSourceTest {
    @Test
    fun `alleen de video's van de release, met de titel uit het label`() {
        val release = """{"assets":[
            {"name":"alpenglow-horn.mp4","label":"Alpenglow","browser_download_url":"https://x/alpenglow-horn.mp4"},
            {"name":"world-turtle.mp4","label":"","browser_download_url":"https://x/world-turtle.mp4"},
            {"name":"lijst.json","label":null,"browser_download_url":"https://x/lijst.json"}
        ]}"""
        assertEquals(
            listOf(
                PortalClip("portaal:alpenglow-horn", "Alpenglow", "https://x/alpenglow-horn.mp4"),
                PortalClip("portaal:world-turtle", "World turtle", "https://x/world-turtle.mp4"),
            ),
            PortalSource.parse(release),
        )
    }

    @Test
    fun `een release zonder bestanden geeft een lege lijst`() {
        assertEquals(emptyList<PortalClip>(), PortalSource.parse("{}"))
    }
}
