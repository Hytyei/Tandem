package app.tandem.data

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

fun parseDate(s: String?): LocalDate? = runCatching { LocalDate.parse(s) }.getOrNull()
fun parseTime(s: String?): LocalTime? = runCatching { LocalTime.parse(s) }.getOrNull()

/** The next date this event happens on or after [today]; null if a one-off event is in the past. */
fun CalendarEvent.nextDate(today: LocalDate = LocalDate.now()): LocalDate? {
    val base = parseDate(date) ?: return null
    if (!repeatYearly) return base.takeIf { !it.isBefore(today) }
    fun inYear(y: Int): LocalDate =
        if (base.monthValue == 2 && base.dayOfMonth == 29 && !java.time.Year.isLeap(y.toLong())) LocalDate.of(y, 2, 28)
        else LocalDate.of(y, base.monthValue, base.dayOfMonth)
    val thisYear = inYear(today.year)
    return if (!thisYear.isBefore(today)) thisYear else inYear(today.year + 1)
}

/** True if this event occurs on [day] (including yearly repeats). */
fun CalendarEvent.occursOn(day: LocalDate): Boolean {
    val base = parseDate(date) ?: return false
    if (!repeatYearly) return base == day
    val monthDay = base.monthValue == day.monthValue && base.dayOfMonth == day.dayOfMonth
    val leapFallback = base.monthValue == 2 && base.dayOfMonth == 29 && !day.isLeapYear && day.monthValue == 2 && day.dayOfMonth == 28
    return !day.isBefore(base) && (monthDay || leapFallback)
}

fun daysUntil(date: LocalDate, today: LocalDate = LocalDate.now()): Long = ChronoUnit.DAYS.between(today, date)

fun countdownLabel(days: Long): String = when {
    days <= 0L -> "Today"
    days == 1L -> "Tomorrow"
    else -> "$days days"
}

fun upcoming(events: List<CalendarEvent>, today: LocalDate = LocalDate.now()): List<Pair<CalendarEvent, LocalDate>> =
    events.mapNotNull { e -> e.nextDate(today)?.let { e to it } }.sortedWith(compareBy({ it.second }, { it.first.time ?: "" }))

fun daysTogether(anniversary: String?, today: LocalDate = LocalDate.now()): Long? =
    parseDate(anniversary)?.let { ChronoUnit.DAYS.between(it, today).coerceAtLeast(0) }

fun timeAgo(ms: Long, now: Long = System.currentTimeMillis()): String {
    val s = ((now - ms) / 1000).coerceAtLeast(0)
    return when {
        s < 45 -> "just now"
        s < 90 * 60 -> "${(s / 60).coerceAtLeast(1)}m ago"
        s < 36 * 3600 -> "${s / 3600}h ago"
        s < 7 * 86400 -> "${s / 86400}d ago"
        else -> Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate().toString()
    }
}

/** The day a diary note is about: its chosen date, or the day it was written. */
fun DiaryEntry.day(): LocalDate =
    parseDate(entryDate) ?: Instant.ofEpochMilli(createdAt).atZone(ZoneId.systemDefault()).toLocalDate()

/** The phone's current UTC offset in minutes (so server-side "days" line up with local midnight). */
fun utcOffsetMinutes(): Int = ZoneId.systemDefault().rules.getOffset(Instant.now()).totalSeconds / 60

/** Special days only, soonest first. */
fun upcomingSpecial(events: List<CalendarEvent>, today: LocalDate = LocalDate.now()) =
    upcoming(events.filter { it.isSpecial }, today)
