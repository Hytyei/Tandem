# Update 4.2 (v1.3.2)

1. New friendlier brain games (Odd One Out, Make the Target, Echo), gentler Code Breaker, Next Number fixed; Lights Out and Slide Puzzle retired.
2. Cycle tab is visible to both partners (no privacy switch).
3. Today: both partners' meters head to head with green/red up/down flashes since last time.

## Apply order
1. Copy the changed files (same relative paths; `ui/components/MetersVs.kt` is new). This zip also contains Updates 4 and 4.1.
2. Backend: no migration this time (no schema change). Redeploy: `npx wrangler deploy` (from the `backend` folder). If you skipped 0006 earlier, run `npx wrangler d1 execute tandem --remote --file=migrations/0006_period.sql` first (once).
3. Android Studio: no Gradle sync needed. Clean Project, Run.
4. Release: tag `v1.3.2`.

## Notes
- **Next Number freeze:** the puzzle generator could loop forever when a counting-down pattern went negative (no valid wrong answers). The generator now keeps every number positive and always terminates.
- **Games:** new keys `odd`, `target`, `echo` (server score cap 1000). `lights` and `slide` stay on the server and in the app as retired games, so a match that was already started with them can still be finished. They no longer appear in the picker, high scores or stats.
- **Meters:** when you save your page with changed meters, the server keeps the previous values as `profile.prevMeters`. The app shows the difference as a ▲ (green) / ▼ (red) pill that flashes a few times. Until someone changes their meters twice there is nothing to compare, so no arrow shows.
- **Cycle:** the server now always returns the partner's cycle; `shared` is always true and the old switch is ignored.

## Test
- `node --experimental-transform-types --no-warnings backend/test/smoke.mjs` all passing.
- Play: start a match of each new game; Next Number should start right after 3-2-1. Check Lights Out / Slide are not offered.
- Both phones: change meters on one (e.g. 40 to 70 and back), open Today on the other and look for the flashing arrow and the head-to-head bars.
- Calendar > Cycle: no sharing switch; the partner segment shows on both phones.
