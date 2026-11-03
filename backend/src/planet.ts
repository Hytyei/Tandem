// The couple's planet: a small world that two suns (one per partner) keep alive. Pure and deterministic, so it
// can be unit-tested and caught up for any number of missed days. See docs/superpowers/specs/2026-11-03-planet-companion-design.md.

export type Biome = "ocean" | "forest" | "crystal" | "aurora";
export const BIOMES: Biome[] = ["ocean", "forest", "crystal", "aurora"];
export type Stage = "thriving" | "dimming" | "winter" | "drift" | "sleeping";

/** What the two of you did on one day, counted from the app's own tables. */
export interface DayActivity {
  shares: Record<string, boolean>;
  notes: number;
  pictures: number;
  rounds: number;
  trips: { id: string; title: string }[];
}

export interface Species { state: "alive" | "dormant" | "extinct"; since: string; good: number; bad: number }
export interface Moon { id: string; name: string; born: string; lost?: string }
export interface Landmark { id: string; name: string; day: string; buried?: boolean }
export interface Building { id: string; kind: string; built: string; ruined?: string }
export interface Spark { day: string; at: number; text: string }
export interface Entry { day: string; kind: string; text: string }

export interface PlanetState {
  v: 1;
  name: string;
  tz: number;               // minutes east of UTC, for day boundaries
  created: string;
  day: string;              // the last day that has been simulated (a finished day)
  members: string[];        // the two user ids, sorted
  suns: Record<string, number>;
  atmosphere: number;
  ice: number;
  life: number;
  stardust: number;
  biomes: Record<Biome, number>;
  quiet: number;            // days in a row neither of you shared
  conj: number;             // conjunction days (both shared) in total
  conjStreak: number;
  lowAtmo: number;          // days in a row with atmosphere under 25
  stage: Stage;
  needsRekindle: boolean;   // set by a dimming or worse; cleared when both of you send a spark
  species: Record<string, Species>;
  moons: Moon[];
  landmarks: Landmark[];
  buildings: Building[];
  hibernate?: { from: string; until: string };
  hibernateMonth?: string;
  warmed: Record<string, string>;     // user -> the day they last warmed the other's sun
  sparks: Record<string, Spark>;
  chronicle: Entry[];       // newest first
}

// ------------------------------------------------------------------ catalog
interface SpeciesDef { key: string; name: string; needs: string; ok: (s: PlanetState) => boolean }
const share = (s: PlanetState, b: Biome) => s.biomes[b];
const thawed = (s: PlanetState, max = 45) => s.ice < max;

export const SPECIES: SpeciesDef[] = [
  { key: "glowfish", name: "Glowfish", needs: "ocean 30 · life 30", ok: (s) => share(s, "ocean") >= 30 && s.life >= 30 && thawed(s) },
  { key: "reefbloom", name: "Reef bloom", needs: "ocean 40 · life 50", ok: (s) => share(s, "ocean") >= 40 && s.life >= 50 && thawed(s) },
  { key: "tidewhale", name: "Tide whale", needs: "ocean 50 · life 70", ok: (s) => share(s, "ocean") >= 50 && s.life >= 70 && thawed(s) },
  { key: "mossling", name: "Mossling", needs: "forest 25 · life 25", ok: (s) => share(s, "forest") >= 25 && s.life >= 25 && thawed(s) },
  { key: "lantern", name: "Lantern tree", needs: "forest 35 · life 45", ok: (s) => share(s, "forest") >= 35 && s.life >= 45 && thawed(s) },
  { key: "duskdeer", name: "Deer of dusk", needs: "forest 45 · life 65", ok: (s) => share(s, "forest") >= 45 && s.life >= 65 && thawed(s) },
  { key: "prismmoth", name: "Prism moth", needs: "crystal 25 · life 30", ok: (s) => share(s, "crystal") >= 25 && s.life >= 30 && thawed(s) },
  { key: "geodecrab", name: "Geode crab", needs: "crystal 40 · life 50", ok: (s) => share(s, "crystal") >= 40 && s.life >= 50 && thawed(s) },
  { key: "starlily", name: "Star lily", needs: "crystal 30 · forest 20 · life 55", ok: (s) => share(s, "crystal") >= 30 && share(s, "forest") >= 20 && s.life >= 55 && thawed(s) },
  { key: "aurorajelly", name: "Aurora jelly", needs: "aurora 25 · life 35", ok: (s) => share(s, "aurora") >= 25 && s.life >= 35 && thawed(s) },
  { key: "snowowl", name: "Snow owl", needs: "aurora 30 · life 40 · likes the cold", ok: (s) => share(s, "aurora") >= 30 && s.life >= 40 && s.ice >= 30 },
  { key: "skyray", name: "Sky ray", needs: "aurora 40 · ocean 20 · life 60", ok: (s) => share(s, "aurora") >= 40 && share(s, "ocean") >= 20 && s.life >= 60 && thawed(s) },
  { key: "twinfinch", name: "Twin finch", needs: "life 40 · 7 shared days in a row", ok: (s) => s.life >= 40 && s.conjStreak >= 7 && thawed(s) },
  { key: "cometfox", name: "Comet fox", needs: "life 80 · two moons", ok: (s) => s.life >= 80 && s.moons.filter((m) => !m.lost).length >= 2 && thawed(s) },
];
const SPECIES_NAME = Object.fromEntries(SPECIES.map((d) => [d.key, d.name]));

