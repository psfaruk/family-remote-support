package com.family.remote.support.ui.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.family.remote.support.NotifCategory
import com.family.remote.support.NotifEvent
import com.family.remote.support.NotifFilter
import com.family.remote.support.NotificationsLog
import com.family.remote.support.R
import com.family.remote.support.applyNotifFilter
import com.family.remote.support.notifCategoryOf
import com.family.remote.support.notifRouteFor
import com.family.remote.support.notifUnreadCount
import com.family.remote.support.ui.components.EmptyState
import com.family.remote.support.ui.components.SectionCard
import com.family.remote.support.ui.components.relTime

/**
 * ui/notifications/NotificationsScreen.kt — the Notifications centre (W7).
 * Shows the local event log with All/Unread/Connection/Location/Security
 * filter chips, per-row mark-as-read, Mark-all and Clear-history actions.
 * All data comes from the local [NotificationsLog] — zero Firestore reads.
 *
 * Every decision (category mapping, filter algebra, read-state, tap routes)
 * is the unit-tested NotifCenter.kt surface — this screen only renders it.
 */
@Composable
fun NotificationsScreen(
    onOpenRoute: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val events by NotificationsLog.events.collectAsState()

    var filter by remember { mutableStateOf(NotifFilter.ALL) }
    var clearOpen by remember { mutableStateOf(false) }

    val filtered = events.applyNotifFilter(filter)
    val unread = events.notifUnreadCount()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SectionCard(
            title = stringResource(R.string.notif_title),
            icon = Icons.Filled.List,
            infoText = stringResource(R.string.notif_hint),
        ) {
            // Filter chips
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
            ) {
                FilterChip(
                    selected = filter == NotifFilter.ALL,
                    onClick = { filter = NotifFilter.ALL },
                    label = { Text(stringResource(R.string.notif_filter_all)) },
                )
                FilterChip(
                    selected = filter == NotifFilter.UNREAD,
                    onClick = { filter = NotifFilter.UNREAD },
                    label = { Text(stringResource(R.string.notif_filter_unread, unread)) },
                )
                FilterChip(
                    selected = filter == NotifFilter.CONNECTION,
                    onClick = { filter = NotifFilter.CONNECTION },
                    label = { Text(stringResource(R.string.notif_filter_connection)) },
                )
                FilterChip(
                    selected = filter == NotifFilter.LOCATION,
                    onClick = { filter = NotifFilter.LOCATION },
                    label = { Text(stringResource(R.string.notif_filter_location)) },
                )
                FilterChip(
                    selected = filter == NotifFilter.SECURITY,
                    onClick = { filter = NotifFilter.SECURITY },
                    label = { Text(stringResource(R.string.notif_filter_security)) },
                )
            }

            if (filtered.isEmpty()) {
                EmptyState(
                    icon = Icons.Filled.CheckCircle,
                    title = stringResource(R.string.notif_empty_filter_title),
                    body = stringResource(R.string.notif_empty_filter_body),
                )
            } else {
                filtered.forEach { event ->
                    NotificationRow(
                        event = event,
                        onMarkRead = { NotificationsLog.markRead(context, event.id) },
                        onTap = {
                            NotificationsLog.markRead(context, event.id)
                            onOpenRoute(notifRouteFor(event.type, event.arg2))
                        },
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (unread > 0) {
                    TextButton(
                        onClick = { NotificationsLog.markAllRead(context) },
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) {
                        Text(stringResource(R.string.notif_action_mark_all))
                    }
                }
                if (events.isNotEmpty()) {
                    TextButton(
                        onClick = { clearOpen = true },
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) {
                        Text(stringResource(R.string.notif_action_clear))
                    }
                }
            }
        }
    }

    if (clearOpen) {
        AlertDialog(
            onDismissRequest = { clearOpen = false },
            title = { Text(stringResource(R.string.notif_clear_title)) },
            text = { Text(stringResource(R.string.notif_clear_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        NotificationsLog.clearHistory(context)
                        clearOpen = false
                    },
                ) {
                    Text(stringResource(R.string.notif_clear_yes))
                }
            },
            dismissButton = {
                TextButton(onClick = { clearOpen = false }) {
                    Text(stringResource(R.string.notif_clear_cancel))
                }
            },
        )
    }
}

/** One notification event row. */
@Composable
private fun NotificationRow(
    event: NotifEvent,
    onMarkRead: () -> Unit,
    onTap: () -> Unit,
) {
    val now = remember(event.id) { System.currentTimeMillis() }
    val category = notifCategoryOf(event.type)
    val isUnread = event.readAt == null

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onTap),
    ) {
        if (isUnread) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = notifEventLabel(event),
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = relTime(event.at, now),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (category != null) {
                    CategoryChip(category)
                }
            }
        }
        if (isUnread) {
            TextButton(
                onClick = onMarkRead,
                contentPadding = PaddingValues(
                    horizontal = 8.dp, vertical = 4.dp,
                ),
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Text(stringResource(R.string.cmn_ok))
            }
        }
    }
}

/** A small tonal chip carrying the category's localized label. */
@Composable
private fun CategoryChip(category: NotifCategory) {
    val label = when (category) {
        NotifCategory.CONNECTION -> stringResource(R.string.notif_filter_connection)
        NotifCategory.LOCATION -> stringResource(R.string.notif_filter_location)
        NotifCategory.SECURITY -> stringResource(R.string.notif_filter_security)
    }
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

/**
 * The localized one-line label of an event (the notif_ev_* strings; the
 * counterpart name fills %1$s when the event carries one). An unknown
 * FUTURE type keeps its raw type string — visible, never mis-labeled.
 */
@Composable
private fun notifEventLabel(event: NotifEvent): String {
    val who = event.arg ?: ""
    return when (event.type) {
        "session_asked" -> stringResource(R.string.notif_ev_asked, who)
        "session_started" -> stringResource(R.string.notif_ev_started, who)
        "session_ended" -> stringResource(R.string.notif_ev_ended, who)
        "session_denied" -> stringResource(R.string.notif_ev_denied, who)
        "session_cancelled" -> stringResource(R.string.notif_ev_cancelled, who)
        "sharing_started" -> stringResource(R.string.notif_ev_sharing_on)
        "sharing_stopped" -> stringResource(R.string.notif_ev_sharing_off)
        "sharing_perm_lost" -> stringResource(R.string.notif_ev_sharing_perm_lost)
        "place_alert" -> stringResource(R.string.notif_ev_place, who)
        "pairing_invited" -> stringResource(R.string.notif_ev_pairing_invited)
        "pairing_approved" -> stringResource(R.string.notif_ev_pairing_approved, who)
        "family_revoked" -> stringResource(R.string.notif_ev_family_revoked, who)
        "guest_removed" -> stringResource(R.string.notif_ev_guest_removed, who)
        "rel_revoked" -> stringResource(R.string.notif_ev_rel_revoked, who)
        "guest_expiring_soon" -> stringResource(R.string.notif_ev_guest_expiring_soon, who)
        "guest_expired" -> stringResource(R.string.notif_ev_guest_expired, who)
        "presence_online" -> stringResource(R.string.notif_ev_presence_online, who)
        "presence_offline" -> stringResource(R.string.notif_ev_presence_offline, who)
        else -> event.type
    }
}
