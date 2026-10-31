# Location Feature Audit — Remote Family Support

**Status:** COMPLETE (Master Prompt Phase 1) · **Audited baseline:** v0.37.0 (versionCode 56) · 2026-10-31
**Scope:** Everything location-related that exists today — engine, UI, authorization model, Firestore rules, permissions, background behavior, notifications — as the factual baseline for the *Consent-Based Live Location, Route History & Movement Insights* master plan.
**Method:** Full-source audit of the 41 Kotlin files, `firestore.rules`, `AndroidManifest.xml`, `app/build.gradle.kts`, and 6 existing docs, cross-checked by two independent research passes. **Zero code changes were made in this phase** (per master plan: audit first, no rewrites).

---

## 1. Existing implementation summary

Live location sharing shipped as milestone **M5 (v0.33.0/52)** and is production-current through v0.37.0. What exists today is a **consent-based, single-document, no-history live location system**:

| Aspect | What exists today (facts) |
|---|---|
| Location source | `FusedLocationProviderClient` (`PRIORITY_BALANCED_POWER_ACCURACY`) with `LocationManager` (GPS + NETWORK) fallback for de-Googled phones; fallback is one-way (never ping-pongs back) |
| Engine | `LocationShareService` — started FGS, `FOREGROUND_SERVICE_TYPE_LOCATION`, `START_NOT_STICKY` (no auto-restart after swipe/reboot, deliberate) |
| Update gates (client) | Fix accepted only if: accuracy ≤ 100 m AND Δt ≥ configured interval AND Δdistance ≥ 25 m (haversine) — stationary back-off doubles interval up to 5-min cap |
| Intervals | Owner-configurable 30 s / 60 s / 5 min (Settings radio dialog) |
| Data model | **One mutable doc per device:** `liveLocations/{uid}_{deviceId}` — `set(merge)` update-in-place, **deleted on stop**; fields: `uid, deviceId, lat, lng, accuracyMeters, sharingEnabled, updatedAt (serverTimestamp), appVersionCode` — no speed/bearing/altitude |
| History | **None — by explicit design** ("master plan PART 6" comments in code). No route, polyline, waypoint, trip, geofence, activity-recognition, or place-alert code exists anywhere (grep-verified) |
| Consent UX | Purpose ModalBottomSheet (`loc_share_title/body`) → runtime FINE+COARSE request → `OwnSharingCard` one-tap on/off; denial → `PermissionDeniedPanel` with settings hand-off |
| Recipients | Not selectable — sharing is with **the paired counterpart of an ACTIVE relationship** (family: until revoked; guest: while `guestExpiresAt > now`, ≤3 h server-bounded) |
| Reader UI | Tracking tab: osmdroid 6.1.18 MAPNIK map (320 dp, CopyrightOverlay, pinch zoom, no zoom buttons), one marker per publishing counterpart, freshness LIVE < 2 min / STALE < 5 min / OFFLINE (reader clock vs server `updatedAt`); `tracking/{relId}` detail screen with accuracy + "Open in maps app" (`geo:` intent) |
| Rules | `liveLocations` block in `firestore.rules` (lines 590–644): write = signed-in owner only + docId pattern + field `hasOnly` + lat/lng ranges + accuracy 0–100000 + `updatedAt` within ±5 min of `request.time`; read = own doc OR `myPairing()` (ACTIVE relationship, guest window) AND `sharingEnabled == true`; delete = owner only. Live probe: `scripts/rules-liveLocations-probe.mjs`, **12/12 PASS** (run live 2026-10-31) |
| Revocation today | `revokeRelationship` → relationship status `REVOKED` → rules deny counterpart reads **immediately**. The `liveLocations` doc itself is NOT deleted and the sharer's FGS keeps writing (owner-only visibility) — see §6 |
| FCM for location | **None.** FCM is used only for support-session asks (`supportSessions`); kill-push worker queries only `supportSessions` (grep-verified, zero `liveLocations` references) |
| Notification | FGS tray notification "Location sharing is on" — channel `location_sharing` (LOW), **never contains coordinates**; local `NotificationsLog` (filesDir JSON, cap 100, cleared at sign-out) records `sharing_started/stopped` events only |
| Quota posture | §75 directive: zero timer-driven reads/writes; listeners are event-driven; writes gated per accepted fix; with both apps closed, Firestore traffic = 0. Measured app-side load ≈ 250–400 reads/day (<1 % of the 50 K Spark tier) |

## 2. Reusable components

These are **directly reusable** and should be extended, not replaced:

