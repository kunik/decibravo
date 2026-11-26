package app.decibravo.data

import app.decibravo.scoring.Calibration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CompetitionRulesTest {
    @Test fun parseNames() { assertEquals(listOf("Три царі", "Ангели", "Ангели"), parseParticipantNames("  Три царі \n\n Ангели\r\nАнгели\n  ")) }
    @Test fun lockAndCalibration() {
        val open = Competition("c", "n", 0, participants = listOf(Participant("p", "A")))
        assertFalse(open.isLocked()); assertEquals(Calibration(gamma = 1.3), open.effectiveCalibration(Calibration(gamma = 1.3)))
        val locked = open.copy(frozenCalibration = Calibration(gamma = 2.0), participants = listOf(Participant("p", "A", "m")))
        assertTrue(locked.isLocked()); assertEquals(2.0, locked.effectiveCalibration(Calibration()).gamma, 0.0)
    }
    @Test fun measureParams_needsFloor() {
        assertNull(Competition("c", "n", 0).measureParams(Calibration()))
        assertEquals(0.002, Competition("c", "n", 0, floorRms = 0.002).measureParams(Calibration())!!.floorRms, 0.0)
    }
}
