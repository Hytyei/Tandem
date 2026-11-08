-- v2.2.0: let someone turn their own cycle tracking off
ALTER TABLE period_settings ADD COLUMN tracking INTEGER NOT NULL DEFAULT 1;
