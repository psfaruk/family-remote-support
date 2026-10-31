package com.family.remote.support

import android.Manifest
import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Surface
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.RadioButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Offset
import kotlin.math.hypot
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.round
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import android.widget.Toast
import androidx.compose.ui.res.stringResource
import com.family.remote.support.ui.components.AppListTile
import com.family.remote.support.ui.components.EmptyState
import com.family.remote.support.ui.components.SectionCard
import com.family.remote.support.ui.components.WideOutlinedButton
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.family.remote.support.ui.components.AppListTile
import com.family.remote.support.ui.components.EmptyState
import com.family.remote.support.ui.components.SectionCard
import com.family.remote.support.ui.components.WideOutlinedButton
import com.family.remote.support.ui.nav.AppBottomNav
import com.family.remote.support.ui.nav.AppRoutes
import com.family.remote.support.ui.nav.AppTopBar
import com.family.remote.support.ui.nav.goTab
import com.family.remote.support.ui.notifications.NotificationsScreen
// M3 (v0.31.0) — extracted screens + component library (FRONTEND_ARCHITECTURE
// §3): the 4 tabs, the consent dialog and the presence chip now live in
// their own ui/* files; MainActivity keeps the shell wiring, the root
// session effects and the session surfaces only.
import androidx.lifecycle.viewmodel.compose.viewModel
import com.family.remote.support.ui.components.PresenceChip
import com.family.remote.support.ui.home.HomeConsentDialog
import com.family.remote.support.ui.home.HomeTab
import com.family.remote.support.ui.home.SessionWatchViewModel
import com.family.remote.support.ui.pairing.PairingCard
import com.family.remote.support.ui.settings.SettingsTab
import com.family.remote.support.ui.tracking.TrackingTab
// M4 (v0.32.0) — nested Home detail routes (NAVIGATION_MAP §2: home/family,
// home/family/{relId}, home/sessions, home/sessions/{id}) and their motion
// (map §4: forward slide ≈200 ms honoring the system Animator scale).
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.family.remote.support.ui.home.FamilyOverviewScreen
import com.family.remote.support.ui.home.MemberDetailScreen
import com.family.remote.support.ui.home.RecentActivityScreen
import com.family.remote.support.ui.home.SessionDetailScreen
import com.family.remote.support.ui.AppWarningColor
// M5 (v0.33.0) — the Tracking detail route (NAVIGATION_MAP §2
// tracking-subtree): tracking/{relId} Location Details screen.
import com.family.remote.support.ui.tracking.LocationDetailScreen
import com.family.remote.support.ui.AuthGate
import com.family.remote.support.ui.FamilyRemoteSupportTheme
import com.family.remote.support.ui.LangPrefBus
import com.family.remote.support.ui.ThemePrefBus
import com.family.remote.support.ui.ThemePreview
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.UserProfileChangeRequest
import org.webrtc.EglBase
import kotlinx.coroutines.delay
import org.webrtc.SurfaceViewRenderer
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

// ═══════════════════════════════════════════════════════════════════════
// Phase 9→11.6 — theme history: v0.9.4 introduced the warm senior theme;
// v0.14.0 moved explanations behind ⓘ popups (component-based UI); v0.15.0
// moved the whole design system into ui/Theme.kt (blue identity + dark
// theme + Light/Dark/System).
// ═══════════════════════════════════════════════════════════════════════
// Phase 11.6 — the color schemes + typography now live in ui/Theme.kt
// (single design-system source of truth). New code should use
// MaterialTheme.colorScheme / com.family.remote.support.ui directly.

// ═══════════════════════════════════════════════════════════════════════
// v0.14.0 — reusable building blocks. Every tab is assembled from THESE:
// SectionCard (icon + ⓘ popup), AppListTile (chat-style row), SettingsTile
// (icon + title + description + switch/chevron), EmptyState, chips.
// ═══════════════════════════════════════════════════════════════════════


class MainActivity : ComponentActivity() {
    // Phase 16 (v0.20.0) — the app opens in the SAVED language (English on a
    // fresh install, per Owner) by wrapping the base context with that
    // Locale. Changing the language in Settings recreates the activity, so
    // every stringResource() repaints in the new language immediately.
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LangPrefBus.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Phase 11.6 — Light / Dark / System (persisted, ui/Theme.kt).
        // Loaded synchronously BEFORE setContent so the very first frame
        // already wears the right theme — no flash, no wrong-theme repaint.
        ThemePrefBus.loadSync(this)
        setContent {
            FamilyRemoteSupportTheme(themePref = ThemePrefBus.pref) {
                HomeScreen()
            }
        }
    }
}

/**
 * Phase 3 sign-in: request a Google ID token via Credential Manager, then
 * exchange it for a Firebase Auth session. Requires GOOGLE_WEB_CLIENT_ID
 * (Web OAuth client) to be baked into BuildConfig at build time.
 */
private suspend fun signInWithGoogle(activity: ComponentActivity): FirebaseUser {
    val credentialManager = CredentialManager.create(activity)
    val googleIdOption = GetGoogleIdOption.Builder()
        .setServerClientId(BuildConfig.GOOGLE_WEB_CLIENT_ID)
        .setFilterByAuthorizedAccounts(false)
        .setAutoSelectEnabled(false)
        .build()
    val request = GetCredentialRequest.Builder()
        .addCredentialOption(googleIdOption)
        .build()
    val response = credentialManager.getCredential(activity, request)
    val credential = response.credential
    if (credential is CustomCredential &&
        credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
    ) {
        val googleId = GoogleIdTokenCredential.createFrom(credential.data)
        val firebaseCredential = GoogleAuthProvider.getCredential(googleId.idToken, null)
        val result = FirebaseAuth.getInstance().signInWithCredential(firebaseCredential).await()
        return result.user ?: throw IllegalStateException("Sign-in returned no user")
    }
    throw IllegalStateException("Unexpected credential type: ${credential.type}")
}

/**
 * Phase 11.7 — email + password sign-in (Owner direction: a dedicated
 * login/sign-up page for new users). Same FirebaseAuth, same uid → all
 * Firestore paths and rules stay untouched (roadmap §22.2).
 */
private suspend fun signInWithEmailPassword(email: String, password: String): FirebaseUser {
    val result = FirebaseAuth.getInstance()
        .signInWithEmailAndPassword(email, password).await()
    return result.user ?: throw IllegalStateException("Sign-in returned no user")
}

/** Create a new email/password account and stamp the chosen display name. */
private suspend fun signUpWithEmailPassword(name: String, email: String, password: String): FirebaseUser {
    val result = FirebaseAuth.getInstance()
        .createUserWithEmailAndPassword(email, password).await()
    val user = result.user ?: throw IllegalStateException("Sign-up returned no user")
    if (name.isNotBlank()) {
        val profile = UserProfileChangeRequest.Builder().setDisplayName(name).build()
        user.updateProfile(profile).await()
    }
    return user
}

/** Stable per-device id (hashed ANDROID_ID) used as the Firestore doc id. */
internal fun stableDeviceId(context: Context): String {
    val androidId = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ANDROID_ID
    ) ?: "unknown-device"
    val digest = MessageDigest.getInstance("SHA-256").digest(androidId.toByteArray())
    return digest.joinToString("") { "%02x".format(it) }.take(24)
}

/** Write/merge the user profile + this device's record in Firestore. */
private suspend fun registerDevice(context: Context, user: FirebaseUser): String {
    val db = Fb.db
    val profile = hashMapOf(
        "displayName" to (user.displayName ?: ""),
        "email" to (user.email ?: ""),
        "photoUrl" to (user.photoUrl?.toString() ?: ""),
        "updatedAt" to FieldValue.serverTimestamp()
    )
    db.collection("users").document(user.uid)
        .set(profile, SetOptions.merge()).awaitWithTimeout("saving your profile")

    val device = hashMapOf(
        "model" to Build.MODEL,
        "manufacturer" to Build.MANUFACTURER,
        "androidVersion" to Build.VERSION.RELEASE,
        "sdkInt" to Build.VERSION.SDK_INT,
        "appVersionName" to BuildConfig.VERSION_NAME,
        "appVersionCode" to BuildConfig.VERSION_CODE,
        "updatedAt" to FieldValue.serverTimestamp()
    )
    val deviceId = stableDeviceId(context)
    db.collection("users").document(user.uid)
        .collection("devices").document(deviceId)
        .set(device, SetOptions.merge()).awaitWithTimeout("saving this device")
    return deviceId
}

