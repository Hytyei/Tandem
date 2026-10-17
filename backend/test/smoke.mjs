import { DatabaseSync } from "node:sqlite";
import fs from "node:fs";
const mod = await import(new URL("../src/index.ts", import.meta.url).href);
const sqlite = new DatabaseSync(":memory:");
sqlite.exec(fs.readFileSync(new URL("../schema.sql", import.meta.url), "utf8"));
const norm = (v) => (v instanceof ArrayBuffer ? new Uint8Array(v) : v);
class Stmt {
  constructor(sql) { this.sql = sql; this.args = []; }
  bind(...a) { this.args = a.map(norm); return this; }
  async first() { return sqlite.prepare(this.sql).get(...this.args) ?? null; }
  async all() { return { results: sqlite.prepare(this.sql).all(...this.args), meta: {} }; }
  async run() { const r = sqlite.prepare(this.sql).run(...this.args); return { results: [], meta: { changes: Number(r.changes) } }; }
}
const DB = { prepare: (q) => new Stmt(q), async batch(ss) { const out = []; for (const s of ss) out.push(await s.run()); return out; } };
const env = { DB, JWT_SECRET: "test-secret" };
const call = async (method, path, token, body, raw) => {
  const headers = {}; if (token) headers.authorization = "Bearer " + token;
  let b; if (raw) b = raw; else if (body) { b = JSON.stringify(body); headers["content-type"] = "application/json"; }
  const r = await mod.default.fetch(new Request("https://x" + path, { method, headers, body: b }), env);
  const ct = r.headers.get("content-type") || "";
  return { status: r.status, body: ct.includes("json") ? await r.json() : new Uint8Array(await r.arrayBuffer()) };
};
let fails = 0;
const ok = (name, cond, extra) => { console.log((cond ? "PASS " : "FAIL ") + name + (cond ? "" : " " + JSON.stringify(extra))); if (!cond) fails++; };

let r = await call("POST", "/auth/register", null, { username: "sara", password: "secret123", displayName: "Sara" });
ok("register A", r.status === 201 && r.body.token, r); const A = r.body.token; const aId = r.body.user.id;
r = await call("POST", "/auth/register", null, { username: "SARA", password: "secret123" });
ok("duplicate username (case-insens) rejected", r.status === 409, r);
r = await call("POST", "/auth/register", null, { username: "ali", password: "secret234", displayName: "Ali" });
const B = r.body.token; const bId = r.body.user.id;
r = await call("POST", "/auth/login", null, { username: "sara", password: "wrong!!" });
ok("bad password 401", r.status === 401, r);
r = await call("POST", "/auth/login", null, { username: "sara", password: "secret123" });
ok("login ok", r.status === 200 && r.body.token, r);
r = await call("GET", "/sync", "garbage");
ok("bad token 401", r.status === 401, r);
r = await call("POST", "/moods", A, { mood: "happy" });
ok("mood before pairing -> 409", r.status === 409, r);

r = await call("POST", "/couple", A);
ok("create invite", r.status === 201 && /^[A-Z0-9]{6}$/.test(r.body.inviteCode) && !r.body.paired, r); const code = r.body.inviteCode;
r = await call("POST", "/couple/join", A, { code });
ok("cannot join own code", r.status === 400, r);
r = await call("POST", "/couple/join", B, { code: "ZZZZZZ" });
ok("unknown code 404", r.status === 404, r);
r = await call("POST", "/couple/join", B, { code: code.toLowerCase() });
ok("join (lowercase ok)", r.status === 200 && r.body.paired && r.body.partner.id === aId && r.body.anniversary, r);
r = await call("GET", "/sync", A);
ok("A sees partner after join", r.body.couple.paired && r.body.couple.partner.id === bId && r.body.couple.inviteCode === null, r.body.couple);

r = await call("POST", "/moods", A, { mood: "loved", activity: "eating", note: "pasta night" });
ok("post mood", r.status === 201 && r.body.activity === "eating", r);
r = await call("POST", "/moods", A, { mood: "nope" });
ok("invalid mood 400", r.status === 400, r);
await new Promise(s => setTimeout(s, 5));
r = await call("POST", "/moods", B, { mood: "tired", activity: "sleeping" });
r = await call("POST", "/moods", A, { mood: "calm" });
r = await call("POST", "/diary", B, { text: "Thinking of you", mood: "happy" });
ok("post diary", r.status === 201, r); const dId = r.body.id;
r = await call("DELETE", "/diary/" + dId, A);
ok("cannot delete partner's note", r.status === 404, r);
r = await call("POST", "/events", A, { title: "Anniversary", date: "2027-02-14", repeatYearly: true, emoji: "💍" });
ok("add event", r.status === 201 && r.body.repeatYearly === true, r); const eId = r.body.id;
r = await call("POST", "/events", A, { title: "x", date: "2027-13-40" });
ok("bad date 400", r.status === 400, r);
r = await call("PUT", "/events/" + eId, B, { title: "Our day", date: "2027-02-14", time: "19:30", repeatYearly: true });
ok("partner edits event", r.status === 200 && r.body.time === "19:30", r);

r = await call("GET", "/sync", B);
const s = r.body;
ok("sync: latest moods per user", s.latestMoods[aId]?.mood === "calm" && s.latestMoods[bId]?.mood === "tired", s.latestMoods);
ok("sync: stats", s.stats.moods === 3 && s.stats.diary === 1, s.stats);
ok("sync: moods newest first", s.moods[0].mood === "calm", s.moods.map(m => m.mood));
ok("sync: events + diary", s.events.length === 1 && s.diary.length === 1, { e: s.events.length, d: s.diary.length });

const jpeg = new Uint8Array(500); jpeg[0] = 0xff; jpeg[1] = 0xd8;
r = await call("PUT", "/me/avatar", A, null, jpeg);
ok("avatar upload", r.status === 200 && r.body.hasAvatar && r.body.avatarVersion === 1, r);
r = await call("GET", "/avatar/" + aId, B);
ok("partner can fetch avatar", r.status === 200 && r.body.length === 500 && r.body[0] === 0xff, { s: r.status, len: r.body.length });
r = await call("POST", "/auth/register", null, { username: "eve", password: "secret345" });
const E = r.body.token;
r = await call("GET", "/avatar/" + aId, E);
ok("stranger cannot fetch avatar", r.status === 404, r);
r = await call("PUT", "/me/avatar", A, null, new Uint8Array(10));
ok("tiny/invalid avatar rejected", r.status === 413, r);

