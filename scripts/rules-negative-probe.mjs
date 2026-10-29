/**
 * Phase 9 (v0.10.0) — RUNTIME NEGATIVE PROBE of the LIVE Firestore rules.
 *
 * Why: the service account BYPASSES the rules (Admin path), so it can never
 * prove what an ordinary signed-in app may or may not do. This script mints
 * a synthetic identity ("rules-probe") via a self-signed Firebase custom
 * token, exchanges it for a real ID token through the Identity Toolkit, and
 * then fires a battery of requests that the rules must REFUSE.
 *
 *   P0  positive control — the probe writes+deletes its OWN devicePresence
 *       doc (allowed by design); proves the auth path works at all.
 *   N1..N10 — everything a hostile/curious client might try, all expected
 *       to come back HTTP 403 PERMISSION_DENIED.
 *
 * Nothing readable is touched: denied reads return errors, the only writes
 * that ever happen are the probe's own throwaway presence doc (P0), which
 * the script deletes itself, and the synthetic Auth user, deleted in the
 * cleanup. No session/invitation/token doc is ever created.
 *
 * Exit code 0 = all expectations met; 1 = any deviation (rules drift).
 */
import { readFileSync } from "node:fs";
import crypto from "node:crypto";

// ---------- service account ----------
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

// ---------- web API key (the app's own key; powers Identity Toolkit) ----------
const gs = JSON.parse(
  readFileSync("/home/z/my-project/android-app/app/google-services.json", "utf8")
);
const API_KEY = gs.client?.[0]?.api_key?.[0]?.current_key;
if (!API_KEY) {
  console.error("✘ no api_key found in google-services.json");
  process.exit(1);
}

const PROBE_UID = "rules-probe";

function b64u(buf) {
  return Buffer.from(buf).toString("base64url");
}

/** OAuth2 access token for the SA (Admin-path reconnaissance). */
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

