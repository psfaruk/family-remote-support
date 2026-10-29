/**
 * Publish android-app/firestore.rules as the LIVE Firestore rules of the
 * project, using the service-account key (no console, no Owner action).
 *
 * Flow = exactly what `firebase deploy --only firestore:rules` does:
 *   1. create a new ruleset with the file content
 *   2. point the release `cloud.firestore/<dbId>` at it
 *
 * The release name is nested per database in the current Firebase console
 * ("cloud.firestore/default" for this project's database id "default"), so
 * the script lists releases first and PATCHes the existing one (or creates
 * it when absent).
 */
import { readFileSync, existsSync } from "node:fs";
import crypto from "node:crypto";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";

// v0.21.0 (audit) — same resolution order as scripts/fcm-kill-push.mjs:
// env (JSON blob OR file path) → legacy home path → project .secrets path.
// The old version treated the FIREBASE_SA_JSON env var as a PATH only, so
// the (documented) JSON-blob convention silently never worked here.
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
// v0.21.0 (audit) — repo-relative rules path (the old hardcoded sandbox path
// failed everywhere else, e.g. CI or another checkout).
const SCRIPT_DIR = dirname(fileURLToPath(import.meta.url));
const RULES_PATH = join(SCRIPT_DIR, "..", "android-app", "firestore.rules");
if (!existsSync(RULES_PATH)) {
  console.error("✘ firestore.rules not found at", RULES_PATH);
  process.exit(1);
}

const sa = JSON.parse(
  SA_RESOLVED.blob ?? readFileSync(SA_RESOLVED.path, "utf8")
);
const PROJECT = sa.project_id;
const rules = readFileSync(RULES_PATH, "utf8");

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
  if (!res.ok) throw new Error(`token failed: ${res.status} ${JSON.stringify(j)}`);
  return j.access_token;
}

const token = await getAccessToken();
const H = { Authorization: `Bearer ${token}`, "Content-Type": "application/json" };

// ---------- 1. create the ruleset ----------
const createRes = await fetch(
  `https://firebaserules.googleapis.com/v1/projects/${PROJECT}/rulesets`,
  {
    method: "POST",
    headers: H,
    body: JSON.stringify({
      source: { files: [{ name: "firestore.rules", content: rules }] },
    }),
  }
);
if (!createRes.ok) {
  console.error("✘ ruleset create failed:", createRes.status, await createRes.text());
  process.exit(1);
}
const ruleset = await createRes.json();
console.log("✔ ruleset created:", ruleset.name.split("/").pop());

// ---------- 2. point the release at it ----------
const listRes = await fetch(
  `https://firebaserules.googleapis.com/v1/projects/${PROJECT}/releases?pageSize=50`,
  { headers: H }
);
if (!listRes.ok) {
  console.error("✘ release list failed:", listRes.status, await listRes.text());
  process.exit(1);
}
const releases = (await listRes.json()).releases || [];
// v0.21.0 (audit) — target the EXACT database release, not the first
// "cloud.firestore/*" match (with more than one database the old first-match
// could PATCH the wrong database's rules). This project's database id is
// "default" (NOT "(default)" — proven by live probes in Phase 6).
const DB_ID = "default";
const exact = `projects/${PROJECT}/releases/cloud.firestore/${DB_ID}`;
const existing = releases.find((r) => r.name === exact);
const releaseName = existing ? existing.name : exact;
console.log("→ target release:", releaseName.split("/releases/")[1]);

const patchRes = await fetch(
  `https://firebaserules.googleapis.com/v1/${releaseName}`,
  {
    method: "PATCH",
    headers: H,
    // The v1 PATCH takes an UpdateReleaseRequest: {release, updateMask}.
    body: JSON.stringify({
      release: { name: releaseName, rulesetName: ruleset.name },
      updateMask: "ruleset_name",
    }),
  }
);
if (patchRes.ok) {
  const rel = await patchRes.json();
  console.log("✔ release updated →", rel.rulesetName.split("/").pop());
} else if (patchRes.status === 404) {
  const createRel = await fetch(
    `https://firebaserules.googleapis.com/v1/projects/${PROJECT}/releases`,
    {
      method: "POST",
      headers: H,
      body: JSON.stringify({ name: releaseName, rulesetName: ruleset.name }),
    }
  );
  if (!createRel.ok) {
    console.error("✘ release create failed:", createRel.status, await createRel.text());
    process.exit(1);
  }
  const rel = await createRel.json();
  console.log("✔ release created →", rel.rulesetName.split("/").pop());
} else {
  console.error("✘ release patch failed:", patchRes.status, await patchRes.text());
  process.exit(1);
}

// ---------- 3. verify ----------
const verifyRes = await fetch(
  `https://firebaserules.googleapis.com/v1/${releaseName}`,
  { headers: H }
);
const rel = await verifyRes.json();
console.log("✔ VERIFY: live release now points at ruleset", rel.rulesetName.split("/").pop());
console.log(`Done. ${rules.split("\n").length} lines of rules are LIVE.`);
