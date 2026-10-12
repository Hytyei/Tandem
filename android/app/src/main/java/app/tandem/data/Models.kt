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
)

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
@Serializable data class DiaryBody(val text: String, val mood: String?)
@Serializable
data class EventBody(
    val title: String, val emoji: String, val date: String,
    val time: String?, val repeatYearly: Boolean, val notes: String,
)
