package com.family.remote.support.ui.home

// M4 (v0.32.0) — Recent Activity + Session Detail nested routes
// (NAVIGATION_MAP §2): the old Settings ActivityFeed graduates to a real
// route; session detail is the existing one-shot getSession read, no new
// listeners.

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.family.remote.support.R
import com.family.remote.support.SessionView
import com.family.remote.support.StatusRow
import com.family.remote.support.getSession
import com.family.remote.support.mmss
import com.family.remote.support.ui.components.AppListTile
import com.family.remote.support.ui.components.EmptyState
import com.family.remote.support.ui.components.ErrorPanel
import com.family.remote.support.ui.components.LoadingSkeleton
import com.family.remote.support.ui.components.TickingBlock
import com.family.remote.support.ui.components.WideOutlinedButton
import com.family.remote.support.ui.components.sessionBadgeKind
import com.family.remote.support.ui.components.sessionStatus
import com.family.remote.support.ui.components.statusChipColors
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.CancellationException

// ═══════════════════════════════════════════════════════════════════════
// M4 (v0.32.0) — the nested Home destinations (NAVIGATION_MAP §2):
//   • home/sessions      → RecentActivityScreen — the Settings-tab
//     ActivityFeed (M3 extraction) graduates to a real route. The live
//     list IS the root-scoped SessionWatchViewModel state passed in;
//     this screen creates NO Firestore listener of its own.
//   • home/sessions/{id} → SessionDetailScreen — status, duration and
//     ended-by for one session: LIVE while the root watch still holds
//     it, otherwise the SAME one-shot getSession read the last-finished
//     line already performs. No new queries, no new listeners.
// The caller supplies the scroll container (no verticalScroll here).
// ═══════════════════════════════════════════════════════════════════════

/**
 * NAVIGATION_MAP §2 "home/sessions — Recent activity".
 *
 * @param user            the signed-in account (the shell only composes
 *                        this route authenticated; a null uid simply
 *                        falls to the non-owner label branches).
 * @param sessions        root SessionWatchViewModel live state (open
 *                        sessions only) — no listener is created here.
 * @param sessionsLoaded  false until the watch's first snapshot.
 * @param sessionsError   already-localized routed error text (the shell
 *                        maps it via describeFirestoreError).
 * @param onRetrySessions retry for the watch (bumps the shell retry key).
 * @param onOpenSession   navigates to home/sessions/{id}.
 */
