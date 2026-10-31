# Location Implementation Progress — Remote Family Support

Persistent progress document for the *Consent-Based Live Location, Route History & Movement Insights* master plan. Updated after each phase. **Nothing is marked working unless it is implemented and tested.**

---

## Current state: Phases 1–3 complete — awaiting Owner review (frontend gate not yet passed)

| Phase | Deliverable | Status | Changed files | Tests | Known issues | Next |
|---|---|---|---|---|---|---|
| 1 — Audit | `docs/location/LOCATION_FEATURE_AUDIT.md` | ✅ COMPLETE 2026-10-31 | docs only (4 new files, 0 code) | 2 independent full-source research passes, coordinator-verified | 6 documentation-drift items found (audit Appendix A) — fixes scheduled in Phase 4 | Phase 2 |
| 2 — UX spec | `docs/location/LOCATION_UX_SPECIFICATION.md` | ✅ COMPLETE 2026-10-31 | — | — | ~95 new string keys to author at Phase 4 (15 locales, BN-complete) | Phase 3 |
| 3 — Data & security design | `docs/location/LOCATION_DATA_AND_SECURITY_DESIGN.md` | ✅ COMPLETE 2026-10-31 | — | rules-probe suite designed (≥30 cases, A/H/P/E/L series) | list-rule clock-skew bounds to be locked by probe cases at Phase 8 | **Owner review gate** |
| 4 — Frontend (mock) | All screens navigable, demo-labeled | ⏸ NOT STARTED (blocked on approval) | — | — | — | after approval |
| 5 — Frontend review gate | Owner approval | pending | | | | |
| 6–14 | permissions → release checklist | pending | | | | |

## Decisions locked so far

- **D1** rules-first enforcement; no server compute in v1; any future trusted ops go to the existing Cloudflare Worker with a quota case + Owner approval.
- **D2** no push notifications for location events in v1 (recipient learns on next open).
- **D3** place alerts v1 = **self-alerts on the owner's own device** (master plan's examples are first-person); recipient-facing alerts = documented future extension.
- **D4** movement classification is device-local, render-time, always labeled an estimate; raw OS-provided speed/bearing are the only stored motion facts.
- **D5** history path = `users/{ownerUid}/locationHistory/{recordId}` (rules-ergonomic owner scoping); `liveLocations` stays exactly as-is with a dual read path for zero-downtime migration.
- **D6** retention cleanup = on-device opportunistic batch deletes on user action (§75-compliant, no sweeps); query-time expiry makes expired records invisible regardless.

## Open questions for the Owner (review package)

1. **Scope approval** — v1 ships Live + Route History + Movement Insights, Place Alerts self-serve (per the master plan's own recommendation, place alerts last). Confirm.
2. **History granularity** — ≥50 m between retained points (D-data-minimization). Coarser = fewer writes; finer = better routes. Confirm 50 m or pick 25/100.
3. **`ACCESS_BACKGROUND_LOCATION`** — v1 stays without it (honest "while the service runs" scoping). Confirm, or request the background-permission track (separate decision record + Play-policy disclosure work).
4. **Temporary-scope presets** — 1 h / 8 h / until-midnight chips (UX spec §5 step 3). Confirm or adjust.

## Change log

- **2026-10-31 — Phases 1–3 delivered.** 4 documents added under `docs/location/` (this file + audit + UX spec + data/security design). Zero code changes, zero Firebase requests from the sandbox (§75 respected; push pipeline only). Repository remains at v0.37.0 (56). CI unaffected (docs-only push).

*Last updated: 2026-10-31 · Phase next: Owner review → Phase 4 frontend (mock data).*
