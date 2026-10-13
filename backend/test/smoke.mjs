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
r = await call("POST", "/auth/register", null, { username: "zed", password: "secret456" });
for (let i = 0; i < 8; i++) await call("POST", "/auth/login", null, { username: "zed", password: "nope-nope" });
r = await call("POST", "/auth/login", null, { username: "zed", password: "secret456" });
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
console.log(fails ? `\n${fails} FAILED` : "\nall passed");
process.exit(fails ? 1 : 0);
