-- The couple's cosmos (v2.0.0): stardust, storages, pets and the galaxy seed, as one JSON document (src/cosmos.ts).
CREATE TABLE IF NOT EXISTS cosmos (
  couple_id TEXT PRIMARY KEY,
  state TEXT NOT NULL,
  version INTEGER NOT NULL DEFAULT 1,
  updated_at INTEGER NOT NULL
);
