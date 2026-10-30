-- Tandem v1.5.0: Sign in with Google. Migration for the EXISTING remote database (run it ONCE, after 0007).
--   npx wrangler d1 execute tandem --remote --file=migrations/0008_google.sql
-- Only ADDS a table (IF NOT EXISTS), so running it twice is harmless.

CREATE TABLE IF NOT EXISTS google_links (
  sub TEXT PRIMARY KEY,
  user_id TEXT NOT NULL UNIQUE,
  email TEXT,
  no_password INTEGER NOT NULL DEFAULT 0,
  created_at INTEGER NOT NULL
);
