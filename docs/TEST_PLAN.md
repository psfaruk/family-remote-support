# TEST_PLAN — Master-Plan PART 13 (Phase 21 planning)

> Status: **PLANNING — awaiting Owner approval.** Honest environment statement first.

## 0. What can actually be verified here (no deception)

| Environment | Can do | Cannot do |
|---|---|---|
| Sandbox (remote) | static analysis, scripts, Contents-API pushes, readback verify, Firestore rules probes against the live project (SA path), Worker/health checks | run Gradle builds, emulators, physical devices |
| GitHub Actions CI | full `assembleDebug/Release`, AXML gate (versionCode/versionName/v2 signing/stale strings), artifacts | interactive testing |
| Owner's 2 phones | the ONLY real device test surface (install, UX, permissions, location, notifications, OEM behavior) | — |

"Implemented" claims in docs = code exists + CI green + (where applicable) probe-verified.
"Tested on device" is claimed **only** by the Owner's own runs (recorded in worklog).

## 1. Existing automated verification (kept, extended)

- CI gates: build + `scripts/verify-apk-axml.mjs` (version assert, stale-version probe, v2 signing).
- Rules probes (live-project, self-cleaning): `rules-negative-probe.mjs` (16 hostile cases),
  `rules-audit-probe.mjs`, `rules-pairing-probe.mjs` (positive round-trip),
  **`rules-liveLocations-probe.mjs` (M5, 12 cases — RUN LIVE 2026-10-31: 12/12 PASS:
  deny stranger/sharing-off/revoked/post-window-guest/forged-fields/out-of-range/
  stale-timestamp/impersonated-write/list; allow ACTIVE counterpart + live guest)**.
- Ops checks: Worker `/health`, sweep timing, quota-aware backoff state.

**New probes (M5/M9):** `rules-liveLocations-probe.mjs` — **✅ shipped AND run live in M5
  (12/12)**: deny-before-pairing / deny-sharing-off / deny-revoked / deny-guest-after-window /
  allow-ACTIVE-counterpart / deny-forged-fields-or-stale-updatedAt; sweep-exclusion verified
  by code inspection (worker queries only supportSessions); quota-budget probe (documented
  reads during a scripted day) — **delivered in M9 as §1a** (estimate-by-source table +
  observed 429 signature; the metering APIs are denied to the SA, honestly recorded).
- **M6 (2026-10-31): FULL battery re-run LIVE — 4/4 green** (pairing · audit A0–A21 ·
  negative 16-hostile Family/Guest · liveLocations 12/12) — the Family/Guest authorization
  verification milestone; recorded in worklog §69. APK unchanged (v0.33.0/52).
- **M7 (2026-10-31): remote-support integration audit** — 2 real parallel read-only agents
  (M7-A WebRTC/session/signaling · M7-B assist/FCM-routing/kill-push) + coordinator
  verification: all chains **INTACT** (code-level; zero refactor regressions). Ops findings:
  sandbox instant tier re-synced + restarted (stale-script regression fixed in-sandbox);
  GH Actions backstop last run SUCCESS (3 prior failures = Firestore 429 quota, verified
  from logs — self-healing, not code); Worker `/health` FIREBASE_SA_JSON still `false`
  (permanent tier remains Owner-blocked). CLOUDFLARE_SETUP.md false "app-side instant call
  shipped v0.19.0" claim retracted. Docs-only; APK unchanged (v0.33.0/52). Device-side
  remote-support scenarios **#19/#20/#21 remain Owner manual** (M7 verified code-level
  only). Recorded in worklog §70.
- **M8 (2026-10-31, v0.34.0/53 — CODE milestone, reinstall both phones):** notifications
  local persisted events store (G2) + accessibility labels + i18n completion (default↔bn
  inversion fixed: 209 swaps; 74 new keys ×15; 526 keys per locale, verification matrices
  PASS) + per-second recomposition isolated (TickingBlock). New device checks for the
  Owner: (a) language বাংলা across the UI; (b) bell → "Recent events" section shows real
  events and survives app restart; (c) sign-out clears the events list; (d) session/share
  flows unchanged otherwise. Recorded in worklog §71.
