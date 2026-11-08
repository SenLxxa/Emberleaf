// Testet "Anmelden mit Microsoft" der Webseite, ohne echtes Microsoft: Microsoft, Xbox Live und Mojang werden
// nachgespielt, die Datenbank ist SQLite aus Node.
//   node emberleaf-server/test/microsoft-login.test.mjs      (nach python build.py)
import { DatabaseSync } from "node:sqlite";
import worker from "../worker.js";

const db = new DatabaseSync(":memory:");
class Stmt {
  constructor(sql) { this.sql = sql; this.args = []; }
  bind(...args) { this.args = args; return this; }
  exec() { return db.prepare(this.sql).all(...this.args).map((r) => ({ ...r })); }
  async first() { return this.exec()[0] ?? null; }
  async all() { return { results: this.exec() }; }
  async run() { this.exec(); return { success: true }; }
}
const env = {
  MS_CLIENT_ID: "c921157f-fcc0-47b6-9eb0-ec3eaa8c6d76", UPDATE_OWNER: "004910347cbb4a3fa020e611711bb4f3",
  DB: { prepare: (sql) => new Stmt(sql), batch: async (stmts) => stmts.map((s) => ({ results: s.exec() })) },
};
const ME = { id: "004910347cbb4a3fa020e611711bb4f3", name: "SenLxca" };

let failures = 0;
const check = (name, cond, extra = "") => {
  if (!cond) failures++;
  console.log(`${cond ? "✓" : "✗"} ${name}${extra ? ` – ${extra}` : ""}`);
};

// Microsoft, Xbox, Mojang nachspielen
const seen = {};
let mode = "ok";
const realFetch = globalThis.fetch;
globalThis.fetch = async (input, init = {}) => {
  const url = String(input instanceof Request ? input.url : input);
  const body = init.body ? String(init.body) : "";
  const reply = (status, data) => new Response(JSON.stringify(data), { status, headers: { "content-type": "application/json" } });
  if (url.startsWith("https://login.microsoftonline.com/consumers/oauth2/v2.0/token")) {
    seen.token = new URLSearchParams(body);
    return seen.token.get("code") === "gut" ? reply(200, { access_token: "ms-token", refresh_token: "r" }) : reply(400, { error: "invalid_grant" });
  }
  if (url === "https://user.auth.xboxlive.com/user/authenticate") {
    seen.xbl = JSON.parse(body);
    return reply(200, { Token: "xbl-token", DisplayClaims: { xui: [{ uhs: "1234" }] } });
  }
  if (url === "https://xsts.auth.xboxlive.com/xsts/authorize") {
    if (mode === "child") return reply(401, { XErr: 2148916238 });
    return reply(200, { Token: "xsts-token", DisplayClaims: { xui: [{ uhs: "1234" }] } });
  }
  if (url === "https://api.minecraftservices.com/authentication/login_with_xbox") {
    seen.mc = JSON.parse(body);
    return reply(200, { access_token: "mc-token" });
  }
  if (url === "https://api.minecraftservices.com/minecraft/profile") {
    if (mode === "noGame") return reply(404, { error: "NOT_FOUND" });
    seen.profileAuth = new Headers(init.headers).get("authorization");
    return reply(200, ME);
  }
  return realFetch(input, init);
};

const call = (path, headers = {}) => worker.fetch(new Request(`https://lagerfeuer.test${path}`, { headers, redirect: "manual" }), env);

async function start(to = "/upload") {
  const res = await call(`/auth/microsoft?to=${to}`);
  const loc = new URL(res.headers.get("location"));
  const state = loc.searchParams.get("state");
  return { res, loc, state, cookie: `emberleaf_oauth=${state}` };
}

