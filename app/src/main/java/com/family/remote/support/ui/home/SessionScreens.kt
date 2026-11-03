package com.family.remote.support.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.family.remote.support.Nicknames
import com.family.remote.support.R
import com.family.remote.support.SessionView
import com.family.remote.support.describeFirestoreError
import com.family.remote.support.getSession
import com.family.remote.support.localizedMessage
import com.family.remote.support.ui.components.AppListTile
import com.family.remote.support.ui.components.BackTitleRow
import com.family.remote.support.ui.components.BigButton
import com.family.remote.support.ui.components.DetailRow
import com.family.remote.support.ui.components.EmptyState
import com.family.remote.support.ui.components.Fmt
import com.family.remote.support.ui.components.LoadingSkeleton
import com.family.remote.support.ui.components.SectionCard
import com.family.remote.support.ui.components.StatusBadge
import com.family.remote.support.ui.components.appLocale
import com.family.remote.support.ui.components.relTime
import com.family.remote.support.ui.components.tickingNow
import com.family.remote.support.ui.nav.AppRoutes
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.launch

/**
 * ui/home/SessionScreens.kt — the home/sessions subtree (W2;
 * FRONTEND_UI_PLAN_BN §2.2-nested):
 *
 *   home/sessions       → SessionsScreen     (running + just-ended)
 *   home/sessions/{id}  → SessionDetailScreen (facts, deadlines, actions)
 *
 * Lists read [HomeViewModel]'s ONE listener set. The detail screen also
 * serves COLD deep links (a notification tap can land here before any
 * listener ever delivered this id): a one-shot [getSession] fills the
 * gap — a single read, only when needed.
 */
// ═══════════════════════════════════════════════════════════════════

