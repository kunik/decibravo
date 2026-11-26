package app.decibravo.ui.edit

import app.decibravo.data.Competition
import app.decibravo.data.Participant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EditFormTest {
    @Test fun steppers_respectBounds() {
        assertEquals(3, EditForm(maxDurationSec = 3).minusMax().maxDurationSec); assertEquals(60, EditForm(maxDurationSec = 60).plusMax().maxDurationSec)
        assertEquals(1.0, EditForm(quietEndSec = 1.0).minusQuiet().quietEndSec, 0.0); assertEquals(5.0, EditForm(quietEndSec = 5.0).plusQuiet().quietEndSec, 0.0)
        assertEquals(2.5, EditForm().plusQuiet().quietEndSec, 0.0); assertEquals(11, EditForm().plusMax().maxDurationSec)
    }
    @Test fun canSave_needsName() { assertFalse(EditForm(name = "  ").canSave); assertTrue(EditForm(name = "Проба").canSave) }
    @Test fun summaryLine_texts() {
        val ps = List(14) { Participant("$it", "п$it") }
        assertEquals("Ще не почато · 14 учасників", summaryLine(Competition("c", "n", 0, participants = ps)))
        assertEquals("1 із 14 замірів", summaryLine(Competition("c", "n", 0, participants = ps.mapIndexed { i, p -> if (i == 0) p.copy(measurementId = "m") else p })))
    }
    @Test fun formAfterLoad_refillsParticipantsAfterUnlock() {
        val form = EditForm("Назва", "A\nB\nC", 12, 3.0)
        val afterRemove = Competition("c", "Назва", 0, participants = listOf(Participant("b", "B"), Participant("c", "C")))
        val f = formAfterLoad(form, afterRemove, wasLocked = true, fillAll = false)
        assertEquals("B\nC", f.participantsText); assertEquals("Назва", f.name); assertEquals(12, f.maxDurationSec); assertEquals(3.0, f.quietEndSec, 0.0)
    }
    @Test fun formAfterLoad_refillsWhileLocked_andKeepsTypedTextOtherwise() {
        val locked = Competition("c", "n", 0, participants = listOf(Participant("a", "A", "m"), Participant("d", "D")))
        assertEquals("A\nD", formAfterLoad(EditForm(participantsText = "A"), locked, wasLocked = false, fillAll = false).participantsText)
        val open = Competition("c", "n", 0, participants = listOf(Participant("a", "A")))
        assertEquals("A\nнабране", formAfterLoad(EditForm(participantsText = "A\nнабране"), open, wasLocked = false, fillAll = false).participantsText)
    }
    @Test fun formAfterLoad_fillAllTakesEverything() {
        val c = Competition("c", "Диск", 0, maxDurationSec = 20, quietEndSec = 1.5, participants = listOf(Participant("a", "A")))
        assertEquals(EditForm("Диск", "A", 20, 1.5), formAfterLoad(EditForm(), c, wasLocked = false, fillAll = true))
    }
}
