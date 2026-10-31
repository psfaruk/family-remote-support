     1→# IMPLEMENTATION_STATUS — Reconciled Tracker (Master-Plan PART 15 #9)

> Status: **IMPLEMENTATION checkpoint — M5 shipped.** Reconciles the ORIGINAL phase history
> (Phase 0–22) with the master-plan phases (M0–M10). The stale §12 table inside
> `docs/PHASE-0.md` is superseded by this file from now on (PHASE-0 remains the append-only
> narrative record).
> Build at writing: **v0.33.0 (versionCode 52)** · HEAD at M4-close `7162e3c` (CI SUCCESS,
> run 41170479983) · M5 push follows this doc. Rules for the new `liveLocations` collection
> are **LIVE** (ruleset d1a38d5e) and **probe-verified 12/12** before the push.

## 1. Original phases (verified from PHASE-0.md §18–43, README status, worklog §44–63)

| Phase | Content | Build | State |
|---|---|---|---|
| 0 | Environment & feasibility, plan, decision record | — | ✅ |
| 1–4 | Auth, pairing, devices, presence | v0.5.x | ✅ |
| 5–7 | FCM, sessions, WebRTC signaling | v0.8–0.13 | ✅ |
| 8–10 | Hardening, wizard, notifications | v0.13–0.15 | ✅ |
| 11–11.8 | UI/UX polish series (design system, AuthGate) | v0.15–0.18.1 | ✅ |
| 12–13 | Privacy docs, sideload release path | v0.18.0 | ✅ |
| 14–14.1 | Cloudflare Worker kill-push + wrangler live proof | infra | ✅ |
| 15–17 | Quota-aware ops, 4-tab shell restructure (Home/Phone/Live location/Settings), misc | v0.19–0.21 | ✅ |
| 18.1–18.9 | Deep-audit series: pairing/reboot, session UX, latency, notification-chain, rules+23 probes, app-logic (code 6→12 digit), i18n notifications, security caps, CI/docs | v0.22.0–0.26.0 (41→45) | ✅ |
| 19.1–19.2 | Full i18n extraction (~305 literals → stringResource; 231+39 keys ×15) | v0.27.0–0.28.0 (46→47) | ✅ |
| 20 | Firestore free-quota incident: 5 s→30 s sweep + backoff, zombie EXPIRE, `err_quota` ×15, presence labels | v0.29.0 (48) | ✅ |
| 21 | Master plan M0–M4 (audit → docs → shell → extraction → nested nav + Settings partitions) | v0.30.0–0.32.0 (49→51) | ✅ |
| 22 | Master plan M5 — Live Location (engine, map, rules, probes) | v0.33.0 (52) | ✅ |

## 2. Master-plan phases (this instruction) — current state

