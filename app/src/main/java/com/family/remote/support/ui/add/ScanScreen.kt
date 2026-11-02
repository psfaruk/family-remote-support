package com.family.remote.support.ui.add

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.family.remote.support.QrScanDialog
import com.family.remote.support.R
import com.family.remote.support.RelationshipView
import com.family.remote.support.REL_KIND_GUEST
import com.family.remote.support.RedemptionException
import com.family.remote.support.describeFirestoreError
import com.family.remote.support.ui.components.BackTitleRow
import com.family.remote.support.ui.components.BigButton
import com.family.remote.support.ui.components.ErrorPanel
import com.family.remote.support.ui.components.SectionCard
import com.family.remote.support.ui.components.WideOutlinedButton
import com.family.remote.support.ui.nav.AppRoutes
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * ui/add/ScanScreen.kt — the HELPER side of pairing (W3; FRONTEND_UI_PLAN_BN
 * §2.3): scan the other phone's QR through the existing [QrScanDialog]
 * (CameraX + ML Kit), or type/paste the 32-char code — then the role
 * preview sheet (Phase 12's rs_* contract: what the code will make you,
 * the three feature rows, the honest legacy note), then the one-shot
 * atomic redemption with the 200 ms ✓-pop and the permission-wizard
 * shortcut. Manual entry is imePadding-ed; errors are shown INSIDE the
 * sheet so the code stays editable for a retry.
 */
@Composable
fun ScanScreen(
    user: FirebaseUser,
    vm: AddViewModel,
    onBack: () -> Unit,
    onOpenRoute: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var scanOpen by rememberSaveable { mutableStateOf(false) }
    var manualCode by rememberSaveable { mutableStateOf("") }
    var pendingCode by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingHint by rememberSaveable { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var redeemed by remember { mutableStateOf<RelationshipView?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        BackTitleRow(title = stringResource(R.string.scan_screen_t), onBack = onBack)

        if (redeemed == null) {
            Text(
                text = stringResource(R.string.ph_role_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            BigButton(
                text = stringResource(R.string.btn_scan_short),
                onClick = { scanOpen = true },
                leading = {
                    Icon(
                        imageVector = Icons.Filled.Phone,
                        contentDescription = null,
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            )

            // ── the manual fallback (typed / pasted / dictated codes) ──
            SectionCard(
                title = stringResource(R.string.btn_import_code),
                infoText = stringResource(R.string.rs_cancel_note),
            ) {
                OutlinedTextField(
                    value = manualCode,
                    onValueChange = { manualCode = it },
                    label = { Text(stringResource(R.string.ph_code_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = stringResource(R.string.scan_manual_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                WideOutlinedButton(
                    text = stringResource(R.string.btn_import_code),
                    onClick = {
                        val raw = manualCode.trim()
                        if (raw.isNotBlank()) {
                            pendingCode = raw
                            pendingHint = null
                            error = null
                        }
                    },
                    enabled = manualCode.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            error?.let {
                ErrorPanel(message = it)
            }
        } else {
            RedeemedCard(
                rel = redeemed!!,
                onWizard = { onOpenRoute(AppRoutes.ADD_PERMISSIONS) },
                onDone = onBack,
            )
        }
    }

    // ── the camera dialog (existing, unchanged — Phase 5/12 contract) ──
    if (scanOpen) {
        QrScanDialog(
            onCode = { code, kindHint ->
                scanOpen = false
                pendingCode = code
                pendingHint = kindHint
                error = null
            },
            onDismiss = { scanOpen = false },
        )
    }

    // ── the role-preview sheet (rs_* strings, ×15 locales since v0.28) ──
    pendingCode?.let { code ->
        AlertDialog(
            onDismissRequest = { if (!busy) pendingCode = null },
            title = { Text(stringResource(R.string.rs_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = when (pendingHint) {
                            REL_KIND_GUEST -> stringResource(R.string.rs_you_guest)
                            com.family.remote.support.REL_KIND_FAMILY ->
                                stringResource(R.string.rs_you_family)
                            else -> stringResource(R.string.rs_legacy_note)
                        },
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        text = when (pendingHint) {
                            REL_KIND_GUEST -> stringResource(R.string.rs_guest_desc)
                            com.family.remote.support.REL_KIND_FAMILY ->
                                stringResource(R.string.rs_family_desc)
                            else -> stringResource(R.string.rs_verifying)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    // The three feature rows (plan's rs_feat contract).
                    Text(
                        text = "· " + stringResource(R.string.rs_feat_request),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        text = "· " + stringResource(R.string.rs_feat_perm),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        text = "· " + stringResource(R.string.rs_feat_autoend),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    SelectionContainer {
                        Text(
                            text = code,
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = stringResource(R.string.rs_check_code),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    error?.let {
                        Text(
                            text = stringResource(R.string.ph_pairing_failed, it),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            },
            confirmButton = {
                BigButton(
                    text = stringResource(R.string.btn_connect),
                    loading = busy,
                    onClick = {
                        scope.launch {
                            busy = true
                            error = null
                            try {
                                val rel = vm.redeem(context, user, code)
                                pendingCode = null
                                manualCode = ""
                                redeemed = rel
                            } catch (e: RedemptionException) {
                                error = e.message
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                error = describeFirestoreError(context, e)
                            } finally {
                                busy = false
                            }
                        }
                    },
                )
            },
            dismissButton = {
                androidx.compose.material3.TextButton(
                    onClick = { if (!busy) pendingCode = null },
                ) {
                    Text(stringResource(R.string.btn_cancel_plain))
                }
            },
        )
    }
}

/**
 * Success: the 200 ms ✓-pop (plan §1.3), the honest "you can now help …"
 * line (ph_paired_ok), the standing consent rule, and the wizard shortcut.
 */
@Composable
private fun RedeemedCard(
    rel: RelationshipView,
    onWizard: () -> Unit,
    onDone: () -> Unit,
) {
    val shown = animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(durationMillis = 200),
        label = "redeemed-pop",
    )
    SectionCard(
        title = stringResource(R.string.ph_status_paired),
        modifier = Modifier.graphicsLayer {
            scaleX = shown.value
            scaleY = shown.value
            alpha = shown.value
        },
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = stringResource(R.string.ph_status_paired),
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(40.dp),
                )
            }
        }
        Text(
            text = stringResource(
                R.string.ph_paired_ok,
                rel.recipientEmail,
                rel.recipientDeviceModel,
            ),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        if (rel.kind == REL_KIND_GUEST) {
            Text(
                text = stringResource(R.string.rs_guest_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = stringResource(R.string.rs_perm_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        BigButton(
            text = stringResource(R.string.scan_wizard_btn),
            onClick = onWizard,
            modifier = Modifier.fillMaxWidth(),
        )
        WideOutlinedButton(
            text = stringResource(R.string.cmn_ok),
            onClick = onDone,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
