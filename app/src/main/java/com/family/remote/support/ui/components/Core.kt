package com.family.remote.support.ui.components

// ═══════════════════════════════════════════════════════════════════════
// Phase 21/M2 (v0.30.0) — first public design-system components, promoted
// VERBATIM out of MainActivity (they were file-private there). No visual
// or behavioral change: identical code, identical defaults — only the
// visibility (private → public) and the package moved, so MainActivity
// and the new Notifications screen share ONE implementation (master rule:
// one coherent system, no second variant of the same widget).
// Agent-ownership: `ui/components/*` (FRONTEND_ARCHITECTURE §6). The rest
// of the private widgets (SettingsTile, IconStatusTile, QuickActionTile,
// BigButton, StatusBadge, …) follow in the M3 extraction wave.
// ═══════════════════════════════════════════════════════════════════════

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.family.remote.support.R

/**
 * Section card: icon-marked title row + optional ⓘ button that opens the
 * LONG explanation in a popup dialog — the card body itself stays short.
 */
@Composable
fun SectionCard(
    title: String,
    icon: ImageVector? = null,
    infoText: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    var showInfo by remember { mutableStateOf(false) }
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                if (infoText != null) {
                    IconButton(
                        onClick = { showInfo = true },
                        // Phase 11.6 — 48dp touch target (was 36dp).
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Info,
                                contentDescription = stringResource(R.string.cd_details),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
            content()
        }
    }
    if (showInfo && infoText != null) {
        AlertDialog(
            onDismissRequest = { showInfo = false },
            confirmButton = {
                TextButton(onClick = { showInfo = false }) {
                    Text(stringResource(R.string.cmn_ok))
                }
            },
            title = {
                Text(text = title, fontWeight = FontWeight.Bold)
            },
            text = { Text(text = infoText) }
        )
    }
}

/**
 * v0.14.0 — chat-style list tile: avatar circle (initials or icon) + title
 * + one-line description + trailing chip/chevron. The single row shape used
 * by paired phones, devices, activity feed and account.
 */
@Composable
fun AppListTile(
    title: String,
    subtitle: String,
    initials: String? = null,
    icon: ImageVector? = null,
    avatarContainer: Color = MaterialTheme.colorScheme.primaryContainer,
    avatarContent: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    chip: String? = null,
    chipContainer: Color = MaterialTheme.colorScheme.secondaryContainer,
    chipContent: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    onClick: (() -> Unit)? = null,
    trailingIcon: ImageVector? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(avatarContainer, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            when {
                initials != null -> Text(
                    text = initials.take(2).uppercase(),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = avatarContent
                )
                icon != null -> Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = avatarContent,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (chip != null) {
            Text(
                text = chip,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = chipContent,
                maxLines = 1,
                modifier = Modifier
                    .background(chipContainer, RoundedCornerShape(999.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }
        if (trailingIcon != null) {
            Icon(
                imageVector = trailingIcon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/** v0.14.0 — friendly empty state: big icon + one line + optional action. */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    hint: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    illustrationRes: Int? = null
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (illustrationRes != null) {
            // Phase 11.6 — friendly on-device vector illustration (docs
            // §13); no remote images anywhere.
            Image(
                painter = painterResource(illustrationRes),
                contentDescription = null,
                modifier = Modifier.size(96.dp)
            )
        } else {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(44.dp)
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = hint,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (actionLabel != null && onAction != null) {
            WideOutlinedButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

/** Phase 9 (T1) — secondary action: full-width, ≥48dp tall. */
@Composable
fun WideOutlinedButton(
    enabled: Boolean = true,
    onClick: () -> Unit,
    content: @Composable RowScope.() -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
        content = content
    )
}

/**
 * v0.37.0 (dedupe wave) — the M4 detail-screen header, promoted from
 * LocationDetailScreen's private copy into the design system so ALL detail
 * screens share ONE back-arrow row (LocationDetailScreen, MemberDetailScreen
 * and SessionDetailScreen used to carry three identical copies — the audit's
 * duplicate cluster #5). Verbatim body, only the visibility moved.
 */
@Composable
fun BackTitleRow(title: String, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.cd_back)
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
    }
}
