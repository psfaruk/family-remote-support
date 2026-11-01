/**
 * Location master plan Phase 11 (v0.43.0) — RUNTIME PROBE of the LIVE
 * "placeAlerts" Firestore rules (ruleset 2fc2e1da…).
 *
 * Why: the service account BYPASSES the rules (Admin path), so it can
 * never prove what an ordinary signed-in app may or may not do. This
 * script mints two synthetic identities via self-signed Firebase custom
 * tokens (machinery copied from scripts/rules-liveLocations-probe.mjs),
 * exchanges them for real ID tokens through the Identity Toolkit, and
 * asserts exactly what the Phase 11 placeAlerts block must allow (200)
 * and refuse (403):
 *
 *   P0  positive control — the owner creates, reads and deletes its OWN
 *       place doc (full lifecycle in-case).
 *   P1  allow-update — the owner PATCHes radius 200→500 (masked PATCH;
 *       updatedAt stays fresh from the create seconds earlier).
 *   P2  allow-bounds — radius 50 + cooldown 5 + 64-char name on create,
 *       then PATCH to radius 1000 + cooldown 120 (every rules bound
 *       probed at its inclusive edge), then delete.
 *   P3  stranger-filtered-list — a stranger LISTing with its OWN
 *       ownerUserId equality filter → 200 with ZERO rows (the rules
 *       filter binds; a stranger never sees the owner's places).
 *   P4  owner-list — the owner lists with its own equality filter and
 *       gets EXACTLY its two fixture rows (the app's real query shape —
 *       same lesson as the Phase 8 H7 revision).
 *   P5  allow-bengali-name — a 56-CHARACTER Bengali name (154 UTF-8
 *       bytes) is ALLOWED: rules string.size() counts CHARACTERS, not
 *       bytes (live-verified 2026-11-01 before being locked here), so
 *       the design's "≤64 chars" bound holds verbatim for the
 *       Bengali-first user base.
 *
 *   N1  deny-stranger-get — the stranger GETs the owner's doc.
 *   N2  deny-stranger-unfiltered-list — the stranger LISTs the
 *       collection with NO filter (the shape the app never issues).
 *   N4  deny-forged-docId — the OWNER writes a docId under the
 *       STRANGER's uid pattern (ownerUserId field = owner — every other
 *       condition is satisfiable, so ONLY docId.matches() can and must
 *       refuse it).
 *   N5  deny-forged-owner-field — the owner writes its own-pattern
 *       docId with ownerUserId = the stranger.
 *   N6  deny-extra-field — hasOnly violation ("notes").
 *   N7  deny-radius-floor — radiusMeters 49.
 *   N8  deny-radius-ceiling — radiusMeters 1001.
 *   N9  deny-cooldown-floor — cooldownMinutes 4.
 *   N10 deny-cooldown-ceiling — cooldownMinutes 121.
 *   N11 deny-name-too-long — 65 chars.
 *   N12 deny-name-empty — "".
 *   N13 deny-lat-range — lat 91.0.
 *   N14 deny-radius-type — a STRING "200" instead of int.
 *   N15 deny-stale-created — createdAt 20 min in the past (outside the
 *       ±10-min window).
 *   N16 deny-update-forges-createdAt — masked PATCH touching createdAt
 *       (the diff fence: createdAt is not an updatable key).
 *   N17 deny-update-forges-owner — masked PATCH touching ownerUserId.
 *   N18 deny-stale-update — masked PATCH setting updatedAt 20 min in
 *       the past (outside the ±10-min window).
 *   N19 deny-stranger-delete — the stranger DELETEs the owner's doc.
 *   N20 deny-owner-unfiltered-list — the OWNER lists the collection
 *       with NO filter (the app always sends the ownerUserId equality;
 *       the unfiltered shape cannot prove per-doc safety).
 *
 * Fixtures: every placeAlerts write/read goes through real ID tokens so
 * the rules are exercised exactly like the app would; the SA (Admin)
 * path is used ONLY for the zero-residue cleanup sweep. Timestamps are
 * sent as Firestore timestampValues so the ±10-min windows are genuinely
 * satisfied (positive cases) or deliberately violated (N15/N18).
 *
 * Nothing of any real user is touched. Every synthetic doc (this run's
 * AND any crashed earlier run's leftovers) and both synthetic Auth users
 * are deleted in the cleanup — zero residue. Exit code 0 = all
 * expectations met; 1 = any deviation (rules drift — or the Phase 11
 * block is not published yet: the catch-all then denies placeAlerts and
 * even P0 fails with 403).
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

const OWNER_UID = "rules-pla"; // the place owner (the only writer/reader)
const STRANGER_UID = "rules-pla-x"; // every other identity (paired or not)

function b64u(buf) {
  return Buffer.from(buf).toString("base64url");
}

/** OAuth2 access token for the SA (Admin-path cleanup only). */
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
// same as every other rules-* probe in this repo.
const BASE = `https://firestore.googleapis.com/v1/projects/${PROJECT}/databases/default/documents`;
const F = (s) => ({ stringValue: s });
const TS = (ms) => ({ timestampValue: new Date(ms).toISOString() });
const I = (n) => ({ integerValue: String(n) });
const D = (n) => ({ doubleValue: n });
const B = (b) => ({ booleanValue: b });

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

