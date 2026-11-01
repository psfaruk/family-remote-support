# RELEASE_NOTES — Family Remote Support — M10 Final Report (2026-10-31)

> **Release state: v0.44.0 (versionCode 64) — smooth-app wave (fast 3-style
> map + local crash journal + IO-hardened polls), 2026-11-01** · repo
> `psfaruk/family-remote-support` (public, `main`, CI green at every milestone
> close) · master-plan milestones **M0–M10 all ✅** plus the Owner's Pre-M10
> polish pass.
>
> **v0.44.0 in one line (the Owner's device report on v0.43.0/63: "সেটিং
> ট্যাব এ ক্লিক করলে ক্র্যাশ করে", install-time crash dialog, "অ্যাপ টি স্মুথ
> লি চলে ঠিক করে দেন", "গুগল ম্যাপ একেবারেই স্লো … 2D 3D ও setelite ম্যাপ
> যুক্ত করতে হবে", frontend↔backend match):** the map's hard-wired OSM
> MAPNIK source (a community server with no South-Asia edge — THE reason
> the map felt "একেবারেই স্লো") was replaced by a 3-style registry with
> one-tap LIVE switching chips: **2D street** (CARTO Voyager — same OSM
> data from a fast global CDN, the new default), **স্যাটেলাইট** (Esri
> World Imagery) and **3D terrain** (Esri World Topo hillshade — honest
> note: true photorealistic 3D buildings are not possible on a 2D tile
> engine; hillshaded relief is the honest equivalent). osmdroid plumbing
> tuned (6 download threads, 48-tile memory LRU vs the tiny stock default,
> 128 MB bounded disk cache) and map redraws are now dirty-tracked — the
> 30 s freshness tick no longer repaints an unchanged map. **Crash
> diagnostics:** the app ships zero crash SDKs by policy, so device crash
> reports arrived with no stack trace — a purely LOCAL crash journal now
> captures every uncaught exception to a file (view / share / delete in
> Settings → About; nothing ever leaves the phone by itself; the next
> crash report comes with its exact stack trace). **Smoothness:** every
> 1.5–2.5 s settings/power/notification poll now reads off the main
> thread (Dispatchers.IO), and the account's phone list refreshes on app
> resume (frontend↔backend match — everything else already updates live
> from Firestore listeners). The install-time "report to the mobile
> company" dialog is Android's own crash/scan report UI — if it appears
> again after v0.44.0, open Settings → About → Crash report and the
> captured stack trace will say exactly what broke.
>
> **⚠ Post-release ops change (Owner quota directive — see §9a):** all
> automatic server-side Firestore polling is PARKED (sandbox stopped, Worker
> cron removed, Actions schedule disabled) and the app itself became
> foreground-only for presence in v0.36.0/55, the QA & dedupe wave in
> v0.37.0/56, the Location master-plan Phase 4 frontend wave in
> v0.38.0/57, the Location Phases 6–8 implementation wave in v0.39.0/58,
> the Location Phase 9 route-history wave in v0.40.0/59, the Location
> Phase 10 movement-insights wave in v0.41.0/60, and
> **v0.41.1 (versionCode 61) — the current release build — the Phase 10
> units fix + Phase 8 gate closure.** The quota math and re-arm
> runbook are in §9a and `docs/CLOUDFLARE_SETUP.md` (কোটা-নির্দেশ section).
>
> **v0.41.1 in one line:** the repo's first CI unit-test gate proved its
> worth on its maiden run — it caught that the journey-history movement
> card's points-based speed math fed milliseconds into a seconds
> formula, making every LIMITED estimate read "possibly stationary";
> one line fixed it (numeric re-verification: all 25 cases), and the
> same wave closed the Phase 8 live rules gate authoritatively: the
> scheduled rerun scored 32/33 (the sole failure was the §80-anticipated
> H7 strict-semantics divergence — a retention-only recipient list is
> denied because the rules bind `resource.data.deviceId` to
> deviceId-equality queries only), the probe's H7 was rewritten to the
> app's EXACT day-query shape with a returned-rows assertion, a new H15
> negative locked the boundary, and the authoritative rerun passed
> **35/35 — end-to-end through the live rules, not the Admin bypass**.
> Do NOT install 0.41.0/60 (its points-based movement estimates are all
> "possibly stationary"); install v0.41.1/61.
>
> **v0.41.0 in one line:** movement insights went REAL on the
> journey-history screen — a pure-Kotlin `MovementClassifier` (no Android
> dependency, unit-tested — the repo's FIRST test source set, running in
> CI before every assemble) classifies the day's last observed movement
> at render time from records the screen already holds (zero extra
> Firestore reads): MODERATE confidence when the record carries the
> OS-provided speed, LIMITED when the estimate is points-based, the
> honest "Movement type unavailable" line when the evidence cannot
> support any label, and impossible speeds (< 0 or > 120 km/h) / stale
> observations rejected outright. The detail screen's section C stays
> HIDDEN this wave (recorded D11e: the liveLocations rules carry no
> motion facts, and a per-open day query just for the card would read up
> to 1000 records a visit — half the daily free read quota). The demo
> history fixtures were also retimed to the documented
> solid-runs-plus-one-20-min-gap shape (the Phase 4 fixtures spaced every
> pair 20–60 min apart, which the >10-min threshold dashed in full).
> v0.40.0 recap: real day routes through the bounded composite-index
> query, grant-gated sections B/E, the D6 cleanup + shared delete flow;
> the Phase 8 probe-gate rerun remains the outstanding operational item
> (the Phase 9 composite-index prerequisite was CANCELED 2026-11-01 —
> live-verified unnecessary, see §7.3).

## 1. What this is

Android-to-Android remote support for families: a trusted family member helps fix
another family phone — live **view-only screen share**, **guided remote taps and
typing** (with the receiver watching a live on/off indicator), **high-priority
"support ask" alerts that arrive even when the app process is dead**, and
**consent-based live location** — always with the receiver's explicit permission,
enforced server-side by Firestore rules, in **15 languages**. Two roles of trust:
permanent **Family** and time-boxed **Guest** pairing.

## 2. Owner quick start (বাংলা — সংক্ষেপ)

1. **দুটো ফোনেই v0.40.0 (versionCode 59) বসান** — GitHub repo → Actions → সবুজ
   (SUCCESS) যেকোনো রান → Artifacts → `app-release-apk` ডাউনলোড → ইনস্টল
   (আগের ভার্সনের উপরেই পরিষ্কারভাবে বসে যায় — একই CI keystore-এ সাইন করা)।
2. বসানোর পরের দ্রুত যাচাই: Settings-এ নতুন **পাঁচ-কার্ড ক্রম** (Account →
   Device Management → Privacy & Security → Preferences → About & session) ·
   Language লাইনে ট্যাপ করলে **ভাষা-বাছাই ডায়ালগ** · বেল → Settings ট্যাবে
   ফিরলে পৃষ্ঠা **উপর থেকে** শুরু · কীবোর্ড উঠলে ট্যাব-বার ও কনটেন্ট **কীবোর্ডের
   উপরে** থাকে।
3. **কোটার দিন শেষ — আর "লিমিট শেষ" বার্তা আসবে না:** সব সার্ভার-সাইড অটো-পোলিং
   বন্ধ (§9a) + অ্যাপ এখন ইউজার-চালিত (অ্যাপ বন্ধ থাকলে Firebase-এ শূন্য ট্রাফিক)।
   ভবিষ্যতে কখনো ভারী ব্যবহারে কোটা ছুঁয়ে ফেললেও **প্রতিদিন ১৩:০০ (ঢাকা)**
   রিসেট হয়ে নিজে থেকেই ঠিক হয়ে যায় (নিচের §7.1)।
4. **নতুন (v0.38.0): Location Phase 4** — Tracking ট্যাবে এখন সদস্য-কার্ড
   তালিকা + ৬-ধাপ শেয়ারিং সেটআপ উইজার্ড। খালি অবস্থায় (বা তালিকার একেবারে
   নিচে) **"নতুন স্ক্রিনগুলো আগে দেখুন (ডেমো ডেটা)"** বোতামে ঢুকলে নতুন সব
   স্ক্রিন ডেমো মোডে দেখা যায় — শীর্ষে সবসময় "DEMO DATA — not real locations"
   ব্যানার থাকবে; ব্যানারের ✕ বা ব্যাক-বোতামে বের হয়ে আসুন। Settings →
   Privacy-র লেখাও এখন বাস্তবের সঙ্গে মিলে (শুধু লাইভ লোকেশন — ইতিহাস নয়)।

## 3. Install (both phones)

| | |
|---|---|
| Artifact | `app-release-apk` from any **SUCCESS** run of the *Android Build* workflow (GitHub → Actions). `app-debug-apk` also uploaded. |
| Signing | CI keystore (GitHub Secrets) — **every version over-installs cleanly**; no uninstall needed, data survives. |
| Gate | Since v0.18.0 every release APK passes `scripts/verify-apk-axml.mjs` in CI: versionCode (INT_DEC), versionName, stale-version probe, v2 signing block — build fails otherwise. |
| Retention | Artifacts are kept **7 days** (public-repo pull-scraping protection, v0.21.0 decision). Re-run the workflow to refresh at any time. |
| Sideload | Allow "install unknown apps" for your browser/file manager once; Android will prompt. No Play Store — distribution is family-internal by design. |
| Version history (M-series) | v0.30.0/49 M2 shell · v0.31.0/50 M3 extraction · v0.32.0/51 M4 nested-nav · v0.33.0/52 M5 live location · v0.34.0/53 M8 notifications/a11y/i18n · v0.35.0/54 Pre-M10 Settings polish · v0.36.0/55 quota-directive build (foreground-only presence) · v0.37.0/56 QA & dedupe wave (crash-edge fixes, auth timeouts, parallel latency wins, 14/15 duplicate clusters single-sourced) · **v0.38.0/57 Location master-plan Phase 4 — frontend mock wave: Tracking member cards + 7-state sharing statuses, 6-step consent wizard, journey-history & My-places screens, explicitly-entered DEMO mode (zero Firestore), privacy-policy text corrected to the shipped M5 truth — current** — M6/M7/M9/M10 were docs/infra-only. |

**Post-install regression (5 minutes, both phones):** language বাংলা across the
UI · bell → "Recent events" (persists restart; clears on sign-out) · Settings
five-card order + language dialog · bell → Settings lands at top · keyboard
ride-up · one full ask→grant→share→end round · one location round (if sharing
consented). Full checklists: `docs/TEST_PLAN.md` (device rows) + worklog §71/§73.

## 4. Feature inventory (as shipped in v0.35.0/54)

- **Accounts:** Google Sign-In (Credential Manager) + email/password + reset;
  sign-out hygiene (stops location share, clears notification log, detaches
  listeners).
- **Pairing:** role-typed QR (Family / Guest) + 12-digit single-use code with
  TTL; claim-gated redemption; revoke/remove; guest = time-boxed window with
  server-side bounds.
- **Support sessions:** ask → 2-minute answer window → grant/deny/cancel/end/
  expire; full FSM fences enforced in Firestore rules (side/step/cap/payload);
  the receiver's consent is a UI precondition AND a server precondition.
- **Remote support:** WebRTC view-only screen share (FGS with foreground-first
  start), remote assist = guided taps (`dispatchGesture`) + text
  (`ACTION_SET_TEXT`) with a live on/off indicator; TURN via metered.ca +
  Oracle (runtime-fetched, bounded); signal channel in Firestore with strict
  listener hygiene.
- **Notifications:** localized FCM; tap routes to the pending ask; **local
  persisted events log** (filesDir JSON, cap 100, zero extra Firestore reads,
  cleared at sign-out) on the bell screen. While the app process is alive
  (foreground or tray) asks ring instantly via the event-driven listener; with
  the process dead, asks surface on the next app open (see §9a for the parked
  dead-app push tiers and the re-arm runbook).
- **Live location:** consent sheet + runtime permission + interval choice
  (30 s/1 min/5 min); osmdroid OSM map with attribution, one marker per
  authorized counterpart, honest LIVE/STALE/OFFLINE freshness (an old fix is
  never labeled live); update-in-place single doc per device (no history
  growth); 8-field schema/freshness rules — 12/12 live probe green.
- **Settings (Pre-M10 layout):** Account · Device Management · Privacy &
  Security (location sharing + interval, guest access, permission wizard,
  privacy policy, usage rules) · Preferences (theme trio, **language picker
  dialog**, notification toggle, test probe) · About & session (version +
  red-outlined sign-out last).
- **i18n:** 15 languages (bn/en/ar/de/es/fr/hi/id/ja/ne/pt/ru/tr/ur/zh), 526+
  keys/locale, in-app switch, localized notifications + error pipeline.
- **Accessibility:** content descriptions on interactive elements, ≥48 dp
  targets, large-font tolerant layouts (2-line tile titles), reduced-motion
  honored (Animator durationScale).

## 5. Build from source

```bash
git clone https://github.com/psfaruk/family-remote-support
cd family-remote-support
# CI path (recommended — identical to releases):
#   push to main → .github/workflows/android-build.yml → signed APK artifact
# Local path: Android Studio (JDK 17) or:
./gradlew assembleRelease   # signing via your own keystore.properties
./gradlew assembleDebug     # debuggable build, same Firestore project
```

Stack: Kotlin 2.0.20 · Jetpack Compose (BOM 2024.09.03) · AGP 8.5.2 ·
Navigation-Compose 2.8.2 · Firebase BoM 33.4.0 (Firestore 25.1.0, Auth) ·
osmdroid 6.1.18 · play-services-location 21.3.0 · minSdk 26 / targetSdk 34.
Firestore database id `default` (CI-overridable `FIRESTORE_DATABASE_ID`).
Every round-trip has a 30 s timeout + localized error mapping.

## 6. Architecture at a glance

- **Client:** single Android app, both roles; no server component owned by the
  family; all trust enforced by Firestore rules (published via the project SA,
  `scripts/publish-firestore-rules.mjs`).
- **Firebase (free tier):** Auth · Firestore (ruleset d1a38d5e — probe-verified
  4/4 suites live at M9) · FCM HTTP v1.
- **The app is the ONLY Firebase client (Owner quota directive, §9a):** all
  reads are user actions or event-driven listeners (an idle listener bills 0
  reads); since v0.36.0 the presence heartbeat is foreground-only, so a closed
  app does ZERO Firebase traffic.
- **Cloudflare Worker `family-remote-support`:** deployed but DORMANT — cron
  trigger removed (`wrangler.toml` `crons = []`), runtime Secrets intact,
  `POST /sweep` (KILL_TOKEN) kept for on-demand/diagnostic sweeps. Re-arm =
  one config line + push (§9a).
- **GitHub Actions `kill-push.yml`:** schedule disabled, manual dispatch only.
- **Android Build CI:** unchanged — compile/lint/APK + AXML/signing gate on
  every push.

## 7. Known limitations & operational notes (honest ledger)

### 7.1 Firestore free-tier daily quota — RESOLVED by the quota directive (§9a)

50,000 reads/day shared by everything. The historical exhaustion (evening
`RESOURCE_EXHAUSTED`/429, resetting 07:00 UTC = 13:00 ঢাকা) was root-caused to
the auto-polling server tiers — the sandbox sweep alone, observed running as
TWO instances at 5 s, attempted ~69,000 reads/day (>100% of the quota by
itself). With all auto tiers parked (§9a) the family's measured app-side load
is ~250–400 reads/day (<1%), so quota exhaustion is no longer an expected
behavior. Should extreme future usage ever hit it: the in-app message is an
honest localized one (`err_quota` ×15), and the quota resets at **13:00 ঢাকা**
daily; Firebase Blaze billing lifts the cap if the family ever outgrows it.
Sweep budget by source: `docs/TEST_PLAN.md` §1a (historical estimates) + §9a
(current posture).

### 7.2 Structural / by-design

- **No Play Store** — sideload only; artifacts expire after 7 days (re-run CI).
- **Dead-app ask alerts are PARKED (Owner quota directive, §9a):** with the
  recipient's app process dead, a support ask does not ring the phone; it
  surfaces when the app is next opened (or instantly while the process is
  alive — foreground or tray — via the event-driven listener). Re-arm any
  tier in ~30 s (§9a) if that trade-off is ever missed.
- **App-side instant `POST /sweep` deliberately not implemented** (would leak a
  bearer token inside a public repo — M7 retraction of an earlier doc claim;
  also now moot while tiers are parked).
- **Worker `/health` "last" is per-isolate and now moot** (no cron sweeps); the
  endpoint still verifies liveness + secrets presence.
- **Device-side test rows are Owner-manual** (`docs/TEST_PLAN.md` #1–#3,
  #7–#15, #18–#24 + the M8 checklist) — everything automatable was verified
  live (4/4 probe suites, CI gate, full-history secret scan).
- **FCM token rotation can cold-start a dead process** (`PushTokenService` —
  OS-driven, rare, needed to keep receiving pushes; 1 registry write).
- **Revoke does not end an in-flight ACTIVE session** (§76 audit finding,
  accepted): revocation flips the relationship doc to REVOKED, but a live
  session keeps its p2p video + touch until sessionEndsAt (≤15 min) — rules
  do block NEW signal/cmd writes only after status changes. Ending open
  sessions at revoke time is a recorded follow-up (needs a bounded
  whereIn query or a rules-side rel-status check on signal create).
- **Revoked FAMILY relationship docs are permanent PII residue** (by design:
  family docs are rules-undeletable; only guest docs delete). A revoked
  family pair-key also permanently blocks guest re-pairing in that
  direction — Firebase console is the only escape (documented trade-off).
- **`pairingInvitations` docs persist with raw tokens** (readable by any
  signed-in account — 5-min TTL + single-use make this low-risk, but a
  post-redemption scrub is a recorded follow-up).

### 7.3 Recorded follow-ups (NOT blockers, tracked for honesty)

- **v0.42.0/62 — D11(e) activation wave (worklog §83):** the detail screen's
  section C now renders the LIVE movement card (liveLocations carries the
  optional rules-validated `speedMetersPerSecond`; the engine passes the OS
  fix speed, cleared when missing/glitched; probe 16/16 against the published
  rules; zero extra reads — classifyLive runs on data already in hand).
- **v0.43.0/63 — Phase 11 place alerts (worklog §84):** "My places & alerts"
  is now REAL: rules-first `placeAlerts` block (owner-only in every
  direction, live probe **25/25**, zero residue), pure-Kotlin
  `PlaceAlertEngine` (19 CI unit tests — silent init, ≤radius enter /
  ≥radius+25 m exit hysteresis, per-place cooldown gating emission only),
  the service evaluates fences inside the already-running location FGS on
  fixes already flowing (DR-P2 realized: **zero new permissions**, no
  GeofencingClient, no background location, zero engine Firestore reads),
  local notifications carry the place NAME only (never coordinates, Bengali
  via the app-language system), the places screen persists through Firestore
  (survives reinstall) with a local mirror so monitoring works offline, and
  the 20-place cap keeps the surface bounded. Honest scope, stated on the
  screen: alerts fire only while sharing is on; recipient-facing alerts
  remain a future extension.
- MainActivity prefix-parsed setup strings ("Registered ✓…" — needs flag
  plumbing first; M8 remainder).
- ScreenShareEngine bilingual assist banners + diag lines (Phase-16b style).
- 2 keys deliberately all-English (`feed_last_sub` pure-format,
  `pw_perm_assist`), bn `notif_item_perms` placeholder-order quirk (pre-existing).
- Recent Activity list shows open + last-finished (full history needs a bounded
  query + composite index — quota discipline, M4 decision).
- §76 dedupe remainder (1 of 15 clusters deliberately skipped): the
  last-finished `getSession` one-shot effect still exists in both AddDeviceTab
  and ActivityScreens (different state homes; migrating needs a shared
  session-state owner — revisit only if a third copy appears).
- §76 audit notes (no action): SessionNotifier silently drops listener errors
  (quota windows surface via the other error paths); tracking listeners stay
  registered while the app is backgrounded (foreground-only presence bounds
  the writes; reads ≈5.8 k/day budgeted in TEST_PLAN §1a); map zoom/pan resets
  on tab re-entry/rotation (osmdroid view is non-saveable — accepted).
- **v0.41.1 Phase 8 gate closure + units fix (worklog §82):** the
  Phase 8 live probe gate — pending since v0.39.0 (quota-aborted first
  run) — is now **PASSED**: original 33-case suite scored 32/33 on the
  authoritative rerun (the sole failure was H7, exactly the §80 risk
  disclosure: a retention-only recipient list is denied under strict
  rules semantics because the recipient branch references
  `resource.data.deviceId`, which only a deviceId-equality query can
  bind — the app never issues that shape); H7 was rewritten to the
  app's EXACT D10a day query (deviceId EQUAL + retentionExpiresAt
  > now+5 min + recordedAt day window + composite order + limit 1000)
  WITH a returned-rows assertion (exactly the one in-retention record,
  expired fixture excluded), H15 added as the boundary negative, and
  the rerun passed **35/35 (34 cases, 35 assertions), zero residue** —
  the shipping HistoryScreen query contract verified end-to-end
  through the live rules by an authorized family recipient. The same
  wave's CI run on v0.41.0/60 failed the new unit-test step with 4
  journey-tail failures: the points-based speed math divided by
  milliseconds instead of seconds (1000× low → every LIMITED estimate
  read "possibly stationary"). Fixed in one line, re-verified
  numerically against all 25 cases, shipped as **v0.41.1/61** — the
  gate design working exactly as intended.
- **v0.40.0 ONE-TIME composite index prerequisite — CANCELED 2026-11-01
  (Phase 10, live-verified unnecessary):** Phase 9 recorded that the
  journey-history day queries need the composite index
  `locationHistory` · deviceId ASC · retentionExpiresAt ASC · recordedAt
  ASC — a documented-construction assumption that could not be runtime-
  tested from the sandbox (no device). On the Owner's Phase 10
  instruction the coordinator attempted the creation live: the SA is
  denied `datastore.indexes.create` (IAM role missing; self-escalation
  also denied — `testIamPermissions` holds only databases.get +
  indexes.list). The coordinator then ran the app's EXACT day-query
  shape through the Firestore Admin REST API against a probe
  subcollection with decoy documents: **the query executes correctly
  with ZERO composite indexes** (Firestore serves equality + two
  ranges + matching orderBy via single-field index merge; correct
  subset + correct order verified, decoys excluded). The Owner console
  action is therefore REMOVED — `scripts/create-location-index.mjs`
  ships as the optional perf-optimization tool (idempotent, honest
  exit codes) should day volumes ever want the composite; the
  `hist_index_hint` error ladder stays as harmless defense-in-depth.
  Live-test cost: ~13 writes + ~10 reads + 13 deletes against the
  user-directed instruction (§75-compliant), zero residue.
- **v0.40.0 Phase 9 honest edges (worklog §80):** day chips show the grant's
  retention-window days with honest per-day empties (the spec's
  "days-with-records metadata doc" is not in the published rules — deferred
  with the rules-amendment track); section B renders the action + retention
  line without summary chips (quota-protective — the chips live on the
  journey screen); "delete today" enumerates from the LOCAL write log (the
  rules make expired records unlistable, so the log is the only discovery
  path); design §6.2's server-side cleanup query (`retentionExpiresAt <=
  now`) is UNEXECUTABLE under the published rules (denied by the retention
  floor on every list) — Phase 9 ships the local-log blind-delete variant
  and records the rules-amendment as a future item with its own probe
  cases.

## 8. Verification summary (what "tested" means here)

- **Live behavior probes (M6 + M9 re-run, 4/4 suites green, self-cleaning):**
  pairing round-trip + claim fences · session-FSM audit A0–A21 · negative
  battery P0–N18 + G1/G2 (family↔guest kind fence, guest-window bounds,
  post-END signals, forged fields) · liveLocations 12/12.
- **CI gate on every push:** compile + lint + APK build + AXML/signing
  verification; every milestone closed on a green HEAD run (IDs in worklog).
- **Security review (M9):** full 615-commit history scanned — zero leaked
  secrets (only hits: the Worker's PEM-parsing code literal +
  google-services.json identifier-class key, both by design); workflows
  least-privilege (`contents: read` / `permissions: {}`); manifest posture
  (services `exported=false`, `allowBackup=false`, scoped FileProvider,
  gated accessibility binding); `.secrets` never in-repo.
- **Infrastructure end-to-end:** SA → Firestore REST read 200 (real token
  registry) → FCM send 200 + message_id (labeled test push to the live phone).
- **M10 (this milestone):** the last open infra item closed — see §9.

## 9. M10 — the Cloudflare stable tier, closed

The only open item M9 left was the Worker's `FIREBASE_SA_JSON` runtime Secret.
Resolution (2026-10-31, all evidence captured in worklog §74 and
`docs/CLOUDFLARE_SETUP.md` M10):

1. **Owner unlocked the API token** (edited to allow-all; the M9 IP filter is
   gone — `GET /accounts` now 200 from the same egress IP that was blocked).
2. **Secret set programmatically** via the REST secrets API → **HTTP 201**;
   script settings now show both bindings as `secret_text` (runtime, deploy-
   surviving — same mechanism that kept `KILL_TOKEN` alive through every
   deploy since Phase 14).
3. **`/health` → `FIREBASE_SA_JSON: true`.**
4. **Cron proven firing every minute** (GraphQL `workersInvocationsAdaptive`:
   one success per minute at a fixed second offset) **with 2 subrequests per
   sweep = OAuth token call + Firestore query** — i.e. the SA JSON is parsed,
   signed and accepted. (During the Dhaka-evening quota window the Firestore
   leg returns 429 like every other tier — expected, self-heals at 13:00 ঢাকা.)
5. Post-push re-check (next deploy auto-triggered by this very docs push)
   confirms the secret survives Workers Builds deploys.

**All three kill-push tiers were simultaneously operational — first time
since the permanent tier was built (Phase 14).**

## 9a. Post-release ops change — the Owner quota directive (same day, 2026-10-31)

Hours after M10 closed, the Owner reported the recurring evening
"API limit exhausted" errors and ordered: *read/write only when the user
acts; auto-updates off otherwise; zero requests from the sandbox — requests
only from the app.* Root cause (measured, live-observed): the sandbox
kill-push service was polling Firestore every 5 s **as two zombie instances**
(≈69,000 read attempts/day = >100% of the 50,000/day free quota by itself);
with the Worker's every-minute cron (2,880/day) and the Actions 5-min
backstop (576/day) the auto tiers consumed the quota by early afternoon
daily. Resolution — every auto poller parked:

1. **Sandbox:** both kill-push instances killed; port 3020 closed; zero
   sandbox→Firebase traffic (scripts/probes remain on-demand only).
2. **Cloudflare Worker:** cron trigger removed via `wrangler.toml`
   `crons = []` (explicit so every deploy re-syncs to zero; verified
   `schedules: []` via API after the deploy; secrets still `true`). The Worker
   stays deployed as an on-demand `POST /sweep` endpoint.
3. **GitHub Actions:** `kill-push.yml` schedule commented out — manual
   dispatch only.
4. **App (v0.36.0/55):** an independent audit verified the app has ZERO
timer-driven Firestore reads (all reads are user actions or event-driven
listeners — an idle listener bills 0 reads); the one timer-driven WRITE with
the app closed (the 15-min presence `HeartbeatWorker`) was retired — presence
is now foreground-only (app open + every 5 min while RESUMED + one final
write on ON_STOP), and old installs' persisted periodic work is cancelled at
first v0.36.0 open (the worker class also self-terminates via
`Result.failure`). SessionNotifier's listener got a `.limit(10)` cap.

**Net effect: with both phones' apps closed, the project's Firestore traffic
is exactly zero.** Honest trade-off: dead-app ask alerts are gone while the
tiers are parked (§7.2). **Re-arm runbook (~30 s per tier):** Worker — set
`crons = ["* * * * *"]` in `wrangler.toml` and push to main; Actions —
uncomment the `schedule:` block in `kill-push.yml` and push; sandbox —
`nohup bun run dev` in `mini-services/kill-push-service`. Secrets and the
sweep code are intact in all three, so re-arming needs no credential work.

## 10. Maintenance runbook (what to check, when)

| When | Check | Where | Healthy looks like |
|---|---|---|---|
| Nothing routine | — | — | Zero automatic Firebase traffic by design (§9a); nothing runs unattended except the Android Build CI on pushes. |
| "Ask didn't arrive" | Is the recipient's app alive? | their phone | Process alive (foreground/tray) → listener rings instantly; process dead → ask shows on next open — that IS the parked-tiers trade-off (§7.2) |
| | Want dead-app alerts back? | §9a re-arm | Worker: `crons = ["* * * * *"]` in wrangler.toml + push (deploy re-arms in ~a minute) |
| | Worker sanity | `https://family-remote-support.www-farukkhan-fk885.workers.dev/health` | `ok:true`, both secrets `true` |
| "Build broken?" | Android Build workflow | GitHub → Actions | green on `main`; the AXML gate fails loudly on version/signing drift |
| "Location stale" | Tracking tab | in-app | freshness label honest (LIVE <2 min); check consent + interval + battery optimization |
| "Presence shows Last seen" | counterpart actually used their phone recently? | in-app badge | By design since v0.36.0: "Online" decays ~20 min after the counterpart last used the app (foreground-only heartbeats) |
| Token hygiene | CF API token | dash.cloudflare.com → My Profile → API Tokens | stays permission-scoped; rotate by editing + re-set the Secret via the same API path |
| Firestore rules | `firestore.rules` | repo | published by SA script; probe battery re-runnable (`scripts/rules-*-probe.mjs`) |
| On-demand sweep | Actions → kill-push → Run workflow (or Worker POST /sweep) | manual only | one SUCCESS run; useful as a diagnostic after an unusual incident |

## 11. Milestone ledger (M0–M10)

| M | One line | Build |
|---|---|---|
| M0 | 3-agent repository audit + state verification | docs |
| M1 | 9-doc architecture/planning set (Owner-approved gate) | docs |
| M2 | Navigation-Compose shell, tab rename, notifications surface | v0.30.0/49 |
| M3 | Screen extraction + ViewModel layer + component library | v0.31.0/50 |
| M4 | Nested nav wave-1 + Settings partitions + drift fixes | v0.32.0/51 |
| M5 | Live location: engine, OSM map, rules, 12/12 probes | v0.33.0/52 |
| M6 | Family/Guest authorization verified (G1–G4) | docs |
| M7 | Remote-support integration audit — zero regressions; 3 honest findings | docs |
| M8 | Notifications store (G2), a11y, i18n completion, perf | v0.34.0/53 |
| M9 | TEST_PLAN auto rows live 4/4 + security review + quota budget | docs |
| Pre-M10 | Owner polish: Settings 5-card layout, language dialog, tab/keyboard fixes | v0.35.0/54 |
| M10 | Release readiness: this file, README/STATUS updates, CF stable tier closed | docs |
| Post-M10 | Owner quota directive: auto-polling parked (sandbox/Worker/Actions) + foreground-only presence | **v0.36.0/55** |
| §76 QA wave | Owner's full back-end test order: 6 parallel audits + secrets scan + VLM screenshots; 2 crash-edge fixes, auth hardening, parallel latency wins, 14/15 duplicate clusters single-sourced, dead code + 315 MB sandbox data removed, preview refreshed | **v0.37.0/56** |

Full narratives: `worklog.md` §44–§75 (append-only, honest deltas included) and
`docs/PHASE-0.md` for the pre-master-plan era.

---

## 12. v0.45.0/65 — the audit-fix wave (P1–P14)

A deep static audit of v0.44.0 produced 14 findings (P1–P14). This release
implements every fix that could land without new owner-held secrets; the
per-finding status:

| # | Finding | Status |
|---|---|---|
| P1 | Ask never reaches a killed recipient app (sweeps parked, 2-min TTL) | **Fixed** — event-driven push: the writing app calls the Worker's new `POST /notify` with a short-lived Firebase ID token; the Worker verifies the token (tokeninfo + aud/iss/exp), re-reads the session server-side, and sends a HIGH-priority FCM **data** message; `PushTokenService.onMessageReceived` builds the localized notification with Allow/Deny. TTL 2 → 4 min (rules ceiling is 5). Needs the one-time `config/notify` doc (below). Force-stopped apps remain unreachable — that is an Android platform rule. |
| P2 | Stationary phone shows OFFLINE after 5 min | **Fixed** — stationary heartbeat merges `updatedAt`-only every ≥ 4 min (rules-compatible merge); freshness windows LIVE < 5 min / STALE < 15 min. |
| P3 | Sharing dies silently on OEM kills | **Partially fixed** — the service's unexpected-stop path (onDestroy with sharing still enabled + user present) posts a "sharing stopped — tap to restart" notice with a Tracking deep link. Boot-restart remains a deliberate privacy decision (§5). |
| P4 | Notifications land nowhere, no actions, dead asks linger | **Fixed** — one `AskNotifier` factory for the listener path AND the FCM data path: deep-link content intents (`DeepLinks` + singleTop + onNewIntent), Allow/Deny action buttons (`SessionActionReceiver` runs the same rules-checked transactions), `setTimeoutAfter(deadline)`, CATEGORY_MESSAGE. |
| P5 | Map gesture conflict, jumpy zoom, camera reset | **Fixed (code-side)** — `NestableMapView` claims the gesture stream inside the scrolling column; animated `zoomIn()/zoomOut()`; camera (lat/lng/zoom) now `rememberSaveable`; co-located pins no longer slam to max zoom. The full-height map + bottom-sheet layout and the Google-Maps-engine migration remain open design decisions. |
| P6 | Security & release posture | **Mostly fixed** — R8 ON with conservative keep rules; CI hard-fails when the release keystore secret is missing (no more debug-signed releases); legacy 6-char pairing codes retired; **revoke now ENDS the pair's live sessions** (rules-allowed transitions). TURN ephemeral credentials remain open (needs the provider's API decision — see §7.2). |
| P7 | Deadline math trusts the phone's clock | **Fixed** — `ServerClock` estimates server-time offset from snapshots the app already receives (ask createdAt / FCM sentAt); every deadline comparison and the ask write itself use the corrected clock. |
| P8 | Notification Center is not a real center | **Fixed** — events recorded at the REAL event time (`recordAt`); place alerts, denials and cancellations are logged; a (type, sessionId) dedupe keeps the listener path and the FCM path from double-writing. |
| P9 | Language inconsistencies | **Fixed in-app** — location-sharing channel name/description, "Registering this device…", and the Settings test notification (which also stopped renaming the session channel) are all localized; new keys ship EN + BN (other locales fall back to EN per the Phase-4 precedent). The stored bilingual SessionRepo block messages and the "bn first-run" default remain open (the English-first default is a recorded Owner decision). |
| P10 | Font sizes below senior-first guidance | **Fixed** — body/label scale restored to body 16 / bodyMedium 15 / bodySmall 14 / labels 14–13 sp; titles stay compact per the v0.19 Owner fit request. |
| P11 | Settings-tab crash, cause unknown | **Root-caused via CrashJournal** — `private` top-level ViewModels (`LocationDetailViewModel`, `HistoryViewModel`, `PlacesViewModel`) crash at reflection instantiation (`IllegalAccessException`); all three are public now, with guard comments. If a Settings-specific crash remains, capture the new stack. |
| P12 | Permanent 2.5 s polling, no R8, huge files | **Partially fixed** — the permission-checklist loop is now RESUMED-only (`repeatOnLifecycle`); R8 ON. The big-file split remains a maintenance task. |
| P13 | Emails instead of names | **Partially fixed** — local nicknames (relId → "আব্বু/আপু…") editable on the member detail screen, used by the member title and every session notification; zero Firestore cost. Card/list surfaces migrate in a later wave. |
| P14 | CARTO/Esri tile terms | **Open** — unchanged family-scale fair-use posture; re-verify before public distribution or move to the Maps SDK. |

