# NAVIGATION_MAP — Target Hierarchy (Master-Plan PART 3)

> Status: **PLANNING — awaiting Owner approval.** Current shell = manual 4-tab state machine
> (`MainActivity.kt:763,1304-1331`); target = same 4 tabs + type-safe nested routes
> (Decision C3 in PROJECT_CONTEXT §19).

## 1. Decision C1/C2 — primary tabs (exactly four, per master instruction)

| # | Instruction name | Current label | Change | Icon |
|---|---|---|---|---|
| 1 | **Home** | Home | keep | `Icons.Filled.Home` |
| 2 | **Add Device** | Phone | strings-only rename (all 15 locales) | `Icons.Filled.PersonAdd` (icon swap) |
| 3 | **Tracking** | Live location | strings-only rename | `Icons.Filled.LocationOn` |
| 4 | **Settings** | Settings | keep | `Icons.Filled.Settings` |

"Folder / sub-file" wording in the instruction = the nested hierarchy below; no fake
file-manager UI. Devices and Sessions are **nested screens and Home sections**, never extra
tabs (conflict C2 resolved in favor of PART 3 "exactly four").

## 2. Route tree (target)

```
RootShell (Scaffold: AppTopBar + NavHost + BottomNav, tab state saved/restored)
│   Top bar: app title (localized) · presence chip · bell → notifications (badge = unread)
├── auth (when signed out) → AuthGate (login/sign-up/reset)          [no bottom bar]
├── Tab HOME
│   ├── home                      dashboard: greeting, connection summary, device counts,
│   │                             sharing status, quick actions, pending pairing, guest status,
│   │                             security warnings (real data only)
│   ├── home/family               Family Overview → trusted-device list (empty state → Add Device CTA)
│   │   └── home/family/{relId}   Member Details: connection status, last location (M5),
│   │                             kind/guest window, available support actions (ask/assist)
│   ├── home/sessions             Recent activity (session history list; real records only)
│   │   └── home/sessions/{id}    Session detail (status, duration, ended-by)
│   └── home/add (quick action)   → routes into Tab ADD DEVICE flow
├── Tab ADD DEVICE
│   ├── add                       mode choice: Family Mode | Guest Mode (clearly separated)
│   ├── add/family                explain permanent pairing → generate QR (5-min TTL) / show code
│   │                             · "Scan a code instead" (this device joins)
│   ├── add/guest                 explain temporary session → scope+duration → create/scan
│   ├── add/scan                  QR scanner (CameraX + ML Kit) + manual 12-digit code entry
│   ├── add/progress              pairing progress / role preview / success·failure·expired
│   └── add/permissions           "what needs separate setup" wizard hand-off
├── Tab TRACKING (M5)
│   ├── tracking                  map (osmdroid) + authorized markers + device selector sheet
│   │                             + freshness (live/stale/offline) + sharing-status banner
│   │                             + loading/permission-denied/error/empty states
│   └── tracking/{relId}          Location Details: last update time, accuracy, staleness,
│                                 sharing on/off (owner), open-in-maps action, revoke/sharing controls
├── Tab SETTINGS
│   ├── settings                  grouped list (Account · Appearance · Device Mgmt · Location & Privacy ·
│   │                             Remote Support · Guest Access · Security · About)
│   ├── settings/account          account info, auth status, sign-out
│   ├── settings/appearance       theme (system/light/dark) · language (15)
│   ├── settings/devices          paired devices → detail (reuse home/family/{relId}) + revoke
│   ├── settings/location         sharing status, permission status, background info (M5)
│   ├── settings/remote           screen-share settings, assist setup, active session, terminate
│   ├── settings/guest            active guest sessions, expiry, revocation
│   ├── settings/security         trusted devices, authorization status, security notes
│   ├── settings/notifications    per-category toggles (real ones only)
│   └── settings/about            version, package, privacy doc, help
└── notifications                 header bell target: real events (pairing, status change,
                                  sharing on/off, session start/end, permission attention)
                                  [no fabricated notifications]
```

## 3. Back-stack & state rules

- Bottom tabs use `saveState/restoreState` semantics; switching tabs never loses scroll or
  nested stack (current `tabScrollStates` behavior is preserved through the NavHost).
- Detail routes pop with system back; overlays that are *modal by nature* (QR scanner,
  consent, role preview, permission wizard, doc overlay) remain dialogs/bottom sheets so the
  back key dismisses them without leaving the flow.
- Cold-start from a session notification deep-opens the active-session surface (existing
  `SessionUiBus.fullscreen` + intent path, `MainActivity.kt:126-131`) and must survive the
  nav migration (W1 gate).
- No unexpected tab resets on config change/process death: `rememberSaveable` → SavedStateHandle.
- Deep links: none in v1 beyond the FCM cold-start path (documented).

## 4. Transitions & motion

Crossfade ≤150 ms between tabs; forward slide ≈200 ms for nested routes (respect
`Animator.durationScale`/reduced-motion); no animation may imply success before the backend
confirms it (UI_UX doc §12 rule stands).

## 5. Empty / loading / error / permission states (mandatory per route)

Every route defines: loading (skeleton or spinner), empty (friendly + primary action,
e.g., Home with no devices → "Add Device" CTA), error (localized + retry, reusing
`describeFirestoreError` mapping incl. `err_quota`), permission-denied (explain purpose +
system-settings hand-off). These states are design-system components (`ui/components/States.kt`),
not per-screen ad-hoc UI.

## 6. Rename mechanics (when approved)

`nav_home / nav_phone → nav_add_device / nav_location → nav_tracking` and their 15 locale
files; bottom bar labels + Home quick-action strings updated together; version bump
v0.30.0/49 at the first implementation release. No database/rule impact.