r = await call("POST", "/couple", E); const code2 = r.body.inviteCode;
r = await call("POST", "/couple/join", B, { code: code2 });
ok("already-paired user cannot join another", r.status === 409, r);

// ---------------------------------------------------------------- update 1
r = await call("POST", "/auth/register", null, { username: "shorty", password: "1234567" });
ok("password must be 8+ chars", r.status === 400, r);

// special days
r = await call("POST", "/events", A, { title: "Ali's birthday", date: "2027-05-01", kind: "special", category: "birthday", repeatYearly: true, emoji: "🎂" });
ok("special day saved", r.status === 201 && r.body.kind === "special" && r.body.category === "birthday" && r.body.updatedAt > 0, r);
r = await call("POST", "/events", A, { title: "Odd", date: "2027-05-02", kind: "special", category: "<script>" });
ok("unknown category -> custom", r.status === 201 && r.body.category === "custom", r);
r = await call("POST", "/events", A, { title: "Plain", date: "2027-05-03" });
ok("default kind is event", r.body.kind === "event" && r.body.category === null, r);

// diary: backdated entries + pictures
r = await call("POST", "/diary", A, { text: "Our first date", entryDate: "2024-03-09" });
ok("backdated diary entry", r.status === 201 && r.body.entryDate === "2024-03-09", r); const oldId = r.body.id;
r = await call("POST", "/diary", A, { text: "future", entryDate: "2999-01-01" });
ok("future diary date rejected", r.status === 400, r);
r = await call("POST", "/diary", A, { text: "bad", entryDate: "yesterday" });
ok("bad diary date rejected", r.status === 400, r);
const pic = new Uint8Array(3000); pic[0] = 0xff; pic[1] = 0xd8; pic[2] = 0xff;
r = await call("POST", `/diary/${oldId}/images`, A, null, pic);
ok("attach picture", r.status === 201 && r.body.id, r); const picId = r.body.id;
r = await call("POST", `/diary/${oldId}/images`, B, null, pic);
ok("partner cannot attach to my note", r.status === 404, r);
r = await call("POST", `/diary/${oldId}/images`, A, null, new Uint8Array(3000).fill(7));
ok("non-JPEG rejected", r.status === 415, r);
r = await call("POST", `/diary/${oldId}/images`, A, null, new Uint8Array(500_000).fill(255));
ok("oversize picture rejected", r.status === 413, r.status);
r = await call("GET", "/diary/image/" + picId, B);
ok("partner can view picture", r.status === 200 && r.body.length === 3000, { s: r.status });
r = await call("GET", "/diary/image/" + picId, E);
ok("stranger cannot view picture", r.status === 404, r);
for (let i = 0; i < 3; i++) await call("POST", `/diary/${oldId}/images`, A, null, pic);
r = await call("POST", `/diary/${oldId}/images`, A, null, pic);
ok("max 4 pictures per note", r.status === 409, r);
r = await call("GET", "/sync", B);
const old = r.body.diary.find((d) => d.id === oldId);
ok("sync lists pictures + entry date", old && old.images.length === 4 && old.entryDate === "2024-03-09", old);
ok("diary ordered by entry date (newest day first)", r.body.diary[r.body.diary.length - 1].id === oldId, r.body.diary.map((d) => d.entryDate));
r = await call("GET", "/stats?tz=210", A);
ok("stats detail", r.status === 200 && r.body.streak >= 1 && r.body.photos === 4 && r.body.moodCounts.length > 0 && r.body.daily.length > 0, r.body);

// security: rate limiting + headers
r = await call("POST", "/auth/register", null, { username: "zolt", password: "secret456" });
for (let i = 0; i < 8; i++) await call("POST", "/auth/login", null, { username: "zolt", password: "nope-nope" });
r = await call("POST", "/auth/login", null, { username: "zolt", password: "secret456" });
ok("login locked after repeated failures", r.status === 429, r);
{
  const raw = await mod.default.fetch(new Request("https://x/health"), env);
  ok("security headers, no CORS", raw.headers.get("x-content-type-options") === "nosniff" && !raw.headers.get("access-control-allow-origin") && raw.headers.get("cache-control") === "no-store");
}
{
  const big = await mod.default.fetch(new Request("https://x/auth/login", { method: "POST", headers: { "content-length": "999999", "content-type": "application/json" }, body: "{}" }), env);
  ok("oversized JSON rejected", big.status === 413, big.status);
}

// removing a partner keeps history; a new partner starts blank; the same pair gets history back
r = await call("POST", "/couple/leave", A);
ok("A removes partner", r.status === 200, r);
r = await call("GET", "/sync", B);
ok("B is unpaired too", r.body.couple.paired === false && r.body.moods.length === 0 && r.body.diary.length === 0, r.body.couple);
r = await call("GET", "/diary/image/" + picId, B);
ok("old partner loses access to shared pictures", r.status === 409 || r.status === 404, r.status);
r = await call("POST", "/moods", B, { mood: "sad" });
ok("unpaired user cannot post", r.status === 409, r);
r = await call("POST", "/couple/join", A, { code: code2 });
ok("A joins a new partner (eve)", r.status === 200 && r.body.paired && r.body.partner.username === "eve", r);
r = await call("GET", "/sync", A);
ok("new partner starts blank", r.body.moods.length === 0 && r.body.diary.length === 0 && r.body.events.length === 0 && r.body.stats.moods === 0, r.body.stats);
r = await call("POST", "/couple/leave", E);
ok("eve removes A", r.status === 200, r);
r = await call("POST", "/couple", B); const code3 = r.body.inviteCode;
r = await call("POST", "/couple/join", A, { code: code3 });
ok("same pair reconnects", r.status === 200 && r.body.paired && r.body.partner.id === bId, r);
r = await call("GET", "/sync", A);
ok("history restored for the same pair", r.body.moods.length === 3 && r.body.diary.length === 2 && r.body.events.length === 4, { m: r.body.moods.length, d: r.body.diary.length, e: r.body.events.length });
r = await call("GET", "/diary/image/" + picId, B);
ok("pictures restored too", r.status === 200, r.status);

// deleting an account
r = await call("DELETE", "/me", E, { password: "wrong-pass" });
ok("delete account needs the right password", r.status === 401, r);
r = await call("DELETE", "/me", B, { password: "secret234" });
ok("account deleted", r.status === 200, r);
r = await call("GET", "/sync", B);
ok("deleted account's token stops working", r.status === 401, r);
r = await call("GET", "/sync", A);
ok("partner is unpaired after deletion, keeps own data", r.body.couple.paired === false, r.body.couple);
r = await call("POST", "/auth/login", null, { username: "ali", password: "secret234" });
ok("deleted user cannot log in", r.status === 401, r);

