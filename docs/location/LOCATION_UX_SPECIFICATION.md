# Location UX Specification — Remote Family Support

**Status:** COMPLETE (Master Prompt Phase 2) · **Baseline:** v0.37.0 (56) · 2026-10-31
**Depends on:** `LOCATION_FEATURE_AUDIT.md` (facts), existing design system (`docs/UI_UX_DESIGN_SYSTEM.md`, `ui/components/`), `docs/NAVIGATION_MAP.md`.
**Rule:** extend the existing Tracking tab and design system — **no fifth bottom tab**, no new visual language. All user-facing text via Android resources (`loc_*`, `hist_*`, `ins_*`, `place_*` prefixes), EN + BN (+13 locales in the same keys).

---

## 1. Screen inventory & navigation

```
Tracking (tab — dashboard, extended)
├─ Own sharing card (extended → 6-step setup wizard)
├─ Family member cards (NEW list) + map (existing)
├─ tracking/{relId}            member detail (extended: sections A–E)
│   └─ tracking/{relId}/history   journey history (NEW: date selector + route)
├─ tracking/places             my saved places & alerts (NEW, own device)
└─ (modal) SharingSetupSheet   6-step consent wizard (NEW)
```

Back-stack rules follow `NAVIGATION_MAP.md` §3 unchanged (slide ≈200 ms, reduced-motion honored, tab stays selected via `tabForRoute()`).

## 2. Tracking dashboard (tab root)

Top-to-bottom (all inside the existing scroll column; canonical state priority unchanged: routed error → `ErrorPanel`; !loaded → `LoadingSkeleton(3)`; empty → `EmptyState`):

1. **Own sharing card** (`OwnSharingCard`, extended). Title/status one of:
   - `loc_share_on` "Location sharing is ON" / "লোকেশন শেয়ার চালু আছে" + upload-state line (NEW, see §8)
   - `loc_share_off` + `BigButton(loc_share_start)` → now opens the **6-step setup wizard** (§5) instead of the old single consent sheet
   - `loc_share_paused` "Sharing paused" / "শেয়ার বিরত" (NEW status) + resume/stop buttons
