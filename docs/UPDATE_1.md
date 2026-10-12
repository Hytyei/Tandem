# Update 1

## Apply order
1. Copy the changed files (same relative paths). Delete the two old launcher XML drawables (see below).
2. Edit `android/gradle.properties` (partial edit): set `TANDEM_GITHUB_REPO=Hytyei/Tandem`; keep `TANDEM_API_URL=https://tandem-api.thehdv7.workers.dev`.
3. Backend: `cd backend && npx wrangler d1 execute tandem --remote --file=migrations/0002_update1.sql`, then `npx wrangler deploy`.
   If it reports "duplicate column name" it was already applied; stop. Do not re-run `schema.sql`.
4. Android Studio: Sync Project (no new dependencies), then Build > Clean, Run.

## Security review
Fixed: tokens/cache now encrypted (Keystore); token sent only to the API host over https; EXIF stripped from photos;
login/register/join-code/delete rate limiting; security headers, no CORS; 64 KB JSON cap; 8-char minimum for new passwords;
updater restricted to the repo's release assets, SHA-256 checked; lock-screen-safe notifications.
Not implemented (suggestions): server-side token revocation and a shorter JWT lifetime (currently 90 days).
Removed partners' data stays in D1 on purpose so a reconnect can restore it.
