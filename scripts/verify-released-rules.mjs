/**
 * verify-released-rules.mjs — READ-ONLY gate: prove the LIVE Firestore rules
 * of the project are byte-exact identical to the repo's firestore.rules.
 *
 * Added by the Location master plan Phase 12 security review (§85 /
 * LOCATION_SECURITY_REVIEW.md §2.1): the review's core enforcement evidence
 * is "what the repo documents is what production enforces" — this script
 * makes that check reproducible at every future gate (the release
 * checklist runs it as a standing step).
 *
 * What it does (2 REST GETs + 1 OAuth token exchange — §75-safe, no
 * document reads/writes, nothing mutated):
 *   1. mint a short-lived access token from the service-account key
 *   2. GET the project's releases, find cloud.firestore/<default>
 *   3. GET the ruleset that release points at
 *   4. sha256-compare the ruleset's firestore.rules content against the
 *      local repo file
 *
 * Exit codes: 0 = MATCH · 1 = API/auth failure · 2 = MISMATCH (released
 * rules differ from the repo — publishing drift; run
 * publish-firestore-rules.mjs only after the diff is understood).
 *
 * SA resolution order mirrors publish-firestore-rules.mjs /
 * fcm-kill-push.mjs: FIREBASE_SA_JSON env (blob or path) → legacy home
 * path → project .secrets path.
 */
import { readFileSync, existsSync } from "node:fs";
import crypto from "node:crypto";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";

const envSa = process.env.FIREBASE_SA_JSON;
const envSaIsBlob = Boolean(envSa && envSa.trim().startsWith("{"));
const SA_CANDIDATES = [
  envSaIsBlob ? null : envSa,
  "/home/z/.secrets/firebase-sa.json",
  "/home/z/my-project/.secrets/firebase-sa.json",
].filter(Boolean);
const SA_RESOLVED = envSaIsBlob
  ? { blob: envSa, label: "FIREBASE_SA_JSON env (blob)" }
  : SA_CANDIDATES.find((p) => existsSync(p))
    ? { path: SA_CANDIDATES.find((p) => existsSync(p)), label: null }
    : null;
if (!SA_RESOLVED) {
  console.error("✘ no service-account key found (tried:", SA_CANDIDATES.join(", "), ")");
  process.exit(1);
}

const SCRIPT_DIR = dirname(fileURLToPath(import.meta.url));
const RULES_CANDIDATES = [
  join(SCRIPT_DIR, "..", "firestore.rules"), // <repo>/scripts → <repo>/firestore.rules (current)
  join(SCRIPT_DIR, "..", "android-app", "firestore.rules"), // legacy layout
];
const RULES_PATH = RULES_CANDIDATES.find((p) => existsSync(p));
if (!RULES_PATH) {
  console.error("✘ firestore.rules not found (tried:", RULES_CANDIDATES.join(", "), ")");
  process.exit(1);
}

const sa = JSON.parse(
  SA_RESOLVED.blob ?? readFileSync(SA_RESOLVED.path, "utf8")
);
const PROJECT = sa.project_id;
// Same database id the app targets (Fb.kt: "default", NOT "(default)").
const DB_ID = "default";

async function getAccessToken() {
  const now = Math.floor(Date.now() / 1000);
  const header = Buffer.from(JSON.stringify({ alg: "RS256", typ: "JWT" })).toString("base64url");
  const claims = {
    iss: sa.client_email,
    scope: "https://www.googleapis.com/auth/cloud-platform",
    aud: "https://oauth2.googleapis.com/token",
    iat: now,
    exp: now + 3600,
  };
  const payload = Buffer.from(JSON.stringify(claims)).toString("base64url");
  const unsigned = `${header}.${payload}`;
  const signature = crypto
    .createSign("RSA-SHA256")
    .update(unsigned)
    .sign(sa.private_key)
    .toString("base64url");
  const res = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
      assertion: `${unsigned}.${signature}`,
    }),
  });
  const j = await res.json();
  if (!res.ok) throw new Error(`token failed: ${res.status} ${JSON.stringify(j).slice(0, 200)}`);
  return j.access_token;
}

const token = await getAccessToken();
const H = { Authorization: `Bearer ${token}` };

// 1. find the release in force for the app's database
const listRes = await fetch(
  `https://firebaserules.googleapis.com/v1/projects/${PROJECT}/releases?pageSize=50`,
  { headers: H }
);
if (!listRes.ok) {
  console.error("✘ release list failed:", listRes.status, (await listRes.text()).slice(0, 200));
  process.exit(1);
}
const releases = (await listRes.json()).releases || [];
const exact = `projects/${PROJECT}/releases/cloud.firestore/${DB_ID}`;
const rel = releases.find((r) => r.name === exact);
if (!rel) {
  console.error(`✘ no release found for ${exact} (releases present: ${releases.map((r) => r.name).join(", ") || "none"})`);
  process.exit(1);
}

// 2. fetch the ruleset content (READ-ONLY)
const rsRes = await fetch(
  `https://firebaserules.googleapis.com/v1/${rel.rulesetName}`,
  { headers: H }
);
if (!rsRes.ok) {
  console.error("✘ ruleset get failed:", rsRes.status, (await rsRes.text()).slice(0, 200));
  process.exit(1);
}
const rs = await rsRes.json();
const file = (rs.source?.files || []).find((f) => f.name === "firestore.rules");
if (!file) {
  console.error("✘ ruleset has no firestore.rules file");
  process.exit(1);
}

// 3. byte-exact comparison
const remote = file.content;
const local = readFileSync(RULES_PATH, "utf8");
const h = (s) => crypto.createHash("sha256").update(s, "utf8").digest("hex");
const remoteSha = h(remote);
const localSha = h(local);

console.log("release in force :", rel.name);
console.log("released ruleset :", rel.rulesetName.split("/").pop());
console.log("released sha256  :", remoteSha);
console.log("repo     sha256  :", localSha);
console.log("bytes remote/local:", remote.length, "/", local.length);

if (remote === local) {
  console.log("MATCH — released rules == repo firestore.rules (byte-exact)");
  process.exit(0);
} else {
  console.log("MISMATCH — released rules differ from the repo file!");
  console.log("  first differing line:", (() => {
    const a = remote.split("\n");
    const b = local.split("\n");
    for (let i = 0; i < Math.max(a.length, b.length); i++) {
      if (a[i] !== b[i]) return `line ${i + 1}: released=${JSON.stringify(a[i])} repo=${JSON.stringify(b[i])}`;
    }
    return "(lengths differ)";
  })());
  process.exit(2);
}
