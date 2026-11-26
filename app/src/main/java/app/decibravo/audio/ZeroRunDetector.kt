package app.decibravo.audio

/** Скільки кадрів поспіль із самих нулів вважаємо збоєм запису: 10 кадрів = 1 с. Справжня тиша в тихій кімнаті нулем не буває. */
const val SILENT_RUN_FRAMES = 10

/** Помічає «мовчазний» запис: мікрофон зайнятий чи заглушений системою й віддає лише нулі. */
class ZeroRunDetector(private val limit: Int = SILENT_RUN_FRAMES) {
    private var run = 0

    /** true, коли поспіль набралося [limit] кадрів, у яких кожен відлік дорівнює 0. */
    fun push(pcm: ShortArray): Boolean {
        run = if (pcm.all { it.toInt() == 0 }) run + 1 else 0
        return run >= limit
    }
}

class SilentCaptureException : IllegalStateException("Мікрофон віддає лише нулі")
