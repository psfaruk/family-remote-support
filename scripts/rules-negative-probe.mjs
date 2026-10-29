/**
 * Phase 9/10 — RUNTIME NEGATIVE PROBE of the LIVE Firestore rules.
 *
 * Why: the service account BYPASSES the rules (Admin path), so it can never
 * prove what an ordinary signed-in app may or may not do. This script mints
 * synthetic identities ("rules-probe" + Phase 10's helper "rules-probe-h")
 * via self-signed Firebase custom tokens, exchanges them for real ID tokens
 * through the Identity Toolkit, and then fires a battery of requests that
 * the rules must REFUSE.
 *
 *   P0  positive control — the probe writes+deletes its OWN devicePresence
 *       doc (allowed by design); proves the auth path works at all.
 *   N1..N10 — everything a hostile/curious client might try, all expected
 *       to come back HTTP 403 PERMISSION_DENIED.
 *   Phase 10 (v0.11.0) — guest-mode proofs (P10 + G1/G2/N11..N16):
 *       the helper identity performs a REAL REST-commit redeem simulation
 *       (invitation -> PAIRED + guest relationship in one atomic commit),
 *       a live guest ask is ALLOWED, kind mismatches / over-long windows /
 *       family-into-guest conversions / post-window asks are ALL refused,
 *       and the lazy EXPIRED + guest DELETE paths are proven allowed.
 *
 * Nothing readable is touched: denied reads return errors, the only writes
 * are the probe's own throwaway docs (presence doc P0, synthetic guest
 * invitations/relationship/session — all deleted or cancelled afterwards)
 * and the synthetic Auth users, deleted in the cleanup. The single CANCELLED
 * session doc (G2) is inert: the kill-push sweep only looks at REQUESTED.
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
const HELPER_UID = "rules-probe-h"; // Phase 10: the redeeming helper identity

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
function mintCustomToken(uid = PROBE_UID) {
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

  // ═══════════ Phase 10 (v0.11.0): Guest mode proofs ═══════════
  // A SECOND synthetic identity plays the helper so the guest redeem can be
  // simulated EXACTLY like the app does it: invitation (inviter = probe A,
  // kind=guest) + a single atomic REST commit [invitation -> PAIRED,
  // relationship create]. The +65 s guest window lets the post-window
  // refusal (N15) run after a short sleep while the probe stays ~80 s total.
  const helperToken = await probeIdToken(mintCustomToken(HELPER_UID));
  const hH = { Authorization: `Bearer ${helperToken}`, "Content-Type": "application/json" };
  console.log(`probe identity minted: ${HELPER_UID} (helper)`);

  const t0 = Date.now();
  const devHex24 = crypto.randomBytes(12).toString("hex");
  // v0.21.0 — the synthetic invitations carry the RAW one-time token field
  // (mirrors the v0.21.0 app + rules: redemption must prove token knowledge
  // SERVER-SIDE via claimToken == the stored token).
  const PROBE_TOKEN = "0123456789abcdef0123"; // exactly 20 chars, like the app's
  const FAKE_EMAILS = {
    [PROBE_UID]: "rules-probe@invalid.local",
    [HELPER_UID]: "rules-probe-helper@invalid.local",
  };
  const relIdG = `${HELPER_UID}_${PROBE_UID}`; // pairKey helper_recipient
  const inviteIds = [];
  // Writes inside documents:commit must use RELATIVE doc names (the error
  // "lacks \"projects\" at index 0" comes from full URLs) — GET/DELETE use
  // full resource URLs, commit writes use projects/{p}/databases/{db}/…
  const RELDOC = `projects/${PROJECT}/databases/default/documents`;

  /** Create a synthetic invitation owned by probe A. */
  async function makeInvitation(invId, kind) {
    inviteIds.push(invId);
    const r = await fetch(`${BASE}/pairingInvitations?documentId=${invId}`, {
      method: "POST",
      headers: pH,
      body: JSON.stringify({
        fields: {
          inviterUid: F(PROBE_UID),
          inviterEmail: F(FAKE_EMAILS[PROBE_UID]),
          recipientDeviceId: F(devHex24),
          recipientDeviceModel: F("rules-probe-synthetic"),
          status: F("PENDING"),
          kind: F(kind),
          tokenHash: F("c".repeat(64)),
          token: F(PROBE_TOKEN),
          createdAt: TS(t0),
          expiresAt: TS(t0 + 5 * 60_000),
        },
      }),
    });
    return r;
  }

  /** One atomic commit: invitation -> PAIRED (+ guest rel when guestKind).
   * v0.21.0: [claimToken] proves knowledge of the raw token server-side;
   * [claimTokenValue] lets the N17/N18 attack reproductions omit/forge it. */
  function redeemCommit(invId, relId, kind, guestEndsMs, claimTokenValue = PROBE_TOKEN) {
    const invFields = {
      status: F("PAIRED"),
      claimedBy: F(HELPER_UID),
      claimedEmail: F(FAKE_EMAILS[HELPER_UID]),
      claimedAt: TS(Date.now()),
      updatedAt: TS(Date.now()),
    };
    const invMask = ["status", "claimedBy", "claimedEmail", "claimedAt", "updatedAt"];
    if (claimTokenValue !== null) {
      invFields.claimToken = F(claimTokenValue);
      invMask.push("claimToken");
    }
    const invUpdate = {
      update: {
        name: `${RELDOC}/pairingInvitations/${invId}`,
        fields: invFields,
      },
      updateMask: { fieldPaths: invMask },
    };
    const relFields = {
      helperUid: F(HELPER_UID),
      helperEmail: F(FAKE_EMAILS[HELPER_UID]),
      recipientUid: F(PROBE_UID),
      recipientEmail: F(FAKE_EMAILS[PROBE_UID]),
      recipientDeviceId: F(devHex24),
      recipientDeviceModel: F("rules-probe-synthetic"),
      pairKey: F(relId),
      status: F("ACTIVE"),
      kind: F(kind),
      createdByInvitation: F(invId),
      createdAt: TS(Date.now()),
      updatedAt: TS(Date.now()),
    };
    if (kind === "guest") relFields.guestExpiresAt = TS(guestEndsMs);
    const writes = [invUpdate];
    if (relId) {
      writes.push({
        update: { name: `${RELDOC}/trustedDeviceRelationships/${relId}`, fields: relFields },
        // family re-activation attempts only mask status+updatedAt
        ...(guestEndsMs === null
          ? { updateMask: { fieldPaths: ["status", "updatedAt"] } }
          : {}),
      });
    }
    return fetch(`${BASE}:commit`, {
      method: "POST",
      headers: hH,
      body: JSON.stringify({ writes }),
    });
  }

  // ---------- P10: guest-kind invitation (positive control) ----------
  await run("P10 create guest invitation (probe-owned)", async () => {
    const r = await makeInvitation("PRBGST0", "guest");
    if (r.status === 200) {
      await fetch(`${BASE}/pairingInvitations/PRBGST0`, { method: "DELETE", headers: pH });
      inviteIds.pop();
    }
    return { expected: "200 ALLOWED", got: `HTTP ${r.status}`, pass: r.status === 200 };
  });

  // ---------- N11: relationship create without any invitation ----------
  await expectDenied("N11 create guest rel (no invitation)", () =>
    fetch(`${BASE}/trustedDeviceRelationships?documentId=${relIdG}`, {
      method: "POST",
      headers: hH,
      body: JSON.stringify({
        fields: {
          helperUid: F(HELPER_UID),
          helperEmail: F(FAKE_EMAILS[HELPER_UID]),
          recipientUid: F(PROBE_UID),
          recipientEmail: F(FAKE_EMAILS[PROBE_UID]),
          recipientDeviceId: F(devHex24),
          recipientDeviceModel: F("probe"),
          pairKey: F(relIdG),
          status: F("ACTIVE"),
          kind: F("guest"),
          guestExpiresAt: TS(Date.now() + 3600_000),
          createdByInvitation: F("NONEXIST"),
          createdAt: TS(Date.now()),
          updatedAt: TS(Date.now()),
        },
      }),
    })
  );

  // ---------- G1: REAL guest redeem (atomic commit) — allowed ----------
  await run("G1 guest redeem commit (live 65 s window)", async () => {
    await makeInvitation("PRBGST1", "guest");
    const r = await redeemCommit("PRBGST1", relIdG, "guest", Date.now() + 65_000);
    return { expected: "200 ALLOWED", got: `HTTP ${r.status}`, pass: r.status === 200 };
  });

  // ---------- N17 (v0.21.0): redeem WITHOUT the token — must be DENIED ----
  // This is the exact audit attack: the old rules verified NOTHING about the
  // token, so anyone who learned the 6-char doc id could claim the pairing.
  await run("N17 redeem with NO claimToken (audit attack)", async () => {
    await makeInvitation("PRBWOT1", "guest");
    const r = await redeemCommit("PRBWOT1", null, "guest", Date.now() + 65_000, null);
    return { expected: "403 DENIED", got: `HTTP ${r.status}`, pass: r.status === 403 };
  });

  // ---------- N18 (v0.21.0): redeem with a FORGED token — must be DENIED ---
  await run("N18 redeem with WRONG claimToken (forged)", async () => {
    await makeInvitation("PRBWOT2", "guest");
    const r = await redeemCommit("PRBWOT2", null, "guest", Date.now() + 65_000, "ffffffffffffffffffff");
    return { expected: "403 DENIED", got: `HTTP ${r.status}`, pass: r.status === 403 };
  });

  // ---------- G2: live guest ask allowed (+ honest cancel cleanup) ----------
  const guestSessionId = "prb-gst-session";
  await run("G2 guest session ask inside window", async () => {
    const r = await fetch(`${BASE}/supportSessions?documentId=${guestSessionId}`, {
      method: "POST",
      headers: hH,
      body: JSON.stringify({
        fields: {
          relId: F(relIdG),
          helperUid: F(HELPER_UID),
          helperEmail: F(FAKE_EMAILS[HELPER_UID]),
          recipientUid: F(PROBE_UID),
          recipientEmail: F(FAKE_EMAILS[PROBE_UID]),
          recipientDeviceModel: F("rules-probe-synthetic"),
          status: F("REQUESTED"),
          kind: F("guest"),
          requestedAt: TS(Date.now()),
          respondDeadline: TS(Date.now() + 60_000),
          createdAt: TS(Date.now()),
          updatedAt: TS(Date.now()),
        },
      }),
    });
    // immediately take it back through the rules-allowed cancel path so the
    // residue is an inert CANCELLED doc (sweep only ever reads REQUESTED)
    if (r.status === 200) {
      await fetch(
        `${BASE}/supportSessions/${guestSessionId}?updateMask.fieldPaths=status&updateMask.fieldPaths=respondedAt&updateMask.fieldPaths=updatedAt`,
        {
          method: "PATCH",
          headers: hH,
          body: JSON.stringify({
            fields: { status: F("CANCELLED"), respondedAt: TS(Date.now()), updatedAt: TS(Date.now()) },
          }),
        }
      );
    }
    return { expected: "200 ALLOWED", got: `HTTP ${r.status}`, pass: r.status === 200 };
  });

  // ---------- N12: family-kind ask riding a GUEST relationship ----------
  await expectDenied("N12 family ask on guest rel", () =>
    fetch(`${BASE}/supportSessions?documentId=prb-gst-n12`, {
      method: "POST",
      headers: hH,
      body: JSON.stringify({
        fields: {
          relId: F(relIdG),
          helperUid: F(HELPER_UID),
          helperEmail: F(FAKE_EMAILS[HELPER_UID]),
          recipientUid: F(PROBE_UID),
          recipientEmail: F(FAKE_EMAILS[PROBE_UID]),
          recipientDeviceModel: F("probe"),
          status: F("REQUESTED"),
          kind: F("family"),
          requestedAt: TS(Date.now()),
          respondDeadline: TS(Date.now() + 60_000),
          createdAt: TS(Date.now()),
          updatedAt: TS(Date.now()),
        },
      }),
    })
  );

  // ---------- N13: kind-flip attempt (guest -> family on the doc) --------
  // The kind field is FROZEN after creation: no update branch lists it.
  await expectDenied("N13 guest rel kind-flip to family", () =>
    fetch(
      `${BASE}/trustedDeviceRelationships/${relIdG}?updateMask.fieldPaths=kind&updateMask.fieldPaths=updatedAt`,
      {
        method: "PATCH",
        headers: hH,
        body: JSON.stringify({ fields: { kind: F("family"), updatedAt: TS(Date.now()) } }),
      }
    )
  );

  // ---------- N14: over-long guest window (server re-bound) ----------
  await run("N14 guest redeem with 10 h window", async () => {
    await makeInvitation("PRBGST2", "guest");
    const r = await redeemCommit("PRBGST2", relIdG + "x", "guest", Date.now() + 10 * 3600_000);
    return { expected: "403 DENIED", got: `HTTP ${r.status}`, pass: r.status === 403 };
  });

  // ---------- wait out the 65 s guest window ----------
  const waitMs = 70_000 - (Date.now() - t0);
  if (waitMs > 0) {
    console.log(`waiting ${(waitMs / 1000).toFixed(0)} s for the guest window to pass…`);
    await new Promise((res) => setTimeout(res, waitMs));
  }

  // ---------- N15: the SAME guest ask after the window ----------
  await expectDenied("N15 guest ask after window", () =>
    fetch(`${BASE}/supportSessions?documentId=prb-gst-n15`, {
      method: "POST",
      headers: hH,
      body: JSON.stringify({
        fields: {
          relId: F(relIdG),
          helperUid: F(HELPER_UID),
          helperEmail: F(FAKE_EMAILS[HELPER_UID]),
          recipientUid: F(PROBE_UID),
          recipientEmail: F(FAKE_EMAILS[PROBE_UID]),
          recipientDeviceModel: F("probe"),
          status: F("REQUESTED"),
          kind: F("guest"),
          requestedAt: TS(Date.now()),
          respondDeadline: TS(Date.now() + 60_000),
          createdAt: TS(Date.now()),
          updatedAt: TS(Date.now()),
        },
      }),
    })
  );

  // ---------- N13b: silent window extension via the STALE invitation ------
  // Runs AFTER the claimedAt-freshness window (2 min) has passed, so the
  // original guest invitation (claimed at ~t0) is genuinely stale. Without
  // the fence, a hostile helper could extend the guest window forever by
  // re-running a fake re-activation on the already-PAIRED invitation.
  const waitMs2 = 150_000 - (Date.now() - t0);
  if (waitMs2 > 0) {
    console.log(`waiting ${(waitMs2 / 1000).toFixed(0)} s more for the claimedAt-freshness window (2 min) to pass…`);
    await new Promise((res) => setTimeout(res, waitMs2));
  }
  await expectDenied("N13b silent guest window extension", () =>
    fetch(
      `${BASE}/trustedDeviceRelationships/${relIdG}?updateMask.fieldPaths=status&updateMask.fieldPaths=guestExpiresAt&updateMask.fieldPaths=updatedAt`,
      {
        method: "PATCH",
        headers: hH,
        body: JSON.stringify({
          fields: {
            status: F("ACTIVE"),
            guestExpiresAt: TS(Date.now() + 2 * 3600_000),
            updatedAt: TS(Date.now()),
          },
        }),
      }
    )
  );

  // ---------- N16a: lazy EXPIRED now allowed (positive) ----------
  await run("N16a lazy EXPIRED of over-time guest rel", async () => {
    const r = await fetch(
      `${BASE}/trustedDeviceRelationships/${relIdG}?updateMask.fieldPaths=status&updateMask.fieldPaths=updatedAt`,
      {
        method: "PATCH",
        headers: hH,
        body: JSON.stringify({ fields: { status: F("EXPIRED"), updatedAt: TS(Date.now()) } }),
      }
    );
    return { expected: "200 ALLOWED", got: `HTTP ${r.status}`, pass: r.status === 200 };
  });

  // ---------- N16b: guest rel DELETE allowed (positive; also cleanup) ------
  await run("N16b guest rel DELETE (participant)", async () => {
    const r = await fetch(`${BASE}/trustedDeviceRelationships/${relIdG}`, {
      method: "DELETE",
      headers: hH,
    });
    return { expected: "200 ALLOWED", got: `HTTP ${r.status}`, pass: r.status === 200 };
  });

  // ---------- cleanup: probe A deletes its synthetic invitations ----------
  let invCleanup = "n/a";
  try {
    const codes = [];
    for (const id of inviteIds) {
      const d = await fetch(`${BASE}/pairingInvitations/${id}`, { method: "DELETE", headers: pH });
      codes.push(`${id}:${d.status}`);
    }
    invCleanup = codes.join(", ") || "none";
  } catch (e) {
    invCleanup = `THREW ${e.message}`;
  }

  // ---------- cleanup: delete the synthetic Auth users ----------
  let cleanup = "n/a";
  try {
    const codes = [];
    for (const tok of [idToken, helperToken]) {
      const del = await fetch(
        `https://identitytoolkit.googleapis.com/v1/accounts:delete?key=${API_KEY}`,
        {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ idToken: tok }),
        }
      );
      codes.push(del.status);
    }
    cleanup = codes.join(" / ");
  } catch (e) {
    cleanup = `THREW ${e.message}`;
  }

  // ================= report ===============================================
  console.log("\n────────── Phase 9/10 negative-probe results ──────────");
  let allPass = true;
  let skipped = 0;
  for (const r of results) {
    const mark = r.pass ? (String(r.got).startsWith("SKIPPED") ? "◐" : "✔") : "✘";
    if (!r.pass) allPass = false;
    if (String(r.got).startsWith("SKIPPED")) skipped += 1;
    console.log(`${mark} ${r.name.padEnd(52)} expected ${r.expected.padEnd(12)} got ${r.got}`);
  }
  console.log(`\nsynthetic Auth user cleanup (accounts:delete): ${cleanup}`);
  // v0.21.0 (audit): SKIPPED probes used to print under the same green
  // banner — on an empty database the probe claimed "everything proven"
  // while having proven almost nothing. They no longer fail the run (they
  // are environment-dependent, not rule drift) but they are now COUNTED and
  // reported so a green run cannot create false confidence.
  if (skipped > 0) {
    console.log(`◐ NOTE: ${skipped} probe(s) were SKIPPED (no fixture doc) — NOT proven either way.`);
  }
  console.log(allPass
    ? "✔✔ ALL EXECUTED EXPECTATIONS MET — the live rules refused every hostile path it could test."
    : "✘✘ RULES DRIFT — at least one hostile path was NOT denied!");
  process.exit(allPass ? 0 : 1);
}

main().catch((e) => {
  console.error("✘ probe crashed:", e);
  process.exit(1);
});
