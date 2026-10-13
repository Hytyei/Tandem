package app.tandem.data

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

/** Everything achievements are computed from (no extra server calls: they are derived from Stats + days together). */
class AchContext(val days: Long, val stats: StatsDetail) {
    val distinctTypes: Int get() = stats.dateTypeCounts.map { it.dateType }.distinct().size
    val typeTotal: Int get() = stats.dateTypeCounts.sumOf { it.n }
}

enum class AchGroup(val label: String, val tint: Color) {
    Together("Time together", Color(0xFFE11D48)),
    Streak("Check-in streaks", Color(0xFFF59E0B)),
    Moods("Moods", Color(0xFFEC4899)),
    Diary("Diary & memories", Color(0xFF6366F1)),
    Calendar("Plans & special days", Color(0xFF0EA5E9)),
    Dates("Dates & adventures", Color(0xFF10B981)),
}

class Achievement(
    val id: String,
    val group: AchGroup,
    val title: String,
    val description: String,
    val icon: ImageVector,
    val target: Int,
    val value: (AchContext) -> Int,
) {
    fun progress(c: AchContext) = value(c).coerceAtMost(target)
    fun unlocked(c: AchContext) = value(c) >= target
}

object Achievements {
    fun context(days: Long, stats: StatsDetail) = AchContext(days, stats)

    private fun days(id: String, n: Int, title: String, desc: String, icon: ImageVector) =
        Achievement(id, AchGroup.Together, title, desc, icon, n) { it.days.toInt() }

    val all: List<Achievement> = listOf(
        days("days7", 7, "First week", "7 days together", Icons.Filled.Favorite),
        days("days30", 30, "One month", "30 days together", Icons.Filled.Today),
        days("days100", 100, "100 days", "100 days together", Icons.Filled.Celebration),
        days("days200", 200, "200 days", "200 days together", Icons.Filled.Loyalty),
        days("days365", 365, "One year", "365 days together", Icons.Filled.Cake),
        days("days500", 500, "500 days", "500 days together", Icons.Filled.AutoAwesome),
        days("days730", 730, "Two years", "730 days together", Icons.Filled.WorkspacePremium),
        days("days1000", 1000, "1000 days", "1000 days together", Icons.Filled.Diamond),

        Achievement("streak3", AchGroup.Streak, "On a roll", "Check in 3 days in a row", Icons.Filled.Bolt, 3) { it.stats.bestStreak },
        Achievement("streak7", AchGroup.Streak, "Full week", "Check in 7 days in a row", Icons.Filled.LocalFireDepartment, 7) { it.stats.bestStreak },
        Achievement("streak30", AchGroup.Streak, "Unbreakable", "Check in 30 days in a row", Icons.Filled.EmojiEvents, 30) { it.stats.bestStreak },
        Achievement("active30", AchGroup.Streak, "Regulars", "30 days with a check-in", Icons.Filled.CalendarMonth, 30) { it.stats.checkInDays },
        Achievement("active100", AchGroup.Streak, "Always there", "100 days with a check-in", Icons.Filled.Verified, 100) { it.stats.checkInDays },

        Achievement("moods10", AchGroup.Moods, "Open book", "Share 10 moods", Icons.Filled.EmojiEmotions, 10) { it.stats.totals.moods },
        Achievement("moods100", AchGroup.Moods, "Mood tracker", "Share 100 moods", Icons.Filled.Timeline, 100) { it.stats.totals.moods },
        Achievement("moods500", AchGroup.Moods, "Emotional compass", "Share 500 moods", Icons.Filled.Explore, 500) { it.stats.totals.moods },

        Achievement("diary1", AchGroup.Diary, "Dear diary", "Pin your first note", Icons.Filled.Edit, 1) { it.stats.totals.diary },
        Achievement("diary10", AchGroup.Diary, "Storytellers", "Pin 10 notes", Icons.Filled.Bookmark, 10) { it.stats.totals.diary },
        Achievement("diary50", AchGroup.Diary, "Memory keepers", "Pin 50 notes", Icons.Filled.Collections, 50) { it.stats.totals.diary },
        Achievement("photo1", AchGroup.Diary, "Say cheese", "Attach your first picture", Icons.Filled.PhotoCamera, 1) { it.stats.photos },
        Achievement("photo25", AchGroup.Diary, "Photo album", "Attach 25 pictures", Icons.Filled.Collections, 25) { it.stats.photos },
        Achievement("rated1", AchGroup.Diary, "Worth remembering", "Rate your first memory", Icons.Filled.Grade, 1) { it.stats.totals.rated },
        Achievement("five1", AchGroup.Diary, "Core memory", "Give a memory 5 hearts", Icons.Filled.Favorite, 1) { it.stats.totals.fiveStar },
        Achievement("five5", AchGroup.Diary, "Golden collection", "Five memories with 5 hearts", Icons.Filled.Star, 5) { it.stats.totals.fiveStar },

        Achievement("events5", AchGroup.Calendar, "Planners", "Add 5 events", Icons.Filled.Event, 5) { it.stats.totals.events },
        Achievement("events25", AchGroup.Calendar, "Always something on", "Add 25 events", Icons.Filled.CalendarMonth, 25) { it.stats.totals.events },
        Achievement("special1", AchGroup.Calendar, "Date to remember", "Add a special day", Icons.Filled.Cake, 1) { it.stats.totals.specials },
        Achievement("special5", AchGroup.Calendar, "Every milestone", "Add 5 special days", Icons.Filled.Celebration, 5) { it.stats.totals.specials },

        Achievement("type3", AchGroup.Dates, "Variety pack", "Try 3 different kinds of dates", Icons.Filled.Explore, 3) { it.distinctTypes },
        Achievement("type8", AchGroup.Dates, "Adventurers", "Try 8 different kinds of dates", Icons.Filled.AutoAwesome, 8) { it.distinctTypes },
        Achievement("things10", AchGroup.Dates, "Quality time", "Log 10 things you did together", Icons.Filled.Loyalty, 10) { it.typeTotal },
        Achievement("things50", AchGroup.Dates, "Never bored", "Log 50 things you did together", Icons.Filled.EmojiEvents, 50) { it.typeTotal },
    )
}

fun unlockedAchievements(c: AchContext): List<Achievement> = Achievements.all.filter { it.unlocked(c) }