@Composable
internal fun RecentActivityScreen(
    user: FirebaseUser?,
    sessions: List<SessionView>,
    sessionsLoaded: Boolean,
    sessionsError: String?,
    onRetrySessions: () -> Unit,
    onOpenSession: (String) -> Unit
) {
    // The last session that closed — fetched once when it leaves the open
    // set (ported verbatim from the Settings-tab ActivityFeed: the live
    // watch only tracks OPEN sessions, so the "Last session" line survives
    // via one one-shot getSession of the id that just disappeared).
    var lastFinished by remember { mutableStateOf<SessionView?>(null) }
    var lastOpenId by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val meUid = user?.uid

    val openSession = sessions.firstOrNull {
        it.status == "REQUESTED" || it.status == "ACTIVE"
    }
    LaunchedEffect(sessions) {
        val currentOpenId = openSession?.id
        val prevOpenId = lastOpenId
        if (prevOpenId != null && prevOpenId != currentOpenId) {
            try {
                lastFinished = getSession(prevOpenId)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // best effort — the history line is informational only
            }
        }
        lastOpenId = currentOpenId
    }

    // The screen heading IS the section title — rows render directly
    // below it (no extra SectionCard).
    Text(
        text = stringResource(R.string.actv_title),
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold
    )

    // NAVIGATION_MAP §5 — real states as design-system components. After
    // a snapshot the last list always stays (v0.21.0 semantics — a routed
    // transient error never unmounts live rows), so the rows below keep
    // rendering even while the error panel is shown.
    when {
        !sessionsLoaded -> LoadingSkeleton(rows = 2)
        sessionsError != null -> ErrorPanel(message = sessionsError, onRetry = onRetrySessions)
        openSession == null && lastFinished == null -> EmptyState(
            icon = Icons.Filled.Notifications,
            title = stringResource(R.string.actv_empty_t),
            hint = stringResource(R.string.actv_empty_h),
            illustrationRes = R.drawable.ic_empty_activity
        )
    }

    // M4 — one tile per open session (the old feed showed only the first;
    // the route shows them all), then the last-finished line. Every row is
    // now a route LINK into the session's detail page.
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        sessions.forEachIndexed { index, s ->
            if (index > 0) HorizontalDivider()
            // M8 (v0.34.0) perf pass — the countdown lives inside the row's
            // own TickingBlock, so ONLY that row re-renders each second (the
            // screen heading, states and last-finished row stay still).
            TickingBlock(key = s.id) { now ->
                val secondsLeft = when (s.status) {
                    "REQUESTED" -> ((s.respondDeadlineMillis - now) / 1000).coerceAtLeast(0)
                    else -> ((s.sessionEndsAtMillis - now) / 1000).coerceAtLeast(0)
                }
                AppListTile(
                    title = when {
                        s.status == "REQUESTED" && s.recipientUid == meUid ->
                            stringResource(
                                R.string.feed_req_received,
                                s.helperEmail.ifBlank { stringResource(R.string.fallback_someone) }
                            )
                        s.status == "REQUESTED" ->
                            stringResource(R.string.feed_req_sent, s.recipientEmail)
                        s.status == "ACTIVE" && s.helperUid == meUid ->
                            stringResource(R.string.feed_active_theirs, s.recipientEmail)
                        else ->
                            stringResource(
                                R.string.feed_active_mine,
                                s.helperEmail.ifBlank { stringResource(R.string.fallback_someone) }
                            )
                    },
                    subtitle = when {
                        s.status == "REQUESTED" && s.recipientUid == meUid ->
                            stringResource(R.string.feed_need_decision, mmss(secondsLeft))
                        s.status == "REQUESTED" ->
                            stringResource(R.string.feed_awaiting, mmss(secondsLeft))
                        else ->
                            stringResource(R.string.feed_in_progress, mmss(secondsLeft))
                    },
                    icon = if (s.status == "ACTIVE") Icons.Filled.CheckCircle else Icons.Filled.Send,
                    avatarContainer = if (s.status == "ACTIVE") {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.tertiaryContainer
                    },
                    avatarContent = if (s.status == "ACTIVE") {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onTertiaryContainer
                    },
                    chip = if (s.kind == "guest") {
                        stringResource(R.string.sess_guest)
                    } else {
                        sessionStatus(context, s.status)
                    },
                    chipContainer = if (s.kind == "guest") {
                        MaterialTheme.colorScheme.tertiaryContainer
                    } else {
                        statusChipColors(sessionBadgeKind(s.status)).first
                    },
                    chipContent = if (s.kind == "guest") {
                        MaterialTheme.colorScheme.onTertiaryContainer
                    } else {
                        statusChipColors(sessionBadgeKind(s.status)).second
                    },
                    onClick = { onOpenSession(s.id) },
                    trailingIcon = Icons.Filled.KeyboardArrowRight
                )
            }
        }
        lastFinished?.let { s ->
            // Divider between the open rows and the last-finished row when
            // both are present (the old feed drew it unconditionally).
            if (sessions.isNotEmpty()) HorizontalDivider()
            AppListTile(
                title = stringResource(R.string.feed_last_title),
                subtitle = stringResource(R.string.feed_last_sub, s.helperEmail.ifBlank { "?" }, s.recipientEmail) +
                    (s.endedBy?.let { context.getString(R.string.feed_ended_by, it) } ?: ""),
                icon = Icons.Filled.CheckCircle,
                avatarContainer = MaterialTheme.colorScheme.surfaceVariant,
                avatarContent = MaterialTheme.colorScheme.onSurfaceVariant,
                chip = sessionStatus(context, s.status),
                chipContainer = statusChipColors(sessionBadgeKind(s.status)).first,
                chipContent = statusChipColors(sessionBadgeKind(s.status)).second,
                onClick = { onOpenSession(s.id) },
                trailingIcon = Icons.Filled.KeyboardArrowRight
            )
        }
    }
}

