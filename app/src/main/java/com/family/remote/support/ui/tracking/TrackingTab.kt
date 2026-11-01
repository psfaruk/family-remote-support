// ═══════════════════════════════════════════════════════════════════════
// Phase 22/M5 (v0.33.0) — the TRACKING tab becomes real
// (docs/LIVE_LOCATION_ARCHITECTURE.md §3 flow / §5 permission plan / §6
// freshness, docs/NAVIGATION_MAP.md §2 "tracking" = map + authorized
// markers + device selector sheet + freshness + sharing-status banner +
// loading/permission-denied/error/empty states).
//
// Phase 4 (v0.38.0, frontend mock wave) — the tab grows the Phase 4
// surfaces: the member list (one MemberLocationCard per authorized
// counterpart, shared with the demo dashboard), the 6-step sharing-setup
// wizard behind Start (SharingSetupSheet.kt), the Places & alerts entry,
// map zoom controls + accuracy circles (solid LIVE, dashed STALE/OFFLINE),
// and the explicitly-entered DEMO dashboard (process-memory fixtures,
// zero Firestore traffic — the real listeners are torn down while it is
// active and never mixed with demo data).
//
// Layout of this file:
//   • shared freshness helpers (LocationFreshness, chip/text colors,
//     "last known" line, device labels) — internal, reused by
//     LocationDetailScreen.kt in this same package;
//   • OwnSharingCard — the own-device sharing banner (the 6-step
//     SharingSetupSheet → runtime FINE/COARSE permission → M5-A
//     start/stopLocationSharing); internal so the Location Detail screen
//     can show the same owner control in its defensive own-device branch
//     (its no-arg call still compiles: recipients/isDemo default);
//   • MemberLocationUi + MemberLocationCard — the ONE member position
//     card real pins and demo members both render through;
//   • TrackingTab — the tab itself (canonical states, the member list +
//     map inside the 30 s TickingBlock, the demo branch);
//   • DemoTrackingDashboard — the in-memory demo (banner, demo own card,
//     the all-states gallery, the three fictional members, demo map);
//   • CounterpartMapSection — the osmdroid MapView wrapped in AndroidView
//     (lifecycle-correct resume/pause/detach), one Marker per counterpart
//     device that has actually published a position, accuracy circles
//     under the markers, zoom controls, camera fitted to the pinned
//     devices, and the device-selector ModalBottomSheet.
//
// Honesty rules baked in: positions come ONLY from TrackingViewModel state
// (never a direct watchLiveLocation call here); an old coordinate is never
// labeled live (freshness is computed from updatedAt vs the reader's
// clock, and the "Last known — HH:mm ±N m" line is ALWAYS shown); no
// history/track polylines — a single marker per device (master PART 6);
// real cards carry NO history button in Phase 4 because no real
// authorization source exists yet (an honest absence, not a hidden one);
// v0.44.0: the map attribution (CopyrightOverlay) follows the ACTIVE tile
// source (CARTO / Esri — MapStyle.kt owns the per-style line), still added
// once, before any marker, and never removed mid-session.
// ═══════════════════════════════════════════════════════════════════════
package com.family.remote.support.ui.tracking

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.family.remote.support.LiveLocationView
import com.family.remote.support.LocationSharingPrefs
import com.family.remote.support.LocationUploadReporter
import com.family.remote.support.LocationUploadStatus
import com.family.remote.support.R
import com.family.remote.support.RelationshipView
import com.family.remote.support.describeFirestoreError
import com.family.remote.support.flushPendingLocationAuthz
import com.family.remote.support.cleanupExpiredOwnHistory
import com.family.remote.support.flushPendingHistoryDeletion
import com.family.remote.support.grantLocationAuthorizations
import com.family.remote.support.hasAnyLocationPermission
import com.family.remote.support.startLocationSharing
import com.family.remote.support.stopLocationSharing
import com.family.remote.support.ui.AppWarningColor
import com.family.remote.support.ui.components.AppListTile
import com.family.remote.support.ui.components.BigButton
import com.family.remote.support.ui.components.EmptyState
import com.family.remote.support.ui.components.ErrorPanel
import com.family.remote.support.ui.components.LoadingSkeleton
import com.family.remote.support.ui.components.PermissionDeniedPanel
import com.family.remote.support.ui.components.SectionCard
import com.family.remote.support.ui.components.StatusBadge
import com.family.remote.support.ui.components.TickingBlock
import com.family.remote.support.ui.components.WideOutlinedButton
import com.family.remote.support.ui.components.statusChipColors
import com.family.remote.support.ui.tracking.demo.DemoBanner
import com.family.remote.support.ui.tracking.demo.DemoTrackingDataSource
import com.family.remote.support.ui.tracking.demo.DemoTrackingMode
import com.google.firebase.auth.FirebaseUser
import android.view.MotionEvent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.CopyrightOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Overlay

// ── Freshness (LIVE_LOCATION_ARCHITECTURE §3/§6, reader's clock) ──────────

/**
 * Freshness bucket for a published position, computed from updatedAt vs
 * the reader's clock. v0.45.0 (audit P2): LIVE < 5 min, STALE < 15 min,
 * else OFFLINE — the writer now heartbeats a STATIONARY phone every
 * ~4 min (writeLocationHeartbeat), so a still phone stays LIVE and a
 * briefly-unreachable one stays STALE instead of falling OFFLINE after
 * 5 minutes of sitting still at home. An old coordinate is NEVER labeled
 * live.
 */
internal enum class LocationFreshness { LIVE, STALE, OFFLINE }

/** Bucket for a live-location doc; a missing/zero timestamp counts as OFFLINE. */
internal fun locationFreshness(location: LiveLocationView, nowMillis: Long): LocationFreshness {
    if (location.updatedAtMillis <= 0L) return LocationFreshness.OFFLINE
    val ageMillis = nowMillis - location.updatedAtMillis
    return when {
        ageMillis < 300_000L -> LocationFreshness.LIVE
        ageMillis < 900_000L -> LocationFreshness.STALE
        else -> LocationFreshness.OFFLINE
    }
}

/** Localized freshness word (Live / Stale / Offline). */
@Composable
internal fun freshnessLabel(freshness: LocationFreshness): String = when (freshness) {
    LocationFreshness.LIVE -> stringResource(R.string.loc_fresh_live)
    LocationFreshness.STALE -> stringResource(R.string.loc_fresh_stale)
    LocationFreshness.OFFLINE -> stringResource(R.string.loc_fresh_offline)
}

