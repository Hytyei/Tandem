// The couple's cosmos: a stardust wallet, everyone's pet storages, up to three pets and a galaxy seed.
// Pure and deterministic given its inputs, so it can be unit-tested. See docs/superpowers/specs/2026-11-07-pets-and-galaxy-design.md.

export type Kind = "mood" | "note" | "pic" | "round" | "trip";
export interface Event { kind: Kind; user: string; at: number }

export interface Store { food: number; drink: number; love: number }
export interface Pet {
  id: string; species: string; breed: string; coat: number; name: string; born: number;
  hunger: number; thirst: number; love: number;
  bond: Record<string, number>;
  /** Growth points: care makes a pet grow up (with age). Missing on pets adopted before 2.3.0 until their first tick. */
  xp?: number;
  /** Per person: how often they've cared, their care streak and what they did last. */
  care?: Record<string, Care>;
  /** Per person: the day the pet last brought them a gift (soulmates only). */
  gifts?: Record<string, string>;
  /** The day both of you last cared for it (a together bonus, once a day). */
  together?: string;
  /** This pet's own small history: growing up, bonds deepening. Newest first, at most 24. */
  moments?: Moment[];
  lastTick: number;
  pats: Record<string, number>;      // user -> when they last gave a free pat
}
export interface Entry { at: number; kind: string; text: string }
export interface Care { feed: number; water: number; cuddle: number; pat: number; day: string; streak: number; last: string[] }
/** A moment in a pet's life. [user] is set when it's about one of you (the app shows the name). */
export interface Moment { at: number; kind: string; text: string; user?: string }

/** Bond levels: [from, name]. */
export const BOND_LEVELS: [number, string][] = [[0, "Stranger"], [15, "Acquaintance"], [30, "Friend"], [50, "Close friend"], [70, "Best friend"], [90, "Soulmate"]];
export const bondLevel = (b: number) => { let i = 0; BOND_LEVELS.forEach(([from], k) => { if (b >= from) i = k; }); return i; };
/** Growth stages: [name, growth points needed, days of age needed]. */
export const STAGES: [string, number, number][] = [["baby", 0, 0], ["young", 80, 2], ["adult", 300, 7]];

export interface Cosmos {
  v: 1;
  members: string[];
  tz: number;
  stardust: number;         // the wallet
  lifetime: number;         // everything ever earned: grows the galaxy
  comets: number;           // the re-roll item
  seed: number;             // the galaxy
  candidates: number[];     // re-roll choices waiting to be picked
  scannedAt: number;        // events up to here are counted
  day: string;              // the day the daily caps below belong to
  counts: Record<string, number>;   // "user:kind" -> how many counted today
  conjDay: string;          // the last day you both shared a mood (bonus given)
  streak: number;           // days in a row you both shared
  milestones: string[];
  stores: Record<string, Store>;
  pets: Pet[];
  log: Entry[];
}

export const SPECIES: Record<string, string[]> = {
  cat: ["persian", "scottish", "british", "siamese", "ginger", "tuxedo"],
  dog: ["golden", "shiba", "corgi", "pomeranian", "husky"],
  hamster: ["syrian", "winterwhite", "panda", "robo"],
  rabbit: ["hollandlop", "dwarf", "lionhead", "rex"],
  parrot: ["budgie", "cockatiel", "lovebird", "conure"],
};
export const ADOPT_COST = [0, 200, 500];
export const MAX_PETS = 3;
export const PAT_EVERY = 30 * 60_000;
const DAILY_CAP: Record<Kind, number> = { mood: 3, note: 5, pic: 6, round: 30, trip: 3 };
const STAR: Record<Kind, number> = { mood: 3, note: 5, pic: 2, round: 2, trip: 15 };
const GIFT: Record<Kind, Partial<Store>> = { mood: { love: 2, food: 1 }, note: { food: 2, drink: 1 }, pic: { love: 1 }, round: { drink: 1, food: 1 }, trip: { love: 3, food: 2, drink: 2 } };