1. **`LocationShareService`** — FGS lifecycle with all three Android 14 permission-revocation guards (v0.37.0), type-0 `startForeground` fallback, coordinate-free notification, stationary back-off, §3 write gates.
2. **`LiveLocationRepo`** — write/read/delete patterns with `awaitWithTimeout`, `serverTimestamp`, `BuildConfig.VERSION_CODE` stamping; the sole owner of location Firestore access (no location code leaks in SessionRepo/PairingRepo — verified).
3. **Rules pattern `myPairing()` + live probe harness** — the proven authorization primitive (relationship + guest-window + `request.time` checks) and the `rules-*-probe.mjs` live-test method to clone for every new collection.
4. **Consent sheet + permission UX** — `OwnSharingCard`, purpose ModalBottomSheet, `RequestMultiplePermissions` (FINE+COARSE, either grant suffices), `PermissionDeniedPanel`.
5. **`TrackingViewModel` listener-diffing** — relationship listeners → one `watchLiveLocation` listener per authorized counterpart; predicate `isTrackableCounterpart`; listeners survive tab switch (saveState) with 0 idle reads.
6. **Canonical UI states** — routed `ErrorPanel` / `LoadingSkeleton` / `EmptyState` / `TickingBlock(30 s)` re-composition; all four already wired in TrackingTab.
7. **Design system** — `SectionCard`, `AppListTile`, `StatusBadge`, `PresenceBadge`, `BigButton`/`WideOutlinedButton`, `BackTitleRow`, spacing/shape/touch-target tokens (≥48 dp), full dark scheme.
8. **Localization** — Android resources only (no hard-coded UI text), **15 languages with full Bengali parity** (533/529 keys); `LangPrefBus` + `attachBaseContext` wrap.
9. **Freshness policy** — LIVE/STALE/OFFLINE thresholds (code: <2 min / <5 min) and the reader-clock-vs-server-timestamp pattern.
10. **Honesty conventions** — "Last known — HH:mm · ±N m" marker snippets, `loc_never` for never-shared, revoke → honest not-found (never stale data presented as current).

## 3. Missing components (gap to the master-plan target)

| # | Master-plan requirement | Status today |
|---|---|---|
| 1 | Route History (collection, date selector, polyline rendering, data-gap handling, summaries, deletion) | **Absent** — no storage, no UI, no rules |
| 2 | Movement Insights (speed validation, conservative activity classification, uncertainty labels) | **Absent** — `LiveLocationView` carries no speed/bearing; no `ACTIVITY_RECOGNITION` permission |
| 3 | Place Alerts (saved places, arrival/departure, cooldown, owner-informed) | **Absent** — no geofencing code |
| 4 | Independent location authorization entity (`locationAuthorizations` with `enabledFeatures`, `historyAccessEnabled`, `retentionPolicy`, `expiresAt`, `revokedAt`, version) | **Absent** — authorization is *implied* by (relationship ACTIVE + `sharingEnabled` flag); recipients are not selectable, scopes do not exist |
| 5 | Sharing scope options (once / temporary / until-stopped / with-history) | **Absent** — only "until stopped" |
| 6 | History retention periods + backend enforcement | **Absent** — nothing retained |
| 7 | Server-side security-audit events (`locationAuditEvents`) | **Absent** — only local `NotificationsLog` (never uploaded) |
| 8 | Trusted-backend operations beyond rules (retention cleanup, alert dispatch, deletion verification) | **Absent** — Cloudflare Worker handles session kill-push only |
| 9 | Reverse geocoding (approximate address) | **Absent** — by design so far; pin remains usable without it (already true) |
| 10 | Upload-state separation (acquired / pending / successful / unavailable) | **Partial** — service swallows offline write failures silently; UI shows doc state, not upload state |
| 11 | Accuracy circle / map zoom buttons / route polyline overlay | **Absent** — accuracy shown as text only; pinch zoom only |
| 12 | Recipient notification when access ends | **Absent** — recipient UI falls to not-found on next read |

## 4. Compatibility risks

