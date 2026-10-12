CREATE TABLE IF NOT EXISTS users (
  id TEXT PRIMARY KEY,
  username TEXT NOT NULL UNIQUE COLLATE NOCASE,
  display_name TEXT NOT NULL,
  bio TEXT NOT NULL DEFAULT '',
  pass_hash TEXT NOT NULL,
  pass_salt TEXT NOT NULL,
  avatar BLOB,
  avatar_version INTEGER NOT NULL DEFAULT 0,
  couple_id TEXT,
  created_at INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS couples (
  id TEXT PRIMARY KEY,
  invite_code TEXT UNIQUE,
  anniversary TEXT,           -- YYYY-MM-DD, the day the relationship started
  created_at INTEGER NOT NULL,
  status TEXT NOT NULL DEFAULT 'active',   -- 'active' | 'ended' (history kept after a partner is removed)
  user_a TEXT,                -- the two members, sorted by id
  user_b TEXT,
  ended_at INTEGER
);
CREATE INDEX IF NOT EXISTS idx_couples_pair ON couples(user_a, user_b, status);

CREATE TABLE IF NOT EXISTS moods (
  id TEXT PRIMARY KEY,
  user_id TEXT NOT NULL,
  couple_id TEXT NOT NULL,
  mood TEXT NOT NULL,         -- happy, loved, ...
  activity TEXT,              -- gaming, working, ... (nullable)
  note TEXT NOT NULL DEFAULT '',
  created_at INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_moods_couple ON moods(couple_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_moods_user ON moods(user_id, created_at DESC);

CREATE TABLE IF NOT EXISTS diary (
  id TEXT PRIMARY KEY,
  user_id TEXT NOT NULL,
  couple_id TEXT NOT NULL,
  text TEXT NOT NULL,
  mood TEXT,
  created_at INTEGER NOT NULL,
  entry_date TEXT             -- YYYY-MM-DD, NULL = day it was written
);
CREATE TABLE IF NOT EXISTS diary_images (
  id TEXT PRIMARY KEY,
  diary_id TEXT NOT NULL,
  couple_id TEXT NOT NULL,
  user_id TEXT NOT NULL,
  data BLOB NOT NULL,
  created_at INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_diary_images_diary ON diary_images(diary_id);
CREATE INDEX IF NOT EXISTS idx_diary_images_couple ON diary_images(couple_id);
CREATE INDEX IF NOT EXISTS idx_diary_couple ON diary(couple_id, created_at DESC);

CREATE TABLE IF NOT EXISTS events (
  id TEXT PRIMARY KEY,
  couple_id TEXT NOT NULL,
  created_by TEXT NOT NULL,
  title TEXT NOT NULL,
  emoji TEXT NOT NULL DEFAULT '❤️',
  date TEXT NOT NULL,         -- YYYY-MM-DD
  time TEXT,                  -- HH:mm (nullable = all day)
  repeat_yearly INTEGER NOT NULL DEFAULT 0,
  notes TEXT NOT NULL DEFAULT '',
  created_at INTEGER NOT NULL,
  kind TEXT NOT NULL DEFAULT 'event',      -- 'event' | 'special'
  category TEXT,
  updated_at INTEGER NOT NULL DEFAULT 0,
  updated_by TEXT
);
CREATE INDEX IF NOT EXISTS idx_events_couple ON events(couple_id, date);

CREATE TABLE IF NOT EXISTS auth_attempts (
  key TEXT PRIMARY KEY,
  count INTEGER NOT NULL,
  reset_at INTEGER NOT NULL
);
