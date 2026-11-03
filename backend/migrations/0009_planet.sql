-- The couple's planet (v1.7.0). The whole world is one JSON document, simulated day by day in src/planet.ts.
CREATE TABLE IF NOT EXISTS planets (
  couple_id TEXT PRIMARY KEY,
  state TEXT NOT NULL,
  version INTEGER NOT NULL DEFAULT 1,
  updated_at INTEGER NOT NULL
);
