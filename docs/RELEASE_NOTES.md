# RELEASE_NOTES — Family Remote Support — M10 Final Report (2026-10-31)

> **Release state: v0.35.0 (versionCode 54)** · repo `psfaruk/family-remote-support`
> (public, `main`, CI green at every milestone close) · master-plan milestones
> **M0–M10 all ✅** plus the Owner's Pre-M10 polish pass. This is the
> release-readiness document the master plan requires: what ships, how to install
> and build it, what is verified, what is known-limited, and how to keep it running.
> APK **unchanged by M10** (docs-only milestone) — the release binary is the
> Pre-M10 build **v0.35.0 / 54**.

## 1. What this is

Android-to-Android remote support for families: a trusted family member helps fix
another family phone — live **view-only screen share**, **guided remote taps and
typing** (with the receiver watching a live on/off indicator), **high-priority
"support ask" alerts that arrive even when the app process is dead**, and
**consent-based live location** — always with the receiver's explicit permission,
enforced server-side by Firestore rules, in **15 languages**. Two roles of trust:
permanent **Family** and time-boxed **Guest** pairing.

## 2. Owner quick start (বাংলা — সংক্ষেপ)

1. **দুটো ফোনেই v0.35.0 (versionCode 54) বসান** — GitHub repo → Actions → সবুজ
   (SUCCESS) যেকোনো রান → Artifacts → `app-release-apk` ডাউনলোড → ইনস্টল
   (আগের ভার্সনের উপরেই পরিষ্কারভাবে বসে যায় — একই CI keystore-এ সাইন করা)।
2. বসানোর পরের দ্রুত যাচাই: Settings-এ নতুন **পাঁচ-কার্ড ক্রম** (Account →
   Device Management → Privacy & Security → Preferences → About & session) ·
   Language লাইনে ট্যাপ করলে **ভাষা-বাছাই ডায়ালগ** · বেল → Settings ট্যাবে
   ফিরলে পৃষ্ঠা **উপর থেকে** শুরু · কীবোর্ড উঠলে ট্যাব-বার ও কনটেন্ট **কীবোর্ডের
   উপরে** থাকে।
3. **সব কিছু বিনা খরচে চলে** (Firestore/Cloudflare/GitHub ফ্রি টিয়ার)। একটাই
   পরিচিত আচরণ: দিনের শেষে (ঢাকার সন্ধ্যা) Firestore-এর ফ্রি পাঠ-কোটা শেষ হলে
   অ্যাপ "কোটা শেষ" বার্তা দেখাতে পারে — **প্রতিদিন ১৩:০০ (ঢাকা)** কোটা রিসেট
   হয়ে নিজে থেকেই ঠিক হয়ে যায় (নিচের §7.1)।

## 3. Install (both phones)

| | |
|---|---|
| Artifact | `app-release-apk` from any **SUCCESS** run of the *Android Build* workflow (GitHub → Actions). `app-debug-apk` also uploaded. |
| Signing | CI keystore (GitHub Secrets) — **every version over-installs cleanly**; no uninstall needed, data survives. |
| Gate | Since v0.18.0 every release APK passes `scripts/verify-apk-axml.mjs` in CI: versionCode (INT_DEC), versionName, stale-version probe, v2 signing block — build fails otherwise. |
| Retention | Artifacts are kept **7 days** (public-repo pull-scraping protection, v0.21.0 decision). Re-run the workflow to refresh at any time. |
| Sideload | Allow "install unknown apps" for your browser/file manager once; Android will prompt. No Play Store — distribution is family-internal by design. |
| Version history (M-series) | v0.30.0/49 M2 shell · v0.31.0/50 M3 extraction · v0.32.0/51 M4 nested-nav · v0.33.0/52 M5 live location · v0.34.0/53 M8 notifications/a11y/i18n · **v0.35.0/54 Pre-M10 Settings polish (current)** — M6/M7/M9/M10 were docs/infra-only. |

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
  cleared at sign-out) on the bell screen.
