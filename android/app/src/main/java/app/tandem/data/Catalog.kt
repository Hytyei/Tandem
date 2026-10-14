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
    Nails("nails", "Nail time", R.drawable.act_nails, R.drawable.w_act_nails, Color(0xFFE0558A)),
    Working("working", "Working", R.drawable.act_working, R.drawable.w_act_working, Color(0xFF6B7FB8)),
    Coding("coding", "Coding", R.drawable.act_coding, R.drawable.w_act_coding, Color(0xFF7C5CD6)),
    Studying("studying", "Studying", R.drawable.act_studying, R.drawable.w_act_studying, Color(0xFFB07A4F)),
    Partying("partying", "Partying", R.drawable.act_partying, R.drawable.w_act_partying, Color(0xFFD946A8));

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

/** Kinds of dates / things you do together (shown as chips, counted in Stats). Keys must match the backend. */
enum class DateType(val key: String, val label: String, val emoji: String) {
    DateNight("datenight", "Date night", "🍷"),
    Movie("movie", "Movie night", "🎬"),
    Gaming("gaming", "Gaming session", "🎮"),
    Dinner("dinner", "Dinner out", "🍽️"),
    Coffee("coffee", "Coffee date", "☕"),
    Walk("walk", "Walk & nature", "🌿"),
    Trip("trip", "Trip / getaway", "✈️"),
    Cooking("cooking", "Cooking together", "🍳"),
    Concert("concert", "Concert / show", "🎶"),
    Workout("workout", "Workout", "🏃"),
    Picnic("picnic", "Picnic", "🧺"),
    BoardGames("boardgames", "Game night", "🎲"),
    Cozy("cozy", "Cozy night in", "🛋️"),
    Shopping("shopping", "Shopping", "🛍️"),
    Celebration("celebration", "Celebration", "🎉"),
    Other("other", "Something else", "✨");

    companion object {
        fun from(key: String?): DateType? = entries.firstOrNull { it.key == key }
    }
}

/** How much a memory meant, from "nice" to "unforgettable" (1..5 hearts). */
val RATING_LABELS = listOf("", "Nice", "Good", "Great", "Wonderful", "Unforgettable")
fun ratingLabel(r: Int?): String = RATING_LABELS.getOrElse(r ?: 0) { "" }

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

/** Kinds of places on the Heatmap. Keys must match the backend (PLACE_CATEGORIES). */
enum class PlaceCategory(val key: String, val label: String, val emoji: String, val tint: Color) {
    Restaurant("restaurant", "Cafe & Restaurant", "🍽️", Color(0xFFE8894A)),
    Takeout("takeout", "Take-out Cafe", "☕", Color(0xFFB07A4F)),
    Entertainment("entertainment", "Entertainment", "🎡", Color(0xFFA855C7)),
    Nature("nature", "Parks & Nature", "🌳", Color(0xFF3FA66A)),
    Culture("culture", "Museums & Culture", "🏛️", Color(0xFF5667D6)),
    Shopping("shopping", "Shopping", "🛍️", Color(0xFFE26A94)),
    Nightlife("nightlife", "Bars & Nightlife", "🍸", Color(0xFF6D4AE0)),
    Stay("stay", "Hotels & Stays", "🏨", Color(0xFF3B9AD9)),
    Sights("sights", "Trips & Sights", "🗺️", Color(0xFF0EA5E9)),
    Special("special", "Special places", "💞", Color(0xFFE11D48)),
    Other("other", "Other", "📍", Color(0xFF8B8FA8));

    companion object {
        fun from(key: String?): PlaceCategory = entries.firstOrNull { it.key == key } ?: Other
    }
}

/** Favourite colours on a personal page. */
enum class ProfileColor(val key: String, val label: String, val color: Color) {
    Red("red", "Red", Color(0xFFE5384F)), Orange("orange", "Orange", Color(0xFFF59E0B)), Yellow("yellow", "Yellow", Color(0xFFFFD43B)),
    Green("green", "Green", Color(0xFF3FA66A)), Teal("teal", "Teal", Color(0xFF14B8A6)), Blue("blue", "Blue", Color(0xFF3B82F6)),
    Purple("purple", "Purple", Color(0xFF8B5CF6)), Pink("pink", "Pink", Color(0xFFEC4899)), Black("black", "Black", Color(0xFF1C1C1E)),
    White("white", "White", Color(0xFFF5F5F7)), Brown("brown", "Brown", Color(0xFF8D6E63)), Gray("gray", "Gray", Color(0xFF8E8E93));