/** Milestones: [id, what it counts, threshold, stardust, comets]. */
export const MILESTONES: [string, string, number, number, number][] = [
  ["days30", "days", 30, 60, 0], ["days100", "days", 100, 120, 1], ["days200", "days", 200, 150, 0], ["days365", "days", 365, 300, 1],
  ["days500", "days", 500, 200, 1], ["days730", "days", 730, 300, 1], ["days1000", "days", 1000, 300, 1],
  ["moods10", "moods", 10, 40, 0], ["moods100", "moods", 100, 120, 1], ["moods500", "moods", 500, 250, 1],
  ["notes10", "notes", 10, 60, 0], ["notes50", "notes", 50, 150, 1],
  ["dates1", "trips", 1, 40, 0], ["dates5", "trips", 5, 120, 1], ["dates20", "trips", 20, 250, 1],
  ["rounds25", "rounds", 25, 40, 0], ["rounds100", "rounds", 100, 120, 1], ["rounds500", "rounds", 500, 250, 1],
];

export class CosmosError extends Error { constructor(public status: number, msg: string) { super(msg); } }

export const dayOf = (ms: number, tz: number) => new Date(ms + tz * 60_000).toISOString().slice(0, 10);
const clamp = (v: number, lo = 0, hi = 100) => Math.max(lo, Math.min(hi, v));
const r1 = (v: number) => Math.round(v * 10) / 10;
const randSeed = (rand: () => number) => Math.floor(rand() * 2_147_483_000) + 1;

function log(c: Cosmos, at: number, kind: string, text: string) {
  c.log.unshift({ at, kind, text });
  if (c.log.length > 60) c.log.length = 60;
}

export function newCosmos(members: string[], now: number, tz: number, carry: number, rand: () => number): Cosmos {
  const [a, b] = [...members].sort();
  const c: Cosmos = {
    v: 1, members: [a, b], tz, stardust: carry, lifetime: carry + 300, comets: 1, seed: randSeed(rand), candidates: [],
    scannedAt: now, day: dayOf(now, tz), counts: {}, conjDay: "", streak: 0, milestones: [],
    stores: { [a]: { food: 5, drink: 5, love: 5 }, [b]: { food: 5, drink: 5, love: 5 } }, pets: [], log: [],
  };
  log(c, now, "born", "A galaxy began to spin");
  return c;
}

const give = (c: Cosmos, user: string, g: Partial<Store>) => {
  const s = c.stores[user]; if (!s) return;
  s.food = clamp(s.food + (g.food ?? 0), 0, 99); s.drink = clamp(s.drink + (g.drink ?? 0), 0, 99); s.love = clamp(s.love + (g.love ?? 0), 0, 99);
};
const earn = (c: Cosmos, n: number) => { c.stardust += n; c.lifetime += n; };

/** Counts new events (in time order), within each person's daily caps. */
export function applyEvents(c: Cosmos, events: Event[]) {
  const sorted = [...events].sort((x, y) => x.at - y.at);
  for (const e of sorted) {
    if (e.at <= c.scannedAt || !c.members.includes(e.user)) continue;
    const d = dayOf(e.at, c.tz);
    if (d !== c.day) { c.day = d; c.counts = {}; }
    const k = `${e.user}:${e.kind}`;
    const n = c.counts[k] ?? 0;
    if (n < DAILY_CAP[e.kind]) {
      c.counts[k] = n + 1;
      earn(c, STAR[e.kind]);
      if (e.kind === "trip") for (const m of c.members) give(c, m, GIFT.trip); else give(c, e.user, GIFT[e.kind]);
    }
    // both shared a mood today: a bonus, and a streak toward a comet
    if (e.kind === "mood" && c.conjDay !== d && c.members.every((m) => (c.counts[`${m}:mood`] ?? 0) > 0)) {
      const yesterday = new Date(Date.parse(d + "T00:00:00Z") - 86_400_000).toISOString().slice(0, 10);
      c.streak = c.conjDay === yesterday ? c.streak + 1 : 1;
      c.conjDay = d;
      earn(c, 5);
      if (c.streak % 7 === 0) { c.comets++; log(c, e.at, "comet", `${c.streak} days in a row you both shared. A comet for you`); }
    }
    c.scannedAt = Math.max(c.scannedAt, e.at);
  }
}

