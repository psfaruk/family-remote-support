package com.family.remote.support

import android.Manifest
import android.app.Activity
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
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.imePadding
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import com.family.remote.support.ui.AppWarningColor
import com.family.remote.support.ui.AuthGate
import com.family.remote.support.ui.FamilyRemoteSupportTheme
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

/**
 * Section card: icon-marked title row + optional ⓘ button that opens the
 * LONG explanation in a popup dialog — the card body itself stays short.
 */
@Composable
private fun SectionCard(
    title: String,
    icon: ImageVector? = null,
    infoText: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    var showInfo by remember { mutableStateOf(false) }
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                if (infoText != null) {
                    IconButton(
                        onClick = { showInfo = true },
                        // Phase 11.6 — 48dp touch target (was 36dp).
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Info,
                            contentDescription = "বিস্তারিত · details",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
            content()
        }
    }
    if (showInfo && infoText != null) {
        AlertDialog(
            onDismissRequest = { showInfo = false },
            confirmButton = {
                TextButton(onClick = { showInfo = false }) { Text("ঠিক আছে") }
            },
            title = {
                Text(text = title, fontWeight = FontWeight.Bold)
            },
            text = { Text(text = infoText) }
        )
    }
}

/**
 * v0.14.0 — chat-style list tile: avatar circle (initials or icon) + title
 * + one-line description + trailing chip/chevron. The single row shape used
 * by paired phones, devices, activity feed and account.
 */