### One-time owner setup for P1 (event-driven push)

1. Deploy the Worker (push to main auto-deploys via Workers Builds) — it now
   exposes `POST /notify`.
2. In Firestore create `config/notify` = `{ "url": "https://<your-worker>.workers.dev" }`
   (the exact `*.workers.dev` URL from the Cloudflare dashboard). Every phone
   picks it up within 10 minutes; without the doc the feature stays safely
   off and behaviour matches v0.44.
3. Secrets unchanged: `FIREBASE_SA_JSON` (already set) does the FCM send.

No new permissions, no new Firestore rules, no background listeners — the
notify path costs ~2–3 reads per ask.

---

## 13. v0.46.0/66 — the G1 notification-center wave

The external-audit response (`docs/EXTERNAL_AUDIT_RESPONSE.md`, worklog §88)
verified every claim of the outside report against the source and found
exactly ONE significant genuine gap: the notification center's missing
read/unread state, category filters, row routing and user-facing clear.
This release ships that upgrade — LOCAL-ONLY by design (zero new Firestore
reads/listeners/writes, rules untouched, no new permissions):

| G1 item | What shipped |
|---|---|
| Read/unread state | `NotifEvent.readAt` (schema v2 — a v1 file reads back all-unread, the honest default); tap marks read (a re-tap never re-stamps); "Mark all as read" (hidden while nothing is unread); the bell badge shows the unread count, capped at 99 (a pending consent ask keeps its plain dot — action beats count) |
| Category filters | All / Unread / Connection / Location / Security chips, mapped in ONE pure place (`NotifCenter.kt`): session lifecycle → Connection, denied/cancelled → Security, sharing/place → Location; an unknown future event type stays visible under All only, never mis-filed |
| Row routing | Every Recent-events row is tappable → the event's screen (ask → Home, session outcomes → `home/sessions/{id}`, sharing → Tracking, place alert → My places; unknown → Home, never a dead end) via the DeepLinks dispatcher's own route strings, now shared as `AppNav.goRoute` so an in-app tap and a system-notification tap behave identically |
| Clear history | User-facing, behind a confirmation dialog; the sign-out wipe remains a separate hygiene path (`clearHistory` vs `clear` — two intents, two entry points) |
| Bonus | Event stamps gained their DATE half (`getDateTimeInstance`) — a 100-row history spans days; time-only would misread old rows as today's |