// ---------------------------------------------------------------- update 2
r = await call("POST", "/auth/register", null, { username: "mina", password: "secret345", displayName: "Mina" }); const F = r.body.token;
r = await call("POST", "/auth/register", null, { username: "omid", password: "secret456", displayName: "Omid" }); const G = r.body.token;
r = await call("POST", "/couple", F); const codeFG = r.body.inviteCode;
r = await call("POST", "/couple/join", G, { code: codeFG });
ok("pair F+G", r.status === 200 && r.body.paired, r);
const todayD = new Date().toISOString().slice(0, 10);
const yr = parseInt(todayD.slice(0, 4), 10);
const lastYearToday = `${yr - 1}${todayD.slice(4)}`;
r = await call("POST", "/diary", F, { text: "Movie night", mood: "happy", rating: 5, dateType: "movie", entryDate: lastYearToday });
ok("diary with rating + date type", r.status === 201 && r.body.rating === 5 && r.body.dateType === "movie", r); const mId = r.body.id;
r = await call("POST", "/diary", F, { text: "bad rating", rating: 9 });
ok("rating out of range rejected", r.status === 400, r);
r = await call("POST", "/diary", F, { text: "unknown type", dateType: "nonsense" });
ok("unknown date type -> null", r.status === 201 && r.body.dateType === null && r.body.rating === null, r);
r = await call("PUT", "/diary/" + mId, G, { text: "hijack" });
ok("partner cannot edit my note", r.status === 404, r);
r = await call("PUT", "/diary/" + mId, F, { text: "Movie night (edited)", rating: 4, dateType: "datenight", mood: null });
ok("edit own note", r.status === 200 && r.body.text.endsWith("(edited)") && r.body.rating === 4 && r.body.dateType === "datenight" && r.body.mood === null && r.body.updatedAt > 0 && r.body.entryDate === lastYearToday, r.body);
r = await call("PUT", "/diary/" + mId, F, { rating: null });
ok("clear rating keeps text", r.status === 200 && r.body.rating === null && r.body.text.endsWith("(edited)"), r.body);
r = await call("PUT", "/diary/" + mId, F, { rating: 5, entryDate: "2999-01-01" });
ok("edit cannot move into the future", r.status === 400, r);
r = await call("PUT", "/diary/" + mId, F, { rating: 5 });
ok("re-rate", r.status === 200 && r.body.rating === 5, r.body);
r = await call("POST", "/diary/" + mId + "/images", F, null, new Uint8Array([0xff, 0xd8, 0xff, ...new Array(400).fill(7)]));
const pic2 = r.body.id;
r = await call("DELETE", "/diary/image/" + pic2, G);
ok("partner cannot remove my picture", r.status === 404, r);
r = await call("DELETE", "/diary/image/" + pic2, F);
ok("remove own picture", r.status === 200, r);
r = await call("GET", "/diary/image/" + pic2, F);
ok("removed picture is gone", r.status === 404, r.status);
r = await call("POST", "/events", G, { title: "Game session", date: lastYearToday, emoji: "🎮", dateType: "gaming" });
ok("event with date type", r.status === 201 && r.body.dateType === "gaming", r);
r = await call("POST", "/events", G, { title: "Future dinner", date: `${yr + 1}-01-01`, dateType: "dinner" });
r = await call("GET", "/sync", F);
ok("sync carries rating/date type", r.body.diary.some((d) => d.id === mId && d.rating === 5 && d.dateType === "datenight"), r.body.diary);
r = await call("GET", "/stats?tz=0", F);
const dtc = Object.fromEntries(r.body.dateTypeCounts.map((x) => [x.dateType, x.n]));
ok("stats count date types (past only)", dtc.datenight === 1 && dtc.gaming === 1 && dtc.dinner === undefined, r.body.dateTypeCounts);
ok("stats totals", r.body.totals.diary === 2 && r.body.totals.events === 2 && r.body.totals.fiveStar === 1 && r.body.totals.rated === 1, r.body.totals);
r = await call("GET", "/memories?date=" + todayD, F);
ok("memories: on this day last year", r.status === 200 && r.body.onThisDay.length === 2 && r.body.onThisDay.every((m) => m.yearsAgo === 1) && r.body.onThisDay.some((m) => m.kind === "diary") && r.body.onThisDay.some((m) => m.kind === "event"), r.body.onThisDay);
ok("memories: best ever includes the 5-star note", r.body.bestEver.length === 1 && r.body.bestEver[0].diary.id === mId, r.body.bestEver);
r = await call("GET", "/memories?date=" + yr + "-02-10", F);
ok("memories: other day has none, never throws", r.status === 200 && r.body.onThisDay.length === 0, r.body.onThisDay);
r = await call("GET", "/memories?date=nope", F);
ok("memories: bad date 400", r.status === 400, r);
const lm = new Date(); lm.setUTCDate(1); lm.setUTCMonth(lm.getUTCMonth() - 1);
const lastMonthDay = lm.toISOString().slice(0, 10);
r = await call("POST", "/diary", G, { text: "Last month good", rating: 3, entryDate: lastMonthDay });
r = await call("GET", "/memories?date=" + todayD, F);
ok("memories: last month lists it", r.body.lastMonth.some((m) => m.kind === "diary" && m.diary.text === "Last month good"), r.body.lastMonth);
r = await call("GET", "/memories?date=" + todayD, null);
ok("memories need auth", r.status === 401, r);

// ---------------------------------------------------------------- update 3
r = await call("POST", "/auth/register", null, { username: "nora", password: "secret567", displayName: "Nora" }); const H = r.body.token;
r = await call("POST", "/auth/register", null, { username: "paul", password: "secret678", displayName: "Paul" }); const I = r.body.token;
r = await call("POST", "/auth/register", null, { username: "quin", password: "secret789", displayName: "Quin" }); const J = r.body.token;
r = await call("POST", "/couple", H); const codeHI = r.body.inviteCode;
r = await call("POST", "/couple/join", I, { code: codeHI });
ok("pair H+I", r.status === 200 && r.body.paired, r);

