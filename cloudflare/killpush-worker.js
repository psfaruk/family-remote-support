/**
 * Family Remote Support — Phase 14: kill-push PERMANENT tier (Cloudflare Worker).
 *
 * Free-plan Worker (no card required) that keeps the FCM kill-push alive even
 * when the sandbox is recycled or GitHub schedules are delayed. It runs the
 * SAME sweep as scripts/fcm-kill-push.mjs (the proven Node implementation):
 *
 *   1. Firestore runQuery: supportSessions where status == "REQUESTED"
 *   2. keep only FRESH asks (respondDeadline still in the future, requested
 *      within the last 10 min, not already pushed)
 *   3. look up the recipient's registered tokens in fcmTokens
 *   4. send a high-priority FCM notification per token — the OS itself
 *      displays it while the app process is dead (no process code needed)
 *   5. mark killPushSentAt / killPushStatus (idempotent) and delete
 *      UNREGISTERED tokens (dead registry entries)
 *
 * Triggers:
 *   - Cron Trigger "* * * * *" (every minute, set in the dashboard) — the
 *     permanent tier: worst-case push latency ~60 s, inside the 2-minute
 *     answer window, forever, for free.
 *   - POST /sweep with "Authorization: Bearer <KILL_TOKEN>" — manual sweep
 *     (and, in a later phase, the helper app calls this right after creating
 *     a session doc for a true instant tier).
 *   - GET /health — open liveness + last sweep summary (never any secret).
 *
 * Auth posture (Owner decision, Phase 14): start with a simple shared token;
 * a stricter per-caller scheme can be added later without touching the sweep.
 *
 * Zero dependencies: SA JWT → OAuth access token via Web Crypto (RS256),
 * Firestore REST v1, FCM HTTP v1 — identical endpoints, message shape and
 * constants as the Node implementation, so the two tiers can never drift.
 *
 * Dashboard secrets (Worker → Settings → Variables and Secrets):
 *   FIREBASE_SA_JSON  full Firebase service-account JSON (client_email,
 *                     private_key, project_id are used)
 *   KILL_TOKEN        shared bearer token for POST /sweep
 */

const CHANNEL_ID = "support_session_requests";
/** Only asks requested within this window are pushed (stale ones are noise). */
const FRESH_WINDOW_MILLIS = 10 * 60 * 1000;

// v0.21.0 (audit) — CROSS-TIER IDEMPOTENCY. Three sweep tiers (sandbox 5 s,
// this Worker 60 s, GitHub Actions 5 min) used to race check-then-send-then-
// mark: two tiers reading the same second BOTH sent → duplicate high-
// priority notifications. Each tier now CLAIMS the doc first with an
// optimistic-concurrency precondition (currentDocument.updateTime from the
// read): exactly one claim can win, and only the winner sends.
const CLAIM_TTL_MILLIS = 5 * 60 * 1000;
/** v0.21.0 (audit): free-plan subrequest ceiling is 50/invocation; a fresh
 * doc costs ≤9 (claim + token query + ≤3 sends + ≤3 deletes + mark), so a
 * hard cap of 4 docs/pass keeps the worst case at 37 + the initial query. */
const MAX_DOCS_PER_SWEEP = 4;
/** v0.21.0 (audit): stale REQUESTED docs (deadline long over) are lazily
 * EXPIRED by the sweep itself — they used to be re-fetched by all 1,440
 * daily sweeps forever (read cost + notification-bombing surface). */
const EXPIRE_GRACE_MILLIS = 60 * 1000;

// ---------------------------------------------------------------------------
// Per-isolate state (best effort — isolates may restart at any time)
// ---------------------------------------------------------------------------

let _sa = null; // parsed service account
let _cachedToken = null; // { value, expSec }
const last = { at: null, ok: null, summary: "not run yet" };
let inFlight = false;

// ---------------------------------------------------------------------------
// Tiny helpers (Web APIs only — no node:crypto in Workers)
// ---------------------------------------------------------------------------

