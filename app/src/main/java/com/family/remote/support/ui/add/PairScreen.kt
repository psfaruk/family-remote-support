package com.family.remote.support.ui.add

import android.content.Intent
import android.os.Build
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.family.remote.support.R
import com.family.remote.support.REL_KIND_GUEST
import com.family.remote.support.QrBadge
import com.family.remote.support.ServerClock
import com.family.remote.support.buildQrShareIntent
import com.family.remote.support.copyToClipboard
import com.family.remote.support.describeFirestoreError
import com.family.remote.support.qrBitmap
import com.family.remote.support.stableDeviceId
import com.family.remote.support.ui.LocalWarningColor
import com.family.remote.support.ui.components.BackTitleRow
import com.family.remote.support.ui.components.BigButton
import com.family.remote.support.ui.components.ErrorPanel
import com.family.remote.support.ui.components.SectionCard
import com.family.remote.support.ui.components.TextActionButton
import com.family.remote.support.ui.components.WideOutlinedButton
import com.family.remote.support.ui.components.appLocale
import com.family.remote.support.ui.components.tickingNow
import com.google.firebase.auth.FirebaseUser

/**
 * ui/add/PairScreen.kt — the INVITER side of pairing (W3; FRONTEND_UI_PLAN_BN
 * §2.3's PairSheet): white QR plate (200 dp, ECC-H badge in the middle),
 * the 5-minute countdown chip (≤30 s → warning tone), the mono code with
 * copy + share(PNG+text), and the honest FSM ending states — PAIRED with a
 * 200 ms ✓-pop, CANCELLED, and EXPIRED (derived against ServerClock — audit
 * P7: a phone with a wrong clock must never fake-kill a live code).
 *
 * One screen serves both routes: add/family (permanent) and add/guest
 * (2-hour window) — [kind] is frozen into the invitation at creation.
 */
