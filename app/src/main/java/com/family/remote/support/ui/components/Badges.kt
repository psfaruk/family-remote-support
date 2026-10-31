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
import com.family.remote.support.SessionView
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

/**
 * v0.37.0 (dedupe wave) — the "who ended this session" suffix, promoted
 * from the two drifting copies (AddDeviceTab's `byWhom` and
 * ActivityScreens' local `endedByLabel`). endedBy stores the ender's UID
 * (or "timeout"); the label is "" when unknown, so callers append it
 * unconditionally (audit duplicate cluster #4).
 */
fun endedByLabel(context: Context, s: SessionView): String = when (s.endedBy) {
    null, "" -> ""
    "timeout" -> context.getString(R.string.ended_auto)
    s.helperUid -> context.getString(R.string.ended_by_helper)
    s.recipientUid -> context.getString(R.string.ended_by_recipient)
    else -> ""
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

// ── Presence (Phase 5 badge / Phase 11 chip) ─────────────────────────────

// v0.37.0 (dedupe wave) — shared presence computation (was 2 drifting
// copies: PresenceBadge and PresenceChip each ran their own heartbeat
// watch + 10-second age loop + age/online math). age is null while no
// USABLE heartbeat exists (doc missing, or heartbeatAt == 0 = "never
// seen") — both shells render their placeholder in exactly that case.
private data class PresenceUiState(
    val age: Long?,
    val online: Boolean,
    // The watch's routed-error flag: both shells say "unavailable" and
    // color themselves error, so it belongs in the shared state (the
    // audit sketch's age+online pair cannot preserve that branch).
    val unavailable: Boolean
)

/**
 * v0.37.0 (dedupe wave) — ONE heartbeat watch + 10-second tick feeding
 * [PresenceUiState]; the two presence shells keep only their own label +
 * color logic on top of it.
 */
@Composable
private fun rememberPresenceState(uid: String?, deviceId: String): PresenceUiState {
    var heartbeatAt by remember(uid, deviceId) { mutableStateOf<Long?>(null) }
    var unavailable by remember(uid, deviceId) { mutableStateOf(false) }
    var nowMillis by remember { mutableStateOf(System.currentTimeMillis()) }

    // ONE 10-second age tick (TickingBlock's presence cadence) — was two
    // drifting LaunchedEffect copies.
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

    // heartbeat 0 = "never seen": both shells treat it like "no heartbeat"
    // (their placeholder branch), so it collapses into age == null here.
    val usableHeartbeat = heartbeatAt?.takeIf { it != 0L }
    val age = usableHeartbeat?.let { nowMillis - it }
    // Canonical union of the two drifting copies: the chip's age >= 0 guard
    // (a future-dated heartbeat from clock skew is never "online") wins.
    val online = !unavailable && age != null && age >= 0 && age < PRESENCE_ONLINE_MILLIS
    return PresenceUiState(age = age, online = online, unavailable = unavailable)
}

/**
 * Phase 5 — live presence dot for one device (doc devicePresence/{uid}_{deviceId}).
 * Shows "Online" while the heartbeat is fresh, "Last seen … ago" up to an hour,
 * then "Offline". Refreshes the age every 10 seconds.
 */
@Composable
fun PresenceBadge(uid: String, deviceId: String) {
    // v0.37.0 (dedupe wave) — the watch + 10 s tick + age/online math now
    // live in rememberPresenceState; only this shell's label/color remain.
    val presence = rememberPresenceState(uid, deviceId)

    // Phase 20 (v0.29.0): localized badge labels — the hardcoded English
    // strings shipped to every locale; the color check below now keys off
    // a boolean instead of comparing the rendered text to "Online".
    val label = when {
        presence.unavailable -> stringResource(R.string.pres_unavailable)
        presence.age == null -> stringResource(R.string.pres_never_seen)
        presence.online -> stringResource(R.string.pres_online)
        else -> {
            val age = presence.age
            if (age != null && age < PRESENCE_STALE_MILLIS) {
                stringResource(R.string.pres_last_seen_min, (age / 60000).toInt())
            } else {
                stringResource(R.string.pres_offline)
            }
        }
    }
    // Phase 11 — no leading " · ": the badge now always sits on its own
    // line (relationship rows and the activity feed), not appended inline.
    Text(
        text = label,
        style = MaterialTheme.typography.bodySmall,
        color = when {
            presence.online -> MaterialTheme.colorScheme.primary
            presence.unavailable -> MaterialTheme.colorScheme.error
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
    // v0.37.0 (dedupe wave) — the SAME shared watch/tick/age state as the
    // badge now (rememberPresenceState); this shell keeps only its "…"
    // placeholders and muted-dot look.
    val presence = rememberPresenceState(uid, deviceId)
    val label = when {
        uid == null || deviceId.isBlank() -> "…"
        presence.unavailable -> stringResource(R.string.pres_unavailable)
        presence.online -> stringResource(R.string.pres_online)
        presence.age == null -> "…"
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
                        presence.online -> MaterialTheme.colorScheme.tertiary
                        presence.unavailable -> MaterialTheme.colorScheme.error
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