function bytesToB64url(bytes) {
  let bin = "";
  const arr = bytes instanceof Uint8Array ? bytes : new Uint8Array(bytes);
  const chunk = 0x8000;
  for (let i = 0; i < arr.length; i += chunk) {
    bin += String.fromCharCode.apply(null, arr.subarray(i, i + chunk));
  }
  return btoa(bin).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

function strToBytes(s) {
  return new TextEncoder().encode(s);
}

/** PKCS8 PEM (Google SA "private_key") → ArrayBuffer for crypto.subtle. */
function pemPkcs8ToDer(pem) {
  const b64 = pem
    .replace(/-----BEGIN PRIVATE KEY-----/g, "")
    .replace(/-----END PRIVATE KEY-----/g, "")
    .replace(/\s+/g, "");
  const bin = atob(b64);
  const der = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i++) der[i] = bin.charCodeAt(i);
  return der.buffer;
}

/** Constant-time string compare (length leak only — acceptable here). */
function timingSafeEqualStr(a, b) {
  if (typeof a !== "string" || typeof b !== "string") return false;
  const len = Math.max(a.length, b.length);
  let diff = a.length === b.length ? 0 : 1;
  for (let i = 0; i < len; i++) {
    diff |= (a.charCodeAt(i) || 0) ^ (b.charCodeAt(i) || 0);
  }
  return diff === 0;
}

function fieldGet(fields, name) {
  const v = fields?.[name];
  if (!v) return undefined;
  if ("stringValue" in v) return v.stringValue;
  if ("timestampValue" in v) return new Date(v.timestampValue).getTime();
  if ("integerValue" in v) return Number(v.integerValue);
  if ("doubleValue" in v) return Number(v.doubleValue);
  if ("booleanValue" in v) return v.booleanValue;
  return undefined;
}

// ---------------------------------------------------------------------------
// Service account + OAuth (cached access token)
// ---------------------------------------------------------------------------

function loadSa(env) {
  if (_sa) return _sa;
  const raw = env && env.FIREBASE_SA_JSON;
  if (!raw || typeof raw !== "string") {
    throw new Error("secret FIREBASE_SA_JSON is not set");
  }
  const sa = JSON.parse(raw);
  if (!sa.client_email || !sa.private_key || !sa.project_id) {
    throw new Error("FIREBASE_SA_JSON is missing client_email/private_key/project_id");
  }
  _sa = sa;
  return sa;
}

async function getAccessToken(env) {
  const now = Math.floor(Date.now() / 1000);
  if (_cachedToken && _cachedToken.expSec - 120 > now) {
    return _cachedToken.value;
  }
  const sa = loadSa(env);
  const header = bytesToB64url(strToBytes(JSON.stringify({ alg: "RS256", typ: "JWT" })));
  const claims = {
    iss: sa.client_email,
    // FCM send + Firestore read/write in one assertion.
    scope:
      "https://www.googleapis.com/auth/firebase.messaging https://www.googleapis.com/auth/datastore",
    aud: "https://oauth2.googleapis.com/token",
    iat: now,
    exp: now + 3600,
  };
  const payload = bytesToB64url(strToBytes(JSON.stringify(claims)));
  const unsigned = `${header}.${payload}`;
  const key = await crypto.subtle.importKey(
    "pkcs8",
    pemPkcs8ToDer(sa.private_key),
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["sign"]
  );
  const sig = await crypto.subtle.sign("RSASSA-PKCS1-v1_5", key, strToBytes(unsigned));
  const assertion = `${unsigned}.${bytesToB64url(sig)}`;
  const res = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
      assertion,
    }).toString(),
  });
  const j = await res.json();
  if (!res.ok || !j.access_token) {
    throw new Error(`OAuth token failed: ${res.status} ${JSON.stringify(j).slice(0, 300)}`);
  }
  _cachedToken = { value: j.access_token, expSec: now + Number(j.expires_in || 3600) };
  return j.access_token;
}

async function authHeaders(env, extra = {}) {
  const token = await getAccessToken(env);
  return { Authorization: `Bearer ${token}`, ...extra };
}

// ---------------------------------------------------------------------------
// Firestore REST (SA bypasses security rules — it is the admin path)
// ---------------------------------------------------------------------------

const fsBase = (sa) =>
  `https://firestore.googleapis.com/v1/projects/${sa.project_id}/databases/default/documents`;

/** All docs of a single-equality query (automatic single-field index).
 * v0.21.0 (audit): NDJSON-tolerant parsing — Google sometimes answers
 * :runQuery with streamed/newline-delimited chunks; the old res.json() then
 * threw and killed the whole sweep pass. Also captures updateTime for the
 * claim precondition. */
