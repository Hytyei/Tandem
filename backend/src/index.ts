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

// The only client is the Android app, so there is deliberately no CORS: browsers can't call this API.
const SEC: Record<string, string> = {
  "x-content-type-options": "nosniff",
  "referrer-policy": "no-referrer",
  "content-security-policy": "default-src 'none'; frame-ancestors 'none'",
};

function json(data: unknown, status = 200, extra: Record<string, string> = {}): Response {
  return new Response(JSON.stringify(data), {
    status,
    headers: { "content-type": "application/json; charset=utf-8", "cache-control": "no-store", ...SEC, ...extra },
  });
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

// ------------------------------------------------------------- rate limiting (stored in D1)
const clientIp = (req: Request) => req.headers.get("cf-connecting-ip") ?? "unknown";

async function attempts(env: Env, key: string): Promise<number> {
  const r = await env.DB.prepare("SELECT count FROM auth_attempts WHERE key = ? AND reset_at > ?").bind(key, now()).first<{ count: number }>();
  return r?.count ?? 0;
}
async function bump(env: Env, key: string, windowMs: number): Promise<number> {
  const t = now();
  const r = await env.DB.prepare(
    "INSERT INTO auth_attempts (key, count, reset_at) VALUES (?1, 1, ?2) ON CONFLICT(key) DO UPDATE SET " +
    "count = CASE WHEN reset_at < ?3 THEN 1 ELSE count + 1 END, reset_at = CASE WHEN reset_at < ?3 THEN ?2 ELSE reset_at END RETURNING count"
  ).bind(key, t + windowMs, t).first<{ count: number }>();
  if (Math.random() < 0.02) await env.DB.prepare("DELETE FROM auth_attempts WHERE reset_at < ?").bind(t).run();
  return r?.count ?? 1;
}
const tooMany = () => new HttpError(429, "Too many attempts. Please wait a few minutes and try again.");
const MIN = 60_000;

interface UserRow {
  id: string; username: string; display_name: string; bio: string;
  pass_hash: string; pass_salt: string; avatar_version: number; couple_id: string | null; created_at: number;
}

async function readJson<T = any>(req: Request): Promise<T> {
  const len = parseInt(req.headers.get("content-length") ?? "0", 10);
  if (len > 64 * 1024) throw new HttpError(413, "Request is too large");
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
const isoDay = (ms: number) => new Date(ms).toISOString().slice(0, 10);
function diaryJson(r: any, images: string[] = []) {
  return {
    id: r.id, userId: r.user_id, text: r.text, mood: r.mood, createdAt: r.created_at,
    entryDate: r.entry_date ?? isoDay(r.created_at), images,
    rating: r.rating ?? null, dateType: r.date_type ?? null, updatedAt: r.updated_at ?? 0,
  };
}
async function withImages(env: Env, rows: any[]) {
  const ids = rows.map((r) => r.id as string);
  const map: Record<string, string[]> = {};
  if (ids.length) {
    const { results } = await env.DB.prepare(
      `SELECT id, diary_id FROM diary_images WHERE diary_id IN (${ids.map(() => "?").join(",")}) ORDER BY created_at, rowid`
    ).bind(...ids).all();
    for (const r of results as any[]) (map[r.diary_id] ??= []).push(r.id);
  }
  return rows.map((r) => diaryJson(r, map[r.id] ?? []));
}
const DIARY_ORDER = "ORDER BY COALESCE(entry_date, date(created_at/1000,'unixepoch')) DESC, created_at DESC, rowid DESC";
const DATE_TYPES = ["datenight", "movie", "gaming", "dinner", "coffee", "walk", "trip", "cooking", "concert", "workout", "picnic", "boardgames", "cozy", "shopping", "celebration", "other"];
const dateType = (v: unknown): string | null => (typeof v === "string" && DATE_TYPES.includes(v) ? v : null);
function readRating(v: unknown): number | null {
  if (v == null || v === "") return null;
  if (typeof v !== "number" || !Number.isInteger(v) || v < 1 || v > 5) throw new HttpError(400, "rating must be 1 to 5");
  return v;
}
const CATEGORIES = ["birthday", "valentine", "anniversary", "firstdate", "firstkiss", "proposal", "wedding", "trip", "custom"];
function eventJson(r: any) {
  return {
    id: r.id, createdBy: r.created_by, title: r.title, emoji: r.emoji, date: r.date, time: r.time,
    repeatYearly: !!r.repeat_yearly, notes: r.notes,
    kind: r.kind ?? "event", category: r.category ?? null, updatedAt: r.updated_at ?? 0, updatedBy: r.updated_by ?? null, dateType: r.date_type ?? null,
  };
}

// ------------------------------------------------------------- handlers
type Ctx = { req: Request; env: Env; url: URL; user: UserRow; params: string[] };

async function register(req: Request, env: Env): Promise<Response> {
  const b = await readJson(req);
  const username = str(b.username, "username", 3, 24);
  if (!/^[A-Za-z0-9_.]+$/.test(username)) throw new HttpError(400, "Username may contain letters, numbers, _ and .");
  const password = str(b.password, "password", 8, 128);
  const displayName = str(b.displayName ?? username, "displayName", 1, 40);
  if ((await bump(env, `reg:ip:${clientIp(req)}`, 60 * MIN)) > 10) throw tooMany();
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
  const ukey = `login:u:${username.toLowerCase()}`, ikey = `login:ip:${clientIp(req)}`;
  if ((await attempts(env, ukey)) >= 8 || (await attempts(env, ikey)) >= 40) throw tooMany();
  const user = await env.DB.prepare("SELECT * FROM users WHERE username = ?").bind(username).first<UserRow>();
  // Hash even when the user is missing so response time doesn't leak which usernames exist.
  const salt = user ? b64uDecode(user.pass_salt) : new Uint8Array(new ArrayBuffer(16));
  const hash = await hashPassword(password, salt);
  if (!user || !timingSafeEqual(hash, user.pass_hash)) {
    await bump(env, ukey, 15 * MIN); await bump(env, ikey, 15 * MIN);
    throw new HttpError(401, "Wrong username or password");
  }
  await env.DB.prepare("DELETE FROM auth_attempts WHERE key = ?").bind(ukey).run();
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
  if ((await attempts(c.env, `join:u:${c.user.id}`)) >= 10) throw tooMany();
  const b = await readJson(c.req);
  const code = str(b.code, "code", 4, 12).toUpperCase();
  const couple = await c.env.DB.prepare("SELECT * FROM couples WHERE invite_code = ? AND status = 'active'").bind(code).first<any>();
  if (!couple) { await bump(c.env, `join:u:${c.user.id}`, 60 * MIN); throw new HttpError(404, "Invite code not found"); }
  if (couple.id === c.user.couple_id) throw new HttpError(400, "That is your own invite code");
  const host = await c.env.DB.prepare("SELECT id FROM users WHERE couple_id = ?").bind(couple.id).all<{ id: string }>();
  if (host.results.length >= 2) throw new HttpError(409, "That couple is already full");
  const hostId = host.results[0]?.id;
  if (!hostId) throw new HttpError(404, "Invite code not found");
  const [ua, ub] = [c.user.id, hostId].sort();
  const t = now();

  // Same two people connecting again? Bring their saved history back instead of starting blank.
  const old = await c.env.DB.prepare(
    "SELECT id FROM couples WHERE status = 'ended' AND user_a = ? AND user_b = ? ORDER BY ended_at DESC LIMIT 1"
  ).bind(ua, ub).first<{ id: string }>();

  const stmts = [];
  if (old) {
    stmts.push(
      c.env.DB.prepare("UPDATE users SET couple_id = ? WHERE id IN (?, ?)").bind(old.id, ua, ub),
      c.env.DB.prepare("UPDATE couples SET status = 'active', ended_at = NULL, invite_code = NULL WHERE id = ?").bind(old.id),
      c.env.DB.prepare("DELETE FROM couples WHERE id = ?").bind(couple.id),
    );
  } else {
    stmts.push(
      c.env.DB.prepare("UPDATE users SET couple_id = ? WHERE id = ?").bind(couple.id, c.user.id),
      c.env.DB.prepare("UPDATE couples SET invite_code = NULL, user_a = ?, user_b = ?, anniversary = COALESCE(anniversary, ?) WHERE id = ?")
        .bind(ua, ub, isoDay(t), couple.id),
    );
  }
  if (c.user.couple_id) stmts.push(c.env.DB.prepare("DELETE FROM couples WHERE id = ? AND status = 'active'").bind(c.user.couple_id));
  await c.env.DB.batch(stmts);
  const fresh = (await getUser(c.env, c.user.id))!;
  return json(await coupleInfo(c.env, fresh));
}

/** End the relationship in the app. Nothing is deleted: the history stays stored for the pair, and
 *  comes back only if the same two people connect again. Both people go back to the pairing screen. */
async function coupleLeave(c: Ctx): Promise<Response> {
  const coupleId = requireCouple(c.user);
  const partner = await partnerOf(c.env, c.user);
  if (!partner) {
    await c.env.DB.batch([
      c.env.DB.prepare("UPDATE users SET couple_id = NULL WHERE id = ?").bind(c.user.id),
      c.env.DB.prepare("DELETE FROM couples WHERE id = ?1 AND NOT EXISTS (SELECT 1 FROM moods WHERE couple_id = ?1)").bind(coupleId),
    ]);
    return json({ ok: true });
  }
  const [ua, ub] = [c.user.id, partner.id].sort();
  await c.env.DB.batch([
    c.env.DB.prepare("UPDATE users SET couple_id = NULL WHERE couple_id = ?").bind(coupleId),
    c.env.DB.prepare("UPDATE couples SET status = 'ended', ended_at = ?, user_a = ?, user_b = ?, invite_code = NULL WHERE id = ?")
      .bind(now(), ua, ub, coupleId),
  ]);
  return json({ ok: true });
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
  return new Response(bytes, { headers: { "content-type": type, "cache-control": "private, max-age=86400", ...SEC } });
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
  const t = now();
  let entryDate: string | null = null;
  if (b.entryDate != null && b.entryDate !== "") {
    if (!isDate(b.entryDate)) throw new HttpError(400, "entryDate must be YYYY-MM-DD");
    if (b.entryDate > isoDay(t + 36 * 3600_000)) throw new HttpError(400, "A diary entry can't be dated in the future");
    if (b.entryDate < "1970-01-01") throw new HttpError(400, "entryDate is too old");
    entryDate = b.entryDate;
  }
  const rating = readRating(b.rating);
  const dtype = dateType(b.dateType);
  const id = uid();
  await c.env.DB.prepare("INSERT INTO diary (id, user_id, couple_id, text, mood, entry_date, rating, date_type, created_at) VALUES (?,?,?,?,?,?,?,?,?)")
    .bind(id, c.user.id, coupleId, text, mood, entryDate, rating, dtype, t).run();
  return json(diaryJson({ id, user_id: c.user.id, text, mood, entry_date: entryDate, rating, date_type: dtype, created_at: t }), 201);
}

/** Edit your own note. Send the fields you want to keep or change; `mood`, `rating` and `dateType` may be null to clear them. */
async function diaryPut(c: Ctx): Promise<Response> {
  const coupleId = requireCouple(c.user);
  const row = await c.env.DB.prepare("SELECT * FROM diary WHERE id = ? AND couple_id = ? AND user_id = ?").bind(c.params[0], coupleId, c.user.id).first<any>();
  if (!row) throw new HttpError(404, "Entry not found (you can only edit your own)");
  const b = await readJson(c.req);
  const text = b.text === undefined ? row.text : str(b.text, "text", 1, 2000);
  const mood = b.mood === undefined ? row.mood : b.mood && MOODS.includes(b.mood) ? b.mood : null;
  let entryDate: string | null = row.entry_date ?? null;
  if (b.entryDate != null && b.entryDate !== "") {
    if (!isDate(b.entryDate)) throw new HttpError(400, "entryDate must be YYYY-MM-DD");
    if (b.entryDate > isoDay(now() + 36 * 3600_000)) throw new HttpError(400, "A diary entry can't be dated in the future");
    if (b.entryDate < "1970-01-01") throw new HttpError(400, "entryDate is too old");
    entryDate = b.entryDate;
  }
  const rating = b.rating === undefined ? row.rating ?? null : readRating(b.rating);
  const dtype = b.dateType === undefined ? row.date_type ?? null : dateType(b.dateType);
  const t = now();
  await c.env.DB.prepare("UPDATE diary SET text=?, mood=?, entry_date=?, rating=?, date_type=?, updated_at=? WHERE id=? AND couple_id=? AND user_id=?")
    .bind(text, mood, entryDate, rating, dtype, t, c.params[0], coupleId, c.user.id).run();
  const updated = { ...row, text, mood, entry_date: entryDate, rating, date_type: dtype, updated_at: t };
  return json((await withImages(c.env, [updated]))[0]);
}

async function diaryList(c: Ctx): Promise<Response> {
  const coupleId = requireCouple(c.user);
  const limit = Math.min(Math.max(parseInt(c.url.searchParams.get("limit") ?? "30", 10) || 30, 1), 100);
  const offset = Math.max(parseInt(c.url.searchParams.get("offset") ?? "0", 10) || 0, 0);
  const { results } = await c.env.DB.prepare(`SELECT * FROM diary WHERE couple_id = ? ${DIARY_ORDER} LIMIT ? OFFSET ?`)
    .bind(coupleId, limit, offset).all();
  return json({ entries: await withImages(c.env, results as any[]) });
}

async function diaryDelete(c: Ctx): Promise<Response> {
  const coupleId = requireCouple(c.user);
  const own = await c.env.DB.prepare("SELECT 1 FROM diary WHERE id = ? AND couple_id = ? AND user_id = ?").bind(c.params[0], coupleId, c.user.id).first();
  if (!own) throw new HttpError(404, "Entry not found (you can only delete your own)");
  await c.env.DB.batch([
    c.env.DB.prepare("DELETE FROM diary_images WHERE diary_id = ?").bind(c.params[0]),
    c.env.DB.prepare("DELETE FROM diary WHERE id = ? AND couple_id = ? AND user_id = ?").bind(c.params[0], coupleId, c.user.id),
  ]);
  return json({ ok: true });
}

const MAX_IMAGES_PER_ENTRY = 4;
const MAX_IMAGES_PER_COUPLE = 200;
const MAX_IMAGE_BYTES = 450_000;

/** Raw JPEG body. The app resizes + strips metadata first; the server re-checks size and type. */
async function diaryImagePost(c: Ctx): Promise<Response> {
  const coupleId = requireCouple(c.user);
  const entry = await c.env.DB.prepare("SELECT 1 FROM diary WHERE id = ? AND couple_id = ? AND user_id = ?").bind(c.params[0], coupleId, c.user.id).first();
  if (!entry) throw new HttpError(404, "Entry not found (you can only add pictures to your own notes)");
  const len = parseInt(c.req.headers.get("content-length") ?? "0", 10);
  if (len > MAX_IMAGE_BYTES) throw new HttpError(413, "Picture is too large");
  const buf = await c.req.arrayBuffer();
  if (buf.byteLength < 200 || buf.byteLength > MAX_IMAGE_BYTES) throw new HttpError(413, "Picture must be under 450 KB (the app resizes it for you)");
  const h = new Uint8Array(buf.slice(0, 3));
  if (!(h[0] === 0xff && h[1] === 0xd8 && h[2] === 0xff)) throw new HttpError(415, "Pictures must be JPEG");
  const counts = await c.env.DB.prepare(
    "SELECT (SELECT COUNT(*) FROM diary_images WHERE diary_id = ?1) AS e, (SELECT COUNT(*) FROM diary_images WHERE couple_id = ?2) AS c"
  ).bind(c.params[0], coupleId).first<{ e: number; c: number }>();
  if ((counts?.e ?? 0) >= MAX_IMAGES_PER_ENTRY) throw new HttpError(409, `At most ${MAX_IMAGES_PER_ENTRY} pictures per note`);
  if ((counts?.c ?? 0) >= MAX_IMAGES_PER_COUPLE) throw new HttpError(409, "Your shared picture storage is full. Delete some old notes with pictures first.");
  const id = uid();
  await c.env.DB.prepare("INSERT INTO diary_images (id, diary_id, couple_id, user_id, data, created_at) VALUES (?,?,?,?,?,?)")
    .bind(id, c.params[0], coupleId, c.user.id, buf, now()).run();
  return json({ id }, 201);
}

async function diaryImageDelete(c: Ctx): Promise<Response> {
  const coupleId = requireCouple(c.user);
  const r = await c.env.DB.prepare("DELETE FROM diary_images WHERE id = ? AND couple_id = ? AND user_id = ?").bind(c.params[0], coupleId, c.user.id).run();
  if (!r.meta.changes) throw new HttpError(404, "Picture not found (you can only remove your own)");
  return json({ ok: true });
}

// Only the couple that owns the entry can fetch its pictures.
async function diaryImageGet(c: Ctx): Promise<Response> {
  const coupleId = requireCouple(c.user);
  const row = await c.env.DB.prepare("SELECT data FROM diary_images WHERE id = ? AND couple_id = ?").bind(c.params[0], coupleId)
    .first<{ data: ArrayBuffer | number[] | null }>();
  if (!row?.data) throw new HttpError(404, "Not found");
  const bytes = row.data instanceof ArrayBuffer ? new Uint8Array(row.data) : new Uint8Array(row.data);
  return new Response(bytes, { headers: { "content-type": "image/jpeg", "cache-control": "private, max-age=31536000, immutable", ...SEC } });
}

function readEvent(b: any) {
  const title = str(b.title, "title", 1, 80);
  if (!isDate(b.date)) throw new HttpError(400, "date must be YYYY-MM-DD");
  if (b.time != null && b.time !== "" && !isTime(b.time)) throw new HttpError(400, "time must be HH:mm");
  const emoji = b.emoji ? str(b.emoji, "emoji", 1, 8) : "❤️";
  const notes = b.notes == null ? "" : str(b.notes, "notes", 0, 500);
  const kind = b.kind === "special" ? "special" : "event";
  const category = kind === "special" ? (CATEGORIES.includes(b.category) ? b.category : "custom") : null;
  return { title, date: b.date, time: b.time ? b.time : null, emoji, notes, repeat: b.repeatYearly ? 1 : 0, kind, category, dtype: dateType(b.dateType) };
}

async function eventPost(c: Ctx): Promise<Response> {
  const coupleId = requireCouple(c.user);
  const e = readEvent(await readJson(c.req));
  const id = uid(), t = now();
  const n = await c.env.DB.prepare("SELECT COUNT(*) AS n FROM events WHERE couple_id = ?").bind(coupleId).first<{ n: number }>();
  if ((n?.n ?? 0) >= 500) throw new HttpError(409, "Too many events. Delete some old ones first.");
  await c.env.DB.prepare(
    "INSERT INTO events (id, couple_id, created_by, title, emoji, date, time, repeat_yearly, notes, kind, category, date_type, updated_at, updated_by, created_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)"
  ).bind(id, coupleId, c.user.id, e.title, e.emoji, e.date, e.time, e.repeat, e.notes, e.kind, e.category, e.dtype, t, c.user.id, t).run();
  return json(eventJson({ id, created_by: c.user.id, title: e.title, emoji: e.emoji, date: e.date, time: e.time, repeat_yearly: e.repeat, notes: e.notes, kind: e.kind, category: e.category, date_type: e.dtype, updated_at: t, updated_by: c.user.id }), 201);
}

async function eventPut(c: Ctx): Promise<Response> {
  const coupleId = requireCouple(c.user);
  const e = readEvent(await readJson(c.req));
  const existing = await c.env.DB.prepare("SELECT created_by FROM events WHERE id = ? AND couple_id = ?").bind(c.params[0], coupleId).first<{ created_by: string }>();
  if (!existing) throw new HttpError(404, "Event not found");
  const t = now();
  await c.env.DB.prepare(
    "UPDATE events SET title=?, emoji=?, date=?, time=?, repeat_yearly=?, notes=?, kind=?, category=?, date_type=?, updated_at=?, updated_by=? WHERE id=? AND couple_id=?"
  ).bind(e.title, e.emoji, e.date, e.time, e.repeat, e.notes, e.kind, e.category, e.dtype, t, c.user.id, c.params[0], coupleId).run();
  return json(eventJson({ id: c.params[0], created_by: existing.created_by, title: e.title, emoji: e.emoji, date: e.date, time: e.time, repeat_yearly: e.repeat, notes: e.notes, kind: e.kind, category: e.category, date_type: e.dtype, updated_at: t, updated_by: c.user.id }));
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
    c.env.DB.prepare(`SELECT * FROM diary WHERE couple_id = ? ${DIARY_ORDER} LIMIT 40`).bind(coupleId).all(),
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
    diary: await withImages(c.env, diary.results as any[]),
    events: events.results.map(eventJson),
    stats,
  });
}

