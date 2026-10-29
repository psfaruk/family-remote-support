# Family Remote Support

Android-to-Android remote support app for families.
A trusted family member helps fix another family phone — always with the receiver's clear permission.

## Status

- **Phase 0–6 ✅** — planning, skeleton, Firebase + Google Sign-In, device registry, pairing
  (code + QR + Copy/Save/Share), presence + FCM registration + QR scan + revoke, and the
  **support session consent flow** — Start session → 2-min Allow/Deny ask → 15-min auto-ending
  session. Verified end-to-end on two Google accounts by the Owner (v0.7.1) AND at the data
  layer (ENDED + EXPIRED transitions confirmed in Firestore via the service account).
- **Phase 7 🚧 (v0.8.3, versionCode 17)** — **live screen share (view only)**:
  - Recipient taps **Share screen** on an ACTIVE session → system MediaProjection consent →
    a `mediaProjection` foreground service (notification shown) captures ~720p @ 15 fps
    (20 fps since v0.9.3).
    A small **self-preview** ("this is what the helper sees") proves the capture works.
  - Helper taps **Watch screen** → the recipient's engine posts a WebRTC offer → helper
    answers → live video renders in the app. Either side can stop; the session ending (or
    timing out) stops everything automatically.
  - **v0.8.3 (bigger picture + self-healing connection):**
    - **Runtime-configurable TURN**: the free public relay the app shipped with
      (openrelay.metered.ca) was verified DEAD in Oct 2025 (UDP silent, TCP refused) —
      without a relay, two phones on different carrier networks (CGNAT) usually cannot
      connect at all. The engine now reads optional TURN credentials from a
      `config/turn` Firestore document (written by the Owner's service account,
      read-only for apps) at every session start; credentials can be rotated later
      WITHOUT a new APK. Empty config ⇒ STUN-only (same Wi-Fi / hotspot still works).
      Candidate gathering is now `GATHER_CONTINUALLY`.
    - **Self-healing watch rounds**: a "watch" is now accepted in ANY state while the
      recipient's capture is alive — previously a watch arriving while the recipient
      showed "Connection failed" was IGNORED, so the helper waited forever on
      "Asked the recipient's phone…". A failed round on the recipient no longer kills
      the share: only the PeerConnection is rebuilt and the engine returns to
      WAITING_PEER (no "Stop sharing" + re-consent needed).
    - **Helper auto-retry**: stuck stages (no offer within 12 s, no answer within 12 s,
      no video within 14 s, or a hard FAILED) trigger an automatic fresh round — new
      epoch + new PeerConnection + new "watch" — up to 3 retries (budget refills on
      every success). Only after the budget is spent does the manual advice appear.
    - **Network self-diagnosis**: while gathering, the app counts its candidates by
      type and shows "Network check — direct: N · via STUN: M · via relay: K"; K>0 on
      a screenshot proves a relay was reachable.
    - **Bigger picture**: the helper's video is sized to the real screen aspect ratio
      (decoded frame size published by the engine), capped to fit, and a tap opens a
      **full-screen viewer** (black, edge-to-edge); the helper's screen stays on while
      LIVE. The recipient's self-preview grew from 120 dp to 160 dp.
  - **v0.8.2 fix (black surface even while the watchdog said "Live ✓"):** the render
    surface was attached inside the Compose `AndroidView` factory and silently detached
    again a frame later, when the very same `DisposableEffect` restarted (its
    dispose-after-attach ordering) — frames kept flowing and were even counted, while
    BOTH surfaces (helper video + recipient self-preview) stayed black. Attachment now
    happens in the effect body (after the view key settles) and is re-asserted on every
    recomposition. A fresh "watch" round also resets the remote-description state so a
    re-watch's answer-time ICE candidates can no longer be applied against the previous
    round. Plus: the helper's stale "Request sent…" line is cleared once the session is
    ACTIVE.
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
    - **VP8 preferred** (software codec — identical behaviour on every phone).
  - **No signaling server**: offers/answers/ICE travel as short-lived Firestore docs under
    `supportSessions/{id}/signal`, addressed to one reader (`aud`) — the security rules
    re-check participant, direction, ACTIVE status and payload size server-side.
  - WebRTC via `io.getstream:stream-webrtc-android:1.3.8` (org.webrtc API), ARM ABIs only
    to keep the APK lean. (Touch control arrived in Phase 8.)
  - Rules are now published by the automation via the project service account
    (`scripts/publish-firestore-rules.mjs`) — no console paste needed anymore.