async function queryWhere(env, collectionId, fieldPath, value) {
  const sa = loadSa(env);
  const res = await fetch(`${fsBase(sa)}:runQuery`, {
    method: "POST",
    headers: await authHeaders(env, { "Content-Type": "application/json" }),
    body: JSON.stringify({
      structuredQuery: {
        from: [{ collectionId }],
        where: {
          fieldFilter: { field: { fieldPath }, op: "EQUAL", value: { stringValue: value } },
        },
      },
    }),
  });
  if (!res.ok) {
    throw new Error(`runQuery ${collectionId}: ${res.status} ${(await res.text()).slice(0, 300)}`);
  }
  const text = await res.text();
  let rows;
  try {
    rows = JSON.parse(text);
  } catch {
    // NDJSON fallback: parse line by line, skipping blank/keepalive lines.
    rows = [];
    for (const line of text.split("\n")) {
      const t = line.trim();
      if (!t) continue;
      try {
        rows.push(JSON.parse(t));
      } catch {
        /* skip malformed fragment */
      }
    }
  }
  return rows
    .filter((r) => r.document)
    .map((r) => ({
      id: r.document.name.split("/").pop(),
      name: r.document.name,
      updateTime: r.document.updateTime || null,
      fields: r.document.fields || {},
    }));
}

async function patchDoc(env, docPath, fields, maskPaths) {
  const sa = loadSa(env);
  const mask = maskPaths.map((p) => `updateMask.fieldPaths=${encodeURIComponent(p)}`).join("&");
  const res = await fetch(`${fsBase(sa)}/${docPath}?${mask}`, {
    method: "PATCH",
    headers: await authHeaders(env, { "Content-Type": "application/json" }),
    body: JSON.stringify({ fields }),
  });
  if (!res.ok) {
    throw new Error(`patch ${docPath}: ${res.status} ${(await res.text()).slice(0, 300)}`);
  }
  return res.json();
}

/** v0.21.0 (audit) — PATCH with an optimistic-concurrency precondition:
 * succeeds ONLY if the doc's updateTime still equals [expectedUpdateTime].
 * Returns true when the claim landed; false on 409/ABORTED (another tier
 * touched the doc first — that tier owns the push). */
async function patchDocIfUnchanged(env, docPath, fields, maskPaths, expectedUpdateTime) {
  const sa = loadSa(env);
  const mask = maskPaths.map((p) => `updateMask.fieldPaths=${encodeURIComponent(p)}`).join("&");
  const res = await fetch(`${fsBase(sa)}/${docPath}?${mask}`, {
    method: "PATCH",
    headers: await authHeaders(env, { "Content-Type": "application/json" }),
    body: JSON.stringify({ fields, currentDocument: { updateTime: expectedUpdateTime } }),
  });
  if (res.ok) return true;
  if (res.status === 409 || res.status === 400) {
    // 400: Firestore rejects preconditions on docs without updateTime; 409:
    // precondition failed. Either way this tier does not own the doc.
    try {
      await res.text();
    } catch {
      /* ignore */
    }
    return false;
  }
  throw new Error(`claim patch ${docPath}: ${res.status} ${(await res.text()).slice(0, 300)}`);
}

async function deleteDoc(env, docPath) {
  const sa = loadSa(env);
  const res = await fetch(`${fsBase(sa)}/${docPath}`, {
    method: "DELETE",
    headers: await authHeaders(env),
  });
  if (!res.ok && res.status !== 404) {
    throw new Error(`delete ${docPath}: ${res.status} ${(await res.text()).slice(0, 200)}`);
  }
}

// ---------------------------------------------------------------------------
// FCM HTTP v1
// ---------------------------------------------------------------------------

const FCM_ERROR_KIND = (httpStatus, body) => {
  const status = body?.error?.status || "";
  const msg = body?.error?.message || "";
  if (status === "UNREGISTERED" || httpStatus === 404) return "UNREGISTERED";
  if (status === "INVALID_ARGUMENT" && /token/i.test(msg)) return "BAD_TOKEN";
  return `OTHER:${status || httpStatus}`;
};

