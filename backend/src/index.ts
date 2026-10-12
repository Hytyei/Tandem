// Tandem API — Cloudflare Worker + D1. No external dependencies.

export interface Env {
  DB: D1Database;
  JWT_SECRET: string;
}

const MOODS = ["happy", "loved", "calm", "excited", "tired", "sad", "anxious", "angry"];
const ACTIVITIES = [
  "gaming", "sleeping", "eating", "tv", "music", "football", "cycling",
  "hiking", "camping", "roadtrip", "stargazing", "photography", "hair", "nails",
];
const TOKEN_TTL = 60 * 60 * 24 * 90; // 90 days
const PBKDF2_ITER = 100_000;

class HttpError extends Error {
  constructor(public status: number, message: string) { super(message); }
}

// ------------------------------------------------------------- helpers
const enc = new TextEncoder();
const now = () => Date.now();
const uid = () => crypto.randomUUID().replace(/-/g, "");

function json(data: unknown, status = 200, extra: Record<string, string> = {}): Response {
  return new Response(JSON.stringify(data), {
    status,
    headers: { "content-type": "application/json; charset=utf-8", ...cors(), ...extra },
  });
}
function cors(): Record<string, string> {
  return {
    "access-control-allow-origin": "*",
    "access-control-allow-headers": "authorization, content-type",
    "access-control-allow-methods": "GET, POST, PUT, PATCH, DELETE, OPTIONS",
  };
}

const b64u = (buf: ArrayBuffer | Uint8Array): string => {
  const bytes = buf instanceof Uint8Array ? buf : new Uint8Array(buf);
  let s = "";
  for (const b of bytes) s += String.fromCharCode(b);
  return btoa(s).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
};
const b64uDecode = (s: string) => {
  s = s.replace(/-/g, "+").replace(/_/g, "/");
  while (s.length % 4) s += "=";
  const bin = atob(s);
  const out = new Uint8Array(new ArrayBuffer(bin.length));
  for (let i = 0; i < bin.length; i++) out[i] = bin.charCodeAt(i);
  return out;
};

async function hashPassword(password: string, salt: Uint8Array<ArrayBuffer>): Promise<string> {
  const key = await crypto.subtle.importKey("raw", enc.encode(password), "PBKDF2", false, ["deriveBits"]);
  const bits = await crypto.subtle.deriveBits(
    { name: "PBKDF2", hash: "SHA-256", salt, iterations: PBKDF2_ITER }, key, 256);
  return b64u(bits);
}

function timingSafeEqual(a: string, b: string): boolean {
  if (a.length !== b.length) return false;
  let r = 0;
  for (let i = 0; i < a.length; i++) r |= a.charCodeAt(i) ^ b.charCodeAt(i);
  return r === 0;
}

async function hmacKey(secret: string): Promise<CryptoKey> {
  return crypto.subtle.importKey("raw", enc.encode(secret), { name: "HMAC", hash: "SHA-256" }, false, ["sign", "verify"]);
}

async function signJwt(userId: string, secret: string): Promise<string> {
  const header = b64u(enc.encode(JSON.stringify({ alg: "HS256", typ: "JWT" })));
  const payload = b64u(enc.encode(JSON.stringify({ sub: userId, exp: Math.floor(now() / 1000) + TOKEN_TTL })));
  const sig = await crypto.subtle.sign("HMAC", await hmacKey(secret), enc.encode(`${header}.${payload}`));
  return `${header}.${payload}.${b64u(sig)}`;
}

async function verifyJwt(token: string, secret: string): Promise<string | null> {
  const parts = token.split(".");
  if (parts.length !== 3) return null;
  const ok = await crypto.subtle.verify("HMAC", await hmacKey(secret), b64uDecode(parts[2]), enc.encode(`${parts[0]}.${parts[1]}`));
  if (!ok) return null;
  try {
    const p = JSON.parse(new TextDecoder().decode(b64uDecode(parts[1])));
    if (typeof p.exp !== "number" || p.exp < now() / 1000) return null;
    return String(p.sub);
  } catch { return null; }
}

interface UserRow {
  id: string; username: string; display_name: string; bio: string;
  pass_hash: string; pass_salt: string; avatar_version: number; couple_id: string | null; created_at: number;
}