/** Richer numbers for the Stats screen. `tz` = the phone's UTC offset in minutes so days line up with local midnight. */
async function statsDetail(c: Ctx): Promise<Response> {
  const coupleId = requireCouple(c.user);
  const tz = Math.min(Math.max(parseInt(c.url.searchParams.get("tz") ?? "0", 10) || 0, -840), 840);
  const mod = `${tz >= 0 ? "+" : "-"}${Math.abs(tz)} minutes`;
  const t = now();
  const localToday = new Date(t + tz * 60_000).toISOString().slice(0, 10);
  const [moodCounts, activityCounts, daily, days, diaryCounts, photos, dateTypeCounts, totals] = await Promise.all([
    c.env.DB.prepare("SELECT user_id AS userId, mood, COUNT(*) AS n FROM moods WHERE couple_id = ? GROUP BY user_id, mood").bind(coupleId).all(),
    c.env.DB.prepare("SELECT user_id AS userId, activity, COUNT(*) AS n FROM moods WHERE couple_id = ? AND activity IS NOT NULL GROUP BY user_id, activity ORDER BY n DESC LIMIT 60").bind(coupleId).all(),
    c.env.DB.prepare("SELECT date(created_at/1000,'unixepoch',?1) AS day, user_id AS userId, COUNT(*) AS n FROM moods WHERE couple_id = ?2 AND created_at > ?3 GROUP BY day, user_id")
      .bind(mod, coupleId, t - 32 * 86400_000).all(),
    c.env.DB.prepare(
      "SELECT day FROM (SELECT date(created_at/1000,'unixepoch',?1) AS day FROM moods WHERE couple_id = ?2 UNION SELECT date(created_at/1000,'unixepoch',?1) FROM diary WHERE couple_id = ?2) ORDER BY day DESC LIMIT 800"
    ).bind(mod, coupleId).all(),
    c.env.DB.prepare("SELECT user_id AS userId, COUNT(*) AS n FROM diary WHERE couple_id = ? GROUP BY user_id").bind(coupleId).all(),
    c.env.DB.prepare("SELECT COUNT(*) AS n FROM diary_images WHERE couple_id = ?").bind(coupleId).first<{ n: number }>(),
    // Date types from diary notes plus events that have already happened.
    c.env.DB.prepare(
      "SELECT date_type AS dateType, COUNT(*) AS n FROM (" +
      "SELECT date_type FROM diary WHERE couple_id = ?1 AND date_type IS NOT NULL UNION ALL " +
      "SELECT date_type FROM events WHERE couple_id = ?1 AND date_type IS NOT NULL AND date <= ?2) GROUP BY date_type ORDER BY n DESC"
    ).bind(coupleId, localToday).all(),
    c.env.DB.prepare(
      "SELECT (SELECT COUNT(*) FROM moods WHERE couple_id = ?1) AS moods, (SELECT COUNT(*) FROM diary WHERE couple_id = ?1) AS diary, " +
      "(SELECT COUNT(*) FROM events WHERE couple_id = ?1 AND kind = 'event') AS events, (SELECT COUNT(*) FROM events WHERE couple_id = ?1 AND kind = 'special') AS specials, " +
      "(SELECT COUNT(*) FROM diary WHERE couple_id = ?1 AND rating IS NOT NULL) AS rated, (SELECT COUNT(*) FROM diary WHERE couple_id = ?1 AND rating = 5) AS fiveStar"
    ).bind(coupleId).first<any>(),
  ]);
  // Streaks over the days either of you checked in (mood or diary).
  const list = (days.results as any[]).map((r) => r.day as string); // newest first
  const dayNum = (d: string) => Math.floor(Date.parse(d + "T00:00:00Z") / 86400_000);
  const today = Math.floor((t + tz * 60_000) / 86400_000);
  let streak = 0;
  if (list.length && dayNum(list[0]) >= today - 1) {
    streak = 1;
    for (let i = 1; i < list.length && dayNum(list[i - 1]) - dayNum(list[i]) === 1; i++) streak++;
  }
  let best = 0, run = 0;
  for (let i = 0; i < list.length; i++) {
    run = i > 0 && dayNum(list[i - 1]) - dayNum(list[i]) === 1 ? run + 1 : 1;
    if (run > best) best = run;
  }
  return json({
    moodCounts: moodCounts.results, activityCounts: activityCounts.results, daily: daily.results,
    diaryCounts: diaryCounts.results, photos: photos?.n ?? 0, streak, bestStreak: best, checkInDays: list.length,
    dateTypeCounts: dateTypeCounts.results,
    totals: { moods: totals?.moods ?? 0, diary: totals?.diary ?? 0, events: totals?.events ?? 0, specials: totals?.specials ?? 0, rated: totals?.rated ?? 0, fiveStar: totals?.fiveStar ?? 0 },
  });
}

