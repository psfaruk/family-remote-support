package com.family.remote.support.ui.tracking

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.family.remote.support.LiveLocationView
import com.family.remote.support.Nicknames
import com.family.remote.support.R
import com.family.remote.support.describeFirestoreError
import com.family.remote.support.watchLiveLocation
import com.family.remote.support.ui.components.BackTitleRow
import com.family.remote.support.ui.components.BigButton
import com.family.remote.support.ui.components.DetailRow
import com.family.remote.support.ui.components.EmptyState
import com.family.remote.support.ui.components.Fmt
import com.family.remote.support.ui.components.KindChip
import com.family.remote.support.ui.components.PresenceBadge
import com.family.remote.support.ui.components.SectionCard
import com.family.remote.support.ui.components.StatusBadge
import com.family.remote.support.ui.components.appLocale
import com.family.remote.support.ui.components.relTime
import com.family.remote.support.ui.components.tickingNow
import com.family.remote.support.ui.home.HomeViewModel
import com.google.firebase.auth.FirebaseUser
import java.util.Locale

/**
 * ui/tracking/TrackingDetailScreen.kt — tracking/{relId}, the Location
 * Details screen (v0.52.0; NAVIGATION_MAP §2 "M5: Location Details", made
 * REAL in the rebuilt frontend — the W5 wave had shipped the Tracking tab
 * root only, and every partner row dead-ended on a WIP placeholder).
 *
 * Read-only counterpart facts, exactly as the nav map specifies: last
 * update time, accuracy, staleness, kind/guest window, connection status,
 * and the open-in-maps action. The sharer's own controls stay on the
 * Tracking tab root (their sequence, their tab — the Owner's directive).
 *
 * Quota discipline (§75): renders from the ONE shared [HomeViewModel]
 * listener set (rels + presence) and opens EXACTLY ONE single-document
 * listener — the counterpart's liveLocations doc — scoped to this screen
 * (stopped on dispose). Counterpart reads scale with their writes.
 */
@Composable
fun TrackingDetailScreen(
    user: FirebaseUser,
    vm: HomeViewModel,
    relId: String,
    onBack: () -> Unit,
) {
    val ui by vm.ui.collectAsState()
    val context = LocalContext.current
    val locale = appLocale()

    val rel = ui.rels.firstOrNull { it.id == relId }
    val counterpart = rel?.let {
        if (it.helperUid == user.uid) it.recipientEmail else it.helperEmail
    } ?: ""
    val name = Nicknames.displayName(context, relId, counterpart, counterpart)

    // ── the ONE screen-scoped live-location listener ──
    var live by remember(rel?.id, rel?.recipientDeviceId) {
        mutableStateOf<LiveLocationView?>(null)
    }
    var watchError by remember(rel?.id) { mutableStateOf<Exception?>(null) }
    val trackedUid = rel?.recipientUid
    val trackedDeviceId = rel?.recipientDeviceId
    DisposableEffect(trackedUid, trackedDeviceId) {
        var stop: (() -> Unit)? = null
        if (!trackedUid.isNullOrBlank() && !trackedDeviceId.isNullOrBlank()) {
            stop = watchLiveLocation(
                uid = trackedUid,
                deviceId = trackedDeviceId,
                onChange = { view ->
                    live = view
                    watchError = null
                },
                onError = { e -> watchError = e },
            )
        }
        onDispose { stop?.invoke() }
    }

    // Rows with a live freshness label tick at 30 s (M8 ticking isolation).
    val now = tickingNow(30_000L)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        BackTitleRow(title = stringResource(R.string.loc_details), onBack = onBack)

        if (rel == null) {
            // Honest not-found: revoked/expired/removed links disappear
            // from the listener; a stale deep link stays honest.
            EmptyState(
                icon = Icons.Filled.Phone,
                title = stringResource(R.string.fam_not_found),
                body = stringResource(R.string.fam_not_found_h),
            )
        } else {
            // ── connection status ──
            SectionCard(
                title = name,
                icon = Icons.Filled.LocationOn,
                trailing = { StatusBadge(rel.status) },
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    ) {
                        PresenceBadge(presence = ui.presence[rel.id], now = now)
                        KindChip(
                            kind = rel.kind,
                            guestExpiresAtMillis = rel.guestExpiresAtMillis,
                            now = now,
                        )
                    }
                    DetailRow(
                        label = stringResource(R.string.auth_email),
                        value = counterpart,
                    )
                }
            }

            // ── the shared position (the sharer decides; this side reads) ──
            SectionCard(title = stringResource(R.string.loc_title)) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    when {
                        watchError != null ->
                            Text(
                                text = describeFirestoreError(context, watchError!!),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        live == null ->
                            Text(
                                text = if (rel.status == "ACTIVE" && rel.guestLive) {
                                    stringResource(R.string.loc_never)
                                } else {
                                    stringResource(R.string.loc_status_unavailable)
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        else -> {
                            val view = live!!
                            // Freshness first (LIVE / STALE / OFFLINE from the
                            // doc's server-stamped updatedAt — §3), then the
                            // position facts as full sentences (existing keys,
                            // zero new strings), then the coordinates row.
                            Text(
                                text = locationFreshness(view.updatedAtMillis, now),
                                style = MaterialTheme.typography.titleSmall,
                                color = if (now - view.updatedAtMillis <= 90_000L) {
                                    MaterialTheme.colorScheme.tertiary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                            Text(
                                text = stringResource(
                                    R.string.loc_updated,
                                    Fmt.clockTime(view.updatedAtMillis, locale) +
                                        " · " + relTime(view.updatedAtMillis, now),
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                text = stringResource(
                                    R.string.loc_acc_m, view.accuracyMeters
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                text = String.format(
                                    Locale.US,
                                    "%.5f, %.5f",
                                    view.lat,
                                    view.lng,
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            // ── open in the maps app (geo: URI — the system chooser) ──
            val view = live
            if (view != null && rel.status == "ACTIVE" && rel.guestLive) {
                BigButton(
                    text = stringResource(R.string.loc_open_maps),
                    onClick = {
                        try {
                            val geo = Uri.parse(
                                "geo:${view.lat},${view.lng}?q=${view.lat},${view.lng}"
                            )
                            context.startActivity(Intent(Intent.ACTION_VIEW, geo))
                        } catch (_: Exception) {
                            // No maps app installed — the coordinates row
                            // above is the honest fallback.
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // ── who receives this phone's shares (read-only truth) ──
            Text(
                text = stringResource(R.string.loc_priv_receives, name),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** LIVE / STALE / OFFLINE from the doc's server-stamped updatedAt (§3). */
@Composable
private fun locationFreshness(updatedAtMillis: Long, now: Long): String {
    val age = now - updatedAtMillis
    return stringResource(
        when {
            updatedAtMillis <= 0L -> R.string.loc_fresh_offline
            age <= 90_000L -> R.string.loc_fresh_live
            age <= 10 * 60_000L -> R.string.loc_fresh_stale
            else -> R.string.loc_fresh_offline
        }
    )
}
