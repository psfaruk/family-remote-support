/**
 * Location master plan Phase 8 (v0.39.0) — RUNTIME PROBE of the LIVE
 * "locationAuthorizations" + "users/{uid}/locationHistory" Firestore rules
 * and the NEW authorization read path on "liveLocations".
 *
 * Machinery cloned from scripts/rules-liveLocations-probe.mjs (which stays
 * untouched as the L-series legacy regression source): three synthetic
 * identities via self-signed custom tokens, real ID tokens, Firestore REST
 * calls that exercise the rules exactly like the app would. Fixtures for
 * hostile states (expired / revoked / history-disabled authorizations, an
 * expired-retention history record) go through the SA ADMIN path (rules
 * bypass) because the rules themselves refuse to create those states —
 * which is exactly what the negative cases prove.
 *
 *   ── A-series (locationAuthorizations doc) ──────────────────────────
 *   P0  owner create+read+delete own grant (lifecycle control).
 *   A1  owner create grant (family, live+history, WITH_HISTORY/H24) 200.
 *   A2  recipient create the same doc                                403.
 *   A3  owner create with a FORGED docId                             403.
 *   A4  recipient self-escalation PATCH (liveEnabled/historyEnabled) 403.
 *   A8  stranger GET the grant                                       403.
 *   A9  recipient GET their own grant                                200.
 *   A10 recipient UNFILTERED list of the collection                  403.
 *   A11 recipient FILTERED list (recipientUserId == me)              200.
 *   A5  owner revoke (masked PATCH revokedAt+updatedAt)              200.
 *   A6  owner update-after-revoke (frozen doc)                       403.
 *   A12 owner DELETE the revoked grant (the re-grant path)           200.
 *
 *   ── H-series (users/{owner}/locationHistory) ───────────────────────
 *   H1  owner append a valid record                                  200.
 *   H2  owner append with an extra forged key                        403.
 *   H3  owner append lat 91.0                                        403.
 *   H4  owner append speed 91 m/s                                    403.
 *   H5  owner append recordedAt 11 min in the past                   403.
 *   H6  authorized family GET a record                               200.
 *   H7  authorized family FILTERED list (retentionExpiresAt > now)   200.
 *   H14 owner FILTERED list                                          200.
 *   H9  revoked-grant recipient GET                                  403.
 *   H8  history-disabled grant recipient GET (SA fixture)            403.
 *   H10 expired-retention record GET — by the OWNER (invisible to
 *       everyone before physical deletion, design §6.1)              403.
 *   H11 owner UNFILTERED list (one expired record poisons the query) 403.
 *   H12 cross-user append (recipient writing the owner's path)       403.
 *   H13 stranger FILTERED list                                       403.
 *
 *   ── L-series (liveLocations dual read path, design §4.4) ───────────
 *   L1  legacy: ACTIVE family relationship, NO grant → helper GET     200.
 *   L0  no relationship, no grant → helper GET                       403.
 *   L2  grant alone (family), relationship DELETED → helper GET      200.
 *   L4  revoked grant → helper GET                                   403.
 *   L3  expired grant (SA fixture) → helper GET                      403.
 *   L5  sharingEnabled off → helper GET                              403.
 *   L6  stranger (nothing) → GET                                     403.
 *
 * Nothing of any real user is touched. Every synthetic doc (relationship,
 * liveLocations, locationAuthorizations, both history records — this run's
 * AND crashed-run leftovers) and all three synthetic Auth users are
 * deleted in cleanup — zero residue. Exit code 0 = every expectation met.
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

const OWNER_UID = "rules-loc2"; // the sharing device owner (the SHARER)
const HELPER_UID = "rules-loc2-h"; // the recipient (family counterpart)
const STRANGER_UID = "rules-loc2-x"; // a third, never-paired identity

function b64u(buf) {
  return Buffer.from(buf).toString("base64url");
}

/** OAuth2 access token for the SA (Admin-path fixtures + cleanup). */
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