1. **De-Googled devices** — Fused unavailable (platform fallback exists and works); but Geofencing API and Activity Recognition APIs are Play-services-dependent → Phase 11/10 need a fallback strategy (see §8).
2. **Android 14+ FGS contract** — handled since v0.37.0 (three-layer guard, 0 crash paths known); any new long-running collection must reuse the same pattern. `targetSdk 34`; future 35 bump will need re-review of FGS policies (no impact on current location-type usage).
3. **No `ACCESS_BACKGROUND_LOCATION` (deliberate, v1)** — location updates only continue while the location-type FGS runs and while-in-use permission holds; OEM battery managers may kill the FGS on some devices (documented honest limitation in ANDROID_PERMISSION_MATRIX §3). Route History will therefore have **gaps whenever the FGS is not running** — the UX must display gaps honestly (master plan §7.2 forbids fabricating continuity).
4. **osmdroid view state is not saveable** — zoom/center reset on tab re-entry/rotation (accepted limit, RELEASE_NOTES §7.3). Route-history browsing must re-fit per composition (same pattern as today's `MapSyncState`).
5. **Firestore Spark-tier quota + §75 directive** — history multiplies writes (each retained point = 1 write). At the current 60 s cadence a fully-active day ≈ 1.4 K–2.9 K points/day/stream; with retention this is still inside tier, but **the §75 posture (zero idle traffic, on-demand only) must be preserved** — no timer-driven sweeps, no worker polling of location collections (the kill-push worker must never gain `liveLocations`/history queries).
6. **Rules cannot rate-limit** — all throttling stays client-side (existing §3 gates). For history, per-record validation replaces per-write throttling as the rules-side control.
7. **Reboot / process death** — `START_NOT_STICKY` means sharing does not resume after reboot or force-stop (deliberate honesty). History continuity across reboots requires the user to re-enable sharing; UI must not imply otherwise.
8. **Guest expiry is lazy** (whichever app notices first flips ACTIVE→EXPIRED). Rules already compensate by comparing `guestExpiresAt` against `request.time`; **all new collections must use the same `request.time`-based window checks**, never status alone.

## 5. Required dependency changes

**Phase 4 (frontend, mock data): none.** Everything needed (osmdroid `Polyline`, Compose components, resources) is already present.

Later phases (each needs an explicit decision record before adding):
- **Route history:** no new dependency (Firestore + osmdroid `Polyline` overlay suffice).
- **Movement Insights:** `com.google.android.gms:play-services-location:21.3.0` already bundles activity-recognition access, but the **`ACTIVITY_RECOGNITION` runtime permission** must be added to the manifest + matrix, requested contextually, with a no-Play-services fallback (speed-from-points classification only, honestly labeled "Limited").
- **Place Alerts (v1 = self-alerts on the owner's device):** Play-services Geofencing API (in the existing dependency) + `ACCESS_BACKGROUND_LOCATION` decision (v1 self-alerts can work while-in-use + FGS; background geofencing without that permission is unreliable → scope v1 to "while sharing/FGS is on" or defer; see UX spec §7).
- **Trusted backend (retention/alert dispatch, if server-side ops are chosen):** existing Cloudflare Worker vs. Firebase Cloud Functions — decision record required (quota + §75 posture favor minimal, event-driven designs; see design doc §6).

Current coordinates (facts): `play-services-location 21.3.0`, `osmdroid-android 6.1.18`, `firebase-bom 33.4.0`, `compileSdk/targetSdk 34`, `minSdk 26`.

## 6. Security concerns (current state + risks for the new feature)

1. **Revoke does not delete the `liveLocations` doc nor stop the sharer's FGS.** Rules deny counterpart reads instantly (probe N3 PASS), and only the owner can read their own doc — so privacy holds — but the doc lingers (owner-visible) until the owner stops sharing, service dies, or sign-out (`MainActivity` does `stopLocationSharing` + `deleteOwnLocation`). The architecture doc §3 claims "revoke → rules deny + doc delete"; the delete half is aspiration, not code. **Fix direction:** either implement delete-on-revoke (trusted op) or correct the doc; the new `locationAuthorizations` model makes this explicit (`revokedAt` + rules re-check per request).
2. **`strings.xml` `privacy_body` still says "Location. The Live location tab is a placeholder — nothing is collected today"** — placeholder-era text contradicting shipped M5, **user-visible in Settings → Privacy**. Master plan §19 requires privacy disclosures match actual behavior → **must be corrected in the Phase 4 frontend wave** (string-only fix).
3. **Client-side throttling only** (rules cannot rate-limit) — acceptable for live doc (bounded writes); for history, per-record field validation + owner-only writes + size caps are the rules-side controls (same as today's `hasOnly` + ranges pattern).
4. **Never trust client-provided identity for writes** — current rules already require `request.resource.data.uid == request.auth.uid` + docId pattern `auth.uid_[0-9a-f]{24}`; every new collection must keep this invariant.
5. **No coordinates in logs/notifications/crash reports** — currently true (notification text, `NotificationsLog`, and event strings carry none). Audit events for the new feature must record **metadata only** (who/when/what-kind), never location copies.
6. **Revoked-recipient cache** — listeners are removed when the relationship predicate drops the counterpart (verified), keep this invariant when adding history listeners.
7. **`appVersionCode` stamping** exists on the live doc — extend to history records for abuse triage.

## 7. Background-location constraints (Android reality, honestly stated)

- **No background permission in v1 (deliberate).** Updates continue only while the location-type FGS runs with while-in-use permission. Android 14 killed the "start FGS from background" path — sharing is started while visible, which the app respects.
- **FGS can be killed** by OEM battery managers (Mi/Oppo/Vivo etc.), Doze, or user swipe (START_NOT_STICKY). The reader-side LIVE/STALE/OFFLINE labels are the honest state surface; the sharing side has no "upload pending" indicator yet (gap #10).
- **Battery saver** reduces location accuracy/frequency at OS level — the app does not detect it explicitly; effects surface as larger `accuracyMeters` and slower updates. UX spec adds honest copy for this (master plan: inform users about these limits).
- **Reboot:** sharing off (no auto-resume). **GPS off:** Fused/platform requests simply stop delivering fixes; doc goes stale → reader shows STALE then OFFLINE. **Permission revoked mid-run:** service self-stops via the three-layer guard (v0.37.0).
- **Stationary back-off (×2 → 5-min cap)** already implements the master plan's "reduce frequency when stationary" guidance.

## 8. Recommended architecture

**Preserve the existing app shape** (single module, single Activity, 4-tab nav — Home / Add Device / Tracking / Settings; integrate into Tracking per master plan §3). The project's flat file layout (repos at package root, `ui/<feature>/` screens) maps to the master plan's suggested `feature/tracking/` packages as follows — **adapted, not rewritten**:

```
com.family.remote.support/
├─ LocationShareService.kt        (KEEP — live engine, extend with upload-state)
├─ LiveLocationRepo.kt            (KEEP — live doc read/write)
├─ LocationHistoryRepo.kt         (NEW — history write/read/delete, retention queries)
├─ PlaceAlertRepo.kt              (NEW — saved places + alert configs)
├─ LocationAuthzRepo.kt           (NEW — locationAuthorizations CRUD; recipient scoping)
├─ MovementClassifier.kt          (NEW — pure Kotlin, unit-testable, no Android dep)
├─ JourneySummarizer.kt           (NEW — pure Kotlin: distance/duration/coverage)
└─ ui/tracking/
   ├─ TrackingTab.kt              (EXTEND — dashboard: member cards + map + own sharing)
   ├─ TrackingViewModel.kt        (EXTEND — authorization-aware data owner)
   ├─ LocationDetailScreen.kt     (EXTEND — sections A–E, authorization-gated)
   ├─ HistoryScreen.kt            (NEW — date selector + route map + summary)
   ├─ SharingSetupSheet.kt        (NEW — 6-step consent wizard)
   ├─ PlaceAlertsSheet.kt         (NEW — places + alert config)
   └─ components/                 (NEW — reusable: status cards, date selector, gap legend)
```

Data-side principles (detailed in `LOCATION_DATA_AND_SECURITY_DESIGN.md`):
1. **Keep `liveLocations` exactly as-is** (bounded, quota-safe, probe-verified) — history is a separate opt-in collection; live doc stays the deletion-simple current-state record.
2. **Add `locationAuthorizations/{authorizationId}`** as the *only* source of truth for "who may see what" beyond the live doc (recipient-scoped, feature-scoped, expiry, revocation) — pairing (relationship) remains necessary-but-not-sufficient, exactly as FAMILY_GUEST_AUTHORIZATION.md already states ("pairing creates a relationship — never a blanket authorization").
3. **History path owner-scoped** — final shape decided in the design doc (rules ergonomics + query cost); every read gated by authorization doc + `retentionExpiresAt > request.time`.
4. **Rules-first enforcement** (the proven `myPairing` + probe pattern); trusted-worker ops only where rules genuinely cannot (owner-side retention cleanup can run on-device at write time; server sweeps violate §75 posture — see design doc §6 decision record).
5. **Movement classification is device-local and pure-Kotlin** (speed plausibility + point-to-point deltas; optional ACTIVITY_RECOGNITION enrichment, contextually requested, honest fallback).
6. **Place Alerts v1 = self-alerts on the owner's device** (the master plan's own examples are first-person: "notify me when *I* arrive"); local geofencing + local notification, zero server dispatch, owner-informed by definition. Recipient-facing alerts = documented future extension (needs push + worker + quota case).

## 9. Implementation plan

Master plan phases mapped to this repo's milestone convention; **every phase ends with the CI compile gate + probe gates as applicable** (readback-verify push pipeline; §75 posture throughout — no new background traffic):

| Phase | Deliverable | Gate |
|---|---|---|
| 1 Audit | `docs/location/LOCATION_FEATURE_AUDIT.md` (this doc) | Owner review |
| 2 UX spec | `LOCATION_UX_SPECIFICATION.md` | Owner review |
| 3 Data/security design | `LOCATION_DATA_AND_SECURITY_DESIGN.md` | Owner review |
| 4 Frontend (mock) | All screens navigable with clearly-labeled DEMO data; **fix `privacy_body` drift string**; orphaned `loc_perm_*` strings removed | Phase 5 review gate |
| 5 Frontend review | Screens + known limits + accessibility findings presented | **Owner approval — required before backend** |
| 6 Permissions | Contextual ACTIVITY_RECOGNITION / background-location decision records + matrix update + wizard integration | Matrix doc + probes |
| 7 Location engine | History-aware `LocationShareService` (upload-state surfaced, history enqueue when authorized), freshness tests | Unit tests + CI |
| 8 Backend integration | `locationAuthorizations` + history rules + extended probe suite (~30 cases) | **Probe suite 100 % PASS (live)** |
| 9 Route history | History UI + retention + deletion workflows | Authorization tests |
| 10 Movement insights | `MovementClassifier` + UI with uncertainty labels | Unit tests (incl. impossible-speed rejection) |
| 11 Place alerts | Self-alert geofencing + config UI (scope per decision record) | Device test |
| 12 Security review | `LOCATION_SECURITY_REVIEW.md` | Owner review |
| 13 Performance | `LOCATION_PERFORMANCE_REPORT.md` (measured, not claimed) | Owner review |
| 14 Release readiness | `LOCATION_RELEASE_CHECKLIST.md` + Data-safety/privacy-policy sync | Final gate |

## 10. Testing plan

Extend the existing harness (no new frameworks — the repo has no Kotlin test infra yet; rules probes are the proven live-test method):

1. **Unit (pure Kotlin, new):** freshness thresholds; haversine + `MIN_MOVE_METERS` gate; `MovementClassifier` (impossible-speed rejection, stale-input rejection, uncertainty labels); `JourneySummarizer` (distance approximation, gap detection, coverage warnings); retention-expiry arithmetic.
2. **Rules probes (live, Node — clone `rules-liveLocations-probe.mjs`):** authorization matrix — family+history granted (200), family without `historyAccessEnabled` (403), guest in/out of window, revoked authorization reuse, expired-record reads, out-of-scope date-range queries, forged fields (`hasOnly` violations), cross-user writes, `retentionExpiresAt` tampering. Target ≥ 30 cases, 100 % PASS required at Phase 8 gate.
3. **Integration (Owner physical checklist — extends TEST_PLAN §2 rows 10–18):** screen off, internet loss, battery saver, permission revoke mid-share, reboot, OEM battery-killer device, GPS off, notification-permission denied.
4. **UI review (Phase 5):** every state in the UX spec rendered (including all 7 sharing statuses, gap rendering, demo-data banner); Bengali layout verified; accessibility (no color-only status, ≥48 dp targets, screen-reader labels on map controls).
5. **Security tests:** all §17.5 master-plan attempts must be denied — encoded as probe N-cases above.

---

## Appendix A — Documentation drift found during this audit (facts, to fix in Phase 4)

| # | Location | Drift | Fix |
|---|---|---|---|
| A1 | `res/values/strings.xml:162` `privacy_body` | Says location tab is a placeholder, "nothing is collected today" — false since M5; **user-visible in Settings → Privacy** | Rewrite to match actual behavior (live-only, no history, consent-based) |
| A2 | `LIVE_LOCATION_ARCHITECTURE.md` §3 | Claims revoke deletes the location doc; code only denies reads | Correct doc (or implement in Phase 8) |
| A3 | `PROJECT_CONTEXT.md` §6 | `liveLocations` row still labeled "planned (M5)" | Label as shipped |
| A4 | `ANDROID_PERMISSION_MATRIX.md` header | Still "PLANNING" though M5 shipped | Update status header |
| A5 | `LIVE_LOCATION_ARCHITECTURE.md` freshness | "<2× interval" phrasing vs code's fixed 2 min/5 min thresholds | Align doc to code values |
| A6 | Orphaned strings | `loc_perm_title/body`, `loc_coming_title/body`, `ph_two_kinds_info`, `ph_denied_short` unreferenced | Delete in Phase 4 (dead-code rule) |

*Audit complete. No code was changed. Next: Phase 2 (`LOCATION_UX_SPECIFICATION.md`) and Phase 3 (`LOCATION_DATA_AND_SECURITY_DESIGN.md`) — both delivered alongside this document.*