| M | Content | State |
|---|---|---|
| M0 | Repository audit (3 parallel agents: frontend / backend-infra / docs-tracker) + state verification | ✅ 2026-10-31 (this push) |
| M1 | Architecture & design specification: 8 new docs + UI_UX §26 reconciliation + PROJECT_CONTEXT | ✅ 2026-10-31 (this push) |
| **⛔ APPROVAL GATE** | Owner review of the M1 document set | ✅ **APPROVED 2026-10-31 — Owner's explicit "M2 শুরু করো" command** |
| M2 | Shared application shell: header polish, notifications surface, 4-tab rename (C1), theme foundation, nested navigation (C3 spike) | ✅ 2026-10-31 (v0.30.0/49) — C3 spike: Navigation-Compose **ADOPTED** for the shell (routes+bars extracted to `ui/nav/AppNav.kt`; crossfade 120 ms; saveState/restoreState tab switching). C1 rename ×15 locales (+ the 7 tab-pointing copy keys per locale fixed). Dedicated notifications surface (real events, in-memory). Components promoted verbatim: SectionCard/AppListTile/EmptyState/WideOutlinedButton → `ui/components/Core.kt`. Theme foundation: tokens verified — already master-palette-exact (no delta needed). |
| M3 | Frontend redesign: Home, Add Device, Tracking, Settings + detail screens with real states; ViewModel layer (`lifecycle-viewmodel-compose`) + component-library completion | ✅ **2026-10-31 (this push, v0.31.0/50)** — **Screen extraction (W2):** HomeTab (`ui/home/HomeTab.kt`, incl. consent card/dialog + feature tiles), AddDeviceTab (`ui/pairing/AddDeviceTab.kt`, 1,760 lines incl. PairingSections/role sheet), SettingsTab (`ui/settings/SettingsTab.kt`, 1,091 lines incl. activity feed), TrackingTab (`ui/tracking/TrackingTab.kt`, placeholder renamed) — all VERBATIM moves (byte-identical bodies, visibility-only changes; 2 real parallel extraction agents + coordinator surgery per PROJECT_CONTEXT §18 file ownership). MainActivity 5,683 → ~2,000 lines (shell + root session effects + session surfaces). **ViewModel layer:** `lifecycle-viewmodel-compose` 2.8.6 added; `ui/home/SessionWatchViewModel.kt` owns the session watch as `StateFlow<SessionsUiState>` — listener errors ROUTED into state (closes audit risk #5's root-level swallowed `onError`), Home shows `LoadingSkeleton` before first snapshot + `ErrorPanel`(retry) on pre-snapshot failure; root-scoped above the NavHost ON PURPOSE (zombie-sweep/stop guarantees must keep running on every tab). **Component library complete (§6):** Cards.kt (SettingsTile/IconStatusTile/QuickActionTile) · Buttons.kt (BigButton) · Badges.kt (StatusBadge/sessionStatus/sessionBadgeKind/statusChipColors/PresenceBadge/PresenceChip) · States.kt (LoadingSkeleton/ErrorPanel/PermissionDeniedPanel — canonical; permission panel wired M4/M5) — all verbatim promotions, one variant per widget. **Strings:** +2 keys (`states_retry`, `states_open_settings`) ×15 locales = 30 translations. **Honest deltas:** (1) NAVIGATION_MAP §2 nested detail routes (home/family/{relId}, home/sessions/{id}, settings/*, add/*, tracking/{relId}) NOT built here — tab-level extraction first, route-tree expansion = M4 wave; (2) collection via `collectAsState` (shell-always-composed scope ≙ repeatOnLifecycle in effect; no runtime-compose dep — C4); (3) no user-visible behavior change except the new honest loading/error states on Home. |
| M4 | Existing-feature integration checks (pairing, sessions, revocation) + drift fixes | ✅ **2026-10-31 (this push, v0.32.0/51)** — **Nested navigation wave 1 (NAVIGATION_MAP §2 Home subtree):** `home/family` Family Overview (PairTile list + live presence + guest countdowns, real loading/error/empty states, empty-state CTA → Add Device) · `home/family/{relId}` Member Details (the old AddDeviceTab §4 action sheet promoted to a route — role row, guest window, PresenceBadge, request-session, family revoke, guest remove: SAME PairingRepo/SessionRepo calls, success pops the route) · `home/sessions` Recent Activity (rows per open session + last-finished line, each a route link; data = root SessionWatchViewModel state + the ported lastFinished one-shot pattern) · `home/sessions/{id}` Session Details (StatusRow set: status/type/helper/recipient/duration/ended-by; LIVE while the root watch holds it, else the SAME one-shot getSession). Home gets two QuickActionTile entries; nested routes slide ≈200 ms honoring Animator durationScale (reduced-motion = instant); Home tab stays selected on nested routes (tabForRoute); system back pops. **Settings restructured into the master's 8 partitions** (Account · Appearance(theme+language) · Device Management(→home/family + devices registry) · Location & Privacy(honest M5 note + policy docs) · Remote Support(notif/assist/test) · Guest Access(note + →home/family) · Security(permission wizard) · About; sign-out LAST = Owner's standing order, recorded deviation). ActivityTab/ActivityFeed graduated to the route (deleted from SettingsTab). **add/* stays modal on purpose** (map §3); tracking/{relId} = M5. **Drift fixed:** manifest assist-config comment now truthful (canRetrieveWindowContent=true since Phase 8.3, ACTION_SET_TEXT only). 2 real parallel agents (M4-A/M4-B) + coordinator. **Honest deltas:** (1) full session-history LIST needs a bounded query + composite index — deliberately NOT added (quota + index discipline, watchSessions is equality-only by design); Recent Activity shows the real data that exists (open + last-finished). (2) settings/* subsection ROUTES beyond devices (account/appearance/location/remote/guest/security/notifications/about as separate screens) remain future waves (M6/M8 own guest/notifications; grouped-list form ships now). (3) i18n residuals (~16 hardcoded lines + parity + default↔bn inversion) stay M8. (4) no new deps/listeners/queries/rules. 30 new keys ×15 = 450 entries. |
| M5 | Live location implementation (engine, map, rules, probes) | ✅ **2026-10-31 (v0.33.0/52)** — **Engine (M5.a/b):** `LiveLocationRepo.kt` (contract API: LiveLocationView / watchLiveLocation / writeLocationFix / deleteOwnLocation / LocationSharingPrefs / start-stopLocationSharing / isLocationSharingRunning) + `LocationShareService.kt` (FGS type location, low-importance channel, coordinate-free persistent notification R.string.loc_notif_*; FusedLocation PRIORITY_BALANCED with LocationManager fallback; acceptance gates Δt≥interval ∧ Δd≥25 m ∧ accuracy≤100 m; stationary back-off ×2 cap 5 min; defensive stop on permission loss / signed-out / consent-off). **Rules (M5.a):** liveLocations block (56 lines) published LIVE (ruleset d1a38d5e) — own-doc or counterpart-of-ACTIVE (guest window) + sharingEnabled reads; writer = uid-of-docId with docId pattern, hasOnly 8-field set, lat/lng ranges, accuracy int cap, bool, timestamp ±5 min freshness, appVersionCode int; owner-only delete — **runtime probe 12/12 PASS** (`scripts/rules-liveLocations-probe.mjs`, self-cleaning). **Tracking tab (M5.c):** real osmdroid map (MAPNIK, CopyrightOverlay kept, multi-touch, lifecycle-correct) with one marker per counterpart that actually published, camera fit-to-set, device selector sheet, freshness LIVE<2 min/STALE<5 min/OFFLINE + always-visible "Last known — HH:mm ±N m" (never labeled live when old), canonical Loading/Error/Empty/PermissionDenied states, own-sharing banner + consent sheet → FINE/COARSE launcher; `TrackingViewModel` (root listener rebuild per authorized set, routed errors, keep-last). **tracking/{relId} (M5.c):** LocationDetailScreen (back row, freshness/updated/accuracy rows, counterpart sharing READ-ONLY, open-in-maps geo: intent, honest not-found + defensive own-device branch). **Settings (M5.d):** Location partition real (sharing toggle + runtime permission gate + 30 s/1 min/5 min interval dialog; running change restarts the service so it takes effect immediately). **Deps (C4, approved with the Owner's M5 command, same precedent as M2):** play-services-location 21.3.0 + osmdroid-android 6.1.18. **i18n:** +32 keys ×15 = 480 entries (M5-D agent 14 locales + coordinator zh), apostrophe/placeholder/native-digit scans clean. **Sign-out hygiene:** stopLocationSharing + deleteOwnLocation join the Phase-13 cleanup. Multi-agent honesty: 2 real parallel coding agents (M5-A engine / M5-C map UI) + M5-B rules/probe + M5-D i18n (i18n agent hit the turn limit after 14/15 locales — zh completed by the coordinator, recorded); C and D agents exceeded time/turn limits but had already written their full file sets to disk — verified line-by-line by the coordinator before surgery. |
| M6 | Family/Guest authorization verification + G1–G4 deltas | ⛔ |
| M7 | Remote support integration audit (no regression to WebRTC/assist) | ⛔ |
| M8 | Notifications, accessibility, i18n completion, polish | ⛔ |
| M9 | Testing & integration: TEST_PLAN execution, security review | ⛔ |
| M10 | Release readiness: docs, build instructions, known limits, final report | ⛔ |

Rule honored: no later phase starts before its prerequisites are verified (master PART 14).

## 3. Feature-level status (Agent 2-a/2-b audit, 2026-10-31)

| Feature | Status | Evidence |
|---|---|---|
| Google Sign-In (Credential Manager) + email auth + reset | ✅ implemented | `MainActivity.kt:643-687`, `ui/AuthGate.kt` |
| QR generate/scan, 12-digit code, single-use TTL invitations | ✅ | `PairingRepo.kt:276-441`, `QrScanner.kt` |
| Family device list / details / revoke | ✅ | `PairingRepo.kt:521-565`, `MainActivity.kt:4180-4300` |
| Guest mode (window ≤3 h, fence, guest-only delete) | ✅ | rules `:198-313`, `SessionRepo.kt:246-280` |
| Presence (20-min window, heartbeat, chips) | ✅ | `PresenceRepo.kt` |
| FCM + token rotation + session asks + kill-push 3 tiers | ✅ (permanent tier inactive: SA secret unset) | manifest `:76-97`, worker, §63 |
| Screen share (WebRTC + MediaProjection FGS) | ✅ | `ScreenShareEngine.kt`, `ScreenShareService.kt` |
| Remote interaction (accessibility gestures + typing) | ✅ | `AssistService.kt`, `AssistSetup.kt` |
| Sessions + signaling + zombie guards + quota errors | ✅ | `SessionRepo.kt`, `SignalingRepo.kt`, §63 |
| 15-language i18n + theme persistence | ✅ (≈3 keys/locale parity gap; ~16 stray literals) | `ui/Lang.kt`, strings ×15 |
| **Live location** | ✅ **M5 (v0.33.0/52)** — engine + FGS + osmdroid map + detail route + Settings partition; rules LIVE + probe 12/12 | `LiveLocationRepo.kt`, `LocationShareService.kt`, `ui/tracking/*` (rebuilt), `firestore.rules` liveLocations block, `scripts/rules-liveLocations-probe.mjs` |
| Notifications screen (dedicated) | ✅ **M2** — bell → real-events surface (in-memory; persisted store = M8 decision) | `ui/notifications/NotificationsScreen.kt` |
| Navigation-Compose shell | ✅ **M2** (Decision C3 ADOPTED — navigation 2.8.2) · **M3: all 4 tab screens extracted** · **M4: nested Home subtree live** — home/family + home/family/{relId} + home/sessions + home/sessions/{id} (slide ≈200 ms, durationScale-honoring; Home tab stays selected on nested routes; system back pops). add/* stays modal per §3; tracking/{relId} = M5 | `ui/nav/AppNav.kt` (routes + `tabForRoute`), `ui/home/FamilyScreens.kt`, `ui/home/ActivityScreens.kt`, MainActivity NavHost wiring |
| Settings partitions | ✅ **M4** — the master's 8 partitions (real features only); paired phones + Guest Access point into home/family; Activity section graduated to home/sessions | `ui/settings/SettingsTab.kt` |
| ViewModel layer | ✅ **M3** — `lifecycle-viewmodel-compose` 2.8.6; `SessionWatchViewModel` (root-scoped StateFlow<UiState>, routed errors). M4 routes consume the SAME root state — zero extra listeners | `ui/home/SessionWatchViewModel.kt` |
| Tests (unit/UI) | ❌ none (probes + CI + Owner manual only) | TEST_PLAN §0 |

## 4. Known open issues (from audits — each mapped to a phase)

| Issue | Fix wave |
|---|---|
| ~~Live-location placeholder promise~~ | ✅ **fixed M5 (v0.33.0)** — the Tracking tab is the real osmdroid map + consent flow; probe-verified rules |
| ~16 hardcoded user-visible strings + parity gap | M8 (Agent 19 — recorded M4 decision: M4 ships the navigation wave; the full i18n sweep including the default↔bn inversion lands together in M8) |
| ~~Manifest assist-config doc drift~~ | ✅ **fixed M4** (comment now truthful: canRetrieveWindowContent=true since Phase 8.3, ACTION_SET_TEXT typing only) |
| Bell → Settings instead of notifications surface | ✅ fixed M2 |
| Swallowed listener errors / ad-hoc error states | ✅ **fixed M3** — root session-watch `onError` now ROUTED into `SessionWatchViewModel` state (keeps last list per v0.21.0, surfaces pre-snapshot failure as `ErrorPanel`); pairing/settings listeners audited during extraction — already error-routed; `States.kt` = canonical panels · **M4: the new nested routes use the same canonical states (loading skeleton → routed ErrorPanel w/ retry → empty)** | ✅ M3/M4 |
| default↔bn locale inversion — ~203 legacy keys | M8 (full re-language sweep; new M4 keys are clean-language) |
| Per-second recomposition tick | M8 (perf pass) |
| `isMinifyEnabled=false`, no tests, 40 MB APK | record-only; R8 = separate future decision |
| Cloudflare `FIREBASE_SA_JSON` unset (permanent kill-push tier inactive) | **Owner action** (unchanged since §60) |
| docs/PHASE-0.md §12 stale table | superseded by this file (narrative stays) |
| Pre-existing: `assist_service_description` is translatable="false" in default but carries a bn copy | harmless resource-merge artifact (pre-dates M4); cleanup = M8 i18n wave |

## 5. Owner pending actions (M5 edition)

1. Install **v0.33.0 (code 52)** on both phones after CI SUCCESS (same version on both) —
   device checks: TEST_PLAN **#10–#15** (location permission granted/denied/approximate-only,
   owner stops sharing, device offline, stale badge) + #7/#8/#24 as before, plus a visual pass
   over the Tracking tab (map renders with OSM attribution, device selector sheet, consent
   sheet → permission dialog), the `tracking/{relId}` detail screen, and the Settings Location
   partition (toggle + interval dialog). The sharing notification must show while sharing is
   on and contain NO coordinates; stop from any of the three places must clear it.
2. Set Cloudflare secret `FIREBASE_SA_JSON` (dashboard → Worker → Variables and Secrets) —
   activates the permanent kill-push tier (pending since §60).
3. Device-verify v0.30.0/v0.31.0/v0.32.0 items if not yet done, then move to v0.33.0.
