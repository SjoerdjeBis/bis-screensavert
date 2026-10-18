package nl.bis.screensaver

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Calendar

class DriveDatesTest {
    private fun day(time: Long?): Triple<Int, Int, Int>? = time?.let {
        val c = Calendar.getInstance().apply { timeInMillis = it }
        Triple(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
    }

    @Test
    fun `datum uit gangbare bestandsnamen`() {
        assertEquals(Triple(2023, 8, 15), day(DriveClient.nameTime("PXL_20230815_142233123.jpg")))
        assertEquals(Triple(2023, 8, 15), day(DriveClient.nameTime("IMG-20230815-WA0001.jpg")))
        assertEquals(Triple(2023, 8, 15), day(DriveClient.nameTime("Screenshot_2023-08-15-14-22-33.png")))
        assertEquals(Triple(2023, 8, 15), day(DriveClient.nameTime("2023-08-15 14.22.33.jpg")))
        assertEquals(Triple(2024, 1, 1), day(DriveClient.nameTime("VID_20240101_000000.mp4")))
    }

    @Test
    fun `geen datum als er geen geloofwaardige in de naam staat`() {
        assertNull(DriveClient.nameTime("IMG_1234.HEIC"))
        assertNull(DriveClient.nameTime("DSC01234.JPG"))
        assertNull(DriveClient.nameTime("IMG_20231301_120000.jpg")) // maand 13
        assertNull(DriveClient.nameTime("12345620230815.jpg")) // deel van een langer getal
        assertNull(DriveClient.nameTime("IMG_20991231_120000.jpg")) // in de toekomst
    }

    @Test
    fun `opnametijd uit de foto`() {
        val c = Calendar.getInstance().apply {
            clear()
            set(2019, Calendar.MAY, 12, 14, 3, 22)
        }
        assertEquals(c.timeInMillis, DriveClient.exifTime("2019:05:12 14:03:22"))
        assertNull(DriveClient.exifTime(null))
        assertNull(DriveClient.exifTime("   "))
        assertNull(DriveClient.exifTime("0000:00:00 00:00:00")) // camera zonder ingestelde klok
    }
}