/** Read back the device list registered under this account. */
private suspend fun loadDevices(user: FirebaseUser): List<String> {
    val snapshot = Fb.db
        .collection("users").document(user.uid)
        .collection("devices")
        .get()
        .awaitWithTimeout("reading your devices")
    return snapshot.documents.map { doc ->
        val model = doc.getString("model") ?: "Unknown model"
        val version = doc.getString("appVersionName") ?: "?"
        "$model · v$version · id ${doc.id.take(8)}…"
    }
}

@Composable
fun HomeScreen() {
    val context = LocalContext.current
    val activity = context as? ComponentActivity
    val scope = rememberCoroutineScope()

    var signedInUser by remember { mutableStateOf(FirebaseAuth.getInstance().currentUser) }
    var busy by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    // Phase 11.7 — dedicated to the AuthGate screen: friendly error and
    // success text for the login/sign-up/reset attempts.
    var authMessage by remember { mutableStateOf<String?>(null) }
    var authNotice by remember { mutableStateOf<String?>(null) }
    var deviceStatus by remember { mutableStateOf("Not checked yet") }
    var deviceList by remember { mutableStateOf<List<String>>(emptyList()) }
    var registerRetryKey by remember { mutableStateOf(0) }
    var presenceStatus by remember { mutableStateOf("Not sent yet") }
    var pushStatus by remember { mutableStateOf("Not set up yet") }
    // Phase 11 — this device's id, for the top-bar presence chip.
    var ownDeviceId by remember { mutableStateOf("") }

    // Phase 21/M2 (v0.30.0, Decision C3 spike) — the manual `var tab` int
    // machine is GONE: the shell below runs on a NavHost now. Tab selection,
    // per-tab scroll and process-death restoration all moved to navigation
    // state (saveState/restoreState — NAVIGATION_MAP §3), and the bell opens
    // the new NOTIFICATIONS route instead of Settings (audit finding #4).

    // v0.30.0 (M2) — REAL registration outcomes (null = not attempted yet).
    // The notifications surface derives its "setup failed" row from these
    // flags — never from parsing localized display strings.
    var deviceOk by remember { mutableStateOf<Boolean?>(null) }
    var presenceOk by remember { mutableStateOf<Boolean?>(null) }
    var pushOk by remember { mutableStateOf<Boolean?>(null) }

    // v0.14.0 — the ফোন tab's FAB opens the pairing bottom sheet; the sheet
    // itself lives inside PairingSections (it needs the create/redeem state).
    var showPairSheet by remember { mutableStateOf(false) }

    // Phase 11 — the permission wizard is an app-level overlay now, so a tab
    // switch can never close it mid-step.
    var showWizard by remember { mutableStateOf(false) }
    var permSummary by remember { mutableStateOf(readPermChecklist(context)) }

    val webClientConfigured = remember { BuildConfig.GOOGLE_WEB_CLIENT_ID.isNotBlank() }
    // Phase 16 — the Firebase project/app-ids are NO LONGER read for the
    // About popup (Owner: showing Firebase details to every user is a
    // privacy smell). The About dialog now shows version + package + a
    // plain-language storage note + the privacy/rules documents.

    // Phase 5 — schedule the 15-minute background heartbeat (idempotent).
    // Phase 6 — session-request notifications while the app is backgrounded.
    LaunchedEffect(Unit) {
        schedulePeriodicHeartbeat(context)
        SessionNotifier.start(context)
    }

    // Phase 11 — one light session listener drives BOTH the bell badge and
    // the home tab's consent/hint cards. PairingCard keeps its own listener
    // for its interactive cards; both are rules-bound capped queries.
    // v0.21.0 (audit F1): a LOADED flag separates "not loaded yet" from "no
    // session" — the first snapshot arrives asynchronously, and the old
    // tab-scoped teardown effect could not tell an empty loading state from
    // a genuinely closed session, so it killed LIVE shares on tab switches,
    // rotations and language changes.
    // M3 (v0.31.0) — session watch now lives in an activity-scoped ViewModel
    // (ui/home/SessionWatchViewModel.kt, FRONTEND_ARCHITECTURE §4):
    // StateFlow<UiState> with the loaded flag and ROUTED errors (audit risk
    // #5 — the old onError = { } swallowed listener failures; they now
    // surface on Home as a real error panel while no snapshot has arrived).
    // The ViewModel is created HERE, above the NavHost, ON PURPOSE: this
    // composable is the always-composed shell, so the zombie sweep and the
    // "no open session → stop everything" effects below still run on every
    // tab exactly as v0.21.0 designed (a HOME-tab-scoped ViewModel would
    // have regressed that W1-gate behavior).
    val sessionsVm: SessionWatchViewModel = viewModel()
    var sessionRetryKey by remember { mutableStateOf(0) }
    DisposableEffect(signedInUser?.uid, sessionRetryKey) {
        sessionsVm.observe(signedInUser) { e -> describeFirestoreError(context, e) }
        onDispose { }
    }
    val homeSessionsState by sessionsVm.state.collectAsState()
    val homeSessions = homeSessionsState.sessions
    val homeSessionsLoaded = homeSessionsState.loaded
    val openSession = homeSessions.firstOrNull {
        it.status == "REQUESTED" || it.status == "ACTIVE"
    }

    // v0.21.0 (audit F1): the ONLY "no open session → stop everything"
    // effect, hoisted to the ROOT so it runs on every tab (the old one lived
    // inside the Phones tab section and only fired while that tab was
    // composed). Gated on the loaded flag: before the first snapshot the
    // empty list means "still loading", NOT "no session". Both calls are
    // no-ops when nothing is running.
    LaunchedEffect(homeSessionsLoaded, openSession == null, signedInUser?.uid) {
        if (homeSessionsLoaded && openSession == null) {
            ScreenShareEngine.stopHelper()
            ScreenShareService.stop(context)
        }
    }

    // v0.22.0 (Phase 18.1) — ROOT-level zombie-session sweep: a stale ask
    // (past its 2-min window) or an over-time session (past its 15-min life)
    // is cleaned the moment this app is alive, on ANY tab — the old cleanup
    // only ran while the Phones tab composed, so a phone rebooted mid-session
    // stayed blocked ("family আর কানেক্ট হয় না") until someone reopened it.
    // Same rules-checked transitions the request-side self-heal performs.
    LaunchedEffect(homeSessions, signedInUser?.uid) {
        val user = signedInUser ?: return@LaunchedEffect
        val now = System.currentTimeMillis()
        homeSessions.filter { it.isStaleOpen(now) }.forEach { s ->
            try {
                if (s.status == "REQUESTED") expireSession(s.id)
                else endSession(s.id, user.uid, byTimeout = true)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // best effort — PairingSections' per-second sweep and the
                // server sweep perform exactly these transitions too
            }
        }
    }

    // Phase 15 — fresh viewer state per session id (moved from the Phones
    // tab section to the ROOT in v0.21.0: the old placement only reset when
    // that tab was composed). A new session must never inherit a stale
    // fullscreen/stop dialog/touch-control state from the previous one.
    LaunchedEffect(openSession?.id) {
        SessionUiBus.fullscreen = false
        SessionUiBus.stopConfirm = false
        SessionUiBus.typeDialog = false
        SessionUiBus.touchOn = false // v0.21.0 (audit): touch is per-session
    }
    val pendingAsk = openSession != null &&
        openSession.status == "REQUESTED" &&
        openSession.recipientUid == signedInUser?.uid

    // v0.9.5 wizard auto-opens on first run; the checklist refreshes every
    // 2.5 s regardless of which tab is showing. Phase 11.7 — auto-open now
    // waits for sign-in: a new user sees the LOGIN page first, then the
    // wizard (never the wizard over the login screen).
    LaunchedEffect(signedInUser != null) {
        if (signedInUser != null && !isPermissionWizardDone(context)) showWizard = true
    }
    LaunchedEffect(signedInUser != null) {
        // v0.21.0 (audit): no binder reads on the login screen — the poll
        // only runs while a user is actually signed in.
        if (signedInUser == null) return@LaunchedEffect
        while (true) {
            permSummary = readPermChecklist(context)
            delay(2500)
        }
    }

    // After sign-in (or on app open while signed in): refresh device record + list.
    // registerRetryKey lets the user retry manually if the Firestore write failed
    // (e.g. rules not published yet or a flaky network).
    LaunchedEffect(signedInUser?.uid, registerRetryKey) {
        val user = signedInUser ?: return@LaunchedEffect
        deviceStatus = "Registering this device…"
        // v0.21.0 (audit): the FCM token registration used to sit INSIDE the
        // device-registration success path — one transient Firestore failure
        // skipped it entirely, the kill-push sweep then found "noTokens" and
        // killed-app notifications silently never fired (the Owner's
        // "notification asche na" root cause). Each step now runs
        // independently with its own error handling and retry.
        var registeredDeviceId: String? = null
        try {
            registeredDeviceId = registerDevice(context, user)
            deviceStatus = "Registered ✓ (id ${registeredDeviceId.take(8)}…)"
            deviceOk = true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
                deviceOk = false
                deviceStatus = context.getString(R.string.reg_failed, friendlyError(context, e))
        }
        // Phase 5 — presence heartbeat + push token registration. The token
        // registry key is the stable device id, which exists even when the
        // device-record write above failed (rules allow both self-writes).
        val deviceId = registeredDeviceId ?: stableDeviceId(context)
        ownDeviceId = deviceId
        try {
            sendHeartbeat(user.uid, deviceId, Build.MODEL)
            presenceStatus = "Online ✓ — heartbeat sent just now"
            presenceOk = true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
                presenceOk = false
                presenceStatus = context.getString(R.string.presence_failed, friendlyError(context, e))
        }
        try {
            registerFcmToken(user.uid, deviceId)
            pushStatus = "Push notifications ready ✓"
            pushOk = true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
                pushOk = false
                pushStatus = context.getString(R.string.push_failed, friendlyError(context, e))
        }
        try {
            deviceList = loadDevices(user)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // the device list is informational — never block the flow
        }
    }

    // Phase 11.7 — ONE shared sign-in action: the AuthGate's Google button
    // and the settings account card both run exactly this (same Firebase
    // Auth path, same friendly error mapping).
    val startSignIn: () -> Unit = {
        if (activity != null) {
            busy = true
            authMessage = null
            authNotice = null
            scope.launch {
                try {
                    signedInUser = signInWithGoogle(activity)
                } catch (e: GetCredentialException) {
                        authMessage = context.getString(R.string.auth_cancelled)
                } catch (e: Exception) {
                        authMessage = context.getString(R.string.auth_failed, friendlyError(context, e))
                } finally {
                    busy = false
                }
            }
        }
    }

    // Phase 11.7 — the app BEGINS at a real login/sign-up gate. A brand-new
    // user sees THIS screen first (Owner direction: আলাদা লগইন ও সাইন-আপ
    // পেজ); the 4-tab shell below only exists once signed in. Sign-out
    // returns here automatically because signedInUser flips back to null.
    if (signedInUser == null) {
        AuthGate(
            busy = busy,
            authMessage = authMessage,
            authNotice = authNotice,
            onClearMessages = {
                authMessage = null
                authNotice = null
            },
            onGoogleSignIn = startSignIn,
            onEmailSignIn = { email, password ->
                busy = true
                authMessage = null
                authNotice = null
                scope.launch {
                    try {
                        signedInUser = signInWithEmailPassword(email, password)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        authMessage = friendlyError(context, e)
                    } finally {
                        busy = false
                    }
                }
            },
            onEmailSignUp = { name, email, password ->
                busy = true
                authMessage = null
                authNotice = null
                scope.launch {
                    try {
                        signedInUser = signUpWithEmailPassword(name, email, password)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        authMessage = friendlyError(context, e)
                    } finally {
                        busy = false
                    }
                }
            },
            onPasswordReset = { email ->
                busy = true
                authMessage = null
                authNotice = null
                scope.launch {
                    try {
                        FirebaseAuth.getInstance().sendPasswordResetEmail(email).await()
                            authNotice = context.getString(R.string.auth_reset_sent, email)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        authMessage = friendlyError(context, e)
                    } finally {
                        busy = false
                    }
                }
            }
        )
        return
    }

    // Phase 15 (v0.19.0) — a full-size Box so the helper's session control
    // bar can be PINNED to the very bottom of the screen (above the bottom
    // nav) while a watch is on — controls stay under the thumb instead of
    // being buried mid-scroll (Owner report: the buttons were unreachable).
    Box(modifier = Modifier.fillMaxSize()) {
    // v0.21.0 (audit): measured height of the bottom NavigationBar — the
    // pinned SessionKeyBar pads itself up by this so it can never COVER the
    // tab bar (the old code only respected the system inset and drew the key
    // bar on top of the navigation items).
    var bottomNavHeightPx by remember { mutableStateOf(0) }
    // v0.30.0 (Phase 21/M2, Decision C3 spike) — the shell runs on
    // Navigation-Compose: the 4 tab destinations + the notifications route,
    // with saveState/restoreState tab switching (ui/nav/AppNav.goTab).
    // The overlays (permission wizard, pair sheet, consent modal, session
    // dialogs, pinned session bar) keep their EXACT composition sites —
    // app-level or inside tab content — so the W1 gate items behave
    // identically: cold start from a session notification (no intent logic
    // existed before or now — the Firestore listeners drive everything),
    // the wizard overlay, and the fullscreen session dialog (it lives
    // inside the session card, same unmount semantics as the old when(tab)).
    // Crossfade ≤150 ms between destinations per NAVIGATION_MAP §4.
    val navController = rememberNavController()
    // M4 (v0.32.0) — nested detail-route motion (NAVIGATION_MAP §4):
    // forward slide ≈200 ms (fade-tinted), pop slides back out. The duration
    // honors the system Animator duration scale — a user who disabled
    // animations gets instant transitions (tween(0)), the honest
    // reduced-motion behavior. The tab crossfade stays 120 ms as in M2.
    val nestedDur = remember {
        val scale = Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f
        )
        (200 * scale).toInt()
    }
    val nestedEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        slideInHorizontally(tween(nestedDur)) { it / 3 } + fadeIn(tween(nestedDur))
    }
    val nestedExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        fadeOut(tween(nestedDur / 2))
    }
    val nestedPopEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        fadeIn(tween(nestedDur / 2))
    }
    val nestedPopExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        slideOutHorizontally(tween(nestedDur)) { it / 3 } + fadeOut(tween(nestedDur))
    }
    Scaffold(
        topBar = {
            AppTopBar(
                pendingAsk = pendingAsk,
                // v0.30.0 — the bell opens the DEDICATED notifications
                // surface (real events only — see NotificationsScreen).
                onBellClick = {
                    navController.navigate(AppRoutes.NOTIFICATIONS) { launchSingleTop = true }
                },
                presence = {
                    if (signedInUser != null && ownDeviceId.isNotBlank()) {
                        PresenceChip(uid = signedInUser?.uid, deviceId = ownDeviceId)
                    }
                }
            )
        },
        bottomBar = {
            Box(modifier = Modifier.onSizeChanged { bottomNavHeightPx = it.height }) {
                val navEntry by navController.currentBackStackEntryAsState()
                AppBottomNav(
                    currentRoute = navEntry?.destination?.route,
                    onSelect = { route -> navController.goTab(route) }
                )
            }
        }
        // Phase 16 — the right-side "যুক্ত করুন" FAB is GONE (Owner: the
        // add-phone action showed up in TWO places — the permanent top card
        // on the phone tab already covers both directions, and the FAB even
        // covered the Full-screen button mid-scroll). One place, one truth.
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = AppRoutes.HOME,
            modifier = Modifier.padding(innerPadding),
            enterTransition = { fadeIn(tween(120)) },
            exitTransition = { fadeOut(tween(120)) },
            popEnterTransition = { fadeIn(tween(120)) },
            popExitTransition = { fadeOut(tween(120)) }
        ) {
            composable(AppRoutes.HOME) {
                ShellTabColumn {
                    HomeTab(
                        signedInUser = signedInUser,
                        deviceStatus = deviceStatus,
                        presenceStatus = presenceStatus,
                        pushStatus = pushStatus,
                        permSummary = permSummary,
                        openSession = openSession,
                        onOpenWizard = { showWizard = true },
                        onRetryRegistration = { registerRetryKey++ },
                        onGoPairing = { navController.goTab(AppRoutes.ADD) },
                        // M4 — nested-navigation entry points (NAVIGATION_MAP
                        // §2): Family Overview + Recent Activity live UNDER
                        // Home as detail routes, not extra tabs (C2).
                        onOpenFamily = {
                            navController.navigate(AppRoutes.HOME_FAMILY) { launchSingleTop = true }
                        },
                        onOpenActivity = {
                            navController.navigate(AppRoutes.HOME_SESSIONS) { launchSingleTop = true }
                        },
                        busy = busy,
                        onSignIn = startSignIn,
                        // M3 — real session-watch states (SessionWatchViewModel)
                        sessionsError = homeSessionsState.error?.takeIf { !homeSessionsLoaded },
                        sessionsLoading = !homeSessionsLoaded,
                        onRetrySessions = { sessionRetryKey++ }
                    )
                }
            }
            // ── M4 (v0.32.0) — NESTED HOME ROUTES (NAVIGATION_MAP §2) ──────
            // The Family Overview list, its Member Detail screen (the old
            // paired-phone action bottom-sheet, promoted), the Recent
            // Activity list and the Session Detail screen. System back pops
            // them (map §3); the Home tab stays selected (tabForRoute).
            composable(
                AppRoutes.HOME_FAMILY,
                enterTransition = nestedEnter,
                exitTransition = nestedExit,
                popEnterTransition = nestedPopEnter,
                popExitTransition = nestedPopExit
            ) {
                ShellTabColumn {
                    val user = signedInUser
                    if (user != null) {
                        FamilyOverviewScreen(
                            user = user,
                            onOpenMember = { relId ->
                                navController.navigate("home/family/$relId") { launchSingleTop = true }
                            },
                            onGoAddDevice = { navController.goTab(AppRoutes.ADD) }
                        )
                    }
                }
            }
            composable(
                AppRoutes.HOME_FAMILY_ARG,
                arguments = listOf(navArgument("relId") { type = NavType.StringType }),
                enterTransition = nestedEnter,
                exitTransition = nestedExit,
                popEnterTransition = nestedPopEnter,
                popExitTransition = nestedPopExit
            ) { entry ->
                ShellTabColumn {
                    val user = signedInUser
                    val relId = entry.arguments?.getString("relId").orEmpty()
                    if (user != null) {
                        MemberDetailScreen(
                            user = user,
                            relId = relId,
                            openSession = openSession,
                            onBack = { navController.popBackStack() }
                        )
                    }
                }
            }
            composable(
                AppRoutes.HOME_SESSIONS,
                enterTransition = nestedEnter,
                exitTransition = nestedExit,
                popEnterTransition = nestedPopEnter,
                popExitTransition = nestedPopExit
            ) {
                ShellTabColumn {
                    RecentActivityScreen(
                        user = signedInUser,
                        sessions = homeSessions,
                        sessionsLoaded = homeSessionsLoaded,
                        sessionsError = homeSessionsState.error?.takeIf { !homeSessionsLoaded },
                        onRetrySessions = { sessionRetryKey++ },
                        onOpenSession = { id ->
                            navController.navigate("home/sessions/$id") { launchSingleTop = true }
                        }
                    )
                }
            }
            composable(
                AppRoutes.HOME_SESSION_ARG,
                arguments = listOf(navArgument("id") { type = NavType.StringType }),
                enterTransition = nestedEnter,
                exitTransition = nestedExit,
                popEnterTransition = nestedPopEnter,
                popExitTransition = nestedPopExit
            ) { entry ->
                ShellTabColumn {
                    SessionDetailScreen(
                        sessionId = entry.arguments?.getString("id").orEmpty(),
                        liveSessions = homeSessions,
                        onBack = { navController.popBackStack() }
                    )
                }
            }
            composable(AppRoutes.ADD) {
                ShellTabColumn {
                    PairingCard(
                        signedInUser = signedInUser,
                        showPairSheet = showPairSheet,
                        onDismissPairSheet = { showPairSheet = false },
                        onOpenPairSheet = { showPairSheet = true }
                    )
                }
            }
            // Phase 16 — the old একটিভিটি tab became the LIVE LOCATION tab
            // (Owner's order then; an honest placeholder, nothing collected).
            // The Activity content stays merged INTO Settings as requested.
            // M5 (v0.33.0) — the placeholder is GONE: the real Tracking tab
            // (osmdroid map + authorized markers + own-sharing consent card,
            // docs/LIVE_LOCATION_ARCHITECTURE.md) at this same route.
            composable(AppRoutes.TRACKING) {
                ShellTabColumn {
                    TrackingTab(
                        user = signedInUser,
                        // NAVIGATION_MAP §2 tracking-subtree: the map's detail
                        // entry routes into tracking/{relId} (Location Details).
                        onOpenMember = { relId ->
                            navController.navigate("tracking/$relId") { launchSingleTop = true }
                        }
                    )
                }
            }
            // ── M5 (v0.33.0) — NESTED TRACKING ROUTE (NAVIGATION_MAP §2) ─────
            // Location Details: last update time, accuracy, staleness, the
            // counterpart's read-only sharing status + open-in-maps. System
            // back pops it (map §3); the Tracking tab stays selected
            // (tabForRoute) — same motion rules as the M4 home-subtree.
            composable(
                AppRoutes.TRACKING_ARG,
                arguments = listOf(navArgument("relId") { type = NavType.StringType }),
                enterTransition = nestedEnter,
                exitTransition = nestedExit,
                popEnterTransition = nestedPopEnter,
                popExitTransition = nestedPopExit
            ) { entry ->
                ShellTabColumn {
                    LocationDetailScreen(
                        user = signedInUser,
                        relId = entry.arguments?.getString("relId").orEmpty(),
                        onBack = { navController.popBackStack() }
                    )
                }
            }
            composable(AppRoutes.SETTINGS) {
                ShellTabColumn {
                    SettingsTab(
                    signedInUser = signedInUser,
                    busy = busy,
                    statusMessage = statusMessage,
                    deviceList = deviceList,
                    webClientConfigured = webClientConfigured,
                    permSummary = permSummary,
                    onSignIn = startSignIn,
                    onSignOut = {
                        busy = true
                        scope.launch {
                            try {
                                // Phase 13 — sign-out hygiene: while still
                                // authenticated, delete this device's presence
                                // heartbeat + push token (rules-allowed
                                // self-deletes), so paired phones immediately
                                // stop seeing "Online" and a signed-out phone
                                // stops receiving session-request push. The
                                // periodic WorkManager heartbeat is left
                                // scheduled on purpose — HeartbeatWorker
                                // no-ops without a signed-in user, and the
                                // KEEP policy means re-sign-in needs no
                                // re-scheduling. Best-effort: sign-out always
                                // proceeds; any failure becomes an honest
                                // notice on the login screen.
                                // v0.21.0 (audit): signing out must also
                                // tear down any live share/viewer — the old
                                // code left the MediaProjection foreground
                                // service CAPTURING under a signed-out account
                                // (privacy) and the helper's viewer listeners
                                // alive against now-unauthorized rules. Both
                                // calls are no-ops when nothing is running.
                                ScreenShareEngine.stopHelper()
                                ScreenShareService.stop(context)
                                SessionUiBus.fullscreen = false
                                SessionUiBus.stopConfirm = false
                                SessionUiBus.typeDialog = false
                                SessionUiBus.touchOn = false
                                // M5 (v0.33.0) — sign-out also stops live-
                                // location sharing: the FGS stops AND the
                                // sharing pref flips off (same hygiene as the
                                // share/viewer teardown above; the doc delete
                                // below runs while still signed in).
                                stopLocationSharing(context)
                                var cleanupNote: String? = null
                                val currentUser = FirebaseAuth.getInstance().currentUser
                                if (currentUser != null) {
                                    // M5: remove the liveLocations doc while
                                    // still authenticated (rules-allowed
                                    // self-delete; best-effort — a failure never
                                    // traps the user signed in).
                                    try {
                                        deleteOwnLocation(
                                            currentUser.uid,
                                            stableDeviceId(context)
                                        )
                                    } catch (e: CancellationException) {
                                        throw e
                                    } catch (_: Exception) {
                                        // best-effort; the presence/token
                                        // cleanup below reports honestly
                                    }
                                    val clean = try {
                                        deleteOwnPresenceAndToken(
                                            currentUser.uid,
                                            stableDeviceId(context)
                                        )
                                    } catch (e: CancellationException) {
                                        throw e
                                    } catch (_: Exception) {
                                        false
                                    }
                                    if (!clean) {
                                            cleanupNote = context.getString(R.string.so_cleanup_failed)
                                    }
                                }
                                FirebaseAuth.getInstance().signOut()
                                try {
                                    CredentialManager.create(context)
                                        .clearCredentialState(ClearCredentialStateRequest())
                                } catch (_: Exception) {
                                    // clearing credential state is best-effort
                                }
                                signedInUser = null
                                deviceStatus = "Not checked yet"
                                deviceList = emptyList()
                                presenceStatus = "Signed out"
                                pushStatus = "Signed out"
                                deviceOk = null
                                presenceOk = null
                                pushOk = null
                                statusMessage = null
                                authMessage = null
                                authNotice = cleanupNote
                                        ?: context.getString(R.string.so_cleanup_ok)
                            } catch (e: Exception) {
                                statusMessage = "Sign-out failed: ${e.message}"
                            } finally {
                                busy = false
                            }
                        }
                    },
                    onOpenWizard = { showWizard = true },
                    // M4 — the Settings Device Management / Guest Access
                    // partitions point into the Home family subtree
                    // (NAVIGATION_MAP §2: settings/devices reuses home/family).
                    onOpenFamily = {
                        navController.navigate(AppRoutes.HOME_FAMILY) { launchSingleTop = true }
                    }
                    )
                }
            }
            composable(AppRoutes.NOTIFICATIONS) {
                ShellTabColumn {
                    NotificationsScreen(
                        liveSessions = homeSessions,
                        meUid = signedInUser?.uid,
                        hasPendingAsk = pendingAsk,
                        // 4 = PermChecklist.allDone's denominator (notifications,
                        // camera, assist, battery) — a real on-device checklist.
                        permissionDoneCount = permSummary.doneCount,
                        permissionTotal = 4,
                        setupProblemCount = listOf(deviceOk, presenceOk, pushOk)
                            .count { it == false },
                        onOpenHome = { navController.goTab(AppRoutes.HOME) },
                        onOpenWizard = { showWizard = true }
                    )
                }
            }
        }
    }

        // Phase 15 — the pinned bar itself. Visible ONLY for the helper while
        // the viewer is running (and hidden while the full-screen viewer is
        // open — that view carries its own overlay). The three system keys
        // are honest controls: sendAssistNav rides the same epoch-gated
        // command channel as taps and the recipient's assist service
        // performs GLOBAL_ACTION_BACK/HOME/RECENTS, with the same
        // per-command delivery feedback as every other assist command.
        val barShareState by ScreenShareEngine.state.collectAsState()
        val barSession = openSession
        val barMe = signedInUser
        val sessionBarVisible = barMe != null && barSession != null &&
            barSession.status == "ACTIVE" &&
            barSession.helperUid == barMe.uid &&
            barShareState != ShareState.IDLE &&
            !SessionUiBus.fullscreen
        if (sessionBarVisible) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    // v0.21.0 (audit): pad up by the measured bottom-bar height
                    // (NavigationBar already includes its own navigationBars
                    // inset in that height) so the pinned keys sit ABOVE the
                    // tab bar instead of covering it.
                    .padding(bottom = with(LocalDensity.current) {
                        bottomNavHeightPx.toDp()
                    } + 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                SessionKeyBar(touchOn = SessionUiBus.touchOn, inFullscreen = false)
                if (!SessionUiBus.touchOn) {
                    Text(
                            text = stringResource(R.string.assist_touch_hint),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .background(
                                MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.95f),
                                RoundedCornerShape(999.dp)
                            )
                            .padding(horizontal = 10.dp, vertical = 3.dp)
                    )
                }
            }
        }
        if (SessionUiBus.typeDialog && !SessionUiBus.fullscreen) {
            AssistTypeDialog(onDismiss = { SessionUiBus.typeDialog = false })
        }
        if (SessionUiBus.stopConfirm && !SessionUiBus.fullscreen) {
            StopWatchConfirmDialog(
                onDismiss = { SessionUiBus.stopConfirm = false },
                onConfirm = {
                    SessionUiBus.stopConfirm = false
                    ScreenShareEngine.stopHelper()
                }
            )
        }
        // Phase 15 — the approval ask as a CENTERED modal, app-wide (any
        // tab, in front of the eyes). Same Firestore grant/deny as the home
        // card; the security rules re-verify everything either way.
        val askMe = openSession
        val askUser = signedInUser
        if (askUser != null && askMe != null &&
            askMe.status == "REQUESTED" &&
            askMe.recipientUid == askUser.uid
        ) {
            HomeConsentDialog(session = askMe, user = askUser)
        }
    }

    PermissionWizard(
        visible = showWizard,
        onDismiss = {
            showWizard = false
            markPermissionWizardDone(context)
        }
    )
}