/** Theme-aware container/content pair for a freshness chip (PairTile color language). */
@Composable
internal fun freshnessChipColors(freshness: LocationFreshness): Pair<Color, Color> = when (freshness) {
    LocationFreshness.LIVE -> Pair(
        MaterialTheme.colorScheme.tertiaryContainer,
        MaterialTheme.colorScheme.onTertiaryContainer
    )
    LocationFreshness.STALE -> Pair(
        AppWarningColor().copy(alpha = 0.18f),
        AppWarningColor()
    )
    LocationFreshness.OFFLINE -> Pair(
        MaterialTheme.colorScheme.surfaceVariant,
        MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/** Theme-aware text color for a freshness line (PresenceBadge color language). */
@Composable
internal fun freshnessTextColors(freshness: LocationFreshness): Color = when (freshness) {
    LocationFreshness.LIVE -> MaterialTheme.colorScheme.tertiary
    LocationFreshness.STALE -> AppWarningColor()
    LocationFreshness.OFFLINE -> MaterialTheme.colorScheme.onSurfaceVariant
}

/** "HH:mm" for a timestamp — the honest last-known clock line. */
internal fun locationTimeString(updatedAtMillis: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(updatedAtMillis))

/**
 * The always-visible position line: "Last known — HH:mm · ±N m" when a
 * doc exists, "Never shared" when it does not. Never a fabricated time.
 */
@Composable
internal fun lastKnownLine(location: LiveLocationView?): String {
    return if (location == null) {
        stringResource(R.string.loc_never)
    } else {
        stringResource(R.string.loc_last_known, locationTimeString(location.updatedAtMillis)) +
            " · " + stringResource(R.string.loc_acc_m, location.accuracyMeters)
    }
}

/** Counterpart email with honest fallbacks (never a hardcoded "(no email)" literal). */
internal fun counterpartEmail(rel: RelationshipView): String =
    rel.recipientEmail
        .ifBlank { rel.recipientDeviceModel }
        .ifBlank { rel.recipientUid }

/** Marker/tile label for a counterpart device: model first, email as fallback. */
internal fun deviceLabel(rel: RelationshipView): String =
    rel.recipientDeviceModel.ifBlank { counterpartEmail(rel) }

// ── Own-device sharing banner (§5 permission plan: consent → permission → FGS) ──

// v0.37.0 (dedupe wave) — hasAnyLocationPermission ("fine || coarse") now
// lives in LiveLocationRepo.kt as ONE app-wide check; this file's private
// copy was one of the drifting duplicates.

/**
 * Hand-off to this app's system settings page (PermissionDeniedPanel's
 * action). Phase 4: internal — SharingSetupSheet's denied panel uses the
 * same hand-off (one implementation, no second copy).
 */
internal fun openAppSettings(context: Context) {
    try {
        context.startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", context.packageName, null)
            )
        )
    } catch (_: Exception) {
        try {
            context.startActivity(Intent(Settings.ACTION_APPLICATION_SETTINGS))
        } catch (_: Exception) {
            // no settings activity on this device — nothing else we can do
        }
    }
}

/**
 * The own-device sharing card: ON/OFF banner (persisted flag, refreshed on
 * resume so a stop from the notification or the Settings screen is
 * reflected honestly), the one-tap Stop, and — Phase 4 — the 6-step
 * SharingSetupSheet that must precede every Start (the M5 one-tap consent
 * sheet grew into explain → recipients → scope → retention → permission →
 * confirm). On confirm the runtime FINE+COARSE permission step runs
 * inside the sheet; granted → startLocationSharing, denied → the sheet's
 * canonical PermissionDeniedPanel with the system-settings hand-off.
 *
 * @param recipients the wizard's recipient candidates (the Tracking tab
 *                   passes its ACTIVE relationships; the demo dashboard
 *                   passes its fictional members).
 * @param isDemo     demo mode: the card renders from DemoTrackingDataSource
 *                   and never touches the prefs or the sharing service.
 */
