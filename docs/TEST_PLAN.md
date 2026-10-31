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
  reads during a scripted day) remains an M9 discipline.
- **M6 (2026-10-31): FULL battery re-run LIVE — 4/4 green** (pairing · audit A0–A21 ·
  negative 16-hostile Family/Guest · liveLocations 12/12) — the Family/Guest authorization
  verification milestone; recorded in worklog §69. APK unchanged (v0.33.0/52).

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