/**
 * v0.30.0 (Phase 21/M2) — the shared per-destination scroll column. The old
 * shell wrapped ALL tabs in one Column with `tabScrollStates[tab]`; under
 * the NavHost every destination owns its scroll state instead
 * (rememberScrollState is saveable — NavHost saveState/restoreState brings
 * it back on tab return, same user-visible behavior as Phase 11.6).
 */
@Composable
private fun ShellTabColumn(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        content = content
    )
}

/**
 * Phase 11.6 — friendly one-liner for families (docs §10-L): the common
 * failures (network, permission) get human language; anything else falls
 * through to the full technical describeFirestoreError.
 */
// v0.28.0 (Phase 19.2): context-based localized errors (was friendlyErrorBn).
private fun friendlyError(context: Context, e: Exception): String = when (e) {
    // Phase 11.7 — auth-specific failures in family language first.
    is FirebaseAuthInvalidUserException ->
        context.getString(R.string.fe_no_account)
    is FirebaseAuthInvalidCredentialsException ->
        context.getString(R.string.fe_wrong_cred)
    is FirebaseAuthUserCollisionException ->
        context.getString(R.string.fe_in_use)
    is FirebaseAuthWeakPasswordException ->
        context.getString(R.string.fe_weak_pw)
    is FirebaseTooManyRequestsException ->
        context.getString(R.string.fe_too_many)
    is FirebaseNetworkException ->
        context.getString(R.string.fe_no_internet)
    is FirebaseFirestoreException -> when (e.code) {
        FirebaseFirestoreException.Code.UNAVAILABLE,
        FirebaseFirestoreException.Code.DEADLINE_EXCEEDED ->
            context.getString(R.string.fe_no_internet)
        FirebaseFirestoreException.Code.PERMISSION_DENIED ->
            context.getString(R.string.fe_not_allowed)
        else -> describeFirestoreError(context, e)
    }
    else -> describeFirestoreError(context, e)
}


