package app.tandem.data

import kotlinx.serialization.Serializable

@Serializable
data class User(
    val id: String,
    val username: String,
    val displayName: String,
    val bio: String = "",
    val avatarVersion: Int = 0,
    val hasAvatar: Boolean = false,
    val profile: ProfileDetails = ProfileDetails(),
)

/** The personal page each partner fills in and the other can read. Sent as-is to PUT /me/profile. */
@Serializable
data class ProfileDetails(
    val fullName: String = "",
    val birthday: String = "",                 // yyyy-MM-dd or ""
    val colors: List<String> = emptyList(),    // ProfileColor keys (max 3)
    val hobbies: List<String> = emptyList(),
    val sport: String = "",
    val food: String = "",
    val music: String = "",
    val loveLanguage: String = "",             // LoveLanguage key
    val watching: String = "",
    val playing: String = "",
    val listening: String = "",
    val reading: String = "",
    val meters: Map<String, Int> = emptyMap(), // Meter keys -> 0..100
    val nowAt: Long = 0,                       // when "right now" last changed
    val metersAt: Long = 0,
    val media: Map<String, MediaInfo> = emptyMap(), // "watching"/"playing"/"listening"/"reading" -> poster + short info
) {
    fun mediaFor(kind: String): MediaInfo? = media[kind]?.takeIf { it.title.isNotBlank() }
    val hasNow get() = watching.isNotBlank() || playing.isNotBlank() || listening.isNotBlank() || reading.isNotBlank()
    val isEmpty get() = fullName.isBlank() && birthday.isBlank() && colors.isEmpty() && hobbies.isEmpty() && sport.isBlank() &&
        food.isBlank() && music.isBlank() && loveLanguage.isBlank() && !hasNow && meters.isEmpty()
}

/** Poster / cover and a short description for a "now watching / playing / listening / reading" title. */
@Serializable
data class MediaInfo(
    val title: String = "",
    val sub: String = "",      // e.g. "2020 action role-playing video game" or "Artist · Album"
    val blurb: String = "",    // short description
    val img: String = "",      // https image on an allow-listed host
    val src: String = "",      // wikipedia | itunes | openlibrary
)

@Serializable
data class CoupleInfo(
    val paired: Boolean = false,
    val inviteCode: String? = null,
    val anniversary: String? = null,
    val partner: User? = null,
)

@Serializable
data class MoodEntry(
    val id: String,
    val userId: String,
    val mood: String,
    val activity: String? = null,
    val note: String = "",
    val createdAt: Long,
    val reactions: List<Reaction> = emptyList(),
)

/** One emoji reaction (each person can leave one per mood / diary note). */
@Serializable data class Reaction(val userId: String, val emoji: String)
@Serializable data class ReactionBody(val type: String, val id: String, val emoji: String?)
@Serializable data class ReactionsResponse(val reactions: List<Reaction> = emptyList())

@Serializable
data class DiaryEntry(
    val id: String,
    val userId: String,
    val text: String,
    val mood: String? = null,
    val createdAt: Long,
    val entryDate: String? = null,          // yyyy-MM-dd the note is about (may be a past day)
    val images: List<String> = emptyList(), // picture ids, fetched from /diary/image/{id}
    val rating: Int? = null,                // 1..5 hearts
    val dateType: String? = null,           // DateType.key
    val updatedAt: Long = 0,                // > 0 once the note has been edited
    val placeId: String? = null,            // Place.id this memory happened at
    val placeName: String? = null,
    val reactions: List<Reaction> = emptyList(),
)

@Serializable
data class CalendarEvent(
    val id: String,
    val createdBy: String = "",
    val title: String,
    val emoji: String = "❤️",
    val date: String,          // yyyy-MM-dd
    val time: String? = null,  // HH:mm
    val repeatYearly: Boolean = false,
    val notes: String = "",
    val kind: String = "event",      // "event" | "special"
    val category: String? = null,    // SpecialCategory.key for special days
    val updatedAt: Long = 0,
    val updatedBy: String? = null,
    val dateType: String? = null,    // DateType.key
) {
    val isSpecial: Boolean get() = kind == "special"
}

