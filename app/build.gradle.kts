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
        // v0.30.0 (Phase 21/M2 — first master-plan implementation release:
        // shared shell on Navigation-Compose, C1 tab rename, notifications
        // surface). NAVIGATION_MAP §6: v0.30.0/49 is the first-implementation
        // version; each later M-milestone increments from here.
        versionCode = 49
        versionName = "0.30.0"

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
            isMinifyEnabled = false
            signingConfig = if (releaseKeystore.exists()) {
                signingConfigs.getByName("ciRelease")
            } else {
                signingConfigs.getByName("debug")
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
}