- **M9 (2026-10-31): TEST_PLAN execution + security review — every AUTO row re-run LIVE,
  4/4 suites green** (pairing P1a/P1b + N1/N2 · audit A0–A21 · negative P0–N18 + G1/G2 ·
  liveLocations 12/12 — the same battery as M6; zero drift; synthetic fixtures
  self-cleaned). Ops checks: instant tier sweeping every 5 s with 0 errors (script-sync
  verified by md5); GH Actions backstop verified by MANUAL DISPATCH — run **41203101630
  SUCCESS** (its 3 prior schedule failures = Firestore 429 free-quota, self-healing after
  the daily 07:00 UTC reset, logs verified in M7; schedule re-registered by touching the
  workflow comment); Worker `/health` still `FIREBASE_SA_JSON:false` — **new M9 finding:**
  the Owner's Cloudflare API token is **IP-filtered** (HTTP 9109 from the sandbox egress,
  which itself drifts IPs), so the runtime Secret cannot be set programmatically yet;
  unlock = remove the token's Client IP Address Filtering (exact steps in
  CLOUDFLARE_SETUP.md M9) — the last open infra item. Firebase verified END-TO-END:
  SA → Firestore REST read 200 (fcmTokens registry, 3 real device tokens, newest
  2026-10-31T10:31Z) → FCM HTTP v1 send **200 + message_id** (one labeled test push to
  the live token). Security review (full 615-commit history scan): zero secrets — the only
  pattern hits are the Worker's PEM-parsing code string (`pemPkcs8ToDer`) and
  google-services.json's API key (Android identifier-class, by design; console
  key-restriction remains an Owner console check — the SA cannot read the Keys API, 403,
  honestly recorded). Workflows least-privilege re-checked (`contents: read` /
  `permissions: {}`), APK artifacts 7-day retention, manifest posture re-checked (services
  `exported="false"`, `allowBackup="false"`, scoped FileProvider, gated accessibility
  service). Quota budget documented in §1a (metering APIs denied to the SA — estimate
  table + observed 429 signature). **No app code changed (one workflow comment touch +
  docs); APK unchanged (v0.34.0/53).** Owner device rows remain the Owner's (§4
  quick-script stands; M8's reinstall instructions unchanged). Recorded in worklog §72.

### 1a. Quota budget (M9 discipline — documented estimate, not metered)

> **⚠ POSTURE CHANGE (2026-10-31, post-M10 Owner quota directive — supersedes the
> tier rows below):** every auto Firestore poller is PARKED (sandbox killed /
> Worker cron removed via `wrangler.toml` `crons = []` / Actions schedule disabled)
> and the app's presence heartbeat became foreground-only in v0.36.0/55 — with both
> phones' apps closed the project's Firestore traffic is exactly zero. The table
> below is kept as the historical M9 estimate. **Correction discovered by the
> directive wave:** the "≈0 idle" tier rows are BILLING-true (billed reads count
> returned documents) but **quota-false — the free tier's daily read-units counter
> counts every query operation even when it returns nothing** (live proof: the
> sandbox sweeps at 5 s ran head-first into 429 "Free daily read units" with an
> empty result set, as two zombie instances ≈ 69k query-attempts/day = >100% of the
> 50k/day quota alone). That misread is why the exhaustion kept recurring despite
> the idle sweeps "costing nothing". Full record: RELEASE_NOTES §9a + worklog §75.

Honest limit: the SA's IAM roles deny `monitoring.metricDescriptors.list` (403) and the
Keys API is disabled in the project — billed reads cannot be metered from the sandbox.
Budget by source instead (Firestore free tier: 50 k reads / 20 k writes / 20 k deletes /
20 k updates per day; resets 07:00 UTC = 13:00 Dhaka):

| Source | Cadence (steady state) | Reads/day | Writes/day |
|---|---|---|---|
| Instant tier sweep (sandbox :3020) | every 5 s → 2 runQuery (REQUESTED + ACTIVE) | ≈0 idle (Firestore bills only returned docs); +~1 read per live ask/token lookup | idempotent marks only on fresh asks |
| Worker tier (once the Secret is set) | every 60 s → same 2 queries | ≈0 idle (same billing rule) | same marks (idempotent) |
| GH Actions backstop | sporadic (0–5 scheduled runs/day observed) | ≈ same per run | — |
| Session watch (per signed-in phone) | 1 onSnapshot listener | 1 read per session doc delivered (event-driven) | — |
| Presence heartbeat (per phone) | WorkManager 15-min floor (Doze may defer) | 1 read on the counterpart's presence listener per beat | ≤ 96/day/phone |
| Live location (while sharing) | ≥ 30 s–5 min AND ≥ 25 m movement (acceptance gates) | 1 read per fix on the counterpart's listener | bounded by the gates |
| Probe hygiene (M9 battery) | self-cleaning | ~50 synthetic docs created + deleted today | included |