/** Mint a Firebase custom token for a synthetic identity (RS256, local). */
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
// same as scripts/rules-liveLocations-probe.mjs (discovered in Phase 4).
const BASE = `https://firestore.googleapis.com/v1/projects/${PROJECT}/databases/default/documents`;
const F = (s) => ({ stringValue: s });
const TS = (ms) => ({ timestampValue: new Date(ms).toISOString() });
const I = (n) => ({ integerValue: String(n) });
const D = (n) => ({ doubleValue: n });
const B = (b) => ({ booleanValue: b });

const results = [];

async function run(name, fn) {
  try {
    const outcome = await fn();
    results.push({ name, ...outcome });
  } catch (e) {
    results.push({ name, expected: "?", got: `THREW ${e.message}`, pass: false });
  }
}

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

/** Expect a rules ALLOW with an exact HTTP status — anything else fails. */
async function expectAllowed(name, wantStatus, doCall) {
  const r = await doCall();
  const pass = r.status === wantStatus;
  let detail = `HTTP ${r.status}`;
  if (!pass) {
    try {
      const j = await r.json();
      detail += ` ${j.error?.status ?? ""} ${(j.error?.message ?? "").slice(0, 120)}`;
    } catch { /* body not JSON */ }
  }
  results.push({ name, expected: `${wantStatus} ALLOWED`, got: detail, pass });
  return r;
}

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

