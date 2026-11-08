// Emberleaf Lagerfeuer: Freunde, Chat, Party, Online-Status und geteilte Cosmetics für Emberleaf-Spieler.
// Läuft als Cloudflare Worker mit einer D1-Datenbank (Binding "DB", Tabellen aus schema.sql).
//
// Anmeldung wie bei einem echten Minecraft-Server: Der Client meldet sich mit seinem Minecraft-Token bei
// Mojang für eine zufällige serverId an ("join"), dieser Server prüft das bei Mojang ("hasJoined").
// Passwörter oder Tokens bekommt der Server dabei nie zu sehen. Das Handy koppelt man mit einem
// Einmal-Code aus dem Launcher.
//
// Diese Datei wird aus src/worker.js und dem Ordner app/ (Handy-App) gebaut: python build.py

const SESSION_SECONDS = 30 * 24 * 3600;
const APP_SESSION_SECONDS = 180 * 24 * 3600;
const ONLINE_SECONDS = 90;
const MAX_OUTFIT_CHARS = 1_000_000;
const MAX_FRIENDS = 200;
const MAX_MESSAGE_CHARS = 1000;
const MAX_GROUP_MEMBERS = 20;
const MAX_PARTY = 8;
const MAX_IMAGE_BYTES = 450 * 1024;
const KEEP_MESSAGES = 1000;
const INVITE_SECONDS = 10 * 60;
const PAIR_SECONDS = 5 * 60;
const EMOTE_MS = 4000;
const UUID_RE = /^[0-9a-f]{32}$/;
const NAME_RE = /^[A-Za-z0-9_]{1,16}$/;
const SERVER_ID_RE = /^[0-9a-f]{8,40}$/;
const CHAT_RE = /^[dgp][0-9a-f]{16,64}$/;
const IMAGE_ID_RE = /^[0-9a-f]{32}$/;
const ADDRESS_RE = /^[A-Za-z0-9._\-:[\]]{1,120}$/;
const EMOTE_RE = /^emote-[a-z]{2,16}$/;
const PAIR_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
const REACTIONS = ["heart", "laugh", "like", "fire", "wow", "sad"];
const IMAGE_TYPES = ["image/png", "image/jpeg", "image/webp", "image/gif"];
// Abzeichen: manche prüft der Server selbst (Gründer, Freund, Stunden), die anderen meldet der Client
const BADGE_ORDER = ["founder", "alpha", "h500", "h100", "h50", "h10", "friend", "creator", "explorer", "nightowl"];
const CLAIMABLE_BADGES = ["alpha", "creator", "explorer", "nightowl"];
// Kondensstreifen-Farben und ab wie vielen Spielstunden sie freigeschaltet sind (wie im Client)
const CONTRAIL_HOURS = { white: 0, ember: 1, sky: 3, rose: 5, mint: 10, violet: 25, gold: 50, rainbow: 100 };

class HttpError extends Error {
	constructor(status, message) {
		super(message);
		this.status = status;
	}
}

// Gleich wie schema.sql: Der Worker legt die Tabellen beim ersten Aufruf selbst an,
// damit man beim Einrichten im Cloudflare-Dashboard kein SQL eintippen muss.
const SCHEMA = [
	`CREATE TABLE IF NOT EXISTS players (uuid TEXT PRIMARY KEY, name TEXT NOT NULL, outfit TEXT,
		outfit_updated INTEGER NOT NULL DEFAULT 0, online INTEGER NOT NULL DEFAULT 0, server TEXT,
		last_seen INTEGER NOT NULL DEFAULT 0, created INTEGER NOT NULL)`,
	"CREATE INDEX IF NOT EXISTS players_name ON players (name COLLATE NOCASE)",
	"CREATE TABLE IF NOT EXISTS sessions (token_hash TEXT PRIMARY KEY, uuid TEXT NOT NULL, expires INTEGER NOT NULL)",
	"CREATE TABLE IF NOT EXISTS friendships (a TEXT NOT NULL, b TEXT NOT NULL, created INTEGER NOT NULL, PRIMARY KEY (a, b))",
	`CREATE TABLE IF NOT EXISTS friend_requests (from_uuid TEXT NOT NULL, to_uuid TEXT NOT NULL, created INTEGER NOT NULL,
		PRIMARY KEY (from_uuid, to_uuid))`,
	// Emberleaf-Versionen der Webseite: die Dateien selbst liegen in KV (Binding "FILES"), hier nur die Liste
	`CREATE TABLE IF NOT EXISTS site_releases (id TEXT PRIMARY KEY, version TEXT NOT NULL, notes TEXT NOT NULL DEFAULT '',
		files TEXT NOT NULL, state TEXT NOT NULL DEFAULT 'draft', created INTEGER NOT NULL, published INTEGER, by TEXT NOT NULL)`,
	`CREATE TABLE IF NOT EXISTS site_release_parts (release TEXT NOT NULL, file TEXT NOT NULL, part INTEGER NOT NULL,
		size INTEGER NOT NULL, PRIMARY KEY (release, file, part))`,
	`CREATE TABLE IF NOT EXISTS stats (uuid TEXT PRIMARY KEY, playtime INTEGER NOT NULL DEFAULT 0, today INTEGER NOT NULL DEFAULT 0,
		badges TEXT NOT NULL DEFAULT '[]', contrail TEXT, updated INTEGER NOT NULL DEFAULT 0)`,
	"CREATE TABLE IF NOT EXISTS emotes (uuid TEXT PRIMARY KEY, emote TEXT NOT NULL, started INTEGER NOT NULL)",
	`CREATE TABLE IF NOT EXISTS chats (id TEXT PRIMARY KEY, kind TEXT NOT NULL, name TEXT, owner TEXT,
		created INTEGER NOT NULL, last INTEGER NOT NULL DEFAULT 0)`,
	`CREATE TABLE IF NOT EXISTS chat_members (chat TEXT NOT NULL, uuid TEXT NOT NULL, joined INTEGER NOT NULL,
		read INTEGER NOT NULL DEFAULT 0, PRIMARY KEY (chat, uuid))`,
	"CREATE INDEX IF NOT EXISTS chat_members_uuid ON chat_members (uuid)",
	`CREATE TABLE IF NOT EXISTS messages (id INTEGER PRIMARY KEY AUTOINCREMENT, chat TEXT NOT NULL, sender TEXT NOT NULL,
		sent INTEGER NOT NULL, kind TEXT NOT NULL DEFAULT 'text', text TEXT NOT NULL DEFAULT '', reply INTEGER, data TEXT, edited INTEGER)`,
	"CREATE INDEX IF NOT EXISTS messages_chat ON messages (chat, id)",
	"CREATE TABLE IF NOT EXISTS reactions (message INTEGER NOT NULL, uuid TEXT NOT NULL, emoji TEXT NOT NULL, PRIMARY KEY (message, uuid, emoji))",
	`CREATE TABLE IF NOT EXISTS images (id TEXT PRIMARY KEY, owner TEXT NOT NULL, mime TEXT NOT NULL, data BLOB NOT NULL,
		created INTEGER NOT NULL)`,
	"CREATE TABLE IF NOT EXISTS parties (id TEXT PRIMARY KEY, leader TEXT NOT NULL, chat TEXT NOT NULL, created INTEGER NOT NULL)",
	`CREATE TABLE IF NOT EXISTS party_members (party TEXT NOT NULL, uuid TEXT NOT NULL, joined INTEGER NOT NULL,
		PRIMARY KEY (party, uuid))`,
	"CREATE UNIQUE INDEX IF NOT EXISTS party_members_uuid ON party_members (uuid)",
	"CREATE TABLE IF NOT EXISTS party_invites (party TEXT NOT NULL, uuid TEXT NOT NULL, created INTEGER NOT NULL, PRIMARY KEY (party, uuid))",
	"CREATE TABLE IF NOT EXISTS pair_codes (code TEXT PRIMARY KEY, uuid TEXT NOT NULL, expires INTEGER NOT NULL)",
	// Anmelden mit Microsoft auf der Webseite: PKCE-Schlüssel, bis Microsoft zurückleitet (10 Minuten)
	"CREATE TABLE IF NOT EXISTS oauth_states (state TEXT PRIMARY KEY, verifier TEXT NOT NULL, back TEXT NOT NULL, expires INTEGER NOT NULL)",
];
let schemaReady = null;

function ensureSchema(env) {
	schemaReady ||= env.DB.batch(SCHEMA.map((sql) => env.DB.prepare(sql))).catch((e) => {
		schemaReady = null;
		throw e;
	});
	return schemaReady;
}

const json = (data, status = 200) => new Response(JSON.stringify(data), {
	status,
	headers: { "content-type": "application/json; charset=utf-8", "cache-control": "no-store" },
});
const now = () => Math.floor(Date.now() / 1000);
const parseJson = (text, fallback) => {
	try {
		return text ? JSON.parse(text) : fallback;
	} catch {
		return fallback;
	}
};
const randomHex = (bytes) => [...crypto.getRandomValues(new Uint8Array(bytes))].map((b) => b.toString(16).padStart(2, "0")).join("");
const isOnline = (row, t) => Boolean(row.online) && t - row.last_seen < ONLINE_SECONDS;
const intIn = (value, min, max) => (Number.isInteger(value) && value >= min && value <= max ? value : null);

export default {
	async fetch(request, env, ctx) {
		try {
			const url = new URL(request.url);
			// Webseite und Handy-App brauchen keine Datenbank, um sich zu zeigen
			if (request.method === "GET" || request.method === "HEAD") {
				if (url.pathname.startsWith("/m/")) return await mediaFile(url, env);
				const page = siteFile(request, url);
				if (page) return page;
				if (url.pathname === "/app" || url.pathname.startsWith("/app/")) return appFile(request, url);
			}
			if (!env.DB) return json({ error: "Die Datenbank ist nicht verbunden (D1-Bindung „DB“ fehlt)." }, 500);
			await ensureSchema(env);
			return await route(request, env, url, ctx);
		} catch (e) {
			if (e instanceof HttpError) return json({ error: e.message }, e.status);
			console.error(e);
			return json({ error: "Serverfehler" }, 500);
		}
	},
};

