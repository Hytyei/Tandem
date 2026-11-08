package app.tandem.data

import android.icu.util.IslamicCalendar
import android.icu.util.ULocale
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** The little seasonal effect drawn over Today on an occasion. */
enum class OccasionFx { Snow, NewYear, Hearts, Petals, Embers, Leaves, Halloween, Yalda, Sparkles }

/**
 * Days worth marking that the app adds by itself: they show in the calendar, in "Coming up" and on Today,
 * where a small effect plays on the day itself. Persian dates (Nowruz, Yalda, Sepandarmazgan, Chaharshanbe
 * Suri, Sizdah Bedar) come from the Persian calendar and Iran's Girls' Day from the Islamic one, so they land
 * on the right day every year.
 */
class Holiday(val key: String, val name: String, val emoji: String, val fx: OccasionFx, val greeting: String, val line: String)

object Occasions {
    /** For testing: shows this occasion's effect today (set from a debug intent). */
    @Volatile var forced: String? = null

    private val all = listOf(
        Holiday("newyearseve", "New Year's Eve", "🥂", OccasionFx.NewYear, "Happy New Year's Eve", "One more sunset this year. Spend it close"),
        Holiday("newyear", "New Year's Day", "🎆", OccasionFx.NewYear, "Happy New Year", "A whole new year of the two of you"),
        Holiday("valentine", "Valentine's Day", "💘", OccasionFx.Hearts, "Happy Valentine's Day", "Today is a very good day to say it out loud"),
        Holiday("sepandarmazgan", "Sepandarmazgan", "🌹", OccasionFx.Hearts, "Happy Sepandarmazgan", "The old Persian day of love. Say something kind"),
        Holiday("womensday", "International Women's Day", "💐", OccasionFx.Petals, "Happy Women's Day", "For every woman who makes the world softer and stronger"),
        Holiday("chaharshanbesuri", "Chaharshanbe Suri", "🔥", OccasionFx.Embers, "Happy Chaharshanbe Suri", "Jump over the fire and leave the year's worries behind"),
        Holiday("nowruz", "Nowruz", "🌱", OccasionFx.Petals, "Nowruz mobarak", "A new spring, a new year. Eid-etoon mobarak"),
        Holiday("sizdahbedar", "Sizdah Bedar", "🌿", OccasionFx.Leaves, "Happy Sizdah Bedar", "Go outside together and tie a knot in the grass"),
        Holiday("kissday", "International Kissing Day", "💋", OccasionFx.Hearts, "Happy Kissing Day", "You know what to do"),
        Holiday("girlfriendday", "International Girlfriend Day", "💗", OccasionFx.Hearts, "Happy Girlfriend Day", "A good day to spoil her a little"),
        Holiday("boyfriendday", "National Boyfriend Day", "💙", OccasionFx.Sparkles, "Happy Boyfriend Day", "A good day to spoil him a little"),
        Holiday("girlsdayiran", "Girls' Day", "🌸", OccasionFx.Petals, "Happy Girls' Day", "Roozet mobarak to the girl who lights up this app"),
        Holiday("dayofthegirl", "Day of the Girl", "🌷", OccasionFx.Petals, "Happy Day of the Girl", "Here's to every girl, and one in particular"),
        Holiday("halloween", "Halloween", "🎃", OccasionFx.Halloween, "Happy Halloween", "Trick or treat. Preferably treat"),
        Holiday("yalda", "Yalda Night", "🍉", OccasionFx.Yalda, "Happy Yalda", "The longest night of the year. Make it last"),
        Holiday("christmas", "Christmas", "🎄", OccasionFx.Snow, "Merry Christmas", "Warm socks, warm drinks, warm hands"),
    ).associateBy { it.key }

    fun byKey(key: String) = all[key]

    /** Every occasion's date in [year] (Gregorian). */
    fun datesIn(year: Int): List<Pair<Holiday, LocalDate>> {
        val out = mutableListOf<Pair<Holiday, LocalDate>>()
        fun add(key: String, d: LocalDate?) { val o = all[key]; if (o != null && d != null && d.year == year) out += o to d }
        add("newyear", LocalDate.of(year, 1, 1))
        add("valentine", LocalDate.of(year, 2, 14))
        add("womensday", LocalDate.of(year, 3, 8))
        add("kissday", LocalDate.of(year, 7, 6))
        add("girlfriendday", LocalDate.of(year, 8, 1))
        add("boyfriendday", LocalDate.of(year, 10, 3))
        add("dayofthegirl", LocalDate.of(year, 10, 11))
        add("halloween", LocalDate.of(year, 10, 31))
        add("christmas", LocalDate.of(year, 12, 25))
        add("newyearseve", LocalDate.of(year, 12, 31))
        // Persian: Nowruz is 1 Farvardin, Sizdah Bedar 13 Farvardin, Sepandarmazgan 29 Bahman, Yalda the night of 30 Azar
        val nowruz = persian(year, 0, 1)
        add("nowruz", nowruz)
        add("sizdahbedar", nowruz?.plusDays(12))
        add("sepandarmazgan", persianInGregorianYear(year, 10, 29))
        add("yalda", persianInGregorianYear(year, 8, 30))
        // Chaharshanbe Suri: the evening of the last Tuesday before Nowruz (the eve of its last Wednesday)
        nowruz?.let { n -> add("chaharshanbesuri", n.minusDays(1).with(TemporalAdjusters.previousOrSame(DayOfWeek.WEDNESDAY)).minusDays(1)) }
        // Iran's Girls' Day: 1 Dhu al-Qa'dah
        islamicInGregorianYear(year, 10, 1).forEach { add("girlsdayiran", it) }
        return out.sortedBy { it.second }
    }