- **Phase 8 🚧 (v0.9.0, versionCode 18) — REMOTE ASSIST (assisted control):**
  - The helper can now not only SEE the screen but also TAP on it. A **"Touch control"
    switch** on the helper's viewer turns the picture into a remote touch surface: tapping
    the picture (inline or full-screen) sends a normalized tap to the recipient's phone.
    **◀ Back / ⌂ Home / ▦ Recents** buttons dispatch the global navigation actions.
  - On the recipient the taps are performed by **`AssistService`** — an accessibility
    service with `canPerformGestures=true` and **`canRetrieveWindowContent=false`**: it is
    a finger, not an eye — it READS NOTHING from the screen. The grantor enables it once
    via a clear "Turn on Remote assist" button (opens system Settings → Accessibility) and
    can switch it off any time; the share card always shows the current state.
  - Trust chain (same as the video): commands travel the Firestore signal queue as
    `cmd`/`cmdack` docs — only the other participant of an ACTIVE session can write them
    (server-enforced), each carries the CURRENT negotiation epoch (stale commands from an
    earlier round are inert), and the engine drops them the moment the session/capture
    ends. Every command is answered with a delivery ack, so the helper's UI says
    "Tap delivered ✓" or "Remote assist is switched off there" — never a silent failure.
  - Coordinate mapping is resolution-independent: the helper's letterboxed video tap is
    converted to (0..1) normalized coordinates and re-scaled to the recipient's real
    display (maximumWindowMetrics / getRealSize — status + navigation bars included,
    exactly the space the video shows) before `dispatchGesture` injects the tap.
