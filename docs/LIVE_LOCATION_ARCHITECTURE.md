# LIVE_LOCATION_ARCHITECTURE — Consent-Based Family Location Sharing (Master-Plan M5)

> Status: **IMPLEMENTED — M5 shipped (v0.33.0 / versionCode 52, 2026-10-31).** The design
> below was executed as specified: engine + FGS (M5.a/b), Tracking tab map + detail route
> (M5.c), Settings partition + consent + i18n ×15 (M5.d), rules block **published live +
> runtime probe 12/12 PASS** (`scripts/rules-liveLocations-probe.mjs`), CI gate + TEST_PLAN
> location scenarios (M5.e). The original placeholder-era audit note is kept below for the
> record: at planning time the app had **zero** location code — no permissions, no library,
> no map (Agent 2-b audit); the "Live location" tab was a placeholder
> (`MainActivity.kt:3038-3046` then). Remaining honest limits: no background-location
> permission in v1 (§5), tile policy at family scale only (§8), no offline map packaging.

## 1. Repository & architecture assessment (why existing infra suffices)

- Firestore (db `default`) already carries presence (`devicePresence`, doc-id pattern
  `{uid}_{devIdHex}`, 20-min window) with counterpart-only reads enforced by rules
  (`myPairing()` = ACTIVE relationship + live guest window). Location reuses **exactly this
  pattern** — no new backend, no new database, no duplication.
- RTDB is deny-all/unused; signaling stays in Firestore. No new service is introduced.
- Free tier is the binding constraint: **50,000 reads/day, 20,000 writes/day (Spark plan)**
  — the design below is built around those numbers (§7). Not free at every scale (§8).

## 2. Recommended stack (open-source / free where practical)

| Layer | Choice | License / policy notes |
|---|---|---|
| Position fix | **FusedLocationProviderClient** (`com.google.android.gms:play-services-location:21.x`) | GMS present on Owner phones (Firebase already in use); fallback code path via `LocationManager` if GMS missing |
| Map | **osmdroid 6.1.x** (`org.osmdroid:osmdroid-android`) | Apache-2.0; **attribution mandatory** (OSM + tile source shown on map); `User-Agent` must identify the app; OSMF tile policy = moderate use OK, no bulk downloads; offline cache capped (~50 MB, user-clearable). Family-scale traffic (2–4 devices) is well within policy. At scale → switch tile provider (self-host / OpenFreeMap / paid) — documented, not assumed free forever |
| Transport | Firestore single-doc update-in-place | see §4/§7 |
| Foreground service | `LocationShareService`, FGS type `location` | Android 14 requires while-in-use grant + type declaration; persistent owner-visible notification (also satisfies "no hidden tracking") |

Rejected alternatives (documented): Google Maps SDK (tile/billing terms, not OSS),
MapLibre Native (fine, but heavier integration for one feature; may revisit if vector tiles
ever needed).

## 3. Data flow (text diagram)

```
[Recipient device]
  owner taps "Share location" (Tracking/Settings) → consent sheet (purpose + scope)
   → runtime perms FINE/COARSE (while-in-use) → start LocationShareService (FGS, notification)
   → FusedLocation updates → gates: Δt ≥ interval AND Δd ≥ 25 m AND accuracy ≤ 100 m
   → set() merge into liveLocations/{uid}_{devId}  (1 write per accepted fix)

[Family device / map]
  listener on counterpart's single doc (rule: ACTIVE relationship + sharingEnabled)
   → marker update; freshness = f(updatedAt): LIVE (<2×interval) / STALE (>5 min) / OFFLINE
   ("last known — HH:MM", never labeled live)

[Owner stop / revoke]
  owner Stop toggle → service stops + doc cleared; relationship revoke → rules deny + doc delete
```

## 4. Data model & access-control rules (new match block; catch-all deny stays)

```
liveLocations/{docId}   docId = {uid}_[0-9a-f]{24}   (presence pattern)
fields: lat (−90..90), lng (−180..180), accuracyMeters (int ≤ 100000),
        sharingEnabled (bool), updatedAt (timestamp), appVersionCode (int)
        — fixed field set (hasOnly), size caps per Phase 18.8 convention
read:   signedIn AND counterpart relationship ACTIVE (guest: live window)
        AND resource.data.sharingEnabled == true
write:  request.auth.uid == uid-of-docId AND hasOnly(...) AND range checks
        AND updatedAt within ±5 min of requestTime   (prevents stale forgery)
delete: owner (stop/unpair/sign-out cleanup)
```

- Sweep safety: kill-push tiers only query `supportSessions` — `liveLocations` can never be
  swept (verified in worker/probe code, Agent 2-b). A rules probe
  (`scripts/rules-liveLocations-probe.mjs`, Agent 22) will prove: deny-before-pairing,
  deny-when-sharing-off, deny-revoked, deny-guest-after-window, allow-ACTIVE-counterpart,
  deny-forged-fields.
- **No history**: one mutable doc per device, overwritten in place. Continuous history is
  explicitly NOT implemented (master PART 6). Retention = last position only; deleted on
  stop/unpair/sign-out.

## 5. Android permission & background plan (summary — full matrix in ANDROID_PERMISSION_MATRIX.md)