@Serializable
data class Stats(
    val moods: Int = 0,
    val diary: Int = 0,
    val activeDays: Int = 0,
)

@Serializable
data class SyncResponse(
    val serverTime: Long = 0,
    val me: User,
    val couple: CoupleInfo = CoupleInfo(),
    val moods: List<MoodEntry> = emptyList(),
    val latestMoods: Map<String, MoodEntry> = emptyMap(),
    val diary: List<DiaryEntry> = emptyList(),
    val events: List<CalendarEvent> = emptyList(),
    val stats: Stats? = null,
    val trip: Trip? = null,               // the live-tracked date that is in progress, if any
)

@Serializable data class AuthResponse(val token: String, val user: User)
@Serializable data class MeResponse(val user: User, val couple: CoupleInfo)
@Serializable data class MoodsResponse(val moods: List<MoodEntry>)
@Serializable data class DiaryResponse(val entries: List<DiaryEntry>)
@Serializable data class EventsResponse(val events: List<CalendarEvent>)
@Serializable data class ErrorBody(val error: String = "")

@Serializable data class RegisterBody(val username: String, val password: String, val displayName: String)
@Serializable data class LoginBody(val username: String, val password: String)
@Serializable data class ProfileBody(val displayName: String, val bio: String)
@Serializable data class JoinBody(val code: String)
@Serializable data class AnniversaryBody(val anniversary: String)
@Serializable data class MoodBody(val mood: String, val activity: String?, val note: String)
@Serializable data class DiaryBody(
    val text: String, val mood: String?, val entryDate: String? = null,
    val rating: Int? = null, val dateType: String? = null, val placeId: String? = null,
)
@Serializable
data class EventBody(
    val title: String, val emoji: String, val date: String,
    val time: String?, val repeatYearly: Boolean, val notes: String,
    val kind: String = "event", val category: String? = null, val dateType: String? = null,
)
/** "Add as an occasion to the calendar" chosen while posting a diary note. */
data class Occasion(val title: String, val yearly: Boolean)
data class DiaryResult(val failedPictures: Int, val occasionFailed: Boolean)
@Serializable data class OkResponse(val ok: Boolean = true)
@Serializable data class ImageId(val id: String)
@Serializable data class DeleteBody(val password: String)

// ---- /stats (the Stats screen)
@Serializable data class MoodCount(val userId: String, val mood: String, val n: Int)
@Serializable data class ActivityCount(val userId: String, val activity: String, val n: Int)
@Serializable data class DailyCount(val day: String, val userId: String, val n: Int)
@Serializable data class DiaryCount(val userId: String, val n: Int)
@Serializable
data class StatsDetail(
    val moodCounts: List<MoodCount> = emptyList(),
    val activityCounts: List<ActivityCount> = emptyList(),
    val daily: List<DailyCount> = emptyList(),
    val diaryCounts: List<DiaryCount> = emptyList(),
    val photos: Int = 0,
    val streak: Int = 0,
    val bestStreak: Int = 0,
    val checkInDays: Int = 0,
    val dateTypeCounts: List<DateTypeCount> = emptyList(),
    val totals: StatTotals = StatTotals(),
)
@Serializable data class DateTypeCount(val dateType: String, val n: Int)
@Serializable
data class StatTotals(
    val moods: Int = 0, val diary: Int = 0, val events: Int = 0, val specials: Int = 0,
    val rated: Int = 0, val fiveStar: Int = 0, val places: Int = 0, val placeReviews: Int = 0,
)

