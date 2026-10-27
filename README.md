# Family Remote Support

Android-to-Android remote support app for families.
A trusted family member helps fix another family phone — always with the receiver's clear permission.

## Status

- **Phase 0–5 ✅** — planning, skeleton, Firebase + Google Sign-In, device registry, pairing
  (code + QR + Copy/Save/Share), presence + FCM registration + QR scan + revoke. Pairing verified
  end-to-end on two Google accounts by the Owner (v0.6.2).
- **Phase 6 🚧 (v0.7.0, versionCode 12)** — **support session consent flow**:
  helper taps **Start session** on a paired device → the recipient phone gets a live permission
  card (and a notification while the app is backgrounded) with a **2-minute** Allow/Deny ask →
  allowed sessions run for **15 minutes** with a live countdown and auto-end (either side can end
  early) → DENIED / CANCELLED / EXPIRED / timeout are all first-class outcomes. One open session
  per pair at a time. Every transition is a Firestore transaction re-checked by security rules
  (`supportSessions` in firestore.rules).
  - Notification transport today is a process-lifetime Firestore listener — works while the app
    is backgrounded but alive. Push-when-killed arrives with the Phase 7 server (FCM HTTP v1),
    using the tokens registered in Phase 5.
  - **The new rules MUST be re-published** (same console paste path as v0.6.2) before testing.

## Firestore

- Database created via the current Firebase console flow → database id is **`default`**
  (NOT the legacy `(default)`). All access goes through `Fb.kt`
  (`FirebaseFirestore.getInstance(BuildConfig.FIRESTORE_DATABASE_ID)`), overridable in CI
  via the `FIRESTORE_DATABASE_ID` env var.
- Security rules: `firestore.rules` (Phase 4: users, users/{uid}/devices, pairingInvitations,
  trustedDeviceRelationships; Phase 5: devicePresence + fcmTokens; Phase 6: supportSessions;
  catch-all deny). Rules are re-published by the Owner via the console paste path after every
  rules change (latest paste: Phase 6 block).
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
