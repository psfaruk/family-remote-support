/**
 * Phase 8.2 — FCM kill-push sweep (v0.9.8).
 *
 * The recipient app's SessionNotifier (Firestore listener) can only deliver a
 * support-session request while the app PROCESS is alive. When Android has
 * killed the process, the ask silently disappears — the 2-minute answer
 * window expires and the helper sees nothing.
 *
 * This sweep closes that gap. It:
 *   1. finds supportSessions docs with status == "REQUESTED" that are FRESH
 *      (requested within the last 10 min, answer deadline still in the
 *      future) and not yet pushed (no killPushSentAt),
 *   2. looks the recipient's FCM tokens up in fcmTokens (Phase 5 registry),
 *   3. sends a high-priority FCM notification message per token — the OS
 *      itself displays it while the app is dead (no process code needed),
 *   4. marks the session (killPushSentAt / killPushStatus) so sweeps are
 *      idempotent, and deletes UNREGISTERED tokens (dead registry entries).
 *
 * Runners (same script, two homes):
 *   - sandbox mini-service kill-push-service: polls every 5 s  → ~5 s latency,
 *     the tier that actually meets the 2-minute answer window
 *   - GitHub Actions .github/workflows/kill-push.yml: cron every 5 min → a
 *     best-effort BACKSTOP for when the sandbox is recycled (GitHub may
 *     delay schedules; late runs skip — a push for an expired ask would be
 *     confusing, not helpful)
 *
 * No third-party dependencies: SA JWT → access token (RS256 via node:crypto),
 * Firestore REST (SA bypasses security rules — it is the admin path), FCM
 * HTTP v1. Credentials come from FIREBASE_SA_JSON (GitHub secret / env) or
 * the Owner's local secret files. The service account never leaves this box
 * except as a GitHub Actions secret.
 *
 * Usage:
 *   node scripts/fcm-kill-push.mjs          # one sweep, human-readable log
 *   (mini-service)                          import { sweepOnce } from this file
 */
import { readFileSync, existsSync } from "node:fs";
import crypto from "node:crypto";
import { pathToFileURL } from "node:url";

const CHANNEL_ID = "support_session_requests";
/** Only asks requested within this window are pushed (stale ones are noise). */
const FRESH_WINDOW_MILLIS = 10 * 60 * 1000;

// ---------------------------------------------------------------------------
// Service account resolution + OAuth (cached access token)
// ---------------------------------------------------------------------------

function loadSa() {
  const env = process.env.FIREBASE_SA_JSON;
  if (env && env.trim().startsWith("{")) {
    return JSON.parse(env);
  }
  const candidates = [
    "/home/z/.secrets/firebase-sa.json",
    "/home/z/my-project/.secrets/firebase-sa.json",
  ];
  const path = candidates.find((p) => existsSync(p));
  if (!path) {
    throw new Error(
      `no service account: FIREBASE_SA_JSON env not set and none of ${candidates.join(", ")} exist`
    );
  }
  return JSON.parse(readFileSync(path, "utf8"));
}

let _sa = null;
let _cachedToken = null; // { value, expSec }

