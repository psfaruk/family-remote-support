package com.family.remote.support.ui.nav

import android.os.Build
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.family.remote.support.DeepLinks
import com.family.remote.support.R
import com.family.remote.support.REL_KIND_FAMILY
import com.family.remote.support.REL_KIND_GUEST
import com.family.remote.support.registerFcmToken
import com.family.remote.support.sendHeartbeat
import com.family.remote.support.stableDeviceId
import com.family.remote.support.ui.AuthGate
import com.family.remote.support.ui.add.AddTab
import com.family.remote.support.ui.add.AddViewModel
import com.family.remote.support.ui.add.PairScreen
import com.family.remote.support.ui.add.PermissionsScreen
import com.family.remote.support.ui.add.ScanScreen
import com.family.remote.support.ui.components.EmptyState
import com.family.remote.support.ui.home.FamilyListScreen
import com.family.remote.support.ui.home.HomeTab
import com.family.remote.support.ui.home.HomeViewModel
import com.family.remote.support.ui.home.MemberDetailScreen
import com.family.remote.support.ui.home.SessionDetailScreen
import com.family.remote.support.ui.home.SessionsScreen
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * ui/nav/AppNav.kt — the route table + the app shell
 * (FRONTEND_ARCHITECTURE §3, NAVIGATION_MAP §2–§4).
 *
 * W1 (rebuild wave 1): exactly four tabs (Home · Add Device · Tracking ·
 * Settings) + the bell's notifications route, on Navigation-Compose 2.8
 * with saveState/restoreState tab semantics (Decision C3). Every route is
 * already registered so deep links from notifications can never dead-end;
 * the screens behind them land wave by wave (Home W2, Pairing W3, …).
 *
 * Motion (§4): tab switches crossfade ≤150 ms; nested routes slide
 * ≈200 ms. Deep links arrive through the AskNotifier [DeepLinks] bus —
 * cold start parks the route before composition, a live tap re-fires
 * onNewIntent; this shell consumes [DeepLinks.pending] exactly once.
 */

/** The one route table — NotifCenter's notification taps reuse it verbatim. */
object AppRoutes {
    // Tab HOME
    const val HOME = "home"
    const val HOME_FAMILY = "home/family"
    const val HOME_FAMILY_ARG = "home/family/{relId}"
    const val HOME_SESSIONS = "home/sessions"
    const val HOME_SESSION_ARG = "home/sessions/{id}"
    const val HOME_ADD = "home/add"

    // Tab ADD DEVICE
    const val ADD = "add"
    const val ADD_FAMILY = "add/family"
    const val ADD_GUEST = "add/guest"
    const val ADD_SCAN = "add/scan"
    const val ADD_PROGRESS = "add/progress"
    const val ADD_PERMISSIONS = "add/permissions"

    // Tab TRACKING
    const val TRACKING = "tracking"
    const val TRACKING_DETAIL_ARG = "tracking/{relId}"
    const val TRACKING_HISTORY_ARG = "tracking/{relId}/history"
    const val TRACKING_PLACES = "tracking/places"

    // Tab SETTINGS
    const val SETTINGS = "settings"

    // Bell target (NAVIGATION_MAP §2 — root-level, outside the four tabs).
    const val NOTIFICATIONS = "notifications"

    /** Filled helpers (arg-bearing patterns take values at call time). */
    fun homeFamily(relId: String): String = HOME_FAMILY_ARG.replace("{relId}", relId)
    fun homeSession(id: String): String = HOME_SESSION_ARG.replace("{id}", id)
    fun trackingDetail(relId: String): String = TRACKING_DETAIL_ARG.replace("{relId}", relId)
    fun trackingHistory(relId: String): String = TRACKING_HISTORY_ARG.replace("{relId}", relId)

