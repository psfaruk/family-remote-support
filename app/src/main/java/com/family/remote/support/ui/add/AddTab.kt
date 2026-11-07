package com.family.remote.support.ui.add

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.family.remote.support.R
import com.family.remote.support.REL_KIND_GUEST
import com.family.remote.support.ui.components.BackTitleRow
import com.family.remote.support.ui.components.EmptyState
import com.family.remote.support.ui.components.QuickActionTile
import com.family.remote.support.ui.components.SectionCard
import com.family.remote.support.ui.components.TextActionButton
import com.family.remote.support.ui.components.WideOutlinedButton
import com.family.remote.support.ui.home.HomeViewModel
import com.family.remote.support.ui.home.RelRow
import com.family.remote.support.ui.nav.AppRoutes
import com.google.firebase.auth.FirebaseUser

/**
 * ui/add/AddTab.kt — the Add-Device tab root (W3; FRONTEND_UI_PLAN_BN
 * §2.3): the FAMILY/GUEST feature cards (≥140 dp) + "scan a code to join"
 * + the explanation card. A tab root has no back row; the same composable
 * also serves the nested home/add route with one.
 *
 * v0.54.0 (worklog §101 — the Owner's directive "Home tab থেকে get help
 * give help conected family list গুলো add ডিভাইস ট্যাব এ নিয়ে যান" +
 * "কখন এই গুলো আসে আমি টের পাইনা"): this tab is now ALSO the permanent
 * home of the Get-help / Give-help quick actions and the CONNECTED FAMILY
 * LIST — both ALWAYS rendered at the top of the tab, never cards that
 * only appear once pairing succeeds. The list reads the ONE shared
 * HomeViewModel listener set (rels + presence — the same source the home
 * subtree renders from); the Add tab opens no listeners of its own.
 *
 * W3 strings: this subtree reuses the Phase-19 pairing block that has been
 * translated ×15 locales since v0.28.0 (ph_two_ways, ph_add_new,
 * ph_guest_code, ph_btn_scan) plus the qa_ and devices_ string block that
 * moved here from the Home dashboard — zero duplicate keys.
 */
@Composable
fun AddTab(
    user: FirebaseUser,
    vm: HomeViewModel,
    onOpenRoute: (String) -> Unit,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
) {
    val ui by vm.ui.collectAsState()
    // Family first, guests after — same ordering contract as the family
    // list screen; the FULL list lives here (no top-3 truncation).
    val active = ui.rels.filter { it.status == "ACTIVE" && it.guestLive }
        .sortedByDescending { it.kind != REL_KIND_GUEST }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (onBack != null) {
            BackTitleRow(title = stringResource(R.string.nav_add_device), onBack = onBack)
        } else {
            Text(
                text = stringResource(R.string.nav_add_device),
                style = MaterialTheme.typography.titleLarge,
            )
        }

        // ── quick actions: Get help / Give help (moved from Home, §101) ──
        // Persistently visible from the moment the tab opens — the Owner's
        // "কখন এই গুলো আসে আমি টের পাইনা" is answered by making them
        // unconditional, not by making them appear louder. v0.54.0 §101's
        // both-ways family sessions mean EITHER tile is honest from either
        // side: both open the member picker, and every FAMILY member's
        // detail now offers the support session in both directions.
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            QuickActionTile(
                icon = Icons.Filled.Call,
                title = stringResource(R.string.qa_get_help),
                hint = stringResource(R.string.qa_get_help_h),
                onClick = { onOpenRoute(AppRoutes.HOME_FAMILY) },
                modifier = Modifier.weight(1f),
            )
            QuickActionTile(
                icon = Icons.Filled.Favorite,
                title = stringResource(R.string.qa_give_help),
                hint = stringResource(R.string.qa_give_help_h),
                onClick = { onOpenRoute(AppRoutes.HOME_FAMILY) },
                modifier = Modifier.weight(1f),
            )
        }

        // ── the connected family list (moved from Home, §101) ──
        // Every ACTIVE link with its live presence, always rendered — the
        // empty state points at the pairing cards directly below it.
        SectionCard(
            title = stringResource(R.string.devices_title),
            icon = Icons.Filled.Person,
            trailing = if (active.size > 3) {
                {
                    TextActionButton(
                        text = stringResource(R.string.home_see_all),
                        onClick = { onOpenRoute(AppRoutes.HOME_FAMILY) },
                    )
                }
            } else {
                null
            },
        ) {
            if (active.isEmpty()) {
                EmptyState(
                    icon = Icons.Filled.Phone,
                    title = stringResource(R.string.devices_empty_title),
                    body = stringResource(R.string.devices_empty_hint),
                )
            } else {
                Column {
                    active.forEach { rel ->
                        RelRow(user.uid, rel, ui.presence[rel.id], onOpenRoute)
                    }
                }
            }
        }

        // ── the two-ways explainer (ⓘ carries the full text) ──
        SectionCard(
            title = stringResource(R.string.ph_two_ways_title),
            infoText = stringResource(R.string.ph_two_ways_info),
        ) {
            Text(
                text = stringResource(R.string.ph_role_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // ── feature card 1: THIS phone needs help (family code) ──
        AddFeatureCard(
            icon = Icons.Filled.Call,
            title = stringResource(R.string.ph_add_new_title),
            desc = stringResource(R.string.ph_add_new_desc),
            tag = stringResource(R.string.ph_family_code_title),
            onClick = { onOpenRoute(AppRoutes.ADD_FAMILY) },
        )

        // ── feature card 2: temporary guest (2-hour) ──
        AddFeatureCard(
            icon = Icons.Filled.Person,
            title = stringResource(R.string.ph_guest_code_title),
            desc = stringResource(R.string.ph_guest_code_desc),
            tag = stringResource(R.string.ph_kind_guest_short),
            onClick = { onOpenRoute(AppRoutes.ADD_GUEST) },
        )

        // ── the helper side: scan / type the other phone's code ──
        WideOutlinedButton(
            text = stringResource(R.string.ph_btn_scan),
            onClick = { onOpenRoute(AppRoutes.ADD_SCAN) },
        )

        SectionCard(title = stringResource(R.string.ph_badge_legend)) {
            Text(
                text = stringResource(R.string.ph_family_running),
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text = stringResource(R.string.ph_guest_running),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

/**
 * The Add-tab feature card (plan §2.3: featureCard 20 dp corners, ≥140 dp,
 * icon chip + title + description + the small kind tag). File-private —
 * the moved quick actions above use the shared 88 dp QuickActionTile, this
 * one is the Add-tab's bigger pairing sibling, not a second global widget.
 */
@Composable
private fun AddFeatureCard(
    icon: ImageVector,
    title: String,
    desc: String,
    tag: String,
    onClick: () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.large, // featureCard 20
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .clickable(onClick = onClick),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .padding(16.dp)
                .heightIn(min = 140.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null, // the title carries meaning
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(26.dp),
                )
            }
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = desc,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                ) {
                    Text(
                        text = tag,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }
        }
    }
}
