package com.family.remote.support.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.family.remote.support.PRESENCE_ONLINE_MILLIS
import com.family.remote.support.PRESENCE_STALE_MILLIS
import com.family.remote.support.PresenceView
import com.family.remote.support.R
import com.family.remote.support.REL_KIND_GUEST
import com.family.remote.support.ui.LocalWarningColor

/**
 * ui/components/Badges.kt — the badge/chip family (FRONTEND_UI_PLAN_BN
 * পর্ব ৩ · পর্ব ৫-এর টাইমার-টেবিল).
 *
 * Status is NEVER color alone: every badge carries a text label (and most
 * a glyph too), per the design-system master rule.
 */
// ═══════════════════════════════════════════════════════════════════

/** The label + glyph of a session status, from values/strings (st_*). */
@Composable
fun sessionStatusLabel(status: String): String = when (status) {
    "REQUESTED" -> stringResource(R.string.st_requested)
    "ACTIVE" -> stringResource(R.string.st_active)
    "ENDED" -> stringResource(R.string.st_ended)
    "DENIED" -> stringResource(R.string.st_denied)
    "CANCELLED" -> stringResource(R.string.st_cancelled)
    "EXPIRED" -> stringResource(R.string.st_expired)
    else -> status
}

/** A session status chip: color + glyph + localized label (st_*). */
@Composable
fun StatusBadge(status: String) {
    val warning = LocalWarningColor.current
    val (bg, fg) = when (status) {
        "REQUESTED" -> warning.copy(alpha = 0.16f) to warning
        "ACTIVE" ->
            MaterialTheme.colorScheme.tertiaryContainer to
                MaterialTheme.colorScheme.onTertiaryContainer
        "DENIED" ->
            MaterialTheme.colorScheme.errorContainer to
                MaterialTheme.colorScheme.onErrorContainer
        "EXPIRED" -> warning.copy(alpha = 0.16f) to warning
        else ->
            MaterialTheme.colorScheme.surfaceVariant to
                MaterialTheme.colorScheme.onSurfaceVariant
    }
    val glyph = when (status) {
        "ACTIVE" -> "▶"
        "REQUESTED" -> "⏳"
        "ENDED" -> "■"
        "DENIED" -> "✕"
        "CANCELLED" -> "↩"
        else -> "⌛"
    }
    Surface(shape = CircleShape, color = bg) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        ) {
            Text(text = glyph, style = MaterialTheme.typography.labelMedium, color = fg)
            Text(
                text = sessionStatusLabel(status),
                style = MaterialTheme.typography.labelMedium,
                color = fg,
            )
        }
    }
}

/**
 * The relationship kind chip. FAMILY → the amber secondaryContainer chip
 * (§92: family warmth). GUEST → teal tertiaryContainer with a live
 * countdown that turns error-red inside the last 15 minutes of the window
 * (plan §3 GuestCountdownChip; [now] comes from the caller's ticker so
 * only rows that NEED a ticking chip recompose).
 */
@Composable
fun KindChip(kind: String, guestExpiresAtMillis: Long, now: Long) {
    if (kind != REL_KIND_GUEST) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer,
        ) {
            Text(
                text = stringResource(R.string.sess_family),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
        return
    }
    val left = (guestExpiresAtMillis - now).coerceAtLeast(0L)
    val urgent = left < 15 * 60_000L
    val locale = appLocale()
    val (bg, fg) = if (urgent) {
        MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
    } else {
        MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
    }
    Surface(shape = CircleShape, color = bg) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        ) {
            Text(
                text = stringResource(R.string.sess_guest),
                style = MaterialTheme.typography.labelMedium,
                color = fg,
            )
            Text(
                text = "⏳ " + Fmt.hms(left, locale),
                style = MaterialTheme.typography.labelMedium,
                color = fg,
            )
        }
    }
}

/** Which presence state a heartbeat resolves to (PresenceRepo windows). */
enum class PresenceKind { ONLINE, SEEN, OFFLINE, UNAVAILABLE }

/**
 * The linked phone's presence: a colored dot + honest label
 * (pres_online / pres_last_seen_min / pres_offline / pres_unavailable).
 * [presence] may be null (no doc / unreadable) — that is a state, not an
 * error, and renders as "presence unavailable".
 */
@Composable
fun PresenceBadge(presence: PresenceView?, now: Long) {
    val (kind, label) = presenceMeta(presence, now)
    val dot = when (kind) {
        PresenceKind.ONLINE -> MaterialTheme.colorScheme.tertiary
        PresenceKind.SEEN -> LocalWarningColor.current
        PresenceKind.OFFLINE -> MaterialTheme.colorScheme.outline
        PresenceKind.UNAVAILABLE -> MaterialTheme.colorScheme.outline
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(dot, CircleShape),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Pure presence resolution (unit-testable; UI only renders it). */
fun presenceKind(heartbeatAtMillis: Long, now: Long): PresenceKind {
    val age = now - heartbeatAtMillis
    return when {
        heartbeatAtMillis <= 0L -> PresenceKind.UNAVAILABLE
        age < PRESENCE_ONLINE_MILLIS -> PresenceKind.ONLINE
        age < PRESENCE_STALE_MILLIS -> PresenceKind.SEEN
        else -> PresenceKind.OFFLINE
    }
}

/** [presenceKind] + the localized label (pres_* strings). */
@Composable
fun presenceMeta(presence: PresenceView?, now: Long): Pair<PresenceKind, String> {
    if (presence == null || presence.heartbeatAtMillis <= 0L) {
        return PresenceKind.UNAVAILABLE to stringResource(R.string.pres_unavailable)
    }
    return when (presenceKind(presence.heartbeatAtMillis, now)) {
        PresenceKind.ONLINE -> PresenceKind.ONLINE to stringResource(R.string.pres_online)
        PresenceKind.SEEN -> PresenceKind.SEEN to stringResource(
            R.string.pres_last_seen_min,
            ((now - presence.heartbeatAtMillis) / 60_000L).toInt().coerceAtLeast(1),
        )
        PresenceKind.OFFLINE -> PresenceKind.OFFLINE to stringResource(R.string.pres_offline)
        PresenceKind.UNAVAILABLE -> PresenceKind.UNAVAILABLE to stringResource(R.string.pres_unavailable)
    }
}
