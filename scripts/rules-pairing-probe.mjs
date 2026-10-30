/**
 * Phase 18.3a (v0.24.0) — RUNTIME POSITIVE probe of the LIVE pairing rules.
 *
 * Companion to rules-negative-probe.mjs (which only proves hostile paths are
 * denied). THIS script proves the REAL pairing round-trip is ALLOWED — the
 * Phase 17 lesson: strict rules + a client that forgot the contract denied
 * every fresh pairing with PERMISSION_DENIED while all negative probes kept
 * passing. The probe replays exactly what PairingRepo.kt sends:
 *
 *   P1a  inviter creates the invite  (hash + RAW token, 5-min window)
 *   P1b  claimer redeems it          (commit: invite update WITH claimToken
 *                                     + relationship create — the strict
 *                                     rules' getAfter()/claimedAt checks)
 *   N1   redeem WITHOUT claimToken   → 403 (server-side token proof)
 *   N2   redeem with a WRONG token   → 403
 *
 * Cleanup is admin-path (SA bypasses rules): deletes the throwaway invite +
 * relationship docs and both synthetic Auth users. Nothing real is touched.
 *
 * Exit 0 = all expectations met; 1 = any deviation (contract drift).
 */
import { readFileSync, existsSync } from "node:fs";
import crypto from "node:crypto";

const SA_CANDIDATES = [
  process.env.FIREBASE_SA_JSON,
  "/home/z/.secrets/firebase-sa.json",
  "/home/z/my-project/.secrets/firebase-sa.json",
].filter(Boolean);
const SA_RESOLVED = SA_CANDIDATES.find((p) => {
  try { readFileSync(p); return true; } catch { return false; }
});
if (!SA_RESOLVED) {
  console.error("✘ no service-account key found (tried:", SA_CANDIDATES.join(", "), ")");
  process.exit(1);
}
const sa = JSON.parse(readFileSync(SA_RESOLVED, "utf8"));
const PROJECT = sa.project_id;

const gs = JSON.parse(
  readFileSync("/home/z/my-project/android-app/app/google-services.json", "utf8")
);
const API_KEY = gs.client?.[0]?.api_key?.[0]?.current_key;
if (!API_KEY) {
  console.error("✘ no api_key found in google-services.json");
  process.exit(1);
}

function b64u(buf) {
  return Buffer.from(buf).toString("base64url");
}

async function saAccessToken() {
  const now = Math.floor(Date.now() / 1000);
  const header = b64u(JSON.stringify({ alg: "RS256", typ: "JWT" }));
  const claims = {
    iss: sa.client_email,
    scope: "https://www.googleapis.com/auth/cloud-platform",
    aud: "https://oauth2.googleapis.com/token",
    iat: now,
    exp: now + 3600,
  };
  const payload = b64u(JSON.stringify(claims));
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
  if (!res.ok) throw new Error(`SA token failed: ${res.status} ${JSON.stringify(j)}`);
  return j.access_token;
}

function mintCustomToken(uid) {
  const now = Math.floor(Date.now() / 1000);
  const header = b64u(JSON.stringify({ alg: "RS256", typ: "JWT" }));
  const claims = {
    iss: sa.client_email,
    sub: sa.client_email,
    aud: "https://identitytoolkit.googleapis.com/google.identity.identitytoolkit.v1.IdentityToolkit",
    iat: now,
    exp: now + 3600,
    uid,
  };
  const payload = b64u(JSON.stringify(claims));
  const unsigned = `${header}.${payload}`;
  const signature = crypto
    .createSign("RSA-SHA256")
    .update(unsigned)
    .sign(sa.private_key)
    .toString("base64url");
  return `${unsigned}.${signature}`;
}

async function idTokenFor(customToken) {
  const res = await fetch(
    `https://identitytoolkit.googleapis.com/v1/accounts:signInWithCustomToken?key=${API_KEY}`,
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ token: customToken, returnSecureToken: true }),
    }
  );
  const j = await res.json();
  if (!res.ok) throw new Error(`custom-token sign-in failed: ${res.status} ${JSON.stringify(j)}`);
  return j.idToken;
}

