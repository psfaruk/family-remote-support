/**
 * Phase 18.5 (v0.24.0) — RUNTIME AUDIT PROBE of the LIVE Firestore rules.
 *
 * Companion to scripts/rules-negative-probe.mjs (which keeps proving the
 * Phase 9/10 battery). This suite exercises the 18.5 hardening + the
 * kill-push-era hostile paths the old suite never had fixtures for:
 *
 *   Fixtures: synthetic family pair (recipient "rules-audit-a" + helper
 *   "rules-audit-h") via a REAL atomic redeem commit, then their OWN
 *   session docs — nothing of any real user is touched.
 *
 *   Positive controls (200 ALLOWED): honest grant, honest cmd/ice signals,
 *   honest profile patch. Hostile paths (403 DENIED): kill-push mark
 *   forgery (suppression attack), wrong-side grant, over-cap sessionEndsAt,
 *   per-type signal payload violations, unknown signal type, oversized ep,
 *   self-addressed signal, transition with extra fields, over-cap deadline,
 *   profile blob, wrong-typed presence/token timestamps, signals after END.
 *
 * Every fixture (sessions, signals, relationship, invitation, presence,
 * token registry, user profile, synthetic Auth users) is deleted in the
 * cleanup — zero residue. Exit 0 = all expectations met.
 */
import { readFileSync } from "node:fs";
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

const PROBE_UID = "rules-audit-a"; // recipient (invitation owner)
const HELPER_UID = "rules-audit-h"; // helper (redeemer)

const b64u = (buf) => Buffer.from(buf).toString("base64url");
const F = (s) => ({ stringValue: s });
const TS = (ms) => ({ timestampValue: new Date(ms).toISOString() });
const I = (n) => ({ integerValue: String(n) });

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
  const signature = crypto.createSign("RSA-SHA256").update(unsigned).sign(sa.private_key).toString("base64url");
  const res = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({ grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer", assertion: `${unsigned}.${signature}` }),
  });
  const j = await res.json();
  if (!res.ok) throw new Error(`SA token failed: ${res.status}`);
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
  const signature = crypto.createSign("RSA-SHA256").update(unsigned).sign(sa.private_key).toString("base64url");
  return `${unsigned}.${signature}`;
}

async function idTokenFor(uid) {
  const res = await fetch(`https://identitytoolkit.googleapis.com/v1/accounts:signInWithCustomToken?key=${API_KEY}`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ token: mintCustomToken(uid), returnSecureToken: true }),
  });
  const j = await res.json();
  if (!res.ok) throw new Error(`sign-in failed for ${uid}: ${res.status}`);
  return j.idToken;
}

const BASE = `https://firestore.googleapis.com/v1/projects/${PROJECT}/databases/default/documents`;
const RELDOC = `projects/${PROJECT}/databases/default/documents`;

const results = [];
async function check(name, want, fn) {
  try {
    const r = await fn();
    const pass = r.status === want;
    let detail = `HTTP ${r.status}`;
    if (!pass) {
      try {
        const j = await r.json();
        detail += ` ${j.error?.status ?? ""} ${(j.error?.message ?? "").slice(0, 110)}`;
      } catch { /* body not JSON */ }
    }
    results.push({ name, want: `${want}`, got: detail, pass });
  } catch (e) {
    results.push({ name, want: `${want}`, got: `THREW ${e.message}`, pass: false });
  }
}

