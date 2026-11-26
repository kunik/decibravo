package app.decibravo.scoring

import org.junit.Assert.assertEquals
import org.junit.Test

class FormatTest {
    @Test fun numbers() {
        assertEquals("262", fmtScore(261.5)); assertEquals("0", fmtScore(0.0))
        assertEquals("9,1", fmtSeconds(9.12)); assertEquals("10,0", fmtSeconds(10.0)); assertEquals("29", fmtStrength(28.8))
    }
    @Test fun places() { assertEquals(listOf("1-ше", "2-ге", "3-тє", "4-те", "7-ме", "8-ме", "12-те"), listOf(1, 2, 3, 4, 7, 8, 12).map(::fmtPlace)) }
    @Test fun places_beyondTwenty() {
        assertEquals(listOf("11-те", "21-ше", "22-ге", "23-тє", "24-те", "27-ме", "28-ме", "40-ве", "111-те"), listOf(11, 21, 22, 23, 24, 27, 28, 40, 111).map(::fmtPlace))
    }
    @Test fun tableText_format() {
        val r = Result(313.6, 9.8, 32.0, EndReason.LIMIT, false)
        assertEquals("Коляда 2026\n1. Три царі — 314 (9,8 с, сила 32)\n— Чорти",
            tableText("Коляда 2026", listOf(TableRow(1, "Три царі", r), TableRow(null, "Чорти", null))))
    }
    @Test fun scoreScale_shrinksLongNumbers() {
        assertEquals(1f, scoreScale("262"), 0f); assertEquals(0.75f, scoreScale("1240"), 1e-6f); assertEquals(0.6f, scoreScale("12400"), 1e-6f)
    }
}