    companion object {
        fun from(key: String?): ProfileColor? = entries.firstOrNull { it.key == key }
    }
}

enum class LoveLanguage(val key: String, val label: String, val emoji: String) {
    Words("words", "Words of affirmation", "💬"), Time("time", "Quality time", "⏳"), Gifts("gifts", "Gifts", "🎁"),
    Acts("acts", "Acts of service", "🛠️"), Touch("touch", "Physical touch", "🤗");

    companion object {
        fun from(key: String?): LoveLanguage? = entries.firstOrNull { it.key == key }
    }
}

/** The playful 0–100 meters on a personal page. [captions] go from lowest to highest. */
enum class Meter(val key: String, val label: String, val emoji: String, val tint: Color, val captions: List<String>) {
    Spicy("spicy", "Horniness meter", "🔥", Color(0xFFFF4D6D), listOf("Pure as snow", "A little warm", "Getting ideas", "Hot and bothered", "Call me. Now.")),
    Hunger("hunger", "Hunger", "🍕", Color(0xFFF59E0B), listOf("Full & happy", "Peckish", "Snack time", "Hangry incoming", "Feed me or else")),
    Sleepy("sleepy", "Sleepiness", "😴", Color(0xFF6366F1), listOf("Wide awake", "A bit drowsy", "Yawning", "Barely functioning", "Already asleep")),
    Cuddle("cuddle", "Cuddle need", "🫂", Color(0xFFEC4899), listOf("Independent", "A hug would be nice", "Cuddles please", "Clingy mode", "Koala mode")),
    Chaos("chaos", "Chaos level", "🌪️", Color(0xFF14B8A6), listOf("Zen", "Mild mischief", "Feeling silly", "Gremlin hours", "Absolute menace"));

    fun caption(value: Int): String = captions[(value / 20).coerceIn(0, captions.lastIndex)]

    companion object {
        fun from(key: String?): Meter? = entries.firstOrNull { it.key == key }
    }
}

/** Western zodiac sign for a birthday (just for fun on the personal page). */
fun zodiacOf(month: Int, day: Int): String = when {
    (month == 3 && day >= 21) || (month == 4 && day <= 19) -> "♈ Aries"
    (month == 4) || (month == 5 && day <= 20) -> "♉ Taurus"
    (month == 5) || (month == 6 && day <= 20) -> "♊ Gemini"
    (month == 6) || (month == 7 && day <= 22) -> "♋ Cancer"
    (month == 7) || (month == 8 && day <= 22) -> "♌ Leo"
    (month == 8) || (month == 9 && day <= 22) -> "♍ Virgo"
    (month == 9) || (month == 10 && day <= 22) -> "♎ Libra"
    (month == 10) || (month == 11 && day <= 21) -> "♏ Scorpio"
    (month == 11) || (month == 12 && day <= 21) -> "♐ Sagittarius"
    (month == 12) || (month == 1 && day <= 19) -> "♑ Capricorn"
    else -> "♒ Aquarius"
}


/** The emoji people can react with (same list as the backend's REACTIONS). */
val REACTION_EMOJIS = listOf("😂", "🤣", "😭", "🥲", "😐", "🫠", "😑", "🤔", "🐥", "🥹", "🥺", "🔥", "🥳", "📿")

/** The minigames. Keys and score caps must match the backend (GAMES). */
enum class MiniGame(val key: String, val label: String, val emoji: String, val tint: Color, val blurb: String, val rules: String, val unit: String) {
    Heart("heart", "Heart Catch", "💗", Color(0xFFF2476F), "30 seconds · tap every heart", "Hearts pop up all over the screen. Tap as many as you can in 30 seconds. Missed taps cost nothing, but hearts vanish fast.", "hearts"),
    Memory("memory", "Memory Match", "🧠", Color(0xFF6A5CF0), "60 seconds · match the pairs", "Flip two cards to find a pair. Pairs, time left and fewer mistakes all add up to your score.", "pts"),
    Sums("math", "Math Sprint", "➗", Color(0xFF14B8A6), "45 seconds · quick sums", "Answer as many sums as you can in 45 seconds. Right answers score, wrong ones cost you.", "pts"),
    Draw("draw", "Quick Draw", "⚡", Color(0xFFF59E0B), "5 tries · wait, then tap", "Wait for the screen to turn gold, then tap as fast as you can. Tap too early and that try scores zero. Five tries.", "pts");

    companion object {
        fun from(key: String?): MiniGame? = entries.firstOrNull { it.key == key }
    }
}
