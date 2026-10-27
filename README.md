# Family Remote Support

Android-to-Android remote support app for families.
A trusted family member helps fix another family phone — always with the receiver's clear permission.

## Status

- **Phase 0–3 ✅** — planning, project skeleton, Firebase + Google Sign-In, trusted device registry.
- **Phase 4 ✅ (v0.5.2, versionCode 8)** — code-based pairing (6-char id + one-time token, SHA-256
  hash stored, 5-min expiry, atomic redemption) + QR display + live watchers + cancel/redeem.
  Verified on Owner's phone: profile saved ("Registered ✓"), device list read, pairing code +
  QR + countdown + snapshot listeners all live. No hangs, no "Could not reach Firestore".
- **Phase 5 🚧 (v0.6.0 → v0.6.1)** — device presence (heartbeat + TTL badges), FCM token registration,
  QR camera scan (CameraX + ML Kit), revoke UI.
  - **v0.6.1 bugfix:** `redeemCode` failed for everyone with `INVALID_ARGUMENT: Firestore
    transactions require all reads to be executed before all writes` — the relationship doc was
    read *after* the invitation update inside the transaction; now both reads happen first.
  - **v0.6.1 features:** the pairing QR panel now has **Copy code** (clipboard), **Save QR**
    (PNG → `Download/pairing-code-<ID>.png`, MediaStore on Android 10+, runtime permission on
    Android 8–9) and **Share** (share sheet via FileProvider: QR image + readable code text).

## Firestore

- Database created via the current Firebase console flow → database id is **`default`**
  (NOT the legacy `(default)`). All access goes through `Fb.kt`
  (`FirebaseFirestore.getInstance(BuildConfig.FIRESTORE_DATABASE_ID)`), overridable in CI
  via the `FIRESTORE_DATABASE_ID` env var.
- Security rules: `firestore.rules` (Phase 4: users, users/{uid}/devices, pairingInvitations,
  trustedDeviceRelationships; catch-all deny). Published live as ruleset `732d38cb…`
  on release `cloud.firestore/default`.
- Realtime Database exists in the project but is locked deny-all and unused by the app.

## Build

Every push to `main` runs the "Android Build" workflow and uploads `app-release-apk` and
`app-debug-apk` artifacts. Release APKs are signed with the CI keystore (GitHub Secrets),
so every version over-installs cleanly on the Owner's phone.

## Tech notes

- Kotlin 2.0.20 + Jetpack Compose (BOM 2024.09.03), AGP 8.5.2, JDK 17
- Firebase BoM 33.4.0 (firestore 25.1.0, auth), Credential Manager Google Sign-In
- minSdk 26 / targetSdk 34
- Every Firestore round-trip has a hard 30 s timeout (`awaitWithTimeout`) + actionable
  Bengali/English error mapping (`describeFirestoreError`)
- UI language: English (default)
