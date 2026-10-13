package app.tandem.data

/** Patch notes shown inside the app. Add a new entry at the TOP for every release (version = the app's versionName). */
class ReleaseNotes(val version: String, val title: String, val date: String, val items: List<String>)

val CHANGELOG: List<ReleaseNotes> = listOf(
    ReleaseNotes(
        version = "1.1.0", title = "Update 2 · Memories, ratings & achievements", date = "2026-10-13",
        items = listOf(
            "Edit your diary posts: text, mood, date, pictures and rating.",
            "Rate memories with 1–5 hearts, from \"Nice\" to \"Unforgettable\".",
            "Pick what you did: date night, movie night, gaming session and more, on diary posts and calendar events.",
            "Remember when: the Calendar tab shows this day in earlier years, last month's highlights and your best memories.",
            "Achievements for days together, streaks, moods, diary, pictures, plans and dates.",
            "Stats now count the kinds of dates you've had.",
            "Moods in the diary composer show their names, so every picture is readable.",
            "Your app icon now appears on the sign-in and pairing screens.",
            "New Play tab (coming soon) for mini-games.",
        ),
    ),
    ReleaseNotes(
        version = "1.0.1", title = "Update 1 · Stats, special days & notifications", date = "2026-10-12",
        items = listOf(
            "Stats page with days together, streaks, 30-day activity and mood mix.",
            "Special days (birthdays, Valentine's, anniversaries…) inside the Calendar tab.",
            "Diary feed with pictures and entries for other dates.",
            "Notifications for your partner's moods, diary notes and events.",
            "Remove your partner and connect someone new; history is kept per pair.",
            "Notice inside the app when a new release is available.",
            "Security and privacy hardening, and a better dark-mode mood timeline.",
        ),
    ),
)

/** Notes for the running version (falls back to the newest entry). */
fun notesFor(version: String): ReleaseNotes? = CHANGELOG.firstOrNull { it.version == version } ?: CHANGELOG.firstOrNull()
