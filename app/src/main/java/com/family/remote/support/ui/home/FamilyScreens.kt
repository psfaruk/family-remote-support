// ═══════════════════════════════════════════════════════════════════════
// M4 (v0.32.0) — Family Overview + Member Detail nested routes
// (NAVIGATION_MAP §2): the paired-phone list and the old action
// bottom-sheet (AddDeviceTab §3/§4) promoted to real screens; PairTile
// moved verbatim. Existing-feature integration (pairing/sessions/
// revocation) preserved — same PairingRepo calls.
//
// Honest deltas from the verbatim ports (each a one-line edit; none
// changes the pairing/session/revocation behavior):
//   • PairTile: the two hardcoded chip words ("Family" / "Guest") became
//     stringResource(sess_family / sess_guest) — the M4 clean-key rule for
//     NEW screens (AddDeviceTab keeps its own literals until its wave).
//   • The old sheet's modal insets (horizontal 20dp / bottom 28dp) are
//     not kept: the route caller wraps this content in its scroll column,
//     which already applies the screen padding — keeping them would double
//     the edge insets.
//   • "selectedRelId = null" (sheet dismiss) became onBack() (route pop)
//     after a successful request-session / revoke / remove.
//   • Each screen owns ONE watchRelationships listener — the exact
//     AddDeviceTab §1 pattern plus the M3 real-states additions (loaded
//     flag → LoadingSkeleton; routed error → ErrorPanel with retry).
//     Zero new Firestore reads, zero new queries (NAVIGATION_MAP §5).
// ═══════════════════════════════════════════════════════════════════════
package com.family.remote.support.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.family.remote.support.R
import com.family.remote.support.REL_KIND_GUEST
import com.family.remote.support.RelationshipView
import com.family.remote.support.SessionException
import com.family.remote.support.SessionView
import com.family.remote.support.describeFirestoreError
import com.family.remote.support.localizedMessage
import com.family.remote.support.removeGuestRelationship
import com.family.remote.support.requestSession
import com.family.remote.support.revokeRelationship
import com.family.remote.support.ui.AppWarningColor
import com.family.remote.support.ui.components.AppListTile
import com.family.remote.support.ui.components.BackTitleRow
import com.family.remote.support.ui.components.BigButton
import com.family.remote.support.ui.components.EmptyState
import com.family.remote.support.ui.components.ErrorPanel
import com.family.remote.support.ui.components.LoadingSkeleton
import com.family.remote.support.ui.components.PresenceBadge
import com.family.remote.support.ui.components.WideOutlinedButton
import com.family.remote.support.ui.components.sessionStatus
import com.family.remote.support.watchRelationships
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ═══════════════════════════════════════════════════════════════════════
// v0.37.0 (dedupe wave) — ONE relationship watcher + 30 s freshness tick.
//
// The audit (duplicate cluster #2) found the watchRelationships listener +
// the conditional 30-second relNow loop in THREE screens: this file's
// FamilyOverviewScreen and MemberDetailScreen (byte-identical — the
// canonical shape: loaded flag + retryKey re-registration) and
// AddDeviceTab's older copy (no loaded flag). All three now share the
// holder below; PairingRepo.watchRelationships itself is untouched
// (owned by another wave) — only the per-screen copies are gone.
// ═══════════════════════════════════════════════════════════════════════

/**
 * State holder for the shared trusted-relationship watch: the live list,
 * the honest loaded flag, the routed error, and the 30 s freshness clock
 * ([relNow]) that keeps guest-window countdowns honest. NO UI inside —
 * each screen renders its own loading/empty/error states from these.
 */
internal class RelationshipWatchState {
    var relationships by mutableStateOf<List<RelationshipView>>(emptyList())
        private set
    var relationshipsLoaded by mutableStateOf(false)
        private set
    var relationshipsError by mutableStateOf<String?>(null)
        private set

    /** Ticks every 30 s while any guest link exists (see the watch helper). */
    var relNow by mutableStateOf(System.currentTimeMillis())
        private set

    internal fun onRelationships(list: List<RelationshipView>) {
        // Canonical shape: a snapshot clears any routed error and raises
        // the loaded flag.
        relationshipsError = null
        relationships = list
        relationshipsLoaded = true
    }

