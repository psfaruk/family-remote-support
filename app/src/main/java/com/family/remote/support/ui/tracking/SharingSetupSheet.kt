// ═══════════════════════════════════════════════════════════════════════
// Phase 4 (v0.38.0, frontend mock wave) — the LOCATION-SHARING SETUP
// WIZARD: the 6-step ModalBottomSheet that must precede every Start
// (explain → recipients → scope → retention (history only) → permission →
// confirm). It replaces M5's single consent sheet: consent is still
// mandatory, but now the owner picks WHO receives the position, for HOW
// LONG, and whether journey history is kept — before anything starts.
//
// Honesty rules baked in:
//   • every step is non-skippable and back only walks to the previous
//     SHOWN step; cancel at ANY point changes nothing (progression state
//     lives in plain remember — it dies with the sheet, never saved);
//   • the confirm step is a full read-back (recipients, scope, retention,
//     expiry) — the owner confirms exactly what will start;
//   • either FINE or COARSE grant is enough (§5: an "Approximate"-only
//     user chose deliberately); a denial shows the canonical
//     permission-denied panel with the system-settings hand-off, never a
//     fake "granted";
//   • in DEMO mode the real permission launcher is NEVER launched — the
//     explanatory texts render, zero real side effects;
//   • Phase 4 is the frontend wave: onConfirmed persists only the LOCAL
//     consent record (LocationSharingPrefs scope/retention keys); the
//     authoritative authorization document ships in Phase 8
//     (locationAuthorizations) and never gates this UI's honesty.
// ═══════════════════════════════════════════════════════════════════════
package com.family.remote.support.ui.tracking

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.family.remote.support.R
import com.family.remote.support.RelationshipView
import com.family.remote.support.hasAnyLocationPermission
import com.family.remote.support.ui.components.BigButton
import com.family.remote.support.ui.components.PermissionDeniedPanel
import com.family.remote.support.ui.components.RadioOptionRow
import com.family.remote.support.ui.components.WideOutlinedButton
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** One candidate sharing recipient: the OTHER party of a relationship.
 * [kind] ('family' | 'guest') rides along so the Phase 8 authorization
 * carries it — the rules keep requiring a live pairing for guests after
 * their window lapses (family: the authorization IS the consent). */
data class RecipientOption(
    val uid: String,
    val email: String,
    val deviceModel: String,
    val kind: String = "family"
)

/**
 * The other party of a relationship as a sharing-recipient candidate:
 * the helper account when I am the recipient (this phone is being
 * helped), the recipient account otherwise (I am the helper — the
 * recipient side carries the tracked device's identity). The helper side
 * carries no device model in the relationship doc, so its option honestly
 * shows none. Null when my side cannot be determined or the other
 * party's uid is blank — an honest absence, never a guessed recipient.
 */
internal fun recipientOptionFrom(rel: RelationshipView, myUid: String?): RecipientOption? {
    if (myUid == null) return null
    val option = if (rel.recipientUid == myUid) {
        RecipientOption(
            uid = rel.helperUid,
            email = rel.helperEmail,
            deviceModel = "",
            kind = rel.kind
        )
    } else {
        RecipientOption(
            uid = rel.recipientUid,
            email = rel.recipientEmail,
            deviceModel = rel.recipientDeviceModel,
            kind = rel.kind
        )
    }
    // No uid → the other party cannot be identified: an honest absence.
    if (option.uid.isBlank()) return null
    // A blank email still identifies honestly by uid — never invented.
    return if (option.email.isBlank()) option.copy(email = option.uid) else option
}

/** The wizard's six steps, in order; RETENTION renders only for WITH_HISTORY scopes. */
private enum class SetupStep { EXPLAIN, RECIPIENTS, SCOPE, RETENTION, PERMISSIONS, CONFIRM }

/** The temporary-scope duration choices (chips under the TEMPORARY radio row). */
private enum class TempChoice { ONE_HOUR, EIGHT_HOURS, UNTIL_MIDNIGHT }

/** The sheet's fixed step total (the header counter always says "of 6"). */
private const val SETUP_STEP_COUNT = 6

