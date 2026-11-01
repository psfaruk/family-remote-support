     1→# IMPLEMENTATION_STATUS — Reconciled Tracker (Master-Plan PART 15 #9)

> Status: **SMOOTH-APP WAVE SHIPPED — v0.44.0/64 (2026-11-01). All
> master-plan milestones M0–M10 ✅ + Pre-M10 polish +
> quota directive (§75) + QA/dedupe wave (§76) + Location master-plan docs (§77) +
> Location Phase 4 frontend mock (§78) + Location Phases 6–8 implementation (§79) +
> Location Phase 9 route-history UI + retention/deletion (§80) +
> Location Phase 10 movement insights + first CI unit-test gate (§81) +
> Phase 8 live gate PASSED + journey-tail units fix (§82) +
> D11(e) detail-screen live movement card (§83) + Phase 11 place alerts (§84) +
> Phase 12 security review (§85) + v0.44.0 smooth-app wave (§86).**
> §86 in one line: the Owner's v0.43.0/63 device report (Settings-tab
> crash + install-time crash dialog + slow map + 2D/3D/satellite ask +
> frontend↔backend match) shipped as one wave — the hard-wired OSM MAPNIK
> tile source replaced by a 3-style live-switchable registry
> (fast CARTO Voyager 2D default / Esri satellite / Esri 3D-relief
> terrain, per-style attribution, persisted choice), osmdroid plumbing
> tuned (6 download threads, 48-tile memory LRU, 128 MB bounded disk
> cache), map redraws dirty-tracked, a purely LOCAL crash journal
> (uncaught handler → file; view/share/delete in Settings → About; zero
> telemetry — the next crash arrives WITH its stack trace), all
> 1.5–2.5 s binder polls moved to Dispatchers.IO, and the device list
> refreshes on app resume. No crash SDK added; no new permissions; rules
> untouched.
> §85 in one line: `LOCATION_SECURITY_REVIEW.md` closed the Phase 12 gate —
> the RELEASED ruleset live-verified **byte-exact** vs the repo
> (permanent read-only script `scripts/verify-released-rules.mjs`, exit-0
> MATCH), design §10 checklist re-verified item-by-item (7 PASS · 1 FAIL ·
> 2 open), a 9-class threat-model pass with **zero enforcement bypasses**,
> and a 4-finding register routed to Owner rulings: **S-1 HIGH**
> (`privacy_body` still claims history/places/movement are never collected —
> false since v0.40.0/v0.43.0; string-only fix wave recommended),
> **S-2 MEDIUM** (Firestore default disk cache holds location docs at rest —
> the design's "persistence disabled" was never verified; decision record
> needed), **S-3 LOW** (`locationAuditEvents` never implemented —
> forensics-only impact), **S-4 LOW** (place mirror survives sign-out);
> Phases 13–14 (performance → release checklist) remain.
> §83 in one line: the detail screen's section C renders the live movement
> card (optional rules-validated `speedMetersPerSecond` on liveLocations,
> engine clears glitched speeds rather than clamping, probe 16/16, zero
> extra reads).
> §82 in one line: the CI unit-test gate's maiden run caught the
> journey-tail ms/s units slip (points-based pair speeds 1000× low →
> every LIMITED estimate read "possibly stationary") — one-line fix in
> v0.41.1/61; the Phase 8 live rules gate closed authoritatively in the
> same wave (original suite 32/33 with only the §80-anticipated H7
> divergence → H7 rewritten to the app's exact D10a day-query shape with
> a rows assertion + H15 boundary negative → rerun **35/35 EXPECTATIONS
> MET, zero residue** — the HistoryScreen query contract verified
> end-to-end through the live rules by an authorized family recipient).
> §81 in one line: pure-Kotlin `MovementClassifier` (MODERATE sensor-backed /
> LIMITED points-based, impossible-speed + stale-input + gap rejection, the
> honest "unavailable" line) renders on the journey-history screen at
> render time from records already in hand — ZERO extra Firestore reads;
> the repo's FIRST unit-test source set (24 JUnit4 cases) runs in CI before
> every assemble as the Phase 10 gate; detail-screen section C recorded
> HIDDEN (D11e — liveLocations rules carry no motion facts; a per-open day
> query would burn up to 1000 reads/visit); demo history fixtures retimed
> to the documented solid-runs + one-20-min-gap shape (Phase 4 fixtures had
> every pair 20–60 min apart = all-dashed route — found and fixed).
> §76 in one line: six parallel deep audits + secrets scan + VLM screenshot
> verification; 2 edge-crash paths closed, auth hardening + latency wins
> (parallel config/registration, 20 s auth timeouts), **14 of 15 duplicate
> function clusters single-sourced**, the Add tab's duplicate session listener
> removed (root-VM pattern), dead code deleted, sandbox stripped (315+ MB) with
> the kill-push service folder removed so it can never auto-start again.
> Quota posture from §75 unchanged: every auto poller parked; with both phones'
> apps closed the project's Firestore traffic is exactly zero.
> §78 in one line: Location master-plan Phase 4 — all new screens navigable with
> clearly-labeled DEMO data; demo mode = explicitly entered, persistent banner,
> zero Firestore; audit drift closed; +120 string keys ×15 locales (BN complete);
> RadioOptionRow promoted to a single shared source.
> §79 in one line: Location went REAL — permission decision records (zero new
> permissions, DR-P1/DR-P2); scope-aware engine (ONCE auto-stop, TEMPORARY
> expiry alarm + backstop, 50 m history enqueue with OS-provided speed/bearing,
> upload-state line live); `locationAuthorizations` + history rules PUBLISHED
> (ruleset c1ac1396…) with the liveLocations dual read path; wizard writes the
> authoritative grants, every stop path revokes them (§75-safe queue for
> offline); 33-case probe suite shipped — **its live gate run hit the day's
> exhausted read quota mid-suite (incident §79) and reruns right after the
> ~07:00 UTC reset; all parked tiers re-verified clean (sandbox no services,
> Worker crons=[], Actions dispatch-only) — the exhaustion is real usage on
> the 50 K/day free tier, reported to the Owner.**
> §80 in one line: journey history went REAL on the reader side — member
> card + detail sections B/E gate on the actual grant (one diffed one-shot
> read per counterpart), the history screen runs real day queries (deviceId
> equality + retention floor + day range via the composite index, PERMISSION_
> DENIED → honest "not kept", FAILED_PRECONDITION → honest setup hint), the
> owner side gains the D6 local-log retention cleanup + the shared delete
> flow (today / everything, verified-empty, offline-queued); +13 string keys
> ×15 locales (BN complete). Prerequisite: the ONE-TIME composite index on
> `locationHistory` (Owner console — SA denied index-create); Phase 8's live
> probe gate rerun stays scheduled (33/33 claimed only when printed).
> Build at writing: **v0.40.0 (versionCode 59) — reinstall both phones.** Rules
> are **LIVE at ruleset c1ac1396** (v0.39.0: locationAuthorizations +
> users/{uid}/locationHistory + the liveLocations dual read path; the legacy
> branch is verbatim, so the M9 probe battery's semantics are unchanged — the
> 33-case v2 suite re-verifies both paths at the post-reset gate run).

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
| M6 | Family/Guest authorization verification + G1–G4 deltas | ✅ **2026-10-31 (docs-only push; APK v0.33.0/52 unchanged)** — **Live verification (self-cleaning probes, all green):** pairing-probe (P1a/P1b redeem round-trip; N1/N2 no/wrong claimToken → 403) · audit-probe A0–A21 (session-FSM side/step/cap fences, payload caps, schema fences, post-END signals) · negative-probe (P0/N1–N10 + P10/N11–N18/G1/G2/N12–N16b: kind fence family↔guest, guest-window bounds/expiry/silent-extension denied, lazy EXPIRED + participant delete allowed) · liveLocations-probe **12/12 re-run PASS**. **G1–G4:** G1 ✅ M5 (rules LIVE d1a38d5e + consent sheet + FAMILY_GUEST §3 matrix row) · G2 ⛔ M8 by design (out of scope) · G3 ✅ closed-as-covered (OS posture + assist state → Security wizard `PermChecklist`/`AssistSetup.isAssistEnabled`; relationship/guest → home/family; sharing → Location & Privacy + Tracking — all verified in code) · G4 ✅ M4 (manifest comment re-verified). **FAMILY_GUEST §7 acceptance: all 5 criteria probe/scan-backed.** No app code, rules, deps, or strings changed; both phones stay on v0.33.0/52. |
| M7 | Remote support integration audit (no regression to WebRTC/assist) | ✅ **2026-10-31 (docs-only push; APK v0.33.0/52 unchanged)** — **2 real parallel read-only audit agents (M7-A WebRTC/session/signaling chain · M7-B assist/FCM/notification/kill-push chain) + coordinator independent verification** (git `-S` all-history searches, ScreenShareService spot-check, live Worker `/health` probes ×2, GH Actions log download). **Verdicts — all INTACT, zero refactor regressions:** WebRTC screen-share chain (consent launcher → FGS `startForeground` BEFORE `startRecipient` — spot-verified; full teardown incl. signal-listener detach; role-guarded stop; "bye" freshness gate) · session FSM surfaces (request/grant/deny/cancel/end/expire all reachable via the SAME SessionRepo APIs across HomeTab/AddDeviceTab/FamilyScreens nested routes) · signaling lifecycle (no attach-without-detach path) · MainActivity post-refactor wiring (root SessionWatchViewModel above NavHost, no-open-session→stop-everything, root zombie sweep, sign-out teardown incl. M5 location additions) · assist chain (dispatchGesture + ACTION_SET_TEXT + all 7 companion entry points) · FCM registration/notification routing (extras-free tap → Home → pendingAsk card — lands correctly in the NEW nav) · manifest registrations (4 services + POST_NOTIFICATIONS + FCM meta). **3 honest findings:** (1) sandbox instant-tier sweep script was a STALE copy missing the Phase 18.4 claim fix + Phase 20 expiry fix (instant tier could never win a claim → never pushed) — fixed in-sandbox by re-syncing from the repo copy + service restart, first sweep 0 errors; process rule recorded (that path lives OUTSIDE the repo mirror — manual re-sync needed after repo changes to the script); (2) **doc-code drift corrected:** CLOUDFLARE_SETUP.md had claimed the app-side instant `POST /sweep` call "shipped in v0.19.0" — **never implemented** (0 HTTP clients in app code, ever; git `-S` all-history 0 hits; Phase 18.9's F2 fix had been misled by that same doc) — claim retracted this push; any future implementation must first solve the bearer-token-in-public-repo leak problem (design decision); (3) Owner's "Cloudflare worker true" NOT confirmed live: `/health` probed twice today → `FIREBASE_SA_JSON:false`, `last:"not run yet"` (possible cause: added as Build variable instead of runtime Secret); GH Actions backstop last run SUCCESS (41180972665), its 3 prior failures verified from logs = Firestore 429 quota (self-healing, not code). INFO-only: empty `onDispose` on the root session-watch effect (no practical leak — documented, not changed). |
| M8 | Notifications, accessibility, i18n completion, polish | ✅ **2026-10-31 (v0.34.0/53 — CODE milestone, reinstall both phones)** — **5 real agents in 3 waves (honest):** Wave1 parallel M8-A (notifications local store, Kotlin) + M8-B (default↔bn inversion, XML-only); Wave2 M8-C (literals+a11y+perf); Wave3 parallel M8-D1/M8-D2 (7 locales each); + coordinator surgery (staged-keys merge 68+1, FamilyScreens catch migration, AuthGate Bangla cd, assist_service_description cleanup, version bump). **G2 delivered — LOCAL persisted events store** (`NotificationsLog.kt` NEW): JSON in filesDir, cap 100, fire-and-forget IO, defensive load; real events only from EXISTING observations (session asked/started/ended via root-watch state diff + sharing on/off from LocationShareService actual start/stops) — **zero extra Firestore reads, zero coordinates, cleared at sign-out**; NotificationsScreen "Recent events" section + honest new notif_hint ×15. **Design decision (recorded): local, not Firestore — Phase-20 quota discipline.** **i18n inversion FIXED (M8-B):** 209 default↔bn value swaps + cd_notifications suffix cleanup + set_lang_info English + 27 bn translations; 2 keys deliberately left (feed_last_sub pure-format, pw_perm_assist all-15-English); post-state: default has ZERO Bengali keys, bn fully Bengali except the 2 recorded. **Literals extracted (~68 keys):** SessionException stable codes + localizedMessage (9 err_session_*) with catch-site migration (HomeTab×4/AddDeviceTab×3/FamilyScreens×1); ScreenShareEngine 44 ss_* status/retry/recover/fail/ack keys; ScreenShareService share_* ×4 (LangPrefBus.wrap); QrActions qr_* ×2; setup_* ×3 + so_signout_failed; fallback_* ×2; a11y cd_pairing_qr + cd_clear_messages; SessionKey semantics wired (sk_*). **Perf:** NEW `ui/components/Clock.kt` TickingBlock isolates all 6 per-second screen-level ticks — whole screens no longer recompose every second. **i18n completion:** 74 new keys ×14 locales (1,036 entries, M8-D1/D2 with per-locale verification matrices all PASS: well-formed/526 keys/key-set parity/placeholder parity/no unescaped apostrophes/✓ & \n preserved) + notif_hint updated ×15. **Honest remainders (recorded follow-ups):** MainActivity prefix-parsed setup strings ("Registered ✓…" — HomeTab:296-300 startsWith logic needs flag plumbing first); ScreenShareEngine bilingual assist banners + diag lines (Phase-16b); bn notif_item_perms placeholder order pre-existing quirk (out of scope). |
| M9 | Testing & integration: TEST_PLAN execution, security review | ✅ **2026-10-31 (docs-only push; APK v0.34.0/53 unchanged)** — **All AUTO rows re-run LIVE, 4/4 suites green** (pairing · audit A0–A21 · negative P0–N18+G1/G2 · liveLocations 12/12 — the M6 battery, zero drift; synthetic fixtures self-cleaned). **Ops:** instant tier verified sweeping every 5 s (0 errors; script-sync md5 = repo); GH Actions backstop verified by manual dispatch **41203101630 SUCCESS** (3 prior schedule failures = Firestore 429 quota, self-healing after the 07:00 UTC daily reset — M7 log-verified; schedule re-registered by touching the workflow comment); Worker permanent tier ⛔ **new finding: the Owner's Cloudflare API token is IP-filtered (HTTP 9109 — sandbox egress blocked AND drifts IPs)** → `FIREBASE_SA_JSON` still cannot be set programmatically; unlock steps in CLOUDFLARE_SETUP.md M9 (remove the token's Client IP Address Filtering, then the assistant finishes via API + /health verify + live sweep test). **Firebase verified end-to-end:** SA → Firestore REST read 200 (fcmTokens: 3 real device tokens, newest today 10:31Z) → FCM send **200 + message_id** (labeled test push delivered to the live token). **Security review:** full 615-commit history scan — zero secrets (only hits: the Worker's PEM-parsing code string + google-services.json API key, Android identifier-class by design; console key-restriction = Owner console check, the SA can't read the Keys API — 403, recorded); workflows least-privilege re-checked; APK artifacts 7-day retention; manifest posture re-checked (services exported=false, allowBackup=false, scoped FileProvider, gated accessibility service). **Quota budget documented** (TEST_PLAN §1a — metering APIs denied to the SA, so estimate-by-source + observed 429 signature). Owner device rows + M8's reinstall remain the Owner's. |
| Pre-M10 | Owner polish pass (Settings hierarchy, tab/keyboard robustness) | ✅ **2026-10-31 (v0.35.0/54 — CODE push, reinstall both phones)** — **Settings re-partitioned into the Owner's five cards, top-to-bottom:** Account (profile+edit-name) · Device Management (paired phones + this account's phones + remote-assist status tile) · Privacy & Security (location sharing + interval dialog + guest-access row + permission-check row + privacy-policy + usage-rules) · Preferences (theme trio + Language as ONE row → capped+scrollable 15-language picker dialog — the old inline radio wall made the page 4–5 screens tall) · About & session (version row + red-OUTLINED SignOutButton last, per "একবারে নিচে Red Bordered বা Outlined Style-এ"). **Bug fixes from the same report:** (1) bell→Settings "ফাংশন গুলো নিচে পড়ে যায়" root-caused to NavHost scroll-state restoration landing mid-page — the five top-level surfaces now deliberately use a NON-saveable scroll state (ShellTabColumn topOnReentry) so every tab entry starts at TOP; nested detail routes keep saved scroll on back; (2) keyboard hiding functions — Manifest MainActivity now `windowSoftInputMode="adjustResize"` + the shell's bottom bar pads by the `ime` inset (NavigationBar's own navigationBars inset left unconsumed — keyboard-closed look unchanged) so the tab bar AND content ride above the keyboard (previously the unspecified mode let the system pan the window over it); (3) signed-out Family route showed a BLANK screen — now an honest sign-in EmptyState; (4) SettingsTile titles maxLines 1→2 (large-font truncation). **i18n:** +3 keys (set_section_privacysec/prefs/about_session) ×15 locales. **No new deps/listeners/queries/rules.** |
| M10 | Release readiness: docs, build instructions, known limits, final report | ✅ **2026-10-31 (docs-only; APK unchanged v0.35.0/54)** — **Final report `docs/RELEASE_NOTES.md` added** (what ships · Owner quick-start বাংলা · install both phones from CI artifacts · build-from-source · feature inventory · architecture at a glance · verification summary · **known-limits ledger** (Firestore free-quota daily window w/ 13:00-ঢাকা reset, per-isolate `/health` `last`, sideload-only, app-side instant `/sweep` deliberately unimplemented, Owner-manual device rows, recorded follow-ups) · **maintenance runbook** (symptom → check → healthy-looks-like table) · M0–M10 milestone ledger). README Status raised to 🟢 RELEASE-READY with the M6→M10 gap-fill entries. **Cloudflare stable tier CLOSED (the last M9 holdover):** Owner edited the API token to allow-all → IP filter gone (`GET /accounts` 200 from the previously-blocked egress) → `FIREBASE_SA_JSON` set via REST secrets API **HTTP 201** → script bindings show `secret_text` (deploy-surviving, KILL_TOKEN's proven mechanism) → `/health` `FIREBASE_SA_JSON:true` → **GraphQL `workersInvocationsAdaptive` proof: cron fires every minute (fixed-second success rows) with `subreq=2` per sweep = OAuth call + Firestore query** (SA parsed/signed/accepted; the Firestore leg 429s only inside the known quota window, self-heals) → post-push re-check confirms the secret survives the Workers Builds auto-deploy triggered by this very push. All three kill-push tiers simultaneously operational — first time since Phase 14. CLOUDFLARE_SETUP.md M10 section records the resolution + evidence. |
| Post-M10 | Owner quota directive: park every auto Firestore poller; app-only, user-driven traffic | ✅ **2026-10-31 (v0.36.0/55 — CODE push, reinstall both phones)** — Trigger: the recurring evening "API limit exhausted" + the Owner's explicit order ("read/write only when the user acts; auto-updates off; zero requests from the sandbox — only from the app"). **Root cause (live-observed): the sandbox kill-push service was polling at 5 s as TWO zombie instances** (EADDRINUSE survivor pattern from §72) ≈ 69k read attempts/day = **>100% of the free 50k/day quota by itself**; + Worker cron 2,880/day + Actions 576/day → guaranteed early-afternoon 429s. Honest drift note: Phase 20's "30 s + backoff" sweep posture was NOT present in the live script (grep-verified no backoff in the repo copy — the M7 re-sync likely wiped it; §72 had already re-blessed 5 s as the observed baseline). **Resolution:** (1) sandbox — both instances killed, :3020 closed, ZERO sandbox→Firebase runtime traffic (probes/scripts on-demand only); (2) Worker — cron trigger removed (`wrangler.toml` `crons = []` EXPLICIT so every deploy re-syncs to zero; REST clear attempt 400'd on empty-list so the deploy path did it; verified `schedules: []` + secrets still true post-deploy; Worker kept as on-demand `POST /sweep`); (3) Actions — `kill-push.yml` schedule commented out (workflow_dispatch only); (4) app v0.36.0/55 — independent Explore-agent audit proved ZERO timer-driven Firestore reads in the app (all reads = user actions or event-driven listeners, idle listener bills 0); the ONLY timer-driven write with the app closed (15-min presence HeartbeatWorker) RETIRED: foreground-only presence (open + 5-min-while-RESUMED ticker + final write on ON_STOP; `cancelPeriodicHeartbeat` purges persisted periodic work at first open; worker class self-terminates via Result.failure); SessionNotifier listener capped `.limit(10)` (matches watchSessions). **Net effect: both phones' apps closed → project Firestore traffic = exactly zero.** Honest trade-off: dead-app ask alerts parked (ask rings while recipient process alive — foreground/tray — and surfaces on next open); 30-second re-arm runbook recorded in 3 places. No rules/deps/i18n changes. Worklog §75. |

| Loc-P1–P4 | Location master plan (consent-based live location + route history + movement insights + place alerts): Phases 1–3 audit/UX/data-security docs (§77) + Phase 4 frontend mock wave | ✅ **2026-10-31 (docs §77 on the v0.37.0 baseline; code v0.38.0/57 §78)** — Phase 4: 7-state `LocationShareStatus` model (icon+label+color, never color-only); Tracking dashboard member-card list + map zoom buttons / accuracy circles / stale dashed rings + Places entry; 6-step `SharingSetupSheet` consent wizard (scope/retention recorded locally via LocationSharingPrefs until Phase 8's `locationAuthorizations`); `tracking/{relId}` sections A–E (B/C gated by authorization — honest absence when not granted); `tracking/{relId}/history` (day chips, gap-broken solid/dashed polylines, start/end markers, summary card, retention line); `tracking/places` (in-memory list + add form: name + map-center pick + radius/toggles/cooldown chips); `MovementCard` (speed hidden when implausible, confidence only Limited/Moderate); `DemoTrackingDataSource` demo strategy strictly per UX spec §11 — explicitly entered, persistent "DEMO DATA — not real locations" banner, 3 fictional members + 2-day Dhaka-area route with a deliberate 20-min gap + demo places, **zero Firestore** (real TrackingViewModel parked via `observe(null)` while demo active), never persisted, system-back exits; audit Appendix A drift closed: privacy_body rewritten ×15 (A1), 7 orphans deleted (A6 + `set_loc_status`), LIVE_LOCATION_ARCHITECTURE / PROJECT_CONTEXT / ANDROID_PERMISSION_MATRIX corrected (A2–A5); RadioOptionRow promoted to `ui/components/Core.kt` (wizard + Settings share one source); +120 keys ×15 locales (BN complete, 13 mirror EN). Honest edges recorded in `docs/location/LOCATION_IMPLEMENTATION_PROGRESS.md`: real-side statuses = derivable subset (PAUSED/PERMISSION_REQUIRED need Phase 8); upload-state line demo-only until Phase 7; places in-memory (Phase 11); real-side history = honest empty (Phase 8/9). |

Rule honored: no later phase starts before its prerequisites are verified (master PART 14).

## 3. Feature-level status (Agent 2-a/2-b audit, 2026-10-31)

| Feature | Status | Evidence |
|---|---|---|
| Google Sign-In (Credential Manager) + email auth + reset | ✅ implemented | `MainActivity.kt:643-687`, `ui/AuthGate.kt` |
| QR generate/scan, 12-digit code, single-use TTL invitations | ✅ | `PairingRepo.kt:276-441`, `QrScanner.kt` |
| Family device list / details / revoke | ✅ | `PairingRepo.kt:521-565`, `MainActivity.kt:4180-4300` |
| Guest mode (window ≤3 h, fence, guest-only delete) | ✅ | rules `:198-313`, `SessionRepo.kt:246-280` |
| Presence (20-min window, heartbeat, chips) | ✅ | `PresenceRepo.kt` |
| FCM + token rotation + session asks + kill-push 3 tiers | ✅ **M9 live-verified:** instant tier ✅ sweeping 5 s / 0 errors (script md5 = repo); GH Actions backstop ✅ manual-dispatch **41203101630 SUCCESS** (429 schedule failures = quota, self-healing); Worker permanent tier ⛔ blocked on the Owner's CF token IP filter (9109 — see CLOUDFLARE_SETUP M9); app-side instant `POST /sweep` never implemented (false claim retracted M7) | manifest `:76-97`, worker, §63, §70, §72 |
| Screen share (WebRTC + MediaProjection FGS) | ✅ | `ScreenShareEngine.kt`, `ScreenShareService.kt` |
| Remote interaction (accessibility gestures + typing) | ✅ | `AssistService.kt`, `AssistSetup.kt` |
| Sessions + signaling + zombie guards + quota errors | ✅ | `SessionRepo.kt`, `SignalingRepo.kt`, §63 |
| 15-language i18n + theme persistence | ✅ **M8 completion:** default↔bn inversion fixed (209 swaps + 27 bn fills); ~68 literal keys extracted; 526 keys ×15 locales (74 new keys translated ×14); every locale verification matrix PASS | `ui/Lang.kt`, strings ×15 |
| **Live location** | ✅ **M5 (v0.33.0/52)** — engine + FGS + osmdroid map + detail route + Settings partition; rules LIVE + probe 12/12 | `LiveLocationRepo.kt`, `LocationShareService.kt`, `ui/tracking/*` (rebuilt), `firestore.rules` liveLocations block, `scripts/rules-liveLocations-probe.mjs` |
| Location tracking UX v2 (master-plan Phase 4, mock) | ✅ **§78 (v0.38.0/57)** — member cards, 7-state statuses, 6-step consent wizard, journey-history + My-places screens, DEMO mode (explicit entry + persistent banner + zero Firestore, listeners parked) | `ui/tracking/` (+5 new: ShareStatusModel, SharingSetupSheet, demo/DemoTrackingData, HistoryScreen, PlaceAlertsScreen, MovementCard; +4 extended), `res/values*/strings.xml` (+120 ×15), `res/drawable/ic_hist_*`, MainActivity/AppNav/Core/SettingsTab/LiveLocationRepo wiring |
| Notifications screen (dedicated) | ✅ **M2** surface + **M8: persisted local events store (G2)** — NotificationsLog (JSON filesDir, cap 100, real events only, sign-out clear, no coordinates) + Recent-events section; hint copy honest ×15 | `ui/notifications/NotificationsScreen.kt`, `NotificationsLog.kt` |
| Navigation-Compose shell | ✅ **M2** (Decision C3 ADOPTED — navigation 2.8.2) · **M3: all 4 tab screens extracted** · **M4: nested Home subtree live** — home/family + home/family/{relId} + home/sessions + home/sessions/{id} (slide ≈200 ms, durationScale-honoring; Home tab stays selected on nested routes; system back pops). add/* stays modal per §3; tracking/{relId} = M5 | `ui/nav/AppNav.kt` (routes + `tabForRoute`), `ui/home/FamilyScreens.kt`, `ui/home/ActivityScreens.kt`, MainActivity NavHost wiring |
| Settings partitions | ✅ **M4** — the master's 8 partitions (real features only); paired phones + Guest Access point into home/family; Activity section graduated to home/sessions | `ui/settings/SettingsTab.kt` |
| ViewModel layer | ✅ **M3** — `lifecycle-viewmodel-compose` 2.8.6; `SessionWatchViewModel` (root-scoped StateFlow<UiState>, routed errors). M4 routes consume the SAME root state — zero extra listeners | `ui/home/SessionWatchViewModel.kt` |
| Tests (unit/UI) | ❌ none (probes + CI + Owner manual only) | TEST_PLAN §0 |

## 4. Known open issues (from audits — each mapped to a phase)

| Issue | Fix wave |
|---|---|
| ~~Live-location placeholder promise~~ | ✅ **fixed M5 (v0.33.0)** — the Tracking tab is the real osmdroid map + consent flow; probe-verified rules |
| ~~"~16 hardcoded user-visible strings + parity gap"~~ | ✅ **fixed M8** — ~68 keys extracted (SessionRepo codes/ScreenShareEngine ss_*/services/setup/qr/fallback/a11y); parity now exact (526 keys everywhere); remainder: prefix-parsed setup strings need flag plumbing (recorded follow-up) + bilingual assist banners (16b) |
| ~~Manifest assist-config doc drift~~ | ✅ **fixed M4** (comment now truthful: canRetrieveWindowContent=true since Phase 8.3, ACTION_SET_TEXT typing only) |
| Bell → Settings instead of notifications surface | ✅ fixed M2 |
| Swallowed listener errors / ad-hoc error states | ✅ **fixed M3** — root session-watch `onError` now ROUTED into `SessionWatchViewModel` state (keeps last list per v0.21.0, surfaces pre-snapshot failure as `ErrorPanel`); pairing/settings listeners audited during extraction — already error-routed; `States.kt` = canonical panels · **M4: the new nested routes use the same canonical states (loading skeleton → routed ErrorPanel w/ retry → empty)** | ✅ M3/M4 |
| ~~default↔bn locale inversion — ~203 legacy keys~~ | ✅ **fixed M8** — 209 swaps + cd_notifications/set_lang_info specials + 27 bn fills; default now zero-Bengali; 2 deliberate leaves recorded (feed_last_sub, pw_perm_assist) |
| ~~Per-second recomposition tick~~ | ✅ **fixed M8** — TickingBlock (ui/components/Clock.kt) isolates all 6 tick sites; zero screen-level nowMillis remains |
| `isMinifyEnabled=false`, no tests, 40 MB APK | record-only; R8 = separate future decision |
| Cloudflare `FIREBASE_SA_JSON` unset (permanent kill-push tier inactive) | **Owner action — M9 refined:** the Owner's CF API token (saved sandbox-side at `.secrets/cloudflare-token.txt`, never in the repo) is **IP-filtered** (API 9109 from the sandbox; egress IP drifts) → EITHER remove the token's Client IP Address Filtering (My Profile → API Tokens → Edit) and tell the assistant (it then sets the Secret via API + verifies `/health` + live sweep test), OR set the runtime **Secret** manually in Worker → Settings → Variables and Secrets (NOT a Build variable) |
| App-side instant `POST /sweep` — was falsely documented as shipped (v0.19.0); never implemented (M7 audit: 0 HTTP clients in app code, git `-S` all-history 0 hits) | docs corrected M7; future impl blocked on public-repo bearer-token-leak design decision |
| Sandbox kill-push script drift: `my-project/scripts/fcm-kill-push.mjs` (mini-service import path) lives OUTSIDE the repo mirror and had gone stale (missing Phase 18.4 + Phase 20 fixes) | fixed + restarted M7 (sandbox-only); RULE: re-sync after every repo change to `android-app/scripts/fcm-kill-push.mjs` |
| docs/PHASE-0.md §12 stale table | superseded by this file (narrative stays) |
| ~~`assist_service_description` translatable="false" with a bn copy~~ | ✅ **fixed M8** — stray values-bn copy removed (system-settings description now English-only for bn users; recorded trade-off) |
| MainActivity "Registering/Registered/Online/Push ready" strings remain English | follow-up: HomeTab:296-300 prefix-parses them (startsWith) — needs deviceOk/presenceOk/pushOk flags plumbed first |
| ScreenShareEngine bilingual assist banners + diag lines | Phase-16b follow-up (deliberate bilingual style) |

## 5. Owner pending actions (QA-wave edition)

1. **REINSTALL BOTH PHONES — v0.37.0 (versionCode 56)** — the current release
   build (QA & dedupe wave; supersedes the v0.36.0/55 reinstall note — install
   56 INSTEAD, it contains everything 55 had plus the §76 fixes/dedupes). APK
   from any green Android Build run's `app-release-apk` artifact. Post-install
   quick regression:
   - **§76 additions:** sign-in flows get honest timeouts (a hung network no
     longer spins the login button forever); the Add Device tab no longer runs
     a second session listener (slightly lighter quota while pairing);
     revoke→re-pair-as-family needs a FRESH code every time (single-use
     invitations — reusing an old code is the #1 "can't reconnect" cause);
   - **Settings five-card order + language picker dialog + bell→Settings top
     entry + keyboard ride-up** (the v0.35.0/54 checks — all still included);
   - **Presence semantics:** "Online" decays ~20 min after the counterpart last
     USED their phone — closing the app stops its Firestore traffic entirely
     (that is the quota fix working);
   - M8 checks as before: bell → "Recent events", language বাংলা;
   - TEST_PLAN device rows as before (#10–#15, #19/#20/#21, #7/#8/#24 + §4
     quick-script).
   - **Settings five-card order + language picker dialog + bell→Settings top
     entry + keyboard ride-up** (the v0.35.0/54 checks — all still included);
   - **Presence semantics (new, expected behavior):** "Online" now decays
     ~20 min after the counterpart last USED their phone — closing the app
     stops its Firestore traffic entirely (that is the quota fix working);
   - M8 checks as before: bell → "Recent events", language বাংলা;
   - TEST_PLAN device rows as before (#10–#15, #19/#20/#21, #7/#8/#24 + §4
     quick-script).
2. **Cloudflare — DONE, nothing further.** M10 closed the Secret; the
   quota-directive wave then parked the cron (deploy-verified `schedules: []`,
   secrets intact, on-demand `/sweep` kept). If dead-app ask alerts are ever
   missed: re-arm = one line (`crons = ["* * * * *"]`) + push — see
   RELEASE_NOTES §9a.
3. **Firebase — no action needed.** With the auto pollers parked, the daily
   read load is ~250–400 (<1% of quota) — the "API limit exhausted" messages
   should be gone. (Today's residual 429s clear at the 13:00 ঢাকা reset.)
4. **Console eye-check (optional, 1 min):** unchanged from M10 — Firebase
   console → the Android key's restrictions.
5. **Nothing else pending.** The parked-tiers trade-off (dead-app ask alerts
   off) is the Owner's accepted choice; follow-ups unchanged (RELEASE_NOTES
   §7.3).
