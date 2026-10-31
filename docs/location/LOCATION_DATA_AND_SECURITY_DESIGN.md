# Location Data & Security Design — Remote Family Support

**Status:** COMPLETE (Master Prompt Phase 3) · **Baseline:** v0.37.0 (56) · 2026-10-31
**Depends on:** `LOCATION_FEATURE_AUDIT.md` (§8 architecture), `firestore.rules` liveLocations block (the proven enforcement pattern), §75 quota directive.
**Design stance:** rules-first enforcement (server-verified on every request, probe-tested live), owner-scoped data paths, zero timer-driven backend traffic, minimal fields, metadata-only audit.

---

## 1. Collection map (target state)

```
liveLocations/{uid}_{deviceId}                      EXISTING — unchanged semantics, +1 optional read path (§4.4)
locationAuthorizations/{recipientUid}_{ownerUid}_{deviceId}   NEW — the ONLY source of feature grants
users/{ownerUid}/locationHistory/{recordId}          NEW — retained journey points (opt-in)
placeAlerts/{ownerUid}_{n}                           NEW — saved places + self-alert config
locationAuditEvents/{eventId}                        NEW — metadata-only consent/security events
```

Why owner-scoped history path: the rules `match /users/{ownerUid}/locationHistory/{recordId}` binds `ownerUid` as a **path variable**, so recipient-side authorization checks (`get()` on the authz doc keyed by `request.auth.uid + ownerUid + deviceId`) evaluate deterministically per query — the ergonomically safe shape for `list` rules. A flat collection would force per-doc `resource.data` conditions that interact badly with list semantics ("rules are not filters").

## 2. Document schemas

### 2.1 `locationAuthorizations/{recipientUid}_{ownerUid}_{deviceId}`

| Field | Type | Notes |
|---|---|---|
| `ownerUserId` / `recipientUserId` / `deviceId` | string | mirror of docId parts; rules re-verify |
| `enabledFeatures` | map `{ live: bool, history: bool }` | master-plan §6.1 `enabledFeatures` + `historyAccessEnabled` folded into one map (single source of truth) |
| `scope` | string | `ONCE` / `TEMPORARY` / `UNTIL_STOP` / `WITH_HISTORY` |
| `expiresAt` | timestamp, **absent when scope = UNTIL_STOP** | rules check `expiresAt > request.time` when present |
| `retentionPolicy` | string | `NONE` / `H24` / `D7` / `D30` |
| `authorizationVersion` | int | starts at 1; bumped on model migration |
| `createdAt` / `updatedAt` | timestamp (serverTimestamp) | |
| `revokedAt` | timestamp, absent while active | revocation marker (§5) |

**Rules:** write = **owner only** (`ownerUserId == request.auth.uid`), create requires docId == `recipientUid_ownerUid_deviceId` with both uids non-empty and `recipientUserId != request.auth.uid`; updates field-fenced (`affectedKeys().hasOnly(['enabledFeatures','scope','expiresAt','retentionPolicy','updatedAt','revokedAt'])`); `retentionPolicy` only settable when scope ∈ {WITH_HISTORY, UNTIL_STOP-with-history}. Read = owner OR the recipient themselves (`recipientUserId == request.auth.uid`, single-doc get — recipients `list` their own via `where('recipientUserId','==',me)` filter which the rule satisfies). **A recipient can never write, extend, or un-revoke.**

### 2.2 `users/{ownerUid}/locationHistory/{recordId}`

recordId = `{deviceId}_{epochMillis}` (sortable, collision-free per device).

