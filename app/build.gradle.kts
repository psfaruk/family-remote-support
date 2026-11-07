plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.gms.google-services")
}

android {
    namespace = "com.family.remote.support"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.family.remote.support"
        minSdk = 26
        targetSdk = 34
        // v0.33.0 (Phase 22/M5 — Live Location: consent-based sharing engine
        // (LiveLocationRepo + location-type FGS LocationShareService), the
        // real Tracking tab (osmdroid OSM map, authorized markers, freshness
        // LIVE/STALE/OFFLINE, device selector sheet, consent sheet), the
        // tracking/{relId} Location Details route, the Settings Location
        // partition made real (toggle + interval), liveLocations rules block
        // + runtime probe — docs/LIVE_LOCATION_ARCHITECTURE.md).
        // NAVIGATION_MAP §6: v0.30.0/49 was the first-implementation version;
        // each later M-milestone increments from there.
        // Pre-M10 (v0.35.0) — the Owner's Settings hierarchy rework + the
        // tab/keyboard robustness pass: Settings re-partitioned into the
        // five Owner-ordered cards (Account · Device Management · Privacy
        // & Security · Preferences · About & session), the 15-language
        // list moved from an inline radio wall into a capped+scrollable
        // picker dialog, Sign-out became the red-OUTLINED last row, the
        // top-level tabs now open at scroll-top on every entry (the
        // bell→Settings "functions fell down" report — NavHost was
        // restoring a mid-page scroll), the shell became IME-aware
        // (adjustResize + ime-padded bottom bar: nothing hides
        // behind the keyboard), SettingsTile survives large font scales
        // and the signed-out Family route shows an honest empty state.
        // v0.36.0 (post-M10 quota directive — "read/write only when the
        // user acts; auto-updates off; requests only from the app"):
        // presence heartbeat became FOREGROUND-ONLY (open + every 5 min
        // while RESUMED + one final write on ON_STOP; the 15-min
        // WorkManager worker is retired and old installs' persisted
        // work is cancelled at first open — the app now does ZERO
        // Firebase traffic while closed), and SessionNotifier's listener
        // query got a .limit(10) cap. Server sweep tiers parked (see
        // wrangler.toml / kill-push.yml / docs).
        // v0.38.0 (57) — Location master plan Phase 4: frontend mock wave.
        // Tracking member cards + 7-state sharing statuses, 6-step consent
        // wizard, journey-history + My-places screens, explicitly-entered
        // DEMO mode (zero Firestore); privacy_body corrected to the shipped
        // M5 truth; 7 orphan strings removed; +120 string keys ×15 locales.
        // v0.39.0 (58) — Location master plan Phases 6+7+8: permission
        // decision records (zero new permissions); scope-aware engine
        // (ONCE auto-stop, TEMPORARY expiry alarm, history enqueue at 50 m
        // with OS-provided speed/bearing/altitude); locationAuthorizations
        // + users/{uid}/locationHistory rules with the live dual read path
        // and the 33-case probe suite; wizard writes the authoritative
        // grants; upload-state line live on the own card.
        // Location master plan Phase 9 (v0.40.0/59): route-history UI
        // wiring — real day queries through the composite index, recipient
        // grant-gated sections B/E, owner-side retention cleanup + the
        // shared delete flow (today / everything, verified-empty).
        // Location master plan Phase 10 (v0.41.0/60): movement insights —
        // pure-Kotlin MovementClassifier (MODERATE sensor-backed /
        // LIMITED points-based estimates, impossible-speed + stale-input
        // rejection) rendering on the journey-history screen from records
        // already in hand; MovementCard single render path (demo fixtures
        // map onto MovementEstimate); demo history fixtures retimed to the
        // documented solid-runs + one 20-min-gap shape; FIRST unit-test
        // suite (app/src/test, JUnit4) wired into CI before assemble —
        // whose maiden run caught the journey-tail ms/s units slip (every
        // points-based pair ran 1000× slow → "possibly stationary"), fixed
        // here in v0.41.1/61 (worklog §82).
        //
        // v0.42.0/62 — post-gate activation wave (worklog §83): the detail
        // screen's section C went live (liveLocations' OPTIONAL
        // speedMetersPerSecond + engine fix.speed + classifyLive).
        //
        // Phase 11 (v0.43.0/63) — PLACE ALERTS: "My places & alerts" goes
        // real — rules-first placeAlerts block (owner-only, live probe
        // 25/25), pure-Kotlin PlaceAlertEngine (19 CI unit tests) evaluated
        // INSIDE LocationShareService's fix stream (DR-P2: zero new
        // permissions, zero engine Firestore reads), name-only local
        // notifications, Firestore persistence + offline-honest local
        // mirror, 20-place cap. See worklog §84.
        //
        // v0.44.0 (64) — smooth-app wave (Owner device report on 63:
        // "সেটিং ট্যাব এ ক্লিক করলে ক্র্যাশ করে" + "অ্যাপ টি স্মুথ লি চলে
        // ঠিক করে দেন" + "গুগল ম্যাপ একেবারেই স্লো … 2D 3D ও setelite ম্যাপ
        // যুক্ত করতে হবে"): local CrashJournal (uncaught handler → file,
        // zero telemetry, Settings→About view/share/delete), all binder
        // polls moved to Dispatchers.IO, the map's hard-wired OSM MAPNIK
        // replaced by a 3-style registry (fast CARTO Voyager 2D default /
        // Esri satellite / Esri 3D-relief terrain — live switching chips),
        // osmdroid tile plumbing tuned (6 download threads, 48-tile memory
        // LRU, 128 MB disk cache), dirty-tracked map redraws, and the
        // device list now refreshes on app resume. See worklog §86.
        //
        // v0.45.0 (65) — audit-fix wave (the deep audit of v0.44.0):
        // P1 event-driven push (Worker /notify + ID-token auth + FCM data
        // messages through AskNotifier), P2 stationary location heartbeat
        // (LIVE<5m/STALE<15m freshness), P4 deep links + singleTop +
        // Allow/Deny notification actions + setTimeoutAfter, P7 ServerClock
        // for every session deadline comparison, P8 notifications log at
        // real event time + place-alert/denied/cancelled types, P9
        // localized location channel + register status + test-notification
        // channel fix, P10 senior-first body type scale restored, P11 the
        // private-ViewModel crashes (LocationDetail/History/Places), P12
        // RESUMED-only permission polling, P6 R8 on + no debug-signed
        // release in CI + legacy 6-char pairing codes retired + revoke ends
        // live sessions, P5 map gesture/zoom/camera fixes, P3 unexpected-
        // stop notice. See RELEASE_NOTES §8 / worklog §87.
        // v0.46.0 (66) — external-audit G1 wave: the notification center
        // grows up. NotifEvent schema v2 (readAt — backward compatible: a
        // v1 file reads back as all-unread, the honest default), "Unread"
        // filter + Mark-as-Read (tap) + Mark-all, category chips
        // (All/Unread/Connection/Location/Security — mapped in ONE pure
        // place, NotifCenter.kt), Recent-events row taps route to the
        // event's screen through the DeepLinks dispatcher's own route
        // strings (AppNav.goRoute, now shared by the router effect), the
        // bell badge shows the unread count (a pending ask keeps its plain
        // dot — action beats count), Clear-history behind a confirmation
        // dialog, and the event stamp gained its DATE half. 14 new string
        // keys ×15 locales; NotifCenterTest (20 cases) joins the CI unit
        // gate. LOCAL-ONLY wave: zero new Firestore reads/listeners, rules
        // untouched (§75). See worklog §89 / RELEASE_NOTES §13.
        // v0.47.0 (67) — audit-fix wave 2: the deep-audit report's P0/P1
        // items shipped. Notification event-matrix filled (Gap-1..5:
        // pairing invited/approved, family revoked / guest removed /
        // rel revoked, guest expiring-soon / expired, sharing permission
        // lost, presence online/offline — all recorded from EXISTING
        // listeners, zero extra Firestore reads, each with NotifCenter
        // category + route + 10 new event strings EN+BN), the in-app
        // EventBannerBus + EventBannerHost (one routed auto-dismissing
        // banner per fresh event; session_asked excluded — the consent
        // card is the hero), bug fixes B1 (ServerClock in AskNotifier
        // setTimeoutAfter), B2 (SessionActionReceiver goAsync bounded at
        // 9 s with honest fallback), B3 (SessionPush transient-failure
        // retry via WorkManager, ≤3 attempts, network-constrained), B4
        // (background-transition sweep: cached pending asks re-delivered
        // when the last activity stops), P13 completion (nicknames in
        // member cards, home hints, consent, feed, notif rows, tracking
        // cards, detail screens, sharing sheet), M1 terrain-chip honesty,
        // M5 Settings demo shortcut, BE5 "Dead-app alerts" status row,
        // P9 Bangla first-run default. BUILD FIXES after CI run #864:
        // privacy_body's three unescaped apostrophes (from the merged
        // privacy-disclosure PR) escaped as \' — AGP 8.5.2's Kotlin
        // aaptcompiler misreports this as "Invalid unicode escape
        // sequence … {str}"; the duplicate companion object in
        // SessionActionReceiver merged; MainActivity's six duplicate
        // imports de-duplicated; SessionPushWorker.notifyBlocking
        // qualified with the SessionPush object. See worklog §90–91 /
        // RELEASE_NOTES §14.
        // v0.48.0 (68) — design-detail wave (Owner-directed after reviewing
        // the web-edition blueprint): IDENTITY REBRAND blue → emerald —
        // Theme.kt's two full M3 schemes swapped literal-for-literal
        // (light #047857 / container #D1FAE5; dark #6EE7B7 / container
        // #065F46; amber secondary and rose error kept; tertiary → teal so
        // success-chips stay distinct from emerald info-chips). This heals
        // the old launcher-vs-theme mismatch: the launcher was already
        // green (#15803D) while the app was blue. Same wave: window
        // backgrounds synced (values + values-night), the auth hero shield
        // recolored to emerald (ic_auth_hero.xml), SettingsTile + SectionCard
        // leading icons moved into tonal primaryContainer circles (the
        // icon-chip language), EmptyState's fallback icon rests on a soft
        // surfaceVariant circle, and the Home hero greeting became
        // time-aware (greet_morning/afternoon/evening/night ×15 locales;
        // home_welcome retired everywhere). Zero behavioral change, zero
        // new permissions, zero Firestore traffic. See worklog §92.
        // W1 FRONTEND REBUILD (v0.49.0) — the never-committed frontend
        // (MainActivity + ui/) reconstructed and finally COMMITTED: CI on
        // main had been red since run #867 because nine backend files
        // import ui.LangPrefBus / ui.nav.AppRoutes / MainActivity —
        // classes that no branch ever contained (the §89–92 waves that
        // "touched" Theme.kt/HomeTab.kt/Cards.kt worked in a sandbox tree
        // whose ui/ folder never reached git). This wave ships the shell
        // per docs/FRONTEND_UI_PLAN_BN.md §7-W1 and NAVIGATION_MAP §2:
        // Theme.kt (§92 emerald tokens + SeniorTypography + shape ladder +
        // ThemePrefBus + warning CompositionLocal), Lang.kt (LangPrefBus:
        // bn default, wrap() localized contexts for every notifier
        // channel), nav/AppNav.kt (AppRoutes route table + RootShell: 4
        // tabs, bell, crossfade 150ms / nested slide 200ms, saveState/
        // restoreState, DeepLinks consumption cold-start + onNewIntent,
        // never-dead-end routing), components/States.kt (EmptyState /
        // LoadingSkeleton / ErrorPanel / PermissionDeniedPanel),
        // DeviceId.kt (stableDeviceId: ANDROID_ID + persisted-UUID
        // fallback, keys the FCM registry / presence / location-authz),
        // slim MainActivity host (locale wrap, NotificationsLog.load,
        // DeepLinks, edge-to-edge, singleTop onNewIntent), ph_wip ×15
        // locales — honest wave placeholders, zero fabricated data, zero
        // Firestore reads. Waves W2–W8 follow. See worklog §93.
        //
        // v0.50.0/70 (W2) — the REAL home subtree lands: AuthGate (Google
        // Credential Manager + email/password + reset), the ONE
        // HomeViewModel (sessions ×2 + relationships ×2 + one presence doc
        // per live link — the whole subtree shares one listener set),
        // HomeTab dashboard (consent hero, live/awaiting cards, 2×2 status
        // grid, quick actions, linked phones, session activity), family
        // list + member detail (nickname, ask, revoke/remove with confirm),
        // sessions list + session detail (facts, deadlines, ended-by), all
        // wired into AppNav behind the auth gate; presence ticker
        // (heartbeat every 5 min while RESUMED + final beat) + one-shot
        // FCM registration owned by the shell. 64 new strings ×15
        // locales. See worklog §94.
        // v0.51.0/71 (W3) — the Add-Device subtree goes real: AddTab (the
        // two-ways role chooser — FAMILY/GUEST feature cards ≥140 dp + the
        // helper CTA + the explainer card), PairScreen (the INVITER side:
        // createInvitation → white QR plate 200 dp with the ECC-H center
        // badge → 5-min TTL countdown chip vs ServerClock (≤30 s warning,
        // EXPIRED derived honestly) → mono 32-char code + copy + share
        // (PNG + text via QrActions) → cancel → PAIRED ✓-pop 200 ms /
        // CANCELLED), ScanScreen (the HELPER side: the existing QrScanDialog
        // + manual 32-char entry (imePadding) + the Phase-12 role-preview
        // sheet (rs_* contract, legacy-note path) → one-shot atomic redeem
        // → ✓-pop + permission-wizard shortcut), PermissionsScreen (the
        // wizard host), AddViewModel (the ONE pairing FSM — one snapshot
        // listener per created invitation, swapped deterministically), all
        // wired into AppNav (5 WipTabs replaced; HOME_ADD shares AddTab;
        // ADD_PROGRESS stays deep-link resolvable through PairScreen).
        // Most strings reuse the ×15-translated Phase-19 pairing block;
        // 10 new keys ×15 locales. See worklog §95.
        // v0.51.1/72 (W5-W7 rescue wave) — the never-gated "Complete
        // frontend" commit (aa771cf) arrived with 149 compile errors across
        // TrackingTab/SettingsTab/NotificationsScreen/AppNav/
        // TrackingViewModel: wrong R/LangPrefBus/NotifCenter imports,
        // non-core icons (Language/PrivacyTip/Security/Shield), a private
        // material3 Chip call, FilterChip's retired onCheckedChange param,
        // a missing AppNavHost closing brace, watchDevicePresence's
        // onChange/onError mixup, and 2 missing string keys. This wave
        // repairs all of it AND finishes the feature work honestly:
        // TrackingTab renders from the ONE shared HomeViewModel listener
        // set (the duplicate per-partner presence watches in the added
        // TrackingViewModel violated §75 quota discipline — the file is
        // retired), Settings gets real dialogs (edit-name → Firebase
        // profile write, theme rows apply live, 15-language picker with
        // persist+recreate, privacy/rules bodies, a real local test
        // notification) and wired sign-out hygiene, NotificationsScreen
        // renders the unit-tested NotifCenter surface with localized
        // event labels, per-category chips, mark-read and a confirmed
        // Clear-history; notif_filter_unread gained its (%1$d) count.
        // 2 new string keys ×15 locales. See worklog §96.
        // v0.51.2/73 (crash-fix wave) — the Owner device report on 72:
        // "the app installs, opens, and closes a few seconds later."
        // Root cause chain: (1) W1's stableDeviceId returned raw
        // ANDROID_ID (16 hex) / dashed UUID (36) while every LIVE rules
        // device-scoped path pins [0-9a-f]{24} — EVERY presence/location/
        // authz/history write was PERMISSION_DENIED; (2) AppNav's
        // PresenceTickerEffect awaited sendHeartbeat with NO catch in the
        // RESUMED block, so the first denied beat escaped the
        // LaunchedEffect and killed the process seconds after sign-in.
        // Fix: deriveDeviceId folds any seed through SHA-256 → exactly 24
        // lowercase hex (deterministic, JVM-tested: DeviceIdTest, 6
        // cases), and the heartbeat became beatBestEffort — presence is
        // best-effort telemetry and can never kill the app again; the
        // badge degrades to "Last seen…" instead. registerFcmToken stays
        // guarded as before. See worklog §97.
        // v0.52.0/74 (per-tab sequences + live-session wave) — the Owner
        // report: (1) device-add permissions must run INSIDE the Add
        // Device tab (they were surfacing in other tabs); every tab owns
        // its own sequences now; (2) "video share and touch control do
        // not work" — the W4 viewer/share front door was never wired into
        // the rebuilt frontend: NOTHING called ScreenShareEngine.
        // startHelper / ScreenShareService.start (createScreenCaptureIntent
        // had zero call sites). This wave wires W4 for real: the helper's
        // viewer (video + normalized-coordinate touch control + nav/type/
        // data-saver + diagnostics) and the recipient's share card
        // (MediaProjection consent → foreground service → engine), both
        // hosted in the HOME session surfaces; PairScreen PAIRED gains the
        // in-flow permission step; Tracking/Settings host their sequences
        // (shared LocationSharingControls, in-tab wizard dialogs, in-flow
        // location permission ask); home/add mis-hosting route removed;
        // tracking/{relId} becomes the REAL Location Details screen.
        // Firestore rules re-audited path-by-path against the code — the
        // repo rules are coherent (see worklog §98); the failure was the
        // missing UI wiring, not the rules. See worklog §98.
        // v0.54.0/76 (both-ways family + Add-tab migration wave) — the
        // Owner's two directives: (1) "Home tab থেকে get help give help
        // conected family list গুলো add ডিভাইস ট্যাব এ নিয়ে যান" — the
        // Get-help/Give-help quick actions and the linked-phones list
        // moved from the Home dashboard to the TOP of the Add Device tab,
        // always rendered (the Owner "কখন এই গুলো আসে আমি টের পাইনা" —
        // nothing here appears/disappears anymore); Home is the session
        // surface (consent hero, live cards, status grid, activity). (2)
        // "যে ডিভাইস টি ফ্যামেলি কানেক্টেড হবে, উভয় সাইড থেকে স্ক্রিন
        // শেয়ার ও কন্ট্রোল করা যাবে" — FAMILY links are two-way now:
        // firestore.rules accepts a session whose asker is the rel's
        // helper (forward) OR its recipient (reverse, family only; guests
        // stay one-way), SessionRepo.requestSession fills the session's
        // roles from the ask's own direction (asker = helper, counterpart
        // = recipient — grant/deny, share/control and all UI surfaces
        // already key off the SESSION's roles, so they simply point the
        // other way), the duplicate-check covers both directions (one open
        // session per PAIR), and a reverse ask's device model comes from
        // the counterpart's live presence doc (best-effort, "" honest
        // fallback). The member detail's ask tile renders on BOTH sides of
        // a family link. See worklog §101.
        // v0.55.0/77 (no-backend-words wave) — the Owner directive:
        // "অ্যাপ এর fronted এ যেনো কোনো Firebase সম্পর্কিত তথ্য না দেখায়,
        // যদি কোনো কিছু বসাতে হয় অ্যাপ নিজের ভাষায় বলবে।" The family must
        // never read backend internals: (1) strings — about_storage,
        // auth_footer, privacy_body, err_perm_denied, err_db_missing,
        // err_unreachable, err_quota, err_generic, auth_failed and the six
        // ss_fail_* no longer mention Firebase/Firestore/console/rules in
        // ANY of the 15 locales (app's own words: "the app's own secure
        // cloud", "the service", retry advice); (2) code — the raw
        // exception text can never reach the UI again: describeFirestoreError
        // returns localized strings only, AuthGate's fallback is argless,
        // AskNotifier's worker catch never shows e.message, and all eight
        // ScreenShareEngine failure sites keep the detail in diagLog only.
        // v0.56.0/78 (permanent session-start wave, worklog §103) — the
        // Owner report: "এখন আবার সেশন সরু করা যাচ্ছে না কেনো? এটার কি
        // কোনো স্থায়ী সমাধান নাই।" The recurring "can't start a session"
        // had two clock-shaped roots, both closed permanently: (1) the
        // stale-session cleanup compared SERVER-written deadlines against
        // the RAW DEVICE clock — a 5–10-min-slow phone (common on the
        // family's elderly devices) saw a long-dead ask as "still waiting"
        // FOREVER and the zombie blocked every new ask; the ask's own
        // deadline WRITE could also fall outside the rules' server-time
        // window at process start (offset not yet learned → PERMISSION_
        // DENIED). Fixes: ServerClock gained an explicit rules-legal clock
        // ping (syncServerClockBestEffort — serverTimestamp write +
        // forced-server read on the user's own profile, once per process,
        // before every ask/grant) plus persisted-offset restore at app
        // start; requestSession/expireSession now decide staleness on
        // SERVER time; PairingRepo's three rules-checked expiry writes
        // (invite/guest windows) use ServerClock too. (2) A session doc
        // whose respondDeadline/sessionEndsAt was missing (zero) was
        // IMMUNE to every staleness check forever — isStaleOpen (moved to
        // the SDK-free SessionModel.kt with a JVM suite, SessionStaleTest)
        // now falls back to the ask's own age: REQUESTED > 10 min or
        // ACTIVE > 25 min with no deadline/end is stale regardless.
        versionCode = 78
        versionName = "0.56.0"

        // Phase 7 — WebRTC screen share ships native .so libraries for all four
        // ABIs; the family fleet is ARM-only, so drop x86/x86_64 to keep the
        // APK half the size.
        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a")
        }

        // Phase 3: Web Client ID (serverClientId) for Google Sign-In. CI can
        // still override it via the GOOGLE_WEB_CLIENT_ID env var, but the
        // Owner-provided Web OAuth client is baked in as the default so a
        // plain push always produces a working build. (OAuth client IDs are
        // public identifiers, not secrets.)
        val envClientId = System.getenv("GOOGLE_WEB_CLIENT_ID").orEmpty()
        val webClientId = envClientId.ifBlank {
            "1001946927282-k0orohct2gg0jojtclpd00jafikqsim3.apps.googleusercontent.com"
        }
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"$webClientId\"")

        // Firestore database id. Databases created through the current Firebase
        // console flow get the id "default" (NOT the legacy "(default)"). All
        // Firestore access must go through Fb.db which reads this value, so a
        // plain push always matches the Owner's actual database. CI can still
        // override it via the FIRESTORE_DATABASE_ID env var.
        val envDbId = System.getenv("FIRESTORE_DATABASE_ID").orEmpty()
        val firestoreDbId = envDbId.ifBlank { "default" }
        buildConfigField("String", "FIRESTORE_DATABASE_ID", "\"$firestoreDbId\"")
    }

    // Release signing: uses app/release.keystore when present (decoded from
    // GitHub Secrets in CI). Falls back to debug signing so builds never break.
    val releaseKeystore = file("release.keystore")
    signingConfigs {
        if (releaseKeystore.exists()) {
            create("ciRelease") {
                storeFile = releaseKeystore
                storeType = "PKCS12"
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            // v0.45.0 (audit P6-গ): R8 ON for release builds — the ~27.9 MB
            // APK ships unshrunk/unobfuscated today (easy to reverse).
            // Keep rules: app/proguard-rules.pro (WebRTC natives, Firebase
            // listeners, osmdroid, coroutines). Unit tests gate this in CI
            // before assemble, and verify-apk-axml.mjs re-checks the signed
            // artifact afterwards.
            isMinifyEnabled = true
            // Resource shrinking stays OFF for this first R8 wave — the
            // APK-size win is mostly code+dex anyway, and none of the native
            // deps (WebRTC/ML Kit) have been runtime-proven under resource
            // shrinking on the family fleet yet.
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // v0.45.0 (audit P6-ক): no more silent DEBUG-signed releases.
            // Local no-keystore `assembleRelease` now yields an UNSIGNED apk
            // (install-visible immediately) instead of one that later breaks
            // updates with INSTALL_FAILED_UPDATE_INCOMPATIBLE when the real
            // key returns. CI hard-fails earlier when the secret is missing.
            signingConfig = if (releaseKeystore.exists()) {
                signingConfigs.getByName("ciRelease")
            } else {
                null
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.activity:activity-compose:1.9.2")

    // Compose (BOM keeps all versions in sync)
    implementation(platform("androidx.compose:compose-bom:2024.09.03"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    // Phase 11 — bottom-nav + top-bar icons (core set: Home/Phone/
    // Notifications/Settings…). Version comes from the Compose BOM.
    implementation("androidx.compose.material:material-icons-core")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Phase 21/M2 (v0.30.0, Decision C3 spike) — Navigation-Compose shell:
    // 4 tabs + notifications route with saveState/restoreState. 2.8.x is
    // the release-line matching Compose BOM 2024.09.03 (compose 1.7.x).
    // lifecycle-viewmodel-compose deliberately NOT added yet — ViewModels
    // arrive with M3 screen extraction; minimal-deps discipline (C4).
    implementation("androidx.navigation:navigation-compose:2.8.2")

    // M3 (v0.31.0) — ViewModel layer (FRONTEND_ARCHITECTURE §4): one
    // ViewModel per screen area owning StateFlow<UiState>. First consumer:
    // the root-scoped SessionWatchViewModel (routed listener errors).
    // 2.8.6 matches the lifecycle-runtime-ktx line already in use.
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")

    // Firebase (Phase 2) — BoM keeps all Firebase versions in sync
    implementation(platform("com.google.firebase:firebase-bom:33.4.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")

    // Phase 3 — Google Sign-In via Credential Manager + Firestore await()
    implementation("androidx.credentials:credentials:1.3.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")

    // Phase 4 — QR code *generation* for pairing invites (display only;
    // scanning arrives in Phase 5). Pure Java lib, no camera permission.
    implementation("com.google.zxing:core:3.5.3")

    // Phase 5 — QR camera scan (CameraX + on-device ML Kit), background
    // presence heartbeat (WorkManager), push token registration (messaging).
    // (No camera-bom — that artifact is not published on the Google Maven we
    // resolve from; versions are pinned explicitly instead.)
    implementation("androidx.camera:camera-core:1.3.4")
    implementation("androidx.camera:camera-camera2:1.3.4")
    implementation("androidx.camera:camera-lifecycle:1.3.4")
    implementation("androidx.camera:camera-view:1.3.4")
    // ProcessCameraProvider.getInstance() returns a GUAVA ListenableFuture;
    // Google's documented fix for Kotlin consumers is this stub artifact.
    implementation("com.google.guava:listenablefuture:1.0")
    implementation("com.google.mlkit:barcode-scanning:17.2.0")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("com.google.firebase:firebase-messaging")

    // Phase 7 — WebRTC screen share. stream-webrtc-android is the maintained
    // Maven-Central build of libwebrtc (org.webrtc.* API, same as the old
    // google-webrtc artifact that was dropped from JCenter).
    implementation("io.getstream:stream-webrtc-android:1.3.8")

    // Phase 22/M5 (v0.33.0) — Live Location (LIVE_LOCATION_ARCHITECTURE §2):
    // position fixes via the Google Play services fused provider (with a
    // LocationManager fallback path when GMS is unavailable), map via
    // osmdroid (Apache-2.0; OSM tiles with mandatory on-map attribution; the
    // osmdroid User-Agent is set in App.onCreate — OSMF tile policy).
    // C4 decision record (PROJECT_CONTEXT §19): both additions were approved
    // with the Owner's M5 start command, same precedent as navigation-compose
    // with M2 — no map/location library existed in the app before this.
    implementation("com.google.android.gms:play-services-location:21.3.0")
    implementation("org.osmdroid:osmdroid-android:6.1.18")

    // Location master plan Phase 10 (v0.41.0) — the repo's FIRST unit
    // test dependency: MovementClassifierTest is the phase gate (audit §9
    // "Unit tests incl. impossible-speed rejection"), pure JVM, no
    // Android deps; CI runs :app:testReleaseUnitTest before assemble.
    testImplementation("junit:junit:4.13.2")
}