async function sendFcm(env, message) {
  const sa = loadSa(env);
  const res = await fetch(`https://fcm.googleapis.com/v1/projects/${sa.project_id}/messages:send`, {
    method: "POST",
    headers: await authHeaders(env, { "Content-Type": "application/json" }),
    body: JSON.stringify({ message }),
  });
  if (res.ok) {
    const j = await res.json().catch(() => ({}));
    return { ok: true, messageId: j.name || j.message_id || "?" };
  }
  let body = null;
  try {
    body = await res.json();
  } catch {
    /* ignore */
  }
  return {
    ok: false,
    httpStatus: res.status,
    kind: FCM_ERROR_KIND(res.status, body),
    detail: body?.error?.message || "",
  };
}

// ---------------------------------------------------------------------------
// The sweep — one idempotent pass over fresh REQUESTED sessions.
// Identical gates, constants and message shape to scripts/fcm-kill-push.mjs.
// Never throws for per-session problems (they are reported in the summary);
// throws only for config/auth errors.
// ---------------------------------------------------------------------------

async function sweepOnce(env, log = () => {}) {
  const now = Date.now();
  const summary = {
    requestedDocs: 0,
    fresh: 0,
    pushed: 0,
    noTokens: 0,
    tokenErrors: 0,
    deadTokensDeleted: 0,
    alreadyPushed: 0,
    claimLost: 0,
    expiredStale: 0,
    activeDocs: 0,
    activeExpired: 0,
    errors: [],
  };

  // SA must load before anything else — a broken config should fail loudly.
  await getAccessToken(env);

  const docs = await queryWhere(env, "supportSessions", "status", "REQUESTED");
  summary.requestedDocs = docs.length;

  // v0.21.0 (audit): doc-id order from Firestore is arbitrary — process the
  // most urgent (soonest deadline) first under the subrequest cap.
  const byDeadline = (a, b) =>
    (fieldGet(a.fields, "respondDeadline") ?? 0) - (fieldGet(b.fields, "respondDeadline") ?? 0);
  docs.sort(byDeadline);

  let processed = 0;
  for (const doc of docs) {
    // v0.21.0 (audit): one broken doc must never abort the remaining sweep
    // (the old code let a single send/delete rejection escape the loop).
    try {
      const f = doc.fields;
      const requestedAt = fieldGet(f, "requestedAt") ?? fieldGet(f, "createdAt") ?? 0;
      const deadline = fieldGet(f, "respondDeadline") ?? 0;
      const pushedAt = fieldGet(f, "killPushSentAt");
      const claimedAt = fieldGet(f, "killPushClaimAt");

      if (pushedAt) {
        summary.alreadyPushed += 1;
        continue;
      }
      // A live claim from another tier (or our own crashed attempt) owns
      // this doc until CLAIM_TTL passes.
      if (claimedAt && now - claimedAt < CLAIM_TTL_MILLIS) {
        continue;
      }
      if (!deadline || deadline <= now) {
        // v0.21.0 (audit): stale REQUESTED docs are expired here (admin path,
        // same transition the lazy client cleanup performs) — they used to
        // be re-fetched by every one of the 1,440 daily sweeps forever.
        if (deadline && now - deadline > EXPIRE_GRACE_MILLIS) {
          try {
            await patchDoc(
              env,
              `supportSessions/${doc.id}`,
              { status: { stringValue: "EXPIRED" }, updatedAt: { timestampValue: new Date().toISOString() } },
              ["status", "updatedAt"]
            );
            summary.expiredStale += 1;
          } catch {
            /* best effort — the client lazy cleanup also covers this */
          }
        }
        continue; // window already over — a push now would be confusing
      }
      if (!requestedAt || now - requestedAt > FRESH_WINDOW_MILLIS) {
        continue; // stale REQUESTED doc nobody ever answered
      }
      if (processed >= MAX_DOCS_PER_SWEEP) {
        continue; // subrequest budget — the next 60 s sweep takes the rest
      }
      processed += 1;
      summary.fresh += 1;

      const recipientUid = fieldGet(f, "recipientUid") || "";
      const helperEmail = fieldGet(f, "helperEmail") || "A family member";
      const sessionId = doc.id;
      if (!recipientUid) {
        summary.errors.push(`${sessionId}: missing recipientUid`);
        continue;
      }

      // v0.21.0 (audit): CLAIM-BEFORE-SEND — exactly one of the three sweep
      // tiers can win this preconditioned write; the losers skip the doc, so
      // duplicate high-priority pushes are gone.
      const claimed = await patchDocIfUnchanged(
        env,
        `supportSessions/${sessionId}`,
        { killPushClaimAt: { timestampValue: new Date().toISOString() } },
        ["killPushClaimAt"],
        doc.updateTime
      );
      if (!claimed) {
        summary.claimLost += 1;
        continue;
      }

      // Recipient's registered tokens.
      let tokens;
      try {
        const tokenDocs = await queryWhere(env, "fcmTokens", "uid", recipientUid);
        tokens = tokenDocs
          .map((t) => ({ docId: t.id, token: fieldGet(t.fields, "token") }))
          .filter((t) => typeof t.token === "string" && t.token.length >= 32);
      } catch (e) {
        summary.errors.push(`${sessionId}: token lookup failed — ${e.message}`);
        continue;
      }
      if (tokens.length === 0) {
        summary.noTokens += 1;
        log(`kill-push: ${sessionId} → recipient has no FCM tokens (app never registered / new install)`);
        // Deliberately NOT marked: if the recipient opens the app inside the
        // window, registration appears and the next sweep can still push.
        continue;
      }

      const message = {
        token: tokens[0].token, // one device = typically one token; extras are fallback below
        notification: {
          title: "Help requested",
          body: `${helperEmail} wants to help with this phone — open to allow or deny. (সাহায্যের অনুরোধ — অনুমতি দিতে অ্যাপটি খুলুন)`,
        },
        data: {
          type: "session_request",
          sessionId,
          helperEmail,
        },
        android: {
          priority: "HIGH",
          notification: { channel_id: CHANNEL_ID },
        },
      };

      let sent = 0;
      let lastKind = "";
      for (const t of tokens.slice(0, 3)) {
        message.token = t.token;
        let r;
        try {
          r = await sendFcm(env, message);
        } catch (e) {
          // v0.21.0 (audit): a transient network rejection on ONE token
          // must not kill the doc (it used to escape and abort the sweep).
          lastKind = `network:${String(e && e.message ? e.message : e).slice(0, 120)}`;
          continue;
        }
        if (r.ok) {
          sent += 1;
          log(`kill-push: ${sessionId} → token ${t.docId.slice(0, 8)}… OK (${r.messageId})`);
        } else if (r.kind === "UNREGISTERED" || r.kind === "BAD_TOKEN") {
          summary.deadTokensDeleted += 1;
          try {
            await deleteDoc(env, `fcmTokens/${t.docId}`);
            log(`kill-push: token ${t.docId.slice(0, 8)}… UNREGISTERED → registry entry deleted`);
          } catch (e) {
            summary.errors.push(`token ${t.docId} delete failed — ${e.message}`);
          }
        } else {
          lastKind = `${r.kind}${r.detail ? ` (${r.detail.slice(0, 120)})` : ""}`;
        }
      }

      if (sent > 0) {
        summary.pushed += 1;
        try {
          await patchDoc(
            env,
            `supportSessions/${sessionId}`,
            {
              killPushSentAt: { timestampValue: new Date().toISOString() },
              killPushStatus: { stringValue: `sent:${sent}/${Math.min(tokens.length, 3)}` },
            },
            ["killPushSentAt", "killPushStatus"]
          );
        } catch (e) {
          // Delivery happened; the claim + next-sweep TTL covers the rare
          // duplicate this can cause (same trade-off as before, but rarer).
          summary.errors.push(`${sessionId}: mark failed — ${e.message}`);
        }
      } else {
        summary.tokenErrors += 1;
        if (lastKind) summary.errors.push(`${sessionId}: all sends failed — ${lastKind}`);
      }
    } catch (e) {
      summary.errors.push(
        `${doc.id}: sweep error — ${String(e && e.message ? e.message : e).slice(0, 160)}`
      );
    }
  }

  // -----------------------------------------------------------------------
  // v0.22.0 (Phase 18.1): zombie ACTIVE sessions — a phone rebooted (or both
  // apps killed) mid-session leaves the doc ACTIVE past its sessionEndsAt;
  // only a client's lazy expiry used to clear it, and if NO client ever
  // reopened on that pair, the doc stayed ACTIVE forever (it also kept
  // filling the apps' capped open-session watch windows — the Owner's
  // "reboot → family can't reconnect" server-side half). Admin-path
  // ENDED("timeout") — the identical transition the security rules allow
  // either participant; oldest zombie first, hard-capped per sweep.
  // -----------------------------------------------------------------------
  try {
    const actives = await queryWhere(env, "supportSessions", "status", "ACTIVE");
    summary.activeDocs = actives.length;
    const staleActives = actives
      .map((doc) => ({ doc, endsAt: fieldGet(doc.fields, "sessionEndsAt") ?? 0 }))
      .filter((x) => x.endsAt > 0 && now - x.endsAt > EXPIRE_GRACE_MILLIS)
      .sort((a, b) => a.endsAt - b.endsAt); // oldest zombie first
    for (const { doc } of staleActives.slice(0, 50)) {
      try {
        await patchDoc(
          env,
          `supportSessions/${doc.id}`,
          {
            status: { stringValue: "ENDED" },
            endedBy: { stringValue: "timeout" },
            endedAt: { timestampValue: new Date().toISOString() },
            updatedAt: { timestampValue: new Date().toISOString() },
          },
          ["status", "endedBy", "endedAt", "updatedAt"]
        );
        summary.activeExpired += 1;
      } catch {
        /* best effort — the client sweeps also perform this transition */
      }
    }
  } catch (e) {
    summary.errors.push(
      `active-sweep failed — ${String(e && e.message ? e.message : e).slice(0, 120)}`
    );
  }

  return summary;
}

