# Family Remote Support

Android-to-Android remote support app for families.
A trusted family member helps fix another family phone — always with the receiver's clear permission.

## Status

- **Phase 0–6 ✅** — planning, skeleton, Firebase + Google Sign-In, device registry, pairing
  (code + QR + Copy/Save/Share), presence + FCM registration + QR scan + revoke, and the
  **support session consent flow** — Start session → 2-min Allow/Deny ask → 15-min auto-ending
  session. Verified end-to-end on two Google accounts by the Owner (v0.7.1) AND at the data
  layer (ENDED + EXPIRED transitions confirmed in Firestore via the service account).
- **Phase 7 🚧 (v0.8.1, versionCode 15)** — **live screen share (view only)**:
  - Recipient taps **Share screen** on an ACTIVE session → system MediaProjection consent →
    a `mediaProjection` foreground service (notification shown) captures ~720p @ 15 fps.
    A small **self-preview** ("this is what the helper sees") proves the capture works.
  - Helper taps **Watch screen** → the recipient's engine posts a WebRTC offer → helper
    answers → live video renders in the app. Either side can stop; the session ending (or
    timing out) stops everything automatically.
  - **v0.8.1 fixes (black-screen report):**
    - **Epoch rounds**: every signal carries a per-round `ep` token; stale documents from an
      earlier round in Firestore are now ignored instead of poisoning a fresh negotiation
      (the main cause of the black "Answer sent — connecting video…" hang).
    - **ICE restart on re-watch**: each new "watch" produces an offer with brand-new ICE
      credentials and re-gathered candidates (re-watching used to leave the helper with
      zero candidates).
    - **Stale-callback guards** so a closing PeerConnection cannot corrupt a fresh round
      (spurious "Connection failed" messages).
    - **Honest status + frame watchdog**: helper shows "Connection established…",
      "Live — receiving the screen ✓" on first decoded frame, or an explicit
      "Connected, but no video is arriving" warning; "Stop watching" no longer sends "bye",
      so the recipient keeps sharing and the helper can simply watch again.
    - **VP8 preferred** (software codec — identical behaviour on every phone) and a free
      community TURN relay (openrelay.metered.ca) added after Google STUN for hard-NAT
      cases; if the relay is unavailable ICE falls back to STUN.
  - **No signaling server**: offers/answers/ICE travel as short-lived Firestore docs under
    `supportSessions/{id}/signal`, addressed to one reader (`aud`) — the security rules
    re-check participant, direction, ACTIVE status and payload size server-side.
  - WebRTC via `io.getstream:stream-webrtc-android:1.3.8` (org.webrtc API), ARM ABIs only
    to keep the APK lean. No touch control in this phase.
  - Rules are now published by the automation via the project service account
    (`scripts/publish-firestore-rules.mjs`) — no console paste needed anymore.

## Firestore

- Database created via the current Firebase console flow → database id is **`default`**
  (NOT the legacy `(default)`). All access goes through `Fb.kt`
  (`FirebaseFirestore.getInstance(BuildConfig.FIRESTORE_DATABASE_ID)`), overridable in CI
  via the `FIRESTORE_DATABASE_ID` env var.
- Security rules: `firestore.rules` (Phase 3: users, users/{uid}/devices, pairingInvitations,
  trustedDeviceRelationships; Phase 5: devicePresence + fcmTokens; Phase 6: supportSessions;
  Phase 7: supportSessions/{id}/signal; catch-all deny). Since v0.8.0 the automation publishes
  rules itself via the project service account (`scripts/publish-firestore-rules.mjs`) —
  the Owner's console paste path is retired.
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
