# Tandem: handover instructions

Everything a developer or another AI agent needs to keep working on Tandem without any earlier context. Keep this file current: update it in the same commit as any change to features, structure, tooling or plans.

Last updated: 2026-11-10, at version **2.2.0** (versionCode 20).

---

## 1. What Tandem is

Tandem is a private app for one couple: two people pair their accounts and share a small world together. It is a native **Android** app (Kotlin, Jetpack Compose, Glance widgets) talking to a small **Cloudflare Workers + D1** backend (TypeScript, SQLite). There is no web or iOS client.

- **Owner:** GitHub user `Hytyei`. Repository: `Hytyei/Tandem` (branch `main`).
- **Android package:** `app.tandem.couples` (Kotlin namespace `app.tandem`).
- **Production API:** `https://tandem-api.thehdv7.workers.dev`.
- **Distribution:** signed APKs on GitHub Releases. The app updates itself from the latest release (there is no Play Store listing).
- **Design direction:** "minimal dreamy". A dusk sky background (`DreamBackground`), glass cards (`c.card` with a white hairline at alpha .07), serif titles (`Type.title`, `Type.largeTitle`), ivory primary buttons (`0xFFF6F2FF`) with dark text, sentence case, quiet chrome with one bright element per screen.
  - **Copy style:** no emoji in new UI (Material icons instead), no exclamation marks, no uppercase tracked labels.
  - **Motion:** strong ease-out `CubicBezierEasing(0.23f, 1f, 0.32f, 1f)` for entrances and springs for gestures. Always honour reduced motion via `rememberReducedMotion()`.

## 2. Folder layout

```
D:\RandD\Android Apps\Tandem App\
├── Tandem\                 ← this git repository (the source of truth)
│   ├── Instructions.md     ← this file
│   ├── README.md           ← public readme (setup, privacy notes)
│   ├── android\            ← Android Studio project (open this folder)
│   ├── backend\            ← Cloudflare Worker + D1 (TypeScript)
│   ├── docs\               ← icon, old update notes, design specs (docs/superpowers/specs)
│   ├── tools\              ← Python illustration painter for the mood/activity art
│   └── .github\workflows\  ← CI (see section 9)
└── v2.2.0\ (and later v<x.y.z>\)  ← one folder per released version: the signed APK + install.bat
```

