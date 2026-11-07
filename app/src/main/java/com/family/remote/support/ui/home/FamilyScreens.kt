package com.family.remote.support.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.family.remote.support.Nicknames
import com.family.remote.support.R
import com.family.remote.support.REL_KIND_GUEST
import com.family.remote.support.RelationshipView
import com.family.remote.support.ServerClock
import com.family.remote.support.SessionException
import com.family.remote.support.describeFirestoreError
import com.family.remote.support.localizedMessage
import com.family.remote.support.ui.components.BackTitleRow
import com.family.remote.support.ui.components.DetailRow
import com.family.remote.support.ui.components.EmptyState
import com.family.remote.support.ui.components.KindChip
import com.family.remote.support.ui.components.PresenceBadge
import com.family.remote.support.ui.components.SectionCard
import com.family.remote.support.ui.components.SettingsTile
import com.family.remote.support.ui.components.appLocale
import com.family.remote.support.ui.components.presenceMeta
import com.family.remote.support.ui.components.tickingNow
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.launch

/**
 * ui/home/FamilyScreens.kt — the home/family subtree (W2;
 * FRONTEND_UI_PLAN_BN §2.2-nested, NAVIGATION_MAP §2):
 *
 *   home/family          → FamilyListScreen   (active + no-longer-linked)
 *   home/family/{relId}  → MemberDetailScreen (connection, nickname,
 *                          ask-session, revoke/remove with confirmation)
 *
 * Nicknames are strictly LOCAL (Nicknames' own contract): one
 * SharedPreferences map, zero Firestore. Ask/revoke/remove go through
 * [HomeViewModel] so the whole subtree keeps sharing ONE listener set.
 */
// ═══════════════════════════════════════════════════════════════════

// ── home/family ───────────────────────────────────────────────────────────

@Composable
fun FamilyListScreen(
    user: FirebaseUser,
    vm: HomeViewModel,
    onOpenRoute: (String) -> Unit,
    onBack: () -> Unit,
) {
    val ui by vm.ui.collectAsState()
    val active = ui.rels.filter { it.status == "ACTIVE" && it.guestLive }
        .sortedByDescending { it.kind != REL_KIND_GUEST }
    val past = ui.rels.filter { !(it.status == "ACTIVE" && it.guestLive) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        BackTitleRow(title = stringResource(R.string.fam_title), onBack = onBack)

        SectionCard(
            title = stringResource(R.string.devices_title),
            icon = Icons.Filled.Phone,
            infoText = stringResource(R.string.devices_info),
        ) {
            if (active.isEmpty()) {
                EmptyState(
                    icon = Icons.Filled.Phone,
                    title = stringResource(R.string.fam_empty_t),
                    body = stringResource(R.string.fam_empty_h),
                    actionLabel = stringResource(R.string.devices_add_cta),
                    onAction = { onOpenRoute(com.family.remote.support.ui.nav.AppRoutes.ADD) },
                )
            } else {
                Column {
                    active.forEach { rel ->
                        RelRow(user.uid, rel, ui.presence[rel.id], onOpenRoute)
                    }
                }
            }
        }

        if (past.isNotEmpty()) {
            SectionCard(title = stringResource(R.string.fam_past_title)) {
                Column {
                    past.forEach { rel ->
                        PastRelRow(user.uid, rel, onOpenRoute)
                    }
                }
            }
        }
    }
}

/** A link that is no longer usable — kept visible (history, dimmed). */
@Composable
private fun PastRelRow(
    myUid: String,
    rel: RelationshipView,
    onOpenRoute: (String) -> Unit,
) {
    val context = LocalContext.current
    val counterpart = if (rel.helperUid == myUid) rel.recipientEmail else rel.helperEmail
    val name = Nicknames.displayName(context, rel.id, counterpart, counterpart)
    com.family.remote.support.ui.components.AppListTile(
        avatarText = name,
        title = name,
        subtitle = stringResource(
            if (rel.status == "REVOKED") R.string.md_revoked else R.string.md_expired
        ),
        onClick = { onOpenRoute(com.family.remote.support.ui.nav.AppRoutes.homeFamily(rel.id)) },
    )
}

// ── home/family/{relId} ───────────────────────────────────────────────────

