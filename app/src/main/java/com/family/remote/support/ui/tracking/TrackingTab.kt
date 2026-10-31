package com.family.remote.support.ui.tracking

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.family.remote.support.R
import com.family.remote.support.ui.components.EmptyState
import com.family.remote.support.ui.components.SectionCard

// ═══════════════════════════════════════════════════════════════════════
// M3 (v0.31.0) — the Tracking tab's own file (FRONTEND_ARCHITECTURE §3
// "ui/tracking/TrackingTab.kt"). This is the verbatim Phase-16 honest
// placeholder (was `LiveLocationTab` in MainActivity, same loc_* strings):
// it collects NOTHING and promises NOTHING beyond the design system's
// EmptyState. The real implementation — osmdroid map, authorized markers,
// freshness states, server-side authorization — lands in M5 exactly as
// planned in docs/LIVE_LOCATION_ARCHITECTURE.md.
// ═══════════════════════════════════════════════════════════════════════

@Composable
internal fun TrackingTab() {
    SectionCard(title = stringResource(R.string.loc_title), icon = Icons.Filled.LocationOn) {
        EmptyState(
            icon = Icons.Filled.LocationOn,
            title = stringResource(R.string.loc_coming_title),
            hint = stringResource(R.string.loc_coming_body)
        )
    }
}
