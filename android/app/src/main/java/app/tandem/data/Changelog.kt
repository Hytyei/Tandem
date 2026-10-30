package app.tandem.data

/** Patch notes shown inside the app. Add a new entry at the TOP for every release (version = the app's versionName). */
class ReleaseNotes(val version: String, val title: String, val date: String, val items: List<String>)

val CHANGELOG: List<ReleaseNotes> = listOf(
    ReleaseNotes(
        version = "1.5.0", title = "v1.5.0 · A new Play page, Google sign-in & game alerts", date = "2026-10-30",
        items = listOf(
            "Play is now tile based: every game has its own page with how to play, a new match button, your running matches, stats, the hall of fame and past matches.",
            "Games are grouped (quick reflexes, arcade duels, brain teasers, cards), so the page stays tidy however many games there are.",
            "Games waiting for you are impossible to miss: a glowing Your turn section at the top of Play, highlighted cards that show your partner's score, and a number on the Play tab.",
            "Recent games shows finished results by day plus everything still running. Goals and Stats have their own pages too.",
            "Notifications: a game is waiting for you, a new mood, a new diary entry. Each can be switched off under Profile. Without a push server, alerts arrive within about 15 minutes when the app is closed (and within seconds while it is open).",
            "Sign in or sign up with Google, and connect (or disconnect) Google on an existing account under Profile > Account. Google-only accounts can set a password there too.",
            "Two new activities with new artwork: Shopping and Doom scrolling.",
        ),
    ),
    ReleaseNotes(
        version = "1.4.0", title = "v1.4.0 · Six new games, speed stats & one dark look", date = "2026-10-28",
        items = listOf(
            "Tandem is now dark only: the light theme (and the theme setting) is gone.",
            "New games: Whack-a-Mole, Stack, Flappy Heart, Colour Clash (the Stroop test), Word Duel and Blackjack. Both of you get the very same course, word or shoe, so the better player wins.",
            "Blackjack is built for turns: six hands from 500 chips, hit / stand / double, and if your partner already played you see their chip total as the number to beat.",
            "Speed stats: your fastest touch, average reaction time and accuracy are recorded as you play, shown after each round and under Play > Stats, per game and overall.",
            "Touch effects: hearts, stars, coins, confetti and a ripple under your finger when you hit things in a game.",
            "Fixed Make the Target: the tiles and buttons now always fit on screen. Hearts in Heart Catch also stay fully on screen.",
        ),
    ),
    ReleaseNotes(
        version = "1.3.4", title = "Update 4.4 · Echoes & night mode", date = "2026-10-19",
        items = listOf(
            "Moods, Diary and Heatmap are now one tab called Echoes, with a switcher at the top. The tab bar is down to five.",
            "Night mode: after dusk (by your phone's clock) the colours dim a little and the Today sky becomes a proper night sky with a moon and more stars. It fades back by morning.",
            "New tab icons.",
        ),
    ),
    ReleaseNotes(
        version = "1.3.3", title = "Update 4.3 · Right now, head to head", date = "2026-10-19",
        items = listOf(
            "Right now has five slots: watching a series, watching a movie, playing, reading, and obsessed with (a track).",
            "Today shows what each of you is into side by side, with posters and covers, and your meters head to head.",
            "Edit what you're into (and your meters) straight from Today with \"Edit mine\".",
            "Better lookups, with no account needed: TVmaze and IMDb for series and movies, Steam for games, Google Books for books, Deezer for tracks (Wikipedia is no longer the main source).",
        ),
    ),
    ReleaseNotes(
        version = "1.3.2", title = "Update 4.2 · Friendlier brain games & meters vs.", date = "2026-10-19",
        items = listOf(
            "New brain games that are easy to start and fun to get good at: Odd One Out, Make the Target and Echo.",
            "Code Breaker is gentler: 5 colours, no repeated colours in the code, and 10 guesses.",
            "Next Number: fixed the freeze on the countdown. It also starts easier, wrong answers cost less, and streaks earn bonus points.",
            "Lights Out and Slide Puzzle are gone from the game list (matches already started can still be finished).",
            "Today: the meters now show both of you head to head, with a green ▲ or red ▼ flash for how much each changed since last time. The same arrows show on personal pages.",
            "Cycle is now simply visible to both of you: the privacy switch is gone.",
        ),
    ),
    ReleaseNotes(
        version = "1.3.1", title = "Update 4.1 · Single-round games & period tracker", date = "2026-10-18",
        items = listOf(
            "Minigames are now a single round each: pick a game and play. Matches that were already running as best-of-3 or best-of-5 finish as before.",
            "New Cycle tab in Calendar: a period tracker with start/end logging, a month view, flow and symptoms per day, and estimates for your next period and fertile window that learn from your own dates.",
            "Your cycle is private by default. Turn on sharing in Cycle settings if you want your partner to see it (they can't change it).",
        ),
    ),
    ReleaseNotes(
        version = "1.3.0", title = "Update 4 · Live dates, notes & brain teasers", date = "2026-10-17",
        items = listOf(
            "Heatmap: with the heat overlay on, the map turns into a quiet, flattened city so the glow is what you see. The overlay is stronger and warmer where you go most.",
            "Places remember how many times you've been before: enter a number when adding or editing.",
            "Add pictures to any place, and see them in its details.",
            "Saved addresses: pin your home (and your partner's) on the map, so you always know where it is.",
            "Live dates: start a tracked date together, and Tandem records where you went. Afterwards see the route, distance and time, plus your totals. Location is only used while a date is live, and you can stop at any time.",
            "Now watching, playing, listening or reading can be looked up: pick a poster/cover and a short description (for example Cyberpunk 2077) and it appears on your page.",
            "Your birthday is added to the calendar automatically as a birthday occasion.",
            "Mood sharing: pick what you're doing first (required), and the long left-to-right scrolling is gone.",
            "Today: the constellation fills the top of the screen, mood shares come next, then \"about your partner\" with their meters and current activities.",
            "Calendar: a new Notes tab with shared notes and checklists you can both tick off.",
            "Four new brain teasers: Next Number, Code Breaker, Lights Out and Slide Puzzle.",
            "The bottom tab bar has new icons.",
            "The server address is no longer shown in Profile.",
        ),
    ),
    ReleaseNotes(
        version = "1.2.0", title = "Update 3 · Heatmap, minigames & reactions", date = "2026-10-14",
        items = listOf(
            "New Heatmap tab: pin the places you've been to and watch a glowing heatmap of your adventures grow.",
            "Places are grouped by category (cafes & restaurants, take-out, entertainment, nature…) and ranked by your two reviews out of 5.",
            "Both of you can review every place; \"We went again\" counts repeat visits.",
            "Link a place to a diary note, and see it on the post.",
            "Personal pages: full name, birthday, favourite colours, hobbies, sport, food, music, what you're watching or playing right now, plus playful meters.",
            "Open your partner's page from Profile.",
            "Add a diary note to the calendar as an occasion while posting.",
            "New achievements for places and reviews.",
            "React to your partner's moods and diary notes with 😂🤣😭🥲😐🫠😑🤔🐥🥹🥺🔥🥳📿.",
            "The Play tab is alive: turn-based, timed minigames (Heart Catch, Memory Match, Math Sprint, Quick Draw), best-of-1/3/5 matches, high scores, a shared Tandem score and stats.",
            "Goals with stakes: first to N wins or points, and the loser buys lunch (or dinner, or coffee).",
            "Four new activities with artworks: Working, Coding, Studying and Partying.",
        ),
    ),
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
