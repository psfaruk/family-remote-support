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
        versionCode = 3
        versionName = "0.3.0"
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
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Firebase (Phase 2) — BoM keeps all Firebase versions in sync
    implementation(platform("com.google.firebase:firebase-bom:33.4.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
}