Tests: `NotifCenterTest` — 21 new pure-JVM cases (category mapping, filter
algebra, read-state transitions incl. no-re-stamping and no-reordering,
route mapping with literal route strings as an `AppRoutes` guard). Local
gate: 66/66 unit tests green + `assembleDebug` OK (the first full Gradle
gate inside the sandbox — Android SDK 34 + Gradle 8.9 + Temurin 17, the
CI-identical toolchain); `assembleRelease` stays the CI runner's job (the
4 GB sandbox OOM-killed it twice — R8 + lintVital together exceed the
ceiling). Strings: 14 new keys ×15 locales.

Owner device checklist: bell count → open center → five chips (horizontally
scrollable) → unread rows carry a "New" pill → tap a row (navigates AND
marks read) → "Mark all as read" clears the pills → "Clear history" →
confirm → section empties → sign-out still wipes everything, as before.

## 14. v0.47.0/67 — the audit-fix wave 2 + build-rescue wave (2026-11-01)

The deep-audit report (§90) shipped its P0/P1 items; the wave's own build breakage
(rescued in §91) is documented with the same honesty:

| Item | What shipped |
|---|---|
| Notification matrix Gap-1..5 | pairing_invited/approved, family_revoked / guest_removed / rel_revoked, guest_expiring_soon / guest_expired, sharing_perm_lost, presence_online/offline — all recorded from EXISTING listeners (zero extra Firestore reads, §75-compliant), each with a NotifCenter category + row route; 10 new event strings EN+BN |
| In-app event banner | EventBannerBus (StateFlow) + EventBannerHost: one routed, auto-dismissing (6 s), category-colored banner per fresh event, 2-min freshness window (cold-start never replays); session_asked is excluded — the consent card is the hero |
| B1 | AskNotifier.setTimeoutAfter now computes against `ServerClock.now()` — a manual-clock phone no longer kills the tray ask early/late |
| B2 | SessionActionReceiver's goAsync work is bounded (`withTimeoutOrNull(9 s)`); a timeout posts the honest "open the app to answer" result instead of risking the ~10 s broadcast window |
| B3 | SessionPush transient /notify failures enqueue a WorkManager retry (network-constrained, exponential 10 s backoff, ≤3 attempts, survives process death); config-OFF never retries |
| B4 | SessionNotifier caches pending asks every snapshot; `App.onActivityStopped(→0)` re-delivers — the background-transition hole (ask seen while app open, no tray entry after going home) closed |
| P13 complete | Nicknames in member cards, home hints, consent card/dialog, activity feed, notification rows, tracking cards, detail screens, sharing sheet |
| M1 / M5 / BE5 / P9 | Terrain chip honest relabel ("3D look"); Settings demo-mode shortcut; Settings "Dead-app alerts" ON/OFF/UNKNOWN status row (the config/notify honesty fix); fresh installs default to Bangla |

