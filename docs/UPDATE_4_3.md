# Update 4.3 (v1.3.3)

- "Right now" gets five slots: series (`watching`), movie (`movie`, new), game (`playing`), book (`reading`), track you're obsessed with (`listening`).
- Today shows you and your partner head to head: both people's picks side by side with posters, and the meters.
- "Edit mine" on Today opens a short editor for those five slots and your meters.
- Better, free, keyless lookups.

## Apply order
1. Copy the changed files (same relative paths; `ui/screens/RightNowEditor.kt` is new). This zip also contains Updates 4, 4.1 and 4.2.
2. Backend: no migration (the profile is stored as JSON). Redeploy: `npx wrangler deploy` from the `backend` folder (it adds the `movie` field and allows the new poster image hosts).
3. Android Studio: no Gradle sync needed. Clean Project, Run.
4. Release: tag `v1.3.3`.

## Where the information comes from (all free, no account, no key)
| Slot | Main source | Fallback |
|---|---|---|
| Series | TVmaze search (poster, premiere year, channel, genres, summary) | IMDb search suggestions |
| Movie | IMDb search suggestions (poster, year, main cast) | Apple iTunes Search (poster, genre, description) |
| Game | Steam store search + app details (cover, year, developer, genres, description) | Wikipedia |
| Book | Google Books (cover, authors, year, description) | Open Library |
| Track | Deezer (album art, artist, album, length) | Apple iTunes Search |

Notes:
- The lookups run on the phone only when the magnifier is tapped. Only the typed title goes to those services, and the Tandem token is never sent.
- IMDb's suggestion endpoint and Steam's store endpoints are public but not officially documented APIs, so they could change. That's why each slot has a fallback and why picking a result (or typing your own text with no poster) always works.
- The Tandem server only stores the title, a short description and a picture address, and only accepts picture addresses from these hosts: static.tvmaze.com, m.media-amazon.com, *.steamstatic.com, *.dzcdn.net, books.google.com, books.googleusercontent.com, mzstatic.com, covers.openlibrary.org, upload.wikimedia.org.
- If you want an even richer source later, TMDB (movies/series) and RAWG (games) are great but need a free API key. That would have to live as a Worker secret behind a small proxy endpoint, never in the app or repo. Ask if you want that.

## Test
- `node --experimental-transform-types --no-warnings backend/test/smoke.mjs` all passing.
- My page > Right now: search "Inception" (movie), "Severance" (series), "Cyberpunk 2077" (game), "Dune" (book), "Blinding Lights" (track); each should return posters.
- Today: both of you appear side by side per slot; "Edit mine" opens the quick editor; save and check the other phone.