    /** Every concrete route the NavHost can resolve (patterns excluded). */
    val concrete = setOf(
        HOME, HOME_FAMILY, HOME_SESSIONS, HOME_ADD,
        ADD, ADD_FAMILY, ADD_GUEST, ADD_SCAN, ADD_PROGRESS, ADD_PERMISSIONS,
        TRACKING, TRACKING_PLACES,
        SETTINGS, NOTIFICATIONS,
    )

    /**
     * Can the NavHost resolve this route — either a concrete route or an
     * instance of an arg pattern? Unknown/future routes must never
     * dead-end (NotifCenter's "else → home" guarantee, enforced here a
     * second time at the navigation layer).
     */
    fun isResolvable(route: String): Boolean = when {
        route in concrete -> true
        route.startsWith("home/family/") -> true
        route.startsWith("home/sessions/") -> true
        route.startsWith("tracking/") -> true
        else -> false
    }
}

/** The four tabs (NAVIGATION_MAP §1 — exactly four, no fifth). */
private enum class AppTab(val route: String) {
    HOME(AppRoutes.HOME),
    ADD(AppRoutes.ADD),
    TRACKING(AppRoutes.TRACKING),
    SETTINGS(AppRoutes.SETTINGS),
}

/** Which tab owns this route (nested routes belong to their tab). */
private fun tabForRoute(route: String?): AppTab? = when {
    route == null -> null
    route == AppRoutes.HOME || route.startsWith("home/") -> AppTab.HOME
    route == AppRoutes.ADD || route.startsWith("add/") -> AppTab.ADD
    route == AppRoutes.TRACKING || route.startsWith("tracking/") -> AppTab.TRACKING
    route == AppRoutes.SETTINGS -> AppTab.SETTINGS
    else -> null // notifications & friends — keep the current tab lit
}

/**
 * The root shell: auth gate OR top bar (title + bell) + bottom
 * navigation + NavHost. The only public surface MainActivity needs.
 *
 * W2 (v0.50.0): the signed-out user gets the full-screen [AuthGate]
 * (no bars — the prototype's contract); the signed-in user gets the
 * shell plus the foreground-only presence ticker + one-time push
 * registration (PresenceRepo's documented contract), and the home
 * subtree renders the REAL screens (dashboard, family list, member
 * detail, sessions, session detail) against ONE shared
 * [HomeViewModel].
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun RootShell() {
    val nav = rememberNavController()

    // ── auth state: the ONE source of truth ──
    val auth = remember { FirebaseAuth.getInstance() }
    var user by remember { mutableStateOf<FirebaseUser?>(auth.currentUser) }
    DisposableEffect(auth) {
        val listener = FirebaseAuth.AuthStateListener { user = it.currentUser }
        auth.addAuthStateListener(listener)
        onDispose { auth.removeAuthStateListener(listener) }
    }

    val current = user
    if (current == null) {
        // Full-screen gate — no top bar, no tabs (prototype contract).
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            AuthGate()
        }
        return
    }

    // ── the home subtree's ONE listener set ──
    val homeVm: HomeViewModel = viewModel()
    LaunchedEffect(current.uid) { homeVm.setUser(current) }

    // ── the add subtree's ONE pairing FSM (W3) ──
    val addVm: AddViewModel = viewModel()

    // ── foreground-only presence + push registration ──
    PresenceTickerEffect(current)

    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route

    // Which tab is lit: the owning tab of the current route; a route that
    // belongs to no tab (notifications) keeps the last tab lit.
    var selectedTab by rememberSaveable { mutableStateOf(AppTab.HOME) }
    LaunchedEffect(route) {
        tabForRoute(route)?.let { selectedTab = it }
    }

    // AskNotifier's deep-link bus: cold start parked the route before
    // composition; a tap on a live app re-fires it via onNewIntent.
    // Consume exactly once, then navigate — never a dead end.
    LaunchedEffect(nav) {
        DeepLinks.pending.collect { pending ->
            if (!pending.isNullOrBlank()) {
                goRoute(nav, pending)
                DeepLinks.consume()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(
                        onClick = { goRoute(nav, AppRoutes.NOTIFICATIONS) }
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Notifications,
                            contentDescription = stringResource(R.string.notif_title),
                        )
                    }
                },
            )
        },
        bottomBar = {
            AppBottomNav(
                selected = selectedTab,
                onSelect = { tab ->
                    if (tab != selectedTab) switchTab(nav, tab.route)
                },
            )
        },
    ) { padding ->
        AppNavHost(
            nav = nav,
            user = current,
            homeVm = homeVm,
            addVm = addVm,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        )
    }
}

// ── Presence + push (the shell owns them — not the host, not a tab) ─────

/**
 * The foreground-only presence ticker (v0.36 quota directive):
 * heartbeat on entry + every 5 min while RESUMED + a final beat when
 * the user leaves (NonCancellable — a cancelled coroutine may still
 * suspend inside [withContext]). Plus the one-shot FCM registration
 * per signed-in uid, best effort (a push hiccup must never block UI).
 */