// ---- personal page
r = await call("PUT", "/me/profile", H, {
  fullName: "Nora Test", birthday: "1996-04-12", colors: ["pink", "teal", "nonsense", "pink", "blue", "red"], hobbies: ["Reading", " ", "Climbing", "x".repeat(40)],
  sport: "Tennis", food: "Ramen", music: "Lo-fi", loveLanguage: "time", watching: "Severance", playing: "", listening: "", reading: "Dune",
  meters: { spicy: 140, hunger: 55.4, sleepy: -3, bogus: 9 }, evil: "<script>",
});
ok("profile saved + sanitised", r.status === 200 && r.body.profile.fullName === "Nora Test" && r.body.profile.colors.join() === "pink,teal,blue" &&
  r.body.profile.hobbies.join() === "Reading,Climbing" && r.body.profile.meters.spicy === 100 && r.body.profile.meters.hunger === 55 && r.body.profile.meters.sleepy === 0 &&
  r.body.profile.meters.bogus === undefined && r.body.profile.evil === undefined && r.body.profile.nowAt > 0 && r.body.profile.metersAt > 0, r.body);
const nowAt1 = r.body.profile.nowAt;
r = await call("PUT", "/me/profile", H, { fullName: "Nora Test", watching: "Severance", reading: "Dune", meters: { spicy: 100, hunger: 55 } });
ok("unchanged 'now' keeps its timestamp", r.status === 200 && r.body.profile.nowAt === nowAt1, r.body.profile);
r = await call("PUT", "/me/profile", H, { birthday: "2999-01-01" });
ok("future birthday rejected", r.status === 400, r);
r = await call("PUT", "/me/profile", H, { fullName: "n".repeat(100) });
ok("overlong name rejected", r.status === 400, r);
r = await call("GET", "/sync", I);
ok("partner sees the page via sync", r.body.couple.partner.profile.fullName === "Nora Test" && r.body.couple.partner.profile.watching === "Severance", r.body.couple.partner);
r = await call("GET", "/sync", J);
ok("stranger gets no partner", !r.body.couple.partner, r.body.couple);

// ---- places + reviews
r = await call("POST", "/places", H, { name: "Blue Door Cafe", category: "restaurant", lat: 35.6892, lon: 51.389, note: "first coffee", visitDate: "2025-05-01" });
ok("add place", r.status === 201 && r.body.visits === 1 && r.body.firstVisited === "2025-05-01" && r.body.avg === null && r.body.reviews.length === 0, r.body); const p1 = r.body.id;
r = await call("POST", "/places", I, { name: "Cinema Palace", category: "entertainment", lat: 35.7, lon: 51.4 });
const p2 = r.body.id;
r = await call("POST", "/places", I, { name: "Park", category: "weird-category", lat: 35.71, lon: 51.41 });
ok("unknown place category -> other", r.status === 201 && r.body.category === "other", r.body); const p3 = r.body.id;
r = await call("POST", "/places", H, { name: "Nowhere", category: "other", lat: 123, lon: 0 });
ok("bad coordinates rejected", r.status === 400, r);
r = await call("POST", "/places", H, { name: "", category: "other", lat: 1, lon: 1 });
ok("empty name rejected", r.status === 400, r);
r = await call("GET", "/places", J);
ok("unpaired user cannot list places", r.status === 409, r);
r = await call("PUT", "/places/" + p1 + "/review", H, { rating: 5, comment: "perfect" });
ok("my review", r.status === 200 && r.body.avg === 5 && r.body.count === 1, r.body);
r = await call("PUT", "/places/" + p1 + "/review", I, { rating: 3 });
ok("partner review -> average", r.body.avg === 4 && r.body.count === 2 && r.body.reviews.length === 2, r.body);
r = await call("PUT", "/places/" + p1 + "/review", I, { rating: 2, comment: "meh" });
ok("review updated in place (no duplicates)", r.body.count === 2 && r.body.avg === 3.5, r.body);
r = await call("PUT", "/places/" + p1 + "/review", I, { rating: 9 });
ok("review out of range rejected", r.status === 400, r);
r = await call("PUT", "/places/" + p2 + "/review", H, { rating: 5 });
r = await call("PUT", "/places/" + p1 + "/review", J, { rating: 1 });
ok("outsider cannot review", r.status === 409 || r.status === 404, r.status);
r = await call("POST", "/places/" + p1 + "/visit", I);
ok("we went again", r.status === 200 && r.body.visits === 2 && r.body.lastVisited >= "2025-05-01", r.body);
r = await call("PUT", "/places/" + p1, H, { name: "Blue Door Café", note: "our spot" });
ok("edit place keeps rest", r.status === 200 && r.body.name === "Blue Door Café" && r.body.lat === 35.6892 && r.body.visits === 2, r.body);
r = await call("GET", "/places", I);
ok("list has reviews and 3 places", r.status === 200 && r.body.places.length === 3 && r.body.places.find((x) => x.id === p1).reviews.length === 2, r.body.places.length);
r = await call("PUT", "/places/" + p1 + "/review", I, { rating: null });
ok("clear my review", r.body.count === 1 && r.body.avg === 5, r.body);

// ---- diary + place
r = await call("POST", "/diary", H, { text: "Coffee here", placeId: p1 });
ok("diary linked to place", r.status === 201 && r.body.placeId === p1 && r.body.placeName === "Blue Door Café", r.body); const dp = r.body.id;
r = await call("POST", "/diary", H, { text: "bad place", placeId: "nope" });
ok("diary with unknown place rejected", r.status === 400, r);
r = await call("PUT", "/diary/" + dp, H, { placeId: p2 });
ok("move note to another place", r.status === 200 && r.body.placeName === "Cinema Palace" && r.body.text === "Coffee here", r.body);
r = await call("PUT", "/diary/" + dp, H, { placeId: null });
ok("clear place", r.status === 200 && r.body.placeId === null, r.body);
r = await call("PUT", "/diary/" + dp, H, { placeId: p1 });
r = await call("GET", "/sync", I);
ok("sync carries place name", r.body.diary.some((d) => d.id === dp && d.placeName === "Blue Door Café"), r.body.diary);
r = await call("GET", "/stats?tz=0", H);
ok("stats totals include places/reviews", r.body.totals.places === 3 && r.body.totals.placeReviews === 2, r.body.totals);
r = await call("DELETE", "/places/" + p1, I);
ok("delete place", r.status === 200, r);
r = await call("GET", "/sync", H);
ok("deleting a place unlinks notes", r.body.diary.some((d) => d.id === dp && d.placeId === null && d.placeName === null), r.body.diary);
r = await call("GET", "/places", H);
ok("place gone", r.body.places.length === 2 && !r.body.places.some((x) => x.id === p1), r.body.places.length);