| Field | Type | Validation (rules) |
|---|---|---|
| `ownerUserId` | string | `== ownerUid` path var AND `== request.auth.uid` on write |
| `deviceId` | string | 24-hex pattern (existing `stableDeviceId` format) |
| `lat` / `lng` | number | −90..90 / −180..180 |
| `accuracyMeters` | int | 0..100000 |
| `recordedAt` | timestamp | device clock; within `request.time ± 10 min` (skew allowance; same technique as live doc's ±5 min, wider for device clocks) |
| `receivedAt` | timestamp | `serverTimestamp()` (rules see it as request.time — same as live doc today) |
| `speedMetersPerSecond` | number, optional | 0..90 (≈324 km/h — impossible values rejected server-side) |
| `bearingDegrees` | number, optional | 0..360 |
| `altitudeMeters` | number, optional | −1000..10000 |
| `provider` | string | enum: `fused` / `gps` / `network` / `platform` |
| `retentionExpiresAt` | timestamp | `recordedAt + policy`; rules require `> request.time` on `get`, and **`list` queries must filter on it** (§4.3) |
| `authorizationVersion` / `appVersionCode` | int | ≥1, ≤10⁶ |

`hasOnly` the exact field set above (forged-key rejection, same as live doc). **Never trust client identity:** `request.auth.uid` must equal the owner path segment AND `ownerUserId` field.

### 2.3 `placeAlerts/{ownerUid}_{n}`

`ownerUserId`, `name` (≤64 chars), `lat`, `lng` (range-validated), `radiusMeters` (int 50..1000), `arrivalEnabled`/`departureEnabled` (bool), `cooldownMinutes` (int 5..120), `createdAt`/`updatedAt`. Owner-only read/write. **No coordinates ever appear in the alert notification itself** (name only — "You arrived near Work").

### 2.4 `locationAuditEvents/{eventId}`

Metadata **only** — `hasOnly` enforced: `kind` (enum: `sharing_enabled`, `sharing_disabled`, `recipient_added`, `recipient_removed`, `access_revoked`, `history_deleted`), `actorUid`, `ownerUid`, `recipientUid` (optional), `deviceId`, `at` (serverTimestamp), `appVersionCode`. No coordinates, no route copies. Write = owner (`actorUid == request.auth.uid == ownerUid`); read = owner only. **Honest limitation (disclosed):** client-attested best-effort events for owner visibility — a true server-side audit trail requires a trusted backend (decision record D1) and is deferred.

## 3. Data minimization (write path)

History points are written **only** when ALL hold: sharing enabled AND authorization `enabledFeatures.history == true` AND `retentionPolicy != NONE` AND the fix passes the existing §3 gates (accuracy ≤ 100 m, Δt ≥ interval, Δdistance ≥ 25 m) AND **history-specific throttle**: Δdistance ≥ 50 m from the last RETAINED point (history is coarser than live by design — stationary back-off compounds this). Speed/bearing/altitude stored **only when the OS provides them** — never computed-then-presented-as-measured; classifier-derived values stay client-side UI state, not stored facts.

## 4. Rules enforcement model

### 4.1 The invariant set (every new collection)

1. `signedIn()` required; 2. writes owner-only with identity re-verification (`request.auth.uid` == field AND path); 3. field-set `hasOnly` + range validation; 4. `request.time`-based window checks (never status flags alone — guest-window lesson from audit §4.8); 5. delete = owner only; 6. catch-all deny stays.

### 4.2 Authorization resolution function (shared)

```
function locationAuthz(ownerUid, deviceId, feature) {
  return exists(/locationAuthorizations/$(request.auth.uid + '_' + ownerUid + '_' + deviceId))
    && get(...).data.enabledFeatures[feature] == true
    && !('revokedAt' in get(...).data)
    && (!('expiresAt' in get(...).data) || get(...).data.expiresAt > request.time);
}
```

Rules `get()` budget: 1 authz doc (+1 relationship doc for the legacy live branch) — well inside the 10-access limit per evaluation.

### 4.3 History reads (get + list)

- `get`: owner OR `locationAuthz(ownerUid, deviceId, 'history')`, AND `resource.data.retentionExpiresAt > request.time` (expired records are denied individually even before deletion — "not returned through application queries" satisfied at rule level).
- `list`: same authorization, and the client query **must** include `where('retentionExpiresAt', '>', <now + 5 min skew buffer>)` — the rule checks `resource.data.retentionExpiresAt > request.time - duration.value(5,'m')` so a slightly-behind client clock cannot page in expired records (clock-skew-safe pattern; exact bounds locked by probe cases H-series at Phase 8).
- Date-range scoping is client-side query shape (`where('recordedAt', '>=', dayStart).where('recordedAt', '<', dayEnd)`) — the authorization itself grants history access generally; per-master-plan "date-range restrictions" the **owner may set** `historyFromDate` on the authz doc (optional field, rules-checked in list via the same resource-bound technique) — v1 keeps it simple: full-range while authorized; the field is reserved in the schema for v2.

### 4.4 liveLocations (migration — dual read path, zero downtime)

Existing relationship-based read branch stays untouched (legacy clients keep working). New branch added via OR: `locationAuthz(resource.data.uid, resource.data.deviceId, 'live') && resource.data.sharingEnabled == true`. New clients (v0.38+) create the authz doc during the 6-step wizard; old clients' "until stopped" sharing continues to work through the legacy branch. **A relationship alone grants nothing new; an authz doc alone grants nothing without an ACTIVE relationship for guest windows** (family: relationship not required to be re-checked — the authz IS the consent; guest recipients additionally need the relationship alive, mirroring today's `myPairing` guest semantics). Probe matrix gains L-series cases for both paths + cross-path negatives.