/**
 * "Remember when": things from earlier years on this calendar day, last month's highlights, the best-rated notes of this
 * month and of all time. `date` = the phone's local today (YYYY-MM-DD), so it lines up with the person's own midnight.
 */
async function memories(c: Ctx): Promise<Response> {
  const coupleId = requireCouple(c.user);
  const today = c.url.searchParams.get("date");
  if (!isDate(today)) throw new HttpError(400, "date must be YYYY-MM-DD");
  const y = parseInt(today.slice(0, 4), 10), m = parseInt(today.slice(5, 7), 10);
  const pad = (n: number) => String(n).padStart(2, "0");
  const thisStart = `${y}-${pad(m)}-01`;
  const lastStart = m === 1 ? `${y - 1}-12-01` : `${y}-${pad(m - 1)}-01`;
  const E = "COALESCE(entry_date, date(created_at/1000,'unixepoch'))";
  const years = (d: string) => y - parseInt(d.slice(0, 4), 10);
  const db = c.env.DB;
  const [dayD, dayE, lastD, lastE, monthBest, ever] = await Promise.all([
    db.prepare(`SELECT *, ${E} AS d FROM diary WHERE couple_id = ?1 AND substr(${E},6) = ?2 AND ${E} < ?3 ORDER BY d DESC LIMIT 10`).bind(coupleId, today.slice(5), today).all(),
    db.prepare("SELECT * FROM events WHERE couple_id = ?1 AND substr(date,6) = ?2 AND date < ?3 ORDER BY date DESC LIMIT 10").bind(coupleId, today.slice(5), today).all(),
    db.prepare(`SELECT *, ${E} AS d FROM diary WHERE couple_id = ?1 AND ${E} >= ?2 AND ${E} < ?3 ORDER BY COALESCE(rating,0) DESC, d DESC LIMIT 6`).bind(coupleId, lastStart, thisStart).all(),
    db.prepare("SELECT * FROM events WHERE couple_id = ?1 AND date >= ?2 AND date < ?3 ORDER BY date DESC LIMIT 6").bind(coupleId, lastStart, thisStart).all(),
    db.prepare(`SELECT *, ${E} AS d FROM diary WHERE couple_id = ?1 AND rating IS NOT NULL AND ${E} >= ?2 AND ${E} <= ?3 ORDER BY rating DESC, d DESC LIMIT 3`).bind(coupleId, thisStart, today).all(),
    db.prepare(`SELECT *, ${E} AS d FROM diary WHERE couple_id = ?1 AND rating >= 4 ORDER BY rating DESC, d DESC LIMIT 5`).bind(coupleId).all(),
  ]);
  const diary = async (rows: any[], withYears: boolean) => {
    const list = await withImages(c.env, rows);
    return list.map((e, i) => ({ kind: "diary", date: rows[i].d as string, yearsAgo: withYears ? years(rows[i].d) : 0, diary: e, event: null }));
  };
  const events = (rows: any[], withYears: boolean) =>
    rows.map((r) => ({ kind: "event", date: r.date as string, yearsAgo: withYears ? years(r.date) : 0, diary: null, event: eventJson(r) }));
  const byDate = (a: any, b: any) => (a.date < b.date ? 1 : -1);
  return json({
    onThisDay: [...(await diary(dayD.results as any[], true)), ...events(dayE.results as any[], true)].sort(byDate),
    lastMonth: [...(await diary(lastD.results as any[], false)), ...events(lastE.results as any[], false)],
    bestOfMonth: await diary(monthBest.results as any[], false),
    bestEver: await diary(ever.results as any[], true),
  });
}

