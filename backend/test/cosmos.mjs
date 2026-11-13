// Unit tests for the cosmos (backend/src/cosmos.ts).
const C = await import(new URL("../src/cosmos.ts", import.meta.url).href);
let fails = 0;
const ok = (name, cond, extra) => { console.log((cond ? "PASS " : "FAIL ") + name + (cond ? "" : " " + JSON.stringify(extra))); if (!cond) fails++; };
let r = 1; const rand = () => { r = (r * 16807) % 2147483647; return (r - 1) / 2147483646; };
const A = "a", B = "b";
const T0 = Date.parse("2026-11-01T08:00:00Z");
const H = 3_600_000, D = 24 * H;

let c = C.newCosmos([B, A], T0, 0, 120, rand);
ok("carries the planet's stardust and gives a head start", c.stardust === 120 && c.lifetime === 420 && c.comets === 1 && c.stores[A].food === 5);

// earning
C.applyEvents(c, [{ kind: "mood", user: A, at: T0 + 1 }, { kind: "note", user: A, at: T0 + 2 }, { kind: "round", user: B, at: T0 + 3 }]);
ok("events earn stardust", c.stardust === 120 + 3 + 5 + 2, c.stardust);
ok("events fill the doer's storage", c.stores[A].love === 7 && c.stores[A].food === 8 && c.stores[A].drink === 6 && c.stores[B].drink === 6, c.stores);
C.applyEvents(c, [{ kind: "mood", user: A, at: T0 + 1 }]);
ok("an event is only counted once", c.stardust === 130);
C.applyEvents(c, Array.from({ length: 6 }, (_, i) => ({ kind: "mood", user: A, at: T0 + 10 + i })));
ok("daily caps (3 moods a day)", c.counts["a:mood"] === 3 && c.stardust === 136, c.stardust);
C.applyEvents(c, [{ kind: "mood", user: A, at: T0 + D }]);
ok("caps reset the next day", c.counts["a:mood"] === 1);
const before = c.stardust;
C.applyEvents(c, [{ kind: "mood", user: B, at: T0 + D + 5 }]);
ok("both sharing on a day: a bonus and a streak", c.stardust === before + 3 + 5 && c.streak === 1, { s: c.stardust, st: c.streak });
for (let d = 2; d <= 7; d++) C.applyEvents(c, [{ kind: "mood", user: A, at: T0 + d * D }, { kind: "mood", user: B, at: T0 + d * D + 1 }]);
ok("7 shared days in a row: a comet", c.streak === 7 && c.comets === 2, { st: c.streak, cm: c.comets });
C.applyEvents(c, [{ kind: "trip", user: A, at: T0 + 7 * D + 9 }]);
ok("a tracked date fills both storages", c.stores[B].love > 5 && c.stores[A].drink > 6);

// milestones
const lt = c.lifetime;
C.applyMilestones(c, { days: 120, moods: 12, notes: 0, trips: 1, rounds: 3 }, T0);
ok("milestones pay once", c.milestones.length === 4 && c.lifetime === lt + 60 + 120 + 40 + 40 && c.comets === 3, c.milestones);
C.applyMilestones(c, { days: 120, moods: 12, notes: 0, trips: 1, rounds: 3 }, T0);
ok("…and only once", c.milestones.length === 4);

// pets
c.stardust = 250;
C.adopt(c, "cat", "persian", "Mochi", "p1", T0);
ok("the first pet is free", c.stardust === 250 && c.pets.length === 1 && c.pets[0].bond[A] === 15 && c.pets[0].xp === 0 && c.pets[0].moments.length === 1);
C.adopt(c, "dog", "shiba", "Kuma", "p2", T0);
ok("the second costs 200", c.stardust === 50);
let threw = false; try { C.adopt(c, "rabbit", "rex", "Bun", "p3", T0); } catch { threw = true; }
ok("the third costs 500", threw);
threw = false; try { C.adopt(c, "cat", "dragon", "X", "p9", T0); } catch { threw = true; }
ok("unknown breeds are refused", threw);

// needs fall faster with more pets
const one = C.newCosmos([A, B], T0, 0, 0, rand); C.adopt(one, "cat", "ginger", "Solo", "s", T0); C.tickPets(one, T0 + 10 * H);
C.tickPets(c, T0 + 10 * H);
ok("needs fall with time", one.pets[0].hunger === 38, one.pets[0].hunger);
ok("two pets: everyone gets hungrier faster", c.pets[0].hunger < one.pets[0].hunger, c.pets[0].hunger);

// care
const h0 = c.pets[0].hunger, f0 = c.stores[A].food;
C.feed(c, A, "p1", T0 + 10 * H);
ok("feeding uses your food, fills hunger, grows bond and growth", c.pets[0].hunger === Math.min(100, h0 + 22) && c.stores[A].food === f0 - 1 && c.pets[0].bond[A] > c.pets[0].bond[B] + 2 && c.pets[0].xp === 6, c.pets[0]);
c.stores[B].drink = 0; threw = false; try { C.water(c, B, "p1"); } catch { threw = true; }
ok("an empty store can't be spent", threw);
C.cuddle(c, A, "p1", T0 + 10 * H);
ok("cuddles grow the bond most", c.pets[0].bond[A] > c.pets[0].bond[B] + 7);
ok("a free pat", C.pat(c, B, "p1", T0 + 10 * H) === true && C.pat(c, B, "p1", T0 + 10 * H + 60_000) === false && C.pat(c, B, "p1", T0 + 11 * H) === true);