    internal fun onWatchError(message: String) {
        relationshipsError = message
        relationshipsLoaded = true
    }

    internal fun refreshNow() {
        relNow = System.currentTimeMillis()
    }
}

/**
 * v0.37.0 (dedupe wave) — remembers the ONE relationship watcher for
 * [user] (was 3 copies: FamilyOverviewScreen, MemberDetailScreen and
 * AddDeviceTab's older no-loaded-flag shape — audit duplicate cluster #2).
 * [retryKey] re-registers the listener on demand (the screens' retry
 * buttons increment it) — no new queries, no extra reads.
 *
 * The 30 s tick keeps the CANONICAL conditional shape: it only loops while
 * a guest link exists (a TickingBlock-style LaunchedEffect at 30 000 ms —
 * family-only accounts tick zero times, the Phase 10 perf rule).
 */
@Composable
internal fun rememberRelationshipWatch(
    user: FirebaseUser,
    retryKey: Int
): RelationshipWatchState {
    val context = LocalContext.current
    val state = remember(user.uid) { RelationshipWatchState() }

    // Live list of trusted relationships (both directions) — the SAME one
    // listener the old per-screen copies registered, plus the M3
    // real-states additions: a loaded flag for the honest skeleton and a
    // routed, retryable error (NAVIGATION_MAP §5).
    DisposableEffect(user.uid, retryKey) {
        val stopWatching = watchRelationships(
            user,
            onUpdate = { list -> state.onRelationships(list) },
            onError = { e -> state.onWatchError(describeFirestoreError(context, e)) }
        )
        onDispose { stopWatching() }
    }

    // Phase 10 — relNow ticks every 30 s while any guest link exists, so
    // the "বাকি সময়" countdowns stay honest without a per-second churn.
    LaunchedEffect(state.relationships) {
        if (state.relationships.any { it.kind == REL_KIND_GUEST }) {
            while (true) {
                state.refreshNow()
                delay(30_000)
            }
        } else {
            state.refreshNow()
        }
    }
    return state
}

/**
 * v0.14.0 — a paired phone as a chat-style tile: avatar + email + role +
 * Family/Guest badge (+ guest countdown), optional live presence underneath.
 * Tapping opens the action bottom-sheet.
 */
@Composable
internal fun PairTile(
    rel: RelationshipView,
    myUid: String,
    relNow: Long,
    onOpen: () -> Unit
) {
    val isGuest = rel.kind == REL_KIND_GUEST
    val iHelp = rel.helperUid == myUid
    val context = LocalContext.current
    val otherEmail =
        (if (iHelp) rel.recipientEmail else rel.helperEmail).ifBlank { "(no email)" }
    val guestOk = isGuest && rel.status == "ACTIVE" && rel.guestLive
    // Phase 11.6 — minutes until the guest relationship expires (for the
    // warning-colored chip when it is nearly over).
    val guestMinutesLeft = ((rel.guestExpiresAtMillis - relNow) / 60000L).coerceAtLeast(0)
    val chipText = when {
        !isGuest && rel.status == "ACTIVE" -> stringResource(R.string.sess_family)
            !isGuest -> stringResource(R.string.tile_family_status, sessionStatus(context, rel.status))
        guestOk -> {
            val remainingMin =
                ((rel.guestExpiresAtMillis - relNow) / 60000L).coerceAtLeast(0)
            when {
                rel.guestExpiresAtMillis <= 0L -> stringResource(R.string.sess_guest)
                remainingMin >= 60L -> stringResource(R.string.tile_guest_left_hm, remainingMin / 60L, remainingMin % 60L)
                else -> stringResource(R.string.tile_guest_left_m, remainingMin)
            }
        }
            else -> stringResource(R.string.tile_guest_status, sessionStatus(context, rel.status))
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        AppListTile(
            title = otherEmail,
            initials = otherEmail.take(1).ifBlank { "?" },
            subtitle = buildString {
                append(if (iHelp) stringResource(R.string.rel_you_help) else stringResource(R.string.rel_helps_you))
                if (rel.recipientDeviceModel.isNotBlank()) {
                    append(" · ${rel.recipientDeviceModel}")
                }
            },
            chip = chipText,
            chipContainer = when {
                rel.status != "ACTIVE" -> MaterialTheme.colorScheme.errorContainer
                // Phase 11.6 — guest countdown turns warning-colored when it
                // is nearly over (<15 min); the chip text still says the time.
                guestOk && guestMinutesLeft >= 1 && guestMinutesLeft <= 14 ->
                    AppWarningColor().copy(alpha = 0.18f)
                guestOk -> MaterialTheme.colorScheme.tertiaryContainer
                else -> MaterialTheme.colorScheme.primaryContainer
            },
            chipContent = when {
                rel.status != "ACTIVE" -> MaterialTheme.colorScheme.onErrorContainer
                guestOk && guestMinutesLeft >= 1 && guestMinutesLeft <= 14 ->
                    AppWarningColor()
                guestOk -> MaterialTheme.colorScheme.onTertiaryContainer
                else -> MaterialTheme.colorScheme.onPrimaryContainer
            },
            onClick = onOpen,
            trailingIcon = Icons.Filled.KeyboardArrowRight
        )
        if (iHelp && rel.recipientDeviceId.isNotBlank()) {
            Box(modifier = Modifier.padding(start = 56.dp)) {
                PresenceBadge(uid = rel.recipientUid, deviceId = rel.recipientDeviceId)
            }
        }
        if (isGuest && rel.status == "ACTIVE" && !rel.guestLive) {
            Text(
                text = stringResource(R.string.tile_guest_expired),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(start = 56.dp)
            )
        }
    }
}

