# NAVIGATION_MAP — Target Hierarchy (Master-Plan PART 3)

> Status: **M2 SHELL IMPLEMENTED (v0.30.0/49); M3 SCREENS EXTRACTED (v0.31.0/50);
> M4 NESTED HOME SUBTREE + SETTINGS 8 PARTITIONS IMPLEMENTED (v0.32.0/51);
> M5 TRACKING SUBTREE IMPLEMENTED (v0.33.0/52).** The 4 tabs + notifications route run
> on Navigation-Compose (Decision C3 ADOPTED — `ui/nav/AppNav.kt` routes + bars,
> crossfade 120 ms, saveState/restoreState). M4 made the **home/ subtree of §2 REAL**
> (family overview / member details / recent activity / session detail). **M5 made the
> tracking/ subtree REAL:** the Tracking tab is the osmdroid map with authorized markers,
> freshness badges and the own-sharing consent card; `tracking/{relId}` is the Location
> Details screen (read-only counterpart status, open-in-maps). Nested routes slide ≈200 ms
> honoring the system Animator duration scale; the owning tab stays selected on its nested
> routes (`AppRoutes.tabForRoute`); system back pops them (§3). Settings is the master's 8
> partition grouped list (devices/guest entries route into home/family; the Location
> partition is REAL since M5: sharing toggle + interval). **Still open by design:** `add/*`
> stays MODAL per §3 (QR scanner / role preview / permission wizard are dialogs/sheets —
> Back dismisses them without leaving the flow); `settings/*` sub-ROUTES beyond the devices
> entry remain later waves (M6/M8).

## 1. Decision C1/C2 — primary tabs (exactly four, per master instruction)

| # | Instruction name | Current label | Change | Icon |
|---|---|---|---|---|
| 1 | **Home** | Home | keep | `Icons.Filled.Home` |
| 2 | **Add Device** | Phone | strings-only rename (all 15 locales) | `Icons.Filled.Add` — M2 note: §1 originally said PersonAdd, but that icon lives in material-icons-extended (unacceptable: no R8, APK bloat); core-set `Add` shipped instead |
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
│   │                             + M4: QuickActionTiles → home/family and home/sessions
│   ├── home/family               ✅ M4: Family Overview → trusted-device list (empty state →
│   │                             Add Device CTA; PairTile + presence + guest countdowns)
│   │   └── home/family/{relId}   ✅ M4: Member Details: connection status, kind/guest window,
│   │                             available support actions (ask/assist — the old action
│   │                             sheet: request-session / family revoke / guest remove)
│   ├── home/sessions             ✅ M4: Recent activity (open sessions + last-finished;
│   │                             each row a route link — full history list needs a bounded
│   │                             query + composite index, deliberately deferred)
│   │   └── home/sessions/{id}    ✅ M4: Session detail (status, duration, ended-by; LIVE
│   │                             while the root watch holds it, else one-shot getSession)
│   └── home/add (quick action)   → routes into Tab ADD DEVICE flow
├── Tab ADD DEVICE
│   ├── add                       mode choice: Family Mode | Guest Mode (clearly separated)
│   │                             (stays MODAL per §3: the two-ways card + pair sheet +
│   │                             role sheet ARE this flow; M4 moved the paired-phone LIST
│   │                             and the action sheet OUT to home/family)
│   ├── add/family                explain permanent pairing → generate QR (5-min TTL) / show code
│   │                             · "Scan a code instead" (this device joins)
│   ├── add/guest                 explain temporary session → scope+duration → create/scan
│   ├── add/scan                  QR scanner (CameraX + ML Kit) + manual 12-digit code entry
│   ├── add/progress              pairing progress / role preview / success·failure·expired
│   └── add/permissions           "what needs separate setup" wizard hand-off
│                                 (add/scan + add/progress + add/permissions = today's
│                                 QrScanDialog / ScannedRoleSheet / PermissionWizard,
│                                 intentionally modal — see §3)
├── Tab TRACKING (✅ M5, v0.33.0/52)
│   ├── tracking                  ✅ M5: osmdroid map + authorized markers + device selector sheet
│   │                             + freshness (live/stale/offline) + own-sharing consent card
│   │                             + loading/permission-denied/error/empty states
│   └── tracking/{relId}          ✅ M5: Location Details: last update time, accuracy, staleness,
│                                 sharing on/off (counterpart read-only), open-in-maps action
├── Tab SETTINGS
│   ├── settings                  ✅ M4: the master's 8 partition grouped list (Account ·
│   │                             Appearance(theme+language) · Device Management · Location &
│   │                             Privacy · Remote Support · Guest Access · Security · About)
│   ├── settings/account          (grouped-list form ships; sub-ROUTE = later wave)
│   ├── settings/appearance       (theme + language rows inside the Appearance partition)
│   ├── settings/devices          ✅ M4 as an ENTRY: routes into home/family (the map's
│   │                             "reuse home/family/{relId}" rule); registry list stays here
│   ├── settings/location         ✅ M5 REAL: sharing toggle + 30 s/1 min/5 min interval rows
│   │                             + policy docs (privacy/rules) in the partition
│   ├── settings/remote           (notif/assist/test tiles inside the Remote Support partition)
│   ├── settings/guest            ✅ M4 as a NOTE+ENTRY: routes into home/family (guest links
│   │                             with countdowns/removal live there)
│   ├── settings/security         (permission wizard inside the Security partition)
│   ├── settings/notifications    per-category toggles (real ones only — M8)
│   └── settings/about            (About dialog + policy shortcuts, unchanged)
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

## 6. Rename mechanics — ✅ EXECUTED in M2 (v0.30.0/49)

`nav_phone → nav_add_device`, `nav_location → nav_tracking` in all 15 locale files (old keys
removed); bottom-bar labels + the two Home quick-action labels updated together; the 7 copy
strings that pointed users to the old "Phone" tab (hint_requested/active/helping,
msg_allow_ok, feed_in_progress, devices_info, devices_empty_hint) had their tab name fixed
per locale in the same push. No database/rule impact.
