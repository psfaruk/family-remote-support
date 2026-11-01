/**
 * Create (or verify) the Location project's ONE-TIME composite index on
 * `locationHistory` (deviceId ASC, retentionExpiresAt ASC, recordedAt
 * ASC) — the D10a prerequisite for the Phase 9 day queries.
 *
 * Why a script at all: the coordinator service account HOLDS
 * `datastore.indexes.list` but NOT `datastore.indexes.create` (IAM
 * verified live, worklog §80/§81). This script therefore:
 *   • lists first — if the index already exists it just reports/polls
 *     its state (idempotent, safe to re-run);
 *   • attempts the create anyway (a future IAM grant lights this up
 *     with zero further changes);
 *   • on 403 prints the exact console steps + exits 3 (honest failure,
 *     never a silent skip).
 *
 * The index is COLLECTION-scoped: it serves the app's single-collection
 * day queries (`users/{uid}/locationHistory`) on ANY locationHistory
 * collection under any parent (Firestore indexes are stored per
 * collection GROUP; COLLECTION scope = non-collectionGroup queries).
 *
 * Run: node scripts/create-location-index.mjs
 */
import { readFileSync, existsSync } from "node:fs";
import crypto from "node:crypto";

const envSa = process.env.FIREBASE_SA_JSON;
const envSaIsBlob = Boolean(envSa && envSa.trim().startsWith("{"));
const SA_CANDIDATES = [
  envSaIsBlob ? null : envSa,
  "/home/z/.secrets/firebase-sa.json",
  "/home/z/my-project/.secrets/firebase-sa.json",
].filter(Boolean);
const SA_PATH = SA_CANDIDATES.find((p) => existsSync(p));
if (!envSaIsBlob && !SA_PATH) {
  console.error("✘ no service-account key found (tried:", SA_CANDIDATES.join(", "), ")");
  process.exit(1);
}
const sa = JSON.parse(envSaIsBlob ? envSa : readFileSync(SA_PATH, "utf8"));
const PROJECT = sa.project_id;
// This project's Firestore database id is "default" (NOT "(default)") —
// proven by live probes (worklog Phase 6) and re-confirmed by the Phase
// 10 index-link probe.
const DB = "default";

async function getAccessToken() {
  const now = Math.floor(Date.now() / 1000);
  const header = Buffer.from(JSON.stringify({ alg: "RS256", typ: "JWT" })).toString("base64url");
  const claims = {
    iss: sa.client_email,
    scope: "https://www.googleapis.com/auth/datastore",
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
const LIST_URL = `https://firestore.googleapis.com/v1/projects/${PROJECT}/databases/${DB}/collectionGroups/locationHistory/indexes`;

const WANT = ["deviceId", "retentionExpiresAt", "recordedAt"];
const matches = (idx) => {
  const fs = (idx.fields || []).map((f) => f.fieldPath);
  return (
    fs.length === WANT.length &&
    WANT.every((w, k) => fs[k] === w) &&
    (idx.fields || []).every((f) => f.order === "ASCENDING")
  );
};

// 1. list — the SA always holds datastore.indexes.list
const listRes = await fetch(LIST_URL, { headers: H });
if (!listRes.ok) {
  console.error("✘ index list failed:", listRes.status, await listRes.text());
  process.exit(1);
}
const list = await listRes.json();
const existing = (list.indexes || []).find(matches);
if (existing) {
  console.log("✔ composite index present:", existing.name, "state:", existing.state);
} else {
  // 2. attempt the create (lights up the moment the SA is granted
  //    roles/datastore.indexAdmin; until then the 403 is reported
  //    honestly with the console fallback).
  const createRes = await fetch(LIST_URL, {
    method: "POST",
    headers: H,
    body: JSON.stringify({
      fields: WANT.map((f) => ({ fieldPath: f, order: "ASCENDING" })),
      queryScope: "COLLECTION",
    }),
  });
  if (createRes.status === 403 || createRes.status === 409) {
    const body = await createRes.text();
    console.error("✘ create denied (" + createRes.status + "):", body.slice(0, 200));
    console.error(`
The coordinator SA (${sa.client_email}) lacks datastore.indexes.create
(IAM roles/datastore.indexAdmin missing — verified via testIamPermissions).
Owner options, either one works:
  A) Firebase console → Firestore Database → Indexes → Add index:
     Collection ID: locationHistory
     Fields: deviceId Ascending, retentionExpiresAt Ascending, recordedAt Ascending
     Query scope: Collection
  B) Google Cloud console → IAM → grant the SA "Datastore Index Admin",
     then re-run this script (it creates + verifies the index itself).
`);
    process.exit(3);
  }
  if (!createRes.ok) {
    console.error("✘ create failed:", createRes.status, await createRes.text());
    process.exit(1);
  }
  const created = await createRes.json();
  console.log("✔ create accepted:", created.name, "state:", created.state);
}

// 3. poll to READY (bounded — BUILDING typically takes seconds–minutes)
for (let i = 0; i < 30; i++) {
  const reList = await fetch(LIST_URL, { headers: H });
  const idx = (await reList.json()).indexes?.find(matches);
  if (idx?.state === "READY") {
    console.log("✔ VERIFY: composite index READY —", idx.name);
    console.log("The Phase 9 day queries can now execute (hist_index_hint retires).");
    process.exit(0);
  }
  console.log("… state:", idx?.state || "(listing…)", "— waiting 10 s");
  await new Promise((r) => setTimeout(r, 10_000));
}
console.error("✘ index still not READY after ~5 min — re-run this script to re-check.");
process.exit(2);