@Composable
internal fun OwnSharingCard(
    recipients: List<RecipientOption> = emptyList(),
    isDemo: Boolean = false
) {
    if (isDemo) {
        DemoOwnSharingCard(recipients = recipients)
        return
    }
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var sharingOn by remember {
        mutableStateOf(LocationSharingPrefs.isSharingEnabled(context))
    }
    var permissionDenied by remember { mutableStateOf(false) }
    var showSetupSheet by remember { mutableStateOf(false) }
    // Phase 9 — the owner-side "Delete my journey history" dialog (UX
    // spec §9/§10: the affordance lives in the wizard's confirm step;
    // Settings carries its own entry).
    var showDeleteDialog by remember { mutableStateOf(false) }

    // Phase 7 — the engine's live upload verdict (UX spec §8: silent when
    // OK). The reporter is engine-side and Compose-free; this bridge maps
    // it 1:1 onto the UI vocabulary and recomposes on every report.
    var engineUpload by remember {
        mutableStateOf(LocationUploadReporter.current())
    }
    DisposableEffect(Unit) {
        val listener: (LocationUploadStatus) -> Unit = { engineUpload = it }
        LocationUploadReporter.listen(listener)
        onDispose { LocationUploadReporter.unlisten(listener) }
    }
    val upload = when (engineUpload) {
        LocationUploadStatus.OK -> UploadState.OK
        LocationUploadStatus.PENDING -> UploadState.PENDING
        LocationUploadStatus.FAILED -> UploadState.FAILED
        LocationUploadStatus.UNAVAILABLE -> UploadState.UNAVAILABLE
    }

    // Phase 7 — the TEMPORARY scope's expiry line ("shown everywhere the
    // share status appears" — UX spec §5), re-read with the same resume
    // cadence as the toggle itself.
    var scopeKind by remember { mutableStateOf(LocationSharingPrefs.scopeKind(context)) }
    var scopeExpiry by remember { mutableStateOf(LocationSharingPrefs.scopeExpiresAt(context)) }

    // Refresh on resume: the pref can change outside this composition
    // (stop from the sharing notification, the Settings section, a scope
    // auto-stop, or a fresh install restore). While sharing is WANTED but
    // the permission is missing (revoked in system settings), the denied
    // panel shows — the app never pretends a permission was granted. The
    // resume is also the §75-safe moment to retry any queued authorization
    // writes (grant/revoke) that failed offline — a user action, never a
    // timer — and, Phase 9, the D6 opportunistic retention cleanup plus
    // any queued history deletion (design §6.2/§6.3: bounded batches,
    // repeated per open until exhausted, zero timers).
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                sharingOn = LocationSharingPrefs.isSharingEnabled(context)
                permissionDenied = sharingOn && !hasAnyLocationPermission(context)
                scopeKind = LocationSharingPrefs.scopeKind(context)
                scopeExpiry = LocationSharingPrefs.scopeExpiresAt(context)
                flushPendingLocationAuthz(context)
                cleanupExpiredOwnHistory(context)
                flushPendingHistoryDeletion(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (permissionDenied) {
        PermissionDeniedPanel(
            title = stringResource(R.string.loc_denied_title),
            explanation = stringResource(R.string.loc_denied_body),
            onOpenSettings = { openAppSettings(context) }
        )
    }

    SectionCard(
        title = stringResource(
            if (sharingOn) R.string.loc_share_on else R.string.loc_share_off
        ),
        icon = Icons.Filled.LocationOn,
        infoText = stringResource(R.string.set_loc_now)
    ) {
        Text(
            text = stringResource(
                if (sharingOn) R.string.loc_banner_on else R.string.loc_banner_off
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        // Phase 7 — the live upload line (silent when OK, exactly the demo
        // card's contract): PENDING warns in-app-warning color, FAILED in
        // error color, UNAVAILABLE stays quiet gray.
        val uploadLine = uploadStateLine(upload)
        if (sharingOn && uploadLine != null) {
            Text(
                text = uploadLine,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = when (upload) {
                    UploadState.PENDING -> AppWarningColor()
                    UploadState.FAILED -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
        // Phase 7 — the TEMPORARY expiry line on the own card.
        if (sharingOn && scopeKind == LocationSharingPrefs.SCOPE_TEMPORARY &&
            scopeExpiry > System.currentTimeMillis()
        ) {
            Text(
                text = stringResource(
                    R.string.setup_confirm_expiry,
                    SimpleDateFormat("d MMM, HH:mm", Locale.getDefault()).format(Date(scopeExpiry))
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (sharingOn) {
            // Stop is deliberately ONE tap (the setup sheet is only for
            // starting) — the owner can always end sharing immediately.
            BigButton(
                onClick = {
                    // v0.37.0 (dedupe wave) — stopLocationSharing flips the
                    // pref itself before stopping the service; the extra
                    // setSharingEnabled(false) write here was redundant.
                    // v0.39.0 — the same call now revokes the Phase 8
                    // authorization docs (best-effort, queued offline).
                    stopLocationSharing(context)
                    sharingOn = false
                }
            ) {
                Text(stringResource(R.string.loc_share_stop))
            }
        } else {
            BigButton(onClick = { showSetupSheet = true }) {
                Text(stringResource(R.string.loc_share_start))
            }
        }
    }

    if (showSetupSheet) {
        // Phase 4 — the 6-step setup wizard (explain → recipients → scope
        // → retention → permission → confirm). Cancel at any step changes
        // nothing; the confirm read-back is what starts sharing.
        SharingSetupSheet(
            recipients = recipients,
            isDemo = false,
            onDismiss = { showSetupSheet = false },
            // Phase 9 — "Delete existing journey history" rides the confirm
            // step (spec §9's owner-side delete home #1); the dialog itself
            // is the ONE shared implementation.
            onDeleteHistory = { showDeleteDialog = true },
            onConfirmed = { selected, scope, tempMillis, retention ->
                // Phase 4: the LOCAL consent record (scope/expiry/
                // retention) — the engine's auto-stop reads exactly these.
                LocationSharingPrefs.setScopeKind(context, scope.name)
                LocationSharingPrefs.setScopeExpiresAt(context, tempMillis ?: 0L)
                LocationSharingPrefs.setRetentionKind(context, retention.name)
                // Phase 8 — the AUTHORITATIVE authorization docs (one per
                // recipient, owner-only writes, rules-fenced server-side).
                // Best-effort here: offline failures queue and retry on
                // the next user action (Tracking resume / next confirm).
                grantLocationAuthorizations(
                    context = context,
                    recipients = selected.map { it.uid to it.kind },
                    scopeKind = scope.name,
                    tempMillis = tempMillis ?: 0L,
                    retentionKind = retention.name
                )
                // v0.37.0 (dedupe wave) — startLocationSharing persists the
                // consent pref itself (LiveLocationRepo records it FIRST),
                // then brings the location foreground service up.
                startLocationSharing(context)
                sharingOn = true
                showSetupSheet = false
            }
        )
    }

    if (showDeleteDialog) {
        // Phase 9 — the shared owner-side delete flow (today / everything,
        // verified-empty, offline-queued honestly).
        DeleteHistoryDialog(onDismiss = { showDeleteDialog = false })
    }
}

/**
 * Phase 4 — the demo own-card: the card slot OwnSharingCard fills on the
 * real dashboard, rendered from DemoTrackingDataSource instead of the
 * prefs and the sharing service. The status title, the cyclable upload
 * line and the Resume/Stop flips are process-local; Start opens the
 * wizard in demo mode (its permission step shows the explanatory texts
 * only — the real launcher is never launched from demo).
 */
@Composable
private fun DemoOwnSharingCard(recipients: List<RecipientOption>) {
    var showSetupSheet by remember { mutableStateOf(false) }

    SectionCard(
        title = locationShareStatusLabel(DemoTrackingDataSource.ownStatus),
        icon = Icons.Filled.LocationOn,
        infoText = stringResource(R.string.set_loc_now)
    ) {
        // Upload-state line: OK is silent (uploadStateLine's contract),
        // the other three states render their honest line. The hint below
        // is the tap target that cycles all four states — it is the only
        // visible affordance while OK, precisely because OK stays silent.
        val uploadLine = uploadStateLine(DemoTrackingDataSource.ownUpload)
        if (uploadLine != null) {
            Text(
                text = uploadLine,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = when (DemoTrackingDataSource.ownUpload) {
                    UploadState.PENDING -> AppWarningColor()
                    UploadState.FAILED -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
        Text(
            text = stringResource(R.string.demo_upload_cycle),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.clickable {
                DemoTrackingDataSource.ownUpload = when (DemoTrackingDataSource.ownUpload) {
                    UploadState.OK -> UploadState.PENDING
                    UploadState.PENDING -> UploadState.FAILED
                    UploadState.FAILED -> UploadState.UNAVAILABLE
                    UploadState.UNAVAILABLE -> UploadState.OK
                }
            }
        )
        if (DemoTrackingDataSource.ownStatus == LocationShareStatus.SHARING_LIVE) {
            BigButton(
                onClick = {
                    DemoTrackingDataSource.ownStatus = LocationShareStatus.SHARING_PAUSED
                }
            ) {
                Text(stringResource(R.string.loc_share_stop))
            }
        } else {
            BigButton(onClick = { showSetupSheet = true }) {
                Text(stringResource(R.string.loc_share_start))
            }
            // Quick Resume flip beside the wizard walkthrough — both flip
            // the demo-local status only (no service, no Firestore).
            TextButton(
                onClick = {
                    DemoTrackingDataSource.ownStatus = LocationShareStatus.SHARING_LIVE
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.loc_resume))
            }
        }
    }

    if (showSetupSheet) {
        SharingSetupSheet(
            recipients = recipients,
            isDemo = true,
            onDismiss = { showSetupSheet = false },
            onConfirmed = { _, _, _, _ ->
                // Demo confirm: flip the demo-local status only.
                DemoTrackingDataSource.ownStatus = LocationShareStatus.SHARING_LIVE
                showSetupSheet = false
            }
        )
    }
}

// ── Member position cards (shared by the real + demo dashboards) ─────────

/**
 * Phase 4 — the shared presentation input for one member position card
 * and the map section: real CounterpartPins and demo members both map
 * into this small shape, so the SAME composables render both worlds
 * without ever faking a RelationshipView for demo data. [status] comes
 * from deriveShareStatus on the real side and from the fixture on the
 * demo side; [freshness] is always computed from the reader's clock.
 */
internal data class MemberLocationUi(
    val relId: String,
    val title: String,
    val subtitle: String,
    val status: LocationShareStatus,
    val freshness: LocationFreshness?,
    val location: LiveLocationView?
)

/** Real pin → shared card/map presentation input (status derived, never fabricated). */
private fun CounterpartPin.toMemberUi(nowMillis: Long): MemberLocationUi = MemberLocationUi(
    relId = rel.id,
    title = deviceLabel(rel),
    subtitle = counterpartEmail(rel),
    status = deriveShareStatus(location, nowMillis),
    freshness = location?.let { locationFreshness(it, nowMillis) },
    location = location
)

/**
 * Phase 4 — ONE member position card: initials avatar, display name,
 * email, the StatusBadge for the (derived or fixture) status, and the
 * always-visible freshness + "Last known — HH:mm · ±N m" line with its
 * status icon (the text always carries the meaning — color never does it
 * alone). The Details button routes into tracking/{relId}; the History
 * button appears only where history is actually available (demo fixtures
 * in Phase 4 — real cards honestly have no authorization source yet, so
 * they show no history button at all).
 */
@Composable
internal fun MemberLocationCard(
    member: MemberLocationUi,
    onOpenDetails: () -> Unit,
    onOpenHistory: (() -> Unit)? = null
) {
    val statusKind = locationShareStatusChipKind(member.status)
    val chipContent = statusChipColors(statusKind).second
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            MaterialTheme.colorScheme.secondaryContainer,
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = member.title.take(1).uppercase(),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = member.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = member.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                StatusBadge(
                    label = locationShareStatusLabel(member.status),
                    kind = statusKind
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = locationShareStatusIcon(member.status),
                    contentDescription = null,
                    tint = chipContent,
                    modifier = Modifier.size(16.dp)
                )
                val freshnessWord = member.freshness?.let { freshnessLabel(it) }
                Text(
                    text = if (freshnessWord == null) {
                        lastKnownLine(member.location)
                    } else {
                        freshnessWord + " · " + lastKnownLine(member.location)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                if (onOpenHistory != null) {
                    TextButton(onClick = onOpenHistory) {
                        Text(stringResource(R.string.hist_title))
                    }
                }
                TextButton(onClick = onOpenDetails) {
                    Text(stringResource(R.string.loc_details))
                }
            }
        }
    }
}

// ── The tab ──────────────────────────────────────────────────────────────

/**
 * M5 (v0.33.0) — NAVIGATION_MAP §2 "tracking": the map tab. Locations are
 * read ONLY through TrackingViewModel state.
 *
 * Phase 4 (v0.38.0) — the member card list joins the map under the 30 s
 * freshness clock, the Places & alerts entry lands at the bottom, and the
 * explicitly-entered demo dashboard replaces the real one while active
 * (§75/demo-never-mixed: the real Firestore listeners are torn DOWN for
 * the whole demo session — see the observe effect below).
 *
 * @param user          the signed-in account (null → the honest signed-out
 *                      empty state, same convention PairingCard uses).
 * @param onOpenMember  navigates to tracking/{relId} for a counterpart.
 * @param onOpenPlaces  navigates to the tracking/places route (My places
 *                      & alerts).
 * @param onOpenHistory navigates to tracking/{relId}/history — Phase 4
 *                      wires it only from demo member cards with granted
 *                      history (real cards have no authorization source
 *                      yet, so they never show the button).
 */
@Composable
internal fun TrackingTab(
    user: FirebaseUser?,
    onOpenMember: (String) -> Unit,
    onOpenPlaces: () -> Unit = {},
    onOpenHistory: (String) -> Unit = {}
) {
    val context = LocalContext.current
    if (user == null) {
        SectionCard(title = stringResource(R.string.loc_title), icon = Icons.Filled.LocationOn) {
            EmptyState(
                icon = Icons.Filled.LocationOn,
                title = stringResource(R.string.phone_signin_first),
                hint = stringResource(R.string.phone_signin_hint)
            )
        }
        return
    }

    val vm: TrackingViewModel = viewModel()
    var retryKey by remember { mutableStateOf(0) }

    // §75/demo-never-mixed discipline: while the demo dashboard is active
    // the REAL watches must be DOWN — observe(null) tears the relationship
    // listener and every location listener out (zero Firestore traffic in
    // demo); leaving demo re-observes from scratch. The effect is keyed on
    // DemoTrackingMode.active and deliberately sits BEFORE the demo
    // early-return below: it has to stay in composition across the flip,
    // or toggling the demo would never re-run it.
    LaunchedEffect(user.uid, retryKey, DemoTrackingMode.active) {
        if (DemoTrackingMode.active) {
            vm.observe(null) { _ -> "" }
        } else {
            vm.observe(user) { e -> describeFirestoreError(context, e) }
        }
    }
    val state by vm.state.collectAsState()

    // ── Phase 4 demo branch: the in-memory dashboard INSTEAD of the real
    // one (the two data worlds are never mixed in one composition). ──────
    if (DemoTrackingMode.active) {
        DemoTrackingDashboard(
            onOpenMember = onOpenMember,
            onOpenHistory = onOpenHistory,
            onOpenPlaces = onOpenPlaces
        )
        return
    }

    OwnSharingCard(
        recipients = state.relationships.mapNotNull { recipientOptionFrom(it, user.uid) },
        isDemo = false
    )

    // Canonical states in priority order (NAVIGATION_MAP §5): first
    // snapshot not arrived → skeleton; routed error with no loaded data →
    // retryable error panel; after a snapshot the last data always stays
    // (the transient error is not re-shown, exactly like HomeTab). No
    // paired devices → the honest empty state (no CTA to pair: this tab
    // has no pairing callback — pairing lives on the Add Device tab; the
    // Phase 4 demo entry rides EmptyState's action slot).
    val routedError = state.error?.takeIf { !state.loaded }
    when {
        routedError != null -> ErrorPanel(message = routedError, onRetry = { retryKey++ })
        !state.loaded -> LoadingSkeleton(rows = 3)
        state.counterparts.isEmpty() -> EmptyState(
            icon = Icons.Filled.LocationOn,
            title = stringResource(R.string.loc_empty_title),
            hint = stringResource(R.string.loc_empty_body),
            // Phase 4 — the explicitly-entered demo (EmptyState renders
            // this as its full-width outlined action). Demo state is
            // process-memory only; entering it tears the real listeners
            // down (the observe effect above).
            actionLabel = stringResource(R.string.demo_enter),
            onAction = { DemoTrackingMode.active = true }
        )
        else -> {
            // v0.37.0 (dedupe wave) — the 30 s freshness clock (the
            // PresenceBadge cadence) now lives in TickingBlock instead of a
            // screen-level while(true) loop. Phase 4: the MEMBER CARDS
            // joined the map inside the block — everything freshness-
            // dependent recomposes on the tick, everything else does not.
            TickingBlock(intervalMillis = 30_000) { nowMillis ->
                val memberUis = state.counterparts.map { it.toMemberUi(nowMillis) }
                memberUis.forEach { ui ->
                    MemberLocationCard(
                        member = ui,
                        onOpenDetails = { onOpenMember(ui.relId) },
                        // Phase 9 — the history button appears ONLY when the
                        // sharer's authorization grants it (the ViewModel's
                        // diffed one-shot read; a missing/unread grant = no
                        // button — the honest absence, never a teaser).
                        onOpenHistory = if (state.historyGrants[ui.relId] == true) {
                            { onOpenHistory(ui.relId) }
                        } else {
                            null
                        }
                    )
                }
                CounterpartMapSection(
                    members = memberUis,
                    onOpenMember = onOpenMember
                )
            }
            // Phase 4 — the Places & alerts entry below the member list +
            // map (the screen is demo-fed until Phase 8 persistence lands).
            WideOutlinedButton(onClick = onOpenPlaces) {
                Text(stringResource(R.string.place_my_title))
            }
            // Phase 4 (documented micro-deviation) — a QUIET secondary demo
            // entry at the bottom of the loaded real dashboard: the Phase 5
            // review needs demo access on populated accounts too (the
            // empty-state entry alone is unreachable there). The demo never
            // mixes data — entering it tears the real listeners down via
            // the observe effect above.
            TextButton(
                onClick = { DemoTrackingMode.active = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.demo_enter))
            }
        }
    }
}

// ── Demo dashboard (Phase 4 — process-memory only) ───────────────────────

/**
 * Phase 4 — the demo dashboard: the persistent banner (with its ✕ exit
 * and the system-back exit), the demo own card, the all-states gallery
 * (the Phase 5 "every state rendered" guarantee), the three fictional
 * member cards (history buttons only where the fixture grants history),
 * the demo-fed map section and the places entry. Everything renders from
 * DemoTrackingDataSource — nothing here touches Firestore, the prefs or
 * the sharing service.
 */
@Composable
private fun DemoTrackingDashboard(
    onOpenMember: (String) -> Unit,
    onOpenHistory: (String) -> Unit,
    onOpenPlaces: () -> Unit
) {
    DemoBanner(onExit = { DemoTrackingMode.active = false })
    // System back exits the demo — never a real screen while demo data is
    // on display (the real tab and its listeners come back on exit).
    BackHandler { DemoTrackingMode.active = false }

    // Demo own card — the wizard walkthrough plus the quick status flips.
    OwnSharingCard(
        recipients = DemoTrackingDataSource.members.map { member ->
            RecipientOption(
                // Demo accounts have no real uid; the fixture's location
                // uid (or the demo rel id) stands in — demo-only, never
                // written anywhere.
                uid = member.location?.uid ?: member.relId,
                email = member.email,
                deviceModel = member.deviceModel
            )
        },
        isDemo = true
    )

    // The status gallery: all SEVEN sharing states rendered with their
    // icon + badge + color, so Phase 5 can verify every state visually.
    SectionCard(title = stringResource(R.string.loc_status_gallery), icon = Icons.Filled.Info) {
        LocationShareStatus.entries.forEach { status ->
            val chipColors = statusChipColors(locationShareStatusChipKind(status))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = locationShareStatusIcon(status),
                    contentDescription = null,
                    tint = chipColors.second,
                    modifier = Modifier.size(18.dp)
                )
                StatusBadge(
                    label = locationShareStatusLabel(status),
                    kind = locationShareStatusChipKind(status)
                )
            }
        }
    }

    // The three fictional members + the demo-fed map — the SAME shared
    // card composable and map section the real dashboard uses, fed from
    // DemoTrackingDataSource instead of CounterpartPins (no
    // RelationshipView is faked). The 30 s clock keeps the demo
    // freshness lines honest too.
    TickingBlock(intervalMillis = 30_000) { nowMillis ->
        val memberUis = DemoTrackingDataSource.members.map { member ->
            MemberLocationUi(
                relId = member.relId,
                title = stringResource(member.nameRes),
                subtitle = member.email,
                status = member.status,
                freshness = member.location?.let { locationFreshness(it, nowMillis) },
                location = member.location
            )
        }
        memberUis.forEach { ui ->
            val historyGranted =
                DemoTrackingDataSource.member(ui.relId)?.historyGranted == true
            MemberLocationCard(
                member = ui,
                onOpenDetails = { onOpenMember(ui.relId) },
                // History button only where the fixture grants it — the
                // history route itself is demo-fed in Phase 4.
                onOpenHistory = if (historyGranted) {
                    { onOpenHistory(ui.relId) }
                } else {
                    null
                }
            )
        }
        CounterpartMapSection(members = memberUis, onOpenMember = onOpenMember)
    }

    WideOutlinedButton(onClick = onOpenPlaces) {
        Text(stringResource(R.string.place_my_title))
    }
}

// ── Map section + device selector sheet ─────────────────────────────────

/** Sane "world view" starting zoom — nothing is implied about any device. */
private const val WORLD_ZOOM = 3.0

/** Street-level zoom when the camera centers on a single pinned device. */
private const val FOCUS_ZOOM = 16.0

/** Pure marker data (computed in composition so the update lambda stays context-free). */
private data class MarkerVisual(
    val relId: String,
    val lat: Double,
    val lng: Double,
    val title: String,
    val snippet: String,
    val accuracyMeters: Int,
    val freshness: LocationFreshness
)

/**
 * The accuracy circle for one marker, or null when the fix's radius is
 * not sane (outside 1..1000 m) — a 0 or absurd accuracy draws nothing
 * rather than a misleading circle.
 */
private fun MarkerVisual.accuracyVisual(): AccuracyVisual? {
    if (accuracyMeters !in 1..1000) return null
    return AccuracyVisual(
        relId = relId,
        lat = lat,
        lng = lng,
        radiusMeters = accuracyMeters.toDouble(),
        // STALE/OFFLINE pins get the dashed ring — the visual never says
        // "live" about an old fix (the snippet text already says it too).
        dashed = freshness != LocationFreshness.LIVE
    )
}

/** Pure accuracy-circle data (the overlay is rebuilt when any field changes). */
private data class AccuracyVisual(
    val relId: String,
    val lat: Double,
    val lng: Double,
    val radiusMeters: Double,
    val dashed: Boolean
)

/** A registered accuracy overlay plus the visual it was built from. */
private class AccuracyEntry(
    val overlay: AccuracyCircleOverlay,
    val visual: AccuracyVisual
)

/**
 * Phase 4 — the accuracy circle overlay: ONE stroked circle around a
 * marker's position with the fix's accuracyMeters as its ground radius.
 * Deliberately simple — no fill, no tap handling, no projection caching.
 * The pixel radius comes from projecting a latitude-offset twin point
 * (Mercator is conformal, so the vertical pixel scale equals the
 * horizontal one at that latitude — the drawn circle IS the ground
 * circle), avoiding any dependence on Projection.metersToPixels
 * overloads across osmdroid versions.
 */
private class AccuracyCircleOverlay(
    private val center: GeoPoint,
    private val radiusMeters: Double,
    dashed: Boolean,
    strokeColor: Int
) : Overlay() {
    private val paint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        isAntiAlias = true
        color = strokeColor
        if (dashed) {
            pathEffect = DashPathEffect(floatArrayOf(10f, 8f), 0f)
        }
    }

    override fun draw(c: Canvas, osmv: MapView, shadow: Boolean) {
        if (shadow) return
        val projection = osmv.projection
        val centerPx = projection.toPixels(center, null)
        val edgePx = projection.toPixels(
            GeoPoint(center.latitude + radiusMeters / 111_320.0, center.longitude),
            null
        )
        val radiusPx = abs((edgePx.y - centerPx.y).toDouble())
        // Sub-2px circles are invisible — skip the draw call entirely.
        if (radiusPx >= 2.0) {
            c.drawCircle(centerPx.x.toFloat(), centerPx.y.toFloat(), radiusPx.toFloat(), paint)
        }
    }
}

/**
 * Per-composition map sync bookkeeping: markers and accuracy circles by
 * relationship id plus the last camera-fit key. Deliberately NOT
 * snapshot state — it is only touched inside AndroidView.update, and it
 * must die with its MapView (a file-level map would leak markers from a
 * detached map into a fresh one after tab switches or configuration
 * changes).
 */
private class MapSyncState {
    val markers = HashMap<String, Marker>()
    val accuracy = HashMap<String, AccuracyEntry>()
    var fittedKey: String? = null

    /** v0.44.0 — the map style whose tile source is currently applied
     *  (null = nothing applied on this MapView yet). Style switches are
     *  live: setTileSource on the remembered view, markers untouched. */
    var appliedStyle: String? = null
}

/** Index-based overlay removal (the marker drop path's helper). */
private fun removeOverlayFrom(mv: MapView, overlay: Overlay) {
    val index = mv.overlays.indexOf(overlay)
    if (index >= 0) {
        mv.overlays.removeAt(index)
    }
}

/**
 * v0.45.0 (audit P5 — the map-gesture conflict): a MapView that lives
 * INSIDE a verticalScroll column fights the page for every vertical drag
 * (the page scrolls, the map does not pan — the "ম্যাপ স্মুথ নয়" feeling).
 * Claiming the gesture stream the moment a finger lands on the map —
 * parent.requestDisallowInterceptTouchEvent(true) on ACTION_DOWN — hands
 * pan AND pinch to the map; releasing it on UP/CANCEL restores page
 * scrolling the instant the finger leaves. Idempotent with osmdroid's own
 * internal request, and harmless in non-scrolling parents.
 */
private class NestableMapView(context: android.content.Context) : MapView(context) {
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN,
            MotionEvent.ACTION_POINTER_DOWN ->
                parent?.requestDisallowInterceptTouchEvent(true)
            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL,
            MotionEvent.ACTION_POINTER_UP ->
                parent?.requestDisallowInterceptTouchEvent(false)
        }
        return super.dispatchTouchEvent(event)
    }
}

/**
 * The map section — ONE Marker per member device that has actually
 * published a position (no marker is invented for a device that never
 * shared), an accuracy circle under each marker when the fix's radius is
 * sane, zoom controls in the bottom-end corner, the camera fitted to the
 * set of pinned devices, at-a-glance freshness for the focused device and
 * the device-selector ModalBottomSheet. Phase 4: the input is the shared
 * [MemberLocationUi] (real pins and demo members both feed it).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CounterpartMapSection(
    members: List<MemberLocationUi>,
    onOpenMember: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var selectedRelId by rememberSaveable { mutableStateOf<String?>(null) }
    var showDeviceSheet by remember { mutableStateOf(false) }
    // v0.44.0 (fast-map wave) — the persisted map STYLE (2D street /
    // satellite / terrain). The chips row above the map writes it; the
    // AndroidView update applies it LIVE (one tap, no view rebuild).
    var mapStyle by remember { mutableStateOf(MapStyles.current(context)) }

    // v0.45.0 (audit P5) — the camera SURVIVES tab switches and rotation:
    // pan/zoom write into these saveable slots through the MapListener
    // below, and a freshly re-created MapView restores them instead of
    // snapping back to the world view (the RELEASE_NOTES "accepted"
    // reset — no longer accepted).
    var camLat by rememberSaveable { mutableStateOf(0.0) }
    var camLng by rememberSaveable { mutableStateOf(0.0) }
    var camZoom by rememberSaveable { mutableStateOf(0.0) }

    // One MarkerVisual per device that has ACTUALLY published a position
    // (no marker is invented for a device that never shared).
    val pinnedVisuals = members.mapNotNull { member ->
        val location = member.location ?: return@mapNotNull null
        MarkerVisual(
            relId = member.relId,
            lat = location.lat,
            lng = location.lng,
            title = member.title,
            snippet = lastKnownLine(location),
            accuracyMeters = location.accuracyMeters,
            freshness = member.freshness ?: LocationFreshness.OFFLINE
        )
    }

    // The MapView is remembered (one instance across recompositions) and
    // wired to the host lifecycle: ON_RESUME/ON_PAUSE forward to the
    // osmdroid calls, disposal detaches the view and frees its overlays.
    // v0.44.0 — the tile source comes from the persisted map STYLE
    // (MapStyle.kt: fast CARTO Voyager 2D default / Esri satellite /
    // Esri terrain) and switches LIVE from the chips above the map;
    // multi-touch pan/zoom stays on. The copyright overlay is added once,
    // before any marker, and never removed — its text follows the ACTIVE
    // tile source (per-style attribution, MapStyles.attribution).
    val copyrightOverlay = remember {
        CopyrightOverlay(context).apply {
            // osmdroid 6.1.18: the overlay's setter is setCopyrightNotice;
            // each tile source ALSO carries its own copyright (the overlay
            // reads the ACTIVE source at draw time) — both carry the same
            // per-style line (MapStyles.attribution).
            setCopyrightNotice(MapStyles.attribution(MapStyles.current(context)))
        }
    }
    val mapView = remember {
        // v0.45.0 (audit P5): NestableMapView claims the gesture stream so
        // the surrounding verticalScroll can never eat the map's pan/pinch.
        NestableMapView(context).apply {
            MapStyles.apply(this, MapStyles.current(context))
            setMultiTouchControls(true)
            controller.setZoom(WORLD_ZOOM)
            overlays.add(copyrightOverlay)
            if (camLat != 0.0 || camLng != 0.0) {
                controller.setCenter(GeoPoint(camLat, camLng))
                if (camZoom > 0.0) controller.setZoom(camZoom)
            }
            // v0.45.0 (audit P5): keep the saveable camera slots in sync
            // with every pan/zoom (main thread — osmdroid dispatches there).
            addMapListener(object : MapListener {
                override fun onScroll(event: ScrollEvent?): Boolean {
                    camLat = mapCenter.latitude
                    camLng = mapCenter.longitude
                    return false
                }

                override fun onZoom(event: ZoomEvent?): Boolean {
                    camZoom = zoomLevelDouble
                    return false
                }
            })
        }
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onDetach()
        }
    }
    val sync = remember { MapSyncState() }
    // v0.45.0 (audit P5): with a RESTORED camera, suppress the first
    // auto-fit for the same pinned set — the user's zoom/pan wins until the
    // set of publishing devices actually changes.
    if ((camLat != 0.0 || camLng != 0.0) && sync.fittedKey == null) {
        sync.fittedKey = pinnedVisuals.joinToString("|") { it.relId }
    }

    // Overlay stroke colors resolved in the COMPOSABLE scope (the update
    // lambda below is not one): solid tertiary for LIVE rings, muted
    // onSurfaceVariant for the dashed STALE/OFFLINE rings.
    val liveStroke = MaterialTheme.colorScheme.tertiary.toArgb()
    val dimStroke = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()

    SectionCard(title = stringResource(R.string.loc_title), icon = Icons.Filled.LocationOn) {
        // v0.44.0 (Owner: "2D 3D ও setelite ম্যাপ যুক্ত করতে হবে") — the
        // map STYLE chips: 2D street / satellite / 3D-relief terrain. One
        // tap persists the choice and switches the tile source LIVE —
        // no view rebuild, markers and camera untouched. The chips sit
        // above the map so the active mode is always visible.
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = mapStyle == MapStyles.STYLE_2D,
                onClick = {
                    mapStyle = MapStyles.STYLE_2D
                    MapStyles.set(context, MapStyles.STYLE_2D)
                },
                label = { Text(stringResource(R.string.map_style_2d)) }
            )
            FilterChip(
                selected = mapStyle == MapStyles.STYLE_SATELLITE,
                onClick = {
                    mapStyle = MapStyles.STYLE_SATELLITE
                    MapStyles.set(context, MapStyles.STYLE_SATELLITE)
                },
                label = { Text(stringResource(R.string.map_style_satellite)) }
            )
            FilterChip(
                selected = mapStyle == MapStyles.STYLE_TERRAIN,
                onClick = {
                    mapStyle = MapStyles.STYLE_TERRAIN
                    MapStyles.set(context, MapStyles.STYLE_TERRAIN)
                },
                label = { Text(stringResource(R.string.map_style_terrain)) }
            )
        }
        Box {
            AndroidView(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(320.dp)
                    .clip(RoundedCornerShape(14.dp)),
                factory = { mapView },
                update = { mv ->
                    // v0.44.0 — the whole update is DIRTY-TRACKED: the 30 s
                    // freshness tick recomposes this section, but an
                    // unchanged map no longer forces a full redraw (the
                    // "স্মুথ" ask — invalidate only when something really
                    // changed).
                    var dirty = false
                    // 0. v0.44.0 — LIVE style switch: apply the tile source
                    //    + the matching attribution only when the chip
                    //    choice actually changed for THIS MapView.
                    if (sync.appliedStyle != mapStyle) {
                        sync.appliedStyle = mapStyle
                        MapStyles.apply(mv, mapStyle)
                        copyrightOverlay.setCopyrightNotice(
                            MapStyles.attribution(mapStyle)
                        )
                        dirty = true
                    }
                    // 1. drop markers + accuracy circles whose device
                    //    stopped publishing a position
                    val pinnedIds = pinnedVisuals.map { it.relId }.toHashSet()
                    (sync.markers.keys + sync.accuracy.keys)
                        .filter { it !in pinnedIds }
                        .forEach { relId ->
                            sync.markers.remove(relId)?.let { marker ->
                                removeOverlayFrom(mv, marker)
                                dirty = true
                            }
                            sync.accuracy.remove(relId)?.let { entry ->
                                removeOverlayFrom(mv, entry.overlay)
                                dirty = true
                            }
                        }
                    // 2. accuracy circles (inserted right after the
                    //    copyright overlay → drawn UNDER the markers):
                    //    solid for LIVE, dashed ring for STALE/OFFLINE,
                    //    only for sane radii (1..1000 m). Rebuilt only
                    //    when position/radius/style actually changed.
                    pinnedVisuals.forEach { visual ->
                        val wanted = visual.accuracyVisual()
                        val entry = sync.accuracy[visual.relId]
                        if (wanted == null) {
                            if (entry != null) {
                                removeOverlayFrom(mv, entry.overlay)
                                sync.accuracy.remove(visual.relId)
                                dirty = true
                            }
                        } else if (entry == null || entry.visual != wanted) {
                            if (entry != null) {
                                removeOverlayFrom(mv, entry.overlay)
                            }
                            val overlay = AccuracyCircleOverlay(
                                center = GeoPoint(wanted.lat, wanted.lng),
                                radiusMeters = wanted.radiusMeters,
                                dashed = wanted.dashed,
                                strokeColor = if (wanted.dashed) dimStroke else liveStroke
                            )
                            // minOf guards a (never-expected) missing
                            // copyright overlay at index 0.
                            mv.overlays.add(minOf(1, mv.overlays.size), overlay)
                            sync.accuracy[visual.relId] = AccuracyEntry(overlay, wanted)
                            dirty = true
                        }
                    }
                    // 3. add new markers, move/relabel existing ones —
                    //    unchanged pins skip even the invalidate.
                    pinnedVisuals.forEach { visual ->
                        val point = GeoPoint(visual.lat, visual.lng)
                        val existing = sync.markers[visual.relId]
                        if (existing == null) {
                            val marker = Marker(mv)
                            marker.setPosition(point)
                            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                            marker.setTitle(visual.title)
                            marker.setSnippet(visual.snippet)
                            mv.overlays.add(marker)
                            sync.markers[visual.relId] = marker
                            dirty = true
                        } else {
                            if (existing.position.latitude != visual.lat ||
                                existing.position.longitude != visual.lng
                            ) {
                                existing.setPosition(point)
                                dirty = true
                            }
                            if (existing.title != visual.title) {
                                existing.setTitle(visual.title)
                                dirty = true
                            }
                            if (existing.snippet != visual.snippet) {
                                existing.setSnippet(visual.snippet)
                                dirty = true
                            }
                        }
                    }
                    // 4. camera follows the SET of pinned devices (a new fix for
                    //    an already-pinned device must not yank the camera)
                    val fitKey = pinnedVisuals.joinToString("|") { it.relId }
                    if (fitKey != sync.fittedKey) {
                        sync.fittedKey = fitKey
                        dirty = true
                        if (pinnedVisuals.size == 1) {
                            mv.controller.setZoom(FOCUS_ZOOM)
                            mv.controller.setCenter(
                                GeoPoint(pinnedVisuals[0].lat, pinnedVisuals[0].lng)
                            )
                        } else if (pinnedVisuals.size > 1) {
                            // v0.45.0 (audit P5): co-located pins produce a
                            // near-zero-span box — zoomToBoundingBox would
                            // slam to maximum zoom. A span under ~0.0005°
                            // (≈ 55 m) is treated as ONE point instead.
                            // (osmdroid 6.1.18 BoundingBox has no spanE6
                            // getters — the span is north−south / east−west.)
                            val points =
                                pinnedVisuals.map { GeoPoint(it.lat, it.lng) }
                            val box = BoundingBox.fromGeoPoints(points)
                            val latSpan = box.latNorth - box.latSouth
                            val lonSpan = box.lonEast - box.lonWest
                            if (latSpan < 0.0005 && lonSpan < 0.0005) {
                                mv.controller.setZoom(FOCUS_ZOOM)
                                mv.controller.setCenter(points[0])
                            } else {
                                mv.zoomToBoundingBox(box, false, 80)
                            }
                        } else {
                            // no pinned device left → back to the world view
                            mv.controller.setZoom(WORLD_ZOOM)
                        }
                    }
                    // v0.44.0 — repaint ONLY when something actually changed.
                    if (dirty) mv.invalidate()
                }
            )
            // Phase 4 — zoom +/− controls over the map's bottom-end corner:
            // 48 dp IconButtons (core-set KeyboardArrow glyphs — no "+/−"
            // pair exists in the core set), semitransparent plates so they
            // read over the tiles, honest cd_map_zoom_* descriptions.
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(
                    onClick = {
                        // v0.45.0 (audit P5): animated zoom (controller.zoomIn
                        // runs the pinch-scale animation) instead of the
                        // integer setZoom jump.
                        mapView.controller.zoomIn()
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                            CircleShape
                        )
                ) {
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowUp,
                        contentDescription = stringResource(R.string.cd_map_zoom_in)
                    )
                }
                IconButton(
                    onClick = {
                        // v0.45.0 (audit P5): animated zoom-out, same as above.
                        mapView.controller.zoomOut()
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                            CircleShape
                        )
                ) {
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowDown,
                        contentDescription = stringResource(R.string.cd_map_zoom_out)
                    )
                }
            }
        }

        // At-a-glance freshness for the focused device (sheet pick or first
        // pinned device), with the always-visible last-known line.
        val focused = members.firstOrNull { it.relId == selectedRelId }
            ?: members.firstOrNull { it.location != null }
        if (focused == null) {
            Text(
                text = stringResource(R.string.loc_no_fix),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    val location = focused.location
                    if (location == null) {
                        Text(
                            text = stringResource(R.string.loc_never),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        val freshness = focused.freshness
                            ?: locationFreshness(location, System.currentTimeMillis())
                        Text(
                            text = freshnessLabel(freshness),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = freshnessTextColors(freshness)
                        )
                        Text(
                            text = lastKnownLine(location),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                TextButton(onClick = { onOpenMember(focused.relId) }) {
                    Text(stringResource(R.string.loc_details))
                }
            }
        }

        WideOutlinedButton(onClick = { showDeviceSheet = true }) {
            Text(stringResource(R.string.loc_list_title))
        }
    }

    if (showDeviceSheet) {
        val sheetState = rememberModalBottomSheetState()
        ModalBottomSheet(
            onDismissRequest = { showDeviceSheet = false },
            sheetState = sheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = stringResource(R.string.loc_list_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                members.forEach { member ->
                    DevicePickerRow(
                        member = member,
                        onPick = {
                            selectedRelId = member.relId
                            member.location?.let { location ->
                                mapView.controller.animateTo(
                                    GeoPoint(location.lat, location.lng)
                                )
                            }
                            showDeviceSheet = false
                        },
                        onOpenDetails = {
                            showDeviceSheet = false
                            onOpenMember(member.relId)
                        }
                    )
                }
            }
        }
    }
}

/**
 * One member device in the selector sheet: name/email tile with the
 * freshness chip, the always-visible last-known line, and the route into
 * tracking/{relId} (loc_details). Tapping the tile selects the device and
 * centers the map when a position exists.
 */
@Composable
private fun DevicePickerRow(
    member: MemberLocationUi,
    onPick: () -> Unit,
    onOpenDetails: () -> Unit
) {
    val freshness = member.freshness
    val chipColors = freshness?.let { freshnessChipColors(it) }
    Column {
        AppListTile(
            title = member.title,
            subtitle = member.subtitle,
            icon = Icons.Filled.Phone,
            chip = freshness?.let { freshnessLabel(it) },
            chipContainer = chipColors?.first
                ?: MaterialTheme.colorScheme.secondaryContainer,
            chipContent = chipColors?.second
                ?: MaterialTheme.colorScheme.onSecondaryContainer,
            onClick = onPick
        )
        Row(
            modifier = Modifier.padding(start = 56.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = lastKnownLine(member.location),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onOpenDetails) {
                Text(stringResource(R.string.loc_details))
            }
        }
    }
}