// ---------------------------------------------------------------------------
// Entry points
// ---------------------------------------------------------------------------

async function runSweep(env) {
  if (inFlight) return { skipped: true, reason: "sweep-already-running" };
  inFlight = true;
  const at = new Date().toISOString();
  try {
    const s = await sweepOnce(env);
    last.at = at;
    last.ok = true;
    // v0.21.0 (audit): /health used to expose the FULL summary (doc ids,
    // token-id prefixes, Google error text) to the public internet — keep
    // only counts + a coarse error tally here; the full detail lives in the
    // Worker observability logs (dashboard-only).
    const { errors, ...counts } = s;
    last.summary = JSON.stringify({ ...counts, errors: Array.isArray(errors) ? errors.length : 0 });
    return s;
  } catch (e) {
    last.at = at;
    last.ok = false;
    // Truncated — /health is public; the full error lives in observability logs.
    last.summary = ("error: " + String(e && e.message ? e.message : e)).slice(0, 80);
    throw e;
  } finally {
    inFlight = false;
  }
}

const json = (obj, status = 200) =>
  // v0.21.0 (audit): CORS headers REMOVED — this Worker serves cron + a
  // bearer-token POST endpoint only; "*" origins with Authorization allowed
  // served no purpose and widened the blast radius if KILL_TOKEN ever leaks.
  new Response(JSON.stringify(obj), {
    status,
    headers: { "Content-Type": "application/json" },
  });

