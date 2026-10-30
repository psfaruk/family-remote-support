/**
 * Write (or update) the Firestore `config/quality` document with the Owner's
 * service account. The Android app (v0.9.7+) reads this document at every
 * screen-share session start (60 s cache) — so codec/fps/bitrate experiments
 * are flipped WITHOUT shipping a new APK.
 *
 * Fields (all clamped app-side):
 *   codec   = "vp8" (proven everywhere) | "h264" (hardware encoder)
 *   fps     = capture/encode ceiling, 10..60 (60 only sane with h264)
 *   minKbps = bitrate floor (hard floor on the estimator)
 *   maxKbps = bitrate ceiling
 *
 * Usage:
 *   node scripts/set-quality-config.mjs                       → defaults (vp8·30·1200–4000)
 *   node scripts/set-quality-config.mjs h264 60 1500 5000     → the 60 fps experiment
 *   node scripts/set-quality-config.mjs vp8 30 1200 4000      → revert to proven v0.9.6 path
 */
import { readFileSync, existsSync } from "node:fs";
import crypto from "node:crypto";

const SA_CANDIDATES = [
  "/home/z/.secrets/firebase-sa.json",
  "/home/z/my-project/.secrets/firebase-sa.json",
];
const saPath = SA_CANDIDATES.find((p) => existsSync(p));
if (!saPath) {
  console.error("✘ no service account found in:", SA_CANDIDATES);
  process.exit(1);
}
const sa = JSON.parse(readFileSync(saPath, "utf8"));
const PROJECT = sa.project_id;

const codec = (process.argv[2] || "vp8").trim().toLowerCase() === "h264" ? "h264" : "vp8";
const clamp = (v, lo, hi, dflt) => {
  const n = Number(v);
  return Number.isFinite(n) ? Math.round(Math.min(hi, Math.max(lo, n))) : dflt;
};
const fps = clamp(process.argv[3], 10, 60, 30);
let minKbps = clamp(process.argv[4], 100, 50_000, 1_200);
let maxKbps = clamp(process.argv[5], minKbps, 100_000, Math.max(minKbps, 4_000));
// v0.24.0 (Phase 18.3) — what suffers first under bandwidth pressure.
// "framerate" (v0.24 default — freshness over sharpness, the anti-latency
// choice) | "balanced" | "resolution" (the old keep-sharp-but-stall).
const rawPref = (process.argv[6] || "framerate").trim().toLowerCase();
const pref = ["framerate", "balanced", "resolution"].includes(rawPref) ? rawPref : "framerate";

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

const note =
  codec === "h264"
    ? "HARDWARE H264 EXPERIMENT — phones without a working HW encoder fall back to VP8@30 automatically (probe + SDP fallback). Revert: set codec=vp8."
    : "Proven software VP8 path (v0.9.6 behaviour) — the safe default.";

const doc = {
  fields: {
    codec: { stringValue: codec },
    fps: { integerValue: String(fps) },
    minKbps: { integerValue: String(minKbps) },
    maxKbps: { integerValue: String(maxKbps) },
    degradationPreference: { stringValue: pref },
    updatedAt: { timestampValue: new Date().toISOString() },
    note: { stringValue: note },
  },
};

// NOTE: this project's Firestore database id is "default" (not "(default)").
const url = `https://firestore.googleapis.com/v1/projects/${PROJECT}/databases/default/documents/config/quality`;
const res = await fetch(url, { method: "PATCH", headers: H, body: JSON.stringify(doc) });
if (!res.ok) {
  console.error("✘ write failed:", res.status, await res.text());
  process.exit(1);
}
const back = await fetch(url, { headers: H });
const backDoc = await back.json();
const f = backDoc.fields || {};
console.log(
  `✔ config/quality written — codec=${f.codec?.stringValue} fps=${f.fps?.integerValue} ` +
    `bitrate=${f.minKbps?.integerValue}k–${f.maxKbps?.integerValue}k pref=${f.degradationPreference?.stringValue}`
);
console.log(`  LIVE config: ${codec.toUpperCase()} @${fps}fps ${minKbps}–${maxKbps}kbps · ${pref}-first`);