async function readJson<T = any>(req: Request): Promise<T> {
  try { return (await req.json()) as T; } catch { throw new HttpError(400, "Invalid JSON body"); }
}

function str(v: unknown, field: string, min: number, max: number): string {
  if (typeof v !== "string") throw new HttpError(400, `${field} is required`);
  const t = v.trim();
  if (t.length < min || t.length > max) throw new HttpError(400, `${field} must be ${min}-${max} characters`);
  return t;
}
const isDate = (s: unknown): s is string => typeof s === "string" && /^\d{4}-\d{2}-\d{2}$/.test(s) && !isNaN(Date.parse(s));
const isTime = (s: unknown): s is string => typeof s === "string" && /^([01]\d|2[0-3]):[0-5]\d$/.test(s);

function publicUser(u: UserRow) {
  return {
    id: u.id, username: u.username, displayName: u.display_name, bio: u.bio,
    avatarVersion: u.avatar_version, hasAvatar: u.avatar_version > 0,
  };
}

async function getUser(env: Env, id: string): Promise<UserRow | null> {
  return env.DB.prepare("SELECT * FROM users WHERE id = ?").bind(id).first<UserRow>();
}

async function partnerOf(env: Env, user: UserRow): Promise<UserRow | null> {
  if (!user.couple_id) return null;
  return env.DB.prepare("SELECT * FROM users WHERE couple_id = ? AND id != ?").bind(user.couple_id, user.id).first<UserRow>();
}

function requireCouple(user: UserRow): string {
  if (!user.couple_id) throw new HttpError(409, "You are not paired with a partner yet");
  return user.couple_id;
}

function moodJson(r: any) {
  return { id: r.id, userId: r.user_id, mood: r.mood, activity: r.activity, note: r.note, createdAt: r.created_at };
}
function diaryJson(r: any) {
  return { id: r.id, userId: r.user_id, text: r.text, mood: r.mood, createdAt: r.created_at };
}
function eventJson(r: any) {
  return {
    id: r.id, createdBy: r.created_by, title: r.title, emoji: r.emoji, date: r.date, time: r.time,
    repeatYearly: !!r.repeat_yearly, notes: r.notes,
  };
}

// ------------------------------------------------------------- handlers
type Ctx = { req: Request; env: Env; url: URL; user: UserRow; params: string[] };

async function register(req: Request, env: Env): Promise<Response> {
  const b = await readJson(req);
  const username = str(b.username, "username", 3, 24);
  if (!/^[A-Za-z0-9_.]+$/.test(username)) throw new HttpError(400, "Username may contain letters, numbers, _ and .");
  const password = str(b.password, "password", 6, 128);
  const displayName = str(b.displayName ?? username, "displayName", 1, 40);
  const exists = await env.DB.prepare("SELECT 1 FROM users WHERE username = ?").bind(username).first();
  if (exists) throw new HttpError(409, "That username is taken");
  const salt = crypto.getRandomValues(new Uint8Array(new ArrayBuffer(16)));
  const hash = await hashPassword(password, salt);
  const id = uid();
  await env.DB.prepare(
    "INSERT INTO users (id, username, display_name, pass_hash, pass_salt, created_at) VALUES (?,?,?,?,?,?)"
  ).bind(id, username, displayName, hash, b64u(salt), now()).run();
  const user = (await getUser(env, id))!;
  return json({ token: await signJwt(id, env.JWT_SECRET), user: publicUser(user) }, 201);
}

async function login(req: Request, env: Env): Promise<Response> {
  const b = await readJson(req);
  const username = str(b.username, "username", 1, 64);
  const password = str(b.password, "password", 1, 128);
  const user = await env.DB.prepare("SELECT * FROM users WHERE username = ?").bind(username).first<UserRow>();
  // Hash even when the user is missing so response time doesn't leak which usernames exist.
  const salt = user ? b64uDecode(user.pass_salt) : new Uint8Array(new ArrayBuffer(16));
  const hash = await hashPassword(password, salt);
  if (!user || !timingSafeEqual(hash, user.pass_hash)) throw new HttpError(401, "Wrong username or password");
  return json({ token: await signJwt(user.id, env.JWT_SECRET), user: publicUser(user) });
}

