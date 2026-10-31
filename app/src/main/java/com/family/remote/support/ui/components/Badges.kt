package com.family.remote.support.ui.components

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.family.remote.support.PRESENCE_ONLINE_MILLIS
import com.family.remote.support.PRESENCE_STALE_MILLIS
import com.family.remote.support.R
import com.family.remote.support.watchDevicePresence
import kotlinx.coroutines.delay

// ═══════════════════════════════════════════════════════════════════════
// M3 (v0.31.0) — badge/chip family of the component library
// (FRONTEND_ARCHITECTURE §6, agent-ownership "Badges.kt"). Moved VERBATIM
// out of MainActivity.kt: sessionStatus / sessionBadgeKind /
// statusChipColors / StatusBadge (Phase 11.6 status system), PresenceBadge
// (Phase 5) and the top-bar PresenceChip (Phase 11). No visual or
// behavioral change — only visibility (private → public) and the package,
// so home/pairing/settings share ONE implementation of every badge
// (master rule: one coherent system, no second variant).
// ═══════════════════════════════════════════════════════════════════════

/** Phase 11 — honest localized name for a session status. v0.28.0 (Phase 19.2). */
fun sessionStatus(context: Context, status: String): String = when (status) {
    "REQUESTED" -> context.getString(R.string.st_requested)
    "ACTIVE" -> context.getString(R.string.st_active)
    "ENDED" -> context.getString(R.string.st_ended)
    "DENIED" -> context.getString(R.string.st_denied)
    "CANCELLED" -> context.getString(R.string.st_cancelled)
    "EXPIRED" -> context.getString(R.string.st_expired)
    else -> status
}

/** Semantic bucket for a session status: success / info / error / neutral. */
fun sessionBadgeKind(status: String): String = when (status) {
    "ACTIVE" -> "success"
    "REQUESTED" -> "info"
    "DENIED", "CANCELLED", "EXPIRED" -> "error"
    else -> "neutral"
}

/** Theme-aware container/content pair for a badge kind. */
@Composable
fun statusChipColors(kind: String): Pair<Color, Color> = when (kind) {
    "success" -> Pair(
        MaterialTheme.colorScheme.tertiaryContainer,
        MaterialTheme.colorScheme.onTertiaryContainer
    )
    "info" -> Pair(
        MaterialTheme.colorScheme.primaryContainer,
        MaterialTheme.colorScheme.onPrimaryContainer
    )
    "error" -> Pair(
        MaterialTheme.colorScheme.errorContainer,
        MaterialTheme.colorScheme.onErrorContainer
    )
    else -> Pair(
        MaterialTheme.colorScheme.surfaceVariant,
        MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/** Small pill: icon + word; reused wherever a state is shown. */
@Composable
fun StatusBadge(label: String, kind: String) {
    val colors = statusChipColors(kind)
    Row(
        modifier = Modifier
            .background(colors.first, RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        when (kind) {
            "success" -> Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = colors.second,
                modifier = Modifier.size(13.dp)
            )
            "info" -> Icon(
                imageVector = Icons.Filled.Send,
                contentDescription = null,
                tint = colors.second,
                modifier = Modifier.size(12.dp)
            )
            "error" -> Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = null,
                tint = colors.second,
                modifier = Modifier.size(13.dp)
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = colors.second
        )
    }
}

/**
 * Phase 5 — live presence dot for one device (doc devicePresence/{uid}_{deviceId}).
 * Shows "Online" while the heartbeat is fresh, "Last seen … ago" up to an hour,
 * then "Offline". Refreshes the age every 10 seconds.
 */
@Composable
fun PresenceBadge(uid: String, deviceId: String) {
    var heartbeatAt by remember(uid, deviceId) { mutableStateOf<Long?>(null) }
    var unavailable by remember(uid, deviceId) { mutableStateOf(false) }
    var nowMillis by remember { mutableStateOf(System.currentTimeMillis()) }

    LaunchedEffect(uid, deviceId) {
        while (true) {
            nowMillis = System.currentTimeMillis()
            delay(10_000)
        }
    }
    DisposableEffect(uid, deviceId) {
        val stopWatching = watchDevicePresence(
            uid,
            deviceId,
            onChange = { view ->
                unavailable = false
                heartbeatAt = view?.heartbeatAtMillis
            },
            onError = { unavailable = true }
        )
        onDispose { stopWatching() }
    }

    // Phase 20 (v0.29.0): localized badge labels — the hardcoded English
    // strings shipped to every locale; the color check below now keys off
    // a boolean instead of comparing the rendered text to "Online".
    val age = heartbeatAt?.let { nowMillis - it } ?: -1L
    val online = !unavailable && heartbeatAt != null && heartbeatAt != 0L &&
        age < PRESENCE_ONLINE_MILLIS
    val label = when {
        unavailable -> stringResource(R.string.pres_unavailable)
        heartbeatAt == null || heartbeatAt == 0L -> stringResource(R.string.pres_never_seen)
        online -> stringResource(R.string.pres_online)
        age < PRESENCE_STALE_MILLIS -> stringResource(R.string.pres_last_seen_min, (age / 60000).toInt())
        else -> stringResource(R.string.pres_offline)
    }
    // Phase 11 — no leading " · ": the badge now always sits on its own
    // line (relationship rows and the activity feed), not appended inline.
    Text(
        text = label,
        style = MaterialTheme.typography.bodySmall,
        color = when {
            online -> MaterialTheme.colorScheme.primary
            unavailable -> MaterialTheme.colorScheme.error
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        fontWeight = FontWeight.Medium
    )
}

/**
 * Phase 11 — tiny live presence chip for the top bar: green-ish dot while
 * this device's heartbeat is fresh, muted otherwise. Honest label only.
 */
@Composable
fun PresenceChip(uid: String?, deviceId: String) {
    var heartbeatAt by remember(uid, deviceId) { mutableStateOf<Long?>(null) }
    var unavailable by remember(uid, deviceId) { mutableStateOf(false) }
    var nowMillis by remember { mutableStateOf(System.currentTimeMillis()) }

    LaunchedEffect(uid, deviceId) {
        while (true) {
            nowMillis = System.currentTimeMillis()
            delay(10_000)
        }
    }
    DisposableEffect(uid, deviceId) {
        val stopWatching = if (uid != null && deviceId.isNotBlank()) {
            watchDevicePresence(
                uid,
                deviceId,
                onChange = { view ->
                    unavailable = false
                    heartbeatAt = view?.heartbeatAtMillis
                },
                onError = { unavailable = true }
            )
        } else {
            null
        }
        onDispose { stopWatching?.invoke() }
    }

    val age = heartbeatAt?.let { nowMillis - it } ?: -1L
    val online = !unavailable && heartbeatAt != null && heartbeatAt != 0L &&
        age >= 0 && age < PRESENCE_ONLINE_MILLIS
    val label = when {
        uid == null || deviceId.isBlank() -> "…"
        unavailable -> stringResource(R.string.pres_unavailable)
        online -> stringResource(R.string.pres_online)
        heartbeatAt == null || heartbeatAt == 0L -> "…"
        else -> stringResource(R.string.pres_offline)
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(
                    color = when {
                        online -> MaterialTheme.colorScheme.tertiary
                        unavailable -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    },
                    shape = CircleShape
                )
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