@Composable
fun PairScreen(
    user: FirebaseUser,
    vm: AddViewModel,
    kind: String,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val state by vm.pairing.collectAsState()
    val locale = appLocale()
    val warning = LocalWarningColor.current

    val titleRes = if (kind == REL_KIND_GUEST) R.string.ph_guest_code_title else R.string.ph_family_code_title
    val hintRes = if (kind == REL_KIND_GUEST) R.string.ph_kind_guest_short else R.string.ph_kind_family_short

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        BackTitleRow(title = stringResource(titleRes), onBack = onBack)

        when (state.phase) {
            AddViewModel.PairPhase.IDLE, AddViewModel.PairPhase.CREATING -> {
                // ── before the code exists: the honest explanation ──
                SectionCard(title = stringResource(R.string.ph_give_code_hint)) {
                    Text(
                        text = stringResource(R.string.ph_two_ways_info),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = stringResource(hintRes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (kind == REL_KIND_GUEST) {
                        Text(
                            text = stringResource(R.string.rs_guest_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.ph_family_only_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                BigButton(
                    text = stringResource(R.string.pair_create_btn),
                    onClick = {
                        vm.startPairing(
                            user = user,
                            deviceId = stableDeviceId(context),
                            deviceModel = deviceModelName(),
                            kind = kind,
                        )
                    },
                    loading = state.phase == AddViewModel.PairPhase.CREATING,
                    modifier = Modifier.fillMaxWidth(),
                )
                state.error?.let {
                    ErrorPanel(message = describeFirestoreError(context, it))
                }
            }

            AddViewModel.PairPhase.PENDING -> {
                val invite = state.invite
                if (invite == null) {
                    // Impossible in the FSM (PENDING always carries the invite);
                    // the honest recovery is the IDLE screen, not a crash.
                    LaunchedEffect(Unit) { vm.reset() }
                } else {
                    val now = tickingNow() // 1 s — the 5-min TTL (plan §5)
                    val left = invite.expiresAtMillis - now
                    val expired = left <= 0L

                    if (expired) {
                        // ── EXPIRED (derived): highlight "create again" ──
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = warning.copy(alpha = 0.12f),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Text(
                                    text = "⌛ " + stringResource(R.string.pair_expired_new),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = warning,
                                )
                                BigButton(
                                    text = stringResource(R.string.pair_create_btn),
                                    onClick = {
                                        vm.startPairing(
                                            user = user,
                                            deviceId = stableDeviceId(context),
                                            deviceModel = deviceModelName(),
                                            kind = kind,
                                        )
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    } else {
                        PairingPendingCard(
                            vm = vm,
                            state = state,
                            invite = invite,
                            leftMillis = left,
                            locale = locale,
                            onCopy = {
                                val ok = copyToClipboard(
                                    context,
                                    context.getString(R.string.ph_code_label),
                                    invite.code,
                                )
                                if (ok) {
                                    android.widget.Toast.makeText(
                                        context,
                                        R.string.pair_copied,
                                        android.widget.Toast.LENGTH_SHORT,
                                    ).show()
                                }
                            },
                        )
                    }

                    state.error?.let {
                        ErrorPanel(message = describeFirestoreError(context, it))
                    }
                    state.watchError?.let {
                        // Non-fatal: the code stays on screen, the row is honest.
                        ErrorPanel(message = describeFirestoreError(context, it))
                    }
                }
            }

            AddViewModel.PairPhase.PAIRED -> PairedDoneCard(vm, state.claimedEmail, onBack)
            AddViewModel.PairPhase.CANCELLED -> {
                SectionCard(title = stringResource(R.string.pair_cancelled_new)) {
                    BigButton(
                        text = stringResource(R.string.pair_create_btn),
                        onClick = {
                            vm.startPairing(
                                user = user,
                                deviceId = stableDeviceId(context),
                                deviceModel = deviceModelName(),
                                kind = kind,
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

/**
 * The live pending invitation: white QR plate + countdown + mono code +
 * copy/share/cancel. Countdown turns warning ≤30 s (plan §5 QR-TTL row) —
 * color alone never carries the state (glyph + text always present).
 */
@Composable
private fun PairingPendingCard(
    vm: AddViewModel,
    state: AddViewModel.PairingState,
    invite: com.family.remote.support.InviteCode,
    leftMillis: Long,
    locale: java.util.Locale,
    onCopy: () -> Unit,
) {
    val context = LocalContext.current
    val warning = LocalWarningColor.current
    val badge = QrBadge.ofKind(state.kind)
    // ECC-H + the center role badge (Phase 12) — generated once per payload.
    val bitmap = remember(invite.qrPayload, badge) {
        qrBitmap(invite.qrPayload, badge = badge)
    }

    SectionCard(
        title = stringResource(R.string.ph_status_waiting),
        icon = null,
    ) {
        // ── QR on its always-white plate (dark-mode edge-case পর্ব ৬-৮) ──
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = androidx.compose.ui.graphics.Color.White,
                modifier = Modifier.padding(8.dp),
            ) {
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = stringResource(R.string.cd_pairing_qr),
                        modifier = Modifier
                            .padding(12.dp)
                            .size(200.dp),
                    )
                } else {
                    Text(
                        text = invite.code,
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall,
                        color = androidx.compose.ui.graphics.Color.Black,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        }

        // ── the 5-minute countdown chip ──
        val seconds = ((leftMillis + 999) / 1000).toInt()
        val urgent = leftMillis <= 30_000L
        Surface(
            shape = CircleShape,
            color = if (urgent) warning.copy(alpha = 0.16f)
            else MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        ) {
            Text(
                text = "⏳ " + stringResource(
                    R.string.ph_countdown,
                    seconds / 60,
                    (seconds % 60).toString().padStart(2, '0'),
                ),
                style = MaterialTheme.typography.labelLarge,
                color = if (urgent) warning else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            )
        }

        Text(
            text = stringResource(R.string.pair_waiting_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // ── the manual code: mono, selectable, wraps (1.3× font rule) ──
        Text(
            text = invite.code,
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextActionButton(text = stringResource(R.string.btn_copy), onClick = onCopy)
            TextActionButton(
                text = stringResource(R.string.btn_share),
                onClick = {
                    val bmp = bitmap ?: return@TextActionButton
                    val intent: Intent? = buildQrShareIntent(context, bmp, invite.code)
                    if (intent != null) {
                        context.startActivity(intent)
                    }
                },
            )
        }

        WideOutlinedButton(
            text = stringResource(R.string.btn_cancel_code),
            onClick = { vm.cancelPending() },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * PAIRED ✓ — the 200 ms icon-pop (plan §1.3: "PAIRED ✓(icon-pop 200ms)"),
 * who joined, and the standing rule: linked ≠ permission.
 */
@Composable
private fun PairedDoneCard(vm: AddViewModel, claimedEmail: String?, onBack: () -> Unit) {
    val shown = animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(durationMillis = 200),
        label = "paired-pop",
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
            text = stringResource(R.string.pair_paired_with, claimedEmail ?: ""),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = stringResource(R.string.rel_always_permission),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        WideOutlinedButton(
            text = stringResource(R.string.cmn_ok),
            onClick = {
                vm.reset()
                onBack()
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** "Manufacturer Model" — the label stored with the invitation (AppNav's recipe). */
private fun deviceModelName(): String =
    ((Build.MANUFACTURER?.trim())?.plus(" ") ?: "") + (Build.MODEL ?: "")
