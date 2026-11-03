// Unit tests for the planet simulation (backend/src/planet.ts).
const P = await import(new URL("../src/planet.ts", import.meta.url).href);
let fails = 0;
const ok = (name, cond, extra) => { console.log((cond ? "PASS " : "FAIL ") + name + (cond ? "" : " " + JSON.stringify(extra))); if (!cond) fails++; };

const A = "a-user", B = "b-user";
const both = { shares: { [A]: true, [B]: true }, notes: 1, pictures: 0, rounds: 2, trips: [] };
const onlyA = { shares: { [A]: true, [B]: false }, notes: 0, pictures: 0, rounds: 0, trips: [] };
const none = { shares: {}, notes: 0, pictures: 0, rounds: 0, trips: [] };
const day = (start, n) => { let d = start; for (let i = 0; i < n; i++) d = P.nextDay(d); return d; };
const run = (s, from, n, act) => { let d = from; for (let i = 0; i < n; i++) { P.simulateDay(s, d, typeof act === "function" ? act(i) : act, "x"); d = P.nextDay(d); } return d; };

// ---- a good month
let s = P.newPlanet([B, A], "2026-01-01", 0);
ok("new planet sorts members and starts thriving", s.members[0] === A && s.stage === "thriving" && s.day === "2025-12-31");
let d = run(s, "2026-01-01", 30, both);
ok("sharing every day keeps the suns full", s.suns[A] === 100 && s.suns[B] === 100, s.suns);
ok("atmosphere and life grow", s.atmosphere > 90 && s.life > 80, { a: s.atmosphere, l: s.life });
ok("ice melts", s.ice === 0, s.ice);
ok("stardust from rounds", s.stardust === 30 + 30 * 4, s.stardust);
ok("first moon after 7 shared days, second after 27", s.moons.length === 2 && s.moons[0].born === day("2026-01-01", 6), s.moons);
const alive = Object.entries(s.species).filter(([, v]) => v.state === "alive").map(([k]) => k);
ok("species appear (balanced biomes)", alive.includes("mossling") && alive.includes("prismmoth") && alive.includes("twinfinch"), alive);
ok("comet fox needs two moons and life 80", alive.includes("cometfox"), alive);

// ---- lopsided: only one of you shows up
let t = P.newPlanet([A, B], "2026-01-01", 0);
run(t, "2026-01-01", 10, onlyA);
ok("an absent partner's sun dims", t.suns[B] < 20 && t.suns[A] === 100, t.suns);
ok("an uneven orbit brings ice", t.ice > 20, t.ice);
ok("one partner alone doesn't trigger the silence stages", t.quiet === 0 && t.stage === "thriving", t.stage);

// ---- silence
let q = P.newPlanet([A, B], "2026-01-01", 0);
d = run(q, "2026-01-01", 30, both);
const moonsBefore = q.moons.filter((m) => !m.lost).length;
d = run(q, d, 3, none);
ok("3 quiet days: dimming, a species goes dormant", q.stage === "dimming" && q.needsRekindle && q.chronicle[0].kind !== "x" && Object.values(q.species).some((v) => v.state === "dormant"), q.chronicle.slice(0, 3));
q.landmarks.push({ id: "t1", name: "Sunset walk", day: d });
d = run(q, d, 4, none);
ok("7 quiet days: winter, ice 70, a landmark buried", q.stage === "winter" && q.ice >= 70 && q.landmarks[0].buried, { stage: q.stage, ice: q.ice });
d = run(q, d, 7, none);
ok("14 quiet days: drift, a moon is lost for good", q.stage === "drift" && q.moons.filter((m) => !m.lost).length === moonsBefore - 1, q.moons);
d = run(q, d, 7, none);
ok("21 quiet days: another moon lost", q.moons.filter((m) => !m.lost).length === moonsBefore - 2, q.moons);
d = run(q, d, 7, none);
ok("28 quiet days: no moons left, a dormant species dies out", Object.values(q.species).some((v) => v.state === "extinct"), q.species);
const lostMoons = q.moons.filter((m) => m.lost).length;

// coming back: sharing alone isn't enough
d = run(q, d, 3, both);
ok("sharing again doesn't undo it: still needs rekindling", q.needsRekindle && q.stage !== "thriving", q.stage);
ok("no life grows until rekindled", q.life <= 30, q.life);
ok("a single spark doesn't rekindle", P.spark(q, A, "miss you", d, 1000) === false && q.needsRekindle);
ok("a second spark from the other within a day does", P.spark(q, B, "me too", d, 1000 + 3600_000) === true && !q.needsRekindle && q.stage === "thriving");
const lifeAt = q.life;
d = run(q, d, 10, both);
ok("after rekindling the world recovers (slowly, the ice melts first), lost moons stay lost", q.ice < 45 && q.life >= 20 && q.life > lifeAt && q.moons.filter((m) => m.lost).length === lostMoons, { life: q.life, ice: q.ice });