try {
  const s = await start();
  check("Start leitet zu Microsoft", s.res.status === 302 && s.loc.origin === "https://login.microsoftonline.com");
  check("mit Client-ID, PKCE und Rückruf-Adresse", s.loc.searchParams.get("client_id") === env.MS_CLIENT_ID
    && s.loc.searchParams.get("code_challenge_method") === "S256" && s.loc.searchParams.get("redirect_uri") === "https://lagerfeuer.test/auth/callback");
  check("Cookie bindet die Anmeldung an den Browser", (s.res.headers.get("set-cookie") || "").includes(`emberleaf_oauth=${s.state}`)
    && s.res.headers.get("set-cookie").includes("HttpOnly"));

  const noCookie = await call(`/auth/callback?code=gut&state=${s.state}`);
  check("fremder Rückruf ohne Cookie: abgelehnt", noCookie.headers.get("location").includes("#login-error="));

  const ok = await call(`/auth/callback?code=gut&state=${s.state}`, { cookie: s.cookie });
  const back = new URL(ok.headers.get("location"));
  check("zurück auf /upload mit Einmal-Code", ok.status === 302 && back.pathname === "/upload" && /^#login=[A-Z0-9]{6}$/.test(back.hash), back.href);
  check("PKCE-Prüfwert an Microsoft geschickt", (seen.token?.get("code_verifier") || "").length >= 43);
  check("Xbox und Minecraft richtig gefragt", seen.xbl?.Properties?.RpsTicket === "d=ms-token" && seen.mc?.identityToken === "XBL3.0 x=1234;xsts-token"
    && seen.profileAuth === "Bearer mc-token");

  const again = await call(`/auth/callback?code=gut&state=${s.state}`, { cookie: s.cookie });
  check("derselbe Rückruf geht nur einmal", again.headers.get("location").includes("#login-error="));

  const claim = await worker.fetch(new Request("https://lagerfeuer.test/v1/pair/claim", {
    method: "POST", headers: { "content-type": "application/json" }, body: JSON.stringify({ code: back.hash.slice(7) }) }), env);
  const session = await claim.json();
  check("Code gegen Anmeldung getauscht", claim.status === 200 && session.uuid === ME.id && session.name === ME.name);
  const upd = await (await worker.fetch(new Request("https://lagerfeuer.test/v1/update", { headers: { authorization: `Bearer ${session.token}` } }), env)).json();
  check("SenLxca ist Besitzer (per Minecraft-ID)", upd.owner === true);

  const s2 = await start("/");
  const cancel = await call(`/auth/callback?error=access_denied&state=${s2.state}`, { cookie: s2.cookie });
  check("abgebrochen: Meldung auf der Startseite", decodeURIComponent(new URL(cancel.headers.get("location")).hash).includes("abgebrochen"));

  mode = "noGame";
  const s3 = await start("/");
  const noGame = await call(`/auth/callback?code=gut&state=${s3.state}`, { cookie: s3.cookie });
  check("ohne Minecraft: klare Meldung", decodeURIComponent(new URL(noGame.headers.get("location")).hash).includes("kein Minecraft"));

  mode = "child";
  const s4 = await start("/");
  const child = await call(`/auth/callback?code=gut&state=${s4.state}`, { cookie: s4.cookie });
  check("Kinderkonto: klare Meldung", decodeURIComponent(new URL(child.headers.get("location")).hash).includes("Kinderkonten"));

  mode = "ok";
  const s5 = await start("/");
  const bad = await call(`/auth/callback?code=falsch&state=${s5.state}`, { cookie: s5.cookie });
  check("falscher Code: Microsoft lehnt ab", decodeURIComponent(new URL(bad.headers.get("location")).hash).includes("abgelehnt"));
  check("unbekannte Rückkehr-Seite wird zu /", new URL((await start("https://boese.example")).res.headers.get("location")).searchParams.get("state") !== null
    && db.prepare("SELECT back FROM oauth_states ORDER BY expires DESC LIMIT 1").get().back === "/");
} catch (e) {
  failures++;
  console.log("Absturz:", e.stack);
}
console.log(failures ? `\n${failures} Test(s) fehlgeschlagen` : "\nALLE TESTS BESTANDEN");
process.exit(failures ? 1 : 0);
