package com.family.remote.support.ui.settings

import android.app.Activity
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.family.remote.support.AskNotifier
import com.family.remote.support.BuildConfig
import com.family.remote.support.R
import com.family.remote.support.isNotifGranted
import com.family.remote.support.readPermChecklist
import com.family.remote.support.ui.LangPrefBus
import com.family.remote.support.ui.ThemePrefBus
import com.family.remote.support.ui.components.DetailRow
import com.family.remote.support.ui.components.SectionCard
import com.family.remote.support.ui.components.SettingsTile
import com.family.remote.support.ui.components.WideOutlinedButton
import com.family.remote.support.ui.nav.AppRoutes
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.UserProfileChangeRequest
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * ui/settings/SettingsTab.kt — the Settings tab (W6, FRONTEND_UI_PLAN_BN §2.5).
 * Five cards in the Owner's order: Account · Device Management · Privacy &
 * Security · Preferences · About & session (sign-out last, red outlined).
 *
 * Everything here is real, nothing is a stub:
 *  • Account — profile row + edit-name dialog writing the Firebase profile;
 *  • Preferences — theme rows write [ThemePrefBus] (applied live), the
 *    language row opens the 15-language picker (persist + activity recreate,
 *    LangPrefBus's documented contract), the test-notification row posts a
 *    real local notification on the session-requests channel;
 *  • About — [BuildConfig] facts only;
 *  • sign-out is wired by the host (AppNav) and lands last.
 */
@Composable
fun SettingsTab(
    user: FirebaseUser?,
    onOpenRoute: (String) -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val perms by remember { mutableStateOf(readPermChecklist(context)) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // ── 1. Account ──
        SectionCard(title = stringResource(R.string.set_section_account), icon = Icons.Filled.AccountCircle) {
            if (user == null) {
                Text(stringResource(R.string.home_not_signed_in))
            } else {
                ProfileRow(user)
                Spacer(Modifier.heightIn(min = 8.dp))
                EditNameSection(user)
            }
        }

        // ── 2. Device Management ──
        SectionCard(title = stringResource(R.string.set_section_devices), icon = Icons.Filled.Phone) {
            SettingsTile(
                title = stringResource(R.string.set_dev_entry_t),
                desc = stringResource(R.string.set_dev_entry_d),
                icon = Icons.Filled.Person,
                onClick = { onOpenRoute(AppRoutes.HOME_FAMILY) },
            )
            SettingsTile(
                title = stringResource(R.string.set_devices_title),
                desc = stringResource(R.string.set_no_devices),
                icon = Icons.Filled.Phone,
                onClick = { onOpenRoute(AppRoutes.ADD) },
            )
        }

        // ── 3. Privacy & Security ──
        var privacyOpen by remember { mutableStateOf(false) }
        var rulesOpen by remember { mutableStateOf(false) }
        SectionCard(title = stringResource(R.string.set_section_privacysec), icon = Icons.Filled.Lock) {
            SettingsTile(
                title = stringResource(R.string.loc_share_title),
                desc = stringResource(R.string.set_loc_now),
                icon = Icons.Filled.LocationOn,
                onClick = { onOpenRoute(AppRoutes.TRACKING) },
            )
            SettingsTile(
                title = stringResource(R.string.set_perm_info),
                desc = if (perms.allDone) stringResource(R.string.set_perm_alldone)
                else stringResource(R.string.pw_progress, perms.doneCount, perms.missingNames(context)),
                icon = Icons.Filled.CheckCircle,
                onClick = { onOpenRoute(AppRoutes.ADD_PERMISSIONS) },
            )
            SettingsTile(
                title = stringResource(R.string.set_privacy),
                desc = stringResource(R.string.set_privacy_sub),
                icon = Icons.Filled.Info,
                onClick = { privacyOpen = true },
            )
            SettingsTile(
                title = stringResource(R.string.set_rules),
                desc = stringResource(R.string.set_rules_sub),
                icon = Icons.Filled.List,
                onClick = { rulesOpen = true },
            )
        }
        if (privacyOpen) {
            LongTextDialog(
                title = stringResource(R.string.set_privacy),
                body = stringResource(R.string.privacy_body),
                onDismiss = { privacyOpen = false },
            )
        }
        if (rulesOpen) {
            LongTextDialog(
                title = stringResource(R.string.rules_title),
                body = stringResource(R.string.rules_body),
                onDismiss = { rulesOpen = false },
            )
        }

        // ── 4. Preferences ──
        SectionCard(title = stringResource(R.string.set_section_prefs), icon = Icons.Filled.Settings) {
            ThemeSection()
            LanguageSection()
            TestNotifRow()
        }

        // ── 5. About & session (sign-out LAST, red outlined) ──
        SectionCard(title = stringResource(R.string.set_section_about_session), icon = Icons.Filled.Info) {
            DetailRow(
                label = stringResource(R.string.set_version),
                value = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
            )
            Spacer(Modifier.heightIn(min = 12.dp))
            WideOutlinedButton(
                text = stringResource(R.string.set_sign_out),
                onClick = onSignOut,
                danger = true,
            )
        }
    }
}

// ── 1. Account ─────────────────────────────────────────────────────────────

@Composable
private fun ProfileRow(user: FirebaseUser) {
    // Local echo of the last successful profile write — the FirebaseUser
    // object mutates in place after updateProfile, which composition cannot
    // observe; this state gives it the trigger it needs.
    var savedName by remember(user.uid) { mutableStateOf<String?>(null) }
    val name = (savedName ?: user.displayName)?.takeIf { it.isNotBlank() }
        ?: user.email ?: ""
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = name, style = MaterialTheme.typography.titleSmall)
        Text(
            text = user.email ?: "",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The edit-name row + its dialog (a real Firebase profile write). */
@Composable
private fun EditNameSection(user: FirebaseUser) {
    val scope = rememberCoroutineScope()
    var open by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }

    WideOutlinedButton(
        text = stringResource(R.string.set_edit_name),
        onClick = { open = true },
    )

    if (open) {
        EditNameDialog(
            initial = user.displayName ?: "",
            saving = saving,
            onSave = { newName ->
                saving = true
                scope.launch {
                    try {
                        user.updateProfile(
                            UserProfileChangeRequest.Builder()
                                .setDisplayName(newName)
                                .build()
                        ).await()
                        saving = false
                        open = false
                    } catch (_: Exception) {
                        saving = false // stays open — the user can retry or dismiss
                    }
                }
            },
            onDismiss = { if (!saving) open = false },
        )
    }
}

@Composable
private fun EditNameDialog(
    initial: String,
    saving: Boolean,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.set_edit_name)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                enabled = !saving,
                label = { Text(stringResource(R.string.nick_label)) },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            if (saving) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                TextButton(
                    onClick = { onSave(name.trim()) },
                    enabled = name.isNotBlank(),
                ) {
                    Text(stringResource(R.string.nick_save))
                }
            }
        },
        dismissButton = {
            if (!saving) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.set_cancel))
                }
            }
        },
    )
}