/**
 * M4 (v0.32.0) — NAVIGATION_MAP §2 "home/family — Family Overview": the
 * trusted-device list (the old Phones tab's "যুক্ত ফোন" section,
 * AddDeviceTab §3) as a real route. The list itself is unchanged — PairTile
 * verbatim; one tap now navigates to the member detail route instead of
 * opening the action sheet. The caller wraps this content in its scroll
 * column (no verticalScroll here, same as every other route content).
 *
 * @param user         the signed-in account (the shell composes this route
 *                     authenticated only).
 * @param onOpenMember navigates to home/family/{relId} for the tapped tile.
 * @param onGoAddDevice the empty state's primary action — the same
 *                     "Add Device" hand-off the old empty state offered.
 */
@Composable
internal fun FamilyOverviewScreen(
    user: FirebaseUser,
    onOpenMember: (String) -> Unit,
    onGoAddDevice: () -> Unit
) {
    // v0.37.0 (dedupe wave, cluster #2) — the relationship listener + 30 s
    // tick moved to the shared rememberRelationshipWatch (was a
    // byte-identical copy with MemberDetailScreen's + the older AddDeviceTab
    // shape). retryKey still re-registers the listener on demand.
    var retryKey by remember { mutableStateOf(0) }
    val watch = rememberRelationshipWatch(user, retryKey)

    Text(
        text = stringResource(R.string.fam_title),
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold
    )

    if (!watch.relationshipsLoaded) {
        LoadingSkeleton(rows = 3)
    } else if (watch.relationshipsError != null) {
        ErrorPanel(message = watch.relationshipsError!!, onRetry = { retryKey++ })
    } else if (watch.relationships.isEmpty()) {
        EmptyState(
            icon = Icons.Filled.Phone,
            title = stringResource(R.string.fam_empty_t),
            hint = stringResource(R.string.fam_empty_h),
            illustrationRes = R.drawable.ic_empty_devices,
            actionLabel = stringResource(R.string.nav_add_device),
            onAction = onGoAddDevice
        )
    } else {
        Column {
            watch.relationships.forEachIndexed { index, rel ->
                if (index > 0) HorizontalDivider()
                PairTile(
                    rel = rel,
                    myUid = user.uid,
                    relNow = watch.relNow,
                    onOpen = { onOpenMember(rel.id) }
                )
            }
        }
    }
}