2. **Member list (NEW)** — one `MemberLocationCard` per authorized counterpart (replaces today's hidden-in-map list; the device-selector sheet remains for map focus). Card content (master plan §4.1):
   - `AppListTile`: initials avatar, display name (model → email fallback), **status chip** (`StatusBadge`, see §3)
   - Last successful update ("Updated 9:15 AM" / "সর্বন্ধীন ৯:১৫"), accuracy ("±25 m" / "±২৫ মিটার") when available, current/stale flag via existing freshness colors + **icon** (never color-only: ● live / ◐ stale / ○ offline + label)
   - `TextButton(loc_details)` → `tracking/{relId}`
   - `TextButton(hist_view "Journey history")` → `tracking/{relId}/history` **only when the authorization grants history** (`historyAccessEnabled`)
3. **Map** (`CounterpartMapSection`, existing) — gains: accuracy circle overlay per marker (when `accuracyMeters` present), zoom +/− buttons (48 dp, contentDescription `cd_map_zoom_in/out`), route overlay entry point (from history screen), and "stale marker" styling (dashed circle) for STALE/OFFLINE markers with the existing last-known snippet.
4. **Places entry** — `WideOutlinedButton(place_my_title "My places & alerts")` → `tracking/places` (own-device self-alerts; see §7).

## 3. Sharing status model (7 states, master plan §4.1)

One `LocationShareStatus` enum drives chip kind, card copy, and member-detail header — rendered with **icon + label + color** (accessibility: never color alone):

| Status | Chip kind | Meaning (plain copy EN / BN) |
|---|---|---|
| `SHARING_LIVE` | success | "Sharing live location" / "লাইভ লোকেশন শেয়ার হচ্ছে" |
| `SHARING_PAUSED` | warning | "Sharing temporarily paused" / "শেয়ার সাময়িকভাবে বিরত" |
| `PERMISSION_REQUIRED` | error | "Location permission needed" / "লোকেশনের অনুমতি দরকার" + settings hand-off (`PermissionDeniedPanel` pattern) |
| `DEVICE_OFFLINE` | neutral | "Device offline" / "ডিভাইস অফলাইন" — **always paired with last-known time; never shown as current** |
| `LAST_LOCATION` | neutral | "Last location received" / "সর্বন্ধীন লোকেশন পাওয়া গেছে" (doc exists, sharingEnabled true, stale) |
| `UNAVAILABLE` | neutral | "Location unavailable" / "লোকেশন পাওয়া যাচ্ছে না" (no doc / never shared) |
| `STOPPED` | neutral | "Sharing stopped by owner" / "মালিক শেয়ার বন্ধ করেছেন" |

Freshness labels stay as-is (LIVE < 2 min / STALE < 5 min / OFFLINE) and are displayed **separately** from share status (a live share can still be momentarily stale).

## 4. Member detail screen — `tracking/{relId}` (sections A–E, master plan §4.2)

Keeps existing header (`BackTitleRow(loc_details)`, email, role line, freshness, `loc_updated`, `loc_acc_m`, read-only counterpart share status, `loc_open_maps`) and adds authorization-gated sections **rendered only when permitted** (each section card shows a one-line "what you may see" note):

- **A. Current location** (existing content + accuracy circle + optional reverse-geocoded approximate line `loc_addr_approx "Near: %1$s (approximate)"` — only when geocoding is enabled; failure → line simply absent, pin stays usable).
- **B. Journey history** (NEW, only if authorization grants): summary chips (points, approximate distance, coverage %) + `BigButton(hist_open "View journey history")` → `tracking/{relId}/history`; else nothing (no locked/teaser cards — honest absence).
- **C. Movement insights** (NEW, only if authorized + data quality sufficient): see §6.
- **D. Place alerts on this member** (NEW, only if the member's owner has enabled recipient alerts — **v1 defers recipient alerts**; section reserved, ships hidden).
- **E. Sharing & privacy** (NEW): who receives (recipient list from authorization), sharing expiry (if temporary scope), retention period in force, revoke state, and "History access: granted/none" line. Owner-side equivalent lives in the wizard's confirm step.

Not-found / revoked / expired → existing honest `EmptyState(fam_not_found)` path (unchanged).

## 5. Location-sharing setup wizard — `SharingSetupSheet` (6 steps, master plan §4.3)

A `ModalBottomSheet` state machine (steps non-skippable; back = previous step; cancel anytime = nothing changed):

| Step | Content | Strings (new) |
|---|---|---|
| 1 Explain | What is collected (position + accuracy + time), why, who receives it (named recipients from step 2), whether history is stored, how long kept, how to pause/stop, what happens offline. Existing `loc_share_body` text is the shortened descendant reused here. | `setup_explain_*` |
| 2 Recipients | Explicit multi-select of paired ACTIVE counterparts (`AppListTile` + checkbox rows). Nobody selected → Next disabled. "Being linked is not permission" footer reused (`rel_always_permission`). | `setup_pick_*` |
| 3 Scope | Radio: `scope_once` "Share once" · `scope_temp` "Share for a set time" (chips: 1 h / 8 h / until midnight) · `scope_until_stop` "Until I stop it" (default, matches today) · `scope_with_history` "Share + keep journey history". Each row's helper text explains ongoing behavior + how to revoke. | `setup_scope_*` |
| 4 Retention (only if scope = with-history) | Radio: `ret_none` "Don't save history" · `ret_24h` "24 hours" · `ret_7d` "7 days" · `ret_30d` "30 days" + honest note: `ret_note` "Records expire automatically; deletion also removes them from the app. Old copies may persist briefly in backups." | `setup_ret_*` |
| 5 Permissions | Contextual FINE/COARSE request (existing launcher reused); if history chosen and battery-optimized devices detected → honest note `bg_note` "Sharing continues while the phone is on and the app's location service is running. Battery saver or switching the phone off can pause updates." NO new permission requested in v1. | `setup_perm_*` |
| 6 Confirm | Read-back summary card: recipients, scope, retention, expiry, [Start sharing] `BigButton`. Explicit tap = consent recorded (authorization doc created in Phase 8; Phase 4 mock = local state only). | `setup_confirm_*` |

"Share once" scope: writes a single fix then auto-stops (reuses existing stop path). Temporary scope: expiry timestamp shown everywhere the share status appears.

## 6. Movement insights presentation (master plan §8.3)

`MovementCard` (in member detail §4-C and history screen):
- Line 1: `ins_move "Movement: %1$s"` with label from `MovementClassifier` — `ins_walk "Possibly walking"` / `ins_cycle "Possibly cycling"` / `ins_vehicle "Possibly in a vehicle"` / `ins_still "Possibly stationary"` / `ins_unknown "Movement type unavailable"`.
- Line 2: `ins_speed "Estimated speed: %1$s"` (km/h, rounded; hidden when implausible).
- Line 3: `ins_obs "Last observation: %1$s"`.
- Line 4: `ins_conf "Confidence: %1$s"` — only two honest values: `ins_conf_limited "Limited"` (points-based) / `ins_conf_moderate "Moderate"` (sensor-backed), never a fabricated numeric score.
- Insufficient evidence → single line `ins_unknown`; **no card at all when no fresh data** (never show stale classification as current).

## 7. My places & alerts — `tracking/places` (v1: self-alerts, owner's own device)

- List of saved places (`AppListTile` + map-pin icon, name, approximate radius); `BigButton(place_add)` opens a small form (name + pick-on-map at current position or map center; radius chips 100/200/500 m).
- Per place: arrival/departure toggles, cooldown (default 10 min, min 5), status line ("Monitored while sharing is on" — honest scope: geofence active only while the location FGS runs; v1 does NOT request background location).
- Owner-informed by definition (it is the owner's own phone notifying the owner). Recipient-facing alerts are explicitly listed as a future extension in the screen footer note `place_future_note`.
- Alerts fire as local notifications on the existing `location_sharing` channel pattern (coordinate-free text: "You arrived near %1$s").

## 8. Upload-state & offline honesty (master plan §13)

`OwnSharingCard` gains an upload-state line (local, no extra Firestore traffic — derived from the service's write results):
- `up_ok` — silent (absent line) when last write succeeded
- `up_pending` "Update waiting for internet…" / "ইন্টারনেটের জন্য অপেক্ষা করছে…" (warning color)
- `up_failed` "Couldn't send last update — will retry with the next fix" / "সর্বন্ধীন আপডেট পাঠানো যায়নি — পরের ফিক্সে আবার চেষ্টা হবে"
- `up_unavailable` "Location update unavailable — check location settings" / "লোকেশন আপডেট পাওয়া যাচ্ছে না — লোকেশন সেটিংস দেখুন"
Never a false "recipient has received it" claim; the recipient side keeps showing its own freshness labels.

## 9. Journey history screen — `tracking/{relId}/history`

- Header: `BackTitleRow(hist_title "Journey history")` + member name.
- **Date selector**: horizontal day chips (last N days where records exist — derived from a single metadata doc, not N queries), + "No records kept" empty (`hist_none "No journey records for this day — either sharing was off or history wasn't enabled."`).
- **Route map** (osmdroid, same lifecycle rules): `Polyline` segments **broken at gaps** (gap = >10 min between consecutive points → dashed placeholder + `hist_gap "No data %1$s – %2$s"` chip); start marker (green ▶) and end marker (■) with times; point markers omitted beyond a density cap (cluster-simplified to path only).
- **Summary card** (only with data): `hist_sum_points "Recorded points: %1$d"` · `hist_sum_dist "Approximate distance: %1$s (from recorded points)"` · `hist_sum_time "From %1$s to %2$s"` · `hist_sum_cov "Data coverage: %1$d%% of the period"` + `hist_sum_note "Distances are approximations between recorded points, not the exact traveled path."`
- **Retention line**: `hist_ret "History kept: %1$s"` (or `hist_ret_none`); owner-side "Delete my history" lives in the wizard confirm screen & Settings (with scope confirm dialog: day / all).

## 10. Revocation UX (all flows)

- **Owner revokes a recipient** (wizard step 2 edit / detail §4-E): confirm dialog `rev_confirm "Stop %1$s's access to your location and history?"` → authorization `revokedAt` (Phase 8) → UI updates instantly (recipient disappears from authorized lists); recipient sees honest not-found on next access — no crash, no stale card.
- **Owner pauses sharing**: `loc_share_paused` status; map markers across recipients flip to last-known styling.
- **Recipient disconnects pairing** (existing Remove/Revoke): location access ends via rules (today's behavior) — recipient's own UI shows `hist_access_end "You no longer have access to this history."`
- **Owner deletes history**: confirm scope → progress → verified-empty state; offline → `del_offline "History will be deleted when you're back online."` (queued, honest).

## 11. Demo-data strategy (Phase 4 ONLY — master plan §4 of phases)

- Demo mode is **explicitly entered** ("Preview the new screens (demo data)" button on the Tracking empty state) — never automatic, never mixed with real listeners.
- Persistent top banner on every demo screen: `demo_banner "DEMO DATA — not real locations"` / "ডেমো ডেটা — প্রকৃত লোকেশন নয়" (warning color, `Badge` chip + `SectionCard` info).
- Demo fixtures: 3 fictional members with the 7 statuses, a 2-day demo route (Dhaka-area coordinates, deliberate 20-min gap), demo movement values, demo places — all from a local `DemoTrackingDataSource` object; zero Firestore reads/writes; map renders fixtures through the same composables (a `LocationSource` interface keeps the swap clean).
- Exiting demo (back / banner ✕) returns to the real empty state; demo state is never persisted.

## 12. Accessibility & localization checklist (Phase 5 gate items)

- Every status = icon + text + color (§3); touch targets ≥ 48 dp (zoom buttons, chips, wizard options); map zoom buttons have `contentDescription`.
- Screen-reader: status chips `semantics` announce "Location sharing status: …"; freshness line announced on member card; demo banner announced on screen entry.
- Bengali: all new strings authored BN-first then EN, layout verified at 1.3× font scale (long BN strings wrap — use `heightIn` + wrap, never fixed heights); numbers localized via existing locale-aware formatting.
- Reduced-motion honored (existing animator-scale check) — map camera animateTo stays (user-triggered).

## 13. New string keys (authoring list — EN / BN canonical pair per key)

Prefix blocks: `loc_*` (status additions: paused/last/unavailable/stopped), `setup_*` (wizard, ~24 keys), `hist_*` (~22), `ins_*` (10), `place_*` (~16), `up_*` (4), `demo_*` (3), `cd_*` (2). Full key-value table is produced during Phase 4 implementation (single PR, resources-only, all 15 locales — placeholders mirror EN until each locale is translated; BN ships complete).

---

*Phase 2 complete. Review gate: Owner approves this UX plan (plus the audit + data/security design) before Phase 4 frontend implementation begins. No code has been changed.*
