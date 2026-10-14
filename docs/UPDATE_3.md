# Update 3 (v1.2.0)

Contents: Heatmap tab, personal pages, "add to calendar" when posting, emoji reactions, turn-based minigames with high scores, goals and stats, and four new activities (Working, Coding, Studying, Partying).

Also in this release: widget fix (each widget now always renders the type it was added as, and updates are paired by Android's own widget ids) and a new constellation "days together" panel on Today and Stats.

## Apply order
1. Copy the changed files (same relative paths; new files are listed in the reply).
2. Backend (from the `backend` folder):
   - `npx wrangler d1 execute tandem --remote --file=migrations/0004_update3.sql` (once). If you have not applied `0003_update2.sql` yet, run it first.
   - "duplicate column name" means it was already applied: stop, do not re-run.
   - `npx wrangler deploy` (required: new endpoints and new activity names are validated server side).
3. Android Studio: Sync Project (no new dependencies), Build > Clean Project, Run. The manifest now declares optional location permissions.
4. Release: commit, then tag `v1.2.0` (matches `versionName` and the patch notes).

## Backend API additions
- `PUT /me/profile`; `/sync` users carry a `profile` object.
- `GET/POST /places`, `PUT/DELETE /places/:id`, `POST /places/:id/visit`, `PUT /places/:id/review`; diary accepts and returns `placeId`/`placeName`.
- `PUT /reactions` `{type: mood|diary, id, emoji|null}`; moods and diary entries carry `reactions`.
- `GET /games`, `POST /games/matches`, `PUT /games/matches/:id/score`, `DELETE /games/matches/:id`, `POST /games/goals`, `PUT /games/goals/:id/settle`, `DELETE /games/goals/:id`.
- Activities `working`, `coding`, `studying`, `partying`.

## How the minigames work
A match is 1, 3 or 5 rounds of one game. Both players get the same seed, so they play the identical timed round on their own phone, one after the other (the starter goes first in odd rounds, the partner in even ones). The higher score wins the round, most rounds wins the match (ties broken by total points, else a draw). Leaving a round mid-way scores 0. Scores are range-checked by the server but not cheat-proof: this is a game between two people who trust each other.
Goals: "first to N match wins" or "first to N points", counted from the moment the goal is created. When someone reaches it, the other owes the stakes ("Loser buys dinner") until either of you taps "Paid up".

## Test
- `cd backend && npm test` (all passing), `tsc` clean.
- App: add a place (search, long-press, "use my location"), review it from both phones, check the ranking; post a note with a place and as a calendar occasion; fill in My page and open it on the other phone; react to a mood and a note; start a match, play round 1, play round 1 on the other phone.