// ---- new activities
r = await call("POST", "/moods", H, { mood: "calm", activity: "coding" });
ok("activity coding accepted", r.status === 201 && r.body.activity === "coding", r); const moodNora = r.body.id;
for (const a of ["working", "studying", "partying"]) { r = await call("POST", "/moods", I, { mood: "happy", activity: a }); ok("activity " + a, r.status === 201, r); }
r = await call("POST", "/moods", I, { mood: "happy", activity: "knitting" });
ok("unknown activity rejected", r.status === 400, r);
const moodPaul = (await call("GET", "/sync", I)).body.latestMoods;

// ---- reactions
const noraId = (await call("GET", "/me", H)).body.user.id, paulId = (await call("GET", "/me", I)).body.user.id;
r = await call("PUT", "/reactions", I, { type: "mood", id: moodNora, emoji: "🔥" });
ok("react to partner's mood", r.status === 200 && r.body.reactions.length === 1 && r.body.reactions[0].emoji === "🔥" && r.body.reactions[0].userId === paulId, r);
r = await call("PUT", "/reactions", I, { type: "mood", id: moodNora, emoji: "🥹" });
ok("changing reaction replaces it", r.body.reactions.length === 1 && r.body.reactions[0].emoji === "🥹", r.body);
r = await call("PUT", "/reactions", H, { type: "mood", id: moodNora, emoji: "😂" });
ok("can't react to own post", r.status === 403, r);
r = await call("PUT", "/reactions", I, { type: "mood", id: moodNora, emoji: "👍" });
ok("emoji outside the list rejected", r.status === 400, r);
r = await call("PUT", "/reactions", I, { type: "other", id: moodNora, emoji: "🔥" });
ok("bad type rejected", r.status === 400, r);
r = await call("PUT", "/reactions", A, { type: "mood", id: moodNora, emoji: "🔥" });
ok("stranger can't react (not paired)", r.status === 409, r);
r = await call("PUT", "/reactions", I, { type: "diary", id: "missing", emoji: "🔥" });
ok("unknown post 404", r.status === 404, r);
r = await call("GET", "/sync", H);
ok("sync carries mood reactions", r.body.latestMoods[noraId].reactions[0].emoji === "🥹" && r.body.moods.find((m) => m.id === moodNora).reactions.length === 1, r.body.latestMoods);
r = await call("POST", "/diary", H, { text: "reactable" }); const reDiary = r.body.id;
r = await call("PUT", "/reactions", I, { type: "diary", id: reDiary, emoji: "🥳" });
ok("react to diary", r.status === 200 && r.body.reactions[0].emoji === "🥳", r);
r = await call("GET", "/sync", H);
ok("sync carries diary reactions", r.body.diary.find((d) => d.id === reDiary).reactions[0].emoji === "🥳", r.body.diary[0]);
r = await call("PUT", "/reactions", I, { type: "diary", id: reDiary, emoji: null });
ok("remove reaction", r.status === 200 && r.body.reactions.length === 0, r);
r = await call("PUT", "/reactions", I, { type: "diary", id: reDiary, emoji: "📿" });
r = await call("DELETE", "/diary/" + reDiary, H);
ok("deleting a note removes its reactions", sqlite.prepare("SELECT COUNT(*) AS n FROM reactions WHERE target_id = ?").get(reDiary).n === 0, r);

