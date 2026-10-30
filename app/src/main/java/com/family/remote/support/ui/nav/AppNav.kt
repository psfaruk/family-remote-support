package com.family.remote.support.ui.nav

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import com.family.remote.support.R

// ═══════════════════════════════════════════════════════════════════════
// Phase 21/M2 (v0.30.0) — the shared application shell, extracted from
// MainActivity into `ui/nav/AppNav.kt` (FRONTEND_ARCHITECTURE §3 target
// layout, agent-ownership map §18 "Shell + navigation"). This is the
// Decision-C3 SPIKE slice: routes + top bar + bottom bar live here; the
// NavHost wiring + per-tab content stay in MainActivity for now (full
// screen extraction is M3), so the W1 gate items — cold start from a
// session notification, the permission-wizard overlay and the fullscreen
// session dialog — keep their exact composition sites and behavior.
// ═══════════════════════════════════════════════════════════════════════

/**
 * Route table (NAVIGATION_MAP §2). Exactly FOUR primary tabs per the
 * master instruction — Devices/Sessions are Home sections + Settings
 * surfaces, never extra tabs (conflict C2 resolution). NOTIFICATIONS is
 * the header-bell target, a plain destination above the tabs.
 */
object AppRoutes {
    const val HOME = "home"
    const val ADD = "add"
    const val TRACKING = "tracking"
    const val SETTINGS = "settings"
    const val NOTIFICATIONS = "notifications"
}

/**
 * Bottom-tab switch with state preservation (NAVIGATION_MAP §3):
 * popUpTo(start){saveState} keeps one destination per tab in the stack,
 * launchSingleTop never stacks the same tab twice, restoreState brings
 * back the tab's scroll/nested state on return. System back from a tab
 * therefore walks back to Home first, then exits — the platform-standard
 * tab behavior (previously back always exited; documented delta in the
 * M2 release notes + TEST_PLAN scenario 8 for the Owner's device check).
 */
fun NavHostController.goTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/**
 * Phase 21/M2 — shared top bar. The app title is now the localized,
 * brand-fixed `app_title` ("Remote Family Support") — this closes the
 * hardcoded-top-bar-title finding from the Agent 2-a audit (it was the
 * literal "Family Remote Support" before). The presence chip is passed
 * as a slot so this file stays free of presence/Firestore deps; the bell
 * badge keeps its honest meaning: ON while a consent ask is pending
 * (the only signal that demands an action right now).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(
    pendingAsk: Boolean,
    onBellClick: () -> Unit,
    presence: (@Composable () -> Unit)? = null
) {
    TopAppBar(
        title = {
            Text(
                text = stringResource(R.string.app_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        actions = {
            if (presence != null) {
                presence()
            }
            IconButton(onClick = onBellClick) {
                BadgedBox(badge = { if (pendingAsk) Badge() }) {
                    Icon(
                        imageVector = Icons.Filled.Notifications,
                        contentDescription = stringResource(R.string.cd_notifications)
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background
        )
    )
}

/**
 * Phase 21/M2 (v0.30.0) — bottom navigation after the C1 rename
 * (NAVIGATION_MAP §1/§6): Home · **Add Device** · **Tracking** · Settings.
 * Strings-only rename in the same 4 slots —
 * no 5th tab; the labels come from `nav_home / nav_add_device /
 * nav_tracking / nav_settings` in all 15 languages. Selection follows
 * the NavHost's current route, so the Notifications surface shows with
 * no tab highlighted (honest state, no fake selection).
 */
@Composable
fun AppBottomNav(currentRoute: String?, onSelect: (String) -> Unit) {
    NavigationBar {
        NavigationBarItem(
            selected = currentRoute == AppRoutes.HOME,
            onClick = { onSelect(AppRoutes.HOME) },
            icon = { Icon(Icons.Filled.Home, contentDescription = null) },
            label = { Text(stringResource(R.string.nav_home)) }
        )
        NavigationBarItem(
            selected = currentRoute == AppRoutes.ADD,
            onClick = { onSelect(AppRoutes.ADD) },
            // Icons.Filled.Add (core set): NAVIGATION_MAP §1 said PersonAdd,
            // but PersonAdd lives in material-icons-extended — unacceptable
            // here (isMinifyEnabled=false, no R8: extended icons would bloat
            // the APK). Add is the core-set equivalent for an "add device"
            // affordance; deviation recorded in the worklog §65 + map.
            icon = { Icon(Icons.Filled.Add, contentDescription = null) },
            label = { Text(stringResource(R.string.nav_add_device)) }
        )
        NavigationBarItem(
            selected = currentRoute == AppRoutes.TRACKING,
            onClick = { onSelect(AppRoutes.TRACKING) },
            icon = { Icon(Icons.Filled.LocationOn, contentDescription = null) },
            label = { Text(stringResource(R.string.nav_tracking)) }
        )
        NavigationBarItem(
            selected = currentRoute == AppRoutes.SETTINGS,
            onClick = { onSelect(AppRoutes.SETTINGS) },
            icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
            label = { Text(stringResource(R.string.nav_settings)) }
        )
    }
}