/** "M:SS" countdown text used by the session cards.
 *  M3: internal — shared by the extracted home/pairing/settings screens. */
internal fun mmss(seconds: Long): String = "%d:%02d".format(seconds / 60, seconds % 60)

/**
 * Phase 7 — screen share controls inside the ACTIVE session card.
 *
 * Recipient side: "Share screen" runs the system MediaProjection consent
 * dialog; on approval the foreground service starts and the engine captures.
 * "Stop sharing" tears it down (a "bye" signal stops the helper's viewer).
 * v0.9.0 (Phase 8): the card also shows the Remote-assist state and offers
 * the one-time enablement (system accessibility settings) that lets the
 * helper tap on this screen.
 *
 * Helper side: "Watch screen" asks the recipient's engine for an offer and
 * renders the incoming track on a SurfaceViewRenderer. v0.9.0 (Phase 8): a
 * "Touch control" switch turns the picture into a remote touch surface
 * (taps are sent as epoch-gated "cmd" signals and performed by the
 * recipient's assist service), with Back/Home/Recents shortcut buttons and
 * per-command delivery feedback.
 */
// ═════════════════════════════════════════════════════════════════
// Phase 15 (v0.19.0) — helper session CONTROL BAR.
//
// Owner report: the Back/Home/Recents keys lived mid-scroll in the phone
// tab (unreachable, half-hidden behind the bottom nav) and the full-screen
// viewer had NO controls at all. Fix: ONE compact bar in TWO surfaces —
//   • inline: pinned to the very bottom of the SCREEN (root Box), above
//     the bottom navigation, while the helper watches;
//   • full-screen: the same bar as a small overlay at the bottom of the
//     viewer (with an exit-to-small key).
// The three system keys are REAL controls — sendAssistNav("back"/"home"/
// "recents") rides the same epoch-gated command channel as taps, and the
// recipient's assist service performs GLOBAL_ACTION_BACK/HOME/RECENTS.
// SessionUiBus is the single source of truth for both surfaces.
// ═════════════════════════════════════════════════════════════════