// neglect: sulking and fading bonds, never death
C.tickPets(c, T0 + 6 * D);
ok("neglect makes a pet sulk", C.mood(c.pets[0]) === "sulking" && c.pets.length === 2);
ok("…and its bonds fade faster", c.pets[0].bond[A] < 15, c.pets[0].bond);

// growing up: care and age both count
{
  const g = C.newCosmos([A, B], T0, 0, 0, rand); g.stores[A] = { food: 99, drink: 99, love: 99 }; g.stores[B] = { food: 99, drink: 99, love: 99 };
  C.adopt(g, "cat", "ginger", "Kit", "k", T0);
  ok("a newborn is a baby", C.stageOf(g.pets[0], T0) === 0 && C.growth(g.pets[0], T0) === 0);
  for (let i = 0; i < 40; i++) C.cuddle(g, A, "k", T0 + i * 1000);
  C.feed(g, B, "k", T0 + 20_000);
  ok("care alone doesn't grow it up overnight", C.stageOf(g.pets[0], T0 + H) === 0 && g.pets[0].xp >= 300, g.pets[0].xp);
  C.tickPets(g, T0 + 61 * D);
  ok("a kitten is young at two months", C.stageOf(g.pets[0], T0 + 61 * D) === 1 && g.pets[0].moments.some((m) => m.kind === "grow"), g.pets[0].moments);
  ok("…and the next step is a year old", C.nextStage(g.pets[0], T0 + 61 * D).days === 365);
  g.pets[0].xp = 2000;
  C.tickPets(g, T0 + 200 * D);
  ok("not grown before its time, however well cared for", C.stageOf(g.pets[0], T0 + 200 * D) === 1 && C.growth(g.pets[0], T0 + 200 * D) < 1);
  C.tickPets(g, T0 + 366 * D);
  ok("a cat at a year", C.stageOf(g.pets[0], T0 + 366 * D) === 2 && C.growth(g.pets[0], T0 + 366 * D) === 1 && C.nextStage(g.pets[0], T0 + 366 * D) === null);
  const h = C.newCosmos([A, B], T0, 0, 0, rand); C.adopt(h, "hamster", "syrian", "Pip", "h", T0); h.pets[0].xp = 500;
  ok("hamsters grow up in three months", C.stageOf(h.pets[0], T0 + 91 * D) === 2 && C.stageOf(h.pets[0], T0 + 80 * D) === 1);
  ok("both caring on a day: a together bonus", g.pets[0].moments.some((m) => m.kind === "together"));
  // bond levels, streaks, perks
  const b0 = g.pets[0].bond[A];
  ok("bond levels have names", C.BOND_LEVELS[C.bondLevel(b0)][1].length > 0 && C.bondLevel(95) === 5 && C.bondLevel(0) === 0);
  g.pets[0].bond[A] = 92; g.pets[0].pats = {};
  const before = { ...g.stores[A] };
  ok("a soulmate's pat brings a gift once a day", C.pat(g, A, "k", T0 + 9 * D) && g.stores[A].love === Math.min(99, before.love + 2) && g.pets[0].moments[0].kind === "gift", g.pets[0].moments[0]);
  ok("friends wait less between pats", C.patWait(g.pets[0], A) === 20 * 60_000 && C.patWait({ bond: { x: 5 } }, "x") === 30 * 60_000);
  ok("…once a day", C.pat(g, A, "k", T0 + 9 * D + H) && g.stores[A].love === Math.min(99, before.love + 2));
  const old = C.newCosmos([A, B], T0, 0, 0, rand); C.adopt(old, "dog", "shiba", "Old", "o", T0); delete old.pets[0].xp;
  C.tickPets(old, T0 + 5 * D);
  ok("pets from before growth get growth for their age", old.pets[0].xp === 150 && C.stageOf(old.pets[0], T0 + 5 * D) === 0, old.pets[0].xp);
}

// galaxy
const s0 = c.seed, comets = c.comets;
C.reroll(c, rand);
ok("a re-roll costs a comet and gives three choices", c.comets === comets - 1 && c.candidates.length === 3 && c.seed === s0);
C.choose(c, c.candidates[1], T0);
ok("choosing one keeps it", c.seed !== s0 && c.candidates.length === 0);
threw = false; try { C.choose(c, 12345, T0); } catch { threw = true; }
ok("you can only choose from your candidates", threw);
c.comets = 0; threw = false; try { C.reroll(c, rand); } catch { threw = true; }
ok("no comet, no re-roll", threw);

C.release(c, "p2", T0);
ok("release", c.pets.length === 1 && c.log[0].kind === "release");
const sm = C.summary(c);
ok("summary", sm.pets.length === 1 && sm.pets[0].mood === "sulking" && typeof sm.seed === "number");

console.log(fails ? `\n${fails} FAILED` : "\nall passed");
process.exit(fails ? 1 : 0);
