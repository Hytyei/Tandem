# Update 4 (v1.3.0)

Contents: minimal heatmap map, visit counts, place photos, saved addresses, live-tracked dates with route and stats, media lookup (poster + info) for "now watching/playing/listening/reading", birthdays in the calendar, reworked mood sheet, shared notes and checklists, four brain-teaser minigames, new tab icons, Today page layout, Server hidden from Profile.

## Apply order
1. Copy the changed files (same relative paths). New files include `NotesScreen.kt`, `HeatmapExtras.kt`, `MediaLookup.kt`, `TrackingService.kt`, the `ic_tab_*.png` drawables and `backend/migrations/0005_update4.sql`.
2. Backend (from the `backend` folder):
   - `npx wrangler d1 execute tandem --remote --file=migrations/0005_update4.sql` (once). "duplicate column name" means it was already applied: stop, do not re-run.
   - `npx wrangler deploy` (required: new endpoints).
3. Android Studio: no new dependencies, so Gradle sync is not needed. Build > Clean Project, then Run. The manifest declares a new foreground service (type `location`) and notification/location permissions.
4. Release: commit, then tag `v1.3.0` (matches `versionName` and the patch notes).

## Backend API additions
- Places: `visits` on create/edit, `POST/DELETE /places/:id/images`, `GET /places/images/:id`.
- Saved addresses: `GET/POST /addresses`, `PUT/DELETE /addresses/:id`.
- Notes: `/notes`, `/notes/:id`, `/notes/:id/items`, `/notes/items/:id`.
- Trips: `/trips`, `/trips/:id`, `/trips/:id/join|leave|points|end`. Distance is computed on the server with jitter filtering.
- Profile `media` map (validated), brain-teaser game keys `seq`, `code`, `lights`, `slide`.

## Privacy notes
Location is read only while a live date runs. Routes and saved addresses are visible to the couple only. Media lookup contacts Wikipedia, iTunes Search and Open Library. The minimal map look is a colour filter on the OpenStreetMap tiles (no extra map provider).

## Test
- `cd backend && npm test` and `tsc` clean.
- App: heat on/off on the map; add a place with "times visited" and photos; save your home address and check the pin on the other phone; start a live date on both phones, walk a bit, end it, open the route; add a note and a checklist and tick items on the other phone; search for a game under "Playing" on My page; set a birthday and check the calendar; play each brain teaser in a match.