@Composable
fun MemberDetailScreen(
    user: FirebaseUser,
    vm: HomeViewModel,
    relId: String,
    onBack: () -> Unit,
) {
    val ui by vm.ui.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val locale = appLocale()

    val rel = ui.rels.firstOrNull { it.id == relId }
    val counterpart = rel?.let {
        if (it.helperUid == user.uid) it.recipientEmail else it.helperEmail
    } ?: ""
    val name = Nicknames.displayName(context, relId, counterpart, counterpart)

    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var confirmRevoke by rememberSaveable { mutableStateOf(false) }
    var confirmRemove by rememberSaveable { mutableStateOf(false) }

    fun act(block: suspend () -> Unit) {
        if (busy) return
        scope.launch {
            busy = true
            error = null
            notice = null
            try {
                block()
            } catch (e: SessionException) {
                error = e.localizedMessage(context)
            } catch (e: Exception) {
                error = describeFirestoreError(context, e)
            } finally {
                busy = false
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        BackTitleRow(title = name, onBack = onBack)

        if (rel == null) {
            // Honest not-found: revoked/expired/removed links disappear
            // from the listener; the deep link stays honest (plan §6-৭).
            EmptyState(
                icon = Icons.Filled.Phone,
                title = stringResource(R.string.fam_not_found),
                body = stringResource(R.string.fam_not_found_h),
            )
        } else {
            val usable = rel.status == "ACTIVE" && rel.guestLive
            val now = if (rel.kind == REL_KIND_GUEST) tickingNow(30_000L)
            else remember { ServerClock.now() }

            // ── connection status ──
            SectionCard(
                title = stringResource(R.string.md_conn_title),
                icon = Icons.Filled.Phone,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    ) {
                        PresenceBadge(presence = ui.presence[rel.id], now = now)
                        KindChip(
                            kind = rel.kind,
                            guestExpiresAtMillis = rel.guestExpiresAtMillis,
                            now = now,
                        )
                    }
                    DetailRow(
                        label = stringResource(R.string.auth_email),
                        value = counterpart,
                    )
                    DetailRow(
                        label = stringResource(R.string.sess_kind),
                        value = stringResource(
                            if (rel.kind == REL_KIND_GUEST) {
                                R.string.md_temp_link
                            } else {
                                R.string.md_perm_link
                            }
                        ) + when {
                            rel.status == "REVOKED" ->
                                " · " + stringResource(R.string.md_revoked)
                            rel.status == "EXPIRED" || !rel.guestLive ->
                                " · " + stringResource(R.string.md_expired)
                            else -> ""
                        },
                    )
                    DetailRow(
                        label = stringResource(R.string.sess_helper),
                        value = if (rel.helperUid == user.uid) {
                            stringResource(R.string.sess_you)
                        } else {
                            name
                        },
                    )
                    NicknameRow(relId = rel.id, current = Nicknames.get(context, rel.id))
                }
            }

            // ── support actions ──
            SectionCard(
                title = stringResource(R.string.md_actions_title),
                icon = Icons.Filled.Favorite,
            ) {
                Column {
                    // v0.54.0 (§101 — both-ways family): EITHER side of a
                    // FAMILY link may ask for a session — the asker becomes
                    // the session's helper, so from the rel recipient's
                    // phone the session simply points the other way. A
                    // GUEST link's owner (the rel recipient) never sees the
                    // ask tile: guest sessions are one-way by design (the
                    // guest — the rel helper — asks, the owner allows).
                    if (rel.helperUid == user.uid || rel.kind != REL_KIND_GUEST) {
                        SettingsTile(
                            title = stringResource(R.string.md_ask_title),
                            desc = if (usable) {
                                stringResource(R.string.md_ask_desc, name)
                            } else {
                                stringResource(R.string.md_ask_disabled)
                            },
                            icon = Icons.Filled.PlayArrow,
                            onClick = if (usable && !busy) {
                                {
                                    act {
                                        vm.ask(rel)
                                        notice = context.getString(R.string.ph_req_sent, name)
                                    }
                                }
                            } else {
                                null
                            },
                        )
                    }
                    if (rel.kind == REL_KIND_GUEST) {
                        SettingsTile(
                            title = stringResource(R.string.md_remove_guest),
                            desc = stringResource(R.string.md_remove_guest_d),
                            icon = Icons.Filled.Warning,
                            danger = true,
                            onClick = if (usable) {
                                { confirmRemove = true }
                            } else {
                                null
                            },
                        )
                    } else {
                        SettingsTile(
                            title = stringResource(R.string.md_revoke),
                            desc = stringResource(R.string.md_revoke_d),
                            icon = Icons.Filled.Warning,
                            danger = true,
                            onClick = if (usable) {
                                { confirmRevoke = true }
                            } else {
                                null
                            },
                        )
                    }
                }
            }

            if (notice != null) {
                Surface3Notice(text = notice!!)
            }
            if (error != null) {
                Surface3Error(text = error!!)
            }
        }
    }

    if (confirmRevoke && rel != null) {
        AlertDialog(
            onDismissRequest = { confirmRevoke = false },
            title = { Text(stringResource(R.string.md_revoke_confirm_t, name)) },
            text = { Text(stringResource(R.string.md_revoke_confirm_b)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmRevoke = false
                    act { vm.revoke(rel.id) }
                }) {
                    Text(
                        text = stringResource(R.string.md_revoke),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmRevoke = false }) {
                    Text(stringResource(R.string.set_cancel))
                }
            },
        )
    }
    if (confirmRemove && rel != null) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text(stringResource(R.string.md_remove_confirm_t, name)) },
            text = { Text(stringResource(R.string.md_remove_confirm_b)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmRemove = false
                    act { vm.removeGuest(rel.id) }
                }) {
                    Text(
                        text = stringResource(R.string.md_remove_guest),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmRemove = false }) {
                    Text(stringResource(R.string.set_cancel))
                }
            },
        )
    }
}

/** The strictly-local nickname editor (Nicknames' documented home). */
@Composable
private fun NicknameRow(relId: String, current: String?) {
    val context = LocalContext.current
    var value by rememberSaveable(current) { mutableStateOf(current ?: "") }
    var saved by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.nick_label),
            style = MaterialTheme.typography.titleSmall,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                placeholder = { Text(stringResource(R.string.nick_hint)) },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            com.family.remote.support.ui.components.BigButton(
                text = stringResource(R.string.nick_save),
                onClick = {
                    Nicknames.set(context, relId, value)
                    saved = true
                },
                enabled = value.trim() != (current ?: ""),
            )
        }
        if (saved) {
            Text(
                text = stringResource(R.string.nick_saved),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.tertiary,
            )
            LaunchedEffect(value) { saved = false }
        }
    }
}

// ── small shared feedback rows ────────────────────────────────────────────

@Composable
private fun Surface3Notice(text: String) {
    androidx.compose.material3.Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.tertiaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        )
    }
}

@Composable
private fun Surface3Error(text: String) {
    androidx.compose.material3.Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        )
    }
}