/** Erases the caller's own data and account. The partner (if any) is unpaired and keeps their own data. */
async function accountDelete(c: Ctx): Promise<Response> {
  const b = await readJson(c.req);
  const password = str(b.password, "password", 1, 128);
  const ukey = `login:u:${c.user.username.toLowerCase()}`;
  if ((await attempts(c.env, ukey)) >= 8) throw tooMany();
  const hash = await hashPassword(password, b64uDecode(c.user.pass_salt));
  if (!timingSafeEqual(hash, c.user.pass_hash)) { await bump(c.env, ukey, 15 * MIN); throw new HttpError(401, "Wrong password"); }
  const id = c.user.id;
  const stmts = [];
  if (c.user.couple_id) {
    stmts.push(
      c.env.DB.prepare("UPDATE users SET couple_id = NULL WHERE couple_id = ?").bind(c.user.couple_id),
      c.env.DB.prepare("UPDATE couples SET status = 'ended', ended_at = ?, invite_code = NULL WHERE id = ?").bind(now(), c.user.couple_id),
    );
  }
  stmts.push(
    c.env.DB.prepare("DELETE FROM diary_images WHERE user_id = ?").bind(id),
    c.env.DB.prepare("DELETE FROM diary WHERE user_id = ?").bind(id),
    c.env.DB.prepare("DELETE FROM moods WHERE user_id = ?").bind(id),
    c.env.DB.prepare("DELETE FROM events WHERE created_by = ?").bind(id),
    c.env.DB.prepare("DELETE FROM users WHERE id = ?").bind(id),
  );
  await c.env.DB.batch(stmts);
  return json({ ok: true });
}

