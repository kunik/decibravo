package app.decibravo.data

import app.decibravo.scoring.Calibration
import app.decibravo.scoring.replay
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

class Store(private val root: File, private val now: () -> Long = System::currentTimeMillis) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    init {
        File(root, "tmp").deleteRecursively()
        File(root, "tmp").mkdirs()
    }

    private fun newId() = UUID.randomUUID().toString()
    private fun calibrationFile() = File(root, "calibration.json")
    private fun compDir(id: String) = File(File(root, "competitions"), id)
    private fun compFile(id: String) = File(compDir(id), "competition.json")
    private fun measDir(id: String) = File(compDir(id), "measurements")

    private fun writeAtomic(target: File, text: String) {
        target.parentFile?.mkdirs()
        val tmp = File(target.parentFile, target.name + ".tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(target)) {
            target.delete()
            check(tmp.renameTo(target)) { "rename failed: $target" }
        }
    }

    fun loadCalibration(): Calibration =
        try { json.decodeFromString<Calibration>(calibrationFile().readText()) } catch (e: Exception) { Calibration() }

    fun saveCalibration(c: Calibration) = writeAtomic(calibrationFile(), json.encodeToString(c))

    fun loadCompetition(id: String): Competition? =
        try { json.decodeFromString<Competition>(compFile(id).readText()) } catch (e: Exception) { null }

    fun listCompetitions(): List<Competition> =
        File(root, "competitions").listFiles()
            ?.filter { it.isDirectory }
            ?.mapNotNull { loadCompetition(it.name) }
            ?.sortedByDescending { it.createdAt }
            ?: emptyList()

    fun createCompetition(name: String, names: List<String>, maxDurationSec: Int, quietEndSec: Double): Competition {
        val c = Competition(
            id = newId(), name = name, createdAt = now(),
            maxDurationSec = maxDurationSec, quietEndSec = quietEndSec,
            participants = names.map { Participant(newId(), it) },
        )
        measDir(c.id).mkdirs()
        writeAtomic(compFile(c.id), json.encodeToString(c))
        return c
    }

    fun saveCompetition(c: Competition) {
        val old = loadCompetition(c.id)
        if (old != null && old.isLocked() &&
            (old.maxDurationSec != c.maxDurationSec || old.quietEndSec != c.quietEndSec || old.floorRms != c.floorRms)
        ) throw IllegalStateException("Competition is locked")
        writeAtomic(compFile(c.id), json.encodeToString(c))
    }

    fun deleteCompetition(id: String) { compDir(id).deleteRecursively() }

    private fun require(id: String): Competition =
        loadCompetition(id) ?: throw IllegalArgumentException("No competition $id")

    fun addParticipants(competitionId: String, names: List<String>): Competition {
        val c = require(competitionId)
        val updated = c.copy(participants = c.participants + names.map { Participant(newId(), it) })
        saveCompetition(updated)
        return updated
    }

    fun renameParticipant(competitionId: String, participantId: String, name: String): Competition {
        val c = require(competitionId)
        val updated = c.copy(participants = c.participants.map { if (it.id == participantId) it.copy(name = name) else it })
        saveCompetition(updated)
        return updated
    }

    private fun deleteMeasurementFiles(competitionId: String, measurementId: String) {
        File(measDir(competitionId), "$measurementId.json").delete()
        File(measDir(competitionId), "$measurementId.wav").delete()
    }

    private fun withoutCalibrationIfEmpty(c: Competition): Competition =
        if (c.acceptedCount() == 0) c.copy(frozenCalibration = null) else c

    fun removeParticipant(competitionId: String, participantId: String): Competition {
        val c = require(competitionId)
        c.participants.firstOrNull { it.id == participantId }?.measurementId?.let { deleteMeasurementFiles(competitionId, it) }
        val updated = withoutCalibrationIfEmpty(c.copy(participants = c.participants.filter { it.id != participantId }))
        saveCompetition(updated)
        return updated
    }

    fun loadMeasurement(competitionId: String, measurementId: String): Measurement? =
        try { json.decodeFromString<Measurement>(File(measDir(competitionId), "$measurementId.json").readText()) }
        catch (e: Exception) { null }

    fun accept(competitionId: String, participantId: String, pending: PendingMeasurement): Competition {
        val c = require(competitionId)
        val participant = c.participants.first { it.id == participantId }
        val mid = newId()
        val dir = measDir(competitionId).also { it.mkdirs() }
        val wavName = "$mid.wav"
        val wavTarget = File(dir, wavName)
        if (!pending.wavTemp.renameTo(wavTarget)) {
            pending.wavTemp.copyTo(wavTarget, overwrite = true)
            pending.wavTemp.delete()
        }
        val m = Measurement(mid, participantId, now(), pending.audioSource, pending.frames, pending.result, wavName)
        writeAtomic(File(dir, "$mid.json"), json.encodeToString(m))
        participant.measurementId?.let { deleteMeasurementFiles(competitionId, it) }
        val updated = c.copy(
            frozenCalibration = c.frozenCalibration ?: loadCalibration(),
            participants = c.participants.map { if (it.id == participantId) it.copy(measurementId = mid) else it },
        )
        saveCompetition(updated)
        return updated
    }

    fun deleteMeasurement(competitionId: String, participantId: String): Competition {
        val c = require(competitionId)
        c.participants.firstOrNull { it.id == participantId }?.measurementId?.let { deleteMeasurementFiles(competitionId, it) }
        val updated = withoutCalibrationIfEmpty(
            c.copy(participants = c.participants.map { if (it.id == participantId) it.copy(measurementId = null) else it })
        )
        saveCompetition(updated)
        return updated
    }

    fun recalculate(competitionId: String): Competition {
        val c = require(competitionId)
        val frozen = c.frozenCalibration ?: return c
        if (c.acceptedCount() == 0) return c
        val device = loadCalibration()
        val newCal = frozen.copy(refDbfs = device.refDbfs, gamma = device.gamma)
        val params = c.copy(frozenCalibration = newCal).measureParams(device) ?: return c
        for (p in c.participants) {
            val mid = p.measurementId ?: continue
            val m = loadMeasurement(competitionId, mid) ?: continue
            val r = replay(m.frames, params)
            writeAtomic(File(measDir(competitionId), "$mid.json"), json.encodeToString(m.copy(result = r)))
        }
        val updated = c.copy(frozenCalibration = newCal)
        saveCompetition(updated)
        return updated
    }

    fun newTempWav(): File {
        val dir = File(root, "tmp").also { it.mkdirs() }
        return File(dir, "${newId()}.wav")
    }
}
