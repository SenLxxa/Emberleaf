-- Emberleaf Lagerfeuer: Datenbank (Cloudflare D1 / SQLite)
CREATE TABLE IF NOT EXISTS players (
	uuid TEXT PRIMARY KEY,
	name TEXT NOT NULL,
	outfit TEXT,
	outfit_updated INTEGER NOT NULL DEFAULT 0,
	online INTEGER NOT NULL DEFAULT 0,
	server TEXT,
	last_seen INTEGER NOT NULL DEFAULT 0,
	created INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS players_name ON players (name COLLATE NOCASE);

CREATE TABLE IF NOT EXISTS sessions (
	token_hash TEXT PRIMARY KEY,
	uuid TEXT NOT NULL,
	expires INTEGER NOT NULL
);

-- a < b, so every friendship exists exactly once
CREATE TABLE IF NOT EXISTS friendships (
	a TEXT NOT NULL,
	b TEXT NOT NULL,
	created INTEGER NOT NULL,
	PRIMARY KEY (a, b)
);

CREATE TABLE IF NOT EXISTS friend_requests (
	from_uuid TEXT NOT NULL,
	to_uuid TEXT NOT NULL,
	created INTEGER NOT NULL,
	PRIMARY KEY (from_uuid, to_uuid)
);

-- Client-Updates: eine Zeile pro Update, die Dateien in 512-KB-Stücken
CREATE TABLE IF NOT EXISTS releases (
	id TEXT PRIMARY KEY,
	notes TEXT,
	files TEXT NOT NULL,
	published INTEGER NOT NULL DEFAULT 0,
	created INTEGER NOT NULL,
	by TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS release_chunks (
	release TEXT NOT NULL,
	file TEXT NOT NULL,
	part INTEGER NOT NULL,
	data BLOB NOT NULL,
	PRIMARY KEY (release, file, part)
);
