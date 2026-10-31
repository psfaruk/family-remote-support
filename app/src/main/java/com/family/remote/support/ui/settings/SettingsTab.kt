package com.family.remote.support.ui.settings

// M3 (v0.31.0) — verbatim extraction of the Settings tab + recent-activity
// feed from MainActivity (FRONTEND_ARCHITECTURE §3/§10 W2). Behavior-identical:
// the only change is private → internal visibility on the ten moved items.
// M4 (v0.32.0) — restructured into the EIGHT partitions of the master
// instruction PART 7 (Account · Appearance · Device Management · Location &
// Privacy · Remote Support · Guest Access · Security · About — real features
// only). ActivityTab/ActivityFeed GRADUATED to the home/sessions route
// (ui/home/ActivityScreens.kt) and the paired phones to home/family; the
// devices list moved from Account into Device Management.

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.family.remote.support.AssistSetup
import com.family.remote.support.AssistSetupGuide
import com.family.remote.support.BuildConfig
import com.family.remote.support.Fb
import com.family.remote.support.LocationSharingPrefs
import com.family.remote.support.MainActivity
import com.family.remote.support.PermChecklist
import com.family.remote.support.R
import com.family.remote.support.StatusRow
import com.family.remote.support.describeFirestoreError
import com.family.remote.support.isLocationSharingRunning
import com.family.remote.support.startLocationSharing
import com.family.remote.support.stopLocationSharing
import com.family.remote.support.ui.LangPrefBus
import com.family.remote.support.ui.ThemePrefBus
import com.family.remote.support.ui.ThemePreview
import com.family.remote.support.ui.components.AppListTile
import com.family.remote.support.ui.components.BigButton
import com.family.remote.support.ui.components.EmptyState
import com.family.remote.support.ui.components.SectionCard
import com.family.remote.support.ui.components.SettingsTile
import com.family.remote.support.ui.components.WideOutlinedButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/** Phase 11.6 — one appearance choice row with a mini theme-preview dot. */
@Composable
internal fun ThemeOptionRow(
    label: String,
    subtitle: String,
    selected: Boolean,
    preview: Int,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .border(2.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            when (preview) {
                0 -> Column(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .background(ThemePreview.lightBg)
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .background(ThemePreview.darkBg)
                    )
                }
                1 -> Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(ThemePreview.lightBg)
                )
                else -> Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(ThemePreview.darkBg)
                )
            }
            if (selected) {
                Icon(
                    imageVector = Icons.Filled.Check,
                        contentDescription = stringResource(R.string.cd_selected),
                    tint = if (preview == 2) {
                        ThemePreview.darkAccent
                    } else {
                        ThemePreview.lightAccent
                    },
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (selected) {
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
internal fun SettingsTab(
    signedInUser: FirebaseUser?,
    busy: Boolean,
    statusMessage: String?,
    deviceList: List<String>,
    webClientConfigured: Boolean,
    permSummary: PermChecklist,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
    onOpenWizard: () -> Unit,
    // M4 (v0.32.0) — the Device Management / Guest Access partitions point
    // into the Home family subtree (NAVIGATION_MAP §2: settings/devices
    // reuses home/family — paired phones, support actions, revocation).
    onOpenFamily: () -> Unit = {}
) {
    val context = LocalContext.current
    // Phase 16 — full-screen policy documents (privacy / rules) rendered
    // as overlays; null = none open.
    var showDoc by remember { mutableStateOf<String?>(null) }

    // ── M4 (v0.32.0) — THE EIGHT PARTITIONS (master instruction PART 7;
    // NAVIGATION_MAP §2 "settings" grouped list): Account · Appearance ·
    // Device Management · Location & Privacy · Remote Support · Guest
    // Access · Security · About — real features only, nothing fabricated.
    // The old §6 Activity section graduated to the home/sessions route
    // (RecentActivityScreen); the paired phones now live behind the Device
    // Management entry (home/family). Sign-out stays LAST at the bottom
    // (the Owner's standing Phase-16 order — a recorded deviation from the
    // map's settings/account sketch).

    // ── ১. ACCOUNT — profile + auth status (+ editable name).
    var showEditName by remember { mutableStateOf(false) }
    SectionCard(title = stringResource(R.string.set_section_account), icon = Icons.Filled.Person) {
        if (!webClientConfigured) {
            Text(
                    text = stringResource(R.string.set_signin_disabled),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
        if (signedInUser == null) {
            EmptyState(
                icon = Icons.Filled.Person,
                title = stringResource(R.string.home_not_signed_in),
                    hint = stringResource(R.string.set_profile_hint),
            )
            BigButton(
                enabled = webClientConfigured && !busy,
                onClick = onSignIn
            ) {
                if (busy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                        Text(stringResource(R.string.btn_google_signin))
                }
            }
        } else {
            AppListTile(
                title = signedInUser.displayName ?: "(no name)",
                subtitle = signedInUser.email ?: "(no email)",
                initials = (signedInUser.displayName ?: signedInUser.email ?: "?")
                    .take(1).ifBlank { "?" }
            )
            SettingsTile(
                icon = Icons.Filled.Create,
                title = stringResource(R.string.set_edit_name),
                description = stringResource(R.string.set_profile_sub),
                onClick = { showEditName = true },
                trailing = {
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            )
        }
        statusMessage?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }

    // ── ২. APPEARANCE — theme (3 options) + language (15) in ONE partition
    // (NAVIGATION_MAP §2: settings/appearance covers both).
    SectionCard(
        title = stringResource(R.string.set_section_look),
        icon = Icons.Filled.Settings,
            infoText = stringResource(R.string.set_theme_info),
    ) {
        ThemeOptionRow(
                label = stringResource(R.string.th_system),
                subtitle = stringResource(R.string.th_system_sub),
            selected = ThemePrefBus.pref == ThemePrefBus.SYSTEM,
            preview = 0,
            onClick = { ThemePrefBus.set(context, ThemePrefBus.SYSTEM) }
        )
        ThemeOptionRow(
                label = stringResource(R.string.th_light),
                subtitle = stringResource(R.string.th_light_sub),
            selected = ThemePrefBus.pref == ThemePrefBus.LIGHT,
            preview = 1,
            onClick = { ThemePrefBus.set(context, ThemePrefBus.LIGHT) }
        )
        ThemeOptionRow(
                label = stringResource(R.string.th_dark),
                subtitle = stringResource(R.string.th_dark_sub),
            selected = ThemePrefBus.pref == ThemePrefBus.DARK,
            preview = 2,
            onClick = { ThemePrefBus.set(context, ThemePrefBus.DARK) }
        )
        HorizontalDivider()
        Text(
            text = stringResource(R.string.set_section_lang),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = stringResource(R.string.set_lang_sub),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        LangPrefBus.supported.forEach { (code, nativeName) ->
            LangOptionRow(
                label = nativeName,
                selected = LangPrefBus.code == code,
                onClick = { LangPrefBus.set(context, code) }
            )
        }
    }

    // ── ৩. DEVICE MANAGEMENT — paired phones (the home/family route) +
    // this account's own registered devices (users/{uid}/devices).
    SectionCard(title = stringResource(R.string.set_section_devices), icon = Icons.Filled.Phone) {
        SettingsTile(
            icon = Icons.Filled.Phone,
            title = stringResource(R.string.set_dev_entry_t),
            description = stringResource(R.string.set_dev_entry_d),
            onClick = onOpenFamily,
            trailing = {
                Icon(
                    imageVector = Icons.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        )
        if (deviceList.isNotEmpty()) {
            HorizontalDivider()
            Text(
                text = stringResource(R.string.set_devices_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Column {
                deviceList.forEachIndexed { index, entry ->
                    if (index > 0) HorizontalDivider()
                    val parts = entry.split(" · ")
                    AppListTile(
                        title = parts.getOrNull(0) ?: entry,
                        subtitle = parts.drop(1).joinToString(" · ").ifBlank { " " },
                        icon = Icons.Filled.Phone
                    )
                }
            }
        }
    }

    // ── ৪. LOCATION & PRIVACY — the honest M5 status note + both policy
    // documents (Owner: "নীতিমালা ও প্রাইভেসি পলিসি এই ফিচারস গুলো উন্মুক্ত ও
    // অ্যাপ এ থাকা উচিত")।
    SectionCard(title = stringResource(R.string.set_section_location), icon = Icons.Filled.LocationOn) {
        // M5 (v0.33.0) — the "planned" note is replaced by the real
        // controls: sharing toggle (+ runtime permission gate) and the
        // update-interval choice; the two policy documents stay.
        LocationSharingControls()
        HorizontalDivider()
        SettingsTile(
            icon = Icons.Filled.Lock,
            title = stringResource(R.string.set_privacy),
            description = stringResource(R.string.set_privacy_sub),
            onClick = { showDoc = "privacy" },
            trailing = {
                Icon(
                    imageVector = Icons.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        )
        HorizontalDivider()
        SettingsTile(
            icon = Icons.Filled.CheckCircle,
            title = stringResource(R.string.set_rules),
            description = stringResource(R.string.set_rules_sub),
            onClick = { showDoc = "rules" },
            trailing = {
                Icon(
                    imageVector = Icons.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        )
    }

    // ── ৫. REMOTE SUPPORT — session-request delivery + the assist setup.
    SectionCard(title = stringResource(R.string.set_section_remote), icon = Icons.Filled.Send) {
        SettingsNotifTile()
        HorizontalDivider()
        TestNotifRow()
        HorizontalDivider()
        SettingsAssistTile()
    }

    // ── ৬. GUEST ACCESS — the honest temporary-window note; the live guest
    // links (countdowns, removal) live in the Family Devices screen.
    SectionCard(title = stringResource(R.string.set_section_guest), icon = Icons.Filled.Person) {
        Text(
            text = stringResource(R.string.set_guest_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        WideOutlinedButton(onClick = onOpenFamily) {
            Icon(
                imageVector = Icons.Filled.KeyboardArrowRight,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Text("  " + stringResource(R.string.fam_title))
        }
    }

    // ── ৭. SECURITY — the permission wizard (notifications · camera ·
    // assist · battery — the app's whole permission posture in one place).
    SectionCard(
        title = stringResource(R.string.set_section_security),
        icon = Icons.Filled.Lock,
            infoText = stringResource(R.string.set_perm_info),
    ) {
        SettingsTile(
            icon = Icons.Filled.Lock,
                title = stringResource(R.string.pw_title),
            description = if (permSummary.allDone) {
                    stringResource(R.string.set_perm_alldone)
            } else {
                stringResource(R.string.pw_progress, permSummary.doneCount, permSummary.missingNames(context))
            },
            onClick = onOpenWizard,
            trailing = {
                Icon(
                    imageVector = Icons.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        )
    }

    // ── ৮. About — FIXED (Owner: "About অপশন টি ঠিক নাই, সেখানে Firebase
    // এর তথ্য গুলো দেখা যাচ্ছে। এটা তো প্রাইভেসি হলো না"): version + package
    // only, plus a plain-language storage note and the two policy docs.
    SectionCard(title = stringResource(R.string.set_section_about), icon = Icons.Filled.Info) {
        var showAbout by remember { mutableStateOf(false) }
        SettingsTile(
            icon = Icons.Filled.Info,
            title = "v${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})",
            description = stringResource(R.string.set_about_sub),
            onClick = { showAbout = true },
            trailing = {
                Icon(
                    imageVector = Icons.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        )
        if (showAbout) {
            AlertDialog(
                onDismissRequest = { showAbout = false },
                confirmButton = {
                    TextButton(onClick = { showAbout = false }) {
                        Text(stringResource(R.string.cmn_ok))
                    }
                },
                title = {
                    Text(stringResource(R.string.set_section_about), fontWeight = FontWeight.Bold)
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatusRow(
                                label = stringResource(R.string.set_version),
                            value = "v${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})"
                        )
                        StatusRow(label = "Package", value = "com.family.remote.support")
                        Text(
                            text = stringResource(R.string.about_storage),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { showDoc = "privacy" }) {
                                Text(stringResource(R.string.set_privacy))
                            }
                            TextButton(onClick = { showDoc = "rules" }) {
                                Text(stringResource(R.string.set_rules))
                            }
                        }
                    }
                }
            )
        }
    }

    // ── Sign-out LAST (Owner: "লগআউট করার অপশন টি নিচের দিকে থাকলে ভালো")।
    if (signedInUser != null) {
        WideOutlinedButton(enabled = !busy, onClick = onSignOut) {
            Icon(
                imageVector = Icons.Filled.ExitToApp,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Text("  " + stringResource(R.string.set_sign_out))
        }
    }

    // Full-screen policy document overlays.
    when (showDoc) {
        "privacy" -> DocOverlay(
            title = stringResource(R.string.privacy_title),
            body = stringResource(R.string.privacy_body)
        ) { showDoc = null }
        "rules" -> DocOverlay(
            title = stringResource(R.string.rules_title),
            body = stringResource(R.string.rules_body)
        ) { showDoc = null }
    }

    // Edit-name dialog (profile).
    if (showEditName && signedInUser != null) {
        EditNameDialog(
            currentName = signedInUser.displayName ?: "",
            onDismiss = { showEditName = false }
        )
    }
}

/**
 * Phase 16 — profile name editor: updates the Firebase Auth display name
 * and mirrors it into users/{uid} (the doc the app already reads). Honest
 * inline error on failure, toast on success, no silent lies.
 */
@Composable
internal fun EditNameDialog(
    currentName: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(currentName) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text(stringResource(R.string.set_edit_name), fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.set_name_label)) },
                    singleLine = true,
                    enabled = !saving
                )
                error?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !saving && name.trim().length >= 2,
                onClick = {
                    saving = true
                    error = null
                    scope.launch {
                        try {
                            val user = FirebaseAuth.getInstance().currentUser
                                ?: throw IllegalStateException("Not signed in")
                            user.updateProfile(
                                UserProfileChangeRequest.Builder()
                                    .setDisplayName(name.trim())
                                    .build()
                            ).await()
                            Fb.db.collection("users").document(user.uid)
                                .set(
                                    hashMapOf(
                                        "displayName" to name.trim(),
                                        "updatedAt" to FieldValue.serverTimestamp()
                                    ),
                                    SetOptions.merge()
                                ).await()
                            Toast.makeText(
                                context,
                                context.getString(R.string.set_saved),
                                Toast.LENGTH_SHORT
                            ).show()
                            onDismiss()
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            error = context.getString(R.string.err_generic, "sync", describeFirestoreError(context, e))
                        } finally {
                            saving = false
                        }
                    }
                }
            ) {
                Text(stringResource(R.string.set_save))
            }
        },
        dismissButton = {
            TextButton(enabled = !saving, onClick = onDismiss) {
                Text(stringResource(R.string.set_cancel))
            }
        }
    )
}

/**
 * Phase 16 — language option row: the language's OWN native name + a radio
 * check. Native names never translate — each language shows as it calls
 * itself, exactly like every mainstream app does.
 */
@Composable
internal fun LangOptionRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

/**
 * Phase 16 — TEST NOTIFICATION (Owner: "নোটিফিকেশন আসে না") — an honest
 * one-tap probe: posts a REAL notification on the SAME channel the session
 * requests use, so channel + permission are verified instantly without
 * asking anyone for help. If the permission is off, it says so instead of
 * pretending.
 */
@Composable
internal fun TestNotifRow() {
    val context = LocalContext.current
    var feedback by remember { mutableStateOf<String?>(null) }
    SettingsTile(
        icon = Icons.Filled.Notifications,
        title = stringResource(R.string.set_test_notif),
            description = feedback ?: stringResource(R.string.tnotif_desc),
        onClick = {
            val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(
                    context, Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                feedback = context.getString(R.string.set_test_notif_blocked)
                return@SettingsTile
            }
            try {
                // The channel is created by SessionNotifier at app start;
                // (re)creating it here is idempotent and keeps the probe
                // honest even on a brand-new process.
                val channelId = "support_session_requests"
                val manager = context.getSystemService(Context.NOTIFICATION_SERVICE)
                    as NotificationManager
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    manager.createNotificationChannel(
                        NotificationChannel(
                            channelId,
                            "Support session requests",
                            NotificationManager.IMPORTANCE_HIGH
                        )
                    )
                }
                val openIntent = Intent(context, MainActivity::class.java)
                val pending = PendingIntent.getActivity(
                    context,
                    990001,
                    openIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                val notification = NotificationCompat.Builder(context, channelId)
                    .setSmallIcon(R.drawable.ic_stat_notify)
                    .setContentTitle("Family Remote Support")
                        .setContentText(LangPrefBus.wrap(context).getString(R.string.notif_test_ok))
                    .setAutoCancel(true)
                    .setContentIntent(pending)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .build()
                manager.notify(990001, notification)
                feedback = context.getString(R.string.set_test_notif_sent)
            } catch (_: Exception) {
                feedback = context.getString(R.string.set_test_notif_blocked)
            }
        }
    )
}

/**
 * Phase 16 — full-screen policy document overlay (privacy / rules):
 * scrollable, dismissible, honest — nothing leaves the device to show it.
 */
@Composable
internal fun DocOverlay(
    title: String,
    body: String,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .systemBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                            contentDescription = stringResource(R.string.cd_close),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 24.dp)
            ) {
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

/**
 * M5 (v0.33.0) — the Location & Privacy partition's live-sharing controls:
 * the owner's standing ON/OFF toggle (+ the runtime while-in-use location
 * permission gate — Android 14's location-FGS rule) and the update-interval
 * choice (30 s / 1 min / 5 min, LIVE_LOCATION_ARCHITECTURE §6). The same
 * LocationSharingPrefs / start-stop path the Tracking tab's OwnSharingCard
 * drives; the Tracking tab adds the consent SHEET before a first start,
 * here the toggle doubles as the always-available off-switch (stop is one
 * tap by design — mirroring the sharing notification's stop affordance).
 */
@Composable
private fun LocationSharingControls() {
    val context = LocalContext.current
    var sharingOn by remember {
        mutableStateOf(LocationSharingPrefs.isSharingEnabled(context))
    }
    var intervalMillis by remember {
        mutableStateOf(LocationSharingPrefs.intervalMillis(context))
    }
    var showIntervalDialog by remember { mutableStateOf(false) }

    // Turning sharing ON needs the while-in-use grant first (the FGS cannot
    // legally start without it on Android 14). The system dialog only ever
    // appears AFTER this row has spelled out what the permission is for.
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val fine = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarse = grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (fine || coarse) {
            startLocationSharing(context)
            sharingOn = true
        } else {
            // Honest denial: nothing starts; the toast explains why.
            Toast.makeText(context, R.string.loc_denied_body, Toast.LENGTH_LONG).show()
        }
    }

    SettingsTile(
        icon = Icons.Filled.LocationOn,
        title = stringResource(
            if (sharingOn) R.string.loc_share_on else R.string.loc_share_off
        ),
        description = stringResource(
            if (sharingOn) R.string.loc_banner_on else R.string.loc_banner_off
        ),
        trailing = {
            Switch(
                checked = sharingOn,
                onCheckedChange = { wantOn ->
                    if (wantOn) {
                        val fine = ContextCompat.checkSelfPermission(
                            context, Manifest.permission.ACCESS_FINE_LOCATION
                        ) == PackageManager.PERMISSION_GRANTED
                        val coarse = ContextCompat.checkSelfPermission(
                            context, Manifest.permission.ACCESS_COARSE_LOCATION
                        ) == PackageManager.PERMISSION_GRANTED
                        if (fine || coarse) {
                            startLocationSharing(context)
                            sharingOn = true
                        } else {
                            permissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                            )
                        }
                    } else {
                        // Stop is ALWAYS one tap — no confirmation sheet for
                        // ending sharing (the owner's standing privacy rule).
                        stopLocationSharing(context)
                        sharingOn = false
                    }
                }
            )
        }
    )
    HorizontalDivider()
    SettingsTile(
        icon = Icons.Filled.List,
        title = stringResource(R.string.loc_interval_label),
        description = intervalChoiceLabel(intervalMillis),
        onClick = { showIntervalDialog = true }
    )

    if (showIntervalDialog) {
        AlertDialog(
            onDismissRequest = { showIntervalDialog = false },
            title = {
                Text(
                    text = stringResource(R.string.loc_interval_label),
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    IntervalChoiceRow(
                        label = stringResource(R.string.loc_interval_30s),
                        value = LocationSharingPrefs.INTERVAL_30S,
                        currentMillis = intervalMillis,
                        onPick = {
                            intervalMillis = it
                            showIntervalDialog = false
                        },
                        applyIntervalChoice = { picked ->
                            LocationSharingPrefs.setIntervalMillis(context, picked)
                            // The service reads the interval at start; restart
                            // it so a LIVE change takes effect immediately.
                            // The stop path deletes the shared doc once and
                            // the fresh start republishes on the next accepted
                            // fix — freshness stays honest throughout (never
                            // "live" during the gap).
                            if (sharingOn && isLocationSharingRunning(context)) {
                                stopLocationSharing(context)
                                startLocationSharing(context)
                            }
                        }
                    )
                    IntervalChoiceRow(
                        label = stringResource(R.string.loc_interval_1m),
                        value = LocationSharingPrefs.INTERVAL_1M,
                        currentMillis = intervalMillis,
                        onPick = {
                            intervalMillis = it
                            showIntervalDialog = false
                        },
                        applyIntervalChoice = { picked ->
                            LocationSharingPrefs.setIntervalMillis(context, picked)
                            if (sharingOn && isLocationSharingRunning(context)) {
                                stopLocationSharing(context)
                                startLocationSharing(context)
                            }
                        }
                    )
                    IntervalChoiceRow(
                        label = stringResource(R.string.loc_interval_5m),
                        value = LocationSharingPrefs.INTERVAL_5M,
                        currentMillis = intervalMillis,
                        onPick = {
                            intervalMillis = it
                            showIntervalDialog = false
                        },
                        applyIntervalChoice = { picked ->
                            LocationSharingPrefs.setIntervalMillis(context, picked)
                            if (sharingOn && isLocationSharingRunning(context)) {
                                stopLocationSharing(context)
                                startLocationSharing(context)
                            }
                        }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showIntervalDialog = false }) {
                    Text(stringResource(R.string.set_cancel))
                }
            }
        )
    }
}

/** Localized label for the currently configured interval. */
@Composable
private fun intervalChoiceLabel(millis: Long): String = when (millis) {
    LocationSharingPrefs.INTERVAL_30S -> stringResource(R.string.loc_interval_30s)
    LocationSharingPrefs.INTERVAL_5M -> stringResource(R.string.loc_interval_5m)
    else -> stringResource(R.string.loc_interval_1m)
}

/** One radio row of the interval dialog (the language dialog's row style). */
@Composable
private fun IntervalChoiceRow(
    label: String,
    value: Long,
    currentMillis: Long,
    onPick: (Long) -> Unit,
    applyIntervalChoice: (Long) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                applyIntervalChoice(value)
                onPick(value)
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = currentMillis == value,
            onClick = {
                applyIntervalChoice(value)
                onPick(value)
            }
        )
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
    }
}

/**
 * v0.14.0 — নোটিফিকেশন টগল (আসল Switch): চালু/বন্ধ অবস্থা সিস্টেম থেকে পড়া
 * হয় (১.৫ সেকেন্ডে রিফ্রেশ, সৎ)। চালু করতে চাইলে Android 13+ রানটাইম
 * পারমিশন চাওয়া হয়; বন্ধ করতে সিস্টেমের নোটিফিকেশন-সেটিংসে পাঠানো হয় —
 * অ্যাপ নিজে থেকে কখনো মিথ্যা অবস্থা দেখায় না।
 */
@Composable
internal fun SettingsNotifTile() {
    val context = LocalContext.current
    var notifGranted by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(
                    context, Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
        )
    }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        notifGranted = granted || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
    }
    LaunchedEffect(Unit) {
        while (true) {
            notifGranted =
                Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                    ContextCompat.checkSelfPermission(
                        context, Manifest.permission.POST_NOTIFICATIONS
                    ) == PackageManager.PERMISSION_GRANTED
            delay(1500)
        }
    }
    SettingsTile(
        icon = Icons.Filled.Notifications,
            title = stringResource(R.string.pw_perm_notif),
        description = if (notifGranted) {
                stringResource(R.string.tile_notif_on)
        } else {
                stringResource(R.string.tile_notif_off)
        },
        trailing = {
            Switch(
                checked = notifGranted,
                onCheckedChange = { want ->
                    if (want) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    } else {
                        // Honest OFF: notifications can only truly be turned
                        // off in system settings — send the user there.
                        try {
                            context.startActivity(
                                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                            )
                        } catch (_: Exception) {
                            try {
                                context.startActivity(
                                    Intent(Settings.ACTION_SETTINGS)
                                )
                            } catch (_: Exception) {
                                // no settings activity — nothing else to do
                            }
                        }
                    }
                }
            )
        }
    )
}

/**
 * v0.14.0 — Remote assist স্ট্যাটাস টাইল (সৎ পোল ১.৫ সে): চালু থাকলে ✓,
 * বন্ধ থাকলে 'সেটআপ' বোতাম — বাংলা ধাপে-ধাপে গাইড ডায়ালগ (AssistSetupGuide)।
 */
@Composable
internal fun SettingsAssistTile() {
    val context = LocalContext.current
    var assistOn by remember { mutableStateOf(AssistSetup.isAssistReady(context)) }
    var showGuide by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            assistOn = AssistSetup.isAssistReady(context)
            delay(1500)
        }
    }
    SettingsTile(
        icon = Icons.Filled.Person,
            title = stringResource(R.string.pw_perm_assist_title),
        description = if (assistOn) {
                stringResource(R.string.tile_assist_on)
        } else {
                stringResource(R.string.tile_assist_off)
        },
        onClick = if (!assistOn) {
            { showGuide = true }
        } else {
            null
        },
        trailing = {
            if (!assistOn) {
                OutlinedButton(
                    onClick = { showGuide = true },
                    modifier = Modifier.heightIn(min = 44.dp)
                ) {
                        Text(stringResource(R.string.btn_setup))
                }
            } else {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    )
    if (showGuide) {
        AssistSetupGuide(onDismiss = { showGuide = false })
    }
}