async function getAccessToken() {
  const now = Math.floor(Date.now() / 1000);
  if (_cachedToken && _cachedToken.expSec - 120 > now) {
    return _cachedToken.value;
  }
  if (!_sa) _sa = loadSa();
  const sa = _sa;
  const header = Buffer.from(JSON.stringify({ alg: "RS256", typ: "JWT" })).toString("base64url");
  const claims = {
    iss: sa.client_email,
    // FCM send + Firestore read/write in one assertion.
    scope:
      "https://www.googleapis.com/auth/firebase.messaging https://www.googleapis.com/auth/datastore",
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
  if (!res.ok || !j.access_token) {
    throw new Error(`OAuth token failed: ${res.status} ${JSON.stringify(j).slice(0, 300)}`);
  }
  _cachedToken = { value: j.access_token, expSec: now + Number(j.expires_in || 3600) };
  return j.access_token;
}

async function authHeaders(extra = {}) {
  const token = await getAccessToken();
  return { Authorization: `Bearer ${token}`, ...extra };
}

// ---------------------------------------------------------------------------
// Firestore REST helpers
// ---------------------------------------------------------------------------

const SA_OBJ = () => (_sa ??= loadSa());
const fsBase = () =>
  `https://firestore.googleapis.com/v1/projects/${SA_OBJ().project_id}/databases/default/documents`;

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

/** All docs of a single-equality query (automatic single-field index). */
async function queryWhere(collectionId, fieldPath, value) {
  const res = await fetch(`${fsBase()}:runQuery`, {
    method: "POST",
    headers: await authHeaders({ "Content-Type": "application/json" }),
    body: JSON.stringify({
      structuredQuery: {
        from: [{ collectionId }],
        where: {
          fieldFilter: { field: { fieldPath }, op: "EQUAL", value: { stringValue: value } },
        },
      },
    }),
  });
  if (!res.ok) throw new Error(`runQuery ${collectionId}: ${res.status} ${(await res.text()).slice(0, 300)}`);
  const rows = await res.json();
  return rows
    .filter((r) => r.document)
    .map((r) => ({
      id: r.document.name.split("/").pop(),
      name: r.document.name,
      fields: r.document.fields || {},
    }));
}

async function patchDoc(docPath, fields, maskPaths) {
  const mask = maskPaths.map((p) => `updateMask.fieldPaths=${encodeURIComponent(p)}`).join("&");
  const res = await fetch(`${fsBase()}/${docPath}?${mask}`, {
    method: "PATCH",
    headers: await authHeaders({ "Content-Type": "application/json" }),
    body: JSON.stringify({ fields }),
  });
  if (!res.ok) throw new Error(`patch ${docPath}: ${res.status} ${(await res.text()).slice(0, 300)}`);
  return res.json();
}

async function deleteDoc(docPath) {
  const res = await fetch(`${fsBase()}/${docPath}`, {
    method: "DELETE",
    headers: await authHeaders(),
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

async function sendFcm(message) {
  const res = await fetch(
    `https://fcm.googleapis.com/v1/projects/${SA_OBJ().project_id}/messages:send`,
    {
      method: "POST",
      headers: await authHeaders({ "Content-Type": "application/json" }),
      body: JSON.stringify({ message }),
    }
  );
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
  return { ok: false, httpStatus: res.status, kind: FCM_ERROR_KIND(res.status, body), detail: body?.error?.message || "" };
}

// ---------------------------------------------------------------------------
// The sweep
// ---------------------------------------------------------------------------

/**
 * One idempotent sweep over fresh REQUESTED sessions.
 * Returns a summary — never throws for per-session problems (they are
 * reported in the summary); throws only for config/auth errors.
 */
export async function sweepOnce(log = console.log) {
  const now = Date.now();
  const summary = {
    requestedDocs: 0,
    fresh: 0,
    pushed: 0,
    noTokens: 0,
    tokenErrors: 0,
    deadTokensDeleted: 0,
    alreadyPushed: 0,
    errors: [],
  };

  // SA must load before anything else — a broken config should fail loudly.
  await getAccessToken();

  const docs = await queryWhere("supportSessions", "status", "REQUESTED");
  summary.requestedDocs = docs.length;

  for (const doc of docs) {
    const f = doc.fields;
    const requestedAt = fieldGet(f, "requestedAt") ?? fieldGet(f, "createdAt") ?? 0;
    const deadline = fieldGet(f, "respondDeadline") ?? 0;
    const pushedAt = fieldGet(f, "killPushSentAt");

    if (pushedAt) {
      summary.alreadyPushed += 1;
      continue;
    }
    if (!deadline || deadline <= now) {
      continue; // window already over — a push now would be confusing
    }
    if (!requestedAt || now - requestedAt > FRESH_WINDOW_MILLIS) {
      continue; // stale REQUESTED doc nobody ever answered (app was dead for it)
    }
    summary.fresh += 1;

    const recipientUid = fieldGet(f, "recipientUid") || "";
    const helperEmail = fieldGet(f, "helperEmail") || "A family member";
    const sessionId = doc.id;
    if (!recipientUid) {
      summary.errors.push(`${sessionId}: missing recipientUid`);
      continue;
    }

    // Recipient's registered tokens (Phase 5 registry).
    let tokens;
    try {
      const tokenDocs = await queryWhere("fcmTokens", "uid", recipientUid);
      tokens = tokenDocs
        .map((t) => ({ docId: t.id, token: fieldGet(t.fields, "token") }))
        .filter((t) => typeof t.token === "string" && t.token.length >= 32);
    } catch (e) {
      summary.errors.push(`${sessionId}: token lookup failed — ${e.message}`);
      continue;
    }
    if (tokens.length === 0) {
      summary.noTokens += 1;
      log(`kill-push: ${sessionId} → recipient ${recipientUid.slice(0, 8)}… has no FCM tokens (app never registered / new install)`);
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
      const r = await sendFcm(message);
      if (r.ok) {
        sent += 1;
        log(`kill-push: ${sessionId} → token ${t.docId.slice(0, 8)}… OK (${r.messageId})`);
      } else if (r.kind === "UNREGISTERED" || r.kind === "BAD_TOKEN") {
        summary.deadTokensDeleted += 1;
        try {
          await deleteDoc(`fcmTokens/${t.docId}`);
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
          `supportSessions/${sessionId}`,
          {
            killPushSentAt: { timestampValue: new Date().toISOString() },
            killPushStatus: { stringValue: `sent:${sent}/${Math.min(tokens.length, 3)}` },
          },
          ["killPushSentAt", "killPushStatus"]
        );
      } catch (e) {
        // Delivery happened; a duplicate on the next sweep is acceptable.
        summary.errors.push(`${sessionId}: mark failed — ${e.message}`);
      }
    } else {
      summary.tokenErrors += 1;
      if (lastKind) summary.errors.push(`${sessionId}: all sends failed — ${lastKind}`);
    }
  }

  return summary;
}

// ---------------------------------------------------------------------------
// CLI entry
// ---------------------------------------------------------------------------

export function formatSummary(s) {
  return (
    `sweep: ${s.requestedDocs} REQUESTED doc(s), ${s.fresh} fresh, ` +
    `${s.pushed} pushed, ${s.noTokens} no-tokens, ${s.alreadyPushed} already-pushed, ` +
    `${s.tokenErrors} send-failed, ${s.deadTokensDeleted} dead-token(s) cleaned` +
    (s.errors.length ? ` | errors: ${s.errors.join(" ; ")}` : "")
  );
}

const isMain = process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href;
if (isMain) {
  sweepOnce()
    .then((s) => {
      console.log(formatSummary(s));
      process.exit(0);
    })
    .catch((e) => {
      console.error("✘ kill-push sweep failed:", e.message);
      process.exit(1);
    });
}