/** Unlocks every milestone the totals have reached that wasn't unlocked yet. */
export function applyMilestones(c: Cosmos, totals: Record<string, number>, at: number) {
  for (const [id, what, n, stars, comets] of MILESTONES) {
    if (c.milestones.includes(id) || (totals[what] ?? 0) < n) continue;
    c.milestones.push(id);
    earn(c, stars); c.comets += comets;
    log(c, at, "milestone", `Milestone: ${n} ${what === "days" ? "days together" : what === "trips" ? (n === 1 ? "tracked date" : "tracked dates") : what === "rounds" ? "game rounds" : what}. +${stars} stardust${comets ? " and a comet" : ""}`);
  }
}

/** Needs fall with time; every extra pet makes them all fall faster. Deep bonds fade slower. */
export function tickPets(c: Cosmos, now: number) {
  const mult = 1 + .35 * Math.max(0, c.pets.length - 1);
  for (const p of c.pets) {
    // pets from before growing existed: give them the growth their age would have earned
    if (p.xp === undefined) p.xp = Math.min(STAGES[2][1], Math.floor(Math.max(0, now - p.born) / 86_400_000) * 45);
    const h = Math.max(0, now - p.lastTick) / 3_600_000;
    if (h <= 0) continue;
    p.hunger = r1(clamp(p.hunger - 4.2 * h * mult));
    p.thirst = r1(clamp(p.thirst - 5 * h * mult));
    p.love = r1(clamp(p.love - 2.8 * h * mult));
    const sulking = (p.hunger + p.thirst + p.love) / 3 < 15;
    for (const m of c.members) {
      const b = p.bond[m] ?? 30;
      // trust is hard to lose: the deeper the bond, the slower it fades (sulking still costs)
      const fade = sulking ? .25 : Math.max(.012, .05 - bondLevel(b) * .007);
      p.bond[m] = r1(clamp(b - h * fade));
    }
    p.lastTick = now;
    checkGrowth(p, now);
  }
}

/** The stage this pet is at (0 baby, 1 young, 2 adult): growth points and age both have to be there. */
export function stageOf(p: Pet, now: number): number {
  const days = Math.max(0, now - p.born) / 86_400_000;
  let st = 0;
  STAGES.forEach(([, xp, d], k) => { if ((p.xp ?? 0) >= xp && days >= d) st = k; });
  return st;
}

/** 0..1 across the whole life: 0 a newborn, 1 grown up. Smooth, so the art can grow gradually. */
export function growth(p: Pet, now: number): number {
  const st = stageOf(p, now);
  if (st >= 2) return 1;
  const [, x0, d0] = STAGES[st]; const [, x1, d1] = STAGES[st + 1];
  const days = Math.max(0, now - p.born) / 86_400_000;
  const fx = Math.min(1, Math.max(0, ((p.xp ?? 0) - x0) / (x1 - x0)));
  const fd = Math.min(1, Math.max(0, (days - d0) / (d1 - d0)));
  return (st + Math.min(fx, fd)) / 2;
}

function moment(p: Pet, at: number, kind: string, text: string, user?: string) {
  p.moments = [{ at, kind, text, ...(user ? { user } : {}) }, ...(p.moments ?? [])].slice(0, 24);
}

