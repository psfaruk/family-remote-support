package com.family.remote.support.ui.notifications

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.Chip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.family.remote.support.NotifEvent
import com.family.remote.support.NotifCenter
import com.family.remote.support.NotifFilter
import com.family.remote.support.NotificationsLog
import com.family.remote.support.R
import com.family.remote.support.ui.components.EmptyState
import com.family.remote.support.ui.components.SectionCard

/**
 * ui/notifications/NotificationsScreen.kt — the Notifications centre (W7).
 * Shows the local event log with All/Unread/Connection/Location/Security
 * filter chips, per-row mark-as-read, Mark-all and Clear-history actions.
 * All data comes from the local [NotificationsLog] — zero Firestore reads.
 */
@Composable
fun NotificationsScreen(
    onOpenRoute: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val events by NotificationsLog.events.collectAsState()

    var filter by remember { mutableStateOf(NotifFilter.ALL) }

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
                modifier = Modifier.fillMaxWidth(),
            ) {
                FilterChip(
                    selected = filter == NotifFilter.ALL,
                    onCheckedChange = { filter = NotifFilter.ALL },
                    label = { Text(stringResource(R.string.notif_filter_all)) },
                )
                FilterChip(
                    selected = filter == NotifFilter.UNREAD,
                    onCheckedChange = { filter = NotifFilter.UNREAD },
                    label = { Text(stringResource(R.string.notif_filter_unread, unread)) },
                )
                FilterChip(
                    selected = filter == NotifFilter.CONNECTION,
                    onCheckedChange = { filter = NotifFilter.CONNECTION },
                    label = { Text(stringResource(R.string.notif_filter_connection)) },
                )
                FilterChip(
                    selected = filter == NotifFilter.LOCATION,
                    onCheckedChange = { filter = NotifFilter.LOCATION },
                    label = { Text(stringResource(R.string.notif_filter_location)) },
                )
                FilterChip(
                    selected = filter == NotifFilter.SECURITY,
                    onCheckedChange = { filter = NotifFilter.SECURITY },
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
                    androidx.compose.material3.TextButton(
                        onClick = { NotificationsLog.markAllRead(context) },
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) {
                        Text(stringResource(R.string.notif_action_mark_all))
                    }
                }
                androidx.compose.material3.TextButton(
                    onClick = { /* clear history handled by host */ },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text(stringResource(R.string.notif_action_clear))
                }
            }
        }
    }
}

/** One notification event row. */
@Composable
private fun NotificationRow(
    event: NotifEvent,
    onMarkRead: () -> Unit,
    onTap: () -> Unit,
) {
    val context = LocalContext.current
    val category = NotifCenter.notifCategoryOf(event.type)
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
                modifier = Modifier.size(8.dp).background(
                    MaterialTheme.colorScheme.primary, CircleShape
                ),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = event.type,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
            )
            Text(
                text = event.arg ?: "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (category != null) {
            Chip(
                onClick = null,
                label = { Text(category.name) },
            )
        }
    }
}

/** Apply NotifFilter to a list (delegates to NotifCenter). */
fun List<NotifEvent>.applyNotifFilter(filter: NotifFilter): List<NotifEvent> =
    when (filter) {
        NotifFilter.ALL -> this
        NotifFilter.UNREAD -> filter { it.readAt == null }
        NotifFilter.CONNECTION -> filter { NotifCenter.notifCategoryOf(it.type) == NotifCenter.NotifCategory.CONNECTION }
        NotifFilter.LOCATION -> filter { NotifCenter.notifCategoryOf(it.type) == NotifCenter.NotifCategory.LOCATION }
        NotifFilter.SECURITY -> filter { NotifCenter.notifCategoryOf(it.type) == NotifCenter.NotifCategory.SECURITY }
    }
