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
        versionCode = 67
        versionName = "0.47.0"

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