function checkGrowth(p: Pet, now: number) {
  const st = stageOf(p, now);
  const seen = (p.moments ?? []).filter((m) => m.kind === "grow").length;
  if (st > seen) moment(p, now, "grow", st === 1 ? `${p.name} is growing up` : `${p.name} is all grown up`);
}

export const mood = (p: Pet) => {
  const avg = (p.hunger + p.thirst + p.love) / 3;
  return avg >= 70 ? "happy" : avg >= 40 ? "okay" : avg >= 15 ? "grumpy" : "sulking";
};

// ------------------------------------------------------------------ actions
function pet(c: Cosmos, id: string): Pet {
  const p = c.pets.find((x) => x.id === id);
  if (!p) throw new CosmosError(404, "No such pet");
  return p;
}

export function adopt(c: Cosmos, species: string, breed: string, name: string, id: string, now: number, coat = 0) {
  if (!SPECIES[species]?.includes(breed)) throw new CosmosError(400, "Unknown pet");
  if (c.pets.length >= MAX_PETS) throw new CosmosError(409, "Three pets is the most you can look after");
  const cost = ADOPT_COST[c.pets.length];
  if (c.stardust < cost) throw new CosmosError(409, `Adopting another pet takes ${cost} stardust`);
  c.stardust -= cost;
  c.pets.push({ id, species, breed, coat: Math.max(0, Math.min(2, Math.floor(coat) || 0)), name, born: now, hunger: 80, thirst: 80, love: 70, bond: Object.fromEntries(c.members.map((m) => [m, 15])), lastTick: now, pats: {}, xp: 0, care: {}, gifts: {}, moments: [{ at: now, kind: "adopt", text: `${name} came home` }] });
  log(c, now, "adopt", `${name} came home`);
}

function spend(c: Cosmos, user: string, what: keyof Store) {
  const s = c.stores[user];
  if (!s || s[what] < 1) throw new CosmosError(409, what === "food" ? "Your food store is empty. Share a mood, write a note or play a game to fill it" : what === "drink" ? "Your drink store is empty. Write a note or play a game to fill it" : "You're out of affection to give. Share a mood or a picture to fill it");
  s[what] -= 1;
}

type CareKind = "feed" | "water" | "cuddle" | "pat";
const CARE_BOND: Record<CareKind, number> = { feed: 3, water: 2, cuddle: 5, pat: 1 };
const CARE_XP: Record<CareKind, number> = { feed: 6, water: 5, cuddle: 8, pat: 3 };

/**
 * Care from [user]: bond grows more with a care streak (days in a row) and with variety (not the same thing
 * three times running), and less as it gets deep (each level is harder). Growth points too, and a bonus the
 * first time in a day that both of you have cared. Crossing a bond level is a moment in the pet's life.
 */
function cared(c: Cosmos, p: Pet, user: string, kind: CareKind, now: number) {
  const day = dayOf(now, c.tz);
  p.care = p.care ?? {};
  const cr = p.care[user] ?? { feed: 0, water: 0, cuddle: 0, pat: 0, day: "", streak: 0, last: [] };
  if (cr.day !== day) {
    const yesterday = new Date(Date.parse(day + "T00:00:00Z") - 86_400_000).toISOString().slice(0, 10);
    cr.streak = cr.day === yesterday ? cr.streak + 1 : 1;
    cr.day = day;
  }
  cr[kind] += 1;
  const variety = !(cr.last.length >= 2 && cr.last.every((k) => k === kind));
  cr.last = [kind, ...cr.last].slice(0, 2);
  p.care[user] = cr;

  const before = p.bond[user] ?? 15;
  const gain = CARE_BOND[kind] * (1 + .08 * Math.min(cr.streak - 1, 6)) * (variety ? 1.25 : .8) * (1 - before / 160);
  p.bond[user] = r1(clamp(before + gain));
  const lb = bondLevel(before), la = bondLevel(p.bond[user]);
  if (la > lb) moment(p, now, "bond", `${BOND_LEVELS[la][1]}`, user);

  p.xp = (p.xp ?? 0) + CARE_XP[kind];
  // both of you cared today: a together bonus
  if (p.together !== day && c.members.every((m) => p.care?.[m]?.day === day)) {
    p.together = day; p.xp += 10;
    moment(p, now, "together", "You both looked after it today");
  }
  checkGrowth(p, now);
}

