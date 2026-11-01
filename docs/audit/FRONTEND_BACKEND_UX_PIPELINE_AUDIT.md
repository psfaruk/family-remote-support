# Frontend / Backend / UX / Pipeline Audit
Date: 2026-11-01
Repository: psfaruk/family-remote-support

## Scope and confidence
This is a source/documentation audit, not a claim of full physical-device validation. The repository already has substantial implementations for Tracking, notifications, navigation, Family/Guest pairing, Saved Places, and CI. Avoid rebuilding existing features blindly. Verify the current branch/CI state before changing implementation.

## Confirmed actionable gaps

### P1 — Pull-request pipeline coverage
The existing Android Build workflow is triggered by pushes to `main` and manual dispatch. That means a change can sit in a PR without a required, secret-independent Android test/build result. Release-signing secrets should not be required to validate a PR.
**Fix prepared in this branch:** add `.github/workflows/android-pr-checks.yml`, running debug unit tests and debug APK assembly on PRs without release secrets. Release APK signing remains in the main-branch workflow.

### P1 — Privacy wording and local account cleanup
The prior review found that privacy disclosures did not clearly explain Journey History, Saved Places, and derived movement information. This branch updates English and Bengali `privacy_body` strings. It also adds a local Saved Places cache clear and clears in-memory place rules during sign-out.
**Still required:** compare all translated privacy strings and actual Firebase/Firestore retention rules before release. Local sign-out cleanup does not erase server-side history; the UI must not imply that it does.

### P1 — Notification event lifecycle
The app has a dedicated Notifications screen and a pure `NotifCenter` decision layer. The remaining integration audit should verify every real event end-to-end:
producer/listener → dedupe/idempotency → persisted event/read state → OS notification channel → notification tap route → target screen/action.
Cover support request, accepted/denied/cancelled/ended, pairing/guest expiry, location-sharing stopped, place arrival/departure, permission denial, offline/reconnect, and background delivery. Do not fabricate events or expose coordinates in notification payloads.

### P1 — Location/map correctness
For each displayed member, distinguish Loading, Sharing Off, Permission Denied, Offline, Stale, and Live. Show last update time and accuracy, not just a pin. Verify that the map shows only authorized paired members, guest expiry/revocation removes access server-side, and stopping/revoking location sharing prevents future writes. Test permission denial, approximate-only permission, background restrictions, and device clock/network changes on physical Android devices.

### P1 — Family vs Guest authorization
Verify one-time/expiring QR redemption, explicit receiver consent, guest expiry, revoke, account switching, and server-side enforcement. A guest must not become a persistent Family connection through UI state alone.

### P2 — UI clarity and interaction consistency
Use one clear primary action per screen; consistent labels and icon semantics; skeleton/loading, empty, error, offline, permission-denied, and retry states; confirmations for destructive actions; accessible touch targets; content descriptions; and readable text scaling. Preserve the four-tab information architecture (Home, Add Device, Tracking, Settings) and keep notification access discoverable from the header.

### P2 — Navigation sequence
Document and test the critical paths:
1. Install → permissions explained in context → sign in.
2. Add Device → scan/create QR → show identity and connection type → recipient approves → success/expiry feedback.
3. Tracking → choose authorized member → verify freshness/status → start/stop sharing with explicit consent.
4. Notification → tap → correct detail/action → mark read without duplicating events.
5. Sign out → stop local tracking/alerts → clear account-scoped local caches → confirm remote sharing is stopped or explain what remains server-side.

## Required validation gates
- `testDebugUnitTest` and `assembleDebug` pass on every PR.
- Existing release build, AXML/version/signing checks remain green on main.
- Add unit tests for notification routing/dedupe, stale-location states, guest expiry, and sign-out cleanup.
- Two-device manual smoke test for QR pairing, notification tap routing, map freshness, location permission changes, and background delivery.
- Check Firebase rules with negative tests for stranger access, revoked pairings, expired guest sessions, and sharing-off writes.

## Release rule
Do not merge or claim the whole app is fixed until CI passes and the owner completes the two-device Android smoke test. This branch only addresses the specific code and pipeline changes listed above; the remaining end-to-end checks need separate verification.
