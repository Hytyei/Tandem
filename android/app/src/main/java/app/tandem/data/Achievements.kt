package app.tandem.data

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

/** Everything achievements are computed from (no extra server calls: they are derived from Stats + days together). */
class AchContext(
    val days: Long,
    val stats: StatsDetail,
    /** Games and the cosmos (pets, galaxy) when they're loaded; achievements about them wait until then. */
    val games: GameStats? = null,
    val cosmos: CosmosState? = null,
    val now: Long = System.currentTimeMillis(),
) {
    val distinctTypes: Int get() = stats.dateTypeCounts.map { it.dateType }.distinct().size
    val typeTotal: Int get() = stats.dateTypeCounts.sumOf { it.n }
    private val pets get() = cosmos?.pets.orEmpty()
    val gamesPlayed: Int get() = games?.bests?.map { it.game }?.distinct()?.size ?: 0
    val bestWinStreak: Int get() = games?.players?.maxOfOrNull { it.bestStreak } ?: 0
    val roundsPlayed: Int get() = games?.players?.sumOf { it.rounds } ?: 0
    val topBond: Int get() = pets.flatMap { it.bond.values }.maxOrNull()?.toInt() ?: 0
    val lovedByBoth: Int get() = if (pets.any { p -> p.bond.size >= 2 && p.bond.values.all { it >= 60.0 } }) 1 else 0
    val allHappy: Int get() = if (pets.isNotEmpty() && pets.all { it.mood == "happy" }) 1 else 0
    val oldestPetDays: Int get() = pets.minOfOrNull { it.born }?.let { ((now - it) / 86_400_000L).toInt() } ?: 0
    val speciesKept: Int get() = pets.map { it.species }.distinct().size
    val rerolled: Int get() = cosmos?.log?.count { it.kind == "galaxy" } ?: 0
}

enum class AchGroup(val label: String, val tint: Color) {
    Together("Time together", Color(0xFFE11D48)),
    Streak("Check-in streaks", Color(0xFFF59E0B)),
    Moods("Moods", Color(0xFFEC4899)),
    Diary("Diary & memories", Color(0xFF6366F1)),
    Calendar("Plans & special days", Color(0xFF0EA5E9)),
    Dates("Dates & adventures", Color(0xFF10B981)),
    Games("Games", Color(0xFF8B5CF6)),
    Pets("Pets", Color(0xFFF59E0B)),
    Galaxy("Galaxy", Color(0xFF38BDF8)),
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
    fun context(days: Long, stats: StatsDetail, games: GameStats? = null, cosmos: CosmosState? = null) = AchContext(days, stats, games, cosmos)

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
        Achievement("place1", AchGroup.Dates, "First pin", "Add your first place to the Heatmap", Icons.Filled.Place, 1) { it.stats.totals.places },
        Achievement("place10", AchGroup.Dates, "Explorers", "Add 10 places", Icons.Filled.Map, 10) { it.stats.totals.places },
        Achievement("place30", AchGroup.Dates, "Globetrotters", "Add 30 places", Icons.Filled.Explore, 30) { it.stats.totals.places },
        Achievement("review1", AchGroup.Dates, "Critics", "Review a place", Icons.Filled.RateReview, 1) { it.stats.totals.placeReviews },
        Achievement("review20", AchGroup.Dates, "Taste makers", "Write 20 place reviews", Icons.Filled.Star, 20) { it.stats.totals.placeReviews },
        Achievement("things10", AchGroup.Dates, "Quality time", "Log 10 things you did together", Icons.Filled.Loyalty, 10) { it.typeTotal },
        Achievement("things50", AchGroup.Dates, "Never bored", "Log 50 things you did together", Icons.Filled.EmojiEvents, 50) { it.typeTotal },
        Achievement("place50", AchGroup.Dates, "Cartographers", "Put 50 places on your Heatmap", Icons.Filled.TravelExplore, 50) { it.stats.totals.places },
        Achievement("review50", AchGroup.Dates, "The guidebook", "Write 50 place reviews", Icons.Filled.MenuBook, 50) { it.stats.totals.placeReviews },