async function coupleInfo(env: Env, user: UserRow) {
  if (!user.couple_id) return { paired: false, inviteCode: null, anniversary: null, partner: null };
  const couple = await env.DB.prepare("SELECT * FROM couples WHERE id = ?").bind(user.couple_id).first<any>();
  const partner = await partnerOf(env, user);
  return {
    paired: !!partner,
    inviteCode: partner ? null : couple?.invite_code ?? null,
    anniversary: couple?.anniversary ?? null,
    partner: partner ? publicUser(partner) : null,
  };
}

function genCode(): string {
  const alphabet = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
  const bytes = crypto.getRandomValues(new Uint8Array(6));
  return Array.from(bytes, (b) => alphabet[b % alphabet.length]).join("");
}

async function coupleCreate(c: Ctx): Promise<Response> {
  if (c.user.couple_id) return json(await coupleInfo(c.env, c.user));
  const id = uid();
  let code = genCode();
  for (let i = 0; i < 5; i++) {
    const clash = await c.env.DB.prepare("SELECT 1 FROM couples WHERE invite_code = ?").bind(code).first();
    if (!clash) break;
    code = genCode();
  }
  await c.env.DB.batch([
    c.env.DB.prepare("INSERT INTO couples (id, invite_code, created_at) VALUES (?,?,?)").bind(id, code, now()),
    c.env.DB.prepare("UPDATE users SET couple_id = ? WHERE id = ?").bind(id, c.user.id),
  ]);
  const fresh = (await getUser(c.env, c.user.id))!;
  return json(await coupleInfo(c.env, fresh), 201);
}

async function coupleJoin(c: Ctx): Promise<Response> {
  if (c.user.couple_id) {
    const partner = await partnerOf(c.env, c.user);
    if (partner) throw new HttpError(409, "You are already paired");
    // Alone in own couple: allow switching by joining someone else's code.
  }
  const b = await readJson(c.req);
  const code = str(b.code, "code", 4, 12).toUpperCase();
  const couple = await c.env.DB.prepare("SELECT * FROM couples WHERE invite_code = ?").bind(code).first<any>();
  if (!couple) throw new HttpError(404, "Invite code not found");
  if (couple.id === c.user.couple_id) throw new HttpError(400, "That is your own invite code");
  const members = await c.env.DB.prepare("SELECT COUNT(*) AS n FROM users WHERE couple_id = ?").bind(couple.id).first<{ n: number }>();
  if ((members?.n ?? 0) >= 2) throw new HttpError(409, "That couple is already full");
  const stmts = [
    c.env.DB.prepare("UPDATE users SET couple_id = ? WHERE id = ?").bind(couple.id, c.user.id),
    c.env.DB.prepare("UPDATE couples SET invite_code = NULL, anniversary = COALESCE(anniversary, ?) WHERE id = ?")
      .bind(new Date().toISOString().slice(0, 10), couple.id),
  ];
  if (c.user.couple_id) stmts.push(c.env.DB.prepare("DELETE FROM couples WHERE id = ?").bind(c.user.couple_id));
  await c.env.DB.batch(stmts);
  const fresh = (await getUser(c.env, c.user.id))!;
  return json(await coupleInfo(c.env, fresh));
}

async function coupleUpdate(c: Ctx): Promise<Response> {
  const coupleId = requireCouple(c.user);
  const b = await readJson(c.req);
  if (!isDate(b.anniversary)) throw new HttpError(400, "anniversary must be YYYY-MM-DD");
  await c.env.DB.prepare("UPDATE couples SET anniversary = ? WHERE id = ?").bind(b.anniversary, coupleId).run();
  return json(await coupleInfo(c.env, c.user));
}

async function profileUpdate(c: Ctx): Promise<Response> {
  const b = await readJson(c.req);
  const displayName = b.displayName !== undefined ? str(b.displayName, "displayName", 1, 40) : c.user.display_name;
  const bio = b.bio !== undefined ? str(b.bio ?? "", "bio", 0, 160) : c.user.bio;
  await c.env.DB.prepare("UPDATE users SET display_name = ?, bio = ? WHERE id = ?").bind(displayName, bio, c.user.id).run();
  return json(publicUser((await getUser(c.env, c.user.id))!));
}