// This project's Firestore database id is "default" (NOT "(default)").
const BASE = `https://firestore.googleapis.com/v1/projects/${PROJECT}/databases/default/documents`;
/** Relative resource name — write.name must NOT be a URL ("lacks projects at index 0"). */
const DOCNAME = (coll, id) => `projects/${PROJECT}/databases/default/documents/${coll}/${id}`;
const F = (s) => ({ stringValue: s });
const TS = (ms) => ({ timestampValue: new Date(ms).toISOString() });
const NOW = () => ({ timestampValue: new Date().toISOString() });
const sha256Hex = (v) =>
  crypto.createHash("sha256").update(v).digest("hex");

// No I, L, O, 0 or 1 — same alphabet as the client.
const ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
function inviteId() {
  let s = "PRB";
  for (let i = 0; i < 3; i++) s += ALPHABET[crypto.randomInt(ALPHABET.length)];
  return s + "-" + crypto.randomBytes(4).toString("hex"); // doc id (free-form)
}
function token20() {
  return crypto.randomBytes(10).toString("hex"); // 20 hex chars — matches client
}

const results = [];
function record(name, expected, got, pass) {
  results.push({ name, expected, got, pass });
}

/** The invite doc exactly as PairingRepo.createInvitation writes it. */
function inviteFields(uid, email, token) {
  return {
    fields: {
      inviterUid: F(uid),
      inviterEmail: F(email),
      recipientDeviceId: F("0123456789abcdef01234567"),
      recipientDeviceModel: F("rules-pairing-probe"),
      status: F("PENDING"),
      tokenHash: F(sha256Hex(token)),
      token: F(token),
      createdAt: NOW(),
      expiresAt: TS(Date.now() + 5 * 60 * 1000),
    },
  };
}

/**
 * The redeem commit exactly as PairingRepo.redeemCode's transaction writes
 * it (one atomic batch): invite update (with or without claimToken) +
 * relationship create. serverTimestamp fields ride updateTransforms.
 */
function redeemCommit(invName, inviterUid, inviterEmail, claimUid, claimEmail, relId, token, includeClaim) {
  const invUpdateFields = {
    status: F("PAIRED"),
    claimedBy: F(claimUid),
    claimedEmail: F(claimEmail),
  };
  const mask = ["status", "claimedBy", "claimedEmail"];
  if (includeClaim) {
    invUpdateFields.claimToken = F(token);
    mask.push("claimToken");
  }
  return {
    writes: [
      {
        update: { name: invName, fields: invUpdateFields },
        updateMask: { fieldPaths: mask },
        updateTransforms: [
          { fieldPath: "claimedAt", setToServerValue: "REQUEST_TIME" },
          { fieldPath: "updatedAt", setToServerValue: "REQUEST_TIME" },
        ],
      },
      {
        update: {
          name: DOCNAME("trustedDeviceRelationships", relId),
          fields: {
            helperUid: F(claimUid),
            helperEmail: F(claimEmail),
            recipientUid: F(inviterUid),
            recipientEmail: F(inviterEmail),
            recipientDeviceId: F("0123456789abcdef01234567"),
            recipientDeviceModel: F("rules-pairing-probe"),
            pairKey: F(relId),
            status: F("ACTIVE"),
            createdByInvitation: F(invName.split("/").pop()),
          },
        },
        updateTransforms: [
          { fieldPath: "createdAt", setToServerValue: "REQUEST_TIME" },
          { fieldPath: "updatedAt", setToServerValue: "REQUEST_TIME" },
        ],
      },
    ],
  };
}

