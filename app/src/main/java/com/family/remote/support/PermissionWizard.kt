package com.family.remote.support

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay

// ═══════════════════════════════════════════════════════════════════════
// v0.9.5 — PERMISSION WIZARD + the shared Bengali guide for the Remote-
// assist accessibility setup.
//
// The Owner asked: "যখন অ্যাপ ইনস্টল বা ওপেন করবে, তখন সকল পারমিশন দিয়ে
// ইনস্টল করতে হবে।" Android cannot grant runtime permissions at install
// time (security model), but this wizard is the closest honest equivalent:
// the FIRST time the app opens it walks the user through every permission
// the app can ever need, with live ✓/⬜ status per item, plain-Bengali
// instructions, and an automatic re-check when they come back from
// Settings. It is reachable any time from the Permissions card.
//
// Screen-capture consent is deliberately NOT here — Android requires the
// per-session projection dialog (that gate cannot and should not be
// pre-granted); the wizard says so explicitly.
// ═══════════════════════════════════════════════════════════════════════

private const val WIZARD_PREFS = "permission_wizard"
private const val WIZARD_DONE_KEY = "wizard_done_v1"

fun isPermissionWizardDone(context: Context): Boolean =
    context.getSharedPreferences(WIZARD_PREFS, Context.MODE_PRIVATE)
        .getBoolean(WIZARD_DONE_KEY, false)

fun markPermissionWizardDone(context: Context) {
    context.getSharedPreferences(WIZARD_PREFS, Context.MODE_PRIVATE)
        .edit().putBoolean(WIZARD_DONE_KEY, true).apply()
}

/** Live status of everything the wizard tracks. */
data class PermChecklist(
    val notifications: Boolean,
    val camera: Boolean,
    val assist: Boolean,
    val battery: Boolean
) {
    val doneCount: Int get() = listOf(notifications, camera, assist, battery).count { it }
    val allDone: Boolean get() = doneCount == 4
    // v0.27.0 (Phase 19.1): localized names via resources (was missingNamesBn()).
    fun missingNames(context: Context): String {
        val out = mutableListOf<String>()
        if (!notifications) out.add(context.getString(R.string.pw_perm_notif))
        if (!camera) out.add(context.getString(R.string.pw_perm_camera))
        if (!assist) out.add(context.getString(R.string.pw_perm_assist))
        if (!battery) out.add(context.getString(R.string.pw_perm_battery))
        return out.joinToString(" · ")
    }
}

/** Reads the current permission state (cheap — safe to poll). */
fun readPermChecklist(context: Context): PermChecklist {
    val notifications = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    val camera = ContextCompat.checkSelfPermission(
        context, Manifest.permission.CAMERA
    ) == PackageManager.PERMISSION_GRANTED
    val assist = AssistSetup.isAssistEnabled(context)
    val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    val battery = pm.isIgnoringBatteryOptimizations(context.packageName)
    return PermChecklist(notifications, camera, assist, battery)
}

/** Battery-optimization exemption: heartbeats + session alerts stay timely. */
@SuppressLint("BatteryLife")
fun requestBatteryExemption(context: Context) {
    try {
        context.startActivity(
            Intent(
                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                Uri.parse("package:${context.packageName}")
            )
        )
    } catch (_: Exception) {
        try {
            context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        } catch (_: Exception) {
        }
    }
}

/**
 * v0.9.5 — the SHARED Bengali step-by-step guide shown before opening
 * system accessibility settings. This is the fix for the 28 Oct session:
 * the recipient had switched on Android's built-in "Accessibility menu
 * shortcut" (wrong switch) because nothing told them which line to pick.
 * The guide names the exact line, warns against the wrong one, and then
 * opens Settings with our service row highlighted (AssistSetup).
 */
@Composable
fun AssistSetupGuide(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = stringResource(R.string.pw_guide_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.pw_guide_intro),
                    style = MaterialTheme.typography.bodyMedium
                )
                GuideStep(
                    num = stringResource(R.string.pw_step_1),
                    text = stringResource(R.string.pw_guide_step1_text)
                )
                GuideStep(
                    num = stringResource(R.string.pw_step_2),
                    text = stringResource(R.string.pw_guide_step2_text)
                )
                GuideStep(
                    num = stringResource(R.string.pw_step_3),
                    text = stringResource(R.string.pw_guide_step3_text)
                )
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        text = stringResource(R.string.pw_guide_warning),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(12.dp)
                    )
                }
                Text(
                    text = stringResource(R.string.pw_guide_privacy),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = {
                        onDismiss()
                        AssistSetup.openAssistSettings(ctx)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                ) {
                    Text(stringResource(R.string.pw_open_settings), fontWeight = FontWeight.SemiBold)
                }
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                ) {
                    Text(stringResource(R.string.pw_later))
                }
            }
        }
    }
}

