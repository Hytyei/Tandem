package app.tandem.ui.screens

import androidx.compose.runtime.staticCompositionLocalOf
import app.tandem.data.PerfBody

/** What a round measures about how you play: reaction times, hits and misses. Games fill it in as you go. */
class PerfLog {
    private val ms = ArrayList<Int>()
    var hits = 0; private set
    var misses = 0; private set

    /** One reaction (or answer) time, in milliseconds. */
    fun react(v: Int) { if (v in 1..120_000) ms.add(v) }
    fun hit() { hits++ }
    fun miss() { misses++ }
    fun clear() { ms.clear(); hits = 0; misses = 0 }

    val best: Int get() = ms.minOrNull() ?: 0
    val avg: Int get() = if (ms.isEmpty()) 0 else ms.average().toInt()
    val samples: Int get() = ms.size

    fun toBody(): PerfBody? = if (ms.isEmpty() && hits + misses == 0) null else PerfBody(best, avg, ms.size, hits, misses)
}

val LocalPerf = staticCompositionLocalOf { PerfLog() }

/** The partner's score for this round, when they already played it (games like Blackjack show it as the number to beat). */
val LocalBeat = staticCompositionLocalOf<Int?> { null }
