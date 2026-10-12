-- Tandem update 1 — migration for the EXISTING remote database (run it ONCE).
--   npx wrangler d1 execute tandem --remote --file=migrations/0002_update1.sql
-- It only adds columns/tables and back-fills data; nothing is dropped or rewritten.
-- If wrangler answers "duplicate column name", the migration (or part of it) was already applied — stop and
-- tell me which statement failed instead of re-running it.

-- Special days + edit tracking for events
ALTER TABLE events ADD COLUMN kind TEXT NOT NULL DEFAULT 'event';      -- 'event' | 'special'
ALTER TABLE events ADD COLUMN category TEXT;                           -- birthday, valentine, firstdate, ...
ALTER TABLE events ADD COLUMN updated_at INTEGER NOT NULL DEFAULT 0;
ALTER TABLE events ADD COLUMN updated_by TEXT;

-- Diary: entries can be dated for another day, and can carry pictures
ALTER TABLE diary ADD COLUMN entry_date TEXT;                          -- YYYY-MM-DD, NULL = day it was written
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

-- Removing a partner keeps the history ("ended" couple) so it can be restored if the same two people reconnect
ALTER TABLE couples ADD COLUMN status TEXT NOT NULL DEFAULT 'active'; -- 'active' | 'ended'
ALTER TABLE couples ADD COLUMN user_a TEXT;                            -- the two members, sorted by id
ALTER TABLE couples ADD COLUMN user_b TEXT;
ALTER TABLE couples ADD COLUMN ended_at INTEGER;
CREATE INDEX IF NOT EXISTS idx_couples_pair ON couples(user_a, user_b, status);
-- back-fill the members of every couple that is already paired
UPDATE couples SET
  user_a = (SELECT MIN(id) FROM users WHERE users.couple_id = couples.id),
  user_b = (SELECT MAX(id) FROM users WHERE users.couple_id = couples.id)
WHERE (SELECT COUNT(*) FROM users WHERE users.couple_id = couples.id) = 2;

-- Rate limiting for sign-in / register / invite codes
CREATE TABLE IF NOT EXISTS auth_attempts (
  key TEXT PRIMARY KEY,
  count INTEGER NOT NULL,
  reset_at INTEGER NOT NULL
);
