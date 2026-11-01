// ═══════════════════════════════════════════════════════════════════════
// Phase 4 (v0.38.0/57) + Phase 10 (v0.41.0/60) — MovementCard
// (docs/location/LOCATION_UX_SPECIFICATION.md §6 "Movement insights
// presentation", master plan §8.3). Used by the journey-history screen
// and the demo member detail section C.
//
// Honesty rules baked in (§6): only the two honest confidence words are
// ever shown (LIMITED / MODERATE — never a fabricated numeric score);
// the estimated-speed line is HIDDEN when the value is missing or
// implausible (< 0.5 or > 120 km/h covers GPS drift and impossible
// speeds alike); null renders NOTHING (no stale classification is ever
// presented as current — the classifier itself rejects stale inputs,
// the guard here is defense in depth); insufficient evidence renders
// the single honest "Movement type unavailable" line, never a guess.
// ═══════════════════════════════════════════════════════════════════════
package com.family.remote.support.ui.tracking

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.family.remote.support.MovementConfidence
import com.family.remote.support.MovementEstimate
import com.family.remote.support.MovementKind
import com.family.remote.support.R
import com.family.remote.support.ui.components.SectionCard
import java.util.Locale

/**
 * §6 presentation of one movement classification inside a SectionCard.
 * The card title is the section's one-line "what you may see" note
 * (sec_c_note), the same convention sections A/B/E use on the detail
 * screen. Icon: Icons.Filled.PlayArrow — a core-set stand-in for a
 * movement glyph (material-icons-core has no Directions* icons).
 *
 * Phase 10: the parameter is the classifier's real [MovementEstimate]
 * (the demo fixtures map onto it via [com.family.remote.support.ui.tracking.demo.toMovementEstimate]
 * — ONE render path, the v0.37.0 single-source discipline).
 *
 * @param estimate the classification to render; null renders nothing.
 */
@Composable
internal fun MovementCard(estimate: MovementEstimate?) {
    // No fresh classification → no card at all (§6: never show a stale
    // classification as current).
    if (estimate == null) return

    if (estimate.insufficientEvidence) {
        // Fresh data, insufficient evidence inside it — the honest
        // single line, never a guessed kind (§6).
        SectionCard(
            title = stringResource(R.string.sec_c_note),
            icon = Icons.Filled.PlayArrow
        ) {
            Text(
                text = stringResource(R.string.ins_unknown),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
        return
    }

    val kindLabel = stringResource(
        when (estimate.kind) {
            MovementKind.WALK -> R.string.ins_walk
            MovementKind.CYCLE -> R.string.ins_cycle
            MovementKind.VEHICLE -> R.string.ins_vehicle
            MovementKind.STILL -> R.string.ins_still
        }
    )
    val confidenceLabel = stringResource(
        when (estimate.confidence) {
            MovementConfidence.LIMITED -> R.string.ins_conf_limited
            MovementConfidence.MODERATE -> R.string.ins_conf_moderate
        }
    )

    SectionCard(
        title = stringResource(R.string.sec_c_note),
        icon = Icons.Filled.PlayArrow
    ) {
        // Line 1 — the classified activity, always the honest "possibly"
        // wording from the string resources.
        Text(
            text = stringResource(R.string.ins_move, kindLabel),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold
        )
        // Line 2 — estimated speed, hidden when implausible (see header).
        val speed = estimate.speedKmh
        if (speed != null && speed >= 0.5 && speed <= 120.0) {
            Text(
                text = stringResource(
                    R.string.ins_speed,
                    String.format(Locale.getDefault(), "%.1f km/h", speed)
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        // Line 3 — when this was observed (the reader's clock line style).
        Text(
            text = stringResource(
                R.string.ins_obs,
                locationTimeString(estimate.observedAtMillis)
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        // Line 4 — confidence word only (LIMITED/MODERATE), never a score.
        Text(
            text = stringResource(R.string.ins_conf, confidenceLabel),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