async function main() {
  const saTok = await saAccessToken();
  const saH = { Authorization: `Bearer ${saTok}`, "Content-Type": "application/json" };

  const aTok = await idTokenFor(PROBE_UID);
  const hTok = await idTokenFor(HELPER_UID);
  const aH = { Authorization: `Bearer ${aTok}`, "Content-Type": "application/json" };
  const hH = { Authorization: `Bearer ${hTok}`, "Content-Type": "application/json" };
  console.log(`identities minted: ${PROBE_UID} (recipient) + ${HELPER_UID} (helper)`);

  const run = `prb184-${Date.now()}`;
  const invId = `${run}-inv`;
  const relId = `${HELPER_UID}_${PROBE_UID}`;
  const devHex24 = crypto.randomBytes(12).toString("hex");
  const PROBE_TOKEN = "0123456789abcdef0123";
  const created = {
    sessions: [],
    signals: [],
    rels: [relId],
    invites: [invId],
    presence: [`${PROBE_UID}_${devHex24}`],
    tokens: [],
    users: [PROBE_UID, HELPER_UID],
  };

  // ---------------- fixture: family invitation + atomic redeem ----------------
  const t0 = Date.now();
  let r = await fetch(`${BASE}/pairingInvitations?documentId=${invId}`, {
    method: "POST", headers: aH,
    body: JSON.stringify({
      fields: {
        inviterUid: F(PROBE_UID), inviterEmail: F("rules-audit-a@invalid.local"),
        recipientDeviceId: F(devHex24), recipientDeviceModel: F("rules-audit-synthetic"),
        status: F("PENDING"), kind: F("family"), tokenHash: F("c".repeat(64)),
        token: F(PROBE_TOKEN), createdAt: TS(t0), expiresAt: TS(t0 + 5 * 60_000),
      },
    }),
  });
  if (r.status !== 200) { console.error(`✘ fixture invitation failed: HTTP ${r.status}`); process.exit(1); }

  r = await fetch(`${BASE}:commit`, {
    method: "POST", headers: hH,
    body: JSON.stringify({
      writes: [
        {
          update: {
            name: `${RELDOC}/pairingInvitations/${invId}`,
            fields: {
              status: F("PAIRED"), claimedBy: F(HELPER_UID),
              claimedEmail: F("rules-audit-h@invalid.local"),
              claimedAt: TS(Date.now()), updatedAt: TS(Date.now()),
              claimToken: F(PROBE_TOKEN),
            },
          },
          updateMask: { fieldPaths: ["status", "claimedBy", "claimedEmail", "claimedAt", "updatedAt", "claimToken"] },
        },
        {
          update: {
            name: `${RELDOC}/trustedDeviceRelationships/${relId}`,
            fields: {
              helperUid: F(HELPER_UID), helperEmail: F("rules-audit-h@invalid.local"),
              recipientUid: F(PROBE_UID), recipientEmail: F("rules-audit-a@invalid.local"),
              recipientDeviceId: F(devHex24), recipientDeviceModel: F("rules-audit-synthetic"),
              pairKey: F(relId), status: F("ACTIVE"), kind: F("family"),
              createdByInvitation: F(invId), createdAt: TS(Date.now()), updatedAt: TS(Date.now()),
            },
          },
        },
      ],
    }),
  });
  if (r.status !== 200) { console.error(`✘ fixture redeem failed: HTTP ${r.status}`); process.exit(1); }
  console.log("fixture ready: family relationship ACTIVE");

  /** helper creates a REQUESTED session doc with sane values. */
  async function makeSession(sfx, deadlineMs) {
    const id = `${run}-${sfx}`;
    const res = await fetch(`${BASE}/supportSessions?documentId=${id}`, {
      method: "POST", headers: hH,
      body: JSON.stringify({
        fields: {
          relId: F(relId), helperUid: F(HELPER_UID), helperEmail: F("rules-audit-h@invalid.local"),
          recipientUid: F(PROBE_UID), recipientEmail: F("rules-audit-a@invalid.local"),
          recipientDeviceModel: F("rules-audit-synthetic"), status: F("REQUESTED"),
          kind: F("family"), requestedAt: TS(Date.now()),
          respondDeadline: TS(deadlineMs ?? Date.now() + 4 * 60_000),
          createdAt: TS(Date.now()), updatedAt: TS(Date.now()),
        },
      }),
    });
    if (res.status === 200) created.sessions.push(id);
    return { res, id };
  }

  /** helper creates a signal under a session. */
  async function makeSignal(sessId, sfx, type, payloadLen, epLen = 10, audUid = PROBE_UID) {
    const sid = `${run}-${sfx}`;
    const res = await fetch(`${BASE}/supportSessions/${sessId}/signal?documentId=${sid}`, {
      method: "POST", headers: hH,
      body: JSON.stringify({
        fields: {
          aud: F(audUid), from: F(HELPER_UID), type: F(type),
          payload: F("x".repeat(payloadLen)), ep: F("e".repeat(epLen)),
          createdAt: TS(Date.now()),
        },
      }),
    });
    if (res.status === 200) created.signals.push(`${sessId}/signal/${sid}`);
    return res;
  }

  // ================= battery =================
  // S1: honest flow start → recipient grants (+15 min, app-identical).
  const s1 = await makeSession("s1");
  await check("A0 helper ask on ACTIVE family rel (positive)", 200, () => s1.res);

  await check("A1 recipient honest grant REQUESTED→ACTIVE (positive)", 200, async () =>
    fetch(`${BASE}/supportSessions/${s1.id}?updateMask.fieldPaths=status&updateMask.fieldPaths=respondedAt&updateMask.fieldPaths=grantedAt&updateMask.fieldPaths=sessionEndsAt&updateMask.fieldPaths=updatedAt`, {
      method: "PATCH", headers: aH,
      body: JSON.stringify({
        fields: {
          status: F("ACTIVE"), respondedAt: TS(Date.now()), grantedAt: TS(Date.now()),
          sessionEndsAt: TS(Date.now() + 15 * 60_000), updatedAt: TS(Date.now()),
        },
      }),
    }),
  );

  // THE 18.4-adjacent attack: a participant forges the sweep's marks to
  // suppress/redirect the kill-push (the marks are admin-only).
  await check("A2 helper forges killPushSentAt+killPushStatus", 403, () =>
    fetch(`${BASE}/supportSessions/${s1.id}?updateMask.fieldPaths=killPushSentAt&updateMask.fieldPaths=killPushStatus&updateMask.fieldPaths=updatedAt`, {
      method: "PATCH", headers: hH,
      body: JSON.stringify({
        fields: {
          killPushSentAt: TS(Date.now()), killPushStatus: F("sent:1/1"), updatedAt: TS(Date.now()),
        },
      }),
    }),
  );
  await check("A3 helper forges killPushClaimAt only (no status)", 403, () =>
    fetch(`${BASE}/supportSessions/${s1.id}?updateMask.fieldPaths=killPushClaimAt&updateMask.fieldPaths=updatedAt`, {
      method: "PATCH", headers: hH,
      body: JSON.stringify({ fields: { killPushClaimAt: TS(Date.now()), updatedAt: TS(Date.now()) } }),
    }),
  );

  // Wrong-side grant + over-cap window.
  const s2 = await makeSession("s2");
  await check("A4 helper tries REQUESTED→ACTIVE (wrong side)", 403, () =>
    fetch(`${BASE}/supportSessions/${s2.id}?updateMask.fieldPaths=status&updateMask.fieldPaths=sessionEndsAt&updateMask.fieldPaths=updatedAt`, {
      method: "PATCH", headers: hH,
      body: JSON.stringify({
        fields: { status: F("ACTIVE"), sessionEndsAt: TS(Date.now() + 15 * 60_000), updatedAt: TS(Date.now()) },
      }),
    }),
  );
  const s3 = await makeSession("s3");
  await check("A5 recipient grant with sessionEndsAt +21 min (over cap)", 403, () =>
    fetch(`${BASE}/supportSessions/${s3.id}?updateMask.fieldPaths=status&updateMask.fieldPaths=sessionEndsAt&updateMask.fieldPaths=updatedAt`, {
      method: "PATCH", headers: aH,
      body: JSON.stringify({
        fields: { status: F("ACTIVE"), sessionEndsAt: TS(Date.now() + 21 * 60_000), updatedAt: TS(Date.now()) },
      }),
    }),
  );

  // Per-type signal payload caps (18.5 hardening) — hostile vs honest.
  await check("A6 cmd payload 5000 chars (> 4096)", 403, () => makeSignal(s1.id, "sig6", "cmd", 5000));
  await check("A7 cmd payload 1000 chars (positive)", 200, () => makeSignal(s1.id, "sig7", "cmd", 1000));
  await check("A8 ice payload 17000 chars (> 16384)", 403, () => makeSignal(s1.id, "sig8", "ice", 17000));
  await check("A9 ice payload 2000 chars (positive)", 200, () => makeSignal(s1.id, "sig9", "ice", 2000));
  await check("A10 watch payload 300 chars (> 256)", 403, () => makeSignal(s1.id, "sig10", "watch", 300));
  await check("A11 unknown signal type 'evil'", 403, () => makeSignal(s1.id, "sig11", "evil", 10));
  await check("A12 ep 70 chars (> 64)", 403, () => makeSignal(s1.id, "sig12", "cmd", 10, 70));
  await check("A13 signal addressed to SELF (aud == from)", 403, () =>
    makeSignal(s1.id, "sig13", "cmd", 10, 10, HELPER_UID),
  );

  // Transition hygiene + deadline cap.
  await check("A14 REQUESTED→EXPIRED with extra respondedAt", 403, () =>
    fetch(`${BASE}/supportSessions/${s2.id}?updateMask.fieldPaths=status&updateMask.fieldPaths=respondedAt&updateMask.fieldPaths=updatedAt`, {
      method: "PATCH", headers: hH,
      body: JSON.stringify({ fields: { status: F("EXPIRED"), respondedAt: TS(Date.now()), updatedAt: TS(Date.now()) } }),
    }),
  );
  await check("A15 session create with deadline +6 min (> 5 min)", 403, () =>
    makeSession("s4x", Date.now() + 6 * 60_000).then((x) => x.res),
  );

  // Profile/registry type+size hardening.
  await check("A16 users displayName 900 KB blob", 403, () =>
    fetch(`${BASE}/users/${PROBE_UID}?updateMask.fieldPaths=displayName&updateMask.fieldPaths=updatedAt`, {
      method: "PATCH", headers: aH,
      body: JSON.stringify({ fields: { displayName: F("N".repeat(900_000)), updatedAt: TS(Date.now()) } }),
    }),
  );
  await check("A17 users displayName 200 chars (positive)", 200, async () => {
    const res = await fetch(`${BASE}/users/${PROBE_UID}?updateMask.fieldPaths=displayName&updateMask.fieldPaths=email&updateMask.fieldPaths=updatedAt`, {
      method: "PATCH", headers: aH,
      body: JSON.stringify({
        fields: { displayName: F("Audit Probe"), email: F("rules-audit-a@invalid.local"), updatedAt: TS(Date.now()) },
      }),
    });
    return res;
  });
  await check("A18 devicePresence model as int", 403, () =>
    fetch(`${BASE}/devicePresence/${PROBE_UID}_${devHex24}`, {
      method: "PATCH", headers: aH,
      body: JSON.stringify({
        fields: {
          uid: F(PROBE_UID), deviceId: F(devHex24), model: I(123),
          heartbeatAt: TS(Date.now()), updatedAt: TS(Date.now()),
        },
      }),
    }),
  );
  await check("A18b devicePresence heartbeatAt as string", 403, () =>
    fetch(`${BASE}/devicePresence/${PROBE_UID}_${devHex24}`, {
      method: "PATCH", headers: aH,
      body: JSON.stringify({
        fields: {
          uid: F(PROBE_UID), deviceId: F(devHex24), model: F("rules-audit-synthetic"),
          heartbeatAt: F("not-a-timestamp"), updatedAt: TS(Date.now()),
        },
      }),
    }),
  );
  const tokId = crypto.createHash("sha256").update(`audit-${run}`).digest("hex").slice(0, 24);
  created.tokens.push(tokId);
  await check("A19 fcmTokens updatedAt as string", 403, () =>
    fetch(`${BASE}/fcmTokens/${tokId}`, {
      method: "PATCH", headers: aH,
      body: JSON.stringify({
        fields: {
          uid: F(PROBE_UID), deviceId: F(devHex24),
          token: F("t".repeat(142)), updatedAt: F("not-a-timestamp"),
        },
      }),
    }).then(async (bad) => {
      // PATCH on a missing doc creates it — if the rule FAILED to deny we must
      // not leave residue; if denied (403) nothing was written.
      return bad;
    }),
  );

  // After END, the signal surface must close (server-side window enforcement).
  await check("A20 recipient honest END (positive)", 200, () =>
    fetch(`${BASE}/supportSessions/${s1.id}?updateMask.fieldPaths=status&updateMask.fieldPaths=endedAt&updateMask.fieldPaths=endedBy&updateMask.fieldPaths=updatedAt`, {
      method: "PATCH", headers: aH,
      body: JSON.stringify({ fields: { status: F("ENDED"), endedAt: TS(Date.now()), endedBy: F(PROBE_UID), updatedAt: TS(Date.now()) } }),
    }),
  );
  await check("A21 cmd signal after ENDED (window closed)", 403, () =>
    makeSignal(s1.id, "sig21", "cmd", 10),
  );

  // ---------------- cleanup (SA — zero residue) ----------------
  const delPaths = [
    ...created.signals.map((p) => `supportSessions/${p}`),
    ...created.sessions.map((id) => `supportSessions/${id}`),
    ...created.rels.map((id) => `trustedDeviceRelationships/${id}`),
    ...created.invites.map((id) => `pairingInvitations/${id}`),
    ...created.presence.map((id) => `devicePresence/${id}`),
    ...created.tokens.map((id) => `fcmTokens/${id}`),
    ...created.users.map((id) => `users/${id}`),
  ];
  let ok = 0, fail = [];
  for (const p of delPaths) {
    try {
      const d = await fetch(`${BASE}/${p}`, { method: "DELETE", headers: saH });
      if (d.ok || d.status === 404) ok += 1; else fail.push(`${p.split("/").slice(-2).join("/")}=${d.status}`);
    } catch (e) {
      fail.push(`${p}=${e.message}`);
    }
  }
  console.log(`cleanup (SA): ${ok}/${delPaths.length} deleted${fail.length ? " — FAILED: " + fail.join(", ") : ""}`);

  let authOk = 0;
  for (const tok of [aTok, hTok]) {
    try {
      const d = await fetch(`https://identitytoolkit.googleapis.com/v1/accounts:delete?key=${API_KEY}`, {
        method: "POST", headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ idToken: tok }),
      });
      if (d.ok) authOk += 1;
    } catch { /* count below */ }
  }
  console.log(`synthetic Auth user cleanup: ${authOk}/2`);

  // ---------------- report ----------------
  console.log("\n────────── Phase 18.5 audit-probe results ──────────");
  let all = true;
  for (const r of results) {
    console.log(`${r.pass ? "✔" : "✘"} ${r.name.padEnd(52)} expected ${String(r.want).padEnd(9)} got ${r.got}`);
    all = all && r.pass;
  }
  console.log(all ? "\n✔✔ ALL EXPECTATIONS MET — 18.5 hardening proven live." : "\n✘✘ DRIFT — at least one expectation failed.");
  process.exit(all && fail.length === 0 ? 0 : 1);
}

main().catch((e) => { console.error("✘ fatal:", e.message); process.exit(1); });