- Request `ACCESS_FINE_LOCATION` + `ACCESS_COARSE_LOCATION` **while-in-use** with a
  purpose explanation *before* the system dialog.
- v1 does **NOT** request `ACCESS_BACKGROUND_LOCATION`. Sharing runs in a `location`-type FGS
  started while the app is visible (Android 14-compliant). Honest limitation: on aggressive
  OEMs, swipe-away can kill sharing; the app offers the existing battery-optimization
  exemption prompt (PermissionWizard pattern) with a clear explanation — never promises
  uninterrupted background updates.
- Persistent FGS notification ("Location sharing on — tap to stop", localized ×15, no
  coordinates in the notification). Notification appears even when sharing started silently
  after reboot — i.e., **no stealth operation**; reboot-resume is deliberately NOT auto-restart
  in v1 (owner re-toggles).
- Stale/absent handling: never show an old coordinate as live (badge + timestamp + accuracy
  always visible); "never shared" empty state; permission-denied state explains the enable path.

## 6. Update strategy (adaptive, configurable)

| Situation | Cadence | Distance gate |
|---|---|---|
| Map open (foreground viewer) | 30 s | 25 m |
| FGS background sharing | 60 s | 25 m |
| Stationary (≤25 m over last 3 fixes) | back off ×2 up to 5 min | — |
| Offline / no fix | no writes; doc keeps last state + updatedAt | — |

Owner-configurable interval in Settings: 30 s / 1 min / 5 min (bounded by quota budget §7).
Accuracy vs battery trade-off documented; `PRIORITY_BALANCED_POWER_ACCURACY` default,
`HIGH_ACCURACY` only while a session/map is active.

## 7. Quota budget (Firestore Spark: 50 K reads / 20 K writes per day)

| Consumer | Worst case / day |
|---|---|
| Kill-push sweeps (post-Phase-20): sandbox 30 s + Worker 1,440 + Actions | ≈ 6.2 K reads |
| App baseline listeners (relationships/sessions/presence/config, measured §63) | ≈ 5–10 K reads |
| Location writes: 2 sharing devices × 60 s | ≤ 2,880 writes |
| Location writes worst: 2 × 30 s + backoff-offs | ≤ 5,760 writes |
| Counterpart reads (listener snapshots ≈ writes) × 2 pairs | ≤ 5,760 reads |
| **Total** | **≈ 12–16 K reads, ≤ 9 K writes → safely inside free tier** |

Guardrails: doc-count bounded (one per device), no append-only, sweeps never touch the
collection, cadence caps enforced client-side (rules cannot rate-limit — Agent 2-b).

## 8. Cost & scaling honesty

Free tier covers this family-scale design. Costs appear only if: billing enabled by choice,
tile traffic becomes heavy (own tile hosting), Firestore exceeds free quotas at many-device
scale, or FCM/Worker tiering changes. Statement required by the master prompt: **nothing here
is guaranteed free at every scale**; the design keeps every knob (cadence, gates, doc model)
to shrink usage before money is needed.

## 9. Security & privacy threat assessment

| Threat | Mitigation |
|---|---|
| Stranger reads location | rules: counterpart-of-ACTIVE-relationship only + sharingEnabled; catch-all deny; probe-verified |
| Ex-guest reads after window | guest read requires live window (same `myPairing()` logic) — deny + probe case |
| Revoked device keeps reading | REVOKED ⇒ rules deny immediately; owner's stop deletes doc |
| Forged/stale coordinates by client | field set + ranges + updatedAt freshness precondition |
| Silent tracking | FGS notification always visible; owner stop in 2 places (Tracking tab, Settings); sharing toggle is explicit |
| Coordinate leakage via notifications | coordinates never in notifications (lock-screen safe) |
| QR alone grants location | pairing ≠ authorization: sharing is a separate owner toggle (FAMILY_GUEST_AUTHORIZATION.md) |

## 10. Implementation steps & files (M5, after approval)

| Step | Content | Files |
|---|---|---|
| M5.a | deps + manifest (FINE/COARSE, FOREGROUND_SERVICE_LOCATION, FGS type) + rules block + publish + probe | `app/build.gradle.kts`, `AndroidManifest.xml`, `firestore.rules`, `scripts/rules-liveLocations-probe.mjs` |
| M5.b | engine: repo + FGS + gates + stop/cleanup | new `LiveLocationRepo.kt`, `LocationShareService.kt` |
| M5.c | Tracking tab: osmdroid map, markers, selector sheet, freshness badges, states | new `ui/tracking/TrackingTab.kt` (+ViewModel) |
| M5.d | owner controls + Settings section + consent sheet + i18n keys ×15 (+≈20 keys) | settings extracts, `strings.xml`×15 |
| M5.e | tests/probes/CI + TEST_PLAN location scenarios + docs | TEST_PLAN.md, README, worklog |

Version at first M5 release: **v0.30.0 / versionCode 49** (or later if bundled with M2–M4).
Acceptance criteria: all probe cases pass; quota budget respected on a live 2-device day;
freshness badges proven stale/offline by clock tampering; revoke cuts access instantly;
Owner's physical test script passes on both phones.
