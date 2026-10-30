# FAMILY_GUEST_AUTHORIZATION — Pairing vs Feature Permissions (Master-Plan PART 10 / M6)

> Status: **PLANNING — awaiting Owner approval** (documents existing reality + the M5/M6
> deltas). Evidence: `firestore.rules` (596 lines, Agent 2-b audit), `PairingRepo.kt`,
> `SessionRepo.kt`, guest UI (`MainActivity.kt`).

## 1. Core principle (already implemented, kept)

**Pairing creates a relationship — never a blanket authorization.** Every sensitive feature
(screen capture, remote interaction, and future location sharing) has its own consent and
operational status; Android may additionally require renewed system consent at any time.
All enforcement is server-side (rules); the UI never gates by hiding buttons alone.

## 2. Relationship state diagram (as-is, verified)

```
                 createInvitation (PENDING, raw token+sha256, TTL 10 m rules / 5 m UI)
                          │ recipient scans QR / types 12-digit code
                          ▼
                    ScannedRoleSheet (role preview: Family | Guest)
                          │ redeemCode txn — claimToken proof, claimedBy, ≤2 min freshness
                          ▼
                 trustedDeviceRelationships/{helperUid_recipientUid}
                   kind FROZEN at creation (guest ≠ family, rules-enforced)
                          │
        ┌─────────────────┼──────────────────────────────┐
        ▼ FAMILY           ▼ GUEST                        │
   ACTIVE (until       ACTIVE inside server-bounded    status changes only via
   owner REVOKES)      window (guestExpiresAtMillis,   strict per-branch affectedKeys;
        │              ≤3 h; EXPIRED past window)      guest docs deletable by
        ▼              auto-EXPIRED                    participants (cleanup)
     REVOKED ───────────────► (both: no reads/writes beyond self; rules deny counterpart)
```

Anti-abuse already in rules: single-use token (status PENDING→PAIRED transition), sha256
tokenHash + raw-token claim proof, expiry bounds, claim-freshness 2 min, kind fence
(family invite cannot be redeemed as guest and vice-versa), catch-all deny.

## 3. Feature-authorization matrix (who can grant what)

| Capability | Grantor | Grant mechanism | Persistence | Revocation | Guest eligible |
|---|---|---|---|---|---|
| Family membership (pairing) | Both devices | QR/code redeem (transaction) | Until revoked | Owner revoke (status REVOKED) | n/a (separate kind) |
| Screen-share session | **Recipient owner** | consent modal + Android MediaProjection dialog, every session | Per-session | End-session button / timeout / deny | ✅ inside live window only |
| Remote interaction (assist gestures) | **Recipient owner** | assist-type dialog per session + AccessibilityService setup (system screen) | Service grant persists (user-revocable in system settings); per-session assist authorization | Stop controls + revoke + system settings | ✅ inside live window only |
| Push ask notifications | Recipient | POST_NOTIFICATIONS runtime grant | Until revoked (OS) | System settings | ✅ |
| Presence visibility | Automatic by pairing | rules `myPairing()` (ACTIVE + window) | While ACTIVE | Revocation | ✅ while window live |
| **Location sharing (M5, planned)** | **Device owner** | explicit toggle + purpose sheet + FINE/COARSE while-in-use | Until owner stops / revoked | Stop toggle (Tracking/Settings), revoke, doc delete | ✅ inside live window only, same rules fence |

Repeated-consent discipline: still-valid Android grants are reused (no re-prompt loops);
MediaProjection on Android 14 re-prompts per capture session (OS behavior — documented, never
bypassed); accessibility grant persists until the user disables it.

## 4. Session FSM (supportSessions, as-is)

```
REQUESTED ──(recipient grants ≤5 m deadline)──► ACTIVE ──(end/20 m cap/guest window)──► ENDED
    │──► DENIED (recipient)      │──► CANCELLED (helper)      │──► EXPIRED (lazy/worker)
Zombie guards: isStaleOpen (+4 s grace), ROOT sweep, worker zombie→ENDED("timeout"),
deadline-less REQUESTED → EXPIRED after fresh window (Phase 20 F2).
Signal subcollection: writable only while ACTIVE and sessionEndsAt > now (server-enforced).
```

## 5. Guest-session lifecycle guarantees

- Temporary credentials = the pairing record itself is time-bounded (server `saneGuestWindow`
  ≤3 h; client default 2 h); sessions inside the window are additionally capped
  (`SessionRepo.kt:246-280`).
- Expiry enforced server-side (rules + sweeps + worker) — not just UI.
- Guest can never become Family (kind frozen; separate invitations).
- Immediate revocation: delete guest relationship → rules deny on next access; live session
  end flow terminates in-progress support.
- No persistence after end: signal queues wiped by participants; guest docs deletable;
  presence simply ages out.

## 6. Planned deltas (require approval; minimal by design)

| # | Delta | Where |
|---|---|---|
| G1 | Location = new row in §3 matrix + consent sheet copy + rules `liveLocations` block | M5 |
| G2 | Notifications for relationship/session/sharing events (real events only) | M8 |
| G3 | Settings "Security" subsection surfacing authorization states (read-only truth) | M4 |
| G4 | Fix manifest/assist-config doc drift (`canRetrieveWindowContent`) in privacy copy | M4 |

## 7. Acceptance criteria

1. Probe battery (existing `rules-*-probe.mjs` + new liveLocations probe) passes all deny/allow
   cases on the live project after every rules change.
2. Revoking a device cuts presence/location/sessions within one rules evaluation (no cache
   grace beyond Firestore's own).
3. Guest window expiry is observable without app interaction (server-side sweep evidence).
4. No UI path can trigger a sensitive operation that rules would deny (negative probes).
5. All consent surfaces are localized ×15 and reachable from the owner's own screen.
