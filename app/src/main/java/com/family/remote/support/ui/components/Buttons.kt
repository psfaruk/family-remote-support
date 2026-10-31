package com.family.remote.support.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

// ═══════════════════════════════════════════════════════════════════════
// M3 (v0.31.0) — button family of the component library
// (FRONTEND_ARCHITECTURE §6, agent-ownership "Buttons.kt"). Moved VERBATIM
// out of MainActivity.kt: BigButton (Phase 9/T1 primary action). No visual
// or behavioral change — only visibility (private → public) and the
// package, so auth/home/pairing/settings share ONE primary button
// (master rule: one coherent system, no second variant).
// ═══════════════════════════════════════════════════════════════════════

/** Phase 9 (T1) — primary action: full-width, ≥52dp tall (senior thumbs). */
@Composable
fun BigButton(
    enabled: Boolean = true,
    onClick: () -> Unit,
    content: @Composable RowScope.() -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp),
        content = content
    )
}

/**
 * Pre-M10 (v0.35.0) — the destructive secondary action (Settings sign-out).
 * Owner direction: "Sign Out Button (একবারে নিচে Red Bordered বা Outlined
 * Style-এ)" — red-OUTLINED, full-width, ≥48dp, no fill weight, so ending the
 * session reads as a deliberate final step without looking like a primary
 * action. Lives in the ONE button family (same rule as BigButton/
 * WideOutlinedButton: no per-screen variants).
 */
@Composable
fun SignOutButton(
    text: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.error),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = MaterialTheme.colorScheme.error,
            disabledContentColor = MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
        )
    ) {
        Icon(
            imageVector = Icons.Filled.ExitToApp,
            contentDescription = null,
            modifier = Modifier.size(18.dp)
        )
        Text("  " + text)
    }
}
