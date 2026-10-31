package com.family.remote.support.ui.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
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
