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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import com.family.remote.support.RelationshipView
import com.family.remote.support.describeFirestoreError
import com.family.remote.support.watchLiveLocation
import com.family.remote.support.watchRelationships
import com.family.remote.support.ui.components.EmptyState
import com.family.remote.support.ui.components.ErrorPanel
import com.family.remote.support.ui.components.LoadingSkeleton
import com.family.remote.support.ui.components.WideOutlinedButton
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** UiState for the detail watch: the resolved relationship + its last position. */
private data class LocationDetailUiState(
    val rel: RelationshipView? = null,
    val location: LiveLocationView? = null,
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

    fun observe(user: FirebaseUser?, relId: String, describeError: (Exception) -> String) {
        stopWatch?.invoke()
        stopWatch = null
        stopLocation?.invoke()
        stopLocation = null
        watchKey = null
        location = null
        relationships = emptyList()
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
        val key = if (rel != null && uid != null && rel.status == "ACTIVE" && rel.guestLive &&
            rel.recipientUid != uid && rel.recipientDeviceId.isNotBlank()
        ) {
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
            }
        }
    }

    private fun emit() {
        _state.value = LocationDetailUiState(
            rel = currentRel(),
            location = location,
            loaded = true
        )
    }

    override fun onCleared() {
        stopWatch?.invoke()
        stopWatch = null
        stopLocation?.invoke()
        stopLocation = null
    }
}

/**
 * M5 (v0.33.0) — NAVIGATION_MAP §2 "tracking/{relId} — Location Details".
 *
 * @param user   the signed-in account (null → the honest signed-out state
 *               with a way back).
 * @param relId  the relationship doc id from the route argument.
 * @param onBack pops the route (back row, not-found escape).
 */
@Composable
internal fun LocationDetailScreen(user: FirebaseUser?, relId: String, onBack: () -> Unit) {
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

    // Freshness is clock-based — tick every 30 s (PresenceBadge cadence)
    // so the LIVE/STALE/OFFLINE line stays honest while the screen is open.
    var nowMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            nowMillis = System.currentTimeMillis()
            delay(30_000)
        }
    }

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
                CounterpartDetailBody(
                    rel = rel,
                    location = state.location,
                    nowMillis = nowMillis,
                    onBack = onBack
                )
            }
        }
    }
}

/**
 * The counterpart detail body: device identity (email + role + model, the
 * MemberDetailScreen line style), freshness + "Updated HH:mm" + accuracy
 * (ALWAYS visible — an old coordinate is never labeled live), the
 * read-only sharing status, and the open-in-maps action when a position
 * exists.
 */
@Composable
private fun CounterpartDetailBody(
    rel: RelationshipView,
    location: LiveLocationView?,
    nowMillis: Long,
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

/** The M4 detail-screen header: back arrow + screen title (MemberDetailScreen style). */
@Composable
private fun BackTitleRow(title: String, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.cd_back)
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
    }
}