// ---- minigames
r = await call("GET", "/games", A);
ok("games need a partner", r.status === 409, r);
r = await call("POST", "/games/matches", H, { game: "chess", rounds: 3 });
ok("unknown game rejected", r.status === 400, r);
r = await call("POST", "/games/matches", H, { game: "heart", rounds: 2 });
ok("bad round count rejected", r.status === 400, r);
r = await call("POST", "/games/matches", H, { game: "heart", rounds: 3 });
ok("create match", r.status === 201 && r.body.status === "active" && r.body.rounds === 3 && r.body.createdBy === noraId && Number.isInteger(r.body.seed), r); const m1 = r.body.id;
r = await call("PUT", `/games/matches/${m1}/score`, I, { round: 1, score: 10 });
ok("partner can't go before the starter", r.status === 409, r);
r = await call("PUT", `/games/matches/${m1}/score`, H, { round: 2, score: 10 });
ok("can't skip a round", r.status === 409, r);
r = await call("PUT", `/games/matches/${m1}/score`, H, { round: 1, score: 9999 });
ok("impossible score rejected", r.status === 400, r);
r = await call("PUT", `/games/matches/${m1}/score`, H, { round: 1, score: 20 });
ok("starter plays round 1", r.status === 200 && r.body.scores.length === 1, r);
r = await call("PUT", `/games/matches/${m1}/score`, H, { round: 1, score: 25 });
ok("can't replay a round", r.status === 409, r);
r = await call("PUT", `/games/matches/${m1}/score`, I, { round: 1, score: 15 });
ok("partner plays round 1", r.status === 200 && r.body.scores.length === 2 && r.body.status === "active", r);
r = await call("PUT", `/games/matches/${m1}/score`, H, { round: 2, score: 30 });
ok("round 2: starter can't go first", r.status === 409, r);
r = await call("PUT", `/games/matches/${m1}/score`, I, { round: 2, score: 12 });
ok("round 2 partner goes first", r.status === 200, r);
r = await call("PUT", `/games/matches/${m1}/score`, H, { round: 2, score: 5 });
ok("round 2 starter second", r.status === 200 && r.body.status === "active", r);
// goal created before the match finishes: first to 2 match wins
r = await call("POST", "/games/goals", I, { title: "Loser buys dinner 🍽️", metric: "wins", target: 2 });
ok("create goal", r.status === 201 && r.body.status === "active" && r.body.target === 2, r); const g1 = r.body.id;
r = await call("POST", "/games/goals", I, { title: "x", metric: "lives", target: 2 });
ok("bad goal metric rejected", r.status === 400, r);
r = await call("POST", "/games/goals", I, { title: "x", metric: "points", target: 5 });
ok("points target must be >= 100", r.status === 400, r);
r = await call("PUT", `/games/matches/${m1}/score`, H, { round: 3, score: 40 });
r = await call("PUT", `/games/matches/${m1}/score`, I, { round: 3, score: 41 });
ok("match finishes; 2 rounds each -> tie on rounds is broken by total", r.body.status === "done" && r.body.winnerId === paulId, r.body);
// totals: Nora 20+5+40=65 ; Paul 15+12+41=68 ; rounds: Nora won R1 and R3? R1 20>15 Nora, R2 12>5 Paul, R3 41>40 Paul => Paul wins 2-1
r = await call("GET", "/games", H);
ok("summary: goal still active (1 win of 2)", r.body.goals[0].status === "active" && r.body.goals[0].progress[paulId] === 1, r.body.goals);
ok("summary: stats", r.body.stats.done === 1 && r.body.stats.players.find((p) => p.userId === paulId).wins === 1 && r.body.stats.tandemScore === 133, r.body.stats);
ok("summary: bests + highs", r.body.stats.bests.find((b) => b.game === "heart" && b.userId === paulId).best === 41 && r.body.stats.highs[0].score === 41, r.body.stats.highs);
// second match: Paul starts? Nora creates again, Paul wins both -> goal reached
r = await call("POST", "/games/matches", I, { game: "math", rounds: 1 }); const m2 = r.body.id;
r = await call("PUT", `/games/matches/${m2}/score`, I, { round: 1, score: 300 });
r = await call("PUT", `/games/matches/${m2}/score`, H, { round: 1, score: 100 });
ok("single-round match decided", r.body.status === "done" && r.body.winnerId === paulId, r.body);
r = await call("GET", "/games", H);
const goal = r.body.goals.find((g) => g.id === g1);
ok("goal won by Paul", goal.status === "won" && goal.winnerId === paulId && goal.progress[paulId] === 2, goal);
ok("streaks tracked", r.body.stats.players.find((p) => p.userId === paulId).streak === 2 && r.body.stats.players.find((p) => p.userId === noraId).streak === 0, r.body.stats.players);
r = await call("PUT", `/games/goals/${g1}/settle`, H);
ok("settle won goal", r.status === 200 && r.body.status === "settled", r);
r = await call("PUT", `/games/goals/${g1}/settle`, H, {});
ok("can't settle twice", r.status === 409, r);
// draw: equal everything
r = await call("POST", "/games/matches", H, { game: "draw", rounds: 1 }); const m3 = r.body.id;
r = await call("PUT", `/games/matches/${m3}/score`, H, { round: 1, score: 500 });
r = await call("PUT", `/games/matches/${m3}/score`, I, { round: 1, score: 500 });
ok("tie = draw (no winner)", r.body.status === "done" && r.body.winnerId === null, r.body);
r = await call("GET", "/games", I);
ok("draw counted, streak reset", r.body.stats.draws === 1 && r.body.stats.done === 3 && r.body.stats.players.find((p) => p.userId === paulId).streak === 0, r.body.stats);
// cancel + limits
r = await call("POST", "/games/matches", H, { game: "memory", rounds: 3 }); const m4 = r.body.id;
r = await call("DELETE", `/games/matches/${m4}`, I);
ok("cancel active match", r.status === 200, r);
r = await call("DELETE", `/games/matches/${m1}`, I);
ok("finished matches can't be deleted", r.status === 409, r);
r = await call("POST", "/games/goals", H, { title: "Points race", metric: "points", target: 100 }); const g2 = r.body.id;
r = await call("POST", "/games/matches", H, { game: "heart", rounds: 1 }); const m5 = r.body.id;
r = await call("PUT", `/games/matches/${m5}/score`, H, { round: 1, score: 110 });
r = await call("PUT", `/games/matches/${m5}/score`, I, { round: 1, score: 30 });
r = await call("GET", "/games", H);
ok("points goal won by whoever reaches first", r.body.goals.find((g) => g.id === g2).status === "won" && r.body.goals.find((g) => g.id === g2).winnerId === noraId, r.body.goals);
r = await call("DELETE", `/games/goals/${g2}`, I);
ok("delete goal", r.status === 200, r);
r = await call("DELETE", `/games/goals/${g2}`, I);
ok("delete goal twice 404", r.status === 404, r);


// ================= update 4 =================
// ---- visits on create + place pictures
r = await call("POST", "/places", H, { name: "Old Bridge", category: "sights", lat: 35.1, lon: 51.1, visits: 7 }); const p4 = r.body.id;
ok("place can start with N earlier visits", r.status === 201 && r.body.visits === 7 && Array.isArray(r.body.images) && r.body.images.length === 0, r);
r = await call("POST", "/places", H, { name: "Bad visits", category: "other", lat: 1, lon: 1, visits: 0 });
ok("visits 0 rejected", r.status === 400, r);
r = await call("POST", "/places", H, { name: "Bad visits", category: "other", lat: 1, lon: 1, visits: 1000 });
ok("visits 1000 rejected", r.status === 400, r);
r = await call("POST", "/places", H, { name: "Bad visits", category: "other", lat: 1, lon: 1, visits: 2.5 });
ok("fractional visits rejected", r.status === 400, r);
const big = new Uint8Array(500); big[0] = 0xff; big[1] = 0xd8; big[2] = 0xff;
r = await call("POST", `/places/${p4}/images`, H, null, big);
ok("add place picture", r.status === 201 && r.body.id, r); const pi1 = r.body.id;
r = await call("POST", `/places/${p4}/images`, I, null, big);
ok("partner can add a picture to the shared place", r.status === 201, r); const pi2 = r.body.id;
r = await call("POST", `/places/${p4}/images`, J, null, big);
ok("outsider cannot add place pictures", r.status === 409 || r.status === 404, r);
r = await call("POST", `/places/${p4}/images`, H, null, new Uint8Array(500));
ok("non-JPEG place picture rejected", r.status === 415, r);
r = await call("POST", `/places/${p4}/images`, H, null, new Uint8Array(600000).fill(0xff));
ok("huge place picture rejected", r.status === 413, r.status);
r = await call("GET", "/places", I);
ok("place lists its pictures", r.body.places.find((x) => x.id === p4).images.length === 2, r.body.places.find((x) => x.id === p4));
r = await call("GET", `/places/images/${pi1}`, I);
ok("partner fetches picture bytes", r.status === 200 && r.body.length === 500, r.status);
r = await call("GET", `/places/images/${pi1}`, J);
ok("outsider cannot fetch place picture", r.status === 409 || r.status === 404, r.status);
for (let k = 0; k < 4; k++) await call("POST", `/places/${p4}/images`, H, null, big);
r = await call("POST", `/places/${p4}/images`, H, null, big);
ok("max 6 pictures per place", r.status === 409, r);
r = await call("DELETE", `/places/images/${pi2}`, H);
ok("either partner can remove a place picture", r.status === 200, r);
r = await call("DELETE", `/places/images/${pi2}`, H);
ok("removing twice 404", r.status === 404, r);

