package com.family.remote.support.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.family.remote.support.R

// ═══════════════════════════════════════════════════════════════════════
// M3 (v0.31.0) — state components of the design system
// (FRONTEND_ARCHITECTURE §6 "States.kt", NAVIGATION_MAP §5: every route
// defines loading / empty / error / permission-denied as design-system
// components, NOT per-screen ad-hoc UI; audit risk #5: swallowed listener
// errors must surface as real, localized, retryable states).
//
// EmptyState already lives in Core.kt (M2) — the same one-variant rule
// applies here: screens compose these instead of hand-rolling panels.
// Wire-up in M3: Home session-watch loading + routed error. M4/M5 wire
// the permission panel (settings/location, tracking) — canonical
// implementation ships now so no second variant can appear.
// ═══════════════════════════════════════════════════════════════════════

/**
 * Loading placeholder: quiet grey rows (no spinner, no fake data — the
 * master rule forbids fabricated content). Used while a listener's first
 * snapshot has not arrived yet.
 */
@Composable
fun LoadingSkeleton(rows: Int = 3, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        repeat(rows.coerceIn(1, 6)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
        }
    }
}

/**
 * Honest error panel: the routed, localized failure message + optional
 * retry. Callers pass the already-mapped text (friendlyError /
 * describeFirestoreError) — this component never invents a message.
 */
@Composable
fun ErrorPanel(
    message: String,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
            onRetry?.let { retry ->
                TextButton(onClick = retry) {
                    Text(
                        text = stringResource(R.string.states_retry),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

/**
 * Permission-denied panel: what the permission is FOR (plain language) +
 * the system-settings hand-off. The app never pretends a permission was
 * granted — the panel states the purpose and hands off to the OS.
 * M4/M5 wire-up (settings/location, tracking); canonical component now.
 */
@Composable
fun PermissionDeniedPanel(
    title: String,
    explanation: String,
    modifier: Modifier = Modifier,
    onOpenSettings: (() -> Unit)? = null
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = explanation,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            onOpenSettings?.let { openSettings ->
                TextButton(onClick = openSettings) {
                    Text(text = stringResource(R.string.states_open_settings))
                }
            }
        }
    }
}