/** Shared UI state for the helper's session surfaces (panel + pinned bar).
 *  M3: internal so the extracted ui/home/HomeConsentDialog can share the
 *  one-per-ask dedup bus — still the single source of truth. */
internal object SessionUiBus {
    var touchOn by mutableStateOf(false)      // mirrors the touch-control switch
    var fullscreen by mutableStateOf(false)   // full-screen viewer open
    var typeDialog by mutableStateOf(false)   // type-text dialog open
    var stopConfirm by mutableStateOf(false)  // stop-watching confirm open

    /**
     * v0.21.0 (audit) — session asks the CENTERED consent modal already
     * showed (dismissed or answered). Keeping this OUT of composition memory
     * makes the "one prompt per session id" promise durable across tab
     * switches, rotations, language changes and listener error-reconnects,
     * where a positional remember() used to re-prompt the SAME ask.
     */
    val shownAsks = mutableSetOf<String>()
}


/** One compact key: ≥44 dp target, one glyph, real contentDescription. */
@Composable
private fun SessionKey(glyph: String, label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(
                if (enabled) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    Color.Transparent
                }
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = glyph,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            color = if (enabled) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            }
        )
    }
}

/** [◀][⌂][▦] | [⌨][⛶/⤡][✕] — small, bottom-pinned, always reachable. */
@Composable
private fun SessionKeyBar(touchOn: Boolean, inFullscreen: Boolean) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.97f),
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
                SessionKey("◀", stringResource(R.string.sk_back), touchOn) {
                ScreenShareEngine.sendAssistNav("back")
            }
                SessionKey("⌂", stringResource(R.string.sk_home), touchOn) {
                ScreenShareEngine.sendAssistNav("home")
            }
                SessionKey("▦", stringResource(R.string.sk_recents), touchOn) {
                ScreenShareEngine.sendAssistNav("recents")
            }
                SessionKey("⌨", stringResource(R.string.sk_type), touchOn) {
                SessionUiBus.typeDialog = true
            }
            SessionKey(
                if (inFullscreen) "⤡" else "⛶",
                    if (inFullscreen) stringResource(R.string.sk_small) else stringResource(R.string.sk_full),
                true
            ) { SessionUiBus.fullscreen = !inFullscreen }
                SessionKey("✕", stringResource(R.string.sk_stop), true) {
                SessionUiBus.stopConfirm = true
            }
        }
    }
}