/**
 * NAVIGATION_MAP §2 "home/sessions/{id} — Session detail (status,
 * duration, ended-by)". Informational only — the consent/request ACTIONS
 * live on Home and the member detail; this screen never writes anything.
 *
 * @param sessionId   the route's session id.
 * @param liveSessions the same root live state — while the watch still
 *                    holds this session the detail recomposes LIVE.
 * @param onBack      pops back to the recent-activity list.
 */
@Composable
internal fun SessionDetailScreen(
    sessionId: String,
    liveSessions: List<SessionView>,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    // Back row — the nested route's affordance back to the list.
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.cd_back)
            )
        }
        Text(
            text = stringResource(R.string.sess_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
    }

    // LIVE while the root watch still holds the session; otherwise the
    // same one-shot getSession read the last-finished line performs — no
    // new listener, no new query (audit rule: navigation adds zero reads
    // beyond the one the app already makes).
    val live = liveSessions.firstOrNull { it.id == sessionId }

    var fetched by remember(sessionId) { mutableStateOf<SessionView?>(null) }
    var fetchDone by remember(sessionId) { mutableStateOf(false) }
    // Retry hook (bump to re-run the one-shot fetch); unused until the
    // shell wires a retry action.
    var fetchKey by remember(sessionId) { mutableStateOf(0) }
    LaunchedEffect(sessionId, fetchKey) {
        try {
            fetched = getSession(sessionId)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // best effort — the honest "not found" state below covers it
            fetched = null
        } finally {
            fetchDone = true
        }
    }

    when {
        live == null && !fetchDone -> LoadingSkeleton(rows = 3)
        live == null && fetchDone && fetched == null -> {
            EmptyState(
                icon = Icons.Filled.Notifications,
                title = stringResource(R.string.sess_not_found),
                hint = stringResource(R.string.sess_not_found_h)
            )
            WideOutlinedButton(onClick = onBack) {
                Text(stringResource(R.string.cd_back))
            }
        }
        else -> {
            val s = live ?: fetched!!

            // For a final session sessionEndsAtMillis holds the end; for
            // REQUESTED the interesting number is the answer countdown —
            // mirrors how the Home/Pairing cards show their mmss numbers.
            // M8 (v0.34.0) perf pass — the countdown Text carries its own
            // TickingBlock now, so the whole detail screen stops
            // recomposing every second. A FINAL session has no moving
            // number at all, so its duration row stays completely static
            // (the old code ticked for ACTIVE rows too, pointlessly).
            val endRef = if (s.status == "ACTIVE") {
                s.sessionEndsAtMillis
            } else if (s.status == "REQUESTED") {
                s.respondDeadlineMillis
            } else {
                s.sessionEndsAtMillis
            }

            // Ended-by — the working mapping from the pairing card
            // (endedBy is the ender's uid, or "timeout"); a blank result
            // skips the row entirely.
            val endedByLabel = when (s.endedBy) {
                null, "" -> ""
                "timeout" -> context.getString(R.string.ended_auto)
                s.helperUid -> context.getString(R.string.ended_by_helper)
                s.recipientUid -> context.getString(R.string.ended_by_recipient)
                else -> ""
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                StatusRow(stringResource(R.string.sess_state), sessionStatus(context, s.status))
                StatusRow(
                    stringResource(R.string.sess_kind),
                    if (s.kind == "guest") stringResource(R.string.sess_guest)
                    else stringResource(R.string.sess_family)
                )
                StatusRow(stringResource(R.string.sess_helper), s.helperEmail.ifBlank { "—" })
                StatusRow(stringResource(R.string.sess_recipient), s.recipientEmail.ifBlank { "—" })
                if (s.status == "REQUESTED") {
                    TickingBlock(key = s.id) { now ->
                        StatusRow(
                            stringResource(R.string.sess_duration),
                            mmss(((s.respondDeadlineMillis - now) / 1000).coerceAtLeast(0))
                        )
                    }
                } else {
                    StatusRow(
                        stringResource(R.string.sess_duration),
                        mmss(((endRef - s.requestedAtMillis) / 1000).coerceAtLeast(0))
                    )
                }
                if (endedByLabel.isNotBlank()) {
                    StatusRow(stringResource(R.string.sess_endedby), endedByLabel)
                }
            }
        }
    }
}