// ---- places (Heatmap tab)
@Serializable data class PlaceReview(val userId: String, val rating: Int, val comment: String = "", val updatedAt: Long = 0)
@Serializable
data class Place(
    val id: String,
    val createdBy: String = "",
    val name: String,
    val category: String = "other",
    val lat: Double,
    val lon: Double,
    val note: String = "",
    val visits: Int = 1,
    val firstVisited: String? = null,
    val lastVisited: String? = null,
    val createdAt: Long = 0,
    val reviews: List<PlaceReview> = emptyList(),
    val avg: Double? = null,
    val count: Int = 0,
    val images: List<String> = emptyList(),   // picture ids, fetched from /places/images/{id}
) {
    fun reviewOf(userId: String?) = reviews.firstOrNull { it.userId == userId }
}
@Serializable data class PlacesResponse(val places: List<Place> = emptyList())
@Serializable data class PlaceBody(
    val name: String, val category: String, val lat: Double, val lon: Double, val note: String = "", val visitDate: String? = null,
    /** How many times you have been there (1..999). Always send it when editing; optional (null = 1) when adding. */
    val visits: Int? = null,
)
@Serializable data class ReviewBody(val rating: Int?, val comment: String = "")
/** One hit from the place-name search. */
data class GeoResult(val name: String, val detail: String, val lat: Double, val lon: Double)

// ---- /memories ("remember when" in the Calendar tab)
@Serializable
data class Memory(
    val kind: String,                 // "diary" | "event"
    val date: String,                 // yyyy-MM-dd
    val yearsAgo: Int = 0,
    val diary: DiaryEntry? = null,
    val event: CalendarEvent? = null,
)
@Serializable
data class Memories(
    val onThisDay: List<Memory> = emptyList(),
    val lastMonth: List<Memory> = emptyList(),
    val bestOfMonth: List<Memory> = emptyList(),
    val bestEver: List<Memory> = emptyList(),
) {
    val isEmpty get() = onThisDay.isEmpty() && lastMonth.isEmpty() && bestOfMonth.isEmpty() && bestEver.isEmpty()
}


// ---- minigames (turn-based, timed)
@Serializable data class GameScore(val round: Int, val userId: String, val score: Int)
@Serializable
data class Match(
    val id: String,
    val game: String,
    val rounds: Int,
    val createdBy: String,
    val status: String = "active",          // active | done
    val winnerId: String? = null,           // null + done = draw
    val seed: Long = 0,
    val createdAt: Long = 0,
    val finishedAt: Long = 0,
    val scores: List<GameScore> = emptyList(),
) {
    val isDone get() = status == "done"
    fun scoreOf(round: Int, userId: String): Int? = scores.firstOrNull { it.round == round && it.userId == userId }?.score
    fun total(userId: String) = scores.filter { it.userId == userId }.sumOf { it.score }
    fun roundWins(userId: String, otherId: String) = (1..rounds).count { r ->
        val a = scoreOf(r, userId); val b = scoreOf(r, otherId); a != null && b != null && a > b
    }
    /** Who plays first in [round]: the starter in odd rounds, the other player in even rounds. */
    fun firstIn(round: Int, me: String, partner: String) = if (round % 2 == 1) createdBy else if (createdBy == me) partner else me
    /** The round [me] can play right now, or null (waiting for the partner, or finished). */
    fun nextTurn(me: String, partner: String): Int? {
        if (isDone) return null
        for (r in 1..rounds) {
            val mine = scoreOf(r, me); val theirs = scoreOf(r, partner)
            if (mine != null && theirs != null) continue
            return if (mine == null && (firstIn(r, me, partner) == me || theirs != null)) r else null
        }
        return null
    }
    /** The round currently being played (for "Round 2 of 3"). */
    fun currentRound(): Int = (1..rounds).firstOrNull { r -> scores.count { it.round == r } < 2 } ?: rounds
}
@Serializable
data class GameGoal(
    val id: String,
    val title: String,
    val metric: String,                     // wins | points
    val target: Int,
    val status: String = "active",          // active | won | settled
    val winnerId: String? = null,
    val createdBy: String = "",
    val createdAt: Long = 0,
    val wonAt: Long = 0,
    val settledAt: Long = 0,
    val progress: Map<String, Int> = emptyMap(),
)
@Serializable data class PlayerStat(val userId: String, val wins: Int = 0, val points: Int = 0, val rounds: Int = 0, val streak: Int = 0, val bestStreak: Int = 0)
@Serializable data class BestRow(val game: String, val userId: String, val best: Int = 0, val plays: Int = 0, val total: Int = 0)
@Serializable data class GameWinRow(val game: String, val userId: String? = null, val n: Int = 0)
@Serializable data class HighRow(val game: String, val userId: String, val score: Int = 0, val at: Long = 0)
@Serializable
data class GameStats(
    val done: Int = 0,
    val draws: Int = 0,
    val tandemScore: Int = 0,
    val players: List<PlayerStat> = emptyList(),
    val bests: List<BestRow> = emptyList(),
    val gameWins: List<GameWinRow> = emptyList(),
    val highs: List<HighRow> = emptyList(),
)
@Serializable data class GamesResponse(val matches: List<Match> = emptyList(), val goals: List<GameGoal> = emptyList(), val stats: GameStats = GameStats())
@Serializable data class MatchBody(val game: String, val rounds: Int)
@Serializable data class ScoreBody(val round: Int, val score: Int)
@Serializable data class GoalBody(val title: String, val metric: String, val target: Int)


