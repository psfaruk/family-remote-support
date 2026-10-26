# Family Remote Support

Android-to-Android remote support app for families.
A trusted family member helps fix another family phone — always with the receiver's clear permission.

## Status
Phase 1: project skeleton + automatic debug APK build via GitHub Actions.

## Build
Every push to `main` runs the "Android Debug Build" workflow and uploads an
`app-debug-apk` artifact (from `app/build/outputs/apk/debug/app-debug.apk`).

## Tech notes (Phase 1)
- Kotlin 2.0.20 + Jetpack Compose (BOM 2024.09.03), AGP 8.5.2, JDK 17
- minSdk 26 / targetSdk 34 — no Firebase yet (Phase 2)
- UI language: English (default)
