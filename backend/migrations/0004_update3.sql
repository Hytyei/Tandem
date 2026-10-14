-- Tandem update 3 — migration for the EXISTING remote database (run it ONCE, after 0002 and 0003).
--   npx wrangler d1 execute tandem --remote --file=migrations/0004_update3.sql
-- It only adds columns/tables (profile, places, reviews, reactions, minigames); nothing is dropped or rewritten.
-- If wrangler answers "duplicate column name", the migration was already applied — stop instead of re-running it.

ALTER TABLE users ADD COLUMN profile TEXT;     -- JSON: "my page" details
ALTER TABLE diary ADD COLUMN place_id TEXT;    -- places.id this memory happened at

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
