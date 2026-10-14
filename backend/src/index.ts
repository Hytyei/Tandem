// Tandem API — Cloudflare Worker + D1. No external dependencies.

export interface Env {
  DB: D1Database;
  JWT_SECRET: string;
}

const MOODS = ["happy", "loved", "calm", "excited", "tired", "sad", "anxious", "angry"];
const ACTIVITIES = [
  "gaming", "sleeping", "eating", "tv", "music", "football", "cycling",
  "hiking", "camping", "roadtrip", "stargazing", "photography", "hair", "nails",
  "working", "coding", "studying", "partying",
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
  profile?: string | null;
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
  let profile: unknown = {};
  try { profile = u.profile ? JSON.parse(u.profile) : {}; } catch { profile = {}; }
  return {
    id: u.id, username: u.username, displayName: u.display_name, bio: u.bio,
    avatarVersion: u.avatar_version, hasAvatar: u.avatar_version > 0, profile,
  };
}

// ------------------------------------------------------------- personal page (profile details)
const PROFILE_COLORS = ["red", "orange", "yellow", "green", "teal", "blue", "purple", "pink", "black", "white", "brown", "gray"];
const LOVE_LANGUAGES = ["words", "time", "gifts", "acts", "touch"];
const METERS = ["spicy", "hunger", "sleepy", "cuddle", "chaos"];
const optStr = (v: unknown, field: string, max: number): string => {
  if (v == null) return "";
  if (typeof v !== "string") throw new HttpError(400, `${field} must be text`);
  const t = v.trim();
  if (t.length > max) throw new HttpError(400, `${field} must be at most ${max} characters`);
  return t;
};
/** Validates the "my page" form and returns the JSON that gets stored. Only known keys survive. */
function cleanProfile(b: any, prevRaw: string | null | undefined): Record<string, unknown> {
  let prev: any = {};
  try { prev = prevRaw ? JSON.parse(prevRaw) : {}; } catch { prev = {}; }
  let birthday = "";
  if (b.birthday != null && b.birthday !== "") {
    if (!isDate(b.birthday) || b.birthday < "1900-01-01" || b.birthday > isoDay(now())) throw new HttpError(400, "birthday must be a past date (YYYY-MM-DD)");
    birthday = b.birthday;
  }
  const colors = Array.isArray(b.colors) ? [...new Set(b.colors.filter((x: unknown) => typeof x === "string" && PROFILE_COLORS.includes(x)))].slice(0, 3) : [];
  const hobbies = Array.isArray(b.hobbies)
    ? b.hobbies.filter((x: unknown): x is string => typeof x === "string").map((x: string) => x.trim()).filter((x: string) => x.length > 0 && x.length <= 24).slice(0, 8)
    : [];
  const meters: Record<string, number> = {};
  if (b.meters && typeof b.meters === "object") {
    for (const k of METERS) {
      const v = (b.meters as any)[k];
      if (typeof v === "number" && isFinite(v)) meters[k] = Math.min(100, Math.max(0, Math.round(v)));
    }
  }
  const p: Record<string, unknown> = {
    fullName: optStr(b.fullName, "fullName", 60), birthday, colors, hobbies,
    sport: optStr(b.sport, "sport", 40), food: optStr(b.food, "food", 40), music: optStr(b.music, "music", 40),
    loveLanguage: LOVE_LANGUAGES.includes(b.loveLanguage) ? b.loveLanguage : "",
    watching: optStr(b.watching, "watching", 60), playing: optStr(b.playing, "playing", 60),
    listening: optStr(b.listening, "listening", 60), reading: optStr(b.reading, "reading", 60),
    meters,
  };
  const nowChanged = ["watching", "playing", "listening", "reading"].some((k) => (prev[k] ?? "") !== p[k]);
  p.nowAt = nowChanged ? now() : (prev.nowAt ?? 0);
  const metersChanged = JSON.stringify(prev.meters ?? {}) !== JSON.stringify(meters);
  p.metersAt = metersChanged ? now() : (prev.metersAt ?? 0);
  return p;
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

function moodJson(r: any, reactions: Reaction[] = []) {
  return { id: r.id, userId: r.user_id, mood: r.mood, activity: r.activity, note: r.note, createdAt: r.created_at, reactions };
}

// ------------------------------------------------------------- reactions (one emoji per person per mood / diary note)
const REACTIONS = ["😂", "🤣", "😭", "🥲", "😐", "🫠", "😑", "🤔", "🐥", "🥹", "🥺", "🔥", "🥳", "📿"];
type Reaction = { userId: string; emoji: string };
async function reactionMap(env: Env, type: "mood" | "diary", ids: string[]): Promise<Record<string, Reaction[]>> {
  const map: Record<string, Reaction[]> = {};
  for (let i = 0; i < ids.length; i += 50) {
    const chunk = ids.slice(i, i + 50);
    const { results } = await env.DB.prepare(
      `SELECT target_id, user_id, emoji FROM reactions WHERE target_type = ? AND target_id IN (${chunk.map(() => "?").join(",")}) ORDER BY created_at`
    ).bind(type, ...chunk).all();
    for (const r of results as any[]) (map[r.target_id] ??= []).push({ userId: r.user_id, emoji: r.emoji });
  }
  return map;
}
async function moodsWithReactions(env: Env, rows: any[]) {
  const map = await reactionMap(env, "mood", rows.map((r) => r.id));
  return rows.map((r) => moodJson(r, map[r.id] ?? []));
}
/** PUT /reactions { type: "mood"|"diary", id, emoji | null } — react to your partner's post (null removes your reaction). */
async function reactionPut(c: Ctx): Promise<Response> {
  const coupleId = requireCouple(c.user);
  const b = await readJson(c.req);
  if (b.type !== "mood" && b.type !== "diary") throw new HttpError(400, "type must be mood or diary");
  const id = str(b.id, "id", 1, 64);
  const row = await c.env.DB.prepare(`SELECT couple_id, user_id FROM ${b.type === "mood" ? "moods" : "diary"} WHERE id = ?`).bind(id).first<any>();
  if (!row || row.couple_id !== coupleId) throw new HttpError(404, "That post doesn't exist any more");
  if (row.user_id === c.user.id) throw new HttpError(403, "You can only react to your partner's posts");
  if (b.emoji == null) {
    await c.env.DB.prepare("DELETE FROM reactions WHERE target_type = ? AND target_id = ? AND user_id = ?").bind(b.type, id, c.user.id).run();
  } else {
    if (typeof b.emoji !== "string" || !REACTIONS.includes(b.emoji)) throw new HttpError(400, "That reaction isn't available");
    await c.env.DB.prepare(
      "INSERT INTO reactions (target_type, target_id, user_id, emoji, created_at) VALUES (?,?,?,?,?) " +
      "ON CONFLICT(target_type, target_id, user_id) DO UPDATE SET emoji = excluded.emoji, created_at = excluded.created_at"
    ).bind(b.type, id, c.user.id, b.emoji, now()).run();
  }
  return json({ reactions: (await reactionMap(c.env, b.type, [id]))[id] ?? [] });
}
const isoDay = (ms: number) => new Date(ms).toISOString().slice(0, 10);
function diaryJson(r: any, images: string[] = [], reactions: Reaction[] = []) {
  return {
    reactions,
    id: r.id, userId: r.user_id, text: r.text, mood: r.mood, createdAt: r.created_at,
    entryDate: r.entry_date ?? isoDay(r.created_at), images,
    rating: r.rating ?? null, dateType: r.date_type ?? null, updatedAt: r.updated_at ?? 0,
    placeId: r.place_id ?? null, placeName: r.place_name ?? null,
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
  const placeIds = [...new Set(rows.map((r) => r.place_id).filter((x) => !!x))] as string[];
  const names: Record<string, string> = {};
  if (placeIds.length) {
    const { results } = await env.DB.prepare(`SELECT id, name FROM places WHERE id IN (${placeIds.map(() => "?").join(",")})`).bind(...placeIds).all();
    for (const r of results as any[]) names[r.id] = r.name;
  }
  const reacts = await reactionMap(env, "diary", ids);
  return rows.map((r) => diaryJson({ ...r, place_name: r.place_id ? names[r.place_id] ?? null : null }, map[r.id] ?? [], reacts[r.id] ?? []));
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

async function profilePut(c: Ctx): Promise<Response> {
  const b = await readJson(c.req);
  const p = cleanProfile(b, c.user.profile);
  await c.env.DB.prepare("UPDATE users SET profile = ? WHERE id = ?").bind(JSON.stringify(p), c.user.id).run();
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
  return json({ moods: await moodsWithReactions(c.env, results as any[]) });
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
  const placeId = await readPlaceId(c.env, coupleId, b.placeId);
  const id = uid();
  await c.env.DB.prepare("INSERT INTO diary (id, user_id, couple_id, text, mood, entry_date, rating, date_type, place_id, created_at) VALUES (?,?,?,?,?,?,?,?,?,?)")
    .bind(id, c.user.id, coupleId, text, mood, entryDate, rating, dtype, placeId, t).run();
  return json((await withImages(c.env, [{ id, user_id: c.user.id, text, mood, entry_date: entryDate, rating, date_type: dtype, place_id: placeId, created_at: t }]))[0], 201);
}

/** A place id sent with a note must belong to the same couple (null/"" = no place). */
async function readPlaceId(env: Env, coupleId: string, v: unknown): Promise<string | null> {
  if (v == null || v === "") return null;
  if (typeof v !== "string") throw new HttpError(400, "placeId must be text");
  const ok = await env.DB.prepare("SELECT 1 FROM places WHERE id = ? AND couple_id = ?").bind(v, coupleId).first();
  if (!ok) throw new HttpError(400, "Unknown place");
  return v;
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
  const placeId = b.placeId === undefined ? row.place_id ?? null : await readPlaceId(c.env, coupleId, b.placeId);
  const t = now();
  await c.env.DB.prepare("UPDATE diary SET text=?, mood=?, entry_date=?, rating=?, date_type=?, place_id=?, updated_at=? WHERE id=? AND couple_id=? AND user_id=?")
    .bind(text, mood, entryDate, rating, dtype, placeId, t, c.params[0], coupleId, c.user.id).run();
  const updated = { ...row, text, mood, entry_date: entryDate, rating, date_type: dtype, place_id: placeId, updated_at: t };
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
    c.env.DB.prepare("DELETE FROM reactions WHERE target_type = 'diary' AND target_id = ?").bind(c.params[0]),
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
  const latestWithReactions = await moodsWithReactions(c.env, latest.results as any[]);
  for (const m of latestWithReactions) latestMoods[m.userId] = m;

  return json({
    ...base,
    moods: await moodsWithReactions(c.env, moods.results as any[]),
    latestMoods,
    diary: await withImages(c.env, diary.results as any[]),
    events: events.results.map(eventJson),
    stats,
  });
}

// ------------------------------------------------------------- places (heatmap) + reviews
const PLACE_CATEGORIES = ["restaurant", "takeout", "entertainment", "nature", "culture", "shopping", "nightlife", "stay", "sights", "special", "other"];
const MAX_PLACES = 300;
function placeJson(r: any, reviews: any[]) {
  const rs = reviews.map((x) => ({ userId: x.user_id, rating: x.rating, comment: x.comment, updatedAt: x.updated_at }));
  const avg = rs.length ? Math.round((rs.reduce((a, x) => a + x.rating, 0) / rs.length) * 100) / 100 : null;
  return {
    id: r.id, createdBy: r.created_by, name: r.name, category: r.category, lat: r.lat, lon: r.lon, note: r.note,
    visits: r.visits, firstVisited: r.first_visited ?? null, lastVisited: r.last_visited ?? null,
    createdAt: r.created_at, updatedAt: r.updated_at ?? 0, reviews: rs, avg, count: rs.length,
  };
}
async function loadPlaces(env: Env, coupleId: string, onlyId?: string) {
  const places = onlyId
    ? await env.DB.prepare("SELECT * FROM places WHERE couple_id = ? AND id = ?").bind(coupleId, onlyId).all()
    : await env.DB.prepare("SELECT * FROM places WHERE couple_id = ? ORDER BY created_at DESC, rowid DESC").bind(coupleId).all();
  const rev = onlyId
    ? await env.DB.prepare("SELECT * FROM place_reviews WHERE couple_id = ? AND place_id = ? ORDER BY updated_at").bind(coupleId, onlyId).all()
    : await env.DB.prepare("SELECT * FROM place_reviews WHERE couple_id = ? ORDER BY updated_at").bind(coupleId).all();
  const by: Record<string, any[]> = {};
  for (const r of rev.results as any[]) (by[r.place_id] ??= []).push(r);
  return (places.results as any[]).map((p) => placeJson(p, by[p.id] ?? []));
}
function readPlace(b: any, partial: boolean) {
  const out: Record<string, any> = {};
  if (!partial || b.name !== undefined) out.name = str(b.name, "name", 1, 80);
  if (!partial || b.category !== undefined) out.category = PLACE_CATEGORIES.includes(b.category) ? b.category : "other";
  if (!partial || b.lat !== undefined || b.lon !== undefined) {
    if (typeof b.lat !== "number" || typeof b.lon !== "number" || !isFinite(b.lat) || !isFinite(b.lon) || Math.abs(b.lat) > 90 || Math.abs(b.lon) > 180)
      throw new HttpError(400, "lat/lon must be valid coordinates");
    out.lat = Math.round(b.lat * 1e6) / 1e6; out.lon = Math.round(b.lon * 1e6) / 1e6;
  }
  if (!partial || b.note !== undefined) out.note = b.note == null ? "" : str(b.note, "note", 0, 300);
  return out;
}
const dayOrToday = (v: unknown) => (isDate(v) && v <= isoDay(now() + 36 * 3600_000) ? (v as string) : isoDay(now()));

async function placesList(c: Ctx): Promise<Response> {
  return json({ places: await loadPlaces(c.env, requireCouple(c.user)) });
}
async function placePost(c: Ctx): Promise<Response> {
  const coupleId = requireCouple(c.user);
  const b = await readJson(c.req);
  const p = readPlace(b, false);
  const n = await c.env.DB.prepare("SELECT COUNT(*) AS n FROM places WHERE couple_id = ?").bind(coupleId).first<{ n: number }>();
  if ((n?.n ?? 0) >= MAX_PLACES) throw new HttpError(409, "Too many places. Delete some old ones first.");
  const id = uid(), t = now(), day = dayOrToday(b.visitDate);
  await c.env.DB.prepare(
    "INSERT INTO places (id, couple_id, created_by, name, category, lat, lon, note, visits, first_visited, last_visited, created_at, updated_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)"
  ).bind(id, coupleId, c.user.id, p.name, p.category, p.lat, p.lon, p.note, 1, day, day, t, t).run();
  return json((await loadPlaces(c.env, coupleId, id))[0], 201);
}
async function placeOwned(c: Ctx): Promise<any> {
  const coupleId = requireCouple(c.user);
  const row = await c.env.DB.prepare("SELECT * FROM places WHERE id = ? AND couple_id = ?").bind(c.params[0], coupleId).first<any>();
  if (!row) throw new HttpError(404, "Place not found");
  return row;
}
async function placePut(c: Ctx): Promise<Response> {
  const row = await placeOwned(c);
  const b = await readJson(c.req);
  const p = readPlace(b, true);
  const merged = { ...row, ...p };
  let visits = row.visits;
  if (b.visits !== undefined) {
    if (!Number.isInteger(b.visits) || b.visits < 1 || b.visits > 999) throw new HttpError(400, "visits must be 1 to 999");
    visits = b.visits;
  }
  await c.env.DB.prepare("UPDATE places SET name=?, category=?, lat=?, lon=?, note=?, visits=?, updated_at=? WHERE id=? AND couple_id=?")
    .bind(merged.name, merged.category, merged.lat, merged.lon, merged.note, visits, now(), row.id, row.couple_id).run();
  return json((await loadPlaces(c.env, row.couple_id, row.id))[0]);
}
/** "We went again": one more visit. */
async function placeVisit(c: Ctx): Promise<Response> {
  const row = await placeOwned(c);
  const b = c.req.headers.get("content-length") === "0" ? {} : await readJson(c.req).catch(() => ({}));
  const day = dayOrToday((b as any).date);
  if (row.visits >= 999) throw new HttpError(409, "That is a lot of visits already");
  await c.env.DB.prepare("UPDATE places SET visits = visits + 1, last_visited = ?, first_visited = COALESCE(first_visited, ?), updated_at = ? WHERE id = ?")
    .bind(day > (row.last_visited ?? "") ? day : row.last_visited ?? day, day, now(), row.id).run();
  return json((await loadPlaces(c.env, row.couple_id, row.id))[0]);
}
async function placeDelete(c: Ctx): Promise<Response> {
  const row = await placeOwned(c);
  await c.env.DB.batch([
    c.env.DB.prepare("DELETE FROM place_reviews WHERE place_id = ?").bind(row.id),
    c.env.DB.prepare("UPDATE diary SET place_id = NULL WHERE place_id = ?").bind(row.id),
    c.env.DB.prepare("DELETE FROM places WHERE id = ?").bind(row.id),
  ]);
  return json({ ok: true });
}
/** Your own review of a place (1-5, optional comment). `rating: null` removes it. */
async function placeReview(c: Ctx): Promise<Response> {
  const row = await placeOwned(c);
  const b = await readJson(c.req);
  if (b.rating == null) {
    await c.env.DB.prepare("DELETE FROM place_reviews WHERE place_id = ? AND user_id = ?").bind(row.id, c.user.id).run();
  } else {
    if (typeof b.rating !== "number" || !Number.isInteger(b.rating) || b.rating < 1 || b.rating > 5) throw new HttpError(400, "rating must be 1 to 5");
    const comment = b.comment == null ? "" : str(b.comment, "comment", 0, 200);
    await c.env.DB.prepare(
      "INSERT INTO place_reviews (place_id, couple_id, user_id, rating, comment, updated_at) VALUES (?,?,?,?,?,?) " +
      "ON CONFLICT(place_id, user_id) DO UPDATE SET rating = excluded.rating, comment = excluded.comment, updated_at = excluded.updated_at"
    ).bind(row.id, row.couple_id, c.user.id, b.rating, comment, now()).run();
  }
  return json((await loadPlaces(c.env, row.couple_id, row.id))[0]);
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
      "(SELECT COUNT(*) FROM diary WHERE couple_id = ?1 AND rating IS NOT NULL) AS rated, (SELECT COUNT(*) FROM diary WHERE couple_id = ?1 AND rating = 5) AS fiveStar, " +
      "(SELECT COUNT(*) FROM places WHERE couple_id = ?1) AS places, (SELECT COUNT(*) FROM place_reviews WHERE couple_id = ?1) AS placeReviews"
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
    totals: { moods: totals?.moods ?? 0, diary: totals?.diary ?? 0, events: totals?.events ?? 0, specials: totals?.specials ?? 0, rated: totals?.rated ?? 0, fiveStar: totals?.fiveStar ?? 0, places: totals?.places ?? 0, placeReviews: totals?.placeReviews ?? 0 },
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


// ------------------------------------------------------------- minigames: turn-based timed matches, high scores, goals
// A match is N rounds of one game. Every round both players play the SAME seeded, timed run on their own phone, one after the other
// (the starter goes first in odd rounds, the partner in even rounds). Higher score wins the round; most rounds wins the match.
const GAMES: Record<string, { max: number }> = {
  heart: { max: 120 },    // Heart Catch: hearts tapped in 30 s
  memory: { max: 1500 },  // Memory Match: pairs + time bonus - mistakes
  math: { max: 1000 },    // Math Sprint: correct answers in 45 s
  draw: { max: 3000 },    // Quick Draw: reaction speed over 5 tries
};
const MATCH_ROUNDS = [1, 3, 5];
const MAX_ACTIVE_MATCHES = 6;
const MAX_OPEN_GOALS = 6;
const GOAL_METRICS = ["wins", "points"];

function matchJson(r: any, scores: any[]) {
  return {
    id: r.id, game: r.game, rounds: r.rounds, createdBy: r.created_by, status: r.status, winnerId: r.winner_id ?? null,
    seed: r.seed, createdAt: r.created_at, finishedAt: r.finished_at ?? 0,
    scores: scores.filter((s) => s.match_id === r.id).map((s) => ({ round: s.round, userId: s.user_id, score: s.score })),
  };
}
async function loadMatches(env: Env, coupleId: string, onlyId?: string) {
  const q = onlyId
    ? env.DB.prepare("SELECT * FROM game_matches WHERE couple_id = ? AND id = ?").bind(coupleId, onlyId)
    : env.DB.prepare(
        "SELECT * FROM (SELECT * FROM game_matches WHERE couple_id = ? AND status = 'active' UNION ALL " +
        "SELECT * FROM (SELECT * FROM game_matches WHERE couple_id = ? AND status = 'done' ORDER BY finished_at DESC LIMIT 20)) ORDER BY created_at DESC"
      ).bind(coupleId, coupleId);
  const { results } = await q.all();
  const rows = results as any[];
  const scores: any[] = [];
  for (let i = 0; i < rows.length; i += 50) {
    const chunk = rows.slice(i, i + 50);
    const res = await env.DB.prepare(`SELECT * FROM game_scores WHERE match_id IN (${chunk.map(() => "?").join(",")}) ORDER BY round, created_at`).bind(...chunk.map((r) => r.id)).all();
    scores.push(...(res.results as any[]));
  }
  return rows.map((r) => matchJson(r, scores));
}
function goalJson(r: any, progress: Record<string, number> | null) {
  return {
    id: r.id, title: r.title, metric: r.metric, target: r.target, status: r.status, winnerId: r.winner_id ?? null,
    createdBy: r.created_by, createdAt: r.created_at, wonAt: r.won_at ?? 0, settledAt: r.settled_at ?? 0, progress: progress ?? {},
  };
}
async function goalProgress(env: Env, coupleId: string, g: any, ids: string[]): Promise<Record<string, number>> {
  const out: Record<string, number> = {};
  for (const id of ids) out[id] = 0;
  if (g.metric === "wins") {
    const { results } = await env.DB.prepare(
      "SELECT winner_id, COUNT(*) AS n FROM game_matches WHERE couple_id = ? AND status = 'done' AND finished_at >= ? AND winner_id IS NOT NULL GROUP BY winner_id"
    ).bind(coupleId, g.created_at).all();
    for (const r of results as any[]) if (r.winner_id in out) out[r.winner_id] = r.n;
  } else {
    const { results } = await env.DB.prepare(
      "SELECT s.user_id, SUM(s.score) AS n FROM game_scores s JOIN game_matches m ON m.id = s.match_id WHERE m.couple_id = ? AND s.created_at >= ? GROUP BY s.user_id"
    ).bind(coupleId, g.created_at).all();
    for (const r of results as any[]) if (r.user_id in out) out[r.user_id] = r.n ?? 0;
  }
  return out;
}
/** Marks goals as won the first time somebody reaches the target (the higher one if both do at once). Safe to call any time. */
async function resolveGoals(env: Env, coupleId: string, ids: string[]) {
  const { results } = await env.DB.prepare("SELECT * FROM game_goals WHERE couple_id = ? AND status = 'active'").bind(coupleId).all();
  const out: Record<string, Record<string, number>> = {};
  for (const g of results as any[]) {
    const prog = await goalProgress(env, coupleId, g, ids);
    out[g.id] = prog;
    const reached = ids.filter((i) => prog[i] >= g.target).sort((a, b) => prog[b] - prog[a]);
    if (reached.length === 0) continue;
    if (reached.length > 1 && prog[reached[0]] === prog[reached[1]]) continue; // dead heat: keep playing
    await env.DB.prepare("UPDATE game_goals SET status = 'won', winner_id = ?, won_at = ?, result = ? WHERE id = ? AND status = 'active'")
      .bind(reached[0], now(), JSON.stringify(prog), g.id).run();
  }
  return out;
}
async function gamesSummary(c: Ctx): Promise<Response> {
  const coupleId = requireCouple(c.user);
  const partner = await partnerOf(c.env, c.user);
  const ids = [c.user.id, ...(partner ? [partner.id] : [])];
  const live = await resolveGoals(c.env, coupleId, ids);
  const [matches, goalsRes, scoreAgg, winAgg, order, highs] = await Promise.all([
    loadMatches(c.env, coupleId),
    c.env.DB.prepare("SELECT * FROM game_goals WHERE couple_id = ? ORDER BY CASE status WHEN 'active' THEN 0 WHEN 'won' THEN 1 ELSE 2 END, created_at DESC LIMIT 30").bind(coupleId).all(),
    c.env.DB.prepare(
      "SELECT m.game AS game, s.user_id AS user_id, MAX(s.score) AS best, COUNT(*) AS plays, SUM(s.score) AS total FROM game_scores s JOIN game_matches m ON m.id = s.match_id " +
      "WHERE m.couple_id = ? GROUP BY m.game, s.user_id"
    ).bind(coupleId).all(),
    c.env.DB.prepare("SELECT game, winner_id, COUNT(*) AS n FROM game_matches WHERE couple_id = ? AND status = 'done' GROUP BY game, winner_id").bind(coupleId).all(),
    c.env.DB.prepare("SELECT winner_id FROM game_matches WHERE couple_id = ? AND status = 'done' ORDER BY finished_at ASC, rowid ASC LIMIT 1000").bind(coupleId).all(),
    c.env.DB.prepare(
      "SELECT m.game AS game, s.user_id AS user_id, s.score AS score, s.created_at AS at FROM game_scores s JOIN game_matches m ON m.id = s.match_id " +
      "WHERE m.couple_id = ? ORDER BY s.score DESC, s.created_at ASC LIMIT 400"
    ).bind(coupleId).all(),
  ]);
  const goals = [];
  for (const g of goalsRes.results as any[]) {
    let prog: Record<string, number> | null = live[g.id] ?? null;
    if (!prog) { try { prog = g.result ? JSON.parse(g.result) : null; } catch { prog = null; } }
    goals.push(goalJson(g, prog));
  }
  // streaks
  const streak: Record<string, { cur: number; best: number }> = {};
  for (const id of ids) streak[id] = { cur: 0, best: 0 };
  for (const r of order.results as any[]) {
    for (const id of ids) {
      if (r.winner_id === id) { streak[id].cur++; streak[id].best = Math.max(streak[id].best, streak[id].cur); }
      else streak[id].cur = 0;
    }
  }
  const scoreRows = scoreAgg.results as any[];
  const winRows = winAgg.results as any[];
  const players = ids.map((id) => ({
    userId: id,
    wins: winRows.filter((w) => w.winner_id === id).reduce((a, w) => a + w.n, 0),
    points: scoreRows.filter((r) => r.user_id === id).reduce((a, r) => a + (r.total ?? 0), 0),
    rounds: scoreRows.filter((r) => r.user_id === id).reduce((a, r) => a + r.plays, 0),
    streak: streak[id].cur, bestStreak: streak[id].best,
  }));
  const perGameHighs: any[] = [];
  const counts: Record<string, number> = {};
  for (const h of highs.results as any[]) {
    counts[h.game] = (counts[h.game] ?? 0) + 1;
    if (counts[h.game] <= 5) perGameHighs.push({ game: h.game, userId: h.user_id, score: h.score, at: h.at });
  }
  return json({
    matches, goals,
    stats: {
      done: winRows.reduce((a, w) => a + w.n, 0),
      draws: winRows.filter((w) => w.winner_id == null).reduce((a, w) => a + w.n, 0),
      tandemScore: players.reduce((a, p) => a + p.points, 0),
      players,
      bests: scoreRows.map((r) => ({ game: r.game, userId: r.user_id, best: r.best, plays: r.plays, total: r.total ?? 0 })),
      gameWins: winRows.map((w) => ({ game: w.game, userId: w.winner_id ?? null, n: w.n })),
      highs: perGameHighs,
    },
  });
}
async function matchPost(c: Ctx): Promise<Response> {
  const coupleId = requireCouple(c.user);
  if (!(await partnerOf(c.env, c.user))) throw new HttpError(409, "Connect with your partner first");
  const b = await readJson(c.req);
  if (typeof b.game !== "string" || !(b.game in GAMES)) throw new HttpError(400, `game must be one of ${Object.keys(GAMES).join(", ")}`);
  if (!MATCH_ROUNDS.includes(b.rounds)) throw new HttpError(400, "rounds must be 1, 3 or 5");
  const n = await c.env.DB.prepare("SELECT COUNT(*) AS n FROM game_matches WHERE couple_id = ? AND status = 'active'").bind(coupleId).first<{ n: number }>();
  if ((n?.n ?? 0) >= MAX_ACTIVE_MATCHES) throw new HttpError(409, "You have a lot of unfinished matches. Finish or cancel one first.");
  const id = uid(), t = now();
  const seed = crypto.getRandomValues(new Uint32Array(1))[0] & 0x7fffffff;
  await c.env.DB.prepare("INSERT INTO game_matches (id, couple_id, game, rounds, created_by, status, seed, created_at) VALUES (?,?,?,?,?,'active',?,?)")
    .bind(id, coupleId, b.game, b.rounds, c.user.id, seed, t).run();
  return json((await loadMatches(c.env, coupleId, id))[0], 201);
}
async function matchScore(c: Ctx): Promise<Response> {
  const coupleId = requireCouple(c.user);
  const partner = await partnerOf(c.env, c.user);
  if (!partner) throw new HttpError(409, "Connect with your partner first");
  const m = await c.env.DB.prepare("SELECT * FROM game_matches WHERE id = ? AND couple_id = ?").bind(c.params[0], coupleId).first<any>();
  if (!m) throw new HttpError(404, "Match not found");
  if (m.status !== "active") throw new HttpError(409, "This match is already over");
  const b = await readJson(c.req);
  if (!Number.isInteger(b.round) || b.round < 1 || b.round > m.rounds) throw new HttpError(400, "Invalid round");
  const max = GAMES[m.game].max;
  if (!Number.isInteger(b.score) || b.score < 0 || b.score > max) throw new HttpError(400, `score must be 0 to ${max}`);
  const { results } = await c.env.DB.prepare("SELECT * FROM game_scores WHERE match_id = ?").bind(m.id).all();
  const scores = results as any[];
  const has = (round: number, user: string) => scores.some((s) => s.round === round && s.user_id === user);
  if (has(b.round, c.user.id)) throw new HttpError(409, "You already played this round");
  for (let r = 1; r < b.round; r++) if (!has(r, c.user.id) || !has(r, partner.id)) throw new HttpError(409, "Finish the earlier rounds first");
  const first = b.round % 2 === 1 ? m.created_by : (m.created_by === c.user.id ? partner.id : c.user.id);
  if (c.user.id !== first && !has(b.round, first)) throw new HttpError(409, "It's not your turn yet");
  const t = now();
  await c.env.DB.prepare("INSERT INTO game_scores (match_id, round, user_id, score, created_at) VALUES (?,?,?,?,?)").bind(m.id, b.round, c.user.id, b.score, t).run();
  scores.push({ match_id: m.id, round: b.round, user_id: c.user.id, score: b.score, created_at: t });
  if (scores.length === m.rounds * 2) {
    const wins: Record<string, number> = { [c.user.id]: 0, [partner.id]: 0 };
    const total: Record<string, number> = { [c.user.id]: 0, [partner.id]: 0 };
    for (let r = 1; r <= m.rounds; r++) {
      const a = scores.find((s) => s.round === r && s.user_id === c.user.id)!.score;
      const o = scores.find((s) => s.round === r && s.user_id === partner.id)!.score;
      total[c.user.id] += a; total[partner.id] += o;
      if (a > o) wins[c.user.id]++; else if (o > a) wins[partner.id]++;
    }
    let winner: string | null = null;
    if (wins[c.user.id] !== wins[partner.id]) winner = wins[c.user.id] > wins[partner.id] ? c.user.id : partner.id;
    else if (total[c.user.id] !== total[partner.id]) winner = total[c.user.id] > total[partner.id] ? c.user.id : partner.id;
    await c.env.DB.prepare("UPDATE game_matches SET status = 'done', winner_id = ?, finished_at = ? WHERE id = ?").bind(winner, t, m.id).run();
  }
  await resolveGoals(c.env, coupleId, [c.user.id, partner.id]);
  return json((await loadMatches(c.env, coupleId, m.id))[0]);
}
async function matchDelete(c: Ctx): Promise<Response> {
  const coupleId = requireCouple(c.user);
  const m = await c.env.DB.prepare("SELECT status FROM game_matches WHERE id = ? AND couple_id = ?").bind(c.params[0], coupleId).first<any>();
  if (!m) throw new HttpError(404, "Match not found");
  if (m.status !== "active") throw new HttpError(409, "Finished matches stay in your history");
  await c.env.DB.batch([
    c.env.DB.prepare("DELETE FROM game_scores WHERE match_id = ?").bind(c.params[0]),
    c.env.DB.prepare("DELETE FROM game_matches WHERE id = ?").bind(c.params[0]),
  ]);
  return json({ ok: true });
}
async function goalPost(c: Ctx): Promise<Response> {
  const coupleId = requireCouple(c.user);
  if (!(await partnerOf(c.env, c.user))) throw new HttpError(409, "Connect with your partner first");
  const b = await readJson(c.req);
  const title = str(b.title, "title", 1, 60);
  if (typeof b.metric !== "string" || !GOAL_METRICS.includes(b.metric)) throw new HttpError(400, "metric must be wins or points");
  const [lo, hi] = b.metric === "wins" ? [1, 50] : [100, 100000];
  if (!Number.isInteger(b.target) || b.target < lo || b.target > hi) throw new HttpError(400, `target must be ${lo} to ${hi}`);
  const n = await c.env.DB.prepare("SELECT COUNT(*) AS n FROM game_goals WHERE couple_id = ? AND status IN ('active','won')").bind(coupleId).first<{ n: number }>();
  if ((n?.n ?? 0) >= MAX_OPEN_GOALS) throw new HttpError(409, "Too many open goals. Settle or delete one first.");
  const id = uid();
  await c.env.DB.prepare("INSERT INTO game_goals (id, couple_id, created_by, title, metric, target, status, created_at) VALUES (?,?,?,?,?,?,'active',?)")
    .bind(id, coupleId, c.user.id, title, b.metric, b.target, now()).run();
  const row = await c.env.DB.prepare("SELECT * FROM game_goals WHERE id = ?").bind(id).first<any>();
  return json(goalJson(row, {}), 201);
}
/** "Paid up!": the goal was won and the loser has delivered the lunch. */
async function goalSettle(c: Ctx): Promise<Response> {
  const coupleId = requireCouple(c.user);
  const g = await c.env.DB.prepare("SELECT * FROM game_goals WHERE id = ? AND couple_id = ?").bind(c.params[0], coupleId).first<any>();
  if (!g) throw new HttpError(404, "Goal not found");
  if (g.status !== "won") throw new HttpError(409, "This goal hasn't been won yet");
  await c.env.DB.prepare("UPDATE game_goals SET status = 'settled', settled_at = ? WHERE id = ?").bind(now(), g.id).run();
  const row = await c.env.DB.prepare("SELECT * FROM game_goals WHERE id = ?").bind(g.id).first<any>();
  let prog = null; try { prog = row.result ? JSON.parse(row.result) : null; } catch { /* ignore */ }
  return json(goalJson(row, prog));
}
async function goalDelete(c: Ctx): Promise<Response> {
  const coupleId = requireCouple(c.user);
  const r = await c.env.DB.prepare("DELETE FROM game_goals WHERE id = ? AND couple_id = ?").bind(c.params[0], coupleId).run();
  if (!r.meta.changes) throw new HttpError(404, "Goal not found");
  return json({ ok: true });
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
    c.env.DB.prepare("DELETE FROM reactions WHERE user_id = ? OR (target_type = 'diary' AND target_id IN (SELECT id FROM diary WHERE user_id = ?)) OR (target_type = 'mood' AND target_id IN (SELECT id FROM moods WHERE user_id = ?))").bind(id, id, id),
    c.env.DB.prepare("DELETE FROM diary_images WHERE user_id = ?").bind(id),
    c.env.DB.prepare("DELETE FROM diary WHERE user_id = ?").bind(id),
    c.env.DB.prepare("DELETE FROM game_scores WHERE user_id = ?").bind(id),
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
route("PUT", "/me/profile", profilePut);
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
route("GET", "/places", placesList);
route("POST", "/places", placePost);
route("PUT", "/places/:id", placePut);
route("DELETE", "/places/:id", placeDelete);
route("POST", "/places/:id/visit", placeVisit);
route("PUT", "/places/:id/review", placeReview);
route("PUT", "/reactions", reactionPut);
route("GET", "/games", gamesSummary);
route("POST", "/games/matches", matchPost);
route("PUT", "/games/matches/:id/score", matchScore);
route("DELETE", "/games/matches/:id", matchDelete);
route("POST", "/games/goals", goalPost);
route("PUT", "/games/goals/:id/settle", goalSettle);
route("DELETE", "/games/goals/:id", goalDelete);

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
