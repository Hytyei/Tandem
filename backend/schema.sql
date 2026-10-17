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

-- ---- update 4: place pictures, saved addresses, shared notes, live-tracked trips ----
-- Pictures attached to a place (JPEG, resized by the app, max 450 KB each)
CREATE TABLE IF NOT EXISTS place_images (
  id TEXT PRIMARY KEY,
  place_id TEXT NOT NULL,
  couple_id TEXT NOT NULL,
  user_id TEXT NOT NULL,
  data BLOB NOT NULL,
  created_at INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_place_images_place ON place_images(place_id);
CREATE INDEX IF NOT EXISTS idx_place_images_couple ON place_images(couple_id);

-- Saved addresses ("our homes"): each person's own labelled spots, shown on the shared map
CREATE TABLE IF NOT EXISTS saved_addresses (
  id TEXT PRIMARY KEY,
  couple_id TEXT NOT NULL,
  user_id TEXT NOT NULL,
  label TEXT NOT NULL,
  kind TEXT NOT NULL DEFAULT 'home',     -- home | work | family | friends | other
  address TEXT NOT NULL DEFAULT '',
  lat REAL NOT NULL,
  lon REAL NOT NULL,
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_saved_addresses_couple ON saved_addresses(couple_id);

-- Shared notes and checklists in the Calendar tab
CREATE TABLE IF NOT EXISTS notes (
  id TEXT PRIMARY KEY,
  couple_id TEXT NOT NULL,
  created_by TEXT NOT NULL,
  title TEXT NOT NULL,
  body TEXT NOT NULL DEFAULT '',
  kind TEXT NOT NULL DEFAULT 'note',     -- note | list
  note_date TEXT,                        -- optional YYYY-MM-DD this note belongs to
  pinned INTEGER NOT NULL DEFAULT 0,
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL,
  updated_by TEXT
);
CREATE INDEX IF NOT EXISTS idx_notes_couple ON notes(couple_id, updated_at DESC);

CREATE TABLE IF NOT EXISTS note_items (
  id TEXT PRIMARY KEY,
  note_id TEXT NOT NULL,
  couple_id TEXT NOT NULL,
  text TEXT NOT NULL,
  done INTEGER NOT NULL DEFAULT 0,
  position INTEGER NOT NULL DEFAULT 0,
  done_by TEXT,
  updated_at INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_note_items_note ON note_items(note_id);

-- Live-tracked trips ("we're on a date"): started on purpose, recorded only on phones that joined
CREATE TABLE IF NOT EXISTS trips (
  id TEXT PRIMARY KEY,
  couple_id TEXT NOT NULL,
  started_by TEXT NOT NULL,
  title TEXT NOT NULL,
  status TEXT NOT NULL DEFAULT 'active', -- active | done
  started_at INTEGER NOT NULL,
  ended_at INTEGER,
  distance_m REAL NOT NULL DEFAULT 0     -- the longer of the two recorded paths
);
CREATE INDEX IF NOT EXISTS idx_trips_couple ON trips(couple_id, started_at DESC);

CREATE TABLE IF NOT EXISTS trip_members (
  trip_id TEXT NOT NULL,
  user_id TEXT NOT NULL,
  joined_at INTEGER NOT NULL,
  left_at INTEGER,
  distance_m REAL NOT NULL DEFAULT 0,
  points INTEGER NOT NULL DEFAULT 0,
  last_t INTEGER,
  last_lat REAL,
  last_lon REAL,
  PRIMARY KEY (trip_id, user_id)
);

CREATE TABLE IF NOT EXISTS trip_points (
  trip_id TEXT NOT NULL,
  user_id TEXT NOT NULL,
  t INTEGER NOT NULL,
  lat REAL NOT NULL,
  lon REAL NOT NULL,
  PRIMARY KEY (trip_id, user_id, t)
);

-- Period tracker (Calendar tab): each person's own log. Their partner can only see it if they switch sharing on.
CREATE TABLE IF NOT EXISTS period_settings (
  user_id TEXT PRIMARY KEY,
  cycle_length INTEGER NOT NULL DEFAULT 28,
  period_length INTEGER NOT NULL DEFAULT 5,
  shared INTEGER NOT NULL DEFAULT 0,
  updated_at INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS period_logs (
  id TEXT PRIMARY KEY,
  user_id TEXT NOT NULL,
  start_date TEXT NOT NULL,              -- YYYY-MM-DD
  end_date TEXT,                         -- NULL while the period is still going
  note TEXT NOT NULL DEFAULT '',
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_period_logs_user ON period_logs(user_id, start_date DESC);

-- Optional per-day log: flow strength (1 light, 2 medium, 3 heavy) and symptoms
CREATE TABLE IF NOT EXISTS period_days (
  user_id TEXT NOT NULL,
  day TEXT NOT NULL,                     -- YYYY-MM-DD
  flow INTEGER,
  symptoms TEXT NOT NULL DEFAULT '',     -- comma separated keys
  note TEXT NOT NULL DEFAULT '',
  updated_at INTEGER NOT NULL,
  PRIMARY KEY (user_id, day)
);