export const BUILDINGS: Record<string, { name: string; cost: number }> = {
  observatory: { name: "Observatory", cost: 40 },
  garden: { name: "Garden", cost: 50 },
  lighthouse: { name: "Lighthouse", cost: 60 },
  bridge: { name: "Bridge", cost: 80 },
  aurora: { name: "Aurora station", cost: 100 },
};
export const TERRAFORM_COST = 30;
export const WARM_GIFT = 10;
const MOON_AT = (n: number) => 7 + n * 20;          // the first moon at 7 shared days, then every 20

// ------------------------------------------------------------------ days
export const dayOf = (ms: number, tz: number) => new Date(ms + tz * 60_000).toISOString().slice(0, 10);
export const nextDay = (d: string) => new Date(Date.parse(d + "T00:00:00Z") + 86_400_000).toISOString().slice(0, 10);
export const startOfDay = (d: string, tz: number) => Date.parse(d + "T00:00:00Z") - tz * 60_000;
const clamp = (v: number, lo = 0, hi = 100) => Math.max(lo, Math.min(hi, v));
const r1 = (v: number) => Math.round(v * 10) / 10;

/** A small stable hash, so "which species goes dormant" differs per couple and day but never between two reads. */
function pick<T>(items: T[], salt: string): T | undefined {
  if (!items.length) return undefined;
  let h = 2166136261;
  for (let i = 0; i < salt.length; i++) { h ^= salt.charCodeAt(i); h = Math.imul(h, 16777619); }
  return items[(h >>> 0) % items.length];
}

function log(s: PlanetState, day: string, kind: string, text: string) {
  s.chronicle.unshift({ day, kind, text });
  if (s.chronicle.length > 80) s.chronicle.length = 80;
}

export function newPlanet(members: string[], today: string, tz: number): PlanetState {
  const [a, b] = [...members].sort();
  const s: PlanetState = {
    v: 1, name: "Our planet", tz, created: today, day: prevDay(today), members: [a, b],
    suns: { [a]: 60, [b]: 60 }, atmosphere: 60, ice: 20, life: 30, stardust: 30,
    biomes: { ocean: 25, forest: 25, crystal: 25, aurora: 25 },
    quiet: 0, conj: 0, conjStreak: 0, lowAtmo: 0, stage: "thriving", needsRekindle: false,
    species: {}, moons: [], landmarks: [], buildings: [], warmed: {}, sparks: {}, chronicle: [],
  };
  log(s, today, "born", "A new world began to turn");
  return s;
}

export const prevDay = (d: string) => new Date(Date.parse(d + "T00:00:00Z") - 86_400_000).toISOString().slice(0, 10);

const sleeping = (s: PlanetState, day: string) => !!s.hibernate && day >= s.hibernate.from && day <= s.hibernate.until;

