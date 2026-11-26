package app.decibravo.scoring

import java.util.Locale
import kotlin.math.roundToLong

fun fmtScore(score: Double): String = score.roundToLong().toString()

fun fmtSeconds(sec: Double): String = String.format(Locale.US, "%.1f", sec).replace('.', ',')

fun fmtStrength(strength: Double): String = strength.roundToLong().toString()

fun fmtPlace(place: Int): String = when (place) {
    1 -> "1-ше"
    2 -> "2-ге"
    3 -> "3-тє"
    7, 8 -> "$place-ме"
    else -> "$place-те"
}

data class TableRow(val place: Int?, val name: String, val result: Result?)

fun tableText(competitionName: String, rows: List<TableRow>): String =
    (listOf(competitionName) + rows.map { row ->
        val r = row.result
        if (r == null || row.place == null) {
            "— ${row.name}"
        } else {
            "${row.place}. ${row.name} — ${fmtScore(r.score)} (${fmtSeconds(r.durationSec)} с, сила ${fmtStrength(r.strength)})"
        }
    }).joinToString("\n")

fun scoreScale(text: String): Float = if (text.length <= 3) 1f else 3f / text.length