- **Since v0.9.0 (fixes, no new phases):** v0.9.1 — runtime metered.ca TURN via the
  `config/turn` Firestore doc (free tier 500MB/month, burns only on cross-network relay);
  v0.9.2 — the three forensics-proven root causes of the black/dead picture fixed
  (permanent proxy sink for the renderer race, every SDP failure routed to diagnostics
  instead of a silent dead-end, TURN-config read capped at 5s) + on-screen Diagnostics
  panel; v0.9.3 — bitrate floor (min 800kbps / max 2.5Mbps, MAINTAIN_RESOLUTION so text
  stays readable) + 20 fps capture + PERSISTENT assist-off warning with an actionable
  hint (the tap pipeline itself was proven working — the recipient's Accessibility
  service was simply switched off) + the About panel shows the REAL version;
  v0.9.5 — enablement-UX: a first-open Permission Wizard with a Bengali guide that opens
  the RIGHT Accessibility switch (highlighted) instead of Android's generic
  "Accessibility menu shortcut" + a dropped-tap warning banner; v0.9.6 — pixel-perfect
  touch (taps now map onto the REAL display instead of the smaller app window — the
  root cause of "pressed h, tapped u"), INSTANT taps (WebRTC data channel, sub-100 ms),
  30 fps 1536-class capture; v0.9.7 — the owner-tunable `config/quality` Firestore doc
  (codec vp8/h264, 10–60 fps, bitrate floor/ceiling — flipped remotely, no new APK),
  the hardware-H264 path with an on-screen capability probe ("codec probe — H264
  enc=Y dec=Y …"), negotiated-codec proof in diagnostics, and automatic VP8/30fps
  fallback whenever hardware H264 is unavailable; v0.9.8 — FCM kill-push (Phase 8.2):
  a session request now reaches the recipient even when Android has killed the app —
  a sweep service (sandbox mini-service polling every 5 s, plus a GitHub Actions cron
  backstop, both running the same `scripts/fcm-kill-push.mjs`) finds fresh REQUESTED
  sessions and sends a high-priority FCM notification to the recipient's registered
  tokens; the OS displays it in the `support_session_requests` channel from the killed
  state, and tapping it cold-starts the app into the in-app ask card. Idempotent
  (killPushSentAt mark), only pushes asks whose 2-minute answer window is still open,
  auto-deletes UNREGISTERED tokens, and ships no new app dependencies; v0.9.9 —
  remote SWIPE and LONG-PRESS join the tap (Phase 8.3): the helper's real drag
  duration travels with each swipe (slow finger = slow scroll), long-press opens
  context menus, and both land with the same real-display precision as taps —
  still zero window-content retrieval. Plus low-bitrate-when-relayed: the sender
  checks WebRTC stats 6 s after connect and, if the selected path is a TURN
  relay, caps the stream at 1500 kbps so the metered.ca free tier lasts instead
  of burning in ~17 minutes (direct/hotspot never capped).
- **Security hardening ✅ (v0.10.0, versionCode 28) — roadmap Phase 9:**
  - **Runtime negative-probe of the LIVE rules** (`scripts/rules-negative-probe.mjs`): a
    synthetic "rules-probe" identity (self-signed Firebase custom token → Identity Toolkit
    ID token) fires 12 hostile paths against production Firestore — reading another user's
    profile/token, listing sessions/invitations, writing config, creating a session without
    an ACTIVE relationship, injecting a signal/cmd into a real session, oversized/too-short
    FCM tokens, mutating a real session, deleting a relationship — **all 12 refused with
    403** (positive control: the probe writes+deletes its own presence doc). No secrets in
    repo, no project-state residue (probe user deleted, throwaway doc removed).
  - **Replay/abuse guards on the assist injector** (engine): Firestore `cmd` signals must
    be fresh (server `createdAt` ≤ 15 s — `SignalingRepo.watch` now passes it), payloads
    ≤ 4 KB on both transports, coordinates must be finite and are clamped to 0..1 at the
    boundary (AssistService re-clamps — two independent fences), durations clamped, and a
    sliding-window rate cap (25 cmds/s) stops gesture floods. Every refused command now
    acks truthfully (`bad-payload` / `rate` / `assist-off`) so the helper's UI never lies.
  - **Rules tightening**: `fcmTokens` writes are type-checked (`uid`/`deviceId`/`token`
    strings) and size-bounded (32–4096 chars; real tokens are ~142) — deployed live and
    readback-verified (ruleset fc6d8ec0).
  - **Secure-logging audit**: the app ships **zero** `android.util.Log` calls — the only
    diagnostics are the in-app 14-line ring buffer, which never leaves the device and
    never contains tokens/emails. Verified by grep across all sources.
- **Phase 9 ✅ (v0.9.4, versionCode 22) — FAMILY UI POLISH (T0–T3):**
  - **T0 — warm family theme**: one fixed light Material 3 scheme (warm amber/brown
    primary, warm surfaces — no cold corporate blue) + a senior type scale
    (body 14→16sp, small 12→14sp, bolder section titles), applied from the single
    `MaterialTheme` wrapper. Zero logic touched.
  - **T1 — consistent layout**: every section card uses one `SectionCard` (uniform
    padding/spacing/title); primary actions are full-width ≥52dp `BigButton`s and
    secondary actions ≥48dp; pairing — the heart of the app — moved up right under
    Account; the dev-console rows (package / Firebase ids / next milestone) now live
    in a collapsed "About · অ্যাপ সম্পর্কে" card; the dead "Phase 2 build check" card
    is retired.
  - **T2 — bilingual senior flow**: family-facing copy is বাংলা-first with English
    kept for technical strings; phase-number prefixes ("Phase 4 — …") removed from
    user-visible titles; the pairing code renders headline-size in the theme's
    primary color; the consent card asks in Bangla with Allow/Deny as two
    weight-equal big buttons; session state lines are Bangla-first.
  - **T3 — touch-control overlay polish**: the helper's live picture shows a warm
    rim + a "REMOTE TOUCH ON · টাচ চালু" badge (inline AND full-screen) whenever
    taps go remote; ◀ Back / ⌂ Home / ▦ Recents became equal-width ≥48dp buttons;
    the tiny full-screen "✕ Close" text became a real ≥48dp button; the
    recipient's Remote-assist state line is now bold bilingual with a prominent
    one-tap setup button — the exact step the v0.9.3 forensics showed being missed.

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
- UI language: বাংলা-first bilingual (Phase 9); technical/status strings remain English