// ------------------------------------------------------------- router
type Handler = (c: Ctx) => Promise<Response>;
const routes: { method: string; re: RegExp; h: Handler }[] = [];
const route = (method: string, path: string, h: Handler) =>
  routes.push({ method, re: new RegExp("^" + path.replace(/:\w+/g, "([^/]+)") + "$"), h });

route("GET", "/me", async (c) => json({ user: publicUser(c.user), couple: await coupleInfo(c.env, c.user) }));
route("PATCH", "/me", profileUpdate);
route("DELETE", "/me", accountDelete);
route("PUT", "/me/avatar", avatarPut);
route("GET", "/avatar/:id", avatarGet);
route("POST", "/couple", coupleCreate);
route("POST", "/couple/join", coupleJoin);
route("POST", "/couple/leave", coupleLeave);
route("PATCH", "/couple", coupleUpdate);
route("POST", "/moods", moodPost);
route("GET", "/moods", moodList);
route("POST", "/diary", diaryPost);
route("GET", "/diary", diaryList);
route("PUT", "/diary/:id", diaryPut);
route("DELETE", "/diary/:id", diaryDelete);
route("DELETE", "/diary/image/:id", diaryImageDelete);
route("POST", "/diary/:id/images", diaryImagePost);
route("GET", "/diary/image/:id", diaryImageGet);
route("GET", "/events", eventList);
route("POST", "/events", eventPost);
route("PUT", "/events/:id", eventPut);
route("DELETE", "/events/:id", eventDelete);
route("GET", "/sync", sync);
route("GET", "/stats", statsDetail);
route("GET", "/memories", memories);

export default {
  async fetch(req: Request, env: Env): Promise<Response> {
    if (req.method === "OPTIONS") return new Response(null, { status: 204, headers: SEC });
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
      if (!user) throw new HttpError(401, "Please sign in again");

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
