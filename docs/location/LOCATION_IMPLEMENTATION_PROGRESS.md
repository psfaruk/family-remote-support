# Location Implementation Progress — Remote Family Support

Persistent progress document for the *Consent-Based Live Location, Route History & Movement Insights* master plan. Updated after each phase. **Nothing is marked working unless it is implemented and tested.**

---

## Current state: Phases 1–4 complete — awaiting Owner UI review (Phase 5 gate)

| Phase | Deliverable | Status | Changed files | Tests | Known issues | Next |
|---|---|---|---|---|---|---|
| 1 — Audit | `docs/location/LOCATION_FEATURE_AUDIT.md` | ✅ COMPLETE 2026-10-31 | docs only (4 new files, 0 code) | 2 independent full-source research passes, coordinator-verified | 6 documentation-drift items found (audit Appendix A) — **all fixed in Phase 4** (A1–A5 + orphan strings) | — |
| 2 — UX spec | `docs/location/LOCATION_UX_SPECIFICATION.md` | ✅ COMPLETE 2026-10-31 | — | — | ~95 new string keys to author at Phase 4 → **done: +120 keys ×15 locales, BN complete** | — |
| 3 — Data & security design | `docs/location/LOCATION_DATA_AND_SECURITY_DESIGN.md` | ✅ COMPLETE 2026-10-31 | — | rules-probe suite designed (≥30 cases, A/H/P/E/L series) | list-rule clock-skew bounds to be locked by probe cases at Phase 8 | — |
| 4 — Frontend (mock) | All screens navigable, demo-labeled | ✅ COMPLETE 2026-10-31 (v0.38.0/57, worklog §78) | 34 files: 14 Kotlin (6 new in `ui/tracking/` incl. `demo/DemoTrackingData.kt`; 8 extended: TrackingTab, TrackingViewModel, LocationDetailScreen, MainActivity, AppNav, Core.kt, SettingsTab, LiveLocationRepo) · 15 × `strings.xml` (+120 keys, privacy_body rewritten, 7 orphans deleted) · 2 drawables (`ic_hist_start/end`) · 3 docs (A2–A5 drift fixed) | Coordinator static verification: 14-file symbol-balance PASS; R.string references × 646-key cross-check — zero missing; inter-agent API contract audited signature-by-signature PASS; RadioOptionRow single-source check PASS; mtime inventory = expected change-set exactly. CI compile gate — see change log. | See "Known honest edges" below | **Phase 5 — Owner review** |
| 5 — Frontend review gate | Owner approval | ⏳ PENDING — review checklist in worklog §78 | | | | |
| 6–14 | permissions → release checklist | pending | | | | |

## Decisions locked so far

- **D1** rules-first enforcement; no server compute in v1; any future trusted ops go to the existing Cloudflare Worker with a quota case + Owner approval.
- **D2** no push notifications for location events in v1 (recipient learns on next open).
- **D3** place alerts v1 = **self-alerts on the owner's own device** (master plan's examples are first-person); recipient-facing alerts = documented future extension.
- **D4** movement classification is device-local, render-time, always labeled an estimate; raw OS-provided speed/bearing are the only stored motion facts.
- **D5** history path = `users/{ownerUid}/locationHistory/{recordId}` (rules-ergonomic owner scoping); `liveLocations` stays exactly as-is with a dual read path for zero-downtime migration.
- **D6** retention cleanup = on-device opportunistic batch deletes on user action (§75-compliant, no sweeps); query-time expiry makes expired records invisible regardless.
- **D7 (Phase 4, 2026-10-31):** the Owner issued the "Phase 4" go-ahead without objections to the review-package questions — the four open questions are resolved by their documented defaults, each veto-able at the Phase 5 review: (1) v1 scope = Live + Route History + Movement Insights, Place Alerts self-serve last; (2) history granularity = 50 m between retained points; (3) v1 stays **without** `ACCESS_BACKGROUND_LOCATION` (honest "while the service runs" scoping); (4) temporary-scope presets = 1 h / 8 h / until-midnight.

