-- Tandem update 2 — migration for the EXISTING remote database (run it ONCE, after 0002_update1.sql).
--   npx wrangler d1 execute tandem --remote --file=migrations/0003_update2.sql
-- It only adds columns; nothing is dropped or rewritten.
-- If wrangler answers "duplicate column name", the migration was already applied — stop instead of re-running it.

ALTER TABLE diary ADD COLUMN rating INTEGER;       -- 1..5 hearts, NULL = not rated
ALTER TABLE diary ADD COLUMN date_type TEXT;       -- datenight, movie, gaming, ...
ALTER TABLE diary ADD COLUMN updated_at INTEGER;   -- set when the note is edited
ALTER TABLE events ADD COLUMN date_type TEXT;
CREATE INDEX IF NOT EXISTS idx_diary_rating ON diary(couple_id, rating);
