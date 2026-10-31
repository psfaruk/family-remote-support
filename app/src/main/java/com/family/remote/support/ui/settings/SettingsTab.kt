package com.family.remote.support.ui.settings

// M3 (v0.31.0) — verbatim extraction of the Settings tab + recent-activity
// feed from MainActivity (FRONTEND_ARCHITECTURE §3/§10 W2). Behavior-identical:
// the only change is private → internal visibility on the ten moved items.

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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import com.family.remote.support.MainActivity
import com.family.remote.support.PermChecklist
import com.family.remote.support.R
import com.family.remote.support.REL_KIND_GUEST
import com.family.remote.support.RelationshipView
import com.family.remote.support.SessionView
import com.family.remote.support.StatusRow
import com.family.remote.support.describeFirestoreError
import com.family.remote.support.getSession
import com.family.remote.support.mmss
import com.family.remote.support.watchRelationships
import com.family.remote.support.watchSessions
import com.family.remote.support.ui.LangPrefBus
import com.family.remote.support.ui.ThemePrefBus
import com.family.remote.support.ui.ThemePreview
import com.family.remote.support.ui.components.AppListTile
import com.family.remote.support.ui.components.BigButton
import com.family.remote.support.ui.components.EmptyState
import com.family.remote.support.ui.components.PresenceBadge
import com.family.remote.support.ui.components.SectionCard
import com.family.remote.support.ui.components.SettingsTile
import com.family.remote.support.ui.components.WideOutlinedButton
import com.family.remote.support.ui.components.sessionBadgeKind
import com.family.remote.support.ui.components.sessionStatus
import com.family.remote.support.ui.components.statusChipColors
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@Composable
internal fun ActivityTab(user: FirebaseUser?) {
    if (user == null) {
            SectionCard(title = stringResource(R.string.act_title), icon = Icons.Filled.List) {
            EmptyState(
                icon = Icons.Filled.Person,
                    title = stringResource(R.string.act_signin_title),
                    hint = stringResource(R.string.act_signin_hint),
            )
        }
        return
    }
    ActivityFeed(user)
}