### 4.5 Probe suite extension (Phase 8 gate: 100 % PASS live)

Clone `scripts/rules-liveLocations-probe.mjs` → `scripts/rules-location-v2-probe.mjs`, target ≥ 30 cases:
- **A-series (authz doc):** owner create OK; recipient create DENY; forged docId DENY; recipient self-escalation (`enabledFeatures.history` flip) DENY; owner revoke OK (field-fenced); owner un-revoke DENY (revokedAt removal); expired `expiresAt` DENY; stranger read DENY; recipient read-own OK; recipient list-others DENY.
- **H-series (history):** owner write OK; forged key DENY; lat/lng/speed/bearing out-of-range DENY; recordedAt skew > 10 min DENY; authorized family + history GET/LIST 200; family without history flag 403; revoked recipient 403; expired retention record GET 403; expired retention LIST (no filter) 403; skew-buffer list OK; cross-user write DENY; recipient query outside own authorization DENY.
- **P-series (places):** owner CRUD OK; stranger DENY; radius/name bounds.
- **E-series (audit events):** owner write OK; coordinate field in payload DENY (hasOnly); stranger read DENY.
- **L-series (live dual path):** legacy path regression (12 original cases re-run unchanged) + new-path positive/negative.

## 5. Revocation workflow (master plan §6.3 — 8 steps, mapped)

| Step | Implementation |
|---|---|
| 1 Mark revoked on backend | Owner updates authz doc → `revokedAt: serverTimestamp()` (rules: owner-only, field-fenced) |
| 2 Reject associated submissions | Recipient **reads** denied by rules (§4.2/§4.3). The owner's own live/history writes continue (owner's data) — the sharer UI surfaces "sharing to N recipients" so the owner can stop in one tap |
| 3 Deny history requests | `revokedAt` check inside `locationAuthz` — evaluated **per request** (no cache) |
| 4 Notify recipient | No push in v1 (push = session asks only, decision record D2). Recipient's next open: honest not-found + one-time banner `hist_access_end` |
| 5 Cancel subscriptions | Existing listener-diffing drops the counterpart when authz/relationship predicate fails (pattern already verified) |
| 6 Remove cached data | No local history cache by design (memory-only ViewModel state); listener teardown clears it. Firestore offline persistence stays disabled for location data (verify at Phase 8 — implementation check) |
| 7 Update sharing UI | Status model flips to `STOPPED`/absent across cards |
| 8 Minimal audit event | Owner writes `access_revoked` event (metadata-only, §2.4) |

**Stale-authorization rule (master plan "network failure" clause):** all grants are evaluated server-side per request; there is no offline grant cache to go stale — a failed rules evaluation defaults to DENY.

## 6. Retention & deletion

1. **Query-time expiry:** `retentionExpiresAt` checked in rules on every get/list (§4.3) — expired records are invisible even before physical deletion ("do not rely only on hiding" is satisfied because deletion ALSO runs, below).
2. **Physical cleanup, §75-compliant (no server sweeps):** owner-device opportunistic delete — when the user opens Tracking/History (a user action), the repo issues one bounded `where('retentionExpiresAt','<=', now).limit(200)` delete batch; repeats per open until exhausted. Zero timer/background work; the kill-push worker never touches location collections (invariant from audit §4.5).
3. **User-initiated deletion:** scope-confirm dialog (day / all) → batched deletes → **verification re-query must return empty** → UI confirmed state. Offline → deletion queued as a local flag; executed on next app-open with network (honest `del_offline` message; never a false success).
4. **Honest disclosure (UX copy + privacy policy):** TTL-style expiry is not a guaranteed-to-the-second physical erase; provider backups may retain residual copies for a limited period — stated in `ret_note` string and the privacy policy section (Phase 14 sync).

