/**
 * M5 / Phase 22 — RUNTIME PROBE of the LIVE "liveLocations" Firestore rules.
 *
 * Why: the service account BYPASSES the rules (Admin path), so it can never
 * prove what an ordinary signed-in app may or may not do. This script mints
 * three synthetic identities via self-signed Firebase custom tokens
 * (machinery copied from scripts/rules-negative-probe.mjs), exchanges them
 * for real ID tokens through the Identity Toolkit, and asserts exactly what
 * the Phase 22 (M5) liveLocations block must allow (200) and refuse (403):
 *
 *   P0  positive control — the owner creates, reads and deletes its OWN doc.
 *   P1  allow-ACTIVE-counterpart — the paired helper reads the owner's doc
 *       while the family relationship is ACTIVE and sharingEnabled == true.
 *   N1  deny-before-pairing — a third, never-paired identity GETs the doc.
 *   N2  deny-when-sharing-off — sharingEnabled == false ⇒ helper GET denied.
 *   N3  deny-revoked — relationship REVOKED ⇒ helper GET denied instantly.
 *   N4  deny-guest-after-window — guest link with guestExpiresAt in the past
 *       ⇒ helper GET denied (the "ex-guest" threat, architecture doc §9).
 *   N5  deny-forged-fields — owner write carrying an EXTRA key denied.
 *   N6  deny-out-of-range — owner write with lat 91.0 denied.
 *   N7  deny-stale-forgery — owner write with updatedAt 10 min in the past
 *       denied (outside the ±5 min freshness window).
 *   N8  deny-write-others — the helper writing the OWNER's doc id (with its
 *       own uid inside — every other condition is satisfiable, so ONLY the
 *       docId.matches() check can and must refuse it) denied.
 *   N9  deny-stranger-list — an unpaired identity LISTing the collection.
 *   P2  guest-live-window allow — guest link with a FUTURE guestExpiresAt
 *       and sharingEnabled == true ⇒ helper GET 200.
 *
 * Fixtures: the synthetic relationship doc (pairKey helperUid_recipientUid)
 * is written/mutated ONLY through the SA ADMIN path (rules bypass — same
 * convention as the other rules-* probes); every liveLocations read/write
 * goes through real ID tokens so the rules are exercised exactly like the
 * app would. updatedAt is always sent as a Firestore timestampValue so the
 * ±5 min freshness precondition is genuinely satisfied (P0/P1/P2) or
 * deliberately violated (N7).
 *
 * Nothing of any real user is touched. Every synthetic doc (liveLocations,
 * trustedDeviceRelationships — this run's AND any crashed earlier run's
 * leftovers) and all three synthetic Auth users are deleted in the cleanup
 * — zero residue. Exit code 0 = all expectations met; 1 = any deviation
 * (rules drift — or the Phase 22 block is not published yet: the catch-all
 * then denies liveLocations and even P0 fails with 403).
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

const OWNER_UID = "rules-loc"; // the sharing device owner (relationship recipient)
const HELPER_UID = "rules-loc-h"; // the paired counterpart (relationship helper)
const STRANGER_UID = "rules-loc-x"; // a third, never-paired identity

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
// same as scripts/rules-negative-probe.mjs (discovered in Phase 4).
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

  // ================= probe identities =======================================
  const ownerToken = await probeIdToken(mintCustomToken(OWNER_UID));
  const helperToken = await probeIdToken(mintCustomToken(HELPER_UID));
  const strangerToken = await probeIdToken(mintCustomToken(STRANGER_UID));
  const oH = { Authorization: `Bearer ${ownerToken}`, "Content-Type": "application/json" };
  const hH = { Authorization: `Bearer ${helperToken}`, "Content-Type": "application/json" };
  const xH = { Authorization: `Bearer ${strangerToken}`, "Content-Type": "application/json" };
  console.log(`identities minted: ${OWNER_UID} (owner) + ${HELPER_UID} (helper) + ${STRANGER_UID} (stranger)`);

  const devHex = crypto.randomBytes(12).toString("hex"); // 24 hex chars
  const locDocId = `${OWNER_UID}_${devHex}`; // {uid}_[0-9a-f]{24} (presence pattern)
  const relId = `${HELPER_UID}_${OWNER_UID}`; // pairKey helperUid_recipientUid
  const FAKE_EMAILS = {
    [OWNER_UID]: "rules-loc@invalid.local",
    [HELPER_UID]: "rules-loc-helper@invalid.local",
  };

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
      appVersionCode: I(52),
      ...overrides,
    };
  }

  /** Create (REST POST) the location doc at the owner's doc id. */
  async function writeLocDoc(headers, fields) {
    return fetch(`${BASE}/liveLocations?documentId=${locDocId}`, {
      method: "POST",
      headers,
      body: JSON.stringify({ fields }),
    });
  }

  /** GET the owner's location doc (headers decide whose eyes we look through). */
  const locGet = (headers) => fetch(`${BASE}/liveLocations/${locDocId}`, { headers });

  /** Owner toggles sharingEnabled with a rules-legal masked PATCH. */
  async function ownerSetSharing(enabled) {
    return fetch(`${BASE}/liveLocations/${locDocId}?updateMask.fieldPaths=sharingEnabled`, {
      method: "PATCH",
      headers: oH,
      body: JSON.stringify({ fields: { sharingEnabled: B(enabled) } }),
    });
  }

  /** SA (rules-bypassing) masked PATCH of the synthetic relationship doc. */
  async function saRelPatch(fields, mask) {
    const q = mask.map((m) => `updateMask.fieldPaths=${m}`).join("&");
    return fetch(`${BASE}/trustedDeviceRelationships/${relId}?${q}`, {
      method: "PATCH",
      headers: saH,
      body: JSON.stringify({ fields }),
    });
  }

  /** Fixtures must succeed — anything else aborts the probe (exit 1). */
  async function fixture(name, r) {
    if (r.status !== 200) {
      let detail = "";
      try { detail = (await r.text()).slice(0, 200); } catch { /* ignore */ }
      console.error(`✘ fixture failed: ${name} → HTTP ${r.status} ${detail}`);
      process.exit(1);
    }
  }

  // ---------- P0: positive control (owner's own doc lifecycle) ----------
  await run("P0 owner create+read+delete own doc", async () => {
    const c = await writeLocDoc(oH, locFields());
    let gS = 0;
    let dS = 0;
    if (c.status === 200) {
      const g = await locGet(oH);
      gS = g.status;
      if (g.status === 200) {
        const d = await fetch(`${BASE}/liveLocations/${locDocId}`, { method: "DELETE", headers: oH });
        dS = d.status;
      }
    }
    const pass = c.status === 200 && gS === 200 && dS === 200;
    return { expected: "200/200/200", got: `${c.status}/${gS || "—"}/${dS || "—"}`, pass };
  });

  // ---------- fixture: the doc + the ACTIVE family relationship ----------
  await fixture("owner recreate liveLocations doc", await writeLocDoc(oH, locFields()));
  // Idempotence guard: a crashed earlier run may have left the FIXED-id
  // relationship doc behind (a 409 on create would otherwise abort here).
  await fetch(`${BASE}/trustedDeviceRelationships/${relId}`, { method: "DELETE", headers: saH });
  // Field shape mirrors the redeem-commit relationship doc of
  // rules-negative-probe.mjs (pairKey == helperUid_recipientUid); the SA
  // path bypasses rules, but the pairing rules' invariants are kept anyway
  // so the fixture is indistinguishable from a real paired family.
  await fixture(
    "SA create ACTIVE family relationship",
    await fetch(`${BASE}/trustedDeviceRelationships?documentId=${relId}`, {
      method: "POST",
      headers: saH,
      body: JSON.stringify({
        fields: {
          helperUid: F(HELPER_UID),
          helperEmail: F(FAKE_EMAILS[HELPER_UID]),
          recipientUid: F(OWNER_UID),
          recipientEmail: F(FAKE_EMAILS[OWNER_UID]),
          recipientDeviceId: F(devHex),
          recipientDeviceModel: F("rules-loc-synthetic"),
          pairKey: F(relId),
          status: F("ACTIVE"),
          kind: F("family"),
          createdByInvitation: F("PRBLOC0"),
          createdAt: TS(Date.now()),
          updatedAt: TS(Date.now()),
        },
      }),
    })
  );
  console.log("fixture ready: liveLocations doc + ACTIVE family relationship");

  // ---------- P1: paired counterpart of an ACTIVE relationship ----------
  await expectAllowed("P1 helper GET (ACTIVE counterpart)", 200, () => locGet(hH));

  // ---------- N1: never-paired stranger ----------
  await expectDenied("N1 stranger GET (never paired)", () => locGet(xH));

  // ---------- N2: sharing toggled off by the owner ----------
  await fixture("owner PATCH sharingEnabled=false", await ownerSetSharing(false));
  await expectDenied("N2 helper GET (sharingEnabled false)", () => locGet(hH));

  // ---------- N3: relationship revoked ----------
  await fixture("owner PATCH sharingEnabled=true", await ownerSetSharing(true));
  await fixture(
    "SA rel status -> REVOKED",
    await saRelPatch({ status: F("REVOKED"), updatedAt: TS(Date.now()) }, ["status", "updatedAt"])
  );
  await expectDenied("N3 helper GET (relationship REVOKED)", () => locGet(hH));

  // ---------- N4: guest link whose window has passed ----------
  await fixture(
    "SA rel -> ACTIVE guest, window in the past",
    await saRelPatch(
      {
        status: F("ACTIVE"),
        kind: F("guest"),
        guestExpiresAt: TS(Date.now() - 60_000),
        updatedAt: TS(Date.now()),
      },
      ["status", "kind", "guestExpiresAt", "updatedAt"]
    )
  );
  await expectDenied("N4 helper GET (guest window past)", () => locGet(hH));

  // ---------- N5..N8: hostile WRITES (create path — doc removed first) ------
  await fixture(
    "owner DELETE doc (attack reset)",
    await fetch(`${BASE}/liveLocations/${locDocId}`, { method: "DELETE", headers: oH })
  );

  await expectDenied("N5 owner create with extra key", () =>
    writeLocDoc(oH, locFields({ extra: F("forged") }))
  );

  await expectDenied("N6 owner create lat out of range", () =>
    writeLocDoc(oH, locFields({ lat: D(91.0) }))
  );

  await expectDenied("N7 owner create with stale updatedAt", () =>
    writeLocDoc(oH, locFields({ updatedAt: TS(Date.now() - 10 * 60_000) }))
  );

  // The helper writes the OWNER's doc id with ITS OWN uid inside: uid ==
  // auth.uid holds, so ONLY docId.matches(request.auth.uid + '_…') can (and
  // must) refuse the write.
  await expectDenied("N8 helper create on owner doc id", () =>
    writeLocDoc(hH, locFields({ uid: F(HELPER_UID) }))
  );

  // ---------- N9: unpaired identity enumerating the collection ----------
  await fixture("owner recreate liveLocations doc", await writeLocDoc(oH, locFields()));
  await expectDenied("N9 stranger LIST collection", () =>
    fetch(`${BASE}/liveLocations?pageSize=5`, { headers: xH })
  );

  // ---------- P2: guest link inside its live window ----------
  await fixture(
    "SA rel -> ACTIVE guest, window in the future",
    await saRelPatch(
      {
        status: F("ACTIVE"),
        kind: F("guest"),
        guestExpiresAt: TS(Date.now() + 600_000),
        updatedAt: TS(Date.now()),
      },
      ["status", "kind", "guestExpiresAt", "updatedAt"]
    )
  );
  await expectAllowed("P2 helper GET (guest window live)", 200, () => locGet(hH));

  // ---------- cleanup (SA — zero residue) ----------
  // This run's docs plus a best-effort sweep of anything a CRASHED earlier
  // run may have left behind (fixed relationship id / per-run location ids).
  const sweepPaths = new Set([`trustedDeviceRelationships/${relId}`]);
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
  try {
    const res = await fetch(`${BASE}:runQuery`, {
      method: "POST",
      headers: saH,
      body: JSON.stringify({
        structuredQuery: {
          from: [{ collectionId: "trustedDeviceRelationships" }],
          where: {
            fieldFilter: { field: { fieldPath: "helperUid" }, op: "EQUAL", value: F(HELPER_UID) },
          },
        },
      }),
    });
    if (res.ok) {
      for (const row of await parseQueryRows(res)) {
        sweepPaths.add(`trustedDeviceRelationships/${row.document.name.split("/").pop()}`);
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
  console.log("\n────────── M5 (Phase 22) liveLocations probe results ──────────");
  let allPass = true;
  for (const r of results) {
    const mark = r.pass ? "✔" : "✘";
    if (!r.pass) allPass = false;
    console.log(`${mark} ${r.name.padEnd(46)} expected ${r.expected.padEnd(12)} got ${r.got}`);
  }
  if (!allPass) {
    console.log("◐ NOTE: if EVERY case came back 403, the Phase 22 rules block is likely");
    console.log("  not published yet — the catch-all denies liveLocations too");
    console.log("  (scripts/publish-firestore-rules.mjs, run by the coordinator).");
  }
  console.log(allPass
    ? "✔✔ ALL EXPECTATIONS MET — the live liveLocations rules held on every probed path."
    : "✘✘ RULES DRIFT — at least one liveLocations expectation was NOT met!");
  process.exit(allPass && delFail.length === 0 && authOk === 3 ? 0 : 1);
}

main().catch((e) => {
  console.error("✘ probe crashed:", e);
  process.exit(1);
});
