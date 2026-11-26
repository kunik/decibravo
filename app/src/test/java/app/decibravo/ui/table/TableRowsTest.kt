package app.decibravo.ui.table

import app.decibravo.data.Competition
import app.decibravo.data.Measurement
import app.decibravo.data.Participant
import app.decibravo.scoring.EndReason
import app.decibravo.scoring.Result
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TableRowsTest {
    private fun m(pid: String, score: Double, dur: Double, at: Long, overloaded: Boolean = false) =
        Measurement("m$pid", pid, at, "UNPROCESSED", emptyList(), Result(score, dur, score / dur, EndReason.LIMIT, overloaded), "m$pid.wav")

    @Test fun buildTable_ordersAndFormats() {
        val c = Competition("c", "Коляда 2026", 0, participants = listOf(Participant("a", "Ангели", "ma"), Participant("b", "Чорти"), Participant("t", "Три царі", "mt")))
        val ui = buildTable(c, mapOf("a" to m("a", 187.2, 7.1, 2, overloaded = true), "t" to m("t", 313.6, 9.8, 1)))
        assertEquals("2 із 3 замірів · найдовший замір 10 с", ui.subtitle)
        assertEquals(listOf("Три царі", "Ангели"), ui.ranked.map { it.name }); assertEquals(listOf(1, 2), ui.ranked.map { it.place })
        assertEquals("314", ui.ranked[0].score); assertEquals("9,8 с · сила 32", ui.ranked[0].detail); assertTrue(ui.ranked[1].overloaded)
        assertEquals(listOf("Чорти"), ui.waiting.map { it.name })
    }
    @Test fun copyText_listsRankedThenWaiting() {
        val c = Competition("c", "Коляда 2026", 0, participants = listOf(Participant("b", "Чорти"), Participant("t", "Три царі", "mt")))
        assertEquals("Коляда 2026\n1. Три царі — 314 (9,8 с, сила 32)\n— Чорти", copyText(c, mapOf("t" to m("t", 313.6, 9.8, 1))))
    }
}
