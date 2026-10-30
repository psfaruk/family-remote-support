# ANDROID_PERMISSION_MATRIX — Current & Planned (Master-Plan PART 12)

> Status: **PLANNING — awaiting Owner approval.** Current manifest verified by Agent 2-b
> (2026-10-31, v0.29.0/48, `app/src/main/AndroidManifest.xml:4-25`). minSdk 26 / targetSdk 34.

## 1. Current permissions (all justified by shipped features)

| Permission | Purpose | When requested | Persistence / renewal | If denied |
|---|---|---|---|---|
| `INTERNET` | Firestore/FCM/WebRTC | install-time | — | app unusable (core) |
| `CAMERA` | QR pairing scan | runtime, in scanner flow with purpose text | until revoked | manual 12-digit code path offered |
| `POST_NOTIFICATIONS` | session asks, kill-push rescue (13+) | runtime at wizard | until revoked | asks still work in-app; killed-app rescue degraded (documented) |
| `FOREGROUND_SERVICE` | mediaProjection FGS | install-time (13+ type below) | — | — |
| `FOREGROUND_SERVICE_MEDIA_PROJECTION` | Android 14 FGS type | install-time declaration | — | — |
| `WRITE_EXTERNAL_STORAGE` (maxSdk 28) | save QR image (legacy) | legacy install-time | — | save-QR falls back to share sheet |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | presence/heartbeat reliability | wizard, with disclosure (never silent) | user-revocable | degraded background heartbeat (honest UX copy) |

Special grants (not permissions): **AccessibilityService** (`AssistService` — gesture
injection; user enables in system settings via guided `AssistSetup`; revocable anytime;
per-session assist authorization dialog) · **MediaProjection** (system consent dialog per
capture session; Android 14 re-prompts each session — OS behavior, never bypassed; visible
session bar + stop control always).

## 2. Planned additions (M5 — require approval)

| Permission | Purpose | When requested | Persistence / renewal | If denied |
|---|---|---|---|---|
| `ACCESS_COARSE_LOCATION` | approximate location (minimum viable sharing) | runtime, purpose sheet first | until revoked | sharing off; explanation state |
| `ACCESS_FINE_LOCATION` | precise location (owner chooses precision by granting either) | runtime, same flow | until revoked | falls back to coarse prompt behavior |
| `FOREGROUND_SERVICE_LOCATION` | Android 14 FGS type for sharing service | install-time declaration | — | — |
| `FOREGROUND_SERVICE` (already present) | reused by LocationShareService | — | — | — |

**Deliberately NOT requested:** `ACCESS_BACKGROUND_LOCATION` (v1 design: FGS started while
visible — compliant without it; limitation documented honestly) · `SYSTEM_ALERT_WINDOW`
(not needed) · any query/contacts/storage extras.

## 3. Android version behavior notes (compatibility facts the UX must respect)

| Android | Behavior relevant to this app |
|---|---|
| 8.x–9 (26–28) | notification channels (26+); background limits (HeartbeatWorker already used) |
| 10–11 (29–30) | background location would need separate grant — avoided by design; FGS foreground prompt (30+) |
| 12 (31/32) | SplashScreen default; precise-location toggle; approximate option surfaced |
| 13 (33) | POST_NOTIFICATIONS runtime (already handled); FGS notification shown at start |
| 14 (34) | FGS type declaration enforced; MediaProjection user-consent per session; partial media access n/a; `suggest` FGS timeout rules apply to location FGS (long-running OK) |
| OEM layer (Xiaomi/Samsung/etc.) | aggressive battery managers may kill FGS/heartbeats — battery-exemption prompt + honest "background updates may be restricted" copy; never promise uninterrupted tracking |

## 4. Consent UX rules (non-negotiable)

1. Purpose explanation **before** every runtime request (wizard/sheet copy, localized ×15).
2. Still-valid grants are reused — no repeated prompting (master PART 12).
3. Denial never dead-ends: alternative path (manual code / sharing-off / degraded features)
   + "how to enable" hand-off to system settings.
4. Sensitive active states are always visible: screen-share session bar, sharing FGS
   notification, assistant indicator. No hidden capture/collection anywhere.
5. Manifest comment drift fix (assist config `canRetrieveWindowContent` true vs comment
   `false`) lands with M4 — privacy copy must match reality.

## 5. Verification hooks

- CI: build passes with new manifest entries; AXML gate still asserts version/signing.
- Probes: `rules-liveLocations-probe.mjs` proves server-side fencing regardless of client.
- Owner physical tests: TEST_PLAN §3 rows 10–12, 18 (permission granted / denied /
  approximate / background-restricted).
