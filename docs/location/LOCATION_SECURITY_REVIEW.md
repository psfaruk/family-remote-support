# Location Security Review — Remote Family Support

**Status:** COMPLETE (Master plan Phase 12) · **Reviewed baseline:** v0.43.0 (versionCode 63) · 2026-11-01
**Scope:** every location surface shipped across the master plan (M5 live sharing, Phase 8 authorizations + history rules, Phase 9 route history, Phase 10 movement insights, Phase 11 place alerts) — `firestore.rules`, the engine/services/repos, local on-device storage, notifications, user-visible strings, the manifest, and the dependency set — audited against `LOCATION_DATA_AND_SECURITY_DESIGN.md` §10 (the checklist that document explicitly deferred to "the Phase 12 review"), plus a threat-model pass over the whole enforcement stack.
**Method:** full-source static review of the shipped code at the wave HEAD, **one live read-only verification of the RELEASED Firestore ruleset against the repo file** (evidence in §2.1 — run this phase, not claimed from memory), and citation of the three recorded live probe gates + CI gates. **Zero app code changes in this phase** (review deliverable only; findings route to fix waves at the Owner's direction). §75 posture: 2 read-only REST GETs + 1 OAuth token exchange — no document reads/writes.

---

## 1. Executive summary

**The enforcement layer is sound.** Every location read/write in production is rules-bound; the rules file in the repo is **byte-exact identical to the ruleset actually released** on `cloud.firestore/default` (live-verified this phase, §2.1); the three live probe gates stand at **76/76 cumulative** (35/35 location-v2 §82 · 16/16 liveLocations §83 · 25/25 placeAlerts §84, all first-run green against the released rulesets, zero residue); 44 JVM unit tests run in CI on every push. No finding below is an access-control bypass.

**The disclosure layer has one material defect (S-1).** The user-visible privacy string (`privacy_body`, Settings → Privacy, ×15 locales) still says *"no location history is collected in this version"* and lists *"Location history, saved places, or movement profiles"* under **"What is NEVER collected"** — but route history (v0.40.0), saved places with coordinates (v0.43.0), and movement facts (speed/bearing/altitude, v0.40.0) all shipped. This is the audit-A1 drift pattern repeating at feature scale: the Phase 4 rewrite was accurate for v0.38.0; Phases 7–11 shipped the features and nothing re-synced the disclosure. **Recommended: a string-only fix wave before or with Phase 14** (which already mandates the Data-safety/privacy-policy sync as its final gate).

Three further findings, all Low/Medium, all with recommended one-line-or-decision fixes: the Firestore SDK's default **disk cache** persists location documents at rest (the design §5-step-6 verification "offline persistence stays disabled" was never executed — nothing configures it, §5.2); the designed `locationAuditEvents` collection was **never implemented** (consent forensics surface only — no enforcement impact, §5.3); and the place-alert **local mirror survives sign-out** (uid-scoped, own-device data, §5.4).

| # | Finding | Severity | Type | Fix size |
|---|---|---|---|---|
| S-1 | `privacy_body` contradicts shipped features (history/places/movement) | **HIGH** (user-visible disclosure) | disclosure drift | strings ×15 |
| S-2 | Firestore default disk cache holds location docs at rest; design's "persistence disabled" never verified | MEDIUM (rooted/forensic device only) | at-rest exposure | 1 decision record (+ optional settings line) |
| S-3 | `locationAuditEvents` (design §2.4/§5-step-8) never implemented | LOW | design divergence | decision: implement (rules+probe wave) or amend design |
| S-4 | `PlaceAlertStore` SharedPreferences mirror (name+lat+lng) survives sign-out | LOW (own device, uid-scoped) | at-rest residue | 1 line (clear on sign-out) or accept-and-document |

Section 6 records five zero-severity observations (designed behaviors worth having in one place), and §7 states what this review deliberately does not cover.

---

## 2. Evidence base

Everything below was checked this phase against the working tree at the v0.43.0/63 wave HEAD (`cbbbb2d` content — see clone-hygiene note in the worklog §85), unless explicitly cited to a recorded gate.

### 2.1 LIVE: released ruleset == repo rules (run 2026-11-01, this review)

- Release in force: `projects/remote-family-support/releases/cloud.firestore/default` → ruleset **`2fc2e1da-d3d6-4aec-a0b1-af3f1c06647b`** (the §84 Phase-11 ruleset — no rules were published after it).
- Ruleset content fetched read-only and compared: **byte-exact match** with the repo `firestore.rules` — sha256 `8fc066c7b8b89a472bb8fc5c5013cc36cff1bc214ac9fef0da44470333d8f459`, 54,470 bytes, 944 lines, both sides.
- Meaning: **the rules this review reads are the rules production enforces.** No undocumented rules drift. (The check is now a permanent tool: `scripts/verify-released-rules.mjs` — read-only, exit 0 on match — added with this phase; Phase 14's release checklist should run it as a standing gate.)
- Cost: 1 release-list GET + 1 ruleset GET (§75: read-only, user-scheduled review verification).

### 2.2 Recorded live gates (cited, not re-run — each was a user-scheduled gate execution)

| Gate | Result | Record |
|---|---|---|
| `rules-location-v2-probe.mjs` (authorizations + history + dual live path) | **35/35** EXPECTATIONS MET (34 cases, 35 assertions), zero residue — includes the app's exact D10a day-query contract executed end-to-end through the live rules by an authorized family recipient | worklog §82 |
| `rules-liveLocations-probe.mjs` (16 cases after the §83 speed extension) | **16/16**, zero residue | worklog §83 |
| `rules-placeAlerts-probe.mjs` (25 cases, first run) | **25/25 ALL GREEN**, zero residue — includes the Bengali 56-char/154-byte `size()` character-semantics lock (P5) | worklog §84 |
| CI unit tests | 44/44 (MovementClassifier 25 + PlaceAlertEngine 19) at the v0.43.0/63 HEAD; Release-APK verification gate PASS (63/0.43.0, APK Sig Block v2) | worklog §84 |

### 2.3 Static review coverage (this phase)

`firestore.rules` (all four location blocks + helpers + catch-all), `AndroidManifest.xml`, `app/build.gradle.kts` (full dependency list), `Fb.kt`, `LocationShareService.kt` (lifecycle, gates order, notification, upload reporting), `LiveLocationRepo.kt` (write paths, history log, stop path), `LocationAuthzRepo.kt` (grant/revoke/queue/flush), `PlaceAlertRepo.kt` (CRUD, local store, runtime, notifier), `MainActivity.kt` (sign-out teardown block), `NotificationsLog.kt`, `strings.xml` + `values-bn/strings.xml` (privacy, retention, notification strings), plus repo-wide greps for audit-events code, Firestore settings code, and coordinate-bearing log calls.

---

## 3. Design §10 checklist — item-by-item re-verification

The Phase 3 design document closed with ten checkboxes "to be re-verified at Phase 12 review." Verdicts, with evidence:

| # | Item (design §10 wording, abbreviated) | Verdict | Evidence |
|---|---|---|---|
| 1 | Notifications carry no coordinates | **PASS** | FGS tray = `loc_notif_title`/`loc_notif_body` ("Location sharing is on" / "Tap to open the app and stop sharing." — strings.xml:77–78); place alerts = `place_notif_arrive`/`place_notif_depart` ("You arrived near %1$s" — strings.xml:217–218, **name only**; PlaceAlertRepo.kt:372–395 builds the notification from exactly these); BN parity spot-checked. No other location notification exists (D2: no push for location events). |
| 2 | Audit events metadata-only (hasOnly-enforced) | **VACUOUS — never shipped → S-3** | Zero matches for `locationAuditEvents`/audit-event code across `*.kt`, `firestore.rules`, `scripts/` (grep this phase). No rules block exists (the catch-all would deny writes anyway). The only consent-event surface is the local `NotificationsLog` (`sharing_started`/`sharing_stopped`, LocationShareService.kt:210/230). |
| 3 | No coordinates in `NotificationsLog` | **PASS** | `NotifEvent` carries kind + timestamp + at most a counterpart email (NotificationsLog.kt:17–40, doc comment: "never any coordinates"); capped at 100; **cleared at sign-out** (MainActivity.kt:1256). |
| 4 | No crash/analytics SDK ships location | **PASS** | `app/build.gradle.kts` dependencies: firebase-auth, firebase-firestore, firebase-messaging, play-services-location, osmdroid, webrtc, camerax/mlkit (QR), zxing, work-runtime, credentials — **no Analytics, no Crashlytics, no Sentry, no ads SDKs**. Nothing in the build observes location except the app's own code. |
| 5 | No local history cache | **PASS at the app layer / OPEN at the SDK layer → S-2** | App layer: history lives only in ViewModel memory (one-shot queries; ErrorPanel on failure — no partial-cache presentation); `LocationHistoryLog.Entry` = (docId, recordedAtMillis, retentionExpiresAtMillis) — **no coordinates** (LiveLocationRepo.kt:799–846); demo fixtures never persisted. SDK layer: design §5-step-6 said "Firestore offline persistence stays disabled for location data (**verify at Phase 8**)" — **no `FirestoreSettings` exists anywhere in the app** (grep: zero matches; `Fb.kt` instantiates with only the database id), so the Firestore Android **default disk cache is ON** and holds listened/read location docs at rest. The Phase 8 verification never executed. |
| 6 | Sign-out stops FGS + deletes own live doc + flushes queued deletions best-effort | **PASS (with S-4 residue note)** | MainActivity.kt:1190–1264: `stopLocationSharing(context)` (:1208 — which also enqueues the **revoke-all** via LiveLocationRepo.kt:445→449) → `deleteOwnLocation` while still authenticated, best-effort (:1216–1226) → presence/token delete (:1227–1231) → `signOut()` (:1241) → `clearCredentialState` (:1243) → `NotificationsLog.clear` (:1256). Offline revokes/deletes queue in SharedPreferences (uids/dayStart only — metadata) and flush on the next signed-in user action (TrackingTab.kt:339 `flushPendingLocationAuthz`). |
| 7 | Privacy-policy strings match real behavior | **FAIL → S-1** | `privacy_body` (strings.xml:299) says "Only the latest position is kept — **no location history is collected in this version**" and, under "What is NEVER collected": "**Location history, saved places, or movement profiles in this version.**" Shipped reality since v0.40.0/v0.43.0: opt-in route history under `users/{uid}/locationHistory`, saved places with lat/lng under `placeAlerts`, and OS-provided speed/bearing/altitude on history records. Materially false disclosure, user-visible in Settings → Privacy. |
| 8 | Retention disclosure includes backup caveat | **PASS** | `ret_note` (strings.xml:146): "Records expire automatically; deleting them also removes them from the app. **Old copies may persist briefly in backups.**" Present in BN (`values-bn` spot-check) and rendered on the history surface. |
| 9 | Rules probes 100 % PASS at gate | **PASS** | 35/35 (§82) + 16/16 (§83) + 25/25 (§84) = **76/76** across the three location suites, all executed live against the published rulesets, all zero-residue. §2.2. |
| 10 | `hasOnly` blocks every forged-field vector in the probe series | **PASS** | Every location write path carries a `keys().hasOnly` field fence (liveLocations :667–668, locationAuthorizations :744–748, locationHistory :845–850, placeAlerts :902–906) and the N-series probes exercised forged keys (hasOnly smuggling, type confusion, forged docId/ownerUserId) with denies in all three suites. Extra-unknown-key writes are structurally impossible. |

**Score: 7 PASS · 1 FAIL (S-1) · 1 vacuous (S-3) · 1 pass-with-open-sdk-layer (S-2).** The enforcement-side items (9, 10) are the strongest results in the checklist; the defect and the two gaps are all on the disclosure/forensics side.

---

## 4. Threat-model pass

Adversary classes, attempted actions, and outcomes. "DENY" outcomes cite the rule lines and the probe cases that locked them.

| Adversary | Attempt | Outcome |
|---|---|---|
| **Stranger** (any signed-in account, no relationship/grant) | Read/list/write any location doc; forged docIds; unfiltered enumeration | DENY — every block re-verifies identity against path/fields; docId bindings (`uid_[0-9a-f]{24}`, `recipientUid_ownerUid_deviceId`, `[0-9a-f]{24}_[0-9]+`, `uid_[0-9a-f]{16}`); catch-all deny (rules :939–941). Probes: N-series across all three suites (stranger GET/LIST/delete all 403). |
| **Family recipient** (active grant) | Over-read beyond the grant: live without `liveEnabled`, history without `historyEnabled`, other recipients' grants, expired-retention records, out-of-scope queries | DENY — `locationAuthzFeature` (:88–91) checks the exact flag; grant reads limited to own-doc get / own-filter list (A-series probes); retention floor on every read including list with the 5-min skew buffer (:795–804; H-series, incl. H11 poisoning + H10 owner-invisible-expired). |
| **Family recipient** | Escalation: write/extend/un-revoke the authorization, self-grant history | DENY — authz writes are owner-only (:719–777); updates require `ownerUserId == request.auth.uid`, are diff-fenced to 7 keys, and **terminal revoke** (`!('revokedAt' in resource.data)` :754) closes the un-revoke hole; recipient create DENY + self-escalation DENY are probe-locked (A-series). |
| **Guest recipient** after window lapse | Continue reading via a still-listed relationship | DENY — `pairingActiveWith` compares `guestExpiresAt > request.time` on **every** evaluation (:58–69); `locationAuthzFamilyOrPaired` requires it for guests (:97–100). (The audit's §4.8 "never status alone" invariant held through every wave.) |
| **Revoked recipient** | Reuse a revoked grant | DENY — `locationAuthzActive` excludes any doc carrying `revokedAt` (:77–84); evaluated per request, no cache; revoked-recipient 403 probe-locked. |
| **Forged client with a stolen owner account** | Fabricate other users' data; forge fields; poison history | Partial by design (D1 rules-first): can write only **its own** collections within `hasOnly` + ranges + time windows; cannot impersonate another owner (path/field identity re-verification), cannot write plausible-but-fake motion beyond the 0–90 m/s / bearing / altitude windows, cannot back-date beyond ±10 min. **Cannot be rate-limited by rules** (audit §4.6): can flood its own history up to project quota — the known, accepted D1 tradeoff (O-4). |
| **Malicious app on the same device** | Read location data at rest | Mitigated by the Android sandbox: all storage is app-private (filesDir/SharedPreferences), `allowBackup=false` (manifest) keeps it out of cloud backups and device transfers, no location data ever touches external storage (the only WRITE_EXTERNAL_STORAGE use is maxSdk 28 for the QR PNG). Residual: **rooted/forensic devices** can read the Firestore disk cache (S-2) and the places mirror (S-4). |
| **Network observer** | Intercept location | Mitigated: all Firestore traffic is TLS (gRPC/REST over HTTPS); location never appears in notification payloads (item 1) and never in URLs; QR pairing is a separate, token-bound scope. |
| **The OS/user on the owner's own device** | Hidden tracking | DENIED by construction — the no-hidden-tracking guarantee: location-type FGS with a persistent, coordinate-free tray notification; `START_NOT_STICKY`; alerts live exactly as long as the tray does (D13/§84 honest edge, cuts both ways); sign-out teardown (item 6). |

---

## 5. Findings register

Each finding: evidence → impact → recommended action. Severities are disclosure/privacy-weighted, not exploit-weighted — none of these is an enforcement bypass.

### S-1 (HIGH, disclosure) — `privacy_body` contradicts the shipped feature set

- **Evidence:** strings.xml:299 (quoted in §3 item 7); the same text ships ×15 locales (BN included). Phase 4 rewrote it accurately **for v0.38.0** (worklog §78, audit-A1 closure); Phases 9 (v0.40.0 history), 10/7 (movement facts on records), and 11 (v0.43.0 saved places) shipped afterwards without re-syncing it.
- **Impact:** every user who opens Settings → Privacy is told location history, saved places, and movement data are never collected — while the app (with their consent, opt-in, retention-bounded) does exactly those things. This is the single highest-priority item of this review because it is the one finding an ordinary user can actually read, and it misstates precisely the features the master plan's §19 disclosure duty covers. Enforcement is unaffected.
- **Recommended action:** string-only wave (privacy_body ×15 locales, BN complete): state that sharing is consent-based and stoppable; that **with** the owner's explicit choice, journey history is kept for the chosen retention and expires automatically; that saved places (name + coordinates) are stored to survive reinstall; that movement facts (speed) come from the OS only on history records. Fold in the Phase 14 "Data-safety/privacy-policy sync" gate either way. **Process recommendation:** make "privacy string re-check" a standing line in every feature wave's checklist (the release checklist, Phase 14, should hard-code it) — this is the second occurrence of the same drift class (A1 → S-1).

### S-2 (MEDIUM, at-rest) — Firestore disk cache holds location documents; the design's "persistence disabled" was never verified

- **Evidence:** design §5 step 6: "Firestore offline persistence stays disabled for location data (**verify at Phase 8 — implementation check**)". Grep this phase: **zero** occurrences of `FirestoreSettings` / `isPersistenceEnabled` / `PersistentCacheSettings` / `MemoryCacheSettings` anywhere in `app/src`; `Fb.kt` configures only the database id. The Firestore Android SDK default is a **persistent disk cache** in app-private storage: documents delivered to live listeners (own + counterparts' live docs, with coordinates) and results of one-shot reads (history records, places docs, authz docs) land on disk, LRU-evicted over time.
- **Impact:** at-rest copies of location data exist on every device beyond the app's own memory-only design — including, on a **recipient's** device, copies of the owner's history/live docs that persist after revocation (until eviction/uninstall; the app itself can never re-read them post-revocation — rules deny server reads and the app never queries cache-source). Exposure requires a rooted/forensically-imaged device or adb backup on a debuggable build — `allowBackup=false` closes the cloud-backup and device-transfer paths. Realistic severity: medium, privacy-weighted.
- **Mitigations already in place (verified):** app-private sandbox storage; `allowBackup=false`; no analytics/crash SDK that could exfiltrate it; uninstall clears it; the app never presents cached location as fresh (design §9 offline = honest error).
- **Recommended action:** an explicit **decision record** choosing one of: **(a)** switch the Firestore instance to `MemoryCacheSettings()` (one settings line in `Fb.kt`) — the app's location reads are all live listeners or server-source one-shots whose state already lives in ViewModels, and the app's offline philosophy is honest-error, so nothing designed depends on the disk cache; note it changes offline behavior app-wide (pairing/sessions reads during brief network loss) and therefore needs its own mini-review, not a silent line; or **(b)** accept-and-document (this review + privacy string then disclose the SDK cache like any app-private file). Either is defensible; the indefensible state is the current one — a design claim ("disabled") that was never true and never checked.

### S-3 (LOW, forensics/design divergence) — `locationAuditEvents` never implemented

- **Evidence:** design §2.4 specifies the metadata-only collection (kind/actor/owner/recipient/deviceId/at/appVersionCode, hasOnly-enforced, owner-write/owner-read); §5 revocation step 8 and the §10 checklist item 2 both depend on it; the audit's gap table listed it as "Absent (Phase 1)" with the design as the fix path. Grep this phase: zero implementation — no rules block (the catch-all denies it), no client write, no probe series. The E-series probe cases from design §4.5 were deferred to Phase 11 and superseded by the place-alert P-series (worklog §84).
- **Impact:** owner-facing consent forensics only — there is no server-visible record of who granted/revoked what when, beyond the authz docs' own `updatedAt` and the local (sign-out-wiped) `NotificationsLog`. **Access control is fully unaffected** (rules evaluate per request regardless).
- **Recommended action:** decide at the Phase 14 gate: **(a)** implement as designed (client-attested best-effort: small rules block + repo writes on grant/revoke/delete + E-series probe cases — a half-day wave), or **(b)** amend the design to drop v1 audit events and record that the authz doc's updatedAt + local log are the v1 forensics surface. Do not leave the design promising a collection that does not exist.

### S-4 (LOW, at-rest residue) — the place-alert local mirror survives sign-out

- **Evidence:** `PlaceAlertStore` persists `KEY_UID` + `places_json` (name, lat, lng, radius, toggles, cooldown — PlaceAlertRepo.kt:236–287) in SharedPreferences. The sign-out teardown (verified in §3 item 6) clears `NotificationsLog` but **not** the places mirror, the history write-log, or the revoke queue. Cross-account safety holds by construction: the mirror is uid-scoped (`holds()` — a different account signing in on the same phone reads an empty registry and never monitors the predecessor's fences, D13e; the queue carries uids only; the write-log carries docIds + timestamps only).
- **Impact:** after sign-out, the **owner's own** saved-place coordinates remain readable (rooted/forensic device) until a different account signs in, the places are edited, or the app is uninstalled. The same data lives on in Firestore by design (D3 — reinstall survival), so the marginal exposure is the signed-out interval on a compromised device.
- **Recommended action:** one-line clear of `PlaceAlertStore` (and optionally the write-log + queue) in the sign-out path — behavior after re-sign-in is unchanged (the screen reloads from Firestore on open; the mirror exists for the FGS, which sign-out stops anyway) — **or** accept-and-document. Either closes the finding.

### Observations (zero severity — recorded so they exist in exactly one place)

- **O-1 — D12a's live/history asymmetry has an availability cost:** the engine passes `fix.speed`/`fix.bearing`/`fix.altitude` to history writes unfiltered (LocationShareService.kt:592–594; LiveLocationRepo.kt:202–223) — a physically impossible sensor value makes the rules reject the **whole record** (honest gap), while the live doc strips the field and keeps flowing. Deliberate (worklog §83 D12a: "history tolerates a failed append as an honest gap"); an outlier altitude (>10,000 m — e.g. a flight) sacrifices the point. Recorded as designed, not as a defect.
- **O-2 — recordId's embedded deviceId is not cross-checked against the record's `deviceId` field** (rules :806–811 check the pattern and the field separately). Zero security impact: writes are owner-only, and recipient queries bind on the **data** field (`whereEqualTo('deviceId', …)`), so a lying docId prefix only makes the record invisible to that device's queries. Noted for completeness; not worth a rules wave.
- **O-3 — revoke flush timing (the known §79 edge, restated precisely):** an offline stop (incl. sign-out) enqueues revokes; they land on the next signed-in user action (Tracking open — TrackingTab.kt:339 — or the next wizard confirm). Until then the recipient's **history** reads continue within retention (the live doc is already deleted; ONCE/TEMPORARY grants additionally expire server-side by `expiresAt`; **UNTIL_STOP grants have no server clock bound** — the residual exposure class). Rules-first D1 bound, disclosed in worklog §79 and carried since.
- **O-4 — rules cannot rate-limit** (audit §4.6): a compromised owner account can flood its own history to the shared Spark quota. Inherent to rules-first; unchanged since the audit; quota monitoring (§79 incident class) is the mitigation of record.
- **O-5 — ONCE semantics (Phase 8 legacy, still awaiting the Owner's ruling):** current behavior deletes the live doc immediately after the single accepted write — the data-minimizing reading. Security-neutral either way; the one-line alternative ("last-known stays visible until stale") slightly increases residual exposure by design.

---

## 6. Rules deep-dive (per-collection invariants, as released)

Baseline fact for this whole section: **released == repo, byte-exact** (§2.1).

**`liveLocations` (:640–700)** — writer: the owner only, with ownership enforced by the docId binding (`uid_[0-9a-f]{24}`) **and** field identity (`uid == request.auth.uid`) — the update path inherits ownership from the docId pattern, which cannot name another user's doc. Readers: own doc · legacy pairing branch (verbatim M5 semantics, guest windows `request.time`-checked) · Phase 8 authorization branch (`liveEnabled` grant + family-or-paired + `sharingEnabled == true`). Fields: `hasOnly` 9 keys with the optional `speedMetersPerSecond` (0–90 m/s inclusive, verbatim history mirror); `updatedAt` ±5 min window; `appVersionCode` 1–10⁶. Delete: owner only. **Notable:** the dual read path means a relationship alone grants nothing new and a grant alone grants nothing without the flag — both branches were probe-locked (L-series + 16-case suite).

**`locationAuthorizations` (:703–780)** — the single source of feature grants. Writes: owner only, on every path; docId = `{recipientUid}_{ownerUid}_{deviceId}` with both uids re-verified against fields; self-grants impossible (`recipientUserId != request.auth.uid`); `recipientKind` ∈ {family, guest}; scope/`expiresAt` pairing enforced as an equivalence (ONCE/TEMPORARY ⇔ expiresAt present, and it must be in the future); `retentionPolicy ≠ NONE` only with `WITH_HISTORY` + `historyEnabled`. Updates: diff-fenced to 7 keys, `ownerUserId`/`recipientUserId`/`deviceId`/`recipientKind` immutable, **terminal revoke** (a revoked doc accepts no further updates — the un-revoke hole is structurally closed; the only re-grant is delete + recreate, which only the owner can do). Reads: owner or the recipient themselves (own-doc get / own-filter list — probe-locked with rows assertions). Delete: owner.

**`users/{ownerUid}/locationHistory/{recordId}` (:783–855)** — append-only (`update: if false`); writer: the owner only (path + field + recordId pattern `[0-9a-f]{24}_[0-9]+`); every field range-checked (lat/lng, accuracy 0–100,000, `recordedAt`/`receivedAt` ±10 min, optional speed/bearing/altitude windows, provider enum, `retentionExpiresAt > request.time` on write); `hasOnly` 13 keys. Reads: owner or `historyEnabled` grant + family-or-paired — **and the retention floor applies to everyone, the owner included**: an expired record is invisible even to its own owner before physical deletion (a privacy-positive property this review explicitly records as a strength: retention promises are enforced, not just intended). Lists additionally carry the 5-minute clock-skew buffer so a slightly-behind client clock cannot page in expired records (H11/H13-locked).

**`placeAlerts` (:858–936)** — owner-only in **every** direction (get/list/create/update/delete); docId `{uid}_[0-9a-f]{16}`; bounds radius 50–1000 / cooldown 5–120 / name 1–64 **characters** (the live-verified `string.size()` semantics, locked by probe P5's 56-char/154-byte Bengali case — the design's "≤64 chars" holds verbatim for the app's primary locale); ±10-min timestamp windows; updates diff-fenced (8 keys — `ownerUserId`/`createdAt` immutable, `appVersionCode` frozen by the fence). No other identity in the system can even *read* this collection (P3 stranger-list 200+0-rows, N-series).

**Helpers (:58–100)** — `pairingActiveWith` (bidirectional, ACTIVE status, guest window vs `request.time`); `locationAuthzActive` (exists + owner/recipient field re-verification + not-revoked + not-expired); `locationAuthzFeature` (flag with `get(field, false)` default-deny); `locationAuthzFamilyOrPaired` (family: the grant is the consent; guest: relationship must also be alive). Rules-get budget per evaluation: 1 authz doc (+1 relationship doc worst case) — inside the 10-access limit by construction.

**Catch-all (:939–941)** — `allow read, write: if false`. Present, unchanged, last.

---

## 7. What this review does NOT cover (honest limits)

- **No dynamic penetration testing beyond the recorded probe suites.** The live-execution evidence in this review is the 76/76 probe gates (run 2026-11-01 against the released rulesets) plus this phase's ruleset readback. No new adversarial traffic was generated (§75: review verification only).
- **Firebase Auth account security, Cloudflare Worker, TURN, and secrets handling** are outside the location scope (and the secrets live outside the repo by design). The stolen-account adversary in §4 is analyzed only as far as the location rules bound it.
- **On-device behaviors** (notification channel state, OEM battery managers, LangPrefBus locale on the tray) remain the Owner's physical device tests — the Phase 11 device test is still pending on the Owner's checklist; this review's findings do not depend on those tests.
- **Client-side gates (accuracy ≤ 100 m, interval, 25 m/50 m throttles) are quota/UX controls, not security controls** — a forged client bypasses them all; the rules are the only enforcement layer, and that is the correct posture (audit §6.3, unchanged).

---

## 8. Verdict and recommended actions

**Verdict:**

- **Enforcement layer: PASS.** Rules-bound everything, byte-exact released-vs-repo (live-verified), 76/76 probe gates, append-only history, terminal revoke, retention floor for everyone, owner-only places, catch-all deny. No bypass found; none looked structurally reachable.
- **Disclosure layer: FAIL on S-1** (privacy string materially false for shipped features) — the one item this review flags as must-fix **before** the app is described to anyone as production-ready. Phase 14's mandated privacy sync is the backstop; an earlier string wave is recommended.
- **At-rest posture: acceptable but undocumented** (S-2/S-4) — one decision record + optionally one line of code each closes both.
- **Forensics surface: diverged from design** (S-3) — decide implement-or-drop at Phase 14.

**Recommended Owner decisions, in priority order:**

1. **S-1 string wave timing** — authorize a `privacy_body`-only wave (×15 locales, BN complete; optionally also S-4's sign-out clear) as v0.43.1, or fold into Phase 14's release-checklist wave.
2. **S-2 decision record** — memory-cache Firestore settings (with its app-wide mini-review) or accept-and-document in the privacy string.
3. **S-3 ruling** — implement the designed audit events (small rules + probe wave) or amend the design to drop them for v1.
4. **S-4 ruling** — one-line sign-out clear, or accept-and-document.
5. **Standing gate** — adopt `scripts/verify-released-rules.mjs` (added this phase) as a release-checklist step: released rules must equal repo rules, byte-exact, on every release.

**Phase 12 gate = Owner review of this document.** Phase 13 (performance report) and Phase 14 (release checklist) remain.

---

*Review complete. No app code was changed in this phase. One read-only script was added (`scripts/verify-released-rules.mjs`) so the released-vs-repo evidence is reproducible at every future gate. The findings register routes all fixes to Owner-authorized waves.*
