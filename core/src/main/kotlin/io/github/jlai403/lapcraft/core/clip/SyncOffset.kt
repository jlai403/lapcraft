package io.github.jlai403.lapcraft.core.clip

/**
 * Parses the HUD lap time on a picked frame into milliseconds.
 * Accepts the shapes GT7 actually shows: `0.019`, `.019`, `0:00.019`, `0:12.345`.
 * A bare integer is rejected on purpose — 19 could mean 19 ms or 19 s, and guessing is
 * worse than asking the user to type the dot.
 */
object SyncOffset {
    fun parse(text: String): Long? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null
        if (trimmed.startsWith("-")) return null
        val colon = trimmed.indexOf(':')
        val base = if (colon >= 0) trimmed.substring(colon + 1) else trimmed
        val dot = base.indexOf('.')
        if (dot < 0) return null
        val whole = base.substring(0, dot).trim()
        val frac = base.substring(dot + 1).trim()
        if (frac.length !in 1..3) return null
        val wholeSec = if (whole.isEmpty()) 0L else (whole.toLongOrNull() ?: return null)
        if (wholeSec < 0) return null
        val millis = (frac + "000").take(3).toLongOrNull() ?: return null
        val totalMs = wholeSec * 1000L + millis
        if (totalMs < 0) return null
        return totalMs
    }
}