/** v0.23.0 (Phase 18.2) — the fullscreen key overlay folds after this idle time. */
private const val FULLSCREEN_BAR_AUTOHIDE_MILLIS = 4_000L

/**
 * v0.23.0 (Phase 18.2 — Owner bug: "ফুলস্ক্রিনে নিচের অপশনগুলো ফিক্স হয়ে
 * যায়… কিবোর্ডের স্পেস প্রেস করতে পারি না") — the fullscreen key bar used
 * to sit FIXED at the bottom centre: exactly where a remote keyboard's
 * space bar lives on the shared screen, so with touch on, taps meant for
 * that space key hit this local bar instead. The overlay now:
 *   1. AUTO-FOLDS to a small ⌃ pill 4 s after the last touch on it — the
 *      pill docks bottom-END, clear of the space-bar zone;
 *   2. UNFOLDS on a pill tap (the idle timer re-arms);
 *   3. is DRAGGABLE in both states, so the bar/pill can be parked anywhere
 *      on the video (clamped to stay fully on screen);
 *   4. has a ⌄ handle above the bar to fold immediately.
 * Every pointer down on the overlay (keys included) re-arms the idle timer;
 * while the type or stop dialog is open the bar never folds mid-task. State
 * is remembered per fullscreen-open: reopening starts visible, bottom-centre.
 */
@Composable
private fun FullscreenKeyOverlay(touchOn: Boolean) {
    var collapsed by remember { mutableStateOf(false) }
    var lastInteract by remember { mutableStateOf(System.currentTimeMillis()) }
    var barDrag by remember { mutableStateOf(IntOffset.Zero) }
    var pillDrag by remember { mutableStateOf(IntOffset.Zero) }
    var containerPx by remember { mutableStateOf(IntSize.Zero) }
    var barPx by remember { mutableStateOf(IntSize.Zero) }
    var pillPx by remember { mutableStateOf(IntSize.Zero) }

    fun bump() {
        lastInteract = System.currentTimeMillis()
    }

    // Bottom-anchored clamps: the bar (centre anchor) may move anywhere on
    // screen; the pill (end anchor) any distance left/up. Sizes are unknown
    // until first layout — the clamp then returns zero and simply no-ops.
    fun clampBar(d: IntOffset): IntOffset {
        if (containerPx.width <= 0 || containerPx.height <= 0) return IntOffset.Zero
        val maxX = ((containerPx.width - barPx.width) / 2).coerceAtLeast(0)
        val maxY = (containerPx.height - barPx.height).coerceAtLeast(0)
        return IntOffset(
            d.x.coerceIn(-maxX, maxX),
            d.y.coerceIn(-maxY, 0)
        )
    }

    fun clampPill(d: IntOffset): IntOffset {
        if (containerPx.width <= 0 || containerPx.height <= 0) return IntOffset.Zero
        val maxX = (containerPx.width - pillPx.width).coerceAtLeast(0)
        val maxY = (containerPx.height - pillPx.height).coerceAtLeast(0)
        return IntOffset(
            d.x.coerceIn(-maxX, 0),
            d.y.coerceIn(-maxY, 0)
        )
    }

    // 1) auto-fold: 4 s without a touch on the overlay (never mid-dialog).
    LaunchedEffect(Unit) {
        while (true) {
            delay(500)
            if (!collapsed &&
                !SessionUiBus.typeDialog &&
                !SessionUiBus.stopConfirm &&
                System.currentTimeMillis() - lastInteract > FULLSCREEN_BAR_AUTOHIDE_MILLIS
            ) {
                collapsed = true
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { containerPx = it }
    ) {
        if (collapsed) {
            // ── folded: a small ⌃ pill docked bottom-END — out of the
            // space-bar zone. Tap brings the keys back; drag parks it.
            val cdShowKeys = stringResource(R.string.cd_show_keys)
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.94f),
                shadowElevation = 6.dp,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .offset { clampPill(pillDrag) }
                    .onSizeChanged { pillPx = it }
                        .semantics { contentDescription = cdShowKeys }
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val ev = awaitPointerEvent()
                                if (ev.changes.any { it.pressed }) bump()
                            }
                        }
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = {
                            bump()
                            collapsed = false
                        })
                    }
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { bump() },
                            onDrag = { change, amount ->
                                bump()
                                pillDrag = clampPill(pillDrag + amount.round())
                                change.consume()
                            }
                        )
                    }
            ) {
                Text(
                    text = "⌃",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        } else {
            // ── unfolded: the full bar — draggable, with a ⌄ fold handle.
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .offset { clampBar(barDrag) }
                    .onSizeChanged { barPx = it }
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val ev = awaitPointerEvent()
                                if (ev.changes.any { it.pressed }) bump()
                            }
                        }
                    }
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { bump() },
                            onDrag = { change, amount ->
                                bump()
                                barDrag = clampBar(barDrag + amount.round())
                                change.consume()
                            }
                        )
                    }
                    .padding(bottom = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // ⌄ fold handle — a bottom-sheet style chip above the keys;
                // folding clears the whole video (and any remote keyboard).
                val cdHideKeys = stringResource(R.string.cd_hide_keys)
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.94f),
                    shadowElevation = 4.dp,
                    modifier = Modifier
                        .padding(bottom = 6.dp)
                        .clickable {
                            bump()
                            collapsed = true
                        }
                            .semantics { contentDescription = cdHideKeys }
                ) {
                    Text(
                        text = "⌄",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                    )
                }
                SessionKeyBar(touchOn = touchOn, inFullscreen = true)
                if (!touchOn) {
                    Text(
                            text = stringResource(R.string.sk_touch_hint),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .background(
                                Color.Black.copy(alpha = 0.55f),
                                RoundedCornerShape(999.dp)
                            )
                            .padding(horizontal = 10.dp, vertical = 3.dp)
                    )
                }
            }
        }
    }
}

/** Centered confirm before the helper stops watching (never a surprise). */
@Composable
private fun StopWatchConfirmDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.sw_title), fontWeight = FontWeight.Bold) },
        text = {
            Text(
                    stringResource(R.string.sw_body),
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                    Text(stringResource(R.string.btn_yes_stop), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.btn_no_continue)) }
        }
    )
}

