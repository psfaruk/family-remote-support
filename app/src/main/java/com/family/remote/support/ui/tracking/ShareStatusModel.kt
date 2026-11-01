// ═══════════════════════════════════════════════════════════════════════
// Phase 4 (v0.38.0, frontend mock wave) — the location-sharing STATUS
// MODEL: the small enum vocabulary + label/chip/icon/derivation helpers
// every tracking surface (tab member cards, own card, detail/history/
// places screens) renders through, so ONE mapping defines what each
// sharing state looks like (master PART 6: one coherent system, no second
// variant of the same widget).
//
// Honesty rules baked in:
//   • deriveShareStatus never invents a state — it reads only the single
//     liveLocations doc the reader is already authorized to watch
//     (Phase 4 has no authorization entity yet, so the derivation is
//     deliberately document-shaped: null → UNAVAILABLE, owner flipped
//     sharingEnabled off → STOPPED, then the reader's-clock freshness
//     buckets LIVE/STALE/OFFLINE from TrackingTab's canonical math);
//   • uploadStateLine returns null while uploads are OK — the healthy
//     state stays silent instead of showing a fake "sent" line;
//   • icons are core-set only (androidx.compose.material.icons — no
//     extended icons, the project forbids the APK bloat).
// ═══════════════════════════════════════════════════════════════════════
package com.family.remote.support.ui.tracking

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.family.remote.support.LiveLocationView
import com.family.remote.support.R
import java.util.Calendar

/**
 * Phase 4 — the sharing state of ONE device as the family sees it. The
 * real side can currently derive LIVE/PAUSED-by-freshness/OFFLINE/STOPPED/
 * UNAVAILABLE from the single liveLocations doc; SHARING_PAUSED and
 * PERMISSION_REQUIRED become real derivations when the Phase 8
 * authorization entity exists — until then they are demo-gallery states
 * only, never fabricated onto a real device.
 */
enum class LocationShareStatus {
    SHARING_LIVE,
    SHARING_PAUSED,
    PERMISSION_REQUIRED,
    DEVICE_OFFLINE,
    LAST_LOCATION,
    UNAVAILABLE,
    STOPPED
}

/** Phase 4 — the last upload attempt's outcome (own-device line, silent when OK). */
enum class UploadState { OK, PENDING, FAILED, UNAVAILABLE }

/** Phase 4 — the wizard's sharing-scope vocabulary (consent record, Phase 8 enforces). */
enum class ScopeKind { ONCE, TEMPORARY, UNTIL_STOP, WITH_HISTORY }

/** Phase 4 — journey-history retention vocabulary (only meaningful with WITH_HISTORY). */
enum class RetentionKind { NONE, H24, D7, D30 }

/** Phase 4 — coarse movement classification (always an estimate, never a claim). */
enum class MovementKind { WALK, CYCLE, VEHICLE, STILL }

/** Phase 4 — how honest a movement estimate is (few points → LIMITED). */
enum class MovementConfidence { LIMITED, MODERATE }

/** Localized one-word-ish label for a sharing status (StatusBadge text). */
@Composable
internal fun locationShareStatusLabel(s: LocationShareStatus): String = when (s) {
    LocationShareStatus.SHARING_LIVE -> stringResource(R.string.loc_status_live)
    LocationShareStatus.SHARING_PAUSED -> stringResource(R.string.loc_status_paused)
    LocationShareStatus.PERMISSION_REQUIRED -> stringResource(R.string.loc_status_perm)
    LocationShareStatus.DEVICE_OFFLINE -> stringResource(R.string.loc_status_offline)
    LocationShareStatus.LAST_LOCATION -> stringResource(R.string.loc_status_last)
    LocationShareStatus.UNAVAILABLE -> stringResource(R.string.loc_status_unavailable)
    LocationShareStatus.STOPPED -> stringResource(R.string.loc_status_stopped)
}

/**
 * StatusBadge kind for a sharing status — the four-kind semantic language
 * (StatusBadge renders a CheckCircle for success, a Warning icon for
 * error, nothing for neutral):
 *   success = sharing live;            warning = degraded (paused, stale
 *   last-location);                    error   = sharing impossible right
 *   now (permission missing, stopped); neutral = absent data states
 *   (device offline, no doc at all). Color is never the ONLY signal —
 *   the label always travels with it.
 */
internal fun locationShareStatusChipKind(s: LocationShareStatus): String = when (s) {
    LocationShareStatus.SHARING_LIVE -> "success"
    LocationShareStatus.SHARING_PAUSED -> "warning"
    LocationShareStatus.PERMISSION_REQUIRED -> "error"
    LocationShareStatus.DEVICE_OFFLINE -> "neutral"
    LocationShareStatus.LAST_LOCATION -> "warning"
    LocationShareStatus.UNAVAILABLE -> "neutral"
    LocationShareStatus.STOPPED -> "error"
}

