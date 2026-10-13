# Update 2

## Apply order
1. Copy the changed files (same relative paths).
2. Backend: `cd backend`, then `npx wrangler d1 execute tandem --remote --file=migrations/0003_update2.sql` (once), then `npx wrangler deploy`.
   "duplicate column name" means it was already applied: stop, do not re-run.
3. Android Studio: Sync Project (no new dependencies), Build > Clean Project, Run.
4. Release: tag `v1.1.0` so the app's version name matches the patch notes entry.

## What changed
- Logo instead of artwork on the sign-in, pairing, loading and empty screens and the widget placeholder.
- Diary: edit your own posts (text, mood, date, pictures, rating, date type), 1–5 heart rating, date types, mood picker with names.
- Calendar: date type on events, "On this day" teaser, new Memories view.
- Stats: "Things we did together" and an achievements summary; Achievements screen (31 milestones).
- Play tab placeholder; patch notes dialog after an update and under Profile > What's new.

## Backend API additions
`PUT /diary/:id` (author only), `DELETE /diary/image/:id` (author only), `GET /memories?date=YYYY-MM-DD`,
`/stats` now also returns `dateTypeCounts` and `totals`; diary and events carry `dateType`, diary also `rating` and `updatedAt`.