async function main() {
  // ================= SA (Admin path — fixtures + cleanup only) =============
  const saTok = await saAccessToken();
  const saH = { Authorization: `Bearer ${saTok}`, "Content-Type": "application/json" };

  // ---------- quota canary (v0.39.0 incident hardening) ----------
  // One cheap SA read of a known-absent doc: 404 = quota available;
  // 429 = the project's daily free READ units are exhausted — abort BEFORE
  // creating any fixture, with a clear rerun instruction (the 2026-11-01
  // first run died exactly this way mid-suite: worklog §79).
  const canary = await fetch(`${BASE}/locationAuthorizations/quota-canary-nonexistent`, { headers: saH });
  if (canary.status === 429) {
    console.error("✘ Firestore daily read quota exhausted (429 on the canary read).");
    console.error("  The probe created NOTHING. Rerun after the Pacific-midnight quota");
    console.error("  reset (~07:00 UTC). Exit 2 = quota, not rules drift.");
    process.exit(2);
  }
  if (canary.status !== 404) {
    console.error(`◐ canary returned HTTP ${canary.status} (expected 404) — continuing, but watch for 429s below.`);
  }

  // ================= probe identities =======================================
  const ownerToken = await probeIdToken(mintCustomToken(OWNER_UID));
  const helperToken = await probeIdToken(mintCustomToken(HELPER_UID));
  const strangerToken = await probeIdToken(mintCustomToken(STRANGER_UID));
  const oH = { Authorization: `Bearer ${ownerToken}`, "Content-Type": "application/json" };
  const hH = { Authorization: `Bearer ${helperToken}`, "Content-Type": "application/json" };
  const xH = { Authorization: `Bearer ${strangerToken}`, "Content-Type": "application/json" };
  console.log(`identities minted: ${OWNER_UID} (sharer) + ${HELPER_UID} (recipient) + ${STRANGER_UID} (stranger)`);

  const devHex = crypto.randomBytes(12).toString("hex"); // 24 hex chars
  const locDocId = `${OWNER_UID}_${devHex}`;
  const relId = `${HELPER_UID}_${OWNER_UID}`; // pairKey helperUid_recipientUid
  const authzId = `${HELPER_UID}_${OWNER_UID}_${devHex}`;
  const recId = `${devHex}_${Date.now()}`; // valid history record
  const recIdExp = `${devHex}_${Date.now() + 1}`; // expired-retention record
  const FAKE_EMAILS = {
    [OWNER_UID]: "rules-loc2@invalid.local",
    [HELPER_UID]: "rules-loc2-helper@invalid.local",
  };

  // ---------- shared REST helpers ----------

  /** Well-formed liveLocations field map (attack cases override one field). */
  function locFields(overrides = {}) {
    return {
      uid: F(OWNER_UID),
      deviceId: F(devHex),
      lat: D(23.8),
      lng: D(90.4),
      accuracyMeters: I(25),
      sharingEnabled: B(true),
      updatedAt: TS(Date.now()), // fresh ⇒ inside the ±5 min rules window
      appVersionCode: I(58),
      ...overrides,
    };
  }

  const locGet = (headers) => fetch(`${BASE}/liveLocations/${locDocId}`, { headers });

  async function ownerSetSharing(enabled) {
    return fetch(`${BASE}/liveLocations/${locDocId}?updateMask.fieldPaths=sharingEnabled`, {
      method: "PATCH",
      headers: oH,
      body: JSON.stringify({ fields: { sharingEnabled: B(enabled) } }),
    });
  }

  /** Well-formed locationAuthorizations field map (family, live+history). */
  function authzFields(overrides = {}) {
    return {
      ownerUserId: F(OWNER_UID),
      recipientUserId: F(HELPER_UID),
      deviceId: F(devHex),
      recipientKind: F("family"),
      liveEnabled: B(true),
      historyEnabled: B(true),
      scope: F("WITH_HISTORY"),
      retentionPolicy: F("H24"),
      authorizationVersion: I(1),
      createdAt: TS(Date.now()),
      updatedAt: TS(Date.now()),
      ...overrides,
    };
  }

  const authzGet = (headers) => fetch(`${BASE}/locationAuthorizations/${authzId}`, { headers });

  async function authzPatch(headers, fields, mask) {
    const q = mask.map((m) => `updateMask.fieldPaths=${m}`).join("&");
    return fetch(`${BASE}/locationAuthorizations/${authzId}?${q}`, {
      method: "PATCH",
      headers,
      body: JSON.stringify({ fields }),
    });
  }

  /** SA masked PATCH of the authorization doc (hostile-state fixtures). */
  async function saAuthzPatch(fields, mask) {
    const q = mask.map((m) => `updateMask.fieldPaths=${m}`).join("&");
    return fetch(`${BASE}/locationAuthorizations/${authzId}?${q}`, {
      method: "PATCH",
      headers: saH,
      body: JSON.stringify({ fields }),
    });
  }

  /** Well-formed history record field map (attack cases override one field). */
  function histFields(overrides = {}) {
    return {
      ownerUserId: F(OWNER_UID),
      deviceId: F(devHex),
      lat: D(23.8101),
      lng: D(90.4125),
      accuracyMeters: I(18),
      recordedAt: TS(Date.now()),
      receivedAt: TS(Date.now()),
      speedMetersPerSecond: D(1.4),
      bearingDegrees: D(88.0),
      provider: F("fused"),
      retentionExpiresAt: TS(Date.now() + 24 * 3_600_000), // H24
      authorizationVersion: I(1),
      appVersionCode: I(58),
      ...overrides,
    };
  }

  async function histCreate(headers, docId, fields) {
    return fetch(`${BASE}/users/${OWNER_UID}/locationHistory?documentId=${docId}`, {
      method: "POST",
      headers,
      body: JSON.stringify({ fields }),
    });
  }

  const histGet = (headers, docId) =>
    fetch(`${BASE}/users/${OWNER_UID}/locationHistory/${docId}`, { headers });

  /**
   * Structured query on the locationHistory subcollection. The v1 REST
   * binding takes the parent document path in the URL
   * (documents/{...}:runQuery); if a gateway ever rejects that form, the
   * documented body-parent form is the fallback. A 403 from the FIRST form
   * is a genuine rules verdict (the answer), never a routing problem.
   */
  async function histRunQuery(headers, structuredQuery) {
    const urlForm = await fetch(`${BASE}/users/${OWNER_UID}:runQuery`, {
      method: "POST",
      headers,
      body: JSON.stringify({ structuredQuery }),
    });
    if (urlForm.status !== 404) return urlForm;
    return fetch(`${BASE}:runQuery`, {
      method: "POST",
      headers,
      body: JSON.stringify({
        parent: `projects/${PROJECT}/databases/default/documents/users/${OWNER_UID}`,
        structuredQuery,
      }),
    });
  }

  /** Fixtures must succeed — anything else aborts the probe (exit 1),
   *  printing the partial results so a mid-run failure is never silent
   *  (the 2026-11-01 quota abort taught this the hard way). */
  async function fixture(name, r) {
    if (r.status !== 200) {
      let detail = "";
      try { detail = (await r.text()).slice(0, 200); } catch { /* ignore */ }
      console.error(`✘ fixture failed: ${name} → HTTP ${r.status} ${detail}`);
      if (results.length) {
        console.error("── partial results before the abort ──");
        for (const row of results) {
          console.error(`  ${row.pass ? "✔" : "✘"} ${row.name} → ${row.got}`);
        }
      }
      process.exit(1);
    }
  }

  // ---------- fixtures: live doc + ACTIVE family relationship ----------
  await fixture("owner create liveLocations doc", await fetch(
    `${BASE}/liveLocations?documentId=${locDocId}`,
    { method: "POST", headers: oH, body: JSON.stringify({ fields: locFields() }) }
  ));
  await fetch(`${BASE}/trustedDeviceRelationships/${relId}`, { method: "DELETE", headers: saH });
  await fixture("SA create ACTIVE family relationship", await fetch(
    `${BASE}/trustedDeviceRelationships?documentId=${relId}`,
    {
      method: "POST",
      headers: saH,
      body: JSON.stringify({
        fields: {
          helperUid: F(HELPER_UID),
          helperEmail: F(FAKE_EMAILS[HELPER_UID]),
          recipientUid: F(OWNER_UID),
          recipientEmail: F(FAKE_EMAILS[OWNER_UID]),
          recipientDeviceId: F(devHex),
          recipientDeviceModel: F("rules-loc2-synthetic"),
          pairKey: F(relId),
          status: F("ACTIVE"),
          kind: F("family"),
          createdByInvitation: F("PRBLOC2"),
          createdAt: TS(Date.now()),
          updatedAt: TS(Date.now()),
        },
      }),
    }
  ));

  // ---------- L1: legacy read path (relationship alone, no grant) ----------
  await expectAllowed("L1 helper GET live (ACTIVE family rel, no grant)", 200, () => locGet(hH));

  // ---------- L0: relationship gone, no grant ----------
  await fixture("SA DELETE relationship", await fetch(
    `${BASE}/trustedDeviceRelationships/${relId}`, { method: "DELETE", headers: saH }
  ));
  await expectDenied("L0 helper GET live (no rel, no grant)", () => locGet(hH));

  // ---------- P0: grant lifecycle positive control ----------
  await run("P0 owner create+read+delete own grant", async () => {
    const c = await fetch(`${BASE}/locationAuthorizations?documentId=${authzId}`, {
      method: "POST",
      headers: oH,
      body: JSON.stringify({ fields: authzFields() }),
    });
    let gS = 0;
    let dS = 0;
    if (c.status === 200) {
      const g = await authzGet(oH);
      gS = g.status;
      if (g.status === 200) {
        const d = await fetch(`${BASE}/locationAuthorizations/${authzId}`, {
          method: "DELETE",
          headers: oH,
        });
        dS = d.status;
      }
    }
    const pass = c.status === 200 && gS === 200 && dS === 200;
    return { expected: "200/200/200", got: `${c.status}/${gS || "—"}/${dS || "—"}`, pass };
  });

  // ---------- A1: the real grant (family, live+history) ----------
  // Idempotence guard: an aborted earlier run (e.g. the 2026-11-01 quota
  // abort, whose cleanup never ran) may have left a grant behind at SOME
  // devHex; this run's own id can only collide through P0's delete being
  // interrupted — the owner-side DELETE (rules-legal, 404-tolerated)
  // makes the create below deterministic either way.
  await fetch(`${BASE}/locationAuthorizations/${authzId}`, { method: "DELETE", headers: oH });
  await fixture("A1-fixture owner create grant", await fetch(
    `${BASE}/locationAuthorizations?documentId=${authzId}`,
    { method: "POST", headers: oH, body: JSON.stringify({ fields: authzFields() }) }
  ));
  results.push({ name: "A1 owner create grant (family, live+history)", expected: "200 ALLOWED", got: "HTTP 200", pass: true });

  // ---------- A2: recipient cannot create ----------
  await expectDenied("A2 recipient create same grant", () =>
    fetch(`${BASE}/locationAuthorizations?documentId=${authzId}`, {
      method: "POST",
      headers: hH,
      body: JSON.stringify({ fields: authzFields() }),
    })
  );

  // ---------- A3: forged docId ----------
  await expectDenied("A3 owner create with forged docId", () =>
    fetch(`${BASE}/locationAuthorizations?documentId=forged-${devHex}`, {
      method: "POST",
      headers: oH,
      body: JSON.stringify({ fields: authzFields() }),
    })
  );

  // ---------- A4: recipient self-escalation ----------
  await expectDenied("A4 recipient escalation PATCH", () =>
    authzPatch(hH, { historyEnabled: B(true), updatedAt: TS(Date.now()) }, ["historyEnabled", "updatedAt"])
  );

  // ---------- A8/A9/A10/A11: grant reads ----------
  await expectDenied("A8 stranger GET grant", () => authzGet(xH));
  await expectAllowed("A9 recipient GET own grant", 200, () => authzGet(hH));
  await expectDenied("A10 recipient UNFILTERED list grants", () =>
    fetch(`${BASE}:runQuery`, {
      method: "POST",
      headers: hH,
      body: JSON.stringify({ structuredQuery: { from: [{ collectionId: "locationAuthorizations" }] } }),
    })
  );
  await expectAllowed("A11 recipient FILTERED list (recipientUserId == me)", 200, () =>
    fetch(`${BASE}:runQuery`, {
      method: "POST",
      headers: hH,
      body: JSON.stringify({
        structuredQuery: {
          from: [{ collectionId: "locationAuthorizations" }],
          where: {
            fieldFilter: { field: { fieldPath: "recipientUserId" }, op: "EQUAL", value: F(HELPER_UID) },
          },
        },
      }),
    })
  );

  // ---------- L2: the new authorization read path (grant alone) ----------
  await expectAllowed("L2 helper GET live (family grant, no rel)", 200, () => locGet(hH));

  // ---------- H1: owner appends a valid record ----------
  await expectAllowed("H1 owner append history record", 200, () => histCreate(oH, recId, histFields()));

  // ---------- H2..H5: hostile appends (all denied; no state change) ------
  await expectDenied("H2 owner append with extra forged key", () =>
    histCreate(oH, `${devHex}_${Date.now() + 2}`, histFields({ extra: F("forged") }))
  );
  await expectDenied("H3 owner append lat 91.0", () =>
    histCreate(oH, `${devHex}_${Date.now() + 3}`, histFields({ lat: D(91.0) }))
  );
  await expectDenied("H4 owner append speed 91 m/s", () =>
    histCreate(oH, `${devHex}_${Date.now() + 4}`, histFields({ speedMetersPerSecond: D(91.0) }))
  );
  await expectDenied("H5 owner append recordedAt 11 min past", () =>
    histCreate(oH, `${devHex}_${Date.now() + 5}`, histFields({ recordedAt: TS(Date.now() - 11 * 60_000) }))
  );

  // ---------- H6/H7/H14: authorized reads ----------
  await expectAllowed("H6 authorized family GET record", 200, () => histGet(hH, recId));
  await expectAllowed("H7 authorized family FILTERED list", 200, () =>
    histRunQuery(hH, {
      from: [{ collectionId: "locationHistory" }],
      where: {
        fieldFilter: { field: { fieldPath: "retentionExpiresAt" }, op: "GREATER_THAN", value: TS(Date.now()) },
      },
    })
  );
  await expectAllowed("H14 owner FILTERED list", 200, () =>
    histRunQuery(oH, {
      from: [{ collectionId: "locationHistory" }],
      where: {
        fieldFilter: { field: { fieldPath: "retentionExpiresAt" }, op: "GREATER_THAN", value: TS(Date.now()) },
      },
    })
  );

  // ---------- A5: owner revoke through the RULES ----------
  await expectAllowed("A5 owner revoke (masked PATCH)", 200, () =>
    authzPatch(oH, { revokedAt: TS(Date.now()), updatedAt: TS(Date.now()) }, ["revokedAt", "updatedAt"])
  );

  // ---------- A6: revoked doc is frozen ----------
  await expectDenied("A6 owner update-after-revoke", () =>
    authzPatch(oH, { scope: F("UNTIL_STOP"), updatedAt: TS(Date.now()) }, ["scope", "updatedAt"])
  );

  // ---------- H9 / L4: revoked grant denies both features ----------
  await expectDenied("H9 revoked grant history GET", () => histGet(hH, recId));
  await expectDenied("L4 revoked grant live GET", () => locGet(hH));

  // ---------- A12: delete the revoked grant (the re-grant path) ----------
  await expectAllowed("A12 owner DELETE revoked grant", 200, () =>
    fetch(`${BASE}/locationAuthorizations/${authzId}`, { method: "DELETE", headers: oH })
  );

  // ---------- re-grant + hostile-state fixtures for H8 / L3 ----------
  await fixture("re-grant after delete", await fetch(
    `${BASE}/locationAuthorizations?documentId=${authzId}`,
    { method: "POST", headers: oH, body: JSON.stringify({ fields: authzFields() }) }
  ));
  await fixture("SA patch historyEnabled=false", await saAuthzPatch(
    { historyEnabled: B(false), updatedAt: TS(Date.now()) }, ["historyEnabled", "updatedAt"]
  ));
  await expectDenied("H8 history-disabled grant history GET", () => histGet(hH, recId));

  await fixture("SA patch expiresAt=past", await saAuthzPatch(
    { expiresAt: TS(Date.now() - 60_000), updatedAt: TS(Date.now()) }, ["expiresAt", "updatedAt"]
  ));
  await expectDenied("L3 expired grant live GET", () => locGet(hH));

  // ---------- L5: sharing off denies every read path ----------
  await fixture("owner PATCH sharingEnabled=false", await ownerSetSharing(false));
  await expectDenied("L5 helper GET live (sharing off)", () => locGet(hH));
  await fixture("owner PATCH sharingEnabled=true", await ownerSetSharing(true));

  // ---------- L6: stranger ----------
  await expectDenied("L6 stranger GET live", () => locGet(xH));

  // ---------- H10/H11: expired-retention record (SA fixture) ----------
  await fixture("SA append expired-retention record", await fetch(
    `${BASE}/users/${OWNER_UID}/locationHistory?documentId=${recIdExp}`,
    {
      method: "POST",
      headers: saH,
      body: JSON.stringify({
        fields: histFields({ retentionExpiresAt: TS(Date.now() - 3_600_000) }),
      }),
    }
  ));
  await expectDenied("H10 expired-retention record GET (owner!)", () => histGet(oH, recIdExp));
  await expectDenied("H11 owner UNFILTERED list (expired poisons)", () =>
    fetch(`${BASE}/users/${OWNER_UID}/locationHistory?pageSize=10`, { headers: oH })
  );

  // ---------- H12: cross-user append ----------
  await expectDenied("H12 recipient append into owner history", () =>
    histCreate(hH, `${devHex}_${Date.now() + 6}`, histFields())
  );

  // ---------- H13: stranger filtered list ----------
  await expectDenied("H13 stranger FILTERED list", () =>
    histRunQuery(xH, {
      from: [{ collectionId: "locationHistory" }],
      where: {
        fieldFilter: { field: { fieldPath: "retentionExpiresAt" }, op: "GREATER_THAN", value: TS(Date.now()) },
      },
    })
  );

  // ---------- cleanup (SA — zero residue) ----------
  const sweepPaths = new Set([
    `trustedDeviceRelationships/${relId}`,
    `liveLocations/${locDocId}`,
    `locationAuthorizations/${authzId}`,
    `users/${OWNER_UID}/locationHistory/${recId}`,
    `users/${OWNER_UID}/locationHistory/${recIdExp}`,
    `locationAuthorizations/forged-${devHex}`,
  ]);
  // Best-effort sweep of anything a CRASHED earlier run may have left:
  try {
    const res = await fetch(`${BASE}:runQuery`, {
      method: "POST",
      headers: saH,
      body: JSON.stringify({
        structuredQuery: {
          from: [{ collectionId: "locationAuthorizations" }],
          where: {
            fieldFilter: { field: { fieldPath: "recipientUserId" }, op: "EQUAL", value: F(HELPER_UID) },
          },
        },
      }),
    });
    if (res.ok) {
      for (const row of await parseQueryRows(res)) {
        sweepPaths.add(`locationAuthorizations/${row.document.name.split("/").pop()}`);
      }
    }
  } catch { /* best effort */ }
  try {
    const res = await fetch(`${BASE}/users/${OWNER_UID}/locationHistory?pageSize=100`, { headers: saH });
    if (res.ok) {
      const j = await res.json();
      for (const doc of j.documents ?? []) {
        sweepPaths.add(`users/${OWNER_UID}/locationHistory/${doc.name.split("/").pop()}`);
      }
    }
  } catch { /* best effort */ }
  try {
    const res = await fetch(`${BASE}:runQuery`, {
      method: "POST",
      headers: saH,
      body: JSON.stringify({
        structuredQuery: {
          from: [{ collectionId: "liveLocations" }],
          where: {
            fieldFilter: { field: { fieldPath: "uid" }, op: "EQUAL", value: F(OWNER_UID) },
          },
        },
      }),
    });
    if (res.ok) {
      for (const row of await parseQueryRows(res)) {
        sweepPaths.add(`liveLocations/${row.document.name.split("/").pop()}`);
      }
    }
  } catch { /* best effort */ }

  let delOk = 0;
  const delFail = [];
  for (const p of sweepPaths) {
    try {
      const d = await fetch(`${BASE}/${p}`, { method: "DELETE", headers: saH });
      if (d.ok || d.status === 404) delOk += 1;
      else delFail.push(`${p}=${d.status}`);
    } catch (e) {
      delFail.push(`${p}=${e.message}`);
    }
  }
  console.log(
    `cleanup (SA): ${delOk}/${sweepPaths.size} deleted` +
      (delFail.length ? " — FAILED: " + delFail.join(", ") : "")
  );

  let authOk = 0;
  for (const tok of [ownerToken, helperToken, strangerToken]) {
    try {
      const d = await fetch(
        `https://identitytoolkit.googleapis.com/v1/accounts:delete?key=${API_KEY}`,
        {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ idToken: tok }),
        }
      );
      if (d.ok) authOk += 1;
    } catch { /* count below */ }
  }
  console.log(`synthetic Auth user cleanup (accounts:delete): ${authOk}/3`);

  // ================= report ===============================================
  console.log("\n────── Location v2 (Phase 8) rules probe results ──────");
  let allPass = true;
  for (const r of results) {
    const mark = r.pass ? "✔" : "✘";
    if (!r.pass) allPass = false;
    console.log(`${mark} ${r.name.padEnd(48)} expected ${r.expected.padEnd(12)} got ${r.got}`);
  }
  if (!allPass) {
    console.log("◐ NOTE: if EVERY case came back 403 (or 404 on queries), the Phase 8");
    console.log("  rules blocks are likely not published yet — the catch-all denies");
    console.log("  the new collections too (scripts/publish-firestore-rules.mjs).");
  }
  console.log(allPass
    ? `✔✔ ALL ${results.length} EXPECTATIONS MET — the live locationAuthorizations /`
    : "✘✘ RULES DRIFT — at least one Phase 8 expectation was NOT met!");
  if (allPass) console.log("   locationHistory rules held on every probed path.");
  process.exit(allPass && delFail.length === 0 && authOk === 3 ? 0 : 1);
}

main().catch((e) => {
  console.error("✘ probe crashed:", e);
  process.exit(1);
});