**The build rescue (§91):** the wave initially broke the build in three stacked ways
that a toolchain-less sandbox could not see. CI run #864 failed at
`mergeReleaseResources` with a misleading AGP 8.5.2 message ("Invalid unicode escape
sequence … `{str}`") — decompiling `aaptcompiler-8.5.2.jar` proved the real cause:
`privacy_body`'s three unescaped apostrophes (from the merged privacy-disclosure PR;
the Kotlin aaptcompiler port hardcodes a wrong message + a broken string-template in
that branch — C++ aapt2 says "unescaped apostrophe"). Behind it waited three Kotlin
compile errors (duplicate companion object in `SessionActionReceiver`, six duplicate
imports in `MainActivity`, unqualified `notifyBlocking` in `SessionPushWorker`) and
the forgotten version bump. All fixed; the full CI-identical gate (Temurin 17,
Gradle 8.9, SDK 34) now runs green in-sandbox: **70/70 unit tests** (25 + 25 + 20),
`assembleDebug` 46.8 MB, `assembleRelease` (R8) 32.9 MB unsigned + AXML 67/0.47.0 —
the signature check remains the CI runner's job (keystore secret).

**Owner next steps:** watch this push's CI run go green, download the release APK
artifact, and run the two-phone §90 checklist (killed-app ask → tray Allow; open-app
ask → Home press → tray; the new event types in the Center + banners; Settings rows).
