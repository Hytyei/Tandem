-- Tandem update 4 — migration for the EXISTING remote database (run it ONCE, after 0002, 0003 and 0004).
--   npx wrangler d1 execute tandem --remote --file=migrations/0005_update4.sql
-- It only ADDS tables (place pictures, saved addresses, shared notes + checklists, live-tracked trips).
-- Nothing is dropped, renamed or rewritten, and every statement is IF NOT EXISTS, so running it twice is harmless.

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