// ---- saved addresses
r = await call("POST", "/addresses", H, { label: "Nora's home", kind: "home", address: "12 Rose St", lat: 35.7, lon: 51.3 }); const ad1 = r.body.id;
ok("save address", r.status === 201 && r.body.userId === noraId && r.body.kind === "home", r);
r = await call("POST", "/addresses", I, { label: "Paul's place", kind: "weird", lat: 35.71, lon: 51.31 });
ok("unknown kind falls back to home", r.status === 201 && r.body.kind === "home", r); const ad2 = r.body.id;
r = await call("POST", "/addresses", I, { label: "x", lat: 999, lon: 1 });
ok("bad coordinates rejected", r.status === 400, r);
r = await call("GET", "/addresses", I);
ok("partner sees both addresses", r.body.addresses.length === 2 && r.body.addresses.some((a) => a.id === ad1 && a.userId === noraId), r.body);
r = await call("GET", "/addresses", J);
ok("unpaired user can't list addresses", r.status === 409, r);
r = await call("PUT", `/addresses/${ad1}`, I, { label: "Hacked", lat: 1, lon: 1 });
ok("can't edit partner's address", r.status === 404, r);
r = await call("PUT", `/addresses/${ad1}`, H, { label: "Our flat", kind: "home", address: "14 Rose St", lat: 35.7, lon: 51.3 });
ok("edit own address", r.status === 200 && r.body.label === "Our flat", r);
r = await call("DELETE", `/addresses/${ad2}`, H);
ok("can't delete partner's address", r.status === 404, r);
r = await call("DELETE", `/addresses/${ad2}`, I);
ok("delete own address", r.status === 200, r);

// ---- shared notes + checklists
r = await call("POST", "/notes", H, { title: "Trip packing", kind: "list", items: ["Passports", "  Chargers ", "", "Snacks"], date: "2027-03-01" }); const n1 = r.body.id;
ok("create checklist", r.status === 201 && r.body.kind === "list" && r.body.items.length === 3 && r.body.items[1].text === "Chargers" && r.body.date === "2027-03-01", r);
r = await call("POST", "/notes", I, { title: "Gift ideas", body: "something handmade", kind: "note", pinned: true }); const n2 = r.body.id;
ok("create note", r.status === 201 && r.body.pinned === true && r.body.items.length === 0, r);
r = await call("POST", "/notes", I, { title: "", kind: "note" });
ok("empty title rejected", r.status === 400, r);
r = await call("POST", "/notes", I, { title: "x", date: "2027-99-99" });
ok("bad note date rejected", r.status === 400, r);
r = await call("GET", "/notes", I);
ok("partner sees notes, pinned first", r.body.notes.length === 2 && r.body.notes[0].id === n2, r.body.notes.map((n) => n.title));
const itemId = r.body.notes.find((n) => n.id === n1).items[0].id;
r = await call("PUT", `/notes/items/${itemId}`, I, { done: true });
ok("partner ticks an item", r.status === 200 && r.body.items[0].done === true && r.body.items[0].doneBy === paulId, r);
r = await call("PUT", `/notes/items/${itemId}`, H, { done: true });
ok("ticking again keeps who did it", r.body.items[0].doneBy === paulId, r.body.items[0]);
r = await call("PUT", `/notes/items/${itemId}`, H, { done: false });
ok("untick", r.body.items[0].done === false && r.body.items[0].doneBy === null, r.body.items[0]);
r = await call("POST", `/notes/${n1}/items`, I, { text: "Sunscreen" });
ok("add item", r.status === 201 && r.body.items.length === 4 && r.body.items[3].text === "Sunscreen", r);
const lastItem = r.body.items[3].id;
r = await call("DELETE", `/notes/items/${lastItem}`, H);
ok("delete item", r.status === 200 && r.body.items.length === 3, r);
r = await call("PUT", `/notes/${n1}`, H, { title: "Packing (final)" });
ok("rename note keeps items", r.status === 200 && r.body.title === "Packing (final)" && r.body.items.length === 3, r);
r = await call("PUT", `/notes/${n1}`, J, { title: "x" });
ok("outsider can't edit notes", r.status === 409 || r.status === 404, r);
r = await call("DELETE", `/notes/${n2}`, H);
ok("either partner deletes a note", r.status === 200, r);
r = await call("GET", "/notes", H);
ok("note gone", r.body.notes.length === 1, r.body.notes);