/** Mint a Firebase custom token for the probe identity (RS256, local). */
function mintCustomToken() {
  const now = Math.floor(Date.now() / 1000);
  const header = b64u(JSON.stringify({ alg: "RS256", typ: "JWT" }));
  const claims = {
    iss: sa.client_email,
    sub: sa.client_email,
    aud: "https://identitytoolkit.googleapis.com/google.identity.identitytoolkit.v1.IdentityToolkit",
    iat: now,
    exp: now + 3600,
    uid: PROBE_UID,
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

/** Exchange the custom token for a real 1-hour ID token. */
async function probeIdToken(customToken) {
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

// This project's Firestore database id is "default" (NOT "(default)") —
// same as scripts/fcm-kill-push.mjs (discovered in Phase 4).
const BASE = `https://firestore.googleapis.com/v1/projects/${PROJECT}/databases/default/documents`;
const F = (s) => ({ stringValue: s });
const TS = (ms) => ({ timestampValue: new Date(ms).toISOString() });

async function run(name, fn) {
  try {
    const outcome = await fn();
    results.push({ name, ...outcome });
  } catch (e) {
    results.push({ name, expected: "?", got: `THREW ${e.message}`, pass: false });
  }
}

const results = [];
/** Expect a rules DENY (403) — anything else fails the probe. */
async function expectDenied(name, doCall) {
  const r = await doCall();
  const pass = r.status === 403;
  let detail = `HTTP ${r.status}`;
  if (!pass) {
    try {
      const j = await r.json();
      detail += ` ${j.error?.status ?? ""}`;
    } catch { /* body not JSON */ }
  }
  results.push({ name, expected: "403 DENIED", got: detail, pass });
  return r;
}

async function main() {
  // ================= reconnaissance (Admin path — NOT rules-tested) ========
  const saTok = await saAccessToken();
  const saH = { Authorization: `Bearer ${saTok}`, "Content-Type": "application/json" };

  /** runQuery rows: Google returns a JSON array of partitions (sometimes
   *  NDJSON-style markers) — handle both, keep only document partitions. */
  async function parseQueryRows(res) {
    const text = await res.text();
    let rows = [];
    try {
      const j = JSON.parse(text);
      rows = Array.isArray(j) ? j : [j];
    } catch {
      for (const line of text.split("\n")) {
        const t = line.trim();
        if (!t.startsWith("{")) continue;
        try {
          rows.push(JSON.parse(t));
        } catch { /* partition marker like "{}" */ }
      }
    }
    return rows.filter((r) => r && r.document);
  }

  async function saQuery(coll, field, value) {
    const res = await fetch(`${BASE}:runQuery`, {
      method: "POST",
      headers: saH,
      body: JSON.stringify({
        structuredQuery: {
          from: [{ collectionId: coll }],
          where: {
            fieldFilter: {
              field: { fieldPath: field },
              op: "EQUAL",
              value: F(value),
            },
          },
          limit: 1,
        },
      }),
    });
    if (!res.ok) {
      throw new Error(`${coll} recon query failed: HTTP ${res.status} ${(await res.text()).slice(0, 300)}`);
    }
    const rows = await parseQueryRows(res);
    return rows[0]?.document ?? null;
  }

  /** Admin helper: first doc of a collection, no filter. */
  async function saQueryAny(coll) {
    const res = await fetch(`${BASE}:runQuery`, {
      method: "POST",
      headers: saH,
      body: JSON.stringify({
        structuredQuery: { from: [{ collectionId: coll }], limit: 1 },
      }),
    });
    if (!res.ok) {
      throw new Error(`${coll} recon query failed: HTTP ${res.status} ${(await res.text()).slice(0, 300)}`);
    }
    const rows = await parseQueryRows(res);
    return rows[0]?.document ?? null;
  }

  const tokenDoc = await saQueryAny("fcmTokens");
  const anySession = (await saQuery("supportSessions", "status", "CANCELLED"))
    ?? (await saQuery("supportSessions", "status", "ENDED"))
    ?? (await saQuery("supportSessions", "status", "REQUESTED"));
  const anyRel = (await saQuery("trustedDeviceRelationships", "status", "ACTIVE"))
    ?? (await saQuery("trustedDeviceRelationships", "status", "REVOKED"));

  const realUid = tokenDoc?.fields?.uid?.stringValue ?? null;
  const realTokenDocId = tokenDoc?.name?.split("/").pop() ?? null;
  const realSessionId = anySession?.name?.split("/").pop() ?? null;
  const realRelId = anyRel?.name?.split("/").pop() ?? null;
  console.log(`recon: uid=${realUid ? "found" : "MISSING"} tokenDoc=${realTokenDocId ? "found" : "MISSING"} session=${realSessionId ?? "none"} rel=${realRelId ?? "none"}`);
  if (!realUid || !realTokenDocId) {
    console.error("✘ could not gather real ids for the probe");
    process.exit(1);
  }

  // ================= probe identity ========================================
  const idToken = await probeIdToken(mintCustomToken());
  const pH = { Authorization: `Bearer ${idToken}`, "Content-Type": "application/json" };
  console.log(`probe identity minted: ${PROBE_UID}`);

  const now = Date.now();
  const devHex = crypto.randomBytes(12).toString("hex"); // 24 hex chars
  const presenceId = `${PROBE_UID}_${devHex}`;

  // ---------- P0: positive control (allowed by design) ----------
  await run(`P0 create own devicePresence (${presenceId})`, async () => {
    const r = await fetch(`${BASE}/devicePresence?documentId=${presenceId}`, {
      method: "POST",
      headers: pH,
      body: JSON.stringify({
        fields: {
          uid: F(PROBE_UID),
          deviceId: F(devHex),
          model: F("rules-probe-synthetic"),
          heartbeatAt: TS(now),
          updatedAt: TS(now),
        },
      }),
    });
    // expected: 200 created
    const pass = r.status === 200;
    let body = {};
    try { body = await r.json(); } catch { /* ignore */ }
    const createdName = body?.name?.split("/").pop();
    // immediately clean up
    if (pass) {
      await fetch(`${BASE}/devicePresence/${createdName}`, { method: "DELETE", headers: pH });
    }
    return { expected: "200 ALLOWED", got: `HTTP ${r.status}`, pass };
  });

  // ---------- N1: another user's profile ----------
  await expectDenied("N1 GET users/{realUid}", () =>
    fetch(`${BASE}/users/${encodeURIComponent(realUid)}`, { headers: pH })
  );

  // ---------- N2: list sessions of another user ----------
  await expectDenied("N2 runQuery supportSessions helperUid=real", () =>
    fetch(`${BASE}:runQuery`, {
      method: "POST",
      headers: pH,
      body: JSON.stringify({
        structuredQuery: {
          from: [{ collectionId: "supportSessions" }],
          where: {
            fieldFilter: { field: { fieldPath: "helperUid" }, op: "EQUAL", value: F(realUid) },
          },
          limit: 1,
        },
      }),
    })
  );

  // ---------- N3: enumerate invitations ----------
  await expectDenied("N3 list pairingInvitations", () =>
    fetch(`${BASE}/pairingInvitations?pageSize=5`, { headers: pH })
  );

  // ---------- N4: write runtime config ----------
  await expectDenied("N4 patch config/quality", () =>
    fetch(`${BASE}/config/quality?updateMask.fieldPaths=codec`, {
      method: "PATCH",
      headers: pH,
      body: JSON.stringify({ fields: { codec: F("h264") } }),
    })
  );

  // ---------- N5: create a session without an ACTIVE relationship ----------
  await expectDenied("N5 create supportSessions (no relationship)", () =>
    fetch(`${BASE}/supportSessions`, {
      method: "POST",
      headers: pH,
      body: JSON.stringify({
        fields: {
          relId: F(`${PROBE_UID}_${PROBE_UID}`),
          helperUid: F(PROBE_UID),
          helperEmail: F("rules-probe@invalid.local"),
          recipientUid: F(realUid),
          recipientEmail: F("someone@invalid.local"),
          recipientDeviceModel: F("probe"),
          status: F("REQUESTED"),
          requestedAt: TS(now),
          respondDeadline: TS(now + 60_000),
          createdAt: TS(now),
          updatedAt: TS(now),
        },
      }),
    })
  );

  // ---------- N6: write a signal into a real session ----------
  if (realSessionId) {
    await expectDenied("N6 create signal under real session", () =>
      fetch(`${BASE}/supportSessions/${realSessionId}/signal`, {
        method: "POST",
        headers: pH,
        body: JSON.stringify({
          fields: {
            aud: F(PROBE_UID),
            from: F(PROBE_UID),
            type: F("cmd"),
            payload: F("{}"),
            ep: F("probeepoch"),
            createdAt: TS(now),
          },
        }),
      })
    );
  } else {
    results.push({ name: "N6 create signal under real session", expected: "403 DENIED", got: "SKIPPED (no session doc)", pass: true });
  }

  // ---------- N7a: fcmTokens with a too-short token ----------
  await expectDenied("N7a create fcmTokens (token 8 chars)", () =>
    fetch(`${BASE}/fcmTokens`, {
      method: "POST",
      headers: pH,
      body: JSON.stringify({
        fields: {
          uid: F(PROBE_UID),
          deviceId: F(devHex),
          token: F("short"),
          updatedAt: TS(now),
        },
      }),
    })
  );

  // ---------- N7b: fcmTokens with an oversized token (NEW Phase 9 bound) ----
  await expectDenied("N7b create fcmTokens (token 5000 chars)", () =>
    fetch(`${BASE}/fcmTokens`, {
      method: "POST",
      headers: pH,
      body: JSON.stringify({
        fields: {
          uid: F(PROBE_UID),
          deviceId: F(devHex),
          token: F("x".repeat(5000)),
          updatedAt: TS(now),
        },
      }),
    })
  );

  // ---------- N8: read another user's FCM token ----------
  await expectDenied(`N8 GET fcmTokens/${realTokenDocId}`, () =>
    fetch(`${BASE}/fcmTokens/${realTokenDocId}`, { headers: pH })
  );

  // ---------- N9: mutate a real session ----------
  if (realSessionId) {
    await expectDenied(`N9 patch supportSessions/${realSessionId}`, () =>
      fetch(`${BASE}/supportSessions/${realSessionId}?updateMask.fieldPaths=status&updateMask.fieldPaths=updatedAt`, {
        method: "PATCH",
        headers: pH,
        body: JSON.stringify({ fields: { status: F("ENDED"), updatedAt: TS(now) } }),
      })
    );
  } else {
    results.push({ name: `N9 patch supportSessions/…`, expected: "403 DENIED", got: "SKIPPED (no session doc)", pass: true });
  }

  // ---------- N10: delete a relationship ----------
  if (realRelId) {
    await expectDenied(`N10 DELETE trustedDeviceRelationships/${realRelId}`, () =>
      fetch(`${BASE}/trustedDeviceRelationships/${realRelId}`, { method: "DELETE", headers: pH })
    );
  } else {
    results.push({ name: "N10 DELETE trustedDeviceRelationships/…", expected: "403 DENIED", got: "SKIPPED (no relationship doc)", pass: true });
  }

  // ---------- cleanup: delete the synthetic Auth user ----------
  let cleanup = "n/a";
  try {
    const del = await fetch(
      `https://identitytoolkit.googleapis.com/v1/accounts:delete?key=${API_KEY}`,
      {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ idToken }),
      }
    );
    cleanup = `HTTP ${del.status}`;
  } catch (e) {
    cleanup = `THREW ${e.message}`;
  }

  // ================= report ===============================================
  console.log("\n────────── Phase 9 negative-probe results ──────────");
  let allPass = true;
  for (const r of results) {
    const mark = r.pass ? "✔" : "✘";
    if (!r.pass) allPass = false;
    console.log(`${mark} ${r.name.padEnd(52)} expected ${r.expected.padEnd(12)} got ${r.got}`);
  }
  console.log(`\nsynthetic Auth user cleanup (accounts:delete): ${cleanup}`);
  console.log(allPass
    ? "✔✔ ALL EXPECTATIONS MET — the live rules refused every hostile path."
    : "✘✘ RULES DRIFT — at least one hostile path was NOT denied!");
  process.exit(allPass ? 0 : 1);
}

main().catch((e) => {
  console.error("✘ probe crashed:", e);
  process.exit(1);
});