Observed failure signature (matches the table): the 429s land in the Dhaka evening
(17:57 / 22:13 / 01:39 UTC runs) as listener/heartbeat traffic accumulates through the
day, and self-heal after the 07:00 UTC reset (the first run after reset: SUCCESS 07:31
UTC). Phase 20's 30 s sweep + backoff keeps the SERVER sweep cost near zero when idle;
the dominant daily cost is the app's own listener/heartbeat traffic — bounded by the
two-phone reality of this family.

## 2. Scenario matrix (master list → method)

| # | Scenario | Method |
|---|---|---|
| 1 | Clean install | Owner manual (both phones) |
| 2 | Auth success/failure (Google, email, reset) | Owner manual; code-reviewed |
| 3 | Empty Home dashboard | Owner manual (fresh account state) |
| 4 | Family QR pairing success | Owner manual (already routine); pairing probe (auto) |
| 5 | Expired/reused pairing code | rules enforce TTL+single-use (probe 403/409); Owner manual expiry case |
| 6 | Guest session create + expiry | rules window + sweeps (auto); Owner manual |
| 7 | Bottom-nav switching | Owner manual; W1 code review |
| 8 | Nested navigation + back behavior | Owner manual (per NAVIGATION_MAP §3) — **M4 (v0.32.0): REAL nested routes exist now**: Home → Family Devices → member row → Member Details → back (row button AND system back); Home → Recent Activity → session row → Session Details → back; Home tab stays selected on nested routes; add/* stays modal (Back dismisses the sheet/dialog without leaving the flow) |
| 9 | Light/dark themes | Owner manual (screenshot pairs) |
| 10 | Location permission granted | Owner manual (M5) |
| 11 | Location permission denied | Owner manual (M5, explanation state) |
| 12 | Approximate-only location | Owner manual (M5; accuracy shown) |
| 13 | Owner stops sharing | Owner manual + probe (doc deleted) |
| 14 | Device offline | Owner manual (airplane mode) |
| 15 | Stale location | Owner manual (clock/Δt) — badge states |
| 16 | Unauthorized location request | **auto**: liveLocations probe (must 403) |
| 17 | Revoked device access | auto probe + Owner manual |
| 18 | Background-restricted updates | Owner manual (swipe-away/OEM) — honest degraded UX |
| 19 | Screen-share permission denied/revoked | Owner manual (existing flow) |
| 20 | Remote-interaction limits | Owner manual (existing flow) |
| 21 | Notification permission denied | Owner manual (existing in-app fallback) |
| 22 | Network interruption + recovery | Owner manual; backoff verified server-side |
| 23 | Large text / accessibility | Owner manual (font scale 1.3×, TalkBack spot checks) |
| 24 | App restart / state restoration | Owner manual (tab/stack restore) |

## 3. Regression discipline (already the repo's culture, kept)

- Version-locked installs: both phones always on the same build; AXML gate refuses drift.
- Every push = readback-verified; CI must be SUCCESS before Owner installs.
- Each wave ends with: files-changed list, features preserved list, CI run id, known limits —
  posted to the Owner (master PART 17 reporting format).

## 4. Owner's Bengali quick-script (posted per release when relevant)

প্রতি রিলিজে সংক্ষিপ্ত ধাপ: (১) দুই ফোনে একই ভার্সন ইনস্টল → (২) Home-এ ডিভাইস তালিকা ও
presence দেখা → (৩) QR দিয়ে একটি নতুন পেয়ারিং → (৪) সেশন শুরু/শেষ → (৫) [M5] Tracking-এ
শেয়ারিং চালু/বন্ধ, ম্যাপে অন্য ফোনের লোকেশন ও stale/offline ব্যাজ → (৬) revoke করে অ্যাক্সেস
বন্ধ প্রমাণ → (৭) ডার্ক/লাইট থিম ও ভাষা বদল। ফলাফল worklog-এ লেখা হবে।

## 5. Definition of "done" for the master plan

All TEST_PLAN rows executed by their method; probes green on the live project; CI SUCCESS on
final HEAD; docs (IMPLEMENTATION_STATUS.md) match reality; no undisclosed known regression.
