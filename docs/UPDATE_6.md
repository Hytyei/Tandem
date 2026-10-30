# v1.5.0: new Play page, game alerts, Google sign-in

1. **Play redesign.** Play home = "Your turn" section (pulsing cards, partner's score shown), links to Recent games / Goals / Stats, then game tiles grouped by type. Each game has its own page (`ui/screens/GamePages.kt`): rules, new match, running matches, stats (bests, wins, rounds, speed, accuracy), hall of fame, past matches. Route-string navigation lives in `GamesScreen.kt`; shared pieces in `GamesShared.kt`.
2. **Play tab badge** shows how many games wait for you.
3. **Notifications** for games waiting (new `awaiting` list in `/sync`), new moods and new diary entries. Switches in Profile. Delivery while the app is closed is polled (about every 15 min); instant delivery would need a push server (Firebase).
4. **Google sign-in / sign-up and account linking.** `POST /auth/google`, `GET/POST/DELETE /me/google`, `PUT /me/password`. The backend verifies Google's ID token signature against Google's public keys. No secret is needed, only the public web client id.
5. **New activities:** Shopping, Doom scrolling (artwork + backend list).

## Setup
- `cd backend && npx wrangler d1 execute tandem --remote --file=migrations/0008_google.sql`
- Add `GOOGLE_CLIENT_ID = "<web client id>.apps.googleusercontent.com"` under `[vars]` in your `wrangler.toml`, then `npx wrangler deploy`.
- Add `GOOGLE_WEB_CLIENT_ID=<same id>` to `android/gradle.properties` (or env/CI), then Gradle sync (new Credential Manager dependencies).
- Google Cloud Console: create a Web OAuth client and an Android OAuth client (package `app.tandem.couples`, SHA-1 of your release keystore, plus the debug SHA-1 for debug builds); configure the consent screen.
- Google-only accounts must set a password before disconnecting Google or deleting the account.
