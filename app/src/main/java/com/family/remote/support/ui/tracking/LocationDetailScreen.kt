// ═══════════════════════════════════════════════════════════════════════
// Phase 22/M5 (v0.33.0) — Location Detail screen
// (docs/NAVIGATION_MAP.md §2 "tracking/{relId} — Location Details: last
// update time, accuracy, staleness, sharing on/off, open-in-maps action";
// docs/LIVE_LOCATION_ARCHITECTURE.md §3 map-side flow).
//
// Structure mirrors ui/home/FamilyScreens.kt's MemberDetailScreen: a
// back-arrow header row, canonical loading/error/not-found states, one
// relationship listener (the same watchRelationships every family screen
// uses — no new queries) plus a watchLiveLocation listener for the
// resolved counterpart device, and honest rows built from real data only.
//
// Honesty rules: the counterpart's sharing status is READ-ONLY here (only
// its owner can toggle it); no position doc → "Never shared" and the
// open-in-maps action is simply absent; a revoked/expired relationship or
// a stale route id → the honest not-found state with a way back. The
// defensive own-device branch (the relationship's device is THIS phone)
// shows the owner stop control instead of a counterpart view, because the
// owner can never read their own liveLocations doc through the rules.
// ═══════════════════════════════════════════════════════════════════════
package com.family.remote.support.ui.tracking

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.family.remote.support.LiveLocationView
import com.family.remote.support.R
import com.family.remote.support.ReceivedLocationAuthorization
import com.family.remote.support.RelationshipView
import com.family.remote.support.describeFirestoreError
import com.family.remote.support.readReceivedLocationAuthorization
import com.family.remote.support.watchLiveLocation
import com.family.remote.support.watchRelationships
import com.family.remote.support.ui.components.BackTitleRow
import com.family.remote.support.ui.components.BigButton
import com.family.remote.support.ui.components.EmptyState
import com.family.remote.support.ui.components.ErrorPanel
import com.family.remote.support.ui.components.LoadingSkeleton
import com.family.remote.support.ui.components.SectionCard
import com.family.remote.support.ui.components.StatusBadge
import com.family.remote.support.ui.components.TickingBlock
import com.family.remote.support.ui.components.WideOutlinedButton
import com.family.remote.support.ui.tracking.demo.DemoBanner
import com.family.remote.support.ui.tracking.demo.DemoMember
import com.family.remote.support.ui.tracking.demo.DemoTrackingDataSource
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** UiState for the detail watch: the resolved relationship + its last position + the received grant. */
private data class LocationDetailUiState(
    val rel: RelationshipView? = null,
    val location: LiveLocationView? = null,
    // Phase 9 — the grant this account received from the member (section
    // B/E's real values); authzReadDone gates the skeleton-vs-absence call.
    val authz: ReceivedLocationAuthorization? = null,
    val authzReadDone: Boolean = false,
    val loaded: Boolean = false,
    val error: String? = null
)

/**
 * The detail screen's private ViewModel (the TrackingViewModel shape,
 * focused on ONE relationship): one relationship listener resolving
 * [relId], plus a single live-location listener that is re-registered
 * only when the resolved (and still authorized) device changes. Routed
 * errors + keep-last-data follow the same v0.21.0 rule.
 */
private class LocationDetailViewModel : ViewModel() {

    private val _state = MutableStateFlow(LocationDetailUiState())
    val state: StateFlow<LocationDetailUiState> = _state.asStateFlow()

    private var stopWatch: (() -> Unit)? = null
    private var stopLocation: (() -> Unit)? = null
    private var relationships: List<RelationshipView> = emptyList()
    private var location: LiveLocationView? = null
    private var watchKey: Pair<String, String>? = null
    private var myUid: String? = null
    private var relId: String = ""