/** Simulates one finished day. */
export function simulateDay(s: PlanetState, day: string, act: DayActivity, salt = "") {
  if (sleeping(s, day)) { s.day = day; s.stage = "sleeping"; return; }
  if (s.hibernate && day > s.hibernate.until) { log(s, day, "wake", "The planet woke from its sleep"); s.hibernate = undefined; }
  const [a, b] = s.members;
  const sa = !!act.shares[a], sb = !!act.shares[b];
  // 1. the suns
  s.suns[a] = clamp(s.suns[a] + (sa ? 12 : -9));
  s.suns[b] = clamp(s.suns[b] + (sb ? 12 : -9));
  const both = sa && sb;
  // 2. light and atmosphere
  const standing = s.buildings.filter((x) => !x.ruined).length;
  const light = (sa ? 1 : 0) + (sb ? 1 : 0) + (both ? 1 : 0);
  s.atmosphere = r1(clamp(s.atmosphere + 6 * light - 7 - .5 * standing));
  // 3. ice: cold when the suns are low, harsher when they are uneven
  const warmth = (s.suns[a] + s.suns[b]) / 2;
  const tilt = Math.abs(s.suns[a] - s.suns[b]) / 25;
  s.ice = r1(clamp(s.ice + (warmth < 40 ? (40 - warmth) / 4 : -(warmth - 40) / 6) + tilt));
  // 4. life
  if (s.atmosphere > 45 && s.ice < 45 && !s.needsRekindle) s.life = r1(clamp(s.life + Math.min(12, 2 + 3 * act.notes + 2 * act.pictures)));
  else s.life = r1(clamp(s.life - 3));
  // 5. stardust
  s.stardust += 2 * act.rounds + 5 * act.trips.length;
  for (const t of act.trips) {
    s.landmarks.push({ id: t.id, name: t.title, day });
    log(s, day, "landmark", `A landmark rose where you went: ${t.title}`);
  }
  // 6. presence
  if (sa || sb) s.quiet = 0; else s.quiet++;
  if (both) { s.conj++; s.conjStreak++; } else s.conjStreak = 0;
  if (both && s.conj === MOON_AT(s.moons.length)) {
    const n = s.moons.length + 1;
    s.moons.push({ id: `m${n}`, name: `Moon ${n}`, born: day });
    log(s, day, "moon", `A new moon caught in your orbit after ${s.conj} days you both shared`);
  }
  // 7. consequences of silence
  if (s.quiet === 3) {
    const alive = Object.keys(s.species).filter((k) => s.species[k].state === "alive").sort();
    const k = pick(alive, salt + day);
    if (k) { s.species[k] = { ...s.species[k], state: "dormant", since: day, bad: 0 }; log(s, day, "dim", `Three quiet days. The light dimmed and the ${SPECIES_NAME[k].toLowerCase()} went dormant`); }
    else log(s, day, "dim", "Three quiet days. The light dimmed");
    s.needsRekindle = true;
  }
  if (s.quiet === 7) {
    s.ice = Math.max(s.ice, 70);
    const open = s.landmarks.filter((l) => !l.buried);
    const l = pick(open, salt + day);
    if (l) l.buried = true;
    log(s, day, "winter", l ? `A week of silence. Winter came and buried ${l.name} under ice` : "A week of silence. Winter came");
    s.needsRekindle = true;
  }
  if (s.quiet >= 14 && (s.quiet - 14) % 7 === 0) {
    const moon = [...s.moons].reverse().find((m) => !m.lost);
    if (moon) { moon.lost = day; log(s, day, "lost", `${moon.name} drifted out of orbit. It won't come back`); }
    else {
      const dormant = Object.keys(s.species).filter((k) => s.species[k].state === "dormant").sort();
      const k = pick(dormant, salt + day);
      if (k) { s.species[k] = { ...s.species[k], state: "extinct", since: day }; log(s, day, "lost", `The ${SPECIES_NAME[k].toLowerCase()} died out. It is gone for good`); }
      else log(s, day, "lost", "The planet drifted further from its suns");
    }
    s.needsRekindle = true;
  }
  // thaw: one buried landmark comes back each warm day
  if (s.ice < 30) { const l = s.landmarks.find((x) => x.buried); if (l) { l.buried = false; log(s, day, "thaw", `${l.name} came out of the ice`); } }
  // 8. buildings need light
  s.lowAtmo = s.atmosphere < 25 ? s.lowAtmo + 1 : 0;
  if (s.lowAtmo >= 3) {
    const bld = [...s.buildings].reverse().find((x) => !x.ruined);
    if (bld) { bld.ruined = day; log(s, day, "ruin", `The ${BUILDINGS[bld.kind]?.name.toLowerCase() ?? "building"} fell dark and into ruin`); }
    s.lowAtmo = 0;
  }
  // 9. species
  for (const def of SPECIES) {
    const cur = s.species[def.key];
    const good = def.ok(s) && !s.needsRekindle;
    if (!cur) {
      if (!good) continue;
      s.species[def.key] = { state: "dormant", since: day, good: 1, bad: 0 };   // a seed, not yet seen
      continue;
    }
    if (cur.state === "extinct") continue;
    if (good) { cur.good++; cur.bad = 0 } else { cur.bad++; cur.good = 0 }
    if (cur.state === "dormant" && cur.good >= 2) {
      const first = !s.chronicle.some((e) => e.kind === "species" && e.text.includes(def.name));
      cur.state = "alive"; cur.since = day;
      log(s, day, "species", first ? `New life: ${def.name}` : `The ${def.name.toLowerCase()} woke up`);
    } else if (cur.state === "alive" && cur.bad >= 2) {
      cur.state = "dormant"; cur.since = day;
      log(s, day, "dormant", `The ${def.name.toLowerCase()} went dormant`);
    } else if (cur.state === "dormant" && cur.bad >= 10 && s.chronicle.some((e) => e.text.includes(def.name))) {
      cur.state = "extinct"; cur.since = day;
      log(s, day, "lost", `The ${def.name.toLowerCase()} died out. It is gone for good`);
    }
  }
  s.stage = stageOf(s);
  s.day = day;
}

