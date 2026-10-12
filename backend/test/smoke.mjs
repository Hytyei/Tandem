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

let r = await call("POST", "/auth/register", null, { username: "sara", password: "secret1", displayName: "Sara" });
ok("register A", r.status === 201 && r.body.token, r); const A = r.body.token; const aId = r.body.user.id;
r = await call("POST", "/auth/register", null, { username: "SARA", password: "secret1" });
ok("duplicate username (case-insens) rejected", r.status === 409, r);
r = await call("POST", "/auth/register", null, { username: "ali", password: "secret2", displayName: "Ali" });
const B = r.body.token; const bId = r.body.user.id;
r = await call("POST", "/auth/login", null, { username: "sara", password: "wrong!!" });
ok("bad password 401", r.status === 401, r);
r = await call("POST", "/auth/login", null, { username: "sara", password: "secret1" });
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
r = await call("POST", "/auth/register", null, { username: "eve", password: "secret3" });
const E = r.body.token;
r = await call("GET", "/avatar/" + aId, E);
ok("stranger cannot fetch avatar", r.status === 404, r);
r = await call("PUT", "/me/avatar", A, null, new Uint8Array(10));
ok("tiny/invalid avatar rejected", r.status === 413, r);

r = await call("POST", "/couple", E); const code2 = r.body.inviteCode;
r = await call("POST", "/couple/join", B, { code: code2 });
ok("already-paired user cannot join another", r.status === 409, r);
console.log(fails ? `\n${fails} FAILED` : "\nall passed");
process.exit(fails ? 1 : 0);
