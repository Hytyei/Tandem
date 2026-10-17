-- Tandem period tracker — migration for the EXISTING remote database (run it ONCE, after 0005).
--   npx wrangler d1 execute tandem --remote --file=migrations/0006_period.sql
-- Only ADDS tables, every statement is IF NOT EXISTS, so running it twice is harmless.

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