async function avatarPut(c: Ctx): Promise<Response> {
  const buf = await c.req.arrayBuffer();
  if (buf.byteLength < 100 || buf.byteLength > 200_000) throw new HttpError(413, "Avatar must be under 200 KB (the app resizes it for you)");
  const head = new Uint8Array(buf.slice(0, 3));
  const isJpeg = head[0] === 0xff && head[1] === 0xd8;
  const isPng = head[0] === 0x89 && head[1] === 0x50;
  if (!isJpeg && !isPng) throw new HttpError(415, "Avatar must be JPEG or PNG");
  const version = c.user.avatar_version + 1;
  await c.env.DB.prepare("UPDATE users SET avatar = ?, avatar_version = ? WHERE id = ?").bind(buf, version, c.user.id).run();
  return json(publicUser((await getUser(c.env, c.user.id))!));
}

// Avatars are only visible to the owner and their partner.
async function avatarGet(c: Ctx): Promise<Response> {
  const targetId = c.params[0];
  if (targetId !== c.user.id) {
    const partner = await partnerOf(c.env, c.user);
    if (!partner || partner.id !== targetId) throw new HttpError(404, "Not found");
  }
  const row = await c.env.DB.prepare("SELECT avatar FROM users WHERE id = ?").bind(targetId).first<{ avatar: ArrayBuffer | number[] | null }>();
  if (!row?.avatar) throw new HttpError(404, "No avatar");
  const bytes = row.avatar instanceof ArrayBuffer ? new Uint8Array(row.avatar) : new Uint8Array(row.avatar);
  const type = bytes[0] === 0x89 ? "image/png" : "image/jpeg";
  return new Response(bytes, { headers: { "content-type": type, "cache-control": "private, max-age=86400", ...cors() } });
}

async function moodPost(c: Ctx): Promise<Response> {
  const coupleId = requireCouple(c.user);
  const b = await readJson(c.req);
  if (!MOODS.includes(b.mood)) throw new HttpError(400, `mood must be one of ${MOODS.join(", ")}`);
  let activity: string | null = null;
  if (b.activity != null && b.activity !== "") {
    if (!ACTIVITIES.includes(b.activity)) throw new HttpError(400, `activity must be one of ${ACTIVITIES.join(", ")}`);
    activity = b.activity;
  }
  const note = b.note == null ? "" : str(b.note, "note", 0, 140);
  const id = uid(), t = now();
  await c.env.DB.prepare("INSERT INTO moods (id, user_id, couple_id, mood, activity, note, created_at) VALUES (?,?,?,?,?,?,?)")
    .bind(id, c.user.id, coupleId, b.mood, activity, note, t).run();
  return json(moodJson({ id, user_id: c.user.id, mood: b.mood, activity, note, created_at: t }), 201);
}

async function moodList(c: Ctx): Promise<Response> {
  const coupleId = requireCouple(c.user);
  const limit = Math.min(Math.max(parseInt(c.url.searchParams.get("limit") ?? "50", 10) || 50, 1), 200);
  const before = parseInt(c.url.searchParams.get("before") ?? "", 10);
  const q = Number.isFinite(before)
    ? c.env.DB.prepare("SELECT * FROM moods WHERE couple_id = ? AND created_at < ? ORDER BY created_at DESC, rowid DESC LIMIT ?").bind(coupleId, before, limit)
    : c.env.DB.prepare("SELECT * FROM moods WHERE couple_id = ? ORDER BY created_at DESC, rowid DESC LIMIT ?").bind(coupleId, limit);
  const { results } = await q.all();
  return json({ moods: results.map(moodJson) });
}

async function diaryPost(c: Ctx): Promise<Response> {
  const coupleId = requireCouple(c.user);
  const b = await readJson(c.req);
  const text = str(b.text, "text", 1, 2000);
  const mood = b.mood && MOODS.includes(b.mood) ? b.mood : null;
  const id = uid(), t = now();
  await c.env.DB.prepare("INSERT INTO diary (id, user_id, couple_id, text, mood, created_at) VALUES (?,?,?,?,?,?)")
    .bind(id, c.user.id, coupleId, text, mood, t).run();
  return json(diaryJson({ id, user_id: c.user.id, text, mood, created_at: t }), 201);
}