    // Phase 9 — the one-shot recipient-side grant read (one per resolved
    // counterpart, keyed like the location watch so churn never re-reads).
    private var authzKey: Pair<String, String>? = null
    private var receivedAuthz: ReceivedLocationAuthorization? = null
    private var authzReadDone = false
    private val authzScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun observe(user: FirebaseUser?, relId: String, describeError: (Exception) -> String) {
        stopWatch?.invoke()
        stopWatch = null
        stopLocation?.invoke()
        stopLocation = null
        watchKey = null
        location = null
        relationships = emptyList()
        authzKey = null
        receivedAuthz = null
        authzReadDone = false
        myUid = user?.uid
        this.relId = relId
        if (user == null) {
            _state.value = LocationDetailUiState()
            return
        }
        stopWatch = watchRelationships(
            user,
            onUpdate = { rels ->
                relationships = rels
                syncLocationWatch(describeError)
                emit()
            },
            onError = { e ->
                _state.value = _state.value.copy(error = describeError(e))
            }
        )
    }

    private fun currentRel(): RelationshipView? =
        relationships.firstOrNull { it.id == relId }

    /**
     * Watch the counterpart's position only while this relationship
     * actually authorizes it: ACTIVE, guest window live, the tracked
     * device belongs to the other account, and it has a device id. Any
     * other state (revoked, expired, own device) has no listener at all —
     * nothing is shown that the rules would not let us read.
     */
    private fun syncLocationWatch(describeError: (Exception) -> String) {
        val rel = currentRel()
        val uid = myUid
        // v0.37.0 (dedupe wave) — ONE counterpart-trackable predicate
        // (TrackingViewModel.isTrackableCounterpart): was a drifting copy
        // of the same 4 conditions the tab's filter uses.
        val key = if (rel != null && uid != null && rel.isTrackableCounterpart(uid)) {
            rel.recipientUid to rel.recipientDeviceId
        } else {
            null
        }
        if (key != watchKey) {
            stopLocation?.invoke()
            stopLocation = null
            location = null
            watchKey = key
            if (key != null) {
                stopLocation = watchLiveLocation(
                    key.first,
                    key.second,
                    onChange = { view ->
                        location = view
                        emit()
                    },
                    onError = { e ->
                        _state.value = _state.value.copy(error = describeError(e))
                    }
                )
                // Phase 9 — the grant read follows the SAME key swap: one
                // one-shot get per resolved counterpart (user action —
                // opening this screen; §75).
                if (key != authzKey) {
                    authzKey = key
                    val ownerUid = key.first
                    val ownerDeviceId = key.second
                    authzScope.launch {
                        try {
                            receivedAuthz = readReceivedLocationAuthorization(ownerUid, ownerDeviceId)
                        } catch (_: Exception) {
                            // Unreadable right now (offline): sections B/E
                            // keep their honest defaults — no stale grant is
                            // ever rendered as current.
                            receivedAuthz = null
                        }
                        authzReadDone = true
                        emit()
                    }
                }
            }
        }
    }

    private fun emit() {
        _state.value = LocationDetailUiState(
            rel = currentRel(),
            location = location,
            authz = receivedAuthz,
            authzReadDone = authzReadDone,
            loaded = true
        )
    }

    override fun onCleared() {
        stopWatch?.invoke()
        stopWatch = null
        stopLocation?.invoke()
        stopLocation = null
        authzScope.cancel()
    }
}

/**
 * M5 (v0.33.0) — NAVIGATION_MAP §2 "tracking/{relId} — Location Details".
 *
 * @param user   the signed-in account (null → the honest signed-out state
 *               with a way back).
 * @param relId  the relationship doc id from the route argument.
 * @param onBack pops the route (back row, not-found escape).
 * @param onOpenHistory navigates to tracking/{relId}/history — Phase 9
 *               renders the button only when the grant says so.
 */
