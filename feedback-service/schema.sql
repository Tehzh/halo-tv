CREATE TABLE IF NOT EXISTS reports (
 id TEXT PRIMARY KEY, proof TEXT NOT NULL, created INTEGER NOT NULL, updated INTEGER NOT NULL,
 category TEXT NOT NULL, edition TEXT NOT NULL, version TEXT NOT NULL, code INTEGER NOT NULL,
 model TEXT NOT NULL, android TEXT NOT NULL, diagnostics TEXT NOT NULL, description TEXT NOT NULL DEFAULT '',
 status TEXT NOT NULL DEFAULT 'new', reply TEXT NOT NULL DEFAULT '', images TEXT NOT NULL DEFAULT '[]',
 rate_key TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS reports_created ON reports(created);
CREATE INDEX IF NOT EXISTS reports_rate ON reports(rate_key, created);
CREATE INDEX IF NOT EXISTS reports_status ON reports(status, created);