/** Close friends and closer get more out of each bit of care. */
const boost = (p: Pet, user: string) => bondLevel(p.bond[user] ?? 15) >= 3 ? 1.2 : 1;

export function feed(c: Cosmos, user: string, id: string, now = Date.now()) { const p = pet(c, id); spend(c, user, "food"); p.hunger = r1(clamp(p.hunger + 22 * boost(p, user))); cared(c, p, user, "feed", now); }
export function water(c: Cosmos, user: string, id: string, now = Date.now()) { const p = pet(c, id); spend(c, user, "drink"); p.thirst = r1(clamp(p.thirst + 25 * boost(p, user))); cared(c, p, user, "water", now); }
export function cuddle(c: Cosmos, user: string, id: string, now = Date.now()) { const p = pet(c, id); spend(c, user, "love"); p.love = r1(clamp(p.love + 18 * boost(p, user))); cared(c, p, user, "cuddle", now); }

/** How long [user] waits between free pats: friends and closer wait 20 minutes instead of 30. */
export const patWait = (p: Pet, user: string) => bondLevel(p.bond[user] ?? 15) >= 2 ? 20 * 60_000 : PAT_EVERY;

/**
 * A free pat: a little love, a little bond. Returns false when it's too soon. A soulmate's pat, once a day,
 * comes back with a gift: a little of everything for their storage.
 */
export function pat(c: Cosmos, user: string, id: string, now: number): boolean {
  const p = pet(c, id);
  if (now - (p.pats[user] ?? 0) < patWait(p, user)) return false;
  p.pats[user] = now;
  p.love = r1(clamp(p.love + 2));
  cared(c, p, user, "pat", now);
  const day = dayOf(now, c.tz);
  p.gifts = p.gifts ?? {};
  if (bondLevel(p.bond[user] ?? 0) >= 5 && p.gifts[user] !== day) {
    p.gifts[user] = day;
    give(c, user, { food: 2, drink: 2, love: 2 });
    moment(p, now, "gift", "Brought you a gift: 2 of each for your storage", user);
  }
  return true;
}

export function rename(c: Cosmos, id: string, name: string) { pet(c, id).name = name; }

export function release(c: Cosmos, id: string, now: number) {
  const p = pet(c, id);
  c.pets = c.pets.filter((x) => x.id !== id);
  log(c, now, "release", `${p.name} went to live with a nice family upstate`);
}

/** Spend a comet for three new galaxies to choose from. */
export function reroll(c: Cosmos, rand: () => number) {
  if (c.comets < 1) throw new CosmosError(409, "Re-rolling the galaxy takes a comet");
  c.comets -= 1;
  c.candidates = [randSeed(rand), randSeed(rand), randSeed(rand)];
}

export function choose(c: Cosmos, seed: number, now: number) {
  if (seed !== c.seed && !c.candidates.includes(seed)) throw new CosmosError(400, "That galaxy isn't one of your choices");
  c.seed = seed; c.candidates = [];
  log(c, now, "galaxy", "A new galaxy took shape");
}

export function summary(c: Cosmos) {
  return {
    stardust: c.stardust, lifetime: c.lifetime, comets: c.comets, seed: c.seed,
    pets: c.pets.map((p) => ({ id: p.id, species: p.species, breed: p.breed, coat: p.coat ?? 0, name: p.name, born: p.born, hunger: p.hunger, thirst: p.thirst, love: p.love, bond: p.bond, mood: mood(p), growth: growth(p, Date.now()), stage: stageOf(p, Date.now()) })),
  };
}
