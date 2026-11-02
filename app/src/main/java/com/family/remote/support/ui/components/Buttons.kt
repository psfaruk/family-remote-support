package com.family.remote.support.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.family.remote.support.ui.LocalWarningColor

/**
 * ui/components/Buttons.kt — the button family (FRONTEND_UI_PLAN_BN পর্ব ৩).
 *
 * Touch targets: BigButton ≥ 52 dp, every outlined/text action ≥ 48 dp
 * (UI_UX_DESIGN_SYSTEM §6 — the elders' fingers get the room). One
 * implementation per job; no second variant anywhere.
 */
// ═══════════════════════════════════════════════════════════════════

/**
 * The primary action: filled, ≥ 52 dp, control shape (10 dp). [loading]
 * swaps the leading slot for a spinner and disables the tap — the label
 * stays visible (honesty about WHAT is happening).
 */
@Composable
fun BigButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    danger: Boolean = false,
    leading: (@Composable () -> Unit)? = null,
) {
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        shape = MaterialTheme.shapes.small, // control 10
        colors = if (danger) {
            ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
            )
        } else {
            ButtonDefaults.buttonColors()
        },
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp),
        modifier = modifier.heightIn(min = 52.dp),
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
            )
        } else {
            leading?.invoke()
        }
        Spacer(Modifier.width(8.dp))
        Text(text = text, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * The secondary action: full-width outlined, ≥ 48 dp, 2 dp border —
 * visually clearly UNDER the filled [BigButton] it usually sits next to.
 * [danger] renders the error outline for destructive confirmations.
 */
@Composable
fun WideOutlinedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    danger: Boolean = false,
    leading: (@Composable () -> Unit)? = null,
) {
    val color = if (danger) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.primary
    }
    OutlinedButton(
        onClick = onClick,
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(2.dp, color),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = color),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
    ) {
        leading?.invoke()
        Spacer(Modifier.width(8.dp))
        Text(text = text)
    }
}

/**
 * A quiet inline text action ("See all", "Try again") — ≥ 48 dp through
 * its padding, three tones. Never the ONLY way to do something important.
 */
@Composable
fun TextActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    error: Boolean = false,
) {
    TextButton(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
        modifier = modifier.heightIn(min = 48.dp),
    ) {
        Text(
            text = text,
            color = if (error) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.primary
            },
        )
    }
}

/**
 * One of the two Home quick actions: 20 dp featureCard shape, 40 dp
 * primary circle icon, title + hint, ≥ 88 dp tall (FRONTEND_UI_PLAN_BN
 * §2.2-5).
 */
@Composable
fun QuickActionTile(
    icon: ImageVector,
    title: String,
    hint: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = MaterialTheme.shapes.large, // featureCard 20
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .clickable(onClick = onClick),
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .heightIn(min = 88.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null, // the title carries meaning
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(22.dp),
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** The three semantic tones a status-grid tile can carry. */
enum class StatusTone { SUCCESS, WARNING, NEUTRAL }

/**
 * One cell of the Home 2×2 status grid: soft surfaceVariant tile (10 dp
 * shape), 36 dp tone circle + icon, label + value. Status is never color
 * alone — the icon AND the value text change with the tone.
 */
@Composable
fun IconStatusTile(
    icon: ImageVector,
    label: String,
    value: String,
    tone: StatusTone,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val warning = LocalWarningColor.current
    Row(modifier = modifier) {
        Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier
                .weight(1f)
                .clip(MaterialTheme.shapes.small)
                .clickable(onClick = onClick),
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                val (bg, fg) = when (tone) {
                    StatusTone.SUCCESS ->
                        MaterialTheme.colorScheme.tertiaryContainer to
                            MaterialTheme.colorScheme.onTertiaryContainer
                    StatusTone.WARNING -> warning.copy(alpha = 0.16f) to warning
                    StatusTone.NEUTRAL ->
                        MaterialTheme.colorScheme.surface to
                            MaterialTheme.colorScheme.onSurfaceVariant
                }
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(
                            bg,
                            CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null, // label + value carry the status
                        tint = fg,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleSmall,
                )
            }
        }
    }
}