async function route(request, env, url, ctx) {
	const path = url.pathname.replace(/\/+$/, "");
	const method = request.method;
	if (path === "/v1/health") return json({ ok: true, name: "Emberleaf Lagerfeuer", app: true, site: true });
	if (path === "/v1/auth" && method === "POST") return auth(request, env);
	if (path === "/v1/pair/claim" && method === "POST") return pairClaim(request, env);
	if (method === "GET" && path === "/auth/microsoft") return microsoftStart(url, env);
	if (method === "GET" && path === "/auth/callback") return microsoftCallback(request, url, env);
	if (method === "GET" && path.startsWith("/v1/image/")) return getImage(path.slice("/v1/image/".length), env);
	if (method === "GET" && path.startsWith("/v1/skin/")) return getSkin(path.slice("/v1/skin/".length));
	// Updates und Downloads sind öffentlich: Launcher ohne Anmeldung und die Webseite holen sie sich auch
	if (method === "GET" && path === "/v1/update") return latestRelease(request, env);
	if (method === "GET" && path === "/v1/releases") return listReleases(env);
	if ((method === "GET" || method === "HEAD") && path.startsWith("/download/")) return download(request, path, env, ctx);

	const me = await authenticate(request, env);
	switch (`${method} ${path}`) {
		case "GET /v1/me": return json(me);
		case "POST /v1/logout": return logout(request, env);
		case "PUT /v1/outfit": return putOutfit(request, env, me);
		case "GET /v1/outfits": return getOutfits(url, env);
		case "POST /v1/presence": return presence(request, env, me);
		case "POST /v1/sync": return sync(request, env, me);
		case "GET /v1/profile": return profile(url, env, me);
		case "GET /v1/friends": return friends(env, me);
		case "POST /v1/friends/add": return addFriend(request, env, me);
		case "POST /v1/friends/accept": return acceptFriend(request, env, me);
		case "POST /v1/friends/remove": return removeFriend(request, env, me);
		case "GET /v1/chats": return listChats(env, me);
		case "GET /v1/chat/messages": return chatMessages(url, env, me);
		case "POST /v1/chat/send": return sendMessage(request, env, me);
		case "POST /v1/chat/react": return react(request, env, me);
		case "POST /v1/chat/read": return markRead(request, env, me);
		case "POST /v1/chat/delete": return deleteMessage(request, env, me);
		case "POST /v1/chat/group": return createGroup(request, env, me);
		case "POST /v1/chat/group/add": return groupAdd(request, env, me);
		case "POST /v1/chat/group/leave": return groupLeave(request, env, me);
		case "POST /v1/chat/group/rename": return groupRename(request, env, me);
		case "POST /v1/chat/game": return newGame(request, env, me);
		case "POST /v1/chat/game/move": return gameMove(request, env, me);
		case "PUT /v1/image": return putImage(request, env, me);
		case "GET /v1/party": return json(await partyState(env, me));
		case "POST /v1/party/create": return partyCreate(env, me);
		case "POST /v1/party/invite": return partyInvite(request, env, me);
		case "POST /v1/party/join": return partyJoin(request, env, me);
		case "POST /v1/party/decline": return partyDecline(request, env, me);
		case "POST /v1/party/leave": return partyLeave(env, me);
		case "POST /v1/party/kick": return partyKick(request, env, me);
		case "POST /v1/pair/start": return pairStart(url, env, me);
		case "POST /v1/update/begin": return beginRelease(request, env, me);
		case "PUT /v1/update/part": return putPart(request, url, env, me);
		case "POST /v1/update/publish": return publishRelease(request, env, me);
		case "POST /v1/update/discard": return discardRelease(request, env, me);
		default: throw new HttpError(404, "Unbekannter Pfad");
	}
}

async function body(request) {
	try {
		const data = await request.json();
		if (data && typeof data === "object" && !Array.isArray(data)) return data;
	} catch {
		// fällt unten durch
	}
	throw new HttpError(400, "Ungültige Anfrage");
}

// ---------- Anmeldung ----------

async function sha256(text) {
	const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(text));
	return [...new Uint8Array(digest)].map((b) => b.toString(16).padStart(2, "0")).join("");
}