/**
 * The 6-step sharing-setup sheet.
 *
 * @param recipients   the candidate recipients (TrackingUiState.relationships mapped
 *                     through [recipientOptionFrom]; demo mode passes the fictional members).
 * @param isDemo       demo mode: the real permission launcher is never launched and
 *                     onConfirmed's caller flips demo-local state only.
 * @param onDismiss    cancel — nothing has changed at that point, by construction.
 * @param onConfirmed  the confirm read-back: selected recipients, scope, temporary
 *                     expiry (null unless TEMPORARY), history retention.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SharingSetupSheet(
    recipients: List<RecipientOption>,
    isDemo: Boolean,
    onDismiss: () -> Unit,
    onConfirmed: (selected: List<RecipientOption>, scope: ScopeKind, tempMillis: Long?, retention: RetentionKind) -> Unit,
    // Phase 9 — opens the owner-side "Delete existing journey history"
    // flow from the confirm step (spec §9's delete home #1). Null (demo)
    // hides the affordance — demo mode never offers real-data actions.
    onDeleteHistory: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState()

    // Progression state in plain remember (NOT rememberSaveable): cancel at
    // any point must leave NOTHING behind — the state dies with the sheet.
    var step by remember { mutableStateOf(SetupStep.EXPLAIN) }
    var selectedUids by remember { mutableStateOf(setOf<String>()) }
    var scope by remember { mutableStateOf(ScopeKind.UNTIL_STOP) }
    var tempChoice by remember { mutableStateOf(TempChoice.ONE_HOUR) }
    var retention by remember { mutableStateOf(RetentionKind.NONE) }
    var permissionDenied by remember { mutableStateOf(false) }

    // The runtime FINE+COARSE request (the OwnSharingCard M5 pattern):
    // requesting BOTH while-in-use permissions at once; "Approximate"
    // (coarse only) is still enough to share a position, so EITHER grant
    // proceeds — anything less would dishonestly deny a user who
    // deliberately chose approximate.
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val fine = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarse = grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (fine || coarse) {
            permissionDenied = false
            step = SetupStep.CONFIRM
        } else {
            permissionDenied = true
        }
    }

    // Forward/backward through the SHOWN steps only (RETENTION is skipped
    // unless the scope keeps history — the numbering stays monotonic).
    val nextStep: SetupStep = when (step) {
        SetupStep.EXPLAIN -> SetupStep.RECIPIENTS
        SetupStep.RECIPIENTS -> SetupStep.SCOPE
        SetupStep.SCOPE ->
            if (scope == ScopeKind.WITH_HISTORY) SetupStep.RETENTION else SetupStep.PERMISSIONS
        SetupStep.RETENTION -> SetupStep.PERMISSIONS
        SetupStep.PERMISSIONS -> SetupStep.CONFIRM
        SetupStep.CONFIRM -> SetupStep.CONFIRM
    }
    val prevStep: SetupStep = when (step) {
        SetupStep.EXPLAIN -> SetupStep.EXPLAIN
        SetupStep.RECIPIENTS -> SetupStep.EXPLAIN
        SetupStep.SCOPE -> SetupStep.RECIPIENTS
        SetupStep.RETENTION -> SetupStep.SCOPE
        SetupStep.PERMISSIONS ->
            if (scope == ScopeKind.WITH_HISTORY) SetupStep.RETENTION else SetupStep.SCOPE
        SetupStep.CONFIRM ->
            if (scope == ScopeKind.WITH_HISTORY) SetupStep.RETENTION else SetupStep.PERMISSIONS
    }
    val nextEnabled: Boolean = when (step) {
        // Nobody selected → the honest "pick at least one" line + a disabled Next.
        SetupStep.RECIPIENTS -> selectedUids.isNotEmpty()
        // Real path: Next waits for an actual grant (either one); the BigButton
        // below launches the ask. Demo: texts only, nothing to wait for.
        SetupStep.PERMISSIONS -> isDemo || hasAnyLocationPermission(context)
        else -> true
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 560.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header on EVERY step: the sheet title + the honest step counter
            // (always "of 6" — the retention step simply never shows otherwise).
            Text(
                text = stringResource(R.string.setup_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = stringResource(R.string.setup_step, step.ordinal + 1, SETUP_STEP_COUNT),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            HorizontalDivider()

            when (step) {
                SetupStep.EXPLAIN -> ExplainStep()
                SetupStep.RECIPIENTS -> RecipientsStep(
                    recipients = recipients,
                    selectedUids = selectedUids,
                    onToggle = { uid ->
                        selectedUids =
                            if (uid in selectedUids) selectedUids - uid else selectedUids + uid
                    }
                )
                SetupStep.SCOPE -> ScopeStep(
                    scope = scope,
                    tempChoice = tempChoice,
                    onPickScope = { picked -> scope = picked },
                    onPickTemp = { picked -> tempChoice = picked }
                )
                SetupStep.RETENTION -> RetentionStep(
                    retention = retention,
                    onPick = { picked -> retention = picked }
                )
                SetupStep.PERMISSIONS -> PermissionsStep(
                    isDemo = isDemo,
                    withHistory = scope == ScopeKind.WITH_HISTORY,
                    permissionDenied = permissionDenied,
                    onRequestPermission = {
                        permissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            )
                        )
                    }
                )
                SetupStep.CONFIRM -> ConfirmStep(
                    recipients = recipients,
                    selectedUids = selectedUids,
                    scope = scope,
                    tempChoice = tempChoice,
                    retention = retention,
                    onDeleteHistory = onDeleteHistory,
                    onConfirmed = onConfirmed
                )
            }

            HorizontalDivider()
            // Navigation row: Back (Cancel on the first step) / Next — 48 dp
            // targets. Cancel at any point = nothing changed.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (step == SetupStep.EXPLAIN) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Text(stringResource(R.string.setup_cancel))
                    }
                } else {
                    TextButton(
                        onClick = { step = prevStep },
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Text(stringResource(R.string.cd_back))
                    }
                }
                if (step != SetupStep.CONFIRM) {
                    TextButton(
                        enabled = nextEnabled,
                        onClick = { step = nextStep },
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Text(stringResource(R.string.setup_next))
                    }
                }
            }
        }
    }
}

// ── Step bodies ──────────────────────────────────────────────────────────

/** Step 1 — what location sharing means (the five self-labeled explain lines). */
@Composable
private fun ExplainStep() {
    Text(
        text = stringResource(R.string.setup_explain_title),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold
    )
    listOf(
        R.string.setup_explain_what,
        R.string.setup_explain_who,
        R.string.setup_explain_history,
        R.string.setup_explain_control,
        R.string.setup_explain_offline
    ).forEach { lineRes ->
        Text(
            text = stringResource(lineRes),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Step 2 — multi-select recipients (email + device model rows, checkbox rows). */
@Composable
private fun RecipientsStep(
    recipients: List<RecipientOption>,
    selectedUids: Set<String>,
    onToggle: (String) -> Unit
) {
    Text(
        text = stringResource(R.string.setup_pick_title),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold
    )
    if (recipients.isEmpty()) {
        // No ACTIVE relationship at all — nothing to select, Next stays disabled.
        Text(
            text = stringResource(R.string.setup_pick_none),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    } else {
        recipients.forEach { option ->
            RecipientRow(
                option = option,
                checked = option.uid in selectedUids,
                onToggle = { onToggle(option.uid) }
            )
        }
        if (selectedUids.isEmpty()) {
            Text(
                text = stringResource(R.string.setup_pick_none),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    // The standing rule, right where recipients are picked: being paired
    // (or listed here) is never permission by itself.
    Text(
        text = stringResource(R.string.rel_always_permission),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/** One selectable recipient: whole-row toggle + checkbox + email/device lines. */
@Composable
private fun RecipientRow(
    option: RecipientOption,
    checked: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Checkbox(checked = checked, onCheckedChange = { onToggle() })
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = option.email,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = if (checked) FontWeight.Bold else FontWeight.Normal
            )
            if (option.deviceModel.isNotBlank()) {
                Text(
                    text = option.deviceModel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** Step 3 — scope radio rows (each with its help line) + the temporary-duration chips. */
@Composable
private fun ScopeStep(
    scope: ScopeKind,
    tempChoice: TempChoice,
    onPickScope: (ScopeKind) -> Unit,
    onPickTemp: (TempChoice) -> Unit
) {
    Text(
        text = stringResource(R.string.setup_scope_title),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold
    )
    Text(
        text = stringResource(R.string.setup_scope_body),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    ScopeOptionBlock(
        label = scopeLabel(ScopeKind.ONCE),
        help = stringResource(R.string.scope_once_help),
        selected = scope == ScopeKind.ONCE,
        onClick = { onPickScope(ScopeKind.ONCE) }
    )
    ScopeOptionBlock(
        label = scopeLabel(ScopeKind.TEMPORARY),
        help = stringResource(R.string.scope_temp_help),
        selected = scope == ScopeKind.TEMPORARY,
        onClick = { onPickScope(ScopeKind.TEMPORARY) }
    )
    if (scope == ScopeKind.TEMPORARY) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TempChip(
                label = stringResource(R.string.scope_1h),
                selected = tempChoice == TempChoice.ONE_HOUR,
                onClick = { onPickTemp(TempChoice.ONE_HOUR) }
            )
            TempChip(
                label = stringResource(R.string.scope_8h),
                selected = tempChoice == TempChoice.EIGHT_HOURS,
                onClick = { onPickTemp(TempChoice.EIGHT_HOURS) }
            )
            TempChip(
                label = stringResource(R.string.scope_midnight),
                selected = tempChoice == TempChoice.UNTIL_MIDNIGHT,
                onClick = { onPickTemp(TempChoice.UNTIL_MIDNIGHT) }
            )
        }
    }
    ScopeOptionBlock(
        label = scopeLabel(ScopeKind.UNTIL_STOP),
        help = stringResource(R.string.scope_until_stop_help),
        selected = scope == ScopeKind.UNTIL_STOP,
        onClick = { onPickScope(ScopeKind.UNTIL_STOP) }
    )
    ScopeOptionBlock(
        label = scopeLabel(ScopeKind.WITH_HISTORY),
        help = stringResource(R.string.scope_with_history_help),
        selected = scope == ScopeKind.WITH_HISTORY,
        onClick = { onPickScope(ScopeKind.WITH_HISTORY) }
    )
}

/** One scope option: the shared RadioOptionRow plus its help line under the label. */
@Composable
private fun ScopeOptionBlock(
    label: String,
    help: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    // v0.38.0 — the radio row is the shared design-system RadioOptionRow
    // (promoted from SettingsTab's private copy this wave).
    RadioOptionRow(label = label, selected = selected, onClick = onClick)
    // Help indented into the RadioButton's label column (48 dp control + 10 dp spacing).
    Text(
        text = help,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 58.dp)
    )
}

/** One temporary-duration chip (1 h / 8 h / until midnight). */
@Composable
private fun TempChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        color = if (selected) {
            MaterialTheme.colorScheme.onSecondaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                }
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    )
}

/** Step 4 — history retention (only shown for WITH_HISTORY scopes). */
@Composable
private fun RetentionStep(
    retention: RetentionKind,
    onPick: (RetentionKind) -> Unit
) {
    Text(
        text = stringResource(R.string.setup_ret_title),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold
    )
    Text(
        text = stringResource(R.string.setup_ret_body),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    RadioOptionRow(
        label = retentionLabel(RetentionKind.NONE),
        selected = retention == RetentionKind.NONE,
        onClick = { onPick(RetentionKind.NONE) }
    )
    RadioOptionRow(
        label = retentionLabel(RetentionKind.H24),
        selected = retention == RetentionKind.H24,
        onClick = { onPick(RetentionKind.H24) }
    )
    RadioOptionRow(
        label = retentionLabel(RetentionKind.D7),
        selected = retention == RetentionKind.D7,
        onClick = { onPick(RetentionKind.D7) }
    )
    RadioOptionRow(
        label = retentionLabel(RetentionKind.D30),
        selected = retention == RetentionKind.D30,
        onClick = { onPick(RetentionKind.D30) }
    )
    Text(
        text = stringResource(R.string.ret_note),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/** Step 5 — the permission ask (texts always; the real launcher only when not demo). */
@Composable
private fun PermissionsStep(
    isDemo: Boolean,
    withHistory: Boolean,
    permissionDenied: Boolean,
    onRequestPermission: () -> Unit
) {
    val context = LocalContext.current
    Text(
        text = stringResource(R.string.setup_perm_title),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold
    )
    Text(
        text = stringResource(R.string.setup_perm_body),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    if (withHistory) {
        // Only offered when history is on — the honest battery/saver caveat.
        Text(
            text = stringResource(R.string.bg_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    if (!isDemo) {
        if (!hasAnyLocationPermission(context)) {
            BigButton(onClick = onRequestPermission) {
                Text(stringResource(R.string.loc_share_start))
            }
        }
        if (permissionDenied) {
            // Canonical denied panel with the system-settings hand-off —
            // the app never pretends a permission was granted.
            PermissionDeniedPanel(
                title = stringResource(R.string.loc_denied_title),
                explanation = stringResource(R.string.loc_denied_body),
                onOpenSettings = { openAppSettings(context) }
            )
        }
    }
    // DEMO mode: the explanatory texts above are all that renders — the
    // real launcher is never launched from demo (zero real side effects).
}

/** Step 6 — the read-back summary + the one confirm action. */
@Composable
private fun ConfirmStep(
    recipients: List<RecipientOption>,
    selectedUids: Set<String>,
    scope: ScopeKind,
    tempChoice: TempChoice,
    retention: RetentionKind,
    onDeleteHistory: (() -> Unit)? = null,
    onConfirmed: (selected: List<RecipientOption>, scope: ScopeKind, tempMillis: Long?, retention: RetentionKind) -> Unit
) {
    val selected = recipients.filter { it.uid in selectedUids }
    val tempMillis: Long? = if (scope == ScopeKind.TEMPORARY) {
        tempExpiryMillis(tempChoice)
    } else {
        null
    }
    Text(
        text = stringResource(R.string.setup_confirm_title),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold
    )
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = stringResource(
                    R.string.setup_confirm_recipients,
                    selected.joinToString { it.email }
                ),
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = stringResource(R.string.setup_confirm_scope, scopeLabel(scope)),
                style = MaterialTheme.typography.bodyMedium
            )
            if (scope == ScopeKind.WITH_HISTORY) {
                Text(
                    text = stringResource(
                        R.string.setup_confirm_retention,
                        retentionLabel(retention)
                    ),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            if (tempMillis != null) {
                Text(
                    text = stringResource(
                        R.string.setup_confirm_expiry,
                        SimpleDateFormat("d MMM, HH:mm", Locale.getDefault()).format(Date(tempMillis))
                    ),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            // Phase 9 — the owner-side delete affordance rides the WITH-
            // HISTORY confirm step (spec §9: "owner-side 'Delete my
            // history' lives in the wizard confirm screen & Settings").
            if (scope == ScopeKind.WITH_HISTORY && onDeleteHistory != null) {
                WideOutlinedButton(onClick = onDeleteHistory) {
                    Text(stringResource(R.string.setup_del_hist))
                }
            }
        }
    }
    BigButton(
        onClick = { onConfirmed(selected, scope, tempMillis, retention) }
    ) {
        Text(stringResource(R.string.loc_share_start))
    }
}

/** The TEMPORARY scope's end from the current clock (recomputed at confirm time). */
private fun tempExpiryMillis(choice: TempChoice): Long {
    val nowMillis = System.currentTimeMillis()
    return when (choice) {
        TempChoice.ONE_HOUR -> nowMillis + 3_600_000L
        TempChoice.EIGHT_HOURS -> nowMillis + 8 * 3_600_000L
        TempChoice.UNTIL_MIDNIGHT -> nextLocalMidnightMillis(nowMillis)
    }
}