## 7. Backend responsibilities — decision records

- **D1 — Enforcement engine: RULES-FIRST, no server compute in v1.** Everything in §4–§6 is enforceable by Firestore rules + owner-device actions, preserving §75 (zero idle traffic) and the Spark tier. Deferred: true server-side audit trail, recipient-facing alert dispatch, server-verified deletion — any of these would move to the **existing Cloudflare Worker** (already deployed infra, already env-secret managed) rather than new Cloud Functions; each requires its own quota case + Owner approval before implementation.
- **D2 — Push for location events: NOT in v1.** FCM remains session-ask-only. Recipient learns of revocation on next open (honest banner). Rationale: no new push channel, no worker queries over location collections, quota-neutral.
- **D3 — Place alerts: Firestore-backed (small, rare writes) over local-only**, to survive reinstall and match master plan §11.1; quota impact ≈ a handful of writes per edit (negligible). Local-only rejected as primary (data loss on reinstall).
- **D4 — Movement classification: pure-Kotlin, device-local, never stored server-side.** Raw speed/bearing stored (OS-provided) are facts; the classification label is derived at render time and labeled an estimate with honest confidence (UX spec §6).

## 8. Quota analysis (§75 posture preserved)

| Source | Writes/day worst-case | Reads/day | Idle traffic |
|---|---|---|---|
| liveLocations (existing) | ≤1,440 @60 s; realistically 300–600 (gates + back-off) | listener-driven ≈1 per fix per recipient | 0 (event-driven) |
| locationHistory | = accepted-history fixes (≥50 m apart) — bounded by live cadence ≈ 300–800 | one-shot date queries, ~50–200 docs **per user view only** | 0 |
| authorizations / audit / places | ~1 per consent action / edit | owner-only, on-view | 0 |
| **Total (2 active devices)** | **≈ 2.5–3.5 K vs 20 K/day Spark writes** | **< 2 K vs 50 K/day reads** | **0** |

No new listener classes beyond the existing per-counterpart live listeners; history is **always** one-shot get/list (user action). Worker unchanged. Battery: no new wakeups — history piggybacks on the existing FGS fix callbacks.

## 9. Offline & failure behavior (master plan §13)

- **Upload states** (UX spec §8): acquired → pending → ok / failed / unavailable; failed fixes are **not retried individually** (no retry storm — the next accepted fix supersedes; the live doc model makes retries pointless).
- **Reads while offline:** tracking UI shows last-received state with honest freshness labels (existing); history screen requires connectivity (one-shot queries fail → `ErrorPanel` retry, no partial-cache presentation).
- **Rules failure = deny** (default-closed everywhere; catch-all unchanged).
- **Reboot/GPS-off/battery-saver:** sharing does not auto-resume (existing honesty); history has gaps; gap rendering per UX spec §9.

## 10. Privacy & security checklist (to be re-verified at Phase 12 review)

☐ Notifications carry no coordinates (name-only alert text, existing FGS text unchanged) · ☐ audit events metadata-only (hasOnly-enforced) · ☐ no coordinates in `NotificationsLog` (existing) · ☐ no crash/analytics SDK ships location (none in build.gradle — verified in audit) · ☐ no local history cache · ☐ sign-out path stops FGS + deletes own live doc (existing) + flushes queued deletions best-effort · ☐ privacy-policy strings rewritten to match real behavior (audit drift A1, Phase 4) · ☐ retention disclosure includes backup caveat · ☐ rules probes 100 % PASS at gate · ☐ `hasOnly` blocks every forged-field vector listed in probe series.

---

*Phase 3 complete. Together with the audit (Phase 1) and UX specification (Phase 2), this document constitutes the review package for the Owner's approval. **No code has been changed and no data has been collected** — per the master plan, frontend implementation (Phase 4, mock data) begins only after approval.*
