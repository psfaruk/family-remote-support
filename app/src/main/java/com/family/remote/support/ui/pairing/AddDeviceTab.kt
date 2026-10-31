// ═══════════════════════════════════════════════════════════════════════
// Add Device tab (pairing) — M3 (v0.31.0) verbatim extraction from
// MainActivity.kt (FRONTEND_ARCHITECTURE §3/§10, W2).
//
// The composable/object bodies below are copied char-for-char from
// MainActivity.kt (PairingCard, PairingSections, ScannedCode,
// ScannedRoleSheet, ScannedRoleBullet, PairOptionRow, PairTile,
// PairingUiBus). Behavior-identical; the ONLY edits are:
//   • package + import block (symbols that stay behind now come from
//     their own files/packages),
//   • visibility: private → internal on PairingSections, ScannedRoleSheet,
//     ScannedRoleBullet, PairOptionRow, PairTile, PairingUiBus
//     (PairingCard stays public; ScannedCode stays private — used only here).
// ═══════════════════════════════════════════════════════════════════════
package com.family.remote.support.ui.pairing

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.family.remote.support.ui.components.BigButton
import com.family.remote.support.InviteCode
import com.family.remote.support.QrBadge
import com.family.remote.support.QrScanDialog
import com.family.remote.support.REL_KIND_FAMILY
import com.family.remote.support.REL_KIND_GUEST
import com.family.remote.support.RedemptionException
import com.family.remote.support.RelationshipView
import com.family.remote.support.R
import com.family.remote.support.ScreenSharePanel
import com.family.remote.support.SessionException
import com.family.remote.support.SessionView
import com.family.remote.support.buildQrShareIntent
import com.family.remote.support.cancelInvitation
import com.family.remote.support.cancelSession
import com.family.remote.support.copyToClipboard
import com.family.remote.support.createInvitation
import com.family.remote.support.describeFirestoreError
import com.family.remote.support.denySession
import com.family.remote.support.endSession
import com.family.remote.support.expireGuestRelationship
import com.family.remote.support.expireSession
import com.family.remote.support.getSession
import com.family.remote.support.grantSession
import com.family.remote.support.isStaleOpen
import com.family.remote.support.mmss
import com.family.remote.support.qrBitmap
import com.family.remote.support.redeemCode
import com.family.remote.support.removeGuestRelationship
import com.family.remote.support.requestSession
import com.family.remote.support.revokeRelationship
import com.family.remote.support.saveQrToDownloads
import com.family.remote.support.stableDeviceId
import com.family.remote.support.watchInvitation
import com.family.remote.support.watchRelationships
import com.family.remote.support.watchSessions
import com.family.remote.support.ui.AppWarningColor
import com.family.remote.support.ui.components.AppListTile
import com.family.remote.support.ui.components.EmptyState
import com.family.remote.support.ui.components.PresenceBadge
import com.family.remote.support.ui.components.SectionCard
import com.family.remote.support.ui.components.StatusBadge
import com.family.remote.support.ui.components.WideOutlinedButton
import com.family.remote.support.ui.components.sessionBadgeKind
import com.family.remote.support.ui.components.sessionStatus
import com.family.remote.support.ui.home.HomeConsentCard
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun PairingCard(
    signedInUser: FirebaseUser?,
    showPairSheet: Boolean,
    onDismissPairSheet: () -> Unit,
    onOpenPairSheet: () -> Unit
) {
    if (signedInUser == null) {
            SectionCard(title = stringResource(R.string.phone_title), icon = Icons.Filled.Phone) {
            EmptyState(
                icon = Icons.Filled.Person,
                    title = stringResource(R.string.phone_signin_first),
                    hint = stringResource(R.string.phone_signin_hint),
            )
        }
    } else {
        PairingSections(
            user = signedInUser,
            showPairSheet = showPairSheet,
            onDismissPairSheet = onDismissPairSheet,
            onOpenPairSheet = onOpenPairSheet
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PairingSections(
    user: FirebaseUser,
    showPairSheet: Boolean,
    onDismissPairSheet: () -> Unit,
    onOpenPairSheet: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var invite by PairingUiBus.inviteState
    var inviteKind by PairingUiBus.inviteKindState
    var inviteStatus by remember { mutableStateOf<String?>(null) }
    var claimerEmail by remember { mutableStateOf<String?>(null) }
    var inviteSyncError by remember { mutableStateOf<String?>(null) }
    var nowMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    var creatingKind by remember { mutableStateOf<String?>(null) }
    var pairingError by remember { mutableStateOf<String?>(null) }
    var codeInput by remember { mutableStateOf("") }
    var redeeming by remember { mutableStateOf(false) }
    var redeemMessage by remember { mutableStateOf<String?>(null) }
    var relationships by remember { mutableStateOf<List<RelationshipView>>(emptyList()) }
    var relationshipsSyncError by remember { mutableStateOf<String?>(null) }
    var revokingId by remember { mutableStateOf<String?>(null) }
    var showScanner by remember { mutableStateOf(false) }
    // v0.14.0 — which paired phone's action sheet is open (by rel id).
    var selectedRelId by remember { mutableStateOf<String?>(null) }
    // Phase 12 — the code waiting in the role-setup sheet (scan → preview →
    // confirm → redeem → success, one automatic journey).
    var pendingScan by remember { mutableStateOf<ScannedCode?>(null) }
    // Phase 12 — the role of the LAST successful redeem (drives the sheet's
    // success view; null while nothing redeemed yet).
    var redeemedKind by remember { mutableStateOf<String?>(null) }

    // Phase 6 — support session state.
    var sessions by remember { mutableStateOf<List<SessionView>>(emptyList()) }
    var sessionSyncError by remember { mutableStateOf<String?>(null) }
    var sessionBusy by remember { mutableStateOf(false) }
    var sessionMessage by remember { mutableStateOf<String?>(null) }
    var sessionNow by remember { mutableStateOf(System.currentTimeMillis()) }
    // The last session that closed (ended/denied/cancelled/expired) — kept in
    // memory for the "Last session:" line. The live watches only track OPEN
    // sessions (index-free + rules-provable), so when one disappears from the
    // open set we fetch its final state once and remember it here.
    var lastFinishedSession by remember { mutableStateOf<SessionView?>(null) }
    var lastOpenSessionId by remember { mutableStateOf<String?>(null) }

    // Ticks the countdown once per second while an invitation exists.
    LaunchedEffect(invite?.invitationId) {
        if (invite != null) {
            while (true) {
                nowMillis = System.currentTimeMillis()
                delay(1000)
            }
        }
    }

    // Live status of the invitation we created (waiting → paired/cancelled).
    DisposableEffect(invite?.invitationId) {
        val registration = invite?.invitationId?.let { id ->
            watchInvitation(
                id,
                onChange = { view ->
                    inviteSyncError = null
                    inviteStatus = view?.status
                    claimerEmail = view?.claimedEmail
                },
                onError = { e -> inviteSyncError = describeFirestoreError(context, e) }
            )
        }
        onDispose { registration?.remove() }
    }

    // Live list of trusted relationships (both directions).
    DisposableEffect(user.uid) {
        val stopWatching = watchRelationships(
            user,
            onUpdate = { relationshipsSyncError = null; relationships = it },
            onError = { e -> relationshipsSyncError = describeFirestoreError(context, e) }
        )
        onDispose { stopWatching() }
    }

    // Phase 10 — relNow ticks every 30 s while any guest link exists, so
    // the "বাকি সময়" countdowns stay honest without a per-second churn.
    var relNow by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(relationships) {
        if (relationships.any { it.kind == REL_KIND_GUEST }) {
            while (true) {
                relNow = System.currentTimeMillis()
                delay(30_000)
            }
        } else {
            relNow = System.currentTimeMillis()
        }
    }
    // Phase 10 — lazy cleanup: the first app that notices an over-time
    // guest link writes ACTIVE -> EXPIRED (the rules allow exactly this).
    LaunchedEffect(relationships, relNow) {
        relationships
            .filter {
                it.kind == REL_KIND_GUEST && it.status == "ACTIVE" &&
                    it.guestExpiresAtMillis > 0L && relNow > it.guestExpiresAtMillis + 4000
            }
            .forEach { rel ->
                try {
                    expireGuestRelationship(rel.id)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // best effort — the window check elsewhere is the real gate
                }
            }
    }

    // Phase 6 — live list of this account's support sessions (both roles).
    DisposableEffect(user.uid) {
        val stopWatching = watchSessions(
            user,
            onUpdate = { sessionSyncError = null; sessions = it },
            onError = { e -> sessionSyncError = describeFirestoreError(context, e) }
        )
        onDispose { stopWatching() }
    }

    val openSession = sessions.firstOrNull {
        it.status == "REQUESTED" || it.status == "ACTIVE"
    }

    // When a session leaves the open set it has reached a final state — fetch
    // it once for the history line. Fires only on the open→closed transition.
    LaunchedEffect(sessions) {
        val currentOpenId = openSession?.id
        val prevOpenId = lastOpenSessionId
        if (prevOpenId != null && prevOpenId != currentOpenId) {
            try {
                lastFinishedSession = getSession(prevOpenId)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // best effort — the history line is informational only
            }
        }
        lastOpenSessionId = currentOpenId
    }

    // v0.21.0 (audit F1): the per-session viewer-state reset AND the
    // "no open session → stop everything" teardown both moved to the
    // HomeScreen ROOT (they used to live here, so they only ran while the
    // Phones tab was composed — and the teardown fired on the empty
    // pre-snapshot loading state, killing live shares on tab switches).

    // Ticks once per second while a session is open so countdowns stay live.
    LaunchedEffect(openSession?.id, openSession?.status) {
        if (openSession != null) {
            while (true) {
                sessionNow = System.currentTimeMillis()
                delay(1000)
            }
        } else {
            sessionNow = System.currentTimeMillis()
        }
    }

    // Lazy server-less expiry: a stale ask turns EXPIRED, an over-time active
    // session turns ENDED("timeout"). Whichever app notices first does it.
    // v0.22.0 (Phase 18.1): sweeps EVERY open session, not just the newest —
    // repeated reboots used to leave older zombies behind forever (they filled
    // the capped watch window and could push a fresh ask out of it). The same
    // isStaleOpen(+4 s grace) rule the request-side self-heal applies.
    LaunchedEffect(sessions.size, openSession?.id, openSession?.status, sessionNow) {
        if (sessions.isEmpty()) return@LaunchedEffect
        sessions.filter { it.isStaleOpen(sessionNow) }.forEach { s ->
            try {
                if (s.status == "REQUESTED") expireSession(s.id)
                else endSession(s.id, user.uid, byTimeout = true)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // best effort — the ROOT sweep and the server sweep also
                // perform exactly these transitions
            }
        }
    }

    // v0.21.0 (audit F1): the session-close teardown moved to the
    // HomeScreen root (loaded-guarded) — see the comment there.

    // v0.8.2 — the helper's "Request sent…" line is sticky in-memory state;
    // once the session turns ACTIVE on the helper's side it is stale (the
    // active card already says everything), so drop it instead of showing a
    // stale ask-line under the live card.
    LaunchedEffect(openSession?.id, openSession?.status) {
        val s = openSession
        if (s != null && s.status == "ACTIVE" && s.helperUid == user.uid &&
            sessionMessage?.startsWith("Request sent") == true
        ) {
            sessionMessage = null
        }
    }

    val startSessionFor: (RelationshipView) -> Unit = { rel ->
        scope.launch {
            sessionBusy = true
            sessionMessage = null
            try {
                requestSession(user, rel)
                sessionMessage =
                        context.getString(R.string.ph_req_sent, rel.recipientEmail)
                selectedRelId = null
            } catch (e: SessionException) {
                sessionMessage = e.message
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                sessionMessage = context.getString(R.string.err_send_failed, describeFirestoreError(context, e))
            } finally {
                sessionBusy = false
            }
        }
    }

    val answerSession: (SessionView, Boolean) -> Unit = { s, allow ->
        scope.launch {
            sessionBusy = true
            sessionMessage = null
            try {
                if (allow) {
                    grantSession(s.id, user.uid)
                    sessionMessage =
                            context.getString(R.string.ph_allowed_15m)
                } else {
                    denySession(s.id, user.uid)
                        sessionMessage = context.getString(R.string.ph_denied_short)
                }
            } catch (e: SessionException) {
                sessionMessage = e.message
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                sessionMessage = context.getString(R.string.err_answer_failed, describeFirestoreError(context, e))
            } finally {
                sessionBusy = false
            }
        }
    }

    val finishSession: (SessionView) -> Unit = { s ->
        scope.launch {
            sessionBusy = true
            try {
                endSession(s.id, user.uid)
                    sessionMessage = context.getString(R.string.ph_session_ended)
            } catch (e: SessionException) {
                sessionMessage = e.message
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                sessionMessage = context.getString(R.string.err_end_failed, describeFirestoreError(context, e))
            } finally {
                sessionBusy = false
            }
        }
    }

    // Phase 12 — one shared redeem path used by BOTH the typed-code button
    // and the scanned role-setup sheet.
    val doRedeem: (String) -> Unit = { rawCode ->
        scope.launch {
            redeeming = true
            redeemMessage = null
            redeemedKind = null
            try {
                    val rel = redeemCode(context, user, rawCode)
                    redeemedKind = rel.kind
                    redeemMessage = if (rel.kind == REL_KIND_GUEST) {
                        context.getString(R.string.ph_guest_added, rel.recipientEmail, rel.recipientDeviceModel)
                    } else {
                        context.getString(R.string.ph_paired_ok, rel.recipientEmail, rel.recipientDeviceModel)
                    }
                codeInput = ""
            } catch (e: RedemptionException) {
                redeemMessage = e.message
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                    redeemMessage = context.getString(R.string.ph_pairing_failed, describeFirestoreError(context, e))
            } finally {
                redeeming = false
            }
        }
    }

    val currentInvite = invite
    val expired = currentInvite != null &&
        (nowMillis >= currentInvite.expiresAtMillis ||
            (inviteStatus != null && inviteStatus != "PENDING"))
    val showCode = currentInvite != null && !expired

    val createCode: (String) -> Unit = { kind ->
        scope.launch {
            creatingKind = kind
            pairingError = null
            try {
                val deviceId = stableDeviceId(context)
                invite = createInvitation(user, deviceId, Build.MODEL, kind)
                inviteKind = kind
                inviteStatus = "PENDING"
                claimerEmail = null
                inviteSyncError = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                pairingError = context.getString(R.string.err_create_failed, describeFirestoreError(context, e))
            } finally {
                creatingKind = null
            }
        }
    }

    // ---------- ১. লাইভ সেশন: অনুমতি-কার্ড / অপেক্ষা / চলমান ----------
    val currentSession = openSession
    when {
        currentSession != null && currentSession.status == "REQUESTED" &&
            currentSession.recipientUid == user.uid -> {
            // This phone was asked — the prominent consent card.
            HomeConsentCard(session = currentSession, user = user)
        }
        currentSession != null && currentSession.status == "REQUESTED" -> {
            // Helper waiting for the answer.
            val secondsLeft =
                ((currentSession.respondDeadlineMillis - sessionNow) / 1000).coerceAtLeast(0)
            Card {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Send,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                                text = stringResource(R.string.ph_req_to, currentSession.recipientEmail),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = mmss(secondsLeft),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier
                                .background(
                                    MaterialTheme.colorScheme.secondaryContainer,
                                    RoundedCornerShape(999.dp)
                                )
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                    Text(
                            text = stringResource(R.string.ph_wait_allow),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    WideOutlinedButton(
                        enabled = !sessionBusy,
                        onClick = {
                            scope.launch {
                                sessionBusy = true
                                try {
                                    cancelSession(currentSession.id, user.uid)
                                        sessionMessage = context.getString(R.string.ph_cancelled_msg)
                                } catch (e: SessionException) {
                                    sessionMessage = e.message
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    sessionMessage =
                                        context.getString(R.string.err_cancel_failed, describeFirestoreError(context, e))
                                } finally {
                                    sessionBusy = false
                                }
                            }
                        }
                    ) {
                            Text(stringResource(R.string.btn_cancel_request))
                    }
                }
            }
        }
        currentSession != null && currentSession.status == "ACTIVE" -> {
            // Live session on either side.
            val secondsLeft =
                ((currentSession.sessionEndsAtMillis - sessionNow) / 1000).coerceAtLeast(0)
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = if (currentSession.helperUid == user.uid) {
                                    stringResource(R.string.ph_active_ihelp, currentSession.recipientEmail)
                            } else {
                                    stringResource(R.string.ph_active_helping_you, currentSession.helperEmail)
                            },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = mmss(secondsLeft),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier
                                .background(
                                    MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.12f),
                                    RoundedCornerShape(999.dp)
                                )
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                    Text(
                            text = stringResource(R.string.ph_auto_end_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                    )
                    ScreenSharePanel(
                        isHelper = currentSession.helperUid == user.uid,
                        sessionId = currentSession.id,
                        myUid = user.uid,
                        otherUid = if (currentSession.helperUid == user.uid) {
                            currentSession.recipientUid
                        } else {
                            currentSession.helperUid
                        }
                    )
                    WideOutlinedButton(
                        enabled = !sessionBusy,
                        onClick = { finishSession(currentSession) }
                    ) {
                            Text(stringResource(R.string.btn_end_session))
                    }
                }
            }
        }
        else -> {
            lastFinishedSession?.let { lastFinished ->
                    val byWhom = when (lastFinished.endedBy) {
                    null, "" -> ""
                    "timeout" -> context.getString(R.string.ended_auto)
                    lastFinished.helperUid -> context.getString(R.string.ended_by_helper)
                    lastFinished.recipientUid -> context.getString(R.string.ended_by_recipient)
                    else -> ""
                }
                Text(
                        text = stringResource(R.string.ph_last_session_line, sessionStatus(context, lastFinished.status)) + byWhom,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    if (sessionSyncError != null) {
        Text(
            text = sessionSyncError!!,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error
        )
    }
    sessionMessage?.let {
        Text(text = it, style = MaterialTheme.typography.bodySmall)
    }
    pairingError?.let {
        Text(
            text = it,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error
        )
    }

    // ---------- ১b. ফোন যুক্ত করুন — অ্যাপের প্রধান অ্যাকশন, সবসময় দৃশ্যমান ----------
    // v0.18.1 (Phase 11.8) discoverability fix: the helper-side flow (QR scan
    // + code import) lived at the very bottom of this page in v0.13.0 and only
    // inside the FAB sheet from v0.14.0 — the app's PRIMARY action was
    // effectively invisible (Owner: "QR scanner ও কোড ইমপোর্ট দেখা যাচ্ছে না").
    // Now both directions sit in one permanent, prominent card near the top:
    // scan (helper) or create/import (receiver) — one tap each.
    SectionCard(
            title = stringResource(R.string.ph_two_ways_title),
        icon = Icons.Filled.Phone,
            infoText = stringResource(R.string.ph_two_ways_info),
    ) {
        BigButton(
            onClick = { showScanner = true }
        ) {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
                Text("  " + stringResource(R.string.ph_btn_scan))
        }
        WideOutlinedButton(
            onClick = onOpenPairSheet
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
                Text("  " + stringResource(R.string.ph_btn_import))
        }
        Text(
                text = stringResource(R.string.ph_role_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    // v0.18.1 — the tab-level scan entry. Same dialog and same Phase-12
    // role-setup journey as the in-sheet scan button; the dialog renders here
    // only while the sheet is closed, so the two entries can never double-
    // render. State (showScanner/codeInput/pendingScan) is shared.
    if (showScanner && !showPairSheet) {
        QrScanDialog(
            onCode = { code, kindHint ->
                codeInput = code
                redeemMessage = null
                redeemedKind = null
                showScanner = false
                pendingScan = ScannedCode(code, kindHint)
            },
            onDismiss = { showScanner = false }
        )
    }

    // ---------- ২. চলমান পেয়ারিং-কোড কার্ড (dropdown-সহ) ----------
    if (showCode && currentInvite != null) {
        Card {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // QR + save pipeline states MUST precede the dropdown that
                // uses them (Kotlin order-of-declaration inside a block).
                // Phase 12 — the QR now carries its role badge in the center
                // (👪 Family / ⏳ Guest) and the card accents follow the role.
                val kindIsGuest = inviteKind == REL_KIND_GUEST
                val kindColor = if (kindIsGuest) {
                    AppWarningColor()
                } else {
                    MaterialTheme.colorScheme.tertiary
                }
                val qr = remember(currentInvite.code, inviteKind) {
                    qrBitmap(currentInvite.qrPayload, badge = QrBadge.ofKind(inviteKind))
                }
                var saveBusy by remember(currentInvite.invitationId) { mutableStateOf(false) }
                // v0.21.0 (audit): a monotonic trigger counter — the old Boolean
                // flag was reset INSIDE the LaunchedEffect keyed on it, so the
                // effect cancelled itself mid-IO and the "Saved ✓" toast (the
                // exact Owner complaint about missing save feedback) almost
                // never appeared; repeated taps then stacked duplicate PNGs.
                var saveTrigger by remember(currentInvite.invitationId) { mutableStateOf(0) }
                var saveFeedback by remember(currentInvite.invitationId) {
                    mutableStateOf<String?>(null)
                }

                val storagePermissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { granted ->
                    if (granted) {
                        saveTrigger += 1
                    } else {
                        saveFeedback =
                            "Storage permission is needed to save the QR image."
                    }
                }

                LaunchedEffect(saveTrigger) {
                    if (saveTrigger == 0) return@LaunchedEffect
                    val bitmap = qr
                    if (bitmap == null) return@LaunchedEffect
                    saveBusy = true
                    val savedNote = withContext(Dispatchers.IO) {
                        val fileName = "pairing-code-${currentInvite.invitationId}.png"
                        saveQrToDownloads(context, bitmap, fileName)
                    }
                    saveBusy = false
                    // Phase 16 — the Owner asked for self-confirming actions:
                    // a saved QR now TELLS you it was saved (toast), plus the
                    // inline note stays on the card.
                    if (savedNote != null) {
                        saveFeedback = "Saved ✓ $savedNote"
                        Toast.makeText(
                            context,
                            context.getString(R.string.toast_saved, savedNote),
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        saveFeedback = "Could not save the QR image."
                        Toast.makeText(
                            context,
                            context.getString(R.string.toast_save_failed),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = if (inviteKind == REL_KIND_GUEST) {
                            Icons.Filled.DateRange
                        } else {
                            Icons.Filled.AccountCircle
                        },
                        contentDescription = null,
                        tint = kindColor,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = if (inviteKind == REL_KIND_GUEST) {
                                stringResource(R.string.ph_guest_running)
                        } else {
                                stringResource(R.string.ph_family_running)
                        },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    // v0.14.0 — the invite actions moved into a dropdown:
                    // কপি / QR সেভ / শেয়ার।
                    var menuOpen by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(
                                imageVector = Icons.Filled.MoreVert,
                                    contentDescription = stringResource(R.string.cd_more),
                            )
                        }
                        DropdownMenu(
                            expanded = menuOpen,
                            onDismissRequest = { menuOpen = false }
                        ) {
                            DropdownMenuItem(
                                    text = { Text(stringResource(R.string.btn_copy)) },
                                onClick = {
                                    menuOpen = false
                                    val ok = copyToClipboard(
                                        context, "Pairing code", currentInvite.code
                                    )
                                    // Phase 16 — copy now confirms itself
                                    // (Owner: "কপি হয়েছে দেখাতে হবে")।
                                    if (ok) {
                                        Toast.makeText(
                                            context,
                                            context.getString(R.string.toast_copied),
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            )
                            DropdownMenuItem(
                                    text = { Text(stringResource(R.string.btn_save_qr)) },
                                onClick = {
                                    menuOpen = false
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ||
                                        ContextCompat.checkSelfPermission(
                                            context,
                                            Manifest.permission.WRITE_EXTERNAL_STORAGE
                                        ) == PackageManager.PERMISSION_GRANTED
                                    ) {
                                        saveTrigger += 1
                                    } else {
                                        storagePermissionLauncher.launch(
                                            Manifest.permission.WRITE_EXTERNAL_STORAGE
                                        )
                                    }
                                }
                            )
                            DropdownMenuItem(
                                    text = { Text(stringResource(R.string.btn_share)) },
                                onClick = {
                                    menuOpen = false
                                    val q = qr
                                    if (q == null) {
                                        saveFeedback =
                                            "Could not prepare the QR image for sharing."
                                        Toast.makeText(
                                            context,
                                            context.getString(R.string.toast_share_prep_failed),
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    } else {
                                        val intent = buildQrShareIntent(
                                            context, q, currentInvite.code
                                        )
                                        if (intent != null) {
                                            try {
                                                context.startActivity(intent)
                                            } catch (_: Exception) {
                                                saveFeedback = "No app available for sharing."
                                                Toast.makeText(
                                                    context,
                                                    context.getString(R.string.toast_share_no_app),
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        } else {
                                            saveFeedback =
                                                "Could not prepare the QR image for sharing."
                                            Toast.makeText(
                                                context,
                                                context.getString(R.string.toast_share_prep_failed),
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
                Text(
                    text = currentInvite.code,
                    style = MaterialTheme.typography.headlineSmall,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
                Text(
                        text = stringResource(R.string.ph_family_only_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
                val totalSeconds = (currentInvite.expiresAtMillis - nowMillis) / 1000
                Text(
                        text = stringResource(R.string.ph_countdown, totalSeconds / 60, (totalSeconds % 60).toString().padStart(2, '0')),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    // Phase 11.6 — the last 30 s also turns warning-colored
                    // (color + the words, never color alone — docs §4.3).
                    color = if (totalSeconds >= 1 && totalSeconds <= 30) {
                        AppWarningColor()
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
                if (qr != null) {
                    // Phase 11.6 — big QR on a white plate: scanning stays
                    // reliable in BOTH themes (docs §4.4).
                    Column(
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .background(Color.White, RoundedCornerShape(16.dp))
                            .padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Image(
                            bitmap = qr.asImageBitmap(),
                            contentDescription = "Pairing QR code",
                            modifier = Modifier.size(200.dp)
                        )
                    }
                }

                saveFeedback?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                }
                // Phase 11.6 — invite state as a badge (color + icon + word).
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    when (inviteStatus) {
                        "PENDING" -> StatusBadge(
                                label = stringResource(R.string.ph_status_waiting),
                            kind = "info"
                        )
                        "PAIRED" -> StatusBadge(
                                label = stringResource(R.string.ph_status_paired),
                            kind = "success"
                        )
                        else -> {}
                    }
                }
                if (inviteStatus == "PAIRED") {
                    Text(
                            text = stringResource(R.string.ph_claimed_prefix, claimerEmail ?: "helper") +
                            if (kindIsGuest) {
                                stringResource(R.string.ph_kind_guest_short)
                            } else {
                                stringResource(R.string.ph_kind_family_short)
                            },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                }
                if (inviteStatus == "PENDING") {
                    WideOutlinedButton(
                        enabled = creatingKind == null,
                        onClick = {
                            scope.launch {
                                try {
                                    cancelInvitation(currentInvite.invitationId)
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    pairingError =
                                        context.getString(R.string.err_cancel_failed, describeFirestoreError(context, e))
                                }
                            }
                        }
                    ) {
                            Text(stringResource(R.string.btn_cancel_code))
                    }
                }
                inviteSyncError?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    } else if (currentInvite != null) {
            val statusText = when (inviteStatus) {
            "PAIRED" -> stringResource(R.string.ph_inv_paired, claimerEmail ?: "helper")
            "CANCELLED" -> stringResource(R.string.ph_inv_cancelled)
            else -> stringResource(R.string.ph_inv_expired)
        }
        Text(
            text = statusText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    // ---------- ৩. যুক্ত ফোন — List Tiles + অ্যাকশন নিচ-শিট ----------
    SectionCard(
            title = stringResource(R.string.devices_title),
        icon = Icons.Filled.Phone,
            infoText = stringResource(R.string.ph_two_kinds_info),
    ) {
        if (relationships.isEmpty()) {
            if (relationshipsSyncError != null) {
                Text(
                    text = relationshipsSyncError!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            } else {
                EmptyState(
                    icon = Icons.Filled.Phone,
                        title = stringResource(R.string.devices_empty_title),
                        hint = stringResource(R.string.devices_empty_hint2),
                )
            }
        } else {
            Column {
                relationships.forEachIndexed { index, rel ->
                    if (index > 0) HorizontalDivider()
                    PairTile(
                        rel = rel,
                        myUid = user.uid,
                        relNow = relNow,
                        onOpen = { selectedRelId = rel.id }
                    )
                }
            }
        }
    }

    // ---------- ৪. যুক্ত-ফোন অ্যাকশন নিচ-শিট ----------
    val selectedRel = relationships.firstOrNull { it.id == selectedRelId }
    if (selectedRel != null) {
        val isGuest = selectedRel.kind == REL_KIND_GUEST
        val iHelp = selectedRel.helperUid == user.uid
        val otherEmail =
            (if (iHelp) selectedRel.recipientEmail else selectedRel.helperEmail)
                .ifBlank { "(no email)" }
        val sheetState = rememberModalBottomSheetState()
        ModalBottomSheet(
            onDismissRequest = { selectedRelId = null },
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
                                    selectedRelId = null
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    pairingError =
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
                                    selectedRelId = null
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    pairingError =
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

    // ---------- ৫. FAB-এর পেয়ারিং নিচ-শিট ----------
    if (showPairSheet) {
        val pairSheetState = rememberModalBottomSheetState()
        ModalBottomSheet(
            onDismissRequest = onDismissPairSheet,
            sheetState = pairSheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    // Phase 11.6 — the code field never hides behind the
                    // keyboard (docs §16).
                    .imePadding()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                        text = stringResource(R.string.ph_add_new_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                        text = stringResource(R.string.ph_add_new_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                PairOptionRow(
                    icon = Icons.Filled.AccountCircle,
                        title = stringResource(R.string.ph_family_code_title),
                        description = stringResource(R.string.ph_family_code_desc),
                    busy = creatingKind == REL_KIND_FAMILY,
                    enabled = creatingKind == null,
                    onClick = {
                        createCode(REL_KIND_FAMILY)
                        onDismissPairSheet()
                    }
                )
                PairOptionRow(
                    icon = Icons.Filled.DateRange,
                        title = stringResource(R.string.ph_guest_code_title),
                        description = stringResource(R.string.ph_guest_code_desc),
                    busy = creatingKind == REL_KIND_GUEST,
                    enabled = creatingKind == null,
                    onClick = {
                        createCode(REL_KIND_GUEST)
                        onDismissPairSheet()
                    }
                )
                HorizontalDivider()
                Text(
                        text = stringResource(R.string.ph_give_code_hint),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                        text = stringResource(R.string.ph_badge_legend),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = codeInput,
                        onValueChange = { codeInput = it },
                            label = { Text(stringResource(R.string.ph_code_label)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        enabled = !redeeming,
                        onClick = { showScanner = true },
                        modifier = Modifier.heightIn(min = 52.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                            Text("  " + stringResource(R.string.btn_scan_short))
                    }
                }
                if (showScanner) {
                    QrScanDialog(
                        onCode = { code, kindHint ->
                            codeInput = code
                            redeemMessage = null
                            redeemedKind = null
                            showScanner = false
                            // Phase 12 — scan flows straight into the
                            // role-setup sheet (preview → confirm → auto-setup).
                            onDismissPairSheet()
                            pendingScan = ScannedCode(code, kindHint)
                        },
                        onDismiss = { showScanner = false }
                    )
                }
                BigButton(
                    enabled = !redeeming && codeInput.isNotBlank(),
                    onClick = { doRedeem(codeInput) }
                ) {
                    if (redeeming) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                            Text("  " + stringResource(R.string.btn_import_code))
                    }
                }
                redeemMessage?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                            color = if (message.contains("✓")) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        }
                    )
                }
                Text(
                        text = stringResource(R.string.rel_always_permission),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    // ---------- ৬. Phase 12 — স্ক্যান-পরবর্তী রোল-সেটআপ নিচ-শিট ----------
    pendingScan?.let { scan ->
        ScannedRoleSheet(
            scan = scan,
            redeeming = redeeming,
            message = redeemMessage,
            redeemedKind = redeemedKind,
            onConfirm = { doRedeem(scan.code) },
            onDismiss = { pendingScan = null }
        )
    }
}

/** Phase 12 — a scanned pairing code plus the role marker read from its QR. */
private data class ScannedCode(val code: String, val kindHint: String?)

/**
 * Phase 12 — the role-setup sheet that opens the moment a QR is scanned:
 *  1. PREVIEW — what role this code will give (from the &k= marker when the
 *     QR carries it), with the plain-language permission list.
 *  2. CONFIRM — one big "যুক্ত হোন" button; nothing is written until tapped.
 *  3. AUTO-SETUP — the redemption runs and the sheet flips to a success card
 *     with the ACTUAL role (re-read from the Firestore invitation inside the
 *     transaction — the marker on the QR is cosmetic, the server decides).
 * The whole journey happens in one sheet: scan → preview → confirm → paired.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScannedRoleSheet(
    scan: ScannedCode,
    redeeming: Boolean,
    message: String?,
    redeemedKind: String?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    val context = LocalContext.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                    text = stringResource(R.string.rs_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = scan.code,
                style = MaterialTheme.typography.labelMedium,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            when {
                redeeming -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator()
                        Text(
                                text = context.getString(R.string.rs_verifying),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                redeemedKind != null -> {
                    // SUCCESS — the automatic setup is done.
                    val guest = redeemedKind == REL_KIND_GUEST
                    val accent = if (guest) {
                        AppWarningColor()
                    } else {
                        MaterialTheme.colorScheme.tertiary
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        StatusBadge(
                                label = if (guest) stringResource(R.string.rs_kind_guest) else stringResource(R.string.rs_kind_family),
                            kind = "success"
                        )
                    }
                    Text(
                            text = context.getString(R.string.rs_added_ok),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    ScannedRoleBullet(
                        accent = accent,
                            text = stringResource(R.string.rs_perm_note)
                    )
                    if (guest) {
                        ScannedRoleBullet(
                            accent = accent,
                                text = stringResource(R.string.rs_guest_note)
                        )
                    } else {
                        ScannedRoleBullet(
                            accent = accent,
                                text = stringResource(R.string.rs_family_note)
                        )
                    }
                    BigButton(onClick = onDismiss) {
                            Text(stringResource(R.string.btn_ok))
                    }
                }
                else -> {
                    // PREVIEW — nothing written to Firestore until confirmed.
                    val known = scan.kindHint != null
                    val guestHint = scan.kindHint == REL_KIND_GUEST
                    val accent = if (guestHint) {
                        AppWarningColor()
                    } else {
                        MaterialTheme.colorScheme.tertiary
                    }
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = accent.copy(alpha = 0.08f)
                        ),
                        border = BorderStroke(1.dp, accent.copy(alpha = 0.35f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = if (guestHint) {
                                        Icons.Filled.DateRange
                                    } else {
                                        Icons.Filled.Home
                                    },
                                    contentDescription = null,
                                    tint = accent,
                                    modifier = Modifier.size(26.dp)
                                )
                                Text(
                                    text = when {
                                            !known -> stringResource(R.string.rs_check_code)
                                            guestHint -> stringResource(R.string.rs_you_guest)
                                            else -> stringResource(R.string.rs_you_family)
                                    },
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = accent
                                )
                            }
                            Text(
                                text = when {
                                        !known -> stringResource(R.string.rs_legacy_note)
                                        guestHint -> stringResource(R.string.rs_guest_desc)
                                        else -> stringResource(R.string.rs_family_desc)
                                },
                                style = MaterialTheme.typography.bodySmall
                            )
                            HorizontalDivider()
                            ScannedRoleBullet(
                                accent = accent,
                                    stringResource(R.string.rs_feat_request)
                            )
                            ScannedRoleBullet(
                                accent = accent,
                                    stringResource(R.string.rs_feat_perm)
                            )
                            ScannedRoleBullet(
                                accent = accent,
                                    stringResource(R.string.rs_feat_autoend)
                            )
                        }
                    }
                    // Phase 12 — a failed redeem comes back here honestly:
                    // the server's refusal shows INSIDE the sheet.
                    if (message != null) {
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    BigButton(
                        enabled = !redeeming,
                        onClick = onConfirm
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                            Text("  " + stringResource(R.string.btn_connect))
                    }
                    Text(
                            text = stringResource(R.string.rs_cancel_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/** One check-mark bullet line inside the role sheet. */
@Composable
internal fun ScannedRoleBullet(accent: Color, text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = Icons.Filled.Check,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(14.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

/** v0.14.0 — one tappable option row inside the pairing bottom sheet. */
@Composable
internal fun PairOptionRow(
    icon: ImageVector,
    title: String,
    description: String,
    busy: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(
                        MaterialTheme.colorScheme.primaryContainer,
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(24.dp)
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (busy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
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
        !isGuest && rel.status == "ACTIVE" -> "Family"
            !isGuest -> stringResource(R.string.tile_family_status, sessionStatus(context, rel.status))
        guestOk -> {
            val remainingMin =
                ((rel.guestExpiresAtMillis - relNow) / 60000L).coerceAtLeast(0)
            when {
                rel.guestExpiresAtMillis <= 0L -> "Guest"
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
 * v0.21.0 (audit) — process-level pairing-card state. The live invitation
 * used to be plain remember() inside the tab-scoped pairing section, so a
 * recreate (language change, rotation) stranded a PENDING invitation: the
 * card and its watcher vanished while the invitation stayed live in
 * Firestore until expiry. Delegating to this bus keeps the card alive.
 */
internal object PairingUiBus {
    val inviteState = mutableStateOf<InviteCode?>(null)
    val inviteKindState = mutableStateOf(REL_KIND_FAMILY)
}