@Composable
private fun AppListTile(
    title: String,
    subtitle: String,
    initials: String? = null,
    icon: ImageVector? = null,
    avatarContainer: Color = MaterialTheme.colorScheme.primaryContainer,
    avatarContent: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    chip: String? = null,
    chipContainer: Color = MaterialTheme.colorScheme.secondaryContainer,
    chipContent: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    onClick: (() -> Unit)? = null,
    trailingIcon: ImageVector? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(avatarContainer, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            when {
                initials != null -> Text(
                    text = initials.take(2).uppercase(),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = avatarContent
                )
                icon != null -> Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = avatarContent,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (chip != null) {
            Text(
                text = chip,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = chipContent,
                maxLines = 1,
                modifier = Modifier
                    .background(chipContainer, RoundedCornerShape(999.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }
        if (trailingIcon != null) {
            Icon(
                imageVector = trailingIcon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * v0.14.0 — settings row: leading icon + title + DESCRIPTION sub-text +
 * trailing control (Switch / button / chevron). Clicking the row itself
 * fires onClick (when provided).
 */
@Composable
private fun SettingsTile(
    icon: ImageVector,
    title: String,
    description: String,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        trailing?.invoke()
    }
}

/** v0.14.0 — friendly empty state: big icon + one line + optional action. */
@Composable
private fun EmptyState(
    icon: ImageVector,
    title: String,
    hint: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    illustrationRes: Int? = null
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (illustrationRes != null) {
            // Phase 11.6 — friendly on-device vector illustration (docs
            // §13); no remote images anywhere.
            Image(
                painter = painterResource(illustrationRes),
                contentDescription = null,
                modifier = Modifier.size(96.dp)
            )
        } else {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(44.dp)
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = hint,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (actionLabel != null && onAction != null) {
            WideOutlinedButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

/** v0.14.0 — compact status tile for the home 2×2 grid. */
@Composable
private fun IconStatusTile(
    icon: ImageVector,
    label: String,
    value: String,
    ok: Boolean,
    onClick: () -> Unit
) {
    Card {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .background(
                        color = if (ok) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.errorContainer
                        },
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (ok) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onErrorContainer
                    },
                    modifier = Modifier.size(20.dp)
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** v0.14.0 — quick-action tile (home): warm filled card with chevron. */
@Composable
private fun QuickActionTile(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(24.dp)
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                imageVector = Icons.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/** Phase 9 (T1) — primary action: full-width, ≥52dp tall (senior thumbs). */
@Composable
private fun BigButton(
    enabled: Boolean = true,
    onClick: () -> Unit,
    content: @Composable RowScope.() -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp),
        content = content
    )
}

/** Phase 9 (T1) — secondary action: full-width, ≥48dp tall. */
@Composable
private fun WideOutlinedButton(
    enabled: Boolean = true,
    onClick: () -> Unit,
    content: @Composable RowScope.() -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
        content = content
    )
}

class MainActivity : ComponentActivity() {
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

    // Phase 11 — bottom navigation: 0 হোম · 1 ফোন · 2 একটিভিটি · 3 সেটিংস.
    var tab by rememberSaveable { mutableStateOf(0) }

    // v0.14.0 — the ফোন tab's FAB opens the pairing bottom sheet; the sheet
    // itself lives inside PairingSections (it needs the create/redeem state).
    var showPairSheet by remember { mutableStateOf(false) }

    // Phase 11 — the permission wizard is an app-level overlay now, so a tab
    // switch can never close it mid-step.
    var showWizard by remember { mutableStateOf(false) }
    var permSummary by remember { mutableStateOf(readPermChecklist(context)) }

    val webClientConfigured = remember { BuildConfig.GOOGLE_WEB_CLIENT_ID.isNotBlank() }
    val firebaseProjectId = remember {
        try {
            FirebaseApp.getInstance().options.projectId ?: "not loaded"
        } catch (e: Exception) {
            "not loaded"
        }
    }
    val firebaseAppId = remember {
        try {
            FirebaseApp.getInstance().options.applicationId
        } catch (e: Exception) {
            "not loaded"
        }
    }

    // Phase 5 — schedule the 15-minute background heartbeat (idempotent).
    // Phase 6 — session-request notifications while the app is backgrounded.
    LaunchedEffect(Unit) {
        schedulePeriodicHeartbeat(context)
        SessionNotifier.start(context)
    }

    // Phase 11 — one light session listener drives BOTH the bell badge and
    // the home tab's consent/hint cards. PairingCard keeps its own listener
    // for its interactive cards; both are rules-bound capped queries.
    var homeSessions by remember { mutableStateOf<List<SessionView>>(emptyList()) }
    DisposableEffect(signedInUser?.uid) {
        val user = signedInUser
        if (user == null) {
            homeSessions = emptyList()
            onDispose { }
        } else {
            val stop = watchSessions(
                user,
                onUpdate = { homeSessions = it },
                onError = { homeSessions = emptyList() } // badge is best-effort
            )
            onDispose { stop() }
        }
    }
    val openSession = homeSessions.firstOrNull {
        it.status == "REQUESTED" || it.status == "ACTIVE"
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
    LaunchedEffect(Unit) {
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
        try {
            val deviceId = registerDevice(context, user)
            ownDeviceId = deviceId
            deviceStatus = "Registered ✓ (id ${deviceId.take(8)}…)"
            // Phase 5 — presence heartbeat + push token registration.
            try {
                sendHeartbeat(user.uid, deviceId, Build.MODEL)
                presenceStatus = "Online ✓ — heartbeat sent just now"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                presenceStatus = "Presence failed: ${friendlyErrorBn(e)}"
            }
            try {
                registerFcmToken(user.uid, deviceId)
                pushStatus = "Push notifications ready ✓"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                pushStatus = "Push setup failed: ${friendlyErrorBn(e)}"
            }
            deviceList = loadDevices(user)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            deviceStatus = "Registration failed: ${friendlyErrorBn(e)}"
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
                    authMessage = "সাইন-ইন বাতিল বা ব্যর্থ হয়েছে · Sign-in cancelled or failed"
                } catch (e: Exception) {
                    authMessage = "সাইন-ইন হয়নি: ${friendlyErrorBn(e)}"
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
                        authMessage = friendlyErrorBn(e)
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
                        authMessage = friendlyErrorBn(e)
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
                        authNotice =
                            "পাসওয়ার্ড-রিসেট লিংক $email ঠিকানায় পাঠানো হয়েছে — ইমেইল (স্প্যামসহ) দেখুন।"
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        authMessage = friendlyErrorBn(e)
                    } finally {
                        busy = false
                    }
                }
            }
        )
        return
    }

    Scaffold(
        topBar = {
            AppTopBar(
                showPresence = signedInUser != null && ownDeviceId.isNotBlank(),
                presenceUid = signedInUser?.uid,
                presenceDeviceId = ownDeviceId,
                pendingAsk = pendingAsk,
                onBellClick = { tab = 2 }
            )
        },
        bottomBar = { AppBottomNav(tab = tab, onSelect = { tab = it }) },
        floatingActionButton = {
            // v0.14.0 — FAB only on the ফোন tab: opens the pair sheet.
            if (tab == 1 && signedInUser != null) {
                ExtendedFloatingActionButton(
                    onClick = { showPairSheet = true },
                    icon = {
                        Icon(Icons.Filled.Add, contentDescription = null)
                    },
                    text = { Text("যুক্ত করুন") },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    ) { innerPadding ->
        // Phase 11.6 — per-tab scroll positions: switching tabs keeps each
        // tab's place instead of resetting everything back to the top.
        val tabScrollStates = remember { List(4) { ScrollState(0) } }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(tabScrollStates[tab])
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            when (tab) {
                0 -> HomeTab(
                    signedInUser = signedInUser,
                    deviceStatus = deviceStatus,
                    presenceStatus = presenceStatus,
                    pushStatus = pushStatus,
                    deviceList = deviceList,
                    permSummary = permSummary,
                    openSession = openSession,
                    onOpenWizard = { showWizard = true },
                    onRetryRegistration = { registerRetryKey++ },
                    onGoPairing = { tab = 1 },
                    onGoActivity = { tab = 2 },
                    busy = busy,
                    onSignIn = startSignIn
                )
                1 -> PairingCard(
                    signedInUser = signedInUser,
                    showPairSheet = showPairSheet,
                    onDismissPairSheet = { showPairSheet = false }
                )
                2 -> ActivityTab(user = signedInUser)
                else -> SettingsTab(
                    signedInUser = signedInUser,
                    busy = busy,
                    statusMessage = statusMessage,
                    webClientConfigured = webClientConfigured,
                    firebaseProjectId = firebaseProjectId,
                    firebaseAppId = firebaseAppId,
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
                                var cleanupNote: String? = null
                                val currentUser = FirebaseAuth.getInstance().currentUser
                                if (currentUser != null) {
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
                                        cleanupNote = "সাইন-আউট হয়েছে — তবে নেটওয়ার্ক-সমস্যায় এই ফোনের " +
                                            "উপস্থিতি/পুশ-টোকেন মুছা যায়নি। পরে ইন্টারনেট সহ সাইন-ইন করে " +
                                            "আবার সাইন-আউট করলেই পরিষ্কার হয়ে যাবে।"
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
                                statusMessage = null
                                authMessage = null
                                authNotice = cleanupNote
                                    ?: "সাইন-আউট সম্পন্ন — এই ফোনের উপস্থিতি ও পুশ-টোকেন " +
                                        "সার্ভার থেকে মুছে ফেলা হয়েছে।"
                            } catch (e: Exception) {
                                statusMessage = "Sign-out failed: ${e.message}"
                            } finally {
                                busy = false
                            }
                        }
                    },
                    onOpenWizard = { showWizard = true }
                )
            }
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

/** Phase 11 — top bar: app name + live presence chip + notification bell. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppTopBar(
    showPresence: Boolean,
    presenceUid: String?,
    presenceDeviceId: String,
    pendingAsk: Boolean,
    onBellClick: () -> Unit
) {
    TopAppBar(
        title = {
            Text(
                text = "Family Remote Support",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        actions = {
            if (showPresence) {
                PresenceChip(uid = presenceUid, deviceId = presenceDeviceId)
            }
            IconButton(onClick = onBellClick) {
                BadgedBox(badge = { if (pendingAsk) Badge() }) {
                    Icon(
                        imageVector = Icons.Filled.Notifications,
                        contentDescription = "Notifications · নোটিফিকেশন"
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background
        )
    )
}

/** Phase 11 — bottom navigation, four senior-friendly tabs. */
@Composable
private fun AppBottomNav(tab: Int, onSelect: (Int) -> Unit) {
    NavigationBar {
        NavigationBarItem(
            selected = tab == 0,
            onClick = { onSelect(0) },
            icon = { Icon(Icons.Filled.Home, contentDescription = null) },
            label = { Text("হোম") }
        )
        NavigationBarItem(
            selected = tab == 1,
            onClick = { onSelect(1) },
            icon = { Icon(Icons.Filled.Phone, contentDescription = null) },
            label = { Text("ফোন") }
        )
        NavigationBarItem(
            selected = tab == 2,
            onClick = { onSelect(2) },
            icon = { Icon(Icons.Filled.Notifications, contentDescription = null) },
            label = { Text("একটিভিটি") }
        )
        NavigationBarItem(
            selected = tab == 3,
            onClick = { onSelect(3) },
            icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
            label = { Text("সেটিংস") }
        )
    }
}

/**
 * Phase 11 — tiny live presence chip for the top bar: green-ish dot while
 * this device's heartbeat is fresh, muted otherwise. Honest label only.
 */
@Composable
private fun PresenceChip(uid: String?, deviceId: String) {
    var heartbeatAt by remember(uid, deviceId) { mutableStateOf<Long?>(null) }
    var unavailable by remember(uid, deviceId) { mutableStateOf(false) }
    var nowMillis by remember { mutableStateOf(System.currentTimeMillis()) }

    LaunchedEffect(uid, deviceId) {
        while (true) {
            nowMillis = System.currentTimeMillis()
            delay(10_000)
        }
    }
    DisposableEffect(uid, deviceId) {
        val stopWatching = if (uid != null && deviceId.isNotBlank()) {
            watchDevicePresence(
                uid,
                deviceId,
                onChange = { view ->
                    unavailable = false
                    heartbeatAt = view?.heartbeatAtMillis
                },
                onError = { unavailable = true }
            )
        } else {
            null
        }
        onDispose { stopWatching?.invoke() }
    }

    val age = heartbeatAt?.let { nowMillis - it } ?: -1L
    val online = !unavailable && heartbeatAt != null && heartbeatAt != 0L &&
        age >= 0 && age < PRESENCE_ONLINE_MILLIS
    val label = when {
        uid == null || deviceId.isBlank() -> "…"
        unavailable -> "অবস্থা জানা যাচ্ছে না"
        online -> "অনলাইন"
        heartbeatAt == null || heartbeatAt == 0L -> "…"
        else -> "অফলাইন"
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(
                    color = when {
                        online -> MaterialTheme.colorScheme.tertiary
                        unavailable -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    },
                    shape = CircleShape
                )
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════
// হোম ট্যাব (v0.14.0) — কম লেখা, বেশি কাঠামো:
//   ১. অনুমতির অনুরোধ এলে Allow/Deny কার্ড (একদম উপরে)
//   ২. ২×২ স্ট্যাটাস টাইল গ্রিড (পারমিশন · এই ফোন · অনলাইন · নোটিফিকেশন)
//   ৩. ব্যর্থ কিছু থাকলে একটাই কম্প্যাক্ট এরর-কার্ড
//   ৪. এই অ্যাকাউন্টের ফোন — লিস্ট-টাইল
//   ৫. দ্রুত অ্যাকশন — ২টি উষ্ণ টাইল
// বড় ব্যাখ্যা-প্যারাগ্রাফ নেই — বিস্তারিত ⓘ-পপআপে।
// ═══════════════════════════════════════════════════════════════════════
@Composable
private fun HomeTab(
    signedInUser: FirebaseUser?,
    deviceStatus: String,
    presenceStatus: String,
    pushStatus: String,
    deviceList: List<String>,
    permSummary: PermChecklist,
    openSession: SessionView?,
    onOpenWizard: () -> Unit,
    onRetryRegistration: () -> Unit,
    onGoPairing: () -> Unit,
    onGoActivity: () -> Unit,
    busy: Boolean = false,
    onSignIn: () -> Unit = {}
) {
    if (signedInUser != null && openSession != null) {
        if (openSession.status == "REQUESTED" &&
            openSession.recipientUid == signedInUser.uid
        ) {
            // The consent card leads the default tab — no hunting for it.
            HomeConsentCard(session = openSession, user = signedInUser)
        } else {
            HomeLiveHintCard(session = openSession, myUid = signedInUser.uid)
        }
    }

    Text(
        text = "পরিবারের ফোনে দূর থেকে সাহায্য — সবসময় স্পষ্ট অনুমতি নিয়ে।",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    // ২×২ স্ট্যাটাস গ্রিড — প্রতিটি টাইলে আইকন + সংক্ষিপ্ত অবস্থা; ট্যাপ করলে
    // সংশ্লিষ্ট অ্যাকশন (উইজার্ড / রিট্রাই)।
    val registered = deviceStatus.startsWith("Registered")
    val regFailed = deviceStatus.startsWith("Registration failed")
    val regBusy = deviceStatus.startsWith("Registering")
    val presenceOk = presenceStatus.startsWith("Online")
    val pushOk = pushStatus.startsWith("Push notifications ready")
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(modifier = Modifier.weight(1f)) {
            IconStatusTile(
                icon = Icons.Filled.Lock,
                label = "পারমিশন",
                value = if (permSummary.allDone) "সব চালু ✓" else "চালু ${permSummary.doneCount}/4",
                ok = permSummary.allDone,
                onClick = onOpenWizard
            )
        }
        Box(modifier = Modifier.weight(1f)) {
            IconStatusTile(
                icon = Icons.Filled.Phone,
                label = "এই ফোন",
                value = when {
                    registered -> "রেজিস্টার্ড ✓"
                    regBusy -> "চলছে…"
                    regFailed -> "ব্যর্থ — চাপুন"
                    else -> "চেক হয়নি"
                },
                ok = registered,
                onClick = { if (regFailed) onRetryRegistration() else onOpenWizard() }
            )
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(modifier = Modifier.weight(1f)) {
            IconStatusTile(
                icon = Icons.Filled.Person,
                label = "অনলাইন",
                value = when {
                    signedInUser == null -> "সাইন-ইন নেই"
                    presenceOk -> "অনলাইন ✓"
                    presenceStatus.startsWith("Presence failed") -> "ব্যর্থ"
                    else -> "অপেক্ষায়…"
                },
                ok = signedInUser != null && presenceOk,
                onClick = onOpenWizard
            )
        }
        Box(modifier = Modifier.weight(1f)) {
            IconStatusTile(
                icon = Icons.Filled.Notifications,
                label = "নোটিফিকেশন",
                value = when {
                    signedInUser == null -> "সাইন-ইন নেই"
                    pushOk -> "প্রস্তুত ✓"
                    pushStatus.startsWith("Push setup failed") -> "ব্যর্থ"
                    else -> "সেটআপ হয়নি"
                },
                ok = signedInUser != null && pushOk,
                onClick = onOpenWizard
            )
        }
    }

    // যেকোনো সেটআপ-ব্যর্থতা থাকলে একটাই কম্প্যাক্ট এরর-কার্ড (প্রতিটি ব্যর্থতার
    // পূর্ণ কারণ এখানেই — টাইলগুলো ছোট-পরিষ্কার থাকে)।
    val failures = buildList {
        if (regFailed) add(deviceStatus)
        if (presenceStatus.startsWith("Presence failed")) add(presenceStatus)
        if (pushStatus.startsWith("Push setup failed")) add(pushStatus)
    }
    if (failures.isNotEmpty()) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "সেটআপে সমস্যা",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                failures.forEach {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
                WideOutlinedButton(onClick = onRetryRegistration) {
                    Text("আবার চেষ্টা করুন · Retry")
                }
            }
        }
    }

    SectionCard(title = "এই অ্যাকাউন্টের ফোন", icon = Icons.Filled.Phone) {
        if (deviceList.isEmpty()) {
            Text(
                text = "এখনো কোনো ফোন নিবন্ধিত হয়নি।",
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

    Text(
        text = "দ্রুত অ্যাকশন · Quick actions",
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(modifier = Modifier.weight(1f)) {
            QuickActionTile(
                icon = Icons.Filled.Add,
                title = "ফোন যুক্ত করুন",
                subtitle = "কোড বা QR স্ক্যান",
                onClick = onGoPairing
            )
        }
        Box(modifier = Modifier.weight(1f)) {
            QuickActionTile(
                icon = Icons.Filled.List,
                title = "একটিভিটি",
                subtitle = "সেশন ও ফোন",
                onClick = onGoActivity
            )
        }
    }

    Text(
        text = "v${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center
    )
}

/** v0.14.0 — one-line live-session hint card (non-consent cases). */
@Composable
private fun HomeLiveHintCard(session: SessionView, myUid: String) {
    Card {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
            Text(
                text = when {
                    session.status == "REQUESTED" ->
                        "${session.recipientEmail}-কে অনুমতির অনুরোধ গেছে — 'ফোন' ট্যাবে বিস্তারিত।"
                    session.helperUid == myUid ->
                        "${session.recipientEmail}-এর ফোনে সেশন চলছে — 'ফোন' ট্যাবে ভিডিও।"
                    else ->
                        "${session.helperEmail.ifBlank { "Someone" }} আপনার ফোনে সাহায্য করছেন — 'ফোন' ট্যাবে বিস্তারিত।"
                },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/**
 * Phase 11 — the recipient-side consent card, now on the home tab. Same
 * Firestore actions as the pairing card's consent card (grant/deny); the
 * security rules re-verify everything either way.
 */
@Composable
private fun HomeConsentCard(session: SessionView, user: FirebaseUser) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var nowMillis by remember { mutableStateOf(System.currentTimeMillis()) }

    LaunchedEffect(session.id) {
        while (true) {
            nowMillis = System.currentTimeMillis()
            delay(1000)
        }
    }

    Card(
        colors = CardDefaults.cardColors(
            // Phase 11.6 — the approval card is THE most prominent surface
            // on home: primaryContainer reads "action needed" instantly.
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                // Phase 11.6 — screen readers announce this as ONE approval
                // card (docs §15), while Allow/Deny stay separate buttons.
                .semantics(mergeDescendants = true) {},
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth()) {
                StatusBadge(
                    label = "সম্মতির অনুরোধ · Approval requested",
                    kind = "info"
                )
            }
            Text(
                text = "${session.helperEmail.ifBlank { "Someone" }} এই ফোনে সাহায্য করতে চাইছেন।",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            if (session.kind == "guest") {
                Text(
                    text = "Guest (সাময়িক) সাহায্যকারী — এই লিংক দেওয়ার ২ ঘণ্টা পরে নিজেই " +
                        "মুছে যাবে; সেশনটিও তার মেয়াদের মধ্যেই শেষ হবে।",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
            val secondsLeft =
                ((session.respondDeadlineMillis - nowMillis) / 1000).coerceAtLeast(0)
            Text(
                text = "চিনি মানুষ হলে তবেই অনুমতি দিন। অনুরোধটি ${mmss(secondsLeft)}-এ শেষ হবে।",
                style = MaterialTheme.typography.bodyMedium
            )
            message?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (it.contains("✓")) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    }
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    enabled = !busy,
                    onClick = {
                        busy = true
                        scope.launch {
                            try {
                                grantSession(session.id, user.uid)
                                message = "অনুমতি দেওয়া হয়েছে ✓ — 'ফোন' ট্যাবে সেশন দেখুন।"
                            } catch (e: SessionException) {
                                message = e.message
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                message = "অনুমতি দেওয়া যায়নি: ${describeFirestoreError(e)}"
                            } finally {
                                busy = false
                            }
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 52.dp)
                ) {
                    Text("Allow · অনুমতি দিন", fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    enabled = !busy,
                    onClick = {
                        busy = true
                        scope.launch {
                            try {
                                denySession(session.id, user.uid)
                                message = "অনুরোধ প্রত্যাখ্যাত হয়েছে।"
                            } catch (e: SessionException) {
                                message = e.message
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                message = "প্রত্যাখ্যান করা যায়নি: ${describeFirestoreError(e)}"
                            } finally {
                                busy = false
                            }
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 52.dp)
                ) {
                    Text("Deny · না", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════
// একটিভিটি ট্যাব (v0.14.0) — শুধু প্রমাণিত ডেটা, প্রতিটি সারি আইকন-টাইল:
// লাইভ সেশন → শেষ-সেশন → যুক্ত ফোনের প্রেজেন্স। বানানো history নেই।
// ═══════════════════════════════════════════════════════════════════════
@Composable
private fun ActivityTab(user: FirebaseUser?) {
    if (user == null) {
        SectionCard(title = "একটিভিটি", icon = Icons.Filled.List) {
            EmptyState(
                icon = Icons.Filled.Person,
                title = "সাইন-ইন নেই",
                hint = "একটিভিটি দেখতে সেটিংস ট্যাব থেকে সাইন-ইন করুন।"
            )
        }
        return
    }
    ActivityFeed(user)
}

@Composable
private fun ActivityFeed(user: FirebaseUser) {
    var sessions by remember { mutableStateOf<List<SessionView>>(emptyList()) }
    var sessionSyncError by remember { mutableStateOf<String?>(null) }
    var relationships by remember { mutableStateOf<List<RelationshipView>>(emptyList()) }
    var relSyncError by remember { mutableStateOf<String?>(null) }
    // The last session that closed — fetched once when it leaves the open set.
    var lastFinished by remember { mutableStateOf<SessionView?>(null) }
    var lastOpenId by remember { mutableStateOf<String?>(null) }
    var nowMillis by remember { mutableStateOf(System.currentTimeMillis()) }

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
            onError = { sessionSyncError = describeFirestoreError(it) }
        )
        onDispose { stop() }
    }
    DisposableEffect(user.uid) {
        val stop = watchRelationships(
            user,
            onUpdate = { relSyncError = null; relationships = it },
            onError = { relSyncError = describeFirestoreError(it) }
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
        title = "সেশন একটিভিটি",
        icon = Icons.Filled.Notifications,
        infoText = "এখানে শুধু প্রমাণিত তথ্য দেখানো হয়: চলমান বা অপেক্ষমাণ সেশন, সবে-শেষ-হওয়া " +
            "সেশন এবং যুক্ত ফোনের অনলাইন অবস্থা। পুরোনো সেশনের তালিকা ইচ্ছাকৃতভাবে নেই — " +
            "যা ঘটেনি বা যাচাই করা যায় না, তা দেখানো হয় না।"
    ) {
        if (openSession == null && lastFinished == null) {
            EmptyState(
                icon = Icons.Filled.Notifications,
                title = "এখনো কোনো সেশন-একটিভিটি নেই",
                hint = "সেশন শুরু হলে বা অনুমতির অনুরোধ এলে এখানে দেখা যাবে।",
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
                        "${s.helperEmail.ifBlank { "Someone" }} সাহায্যের অনুরোধ করেছেন"
                    s.status == "REQUESTED" ->
                        "${s.recipientEmail}-কে অনুরোধ পাঠানো হয়েছে"
                    s.status == "ACTIVE" && s.helperUid == user.uid ->
                        "${s.recipientEmail}-এর ফোনে সেশন চলছে"
                    else ->
                        "${s.helperEmail.ifBlank { "Someone" }} আপনার ফোনে সাহায্য করছেন"
                },
                subtitle = when {
                    s.status == "REQUESTED" && s.recipientUid == user.uid ->
                        "হোম ট্যাবে Allow/Deny দিন · বাকি ${mmss(secondsLeft)}"
                    s.status == "REQUESTED" ->
                        "অনুমতির অপেক্ষায় · বাকি ${mmss(secondsLeft)}"
                    else ->
                        "'ফোন' ট্যাবে ভিডিও ও নিয়ন্ত্রণ · শেষ হবে ${mmss(secondsLeft)}-এ"
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
                chip = if (s.kind == "guest") "Guest" else sessionStatusBn(s.status),
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
                title = "শেষ সেশন",
                subtitle = "${s.helperEmail.ifBlank { "?" }} → ${s.recipientEmail}" +
                    (s.endedBy?.let { " · শেষ করেছে: $it" } ?: ""),
                icon = Icons.Filled.CheckCircle,
                avatarContainer = MaterialTheme.colorScheme.surfaceVariant,
                avatarContent = MaterialTheme.colorScheme.onSurfaceVariant,
                chip = sessionStatusBn(s.status),
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
        title = "যুক্ত ফোন",
        icon = Icons.Filled.Phone,
        infoText = "আপনার অ্যাকাউন্টে যুক্ত বিশ্বস্ত ফোন আর তাদের লাইভ অনলাইন-অবস্থা। " +
            "ফোন যুক্ত করতে বা সরাতে 'ফোন' ট্যাব দেখুন।"
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
                title = "এখনো কোনো ফোন যুক্ত হয়নি",
                hint = "'ফোন' ট্যাব থেকে কোড বানিয়ে বিশ্বস্ত ফোন যুক্ত করুন।",
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
                            append(if (iHelp) "আপনি সাহায্য করছেন" else "সাহায্য করছে")
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

/** Phase 11 — honest Bengali name for a session status. */
private fun sessionStatusBn(status: String): String = when (status) {
    "REQUESTED" -> "অনুরোধ"
    "ACTIVE" -> "চলছে"
    "ENDED" -> "শেষ"
    "DENIED" -> "প্রত্যাখ্যাত"
    "CANCELLED" -> "বাতিল"
    "EXPIRED" -> "সময় শেষ"
    else -> status
}

// ═══════════════════════════════════════════════════════════════════════
// Phase 11.6 — status badges (docs §7.2): every state shows color + icon
// + a word, never color alone. Kind helpers keep the mapping in ONE place.
// ═══════════════════════════════════════════════════════════════════════

/** Semantic bucket for a session status: success / info / error / neutral. */
private fun sessionBadgeKind(status: String): String = when (status) {
    "ACTIVE" -> "success"
    "REQUESTED" -> "info"
    "DENIED", "CANCELLED", "EXPIRED" -> "error"
    else -> "neutral"
}

/** Theme-aware container/content pair for a badge kind. */
@Composable
private fun statusChipColors(kind: String): Pair<Color, Color> = when (kind) {
    "success" -> Pair(
        MaterialTheme.colorScheme.tertiaryContainer,
        MaterialTheme.colorScheme.onTertiaryContainer
    )
    "info" -> Pair(
        MaterialTheme.colorScheme.primaryContainer,
        MaterialTheme.colorScheme.onPrimaryContainer
    )
    "error" -> Pair(
        MaterialTheme.colorScheme.errorContainer,
        MaterialTheme.colorScheme.onErrorContainer
    )
    else -> Pair(
        MaterialTheme.colorScheme.surfaceVariant,
        MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/** Small pill: icon + word; reused wherever a state is shown. */
@Composable
private fun StatusBadge(label: String, kind: String) {
    val colors = statusChipColors(kind)
    Row(
        modifier = Modifier
            .background(colors.first, RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        when (kind) {
            "success" -> Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = colors.second,
                modifier = Modifier.size(13.dp)
            )
            "info" -> Icon(
                imageVector = Icons.Filled.Send,
                contentDescription = null,
                tint = colors.second,
                modifier = Modifier.size(12.dp)
            )
            "error" -> Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = null,
                tint = colors.second,
                modifier = Modifier.size(13.dp)
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = colors.second
        )
    }
}

/**
 * Phase 11.6 — friendly one-liner for families (docs §10-L): the common
 * failures (network, permission) get human language; anything else falls
 * through to the full technical describeFirestoreError.
 */
private fun friendlyErrorBn(e: Exception): String = when (e) {
    // Phase 11.7 — auth-specific failures in family language first.
    is FirebaseAuthInvalidUserException ->
        "এই ইমেইলে কোনো অ্যাকাউন্ট পাওয়া যায়নি — 'নতুন অ্যাকাউন্ট' ট্যাবে গিয়ে অ্যাকাউন্ট তৈরি করুন। · No account found"
    is FirebaseAuthInvalidCredentialsException ->
        "ইমেইল বা পাসওয়ার্ড ভুল — আবার চেষ্টা করুন। · Wrong email or password"
    is FirebaseAuthUserCollisionException ->
        "এই ইমেইলেই অ্যাকাউন্ট আছে — 'লগইন' ট্যাবে ঢুকে চেষ্টা করুন। · Email already in use"
    is FirebaseAuthWeakPasswordException ->
        "পাসওয়ার্ড কমপক্ষে ৬ অক্ষরের হতে হবে। · Weak password"
    is FirebaseTooManyRequestsException ->
        "অনেকবার চেষ্টা হয়েছে — কিছুক্ষণ পরে আবার করুন। · Too many attempts"
    is FirebaseNetworkException ->
        "ইন্টারনেট সংযোগ পাওয়া যাচ্ছে না — একটু পরে আবার চেষ্টা করুন। · No internet connection"
    is FirebaseFirestoreException -> when (e.code) {
        FirebaseFirestoreException.Code.UNAVAILABLE,
        FirebaseFirestoreException.Code.DEADLINE_EXCEEDED ->
            "ইন্টারনেট সংযোগ পাওয়া যাচ্ছে না — একটু পরে আবার চেষ্টা করুন। · No internet connection"
        FirebaseFirestoreException.Code.PERMISSION_DENIED ->
            "অনুমতি নেই — সেটিংস থেকে সাইন-আউট করে আবার সাইন-ইন করুন। · Not allowed"
        else -> describeFirestoreError(e)
    }
    else -> describeFirestoreError(e)
}

// ═══════════════════════════════════════════════════════════════════════
// সেটিংস ট্যাব (v0.14.0) — প্রতিটি সারি SettingsTile: আইকন + টাইটেল +
// ছোট বিবরণ + ট্রেইলিং কন্ট্রোল (টগল সুইচ / বোতাম / শেভরন)।
// ═══════════════════════════════════════════════════════════════════════
/** Phase 11.6 — one appearance choice row with a mini theme-preview dot. */
@Composable
private fun ThemeOptionRow(
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
                    contentDescription = "নির্বাচিত · selected",
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
private fun SettingsTab(
    signedInUser: FirebaseUser?,
    busy: Boolean,
    statusMessage: String?,
    webClientConfigured: Boolean,
    firebaseProjectId: String,
    firebaseAppId: String,
    permSummary: PermChecklist,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
    onOpenWizard: () -> Unit
) {
    val context = LocalContext.current

    SectionCard(title = "অ্যাকাউন্ট", icon = Icons.Filled.Person) {
        if (!webClientConfigured) {
            Text(
                text = "এই বিল্ডে সাইন-ইন বন্ধ: Web Client ID সেট হয়নি। " +
                    "পরের বিল্ডে যুক্ত হবে।",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
        if (signedInUser == null) {
            EmptyState(
                icon = Icons.Filled.Person,
                title = "সাইন-ইন করা নেই",
                hint = "ইমেইল বা Google অ্যাকাউন্ট দিয়ে সাইন-ইন করলে ডিভাইস ও যুক্তি সংরক্ষিত হবে।"
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
                    Text("গুগল দিয়ে সাইন ইন")
                }
            }
        } else {
            AppListTile(
                title = signedInUser.displayName ?: "(no name)",
                subtitle = signedInUser.email ?: "(no email)",
                initials = (signedInUser.displayName ?: signedInUser.email ?: "?")
                    .take(1).ifBlank { "?" }
            )
            Text(
                text = "আপনার অ্যাকাউন্ট ও ডিভাইস শুধু আপনার নিজের Firebase প্রজেক্টে থাকে।",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            WideOutlinedButton(enabled = !busy, onClick = onSignOut) {
                Icon(
                    imageVector = Icons.Filled.ExitToApp,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Text("  সাইন আউট")
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

    SectionCard(
        title = "সেটআপ ও পারমিশন",
        icon = Icons.Filled.Lock,
        infoText = "এই ৪টি অনুমতি চালু থাকলে অ্যাপের সব ফিচার মসৃণ চলে: " +
            "নোটিফিকেশন (অ্যাপ বন্ধ থাকলেও অনুরোধ দেখা), ক্যামেরা (QR স্ক্যান), " +
            "Remote assist (সাহায্যকারীর রিমোট টাচ) এবং ব্যাটারি-অপটিমাইজেশন ছাড় " +
            "(ব্যাকগ্রাউন্ড হার্টবিট)। 'চেক করুন' চাপলে ধাপে-ধাপে উইজার্ড খুলবে।"
    ) {
        SettingsTile(
            icon = Icons.Filled.Lock,
            title = "পারমিশন চেক",
            description = if (permSummary.allDone) {
                "সব ৪টি পারমিশন চালু ✓ — অ্যাপ পুরোপুরি প্রস্তুত।"
            } else {
                "চালু ${permSummary.doneCount}/4 — বাকি: ${permSummary.missingNamesBn()}"
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
        SettingsAssistTile()
    }

    // Phase 11.6 — REAL appearance setting (docs §9): System / Light /
    // Dark, persisted via ThemePrefBus. The root theme wrapper reads this
    // exact state, so the whole app repaints instantly — not a fake toggle.
    SectionCard(
        title = "চেহারা · Appearance",
        icon = Icons.Filled.Settings,
        infoText = "সিস্টেম = ফোনের দিন/রাত সেটিং মেনে চলে। লাইট = সবসময় উজ্জ্বল, " +
            "ডার্ক = সবসময় অন্ধকার (রাতে চোখে আরাম)। পছন্দ মনে রাখা হয়।"
    ) {
        ThemeOptionRow(
            label = "সিস্টেম · System",
            subtitle = "ফোনের সেটিং অনুযায়ী",
            selected = ThemePrefBus.pref == ThemePrefBus.SYSTEM,
            preview = 0,
            onClick = { ThemePrefBus.set(context, ThemePrefBus.SYSTEM) }
        )
        ThemeOptionRow(
            label = "লাইট · Light",
            subtitle = "সবসময় উজ্জ্বল",
            selected = ThemePrefBus.pref == ThemePrefBus.LIGHT,
            preview = 1,
            onClick = { ThemePrefBus.set(context, ThemePrefBus.LIGHT) }
        )
        ThemeOptionRow(
            label = "ডার্ক · Dark",
            subtitle = "সবসময় অন্ধকার — রাতে আরাম",
            selected = ThemePrefBus.pref == ThemePrefBus.DARK,
            preview = 2,
            onClick = { ThemePrefBus.set(context, ThemePrefBus.DARK) }
        )
    }

    SectionCard(title = "অ্যাপ সম্পর্কে", icon = Icons.Filled.Info) {
        var showAbout by remember { mutableStateOf(false) }
        SettingsTile(
            icon = Icons.Filled.Info,
            title = "v${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})",
            description = "প্যাকেজ, Firebase প্রজেক্ট ও অ্যাপ-ID বিস্তারিত",
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
                    TextButton(onClick = { showAbout = false }) { Text("ঠিক আছে") }
                },
                title = { Text("অ্যাপ সম্পর্কে", fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatusRow(label = "সংস্করণ", value = "v${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})")
                        StatusRow(label = "Package", value = "com.family.remote.support")
                        StatusRow(label = "Firebase project", value = firebaseProjectId)
                        StatusRow(label = "Firebase app ID", value = firebaseAppId)
                    }
                }
            )
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
private fun SettingsNotifTile() {
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
        title = "নোটিফিকেশন",
        description = if (notifGranted) {
            "চালু — অ্যাপ বন্ধ থাকলেও সাহায্যের অনুরোধ দেখা যাবে।"
        } else {
            "বন্ধ — সাহায্যের অনুরোধ মিস হতে পারে।"
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
private fun SettingsAssistTile() {
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
        title = "Remote assist · রিমোট টাচ",
        description = if (assistOn) {
            "চালু ✓ — সাহায্যকারী এই স্ক্রিনে ট্যাপ, সোয়াইপ ও টাইপ করতে পারবেন।"
        } else {
            "বন্ধ — সাহায্যকারী শুধু দেখতে পারবেন, ট্যাপ করতে পারবেন না।"
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
                    Text("সেটআপ")
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

// ═══════════════════════════════════════════════════════════════════════
// ফোন ট্যাব (v0.14.0):
//   • লাইভ সেশন-কার্ড একদম উপরে (অনুমতি চাওয়া / চলমান)
//   • চলমান পেয়ারিং-কোড কার্ড: বড় কোড + QR + ⋮ dropdown (কপি/সেভ/শেয়ার)
//   • যুক্ত ফোন — Chat-স্টাইল List Tile (avatar + নাম + অবস্থা + ব্যাজ),
//     ট্যাপ করলে নিচ-শিটে সব অ্যাকশন (সেশন শুরু / Revoke / Remove)
//   • ডানদিকের FAB "+" → নিচ-শিট: Family / Guest কোড বা কোড-লিখে যুক্ত
//   • বড় ব্যাখ্যা সব ⓘ-পপআপে — স্ক্রিনে শুধু কাজের জিনিস
//   • নোটিফিকেশন-পারমিশন জিজ্ঞাসা সেটিংস ট্যাবে সরানো হয়েছে (টগল-সহ)
// Firestore-লজিক অপরিবর্তিত — rules সব পুনঃযাচাই করে।
// ═══════════════════════════════════════════════════════════════════════
@Composable
fun PairingCard(
    signedInUser: FirebaseUser?,
    showPairSheet: Boolean,
    onDismissPairSheet: () -> Unit
) {
    if (signedInUser == null) {
        SectionCard(title = "ফোন যুক্ত করুন", icon = Icons.Filled.Phone) {
            EmptyState(
                icon = Icons.Filled.Person,
                title = "প্রথমে সাইন-ইন করুন",
                hint = "যুক্তি তৈরি করতে সাইন-ইন দরকার — অ্যাপ খুললেই লগইন/সাইন-আপ পেজ আসে।"
            )
        }
    } else {
        PairingSections(
            user = signedInUser,
            showPairSheet = showPairSheet,
            onDismissPairSheet = onDismissPairSheet
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PairingSections(
    user: FirebaseUser,
    showPairSheet: Boolean,
    onDismissPairSheet: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var invite by remember { mutableStateOf<InviteCode?>(null) }
    var inviteKind by remember { mutableStateOf(REL_KIND_FAMILY) }
    var inviteStatus by remember { mutableStateOf<String?>(null) }
    var claimerEmail by remember { mutableStateOf<String?>(null) }
    var inviteSyncError by remember { mutableStateOf<String?>(null) }
    var nowMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    var creatingKind by remember { mutableStateOf<String?>(null) }
    var pairingError by remember { mutableStateOf<String?>(null) }
    var codeInput by remember { mutableStateOf("") }
    var redeeming by remember { mutableStateOf(false) }
    var redeemMessage by remember { mutableStateOf<String?>(null) }
    var relationships by remember { mutableStateOf<List<RelationshipView>>(emptyList()) }
    var relationshipsSyncError by remember { mutableStateOf<String?>(null) }
    var revokingId by remember { mutableStateOf<String?>(null) }
    var showScanner by remember { mutableStateOf(false) }
    // v0.14.0 — which paired phone's action sheet is open (by rel id).
    var selectedRelId by remember { mutableStateOf<String?>(null) }
    // Phase 12 — the code waiting in the role-setup sheet (scan → preview →
    // confirm → redeem → success, one automatic journey).
    var pendingScan by remember { mutableStateOf<ScannedCode?>(null) }
    // Phase 12 — the role of the LAST successful redeem (drives the sheet's
    // success view; null while nothing redeemed yet).
    var redeemedKind by remember { mutableStateOf<String?>(null) }

    // Phase 6 — support session state.
    var sessions by remember { mutableStateOf<List<SessionView>>(emptyList()) }
    var sessionSyncError by remember { mutableStateOf<String?>(null) }
    var sessionBusy by remember { mutableStateOf(false) }
    var sessionMessage by remember { mutableStateOf<String?>(null) }
    var sessionNow by remember { mutableStateOf(System.currentTimeMillis()) }
    // The last session that closed (ended/denied/cancelled/expired) — kept in
    // memory for the "Last session:" line. The live watches only track OPEN
    // sessions (index-free + rules-provable), so when one disappears from the
    // open set we fetch its final state once and remember it here.
    var lastFinishedSession by remember { mutableStateOf<SessionView?>(null) }
    var lastOpenSessionId by remember { mutableStateOf<String?>(null) }

    // Ticks the countdown once per second while an invitation exists.
    LaunchedEffect(invite?.invitationId) {
        if (invite != null) {
            while (true) {
                nowMillis = System.currentTimeMillis()
                delay(1000)
            }
        }
    }

    // Live status of the invitation we created (waiting → paired/cancelled).
    DisposableEffect(invite?.invitationId) {
        val registration = invite?.invitationId?.let { id ->
            watchInvitation(
                id,
                onChange = { view ->
                    inviteSyncError = null
                    inviteStatus = view?.status
                    claimerEmail = view?.claimedEmail
                },
                onError = { e -> inviteSyncError = describeFirestoreError(e) }
            )
        }
        onDispose { registration?.remove() }
    }

    // Live list of trusted relationships (both directions).
    DisposableEffect(user.uid) {
        val stopWatching = watchRelationships(
            user,
            onUpdate = { relationshipsSyncError = null; relationships = it },
            onError = { e -> relationshipsSyncError = describeFirestoreError(e) }
        )
        onDispose { stopWatching() }
    }

    // Phase 10 — relNow ticks every 30 s while any guest link exists, so
    // the "বাকি সময়" countdowns stay honest without a per-second churn.
    var relNow by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(relationships) {
        if (relationships.any { it.kind == REL_KIND_GUEST }) {
            while (true) {
                relNow = System.currentTimeMillis()
                delay(30_000)
            }
        } else {
            relNow = System.currentTimeMillis()
        }
    }
    // Phase 10 — lazy cleanup: the first app that notices an over-time
    // guest link writes ACTIVE -> EXPIRED (the rules allow exactly this).
    LaunchedEffect(relationships, relNow) {
        relationships
            .filter {
                it.kind == REL_KIND_GUEST && it.status == "ACTIVE" &&
                    it.guestExpiresAtMillis > 0L && relNow > it.guestExpiresAtMillis + 4000
            }
            .forEach { rel ->
                try {
                    expireGuestRelationship(rel.id)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // best effort — the window check elsewhere is the real gate
                }
            }
    }

    // Phase 6 — live list of this account's support sessions (both roles).
    DisposableEffect(user.uid) {
        val stopWatching = watchSessions(
            user,
            onUpdate = { sessionSyncError = null; sessions = it },
            onError = { e -> sessionSyncError = describeFirestoreError(e) }
        )
        onDispose { stopWatching() }
    }

    val openSession = sessions.firstOrNull {
        it.status == "REQUESTED" || it.status == "ACTIVE"
    }

    // When a session leaves the open set it has reached a final state — fetch
    // it once for the history line. Fires only on the open→closed transition.
    LaunchedEffect(sessions) {
        val currentOpenId = openSession?.id
        val prevOpenId = lastOpenSessionId
        if (prevOpenId != null && prevOpenId != currentOpenId) {
            try {
                lastFinishedSession = getSession(prevOpenId)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // best effort — the history line is informational only
            }
        }
        lastOpenSessionId = currentOpenId
    }

    // Ticks once per second while a session is open so countdowns stay live.
    LaunchedEffect(openSession?.id, openSession?.status) {
        if (openSession != null) {
            while (true) {
                sessionNow = System.currentTimeMillis()
                delay(1000)
            }
        } else {
            sessionNow = System.currentTimeMillis()
        }
    }

    // Lazy server-less expiry: a stale ask turns EXPIRED, an over-time active
    // session turns ENDED("timeout"). Whichever app notices first does it.
    LaunchedEffect(openSession?.id, openSession?.status, sessionNow) {
        val s = openSession ?: return@LaunchedEffect
        when (s.status) {
            "REQUESTED" -> if (sessionNow > s.respondDeadlineMillis + 4000) {
                try {
                    expireSession(s.id)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // best effort — the other side will clean it up
                }
            }
            "ACTIVE" -> if (s.sessionEndsAtMillis > 0 &&
                sessionNow > s.sessionEndsAtMillis + 4000
            ) {
                try {
                    endSession(s.id, user.uid, byTimeout = true)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // best effort
                }
            }
        }
    }

    // Phase 7 — when the session closes (ended/denied/expired) or the app
    // restarts with no session, make sure no screen share survives. Both calls
    // are no-ops when nothing is running.
    LaunchedEffect(openSession == null) {
        if (openSession == null) {
            ScreenShareEngine.stopHelper()
            ScreenShareService.stop(context)
        }
    }

    // v0.8.2 — the helper's "Request sent…" line is sticky in-memory state;
    // once the session turns ACTIVE on the helper's side it is stale (the
    // active card already says everything), so drop it instead of showing a
    // stale ask-line under the live card.
    LaunchedEffect(openSession?.id, openSession?.status) {
        val s = openSession
        if (s != null && s.status == "ACTIVE" && s.helperUid == user.uid &&
            sessionMessage?.startsWith("Request sent") == true
        ) {
            sessionMessage = null
        }
    }

    val startSessionFor: (RelationshipView) -> Unit = { rel ->
        scope.launch {
            sessionBusy = true
            sessionMessage = null
            try {
                requestSession(user, rel)
                sessionMessage =
                    "Request sent — ${rel.recipientEmail}-এর অনুমতির অপেক্ষায়…"
                selectedRelId = null
            } catch (e: SessionException) {
                sessionMessage = e.message
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                sessionMessage = "Could not send the request: ${describeFirestoreError(e)}"
            } finally {
                sessionBusy = false
            }
        }
    }

    val answerSession: (SessionView, Boolean) -> Unit = { s, allow ->
        scope.launch {
            sessionBusy = true
            sessionMessage = null
            try {
                if (allow) {
                    grantSession(s.id, user.uid)
                    sessionMessage =
                        "Session allowed ✓ — ১৫ মিনিট পরে নিজে থেকেই শেষ হবে।"
                } else {
                    denySession(s.id, user.uid)
                    sessionMessage = "Request denied · অনুরোধ না-করা হয়েছে।"
                }
            } catch (e: SessionException) {
                sessionMessage = e.message
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                sessionMessage = "Could not answer the request: ${describeFirestoreError(e)}"
            } finally {
                sessionBusy = false
            }
        }
    }

    val finishSession: (SessionView) -> Unit = { s ->
        scope.launch {
            sessionBusy = true
            try {
                endSession(s.id, user.uid)
                sessionMessage = "Session ended · সেশন শেষ হয়েছে।"
            } catch (e: SessionException) {
                sessionMessage = e.message
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                sessionMessage = "Could not end the session: ${describeFirestoreError(e)}"
            } finally {
                sessionBusy = false
            }
        }
    }

    // Phase 12 — one shared redeem path used by BOTH the typed-code button
    // and the scanned role-setup sheet.
    val doRedeem: (String) -> Unit = { rawCode ->
        scope.launch {
            redeeming = true
            redeemMessage = null
            redeemedKind = null
            try {
                val rel = redeemCode(user, rawCode)
                redeemedKind = rel.kind
                redeemMessage = if (rel.kind == REL_KIND_GUEST) {
                    "Guest যুক্ত ✓ — ${rel.recipientEmail} " +
                        "(${rel.recipientDeviceModel})-কে ২ ঘণ্টার মধ্যে " +
                        "সাহায্য করতে পারবেন।"
                } else {
                    "Paired ✓ — এখন আপনি ${rel.recipientEmail} " +
                        "(${rel.recipientDeviceModel})-কে সাহায্য করতে পারবেন।"
                }
                codeInput = ""
            } catch (e: RedemptionException) {
                redeemMessage = e.message
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                redeemMessage = "Pairing failed: ${describeFirestoreError(e)}"
            } finally {
                redeeming = false
            }
        }
    }

    val currentInvite = invite
    val expired = currentInvite != null &&
        (nowMillis >= currentInvite.expiresAtMillis ||
            (inviteStatus != null && inviteStatus != "PENDING"))
    val showCode = currentInvite != null && !expired

    val createCode: (String) -> Unit = { kind ->
        scope.launch {
            creatingKind = kind
            pairingError = null
            try {
                val deviceId = stableDeviceId(context)
                invite = createInvitation(user, deviceId, Build.MODEL, kind)
                inviteKind = kind
                inviteStatus = "PENDING"
                claimerEmail = null
                inviteSyncError = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                pairingError = "Could not create a code: ${describeFirestoreError(e)}"
            } finally {
                creatingKind = null
            }
        }
    }

    // ---------- ১. লাইভ সেশন: অনুমতি-কার্ড / অপেক্ষা / চলমান ----------
    val currentSession = openSession
    when {
        currentSession != null && currentSession.status == "REQUESTED" &&
            currentSession.recipientUid == user.uid -> {
            // This phone was asked — the prominent consent card.
            HomeConsentCard(session = currentSession, user = user)
        }
        currentSession != null && currentSession.status == "REQUESTED" -> {
            // Helper waiting for the answer.
            val secondsLeft =
                ((currentSession.respondDeadlineMillis - sessionNow) / 1000).coerceAtLeast(0)
            Card {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Send,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "${currentSession.recipientEmail}-কে অনুরোধ গেছে",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = mmss(secondsLeft),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier
                                .background(
                                    MaterialTheme.colorScheme.secondaryContainer,
                                    RoundedCornerShape(999.dp)
                                )
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                    Text(
                        text = "গ্রহীতা Allow করলেই সেশন চালু হবে।",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    WideOutlinedButton(
                        enabled = !sessionBusy,
                        onClick = {
                            scope.launch {
                                sessionBusy = true
                                try {
                                    cancelSession(currentSession.id, user.uid)
                                    sessionMessage = "Request cancelled · অনুরোধ বাতিল।"
                                } catch (e: SessionException) {
                                    sessionMessage = e.message
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    sessionMessage =
                                        "Could not cancel: ${describeFirestoreError(e)}"
                                } finally {
                                    sessionBusy = false
                                }
                            }
                        }
                    ) {
                        Text("অনুরোধ বাতিল · Cancel")
                    }
                }
            }
        }
        currentSession != null && currentSession.status == "ACTIVE" -> {
            // Live session on either side.
            val secondsLeft =
                ((currentSession.sessionEndsAtMillis - sessionNow) / 1000).coerceAtLeast(0)
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = if (currentSession.helperUid == user.uid) {
                                "সেশন চালু — আপনি ${currentSession.recipientEmail}-কে সাহায্য করছেন"
                            } else {
                                "সেশন চালু — ${currentSession.helperEmail} আপনাকে সাহায্য করছেন"
                            },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = mmss(secondsLeft),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier
                                .background(
                                    MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.12f),
                                    RoundedCornerShape(999.dp)
                                )
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                    Text(
                        text = "স্বয়ংক্রিয়ভাবে শেষ হবে। আগে শেষ করতে পারেন।",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                    )
                    ScreenSharePanel(
                        isHelper = currentSession.helperUid == user.uid,
                        sessionId = currentSession.id,
                        myUid = user.uid,
                        otherUid = if (currentSession.helperUid == user.uid) {
                            currentSession.recipientUid
                        } else {
                            currentSession.helperUid
                        }
                    )
                    WideOutlinedButton(
                        enabled = !sessionBusy,
                        onClick = { finishSession(currentSession) }
                    ) {
                        Text("সেশন শেষ করুন · End session")
                    }
                }
            }
        }
        else -> {
            lastFinishedSession?.let { lastFinished ->
                val byWhom = when (lastFinished.endedBy) {
                    null, "" -> ""
                    "timeout" -> " (auto-timeout)"
                    lastFinished.helperUid -> " (ended by helper)"
                    lastFinished.recipientUid -> " (ended by recipient)"
                    else -> ""
                }
                Text(
                    text = "শেষ সেশন: ${lastFinished.status.lowercase()}$byWhom",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    if (sessionSyncError != null) {
        Text(
            text = sessionSyncError!!,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error
        )
    }
    sessionMessage?.let {
        Text(text = it, style = MaterialTheme.typography.bodySmall)
    }
    pairingError?.let {
        Text(
            text = it,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error
        )
    }

    // ---------- ২. চলমান পেয়ারিং-কোড কার্ড (dropdown-সহ) ----------
    if (showCode && currentInvite != null) {
        Card {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // QR + save pipeline states MUST precede the dropdown that
                // uses them (Kotlin order-of-declaration inside a block).
                // Phase 12 — the QR now carries its role badge in the center
                // (👪 Family / ⏳ Guest) and the card accents follow the role.
                val kindIsGuest = inviteKind == REL_KIND_GUEST
                val kindColor = if (kindIsGuest) {
                    AppWarningColor()
                } else {
                    MaterialTheme.colorScheme.tertiary
                }
                val qr = remember(currentInvite.code, inviteKind) {
                    qrBitmap(currentInvite.qrPayload, badge = QrBadge.ofKind(inviteKind))
                }
                var copiedJustNow by remember(currentInvite.invitationId) {
                    mutableStateOf(false)
                }
                var saveBusy by remember(currentInvite.invitationId) { mutableStateOf(false) }
                var saveRequest by remember(currentInvite.invitationId) { mutableStateOf(false) }
                var saveFeedback by remember(currentInvite.invitationId) {
                    mutableStateOf<String?>(null)
                }

                val storagePermissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { granted ->
                    if (granted) {
                        saveRequest = true
                    } else {
                        saveFeedback =
                            "Storage permission is needed to save the QR image."
                    }
                }

                LaunchedEffect(saveRequest) {
                    if (!saveRequest) return@LaunchedEffect
                    saveRequest = false
                    val bitmap = qr
                    if (bitmap == null) return@LaunchedEffect
                    saveBusy = true
                    saveFeedback = withContext(Dispatchers.IO) {
                        val fileName = "pairing-code-${currentInvite.invitationId}.png"
                        saveQrToDownloads(context, bitmap, fileName)
                            ?.let { "Saved ✓ $it" }
                            ?: "Could not save the QR image."
                    }
                    saveBusy = false
                }

                LaunchedEffect(copiedJustNow) {
                    if (copiedJustNow) {
                        delay(2000)
                        copiedJustNow = false
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = if (inviteKind == REL_KIND_GUEST) {
                            Icons.Filled.DateRange
                        } else {
                            Icons.Filled.AccountCircle
                        },
                        contentDescription = null,
                        tint = kindColor,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = if (inviteKind == REL_KIND_GUEST) {
                            "Guest কোড চলছে — ২ ঘণ্টার সাময়িক"
                        } else {
                            "Family কোড চলছে — স্থায়ী"
                        },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    // v0.14.0 — the invite actions moved into a dropdown:
                    // কপি / QR সেভ / শেয়ার।
                    var menuOpen by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(
                                imageVector = Icons.Filled.MoreVert,
                                contentDescription = "আরও অ্যাকশন · more"
                            )
                        }
                        DropdownMenu(
                            expanded = menuOpen,
                            onDismissRequest = { menuOpen = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("কোড কপি · Copy") },
                                onClick = {
                                    menuOpen = false
                                    copyToClipboard(
                                        context, "Pairing code", currentInvite.code
                                    )
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("QR সেভ · Save") },
                                onClick = {
                                    menuOpen = false
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ||
                                        ContextCompat.checkSelfPermission(
                                            context,
                                            Manifest.permission.WRITE_EXTERNAL_STORAGE
                                        ) == PackageManager.PERMISSION_GRANTED
                                    ) {
                                        saveRequest = true
                                    } else {
                                        storagePermissionLauncher.launch(
                                            Manifest.permission.WRITE_EXTERNAL_STORAGE
                                        )
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("শেয়ার · Share") },
                                onClick = {
                                    menuOpen = false
                                    val q = qr
                                    if (q == null) {
                                        saveFeedback =
                                            "Could not prepare the QR image for sharing."
                                    } else {
                                        val intent = buildQrShareIntent(
                                            context, q, currentInvite.code
                                        )
                                        if (intent != null) {
                                            try {
                                                context.startActivity(intent)
                                            } catch (_: Exception) {
                                                saveFeedback = "No app available for sharing."
                                            }
                                        } else {
                                            saveFeedback =
                                                "Could not prepare the QR image for sharing."
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
                Text(
                    text = currentInvite.code,
                    style = MaterialTheme.typography.headlineSmall,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "শুধু যে পরিবারের সদস্য সাহায্য করবেন তাকেই দেবেন।",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
                val totalSeconds = (currentInvite.expiresAtMillis - nowMillis) / 1000
                Text(
                    text = "বাকি সময় ${totalSeconds / 60}:${(totalSeconds % 60)
                        .toString().padStart(2, '0')}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    // Phase 11.6 — the last 30 s also turns warning-colored
                    // (color + the words, never color alone — docs §4.3).
                    color = if (totalSeconds >= 1 && totalSeconds <= 30) {
                        AppWarningColor()
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
                if (qr != null) {
                    // Phase 11.6 — big QR on a white plate: scanning stays
                    // reliable in BOTH themes (docs §4.4).
                    Column(
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .background(Color.White, RoundedCornerShape(16.dp))
                            .padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Image(
                            bitmap = qr.asImageBitmap(),
                            contentDescription = "Pairing QR code",
                            modifier = Modifier.size(200.dp)
                        )
                    }
                }

                saveFeedback?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                }
                // Phase 11.6 — invite state as a badge (color + icon + word).
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    when (inviteStatus) {
                        "PENDING" -> StatusBadge(
                            label = "অন্য ফোনের অপেক্ষায় · Waiting",
                            kind = "info"
                        )
                        "PAIRED" -> StatusBadge(
                            label = "যুক্ত · Paired",
                            kind = "success"
                        )
                        else -> {}
                    }
                }
                if (inviteStatus == "PAIRED") {
                    Text(
                        text = "${claimerEmail ?: "helper"} যুক্ত হয়েছে ✓ — " +
                            if (kindIsGuest) {
                                "Guest · ২ ঘণ্টার সাময়িক যুক্তি"
                            } else {
                                "Family · স্থায়ী যুক্তি"
                            },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                }
                if (inviteStatus == "PENDING") {
                    WideOutlinedButton(
                        enabled = creatingKind == null,
                        onClick = {
                            scope.launch {
                                try {
                                    cancelInvitation(currentInvite.invitationId)
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    pairingError =
                                        "Could not cancel: ${describeFirestoreError(e)}"
                                }
                            }
                        }
                    ) {
                        Text("কোড বাতিল · Cancel code")
                    }
                }
                inviteSyncError?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    } else if (currentInvite != null) {
        val statusText = when (inviteStatus) {
            "PAIRED" -> "যুক্ত ✓ ${claimerEmail ?: "helper"}-এর সাথে — দরকার হলে FAB থেকে নতুন কোড বানান।"
            "CANCELLED" -> "কোড বাতিল হয়েছে। FAB (+) থেকে নতুন কোড বানান।"
            else -> "কোডের সময় শেষ। FAB (+) থেকে নতুন কোড বানান।"
        }
        Text(
            text = statusText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    // ---------- ৩. যুক্ত ফোন — List Tiles + অ্যাকশন নিচ-শিট ----------
    SectionCard(
        title = "যুক্ত ফোন",
        icon = Icons.Filled.Phone,
        infoText = "দুই ধরনের যুক্তি আছে। FAMILY কোড = স্থায়ী বিশ্বস্ত ফোন — একবার যুক্ত " +
            "হলে আর কোড লাগে না। GUEST কোড = ২ ঘণ্টার সাময়িক যুক্তি (বাইরের " +
            "সাহায্যকারীর জন্য) — মেয়াদ শেষে নিজেই মুছে যায়, আর Guest কখনো " +
            "স্বয়ংক্রিয়ভাবে Family হয় না। যুক্ত থাকা মানেই অনুমতি নয় — প্রতিটি সেশনে " +
            "গ্রহীতার ফোনে আলাদা লাইভ অনুমতি লাগে, আর সেশন নির্দিষ্ট সময়ে নিজেই শেষ হয়।"
    ) {
        if (relationships.isEmpty()) {
            if (relationshipsSyncError != null) {
                Text(
                    text = relationshipsSyncError!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            } else {
                EmptyState(
                    icon = Icons.Filled.Phone,
                    title = "এখনো কোনো ফোন যুক্ত হয়নি",
                    hint = "ডানদিকের “+ যুক্ত করুন” বোতামে ট্যাপ করে Family বা Guest কোড " +
                        "বানান, অথবা সাহায্যকারীর কোড/QR দিন।"
                )
            }
        } else {
            Column {
                relationships.forEachIndexed { index, rel ->
                    if (index > 0) HorizontalDivider()
                    PairTile(
                        rel = rel,
                        myUid = user.uid,
                        relNow = relNow,
                        onOpen = { selectedRelId = rel.id }
                    )
                }
            }
        }
    }

    // ---------- ৪. যুক্ত-ফোন অ্যাকশন নিচ-শিট ----------
    val selectedRel = relationships.firstOrNull { it.id == selectedRelId }
    if (selectedRel != null) {
        val isGuest = selectedRel.kind == REL_KIND_GUEST
        val iHelp = selectedRel.helperUid == user.uid
        val otherEmail =
            (if (iHelp) selectedRel.recipientEmail else selectedRel.helperEmail)
                .ifBlank { "(no email)" }
        val sheetState = rememberModalBottomSheetState()
        ModalBottomSheet(
            onDismissRequest = { selectedRelId = null },
            sheetState = sheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = otherEmail,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = buildString {
                        append(if (iHelp) "আপনি সাহায্য করছেন" else "আপনাকে সাহায্য করছে")
                        if (selectedRel.recipientDeviceModel.isNotBlank()) {
                            append(" · ${selectedRel.recipientDeviceModel}")
                        }
                        append(" · ${sessionStatusBn(selectedRel.status)}")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (isGuest) {
                    val remainingMin =
                        ((selectedRel.guestExpiresAtMillis - relNow) / 60000L).coerceAtLeast(0)
                    Text(
                        text = when {
                            selectedRel.status != "ACTIVE" -> "Guest যুক্তি — মেয়াদ শেষ।"
                            selectedRel.guestExpiresAtMillis <= 0L -> "Guest (সাময়িক)"
                            remainingMin >= 60L ->
                                "Guest — বাকি ${remainingMin / 60L}ঘ ${remainingMin % 60L}মি"
                            else -> "Guest — বাকি ${remainingMin}মি"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (selectedRel.status == "ACTIVE" && remainingMin > 0L) {
                            MaterialTheme.colorScheme.tertiary
                        } else {
                            MaterialTheme.colorScheme.error
                        }
                    )
                }
                if (iHelp && selectedRel.recipientDeviceId.isNotBlank()) {
                    PresenceBadge(
                        uid = selectedRel.recipientUid,
                        deviceId = selectedRel.recipientDeviceId
                    )
                }
                HorizontalDivider()
                // Phase 12 — the role is a first-class, visible thing.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = if (isGuest) {
                            Icons.Filled.DateRange
                        } else {
                            Icons.Filled.Home
                        },
                        contentDescription = null,
                        tint = if (isGuest) {
                            AppWarningColor()
                        } else {
                            MaterialTheme.colorScheme.tertiary
                        },
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = if (isGuest) {
                            "রোল: GUEST — সাময়িক (মেয়াদ শেষে নিজেই মুছে যায়)"
                        } else {
                            "রোল: FAMILY — স্থায়ী বিশ্বস্ত ফোন"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isGuest) {
                            AppWarningColor()
                        } else {
                            MaterialTheme.colorScheme.tertiary
                        }
                    )
                }
                if (selectedRel.status == "ACTIVE" && iHelp && !selectedRel.guestLive) {
                    Text(
                        text = "Guest মেয়াদ শেষ — আর সেশন শুরু করা যাবে না। নতুন " +
                            "Guest কোড দিন।",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                if (selectedRel.status == "ACTIVE" && iHelp && selectedRel.guestLive) {
                    BigButton(
                        enabled = !sessionBusy && openSession == null,
                        onClick = { startSessionFor(selectedRel) }
                    ) {
                        Text("সেশন শুরু করুন · অনুমতি চাইবে")
                    }
                    if (openSession != null) {
                        Text(
                            text = "একটি সেশন ইতিমধ্যে চলছে — আগে সেটি শেষ করুন।",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (!isGuest && selectedRel.status == "ACTIVE") {
                    OutlinedButton(
                        enabled = revokingId != selectedRel.id,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp),
                        onClick = {
                            revokingId = selectedRel.id
                            scope.launch {
                                try {
                                    revokeRelationship(selectedRel.id)
                                    selectedRelId = null
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    pairingError =
                                        "Could not revoke: ${describeFirestoreError(e)}"
                                } finally {
                                    revokingId = null
                                }
                            }
                        }
                    ) {
                        Text("Revoke · বাতিল")
                    }
                }
                // Phase 10 — a guest link can be REMOVED entirely (the doc
                // is deleted; afterwards a fresh family pairing may reuse
                // the pair). Family links have no such button on purpose.
                if (isGuest) {
                    OutlinedButton(
                        enabled = revokingId != selectedRel.id,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp),
                        onClick = {
                            revokingId = selectedRel.id
                            scope.launch {
                                try {
                                    removeGuestRelationship(selectedRel.id)
                                    selectedRelId = null
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    pairingError =
                                        "Could not remove: ${describeFirestoreError(e)}"
                                } finally {
                                    revokingId = null
                                }
                            }
                        }
                    ) {
                        Text("Remove · সরান")
                    }
                }
            }
        }
    }

    // ---------- ৫. FAB-এর পেয়ারিং নিচ-শিট ----------
    if (showPairSheet) {
        val pairSheetState = rememberModalBottomSheetState()
        ModalBottomSheet(
            onDismissRequest = onDismissPairSheet,
            sheetState = pairSheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    // Phase 11.6 — the code field never hides behind the
                    // keyboard (docs §16).
                    .imePadding()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "নতুন ফোন যুক্ত করুন",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "এই ফোনে সাহায্য দরকার? কোড বানিয়ে অন্য ফোনকে দিন।",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                PairOptionRow(
                    icon = Icons.Filled.AccountCircle,
                    title = "Family কোড · স্থায়ী",
                    description = "একবার যুক্ত হলে আর কোড লাগে না — শুধু পরিবারের জন্য",
                    busy = creatingKind == REL_KIND_FAMILY,
                    enabled = creatingKind == null,
                    onClick = {
                        createCode(REL_KIND_FAMILY)
                        onDismissPairSheet()
                    }
                )
                PairOptionRow(
                    icon = Icons.Filled.DateRange,
                    title = "Guest কোড · ২ ঘণ্টা",
                    description = "বাইরের সাহায্যকারীর জন্য — মেয়াদ শেষে নিজেই মুছে যায়",
                    busy = creatingKind == REL_KIND_GUEST,
                    enabled = creatingKind == null,
                    onClick = {
                        createCode(REL_KIND_GUEST)
                        onDismissPairSheet()
                    }
                )
                HorizontalDivider()
                Text(
                    text = "অন্য কারও ফোনে সাহায্য করবেন? তাদের কোড দিন",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "QR-এর মাঝের প্রতীক রোল বলে দেয় — 👪 Family (স্থায়ী) · " +
                        "⏳ Guest (২ ঘণ্টা)। স্ক্যানের পরেই রোল-নিশ্চিতকরণ দেখাবে।",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = codeInput,
                        onValueChange = { codeInput = it },
                        label = { Text("Pairing code · কোড") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        enabled = !redeeming,
                        onClick = { showScanner = true },
                        modifier = Modifier.heightIn(min = 52.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Text("  স্ক্যান")
                    }
                }
                if (showScanner) {
                    QrScanDialog(
                        onCode = { code, kindHint ->
                            codeInput = code
                            redeemMessage = null
                            redeemedKind = null
                            showScanner = false
                            // Phase 12 — scan flows straight into the
                            // role-setup sheet (preview → confirm → auto-setup).
                            onDismissPairSheet()
                            pendingScan = ScannedCode(code, kindHint)
                        },
                        onDismiss = { showScanner = false }
                    )
                }
                BigButton(
                    enabled = !redeeming && codeInput.isNotBlank(),
                    onClick = { doRedeem(codeInput) }
                ) {
                    if (redeeming) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Text("  কোড দিয়ে যুক্ত করুন")
                    }
                }
                redeemMessage?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (message.startsWith("Paired") ||
                            message.startsWith("Guest যুক্ত")
                        ) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        }
                    )
                }
                Text(
                    text = "যুক্ত থাকা মানেই অনুমতি নয় — প্রতিটি সেশনে আলাদা অনুমতি লাগবেই।",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    // ---------- ৬. Phase 12 — স্ক্যান-পরবর্তী রোল-সেটআপ নিচ-শিট ----------
    pendingScan?.let { scan ->
        ScannedRoleSheet(
            scan = scan,
            redeeming = redeeming,
            message = redeemMessage,
            redeemedKind = redeemedKind,
            onConfirm = { doRedeem(scan.code) },
            onDismiss = { pendingScan = null }
        )
    }
}

/** Phase 12 — a scanned pairing code plus the role marker read from its QR. */
private data class ScannedCode(val code: String, val kindHint: String?)

/**
 * Phase 12 — the role-setup sheet that opens the moment a QR is scanned:
 *  1. PREVIEW — what role this code will give (from the &k= marker when the
 *     QR carries it), with the plain-language permission list.
 *  2. CONFIRM — one big "যুক্ত হোন" button; nothing is written until tapped.
 *  3. AUTO-SETUP — the redemption runs and the sheet flips to a success card
 *     with the ACTUAL role (re-read from the Firestore invitation inside the
 *     transaction — the marker on the QR is cosmetic, the server decides).
 * The whole journey happens in one sheet: scan → preview → confirm → paired.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScannedRoleSheet(
    scan: ScannedCode,
    redeeming: Boolean,
    message: String?,
    redeemedKind: String?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "রোল সেটআপ · Role setup",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = scan.code,
                style = MaterialTheme.typography.labelMedium,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            when {
                redeeming -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator()
                        Text(
                            text = "যুক্ত করা হচ্ছে — সার্ভার রোল যাচাই করছে…",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                redeemedKind != null -> {
                    // SUCCESS — the automatic setup is done.
                    val guest = redeemedKind == REL_KIND_GUEST
                    val accent = if (guest) {
                        AppWarningColor()
                    } else {
                        MaterialTheme.colorScheme.tertiary
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        StatusBadge(
                            label = if (guest) "GUEST · ২ ঘণ্টার সাময়িক" else "FAMILY · স্থায়ী",
                            kind = "success"
                        )
                    }
                    Text(
                        text = "যুক্ত হয়েছে ✓ — এখন “যুক্ত ফোন” তালিকায় এই ফোনটি দেখা যাবে; " +
                            "সেশন শুরু করতে ট্যাপ করুন।",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    ScannedRoleBullet(
                        accent = accent,
                        text = "প্রতিটি সেশনের আগে গ্রহীতার ফোনে লাইভ অনুমতি লাগবেই"
                    )
                    if (guest) {
                        ScannedRoleBullet(
                            accent = accent,
                            text = "২ ঘণ্টা পর যুক্তি নিজেই মুছে যাবে — Guest কখনো Family হয় না"
                        )
                    } else {
                        ScannedRoleBullet(
                            accent = accent,
                            text = "স্থায়ী যুক্তি — পরের বার কোড ছাড়াই সেশন চাওয়া যাবে"
                        )
                    }
                    BigButton(onClick = onDismiss) {
                        Text("ঠিক আছে")
                    }
                }
                else -> {
                    // PREVIEW — nothing written to Firestore until confirmed.
                    val known = scan.kindHint != null
                    val guestHint = scan.kindHint == REL_KIND_GUEST
                    val accent = if (guestHint) {
                        AppWarningColor()
                    } else {
                        MaterialTheme.colorScheme.tertiary
                    }
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = accent.copy(alpha = 0.08f)
                        ),
                        border = BorderStroke(1.dp, accent.copy(alpha = 0.35f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = if (guestHint) {
                                        Icons.Filled.DateRange
                                    } else {
                                        Icons.Filled.Home
                                    },
                                    contentDescription = null,
                                    tint = accent,
                                    modifier = Modifier.size(26.dp)
                                )
                                Text(
                                    text = when {
                                        !known -> "কোড যাচাই করা হবে"
                                        guestHint -> "এই কোড আপনাকে GUEST করবে"
                                        else -> "এই কোড আপনাকে FAMILY করবে"
                                    },
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = accent
                                )
                            }
                            Text(
                                text = when {
                                    !known -> "এটি পুরনো ফরম্যাটের কোড (রোল-প্রতীক নেই) — " +
                                        "যুক্ত হওয়ার পরে প্রকৃত রোল দেখা যাবে।"
                                    guestHint -> "২ ঘণ্টার সাময়িক যুক্তি — মেয়াদ শেষে নিজেই " +
                                        "মুছে যাবে; Guest কখনো Family হয় না।"
                                    else -> "স্থায়ী বিশ্বস্ত যুক্তি — একবার যুক্ত হলে আর " +
                                        "কোড লাগবে না (বাতিল না করা পর্যন্ত)।"
                                },
                                style = MaterialTheme.typography.bodySmall
                            )
                            HorizontalDivider()
                            ScannedRoleBullet(
                                accent = accent,
                                text = "সেশন শুরুর অনুরোধ পাঠানো যাবে (যুক্ত ফোন তালিকা)"
                            )
                            ScannedRoleBullet(
                                accent = accent,
                                text = "প্রতিটি সেশনে গ্রহীতার ফোনে লাইভ অনুমতি লাগবেই"
                            )
                            ScannedRoleBullet(
                                accent = accent,
                                text = "সেশন নির্দিষ্ট সময়ে নিজেই শেষ হয়"
                            )
                        }
                    }
                    // Phase 12 — a failed redeem comes back here honestly:
                    // the server's refusal shows INSIDE the sheet.
                    if (message != null) {
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    BigButton(
                        enabled = !redeeming,
                        onClick = onConfirm
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Text("  যুক্ত হোন · Connect")
                    }
                    Text(
                        text = "বাতিল করলে কিছুই লেখা হবে না — অন্য ফোনের কোড অক্ষত থাকবে।",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/** One check-mark bullet line inside the role sheet. */
@Composable
private fun ScannedRoleBullet(accent: Color, text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = Icons.Filled.Check,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(14.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

/** v0.14.0 — one tappable option row inside the pairing bottom sheet. */
@Composable
private fun PairOptionRow(
    icon: ImageVector,
    title: String,
    description: String,
    busy: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(
                        MaterialTheme.colorScheme.primaryContainer,
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(24.dp)
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (busy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/**
 * v0.14.0 — a paired phone as a chat-style tile: avatar + email + role +
 * Family/Guest badge (+ guest countdown), optional live presence underneath.
 * Tapping opens the action bottom-sheet.
 */
@Composable
private fun PairTile(
    rel: RelationshipView,
    myUid: String,
    relNow: Long,
    onOpen: () -> Unit
) {
    val isGuest = rel.kind == REL_KIND_GUEST
    val iHelp = rel.helperUid == myUid
    val otherEmail =
        (if (iHelp) rel.recipientEmail else rel.helperEmail).ifBlank { "(no email)" }
    val guestOk = isGuest && rel.status == "ACTIVE" && rel.guestLive
    // Phase 11.6 — minutes until the guest relationship expires (for the
    // warning-colored chip when it is nearly over).
    val guestMinutesLeft = ((rel.guestExpiresAtMillis - relNow) / 60000L).coerceAtLeast(0)
    val chipText = when {
        !isGuest && rel.status == "ACTIVE" -> "Family"
        !isGuest -> "Family · ${sessionStatusBn(rel.status)}"
        guestOk -> {
            val remainingMin =
                ((rel.guestExpiresAtMillis - relNow) / 60000L).coerceAtLeast(0)
            when {
                rel.guestExpiresAtMillis <= 0L -> "Guest"
                remainingMin >= 60L -> "Guest · ${remainingMin / 60L}ঘ${remainingMin % 60L}মি"
                else -> "Guest · ${remainingMin}মি"
            }
        }
        else -> "Guest · ${sessionStatusBn(rel.status)}"
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        AppListTile(
            title = otherEmail,
            initials = otherEmail.take(1).ifBlank { "?" },
            subtitle = buildString {
                append(if (iHelp) "আপনি সাহায্য করছেন" else "আপনাকে সাহায্য করছে")
                if (rel.recipientDeviceModel.isNotBlank()) {
                    append(" · ${rel.recipientDeviceModel}")
                }
            },
            chip = chipText,
            chipContainer = when {
                rel.status != "ACTIVE" -> MaterialTheme.colorScheme.errorContainer
                // Phase 11.6 — guest countdown turns warning-colored when it
                // is nearly over (<15 min); the chip text still says the time.
                guestOk && guestMinutesLeft >= 1 && guestMinutesLeft <= 14 ->
                    AppWarningColor().copy(alpha = 0.18f)
                guestOk -> MaterialTheme.colorScheme.tertiaryContainer
                else -> MaterialTheme.colorScheme.primaryContainer
            },
            chipContent = when {
                rel.status != "ACTIVE" -> MaterialTheme.colorScheme.onErrorContainer
                guestOk && guestMinutesLeft >= 1 && guestMinutesLeft <= 14 ->
                    AppWarningColor()
                guestOk -> MaterialTheme.colorScheme.onTertiaryContainer
                else -> MaterialTheme.colorScheme.onPrimaryContainer
            },
            onClick = onOpen,
            trailingIcon = Icons.Filled.KeyboardArrowRight
        )
        if (iHelp && rel.recipientDeviceId.isNotBlank()) {
            Box(modifier = Modifier.padding(start = 56.dp)) {
                PresenceBadge(uid = rel.recipientUid, deviceId = rel.recipientDeviceId)
            }
        }
        if (isGuest && rel.status == "ACTIVE" && !rel.guestLive) {
            Text(
                text = "Guest মেয়াদ শেষ — নতুন Guest কোড দিন।",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(start = 56.dp)
            )
        }
    }
}

/** "M:SS" countdown text used by the session cards. */
private fun mmss(seconds: Long): String = "%d:%02d".format(seconds / 60, seconds % 60)

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
@Composable
private fun ScreenSharePanel(
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
                Text("স্ক্রিন দেখুন · Watch screen")
            }
        } else {
            // v0.8.3 — the picture is now sized to the REAL screen aspect
            // ratio (the engine publishes the decoded frame size), capped so
            // it always fits, and tapping it opens a full-screen viewer. The
            // old fixed 220 dp box is what made the shared screen look tiny.
            val egl = ScreenShareEngine.eglBaseContext
            val frameSize by ScreenShareEngine.frameSize.collectAsState()
            val assistStatus by ScreenShareEngine.assistStatus.collectAsState()
            var fullscreen by remember { mutableStateOf(false) }
            // Phase 8 — when on, taps on the picture are delivered to the
            // recipient's phone instead of opening the local full-screen view.
            var assistMode by remember { mutableStateOf(false) }
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
                if (fullscreen) {
                    Dialog(
                        onDismissRequest = { fullscreen = false },
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
                                    text = "REMOTE TOUCH ON · টাচ চালু",
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
                            // Phase 9 (T3) — the old 16dp "✕ Close" text was
                            // far below a 44dp touch target; a real button now.
                            Button(
                                onClick = { fullscreen = false },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(12.dp)
                                    .heightIn(min = 48.dp)
                            ) {
                                Text("✕ বন্ধ", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                } else {
                    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                        val targetHeight = frameSize?.let { (w, h) ->
                            if (h > 0) maxWidth * (h.toFloat() / w.toFloat()) else 420.dp
                        } ?: 420.dp
                        Box {
                            VideoRendererBox(
                                egl,
                                Modifier
                                    .fillMaxWidth()
                                    .height(targetHeight.coerceAtMost(480.dp))
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
                                        else Modifier.clickable { fullscreen = true }
                                    )
                            )
                            if (assistLive) {
                                Text(
                                    text = "REMOTE TOUCH ON · টাচ চালু",
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
                            "টাচ চালু — ছবিতে ট্যাপ মানে তাদের ফোনে ট্যাপ।"
                        else
                            "ছবিতে ট্যাপ করলে ফুলস্ক্রিন হবে।",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            // v0.12.0 (Phase 8.3 completion) — DATA SAVER. The helper caps
            // the stream's bitrate mid-session (600 kbps) or restores the
            // normal budget, without restarting anything. It rides the same
            // epoch-gated command channel as the touch commands and gets the
            // same truthful ack, so the switch can never silently lie.
            var dataSaver by remember { mutableStateOf(false) }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "ছবি হালকা · Data saver",
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
                    "চালু — ছবি ৬০০ kbps-এ ক্যাপ: ডেটা বাঁচবে, ছবি একটু ঝাপসা হবে।"
                else
                    "বন্ধ — স্বাভাবিক মান।",
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
                    text = "রিমোট টাচ · Touch control",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Switch(checked = assistMode, onCheckedChange = { assistMode = it })
            }
            Text(
                text = if (assistMode)
                    "চালু — ট্যাপ = ট্যাপ · ধরে রাখা = লং-প্রেস · টেনে ছাড়া = সোয়াইপ।"
                else
                    "বন্ধ — শুধু দেখা যাচ্ছে।",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (assistMode) {
                // v0.12.0 (Phase 8.3 completion) — TYPING lives behind one
                // button and one honest dialog (state survives recomposition
                // while the switch is on; it dies with the session naturally).
                var showTypeDialog by remember { mutableStateOf(false) }
                if (showTypeDialog) {
                    AssistTypeDialog(onDismiss = { showTypeDialog = false })
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { ScreenShareEngine.sendAssistNav("back") },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                    ) {
                        Text("◀ Back")
                    }
                    OutlinedButton(
                        onClick = { ScreenShareEngine.sendAssistNav("home") },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                    ) {
                        Text("⌂ Home")
                    }
                    OutlinedButton(
                        onClick = { ScreenShareEngine.sendAssistNav("recents") },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                    ) {
                        Text("▦ Recents")
                    }
                }
                // v0.12.0 (Phase 8.3 completion) — the typing entry point.
                WideOutlinedButton(onClick = { showTypeDialog = true }) {
                    Text("⌨ লেখা টাইপ করুন · Type text")
                }
                WideOutlinedButton(onClick = { fullscreen = true }) {
                    Text("⛶ ফুলস্ক্রিন · Full screen")
                }
            }
            assistStatus?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium
                )
            }
            WideOutlinedButton(onClick = { ScreenShareEngine.stopHelper() }) {
                Text("দেখা বন্ধ · Stop watching")
            }
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
                Text("স্ক্রিন শেয়ার করুন · Share screen")
            }
            Text(
                text = "চালুর পর এই ফোনে যা দেখা যায় সবই সাহায্যকারী দেখতে পাবেন — আপনি বন্ধ " +
                    "করা বা সেশন শেষ হওয়া পর্যন্ত।",
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
                    text = "প্রিভিউ — সাহায্যকারী এটাই দেখছেন:",
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
                Text("শেয়ার বন্ধ · Stop sharing")
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
                    text = "Remote assist চালু ✓ — সাহায্যকারী ট্যাপ, সোয়াইপ ও টাইপ করতে পারবেন।",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            } else {
                Text(
                    text = "Remote assist বন্ধ — সাহায্যকারী শুধু দেখতে পারবেন।",
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
                            text = "⚠️ সাহায্যকারী এখনই ট্যাপ করছেন — কিন্তু Remote assist বন্ধ " +
                                "থাকায় সেগুলো কাজ করছে না! নিচের বাটনে ট্যাপ করে এখনই চালু করুন।",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
                WideOutlinedButton(onClick = { showAssistGuide = true }) {
                    Text("রিমোট টাচ চালু করুন · Turn on Remote assist")
                }
                Text(
                    text = "একবারই সেটআপ লাগবে: তালিকায় \"Family Remote Support assist\" খুঁজে চালু করুন। " +
                        "⚠️ \"Accessibility menu shortcut\" নয় — সেটা কাজ করবে না।",
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
                    text = "ওদের ফোনে লিখুন · Type on their phone",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "আগে রিমোট টাচ দিয়ে ওদের ফোনে লেখার ঘরে ট্যাপ করে নিন। " +
                        "লেখাটি ঘরের আগের লেখা বদলে বসবে।",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { if (it.length <= 500) text = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("লেখা লিখুন…") },
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
                        Text("বাতিল")
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
                        Text("পাঠান · Send")
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

/**
 * Phase 5 — live presence dot for one device (doc devicePresence/{uid}_{deviceId}).
 * Shows "Online" while the heartbeat is fresh, "Last seen … ago" up to an hour,
 * then "Offline". Refreshes the age every 10 seconds.
 */
@Composable
fun PresenceBadge(uid: String, deviceId: String) {
    var heartbeatAt by remember(uid, deviceId) { mutableStateOf<Long?>(null) }
    var unavailable by remember(uid, deviceId) { mutableStateOf(false) }
    var nowMillis by remember { mutableStateOf(System.currentTimeMillis()) }

    LaunchedEffect(uid, deviceId) {
        while (true) {
            nowMillis = System.currentTimeMillis()
            delay(10_000)
        }
    }
    DisposableEffect(uid, deviceId) {
        val stopWatching = watchDevicePresence(
            uid,
            deviceId,
            onChange = { view ->
                unavailable = false
                heartbeatAt = view?.heartbeatAtMillis
            },
            onError = { unavailable = true }
        )
        onDispose { stopWatching() }
    }

    val label = when {
        unavailable -> "presence unavailable"
        heartbeatAt == null || heartbeatAt == 0L -> "never seen"
        else -> {
            val age = nowMillis - heartbeatAt!!
            when {
                age < 0 -> "Online"
                age < PRESENCE_ONLINE_MILLIS -> "Online"
                age < PRESENCE_STALE_MILLIS -> "last seen ${age / 60000} min ago"
                else -> "offline"
            }
        }
    }
    // Phase 11 — no leading " · ": the badge now always sits on its own
    // line (relationship rows and the activity feed), not appended inline.
    Text(
        text = label,
        style = MaterialTheme.typography.bodySmall,
        color = when {
            label == "Online" -> MaterialTheme.colorScheme.primary
            unavailable -> MaterialTheme.colorScheme.error
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        fontWeight = FontWeight.Medium
    )
}