## Known honest edges (Phase 4)

- Real-side member statuses are the **derivable subset only** (SHARING_LIVE / LAST_LOCATION / DEVICE_OFFLINE / UNAVAILABLE / STOPPED, from the existing live doc). SHARING_PAUSED and PERMISSION_REQUIRED render in demo data; they need the Phase 8 `locationAuthorizations` entity before they can be real.
- No history button on real member cards and no section B on real details — no authorization source exists yet (honest absence, no teaser cards).
- The upload-state line (`up_*`) renders in demo (4 states, tap-to-cycle on the demo own-card); real wiring arrives with the Phase 7 engine surfacing write results — until then the line is silent (OK semantics); nothing is fabricated.
- The Places screen is in-memory only for Phase 4 (no persistence, no geofencing, no notifications — Phase 11 scope; the screen's own footer says so).
- The History screen with a real (non-demo) relId shows the honest empty state — data arrives with Phases 8/9.
- Wizard scope/retention choices are recorded locally (`LocationSharingPrefs`); the engine still runs "until stopped" semantics in this wave — once/temporary auto-stop lands with the Phase 7 engine. The wizard copy describes the chosen scope truthfully and Stop always works.
- Demo entry = the empty-state button (UX spec §11) **plus** one quiet secondary entry at the bottom of the loaded real dashboard — a documented micro-deviation so the Phase 5 review is reachable on populated accounts (demo data still never mixes with real listeners).

## Change log

- **2026-10-31 — Phases 1–3 delivered.** 4 documents added under `docs/location/`. Zero code changes, zero Firebase requests from the sandbox (§75 respected).
- **2026-10-31 — Phase 4 delivered (§78, v0.38.0/versionCode 57).** Full screen set: Tracking dashboard (member cards with 7-state chips + freshness icon/label + always-visible last-known line; map zoom buttons, accuracy circles, stale dashed rings; Places entry), 6-step `SharingSetupSheet` consent wizard replacing the old single consent sheet, `tracking/{relId}` sections A–E, `tracking/{relId}/history` (day chips, solid segments broken at >10-min gaps with dashed gap polylines + `hist_gap` chips, start/end markers, summary card, retention line), `tracking/places` (in-memory list + add form with map-center pick, radius/toggles/cooldown chips), `MovementCard` (estimate-labeled, speed hidden when implausible, confidence only Limited/Moderate). Demo strategy per UX spec §11: explicit entry, persistent "DEMO DATA — not real locations" banner on every demo screen, `DemoTrackingDataSource` in-memory fixtures (3 fictional members covering the 7 statuses via the gallery, 2-day Dhaka-area route with a deliberate 20-minute gap, demo places), **zero Firestore traffic** (real TrackingViewModel parked via `observe(null)` while demo is active), demo state never persisted, system-back/banners-✕ exits. Audit Appendix A closed: `privacy_body` rewritten ×15 locales (A1 — matches shipped M5: live-only, consent-based, stop-deletes, no history collected), 7 orphan strings deleted (A6 + `set_loc_status`), LIVE_LOCATION_ARCHITECTURE revoke/freshness corrected (A2/A5), PROJECT_CONTEXT row marked shipped (A3), ANDROID_PERMISSION_MATRIX header CURRENT (A4). `RadioOptionRow` promoted to `ui/components/Core.kt` (Settings + wizard share one source). +120 string keys ×15 locales (BN complete, 13 mirror EN). CI: first-HEAD run failed on exactly one Compose import (toggleable package path — `foundation.selection`, not `foundation`); 1-line fix → run **739 SUCCESS** with the Release-APK verification gate (✓ versionCode = 57 · ✓ versionName = "0.38.0"); full record in worklog §78.

*Last updated: 2026-10-31 · Phase next: Phase 5 — Owner review of every rendered state (checklist in worklog §78).*