export function stageOf(s: PlanetState): Stage {
  if (s.hibernate) return "sleeping";
  if (s.quiet >= 14) return "drift";
  if (s.quiet >= 7) return "winter";
  if (s.quiet >= 3 || s.needsRekindle) return s.stage === "winter" || s.stage === "drift" ? s.stage : "dimming";
  return "thriving";
}

/** Runs every finished day up to (not including) [today]. [activity] gives what happened on a day. */
export function catchUp(s: PlanetState, today: string, activity: (day: string) => DayActivity, salt = "") {
  let d = nextDay(s.day);
  let guard = 0;
  while (d < today && guard++ < 400) { simulateDay(s, d, activity(d), salt); d = nextDay(d); }
}

// ------------------------------------------------------------------ actions (they apply now, to the live state)
export class PlanetError extends Error { constructor(public status: number, msg: string) { super(msg); } }

export function terraform(s: PlanetState, biome: Biome, today: string) {
  if (!BIOMES.includes(biome)) throw new PlanetError(400, "Unknown biome");
  if (s.stardust < TERRAFORM_COST) throw new PlanetError(409, `Terraforming needs ${TERRAFORM_COST} stardust`);
  if (s.biomes[biome] >= 70) throw new PlanetError(409, "That biome can't grow any further");
  s.stardust -= TERRAFORM_COST;
  const grow = Math.min(10, 70 - s.biomes[biome]);
  const others = BIOMES.filter((b) => b !== biome);
  const rest = others.reduce((t, b) => t + s.biomes[b], 0);
  for (const b of others) s.biomes[b] = r1(s.biomes[b] - grow * (rest ? s.biomes[b] / rest : 1 / 3));
  s.biomes[biome] = r1(s.biomes[biome] + grow);
  log(s, today, "terraform", `You grew the ${biome}`);
}

