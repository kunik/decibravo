package app.decibravo.scoring

data class Entry(val participantId: String, val score: Double, val durationSec: Double, val acceptedAt: Long)
data class Ranked(val place: Int, val entry: Entry)

private val order = compareByDescending<Entry> { it.score }
    .thenByDescending { it.durationSec }
    .thenBy { it.acceptedAt }

fun rank(entries: List<Entry>): List<Ranked> =
    entries.sortedWith(order).mapIndexed { i, e -> Ranked(i + 1, e) }

/** Місце нового результату серед others; за повної рівності новий іде після наявних. */
fun placeIfAccepted(score: Double, durationSec: Double, others: List<Entry>): Int {
    val candidate = Entry("", score, durationSec, Long.MAX_VALUE)
    return others.count { order.compare(it, candidate) <= 0 } + 1
}