function randomToken() {
	const bytes = crypto.getRandomValues(new Uint8Array(32));
	return btoa(String.fromCharCode(...bytes)).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

async function verifyWithMojang(name, serverId, env) {
	if (env.TEST_MODE === "1" && serverId.startsWith("7e57")) {
		// Nur für lokale Tests: "7e57" + UUID statt echter Mojang-Prüfung
		return { id: serverId.slice(4), name };
	}
	const r = await fetch(
		`https://sessionserver.mojang.com/session/minecraft/hasJoined?username=${encodeURIComponent(name)}&serverId=${encodeURIComponent(serverId)}`,
	);
	if (r.status !== 200) throw new HttpError(401, "Die Minecraft-Anmeldung konnte nicht bestätigt werden.");
	const profile = await r.json();
	if (!UUID_RE.test(profile.id || "")) throw new HttpError(401, "Unerwartete Antwort von Mojang.");
	return { id: profile.id, name: profile.name };
}

async function newSession(env, uuid, seconds) {
	const token = randomToken();
	const t = now();
	await env.DB.batch([
		env.DB.prepare("INSERT INTO sessions (token_hash, uuid, expires) VALUES (?1, ?2, ?3)").bind(await sha256(token), uuid, t + seconds),
		env.DB.prepare("DELETE FROM sessions WHERE expires < ?1").bind(t),
	]);
	return { token, expires: t + seconds };
}

async function auth(request, env) {
	const { name, serverId } = await body(request);
	if (!NAME_RE.test(name || "") || !SERVER_ID_RE.test(serverId || "")) throw new HttpError(400, "Name oder serverId ungültig");
	const profile = await verifyWithMojang(name, serverId, env);
	await env.DB.prepare(
		"INSERT INTO players (uuid, name, created, last_seen) VALUES (?1, ?2, ?3, ?3) ON CONFLICT(uuid) DO UPDATE SET name = excluded.name",
	).bind(profile.id, profile.name, now()).run();
	const session = await newSession(env, profile.id, SESSION_SECONDS);
	return json({ token: session.token, uuid: profile.id, name: profile.name, expires: session.expires });
}

async function authenticate(request, env) {
	const header = request.headers.get("authorization") || "";
	const token = header.startsWith("Bearer ") ? header.slice(7) : "";
	if (!token) throw new HttpError(401, "Nicht angemeldet");
	const row = await env.DB.prepare(
		"SELECT p.uuid, p.name FROM sessions s JOIN players p ON p.uuid = s.uuid WHERE s.token_hash = ?1 AND s.expires > ?2",
	).bind(await sha256(token), now()).first();
	if (!row) throw new HttpError(401, "Anmeldung abgelaufen");
	return { uuid: row.uuid, name: row.name };
}

async function logout(request, env) {
	const token = (request.headers.get("authorization") || "").slice(7);
	await env.DB.prepare("DELETE FROM sessions WHERE token_hash = ?1").bind(await sha256(token)).run();
	return json({ ok: true });
}

// ---------- Handy koppeln ----------
// Der Launcher (angemeldet) holt einen kurzen Einmal-Code, das Handy tauscht ihn gegen eine eigene Anmeldung.

async function newPairCode(env, uuid) {
	const bytes = crypto.getRandomValues(new Uint8Array(6));
	const code = [...bytes].map((b) => PAIR_ALPHABET[b % PAIR_ALPHABET.length]).join("");
	const t = now();
	await env.DB.batch([
		env.DB.prepare("DELETE FROM pair_codes WHERE uuid = ?1 OR expires < ?2").bind(uuid, t),
		env.DB.prepare("INSERT INTO pair_codes (code, uuid, expires) VALUES (?1, ?2, ?3)").bind(code, uuid, t + PAIR_SECONDS),
	]);
	return { code, expires: t + PAIR_SECONDS };
}

async function pairStart(url, env, me) {
	const pair = await newPairCode(env, me.uuid);
	return json({ ...pair, url: `${url.origin}/app#pair=${pair.code}` });
}

// ---------- Anmelden mit Microsoft auf der Webseite ----------
// Wie im Launcher: Microsoft → Xbox Live → Minecraft-Profil. Der Server sieht die Tokens nur kurz und speichert sie
// nicht, am Ende gibt es einen Einmal-Code (wie beim Handy-Koppeln), den die Seite gegen ihre Anmeldung tauscht.
// In Azure muss <Webseite>/auth/callback als Umleitungs-URI unter "Mobile- und Desktopanwendungen" stehen.

const MS_AUTHORIZE = "https://login.microsoftonline.com/consumers/oauth2/v2.0/authorize";
const MS_TOKEN = "https://login.microsoftonline.com/consumers/oauth2/v2.0/token";
const MS_SCOPE = "XboxLive.signin offline_access";
const OAUTH_SECONDS = 10 * 60;
const BACK_PAGES = ["/", "/upload"];
const OAUTH_COOKIE = "emberleaf_oauth";
const b64url = (bytes) => btoa(String.fromCharCode(...new Uint8Array(bytes))).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");

function redirectTo(location, cookie) {
	const headers = { location, "cache-control": "no-store", "referrer-policy": "no-referrer" };
	if (cookie) headers["set-cookie"] = cookie;
	return new Response(null, { status: 302, headers });
}

async function microsoftStart(url, env) {
	if (!env.MS_CLIENT_ID) throw new HttpError(500, "Anmelden mit Microsoft ist aus (Variable MS_CLIENT_ID fehlt).");
	const state = randomHex(16);
	const verifier = b64url(crypto.getRandomValues(new Uint8Array(48)));
	const challenge = b64url(await crypto.subtle.digest("SHA-256", new TextEncoder().encode(verifier)));
	const back = BACK_PAGES.includes(url.searchParams.get("to")) ? url.searchParams.get("to") : "/";
	await env.DB.batch([
		env.DB.prepare("DELETE FROM oauth_states WHERE expires < ?1").bind(now()),
		env.DB.prepare("INSERT INTO oauth_states (state, verifier, back, expires) VALUES (?1, ?2, ?3, ?4)").bind(state, verifier, back, now() + OAUTH_SECONDS),
	]);
	const params = new URLSearchParams({
		client_id: env.MS_CLIENT_ID, response_type: "code", redirect_uri: `${url.origin}/auth/callback`, response_mode: "query",
		scope: MS_SCOPE, state, code_challenge: challenge, code_challenge_method: "S256", prompt: "select_account",
	});
	// das Cookie bindet die Anmeldung an diesen Browser: ein fremder Rückruf-Link meldet niemanden an
	const cookie = `${OAUTH_COOKIE}=${state}; Path=/auth; Max-Age=${OAUTH_SECONDS}; HttpOnly; Secure; SameSite=Lax`;
	return redirectTo(`${MS_AUTHORIZE}?${params}`, cookie);
}

async function postJson(target, payload) {
	const r = await fetch(target, {
		method: "POST", headers: { "content-type": "application/json", accept: "application/json" }, body: JSON.stringify(payload),
	});
	return { status: r.status, data: await r.json().catch(() => ({})) };
}

async function minecraftProfile(code, verifier, redirectUri, env) {
	const tokenRes = await fetch(MS_TOKEN, {
		method: "POST",
		headers: { "content-type": "application/x-www-form-urlencoded" },
		body: new URLSearchParams({ client_id: env.MS_CLIENT_ID, scope: MS_SCOPE, code, redirect_uri: redirectUri, grant_type: "authorization_code", code_verifier: verifier }),
	});
	const token = await tokenRes.json().catch(() => ({}));
	if (!token.access_token) {
		console.error("Microsoft-Token", tokenRes.status, token.error, token.error_description);
		throw new HttpError(401, "Microsoft hat die Anmeldung abgelehnt. Versuch es nochmal.");
	}
	const xbl = await postJson("https://user.auth.xboxlive.com/user/authenticate", {
		Properties: { AuthMethod: "RPS", SiteName: "user.auth.xboxlive.com", RpsTicket: `d=${token.access_token}` },
		RelyingParty: "http://auth.xboxlive.com", TokenType: "JWT",
	});
	if (!xbl.data.Token) throw new HttpError(401, "Xbox Live hat die Anmeldung abgelehnt.");
	const xsts = await postJson("https://xsts.auth.xboxlive.com/xsts/authorize", {
		Properties: { SandboxId: "RETAIL", UserTokens: [xbl.data.Token] }, RelyingParty: "rp://api.minecraftservices.com/", TokenType: "JWT",
	});
	if (!xsts.data.Token) {
		const xerr = String(xsts.data.XErr || "");
		if (xerr === "2148916233") throw new HttpError(401, "Dieses Microsoft-Konto hat noch kein Xbox-Profil. Starte Minecraft einmal mit dem offiziellen Launcher.");
		if (xerr === "2148916238") throw new HttpError(401, "Bei Kinderkonten muss ein Erwachsener das in der Microsoft-Familie erst erlauben.");
		throw new HttpError(401, "Xbox Live hat die Anmeldung abgelehnt.");
	}
	const uhs = xsts.data.DisplayClaims?.xui?.[0]?.uhs;
	const mc = await postJson("https://api.minecraftservices.com/authentication/login_with_xbox", { identityToken: `XBL3.0 x=${uhs};${xsts.data.Token}` });
	if (!mc.data.access_token) {
		console.error("Minecraft-Login", mc.status, JSON.stringify(mc.data).slice(0, 300));
		throw new HttpError(401, mc.status === 403 ? "Mojang hat diese Anmeldung (noch) nicht freigegeben." : "Die Minecraft-Anmeldung hat nicht geklappt.");
	}
	const profileRes = await fetch("https://api.minecraftservices.com/minecraft/profile", { headers: { authorization: `Bearer ${mc.data.access_token}` } });
	if (profileRes.status === 404) throw new HttpError(403, "Dieses Microsoft-Konto besitzt kein Minecraft: Java Edition.");
	const profile = await profileRes.json().catch(() => ({}));
	if (!UUID_RE.test(profile.id || "") || !NAME_RE.test(profile.name || "")) throw new HttpError(401, "Unerwartete Antwort von Minecraft.");
	return { id: profile.id, name: profile.name };
}

async function microsoftCallback(request, url, env) {
	const clear = `${OAUTH_COOKIE}=; Path=/auth; Max-Age=0; HttpOnly; Secure; SameSite=Lax`;
	const fail = (back, message) => redirectTo(`${url.origin}${back}#login-error=${encodeURIComponent(message)}`, clear);
	const state = url.searchParams.get("state") || "";
	const cookie = (request.headers.get("cookie") || "").match(new RegExp(`(?:^|;\\s*)${OAUTH_COOKIE}=([0-9a-f]{32})`))?.[1];
	if (!/^[0-9a-f]{32}$/.test(state) || state !== cookie) return fail("/", "Die Anmeldung ist abgelaufen. Versuch es nochmal.");
	const saved = await env.DB.prepare("DELETE FROM oauth_states WHERE state = ?1 AND expires > ?2 RETURNING verifier, back").bind(state, now()).first();
	if (!saved) return fail("/", "Die Anmeldung ist abgelaufen. Versuch es nochmal.");
	if (url.searchParams.get("error")) return fail(saved.back, "Die Anmeldung wurde abgebrochen.");
	const code = url.searchParams.get("code") || "";
	if (!code) return fail(saved.back, "Microsoft hat keinen Anmelde-Code geschickt.");
	try {
		const profile = await minecraftProfile(code, saved.verifier, `${url.origin}/auth/callback`, env);
		await env.DB.prepare(
			"INSERT INTO players (uuid, name, created, last_seen) VALUES (?1, ?2, ?3, ?3) ON CONFLICT(uuid) DO UPDATE SET name = excluded.name",
		).bind(profile.id, profile.name, now()).run();
		const pair = await newPairCode(env, profile.id);
		return redirectTo(`${url.origin}${saved.back}#login=${pair.code}`, clear);
	} catch (e) {
		if (!(e instanceof HttpError)) console.error(e);
		return fail(saved.back, e instanceof HttpError ? e.message : "Die Anmeldung bei Microsoft hat nicht geklappt. Versuch es nochmal.");
	}
}

async function pairClaim(request, env) {
	const code = String((await body(request)).code || "").toUpperCase().replace(/[^A-Z0-9]/g, "");
	if (code.length !== 6) throw new HttpError(400, "Der Code hat 6 Zeichen.");
	const row = await env.DB.prepare("SELECT c.uuid, p.name FROM pair_codes c JOIN players p ON p.uuid = c.uuid WHERE c.code = ?1 AND c.expires > ?2")
		.bind(code, now()).first();
	if (!row) throw new HttpError(404, "Dieser Code stimmt nicht oder ist abgelaufen. Hol dir im Launcher einen neuen.");
	await env.DB.prepare("DELETE FROM pair_codes WHERE code = ?1").bind(code).run();
	const session = await newSession(env, row.uuid, APP_SESSION_SECONDS);
	return json({ token: session.token, uuid: row.uuid, name: row.name, expires: session.expires });
}

// ---------- Outfits ----------

async function putOutfit(request, env, me) {
	const text = await request.text();
	if (text.length > MAX_OUTFIT_CHARS) throw new HttpError(413, "Outfit zu groß");
	let items;
	try {
		items = JSON.parse(text);
	} catch {
		throw new HttpError(400, "Outfit ist kein JSON");
	}
	if (!items || typeof items !== "object" || Array.isArray(items)) throw new HttpError(400, "Ungültiges Outfit");
	await env.DB.prepare("UPDATE players SET outfit = ?1, outfit_updated = ?2 WHERE uuid = ?3")
		.bind(JSON.stringify(items), now(), me.uuid).run();
	return json({ ok: true });
}

async function getOutfits(url, env) {
	const uuids = (url.searchParams.get("uuids") || "").split(",").filter((u) => UUID_RE.test(u)).slice(0, 100);
	const outfits = {};
	if (uuids.length) {
		const { results } = await env.DB.prepare("SELECT uuid, outfit, outfit_updated FROM players WHERE uuid IN (SELECT value FROM json_each(?1))")
			.bind(JSON.stringify(uuids)).all();
		for (const row of results) {
			if (row.outfit) outfits[row.uuid] = { updated: row.outfit_updated, items: JSON.parse(row.outfit) };
		}
	}
	return json({ outfits });
}

// ---------- Online-Status, Spielzeit, Abzeichen, Emotes ----------

async function presence(request, env, me) {
	const { online, server } = await body(request);
	const address = typeof server === "string" ? server.slice(0, 120) : null;
	await env.DB.prepare("UPDATE players SET online = ?1, server = ?2, last_seen = ?3 WHERE uuid = ?4")
		.bind(online ? 1 : 0, online ? address : null, now(), me.uuid).run();
	return json({ ok: true });
}

function isOwner(env, me) {
	const owner = String(env.UPDATE_OWNER || "").trim().toLowerCase();
	return Boolean(owner) && (owner === String(me.name || "").toLowerCase() || owner === me.uuid);
}

/** The badges of a player row (uuid, name, playtime, badges, friends). */
function badgesOf(env, row) {
	const have = new Set();
	if (isOwner(env, row)) have.add("founder");
	for (const b of parseJson(row.badges, [])) if (CLAIMABLE_BADGES.includes(b)) have.add(b);
	const h = (row.playtime || 0) / 3600;
	const hours = h >= 500 ? "h500" : h >= 100 ? "h100" : h >= 50 ? "h50" : h >= 10 ? "h10" : null;
	if (hours) have.add(hours);
	if (row.friends > 0) have.add("friend");
	return BADGE_ORDER.filter((b) => have.has(b));
}

/** The contrail colour, but only when the player has played long enough for it. */
function contrailOf(row) {
	const c = row.contrail;
	return c && CONTRAIL_HOURS[c] !== undefined && (row.playtime || 0) >= CONTRAIL_HOURS[c] * 3600 ? c : "white";
}

const todayOf = (row) => (row.updated && new Date(row.updated * 1000).toDateString() === new Date().toDateString() ? row.today || 0 : 0);

function statsJson(env, row) {
	return { playtime: row.playtime || 0, today: todayOf(row), badges: badgesOf(env, row), contrail: contrailOf(row) };
}

const STATS_COLUMNS = `s.playtime, s.today, s.badges, s.contrail, s.updated,
	(SELECT COUNT(*) FROM friendships x WHERE x.a = p.uuid OR x.b = p.uuid) AS friends`;

function statsStatement(env, me, stats, t) {
	const playtime = intIn(stats.playtime, 0, 2_000_000_000) ?? 0;
	const today = intIn(stats.today, 0, 86_400) ?? 0;
	const badges = Array.isArray(stats.badges) ? stats.badges.filter((b) => CLAIMABLE_BADGES.includes(b)).slice(0, 10) : [];
	const contrail = typeof stats.contrail === "string" && CONTRAIL_HOURS[stats.contrail] !== undefined ? stats.contrail : "white";
	// Spielzeit wächst nur, und höchstens so schnell wie die Uhr (plus etwas Spielraum)
	return env.DB.prepare(`INSERT INTO stats (uuid, playtime, today, badges, contrail, updated) VALUES (?1, ?2, ?3, ?4, ?5, ?6)
		ON CONFLICT(uuid) DO UPDATE SET
			playtime = MAX(stats.playtime, MIN(excluded.playtime, stats.playtime + (excluded.updated - stats.updated) + 120)),
			today = excluded.today, badges = excluded.badges, contrail = excluded.contrail, updated = excluded.updated`)
		.bind(me.uuid, playtime, today, JSON.stringify(badges), contrail, t);
}

/**
 * The client mod calls this every few seconds while playing: where you are, your emote and stats (when they changed),
 * and in return the emotes, badges and contrail colours of the Emberleaf players around you, new chat messages and your party.
 */
async function sync(request, env, me) {
	const req = await body(request);
	const t = now();
	const ms = Date.now();
	const online = Boolean(req.online);
	const server = online && typeof req.server === "string" ? req.server.slice(0, 120) : null;
	const nearby = JSON.stringify((Array.isArray(req.nearby) ? req.nearby : []).filter((u) => typeof u === "string" && UUID_RE.test(u)).slice(0, 100));
	// chatSince: the newest message id the client knows; -1 = it knows nothing yet and only wants the current position
	const since = intIn(req.chatSince, -1, Number.MAX_SAFE_INTEGER) ?? -1;

	const statements = [
		// nur schreiben, wenn sich etwas geändert hat oder der Status bald abläuft (spart Datenbank-Schreibvorgänge)
		env.DB.prepare(`UPDATE players SET online = ?1, server = ?2, last_seen = ?3 WHERE uuid = ?4
			AND (online != ?1 OR IFNULL(server, '') != IFNULL(?2, '') OR last_seen < ?3 - 45)`).bind(online ? 1 : 0, server, t, me.uuid),
	];
	if (req.emote !== undefined) {
		if (req.emote && EMOTE_RE.test(req.emote.id || "")) {
			const elapsed = Math.max(0, Math.min(EMOTE_MS, Number(req.emote.elapsed) || 0));
			statements.push(env.DB.prepare("INSERT OR REPLACE INTO emotes (uuid, emote, started) VALUES (?1, ?2, ?3)").bind(me.uuid, req.emote.id, ms - elapsed));
		} else {
			statements.push(env.DB.prepare("DELETE FROM emotes WHERE uuid = ?1").bind(me.uuid));
		}
	}
	if (req.stats && typeof req.stats === "object") statements.push(statsStatement(env, me, req.stats, t));
	const first = statements.length;
	statements.push(
		env.DB.prepare("SELECT uuid, emote, started FROM emotes WHERE started > ?1 AND uuid IN (SELECT value FROM json_each(?2))").bind(ms - EMOTE_MS, nearby),
		env.DB.prepare(`SELECT p.uuid, p.name, p.outfit_updated, ${STATS_COLUMNS} FROM players p LEFT JOIN stats s ON s.uuid = p.uuid
			WHERE p.uuid IN (SELECT value FROM json_each(?1))`).bind(nearby),
		since >= 0
			? env.DB.prepare(`SELECT m.id, m.chat, m.sender, p.name AS senderName, m.kind, m.text, m.sent, c.kind AS chatKind, c.name AS chatName
				FROM messages m JOIN chat_members cm ON cm.chat = m.chat AND cm.uuid = ?1 JOIN players p ON p.uuid = m.sender
				JOIN chats c ON c.id = m.chat WHERE m.id > ?2 AND m.sender != ?1 ORDER BY m.id LIMIT 20`).bind(me.uuid, since)
			: env.DB.prepare("SELECT IFNULL(MAX(m.id), 0) AS cursor FROM messages m JOIN chat_members cm ON cm.chat = m.chat AND cm.uuid = ?1").bind(me.uuid),
		env.DB.prepare(`SELECT COUNT(*) AS n FROM messages m JOIN chat_members cm ON cm.chat = m.chat AND cm.uuid = ?1
			WHERE m.id > cm.read AND m.sender != ?1`).bind(me.uuid),
		...partyStatements(env, me, t),
	);
	const results = await env.DB.batch(statements);
	const [emoteRows, statRows, chatRows, unreadRows, partyRows, inviteRows] = results.slice(first).map((r) => r.results);

	const emotes = {};
	for (const e of emoteRows) emotes[e.uuid] = { id: e.emote, age: Math.max(0, ms - e.started) };
	const players = {};
	// "outfit": when the player last changed their cosmetics, so clients fetch new ones right away
	for (const row of statRows) players[row.uuid] = { name: row.name, outfit: row.outfit_updated || 0, ...statsJson(env, row) };
	const messages = since >= 0 ? chatRows.map((m) => ({
		id: m.id, chat: m.chat, chatKind: m.chatKind, chatName: m.chatName, sender: m.sender, senderName: m.senderName,
		kind: m.kind, text: m.kind === "game" ? "lädt dich zu einem Spiel ein" : m.text, sent: m.sent,
	})) : [];
	const cursor = since >= 0 ? Math.max(since, ...messages.map((m) => m.id)) : chatRows[0]?.cursor || 0;
	return json({
		now: ms,
		emotes,
		players,
		chat: { unread: unreadRows[0]?.n || 0, cursor, messages },
		...partyFrom(partyRows, inviteRows, t),
	});
}

async function profile(url, env, me) {
	const uuid = url.searchParams.get("uuid") || me.uuid;
	if (!UUID_RE.test(uuid)) throw new HttpError(400, "Ungültige UUID");
	if (uuid !== me.uuid && !(await isFriend(env, me.uuid, uuid))) throw new HttpError(403, "Profile siehst du nur von Freunden.");
	const row = await env.DB.prepare(`SELECT p.uuid, p.name, p.online, p.server, p.last_seen, p.created, ${STATS_COLUMNS}
		FROM players p LEFT JOIN stats s ON s.uuid = p.uuid WHERE p.uuid = ?1`).bind(uuid).first();
	if (!row) throw new HttpError(404, "Diesen Spieler gibt es hier nicht.");
	const t = now();
	const online = isOnline(row, t);
	return json({
		uuid: row.uuid, name: row.name, online, server: online ? row.server : null, lastSeen: row.last_seen, since: row.created,
		friends: row.friends, ...statsJson(env, row),
	});
}

// ---------- Freunde ----------

const pair = (x, y) => (x < y ? [x, y] : [y, x]);

async function friends(env, me) {
	const t = now();
	const [list, incoming, outgoing, mine] = await env.DB.batch([
		env.DB.prepare(
			`SELECT p.uuid, p.name, p.online, p.server, p.last_seen, ${STATS_COLUMNS} FROM friendships f
			 JOIN players p ON p.uuid = CASE WHEN f.a = ?1 THEN f.b ELSE f.a END
			 LEFT JOIN stats s ON s.uuid = p.uuid
			 WHERE f.a = ?1 OR f.b = ?1 ORDER BY p.name COLLATE NOCASE`,
		).bind(me.uuid),
		env.DB.prepare("SELECT p.uuid, p.name FROM friend_requests r JOIN players p ON p.uuid = r.from_uuid WHERE r.to_uuid = ?1").bind(me.uuid),
		env.DB.prepare("SELECT p.uuid, p.name FROM friend_requests r JOIN players p ON p.uuid = r.to_uuid WHERE r.from_uuid = ?1").bind(me.uuid),
		env.DB.prepare(`SELECT p.uuid, p.name, ${STATS_COLUMNS} FROM players p LEFT JOIN stats s ON s.uuid = p.uuid WHERE p.uuid = ?1`).bind(me.uuid),
	]);
	const meRow = mine.results[0];
	return json({
		me: { ...me, ...(meRow ? statsJson(env, meRow) : {}) },
		friends: list.results.map((f) => {
			const online = isOnline(f, t);
			return { uuid: f.uuid, name: f.name, online, server: online ? f.server : null, lastSeen: f.last_seen, ...statsJson(env, f) };
		}),
		incoming: incoming.results,
		outgoing: outgoing.results,
	});
}

async function isFriend(env, x, y) {
	const [a, b] = pair(x, y);
	return Boolean(await env.DB.prepare("SELECT 1 FROM friendships WHERE a = ?1 AND b = ?2").bind(a, b).first());
}

async function befriend(env, x, y) {
	const [a, b] = pair(x, y);
	await env.DB.batch([
		env.DB.prepare("INSERT OR IGNORE INTO friendships (a, b, created) VALUES (?1, ?2, ?3)").bind(a, b, now()),
		env.DB.prepare("DELETE FROM friend_requests WHERE (from_uuid = ?1 AND to_uuid = ?2) OR (from_uuid = ?2 AND to_uuid = ?1)").bind(x, y),
	]);
}

async function addFriend(request, env, me) {
	const { name } = await body(request);
	if (!NAME_RE.test(name || "")) throw new HttpError(400, "Ungültiger Minecraft-Name");
	const other = await env.DB.prepare("SELECT uuid, name FROM players WHERE name = ?1 COLLATE NOCASE").bind(name).first();
	if (!other) throw new HttpError(404, `${name} nutzt Emberleaf noch nicht.`);
	if (other.uuid === me.uuid) throw new HttpError(400, "Du kannst dich nicht selbst hinzufügen 🙂");
	if (await isFriend(env, me.uuid, other.uuid)) return json({ status: "friends", friend: other });

	const count = await env.DB.prepare("SELECT COUNT(*) AS n FROM friendships WHERE a = ?1 OR b = ?1").bind(me.uuid).first();
	if (count.n >= MAX_FRIENDS) throw new HttpError(400, "Du hast schon sehr viele Freunde.");

	const reverse = await env.DB.prepare("SELECT 1 FROM friend_requests WHERE from_uuid = ?1 AND to_uuid = ?2").bind(other.uuid, me.uuid).first();
	if (reverse) {
		await befriend(env, me.uuid, other.uuid);
		return json({ status: "friends", friend: other });
	}
	await env.DB.prepare("INSERT OR IGNORE INTO friend_requests (from_uuid, to_uuid, created) VALUES (?1, ?2, ?3)")
		.bind(me.uuid, other.uuid, now()).run();
	return json({ status: "requested", friend: other });
}

async function acceptFriend(request, env, me) {
	const { uuid } = await body(request);
	if (!UUID_RE.test(uuid || "")) throw new HttpError(400, "Ungültige UUID");
	const req = await env.DB.prepare("SELECT 1 FROM friend_requests WHERE from_uuid = ?1 AND to_uuid = ?2").bind(uuid, me.uuid).first();
	if (!req) throw new HttpError(404, "Diese Anfrage gibt es nicht mehr.");
	await befriend(env, me.uuid, uuid);
	return json({ status: "friends" });
}

async function removeFriend(request, env, me) {
	const { uuid } = await body(request);
	if (!UUID_RE.test(uuid || "")) throw new HttpError(400, "Ungültige UUID");
	const [a, b] = pair(me.uuid, uuid);
	await env.DB.batch([
		env.DB.prepare("DELETE FROM friendships WHERE a = ?1 AND b = ?2").bind(a, b),
		env.DB.prepare("DELETE FROM friend_requests WHERE (from_uuid = ?1 AND to_uuid = ?2) OR (from_uuid = ?2 AND to_uuid = ?1)").bind(me.uuid, uuid),
	]);
	return json({ status: "removed" });
}

// ---------- Chat: Direktnachrichten, Gruppen, Party-Chat ----------

const dmId = (x, y) => {
	const [a, b] = pair(x, y);
	return `d${a}${b}`;
};
const dmPartner = (chat, uuid) => (chat.slice(1, 33) === uuid ? chat.slice(33) : chat.slice(1, 33));

async function membership(env, chat, uuid) {
	if (!CHAT_RE.test(chat || "")) throw new HttpError(400, "Ungültiger Chat");
	const row = await env.DB.prepare(
		"SELECT c.id, c.kind, c.name, c.owner, cm.read FROM chats c JOIN chat_members cm ON cm.chat = c.id AND cm.uuid = ?2 WHERE c.id = ?1",
	).bind(chat, uuid).first();
	if (!row) throw new HttpError(404, "Diesen Chat gibt es nicht (mehr).");
	return row;
}

function messageJson(r, reactions = new Map(), replies = new Map()) {
	return {
		id: r.id, chat: r.chat, sender: r.sender, sent: r.sent, kind: r.kind, text: r.text, edited: r.edited || null,
		data: parseJson(r.data, null),
		reply: r.reply ? replies.get(r.reply) || { id: r.reply, missing: true } : null,
		reactions: reactions.get(r.id) || {},
	};
}

async function listChats(env, me) {
	const t = now();
	const [chats, members, lasts] = await env.DB.batch([
		env.DB.prepare(`SELECT c.id, c.kind, c.name, c.owner, c.last, cm.read,
				(SELECT COUNT(*) FROM messages m WHERE m.chat = c.id AND m.id > cm.read AND m.sender != ?1) AS unread
			FROM chat_members cm JOIN chats c ON c.id = cm.chat WHERE cm.uuid = ?1 ORDER BY c.last DESC LIMIT 100`).bind(me.uuid),
		env.DB.prepare(`SELECT cm.chat, p.uuid, p.name, p.online, p.server, p.last_seen FROM chat_members cm JOIN players p ON p.uuid = cm.uuid
			WHERE cm.chat IN (SELECT chat FROM chat_members WHERE uuid = ?1)`).bind(me.uuid),
		env.DB.prepare(`SELECT m.id, m.chat, m.sender, m.kind, m.text, m.sent FROM messages m WHERE m.id IN
			(SELECT MAX(id) FROM messages WHERE chat IN (SELECT chat FROM chat_members WHERE uuid = ?1) GROUP BY chat)`).bind(me.uuid),
	]);
	const byChat = new Map();
	for (const m of members.results) {
		if (!byChat.has(m.chat)) byChat.set(m.chat, []);
		const online = isOnline(m, t);
		byChat.get(m.chat).push({ uuid: m.uuid, name: m.name, online, server: online ? m.server : null });
	}
	const last = new Map(lasts.results.map((m) => [m.chat, m]));
	return json({
		chats: chats.results.map((c) => {
			const list = byChat.get(c.id) || [];
			const other = c.kind === "dm" ? list.find((m) => m.uuid !== me.uuid) : null;
			const l = last.get(c.id);
			return {
				id: c.id, kind: c.kind, owner: c.owner, last: c.last, unread: c.unread,
				name: c.kind === "dm" ? other?.name || "?" : c.name || (c.kind === "party" ? "Party" : "Gruppe"),
				partner: other ? other.uuid : null,
				members: list,
				lastMessage: l ? { id: l.id, sender: l.sender, kind: l.kind, text: l.text, sent: l.sent } : null,
			};
		}),
	});
}

async function chatMessages(url, env, me) {
	const chat = await membership(env, url.searchParams.get("chat"), me.uuid);
	const before = Number(url.searchParams.get("before")) || 0;
	const limit = Math.min(100, Math.max(1, Number(url.searchParams.get("limit")) || 50));
	const t = now();
	const [msgs, members] = await env.DB.batch([
		before > 0
			? env.DB.prepare("SELECT * FROM messages WHERE chat = ?1 AND id < ?2 ORDER BY id DESC LIMIT ?3").bind(chat.id, before, limit + 1)
			: env.DB.prepare("SELECT * FROM messages WHERE chat = ?1 ORDER BY id DESC LIMIT ?2").bind(chat.id, limit + 1),
		env.DB.prepare(`SELECT p.uuid, p.name, p.online, p.server, p.last_seen, cm.read FROM chat_members cm JOIN players p ON p.uuid = cm.uuid
			WHERE cm.chat = ?1 ORDER BY cm.joined`).bind(chat.id),
	]);
	const more = msgs.results.length > limit;
	const rows = msgs.results.slice(0, limit).reverse();
	const ids = JSON.stringify(rows.map((r) => r.id));
	const replyIds = JSON.stringify([...new Set(rows.map((r) => r.reply).filter(Boolean))]);
	const [reactionRows, replyRows] = await env.DB.batch([
		env.DB.prepare("SELECT message, uuid, emoji FROM reactions WHERE message IN (SELECT value FROM json_each(?1))").bind(ids),
		env.DB.prepare("SELECT id, sender, kind, text FROM messages WHERE chat = ?1 AND id IN (SELECT value FROM json_each(?2))").bind(chat.id, replyIds),
	]);
	const reactions = new Map();
	for (const r of reactionRows.results) {
		if (!reactions.has(r.message)) reactions.set(r.message, {});
		(reactions.get(r.message)[r.emoji] ||= []).push(r.uuid);
	}
	const replies = new Map(replyRows.results.map((r) => [r.id, { id: r.id, sender: r.sender, kind: r.kind, text: r.kind === "deleted" ? "" : r.text.slice(0, 120) }]));
	const list = members.results.map((m) => {
		const online = isOnline(m, t);
		return { uuid: m.uuid, name: m.name, online, server: online ? m.server : null, read: m.read };
	});
	const other = chat.kind === "dm" ? list.find((m) => m.uuid !== me.uuid) : null;
	return json({
		chat: { id: chat.id, kind: chat.kind, owner: chat.owner, name: chat.kind === "dm" ? other?.name || "?" : chat.name || "Gruppe", partner: other?.uuid || null },
		members: list,
		messages: rows.map((r) => messageJson(r, reactions, replies)),
		more,
	});
}

async function insertMessage(env, chat, sender, kind, text, data = null, reply = null) {
	const ms = Date.now();
	const row = await env.DB.prepare("INSERT INTO messages (chat, sender, sent, kind, text, reply, data) VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7) RETURNING *")
		.bind(chat, sender, ms, kind, text, reply, data ? JSON.stringify(data) : null).first();
	const statements = [
		env.DB.prepare("UPDATE chats SET last = ?2 WHERE id = ?1").bind(chat, ms),
		// was man selbst schreibt, hat man gelesen
		env.DB.prepare("UPDATE chat_members SET read = MAX(read, ?3) WHERE chat = ?1 AND uuid = ?2").bind(chat, sender, row.id),
	];
	if (row.id % 100 === 0) {
		// ab und zu aufräumen: pro Chat bleiben die letzten KEEP_MESSAGES Nachrichten
		statements.push(
			env.DB.prepare(`DELETE FROM messages WHERE chat = ?1 AND id <= (SELECT id FROM messages WHERE chat = ?1 ORDER BY id DESC LIMIT 1 OFFSET ?2)`)
				.bind(chat, KEEP_MESSAGES),
			env.DB.prepare("DELETE FROM reactions WHERE message NOT IN (SELECT id FROM messages)"),
		);
	}
	await env.DB.batch(statements);
	return row;
}

async function sendMessage(request, env, me) {
	const req = await body(request);
	let chatId = req.chat;
	if (req.to !== undefined) {
		if (!UUID_RE.test(req.to || "") || req.to === me.uuid) throw new HttpError(400, "Ungültige UUID");
		if (!(await isFriend(env, me.uuid, req.to))) throw new HttpError(403, "Nachrichten gehen nur an Freunde.");
		chatId = dmId(me.uuid, req.to);
		const t = now();
		await env.DB.batch([
			env.DB.prepare("INSERT OR IGNORE INTO chats (id, kind, created, last) VALUES (?1, 'dm', ?2, ?3)").bind(chatId, t, Date.now()),
			env.DB.prepare("INSERT OR IGNORE INTO chat_members (chat, uuid, joined) VALUES (?1, ?2, ?3), (?1, ?4, ?3)").bind(chatId, me.uuid, t, req.to),
		]);
	}
	const chat = await membership(env, chatId, me.uuid);
	if (chat.kind === "dm" && !(await isFriend(env, me.uuid, dmPartner(chat.id, me.uuid)))) throw new HttpError(403, "Ihr seid keine Freunde mehr.");
	const kind = req.kind || "text";
	let text = typeof req.text === "string" ? req.text.trim() : "";
	let data = null;
	if (kind === "text") {
		if (!text) throw new HttpError(400, "Die Nachricht ist leer.");
		if (text.length > MAX_MESSAGE_CHARS) throw new HttpError(400, `Höchstens ${MAX_MESSAGE_CHARS} Zeichen.`);
	} else if (kind === "invite") {
		const server = String(req.data?.server || "").trim();
		if (!ADDRESS_RE.test(server)) throw new HttpError(400, "Ungültige Serveradresse");
		data = { server };
		text = text.slice(0, 200);
	} else if (kind === "image") {
		const id = String(req.data?.image || "");
		const image = IMAGE_ID_RE.test(id) && (await env.DB.prepare("SELECT id FROM images WHERE id = ?1 AND owner = ?2").bind(id, me.uuid).first());
		if (!image) throw new HttpError(400, "Das Bild fehlt, lade es nochmal hoch.");
		data = { image: id, w: intIn(req.data.w, 1, 10000), h: intIn(req.data.h, 1, 10000) };
		text = text.slice(0, 200);
	} else {
		throw new HttpError(400, "Unbekannte Nachrichtenart");
	}
	let reply = null;
	if (req.reply !== undefined && req.reply !== null) {
		const r = intIn(req.reply, 1, Number.MAX_SAFE_INTEGER);
		if (!r || !(await env.DB.prepare("SELECT 1 FROM messages WHERE id = ?1 AND chat = ?2").bind(r, chat.id).first())) {
			throw new HttpError(400, "Die Nachricht, auf die du antwortest, gibt es nicht mehr.");
		}
		reply = r;
	}
	const row = await insertMessage(env, chat.id, me.uuid, kind, text, data, reply);
	return json({ message: messageJson(row), chat: chat.id });
}

async function messageForMember(env, id, uuid) {
	const message = intIn(id, 1, Number.MAX_SAFE_INTEGER);
	const row = message && await env.DB.prepare("SELECT m.* FROM messages m JOIN chat_members cm ON cm.chat = m.chat AND cm.uuid = ?2 WHERE m.id = ?1")
		.bind(message, uuid).first();
	if (!row) throw new HttpError(404, "Diese Nachricht gibt es nicht (mehr).");
	return row;
}

async function react(request, env, me) {
	const { message, emoji } = await body(request);
	if (!REACTIONS.includes(emoji)) throw new HttpError(400, "Unbekannte Reaktion");
	const row = await messageForMember(env, message, me.uuid);
	const had = await env.DB.prepare("SELECT 1 FROM reactions WHERE message = ?1 AND uuid = ?2 AND emoji = ?3").bind(row.id, me.uuid, emoji).first();
	await env.DB.prepare(had ? "DELETE FROM reactions WHERE message = ?1 AND uuid = ?2 AND emoji = ?3"
		: "INSERT INTO reactions (message, uuid, emoji) VALUES (?1, ?2, ?3)").bind(row.id, me.uuid, emoji).run();
	return json({ ok: true, active: !had });
}

async function markRead(request, env, me) {
	const req = await body(request);
	const chat = await membership(env, req.chat, me.uuid);
	const upTo = intIn(req.message, 0, Number.MAX_SAFE_INTEGER);
	if (upTo === null) throw new HttpError(400, "Ungültige Nachricht");
	await env.DB.prepare("UPDATE chat_members SET read = MAX(read, ?3) WHERE chat = ?1 AND uuid = ?2").bind(chat.id, me.uuid, upTo).run();
	return json({ ok: true });
}

async function deleteMessage(request, env, me) {
	const row = await messageForMember(env, (await body(request)).message, me.uuid);
	if (row.sender !== me.uuid || row.kind === "system") throw new HttpError(403, "Du kannst nur deine eigenen Nachrichten löschen.");
	await env.DB.batch([
		env.DB.prepare("UPDATE messages SET kind = 'deleted', text = '', data = NULL, edited = ?2 WHERE id = ?1").bind(row.id, Date.now()),
		env.DB.prepare("DELETE FROM reactions WHERE message = ?1").bind(row.id),
	]);
	return json({ ok: true });
}

function groupName(name) {
	const clean = String(name || "").trim().replace(/\s+/g, " ").slice(0, 32);
	if (!clean) throw new HttpError(400, "Gib der Gruppe einen Namen.");
	return clean;
}

async function createGroup(request, env, me) {
	const req = await body(request);
	const name = groupName(req.name);
	const members = [...new Set((Array.isArray(req.members) ? req.members : []).filter((u) => typeof u === "string" && UUID_RE.test(u) && u !== me.uuid))];
	if (members.length < 1) throw new HttpError(400, "Wähle mindestens einen Freund aus.");
	if (members.length + 1 > MAX_GROUP_MEMBERS) throw new HttpError(400, `Eine Gruppe hat höchstens ${MAX_GROUP_MEMBERS} Mitglieder.`);
	for (const u of members) if (!(await isFriend(env, me.uuid, u))) throw new HttpError(403, "In Gruppen kannst du nur Freunde holen.");
	const id = `g${randomHex(8)}`;
	const t = now();
	await env.DB.batch([
		env.DB.prepare("INSERT INTO chats (id, kind, name, owner, created, last) VALUES (?1, 'group', ?2, ?3, ?4, ?5)").bind(id, name, me.uuid, t, Date.now()),
		...[me.uuid, ...members].map((u) => env.DB.prepare("INSERT INTO chat_members (chat, uuid, joined) VALUES (?1, ?2, ?3)").bind(id, u, t)),
	]);
	await insertMessage(env, id, me.uuid, "system", `${me.name} hat die Gruppe „${name}“ erstellt`);
	return json({ chat: id });
}

async function groupChat(env, chatId, me) {
	const chat = await membership(env, chatId, me.uuid);
	if (chat.kind !== "group") throw new HttpError(400, "Das geht nur in Gruppen.");
	return chat;
}

async function groupAdd(request, env, me) {
	const req = await body(request);
	const chat = await groupChat(env, req.chat, me);
	if (!UUID_RE.test(req.uuid || "") || !(await isFriend(env, me.uuid, req.uuid))) throw new HttpError(403, "Du kannst nur deine Freunde hinzufügen.");
	const count = await env.DB.prepare("SELECT COUNT(*) AS n FROM chat_members WHERE chat = ?1").bind(chat.id).first();
	if (count.n >= MAX_GROUP_MEMBERS) throw new HttpError(400, "Die Gruppe ist voll.");
	const other = await env.DB.prepare("SELECT name FROM players WHERE uuid = ?1").bind(req.uuid).first();
	await env.DB.prepare("INSERT OR IGNORE INTO chat_members (chat, uuid, joined) VALUES (?1, ?2, ?3)").bind(chat.id, req.uuid, now()).run();
	await insertMessage(env, chat.id, me.uuid, "system", `${me.name} hat ${other?.name || "jemanden"} hinzugefügt`);
	return json({ ok: true });
}

async function removeFromChat(env, chatId, uuid, notice, actor) {
	await env.DB.prepare("DELETE FROM chat_members WHERE chat = ?1 AND uuid = ?2").bind(chatId, uuid).run();
	const left = await env.DB.prepare("SELECT COUNT(*) AS n FROM chat_members WHERE chat = ?1").bind(chatId).first();
	if (!left.n) {
		// niemand mehr da: Chat ganz löschen
		await env.DB.batch([
			env.DB.prepare("DELETE FROM reactions WHERE message IN (SELECT id FROM messages WHERE chat = ?1)").bind(chatId),
			env.DB.prepare("DELETE FROM messages WHERE chat = ?1").bind(chatId),
			env.DB.prepare("DELETE FROM chats WHERE id = ?1").bind(chatId),
		]);
	} else if (notice) {
		await insertMessage(env, chatId, actor, "system", notice);
	}
}

async function groupLeave(request, env, me) {
	const chat = await groupChat(env, (await body(request)).chat, me);
	await removeFromChat(env, chat.id, me.uuid, `${me.name} hat die Gruppe verlassen`, me.uuid);
	return json({ ok: true });
}

async function groupRename(request, env, me) {
	const req = await body(request);
	const chat = await groupChat(env, req.chat, me);
	const name = groupName(req.name);
	await env.DB.prepare("UPDATE chats SET name = ?2 WHERE id = ?1").bind(chat.id, name).run();
	await insertMessage(env, chat.id, me.uuid, "system", `${me.name} hat die Gruppe in „${name}“ umbenannt`);
	return json({ ok: true });
}

// ---------- Minispiele im Chat: Tic-Tac-Toe und Vier gewinnt ----------

const GAMES = { ttt: { name: "Tic-Tac-Toe", size: 9 }, c4: { name: "Vier gewinnt", size: 42 } };
const TTT_LINES = [[0, 1, 2], [3, 4, 5], [6, 7, 8], [0, 3, 6], [1, 4, 7], [2, 5, 8], [0, 4, 8], [2, 4, 6]];

function winner(game, board) {
	if (game === "ttt") {
		for (const [a, b, c] of TTT_LINES) if (board[a] !== "." && board[a] === board[b] && board[a] === board[c]) return board[a];
	} else {
		for (let r = 0; r < 6; r++) {
			for (let c = 0; c < 7; c++) {
				const v = board[r * 7 + c];
				if (v === ".") continue;
				for (const [dr, dc] of [[0, 1], [1, 0], [1, 1], [1, -1]]) {
					let n = 1;
					while (n < 4) {
						const rr = r + dr * n;
						const cc = c + dc * n;
						if (rr < 0 || rr >= 6 || cc < 0 || cc >= 7 || board[rr * 7 + cc] !== v) break;
						n++;
					}
					if (n === 4) return v;
				}
			}
		}
	}
	return board.includes(".") ? null : "draw";
}

async function newGame(request, env, me) {
	const req = await body(request);
	const chat = await membership(env, req.chat, me.uuid);
	const game = GAMES[req.game];
	if (!game) throw new HttpError(400, "Unbekanntes Spiel");
	const data = { game: req.game, board: ".".repeat(game.size), players: [me.uuid, chat.kind === "dm" ? dmPartner(chat.id, me.uuid) : null], turn: 0, winner: null };
	const row = await insertMessage(env, chat.id, me.uuid, "game", game.name, data);
	return json({ message: messageJson(row) });
}

async function gameMove(request, env, me) {
	const req = await body(request);
	const row = await messageForMember(env, req.message, me.uuid);
	if (row.kind !== "game") throw new HttpError(400, "Das ist kein Spiel.");
	const d = JSON.parse(row.data);
	if (d.winner) throw new HttpError(400, "Das Spiel ist schon vorbei.");
	// in Gruppen spielt mit, wer zuerst als Zweiter zieht
	if (d.players[1] === null && me.uuid !== d.players[0]) d.players[1] = me.uuid;
	const seat = d.players.indexOf(me.uuid);
	if (seat < 0) throw new HttpError(403, "Du spielst hier nicht mit.");
	if (seat !== d.turn) throw new HttpError(400, "Du bist nicht dran.");
	const mark = seat === 0 ? "x" : "o";
	const board = d.board.split("");
	if (d.game === "ttt") {
		const cell = intIn(req.cell, 0, 8);
		if (cell === null || board[cell] !== ".") throw new HttpError(400, "Das Feld ist schon belegt.");
		board[cell] = mark;
	} else {
		const col = intIn(req.cell, 0, 6);
		if (col === null) throw new HttpError(400, "Ungültige Spalte");
		let r = 5;
		while (r >= 0 && board[r * 7 + col] !== ".") r--;
		if (r < 0) throw new HttpError(400, "Die Spalte ist voll.");
		board[r * 7 + col] = mark;
		d.last = r * 7 + col;
	}
	d.board = board.join("");
	const w = winner(d.game, d.board);
	if (w) d.winner = w === "draw" ? "draw" : d.players[w === "x" ? 0 : 1];
	d.turn = 1 - d.turn;
	const ms = Date.now();
	// nur speichern, wenn in der Zwischenzeit niemand anders gezogen hat
	const updated = await env.DB.prepare("UPDATE messages SET data = ?1, edited = ?2 WHERE id = ?3 AND data = ?4 RETURNING id")
		.bind(JSON.stringify(d), ms, row.id, row.data).first();
	if (!updated) throw new HttpError(409, "Jemand war schneller, lade neu.");
	await env.DB.prepare("UPDATE chats SET last = ?2 WHERE id = ?1").bind(row.chat, ms).run();
	return json({ message: messageJson({ ...row, data: JSON.stringify(d), edited: ms }) });
}

// ---------- Bilder im Chat ----------

function looksLike(type, b) {
	if (type === "image/png") return b[0] === 0x89 && b[1] === 0x50 && b[2] === 0x4e && b[3] === 0x47;
	if (type === "image/jpeg") return b[0] === 0xff && b[1] === 0xd8;
	if (type === "image/gif") return b[0] === 0x47 && b[1] === 0x49 && b[2] === 0x46;
	if (type === "image/webp") return b[8] === 0x57 && b[9] === 0x45 && b[10] === 0x42 && b[11] === 0x50;
	return false;
}

async function putImage(request, env, me) {
	const type = (request.headers.get("content-type") || "").split(";")[0].trim().toLowerCase();
	if (!IMAGE_TYPES.includes(type)) throw new HttpError(415, "Nur PNG, JPEG, WebP oder GIF");
	const data = new Uint8Array(await request.arrayBuffer());
	if (data.length > MAX_IMAGE_BYTES) throw new HttpError(413, "Das Bild ist zu groß (höchstens 450 KB).");
	if (data.length < 16 || !looksLike(type, data)) throw new HttpError(400, "Das ist kein Bild.");
	const id = randomHex(16);
	const t = now();
	await env.DB.batch([
		env.DB.prepare("INSERT INTO images (id, owner, mime, data, created) VALUES (?1, ?2, ?3, ?4, ?5)").bind(id, me.uuid, type, data, t),
		env.DB.prepare("DELETE FROM images WHERE created < ?1").bind(t - 90 * 86400),
	]);
	return json({ id });
}

async function getImage(id, env) {
	if (!IMAGE_ID_RE.test(id)) throw new HttpError(404, "Bild nicht gefunden");
	const row = await env.DB.prepare("SELECT mime, data FROM images WHERE id = ?1").bind(id).first();
	if (!row) throw new HttpError(404, "Bild nicht gefunden");
	return new Response(new Uint8Array(row.data), {
		headers: { "content-type": row.mime, "cache-control": "private, max-age=86400", "x-content-type-options": "nosniff" },
	});
}

// Köpfe für die Handy-App: der Skin eines Spielers über Mojang (eine Stunde zwischengespeichert)
async function getSkin(uuid) {
	if (!UUID_RE.test(uuid)) throw new HttpError(404, "Unbekannt");
	const cache = globalThis.caches?.default;
	const key = new Request(`https://lagerfeuer.skin/${uuid}`);
	const hit = cache && (await cache.match(key));
	if (hit) return hit;
	const r = await fetch(`https://sessionserver.mojang.com/session/minecraft/profile/${uuid}`);
	if (r.status !== 200) throw new HttpError(404, "Kein Skin");
	const profile = await r.json();
	const textures = (profile.properties || []).find((p) => p.name === "textures");
	const skinUrl = textures && JSON.parse(atob(textures.value)).textures?.SKIN?.url;
	if (!skinUrl) throw new HttpError(404, "Kein Skin");
	const png = await fetch(skinUrl.replace("http://", "https://"));
	if (png.status !== 200) throw new HttpError(404, "Kein Skin");
	const response = new Response(await png.arrayBuffer(), { headers: { "content-type": "image/png", "cache-control": "public, max-age=3600" } });
	if (cache) await cache.put(key, response.clone());
	return response;
}

// ---------- Party: zusammen von Server zu Server ----------

function partyStatements(env, me, t) {
	return [
		env.DB.prepare(`SELECT p.id, p.leader, p.chat, pm.uuid, pm.joined, pl.name, pl.online, pl.server, pl.last_seen
			FROM party_members mine JOIN parties p ON p.id = mine.party JOIN party_members pm ON pm.party = p.id
			JOIN players pl ON pl.uuid = pm.uuid WHERE mine.uuid = ?1 ORDER BY pm.joined`).bind(me.uuid),
		env.DB.prepare(`SELECT i.party, i.created, p.leader, pl.name AS leaderName FROM party_invites i JOIN parties p ON p.id = i.party
			JOIN players pl ON pl.uuid = p.leader WHERE i.uuid = ?1 AND i.created > ?2`).bind(me.uuid, t - INVITE_SECONDS),
	];
}

function partyFrom(members, invites, t) {
	const party = members.length ? {
		id: members[0].id, leader: members[0].leader, chat: members[0].chat,
		members: members.map((m) => {
			const online = isOnline(m, t);
			return { uuid: m.uuid, name: m.name, online, server: online ? m.server : null };
		}),
	} : null;
	return { party, partyInvites: invites.map((i) => ({ party: i.party, leader: i.leader, leaderName: i.leaderName, expires: i.created + INVITE_SECONDS })) };
}

async function partyState(env, me) {
	const t = now();
	const [members, invites] = await env.DB.batch(partyStatements(env, me, t));
	return partyFrom(members.results, invites.results, t);
}

async function myParty(env, uuid) {
	return env.DB.prepare("SELECT p.id, p.leader, p.chat FROM party_members pm JOIN parties p ON p.id = pm.party WHERE pm.uuid = ?1").bind(uuid).first();
}

async function partyCreate(env, me) {
	if (await myParty(env, me.uuid)) throw new HttpError(400, "Du bist schon in einer Party.");
	const id = randomHex(8);
	const chat = `p${id}`;
	const t = now();
	await env.DB.batch([
		env.DB.prepare("INSERT INTO parties (id, leader, chat, created) VALUES (?1, ?2, ?3, ?4)").bind(id, me.uuid, chat, t),
		env.DB.prepare("INSERT INTO party_members (party, uuid, joined) VALUES (?1, ?2, ?3)").bind(id, me.uuid, Date.now()),
		env.DB.prepare("INSERT INTO chats (id, kind, name, owner, created, last) VALUES (?1, 'party', 'Party', ?2, ?3, ?4)").bind(chat, me.uuid, t, Date.now()),
		env.DB.prepare("INSERT INTO chat_members (chat, uuid, joined) VALUES (?1, ?2, ?3)").bind(chat, me.uuid, t),
	]);
	await insertMessage(env, chat, me.uuid, "system", `${me.name} hat eine Party gestartet`);
	return json(await partyState(env, me));
}

async function partyInvite(request, env, me) {
	const { uuid } = await body(request);
	if (!UUID_RE.test(uuid || "") || !(await isFriend(env, me.uuid, uuid))) throw new HttpError(403, "Du kannst nur Freunde einladen.");
	let party = await myParty(env, me.uuid);
	if (!party) {
		await partyCreate(env, me);
		party = await myParty(env, me.uuid);
	}
	const count = await env.DB.prepare("SELECT COUNT(*) AS n FROM party_members WHERE party = ?1").bind(party.id).first();
	if (count.n >= MAX_PARTY) throw new HttpError(400, `Eine Party hat höchstens ${MAX_PARTY} Leute.`);
	if (await env.DB.prepare("SELECT 1 FROM party_members WHERE party = ?1 AND uuid = ?2").bind(party.id, uuid).first()) {
		return json({ status: "member" });
	}
	await env.DB.prepare("INSERT OR REPLACE INTO party_invites (party, uuid, created) VALUES (?1, ?2, ?3)").bind(party.id, uuid, now()).run();
	return json({ status: "invited", party: party.id });
}

async function leaveParty(env, me, party) {
	await env.DB.prepare("DELETE FROM party_members WHERE party = ?1 AND uuid = ?2").bind(party.id, me.uuid).run();
	const next = await env.DB.prepare("SELECT uuid FROM party_members WHERE party = ?1 ORDER BY joined LIMIT 1").bind(party.id).first();
	if (!next) {
		await env.DB.batch([
			env.DB.prepare("DELETE FROM party_invites WHERE party = ?1").bind(party.id),
			env.DB.prepare("DELETE FROM parties WHERE id = ?1").bind(party.id),
		]);
		await removeFromChat(env, party.chat, me.uuid, null, me.uuid);
		return;
	}
	if (party.leader === me.uuid) await env.DB.prepare("UPDATE parties SET leader = ?2 WHERE id = ?1").bind(party.id, next.uuid).run();
	await removeFromChat(env, party.chat, me.uuid, `${me.name} hat die Party verlassen`, me.uuid);
}

async function partyJoin(request, env, me) {
	const { party: id } = await body(request);
	const invite = await env.DB.prepare(`SELECT p.id, p.leader, p.chat FROM party_invites i JOIN parties p ON p.id = i.party
		WHERE i.party = ?1 AND i.uuid = ?2 AND i.created > ?3`).bind(String(id || ""), me.uuid, now() - INVITE_SECONDS).first();
	if (!invite) throw new HttpError(404, "Die Einladung ist abgelaufen.");
	const current = await myParty(env, me.uuid);
	if (current && current.id !== invite.id) await leaveParty(env, me, current);
	const count = await env.DB.prepare("SELECT COUNT(*) AS n FROM party_members WHERE party = ?1").bind(invite.id).first();
	if (count.n >= MAX_PARTY) throw new HttpError(400, "Die Party ist schon voll.");
	const t = now();
	await env.DB.batch([
		env.DB.prepare("INSERT OR IGNORE INTO party_members (party, uuid, joined) VALUES (?1, ?2, ?3)").bind(invite.id, me.uuid, Date.now()),
		env.DB.prepare("INSERT OR IGNORE INTO chat_members (chat, uuid, joined) VALUES (?1, ?2, ?3)").bind(invite.chat, me.uuid, t),
		env.DB.prepare("DELETE FROM party_invites WHERE party = ?1 AND uuid = ?2").bind(invite.id, me.uuid),
	]);
	await insertMessage(env, invite.chat, me.uuid, "system", `${me.name} ist der Party beigetreten`);
	return json(await partyState(env, me));
}

async function partyDecline(request, env, me) {
	const { party } = await body(request);
	await env.DB.prepare("DELETE FROM party_invites WHERE party = ?1 AND uuid = ?2").bind(String(party || ""), me.uuid).run();
	return json({ ok: true });
}

async function partyLeave(env, me) {
	const party = await myParty(env, me.uuid);
	if (party) await leaveParty(env, me, party);
	return json(await partyState(env, me));
}

async function partyKick(request, env, me) {
	const { uuid } = await body(request);
	const party = await myParty(env, me.uuid);
	if (!party || party.leader !== me.uuid) throw new HttpError(403, "Nur der Party-Leiter kann jemanden entfernen.");
	if (!UUID_RE.test(uuid || "") || uuid === me.uuid) throw new HttpError(400, "Ungültige UUID");
	const other = await env.DB.prepare("SELECT p.name FROM party_members pm JOIN players p ON p.uuid = pm.uuid WHERE pm.party = ?1 AND pm.uuid = ?2")
		.bind(party.id, uuid).first();
	if (!other) throw new HttpError(404, "Dieser Spieler ist nicht in deiner Party.");
	await env.DB.prepare("DELETE FROM party_members WHERE party = ?1 AND uuid = ?2").bind(party.id, uuid).run();
	await removeFromChat(env, party.chat, uuid, `${me.name} hat ${other.name} aus der Party entfernt`, me.uuid);
	return json(await partyState(env, me));
}

// ---------- Emberleaf-Versionen (Webseite und Auto-Update) ----------
// Der Besitzer (Variable UPDATE_OWNER = sein Minecraft-Name) lädt neue Versionen auf der Webseite hoch (/upload).
// Die Dateien liegen in 8-MB-Stücken in Workers KV (Binding "FILES"), die Liste der Versionen in D1.
// Launcher und Webseite fragen /v1/update nach der neuesten Version und laden unter /download/<id>/<datei>.

const PART_BYTES = 8 * 1024 * 1024;
const MAX_FILE_BYTES = 200 * 1024 * 1024;
const FILE_KINDS = { launcher: ".exe", mod: ".jar", shaderpack: ".zip", resourcepack: ".zip" };
const FILE_RE = /^[A-Za-z0-9._+-]{1,80}\.(exe|jar|zip)$/;
const SHA_RE = /^[0-9a-f]{64}$/;
const VERSION_RE = /^\d{1,4}\.\d{1,4}\.\d{1,4}$/;
const RELEASE_RE = /^\d{10,16}$/;
const KEEP_DOWNLOADS = 3; // so viele Versionen bleiben herunterladbar, ältere stehen nur noch in der Liste
const CONTENT_TYPES = { exe: "application/vnd.microsoft.portable-executable", jar: "application/java-archive", zip: "application/zip" };
const LATEST_SQL = "SELECT * FROM site_releases WHERE state = 'live' ORDER BY published DESC, id DESC LIMIT 1";

function requireOwner(env, me) {
	if (!String(env.UPDATE_OWNER || "").trim()) {
		throw new HttpError(403, "Updates verteilen ist aus. Setz im Cloudflare-Dashboard die Variable UPDATE_OWNER auf deinen Minecraft-Namen.");
	}
	if (!isOwner(env, me)) throw new HttpError(403, "Nur der Besitzer von Emberleaf kann Updates hochladen.");
}

function requireFiles(env) {
	if (!env.FILES) throw new HttpError(500, "Der Datei-Speicher ist nicht verbunden (KV-Bindung „FILES“ fehlt).");
}

const partCount = (size) => Math.max(1, Math.ceil(size / PART_BYTES));
const partKey = (id, file, part) => `release/${id}/${file}/${part}`;

function newerVersion(a, b) {
	const x = String(a).split(".").map(Number);
	const y = String(b).split(".").map(Number);
	for (let i = 0; i < 3; i++) if (x[i] !== y[i]) return x[i] > y[i];
	return false;
}

function releaseJson(row) {
	const live = row.state === "live";
	return {
		id: row.id, version: row.version, notes: row.notes || "", published: row.published, available: live,
		files: JSON.parse(row.files).map((f) => ({ ...f, url: live ? `/download/${row.id}/${f.name}` : null })),
	};
}

async function optionalUser(request, env) {
	if (!(request.headers.get("authorization") || "").startsWith("Bearer ")) return null;
	try {
		return await authenticate(request, env);
	} catch {
		return null;
	}
}

async function latestRelease(request, env) {
	const row = await env.DB.prepare(LATEST_SQL).first();
	const me = await optionalUser(request, env);
	return json({ release: row ? releaseJson(row) : null, owner: Boolean(me && isOwner(env, me)) });
}

async function listReleases(env) {
	const { results } = await env.DB.prepare(
		"SELECT * FROM site_releases WHERE state IN ('live', 'archived') ORDER BY published DESC, id DESC LIMIT 40",
	).all();
	return json({ releases: results.map(releaseJson) });
}

// /download/<id>/<datei> (genau diese Version) oder /download/latest/<art> (z. B. latest/launcher)
async function download(request, path, env, ctx) {
	requireFiles(env);
	const [, , which, what] = path.split("/");
	let row;
	let file;
	if (which === "latest") {
		row = await env.DB.prepare(LATEST_SQL).first();
		file = row && JSON.parse(row.files).find((f) => f.kind === what || f.name === what);
	} else if (RELEASE_RE.test(which || "") && FILE_RE.test(what || "")) {
		row = await env.DB.prepare("SELECT * FROM site_releases WHERE id = ?1 AND state = 'live'").bind(which).first();
		file = row && JSON.parse(row.files).find((f) => f.name === what);
	}
	if (!row || !file) throw new HttpError(404, "Diese Datei gibt es nicht (mehr).");
	const headers = {
		"content-type": CONTENT_TYPES[file.name.split(".").pop()] || "application/octet-stream",
		"content-disposition": `attachment; filename="${file.name}"`,
		"cache-control": which === "latest" ? "no-store" : "public, max-age=86400",
		"x-content-type-options": "nosniff",
		"x-emberleaf-version": row.version,
		"x-emberleaf-sha256": file.sha256,
	};
	if (request.method === "HEAD") return new Response(null, { headers: { ...headers, "content-length": String(file.size) } });
	const first = await env.FILES.get(partKey(row.id, file.name, 0), { type: "stream" });
	if (!first) throw new HttpError(503, "Die Datei wurde gerade erst hochgeladen. Versuch es in einer Minute nochmal.");
	// FixedLengthStream (nur bei Cloudflare) schickt die Dateigröße mit, dann zeigt der Browser einen Fortschritt
	const { readable, writable } = typeof FixedLengthStream === "function" ? new FixedLengthStream(file.size) : new TransformStream();
	const pump = (async () => {
		let stream = first;
		for (let part = 0; part < partCount(file.size); part++) {
			if (part > 0) stream = await env.FILES.get(partKey(row.id, file.name, part), { type: "stream" });
			if (!stream) throw new Error(`Stück ${part} von ${file.name} fehlt`);
			await stream.pipeTo(writable, { preventClose: true });
		}
		await writable.close();
	})().catch(async (e) => {
		console.error(e);
		try {
			await writable.abort(e);
		} catch {
			// schon abgebrochen
		}
	});
	ctx?.waitUntil?.(pump);
	return new Response(readable, { headers });
}

async function deleteReleaseFiles(env, id) {
	const { results } = await env.DB.prepare("SELECT file, part FROM site_release_parts WHERE release = ?1").bind(id).all();
	if (env.FILES) await Promise.all(results.map((r) => env.FILES.delete(partKey(id, r.file, r.part))));
	await env.DB.prepare("DELETE FROM site_release_parts WHERE release = ?1").bind(id).run();
}

async function cleanupDrafts(env, before) {
	const { results } = await env.DB.prepare("SELECT id FROM site_releases WHERE state = 'draft' AND created < ?1").bind(before).all();
	for (const r of results) {
		await deleteReleaseFiles(env, r.id);
		await env.DB.prepare("DELETE FROM site_releases WHERE id = ?1").bind(r.id).run();
	}
}

async function beginRelease(request, env, me) {
	requireOwner(env, me);
	requireFiles(env);
	const { version, notes, files } = await body(request);
	if (!VERSION_RE.test(version || "")) throw new HttpError(400, "Die Version bitte als drei Zahlen mit Punkt, z. B. 0.0.8.");
	const live = await env.DB.prepare(LATEST_SQL).first();
	if (live && !newerVersion(version, live.version)) throw new HttpError(400, `Die Version muss höher sein als ${live.version}.`);
	if (!Array.isArray(files) || files.length < 1 || files.length > 6) throw new HttpError(400, "1 bis 6 Dateien pro Update");
	const seen = new Set();
	const clean = files.map((f) => {
		const size = Number(f?.size);
		const kind = f?.kind;
		if (!f || !Object.hasOwn(FILE_KINDS, kind) || !FILE_RE.test(f.name || "") || !f.name.endsWith(FILE_KINDS[kind])
			|| !SHA_RE.test(f.sha256 || "") || !Number.isInteger(size) || size < 1 || size > MAX_FILE_BYTES || seen.has(f.name)) {
			throw new HttpError(400, `Ungültige Datei im Update: ${String(f?.name || "?").slice(0, 80)}`);
		}
		seen.add(f.name);
		return { name: f.name, kind, size, sha256: f.sha256 };
	});
	if (clean.filter((f) => f.kind === "launcher").length > 1) throw new HttpError(400, "Nur eine Launcher-Datei pro Update.");
	await cleanupDrafts(env, now() - 86400);
	const id = String(Date.now());
	await env.DB.prepare("INSERT INTO site_releases (id, version, notes, files, state, created, by) VALUES (?1, ?2, ?3, ?4, 'draft', ?5, ?6)")
		.bind(id, version, String(notes || "").slice(0, 4000), JSON.stringify(clean), now(), me.uuid).run();
	return json({ id, partBytes: PART_BYTES });
}

async function draft(env, id) {
	if (!RELEASE_RE.test(id || "")) throw new HttpError(400, "Ungültige Update-ID");
	const row = await env.DB.prepare("SELECT id, version, files, state FROM site_releases WHERE id = ?1").bind(id).first();
	if (!row) throw new HttpError(404, "Dieses Update gibt es nicht (mehr).");
	if (row.state !== "draft") throw new HttpError(400, "Dieses Update ist schon veröffentlicht.");
	return { id: row.id, version: row.version, files: JSON.parse(row.files) };
}

async function putPart(request, url, env, me) {
	requireOwner(env, me);
	requireFiles(env);
	const release = await draft(env, url.searchParams.get("release"));
	const file = release.files.find((f) => f.name === url.searchParams.get("file"));
	const part = Number(url.searchParams.get("part"));
	if (!file || !Number.isInteger(part) || part < 0 || part >= partCount(file.size)) throw new HttpError(400, "Ungültiges Stück");
	const data = await request.arrayBuffer();
	const expected = Math.min(PART_BYTES, file.size - part * PART_BYTES);
	if (data.byteLength !== expected) throw new HttpError(400, `Stück hat ${data.byteLength} statt ${expected} Bytes`);
	await env.FILES.put(partKey(release.id, file.name, part), data);
	await env.DB.prepare("INSERT OR REPLACE INTO site_release_parts (release, file, part, size) VALUES (?1, ?2, ?3, ?4)")
		.bind(release.id, file.name, part, data.byteLength).run();
	return json({ ok: true });
}

async function publishRelease(request, env, me) {
	requireOwner(env, me);
	const release = await draft(env, (await body(request)).id);
	const { results } = await env.DB.prepare("SELECT file, COUNT(*) AS n FROM site_release_parts WHERE release = ?1 GROUP BY file")
		.bind(release.id).all();
	for (const f of release.files) {
		const have = results.find((r) => r.file === f.name)?.n || 0;
		if (have !== partCount(f.size)) throw new HttpError(400, `${f.name} ist noch nicht komplett hochgeladen (${have}/${partCount(f.size)}).`);
	}
	const live = await env.DB.prepare(LATEST_SQL).first();
	if (live && !newerVersion(release.version, live.version)) throw new HttpError(400, `Inzwischen ist ${live.version} online. Nimm eine höhere Version.`);
	await env.DB.prepare("UPDATE site_releases SET state = 'live', published = ?1 WHERE id = ?2").bind(now(), release.id).run();
	// ältere Versionen: Dateien aufräumen, damit der Speicher klein bleibt; der Eintrag bleibt für die Liste
	const { results: old } = await env.DB.prepare(
		"SELECT id FROM site_releases WHERE state = 'live' ORDER BY published DESC, id DESC LIMIT -1 OFFSET ?1",
	).bind(KEEP_DOWNLOADS).all();
	for (const r of old) {
		await deleteReleaseFiles(env, r.id);
		await env.DB.prepare("UPDATE site_releases SET state = 'archived' WHERE id = ?1").bind(r.id).run();
	}
	return json({ ok: true, id: release.id, version: release.version });
}

async function discardRelease(request, env, me) {
	requireOwner(env, me);
	const release = await draft(env, (await body(request)).id);
	await deleteReleaseFiles(env, release.id);
	await env.DB.prepare("DELETE FROM site_releases WHERE id = ?1").bind(release.id).run();
	return json({ ok: true });
}

// ---------- Webseite (/, /upload, Dateien unter /s/) und Handy-App (/app) ----------

const SITE_FILES = /*@SITE_FILES@*/ {};
const APP_FILES = /*@APP_FILES@*/ {};
const SITE_PAGES = { "/": "index.html", "/upload": "upload.html" };
const SITE_CSP = "default-src 'self'; img-src 'self' data: blob:; style-src 'self' 'unsafe-inline'; script-src 'self'; font-src 'self'; "
	+ "connect-src 'self'; manifest-src 'self'; base-uri 'none'; frame-ancestors 'none'; form-action 'self'";
const APP_CSP = "default-src 'self'; img-src 'self' data: blob:; style-src 'self' 'unsafe-inline'; script-src 'self'; connect-src 'self'; "
	+ "manifest-src 'self'; worker-src 'self'; base-uri 'none'; frame-ancestors 'none'; form-action 'none'";
const decoded = new Map();

function staticResponse(request, key, file, { page = false, csp = "" } = {}) {
	const etag = file.etag ? `"${file.etag}"` : null;
	// Schriften ändern sich nie, alles andere fragt der Browser jedes Mal kurz nach (ETag)
	const cache = file.type.startsWith("font/") ? "public, max-age=604800" : "no-cache";
	if (etag && request.headers.get("if-none-match") === etag) return new Response(null, { status: 304, headers: { etag, "cache-control": cache } });
	let data = file.text;
	if (file.base64) {
		data = decoded.get(key);
		if (!data) {
			data = Uint8Array.from(atob(file.base64), (c) => c.charCodeAt(0));
			decoded.set(key, data);
		}
	}
	const headers = { "content-type": file.type, "cache-control": cache, "x-content-type-options": "nosniff" };
	if (etag) headers.etag = etag;
	if (page) {
		headers["content-security-policy"] = csp;
		headers["referrer-policy"] = "no-referrer";
	}
	return new Response(data, { headers });
}

// Große Bilder der Webseite liegen in KV (media/<name>, hochgeladen von tools/upload_media.py). build.py hängt an jeden
// Verweis ?v=<Prüfsumme>, deshalb dürfen Browser sie ein Jahr lang behalten.
const MEDIA_RE = /^[a-z0-9-]{1,60}\.(webp|png|jpg)$/;

async function mediaFile(url, env) {
	const name = url.pathname.slice(3);
	if (!MEDIA_RE.test(name) || !env.FILES) return new Response("Nicht gefunden", { status: 404 });
	const { value, metadata } = await env.FILES.getWithMetadata(`media/${name}`, { type: "stream" });
	if (!value) return new Response("Nicht gefunden", { status: 404 });
	return new Response(value, {
		headers: {
			"content-type": metadata?.type || "image/webp",
			"cache-control": url.searchParams.has("v") ? "public, max-age=31536000, immutable" : "public, max-age=3600",
			"x-content-type-options": "nosniff",
		},
	});
}

function siteFile(request, url) {
	const page = SITE_PAGES[url.pathname.replace(/\/+$/, "") || "/"];
	const name = page || (url.pathname.startsWith("/s/") ? url.pathname.slice(3) : null);
	if (!name) return null;
	const file = SITE_FILES[name];
	if (!file) return new Response("Nicht gefunden", { status: 404 });
	return staticResponse(request, `s/${name}`, file, { page: Boolean(page), csp: SITE_CSP });
}

function appFile(request, url) {
	if (url.pathname === "/app") return Response.redirect(`${url.origin}/app/`, 302);
	const name = url.pathname.slice("/app/".length) || "index.html";
	const file = APP_FILES[name];
	if (!file) return new Response("Nicht gefunden", { status: 404 });
	return staticResponse(request, `app/${name}`, file, { page: name === "index.html", csp: APP_CSP });
}
