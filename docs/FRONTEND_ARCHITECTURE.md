# FRONTEND_ARCHITECTURE — Target Design (Master-Plan M2–M4)

> Status: **PLANNING — awaiting Owner approval.** No code has been changed for this document.
> Baseline facts cite the Agent 2-a audit (2026-10-31, build v0.29.0/48).

## 1. As-is summary

Single Activity + Compose + M3; 100% of navigation and ~90% of UI inside a 5,884-line
`MainActivity.kt`; manual tab state machine + overlay flags; no ViewModel; reusable
composables are file-private; `ui/Theme.kt` already implements the master palette; 15-language
i18n with `LangPrefBus`; WebRTC/accessibility/pairing logic solid in separate repos/services.

## 2. Design principles for the redesign

1. **Preserve working backend logic.** All repos (`PairingRepo`, `SessionRepo`, `PresenceRepo`,
   `SignalingRepo`, `ConfigRepo`), services (`ScreenShareService`, `AssistService`,
   `PushTokenService`) and rules stay untouched except where a documented change is required.
2. **Incremental extraction, never big-bang.** Each stage ends with a CI-green, AXML-gated
   commit and an installable APK; behavior-identical moves first, visual refresh second.
3. **One coherent shell.** 4 tabs (Home · Add Device · Tracking · Settings), shared top bar,
   nested route hierarchy, consistent loading/empty/error/permission states.
4. **Separation:** UI state (ViewModel/StateFlow) ≠ business logic (repos) ≠ data (Firestore).
5. **Design tokens only** — no hardcoded colors on screens (Theme.kt `MaterialTheme.colorScheme.*`).
6. **i18n complete** — every user-visible string via `stringResource`; key parity ×15.

## 3. Target file layout (incremental — old code deleted only after its replacement is verified)

```
app/src/main/java/com/family/remote/support/
├── MainActivity.kt            slimmed host: setContent + AuthGate + NavHost wiring only
├── ui/
│   ├── Theme.kt  Lang.kt  AuthGate.kt          (existing, extended)
│   ├── nav/AppNav.kt                            NEW: route table + shell scaffold
│   ├── components/                              NEW: promoted, public design-system widgets
│   │   ├── Cards.kt (SectionCard/AppListTile/SettingsTile/StatusBadge)
│   │   ├── Buttons.kt (BigButton/WideOutlinedButton/QuickActionTile)
│   │   ├── States.kt (EmptyState/LoadingSkeleton/ErrorPanel/PermissionDeniedPanel)
│   │   └── Badges.kt (PresenceBadge/status chips)
│   ├── home/HomeTab.kt + HomeViewModel.kt       NEW (extracted from MainActivity)
│   ├── pairing/PairingFlow.kt (+QR, role sheet) NEW (extracted PairingSections)
│   ├── tracking/TrackingTab.kt + TrackingViewModel.kt   NEW (M5)
│   ├── settings/SettingsTab.kt                  NEW (extracted)
│   └── notifications/NotificationsScreen.kt     NEW (M8)
├── LiveLocationRepo.kt  LocationShareService.kt NEW (M5, see LIVE_LOCATION_ARCHITECTURE.md)
└── (existing repos/services unchanged)
```

## 4. State management

- Add `lifecycle-viewmodel-compose`; one ViewModel per tab owning `StateFlow<UiState>`
  (data + loading + error + stale flags). Firestore listeners are opened in `viewModelScope`,
  collected with `repeatOnLifecycle`, and cancelled deterministically (fixes audit risk #5
  swallowed-error pattern by routing every `onError` into UiState).
- Existing singletons (`ScreenShareEngine`, `SessionUiBus`, `LangPrefBus`, `ThemePrefBus`)
  remain during migration; ViewModels wrap them behind the same public API until W5 decides
  on further cleanup (no forced rewrite).

## 5. Navigation (Decision C3)