async function diaryList(c: Ctx): Promise<Response> {
  const coupleId = requireCouple(c.user);
  const limit = Math.min(Math.max(parseInt(c.url.searchParams.get("limit") ?? "50", 10) || 50, 1), 100);
  const before = parseInt(c.url.searchParams.get("before") ?? "", 10);
  const q = Number.isFinite(before)
    ? c.env.DB.prepare("SELECT * FROM diary WHERE couple_id = ? AND created_at < ? ORDER BY created_at DESC, rowid DESC LIMIT ?").bind(coupleId, before, limit)
    : c.env.DB.prepare("SELECT * FROM diary WHERE couple_id = ? ORDER BY created_at DESC, rowid DESC LIMIT ?").bind(coupleId, limit);
  const { results } = await q.all();
  return json({ entries: results.map(diaryJson) });
}

async function diaryDelete(c: Ctx): Promise<Response> {
  const coupleId = requireCouple(c.user);
  const r = await c.env.DB.prepare("DELETE FROM diary WHERE id = ? AND couple_id = ? AND user_id = ?")
    .bind(c.params[0], coupleId, c.user.id).run();
  if (!r.meta.changes) throw new HttpError(404, "Entry not found (you can only delete your own)");
  return json({ ok: true });
}

function readEvent(b: any) {
  const title = str(b.title, "title", 1, 80);
  if (!isDate(b.date)) throw new HttpError(400, "date must be YYYY-MM-DD");
  if (b.time != null && b.time !== "" && !isTime(b.time)) throw new HttpError(400, "time must be HH:mm");
  const emoji = b.emoji ? str(b.emoji, "emoji", 1, 8) : "❤️";
  const notes = b.notes == null ? "" : str(b.notes, "notes", 0, 500);
  return { title, date: b.date, time: b.time ? b.time : null, emoji, notes, repeat: b.repeatYearly ? 1 : 0 };
}

async function eventPost(c: Ctx): Promise<Response> {
  const coupleId = requireCouple(c.user);
  const e = readEvent(await readJson(c.req));
  const id = uid();
  await c.env.DB.prepare(
    "INSERT INTO events (id, couple_id, created_by, title, emoji, date, time, repeat_yearly, notes, created_at) VALUES (?,?,?,?,?,?,?,?,?,?)"
  ).bind(id, coupleId, c.user.id, e.title, e.emoji, e.date, e.time, e.repeat, e.notes, now()).run();
  return json(eventJson({ id, created_by: c.user.id, title: e.title, emoji: e.emoji, date: e.date, time: e.time, repeat_yearly: e.repeat, notes: e.notes }), 201);
}

async function eventPut(c: Ctx): Promise<Response> {
  const coupleId = requireCouple(c.user);
  const e = readEvent(await readJson(c.req));
  const r = await c.env.DB.prepare(
    "UPDATE events SET title=?, emoji=?, date=?, time=?, repeat_yearly=?, notes=? WHERE id=? AND couple_id=?"
  ).bind(e.title, e.emoji, e.date, e.time, e.repeat, e.notes, c.params[0], coupleId).run();
  if (!r.meta.changes) throw new HttpError(404, "Event not found");
  return json(eventJson({ id: c.params[0], created_by: c.user.id, title: e.title, emoji: e.emoji, date: e.date, time: e.time, repeat_yearly: e.repeat, notes: e.notes }));
}

async function eventDelete(c: Ctx): Promise<Response> {
  const coupleId = requireCouple(c.user);
  const r = await c.env.DB.prepare("DELETE FROM events WHERE id = ? AND couple_id = ?").bind(c.params[0], coupleId).run();
  if (!r.meta.changes) throw new HttpError(404, "Event not found");
  return json({ ok: true });
}

async function eventList(c: Ctx): Promise<Response> {
  const coupleId = requireCouple(c.user);
  const { results } = await c.env.DB.prepare("SELECT * FROM events WHERE couple_id = ? ORDER BY date ASC, time ASC").bind(coupleId).all();
  return json({ events: results.map(eventJson) });
}

