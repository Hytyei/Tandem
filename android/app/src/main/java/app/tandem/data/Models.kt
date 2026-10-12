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
)

@Serializable
data class DiaryEntry(
    val id: String,
    val userId: String,
    val text: String,
    val mood: String? = null,
    val createdAt: Long,
    val entryDate: String? = null,          // yyyy-MM-dd the note is about (may be a past day)
    val images: List<String> = emptyList(), // picture ids, fetched from /diary/image/{id}
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
@Serializable data class DiaryBody(val text: String, val mood: String?, val entryDate: String? = null)
@Serializable
data class EventBody(
    val title: String, val emoji: String, val date: String,
    val time: String?, val repeatYearly: Boolean, val notes: String,
    val kind: String = "event", val category: String? = null,
)
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
)