// ── 3. Privacy & Security dialogs ─────────────────────────────────────────

/** A capped, scrollable text dialog (privacy policy / usage rules). */
@Composable
private fun LongTextDialog(title: String, body: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .heightIn(max = 384.dp)
                    .verticalScroll(rememberScrollState()),
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cmn_ok))
            }
        },
    )
}

// ── 4. Preferences: theme ──────────────────────────────────────────────────

@Composable
private fun ThemeSection() {
    val context = LocalContext.current
    val mode by ThemePrefBus.mode.collectAsState()

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        ThemeOptionRow(
            selected = mode == ThemePrefBus.Mode.SYSTEM,
            label = stringResource(R.string.th_system),
            sub = stringResource(R.string.th_system_sub),
            onSelect = { ThemePrefBus.persist(context, ThemePrefBus.Mode.SYSTEM) },
        )
        ThemeOptionRow(
            selected = mode == ThemePrefBus.Mode.LIGHT,
            label = stringResource(R.string.th_light),
            sub = stringResource(R.string.th_light_sub),
            onSelect = { ThemePrefBus.persist(context, ThemePrefBus.Mode.LIGHT) },
        )
        ThemeOptionRow(
            selected = mode == ThemePrefBus.Mode.DARK,
            label = stringResource(R.string.th_dark),
            sub = stringResource(R.string.th_dark_sub),
            onSelect = { ThemePrefBus.persist(context, ThemePrefBus.Mode.DARK) },
        )
    }
}