async function main() {
  const saTok = await saAccessToken();
  const saH = { Authorization: `Bearer ${saTok}`, "Content-Type": "application/json" };

  // ---------- identities ----------
  const suffix = crypto.randomBytes(4).toString("hex");
  const invUid = `probe-inv-${suffix}`;
  const claimUid = `probe-claim-${suffix}`;
  const invTok = await idTokenFor(mintCustomToken(invUid));
  const claimTok = await idTokenFor(mintCustomToken(claimUid));
  const invH = { Authorization: `Bearer ${invTok}`, "Content-Type": "application/json" };
  const claimH = { Authorization: `Bearer ${claimTok}`, "Content-Type": "application/json" };
  console.log(`identities minted: ${invUid} (inviter) / ${claimUid} (claimer)`);

  const inviteIds = [];
  const relIds = [];

  /** one invite round-trip: returns { id, token, invName } */
  async function createInvite() {
    const id = inviteId();
    const token = token20();
    const res = await fetch(`${BASE}/pairingInvitations?documentId=${encodeURIComponent(id)}`, {
      method: "POST",
      headers: invH,
      body: JSON.stringify(inviteFields(invUid, `${invUid}@invalid.local`, token)),
    });
    record(`P1a create invite (${id})`, "200", `HTTP ${res.status}`, res.status === 200);
    if (res.status !== 200) throw new Error(`invite create failed: HTTP ${res.status}`);
    inviteIds.push(id);
    return { id, token, invName: DOCNAME("pairingInvitations", id) };
  }

  async function redeem(inv, includeClaim, wrongToken) {
    const relId = `${claimUid}_${invUid}`;
    relIds.push(relId);
    const res = await fetch(`${BASE}:commit`, {
      method: "POST",
      headers: claimH,
      body: JSON.stringify(
        redeemCommit(
          inv.invName, invUid, `${invUid}@invalid.local`,
          claimUid, `${claimUid}@invalid.local`, relId,
          wrongToken ?? inv.token, includeClaim
        )
      ),
    });
    return res;
  }

  // ---------- P1: the REAL redeem (with claimToken) must SUCCEED ----------
  try {
    const inv1 = await createInvite();
    const r1 = await redeem(inv1, true, null);
    record("P1b redeem WITH claimToken (invite→PAIRED + rel created)", "200", `HTTP ${r1.status}`, r1.status === 200);
  } catch (e) {
    record("P1 redeem round-trip", "200", `THREW ${e.message}`, false);
  }

  // ---------- N1: redeem WITHOUT claimToken must be DENIED ----------
  try {
    const inv2 = await createInvite();
    const r2 = await redeem(inv2, false, null);
    record("N1 redeem WITHOUT claimToken", "403", `HTTP ${r2.status}`, r2.status === 403);
  } catch (e) {
    record("N1 redeem WITHOUT claimToken", "403", `THREW ${e.message}`, false);
  }

  // ---------- N2: redeem with a WRONG claimToken must be DENIED ----------
  try {
    const inv3 = await createInvite();
    const r3 = await redeem(inv3, true, token20()); // valid shape, wrong value
    record("N2 redeem with WRONG claimToken", "403", `HTTP ${r3.status}`, r3.status === 403);
  } catch (e) {
    record("N2 redeem with WRONG claimToken", "403", `THREW ${e.message}`, false);
  }

  // ---------- cleanup (admin path — bypasses rules) ----------
  let cleaned = 0;
  for (const id of inviteIds) {
    try {
      const d = await fetch(`${BASE}/pairingInvitations/${encodeURIComponent(id)}`, { method: "DELETE", headers: saH });
      if (d.ok || d.status === 404) cleaned++;
    } catch { /* best effort */ }
  }
  for (const id of new Set(relIds)) {
    try {
      const d = await fetch(`${BASE}/trustedDeviceRelationships/${encodeURIComponent(id)}`, { method: "DELETE", headers: saH });
      if (d.ok || d.status === 404) cleaned++;
    } catch { /* best effort */ }
  }
  let users = "n/a";
  try {
    const a = await fetch(`https://identitytoolkit.googleapis.com/v1/accounts:delete?key=${API_KEY}`, {
      method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ idToken: invTok }),
    });
    const b = await fetch(`https://identitytoolkit.googleapis.com/v1/accounts:delete?key=${API_KEY}`, {
      method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ idToken: claimTok }),
    });
    users = `HTTP ${a.status}/${b.status}`;
  } catch (e) {
    users = `THREW ${e.message}`;
  }

  // ---------- report ----------
  console.log("\n────────── Phase 18.3a pairing-probe results ──────────");
  let allPass = true;
  for (const r of results) {
    const mark = r.pass ? "✔" : "✘";
    if (!r.pass) allPass = false;
    console.log(`${mark} ${r.name.padEnd(56)} expected ${String(r.expected).padEnd(8)} got ${r.got}`);
  }
  console.log(`\ncleanup: ${cleaned} docs deleted · auth users ${users}`);
  console.log(allPass
    ? "✔✔ PAIRING CONTRACT INTACT — the live rules allow the real redeem and still refuse the hostile ones."
    : "✘✘ PAIRING CONTRACT BROKEN — see the failed lines above.");
  process.exit(allPass ? 0 : 1);
}

main().catch((e) => {
  console.error("✘ probe crashed:", e);
  process.exit(1);
});