export default {
  async fetch(request, env, ctx) {
    const url = new URL(request.url);

    if (url.pathname === "/health") {
      return json({
        ok: true,
        service: "frs-killpush",
        phase: "14 — kill-push permanent tier",
        cron: "* * * * *",
        secretsPresent: {
          FIREBASE_SA_JSON: Boolean(env && env.FIREBASE_SA_JSON),
          KILL_TOKEN: Boolean(env && env.KILL_TOKEN),
        },
        last,
      });
    }

    if (url.pathname === "/sweep") {
      if (request.method !== "POST") {
        return json({ ok: false, error: "POST required" }, 405);
      }
      const got = request.headers.get("Authorization") || "";
      const want = `Bearer ${env && env.KILL_TOKEN ? env.KILL_TOKEN : ""}`;
      if (!env || !env.KILL_TOKEN || !timingSafeEqualStr(got, want)) {
        return json({ ok: false, error: "unauthorized" }, 401);
      }
      try {
        const s = await runSweep(env);
        return json({ ok: true, summary: s });
      } catch (e) {
        return json({ ok: false, error: String(e && e.message ? e.message : e) }, 500);
      }
    }

    return json({ ok: false, error: "not-found" }, 404);
  },

  async scheduled(controller, env, ctx) {
    ctx.waitUntil(
      runSweep(env).catch(() => {
        /* last summary already records the error */
      })
    );
  },
};
