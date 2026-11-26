package app.decibravo.data

import app.decibravo.scoring.Calibration
import app.decibravo.scoring.EndReason
import app.decibravo.scoring.Frame
import app.decibravo.scoring.MeasureParams
import app.decibravo.scoring.fromDb
import app.decibravo.scoring.replay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.math.sqrt

class StoreTest {
    @get:Rule val tmp = TemporaryFolder()

    private val FLOOR = 0.001
    private val REF = fromDb(-30.0)
    private val LOUD = Frame(sqrt(REF * REF + FLOOR * FLOOR), 0)
    private fun seq(vararg parts: Pair<Int, Frame>) = parts.flatMap { (n, f) -> List(n) { f } }

    private fun pending(): PendingMeasurement {
        val frames = seq(30 to LOUD)
        val wav = tmp.newFile()
        WavWriter(wav).use { it.write(ShortArray(4800)) }
        val result = replay(frames, MeasureParams(10, 2.0, FLOOR, Calibration()))
        return PendingMeasurement(frames, result, "UNPROCESSED", wav)
    }

    @Test fun createAndReload() {
        val c = Store(tmp.root).createCompetition("Проба", listOf("A", "B"), 10, 2.0)
        val again = Store(tmp.root).loadCompetition(c.id)!!
        assertEquals("Проба", again.name); assertEquals(listOf("A", "B"), again.participants.map { it.name })
    }
    @Test fun accept_freezesCalibration_andPersists() {
        val s = Store(tmp.root); s.saveCalibration(Calibration(gamma = 1.4)); val c = s.createCompetition("n", listOf("A"), 10, 2.0)
        val after = s.accept(c.id, c.participants[0].id, pending())
        assertEquals(1.4, after.frozenCalibration!!.gamma, 0.0); assertTrue(after.isLocked())
        val m = Store(tmp.root).loadMeasurement(c.id, after.participants[0].measurementId!!)!!
        assertEquals(30, m.frames.size); assertTrue(File(tmp.root, "competitions/${c.id}/measurements/${m.wavFile}").exists())
    }
    @Test fun accept_again_replacesOldFiles() {
        val s = Store(tmp.root); val c = s.createCompetition("n", listOf("A"), 10, 2.0); val pid = c.participants[0].id
        val first = s.accept(c.id, pid, pending()).participants[0].measurementId!!; s.accept(c.id, pid, pending())
        val dir = File(tmp.root, "competitions/${c.id}/measurements")
        assertEquals(2, dir.listFiles()!!.size); assertFalse(File(dir, "$first.json").exists())
    }
    @Test fun deletingLastMeasurement_unlocksAndClearsCalibration() {
        val s = Store(tmp.root); val c = s.createCompetition("n", listOf("A"), 10, 2.0); val pid = c.participants[0].id
        s.accept(c.id, pid, pending()); val after = s.deleteMeasurement(c.id, pid)
        assertFalse(after.isLocked()); assertNull(after.frozenCalibration)
        assertEquals(0, File(tmp.root, "competitions/${c.id}/measurements").listFiles()!!.size)
    }
    @Test(expected = IllegalStateException::class) fun lockedCompetition_rejectsSettingsChange() {
        val s = Store(tmp.root); val c = s.createCompetition("n", listOf("A"), 10, 2.0)
        s.saveCompetition(s.accept(c.id, c.participants[0].id, pending()).copy(maxDurationSec = 20))
    }
    @Test fun lockedCompetition_allowsRenameAndNewParticipants() {
        val s = Store(tmp.root); val c = s.createCompetition("n", listOf("A"), 10, 2.0); s.accept(c.id, c.participants[0].id, pending())
        s.saveCompetition(s.loadCompetition(c.id)!!.copy(name = "нова")); assertEquals(2, s.addParticipants(c.id, listOf("B")).participants.size)
    }
    @Test fun recalculate_takesOnlyRefAndGamma() {
        val s = Store(tmp.root); val c = s.createCompetition("n", listOf("A"), 10, 2.0); val pid = c.participants[0].id
        s.saveCompetition(c.copy(floorRms = 0.001)); val before = s.accept(c.id, pid, pending())
        s.saveCalibration(Calibration(refDbfs = -24.0, gamma = 1.0, thresholdDb = 12.0)); val after = s.recalculate(c.id)
        assertEquals(-24.0, after.frozenCalibration!!.refDbfs, 0.0); assertEquals(6.0, after.frozenCalibration!!.thresholdDb, 0.0)
        val m = s.loadMeasurement(c.id, after.participants[0].measurementId!!)!!
        assertEquals(3.0, m.result.durationSec, 1e-9); assertTrue(m.result.score < 30.0)
    }
    @Test fun corruptCompetition_isSkipped() {
        val s = Store(tmp.root); val good = s.createCompetition("добре", emptyList(), 10, 2.0); val bad = s.createCompetition("зле", emptyList(), 10, 2.0)
        File(tmp.root, "competitions/${bad.id}/competition.json").writeText("{ \"id\": ")
        File(tmp.root, "competitions/${good.id}/competition.json.tmp").writeText("сміття")
        assertEquals(listOf(good.id), Store(tmp.root).listCompetitions().map { it.id }); assertEquals(Calibration(), Store(tmp.root).loadCalibration())
    }
    @Test fun deleteCompetition_removesDirectory() {
        val s = Store(tmp.root); val c = s.createCompetition("n", listOf("A"), 10, 2.0); s.deleteCompetition(c.id)
        assertFalse(File(tmp.root, "competitions/${c.id}").exists()); assertTrue(s.listCompetitions().isEmpty())
    }
    @Test fun removeParticipant_dropsItsMeasurement() {
        val s = Store(tmp.root); val c = s.createCompetition("n", listOf("A", "B"), 10, 2.0); s.accept(c.id, c.participants[0].id, pending())
        val after = s.removeParticipant(c.id, c.participants[0].id); assertEquals(listOf("B"), after.participants.map { it.name }); assertFalse(after.isLocked())
    }
}
