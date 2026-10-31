# PROJECT_CONTEXT — Remote Family Support & Device Assistance Platform

> Master-plan shared-context document (required by the coordinated multi-agent workflow).
> **Status: IMPLEMENTATION (Phase 22 / master-plan M5 shipped 2026-10-31 — Live Location:
> engine + FGS + osmdroid Tracking tab + tracking/{relId} + Settings Location partition;
> liveLocations rules LIVE + probe 12/12; the Owner drives each milestone with an explicit
> command).**
> Created: 2026-10-31 (Asia/Dhaka) · Build now: **v0.33.0 (versionCode 52)**
> Repo: `psfaruk/family-remote-support` (public, branch `main`, sole committer pipeline: Contents-API push + readback-verify)

---

## 1. Project purpose & current state

Two-phone Android app: a trusted family member ("helper") remotely supports another family
member's phone ("recipient") — always with the receiver's explicit, on-screen permission.
Two access modes: **Family Mode** (persistent trusted-device pairing) and **Guest Mode**
(temporary, server-bounded sessions). Free-tier only (Firebase Spark plan) — cost discipline
is a hard design constraint.

| Item | Verified state |
|---|---|
| Phases completed | 0–14.1, 15, 16 (4-tab shell restructure), 17, 18.1–18.9 (deep-audit series), 19.1–19.2 (full i18n), 20 (Firestore quota incident fix), 21 (M0–M4), 22 (M5 Live Location) |
| Master-plan milestones | M0 audit · M1 docs · M2 shell · M3 screen extraction + ViewModel · M4 nested Home subtree + Settings 8 partitions · **M5 Live Location (v0.33.0/52)** — all shipped 2026-10-31 |
| Current build | v0.33.0 / versionCode 52 (CI AXML-gated) |
| Live location | ✅ **shipped M5**: consent sheet + FGS engine + osmdroid map + freshness states + detail route; rules LIVE + probe 12/12 (no background permission in v1; no history by design) |
| Owner pending | (1) install v0.33.0/52 on both phones after CI SUCCESS (TEST_PLAN #10–#15 + visual pass); (2) set Cloudflare `FIREBASE_SA_JSON` secret (permanent kill-push tier still inactive) |

## 2. Repository structure

```
/                         repo root (maps to sandbox /home/z/my-project/android-app)
├── .github/workflows/    android-build.yml (build+AXML gate) · kill-push.yml (5-min cron backstop)
├── app/                  single module: build.gradle.kts, google-services.json, src/main/…
│   └── src/main/java/com/family/remote/support/   20 Kotlin files, 12,526 lines total
├── cloudflare/           killpush-worker.js (Cloudflare Worker, 1-min cron kill-push tier)
├── docs/                 PHASE-0.md (living tracker) · UI_UX_DESIGN_SYSTEM.md · CLOUDFLARE_SETUP.md · (this doc set)
├── scripts/              publish-firestore-rules.mjs · rules-*-probe.mjs · fcm-kill-push.mjs · verify-apk-axml.mjs · set-quality-config.mjs
├── firestore.rules       596 lines, least-privilege, catch-all deny
├── wrangler.toml         Worker config (cron * * * * *)
├── README.md             status block + build/tech notes
└── worklog.md            §1–§64 append-only ops diary (Bengali)
```

## 3. Frontend architecture (as-is — Agent 2-a audit)

- **Single Activity (`MainActivity.kt`, 5,884 lines) + Jetpack Compose + Material 3.**
  No ViewModel layer, no Navigation-Compose, no DI. Navigation = manual state machine:
  `var tab by rememberSaveable` (`MainActivity.kt:763`) + `when(tab)` + boolean overlay flags
  (`showWizard`, `showPairSheet`, `showDoc`) + `SessionUiBus` singleton.
- **Bottom nav = 4 M3 items** (`AppBottomNav`, `MainActivity.kt:1304-1331`): 0 Home · 1 Phone ·
  2 Live location · 3 Settings. Top bar (`AppTopBar`): app title + presence chip + notification
  bell (badge on pending asks; bell currently routes to Settings, not a notifications screen).
- Auth gate: `ui/AuthGate.kt` full-screen login/sign-up/reset when signed out.
- Nested surfaces are overlays/bottom-sheets/dialogs, not routes (pairing sheet, QR scanner
  dialog, role-preview sheet, device detail panel, consent modal, privacy DocOverlay,
  fullscreen session viewer).
- **Theme (`ui/Theme.kt`)**: two hand-authored M3 schemes (light primary `#246BFD`, dark bg
  `#0B1220` — the exact master-plan palette), `SeniorTypography`, `AppShapes` (8/10/16/20/22dp),
  `Spacing` object, `ThemePrefBus` (system/light/dark, persisted). No dynamic color.
- **i18n (`ui/Lang.kt`)**: `LangPrefBus`, 15 languages (bn/en/ar/de/es/fr/hi/id/ja/ne/pt/ru/tr/ur/zh),
  persisted `SharedPreferences`, `attachBaseContext` wrap, Activity recreate on change.
  `strings.xml` ≈374–377 keys × 15 (M2: +16 — nav rename ×2 + notifications ×14; 2 legacy nav
  keys removed). Residual user-visible hardcoded Bengali ≈ **15 lines**
  (ScreenShareEngine assist-ack toasts, SessionRepo status lines, 2 AuthGate a11y labels;
  the top-bar title hardcoded line is FIXED in M2); ~3 keys missing translation per locale;
  **M2 discovery: ~203 legacy keys are default↔bn inverted** (Bengali in `values/`, English in
  `values-bn/`) — recorded, fix = M4/M8 i18n wave.
- Reusable composables (`SectionCard`, `AppListTile`, `SettingsTile`, `EmptyState`,
  `IconStatusTile`, `QuickActionTile`, `BigButton`, `StatusBadge`, …) are **file-private** in
  MainActivity — no cross-file component library yet.

## 4. Backend architecture (as-is — Agent 2-b audit)

- **Firestore** (project `remote-family-support`, database id `default`) is the only database.
  RTDB exists but is **deny-all and unused**. Collections:
  `users/{uid}` · `users/{uid}/devices` · `pairingInvitations` · `trustedDeviceRelationships` ·
  `devicePresence` · `fcmTokens` · `supportSessions` · `supportSessions/{id}/signal` · `config/{turn,quality}`.
- **Security rules** (`firestore.rules`, 596 lines): least-privilege per path; single-use
  invitation redeem with server-side token proof (`claimToken == stored raw token`, sha256
  hash stored, 10-min TTL, 2-min claim freshness); relationship kind frozen (guest≠family);
  guest window server-bounded ≤3 h; session FSM with deadlines (`REQUESTED→ACTIVE/DENIED/
  CANCELLED/EXPIRED`, `ACTIVE→ENDED`, 20-min cap); signal subcollection per-type payload caps;
  **catch-all deny** at the end. No rate limiting possible in rules — cadence discipline is client-side.
- **Kill-push (missed-notification rescue), 3 tiers**: ① sandbox mini-service (30 s poll,
  quota-aware 429 backoff after Phase 20); ② Cloudflare Worker `killpush-worker.js` (cron
  `* * * * *`, claim-before-send via updateTime precondition, FCM send, zombie EXPIRE/ENDED
  sweeps) — **inactive until Owner sets `FIREBASE_SA_JSON`**; ③ GitHub Actions `kill-push.yml`
  (5-min cron, sporadic). Sweep only queries `supportSessions` — safe for new collections.
- **FCM**: `PushTokenService` → `fcmTokens` registry (rotation-safe); `SessionNotifier`
  posts high-priority ask notifications (foreground-suppressed, deduped, localized).
- **WebRTC**: `stream-webrtc-android 1.3.8`; signaling over Firestore subcollection
  `supportSessions/{id}/signal` (offer/answer/ice/watch/bye/cmd/cmdack, epoch replay-guard,
  per-reader watch, self-wipe). TURN/quality runtime config via `config/turn|quality` (SA-written).

## 5. Authentication & authorization flows

- Google Sign-In via **Credential Manager** (`GetGoogleIdOption`, web client id in BuildConfig)
  + email/password sign-up, sign-in, password reset (`ui/AuthGate.kt`).
- No account-deletion flow (documented limitation). Sign-out cleans FCM token + presence.
- Authorization rule of the platform: **pairing ≠ feature permission.** Every sensitive
  operation is gated server-side by rules (family membership + status==ACTIVE + guest live
  window), never by hidden UI. Feature consent (screen capture, remote control, and — future —
  location) is a separate, on-device, revocable grant.

## 6. API & data contracts (summary)

| Path | Writer | Reader | Key fields / constraints |
|---|---|---|---|
| `users/{uid}` | self | self | displayName≤300, email≤320, photoUrl≤2048; no delete |
| `users/{uid}/devices` | self | self | model/mfr/androidVersion caps; int sdkInt/appVersionCode |
| `pairingInvitations/{id}` | inviter | any signed-in get (list=false) | status PENDING→PAIRED; tokenHash(64hex)+raw token; expiresAt ≤+10 m; redeem = claimToken proof |
| `trustedDeviceRelationships/{relId}` | redeem txn only | participants | `pairKey=helperUid_recipientUid`; kind family/guest frozen; guest window ≤3 h; status ACTIVE/REVOKED/EXPIRED |
| `devicePresence/{uid}_{devId}` | self | paired counterpart (ACTIVE+window) | 20-min online window; heartbeat 15-min WorkManager |
| `fcmTokens/{id}` | owner | — (SA/worker reads) | token 32–4096 chars |
| `supportSessions/{id}` | participant FSM | participants | deadlines 5 m ask / 20 m cap; guest capped by rel window; no delete |
| `supportSessions/{id}/signal` | participant | aud-only | per-type payload caps; epoch guard; participant wipe |
| `config/{doc}` | SA only | any signed-in get | turn / quality runtime config |
| `liveLocations/{uid}_{devId}` | **planned (M5)** | paired counterpart + sharingEnabled | see LIVE_LOCATION_ARCHITECTURE.md |

## 7. QR pairing & device-relationship model (as-is)

`createInvitation` (PENDING, raw token 20 chars shown in QR/text + sha256 stored, 5-min client
TTL / 10-min rules TTL) → recipient scans (`QrScanner`, CameraX+ML Kit) or types 12-digit code
→ `ScannedRoleSheet` role preview (Family/Guest) → `redeemCode` transaction (claimToken proof)
→ `trustedDeviceRelationships` doc (ACTIVE) → shown in device list with details/revoke.
Revocation = status `REVOKED` (family) or delete (guest-only). Guests: 2-h default TTL,
server-bounded window, guest-only delete, kind fence prevents guest→family upgrade.

## 8. Family Mode & Guest Mode implementation status

| Capability | Family | Guest |
|---|---|---|
| Persistent pairing, no re-scan | ✅ ACTIVE until revoked | n/a (temporary) |
| Screen-share sessions | ✅ owner consent each session | ✅ inside live window only |
| Remote interaction (accessibility gestures) | ✅ assist setup + per-session | ✅ inside live window only |
| Location sharing | ✅ **M5 (v0.33.0)** — owner opt-in, consent sheet, visible FGS, one-tap stop ×3 | ✅ same rules fence (live window only) |
| Revocation controls | ✅ device list + settings | ✅ auto-expiry + delete + termination |

## 9. Screen sharing & remote interaction (as-is)

`ScreenShareService` FGS (type `mediaProjection`) wraps `ScreenShareEngine` (2,300-line
singleton): helper/recipient roles, MediaProjection capture, WebRTC publish/subscribe,
assist-cmd dispatch, diagnostics. Recipient sees a persistent session bar + fullscreen
viewer; consent modal + stop controls always visible. Remote interaction =
`AssistService` (AccessibilityService, gesture injection taps/swipes/global-nav + typing via
`ACTION_SET_TEXT`), guided setup `AssistSetup.kt`. **Doc drift FIXED M4 (v0.32.0):** the manifest comment now matches
`assist_service_config.xml` (`canRetrieveWindowContent=true` since Phase 8.3 — its only
consumer is the ACTION_SET_TEXT typing command; node text is never read, logged or stored).
Android re-consent behavior documented in
ANDROID_PERMISSION_MATRIX.md; no bypass of system dialogs anywhere.

## 10. Navigation structure (current, post-M4) — NAVIGATION_MAP.md is the target map

**Navigation-Compose shell (Decision C3 adopted at the M2 spike):** `MainActivity` composes a
`NavHost` with the 4 tab destinations (home / add / tracking / settings) + a `notifications`
route **+ the M4 nested Home subtree**: `home/family` (Family Overview — the trusted-device
list), `home/family/{relId}` (Member Details — the old action sheet: request-session, revoke,
guest remove), `home/sessions` (Recent Activity) and `home/sessions/{id}` (Session Details).
Routes, `goTab` (saveState/restoreState) and the top/bottom bars live in `ui/nav/AppNav.kt`
(`AppRoutes.tabForRoute` keeps the Home tab selected on nested routes); nested routes slide
≈200 ms honoring the system Animator duration scale; system back pops them. Settings is the
master's 8-partition grouped list (devices/guest entries route into home/family). The add/*
flow stays modal ON PURPOSE (map §3). Overlays (wizard, pair sheet, consent modal, session
dialogs, fullscreen viewer) keep their pre-M2 composition sites. Remaining route work:
tracking/* = M5; settings/* sub-routes = later waves.

## 11. Design system (current)

Implemented in `ui/Theme.kt` and specified in `docs/UI_UX_DESIGN_SYSTEM.md` (Bengali,
Phase 11.7-frozen; reconciliation appendix §26 added Phase 21). Tokens match the master-plan
palette exactly (light `#246BFD/#F7F9FC/…`, dark `#0B1220/#121C2B/…`, success/warn/error
semantics, ≥48 dp targets, shape scale). Component inventory + screen-by-screen rules live in
that doc; gaps tracked in IMPLEMENTATION_STATUS.md.

## 12. Permissions & Android services (current)

Manifest permissions: `INTERNET`, `CAMERA`, `WRITE_EXTERNAL_STORAGE` (maxSdk 28),
`POST_NOTIFICATIONS`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PROJECTION`,
`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, **`ACCESS_FINE_LOCATION` + `ACCESS_COARSE_LOCATION` +
`FOREGROUND_SERVICE_LOCATION` (M5, v0.33.0 — while-in-use only, NO background permission in
v1)**. Services: `ScreenShareService` (FGS mediaProjection), **`LocationShareService` (FGS
location, M5 — persistent coordinate-free notification, no reboot auto-restart)**,
`AssistService` (accessibility), `PushTokenService` (FCM), `HeartbeatWorker` (WorkManager),
`FileProvider`. Full matrix: ANDROID_PERMISSION_MATRIX.md.

## 13. Dependencies & versions (pinned)

AGP 8.5.2 · Kotlin 2.0.20 (+compose plugin) · Compose BOM 2024.09.03 · Firebase BoM 33.4.0
(auth/firestore/messaging only) · Credential Manager 1.3.0 + googleid 1.1.1 · navigation-compose 2.8.2 (M2) · CameraX 1.3.4 ·
ML Kit barcode 17.2.0 · zxing 3.5.3 · stream-webrtc-android 1.3.8 · work-runtime 2.9.1 ·
**play-services-location 21.3.0 + osmdroid-android 6.1.18 (M5, v0.33.0 — approved with the
Owner's M5 start command, C4 decision recorded in build.gradle)** ·
minSdk 26 / targetSdk 34 / compileSdk 34 / JDK 17 · ABIs armv7+arm64 · `isMinifyEnabled=false`
(no R8), **no test source set**.

## 14. Known bugs, debt & incomplete features (audit-verified)

1. ~~Live location tab = placeholder promise with permanent nav slot~~ **FIXED M5 (v0.33.0)** — real osmdroid map + engine + rules; probe-verified.
2. ~15 user-visible hardcoded Bengali/English string lines outside resources (assist-ack toasts,
   session status lines, service channel text, 2 a11y labels) — top-bar title fixed in M2.
3. ~~Manifest/assist-config documentation drift~~ **FIXED M4 (v0.32.0):** the manifest comment now tells the truth (`canRetrieveWindowContent=true` since Phase 8.3 — ACTION_SET_TEXT typing only).
4. ~~Bell routes to Settings~~ FIXED M2: bell opens the dedicated notifications surface.
5. Several listeners swallow errors (`onError = {}`); no uniform loading/error component.
6. ~3 untranslated keys per locale; emoji-glyph session keys don't localize.
7. `isMinifyEnabled=false`; zero tests; 2-ABI WebRTC APK ≈ 40 MB.
8. Per-second recomposition tick (fine at 1 device, a scale smell).
9. Docs: `docs/PHASE-0.md` §12 table stale; UI_UX doc frozen at Phase 11.7 (§26 reconciles).
10. Ops: Cloudflare `FIREBASE_SA_JSON` unset → permanent kill-push tier inactive.
11. No account-deletion flow (Play-policy consideration if ever published).
12. **(M2 discovery)** default↔bn locale inversion, ~203 legacy keys (see §3 note) — M4/M8.

## 15. Security constraints (non-negotiable)

No secrets in the app or repo (verified by Agent 2-b scan: only public OAuth client id +
google-services.json identifier present; keystore/SA/KILL_TOKEN env-only). Server-side
enforcement for every sensitive op. No hidden surveillance; MediaProjection/accessibility
always with visible session indicators and owner stop controls. Location (M5) is opt-in,
owner-visible (FGS notification), revocable, no history, coordinates never in lock-screen
notifications. Catch-all deny rules; new collections get explicit match blocks + probes.

## 16. Live Location architecture (shipped M5, v0.33.0)

Consent-based, update-in-place, quota-bounded — shipped as planned: FusedLocation (+
LocationManager fallback) + `location`-type FGS (`LocationShareService`, coordinate-free
persistent notification, one-tap stop ×3) → distance/time/accuracy-gated writes to a single
doc `liveLocations/{uid}_{devId}` (≥30–60 s cadence, stationary back-off, ~1.4–2.9K
writes/day/stream) → counterpart-only listener → osmdroid OSM map (mandatory attribution,
app User-Agent) with LIVE/STALE/OFFLINE freshness + "Last known" lines. No
background-location permission in v1; no history (deleted on stop/sign-out); server-side
fence reuses the ACTIVE-relationship + guest-window logic — **rules LIVE + runtime probe
12/12 PASS**. Full plan: `docs/LIVE_LOCATION_ARCHITECTURE.md`.

## 17. Multi-agent execution model (honest report)

This environment **does support genuine parallel agent execution** (independent agent
processes launched simultaneously). Applied here: **Agent 1 = Principal Architect (this
coordinator)**; Task 2-a/2-b/2-c ran as 3 genuine parallel read-only auditor agents
(frontend / backend-infra / docs-tracker). The 24 named roles are mapped onto waves of
parallel agents during implementation (typically 4–8 concurrent coding agents per wave,
strict file ownership, central review before any push) — they are role-prompted generalist
agents, not 24 permanently-separate identities; no simulated "phantom agents" are claimed.
Wave plan: W1 shell/design-system (Agents 3–5, 16, 18) · W2 tab redesigns (6–9, 17) ·
W3 live location (10–13) · W4 remote-support & compat (14, 15, 20) · W5 tests/perf/release
(22, 23, 24). Agent 2 re-audits after every wave.

## 18. Agent ownership & file map (planned — no parallel writes to the same file)

| Area | Owner agent | Files |
|---|---|---|
| Theme/design tokens | 5 | `ui/Theme.kt`, `values/colors*.xml` |
| Shell + navigation | 18 (+3 review) | `MainActivity.kt` (shell only), new `ui/nav/`, `AndroidManifest.xml` |
| Home tab | 6 | new `ui/home/HomeTab.kt` (extracted) |
| Add Device flow | 7, 8 | new `ui/pairing/*` (extracts of PairingSections), `QrScanner.kt`, `QrActions.kt` |
| Guest sessions | 9 | `SessionRepo.kt`, guest UI extracts |
| Live location engine | 10, 12 | new `LiveLocationRepo.kt`, `LocationShareService.kt` |
| Tracking map | 11 | new `ui/tracking/TrackingTab.kt` |
| Location security | 13 | `firestore.rules`, `scripts/rules-liveLocations-probe.mjs` |
| Screen share/assist | 14, 15 | `ScreenShareEngine.kt`, `AssistService.kt` (docs drift fix) |
| Notifications | 16 | `SessionNotifier.kt`, new notifications surface |
| Settings | 17 | settings extracts |
| i18n/a11y | 19 | `strings.xml` ×15, `ui/Lang.kt` |
| Compat | 20 | manifest, PermissionWizard |
| Auth/backend contracts | 21 | `PairingRepo.kt`, `PresenceRepo.kt`, `Fb.kt` |
| Tests | 22 | `scripts/*-probe.mjs`, new probe scripts |
| Performance | 23 | tick loops, listener hygiene |
| Release/docs | 24 | README, worklog, docs/* |

## 19. Integration status & unresolved conflicts (decision log)

| # | Conflict / decision | Architect resolution (Agent 1) | State |
|---|---|---|---|
| C1 | Master instruction tabs = Home/**Add Device**/**Tracking**/Settings vs current Home/Phone/**Live location**/Settings | Adopt instruction labels (strings-only rename; same 4 slots). No 5th tab; Devices/Sessions live under Home + Settings | ✅ **Implemented M2** (nav_add_device/nav_tracking ×15; PersonAdd icon; 7 tab-pointing copy keys fixed per locale) |
| C2 | Master section 7 alternative nav (Home/Devices/Sessions/Settings) conflicts with PART 3's four tabs | PART 3 (explicit "exactly four") wins; Devices/Sessions become nested screens + Home sections | Resolved |
| C3 | Navigation-Compose vs manual state machine | Recommend Navigation-Compose (type-safe routes, back-stack, restoration); fallback = structured manual nested state if migration risk proves high in W1 spike | ✅ **Resolved M2: ADOPTED** — the W1 spike gate items verified by code inspection (cold-start: no intent logic existed; wizard: app-level overlay; fullscreen: Dialog inside tab content, same unmount semantics as the old when(tab)); Owner device check = TEST_PLAN #7/#8/#24. `lifecycle-viewmodel-compose` deliberately deferred to M3 |
| C4 | New dependencies (maps/location) vs minimal-deps discipline | Justified: no map/location library existed; osmdroid is Apache-2.0, quota-safe; documented in LIVE_LOCATION_ARCHITECTURE.md | ✅ **Resolved M5 (v0.33.0)** — play-services-location 21.3.0 + osmdroid-android 6.1.18 added, approved with the Owner's M5 start command (same precedent as navigation-compose with M2) |
| C5 | Read-quota discipline vs feature richness | Location = update-in-place single doc, ≥30 s cadence, excluded from sweeps; budget table in LIVE_LOCATION_ARCHITECTURE.md | Resolved |
| C6 | UI_UX_DESIGN_SYSTEM.md staleness vs duplication ban | Update-in-place: appended §26 reconciliation; no duplicate design doc | Done (this push) |

## 20. Maintenance rule

Any architectural decision change ⇒ update this file (and IMPLEMENTATION_STATUS.md) in the
same push that implements it. Agents must re-read §1–§19 before starting a wave; no agent may
assume another agent's planned work is already merged.
