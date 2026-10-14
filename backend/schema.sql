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
  created_at INTEGER NOT NULL,
  profile TEXT                -- JSON: "my page" details (full name, birthday, favourites, now, meters)
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
  entry_date TEXT,            -- YYYY-MM-DD, NULL = day it was written
  rating INTEGER,             -- 1..5 hearts, NULL = not rated
  date_type TEXT,             -- datenight, movie, gaming, ...
  updated_at INTEGER,         -- set when the note is edited
  place_id TEXT               -- places.id this memory happened at
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
  updated_by TEXT,
  date_type TEXT              -- datenight, movie, gaming, ...
);
CREATE INDEX IF NOT EXISTS idx_events_couple ON events(couple_id, date);

CREATE TABLE IF NOT EXISTS auth_attempts (
  key TEXT PRIMARY KEY,
  count INTEGER NOT NULL,
  reset_at INTEGER NOT NULL
);

-- Heatmap: places you've been to, and each partner's review of them
CREATE TABLE IF NOT EXISTS places (
  id TEXT PRIMARY KEY,
  couple_id TEXT NOT NULL,
  created_by TEXT NOT NULL,
  name TEXT NOT NULL,
  category TEXT NOT NULL DEFAULT 'other',
  lat REAL NOT NULL,
  lon REAL NOT NULL,
  note TEXT NOT NULL DEFAULT '',
  visits INTEGER NOT NULL DEFAULT 1,
  first_visited TEXT,
  last_visited TEXT,
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_places_couple ON places(couple_id);
CREATE TABLE IF NOT EXISTS place_reviews (
  place_id TEXT NOT NULL,
  couple_id TEXT NOT NULL,
  user_id TEXT NOT NULL,
  rating INTEGER NOT NULL,
  comment TEXT NOT NULL DEFAULT '',
  updated_at INTEGER NOT NULL,
  PRIMARY KEY (place_id, user_id)
);
CREATE INDEX IF NOT EXISTS idx_place_reviews_couple ON place_reviews(couple_id);

CREATE TABLE IF NOT EXISTS reactions (
  target_type TEXT NOT NULL,          -- 'mood' | 'diary'
  target_id TEXT NOT NULL,
  user_id TEXT NOT NULL,
  emoji TEXT NOT NULL,
  created_at INTEGER NOT NULL,
  PRIMARY KEY (target_type, target_id, user_id)
);

CREATE TABLE IF NOT EXISTS game_matches (
  id TEXT PRIMARY KEY,
  couple_id TEXT NOT NULL,
  game TEXT NOT NULL,
  rounds INTEGER NOT NULL,
  created_by TEXT NOT NULL,
  status TEXT NOT NULL DEFAULT 'active',   -- active | done
  winner_id TEXT,
  seed INTEGER NOT NULL,
  created_at INTEGER NOT NULL,
  finished_at INTEGER
);
CREATE INDEX IF NOT EXISTS idx_game_matches_couple ON game_matches(couple_id, status);

CREATE TABLE IF NOT EXISTS game_scores (
  match_id TEXT NOT NULL,
  round INTEGER NOT NULL,
  user_id TEXT NOT NULL,
  score INTEGER NOT NULL,
  created_at INTEGER NOT NULL,
  PRIMARY KEY (match_id, round, user_id)
);

CREATE TABLE IF NOT EXISTS game_goals (
  id TEXT PRIMARY KEY,
  couple_id TEXT NOT NULL,
  created_by TEXT NOT NULL,
  title TEXT NOT NULL,
  metric TEXT NOT NULL,                    -- wins | points
  target INTEGER NOT NULL,
  status TEXT NOT NULL DEFAULT 'active',   -- active | won | settled
  winner_id TEXT,
  result TEXT,                             -- JSON progress snapshot when won
  created_at INTEGER NOT NULL,
  won_at INTEGER,
  settled_at INTEGER
);
CREATE INDEX IF NOT EXISTS idx_game_goals_couple ON game_goals(couple_id);