        Achievement("match1", AchGroup.Games, "Game on", "Finish your first match", Icons.Filled.SportsEsports, 1) { it.games?.done ?: 0 },
        Achievement("match10", AchGroup.Games, "Rivals", "Finish 10 matches", Icons.Filled.Bolt, 10) { it.games?.done ?: 0 },
        Achievement("match50", AchGroup.Games, "Old rivals", "Finish 50 matches", Icons.Filled.MilitaryTech, 50) { it.games?.done ?: 0 },
        Achievement("match200", AchGroup.Games, "Arcade legends", "Finish 200 matches", Icons.Filled.EmojiEvents, 200) { it.games?.done ?: 0 },
        Achievement("draw1", AchGroup.Games, "Perfectly matched", "End a match in a draw", Icons.Filled.Balance, 1) { it.games?.draws ?: 0 },
        Achievement("streak5w", AchGroup.Games, "Unstoppable", "Win 5 matches in a row", Icons.Filled.LocalFireDepartment, 5) { it.bestWinStreak },
        Achievement("games8", AchGroup.Games, "Explorers of play", "Play 8 different games", Icons.Filled.Extension, 8) { it.gamesPlayed },
        Achievement("gamesAll", AchGroup.Games, "Played them all", "Play every game at least once", Icons.Filled.Verified, MiniGame.playable.size) { it.gamesPlayed },
        Achievement("rounds250", AchGroup.Games, "Just one more", "Play 250 rounds between you", Icons.Filled.Replay, 250) { it.roundsPlayed },
        Achievement("tandem10k", AchGroup.Games, "Team score", "Reach a Tandem score of 10,000", Icons.Filled.Star, 10_000) { it.games?.tandemScore ?: 0 },

        Achievement("pet1", AchGroup.Pets, "New family member", "Adopt your first pet", Icons.Filled.Pets, 1) { it.cosmos?.pets?.size ?: 0 },
        Achievement("pet3", AchGroup.Pets, "Full house", "Look after three pets at once", Icons.Filled.Home, 3) { it.cosmos?.pets?.size ?: 0 },
        Achievement("petMix", AchGroup.Pets, "Mixed household", "Keep two different kinds of pet", Icons.Filled.Diversity1, 2) { it.speciesKept },
        Achievement("bond80", AchGroup.Pets, "Best friends", "Reach a bond of 80 with a pet", Icons.Filled.Favorite, 80) { it.topBond },
        Achievement("bondBoth", AchGroup.Pets, "Loved by both", "A pet with a bond of 60 with each of you", Icons.Filled.FavoriteBorder, 1) { it.lovedByBoth },
        Achievement("petHappy", AchGroup.Pets, "Happy home", "Every pet happy at the same time", Icons.Filled.SentimentVerySatisfied, 1) { it.allHappy },
        Achievement("pet30", AchGroup.Pets, "Old friend", "Keep a pet for 30 days", Icons.Filled.Cake, 30) { it.oldestPetDays },
        Achievement("pet100", AchGroup.Pets, "Part of the family", "Keep a pet for 100 days", Icons.Filled.WorkspacePremium, 100) { it.oldestPetDays },

        Achievement("dust1k", AchGroup.Galaxy, "Stargazers", "Earn 1,000 stardust", Icons.Filled.AutoAwesome, 1_000) { it.cosmos?.lifetime ?: 0 },
        Achievement("dust5k", AchGroup.Galaxy, "Star makers", "Earn 5,000 stardust", Icons.Filled.Star, 5_000) { it.cosmos?.lifetime ?: 0 },
        Achievement("dust15k", AchGroup.Galaxy, "Fully formed", "Earn 15,000 stardust, a galaxy at its fullest", Icons.Filled.Public, 15_300) { it.cosmos?.lifetime ?: 0 },
        Achievement("comet3", AchGroup.Galaxy, "Comet catchers", "Hold three comets at once", Icons.Filled.Bolt, 3) { it.cosmos?.comets ?: 0 },
        Achievement("reroll1", AchGroup.Galaxy, "A new sky", "Re-roll your galaxy and keep a new one", Icons.Filled.Refresh, 1) { it.rerolled },
        Achievement("cosmosMs5", AchGroup.Galaxy, "Milestone hunters", "Reach 5 stardust milestones", Icons.Filled.Flag, 5) { it.cosmos?.milestones?.size ?: 0 },
    )

    /** Groups whose achievements need data that isn't loaded yet in [c]. */
    fun waiting(c: AchContext): Set<AchGroup> = buildSet { if (c.games == null) add(AchGroup.Games); if (c.cosmos == null) { add(AchGroup.Pets); add(AchGroup.Galaxy) } }
}

fun unlockedAchievements(c: AchContext): List<Achievement> = Achievements.all.filter { it.unlocked(c) }