@Composable
private fun PresenceTickerEffect(user: FirebaseUser) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val owner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    LaunchedEffect(user.uid) {
        val appContext = context.applicationContext
        val deviceId = stableDeviceId(appContext)
        val model = ((Build.MANUFACTURER?.trim())?.plus(" ") ?: "") + (Build.MODEL ?: "")
        owner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            try {
                sendHeartbeat(user.uid, deviceId, model)
                while (true) {
                    delay(5 * 60_000L)
                    sendHeartbeat(user.uid, deviceId, model)
                }
            } finally {
                withContext(NonCancellable) {
                    try {
                        sendHeartbeat(user.uid, deviceId, model)
                    } catch (_: Exception) {
                        // best effort — the 20-min online window covers it
                    }
                }
            }
        }
    }
    LaunchedEffect(user.uid, user.email) {
        try {
            registerFcmToken(user.uid, stableDeviceId(context.applicationContext))
        } catch (_: Exception) {
            // best effort — AskNotifier's alive-process path still works
        }
    }
}

// ── Navigation primitives ─────────────────────────────────────────────────

/** Tab switch (Decision C3): saveState/restoreState, single top. */
private fun switchTab(nav: NavHostController, route: String) {
    nav.navigate(route) {
        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/**
 * Route anywhere — deep links AND the bell. Tab roots switch tabs;
 * nested routes push onto the current stack; anything unresolvable falls
 * back to Home (NotifCenter's "never a dead end" rule, defense in depth).
 */
private fun goRoute(nav: NavHostController, route: String) {
    when (route) {
        AppRoutes.HOME, AppRoutes.ADD, AppRoutes.TRACKING, AppRoutes.SETTINGS ->
            switchTab(nav, route)
        else -> {
            if (AppRoutes.isResolvable(route)) {
                nav.navigate(route) { launchSingleTop = true }
            } else {
                switchTab(nav, AppRoutes.HOME)
            }
        }
    }
}

// ── Bottom navigation ─────────────────────────────────────────────────────

@Composable
private fun AppBottomNav(selected: AppTab, onSelect: (AppTab) -> Unit) {
    NavigationBar {
        NavigationBarItem(
            selected = selected == AppTab.HOME,
            onClick = { onSelect(AppTab.HOME) },
            icon = { Icon(Icons.Filled.Home, contentDescription = null) },
            label = { Text(stringResource(R.string.nav_home)) },
        )
        NavigationBarItem(
            selected = selected == AppTab.ADD,
            onClick = { onSelect(AppTab.ADD) },
            icon = { Icon(Icons.Filled.Add, contentDescription = null) },
            label = { Text(stringResource(R.string.nav_add_device)) },
        )
        NavigationBarItem(
            selected = selected == AppTab.TRACKING,
            onClick = { onSelect(AppTab.TRACKING) },
            icon = { Icon(Icons.Filled.LocationOn, contentDescription = null) },
            label = { Text(stringResource(R.string.nav_tracking)) },
        )
        NavigationBarItem(
            selected = selected == AppTab.SETTINGS,
            onClick = { onSelect(AppTab.SETTINGS) },
            icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
            label = { Text(stringResource(R.string.nav_settings)) },
        )
    }
}

// ── NavHost ───────────────────────────────────────────────────────────────

/** Motion budget (NAVIGATION_MAP §4): tabs crossfade 150 ms, nested 200 ms. */
private const val TAB_MS = 150
private const val NESTED_MS = 200

@Composable
private fun AppNavHost(
    nav: NavHostController,
    user: FirebaseUser,
    homeVm: HomeViewModel,
    addVm: AddViewModel,
    modifier: Modifier = Modifier,
) {
    val go: (String) -> Unit = remember(nav) { { route -> goRoute(nav, route) } }
    val back: () -> Unit = remember(nav) { { nav.popBackStack() } }
    NavHost(
        navController = nav,
        startDestination = AppRoutes.HOME,
        modifier = modifier,
        enterTransition = { fadeIn(tween(TAB_MS)) },
        exitTransition = { fadeOut(tween(TAB_MS)) },
        popEnterTransition = { fadeIn(tween(TAB_MS)) },
        popExitTransition = { fadeOut(tween(TAB_MS)) },
    ) {
        // ── Tab HOME (W2: the REAL screens, one shared HomeViewModel) ───
        composable(AppRoutes.HOME) {
            HomeTab(user = user, vm = homeVm, onOpenRoute = go)
        }
        composable(
            AppRoutes.HOME_FAMILY,
            enterTransition = { slideInHorizontally(tween(NESTED_MS)) { it / 4 } + fadeIn(tween(NESTED_MS)) },
            exitTransition = { fadeOut(tween(TAB_MS)) },
            popEnterTransition = { fadeIn(tween(TAB_MS)) },
            popExitTransition = { slideOutHorizontally(tween(NESTED_MS)) { it / 4 } + fadeOut(tween(NESTED_MS)) },
        ) {
            FamilyListScreen(user = user, vm = homeVm, onOpenRoute = go, onBack = back)
        }
        composable(
            AppRoutes.HOME_FAMILY_ARG,
            arguments = listOf(navArgument("relId") { type = NavType.StringType }),
            enterTransition = { slideInHorizontally(tween(NESTED_MS)) { it / 4 } + fadeIn(tween(NESTED_MS)) },
            exitTransition = { fadeOut(tween(TAB_MS)) },
            popEnterTransition = { fadeIn(tween(TAB_MS)) },
            popExitTransition = { slideOutHorizontally(tween(NESTED_MS)) { it / 4 } + fadeOut(tween(NESTED_MS)) },
        ) { entry ->
            val relId = entry.arguments?.getString("relId").orEmpty()
            MemberDetailScreen(user = user, vm = homeVm, relId = relId, onBack = back)
        }
        composable(
            AppRoutes.HOME_SESSIONS,
            enterTransition = { slideInHorizontally(tween(NESTED_MS)) { it / 4 } + fadeIn(tween(NESTED_MS)) },
            exitTransition = { fadeOut(tween(TAB_MS)) },
            popEnterTransition = { fadeIn(tween(TAB_MS)) },
            popExitTransition = { slideOutHorizontally(tween(NESTED_MS)) { it / 4 } + fadeOut(tween(NESTED_MS)) },
        ) {
            SessionsScreen(user = user, vm = homeVm, onOpenRoute = go, onBack = back)
        }
        composable(
            AppRoutes.HOME_SESSION_ARG,
            arguments = listOf(navArgument("id") { type = NavType.StringType }),
            enterTransition = { slideInHorizontally(tween(NESTED_MS)) { it / 4 } + fadeIn(tween(NESTED_MS)) },
            exitTransition = { fadeOut(tween(TAB_MS)) },
            popEnterTransition = { fadeIn(tween(TAB_MS)) },
            popExitTransition = { slideOutHorizontally(tween(NESTED_MS)) { it / 4 } + fadeOut(tween(NESTED_MS)) },
        ) { entry ->
            val id = entry.arguments?.getString("id").orEmpty()
            SessionDetailScreen(sessionId = id, user = user, vm = homeVm, onOpenRoute = go, onBack = back)
        }
        composable(
            AppRoutes.HOME_ADD,
            enterTransition = { slideInHorizontally(tween(NESTED_MS)) { it / 4 } + fadeIn(tween(NESTED_MS)) },
            exitTransition = { fadeOut(tween(TAB_MS)) },
            popEnterTransition = { fadeIn(tween(TAB_MS)) },
            popExitTransition = { slideOutHorizontally(tween(NESTED_MS)) { it / 4 } + fadeOut(tween(NESTED_MS)) },
        ) {
            AddTab(onOpenRoute = go, onBack = back)
        }

        // ── Tab ADD DEVICE (W3: the REAL pairing subtree — plan §2.3) ──
        composable(AppRoutes.ADD) {
            AddTab(onOpenRoute = go)
        }
        composable(
            AppRoutes.ADD_FAMILY,
            enterTransition = { slideInHorizontally(tween(NESTED_MS)) { it / 4 } + fadeIn(tween(NESTED_MS)) },
            exitTransition = { fadeOut(tween(TAB_MS)) },
            popEnterTransition = { fadeIn(tween(TAB_MS)) },
            popExitTransition = { slideOutHorizontally(tween(NESTED_MS)) { it / 4 } + fadeOut(tween(NESTED_MS)) },
        ) {
            PairScreen(user = user, vm = addVm, kind = REL_KIND_FAMILY, onBack = back)
        }
        composable(
            AppRoutes.ADD_GUEST,
            enterTransition = { slideInHorizontally(tween(NESTED_MS)) { it / 4 } + fadeIn(tween(NESTED_MS)) },
            exitTransition = { fadeOut(tween(TAB_MS)) },
            popEnterTransition = { fadeIn(tween(TAB_MS)) },
            popExitTransition = { slideOutHorizontally(tween(NESTED_MS)) { it / 4 } + fadeOut(tween(NESTED_MS)) },
        ) {
            PairScreen(user = user, vm = addVm, kind = REL_KIND_GUEST, onBack = back)
        }
        composable(
            AppRoutes.ADD_SCAN,
            enterTransition = { slideInHorizontally(tween(NESTED_MS)) { it / 4 } + fadeIn(tween(NESTED_MS)) },
            exitTransition = { fadeOut(tween(TAB_MS)) },
            popEnterTransition = { fadeIn(tween(TAB_MS)) },
            popExitTransition = { slideOutHorizontally(tween(NESTED_MS)) { it / 4 } + fadeOut(tween(NESTED_MS)) },
        ) {
            ScanScreen(user = user, vm = addVm, onBack = back, onOpenRoute = go)
        }
        composable(
            AppRoutes.ADD_PROGRESS,
            enterTransition = { slideInHorizontally(tween(NESTED_MS)) { it / 4 } + fadeIn(tween(NESTED_MS)) },
            exitTransition = { fadeOut(tween(TAB_MS)) },
            popEnterTransition = { fadeIn(tween(TAB_MS)) },
            popExitTransition = { slideOutHorizontally(tween(NESTED_MS)) { it / 4 } + fadeOut(tween(NESTED_MS)) },
        ) {
            // Generic pairing-progress route (deep-link resolvable): the
            // PairScreen IS the live progress UI (5-min TTL + snapshot watch).
            PairScreen(user = user, vm = addVm, kind = REL_KIND_FAMILY, onBack = back)
        }
        composable(
            AppRoutes.ADD_PERMISSIONS,
            enterTransition = { slideInHorizontally(tween(NESTED_MS)) { it / 4 } + fadeIn(tween(NESTED_MS)) },
            exitTransition = { fadeOut(tween(TAB_MS)) },
            popEnterTransition = { fadeIn(tween(TAB_MS)) },
            popExitTransition = { slideOutHorizontally(tween(NESTED_MS)) { it / 4 } + fadeOut(tween(NESTED_MS)) },
        ) {
            PermissionsScreen(onBack = back)
        }

        // ── Tab TRACKING ────────────────────────────────────────────────
        composable(AppRoutes.TRACKING) { WipTab(Icons.Filled.LocationOn, R.string.nav_tracking) }
        composable(
            AppRoutes.TRACKING_DETAIL_ARG,
            arguments = listOf(navArgument("relId") { type = NavType.StringType }),
            enterTransition = { slideInHorizontally(tween(NESTED_MS)) { it / 4 } + fadeIn(tween(NESTED_MS)) },
            exitTransition = { fadeOut(tween(TAB_MS)) },
            popEnterTransition = { fadeIn(tween(TAB_MS)) },
            popExitTransition = { slideOutHorizontally(tween(NESTED_MS)) { it / 4 } + fadeOut(tween(NESTED_MS)) },
        ) { WipTab(Icons.Filled.LocationOn, R.string.nav_tracking) }
        composable(
            AppRoutes.TRACKING_HISTORY_ARG,
            arguments = listOf(navArgument("relId") { type = NavType.StringType }),
            enterTransition = { slideInHorizontally(tween(NESTED_MS)) { it / 4 } + fadeIn(tween(NESTED_MS)) },
            exitTransition = { fadeOut(tween(TAB_MS)) },
            popEnterTransition = { fadeIn(tween(TAB_MS)) },
            popExitTransition = { slideOutHorizontally(tween(NESTED_MS)) { it / 4 } + fadeOut(tween(NESTED_MS)) },
        ) { WipTab(Icons.Filled.LocationOn, R.string.nav_tracking) }
        composable(
            AppRoutes.TRACKING_PLACES,
            enterTransition = { slideInHorizontally(tween(NESTED_MS)) { it / 4 } + fadeIn(tween(NESTED_MS)) },
            exitTransition = { fadeOut(tween(TAB_MS)) },
            popEnterTransition = { fadeIn(tween(TAB_MS)) },
            popExitTransition = { slideOutHorizontally(tween(NESTED_MS)) { it / 4 } + fadeOut(tween(NESTED_MS)) },
        ) { WipTab(Icons.Filled.LocationOn, R.string.nav_tracking) }

        // ── Tab SETTINGS ────────────────────────────────────────────────
        composable(AppRoutes.SETTINGS) { WipTab(Icons.Filled.Settings, R.string.nav_settings) }

        // ── Notifications (bell target) ─────────────────────────────────
        composable(
            AppRoutes.NOTIFICATIONS,
            enterTransition = { slideInHorizontally(tween(NESTED_MS)) { it / 4 } + fadeIn(tween(NESTED_MS)) },
            exitTransition = { fadeOut(tween(TAB_MS)) },
            popEnterTransition = { fadeIn(tween(TAB_MS)) },
            popExitTransition = { slideOutHorizontally(tween(NESTED_MS)) { it / 4 } + fadeOut(tween(NESTED_MS)) },
        ) { WipTab(Icons.Filled.Notifications, R.string.notif_title) }
    }
}

// ── W1 placeholder screen ─────────────────────────────────────────────────

/**
 * Honest wave-1 placeholder: the route exists (deep links resolve, the
 * back stack behaves) and says exactly what is true — the section arrives
 * with its build wave. No fabricated data, ever.
 */
@Composable
private fun WipTab(icon: androidx.compose.ui.graphics.vector.ImageVector, labelRes: Int) {
    EmptyState(
        icon = icon,
        title = stringResource(labelRes),
        body = stringResource(R.string.ph_wip),
        modifier = Modifier.fillMaxSize(),
    )
}
