<p align="center">
  <img src="docs/icon.png" width="120" alt="Tandem icon" />
</p>

<h1 align="center">Tandem</h1>

<p align="center">A private little space for two — shared moods, a diary wall, a calendar with special days, and home-screen widgets.</p>

Native Android app (Kotlin, Jetpack Compose, Glance widgets) with a free Cloudflare Workers + D1 backend. Releases and updates are delivered through [GitHub Releases](https://github.com/Hytyei/Tandem/releases).

## Features

- **Moods** – share how you feel with an illustrated activity; a timeline readable in light and dark mode.
- **Diary wall** – a feed of notes for you and your partner: backdated entries, up to 4 photos per post, editable posts, a 1–5 heart rating for memories and a "what did we do" tag (date night, movie night, gaming session…).
- **Calendar** – events with reminders, a separate **Special days** view (birthdays, Valentine's, anniversaries, first date…) with countdowns, and a **Memories** view: this day in earlier years, last month's highlights, your best-rated moments.
- **Stats & achievements** – days together drawn as a node-graph number, milestones, 30-day activity, mood mix, favourite activities, the kinds of dates you've had, and relationship achievements.
- **Play** – a placeholder tab for upcoming mini-games.
- **Patch notes** – shown inside the app after every update (also under Profile › What's new).
- **Notifications** – partner mood, diary and event updates; event and special-day reminders; new-release notice.
- **Widgets** – partner mood, both moods, diary wall, next event.
- **Partners** – remove a partner and connect a new one. History is kept per pair: reconnecting with the same person restores it, a new partner starts blank.
- **In-app updates** – checks GitHub Releases and installs the signed APK.

## Repository layout

```
android/    Android Studio project (open this folder)
backend/    Cloudflare Worker + D1 (TypeScript, no runtime dependencies)
tools/      Illustration painter (Python)
docs/       Icon and update notes
.github/    CI: signed APK on version tag
```

## Backend (Cloudflare, free plan)

```bash
cd backend
npm install
npx wrangler login
npx wrangler d1 create tandem        # put the printed database_id in wrangler.toml
npm run db:init                      # fresh installs only
npx wrangler secret put JWT_SECRET   # enter a long random value when prompted; never commit it
npm run deploy
```

Existing installs: do **not** re-run `schema.sql`. Apply each file in `backend/migrations/` once, in order, then deploy:

```bash
npx wrangler d1 execute tandem --remote --file=migrations/0002_update1.sql   # skip if already applied
npx wrangler d1 execute tandem --remote --file=migrations/0003_update2.sql
npx wrangler deploy
```

Tests: `cd backend && npm test`.

The secret lives only in Cloudflare (`wrangler secret`) or a local `.dev.vars` file, which is git-ignored.

## Android app

1. Use a current Android Studio (JDK 21). Open the `android/` folder and let Gradle sync.
2. In `android/gradle.properties`:
   - `TANDEM_API_URL` – your Worker URL (public API: `https://tandem-api.thehdv7.workers.dev`)
   - `TANDEM_GITHUB_REPO` – `Hytyei/Tandem`
3. Run on a device or emulator (Android 8.0+).

## Releasing

Push a tag such as `v1.1.0` (the tag becomes the app's version name; add a matching entry at the top of `CHANGELOG` in `android/app/src/main/java/app/tandem/data/Changelog.kt` for the in-app patch notes); the workflow in `.github/workflows/release-android.yml` builds a signed APK and publishes it to Releases. The signing keystore and passwords are stored as GitHub Actions secrets, never in the repo. The app downloads only assets from `https://github.com/Hytyei/Tandem/releases/download/`, ignores pre-releases, and verifies the SHA-256 digest when GitHub provides one.

## Security & privacy

- Session token and cached data are encrypted on-device with an Android Keystore key; backups are disabled; cleartext traffic is off.
- The token is only sent to the configured API host over HTTPS.
- Photos are re-encoded on-device, which removes EXIF/location data; they are served only to the owning couple.
- Passwords are hashed with PBKDF2-SHA256; login, registration and invite-code attempts are rate-limited.
- API responses carry security headers and are not cacheable; there is no CORS access.
- Notifications are hidden on the lock screen by default.
- Account deletion erases your own moods, diary posts, photos and events.

## Notes

There is no push server: while the app is open, partner changes appear within ~30 s; when it is closed they arrive within about 15 minutes (Android background-work limits).