    /** The occasion today, if there is one (or the one forced for testing). */
    fun today(today: LocalDate = LocalDate.now()): Holiday? =
        forced?.let { all[it] } ?: datesIn(today.year).firstOrNull { it.second == today }?.first

    /** Occasions as calendar events, for this year and the next. */
    fun events(today: LocalDate = LocalDate.now()): List<CalendarEvent> =
        (datesIn(today.year) + datesIn(today.year + 1)).map { (o, d) ->
            CalendarEvent(id = "occ:${o.key}:${d.year}", title = o.name, emoji = o.emoji, date = d.toString(), kind = OCCASION)
        }

    const val OCCASION = "occasion"

    // ------------------------------------------------------------------ calendars

    /** The Gregorian date of [day] of Persian month [month] (0 = Farvardin) of the Persian year that starts in Gregorian [gYear]. */
    private fun persian(gYear: Int, month: Int, day: Int): LocalDate? = runCatching {
        val cal = android.icu.util.Calendar.getInstance(android.icu.util.TimeZone.GMT_ZONE, ULocale("en@calendar=persian"))
        cal.clear()
        cal.set(android.icu.util.Calendar.EXTENDED_YEAR, gYear - 621)
        cal.set(android.icu.util.Calendar.MONTH, month)
        cal.set(android.icu.util.Calendar.DAY_OF_MONTH, day)
        val g = android.icu.util.GregorianCalendar(android.icu.util.TimeZone.GMT_ZONE)
        g.timeInMillis = cal.timeInMillis
        LocalDate.of(g.get(android.icu.util.Calendar.YEAR), g.get(android.icu.util.Calendar.MONTH) + 1, g.get(android.icu.util.Calendar.DAY_OF_MONTH))
    }.getOrNull()

    /** A Persian month/day that falls inside Gregorian [gYear] (it may belong to either Persian year that overlaps it). */
    private fun persianInGregorianYear(gYear: Int, month: Int, day: Int): LocalDate? =
        listOf(persian(gYear - 1, month, day), persian(gYear, month, day)).firstOrNull { it?.year == gYear }

    /** Islamic month/day dates (month 0-based) inside Gregorian [gYear]: lunar years are shorter, so there can be two. */
    private fun islamicInGregorianYear(gYear: Int, month: Int, day: Int): List<LocalDate> = runCatching {
        val out = mutableListOf<LocalDate>()
        val probe = IslamicCalendar(android.icu.util.TimeZone.GMT_ZONE, ULocale.ENGLISH); probe.calculationType = IslamicCalendar.CalculationType.ISLAMIC_UMALQURA
        val g0 = android.icu.util.GregorianCalendar(android.icu.util.TimeZone.GMT_ZONE).apply { clear(); set(gYear, 0, 1) }
        probe.timeInMillis = g0.timeInMillis
        val startYear = probe.get(android.icu.util.Calendar.EXTENDED_YEAR)
        for (y in startYear..startYear + 1) {
            val cal = IslamicCalendar(android.icu.util.TimeZone.GMT_ZONE, ULocale.ENGLISH); cal.calculationType = IslamicCalendar.CalculationType.ISLAMIC_UMALQURA
            cal.clear()
            cal.set(android.icu.util.Calendar.EXTENDED_YEAR, y); cal.set(android.icu.util.Calendar.MONTH, month); cal.set(android.icu.util.Calendar.DAY_OF_MONTH, day)
            val g = android.icu.util.GregorianCalendar(android.icu.util.TimeZone.GMT_ZONE); g.timeInMillis = cal.timeInMillis
            val d = LocalDate.of(g.get(android.icu.util.Calendar.YEAR), g.get(android.icu.util.Calendar.MONTH) + 1, g.get(android.icu.util.Calendar.DAY_OF_MONTH))
            if (d.year == gYear) out += d
        }
        out
    }.getOrDefault(emptyList())
}

val CalendarEvent.isOccasion: Boolean get() = kind == Occasions.OCCASION
