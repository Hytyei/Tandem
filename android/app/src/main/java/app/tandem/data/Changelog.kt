package app.tandem.data

/** Patch notes shown inside the app. Add a new entry at the TOP for every release (version = the app's versionName). */
class ReleaseNotes(val version: String, val title: String, val date: String, val items: List<String>)

val CHANGELOG: List<ReleaseNotes> = listOf(
    ReleaseNotes(
        version = "2.1.1", title = "v2.1.1 · Our little world", date = "2026-11-08",
        items = listOf(
            "Your pets and your galaxy now live together on Today, under your moods: the galaxy glows in the sky and your pets sit on a little hill beneath it.",
            "Sleeping pets stay quiet instead of chatting in their sleep, and their little z's no longer sit on their ears.",
        ),
    ),
    ReleaseNotes(
        version = "2.1.0", title = "v2.1.0 · Galaxy fix, new stats and achievements", date = "2026-11-08",
        items = listOf(
            "Your galaxy now shows on every phone (it could stay black on some), and it hangs in the app's own dusk sky instead of on plain black.",
            "Achievements are a night sky you fill in together: every achievement is a star, each kind its own constellation. The ones you're closest to come first, and every medal opens to show how far along you are.",
            "Stats got calmer and clearer: the days-together constellation, six key numbers, a mirrored month of moods, and ranked lists of what you do together.",
            "Play stats and every game's page now show you two head to head, with every comparison side by side and a podium for the best scores.",
            "Goals are races: both of you moving toward the finish line, with a plain line on who needs how many more.",
        ),
    ),
    ReleaseNotes(
        version = "2.0.0", title = "v2.0.0 · Pets and your own galaxy", date = "2026-11-07",
        items = listOf(
            "Adopt a pet together: cats, dogs, hamsters, rabbits and parrots in 23 breeds, each in three coats. They breathe, blink and get up to their own little things: cats lick their paws and knead, dogs wag and pant, hamsters stuff their cheeks, rabbits hop, parrots bob and flap.",
            "Everything you do in Tandem fills your own storage with food, drink and love. Feed, water and cuddle your pets from it, and give a free pat every half hour.",
            "Pets have their own small voices (mute them on the pet page) and opinions, which they share in speech bubbles. They notice who looks after them more.",
            "Up to three pets. The first is free; more pets cost stardust and get hungry faster. A neglected pet sulks, it never dies.",
            "The planet is now a galaxy: tens of thousands of stars on black, unique to the two of you. Turn it with your finger and pinch to come closer.",
            "Stardust comes from moods, notes, pictures, games, dates, streaks and milestones, and your galaxy grows with all of it. Spend a comet to re-roll it and choose between three new ones.",
            "New home screen widget for your pets; the planet widget now shows your galaxy.",
        ),
    ),
    ReleaseNotes(
        version = "1.9.1", title = "v1.9.1 · Orbit fix", date = "2026-11-05",
        items = listOf(
            "Orbit: taps after your first hit now count when the moon is in the light. The moon also speeds up as you go, as it was meant to.",
        ),
    ),
    ReleaseNotes(
        version = "1.9.0", title = "v1.9.0 · A living planet, full meters and new stats", date = "2026-11-05",
        items = listOf(
            "Your planet has a real surface now: continents with coastlines and mountains, seas that deepen, lands that sit where they belong, ice caps, drifting clouds and sunlight from the brighter of your two suns.",
            "Today: what you're both into is a row of posters and covers, and the meters face off head to head with what each one means.",
            "A meter that stays at 100 two times in a row is at full capacity: it glows gold, light runs along it and sparks rise off the end.",
            "Diary: notes are grouped by day, pictures show as a mosaic, and short notes read like quotes.",
            "Heatmap: see where your visits go by kind of place, your favourites as big ranked cards, and every place as a tile you can filter.",
            "Play stats are charts now: who's winning as a ring, the race over time, speed and accuracy, and wins for every game. Each game's page shows your scores over time.",
            "The number on the Play tab is no longer cut off.",
        ),
    ),
    ReleaseNotes(
        version = "1.8.0", title = "v1.8.0 · Three new games", date = "2026-11-04",
        items = listOf(
            "Star Path: a constellation appears, tap its stars in order before time runs out. Each finished one is worth more, and the next has another star.",
            "Orbit: a moon circles a planet, tap when it passes through the lit gate. The gate gets narrower and the moon faster, and sometimes it turns around. Three misses and it's over.",
            "Meteor Drift: slide your light to catch falling stars and dodge the meteors. You both get the very same sky.",
            "Odd One Out is easier to see: the different dot stands out more, even on the hardest grids.",
        ),
    ),
    ReleaseNotes(
        version = "1.7.1", title = "v1.7.1 · Planet fix", date = "2026-11-04",
        items = listOf(
            "Fixed a crash that could happen when opening your planet.",
        ),
    ),
    ReleaseNotes(
        version = "1.7.0", title = "v1.7.0 · Your planet, games made of light and new widgets", date = "2026-11-03",
        items = listOf(
            "Meet your planet: a small world the two of you keep alive, on the Today screen. It orbits two suns, one for each of you, and each sun shines brighter the days you share your mood.",
            "It runs on what you already do. Sharing on the same day fills its air, diary notes and pictures grow life, games give stardust, and tracked dates raise landmarks named after where you went.",
            "Shape it: grow the ocean, forest, crystal or aurora lands, build an observatory, a lighthouse, a garden, a bridge or an aurora station, and meet up to 14 kinds of life that only live where the world suits them.",
            "It has real consequences. Three quiet days and it dims, a week brings winter, and two weeks lose a moon for good. When it dims it needs a spark from each of you to rekindle, and what was lost stays lost.",
            "It's fair to real life: put it to sleep for 3 to 14 days once a month, and lend your partner's sun some of your light on the days you share.",
            "A new widget shows your planet on the home screen, and you'll hear when your partner sends a spark or the planet starts to dim.",
            "Every game was redone around light instead of pictures: a soft dusk behind each round, glowing pieces, bursts of light when you score, streaks that warm the whole screen, and a soft rose glow and a small shake when you miss. Your phone gives a little tap on every hit.",
            "The Play page is calmer: the game waiting for you is one big card, and every game is a glass tile lit in its own colour.",
            "Echo is four moons, Memory cards turn over to glowing shapes, Odd One Out is now about shades of light, Word Duel letters turn over one by one, and Stack slices fall away.",
            "Widgets were redone: the two-moods widget blends both your pictures into one scene, and the diary and event widgets are painted night skies with a glowing countdown ring.",
        ),
    ),
    ReleaseNotes(
        version = "1.6.0", title = "v1.6.0 · New moods timeline, map looks & games with their own worlds", date = "2026-11-03",
        items = listOf(
            "Moods has a new timeline. Two orbits at the top show the last two weeks: each of you is a lane of coloured moons, and a line joins the days you both checked in. Tap a day to jump to it.",
            "The days below read as one column with the time on the left, so notes and reactions have room to breathe.",
            "Heatmap: pick a map look. Star chart, Blueprint, Atlas or the plain Street map, all painted on your phone from OpenStreetMap.",
            "Your tracked dates are on the Heatmap now. Their routes show as trails, they warm the heat layer where you walked, and a flag marks where each date began. Turn them off with the route button.",
            "Every game has its own world: its own colours, a painted scene behind the round and a drawn emblem. The Play page, the game pages and the round screens were all redone around them.",
            "Rounds feel better: a new countdown, a burning fuse for the time left, a score that counts up at the end, and drawn hearts, stars and coins when you hit things.",
            "Heart Catch, Flappy Heart, Stack, Whack-a-Mole, Memory Match, Quick Draw and Blackjack got new artwork.",
            "Widgets match the app's night sky: a starfield, notes on cards, a countdown ring for the next event, and two little stars on the moods widget on days you both shared.",
        ),
    ),
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
