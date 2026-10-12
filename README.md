# Tandem 💞

A private space for two, in a dreamy dusk-lit look: shared mood tracker with illustrated activities, a diary wall, an event
calendar with countdowns and reminders, and home-screen widgets. Native Android (Kotlin + Jetpack
Compose + Glance) with a free Cloudflare Workers + D1 backend. Updates ship through GitHub Releases.

```
Tandem/
├─ android/        Android Studio project (open this folder)
├─ backend/        Cloudflare Worker + D1 (TypeScript, zero runtime dependencies)
├─ tools/          Scene painter: renders the illustrations (numpy + Pillow) into the Android project
└─ .github/        CI: signed APK on tag, optional backend deploy
```

## 1 · Deploy the backend (Cloudflare free plan, no card needed)

```bash
cd backend
npm install
npx wrangler login
npx wrangler d1 create tandem          # copy the printed database_id into wrangler.toml
npm run db:init                        # creates the tables
npx wrangler secret put JWT_SECRET     # paste a long random string (e.g. `openssl rand -base64 48`)
npm run deploy                         # prints https://tandem-api.<you>.workers.dev
```

Check it: open the printed URL — you should see `{"ok":true,"app":"tandem"}`.
Profile pictures are stored (resized to 384 px by the app, ~30 KB) inside D1, so no R2 bucket or payment method is required.

Local dev: copy `.dev.vars.example` to `.dev.vars`, run `npm run db:init:local && npm run dev`.

## 2 · Run the app

1. Use **Android Studio Rabbit 1 | 2026.2.1** (or newer; JDK 21 is bundled). The project targets AGP 9.4.0, Gradle 9.6.0 (Studio offers to create the wrapper),
   Kotlin 2.4.x (built into AGP 9) and compileSdk 36. Android Studio → **Open** → select the `android/` folder (it creates the Gradle wrapper and syncs).
2. In `android/gradle.properties` set:
   - `TANDEM_API_URL` → your Worker URL
   - `TANDEM_GITHUB_REPO` → `your-user/your-repo` (for updates)
3. Run on a device/emulator (Android 8.0+). Create an account, tap **Create invite code**, and sign up the
   second account on another device/emulator to enter the code. (You can also change the server address
   at runtime: Sign-in screen → *Server settings*, or Profile → *Server*.)
4. Long-press the home screen → Widgets → **Tandem** to add *Partner mood*, *Both moods*, *Diary wall* or *Next event*.
   All can be resized between 2×2 and 4×2 (and larger).

## 3 · Publish updates via GitHub

One-time: create a signing key. **Keep it forever — Android only installs an update if it is signed with the same key.**

```bash
keytool -genkeypair -v -keystore tandem.jks -alias tandem -keyalg RSA -keysize 2048 -validity 10000
base64 -w0 tandem.jks        # macOS: base64 -i tandem.jks
```

Add these repository secrets (GitHub → Settings → Secrets and variables → Actions):

| Secret | Value |
|---|---|
| `TANDEM_KEYSTORE_B64` | output of the base64 command |
| `TANDEM_KEYSTORE_PASSWORD` / `TANDEM_KEY_PASSWORD` | the passwords you chose |
| `TANDEM_KEY_ALIAS` | `tandem` |
| `TANDEM_API_URL` | your Worker URL (so CI builds point at it) |
| `CLOUDFLARE_API_TOKEN`, `CLOUDFLARE_ACCOUNT_ID` | only for the optional backend auto-deploy |

Release a new version:

```bash
git tag v1.1.0 && git push origin v1.1.0
```

The workflow builds `Tandem-1.1.0.apk`, publishes a GitHub Release, and installed apps show an
**Update available** banner on the Today tab (checked once a day, or manually in Profile → *Check for updates*).
The first time, Android asks you to allow Tandem to install apps.

For local release builds create `android/keystore.properties` (git-ignored) with `storeFile`, `storePassword`, `keyAlias`, `keyPassword`.

## Notes

- **Notifications**: event reminders are scheduled on the device (evening before at 20:00 and at the event time,
  or 09:00 for all-day events) and re-planned after every sync and reboot — no Firebase needed. Partner mood/diary
  changes arrive through pull-to-refresh, when the app opens, the widget refresh button, and a 15-minute background sync.
- **Widgets** are minimal rounded cards: your partner's current activity artwork (or mood painting) fills the widget with their name, activity and
  "updated … ago" over a soft scrim, plus a small refresh chip. The artwork is part of the synced mood, so your partner's art appears on *your* widget.
  Corners are clipped with `cornerRadius` (Android 12+); older versions show square corners.
- **Artwork**: the 14 activities (gaming, sleeping, eating, tv, music, football, cycling, hiking, camping, roadtrip, stargazing, photography, hair, nails)
  are your supplied paintings in `tools/provided/act_<key>.jpg`. Moods and the home banner are painted by `tools/scenes.py`.
  Re-export with `pip install numpy pillow && python3 tools/export.py android/app/src/main/res`.
  To add an activity: drop `tools/provided/act_<key>.jpg`, add `<key>` to `ACTIVITY_KEYS` in `tools/scenes.py`, to `ACTIVITIES` in `backend/src/index.ts`,
  and to the `Activity` enum in `Catalog.kt`. Make sure you hold the rights to artwork you ship in the app.
- **Privacy**: passwords are PBKDF2-hashed (100k iterations); avatars are only served to you and your partner.
