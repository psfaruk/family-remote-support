# IMPLEMENTATION_STATUS — Reconciled Tracker (Master-Plan PART 15 #9)

> Status: **PLANNING checkpoint.** Reconciles the ORIGINAL phase history (Phase 0–20) with the
> NEW master-plan phases (M0–M10). The stale §12 table inside `docs/PHASE-0.md` is superseded
> by this file from now on (PHASE-0 remains the append-only narrative record).
> Build at writing: **v0.29.0 (versionCode 48)** · HEAD `cf87601` · CI SUCCESS.

## 1. Original phases (verified from PHASE-0.md §18–43, README status, worklog §44–63)

| Phase | Content | Build | State |
|---|---|---|---|
| 0 | Environment & feasibility, plan, decision record | — | ✅ |
| 1–4 | Auth, pairing, devices, presence | v0.5.x | ✅ |
| 5–7 | FCM, sessions, WebRTC signaling | v0.8–0.13 | ✅ |
| 8–10 | Hardening, wizard, notifications | v0.13–0.15 | ✅ |
| 11–11.8 | UI/UX polish series (design system, AuthGate) | v0.15–0.18.1 | ✅ |
| 12–13 | Privacy docs, sideload release path | v0.18.0 | ✅ |
| 14–14.1 | Cloudflare Worker kill-push + wrangler live proof | infra | ✅ |
| 15–17 | Quota-aware ops, 4-tab shell restructure (Home/Phone/Live location/Settings), misc | v0.19–0.21 | ✅ |
| 18.1–18.9 | Deep-audit series: pairing/reboot, session UX, latency, notification-chain, rules+23 probes, app-logic (code 6→12 digit), i18n notifications, security caps, CI/docs | v0.22.0–0.26.0 (41→45) | ✅ |
| 19.1–19.2 | Full i18n extraction (~305 literals → stringResource; 231+39 keys ×15) | v0.27.0–0.28.0 (46→47) | ✅ |
| 20 | Firestore free-quota incident: 5 s→30 s sweep + backoff, zombie EXPIRE, `err_quota` ×15, presence labels | v0.29.0 (48) | ✅ |

## 2. Master-plan phases (this instruction) — current state

| M | Content | State |
|---|---|---|
| M0 | Repository audit (3 parallel agents: frontend / backend-infra / docs-tracker) + state verification | ✅ 2026-10-31 (this push) |
| M1 | Architecture & design specification: 8 new docs + UI_UX §26 reconciliation + PROJECT_CONTEXT | ✅ 2026-10-31 (this push) |
| **⛔ APPROVAL GATE** | **Owner reviews PROJECT_CONTEXT / FRONTEND_ARCHITECTURE / NAVIGATION_MAP / LIVE_LOCATION_ARCHITECTURE / FAMILY_GUEST_AUTHORIZATION / ANDROID_PERMISSION_MATRIX / TEST_PLAN / UI_UX §26 and replies with explicit approval** | **WAITING** |
| M2 | Shared application shell: header polish, notifications surface, 4-tab rename (C1), theme foundation, nested navigation (C3 spike) | ⛔ blocked on approval |
| M3 | Frontend redesign: Home, Add Device, Tracking, Settings + detail screens with real states | ⛔ |
| M4 | Existing-feature integration checks (pairing, sessions, revocation) + drift fixes | ⛔ |
| M5 | Live location implementation (engine, map, rules, probes) — v0.30.0/49 | ⛔ |
| M6 | Family/Guest authorization verification + G1–G4 deltas | ⛔ |
| M7 | Remote support integration audit (no regression to WebRTC/assist) | ⛔ |
| M8 | Notifications, accessibility, i18n completion, polish | ⛔ |
| M9 | Testing & integration: TEST_PLAN execution, security review | ⛔ |
| M10 | Release readiness: docs, build instructions, known limits, final report | ⛔ |

Rule honored: no later phase starts before its prerequisites are verified (master PART 14).

## 3. Feature-level status (Agent 2-a/2-b audit, 2026-10-31)

| Feature | Status | Evidence |
|---|---|---|
| Google Sign-In (Credential Manager) + email auth + reset | ✅ implemented | `MainActivity.kt:643-687`, `ui/AuthGate.kt` |
| QR generate/scan, 12-digit code, single-use TTL invitations | ✅ | `PairingRepo.kt:276-441`, `QrScanner.kt` |
| Family device list / details / revoke | ✅ | `PairingRepo.kt:521-565`, `MainActivity.kt:4180-4300` |
| Guest mode (window ≤3 h, fence, guest-only delete) | ✅ | rules `:198-313`, `SessionRepo.kt:246-280` |
| Presence (20-min window, heartbeat, chips) | ✅ | `PresenceRepo.kt` |
| FCM + token rotation + session asks + kill-push 3 tiers | ✅ (permanent tier inactive: SA secret unset) | manifest `:76-97`, worker, §63 |
| Screen share (WebRTC + MediaProjection FGS) | ✅ | `ScreenShareEngine.kt`, `ScreenShareService.kt` |
| Remote interaction (accessibility gestures + typing) | ✅ | `AssistService.kt`, `AssistSetup.kt` |
| Sessions + signaling + zombie guards + quota errors | ✅ | `SessionRepo.kt`, `SignalingRepo.kt`, §63 |
| 15-language i18n + theme persistence | ✅ (≈3 keys/locale parity gap; ~16 stray literals) | `ui/Lang.kt`, strings ×15 |
| **Live location** | 🔜 **planned M5** (placeholder tab today) | `MainActivity.kt:3038-3046` |
| Notifications screen (dedicated) | 🔜 M2/M8 (bell → Settings today) | `MainActivity.kt:1045-1047` |
| ViewModels / Navigation-Compose / shared component library | 🔜 M2–M3 (Decision C3) | audit §1 |
| Tests (unit/UI) | ❌ none (probes + CI + Owner manual only) | TEST_PLAN §0 |

## 4. Known open issues (from audits — each mapped to a phase)

| Issue | Fix wave |
|---|---|
| Live-location placeholder promise | M5 |
| ~16 hardcoded user-visible strings + parity gap | M4/M8 (Agent 19) |
| Manifest assist-config doc drift | M4 |
| Bell → Settings instead of notifications surface | M2 |
| Swallowed listener errors / ad-hoc error states | M3 (States components) |
| Per-second recomposition tick | M8 (perf pass) |
| `isMinifyEnabled=false`, no tests, 40 MB APK | record-only; R8 = separate future decision |
| Cloudflare `FIREBASE_SA_JSON` unset (permanent kill-push tier inactive) | **Owner action** (unchanged since §60) |
| docs/PHASE-0.md §12 stale table | superseded by this file (narrative stays) |

## 5. Owner pending actions (unchanged + new)

1. Install **v0.29.0 (code 48)** on both phones (after the day's quota reset) — closes the
   RESOURCE_EXHAUSTED screens captured in the 23:00–23:31 screenshots.
2. Set Cloudflare secret `FIREBASE_SA_JSON` (dashboard → Worker → Variables and Secrets) —
   activates the permanent kill-push tier.
3. **Review + approve the M1 document set → unlocks M2.**