/**
 * M4 (v0.32.0) — NAVIGATION_MAP §2 "home/family/{relId} — Member Details":
 * the old paired-phone ACTION BOTTOM-SHEET (AddDeviceTab §4) promoted to a
 * real screen. The sheet's content is ported verbatim (role row, guest
 * window, presence, request-session / revoke / remove — the same
 * PairingRepo/SessionRepo calls); only the chrome changed: a back row
 * instead of the drag handle, real loading/error/not-found states, and
 * successful actions pop the route instead of dismissing the sheet.
 *
 * @param user        the signed-in account (helper/recipient direction of
 *                    the relationship is derived from it, exactly as the
 *                    old sheet did).
 * @param relId       the relationship doc id from the route argument.
 * @param openSession the shell's already-watched REQUESTED/ACTIVE session
 *                    (the same value the old tab used — no new listener).
 * @param onBack      pops the route (back row, not-found escape, and the
 *                    success path of request/revoke/remove).
 */
@Composable
internal fun MemberDetailScreen(
    user: FirebaseUser,
    relId: String,
    openSession: SessionView?,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // v0.37.0 (dedupe wave, cluster #2) — the relationship listener + 30 s
    // tick moved to the shared rememberRelationshipWatch (was a
    // byte-identical copy with FamilyOverviewScreen's + the older
    // AddDeviceTab shape). retryKey still re-registers on demand.
    var retryKey by remember { mutableStateOf(0) }
    val watch = rememberRelationshipWatch(user, retryKey)
    val relationships = watch.relationships
    val relNow = watch.relNow

    // ---------- action state (ported from the old tab/sheet) ----------
    var sessionBusy by remember { mutableStateOf(false) }
    var sessionMessage by remember { mutableStateOf<String?>(null) }
    // was `pairingError` in the old tab — local to this screen now.
    var actionMessage by remember { mutableStateOf<String?>(null) }
    var revokingId by remember { mutableStateOf<String?>(null) }

    // v0.8.2 — helper asks for a support session (ported from AddDeviceTab
    // §2 startSessionFor; same requestSession call, same messages). The old
    // code closed the sheet on success (selectedRelId = null); a real
    // screen pops the route instead.
    val startSessionFor: (RelationshipView) -> Unit = { rel ->
        scope.launch {
            sessionBusy = true
            sessionMessage = null
            try {
                requestSession(user, rel)
                sessionMessage =
                        context.getString(R.string.ph_req_sent, rel.recipientEmail)
                onBack()
            } catch (e: SessionException) {
                sessionMessage = e.localizedMessage(context)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                sessionMessage = context.getString(R.string.err_send_failed, describeFirestoreError(context, e))
            } finally {
                sessionBusy = false
            }
        }
    }

    val selectedRel = relationships.firstOrNull { it.id == relId }

    if (!watch.relationshipsLoaded) {
        LoadingSkeleton(rows = 3)
    } else if (watch.relationshipsError != null) {
        ErrorPanel(message = watch.relationshipsError!!, onRetry = { retryKey++ })
    } else if (selectedRel == null) {
        // Honest not-found: the link was revoked/removed from the other
        // side (or the route argument is stale) — say so, offer the way back.
        EmptyState(
            icon = Icons.Filled.Phone,
            title = stringResource(R.string.fam_not_found),
            hint = stringResource(R.string.fam_not_found_h)
        )
        WideOutlinedButton(onClick = onBack) {
            Text(stringResource(R.string.cd_back))
        }
    } else {
        // ---------- detail body: the old action-sheet content ----------
        val isGuest = selectedRel.kind == REL_KIND_GUEST
        val iHelp = selectedRel.helperUid == user.uid
        val otherEmail =
            (if (iHelp) selectedRel.recipientEmail else selectedRel.helperEmail)
                .ifBlank { "(no email)" }
        Column(
            modifier = Modifier.fillMaxWidth(),
            // (the sheet's own horizontal-20dp/bottom-28dp modal insets are
            // dropped — the caller's scroll column already pads the screen)
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // M4 — the sheet's drag handle became a real back row
            // (v0.37.0 dedupe wave, cluster #5: the inline copy became the
            // design system's components.BackTitleRow).
            BackTitleRow(title = stringResource(R.string.fam_title), onBack = onBack)
            // v0.8.2 — the sent-ask note / action failures, ported as the
            // old tab showed them (plain bodySmall; failures in error color).
            sessionMessage?.let {
                Text(text = it, style = MaterialTheme.typography.bodySmall)
            }
            actionMessage?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            Text(
                text = otherEmail,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = buildString {
                    append(if (iHelp) stringResource(R.string.rel_you_help) else stringResource(R.string.rel_helps_you))
                    if (selectedRel.recipientDeviceModel.isNotBlank()) {
                        append(" · ${selectedRel.recipientDeviceModel}")
                    }
                    append(" · " + sessionStatus(context, selectedRel.status))
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (isGuest) {
                val remainingMin =
                    ((selectedRel.guestExpiresAtMillis - relNow) / 60000L).coerceAtLeast(0)
                Text(
                    text = when {
                        selectedRel.status != "ACTIVE" -> stringResource(R.string.rel_guest_expired)
                        selectedRel.guestExpiresAtMillis <= 0L -> stringResource(R.string.rel_guest_label)
                        remainingMin >= 60L ->
                            stringResource(R.string.rel_guest_left_hm, remainingMin / 60L, remainingMin % 60L)
                        else -> stringResource(R.string.rel_guest_left_m, remainingMin)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (selectedRel.status == "ACTIVE" && remainingMin > 0L) {
                        MaterialTheme.colorScheme.tertiary
                    } else {
                        MaterialTheme.colorScheme.error
                    }
                )
            }
            if (iHelp && selectedRel.recipientDeviceId.isNotBlank()) {
                PresenceBadge(
                    uid = selectedRel.recipientUid,
                    deviceId = selectedRel.recipientDeviceId
                )
            }
            HorizontalDivider()
            // Phase 12 — the role is a first-class, visible thing.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = if (isGuest) {
                        Icons.Filled.DateRange
                    } else {
                        Icons.Filled.Home
                    },
                    contentDescription = null,
                    tint = if (isGuest) {
                        AppWarningColor()
                    } else {
                        MaterialTheme.colorScheme.tertiary
                    },
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = if (isGuest) {
                        stringResource(R.string.rel_role_guest)
                    } else {
                        stringResource(R.string.rel_role_family)
                    },
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isGuest) {
                        AppWarningColor()
                    } else {
                        MaterialTheme.colorScheme.tertiary
                    }
                )
            }
            if (selectedRel.status == "ACTIVE" && iHelp && !selectedRel.guestLive) {
                Text(
                    text = stringResource(R.string.rel_guest_over_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            if (selectedRel.status == "ACTIVE" && iHelp && selectedRel.guestLive) {
                BigButton(
                    enabled = !sessionBusy && openSession == null,
                    onClick = { startSessionFor(selectedRel) }
                ) {
                    Text(stringResource(R.string.btn_request_session))
                }
                if (openSession != null) {
                    Text(
                        text = stringResource(R.string.rel_session_running),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (!isGuest && selectedRel.status == "ACTIVE") {
                OutlinedButton(
                    enabled = revokingId != selectedRel.id,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                    onClick = {
                        revokingId = selectedRel.id
                        scope.launch {
                            try {
                                revokeRelationship(selectedRel.id)
                                onBack()
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                actionMessage =
                                    context.getString(R.string.err_revoke_failed, describeFirestoreError(context, e))
                            } finally {
                                revokingId = null
                            }
                        }
                    }
                ) {
                    Text(stringResource(R.string.btn_revoke))
                }
            }
            // Phase 10 — a guest link can be REMOVED entirely (the doc
            // is deleted; afterwards a fresh family pairing may reuse
            // the pair). Family links have no such button on purpose.
            if (isGuest) {
                OutlinedButton(
                    enabled = revokingId != selectedRel.id,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                    onClick = {
                        revokingId = selectedRel.id
                        scope.launch {
                            try {
                                removeGuestRelationship(selectedRel.id)
                                onBack()
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                actionMessage =
                                    context.getString(R.string.err_remove_failed, describeFirestoreError(context, e))
                            } finally {
                                revokingId = null
                            }
                        }
                    }
                ) {
                    Text(stringResource(R.string.btn_remove))
                }
            }
        }
    }
}