/** Expect a rules ALLOW with an exact HTTP status. */
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
  // ================= SA (Admin path — cleanup ONLY) ========================
  const saTok = await saAccessToken();
  const saH = { Authorization: `Bearer ${saTok}`, "Content-Type": "application/json" };

  // ================= probe identities =======================================
  const ownerToken = await probeIdToken(mintCustomToken(OWNER_UID));
  const strangerToken = await probeIdToken(mintCustomToken(STRANGER_UID));
  const oH = { Authorization: `Bearer ${ownerToken}`, "Content-Type": "application/json" };
  const xH = { Authorization: `Bearer ${strangerToken}`, "Content-Type": "application/json" };
  console.log(`identities minted: ${OWNER_UID} (owner) + ${STRANGER_UID} (stranger)`);

  const hex16 = () => crypto.randomBytes(8).toString("hex"); // 16 hex chars
  const ownDocId = () => `${OWNER_UID}_${hex16()}`;

  /** Well-formed placeAlerts field map (attack cases override one field). */
  function placeFields(overrides = {}) {
    return {
      ownerUserId: F(OWNER_UID),
      name: F("Probe Home"),
      lat: D(23.8),
      lng: D(90.4),
      radiusMeters: I(200),
      arrivalEnabled: B(true),
      departureEnabled: B(false),
      cooldownMinutes: I(10),
      createdAt: TS(Date.now()), // fresh ⇒ inside the ±10-min rules window
      updatedAt: TS(Date.now()),
      appVersionCode: I(63),
      ...overrides,
    };
  }

  /** Create (REST POST) a place doc at [docId] with [fields]. */
  const createDoc = (headers, docId, fields) =>
    fetch(`${BASE}/placeAlerts?documentId=${docId}`, {
      method: "POST",
      headers,
      body: JSON.stringify({ fields }),
    });

  /** Masked PATCH of [docId] touching exactly the masked field paths. */
  const patchDoc = (headers, docId, fields, mask) => {
    const q = mask.map((m) => `updateMask.fieldPaths=${m}`).join("&");
    return fetch(`${BASE}/placeAlerts/${docId}?${q}`, {
      method: "PATCH",
      headers,
      body: JSON.stringify({ fields }),
    });
  };

  const getDoc = (headers, docId) => fetch(`${BASE}/placeAlerts/${docId}`, { headers });
  const delDoc = (headers, docId) =>
    fetch(`${BASE}/placeAlerts/${docId}`, { method: "DELETE", headers });

  // ---------- P0: positive control (owner's own doc lifecycle) ----------
  const p0Id = ownDocId();
  await run("P0 owner create+read+delete own doc", async () => {
    const c = await createDoc(oH, p0Id, placeFields());
    let gS = 0;
    let dS = 0;
    if (c.status === 200) {
      const g = await getDoc(oH, p0Id);
      gS = g.status;
      if (g.status === 200) {
        const d = await delDoc(oH, p0Id);
        dS = d.status;
      }
    }
    const pass = c.status === 200 && gS === 200 && dS === 200;
    return { expected: "200/200/200", got: `${c.status}/${gS || "—"}/${dS || "—"}`, pass };
  });

  // ---------- P1: owner masked-PATCH update (radius 200→500) ----------
  const p1Id = ownDocId();
  await run("P1 owner update radius 200→500", async () => {
    const c = await createDoc(oH, p1Id, placeFields());
    if (c.status !== 200) return { expected: "200/200", got: `create ${c.status}/—`, pass: false };
    const u = await patchDoc(oH, p1Id, { radiusMeters: I(500) }, ["radiusMeters"]);
    await delDoc(oH, p1Id).catch(() => {});
    const pass = u.status === 200;
    return { expected: "200/200", got: `200/${u.status}`, pass };
  });

  // ---------- P2: every rules bound probed at its inclusive edge ----------
  await run("P2 bounds 50/5/64-chars then 1000/120, delete", async () => {
    const id = ownDocId();
    const c = await createDoc(
      oH,
      id,
      placeFields({ radiusMeters: I(50), cooldownMinutes: I(5), name: F("P".repeat(64)) })
    );
    if (c.status !== 200) return { expected: "200/200/200", got: `create ${c.status}/—/—`, pass: false };
    const u = await patchDoc(
      oH,
      id,
      { radiusMeters: I(1000), cooldownMinutes: I(120) },
      ["radiusMeters", "cooldownMinutes"]
    );
    const d = await delDoc(oH, id);
    const pass = u.status === 200 && d.status === 200;
    return { expected: "200/200/200", got: `200/${u.status}/${d.status}`, pass };
  });

  // ---------- P3: stranger's own-filtered list → 200, ZERO rows ----------
  const p4a = ownDocId();
  const p4b = ownDocId();
  await run("P3 stranger own-filter list → 200 + 0 rows", async () => {
    const r = await fetch(`${BASE}:runQuery`, {
      method: "POST",
      headers: xH,
      body: JSON.stringify({
        structuredQuery: {
          from: [{ collectionId: "placeAlerts" }],
          where: {
            fieldFilter: { field: { fieldPath: "ownerUserId" }, op: "EQUAL", value: F(STRANGER_UID) },
          },
        },
      }),
    });
    const rows = r.ok ? await parseQueryRows(r) : [];
    const pass = r.status === 200 && rows.length === 0;
    return { expected: "200 + 0 rows", got: `HTTP ${r.status} + ${rows.length} rows`, pass };
  });

  // ---------- P4: owner's real query shape → EXACTLY its own rows ----------
  await run("P4 owner own-filter list → exactly 2 rows", async () => {
    const c1 = await createDoc(oH, p4a, placeFields());
    const c2 = await createDoc(oH, p4b, placeFields({ name: F("Second place") }));
    if (c1.status !== 200 || c2.status !== 200) {
      return { expected: "200 + 2 rows", got: `fixtures ${c1.status}/${c2.status}`, pass: false };
    }
    const r = await fetch(`${BASE}:runQuery`, {
      method: "POST",
      headers: oH,
      body: JSON.stringify({
        structuredQuery: {
          from: [{ collectionId: "placeAlerts" }],
          where: {
            fieldFilter: { field: { fieldPath: "ownerUserId" }, op: "EQUAL", value: F(OWNER_UID) },
          },
        },
      }),
    });
    const rows = r.ok ? await parseQueryRows(r) : [];
    const ids = rows.map((row) => row.document.name.split("/").pop());
    const pass =
      r.status === 200 &&
      rows.length === 2 &&
      ids.includes(p4a) &&
      ids.includes(p4b);
    await delDoc(oH, p4a).catch(() => {});
    await delDoc(oH, p4b).catch(() => {});
    return {
      expected: "200 + exactly 2 rows",
      got: `HTTP ${r.status} + ${rows.length} rows (${ids.length})`,
      pass,
    };
  });

  // ---------- P5: size() counts CHARACTERS — Bengali names ≤64 chars ----
  await run("P5 56-char Bengali name (154 bytes) allowed", async () => {
    const id = ownDocId();
    const bnName = "বাড়ি বাজার ক্লিনিক স্কুল হাসপাতাল রেস্তোরাঁ পার্ক মসজিদ";
    const c = await createDoc(oH, id, placeFields({ name: F(bnName) }));
    if (c.status === 200) await delDoc(oH, id).catch(() => {});
    const pass = c.status === 200;
    return { expected: "200 (chars, not bytes)", got: `HTTP ${c.status}`, pass };
  });

  // ---------- N-series: every denial path ----------
  const liveId = ownDocId(); // one persistent fixture for read/update/delete attacks
  const liveCreate = await createDoc(oH, liveId, placeFields());
  if (liveCreate.status !== 200) {
    let detail = "";
    try { detail = (await liveCreate.text()).slice(0, 200); } catch { /* ignore */ }
    console.error(
      `✘ fixture failed: N-series live doc → HTTP ${liveCreate.status} ${detail}`
    );
    process.exit(1);
  }

  await expectDenied("N1  stranger GET owner doc", () => getDoc(xH, liveId));

  await expectDenied("N2  stranger unfiltered LIST", () =>
    fetch(`${BASE}:runQuery`, {
      method: "POST",
      headers: xH,
      body: JSON.stringify({ structuredQuery: { from: [{ collectionId: "placeAlerts" }] } }),
    })
  );

  await expectDenied("N4  forged docId (owner under stranger pattern)", () =>
    createDoc(oH, `${STRANGER_UID}_${hex16()}`, placeFields())
  );

  await expectDenied("N5  forged ownerUserId field", () =>
    createDoc(oH, ownDocId(), placeFields({ ownerUserId: F(STRANGER_UID) }))
  );

  await expectDenied("N6  extra field (hasOnly)", () =>
    createDoc(oH, ownDocId(), placeFields({ notes: F("smuggled") }))
  );

  await expectDenied("N7  radius 49 (below floor)", () =>
    createDoc(oH, ownDocId(), placeFields({ radiusMeters: I(49) }))
  );

  await expectDenied("N8  radius 1001 (above ceiling)", () =>
    createDoc(oH, ownDocId(), placeFields({ radiusMeters: I(1001) }))
  );

  await expectDenied("N9  cooldown 4 (below floor)", () =>
    createDoc(oH, ownDocId(), placeFields({ cooldownMinutes: I(4) }))
  );

  await expectDenied("N10 cooldown 121 (above ceiling)", () =>
    createDoc(oH, ownDocId(), placeFields({ cooldownMinutes: I(121) }))
  );

  await expectDenied("N11 name 65 chars", () =>
    createDoc(oH, ownDocId(), placeFields({ name: F("P".repeat(65)) }))
  );

  await expectDenied("N12 empty name", () =>
    createDoc(oH, ownDocId(), placeFields({ name: F("") }))
  );

  await expectDenied("N13 lat 91.0", () =>
    createDoc(oH, ownDocId(), placeFields({ lat: D(91.0) }))
  );

  await expectDenied("N14 radius as string type", () =>
    createDoc(oH, ownDocId(), placeFields({ radiusMeters: F("200") }))
  );

  await expectDenied("N15 stale createdAt (−20 min)", () =>
    createDoc(oH, ownDocId(), placeFields({ createdAt: TS(Date.now() - 20 * 60_000) }))
  );

  await expectDenied("N16 update forges createdAt", () =>
    patchDoc(
      oH,
      liveId,
      { createdAt: TS(Date.now() + 60_000) },
      ["createdAt"]
    )
  );

  await expectDenied("N17 update forges ownerUserId", () =>
    patchDoc(oH, liveId, { ownerUserId: F(STRANGER_UID) }, ["ownerUserId"])
  );

  await expectDenied("N18 update stale updatedAt (−20 min)", () =>
    patchDoc(oH, liveId, { updatedAt: TS(Date.now() - 20 * 60_000) }, ["updatedAt"])
  );

  await expectDenied("N19 stranger DELETE owner doc", () => delDoc(xH, liveId));

  await expectDenied("N20 owner unfiltered LIST", () =>
    fetch(`${BASE}:runQuery`, {
      method: "POST",
      headers: oH,
      body: JSON.stringify({ structuredQuery: { from: [{ collectionId: "placeAlerts" }] } }),
    })
  );

  // ---------- cleanup (SA — zero residue) ----------
  // This run's docs plus a best-effort sweep of anything a CRASHED earlier
  // run may have left behind (all docs owned by the synthetic owner uid).
  const sweepPaths = new Set([`placeAlerts/${liveId}`, `placeAlerts/${p4a}`, `placeAlerts/${p4b}`]);
  try {
    const res = await fetch(`${BASE}:runQuery`, {
      method: "POST",
      headers: saH,
      body: JSON.stringify({
        structuredQuery: {
          from: [{ collectionId: "placeAlerts" }],
          where: {
            fieldFilter: { field: { fieldPath: "ownerUserId" }, op: "EQUAL", value: F(OWNER_UID) },
          },
        },
      }),
    });
    if (res.ok) {
      for (const row of await parseQueryRows(res)) {
        sweepPaths.add(`placeAlerts/${row.document.name.split("/").pop()}`);
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
  for (const tok of [ownerToken, strangerToken]) {
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
  console.log(`synthetic Auth user cleanup (accounts:delete): ${authOk}/2`);

  // ================= report ===============================================
  console.log("\n──── Phase 11 (v0.43.0) — placeAlerts probe results ────");
  let allPass = true;
  for (const r of results) {
    const mark = r.pass ? "✔" : "✘";
    if (!r.pass) allPass = false;
    console.log(`${mark} ${r.name.padEnd(46)} expected ${r.expected.padEnd(12)} got ${r.got}`);
  }
  if (!allPass) {
    console.log("◐ NOTE: if EVERY case came back 403, the Phase 11 rules block is likely");
    console.log("  not published yet (the catch-all denies placeAlerts) — publish via");
    console.log("  scripts/publish-firestore-rules.mjs and re-run.");
  }
  const met = results.filter((r) => r.pass).length;
  console.log(`\n${met}/${results.length} EXPECTATIONS ${allPass ? "MET — ALL GREEN" : "DEVIATED"}`);
  process.exit(allPass ? 0 : 1);
}

/** Shared runner (kept identical to the other probes' semantics). */
async function run(name, fn) {
  try {
    const outcome = await fn();
    results.push({ name, ...outcome });
  } catch (e) {
    results.push({ name, expected: "?", got: `THREW ${e.message}`, pass: false });
  }
}

main().catch((e) => {
  console.error("✘ probe crashed:", e);
  process.exit(1);
});