/** One call that feeds the whole app and every widget. */
async function sync(c: Ctx): Promise<Response> {
  const user = c.user;
  const info = await coupleInfo(c.env, user);
  const base = { serverTime: now(), me: publicUser(user), couple: info };
  if (!user.couple_id) return json({ ...base, moods: [], latestMoods: {}, diary: [], events: [], stats: null });

  const coupleId = user.couple_id;
  const [moods, diary, events, stats, latest] = await Promise.all([
    c.env.DB.prepare("SELECT * FROM moods WHERE couple_id = ? ORDER BY created_at DESC, rowid DESC LIMIT 60").bind(coupleId).all(),
    c.env.DB.prepare("SELECT * FROM diary WHERE couple_id = ? ORDER BY created_at DESC, rowid DESC LIMIT 30").bind(coupleId).all(),
    c.env.DB.prepare("SELECT * FROM events WHERE couple_id = ? ORDER BY date ASC, time ASC").bind(coupleId).all(),
    c.env.DB.prepare(
      "SELECT (SELECT COUNT(*) FROM moods WHERE couple_id = ?1) AS moods, (SELECT COUNT(*) FROM diary WHERE couple_id = ?1) AS diary, " +
      "(SELECT COUNT(DISTINCT date(created_at/1000,'unixepoch')) FROM moods WHERE couple_id = ?1) AS activeDays"
    ).bind(coupleId).first<any>(),
    c.env.DB.prepare(
      "SELECT m.* FROM moods m JOIN (SELECT user_id, MAX(created_at) AS t FROM moods WHERE couple_id = ? GROUP BY user_id) x " +
      "ON m.user_id = x.user_id AND m.created_at = x.t ORDER BY m.rowid"
    ).bind(coupleId).all(),
  ]);
  const latestMoods: Record<string, unknown> = {};
  for (const r of latest.results as any[]) latestMoods[r.user_id] = moodJson(r);

  return json({
    ...base,
    moods: moods.results.map(moodJson),
    latestMoods,
    diary: diary.results.map(diaryJson),
    events: events.results.map(eventJson),
    stats,
  });
}

// ------------------------------------------------------------- router
type Handler = (c: Ctx) => Promise<Response>;
const routes: { method: string; re: RegExp; h: Handler }[] = [];
const route = (method: string, path: string, h: Handler) =>
  routes.push({ method, re: new RegExp("^" + path.replace(/:\w+/g, "([^/]+)") + "$"), h });

route("GET", "/me", async (c) => json({ user: publicUser(c.user), couple: await coupleInfo(c.env, c.user) }));
route("PATCH", "/me", profileUpdate);
route("PUT", "/me/avatar", avatarPut);
route("GET", "/avatar/:id", avatarGet);
route("POST", "/couple", coupleCreate);
route("POST", "/couple/join", coupleJoin);
route("PATCH", "/couple", coupleUpdate);
route("POST", "/moods", moodPost);
route("GET", "/moods", moodList);
route("POST", "/diary", diaryPost);
route("GET", "/diary", diaryList);
route("DELETE", "/diary/:id", diaryDelete);
route("GET", "/events", eventList);
route("POST", "/events", eventPost);
route("PUT", "/events/:id", eventPut);
route("DELETE", "/events/:id", eventDelete);
route("GET", "/sync", sync);

export default {
  async fetch(req: Request, env: Env): Promise<Response> {
    if (req.method === "OPTIONS") return new Response(null, { status: 204, headers: cors() });
    const url = new URL(req.url);
    try {
      if (!env.JWT_SECRET) throw new HttpError(500, "Server is missing JWT_SECRET");
      if (url.pathname === "/" || url.pathname === "/health") return json({ ok: true, app: "tandem" });
      if (req.method === "POST" && url.pathname === "/auth/register") return await register(req, env);
      if (req.method === "POST" && url.pathname === "/auth/login") return await login(req, env);

      const auth = req.headers.get("authorization") ?? "";
      const token = auth.startsWith("Bearer ") ? auth.slice(7) : "";
      const userId = token ? await verifyJwt(token, env.JWT_SECRET) : null;
      if (!userId) throw new HttpError(401, "Please sign in again");
      const user = await getUser(env, userId);
      if (!user) throw new HttpError(401, "Account no longer exists");

      for (const r of routes) {
        if (r.method !== req.method) continue;
        const m = url.pathname.match(r.re);
        if (m) return await r.h({ req, env, url, user, params: m.slice(1) });
      }
      throw new HttpError(404, "Not found");
    } catch (e) {
      if (e instanceof HttpError) return json({ error: e.message }, e.status);
      console.error(e);
      return json({ error: "Something went wrong on the server" }, 500);
    }
  },
};