@Composable
internal fun LocationDetailScreen(
    user: FirebaseUser?,
    relId: String,
    onBack: () -> Unit,
    onOpenHistory: () -> Unit = {}
) {
    val context = LocalContext.current
    if (user == null) {
        BackTitleRow(title = stringResource(R.string.loc_details), onBack = onBack)
        EmptyState(
            icon = Icons.Filled.LocationOn,
            title = stringResource(R.string.phone_signin_first),
            hint = stringResource(R.string.phone_signin_hint)
        )
        return
    }

    val vm: LocationDetailViewModel = viewModel()
    var retryKey by remember { mutableStateOf(0) }
    DisposableEffect(user.uid, relId, retryKey) {
        vm.observe(user, relId) { e -> describeFirestoreError(context, e) }
        onDispose { }
    }
    val state by vm.state.collectAsState()

    // Canonical states (NAVIGATION_MAP §5) — same priority as the tab.
    val routedError = state.error?.takeIf { !state.loaded }
    when {
        routedError != null -> ErrorPanel(message = routedError, onRetry = { retryKey++ })
        !state.loaded -> LoadingSkeleton(rows = 3)
        else -> {
            val rel = state.rel
            if (rel == null || rel.status != "ACTIVE" || !rel.guestLive) {
                // Honest not-found: revoked/expired/removed from the other
                // side (or a stale route argument) — same wording and way
                // back as MemberDetailScreen's not-found branch.
                BackTitleRow(title = stringResource(R.string.loc_details), onBack = onBack)
                EmptyState(
                    icon = Icons.Filled.Phone,
                    title = stringResource(R.string.fam_not_found),
                    hint = stringResource(R.string.fam_not_found_h)
                )
                WideOutlinedButton(onClick = onBack) {
                    Text(stringResource(R.string.cd_back))
                }
            } else if (rel.recipientUid == user.uid) {
                // Defensive branch: the relationship's tracked device is
                // THIS phone — the owner can never read their own doc
                // through the rules, so show the owner stop control (the
                // same card the Tracking tab banner uses) instead of a
                // counterpart position view.
                BackTitleRow(title = stringResource(R.string.loc_details), onBack = onBack)
                Text(
                    text = counterpartEmail(rel),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                OwnSharingCard()
            } else {
                // v0.37.0 (dedupe wave) — the 30 s freshness clock
                // (PresenceBadge cadence) now lives in TickingBlock instead
                // of a screen-level while(true) loop: only the detail body
                // reads it, so the LIVE/STALE/OFFLINE line stays honest
                // while the screen is open without whole-screen churn.
                TickingBlock(intervalMillis = 30_000) { nowMillis ->
                    CounterpartDetailBody(
                        rel = rel,
                        location = state.location,
                        authz = state.authz,
                        authzReadDone = state.authzReadDone,
                        nowMillis = nowMillis,
                        // §4-E "who receives": THIS account is the recipient
                        // viewing the member's sharing — email with the
                        // honest uid fallback (phone-auth accounts may have
                        // no email; counterpartEmail uses the same ladder).
                        viewerEmail = user.email ?: user.uid,
                        onOpenHistory = onOpenHistory,
                        onBack = onBack
                    )
                }
            }
        }
    }
}

/**
 * The counterpart detail body: device identity (email + role + model, the
 * MemberDetailScreen line style), freshness + "Updated HH:mm" + accuracy
 * (ALWAYS visible — an old coordinate is never labeled live), the
 * read-only sharing status, the open-in-maps action when a position
 * exists, section B "Journey history" (grant-gated, Phase 9) and section E
 * "Sharing & privacy" with the grant's REAL values (Phase 9).
 */
@Composable
private fun CounterpartDetailBody(
    rel: RelationshipView,
    location: LiveLocationView?,
    authz: ReceivedLocationAuthorization?,
    authzReadDone: Boolean,
    nowMillis: Long,
    viewerEmail: String,
    onOpenHistory: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val email = counterpartEmail(rel)
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        BackTitleRow(title = stringResource(R.string.loc_details), onBack = onBack)
        Text(
            text = email,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = buildString {
                append(stringResource(R.string.rel_you_help))
                if (rel.recipientDeviceModel.isNotBlank()) {
                    append(" · ${rel.recipientDeviceModel}")
                }
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        HorizontalDivider()
        if (location == null) {
            // No doc at all — the owner never shared (or stopped and the
            // doc was deleted). No position, no maps action, no invention.
            Text(
                text = stringResource(R.string.loc_never),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            val freshness = locationFreshness(location, nowMillis)
            Text(
                text = freshnessLabel(freshness),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = freshnessTextColors(freshness)
            )
            Text(
                text = stringResource(
                    R.string.loc_updated,
                    locationTimeString(location.updatedAtMillis)
                ) + " · " + stringResource(R.string.loc_acc_m, location.accuracyMeters),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        // READ-ONLY counterpart sharing status — only its owner can toggle
        // it (from their own Tracking tab or Settings), never from here.
        val sharingOn = location?.sharingEnabled == true
        Text(
            text = stringResource(
                if (sharingOn) R.string.loc_share_on else R.string.loc_share_off
            ),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = if (sharingOn) {
                MaterialTheme.colorScheme.tertiary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
        if (location != null) {
            WideOutlinedButton(
                onClick = { openInMaps(context, location) }
            ) {
                Text(stringResource(R.string.loc_open_maps))
            }
        }

        // ── Section B "Journey history" — ONLY when the grant says so ──
        // (not granted → NOTHING renders: honest absence, no locked card).
        // Quota-protective deviation, recorded in the progress doc: the
        // spec's summary chips live on the journey screen itself (one
        // bounded query per open); here the action + the retention in
        // force carry the section. A grant still being read renders
        // nothing yet — never a premature absence verdict.
        if (authz != null && authz.activeAt(nowMillis) && authz.historyEnabled) {
            SectionCard(
                title = stringResource(R.string.sec_b_note),
                icon = Icons.Filled.DateRange
            ) {
                BigButton(onClick = onOpenHistory) {
                    Text(stringResource(R.string.hist_open))
                }
                Text(
                    text = stringResource(
                        R.string.hist_ret,
                        retentionLabel(retentionKindFromPolicy(authz.retentionPolicy))
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // ── Section E "Sharing & privacy" (UX spec §4-E, real values) ────
        // What THIS account may receive from the member's sharing: the
        // grant's expiry / retention / history-access lines, all read from
        // the authorization doc this screen fetched. While the read is in
        // flight only the receives line shows (no premature "none"
        // verdict); no doc → the honest "none" lines — a locked/teaser
        // card implying a live feature would be dishonest.
        SectionCard(
            title = stringResource(R.string.sec_e_note),
            icon = Icons.Filled.Lock
        ) {
            Text(
                text = stringResource(R.string.loc_priv_receives, viewerEmail),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!authzReadDone) {
                // Grant read in flight — the remaining lines arrive with it.
            } else if (authz != null && !authz.revoked && authz.activeAt(nowMillis)) {
                if (authz.expiresAtMillis != 0L) {
                    Text(
                        text = stringResource(
                            R.string.loc_priv_expiry,
                            SimpleDateFormat("HH:mm, d MMM", Locale.getDefault())
                                .format(Date(authz.expiresAtMillis))
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = stringResource(
                        R.string.hist_ret,
                        retentionLabel(retentionKindFromPolicy(authz.retentionPolicy))
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = stringResource(
                        R.string.loc_priv_hist,
                        stringResource(
                            if (authz.historyEnabled) {
                                R.string.loc_hist_granted
                            } else {
                                R.string.loc_hist_none_word
                            }
                        )
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else if (authz != null && authz.revoked) {
                // Ended grant: nothing is claimed from a revoked consent —
                // access ended, plainly said.
                Text(
                    text = stringResource(
                        R.string.loc_priv_hist,
                        stringResource(R.string.loc_hist_none_word)
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = stringResource(R.string.hist_access_end),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                // No live grant (never shared / expired / still unread):
                // the honest none-lines, never a teaser.
                Text(
                    text = stringResource(
                        R.string.hist_ret,
                        stringResource(R.string.loc_hist_none_word)
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = stringResource(
                        R.string.loc_priv_hist,
                        stringResource(R.string.loc_hist_none_word)
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * "geo:lat,lng?z=17" view intent — opens whatever maps app handles it.
 * No maps app installed → a silent no-op (better than a crash or a fake
 * "opened" state); the row stays honest because it only appears when a
 * real position exists.
 */
private fun openInMaps(context: Context, location: LiveLocationView) {
    try {
        context.startActivity(
            Intent(
                Intent.ACTION_VIEW,
                Uri.parse("geo:${location.lat},${location.lng}?z=17")
            )
        )
    } catch (_: Exception) {
        // no activity to handle the geo intent — nothing else we can do
    }
}

// v0.37.0 (dedupe wave) — the M4 detail-screen header now lives in the
// design system (ui/components/Core.kt BackTitleRow, promoted VERBATIM from
// this file's private copy); the four call sites above use the shared one.

// ═══════════════════════════════════════════════════════════════════════
// Phase 4 of the Location master plan (v0.38.0/57) — DEMO member detail
// (docs/location/LOCATION_UX_SPECIFICATION.md §4 sections A–E + §11 demo
// strategy). Renders the same sections as the real screen but entirely
// from DemoTrackingDataSource fixtures: zero listeners, zero Firestore
// traffic, the persistent demo banner on top, and explicitly-demo values
// (approximate area words, fixture expiry stamps) that are never
// presented as real data. The screen is reachable only while
// DemoTrackingMode.active (the Tracking tab's demo entry point).
// ═══════════════════════════════════════════════════════════════════════

/**
 * The demo member detail screen (tracking/{demo-relId} while demo mode is
 * on). Same section skeleton as the real [LocationDetailScreen] counterpart
 * branch, fixture-fed.
 *
 * @param relId         the demo member id ("demo-" prefix).
 * @param onBack        returns to the demo dashboard (also wired to the
 *                      demo banner's ✕ — see below).
 * @param onOpenHistory navigates to tracking/{relId}/history.
 */
@Composable
internal fun DemoLocationDetailScreen(
    relId: String,
    onBack: () -> Unit,
    onOpenHistory: (String) -> Unit
) {
    val member = DemoTrackingDataSource.member(relId)
    if (member == null) {
        // Unknown demo id (stale route argument) — the honest not-found
        // state, same wording as the real screen's revoked branch.
        BackTitleRow(title = stringResource(R.string.loc_details), onBack = onBack)
        EmptyState(
            icon = Icons.Filled.Phone,
            title = stringResource(R.string.fam_not_found),
            hint = stringResource(R.string.fam_not_found_h)
        )
        return
    }

    BackTitleRow(title = stringResource(R.string.loc_details), onBack = onBack)
    Text(
        text = stringResource(member.nameRes),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold
    )
    Text(
        text = member.email + " · " + member.deviceModel,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    // The banner's ✕ on DETAIL screens means "leave this screen" (onBack,
    // returning to the demo dashboard) — leaving demo mode itself happens
    // from the dashboard, so the same callback serves both readings here.
    DemoBanner(onExit = onBack)

    // Only the section-A freshness line reads the clock (the real screen's
    // TickingBlock cadence); the rest of the sections stay tick-quiet.
    TickingBlock(intervalMillis = 30_000) { nowMillis ->
        DemoMemberDetailBody(
            member = member,
            nowMillis = nowMillis,
            onBack = onBack,
            onOpenHistory = onOpenHistory
        )
    }
}

/**
 * The demo sections A–E. Authorization-gated sections render ONLY when the
 * fixture grants them (§4 "rendered only when permitted") — no teaser
 * cards for what the fixture does not authorize.
 */
@Composable
private fun DemoMemberDetailBody(
    member: DemoMember,
    nowMillis: Long,
    onBack: () -> Unit,
    onOpenHistory: (String) -> Unit
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // ── Section A "Current location" ──────────────────────────────
        SectionCard(
            title = stringResource(R.string.sec_a_note),
            icon = Icons.Filled.LocationOn
        ) {
            StatusBadge(
                label = locationShareStatusLabel(member.status),
                kind = locationShareStatusChipKind(member.status)
            )
            val location = member.location
            if (location != null) {
                // Icon + label + color (never color alone): the freshness
                // word is derived from the fixture's timestamp vs the
                // reader's clock, exactly like the real screen.
                val freshness = locationFreshness(location, nowMillis)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.LocationOn,
                        contentDescription = null,
                        tint = freshnessTextColors(freshness),
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = freshnessLabel(freshness),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = freshnessTextColors(freshness)
                    )
                }
            }
            // Always-visible last-known line ("Never shared" when the
            // fixture has no position — never a fabricated time).
            Text(
                text = lastKnownLine(member.location),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            // Approximate AREA word only — demo fixtures are never
            // presented as exact addresses.
            Text(
                text = stringResource(
                    R.string.loc_addr_approx,
                    stringResource(member.areaRes)
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (location != null) {
                WideOutlinedButton(
                    onClick = { openInMaps(context, location) }
                ) {
                    Text(stringResource(R.string.loc_open_maps))
                }
            }
        }

        // ── Section B "Journey history" — ONLY when granted ───────────
        // (not granted → NOTHING renders: honest absence, no locked card)
        if (member.historyGranted) {
            SectionCard(
                title = stringResource(R.string.sec_b_note),
                icon = Icons.Filled.DateRange
            ) {
                val days = DemoTrackingDataSource.historyDays(member.relId)
                val latestDay = days.maxByOrNull { it.dayIndex }
                val latestPoints = latestDay
                    ?.let { DemoTrackingDataSource.historyPoints(member.relId, it.dayIndex) }
                    ?: emptyList()
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DemoSummaryChip(
                        text = stringResource(R.string.hist_sum_points, latestPoints.size)
                    )
                    latestDay?.let { day ->
                        DemoSummaryChip(
                            text = stringResource(R.string.hist_sum_cov, day.coveragePercent)
                        )
                    }
                }
                BigButton(onClick = { onOpenHistory(member.relId) }) {
                    Text(stringResource(R.string.hist_open))
                }
                Text(
                    text = stringResource(
                        R.string.hist_ret,
                        retentionLabel(member.retention)
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // ── Section C "Movement insights" — ONLY with a fixture ───────
        // (MovementCard itself also renders nothing for null movement.)
        member.movement?.let { movement ->
            MovementCard(movement = movement)
        }

        // ── Section D "Place alerts on this member" — reserved, ships
        // HIDDEN (v1 defers recipient alerts; no teaser card rendered).

        // ── Section E "Sharing & privacy" ─────────────────────────────
        SectionCard(
            title = stringResource(R.string.sec_e_note),
            icon = Icons.Filled.Lock
        ) {
            // Demo recipients line intentionally omitted: there is no
            // "You" string key, and the named authorization entity only
            // arrives with Phase 8 — listing the other demo members'
            // names as recipients would be misleading.
            member.expiryMillis?.let { expiry ->
                Text(
                    text = stringResource(
                        R.string.loc_priv_expiry,
                        demoExpiryLine(expiry)
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = stringResource(
                    R.string.hist_ret,
                    retentionLabel(member.retention)
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = stringResource(
                    R.string.loc_priv_hist,
                    stringResource(
                        if (member.historyGranted) {
                            R.string.loc_hist_granted
                        } else {
                            R.string.loc_hist_none_word
                        }
                    )
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // A way back for thumbs that scrolled past the header row.
        WideOutlinedButton(onClick = onBack) {
            Text(stringResource(R.string.cd_back))
        }
    }
}

/**
 * Display-only summary pill (section B chips). Same visual language as
 * AppListTile's trailing chip (secondaryContainer pill + SemiBold
 * labelMedium); deliberately a private copy — Core.kt is not this agent's
 * file, so promotion into the design system belongs to a later dedupe
 * wave.
 */
@Composable
private fun DemoSummaryChip(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier
            .background(
                MaterialTheme.colorScheme.secondaryContainer,
                RoundedCornerShape(999.dp)
            )
            .padding(horizontal = 10.dp, vertical = 4.dp)
    )
}

/** "HH:mm, d MMM" — the fixture's expiry stamp (demo value, not live). */
private fun demoExpiryLine(expiryMillis: Long): String =
    SimpleDateFormat("HH:mm, d MMM", Locale.getDefault()).format(Date(expiryMillis))
