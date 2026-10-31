# RELEASE_NOTES — Family Remote Support — M10 Final Report (2026-10-31)

> **Release state: v0.35.0 (versionCode 54)** · repo `psfaruk/family-remote-support`
> (public, `main`, CI green at every milestone close) · master-plan milestones
> **M0–M10 all ✅** plus the Owner's Pre-M10 polish pass. This is the
> release-readiness document the master plan requires: what ships, how to install
> and build it, what is verified, what is known-limited, and how to keep it running.
>
> **⚠ Post-release ops change (same day, Owner quota directive — see §9a):**
> all automatic server-side Firestore polling is PARKED (sandbox stopped,
> Worker cron removed, Actions schedule disabled) and the app itself became
> foreground-only for presence in **v0.36.0 (versionCode 55) — the current
> release build.** The quota math and re-arm runbook are in §9a and
> `docs/CLOUDFLARE_SETUP.md` (কোটা-নির্দেশ section).

## 1. What this is

Android-to-Android remote support for families: a trusted family member helps fix
another family phone — live **view-only screen share**, **guided remote taps and
typing** (with the receiver watching a live on/off indicator), **high-priority
"support ask" alerts that arrive even when the app process is dead**, and
**consent-based live location** — always with the receiver's explicit permission,
enforced server-side by Firestore rules, in **15 languages**. Two roles of trust:
permanent **Family** and time-boxed **Guest** pairing.

## 2. Owner quick start (বাংলা — সংক্ষেপ)

1. **দুটো ফোনেই v0.36.0 (versionCode 55) বসান** — GitHub repo → Actions → সবুজ
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

## 3. Install (both phones)

| | |
|---|---|
| Artifact | `app-release-apk` from any **SUCCESS** run of the *Android Build* workflow (GitHub → Actions). `app-debug-apk` also uploaded. |
| Signing | CI keystore (GitHub Secrets) — **every version over-installs cleanly**; no uninstall needed, data survives. |
| Gate | Since v0.18.0 every release APK passes `scripts/verify-apk-axml.mjs` in CI: versionCode (INT_DEC), versionName, stale-version probe, v2 signing block — build fails otherwise. |
| Retention | Artifacts are kept **7 days** (public-repo pull-scraping protection, v0.21.0 decision). Re-run the workflow to refresh at any time. |
| Sideload | Allow "install unknown apps" for your browser/file manager once; Android will prompt. No Play Store — distribution is family-internal by design. |
| Version history (M-series) | v0.30.0/49 M2 shell · v0.31.0/50 M3 extraction · v0.32.0/51 M4 nested-nav · v0.33.0/52 M5 live location · v0.34.0/53 M8 notifications/a11y/i18n · v0.35.0/54 Pre-M10 Settings polish · **v0.36.0/55 quota-directive build (foreground-only presence — current)** — M6/M7/M9/M10 were docs/infra-only. |

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

### 7.3 Recorded follow-ups (NOT blockers, tracked for honesty)

- MainActivity prefix-parsed setup strings ("Registered ✓…" — needs flag
  plumbing first; M8 remainder).
- ScreenShareEngine bilingual assist banners + diag lines (Phase-16b style).
- 2 keys deliberately all-English (`feed_last_sub` pure-format,
  `pw_perm_assist`), bn `notif_item_perms` placeholder-order quirk (pre-existing).
- Recent Activity list shows open + last-finished (full history needs a bounded
  query + composite index — quota discipline, M4 decision).

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

Full narratives: `worklog.md` §44–§75 (append-only, honest deltas included) and
`docs/PHASE-0.md` for the pre-master-plan era.
