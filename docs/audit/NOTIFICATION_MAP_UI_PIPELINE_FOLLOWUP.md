# Notification / Map / UI / Pipeline Follow-up Audit
Date: 2026-11-01
Repository: psfaruk/family-remote-support

## What this audit confirms
The repository contains a notification decision layer (`NotifCenter.kt`), a notification UI (`NotificationsScreen.kt`), and unit tests (`NotifCenterTest.kt`). Their presence does not by itself prove that every backend event reaches the UI or Android system notification correctly. This document defines the next verification and fix gates; it is not a claim that all end-to-end bugs are fixed.

## P0/P1 — Build validation for pull requests
The current build workflow was not confirmed to run on pull requests. This branch adds `.github/workflows/android-pr-checks.yml` to run unit tests and assemble a debug APK without release-signing secrets. Check CI output before merging. If the project does not provide a Gradle wrapper, standardize and commit the wrapper rather than relying on a runner-installed Gradle version.

## P1 — Notification end-to-end contract
For every event, verify:
1. One stable event ID and authenticated owner/recipient.
2. Backend authorization before data is read or changed.
3. Exactly-once UI effect via idempotent event handling (Realtime listener + FCM must not duplicate).
4. Persisted read/unread state with deterministic ordering.
5. Correct Android notification channel and runtime notification permission handling.
6. Tap routes to the intended screen, with stale/expired actions safely rejected.
7. Sensitive coordinates and private content are not placed in notification text/payload.

Minimum event matrix: support requested/accepted/rejected/cancelled/ended; pairing requested/approved/revoked; Guest expiring/expired; location sharing started/stopped; saved-place arrival/departure; location permission denied/revoked; offline/reconnected. For each, test foreground, background, killed process, duplicate delivery, denied notification permission and tapping an old notification.

## P1 — Map and live-location truthfulness
Each member card and map view should distinguish Loading, Live, Stale, Offline, Sharing Off and Permission Denied. Show last-updated time and accuracy when available. A cached coordinate must never be labelled Live. Stop-sharing and permission revocation must stop subsequent uploads; validate this in service code and server rules, not only the visible toggle.

## P1 — Pairing and location access boundaries
Verify QR one-time redemption and expiry, explicit receiver approval, Guest expiry/revocation, account switching, and server-side rules. A Guest session must not be promoted to persistent Family access solely by client state. Include negative tests for unpaired users, revoked members, expired Guests, and sharing disabled.

## P2 — User-friendly UI
Use clear plain-language labels, one primary action per screen, accessible touch targets, content descriptions, scalable text, and explicit loading/empty/error/offline/permission states. Ask before destructive actions. Provide an actionable permission rationale and a Settings fallback after denial. Keep the established Home / Add Device / Tracking / Settings navigation and make Notifications discoverable from the header.

## P2 — Sequence / pipeline smoke paths
- Fresh install → explain permission in context → sign in.
- Add Device → QR scan/create → show member identity + Family/Guest scope → receiver approval → success or expiry state.
- Tracking → select authorized member → show freshness/status → start/stop sharing → confirm state update.
- Notification → tap → correct screen/action → mark read once.
- Sign out → stop this device's tracking and alerts → clear account-scoped local caches → accurately explain that server-side history may remain.
- PR → unit tests → debug APK build → two-device smoke test → review → merge → release build.

## Required gates before declaring fixed
- CI unit tests and debug build pass.
- Tests cover notification deduplication/routing, stale-location states, Guest expiry, and sign-out cache cleanup.
- Two physical Android devices verify QR pairing, background notification tap routing, location permission changes and map freshness.
- Firebase/Firestore rules are tested for negative access cases.
- Review all translations and privacy wording against actual retention/deletion behavior.

## Limitations
This is a source-level checklist and PR build-workflow change. No Android runtime, Firebase emulator, or two-device test was executed by this audit. Do not merge or describe the whole app as fixed until the required gates pass.
