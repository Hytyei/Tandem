package app.tandem.data

import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.Color
import app.tandem.R

/** The shared vocabulary of moods and activities. Keys must match the backend. */
enum class Mood(val key: String, val label: String, @DrawableRes val art: Int, @DrawableRes val wart: Int, val tint: Color) {
    Happy("happy", "Happy", R.drawable.mood_happy, R.drawable.w_mood_happy, Color(0xFFF59E0B)),
    Loved("loved", "Loved", R.drawable.mood_loved, R.drawable.w_mood_loved, Color(0xFFE11D48)),
    Calm("calm", "Calm", R.drawable.mood_calm, R.drawable.w_mood_calm, Color(0xFF0EA5E9)),
    Excited("excited", "Excited", R.drawable.mood_excited, R.drawable.w_mood_excited, Color(0xFFEC4899)),
    Tired("tired", "Tired", R.drawable.mood_tired, R.drawable.w_mood_tired, Color(0xFF6366F1)),
    Sad("sad", "Sad", R.drawable.mood_sad, R.drawable.w_mood_sad, Color(0xFF3B82F6)),
    Anxious("anxious", "Anxious", R.drawable.mood_anxious, R.drawable.w_mood_anxious, Color(0xFF8B5CF6)),
    Angry("angry", "Angry", R.drawable.mood_angry, R.drawable.w_mood_angry, Color(0xFFDC2626));

    companion object {
        fun from(key: String?): Mood? = entries.firstOrNull { it.key == key }
    }
}

enum class Activity(val key: String, val label: String, @DrawableRes val art: Int, @DrawableRes val wart: Int, val tint: Color) {
    Gaming("gaming", "Gaming", R.drawable.act_gaming, R.drawable.w_act_gaming, Color(0xFF6D4AE0)),
    Sleeping("sleeping", "Napping", R.drawable.act_sleeping, R.drawable.w_act_sleeping, Color(0xFF5B4BB7)),
    Eating("eating", "Lunch & dinner", R.drawable.act_eating, R.drawable.w_act_eating, Color(0xFFE8894A)),
    Tv("tv", "Watching TV", R.drawable.act_tv, R.drawable.w_act_tv, Color(0xFFB04A8F)),
    Music("music", "Music", R.drawable.act_music, R.drawable.w_act_music, Color(0xFFA855C7)),
    Football("football", "Football", R.drawable.act_football, R.drawable.w_act_football, Color(0xFF3FA66A)),
    Cycling("cycling", "Cycling", R.drawable.act_cycling, R.drawable.w_act_cycling, Color(0xFF3B9AD9)),
    Hiking("hiking", "Hiking", R.drawable.act_hiking, R.drawable.w_act_hiking, Color(0xFF4C9A7A)),
    Camping("camping", "Camping", R.drawable.act_camping, R.drawable.w_act_camping, Color(0xFFD9703C)),
    Roadtrip("roadtrip", "Road trip", R.drawable.act_roadtrip, R.drawable.w_act_roadtrip, Color(0xFFE0644F)),
    Stargazing("stargazing", "Stargazing", R.drawable.act_stargazing, R.drawable.w_act_stargazing, Color(0xFF5667D6)),
    Photography("photography", "Photography", R.drawable.act_photography, R.drawable.w_act_photography, Color(0xFF7C6BC9)),
    Hair("hair", "Hair day", R.drawable.act_hair, R.drawable.w_act_hair, Color(0xFFE26A94)),
    Nails("nails", "Nail time", R.drawable.act_nails, R.drawable.w_act_nails, Color(0xFFE0558A));

    companion object {
        fun from(key: String?): Activity? = entries.firstOrNull { it.key == key }
    }
}

/** Kinds of special days (birthdays, Valentine's Day, first date, ...). Keys must match the backend. */
enum class SpecialCategory(val key: String, val label: String, val emoji: String) {
    Birthday("birthday", "Birthday", "🎂"),
    Valentine("valentine", "Valentine's Day", "💘"),
    Anniversary("anniversary", "Anniversary", "💍"),
    FirstDate("firstdate", "First date", "🌹"),
    FirstKiss("firstkiss", "First kiss", "💋"),
    Proposal("proposal", "Proposal", "💎"),
    Wedding("wedding", "Wedding", "👰"),
    Trip("trip", "Trip together", "✈️"),
    Custom("custom", "Custom", "⭐");

    companion object {
        fun from(key: String?): SpecialCategory? = entries.firstOrNull { it.key == key }
    }
}

/** What to show as the "picture" for a mood update: the activity artwork wins, otherwise the mood face. */
@DrawableRes
fun MoodEntry.artRes(): Int =
    Activity.from(activity)?.art ?: Mood.from(mood)?.art ?: Mood.Happy.art

fun MoodEntry.headline(): String {
    val m = Mood.from(mood)?.label
    val a = Activity.from(activity)?.label
    return when {
        a != null && m != null -> "$a · $m"
        a != null -> a
        else -> m ?: "—"
    }
}

fun MoodEntry.tint(): Color = Activity.from(activity)?.tint ?: Mood.from(mood)?.tint ?: Color(0xFFFF2D55)

/** Rounded, small variant of [artRes] for widgets (RemoteViews can't clip corners on older Android). */
@DrawableRes
fun MoodEntry.widgetArtRes(): Int =
    Activity.from(activity)?.wart ?: Mood.from(mood)?.wart ?: Mood.Happy.wart