@Composable
internal fun ScreenSharePanel(
    isHelper: Boolean,
    sessionId: String,
    myUid: String,
    otherUid: String
) {
    val state by ScreenShareEngine.state.collectAsState()
    val info by ScreenShareEngine.info.collectAsState()
    val net by ScreenShareEngine.netInfo.collectAsState()
    val context = LocalContext.current
    val active = state != ShareState.IDLE

    if (isHelper) {
        if (!active) {
            WideOutlinedButton(
                onClick = {
                    ScreenShareEngine.startHelper(context, sessionId, myUid, otherUid)
                }
            ) {
                    Text(stringResource(R.string.btn_watch))
            }
        } else {
            // v0.8.3 — the picture is now sized to the REAL screen aspect
            // ratio (the engine publishes the decoded frame size), capped so
            // it always fits, and tapping it opens a full-screen viewer. The
            // old fixed 220 dp box is what made the shared screen look tiny.
            val egl = ScreenShareEngine.eglBaseContext
            val frameSize by ScreenShareEngine.frameSize.collectAsState()
            val assistStatus by ScreenShareEngine.assistStatus.collectAsState()
            // Phase 8 — when on, taps on the picture are delivered to the
            // recipient's phone instead of opening the local full-screen view.
            // Phase 15 — fullscreen + touch flags live on SessionUiBus (shared
            // with the root-pinned bar and the full-screen overlay).
            val assistMode = SessionUiBus.touchOn
            var boxPx by remember { mutableStateOf(IntSize.Zero) }
            // Phase 9 (T3) — one place decides "remote touch is actually
            // live"; it drives the rim, the badge and the tap routing
            // together, so the helper can never confuse view-only with
            // touch-active.
            val assistLive = assistMode && state == ShareState.LIVE
            // Keep the helper's own screen on while a live stream is shown.
            if (state == ShareState.LIVE) {
                DisposableEffect(Unit) {
                    val activity = context as? Activity
                    activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    onDispose {
                        activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    }
                }
            }
            // Maps a point inside the letterboxed video box to normalized
            // (0..1) coordinates of the REAL remote frame. Shared by tap,
            // long-press and swipe alike. Used inline and in full-screen.
            fun normalized(off: Offset): Pair<Double, Double>? {
                val fs = frameSize ?: return null
                val (fw, fh) = fs
                if (fw <= 0 || fh <= 0 || boxPx.width <= 0 || boxPx.height <= 0) {
                    return null
                }
                val scale = minOf(
                    boxPx.width.toFloat() / fw.toFloat(),
                    boxPx.height.toFloat() / fh.toFloat()
                )
                val cw = fw * scale
                val ch = fh * scale
                val ox = (boxPx.width - cw) / 2f
                val oy = (boxPx.height - ch) / 2f
                val nx = ((off.x - ox) / cw).coerceIn(0f, 1f).toDouble()
                val ny = ((off.y - oy) / ch).coerceIn(0f, 1f).toDouble()
                return nx to ny
            }
            // Phase 8 (v0.9.0) — TAP. Phase 8.3 (v0.9.9) — LONG-PRESS + SWIPE.
            // The three detectors coexist by Compose's gesture rules: a finger
            // held still past the long-press timeout fires onLongPress; a
            // finger that passes touch-slop is claimed by detectDragGestures
            // (which cancels the tap); a quick unmoved release stays a tap.
            fun assistGestureModifier(): Modifier = Modifier
                .onSizeChanged { boxPx = it }
                .pointerInput(frameSize) {
                    detectTapGestures(
                        onTap = { off ->
                            val n = normalized(off) ?: return@detectTapGestures
                            ScreenShareEngine.sendAssistTap(n.first, n.second)
                        },
                        onLongPress = { off ->
                            val n = normalized(off) ?: return@detectTapGestures
                            ScreenShareEngine.sendAssistLongPress(n.first, n.second)
                        }
                    )
                }
                .pointerInput(frameSize) {
                    var start: Offset? = null
                    var cur: Offset? = null
                    var t0 = 0L
                    detectDragGestures(
                        onDragStart = { off ->
                            start = off
                            cur = off
                            t0 = System.currentTimeMillis()
                        },
                        onDrag = { change, _ ->
                            cur = change.position
                        },
                        onDragEnd = {
                            val s = start
                            val c = cur
                            start = null
                            cur = null
                            if (s != null && c != null) {
                                val a = normalized(s)
                                val b = normalized(c)
                                if (a != null && b != null &&
                                    hypot(b.first - a.first, b.second - a.second) > 0.02
                                ) {
                                    // The helper's REAL drag time travels with the
                                    // stroke (clamped): slow finger = slow scroll.
                                    val dur = (System.currentTimeMillis() - t0)
                                        .coerceIn(160L, 1_500L)
                                    ScreenShareEngine.sendAssistSwipe(
                                        a.first, a.second, b.first, b.second, dur
                                    )
                                }
                            }
                        },
                        onDragCancel = {
                            start = null
                            cur = null
                        }
                    )
                }
            if (egl != null) {
                if (SessionUiBus.fullscreen) {
                    Dialog(
                        onDismissRequest = { SessionUiBus.fullscreen = false },
                        properties = DialogProperties(usePlatformDefaultWidth = false)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black)
                        ) {
                            VideoRendererBox(
                                egl,
                                Modifier
                                    .fillMaxSize()
                                    .then(
                                        if (assistLive) assistGestureModifier()
                                        else Modifier
                                    )
                            )
                            if (assistLive) {
                                // Phase 9 (T3) — an unmistakable badge so the
                                // helper never forgets taps are going remote.
                                Text(
                                        text = stringResource(R.string.ss_touch_on),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    style = MaterialTheme.typography.labelLarge,
                                    modifier = Modifier
                                        .align(Alignment.TopStart)
                                        .padding(12.dp)
                                        .background(
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.92f),
                                            RoundedCornerShape(8.dp)
                                        )
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                            // Phase 15 — compact overlay bar at the BOTTOM of
                            // the full-screen viewer: the three system keys,
                            // typing, exit-to-small and stop — everything the
                            // inline bar has, so a full-screen session is
                            // never a dead end (Owner report). The keys are
                            // REAL: sendAssistNav → the recipient's assist
                            // service performs GLOBAL_ACTION_BACK/HOME/RECENTS.
                            // Phase 18.2 (v0.23.0) — no longer FIXED at the
                            // bottom centre (that spot is exactly where a
                            // remote keyboard's space bar lives): the overlay
                            // auto-folds after 4 s, folds/unfolds via ⌄/⌃ and
                            // is draggable anywhere (Owner bug fixed).
                            FullscreenKeyOverlay(touchOn = assistMode)
                            if (SessionUiBus.typeDialog) {
                                AssistTypeDialog(onDismiss = { SessionUiBus.typeDialog = false })
                            }
                            if (SessionUiBus.stopConfirm) {
                                StopWatchConfirmDialog(
                                    onDismiss = { SessionUiBus.stopConfirm = false },
                                    onConfirm = {
                                        SessionUiBus.stopConfirm = false
                                        ScreenShareEngine.stopHelper()
                                        SessionUiBus.fullscreen = false
                                    }
                                )
                            }
                        }
                    }
                } else {
                    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                        val targetHeight = frameSize?.let { (w, h) ->
                            if (h > 0) maxWidth * (h.toFloat() / w.toFloat()) else 380.dp
                        } ?: 380.dp
                        Box {
                            VideoRendererBox(
                                egl,
                                Modifier
                                    .fillMaxWidth()
                                    .height(targetHeight.coerceAtMost(400.dp))
                                    .then(
                                        if (assistLive) {
                                            // Phase 9 (T3) — a warm rim marks
                                            // "taps go to THEIR phone".
                                            Modifier.border(
                                                3.dp,
                                                MaterialTheme.colorScheme.primary,
                                                RoundedCornerShape(10.dp)
                                            )
                                        } else {
                                            Modifier
                                        }
                                    )
                                    .then(
                                        if (assistLive) assistGestureModifier()
                                        else Modifier.clickable { SessionUiBus.fullscreen = true }
                                    )
                            )
                            if (assistLive) {
                                Text(
                                        text = stringResource(R.string.ss_touch_on),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    style = MaterialTheme.typography.labelLarge,
                                    modifier = Modifier
                                        .align(Alignment.TopStart)
                                        .padding(8.dp)
                                        .background(
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.92f),
                                            RoundedCornerShape(8.dp)
                                        )
                                        .padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }
                    Text(
                        text = if (assistLive)
                                stringResource(R.string.ss_touch_hint)
                        else
                                stringResource(R.string.ss_tap_fullscreen),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            // v0.12.0 (Phase 8.3 completion) — DATA SAVER. The helper caps
            // the stream's bitrate mid-session (600 kbps) or restores the
            // normal budget, without restarting anything. It rides the same
            // epoch-gated command channel as the touch commands and gets the
            // same truthful ack, so the switch can never silently lie.
            // v0.21.0 (audit): reset with every session id — the old plain
            // remember() could show OFF while the engine still capped the
            // bitrate (or lie the other way after a recreate).
            var dataSaver by remember(sessionId) { mutableStateOf(false) }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                        text = stringResource(R.string.ss_data_saver),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = dataSaver,
                    onCheckedChange = {
                        dataSaver = it
                        ScreenShareEngine.sendAssistDataSaver(it)
                    }
                )
            }
            Text(
                text = if (dataSaver)
                        stringResource(R.string.ss_ds_on)
                else
                        stringResource(R.string.ss_ds_off),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            // Phase 8 — the touch-control switch, global navigation buttons
            // and per-command delivery feedback. Phase 9 (T3): a clear section
            // header, equal-width ≥48dp nav buttons, bilingual state copy.
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                        text = stringResource(R.string.ss_touch_label),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = SessionUiBus.touchOn,
                    onCheckedChange = { SessionUiBus.touchOn = it }
                )
            }
            Text(
                text = if (assistMode)
                        stringResource(R.string.ss_touch_on_desc)
                else
                        stringResource(R.string.ss_touch_off_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            // Phase 15 — the old mid-scroll Back/Home/Recents row + type +
            // full-screen buttons moved into the pinned bottom bar (root Box)
            // and the full-screen overlay; nothing was removed, only placed
            // where the thumb can always reach it (Owner report).
            assistStatus?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium
                )
            }
            // "দেখা বন্ধ · Stop watching" moved into the pinned bottom bar
            // (✕ key, behind a centered confirm dialog) — Phase 15.
        }
    } else {
        if (!active) {
            val projectionManager = remember {
                context.getSystemService(Context.MEDIA_PROJECTION_SERVICE)
                    as MediaProjectionManager
            }
            val consentLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.StartActivityForResult()
            ) { result ->
                val data = result.data
                if (result.resultCode == android.app.Activity.RESULT_OK && data != null) {
                    ScreenShareService.start(
                        context, sessionId, myUid, otherUid, result.resultCode, data
                    )
                }
            }
            WideOutlinedButton(
                onClick = { consentLauncher.launch(projectionManager.createScreenCaptureIntent()) }
            ) {
                    Text(stringResource(R.string.btn_share_screen))
            }
            Text(
                    text = stringResource(R.string.ss_share_warn),
                style = MaterialTheme.typography.bodySmall
            )
        } else {
            // v0.8.1 — a small self-preview so the recipient can SEE that the
            // capture actually produces frames (it mirrors whatever the phone
            // is showing right now — usually this very app, nested). It is the
            // fastest way to tell "my capture works" from "the connection is
            // broken" when the helper reports a black picture.
            //
            // v0.9.2 — the view is created ONCE and only RELEASED when this
            // composable leaves composition for good; the engine's local
            // proxy sink forwards frames to whatever surface is current. The
            // old DisposableEffect(viewKey) released the live surface during
            // its own key-change dispose (a guaranteed black-preview recipe).
            val previewRef = remember { AtomicReference<SurfaceViewRenderer?>(null) }
            val previewEgl = ScreenShareEngine.eglBaseContext
            if (previewEgl != null) {
                Text(
                        text = stringResource(R.string.ss_preview_label),
                    style = MaterialTheme.typography.bodySmall
                )
                AndroidView(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    factory = { ctx ->
                        SurfaceViewRenderer(ctx).apply {
                            init(previewEgl, null)
                            setMirror(false)
                            setEnableHardwareScaler(true)
                            previewRef.set(this)
                            ScreenShareEngine.attachLocalPreview(this)
                        }
                    },
                    update = { view -> ScreenShareEngine.attachLocalPreview(view) }
                )
                DisposableEffect(Unit) {
                    onDispose {
                        ScreenShareEngine.detachLocalPreview()
                        previewRef.getAndSet(null)?.release()
                    }
                }
            }
            WideOutlinedButton(onClick = { ScreenShareService.stop(context) }) {
                    Text(stringResource(R.string.btn_stop_sharing))
            }
            // Phase 8 (v0.9.0) — Remote assist: state + one-time enablement.
            // Without the service the helper stays view-only forever; with it
            // they may tap (nothing more). The poll keeps the card honest if
            // the user flips the service in system settings mid-session.
            // v0.9.5 — the enablement is now GUIDED: the button opens the
            // Bengali step-by-step dialog (it names the exact service line
            // and warns against the "Accessibility menu shortcut" trap the
            // 28 Oct session fell into), then Settings opens with our row
            // highlighted (EXTRA_FRAGMENT_ARG_KEY). isAssistReady also reads
            // the settings list, so the ✓ appears the moment the switch
            // flips — before Android rebinds the service. droppedTaps turns
            // "the helper is tapping RIGHT NOW and it is not landing" into a
            // loud error banner mid-session.
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            var assistOn by remember { mutableStateOf(AssistSetup.isAssistReady(context)) }
            var showAssistGuide by remember { mutableStateOf(false) }
            val droppedTaps by ScreenShareEngine.assistDropped.collectAsState()
            LaunchedEffect(Unit) {
                while (true) {
                    assistOn = AssistSetup.isAssistReady(context)
                    delay(1500)
                }
            }
            if (assistOn) {
                Text(
                        text = stringResource(R.string.ss_assist_on),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            } else {
                Text(
                        text = stringResource(R.string.ss_assist_off),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                if (droppedTaps > 0) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Text(
                                text = stringResource(R.string.ss_tap_warning),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
                WideOutlinedButton(onClick = { showAssistGuide = true }) {
                        Text(stringResource(R.string.btn_turn_on_assist))
                }
                Text(
                        text = stringResource(R.string.ss_setup_once),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (showAssistGuide) {
                AssistSetupGuide(onDismiss = { showAssistGuide = false })
            }
        }
    }

    info?.let {
        Text(
            text = it,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium
        )
    }
    // v0.8.3 — candidate-type census: "direct N · via STUN M · via relay K".
    // A screenshot of this line alone tells whether both phones can reach a
    // relay (K>0) or are STUN-only — the key fact when two networks refuse
    // to connect without one.
    net?.let {
        Text(
            text = it,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    // v0.9.2 — frame health + the expandable engine log (on both roles).
    DiagnosticsPanel()
}

/**
 * v0.12.0 (Phase 8.3 completion) — the helper's typing dialog.
 *
 * Honest by design: it says the text REPLACES the field's current content
 * and that the field must be focused first (tap it with remote touch).
 * The 500-char cap mirrors the engine's maxTextChars; send is disabled
 * while empty. The engine acks the outcome — "no-field" comes back as an
 * actionable banner, not a shrug.
 */
@Composable
private fun AssistTypeDialog(onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    Dialog(onDismissRequest = onDismiss) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                        text = stringResource(R.string.td_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                        text = stringResource(R.string.td_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { if (it.length <= 500) text = it },
                    modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text(stringResource(R.string.td_placeholder)) },
                    minLines = 2
                )
                Text(
                    text = "${text.length}/500",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.End)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                    ) {
                            Text(stringResource(R.string.btn_cancel_plain))
                    }
                    Button(
                        onClick = {
                            ScreenShareEngine.sendAssistText(text)
                            onDismiss()
                        },
                        enabled = text.isNotBlank(),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                    ) {
                            Text(stringResource(R.string.btn_send))
                    }
                }
            }
        }
    }
}

/**
 * v0.9.2 — the helper's video surface, reusable for the inline view and the
 * full-screen dialog. The renderer view is created ONCE per composition slot
 * and released ONLY when the composable leaves composition for good; frames
 * arrive through the engine's permanent proxy sink, so there is no
 * addSink/removeSink/release race left (the black-picture bug class).
 */
@Composable
private fun VideoRendererBox(egl: EglBase.Context, modifier: Modifier) {
    val viewRef = remember { AtomicReference<SurfaceViewRenderer?>(null) }
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            SurfaceViewRenderer(ctx).apply {
                init(egl, null)
                setMirror(false)
                setEnableHardwareScaler(true)
                viewRef.set(this)
                ScreenShareEngine.attachRenderer(this)
            }
        },
        // Every recomposition re-asserts the target (idempotent swap inside
        // the engine).
        update = { view -> ScreenShareEngine.attachRenderer(view) }
    )
    DisposableEffect(Unit) {
        onDispose {
            ScreenShareEngine.detachRenderer()
            viewRef.getAndSet(null)?.release()
        }
    }
}

/**
 * v0.9.2 — the on-screen diagnostics block: frame health on one line, and a
 * tap-to-expand rolling engine log (last 14 events). The Owner is phone-only
 * — THIS panel is the logcat. One screenshot of it answers "where exactly
 * did the round stop?".
 */
@Composable
private fun DiagnosticsPanel() {
    val stats by ScreenShareEngine.frameStats.collectAsState()
    val diag by ScreenShareEngine.diag.collectAsState()
    if (diag.isEmpty()) return
    var open by remember { mutableStateOf(false) }
    Column {
        Text(
            text = buildString {
                stats?.let {
                    append(it)
                    append("  ·  ")
                }
                append(if (open) "Diagnostics ▴" else "Diagnostics ▾")
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.clickable { open = !open }
        )
        if (open) {
            Text(
                text = diag.joinToString("\n"),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 220.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(top = 4.dp)
            )
        }
    }
}

@Composable
fun StatusRow(label: String, value: String) {
    // Phase 11 — stacked label/value: a long value can never glue itself to
    // the label ("অনলাইনOnline ✓") or crush it into one-char-per-line
    // verticals. The small muted label above reads cleanly for seniors.
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