@Composable
private fun GuideStep(num: String, text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Text(
            text = num,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(end = 10.dp)
        )
        Text(text = text, style = MaterialTheme.typography.bodyMedium)
    }
}

/**
 * v0.9.5 — the permission wizard itself: a full-screen checklist with live
 * state. Auto re-checks whenever the user returns from a Settings page.
 */
@Composable
fun PermissionWizard(
    visible: Boolean,
    onDismiss: () -> Unit
) {
    if (!visible) return
    val ctx = LocalContext.current
    var st by remember { mutableStateOf(readPermChecklist(ctx)) }
    var showAssistGuide by remember { mutableStateOf(false) }

    // Live refresh while open (covers return from Settings + switch flips).
    LaunchedEffect(Unit) {
        while (true) {
            st = readPermChecklist(ctx)
            delay(1500)
        }
    }

    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { st = readPermChecklist(ctx) }
    val camLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { st = readPermChecklist(ctx) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.pw_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                // Phase 11.6 — visible progress: x of 4 at a glance.
                LinearProgressIndicator(
                    progress = { st.doneCount / 4f },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = stringResource(R.string.pw_intro),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (st.allDone) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        // Phase 11.6 — friendly success moment (docs §12).
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(22.dp)
                            )
                            Text(
                                text = stringResource(R.string.pw_all_done),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                } else {
                    Text(
                        text = stringResource(R.string.pw_progress, st.doneCount, st.missingNames(ctx)),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                PermRow(
                    step = stringResource(R.string.pw_step_1),
                    title = stringResource(R.string.pw_perm_notif),
                    desc = stringResource(R.string.pw_perm_notif_desc),
                    done = st.notifications,
                    actionLabel = stringResource(R.string.pw_allow_btn),
                    onAction = {
                        if (Build.VERSION.SDK_INT >= 33) {
                            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                )
                PermRow(
                    step = stringResource(R.string.pw_step_2),
                    title = stringResource(R.string.pw_perm_camera),
                    desc = stringResource(R.string.pw_perm_camera_desc),
                    done = st.camera,
                    actionLabel = stringResource(R.string.pw_allow_btn),
                    onAction = { camLauncher.launch(Manifest.permission.CAMERA) }
                )
                PermRow(
                    step = stringResource(R.string.pw_step_3),
                    title = stringResource(R.string.pw_perm_assist_title),
                    desc = stringResource(R.string.pw_perm_assist_desc),
                    done = st.assist,
                    actionLabel = stringResource(R.string.pw_setup_open_btn),
                    onAction = { showAssistGuide = true }
                )
                PermRow(
                    step = stringResource(R.string.pw_step_4),
                    title = stringResource(R.string.pw_perm_battery),
                    desc = stringResource(R.string.pw_perm_battery_desc),
                    done = st.battery,
                    actionLabel = stringResource(R.string.pw_allow_btn),
                    onAction = { requestBatteryExemption(ctx) }
                )

                Text(
                    text = stringResource(R.string.pw_screen_share_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))

                if (st.allDone) {
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp)
                    ) {
                        Text(stringResource(R.string.pw_done_btn), fontWeight = FontWeight.SemiBold)
                    }
                } else {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                    ) {
                        Text(stringResource(R.string.pw_later))
                    }
                }
            }
        }
    }
    if (showAssistGuide) {
        AssistSetupGuide(onDismiss = { showAssistGuide = false })
    }
}

@Composable
private fun PermRow(
    step: String,
    title: String,
    desc: String,
    done: Boolean,
    actionLabel: String,
    onAction: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (done) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Phase 11.6 — numbered step bubble; turns into a ✓ badge
                // when that permission is done.
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .background(
                            color = if (done) {
                                MaterialTheme.colorScheme.tertiaryContainer
                            } else {
                                MaterialTheme.colorScheme.primaryContainer
                            },
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (done) {
                        Text(
                            text = "✓",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    } else {
                        Text(
                            text = step,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .weight(1f)
                )
                Text(
                    text = if (done) stringResource(R.string.pw_status_done) else stringResource(R.string.pw_status_pending),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (done) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.error
                )
            }
            Text(
                text = desc,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!done) {
                OutlinedButton(
                    onClick = onAction,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                ) {
                    Text(actionLabel)
                }
            }
        }
    }
}
