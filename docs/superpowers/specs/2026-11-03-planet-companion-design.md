# Our planet: the couple's companion (design)

Date: 2026-11-03. Status: approved direction ("a good start, let's see what you can do"). Phase 1 is built in v1.7.0.

## Intent

Couples apps keep people coming back with a shared pet or plant. Tandem's version is a small planet the two of
you keep alive, in Tandem's night-sky language. It has to have depth and real consequences: neglect costs
something, sometimes for good, and the choices you make shape what the world becomes. It must never feel like a
chore list, never shame one partner, and it must be fair to real life (holidays, busy weeks).

## The world

- One planet per couple, created the first time either of you opens it. You name it together (either can rename).
- It orbits **two suns, one per partner**. Each sun's brightness (0..100) follows that partner's own presence.
- Vital signs, all 0..100: **atmosphere** (shared presence), **warmth** (mean of the two suns), **ice**, **life**.
- Four **biomes** share the surface: ocean, forest, crystal, aurora. Their shares always add up to 100.
- **Species** (14 in the catalog) appear when the world suits them, go dormant when it doesn't, and can die out.
- **Moons** are earned and can be lost. **Landmarks** are made from your tracked dates. **Buildings** cost upkeep.
- A **chronicle** keeps what happened, and an archive keeps what was lost.

## Where its energy comes from (what you already do in Tandem)

Counted per day (in the couple's time zone), from the existing tables, so it can't be faked from the client:

| Action | Effect |
| --- | --- |
| Sharing a mood (each partner, once a day counts) | that partner's sun +12, light for the atmosphere |
| Both sharing on the same day (a "conjunction") | extra light, counts toward the next moon |
| Diary notes, pictures | life grows (when the world allows it) |
| Game rounds played | stardust |
| A finished tracked date | a landmark named after it, stardust |

## One simulated day

For every day that has ended since the last visit (catch-up runs on read):

1. Suns: a partner who shared a mood +12, otherwise −9.
2. Light = shares (0, 1 or 2) + 1 bonus for a conjunction. Atmosphere += 6 × light − 7 − upkeep (0.5 per standing building).
3. Ice drifts toward the cold: += (40 − warmth) / 4 when warmth < 40, otherwise −(warmth − 40) / 6; plus |sunA − sunB| / 25 (a lopsided orbit means harsh seasons).
4. Life: if atmosphere > 45 and ice < 45, += 2 + 3 per diary note + 2 per picture (max +12); else −3.
5. Stardust += 2 per game round + 5 per finished date.
6. Quiet days (neither of you shared) count up; any share resets them.
7. Species wake, appear, go dormant or die out (rules below). Buildings fall into ruin after 3 days below 25 atmosphere.
8. Every 20 conjunction days a new moon is born.

## Consequences

| Quiet days | What happens |
| --- | --- |
| 3 | **Dimming**: the planet desaturates, one living species goes dormant |
| 7 | **Winter**: ice at least 70, one landmark buried under ice |
| 14, then every 7 more | **Drift**: a moon leaves orbit for good; with no moons left, a dormant species dies out |

Losses are permanent and listed in the archive. Low atmosphere also ruins buildings and stops life.

### Coming back: rekindling

After a dimming or worse, the world needs **both** of you: each sends the other a short **spark** (a note).
When both sparks land within 24 hours the planet rekindles: atmosphere +25, ice −20, quiet days reset, dormant
species can wake. What was lost stays lost.

### Fairness

- **Hibernation**: once per calendar month, either of you can put the planet to sleep for 3–14 days. Nothing decays
  and nothing grows. Ends early when you choose.
- **Warm their sun**: once a day, a partner who shared a mood can lend light to the other's sun (+10). The suns show
  who has been away; nothing in the UI blames anyone.

## Choices (trade-offs)

- **Terraform** (30 stardust): grow one biome by 10, the others shrink in proportion. It decides which species can live.
- **Build** (stardust, then upkeep): observatory (40), lighthouse (60), garden (50), bridge (80, named after you two),
  aurora station (100). Each adds a light on the surface and costs 0.5 atmosphere a day. Ruins can be **repaired** for half
  the price.
- **Rename** moons when they are born.

## Species catalog (needs: biome share, life, max ice)

glowfish (ocean 30, life 30), reef bloom (ocean 40, life 50), tide whale (ocean 50, life 70),
mossling (forest 25, life 25), lantern tree (forest 35, life 45), deer of dusk (forest 45, life 65),
prism moth (crystal 25, life 30), geode crab (crystal 40, life 50), star lily (crystal 30 + forest 20, life 55),
aurora jelly (aurora 25, life 35), snow owl (aurora 30, ice ≥ 30 allowed, life 40), sky ray (aurora 40 + ocean 20, life 60),
twin finch (any, life 40, conjunction streak ≥ 7), comet fox (any, life 80, 2+ moons).

A species **appears** when its needs have held for 2 days; goes **dormant** when they fail (or in a dimming);
**wakes** after 2 good days; **dies out** after 10 dormant days, or in a drift.

## Phase 2 (later)

Weekly decisions you both vote on (a comet: capture as a moon or seed the oceans), diary pictures as constellations,
the anniversary year-in-review sky.

## Architecture

- Backend: `planets` table (couple_id, state JSON, day, version). Pure simulation in `backend/src/planet.ts`
  (deterministic, unit-tested); handlers in `index.ts`: `GET /planet`, `POST /planet/{name,terraform,build,repair,warm,spark,hibernate,wake}`.
  `/sync` carries a small `planet` summary (stage, quiet days, sparks) for notifications and the widget.
- App: `PlanetScreen` (full page from a card on Today), drawn by one painter used by both Compose and the widget,
  in the games' quiet dreamy style. A fifth widget, "Our planet".
- Day boundaries use the couple's time-zone offset, sent by the app and stored with the planet.

## Testing

Unit tests for the simulation (growth, each consequence stage, rekindling, hibernation, upkeep ruin, species rules),
API smoke tests, and a visual check on the emulator.