- **Recommendation: adopt Navigation-Compose 2.8.x** with type-safe routes for the nested
  hierarchy (Home→Member detail, Tracking→Device location detail, Settings→subsections,
  Notifications screen), preserving bottom-tab state via `saveState/restoreState`, and
  keeping bottom-sheets/dialogs as material3 sheets (not routes) where they are modal today.
  Back-stack correctness, state restoration, and future deep links come for free.
- **Fallback (documented alternative):** keep the manual machine but add a typed nested-state
  holder per tab (sealed-class stack) if the W1 spike shows migration risk to session flows.
- W1 spike gate: migrate shell + Home only; verify cold-start-from-FCM intent, wizard overlay,
  and session fullscreen still behave before any further extraction.
- Full route table: `docs/NAVIGATION_MAP.md`.

## 6. Component library (Agent 5 owns)

Promote the file-private composables into `ui/components/*` (public, documented, themed):
cards/list tiles/settings tiles/buttons/status badges/empty-loading-error-permission states.
No second variant of the same widget is allowed (master rule: one coherent system).
QR containers, device cards, session cards, permission-explanation rows get canonical
implementations here and are reused by every screen.

## 7. Screens to redesign (mapping of existing functionality — nothing is dropped)

| Screen | Exists today | Redesign |
|---|---|---|
| Splash | system theme launch | light polish only (M2) |
| Sign-in | AuthGate (Google + email + reset) | visual refresh within tokens |
| Home dashboard | Home tab (status, quick actions, devices, activity) | restructure per master PART 4 with real data, real empty states |
| Add Device | Phone tab (QR scan / code / guest) | renamed tab; two clearly separated modes Family/Guest (PART 5) |
| Tracking | Live location placeholder | full implementation M5 (PART 6) |
| Settings | Settings tab (account, devices, setup, appearance, language, privacy, activity, about, sign-out) | grouped subsections per PART 7, nested detail screens |
| Notifications | bell → Settings | dedicated screen (PART 8) |
| Permission wizard / QR / role sheet / consent / doc overlay | overlays | kept as flows, restyled |

Fabricated data is forbidden on production screens (existing rule already honored:
no fake devices/sessions).

## 8. i18n & accessibility (Agents 19/20)

- Fix the ~16 residual hardcoded strings (ScreenShareEngine toasts, SessionRepo status lines,
  top-bar title, 2 AuthGate labels) → new keys ×15.
- Restore key parity (≈3 keys/locale missing); Bengali digits preserved for bn/hi/ne/ar/ur.
- All icons/contentDescriptions non-null; 48 dp minimum targets; contrast per UI_UX doc;
  no status by color alone; text-scale safe (no fixed heights).

## 9. Compatibility & performance guardrails (Agents 20/23)

minSdk 26 / targetSdk 34 unchanged. Android 12+ SplashScreen default; Android 13+ notification
runtime permission flow already exists; Android 14 FGS type rules already honored (mediaProjection;
`location` type added in M5). Remove per-second recomposition loops in favor of session-deadline
Flow timers; keep 2-ABI split; evaluate R8 enablement as a separate decision (record-only).

## 10. Migration order (waves; each = reviewable, CI-gated stage)

| Wave | Content | Gate |
|---|---|---|
| W1 | Spike: Navigation-Compose decision (C3) + shell skeleton + components extraction + Home tab | CI + AXML + Owner visual check |
| W2 | Add Device flow extraction + Settings extraction + notifications screen | same |
| W3 | Live location M5 (deps, engine, map, rules, probes) | same + quota probe |
| W4 | Remote-support polish + compat fixes (manifest drift) + i18n completion | same |
| W5 | Perf pass, full test-plan execution, docs/status update, release | TEST_PLAN executed |

## 11. Risks

R1 Navigation migration regression in session flows (mitigated by W1 spike gate + fallback C3).
R2 Extraction introduces subtle state-loss (mitigated: behavior-identical moves, `rememberSaveable`
parity, manual test script). R3 APK size +osmdroid ≈ +1–2 MB (acceptable). R4 Monolith surgery
conflicts (mitigated: strict file ownership in PROJECT_CONTEXT §18). R5 Quota regressions
(mitigated: budget table + probes).