export function build(s: PlanetState, kind: string, today: string, id: string) {
  const def = BUILDINGS[kind];
  if (!def) throw new PlanetError(400, "Unknown building");
  if (s.stardust < def.cost) throw new PlanetError(409, `It needs ${def.cost} stardust`);
  if (s.buildings.filter((b) => !b.ruined).length >= 8) throw new PlanetError(409, "There's no room for more buildings");
  s.stardust -= def.cost;
  s.buildings.push({ id, kind, built: today });
  log(s, today, "build", `You built ${/^[aeiou]/i.test(def.name) ? "an" : "a"} ${def.name.toLowerCase()}`);
}

export function repair(s: PlanetState, id: string, today: string) {
  const b = s.buildings.find((x) => x.id === id);
  if (!b || !b.ruined) throw new PlanetError(404, "Nothing to repair");
  const cost = Math.ceil((BUILDINGS[b.kind]?.cost ?? 40) / 2);
  if (s.stardust < cost) throw new PlanetError(409, `Repairs need ${cost} stardust`);
  s.stardust -= cost; b.ruined = undefined;
  log(s, today, "build", `You repaired the ${BUILDINGS[b.kind]?.name.toLowerCase() ?? "building"}`);
}

export function warm(s: PlanetState, me: string, sharedToday: boolean, today: string) {
  const other = s.members.find((m) => m !== me);
  if (!other) throw new PlanetError(409, "Your partner isn't here");
  if (!sharedToday) throw new PlanetError(409, "Share your mood today first, then you have light to give");
  if (s.warmed[me] === today) throw new PlanetError(409, "You already warmed their sun today");
  s.warmed[me] = today;
  s.suns[other] = clamp(s.suns[other] + WARM_GIFT);
  log(s, today, "warm", "One sun lent the other some light");
}

export function spark(s: PlanetState, me: string, text: string, today: string, at: number): boolean {
  s.sparks[me] = { day: today, at, text };
  const other = s.members.find((m) => m !== me);
  const theirs = other ? s.sparks[other] : undefined;
  if (s.needsRekindle && theirs && Math.abs(theirs.at - at) <= 24 * 3600_000) {
    s.needsRekindle = false;
    s.atmosphere = r1(clamp(s.atmosphere + 25));
    s.ice = r1(clamp(s.ice - 20));
    s.quiet = 0;
    s.stage = stageOf(s);
    s.sparks = {};
    log(s, today, "rekindle", "Two sparks met. The planet rekindled");
    return true;
  }
  return false;
}

export function hibernate(s: PlanetState, days: number, today: string) {
  if (s.hibernate) throw new PlanetError(409, "The planet is already asleep");
  if (!Number.isInteger(days) || days < 3 || days > 14) throw new PlanetError(400, "Sleep for 3 to 14 days");
  const month = today.slice(0, 7);
  if (s.hibernateMonth === month) throw new PlanetError(409, "The planet already slept this month");
  let until = today; for (let i = 1; i < days; i++) until = nextDay(until);
  s.hibernate = { from: today, until };
  s.hibernateMonth = month;
  s.stage = "sleeping";
  log(s, today, "sleep", `The planet went to sleep for ${days} days`);
}

export function wake(s: PlanetState, today: string) {
  if (!s.hibernate) throw new PlanetError(409, "The planet is awake");
  s.hibernate = undefined;
  s.stage = stageOf(s);
  log(s, today, "wake", "You woke the planet early");
}

export function rename(s: PlanetState, name: string, today: string, moonId?: string) {
  if (moonId) {
    const m = s.moons.find((x) => x.id === moonId);
    if (!m) throw new PlanetError(404, "No such moon");
    m.name = name;
  } else { s.name = name; log(s, today, "name", `The planet is called ${name} now`); }
}

/** The small summary /sync carries, for notifications and the widget. */
export function summary(s: PlanetState) {
  return {
    name: s.name, created: s.created, stage: s.stage, quiet: s.quiet, needsRekindle: s.needsRekindle,
    atmosphere: s.atmosphere, ice: s.ice, life: s.life, suns: s.suns, sparks: Object.keys(s.sparks),
    biomes: s.biomes, moons: s.moons.filter((m) => !m.lost).length, alive: Object.values(s.species).filter((x) => x.state === "alive").length,
  };
}
