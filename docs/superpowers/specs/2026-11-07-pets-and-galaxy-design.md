# v2.0.0: pets and the galaxy (design)

Date: 2026-11-07. Replaces the planet companion (2026-11-03 spec). Requested by the owner with art and galaxy references.

## Stardust, items and storages (the economy)

Everything you already do in Tandem earns, counted on the server from its own tables (so it can't be faked):

| Action | Stardust | The doer's storage |
| --- | --- | --- |
| Share a mood (3 a day count) | 3 | +2 affection, +1 food |
| Diary note (5 a day) | 5 | +2 food, +1 drink |
| Picture (6 a day) | 2 | +1 affection |
| Game round (30 a day) | 2 | +1 drink, +1 food |
| Finished tracked date (3 a day) | 15 | both: +3 affection, +2 food, +2 drink |
| Both shared a mood today | +5 | — |
| Every 7 days in a row you both shared | — | +1 comet |
| Milestones (days together, moods, notes, dates, rounds) | 40–300 | big ones +1 comet |

- **Stardust** is the couple's wallet. **Lifetime stardust** never goes down; it is what grows the galaxy.
- **Storages** are per person (food, drink, affection; 0–99 each). You feed and love the pets from your own storage.
- **Comets** are the in-app item for re-rolling the galaxy (a currency later).
- The planet's stardust carries over, plus a welcome start: 5 of each storage for each of you and one comet.

## Pets

- Species and breeds: cats (Persian, Scottish Fold, British Shorthair, Siamese, Ginger Tabby, Tuxedo), dogs (Golden Retriever, Shiba Inu, Corgi, Pomeranian, Husky), hamsters (Syrian, Winter White, Panda, Roborovski), rabbits (Holland Lop, Netherland Dwarf, Lionhead, Rex), parrots (Budgie, Cockatiel, Lovebird, Sun Conure).
- Up to 3 pets. The first is free; the second costs 200 stardust, the third 500. Each extra pet makes every pet's needs fall faster (×1.35 per extra pet): more pets, more upkeep.
- Needs, 0–100, falling with real time: hunger (~24 h from full), thirst (~20 h), love (~36 h).
- Feed (1 food, +22), water (1 drink, +25), cuddle (1 affection, +18). A free pat once every 30 minutes (+2 love).
- **Bond** with each of you (0–100) grows when that person cares for the pet and fades slowly; the pet shows who it prefers.
- When its needs average below 15 the pet sulks: duller colours, droopy pose, grumpier lines. It never dies.
- Art: hand-drawn in code in the references' kawaii style (thick warm outline, soft fills, big glossy eyes, blush, paw beans, sparkles), set in a warm pool of light on the app's dusk.
- Alive: breathing, blinking, tail and ears, plus species actions (cats lick a paw, knead, meow; dogs wag, pant, bark; hamsters nibble and puff their cheeks; rabbits twitch their nose and hop; parrots bob and flap). Short, low, cute voices synthesized on the phone (mute switch in the pet page).
- Comic speech bubbles with dry, funny lines to the couple or to one of you, by situation (hungry, ignored by one of you, a favourite, a full belly, late at night…).

## Galaxy

- A procedural 3D galaxy from a seed: spiral (2–5 arms, bars, tightness), ring or lens shapes, palettes after the references (teal arms with a gold core, silver monochrome, magenta and violet nebulae, rose, amber and teal), dust lanes, nebulae, many thousands of particles on black.
- Grows with lifetime stardust: more stars, more nebulae, brighter core (from about 6,000 to 90,000 particles).
- Drag to rotate (with momentum), slow idle spin, pinch to zoom; rendered with OpenGL point sprites and additive light.
- Re-roll: spend a comet, get three new candidates to preview, keep the one you like.

## Architecture

- Server: `backend/src/cosmos.ts` (pure, unit-tested) + `cosmos` table (one JSON document per couple, versioned like the planet). `GET /cosmos`, `POST /cosmos/pets/{adopt,feed,water,cuddle,pat,rename,release}`, `POST /cosmos/galaxy/{reroll,choose}`. `/sync` carries a summary. Planet routes stay for older app versions; the app stops using them.
- App: `PetArt` (rig + species drawing), `PetStage` (animation), `PetVoice` (synth), `PetLines` (bubbles), `PetsScreen`, `GalaxyGen` + `GalaxyGL` (renderer), `GalaxyScreen`, cards on Today, a pet widget, the planet widget becomes the galaxy widget.