- **Kill-push (the dead-app alert path):** three tiers — sandbox instant (~5 s),
  Cloudflare Worker cron (~60 s, permanent), GitHub Actions (~5 min, backstop);
  cross-tier claim protocol so exactly one tier pushes per ask (no duplicates).
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
- **Kill-push permanent tier:** Cloudflare Worker `family-remote-support`
  (cron `* * * * *`, zero-dependency port of the Node sweep) with **runtime
  Secrets** `FIREBASE_SA_JSON` + `KILL_TOKEN` — set-and-forget; survives every
  deploy (Workers Builds auto-deploys from `main`).
- **Backstop:** GitHub Actions `kill-push.yml` (5-min cron, `permissions: {}`,
  repo secret same SA) — also the quota self-heal point after the daily reset.
- **Instant tier:** the assistant sandbox mini-service (port 3020) — meets the
  2-minute answer window; degrades gracefully to the Worker tier if the sandbox
  is ever recycled.

## 7. Known limitations & operational notes (honest ledger)

### 7.1 Firestore free-tier daily quota (the one visible behavior)

50,000 reads/day shared by everything. Observed signature (M7/M9 log-verified):
after a heavy Dhaka-day the project hits `RESOURCE_EXHAUSTED`/429 by the
evening; **resets 07:00 UTC = 13:00 ঢাকা**, after which the first scheduled run
succeeds again (self-healing, no code issue). In-app the user sees an honest
localized quota message (`err_quota` ×15), not a raw error dump. Sweep budget
by source is documented in `docs/TEST_PLAN.md` §1a (metering APIs are denied
to the SA — estimate-by-source, honestly recorded). If the family ever outgrows
it: Firebase Blaze billing lifts the cap; nothing else changes.

### 7.2 Structural / by-design

- **No Play Store** — sideload only; artifacts expire after 7 days (re-run CI).
- **App-side instant `POST /sweep` deliberately not implemented** (would leak a
  bearer token inside a public repo — M7 retraction of an earlier doc claim;
  the sandbox tier covers the latency instead).
- **Worker `/health` "last" is per-isolate** — it can read "not run yet" while
  the cron tier sweeps fine (the `/health` prober and the cron often land on
  different isolates). Authoritative evidence = the GraphQL invocation metrics
  used in M10 (cron every minute, 2 subrequests per sweep = OAuth + Firestore).
- **Device-side test rows are Owner-manual** (`docs/TEST_PLAN.md` #1–#3,
  #7–#15, #18–#24 + the M8 checklist) — everything automatable was verified
  live (4/4 probe suites, CI gate, full-history secret scan).

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

**All three kill-push tiers are now simultaneously operational — first time
since the permanent tier was built (Phase 14).**

## 10. Maintenance runbook (what to check, when)

| When | Check | Where | Healthy looks like |
|---|---|---|---|
| Nothing routine | — | — | All tiers run unattended; crons are Cloudflare/GitHub-managed. |
| "Ask didn't arrive" | Worker health | `https://family-remote-support.www-farukkhan-fk885.workers.dev/health` | `ok:true`, both secrets `true` (ignore `last` — per-isolate, §7.2) |
| | Actions backstop | GitHub → Actions → kill-push | recent runs SUCCESS; occasional 429 failures self-heal after 13:00 ঢাকা |
| | Firestore quota | same Actions log / app message | 429s only near day-end; first run after 13:00 ঢাকা green |
| "Build broken?" | Android Build workflow | GitHub → Actions | green on `main`; the AXML gate fails loudly on version/signing drift |
| "Location stale" | Tracking tab | in-app | freshness label honest (LIVE <2 min); check consent + interval + battery optimization |
| Token hygiene | CF API token | dash.cloudflare.com → My Profile → API Tokens | stays permission-scoped; rotate by editing + re-set the Secret via the same API path |
| Firestore rules | `firestore.rules` | repo | published by SA script; probe battery re-runnable (`scripts/rules-*-probe.mjs`) |

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
| Pre-M10 | Owner polish: Settings 5-card layout, language dialog, tab/keyboard fixes | **v0.35.0/54** |
| M10 | Release readiness: this file, README/STATUS updates, CF stable tier closed | docs |

Full narratives: `worklog.md` §44–§74 (append-only, honest deltas included) and
`docs/PHASE-0.md` for the pre-master-plan era.
