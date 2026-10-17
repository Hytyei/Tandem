package app.tandem.data

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Symptoms that can be ticked on a day of the period tracker. Keys are what the server stores. */
enum class Symptom(val key: String, val label: String, val emoji: String) {
    Cramps("cramps", "Cramps", "😣"), Headache("headache", "Headache", "🤕"), Backache("backache", "Backache", "🦴"),
    Bloating("bloating", "Bloating", "🎈"), Tender("tender", "Tender", "🥺"), Tired("tired", "Tired", "😴"),
    Moody("moody", "Moody", "🎭"), Cravings("cravings", "Cravings", "🍫"), Acne("acne", "Acne", "🔴"),
    Nausea("nausea", "Nausea", "🤢"), Spotting("spotting", "Spotting", "🩸"), Sleepless("sleepless", "Can't sleep", "🌙");

    companion object { fun from(key: String) = entries.firstOrNull { it.key == key } }
}

enum class CycleDayKind { None, Period, PredictedPeriod, Fertile, Ovulation }

/** Everything the screen needs, worked out from the person's own history. These are estimates, not medical advice. */
class CyclePrediction(
    val avgCycle: Int,
    val avgPeriod: Int,
    val lastStart: LocalDate?,
    val ongoing: PeriodEntry?,
    val nextStart: LocalDate?,
    val ovulation: LocalDate?,
    /** True when at least two periods are logged, so [avgCycle] comes from real history rather than the setting. */
    val learned: Boolean,
)

object CycleMath {
    private fun d(s: String?): LocalDate? = s?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    fun predict(data: PeriodData, today: LocalDate): CyclePrediction {
        val starts = data.periods.mapNotNull { d(it.startDate) }.sorted()
        val gaps = starts.zipWithNext { a, b -> ChronoUnit.DAYS.between(a, b).toInt() }.filter { it in 18..60 }.takeLast(6)
        val avgCycle = if (gaps.isNotEmpty()) Math.round(gaps.average()).toInt() else data.settings.cycleLength
        val lengths = data.periods.mapNotNull { p ->
            val s = d(p.startDate); val e = d(p.endDate)
            if (s != null && e != null) (ChronoUnit.DAYS.between(s, e) + 1).toInt() else null
        }.takeLast(6)
        val avgPeriod = if (lengths.isNotEmpty()) Math.round(lengths.average()).toInt().coerceIn(2, 10) else data.settings.periodLength
        val last = starts.lastOrNull()
        val ongoing = data.periods.firstOrNull { p ->
            val s = d(p.startDate)
            p.endDate == null && s != null && !s.isAfter(today) && ChronoUnit.DAYS.between(s, today) <= 14
        }
        val next = last?.plusDays(avgCycle.toLong())
        return CyclePrediction(avgCycle, avgPeriod, last, ongoing, next, next?.minusDays(14), gaps.isNotEmpty())
    }

    /** What a day of the calendar shows. Predictions only look forward from [today]; real logs always win. */
    fun kindOf(date: LocalDate, data: PeriodData, pred: CyclePrediction, today: LocalDate): CycleDayKind {
        for (p in data.periods) {
            val s = d(p.startDate) ?: continue
            val e = d(p.endDate) ?: minOf(today, s.plusDays(pred.avgPeriod.toLong() - 1 + 4))
            if (!date.isBefore(s) && !date.isAfter(e)) return CycleDayKind.Period
        }
        val last = pred.lastStart ?: return CycleDayKind.None
        if (date.isBefore(last)) return CycleDayKind.None
        for (k in 1..4) {
            val start = last.plusDays((pred.avgCycle * k).toLong())
            val ov = start.minusDays(14)
            if (!date.isBefore(today) && !date.isBefore(start) && !date.isAfter(start.plusDays(pred.avgPeriod.toLong() - 1))) return CycleDayKind.PredictedPeriod
            if (date == ov && !date.isBefore(today)) return CycleDayKind.Ovulation
            if (!date.isBefore(today) && !date.isBefore(ov.minusDays(5)) && !date.isAfter(ov.plusDays(1))) return CycleDayKind.Fertile
        }
        return CycleDayKind.None
    }
}
