package app.decibravo.ui.measure

import app.decibravo.scoring.EndReason
import org.junit.Assert.assertEquals
import org.junit.Test

class MeasureTextsTest {
    @Test fun endReasonTexts() {
        assertEquals("Закінчено: 2 с тиші", endReasonText(EndReason.SILENCE, 2.0)); assertEquals("Закінчено: 1,5 с тиші", endReasonText(EndReason.SILENCE, 1.5))
        assertEquals("Закінчено: досягнуто найдовшого заміру", endReasonText(EndReason.LIMIT, 2.0))
        assertEquals("Закінчено: зупинено вручну", endReasonText(EndReason.STOP, 2.0)); assertEquals("Оплесків не було", endReasonText(EndReason.NO_APPLAUSE, 2.0))
    }
    @Test fun hintAndPlace() {
        assertEquals("Натисніть «Старт», щойно учасник закінчив. Замір зупиниться сам: через 10 с або після 2 с тиші.", readyHint(10, 2.0))
        assertEquals("зараз 3-тє місце", placeText(3))
    }
}
