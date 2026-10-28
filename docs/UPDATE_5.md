# v1.4.0: dark only, six new games, speed stats, touch effects

1. **Dark only.** The light theme and the System/Light/Dark setting are removed. The status and navigation bars are forced to the dark style. (The gentle night-time deepening from 1.3.4 stays.)
2. **Make the Target fix.** The tile grid now measures the space it is given and shrinks to fit, with Clear / Skip always visible. Heart Catch hearts are placed so the whole heart stays inside the play area.
3. **Speed stats.** Games report fastest/average reaction (ms), hits and misses with the score (`perf` in `PUT /games/matches/:id/score`, stored in the new `game_perf` table). `GET /games` returns `stats.perf`. Shown after each round, per game and in a "Speed & reflexes" card under Play > Stats.
4. **New games** (same seed for both players): Whack-a-Mole, Stack, Flappy Heart, Colour Clash, Word Duel. Play lists are grouped: Quick reflexes, Arcade duels, Brain teasers, Cards.
5. **Blackjack.** Two-deck shoe shuffled from the match seed, 6 hands, 500 starting chips, bets 25/50/100/250, hit/stand/double, blackjack pays 3:2, dealer stands on 17. Score = final chips (cap 5000). The partner's total is shown as the number to beat.
6. **Touch effects.** Ripple under every touch, plus hearts / stars / coins / confetti / boom bursts when something is hit (`ui/components/Fx.kt`).

Backend: run `backend/migrations/0007_perf.sql` once (adds one table, IF NOT EXISTS). Then `npx wrangler deploy`. Until then scores save normally and the speed stats are empty.
