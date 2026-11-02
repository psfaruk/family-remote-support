package com.family.remote.support.ui.settings

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.family.remote.support.BuildConfig
import com.family.remote.support.LangPrefBus
import com.family.remote.support.PermChecklist
import com.family.remote.support.readPermChecklist
import com.family.remote.support.ui.AppTheme
import com.family.remote.support.ui.R
import com.family.remote.support.ui.ThemePrefBus
import com.family.remote.support.ui.components.BigButton
import com.family.remote.support.ui.components.DetailRow
import com.family.remote.support.ui.components.EmptyState
import com.family.remote.support.ui.components.SectionCard
import com.family.remote.support.ui.components.SettingsTile
import com.family.remote.support.ui.components.StatusBadge
import com.family.remote.support.ui.components.WideOutlinedButton
import com.family.remote.support.ui.nav.AppRoutes
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.launch

/**
 * ui/settings/SettingsTab.kt — the Settings tab (W6, FRONTEND_UI_PLAN_BN §2.5).
 * Five cards in the Owner's order: Account · Device Management · Privacy &
 * Security · Preferences · About & session (sign-out last, red outlined).
 */

@Composable
fun SettingsTab(
    user: FirebaseUser?,
    onOpenRoute: (String) -> Unit,
    onSignOut: () -> Unit,
    onTestNotification: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var perms by remember { mutableStateOf(readPermChecklist(context)) }

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
                ProfileRow(user, onEditName = { /* edit dialog */ })
                Spacer(Modifier.heightIn(min = 8.dp))
                WideOutlinedButton(
                    text = stringResource(R.string.set_edit_name),
                    onClick = { /* opens name-edit dialog */ },
                )
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
        SectionCard(title = stringResource(R.string.set_section_privacysec), icon = Icons.Filled.Shield) {
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
                icon = Icons.Filled.Security,
                onClick = { onOpenRoute(AppRoutes.ADD_PERMISSIONS) },
            )
            SettingsTile(
                title = stringResource(R.string.set_privacy),
                desc = stringResource(R.string.set_privacy_sub),
                icon = Icons.Filled.PrivacyTip,
                onClick = { /* open privacy policy dialog */ },
            )
            SettingsTile(
                title = stringResource(R.string.set_rules),
                desc = stringResource(R.string.set_rules_sub),
                icon = Icons.Filled.Info,
                onClick = { /* open rules dialog */ },
            )
        }

        // ── 4. Preferences ──
        SectionCard(title = stringResource(R.string.set_section_prefs), icon = Icons.Filled.Settings) {
            ThemeOptionRow(
                selected = ThemePrefBus.Mode.SYSTEM,
                label = stringResource(R.string.th_system),
                sub = stringResource(R.string.th_system_sub),
                onSelect = { ThemePrefBus.persist(context, ThemePrefBus.Mode.SYSTEM) },
            )
            ThemeOptionRow(
                selected = ThemePrefBus.Mode.LIGHT,
                label = stringResource(R.string.th_light),
                sub = stringResource(R.string.th_light_sub),
                onSelect = { ThemePrefBus.persist(context, ThemePrefBus.Mode.LIGHT) },
            )
            ThemeOptionRow(
                selected = ThemePrefBus.Mode.DARK,
                label = stringResource(R.string.th_dark),
                sub = stringResource(R.string.th_dark_sub),
                onSelect = { ThemePrefBus.persist(context, ThemePrefBus.Mode.DARK) },
            )
            LanguageRow(onSelect = { /* language picker dialog */ })
            TestNotifRow(onTestNotification = onTestNotification)
        }

        // ── 5. About & session (sign-out LAST, red outlined) ──
        SectionCard(title = stringResource(R.string.set_section_about_session), icon = Icons.Filled.Info) {
            DetailRow(
                label = stringResource(R.string.set_version),
                value = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
            )
            Spacer(Modifier.heightIn(min = 12.dp))
            SignOutButton(onSignOut = onSignOut)
        }
    }
}

@Composable
private fun ProfileRow(user: FirebaseUser, onEditName: () -> Unit) {
    val name = user.displayName ?: user.email ?: ""
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = name, style = MaterialTheme.typography.titleSmall)
        Text(
            text = user.email ?: "",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ThemeOptionRow(
    selected: ThemePrefBus.Mode,
    label: String,
    sub: String,
    onSelect: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Switch(checked = false, onCheckedChange = null)
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = MaterialTheme.typography.titleSmall)
            Text(
                text = sub,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LanguageRow(onSelect: () -> Unit) {
    SettingsTile(
        title = stringResource(R.string.set_section_lang),
        desc = stringResource(R.string.set_lang_sub),
        icon = Icons.Filled.Language,
        onClick = onSelect,
    )
}

@Composable
private fun TestNotifRow(onTestNotification: () -> Unit) {
    var sent by remember { mutableStateOf(false) }
    SettingsTile(
        title = stringResource(R.string.set_test_notif),
        desc = if (sent) stringResource(R.string.set_test_notif_sent)
        else stringResource(R.string.tnotif_desc),
        icon = Icons.Filled.Notifications,
        onClick = {
            onTestNotification()
            sent = true
        },
    )
}

@Composable
private fun SignOutButton(onSignOut: () -> Unit) {
    WideOutlinedButton(
        text = stringResource(R.string.set_sign_out),
        onClick = onSignOut,
        danger = true,
    )
}