// ---- saved addresses (home etc., shown on the Heatmap map)
@Serializable
data class SavedAddress(
    val id: String,
    val userId: String,
    val label: String,
    val kind: String = "home",
    val address: String = "",
    val lat: Double,
    val lon: Double,
    val updatedAt: Long = 0,
)
@Serializable data class AddressesResponse(val addresses: List<SavedAddress> = emptyList())
@Serializable data class AddressBody(val label: String, val kind: String, val address: String, val lat: Double, val lon: Double)

// ---- shared notes + checklists (Calendar tab)
@Serializable data class NoteItem(val id: String, val text: String, val done: Boolean = false, val doneBy: String? = null, val position: Int = 0)
@Serializable
data class Note(
    val id: String,
    val createdBy: String = "",
    val title: String,
    val body: String = "",
    val kind: String = "note",          // note | list
    val date: String? = null,           // yyyy-MM-dd this note belongs to (optional)
    val pinned: Boolean = false,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
    val updatedBy: String? = null,
    val items: List<NoteItem> = emptyList(),
) {
    val isList get() = kind == "list"
    val doneCount get() = items.count { it.done }
}
@Serializable data class NotesResponse(val notes: List<Note> = emptyList())
/** Used for create AND edit (the server ignores kind/items on edit). */
@Serializable data class NoteBody(
    val title: String, val body: String = "", val kind: String = "note", val date: String? = null, val pinned: Boolean = false,
    val items: List<String> = emptyList(),
)
@Serializable data class NewItemBody(val text: String)
@Serializable data class ItemTextBody(val text: String)
@Serializable data class ItemDoneBody(val done: Boolean)

// ---- live-tracked trips ("we're on a date")
@Serializable
data class TripMember(val userId: String, val joinedAt: Long = 0, val leftAt: Long? = null, val distanceM: Long = 0, val points: Int = 0) {
    val recording get() = leftAt == null
}
@Serializable
data class Trip(
    val id: String,
    val title: String = "Our date",
    val status: String = "active",       // active | done
    val startedBy: String = "",
    val startedAt: Long = 0,
    val endedAt: Long? = null,
    val durationMs: Long = 0,
    val distanceM: Long = 0,             // the longer of the recorded paths
    val members: List<TripMember> = emptyList(),
) {
    val isActive get() = status == "active"
    fun member(userId: String?) = members.firstOrNull { it.userId == userId }
}
@Serializable data class TripTotals(val trips: Int = 0, val distanceM: Long = 0, val longestM: Long = 0, val durationMs: Long = 0)
@Serializable data class TripsResponse(val active: Trip? = null, val trips: List<Trip> = emptyList(), val totals: TripTotals = TripTotals())
/** points are [lat, lon, epochMillis] */
@Serializable data class TripPath(val userId: String, val points: List<List<Double>> = emptyList())
@Serializable data class TripDetail(val trip: Trip, val paths: List<TripPath> = emptyList())
@Serializable data class TripStartBody(val title: String)
@Serializable data class TripTitleBody(val title: String)
@Serializable data class FixBody(val t: Long, val lat: Double, val lon: Double)
@Serializable data class FixesBody(val points: List<FixBody>)
