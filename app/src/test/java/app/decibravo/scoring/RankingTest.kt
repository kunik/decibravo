package app.decibravo.scoring

import org.junit.Assert.assertEquals
import org.junit.Test

class RankingTest {
    @Test fun rank_byScoreThenDurationThenTime() {
        val r = rank(listOf(Entry("a", 100.0, 5.0, 3), Entry("b", 200.0, 4.0, 2), Entry("c", 100.0, 6.0, 4), Entry("d", 100.0, 6.0, 1)))
        assertEquals(listOf("b", "d", "c", "a"), r.map { it.entry.participantId }); assertEquals(listOf(1, 2, 3, 4), r.map { it.place })
    }
    @Test fun rank_usesUnroundedScore() { assertEquals("y", rank(listOf(Entry("x", 261.6, 5.0, 1), Entry("y", 262.4, 5.0, 2)))[0].entry.participantId) }
    @Test fun placeIfAccepted_between() { assertEquals(2, placeIfAccepted(150.0, 5.0, listOf(Entry("a", 100.0, 5.0, 1), Entry("b", 200.0, 4.0, 2)))) }
    @Test fun placeIfAccepted_empty_isFirst() { assertEquals(1, placeIfAccepted(0.0, 0.0, emptyList())) }
}