// ---- trips (live tracking)
r = await call("POST", "/trips", J, { title: "nope" });
ok("unpaired can't start a trip", r.status === 409, r);
r = await call("POST", "/trips", H, { title: "Sunset walk" }); const tr1 = r.body.id; const t0 = r.body.startedAt;
ok("start trip", r.status === 201 && r.body.status === "active" && r.body.members.length === 1 && r.body.startedBy === noraId, r);
r = await call("POST", "/trips", I, {});
ok("only one active trip", r.status === 409, r);
r = await call("GET", "/sync", I);
ok("sync carries the active trip", r.body.trip && r.body.trip.id === tr1 && r.body.trip.members.length === 1, r.body.trip);
r = await call("POST", `/trips/${tr1}/points`, I, { points: [{ t: t0 + 1000, lat: 35.7, lon: 51.4 }] });
ok("must join before sending points", r.status === 403, r);
r = await call("POST", `/trips/${tr1}/join`, I);
ok("partner joins", r.status === 200 && r.body.members.length === 2, r);
const deg = 1 / 111195; // ~1 m of latitude
r = await call("POST", `/trips/${tr1}/points`, H, { points: [
  { t: t0 + 1000, lat: 35.7, lon: 51.4 },
  { t: t0 + 6000, lat: 35.7 + 100 * deg, lon: 51.4 },          // +100 m
  { t: t0 + 11000, lat: 35.7 + 100.5 * deg, lon: 51.4 },       // jitter: ignored
  { t: t0 + 16000, lat: 35.7 + 300 * deg, lon: 51.4 },         // +200 m
  { t: t0 + 17000, lat: 36.7, lon: 51.4 },                     // teleport: ignored
  { t: t0 + 99999999999, lat: 35.7, lon: 51.4 },               // far future: ignored
] });
const hm = r.body.members.find((m) => m.userId === noraId);
ok("distance adds up and ignores jitter/teleports", r.status === 200 && Math.abs(hm.distanceM - 300) <= 3 && hm.points === 3 && Math.abs(r.body.distanceM - 300) <= 3, r.body);
r = await call("POST", `/trips/${tr1}/points`, I, { points: [{ t: t0 + 2000, lat: 35.7, lon: 51.4 }, { t: t0 + 12000, lat: 35.7 + 250 * deg, lon: 51.4 }] });
ok("partner's own path is tracked separately; trip distance is the longer one", r.status === 200 && Math.abs(r.body.distanceM - 300) <= 3 && Math.abs(r.body.members.find((m) => m.userId === paulId).distanceM - 250) <= 3, r.body);
r = await call("POST", `/trips/${tr1}/points`, H, { points: [] });
ok("empty batch rejected", r.status === 400, r);
r = await call("POST", `/trips/${tr1}/points`, H, { points: [{ t: t0 + 2000, lat: 999, lon: 0 }] });
ok("all-invalid batch rejected", r.status === 400, r);
r = await call("GET", `/trips/${tr1}`, I);
ok("trip detail has a path per person", r.status === 200 && r.body.paths.length === 2 && r.body.paths.find((p) => p.userId === noraId).points.length === 3, r.body.paths);
r = await call("POST", `/trips/${tr1}/end`, I);
ok("either partner ends the trip", r.status === 200 && r.body.status === "done" && r.body.endedAt >= t0, r);
r = await call("POST", `/trips/${tr1}/points`, H, { points: [{ t: t0 + 20000, lat: 35.7, lon: 51.4 }] });
ok("no points after the end", r.status === 409, r);
r = await call("GET", "/trips", H);
ok("trip list + totals", r.status === 200 && r.body.active === null && r.body.trips.length === 1 && r.body.totals.trips === 1 && Math.abs(r.body.totals.distanceM - 300) <= 3 && Math.abs(r.body.totals.longestM - 300) <= 3, r.body);
r = await call("PUT", `/trips/${tr1}`, I, { title: "Sunset walk 🌇" });
ok("rename trip", r.status === 200 && r.body.title === "Sunset walk 🌇", r);
r = await call("GET", "/sync", H);
ok("sync has no trip when none is active", r.body.trip === null, r.body.trip);
r = await call("POST", "/trips", I, {}); const tr2 = r.body.id;
ok("a new trip can start after one ends", r.status === 201, r);
r = await call("POST", `/trips/${tr2}/leave`, I);
ok("leave stops your recording", r.status === 200 && r.body.members[0].leftAt > 0, r);
r = await call("POST", `/trips/${tr2}/points`, I, { points: [{ t: Date.now(), lat: 35.7, lon: 51.4 }] });
ok("left members can't send points", r.status === 403, r);
r = await call("DELETE", `/trips/${tr2}`, H);
ok("delete trip", r.status === 200, r);
r = await call("GET", `/trips/${tr2}`, H);
ok("deleted trip is gone", r.status === 404, r);
r = await call("GET", `/trips/${tr1}`, J);
ok("outsider can't read a trip", r.status === 409 || r.status === 404, r.status);

// ---- profile media info (poster / cover)
r = await call("PUT", "/me/profile", H, { watching: "Arcane", playing: "Cyberpunk 2077", media: {
  watching: { title: "Arcane", blurb: "Animated series", img: "https://upload.wikimedia.org/wikipedia/en/thumb/a/a2/x.jpg/330px-x.jpg", src: "wikipedia" },
  playing: { title: "Cyberpunk 2077", blurb: "Open-world RPG", img: "https://evil.example.com/x.jpg", src: "wikipedia" },
  listening: { title: "Ghost song", img: "https://is1-ssl.mzstatic.com/image/x/600x600bb.jpg" },
  reading: "nonsense" } });
ok("media stored next to its title only", r.status === 200 && r.body.profile.media.watching.title === "Arcane" && r.body.profile.media.watching.img.startsWith("https://upload.wikimedia.org/") && !r.body.profile.media.listening, r.body.profile?.media ?? r.body);
ok("media image from an unknown host is dropped", r.body.profile.media.playing.img === "", r.body.profile.media.playing);
r = await call("PUT", "/me/profile", H, { watching: "", playing: "Cyberpunk 2077", media: { watching: { title: "Arcane", img: "https://upload.wikimedia.org/a.jpg" } } });
ok("clearing a title clears its media", r.status === 200 && !r.body.profile.media.watching, r.body.profile?.media);
r = await call("PUT", "/me/profile", H, { media: { playing: { title: "x".repeat(500) } }, playing: "x" });
ok("overlong media text rejected", r.status === 400, r);

// ---- new IQ minigames
for (const g of ["seq", "code", "lights", "slide"]) {
  r = await call("POST", "/games/matches", H, { game: g, rounds: 1 });
  ok(`${g}: match can be created`, r.status === 201 && r.body.game === g, r);
  const mid = r.body.id;
  const cap = { seq: 800, code: 1000, lights: 1000, slide: 1000 }[g];
  r = await call("PUT", `/games/matches/${mid}/score`, H, { round: 1, score: cap + 1 });
  ok(`${g}: score above the cap rejected`, r.status === 400, r);
  r = await call("PUT", `/games/matches/${mid}/score`, H, { round: 1, score: cap });
  ok(`${g}: top score accepted`, r.status === 200, r);
  await call("DELETE", `/games/matches/${mid}`, H);
}

// ---- account deletion cleans the new tables
sqlite.exec("DELETE FROM auth_attempts"); // the register rate limit is per IP
const tmp = await call("POST", "/auth/register", null, { username: "zeke", password: "secret000", displayName: "Zed" }); const Z = tmp.body.token;
const tmp2 = await call("POST", "/auth/register", null, { username: "yuki", password: "secret111", displayName: "Yan" }); const Y = tmp2.body.token;
r = await call("POST", "/couple", Z); await call("POST", "/couple/join", Y, { code: r.body.inviteCode });
await call("POST", "/addresses", Z, { label: "home", lat: 1, lon: 1 });
await call("POST", "/notes", Z, { title: "n", kind: "list", items: ["a"] });
r = await call("POST", "/trips", Z, {}); const trz = r.body.id;
await call("POST", `/trips/${trz}/points`, Z, { points: [{ t: Date.now(), lat: 1, lon: 1 }] });
r = await call("DELETE", "/me", Z, { password: "secret000" });
ok("delete account with update-4 data", r.status === 200, r);
r = await call("GET", "/addresses", Y);
ok("deleted user's addresses are gone", r.status === 409 || r.body.addresses.length === 0, r.body);

console.log(fails ? `\n${fails} FAILED` : "\nall passed");
process.exit(fails ? 1 : 0);
