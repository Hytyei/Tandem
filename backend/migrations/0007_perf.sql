-- Tandem v1.4.0: speed stats for the minigames. Migration for the EXISTING remote database (run it ONCE, after 0006).
--   npx wrangler d1 execute tandem --remote --file=migrations/0007_perf.sql
-- Only ADDS a table (IF NOT EXISTS), so running it twice is harmless. Until you run it, scores keep working; the speed stats are just empty.

CREATE TABLE IF NOT EXISTS game_perf (
  match_id TEXT NOT NULL,
  round INTEGER NOT NULL,
  user_id TEXT NOT NULL,
  game TEXT NOT NULL,
  best_ms INTEGER NOT NULL DEFAULT 0,
  avg_ms INTEGER NOT NULL DEFAULT 0,
  n INTEGER NOT NULL DEFAULT 0,
  hits INTEGER NOT NULL DEFAULT 0,
  misses INTEGER NOT NULL DEFAULT 0,
  PRIMARY KEY (match_id, round, user_id)
);
