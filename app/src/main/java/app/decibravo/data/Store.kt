package app.decibravo.data

import app.decibravo.scoring.Calibration
import app.decibravo.scoring.replay
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.nio.channels.FileChannel
import java.nio.file.StandardOpenOption
import java.util.UUID

class Store(private val root: File, private val now: () -> Long = System::currentTimeMillis) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    init {
        File(root, "tmp").deleteRecursively()
        File(root, "tmp").mkdirs()
        cleanOrphans()
    }

    private fun newId() = UUID.randomUUID().toString()
    private fun calibrationFile() = File(root, "calibration.json")
    private fun compDir(id: String) = File(File(root, "competitions"), id)
    private fun compFile(id: String) = File(compDir(id), "competition.json")
    private fun measDir(id: String) = File(compDir(id), "measurements")

    /** Записує файл на диск: tmp → flush → fsync → rename → fsync теки. Для `backup` попередній придатний файл зберігається як `.bak`. */
    private fun writeAtomic(target: File, text: String, backup: Boolean = false, valid: (String) -> Boolean = { true }) {
        val dir = target.parentFile
        dir?.mkdirs()
        val tmp = File(dir, target.name + ".tmp")
        FileOutputStream(tmp).use { out ->
            out.write(text.toByteArray(Charsets.UTF_8))
            out.flush()
            out.fd.sync()
        }
        if (backup && target.exists()) {
            val current = runCatching { target.readText() }.getOrNull()
            // Придатний файл стає резервною копією; пошкоджений відкладаємо окремо й ніколи не затираємо ним добру копію.
            if (current != null && valid(current)) target.copyTo(backupOf(target), overwrite = true)
            else target.copyTo(File(dir, target.name + ".corrupt"), overwrite = true)
        }
        if (!tmp.renameTo(target)) {
            target.delete()
            check(tmp.renameTo(target)) { "rename failed: $target" }
        }
        syncDir(dir)
    }

    private fun backupOf(f: File) = File(f.parentFile, f.name + ".bak")

    private fun syncDir(dir: File?) {
        if (dir == null) return
        runCatching { FileChannel.open(dir.toPath(), StandardOpenOption.READ).use { it.force(true) } }
    }

    private fun readCompetitionText(t: String): Competition? = runCatching { json.decodeFromString<Competition>(t) }.getOrNull()
    private fun readCalibrationText(t: String): Calibration? = runCatching { json.decodeFromString<Calibration>(t) }.getOrNull()

    private fun <T> readWithBackup(f: File, parse: (String) -> T?): T? {
        runCatching { f.readText() }.getOrNull()?.let { t -> parse(t)?.let { return it } }
        return runCatching { backupOf(f).readText() }.getOrNull()?.let(parse)
    }

    /** true, якщо calibration.json існує, але не читається (і резервної копії теж немає): тоді діють типові значення. */
    fun calibrationUnreadable(): Boolean =
        calibrationFile().exists() && readWithBackup(calibrationFile(), ::readCalibrationText) == null

    /** Ідентифікатори тек змагань, чий competition.json не вдалося прочитати; самі файли лишаються на місці. */
    fun unreadableCompetitionIds(): List<String> =
        File(root, "competitions").listFiles()?.filter { it.isDirectory && loadCompetition(it.name) == null }?.map { it.name }?.sorted()
            ?: emptyList()

    fun loadCalibration(): Calibration = readWithBackup(calibrationFile(), ::readCalibrationText) ?: Calibration()

    fun saveCalibration(c: Calibration) =
        writeAtomic(calibrationFile(), json.encodeToString(c), backup = true) { readCalibrationText(it) != null }

    fun loadCompetition(id: String): Competition? = readWithBackup(compFile(id), ::readCompetitionText)

    fun listCompetitions(): List<Competition> =
        File(root, "competitions").listFiles()
            ?.filter { it.isDirectory }
            ?.mapNotNull { loadCompetition(it.name) }
            ?.sortedByDescending { it.createdAt }
            ?: emptyList()

    /** Прибирає файли замірів, на які не вказує жоден учасник (залишки перерваного збереження), і недописані `.tmp`. */
    private fun cleanOrphans() {
        File(root, "competitions").listFiles()?.filter { it.isDirectory }?.forEach { d ->
            val c = loadCompetition(d.name) ?: return@forEach   // нечитабельне змагання не чіпаємо
            val keep = c.participants.mapNotNull { it.measurementId }.toSet()
            File(d, "measurements").listFiles()?.forEach { f ->
                if (f.name.endsWith(".tmp") || f.nameWithoutExtension !in keep) f.delete()
            }
        }
    }

    fun createCompetition(name: String, names: List<String>, maxDurationSec: Int, quietEndSec: Double): Competition {
        val c = Competition(
            id = newId(), name = name, createdAt = now(),
            maxDurationSec = maxDurationSec, quietEndSec = quietEndSec,
            participants = names.map { Participant(newId(), it) },
        )
        measDir(c.id).mkdirs()
        writeAtomic(compFile(c.id), json.encodeToString(c), backup = true) { readCompetitionText(it) != null }
        return c
    }

    fun saveCompetition(c: Competition) {
        val old = loadCompetition(c.id)
        if (old != null && old.isLocked() &&
            (old.maxDurationSec != c.maxDurationSec || old.quietEndSec != c.quietEndSec || old.floorRms != c.floorRms)
        ) throw IllegalStateException("Competition is locked")
        writeAtomic(compFile(c.id), json.encodeToString(c), backup = true) { readCompetitionText(it) != null }
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
        val oldMid = c.participants.firstOrNull { it.id == participantId }?.measurementId
        val updated = withoutCalibrationIfEmpty(c.copy(participants = c.participants.filter { it.id != participantId }))
        saveCompetition(updated)                                  // спершу нова правда, потім прибирання
        oldMid?.let { deleteMeasurementFiles(competitionId, it) }
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
        // Копіюємо, а не переносимо: якщо збереження нижче не вдасться, тимчасовий запис лишається для повторної спроби.
        pending.wavTemp.copyTo(wavTarget, overwrite = true)
        val m = Measurement(mid, participantId, now(), pending.audioSource, pending.frames, pending.result, wavName)
        writeAtomic(File(dir, "$mid.json"), json.encodeToString(m))
        val oldMid = participant.measurementId
        val updated = c.copy(
            frozenCalibration = c.frozenCalibration ?: loadCalibration(),
            participants = c.participants.map { if (it.id == participantId) it.copy(measurementId = mid) else it },
        )
        saveCompetition(updated)                                  // точка фіксації
        pending.wavTemp.delete()
        oldMid?.let { deleteMeasurementFiles(competitionId, it) } // старе прибираємо лише після неї
        return updated
    }

    fun deleteMeasurement(competitionId: String, participantId: String): Competition {
        val c = require(competitionId)
        val oldMid = c.participants.firstOrNull { it.id == participantId }?.measurementId
        val updated = withoutCalibrationIfEmpty(
            c.copy(participants = c.participants.map { if (it.id == participantId) it.copy(measurementId = null) else it })
        )
        saveCompetition(updated)
        oldMid?.let { deleteMeasurementFiles(competitionId, it) }
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
