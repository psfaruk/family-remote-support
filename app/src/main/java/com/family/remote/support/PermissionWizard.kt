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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
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
    fun missingNamesBn(): String {
        val out = mutableListOf<String>()
        if (!notifications) out.add("নোটিফিকেশন")
        if (!camera) out.add("ক্যামেরা")
        if (!assist) out.add("Remote assist")
        if (!battery) out.add("ব্যাটারি ছাড়")
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
                    text = "Remote assist চালু করার নিয়ম",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "নিচের ৩টা ধাপ হুবহু করুন — জীবনে একবারই লাগবে:",
                    style = MaterialTheme.typography.bodyMedium
                )
                GuideStep(
                    num = "১",
                    text = "সেটিংস খুললে তালিকায় \"Family Remote Support assist\" লাইনটা খুঁজুন " +
                        "(আমাদের অ্যাপেরটা — সাধারণত হাইলাইট করা থাকে)।"
                )
                GuideStep(
                    num = "২",
                    text = "লাইনটায় ট্যাপ করে সুইচটা চালু (ON) করুন।"
                )
                GuideStep(
                    num = "৩",
                    text = "Android-এর সতর্কবার্তা এলে Allow / অনুমতি দিন চাপুন।"
                )
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        text = "⚠️ সাবধান: \"Accessibility menu shortcut\" বা অন্য কোনো সুইচ চালু " +
                            "করলে কাজ হবে না — সেটা আমাদেরটা নয়! শুধু \"Family Remote " +
                            "Support assist\" লাইনটাই সঠিক।",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(12.dp)
                    )
                }
                Text(
                    text = "এই সার্ভিস স্ক্রিনের কিছুই পড়ে না — এটি শুধু ট্যাপ করতে পারে। " +
                        "যখন চান, একই জায়গা থেকে বন্ধ করে দিতে পারবেন।",
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
                    Text("সেটিংস খুলুন · Open settings", fontWeight = FontWeight.SemiBold)
                }
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                ) {
                    Text("পরে করব · Later")
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
                    text = "পারমিশন চেক · Permission check",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "এই অ্যাপ শুধু আপনার অনুমতি নিয়েই কাজ করে। নিচের ৪টা জিনিস " +
                        "একবার চালু করলেই সব ফিচার মসৃণ চলবে।",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (st.allDone) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Text(
                            text = "✓ সব প্রস্তুত! অ্যাপ পুরোপুরি চালু হওয়ার জন্য রেডি।",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                } else {
                    Text(
                        text = "চালু ${st.doneCount}/4 — বাকি: ${st.missingNamesBn()}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                PermRow(
                    step = "১",
                    title = "নোটিফিকেশন · Notifications",
                    desc = "সেশনের অনুরোধ এলে ফোন আপনাকে জানাবে।",
                    done = st.notifications,
                    actionLabel = "অনুমতি দিন",
                    onAction = {
                        if (Build.VERSION.SDK_INT >= 33) {
                            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                )
                PermRow(
                    step = "২",
                    title = "ক্যামেরা · Camera",
                    desc = "পেয়ারিং QR কোড স্ক্যান করতে লাগে (কোড টাইপ করলে লাগে না)।",
                    done = st.camera,
                    actionLabel = "অনুমতি দিন",
                    onAction = { camLauncher.launch(Manifest.permission.CAMERA) }
                )
                PermRow(
                    step = "৩",
                    title = "Remote assist · রিমোট টাচ",
                    desc = "সাহায্যকারী আপনার স্ক্রিনে ট্যাপ করতে পারবেন। স্ক্রিনের কিছুই পড়ে না — শুধু ট্যাপ।",
                    done = st.assist,
                    actionLabel = "সেটআপ খুলুন",
                    onAction = { showAssistGuide = true }
                )
                PermRow(
                    step = "৪",
                    title = "ব্যাটারি ছাড় · Battery",
                    desc = "অ্যাপটা ব্যাকগ্রাউন্ডে থাকলেও বার্তা সময়মত পাবে।",
                    done = st.battery,
                    actionLabel = "অনুমতি দিন",
                    onAction = { requestBatteryExemption(ctx) }
                )

                Text(
                    text = "স্ক্রিন শেয়ারের অনুমতি প্রতিবার শেয়ার শুরুর সময় Android নিজেই জিজ্ঞেস করে — " +
                        "নিরাপত্তার জন্য সেটা আগে থেকে দেওয়া যায় না।",
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
                        Text("শুরু করুন · Done", fontWeight = FontWeight.SemiBold)
                    }
                } else {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                    ) {
                        Text("পরে করব · Later")
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
                Text(
                    text = "$step.",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = if (done) "✓ চালু" else "⬜ বাকি",
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