**Version folders (standing rule):** every new version gets a new folder `Tandem App\v<version>\` holding that version's signed APK and an `install.bat` that installs it on a USB-connected phone with adb. Older version folders are never edited. The repository itself stays in `Tandem\`; it is not copied per version (git history and tags cover the source).

## 3. Requirements and toolchain

| What | Version / location |
| --- | --- |
| JDK | JetBrains Runtime 21 at `%USERPROFILE%\.jdks\jbr-21.0.11` (set `JAVA_HOME` to it) |
| Android SDK | `%LOCALAPPDATA%\Android\Sdk` (compileSdk/targetSdk 36, minSdk 26, build-tools latest) |
| Gradle | wrapper in `android/` (Kotlin DSL). Builds run with `--offline`, because the Google Maven host is unreliable on this network and everything is cached |
| Kotlin / Compose | Kotlin 2.x with the Compose compiler plugin, Compose BOM 2025.06.01, Material 3, material-icons-extended |
| Libraries | Glance 1.1.1, WorkManager 2.10, DataStore, Credential Manager + Google ID (Google sign-in), Coil 2.7, Retrofit 2.11 + OkHttp 4.12, kotlinx.serialization 1.9, coroutines 1.10 |
| Node.js | 22+ (tests use `--experimental-transform-types` and `--experimental-sqlite`) |
| Wrangler | 3.114 (devDependency; an "upgrade to v4" warning is expected and harmless) |
| Python | 3.x with Pillow and numpy (only for `tools/` and test helpers) |
| GitHub CLI | `gh`, logged in as the owner (releases) |
| Emulator | AVD `Medium_Phone_API_35`, started with `-gpu angle_indirect -no-snapshot -no-boot-anim` |

## 4. Accounts, keys and secrets

Never write a password or key into the repo, this file, memory or chat logs.

- **Release signing keystore:** `D:\RandD\Android Apps\AndroidKeys\tandemappv2`, alias `tandemapp`. The signer certificate SHA-256 starts with `d735639d`.
  - `AndroidKeys\tandemapp` (the "v1" key) is **not** the release key.
  - The owner supplies the keystore password when a release is built. It goes only into environment variables for that one build command.
- **Backend JWT secret:** set with `npx wrangler secret put JWT_SECRET` (production). Locally it lives in `backend/.dev.vars`, which is git-ignored and also holds the local test couple's password.
- **Cloudflare:** the PC's wrangler is logged in to the owner's account. D1 database `tandem`, id `cdd023dc-ccad-4fd4-a77f-b82bb0534c11`.
- **Google sign-in:** the web OAuth client id (public, not a secret) is in `android/gradle.properties` (`GOOGLE_WEB_CLIENT_ID`) and in `backend/wrangler.toml` `[vars]`.
  - The Android OAuth client is registered for package `app.tandem.couples` with the release SHA-1 `B2:65:1D:CF:95:4D:D2:F8:12:B7:D3:E9:12:8E:3F:3C:48:3B:2E:06`.
  - Google Cloud Console refuses the in-app browser (HTTP 403), so use a regular browser for it.
- **Production deploys:** the owner allows backend deploys at any time. Publishing a GitHub release needs the owner's go-ahead in that session.

## 5. Features (as of 2.2.0)

There are five tabs: **Today**, **Echoes** (moods, diary, heatmap), **Calendar**, **Play** and **Profile**, plus full-screen overlays opened from them.

**Today**
- The days-together number is drawn as a constellation in the sky, with a greeting and a "You & partner · Stats" chip.
- On an occasion day the sky shows a seasonal effect and a greeting card (see Occasions).
- Mini stats (moods, notes, active days).
- "Right now": both partners' latest moods as illustrated cards, with reactions and a "Share your mood" button.
- **Our little world:** one scene card with the couple's galaxy glowing in the sky and their pets on a hill below. Tapping the sky or the stars pill opens the Galaxy; anything else opens Pets. Pets speak in comic bubbles but stay quiet while dozing at night.
- Partner snapshot ("into right now" posters and playful meters).
- Coming up: the next event within 30 days, occasions included.

**Echoes**
- **Moods:** a timeline of mood shares, each with an illustrated activity.
- **Diary:** a feed of notes, backdated entries, up to 4 photos, 1–5 heart ratings, "what we did" tags, places and reactions.
  - At the top is one memory from the past: this day in an earlier year (from `/memories`), else around this date a year or more ago, else about a month ago, else a stable daily pick of a note at least 14 days old (`ui/screens/FromThePast.kt`).
- **Heatmap:** places on an OpenStreetMap map restyled to the app's palette, with a heat overlay, categories, reviews out of 5, photos per place and saved addresses.

**Calendar** has four sub-tabs: Calendar, Special, Notes and Cycle.
- **Calendar:** month grid, the day's events, and "Next 30 days".
- **Special:** special days with countdowns.
- **Notes:** shared notes and checklists.
- **Cycle:** period tracker for each partner; both can see both, but only the owner can edit.
  - It shows predictions (next period, fertile window, ovulation) and a sentence like "Your period is expected to start in 5 days". Today is ringed on both partners' calendars.
  - "Track my cycle" in Cycle settings turns your own cycle off (server field `tracking`); a partner who turned theirs off is hidden.
- The old "Memories" tab was removed in 2.2.0.

**Occasions** (`data/Occasions.kt`, `ui/components/OccasionFx.kt`, added in 2.2.0)
- Added automatically as read-only calendar events (`kind = "occasion"`, id `occ:<key>:<year>`) for this year and next:
  - fixed dates: New Year, Valentine's, Women's Day, Kissing Day, Girlfriend Day, Boyfriend Day, Day of the Girl, Halloween, Christmas, New Year's Eve;
  - Persian-calendar dates: Nowruz (1 Farvardin), Sizdah Bedar, Sepandarmazgan (29 Bahman), Yalda (30 Azar), Chaharshanbe Suri (the Tuesday before the last Wednesday before Nowruz);
  - Iran's Girls' Day (1 Dhu al-Qa'dah, Umm al-Qura).
- Dates use ICU's Persian and Islamic calendars pinned to UTC, so a phone in Tehran's time zone doesn't shift them by a day.
- On the day itself, Today's sky gets a seasonal effect: snow, confetti and fireworks, hearts, petals, embers, leaves with bats, or pomegranate seeds.
- Debug builds can preview any occasion: `adb shell am start -n app.tandem.couples/app.tandem.MainActivity --es occasion nowruz`.

**Play** (minigames for two)
- 18 games in `data/Catalog.kt` `MiniGame`. Keys: heart, memory, math, draw, mole, path, stack, flap, orbit, drift, odd, target, seq, echo, stroop, word, code, bj.
  - A new game also needs its key added to `GAMES` in `backend/src/index.ts`, deployed before the app ships.
- Each match is a seeded round both partners play; the higher score wins.
- The Play home has a "Your turn" list and a **head-to-head tug of war** (`ui/screens/HeadToHead.kt`): the knot is pulled toward whoever leads, with last-5 form under each side and the Tandem score.
- Also: Recent, Goals (stakes as race tracks, "Paid up"), Stats (versus hero, butterfly bars, by-game split) and a page per game (head to head, side-by-side bars, score chart, podium hall of fame, past matches).

**Pets** (`ui/pets/`)
- Up to 3 pets: cats, dogs, hamsters, rabbits and parrots, 23 breeds with 3 coats each. They are drawn in code in a kawaii style (`PetArt.kt`) and animated with idle loops, micro-actions and reactions (`PetStage.kt`).
- **Needs:** hunger, thirst and love fall over time and faster with more pets. Each partner feeds, waters and cuddles from their own storage, plus a free pat every 30 minutes. Each pet keeps a bond with each partner.
- A neglected pet sulks; pets never die. Adoption costs 0, 200 or 500 stardust for the first, second and third pet.
- **Voices** come from a formant synth in `PetSynth.kt` (meow, trill, purr, boof, pant, squeak, grunt, chirp, nom, lap), with a mute switch.
- **Personality and lines:** two traits per pet, derived from its id, shape what it says (`PetLines.kt`). Easter eggs:
  - pet names with their own lines;
  - 11:11 and 3:33;
  - Fridays and Mondays;
  - holidays;
  - the adoption day;
  - seven quick pokes find the tickle spot.

**Galaxy** (`ui/galaxy/`)
- A procedural galaxy generated from a seed (`GalaxyGen.kt`): spiral, barred, ring or lens shapes, with palettes based on telescope photos. It grows with lifetime stardust, from about 6,000 to 90,000 particles.
- Rendered with OpenGL ES 2 on a transparent `TextureView` with its own EGL thread (`GalaxyGL.kt`), over the dream sky. Drag to rotate with momentum, pinch to zoom. If GL fails, a CPU-painted fallback (`GalaxyBitmap.kt`) is shown.
- **Stardust** is earned from moods, notes, pictures, game rounds, tracked dates, both partners sharing a mood on the same day, and milestones.
- **Comets** are the re-roll item: a re-roll offers 3 candidate galaxies to choose from.

**Stats and achievements**
- **Stats** (from the Today chip): the days constellation, six figures, a mirrored month chart, mood mix, and ranked lists.
- **Achievements:** 63 in total. They are computed on the client from `StatsDetail`, `GameStats` and `CosmosState` (`data/Achievements.kt`). The screen is a constellation sky, "Almost there", and a medal grid.

**Other**
- Personal pages: profile details, "into right now" with posters from public lookups, and meters.
- Google sign-in and account linking.
- Live tracked dates: a foreground location service, routes, totals.
- Reactions on moods and notes.
- Patch notes (`data/Changelog.kt`).
- In-app updater (`update/Updater.kt`).
- Verified badge for usernames `hytyei` and `sanaz` (`ui/components/Verified.kt`).

**Widgets** (Glance, `widget/`)
- Partner mood, both moods, diary wall, next event, galaxy and pets.
- The galaxy widget keeps the old `PlanetReceiver` class name so widgets already on home screens keep working.

**Background and notifications**
- `SyncWorker` runs every 15 minutes (`work/SyncWorker.kt`). It drives notifications (`work/Notifier.kt`) for partner moods and notes, games, events, special days, a pet getting hungry or sulking, and new releases.
- There is no push server: changes appear within about 30 s while the app is open and within about 15 minutes when it's closed.

## 6. Architecture

**Android** (`android/app/src/main/java/app/tandem/`)
- `MainActivity.kt` holds the tab shell and full-screen overlays (stats, achievements, person page, galaxy, pets) shown with `AnimatedVisibility`.
- `AppViewModel.kt` is the single view model. The state comes from `/sync` (`SyncResponse`), and actions go through `Repository`.
- **`data/`**
  - `Api.kt`: Retrofit interface. The auth token is attached only for the API host over HTTPS.
  - `Repository.kt`: calls the API and caches state, encrypted with `SecureBox` and the Android Keystore.
  - `Models.kt`, `Cosmos.kt`: serializable models.
  - `Catalog.kt`: moods, activities, date types, special categories, minigames.
  - `Achievements.kt`, `Occasions.kt`, `Cycle.kt` (cycle math), `Dates.kt`, `Changelog.kt`, `Prefs.kt`.
- `ui/theme/Theme.kt`: colours (`T.colors`: label, secondary, tertiary, card, accent, me, partner, special and others) and `Type`.
- `ui/components/`: shared UI, including `Components.kt` (SoftCard, PrimaryButton, Segmented, `pressable`, `staggerIn`), `Charts.kt`, `StatsKit.kt` (GlassCard, VersusHero, SplitBar, RaceTrack, Medal, AchievementSky, BackDisc), `Dream.kt`, `Constellation.kt`, `OccasionFx.kt` and `Verified.kt`.
- `ui/screens/`: the screens; `ui/pets/` and `ui/galaxy/` as above.
- `widget/`: Glance widgets painted as bitmaps (`WidgetArt.kt`, `SizeMode.Exact`).
- `work/`: sync, notifications, reminders and tracking.

**Backend** (`backend/`)
- `src/index.ts` holds every route and helper, plus `/sync`, which returns the whole state in one call.
- `src/cosmos.ts` is the pure logic for stardust, storages, pets and the galaxy. It is unit-tested and stored as one versioned JSON document per couple in the `cosmos` table.
- `src/planet.ts` is the retired planet companion, kept so app versions before 2.0 still work. `/sync` still returns `planet` as well as `cosmos`.
- Routes: auth and `/me`, `/couple`, `/moods`, `/diary`, `/events`, `/sync`, `/stats`, `/memories`, `/places`, `/addresses`, `/period`, `/notes`, `/trips`, `/reactions`, `/games`, `/cosmos/*` and the legacy `/planet/*`.
- Tables: users, couples, moods, diary, diary_images, events, auth_attempts, places, place_reviews, place_images, saved_addresses, reactions, game_matches, game_scores, game_perf, game_goals, google_links, notes, note_items, trips, trip_members, trip_points, period_settings, period_logs, period_days, planets, cosmos.
- `schema.sql` is for fresh installs only. Existing databases get `migrations/` applied once, in order: 0002 to 0011, where **0011** adds `period_settings.tracking`.

## 7. Building and testing

**Backend tests and type check**
```bash
cd backend
npm install
npm test            # smoke.mjs (in-memory SQLite) + planet.mjs + cosmos.mjs, prints "all passed"
npx tsc --noEmit -p .
```

**Local server + emulator test loop**
1. In `backend/`, start the server: `npx wrangler dev --local --ip 127.0.0.1 --port 8787`. For a new local database run `npm run db:init:local`; for an existing one apply new migrations with `--local`. The test couple is `ava_test` and `ben_test`, with the password in `.dev.vars`.
2. Start the emulator: `"%LOCALAPPDATA%\Android\Sdk\emulator\emulator" -avd Medium_Phone_API_35 -gpu angle_indirect -no-snapshot -no-boot-anim`.
3. **Temporary debug-only changes**, which must be reverted before committing:
   - create `android/app/src/debug/AndroidManifest.xml` with `<application android:usesCleartextTraffic="true" tools:replace="android:usesCleartextTraffic" />`;
   - in `data/Api.kt`, remove `url.isHttps && ` from `if (token != null && url.isHttps && url.host == apiHost())`.
4. Build the debug app: `gradlew :app:assembleDebug --offline -PTANDEM_API_URL=http://10.0.2.2:8787`. Uninstall any release build first (signature mismatch). Install with `adb install -r`, then `adb shell pm grant app.tandem.couples android.permission.POST_NOTIFICATIONS`.
5. **Reverting:** use `sed` (or edit) on the `Api.kt` line and delete `android/app/src/debug`. Never `git checkout` the file, which once wiped unrelated edits. Check with `git diff --quiet -- android/app/src/main/java/app/tandem/data/Api.kt`.
6. A debug-only gallery Activity in `src/debug` (launched with `adb shell am start -n app.tandem.couples/app.tandem.<Name>`) is the fastest way to iterate on art (pets, galaxy, voices). It is deleted along with `src/debug`.

## 8. Releasing a version (local, signed)

The GitHub "Release Android APK" workflow **always fails** because the signing secrets are not set. Releases are built locally:

1. Add an entry at the top of `CHANGELOG` in `data/Changelog.kt`. Bump the defaults in `android/app/build.gradle.kts` (`versionCode` +1, `versionName`).
2. If the backend changed: run `npm test`, apply new migrations to production (`npx wrangler d1 execute tandem --remote --file=migrations/00xx_….sql`), then `npx wrangler deploy`. Ship the server before the app that needs it.
   - The "Deploy backend" workflow also deploys automatically on pushes to `main` that touch `backend/**`, so migrations must already be applied before pushing.
3. Build:
   ```bash
   export JAVA_HOME="$USERPROFILE/.jdks/jbr-21.0.11"
   export TANDEM_KEYSTORE_FILE="D:/RandD/Android Apps/AndroidKeys/tandemappv2" TANDEM_KEY_ALIAS=tandemapp
   export TANDEM_KEYSTORE_PASSWORD=… TANDEM_KEY_PASSWORD=…   # from the owner, never saved
   cd android && ./gradlew assembleRelease --offline -PversionName=X.Y.Z -PversionCode=N
   ```
4. Verify:
   - `apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk`: the SHA-256 must start with `d735639d`;
   - `aapt2 dump badging` shows the right version;
   - install and launch on the emulator and check logcat for `FATAL`.
5. Commit, `git push origin main`, `git tag vX.Y.Z`, `git push origin vX.Y.Z`.
6. Publish: copy the APK as `tandem-release.apk` and run `gh release create vX.Y.Z tandem-release.apk --repo Hytyei/Tandem --title "vX.Y.Z · …" --notes-file notes.md --latest`.
   - Uploads often fail with HTTP 408 on this network. Delete the half-made release (`gh release delete vX.Y.Z --yes`) and retry.
   - Confirm the asset digest equals the local `sha256sum`.
7. Create the version folder `D:\RandD\Android Apps\Tandem App\vX.Y.Z\` with `Tandem-X.Y.Z.apk` and `install.bat`, copied from the previous version folder with the file name updated.
8. Update this file.

## 9. CI (GitHub Actions)

- `deploy-backend.yml`: type-checks and deploys the Worker on pushes to `main` that touch `backend/**` (secrets `CLOUDFLARE_API_TOKEN` and `CLOUDFLARE_ACCOUNT_ID` are set; it works).
- `release-android.yml`: builds a signed APK on version tags. It fails because the keystore secrets were never added. Either add them or delete the workflow; local releases are the real path.

## 10. Known quirks and lessons

- The emulator's GLES (ANGLE) accepts things phone GPUs reject. A uniform shared by the vertex and fragment shaders with different precision made the galaxy black on a real phone, so each shader stage gets its own uniform (`uKind` / `uKindF`).
- `GLSurfaceView` punched a black hole inside animated overlays; the galaxy uses a `TextureView` instead.
- Compose `Modifier.border` did not redraw when the cycle calendar switched to the partner view. Today's ring is drawn by hand with `drawWithContent`.
- In Kotlin, a `/*` inside a KDoc comment (for example a path like `files/voices/*.wav`) opens a nested comment and breaks the build.
- An `Occasion` class already exists in `Models.kt` (diary "add as occasion"); the holiday class is named `Holiday`.
- Achievement unlock toasts: the groups added in 2.2.0 (Games, Pets, Galaxy) are recorded quietly the first time (`$pid:init2` in prefs), so an update doesn't fire a burst of toasts.
- On this PC, long inline Python in bash heredocs sometimes fails to parse when it contains apostrophes; write such scripts to a file and run them.
- The README's feature list predates 2.0 (it still mentions the planet and calendar memories). This file is the up-to-date reference.

## 11. Planned and possible future work

- **In-app currency:** stardust and comets were designed to become a real in-app currency later (owner's request at 2.0.0); comets are the re-roll item today.
- **Pet voices:** confirm by ear on a real phone that the 2.2.0 synth voices sound cute and low enough; tune `PetSynth.kt` (pitches, formants, breath) based on that.
- **Occasions:** notification reminders, a setting to hide holidays the couple doesn't celebrate, more occasions (birthdays already exist as special days), and checking Iran's Girls' Day against the official calendar each year (Umm al-Qura can differ by a day).
- **Push:** real-time push (for example FCM) instead of 15-minute polling.
- **Release CI:** add the signing secrets to GitHub or remove the failing workflow.
- **Clean-up:** retire the `/planet` routes and `planets` table once no pre-2.0 apps remain; update the README feature list; upgrade wrangler to v4.
- **Achievements:** server-side achievement records (they are computed on the client today), and more groups (cycle, notes, live dates).

## 12. Version history (short)

| Version | Date | Highlights |
| --- | --- | --- |
| 1.6.0 | 2026-11-03 | Timeline, heatmap, games and widgets redesign |
| 1.7.0 / 1.7.1 | 2026-11-03/04 | Games restyled around light, the planet companion, Google sign-in; planet crash fix |
| 1.8.0 | 2026-11-04 | Star Path, Orbit, Meteor Drift; clearer Odd One Out |
| 1.9.0 / 1.9.1 | 2026-11-05 | Today, meters, planet surface, diary, heatmap, stats and badge redesign; Orbit fix |
| 2.0.0 | 2026-11-07 | Pets and the procedural galaxy replace the planet |
| 2.1.0 / 2.1.1 | 2026-11-08 | Galaxy on every phone and on the dusk sky; new achievements, stats and goals; "Our little world" scene |
| 2.2.0 | 2026-11-08 | Occasions with effects, 26 more achievements, pet personalities and new voices, tug-of-war head to head, cycle and diary changes, verified badge |