@Composable
internal fun ActivityFeed(user: FirebaseUser) {
    var sessions by remember { mutableStateOf<List<SessionView>>(emptyList()) }
    var sessionSyncError by remember { mutableStateOf<String?>(null) }
    var relationships by remember { mutableStateOf<List<RelationshipView>>(emptyList()) }
    var relSyncError by remember { mutableStateOf<String?>(null) }
    // The last session that closed — fetched once when it leaves the open set.
    var lastFinished by remember { mutableStateOf<SessionView?>(null) }
    var lastOpenId by remember { mutableStateOf<String?>(null) }
    var nowMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    val context = LocalContext.current

    LaunchedEffect(user.uid) {
        while (true) {
            nowMillis = System.currentTimeMillis()
            delay(1000)
        }
    }
    DisposableEffect(user.uid) {
        val stop = watchSessions(
            user,
            onUpdate = { sessionSyncError = null; sessions = it },
            onError = { sessionSyncError = describeFirestoreError(context, it) }
        )
        onDispose { stop() }
    }
    DisposableEffect(user.uid) {
        val stop = watchRelationships(
            user,
            onUpdate = { relSyncError = null; relationships = it },
            onError = { relSyncError = describeFirestoreError(context, it) }
        )
        onDispose { stop() }
    }

    val openSession = sessions.firstOrNull {
        it.status == "REQUESTED" || it.status == "ACTIVE"
    }
    LaunchedEffect(sessions) {
        val currentOpenId = openSession?.id
        val prevOpenId = lastOpenId
        if (prevOpenId != null && prevOpenId != currentOpenId) {
            try {
                lastFinished = getSession(prevOpenId)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // best effort — the history line is informational only
            }
        }
        lastOpenId = currentOpenId
    }

    SectionCard(
            title = stringResource(R.string.feed_title),
        icon = Icons.Filled.Notifications,
            infoText = stringResource(R.string.feed_info),
    ) {
        if (openSession == null && lastFinished == null) {
            EmptyState(
                icon = Icons.Filled.Notifications,
                    title = stringResource(R.string.feed_empty_title),
                    hint = stringResource(R.string.feed_empty_hint),
                illustrationRes = R.drawable.ic_empty_activity
            )
        }
        openSession?.let { s ->
            val secondsLeft = when (s.status) {
                "REQUESTED" -> ((s.respondDeadlineMillis - nowMillis) / 1000).coerceAtLeast(0)
                else -> ((s.sessionEndsAtMillis - nowMillis) / 1000).coerceAtLeast(0)
            }
            AppListTile(
                title = when {
                    s.status == "REQUESTED" && s.recipientUid == user.uid ->
                            stringResource(R.string.feed_req_received, s.helperEmail.ifBlank { "Someone" })
                    s.status == "REQUESTED" ->
                            stringResource(R.string.feed_req_sent, s.recipientEmail)
                    s.status == "ACTIVE" && s.helperUid == user.uid ->
                            stringResource(R.string.feed_active_theirs, s.recipientEmail)
                    else ->
                            stringResource(R.string.feed_active_mine, s.helperEmail.ifBlank { "Someone" })
                },
                subtitle = when {
                    s.status == "REQUESTED" && s.recipientUid == user.uid ->
                            stringResource(R.string.feed_need_decision, mmss(secondsLeft))
                    s.status == "REQUESTED" ->
                            stringResource(R.string.feed_awaiting, mmss(secondsLeft))
                    else ->
                            stringResource(R.string.feed_in_progress, mmss(secondsLeft))
                },
                icon = if (s.status == "ACTIVE") Icons.Filled.CheckCircle else Icons.Filled.Send,
                avatarContainer = if (s.status == "ACTIVE") {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.tertiaryContainer
                },
                avatarContent = if (s.status == "ACTIVE") {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onTertiaryContainer
                },
                chip = if (s.kind == "guest") "Guest" else sessionStatus(context, s.status),
                chipContainer = if (s.kind == "guest") {
                    MaterialTheme.colorScheme.tertiaryContainer
                } else {
                    statusChipColors(sessionBadgeKind(s.status)).first
                },
                chipContent = if (s.kind == "guest") {
                    MaterialTheme.colorScheme.onTertiaryContainer
                } else {
                    statusChipColors(sessionBadgeKind(s.status)).second
                }
            )
        }
        lastFinished?.let { s ->
            HorizontalDivider()
            AppListTile(
                    title = stringResource(R.string.feed_last_title),
                    subtitle = stringResource(R.string.feed_last_sub, s.helperEmail.ifBlank { "?" }, s.recipientEmail) +
                    (s.endedBy?.let { context.getString(R.string.feed_ended_by, it) } ?: ""),
                icon = Icons.Filled.CheckCircle,
                avatarContainer = MaterialTheme.colorScheme.surfaceVariant,
                avatarContent = MaterialTheme.colorScheme.onSurfaceVariant,
                    chip = sessionStatus(context, s.status),
                chipContainer = statusChipColors(sessionBadgeKind(s.status)).first,
                chipContent = statusChipColors(sessionBadgeKind(s.status)).second
            )
        }
        if (sessionSyncError != null) {
            Text(
                text = sessionSyncError!!,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }

    SectionCard(
            title = stringResource(R.string.devices_title),
        icon = Icons.Filled.Phone,
            infoText = stringResource(R.string.devices_info),
    ) {
        if (relSyncError != null) {
            Text(
                text = relSyncError!!,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
        if (relationships.isEmpty() && relSyncError == null) {
            EmptyState(
                icon = Icons.Filled.Phone,
                    title = stringResource(R.string.devices_empty_title),
                    hint = stringResource(R.string.devices_empty_hint),
                illustrationRes = R.drawable.ic_empty_devices
            )
        }
        Column {
            relationships.forEachIndexed { index, rel ->
                if (index > 0) HorizontalDivider()
                val iHelp = rel.helperUid == user.uid
                val otherEmail =
                    (if (iHelp) rel.recipientEmail else rel.helperEmail).ifBlank { "(no email)" }
                Column(modifier = Modifier.fillMaxWidth()) {
                    AppListTile(
                        title = otherEmail,
                        initials = otherEmail.take(1).ifBlank { "?" },
                        subtitle = buildString {
                                append(if (iHelp) stringResource(R.string.rel_you_help) else stringResource(R.string.rel_helping))
                            if (rel.recipientDeviceModel.isNotBlank()) {
                                append(" · ${rel.recipientDeviceModel}")
                            }
                        },
                        chip = if (rel.kind == REL_KIND_GUEST) "Guest" else "Family",
                        chipContainer = if (rel.status == "ACTIVE") {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.errorContainer
                        },
                        chipContent = if (rel.status == "ACTIVE") {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onErrorContainer
                        }
                    )
                    if (iHelp && rel.recipientDeviceId.isNotBlank()) {
                        Box(modifier = Modifier.padding(start = 56.dp)) {
                            PresenceBadge(uid = rel.recipientUid, deviceId = rel.recipientDeviceId)
                        }
                    }
                }
            }
        }
    }
}

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
    onOpenWizard: () -> Unit
) {
    val context = LocalContext.current
    // Phase 16 — full-screen policy documents (privacy / rules) rendered
    // as overlays; null = none open.
    var showDoc by remember { mutableStateOf<String?>(null) }

    // ── ১. Account + PROFILE (Owner: "সেটিংস ট্যাব এর ভিতরে একটি ইউজার
    // প্রোফাইল creat করেন") — name is editable, updates the Firebase
    // profile AND the users/{uid} doc the rest of the app already reads.
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
            HorizontalDivider()
            Text(
                text = stringResource(R.string.set_devices_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            if (deviceList.isEmpty()) {
                Text(
                    text = stringResource(R.string.set_no_devices),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
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
        statusMessage?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }

    // ── ২. Setup & permissions (unchanged logic; + the honest TEST
    // notification probe below — Owner: "নোটিফিকেশন আসে না").
    SectionCard(
        title = stringResource(R.string.set_section_setup),
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
        HorizontalDivider()
        SettingsNotifTile()
        HorizontalDivider()
        TestNotifRow()
        HorizontalDivider()
        SettingsAssistTile()
    }

    // ── ৩. Appearance (unchanged logic).
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
    }

    // ── ৪. LANGUAGE (Owner: "প্রধান ভাষা ইংলিশ হবে… পপুলার কয়েকটা ভাষা
    // যোগ করো 10 15 টি") — 15 languages; English is the default on a fresh
    // install; changing it recreates the activity so EVERYTHING repaints.
    SectionCard(
        title = stringResource(R.string.set_section_lang),
        icon = Icons.Filled.List,
            infoText = stringResource(R.string.set_lang_info)
    ) {
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

    // ── ৫. RULES & PRIVACY (Owner: "নীতিমালা ও প্রাইভেসি পলিসি এই ফিচারস
    // গুলো উন্মুক্ত ও অ্যাপ এ থাকা উচিত") — full documents INSIDE the app.
    SectionCard(
        title = stringResource(R.string.set_section_rules),
        icon = Icons.Filled.Lock
    ) {
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

    // ── ৬. ACTIVITY — the old একটিভিটি tab merged here (Owner request):
    // session activity + paired phones with live online status.
    ActivityTab(user = signedInUser)

    // ── ৭. About — FIXED (Owner: "About অপশন টি ঠিক নাই, সেখানে Firebase
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

    // ── ৮. Sign-out LAST (Owner: "লগআউট করার অপশন টি নিচের দিকে থাকলে ভালো")।
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