// sparks too far apart don't count
let f = P.newPlanet([A, B], "2026-01-01", 0); run(f, "2026-01-01", 3, none);
P.spark(f, A, "hi", "2026-01-04", 0);
ok("sparks more than 24h apart don't rekindle", P.spark(f, B, "hi", "2026-01-05", 25 * 3600_000) === false && f.needsRekindle);

// ---- hibernation
let h = P.newPlanet([A, B], "2026-02-01", 0);
run(h, "2026-02-01", 5, both);
const before = JSON.stringify([h.atmosphere, h.ice, h.life, h.suns]);
P.hibernate(h, 10, "2026-02-06");
ok("hibernate", h.stage === "sleeping" && h.hibernate.until === "2026-02-15");
run(h, "2026-02-06", 10, none);
ok("nothing decays while asleep", JSON.stringify([h.atmosphere, h.ice, h.life, h.suns]) === before && h.quiet === 0);
P.simulateDay(h, "2026-02-16", both, "x");
ok("it wakes by itself", !h.hibernate && h.chronicle.some((e) => e.kind === "wake"));
let threw = false; try { P.hibernate(h, 5, "2026-02-20"); } catch { threw = true; }
ok("only once a month", threw);

// ---- buildings, upkeep, ruin
let b = P.newPlanet([A, B], "2026-03-01", 0);
b.stardust = 200;
P.build(b, "observatory", "2026-03-01", "b1");
P.build(b, "lighthouse", "2026-03-01", "b2");
ok("build costs stardust", b.stardust === 100 && b.buildings.length === 2);
threw = false; try { P.build(b, "aurora", "2026-03-01", "b3"); P.build(b, "aurora", "2026-03-01", "b4"); } catch { threw = true; }
ok("can't build without stardust", threw);
run(b, "2026-03-01", 8, none);
ok("low light for 3 days ruins the newest building", b.buildings.some((x) => x.ruined), b.buildings);
const ruined = b.buildings.find((x) => x.ruined);
b.stardust = 100; P.repair(b, ruined.id, "2026-03-13");
ok("repair for half price", !ruined.ruined && b.stardust < 100);

// ---- terraform
let g = P.newPlanet([A, B], "2026-04-01", 0);
g.stardust = 300;
for (let i = 0; i < 5; i++) P.terraform(g, "ocean", "2026-04-01");
const sum = Object.values(g.biomes).reduce((x, y) => x + y, 0);
ok("terraform grows one biome, the rest shrink, total stays 100", g.biomes.ocean === 70 && Math.abs(sum - 100) < .5, g.biomes);
threw = false; try { P.terraform(g, "ocean", "2026-04-01"); } catch { threw = true; }
ok("a biome stops at 70", threw);

// ---- warm
let w = P.newPlanet([A, B], "2026-05-01", 0);
w.suns[B] = 30;
P.warm(w, A, true, "2026-05-01");
ok("warming lends the other sun light", w.suns[B] === 40);
threw = false; try { P.warm(w, A, true, "2026-05-01"); } catch { threw = true; }
ok("once a day", threw);
threw = false; try { P.warm(w, B, false, "2026-05-01"); } catch { threw = true; }
ok("only after sharing your mood", threw);

// ---- trips become landmarks; catch-up is deterministic
let c1 = P.newPlanet([A, B], "2026-06-01", 0), c2 = P.newPlanet([A, B], "2026-06-01", 0);
const act = (dd) => dd === "2026-06-03" ? { ...both, trips: [{ id: "trip1", title: "Belém by the river" }] } : (dd < "2026-06-10" ? both : none);
P.catchUp(c1, "2026-07-01", act, "couple"); P.catchUp(c2, "2026-07-01", act, "couple");
ok("catch-up runs to yesterday", c1.day === "2026-06-30");
ok("a finished date becomes a landmark", c1.landmarks.some((l) => l.name === "Belém by the river"));
ok("catch-up is deterministic", JSON.stringify(c1) === JSON.stringify(c2));
ok("day boundaries follow the time zone", P.dayOf(Date.parse("2026-06-01T22:00:00Z"), 210) === "2026-06-02" && P.dayOf(Date.parse("2026-06-01T22:00:00Z"), 0) === "2026-06-01");

console.log(fails ? `\n${fails} FAILED` : "\nall passed");
process.exit(fails ? 1 : 0);