@Composable
fun SessionsScreen(
    user: FirebaseUser,
    vm: HomeViewModel,
    onOpenRoute: (String) -> Unit,
    onBack: () -> Unit,
) {
    val ui by vm.ui.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        BackTitleRow(title = stringResource(R.string.feed_title), onBack = onBack)

        SectionCard(
            title = stringResource(R.string.sess_open_title),
            icon = Icons.Filled.List,
            infoText = stringResource(R.string.feed_info),
        ) {
            if (ui.sessions.isEmpty()) {
                Text(
                    text = stringResource(R.string.sess_none_open),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            } else {
                Column {
                    ui.sessions.forEach { s ->
                        SessionListRow(user.uid, s, onOpenRoute)
                    }
                }
            }
        }

        SectionCard(
            title = stringResource(R.string.sess_recent_title),
            icon = Icons.Filled.List,
        ) {
            if (ui.finished.isEmpty()) {
                Text(
                    text = stringResource(R.string.sess_none_recent),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            } else {
                Column {
                    ui.finished.forEach { s ->
                        FinishedSessionRow(user.uid, s, onOpenRoute)
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionListRow(
    myUid: String,
    session: SessionView,
    onOpenRoute: (String) -> Unit,
) {
    val context = LocalContext.current
    val counterpartEmail =
        if (session.helperUid == myUid) session.recipientEmail else session.helperEmail
    val name = Nicknames.displayName(context, session.relId, counterpartEmail, counterpartEmail)
    val isHelper = session.helperUid == myUid
    AppListTile(
        avatarText = name,
        title = name +
            (if (session.recipientDeviceModel.isNotBlank()) {
                " · " + session.recipientDeviceModel
            } else {
                ""
            }),
        subtitle = stringResource(
            if (isHelper) R.string.rel_you_help else R.string.rel_helps_you
        ),
        onClick = { onOpenRoute(AppRoutes.homeSession(session.id)) },
        trailing = { StatusBadge(session.status) },
    )
}

// ── home/sessions/{id} ────────────────────────────────────────────────────

@Composable
fun SessionDetailScreen(
    sessionId: String,
    user: FirebaseUser,
    vm: HomeViewModel,
    onOpenRoute: (String) -> Unit,
    onBack: () -> Unit,
) {
    val ui by vm.ui.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val locale = appLocale()

    // The live sets first; a cold deep link falls back to a one-shot read.
    var fetched by remember(sessionId) { mutableStateOf<SessionView?>(null) }
    var fetching by remember(sessionId) { mutableStateOf(false) }
    val session = ui.sessions.firstOrNull { it.id == sessionId }
        ?: ui.finished.firstOrNull { it.id == sessionId }
        ?: fetched
    if (session == null && !fetching) {
        fetching = true
        LaunchedEffect(sessionId) {
            try {
                fetched = getSession(sessionId)
            } catch (_: Exception) {
                fetched = null // honest not-found below
            }
        }
    }

    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        BackTitleRow(title = stringResource(R.string.sess_title), onBack = onBack)

        when {
            session == null && fetching ->
                LoadingSkeleton(rows = 2)
            session == null ->
                EmptyState(
                    icon = Icons.Filled.List,
                    title = stringResource(R.string.sess_not_found),
                    body = stringResource(R.string.sess_not_found_h),
                )
            else -> {
                val isHelper = session.helperUid == user.uid
                val counterpartEmail =
                    if (isHelper) session.recipientEmail else session.helperEmail
                val name = Nicknames.displayName(
                    context, session.relId, counterpartEmail, counterpartEmail,
                )
                // Only rows with a live deadline tick (M8 isolation).
                val now =
                    if (session.status == "REQUESTED" || session.status == "ACTIVE") {
                        tickingNow()
                    } else {
                        remember { com.family.remote.support.ServerClock.now() }
                    }

                SectionCard(
                    title = name,
                    icon = Icons.Filled.Phone,
                    trailing = { StatusBadge(session.status) },
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        DetailRow(
                            label = stringResource(R.string.sess_helper),
                            value = if (isHelper) {
                                stringResource(R.string.sess_you)
                            } else {
                                name
                            },
                        )
                        DetailRow(
                            label = stringResource(R.string.sess_recipient),
                            value = if (isHelper) {
                                session.recipientDeviceModel.ifBlank {
                                    stringResource(R.string.sess_this_phone)
                                }
                            } else {
                                stringResource(R.string.sess_this_phone)
                            },
                        )
                        DetailRow(
                            label = stringResource(R.string.sess_kind),
                            value = stringResource(
                                if (session.kind == com.family.remote.support.REL_KIND_GUEST) {
                                    R.string.sess_guest
                                } else {
                                    R.string.sess_family
                                }
                            ),
                        )
                        DetailRow(
                            label = stringResource(R.string.sess_requested_at),
                            value = Fmt.clockTime(session.requestedAtMillis, locale) +
                                " · " + relTime(session.requestedAtMillis, now),
                        )
                        if (session.status == "REQUESTED") {
                            DetailRow(
                                label = stringResource(R.string.sess_deadline),
                                value = stringResource(
                                    R.string.sess_left_fmt,
                                    Fmt.mmss(session.respondDeadlineMillis - now, locale),
                                ),
                            )
                        }
                        if (session.status == "ACTIVE") {
                            DetailRow(
                                label = stringResource(R.string.sess_ends_at),
                                value = Fmt.clockTime(session.sessionEndsAtMillis, locale) +
                                    " · " + stringResource(
                                        R.string.sess_left_fmt,
                                        Fmt.mmss(session.sessionEndsAtMillis - now, locale),
                                    ),
                            )
                        }
                        if (session.status != "REQUESTED" && session.status != "ACTIVE") {
                            val resolvedAt =
                                if (session.sessionEndsAtMillis > 0) session.sessionEndsAtMillis
                                else session.respondDeadlineMillis
                            DetailRow(
                                label = stringResource(R.string.sess_ended_at),
                                value = Fmt.clockTime(resolvedAt, locale) +
                                    " · " + relTime(resolvedAt, now),
                            )
                            DetailRow(
                                label = stringResource(R.string.sess_endedby),
                                value = when (session.endedBy) {
                                    null, "" -> "—"
                                    "timeout" -> stringResource(R.string.sess_ender_timeout)
                                    session.helperUid ->
                                        stringResource(R.string.sess_ender_helper)
                                    session.recipientUid ->
                                        stringResource(R.string.sess_ender_recipient)
                                    else -> session.endedBy
                                },
                            )
                        }
                    }
                }

                if (error != null) {
                    Text(
                        text = error!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                if (session.status == "ACTIVE") {
                    // W4 (v0.52.0): the LIVE session surfaces, in-tab:
                    // the helper watches + touches HERE; the recipient
                    // shares HERE (and on Home, right under the live hint).
                    if (isHelper) {
                        HelperViewerSection(session = session, user = user, vm = vm)
                    } else {
                        RecipientShareCard(session = session, user = user)
                        BigButton(
                            text = stringResource(R.string.btn_end_session),
                            danger = true,
                            loading = busy,
                            onClick = {
                                scope.launch {
                                    busy = true
                                    error = null
                                    try {
                                        vm.end(session.id)
                                    } catch (e: com.family.remote.support.SessionException) {
                                        error = e.localizedMessage(context)
                                    } catch (e: Exception) {
                                        error = describeFirestoreError(context, e)
                                    } finally {
                                        busy = false
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}