/**
 * Core-set icon per status (LIVE=LocationOn, PAUSED=Info,
 * PERMISSION_REQUIRED=Warning, DEVICE_OFFLINE=Clear, LAST_LOCATION=Place,
 * UNAVAILABLE=Lock, STOPPED=Close) — a distinct glyph per state so the
 * gallery and the freshness lines never rely on color alone.
 */
internal fun locationShareStatusIcon(s: LocationShareStatus): ImageVector = when (s) {
    LocationShareStatus.SHARING_LIVE -> Icons.Filled.LocationOn
    LocationShareStatus.SHARING_PAUSED -> Icons.Filled.Info
    LocationShareStatus.PERMISSION_REQUIRED -> Icons.Filled.Warning
    LocationShareStatus.DEVICE_OFFLINE -> Icons.Filled.Clear
    LocationShareStatus.LAST_LOCATION -> Icons.Filled.Place
    LocationShareStatus.UNAVAILABLE -> Icons.Filled.Lock
    LocationShareStatus.STOPPED -> Icons.Filled.Close
}

/**
 * Derive the real-side sharing status from the device's liveLocations
 * doc and the READER's clock (never the writer's). Phase 4 has no
 * authorization entity yet, so the derivation is document-shaped only:
 * null → UNAVAILABLE; sharingEnabled off → STOPPED (the owner's own
 * record); freshness LIVE → SHARING_LIVE; STALE → LAST_LOCATION;
 * OFFLINE → DEVICE_OFFLINE.
 */
internal fun deriveShareStatus(location: LiveLocationView?, nowMillis: Long): LocationShareStatus {
    if (location == null) return LocationShareStatus.UNAVAILABLE
    if (!location.sharingEnabled) return LocationShareStatus.STOPPED
    return when (locationFreshness(location, nowMillis)) {
        LocationFreshness.LIVE -> LocationShareStatus.SHARING_LIVE
        LocationFreshness.STALE -> LocationShareStatus.LAST_LOCATION
        LocationFreshness.OFFLINE -> LocationShareStatus.DEVICE_OFFLINE
    }
}

/**
 * The own-device upload line: null while OK (the healthy state stays
 * silent — no fake "sent ✓"), the honest up_* text for
 * PENDING/FAILED/UNAVAILABLE.
 */
@Composable
internal fun uploadStateLine(u: UploadState): String? = when (u) {
    UploadState.OK -> null
    UploadState.PENDING -> stringResource(R.string.up_pending)
    UploadState.FAILED -> stringResource(R.string.up_failed)
    UploadState.UNAVAILABLE -> stringResource(R.string.up_unavailable)
}

/** Localized retention word (null and NONE both read as "no history"). */
@Composable
internal fun retentionLabel(r: RetentionKind?): String = when (r) {
    null, RetentionKind.NONE -> stringResource(R.string.ret_none)
    RetentionKind.H24 -> stringResource(R.string.ret_24h)
    RetentionKind.D7 -> stringResource(R.string.ret_7d)
    RetentionKind.D30 -> stringResource(R.string.ret_30d)
}

/**
 * Phase 9 — the wire `retentionPolicy` string (locationAuthorizations /
 * received grants) → the UI vocabulary. ONE mapping so the detail screen
 * and the history screen can never disagree about what "D7" means;
 * anything unexpected reads as NONE (the honest floor).
 */
internal fun retentionKindFromPolicy(policy: String): RetentionKind = when (policy) {
    "H24" -> RetentionKind.H24
    "D7" -> RetentionKind.D7
    "D30" -> RetentionKind.D30
    else -> RetentionKind.NONE
}

/** Localized sharing-scope word. */
@Composable
internal fun scopeLabel(k: ScopeKind): String = when (k) {
    ScopeKind.ONCE -> stringResource(R.string.scope_once)
    ScopeKind.TEMPORARY -> stringResource(R.string.scope_temp)
    ScopeKind.UNTIL_STOP -> stringResource(R.string.scope_until_stop)
    ScopeKind.WITH_HISTORY -> stringResource(R.string.scope_with_history)
}

/**
 * The "until midnight" scope end: today 23:59:59.999 in the LOCAL time
 * zone. ONE implementation shared by the wizard's midnight chip and the
 * demo expiry fixture (same semantics, no drift). A [fromMillis] already
 * past today's last millisecond rolls to tomorrow's midnight.
 */
internal fun nextLocalMidnightMillis(fromMillis: Long): Long {
    val cal = Calendar.getInstance()
    cal.timeInMillis = fromMillis
    cal.set(Calendar.HOUR_OF_DAY, 23)
    cal.set(Calendar.MINUTE, 59)
    cal.set(Calendar.SECOND, 59)
    cal.set(Calendar.MILLISECOND, 999)
    if (cal.timeInMillis < fromMillis) {
        cal.add(Calendar.DAY_OF_YEAR, 1)
    }
    return cal.timeInMillis
}