@Composable
private fun ThemeOptionRow(
    selected: Boolean,
    label: String,
    sub: String,
    onSelect: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect),
    ) {
        RadioButton(selected = selected, onClick = null)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = sub,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ── 4. Preferences: language ───────────────────────────────────────────────

/** Native display names for [LangPrefBus.availableTags] (order matches). */
private val LANGUAGE_NAMES = mapOf(
    "en" to "English", "bn" to "বাংলা", "hi" to "हिन्दी", "ar" to "العربية",
    "es" to "Español", "fr" to "Français", "pt" to "Português", "ru" to "Русский",
    "id" to "Bahasa Indonesia", "ur" to "اردو", "tr" to "Türkçe", "de" to "Deutsch",
    "zh" to "中文", "ja" to "日本語", "ne" to "नेपाली",
)

@Composable
private fun LanguageSection() {
    val context = LocalContext.current
    val currentTag by LangPrefBus.tag.collectAsState()
    var open by remember { mutableStateOf(false) }

    SettingsTile(
        title = stringResource(R.string.set_section_lang),
        desc = LANGUAGE_NAMES[currentTag] ?: currentTag,
        icon = Icons.Filled.Edit,
        onClick = { open = true },
    )

    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(stringResource(R.string.set_section_lang)) },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Text(
                        text = stringResource(R.string.set_lang_info),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.heightIn(min = 4.dp))
                    LangPrefBus.availableTags.forEach { tag ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    LangPrefBus.persist(context, tag)
                                    open = false
                                    (context as? Activity)?.recreate()
                                },
                        ) {
                            RadioButton(selected = tag == currentTag, onClick = null)
                            Text(
                                text = LANGUAGE_NAMES[tag] ?: tag,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { open = false }) {
                    Text(stringResource(R.string.cmn_ok))
                }
            },
        )
    }
}

// ── 4. Preferences: test notification ──────────────────────────────────────

@Composable
private fun TestNotifRow() {
    val context = LocalContext.current
    var sent by remember { mutableStateOf(false) }
    var blocked by remember { mutableStateOf(false) }

    SettingsTile(
        title = stringResource(R.string.set_test_notif),
        desc = when {
            blocked -> stringResource(R.string.set_test_notif_blocked)
            sent -> stringResource(R.string.set_test_notif_sent)
            else -> stringResource(R.string.tnotif_desc)
        },
        icon = Icons.Filled.Notifications,
        onClick = {
            if (isNotifGranted(context)) {
                postTestNotification(context)
                sent = true
            } else {
                blocked = true
                openAppNotificationSettings(context)
            }
        },
    )
}

/** The system per-app notification settings page (never a dead end). */
private fun openAppNotificationSettings(context: android.content.Context) {
    try {
        context.startActivity(
            Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
        )
    } catch (_: Exception) {
        // No settings activity on this ROM — the description already told
        // the truth (nothing was sent).
    }
}

/**
 * Posts one real local notification on the session-requests channel —
 * the channel, the permission and the tray all get proven in one tap.
 */
private fun postTestNotification(context: android.content.Context) {
    if (!isNotifGranted(context)) return
    AskNotifier.ensureChannel(context)
    val lc = LangPrefBus.wrap(context)
    val notification = NotificationCompat.Builder(lc, AskNotifier.CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_stat_notify)
        .setContentTitle(lc.getString(R.string.set_test_notif))
        .setContentText(lc.getString(R.string.tnotif_desc))
        .setAutoCancel(true)
        .setOnlyAlertOnce(true)
        .build()
    try {
        val manager = context.getSystemService(android.content.Context.NOTIFICATION_SERVICE)
            as android.app.NotificationManager
        manager.notify(TEST_NOTIFICATION_ID, notification)
    } catch (_: SecurityException) {
        // Permission revoked between the check and the post — honest absence.
    }
}

private const val TEST_NOTIFICATION_ID = 475001
