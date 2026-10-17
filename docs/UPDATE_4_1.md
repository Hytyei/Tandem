# Update 4.1 (v1.3.1)

Two changes: minigames are a single round, and the Calendar tab has a Cycle (period tracker) segment.

## Apply order
1. Copy the changed files (same relative paths; `CycleScreen.kt`, `Cycle.kt` and `backend/migrations/0006_period.sql` are new). This zip also contains the Update 4 files, so it is safe to copy over everything.
2. Backend (from the `backend` folder):
   - `npx wrangler d1 execute tandem --remote --file=migrations/0006_period.sql` (once; it only adds three tables).
   - `npx wrangler deploy`
3. Android Studio: no new dependencies, so Gradle sync is not needed. Build > Clean Project, Run.
4. Release: tag `v1.3.1`.

## Games
New matches always have one round (the app sends `rounds: 1`). The server still accepts 1, 3 or 5, so matches that were already started as best-of-3/5 continue and finish normally, and an old app version keeps working.

## Period tracker
- Data is per person: `period_settings` (cycle length, period length, shared flag), `period_logs` (start/end/note) and `period_days` (flow 1-3, symptoms, note).
- `GET /period` returns `{mine, partner}`; `partner` is null unless the partner turned sharing on. Only the owner can write.
- Endpoints: `PUT /period/settings`, `POST /period`, `PUT/DELETE /period/:id`, `PUT /period/days/:date`.
- Predictions are computed on the phone from the average of the last six gaps between period starts (18 to 60 days), falling back to the setting; ovulation is estimated 14 days before the next period and the fertile window is ovulation minus 5 days to plus 1 day. They are estimates only, and the screen says so.
- Deleting your account removes your cycle data.

## Test
- `node --experimental-transform-types --no-warnings backend/test/smoke.mjs` (or `npm test`) all passing.
- App: Calendar > Cycle > "Period started today", then "Period ended"; log a day with flow and symptoms; add a past period; change settings and turn on sharing, then open Calendar > Cycle on the other phone and check the partner segment appears. Start a new match in Play and check there is no rounds picker. If you have a best-of-3 match still running, open it and finish it.
