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
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
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
import androidx.compose.foundation.ScrollState
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
import com.family.remote.support.ui.AppWarningColor
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
                                contentDescription = stringResource(R.string.cd_details),
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
                TextButton(onClick = { showInfo = false }) {
                    Text(stringResource(R.string.cmn_ok))
                }
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
    var homeSessions by remember { mutableStateOf<List<SessionView>>(emptyList()) }
    var homeSessionsLoaded by remember { mutableStateOf(false) }
    DisposableEffect(signedInUser?.uid) {
        val user = signedInUser
        if (user == null) {
            homeSessions = emptyList()
            homeSessionsLoaded = false
            onDispose { }
        } else {
            val stop = watchSessions(
                user,
                onUpdate = {
                    homeSessionsLoaded = true
                    homeSessions = it
                },
                // v0.21.0 (audit): KEEP the last list on a transient listener
                // error — clearing it here used to unmount the consent modal
                // mid-ask and re-prompt the same session after reconnect.
                onError = { }
            )
            onDispose { stop() }
        }
    }
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
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
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
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
                presenceStatus = context.getString(R.string.presence_failed, friendlyError(context, e))
        }
        try {
            registerFcmToken(user.uid, deviceId)
            pushStatus = "Push notifications ready ✓"
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
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
    Scaffold(
        topBar = {
            AppTopBar(
                showPresence = signedInUser != null && ownDeviceId.isNotBlank(),
                presenceUid = signedInUser?.uid,
                presenceDeviceId = ownDeviceId,
                pendingAsk = pendingAsk,
                // Phase 16 — the bell opens Settings, where the merged
                // Activity section (sessions + paired phones) now lives.
                onBellClick = { tab = 3 }
            )
        },
        bottomBar = {
            Box(modifier = Modifier.onSizeChanged { bottomNavHeightPx = it.height }) {
                AppBottomNav(tab = tab, onSelect = { tab = it })
            }
        }
        // Phase 16 — the right-side "যুক্ত করুন" FAB is GONE (Owner: the
        // add-phone action showed up in TWO places — the permanent top card
        // on the phone tab already covers both directions, and the FAB even
        // covered the Full-screen button mid-scroll). One place, one truth.
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
                    permSummary = permSummary,
                    openSession = openSession,
                    onOpenWizard = { showWizard = true },
                    onRetryRegistration = { registerRetryKey++ },
                    onGoPairing = { tab = 1 },
                    busy = busy,
                    onSignIn = startSignIn
                )
                1 -> PairingCard(
                    signedInUser = signedInUser,
                    showPairSheet = showPairSheet,
                    onDismissPairSheet = { showPairSheet = false },
                    onOpenPairSheet = { showPairSheet = true }
                )
                // Phase 16 — the old একটিভিটি tab became the LIVE LOCATION
                // tab (Owner: "একটিভিটি ট্যাব টি মোছে দিয়ে live location নামে
                // একটা ট্যাব ওপেন করেন; এটার কাজ ভবিষ্যতে করবো") — an honest
                // placeholder, nothing collected. The Activity content moved
                // INTO Settings (tab 3), as requested.
                2 -> LiveLocationTab()
                else -> SettingsTab(
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
                    onOpenWizard = { showWizard = true }
                )
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
 * Phase 11 — bottom navigation. Phase 16 (v0.20.0) — the tabs are now
 * হোম · ফোন · লাইভ লোকেশন · সেটিংস (Owner: the একটিভিটি tab is gone —
 * its content merged into Settings; the Live-location placeholder takes
 * the slot). Labels come from the language resources (15 languages).
 */
@Composable
private fun AppBottomNav(tab: Int, onSelect: (Int) -> Unit) {
    NavigationBar {
        NavigationBarItem(
            selected = tab == 0,
            onClick = { onSelect(0) },
            icon = { Icon(Icons.Filled.Home, contentDescription = null) },
            label = { Text(stringResource(R.string.nav_home)) }
        )
        NavigationBarItem(
            selected = tab == 1,
            onClick = { onSelect(1) },
            icon = { Icon(Icons.Filled.Phone, contentDescription = null) },
            label = { Text(stringResource(R.string.nav_phone)) }
        )
        NavigationBarItem(
            selected = tab == 2,
            onClick = { onSelect(2) },
            icon = { Icon(Icons.Filled.LocationOn, contentDescription = null) },
            label = { Text(stringResource(R.string.nav_location)) }
        )
        NavigationBarItem(
            selected = tab == 3,
            onClick = { onSelect(3) },
            icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
            label = { Text(stringResource(R.string.nav_settings)) }
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
        unavailable -> stringResource(R.string.pres_checking)
        online -> stringResource(R.string.pres_online)
        heartbeatAt == null || heartbeatAt == 0L -> "…"
        else -> stringResource(R.string.pres_offline)
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
    permSummary: PermChecklist,
    openSession: SessionView?,
    onOpenWizard: () -> Unit,
    onRetryRegistration: () -> Unit,
    onGoPairing: () -> Unit,
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
    // Phase 15 (v0.19.0) — the approval ask surfaces as a CENTERED modal at
    // the ROOT level (any tab), in front of the eyes (Owner report: popups
    // must never hide below the fold). Dismissing it decides nothing — the
    // card stays on home. Rendered app-wide in the root Box below.

    // ── Phase 16 — HOME REDESIGN (Owner: "হোম ট্যাব ভিজিট করলেই সে অ্যাপ
    // সম্পর্কে পুরোপুরি ধারণা নিয়ে নেবে… আকর্ষণীয় করেন… কিছু অপশন সেটিংসে
    // দিন")। The home tab is now an app SHOWCASE:
    //   ১. Hero welcome card (who you are + the one-line promise)
    //   ২. "What this app can do" feature grid (the app's real features)
    //   ৩. One compact setup strip (details moved to Settings)
    // The old 2×2 technical status grid, the long device list and the
    // quick-action tiles moved to Settings — home stays light and clear.

    Text(
        text = stringResource(R.string.home_tagline),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    // ১. Hero welcome card — who you are + the one-line promise.
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.12f),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_auth_hero),
                    contentDescription = null,
                    modifier = Modifier.size(38.dp)
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = stringResource(R.string.home_welcome),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                )
                Text(
                    text = signedInUser?.let {
                        stringResource(
                            R.string.home_signed_in_as,
                            it.displayName ?: it.email ?: ""
                        )
                    } ?: stringResource(R.string.home_not_signed_in),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = stringResource(R.string.home_tagline),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                )
            }
        }
        if (signedInUser == null && webClientAvailable()) {
            BigButton(enabled = !busy, onClick = onSignIn) {
                if (busy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(stringResource(R.string.auth_btn_login))
                }
            }
        }
    }

    // ২. "What this app can do" — the feature grid. Each tile opens an
    // honest explainer; the pairing tiles also offer the phone tab.
    Text(
        text = stringResource(R.string.home_features),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(modifier = Modifier.weight(1f)) {
            FeatureTile(
                icon = Icons.Filled.PlayArrow,
                title = stringResource(R.string.feat_screen_t),
                desc = stringResource(R.string.feat_screen_d),
                actionLabel = null
            )
        }
        Box(modifier = Modifier.weight(1f)) {
            FeatureTile(
                icon = Icons.Filled.Edit,
                title = stringResource(R.string.feat_touch_t),
                desc = stringResource(R.string.feat_touch_d),
                actionLabel = null
            )
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(modifier = Modifier.weight(1f)) {
            FeatureTile(
                icon = Icons.Filled.Favorite,
                title = stringResource(R.string.feat_family_t),
                desc = stringResource(R.string.feat_family_d),
                actionLabel = stringResource(R.string.nav_phone),
                onAction = onGoPairing
            )
        }
        Box(modifier = Modifier.weight(1f)) {
            FeatureTile(
                icon = Icons.Filled.Person,
                title = stringResource(R.string.feat_guest_t),
                desc = stringResource(R.string.feat_guest_d),
                actionLabel = stringResource(R.string.nav_phone),
                onAction = onGoPairing
            )
        }
    }

    // ৩. One compact setup strip (the technical 2×2 grid moved to Settings).
    val registered = deviceStatus.startsWith("Registered")
    val regFailed = deviceStatus.startsWith("Registration failed")
    val regBusy = deviceStatus.startsWith("Registering")
    val presenceOk = presenceStatus.startsWith("Online")
    val pushOk = pushStatus.startsWith("Push notifications ready")
    val allGood = signedInUser != null && permSummary.allDone &&
        registered && presenceOk && pushOk
    Card {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpenWizard)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .background(
                        color = if (allGood) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.errorContainer
                        },
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (allGood) {
                        Icons.Filled.CheckCircle
                    } else {
                        Icons.Filled.Lock
                    },
                    contentDescription = null,
                    tint = if (allGood) {
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
                    text = if (allGood) {
                        stringResource(R.string.home_setup_ok)
                    } else {
                        stringResource(R.string.home_setup_pending, permSummary.doneCount)
                    },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (allGood) {
                        stringResource(R.string.home_setup_ok_sub)
                    } else {
                        stringResource(R.string.home_setup_pending_sub)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }

    // Any real setup failure still gets its own honest compact card.
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
                    text = stringResource(R.string.home_setup_problem),
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
                    Text(stringResource(R.string.home_retry))
                }
            }
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

/** True when the build actually has a Google Web Client ID for sign-in. */
private fun webClientAvailable(): Boolean =
    try {
        com.family.remote.support.BuildConfig.GOOGLE_WEB_CLIENT_ID.isNotBlank()
    } catch (_: Exception) {
        false
    }

/**
 * Phase 16 — one feature tile of the home showcase grid: icon circle +
 * title + 2-line description, optional action button. Opens an honest
 * explainer dialog on tap (no fake navigation anywhere).
 */
@Composable
private fun FeatureTile(
    icon: ImageVector,
    title: String,
    desc: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    var showInfo by remember { mutableStateOf(false) }
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showInfo = true }
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(
                        MaterialTheme.colorScheme.tertiaryContainer,
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.size(20.dp)
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = desc,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            if (actionLabel != null && onAction != null) {
                WideOutlinedButton(onClick = onAction) { Text(actionLabel) }
            }
        }
    }
    if (showInfo) {
        AlertDialog(
            onDismissRequest = { showInfo = false },
            confirmButton = {
                TextButton(onClick = { showInfo = false }) {
                    Text(stringResource(R.string.cmn_ok))
                }
            },
            title = { Text(title, fontWeight = FontWeight.Bold) },
            text = { Text(desc) }
        )
    }
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
                        stringResource(R.string.hint_requested, session.recipientEmail)
                    session.helperUid == myUid ->
                        stringResource(R.string.hint_active, session.recipientEmail)
                    else ->
                        stringResource(R.string.hint_helping, session.helperEmail.ifBlank { "Someone" })
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
    val context = LocalContext.current

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
                        label = stringResource(R.string.cons_label),
                    kind = "info"
                )
            }
            Text(
                    text = stringResource(R.string.cons_body, session.helperEmail.ifBlank { "Someone" }),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            if (session.kind == "guest") {
                Text(
                        text = stringResource(R.string.cons_guest_note),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
            val secondsLeft =
                ((session.respondDeadlineMillis - nowMillis) / 1000).coerceAtLeast(0)
            Text(
                    text = stringResource(R.string.cons_expiry, mmss(secondsLeft)),
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
                                    message = context.getString(R.string.msg_allow_ok)
                            } catch (e: SessionException) {
                                message = e.message
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                    message = context.getString(R.string.msg_allow_fail, describeFirestoreError(context, e))
                            } finally {
                                busy = false
                            }
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 52.dp)
                ) {
                        Text(stringResource(R.string.btn_allow), fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    enabled = !busy,
                    onClick = {
                        busy = true
                        scope.launch {
                            try {
                                denySession(session.id, user.uid)
                                    message = context.getString(R.string.msg_deny_ok)
                            } catch (e: SessionException) {
                                message = e.message
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                    message = context.getString(R.string.msg_deny_fail, describeFirestoreError(context, e))
                            } finally {
                                busy = false
                            }
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 52.dp)
                ) {
                        Text(stringResource(R.string.btn_deny), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/**
 * Phase 15 (v0.19.0) — the approval ask now ALSO arrives as a CENTERED
 * modal dialog (Owner report: the top card is easy to miss; popups must
 * land in front of the eyes). Same Firestore grant/deny as the card — the
 * security rules re-verify everything either way. Shown once per session
 * id; dismissing it decides nothing, the card stays on home.
 */
@Composable
private fun HomeConsentDialog(session: SessionView, user: FirebaseUser) {
    var show by remember(session.id) {
        // v0.21.0 (audit): durable one-per-ask dedup on the process-level bus —
        // a positional remember() re-prompted the SAME ask after rotation,
        // language change or a listener error/reconnect cycle.
        mutableStateOf(session.id !in SessionUiBus.shownAsks)
    }
    if (!show) return
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var nowMillis by remember { mutableStateOf(System.currentTimeMillis()) }

    LaunchedEffect(session.id) {
        while (true) {
            nowMillis = System.currentTimeMillis()
            delay(1000)
        }
    }
    AlertDialog(
        onDismissRequest = {
            SessionUiBus.shownAsks.add(session.id)
            show = false
        },
            title = { Text(stringResource(R.string.dlg_consent_title), fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                        stringResource(R.string.cons_body, session.helperEmail.ifBlank { "Someone" }),
                    fontWeight = FontWeight.SemiBold
                )
                if (session.kind == "guest") {
                    Text(
                            stringResource(R.string.dlg_guest_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
                val secondsLeft =
                    ((session.respondDeadlineMillis - nowMillis) / 1000).coerceAtLeast(0)
                Text(
                        stringResource(R.string.cons_expiry, mmss(secondsLeft)),
                    style = MaterialTheme.typography.bodyMedium
                )
                message?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (it.contains("✓")) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        }
                    )
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !busy,
                onClick = {
                    busy = true
                    scope.launch {
                        try {
                            grantSession(session.id, user.uid)
                                message = context.getString(R.string.msg_allow_ok)
                            SessionUiBus.shownAsks.add(session.id)
                            delay(600)
                            show = false
                        } catch (e: SessionException) {
                            message = e.message
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                                message = context.getString(R.string.msg_allow_fail, describeFirestoreError(context, e))
                        } finally {
                            busy = false
                        }
                    }
                }
                ) { Text(stringResource(R.string.btn_allow), fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            OutlinedButton(
                enabled = !busy,
                onClick = {
                    busy = true
                    scope.launch {
                        try {
                            denySession(session.id, user.uid)
                                message = context.getString(R.string.msg_deny_ok)
                            SessionUiBus.shownAsks.add(session.id)
                            delay(600)
                            show = false
                        } catch (e: SessionException) {
                            message = e.message
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                                message = context.getString(R.string.msg_deny_fail, describeFirestoreError(context, e))
                        } finally {
                            busy = false
                        }
                    }
                }
                ) { Text(stringResource(R.string.btn_deny), fontWeight = FontWeight.Bold) }
        }
    )
}

// ═══════════════════════════════════════════════════════════════════════
// একটিভিটি ট্যাব (v0.14.0) — শুধু প্রমাণিত ডেটা, প্রতিটি সারি আইকন-টাইল:
// লাইভ সেশন → শেষ-সেশন → যুক্ত ফোনের প্রেজেন্স। বানানো history নেই।
// ═══════════════════════════════════════════════════════════════════════
@Composable
private fun ActivityTab(user: FirebaseUser?) {
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
private fun ActivityFeed(user: FirebaseUser) {
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

/** Phase 11 — honest localized name for a session status. v0.28.0 (Phase 19.2). */
private fun sessionStatus(context: Context, status: String): String = when (status) {
    "REQUESTED" -> context.getString(R.string.st_requested)
    "ACTIVE" -> context.getString(R.string.st_active)
    "ENDED" -> context.getString(R.string.st_ended)
    "DENIED" -> context.getString(R.string.st_denied)
    "CANCELLED" -> context.getString(R.string.st_cancelled)
    "EXPIRED" -> context.getString(R.string.st_expired)
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
private fun SettingsTab(
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
private fun EditNameDialog(
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
private fun LangOptionRow(
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
private fun TestNotifRow() {
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
private fun DocOverlay(
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
 * Phase 16 — the LIVE LOCATION tab: an honest placeholder (Owner: "একটিভিটি
 * ট্যাব টি মোছে দিয়ে live location নামে একটা ট্যাব ওপেন করেন। এটার কাজ
 * ভবিষ্যতে করবো")। Nothing is collected, nothing is shared, no fake map.
 */
@Composable
private fun LiveLocationTab() {
    SectionCard(title = stringResource(R.string.loc_title), icon = Icons.Filled.LocationOn) {
        EmptyState(
            icon = Icons.Filled.LocationOn,
            title = stringResource(R.string.loc_coming_title),
            hint = stringResource(R.string.loc_coming_body)
        )
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

// ═══════════════════════════════════════════════════════════════════════
// ফোন ট্যাব (v0.14.0; v0.18.1-এ স্থায়ী "ফোন যুক্ত করুন" কার্ড যোগ হলো —
//   QR-স্ক্যান/কোড-ইমপোর্ট/কোড-বানানো আর FAB-শিটের পেছনে লুকানো নয়, ট্যাবের উপরেই দৃশ্যমান):
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
    onDismissPairSheet: () -> Unit,
    onOpenPairSheet: () -> Unit
) {
    if (signedInUser == null) {
            SectionCard(title = stringResource(R.string.phone_title), icon = Icons.Filled.Phone) {
            EmptyState(
                icon = Icons.Filled.Person,
                    title = stringResource(R.string.phone_signin_first),
                    hint = stringResource(R.string.phone_signin_hint),
            )
        }
    } else {
        PairingSections(
            user = signedInUser,
            showPairSheet = showPairSheet,
            onDismissPairSheet = onDismissPairSheet,
            onOpenPairSheet = onOpenPairSheet
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PairingSections(
    user: FirebaseUser,
    showPairSheet: Boolean,
    onDismissPairSheet: () -> Unit,
    onOpenPairSheet: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var invite by PairingUiBus.inviteState
    var inviteKind by PairingUiBus.inviteKindState
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
                onError = { e -> inviteSyncError = describeFirestoreError(context, e) }
            )
        }
        onDispose { registration?.remove() }
    }

    // Live list of trusted relationships (both directions).
    DisposableEffect(user.uid) {
        val stopWatching = watchRelationships(
            user,
            onUpdate = { relationshipsSyncError = null; relationships = it },
            onError = { e -> relationshipsSyncError = describeFirestoreError(context, e) }
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
            onError = { e -> sessionSyncError = describeFirestoreError(context, e) }
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

    // v0.21.0 (audit F1): the per-session viewer-state reset AND the
    // "no open session → stop everything" teardown both moved to the
    // HomeScreen ROOT (they used to live here, so they only ran while the
    // Phones tab was composed — and the teardown fired on the empty
    // pre-snapshot loading state, killing live shares on tab switches).

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
    // v0.22.0 (Phase 18.1): sweeps EVERY open session, not just the newest —
    // repeated reboots used to leave older zombies behind forever (they filled
    // the capped watch window and could push a fresh ask out of it). The same
    // isStaleOpen(+4 s grace) rule the request-side self-heal applies.
    LaunchedEffect(sessions.size, openSession?.id, openSession?.status, sessionNow) {
        if (sessions.isEmpty()) return@LaunchedEffect
        sessions.filter { it.isStaleOpen(sessionNow) }.forEach { s ->
            try {
                if (s.status == "REQUESTED") expireSession(s.id)
                else endSession(s.id, user.uid, byTimeout = true)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // best effort — the ROOT sweep and the server sweep also
                // perform exactly these transitions
            }
        }
    }

    // v0.21.0 (audit F1): the session-close teardown moved to the
    // HomeScreen root (loaded-guarded) — see the comment there.

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
                        context.getString(R.string.ph_req_sent, rel.recipientEmail)
                selectedRelId = null
            } catch (e: SessionException) {
                sessionMessage = e.message
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                sessionMessage = context.getString(R.string.err_send_failed, describeFirestoreError(context, e))
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
                            context.getString(R.string.ph_allowed_15m)
                } else {
                    denySession(s.id, user.uid)
                        sessionMessage = context.getString(R.string.ph_denied_short)
                }
            } catch (e: SessionException) {
                sessionMessage = e.message
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                sessionMessage = context.getString(R.string.err_answer_failed, describeFirestoreError(context, e))
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
                    sessionMessage = context.getString(R.string.ph_session_ended)
            } catch (e: SessionException) {
                sessionMessage = e.message
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                sessionMessage = context.getString(R.string.err_end_failed, describeFirestoreError(context, e))
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
                    val rel = redeemCode(context, user, rawCode)
                    redeemedKind = rel.kind
                    redeemMessage = if (rel.kind == REL_KIND_GUEST) {
                        context.getString(R.string.ph_guest_added, rel.recipientEmail, rel.recipientDeviceModel)
                    } else {
                        context.getString(R.string.ph_paired_ok, rel.recipientEmail, rel.recipientDeviceModel)
                    }
                codeInput = ""
            } catch (e: RedemptionException) {
                redeemMessage = e.message
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                    redeemMessage = context.getString(R.string.ph_pairing_failed, describeFirestoreError(context, e))
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
                pairingError = context.getString(R.string.err_create_failed, describeFirestoreError(context, e))
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
                                text = stringResource(R.string.ph_req_to, currentSession.recipientEmail),
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
                            text = stringResource(R.string.ph_wait_allow),
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
                                        sessionMessage = context.getString(R.string.ph_cancelled_msg)
                                } catch (e: SessionException) {
                                    sessionMessage = e.message
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    sessionMessage =
                                        context.getString(R.string.err_cancel_failed, describeFirestoreError(context, e))
                                } finally {
                                    sessionBusy = false
                                }
                            }
                        }
                    ) {
                            Text(stringResource(R.string.btn_cancel_request))
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
                                    stringResource(R.string.ph_active_ihelp, currentSession.recipientEmail)
                            } else {
                                    stringResource(R.string.ph_active_helping_you, currentSession.helperEmail)
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
                            text = stringResource(R.string.ph_auto_end_note),
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
                            Text(stringResource(R.string.btn_end_session))
                    }
                }
            }
        }
        else -> {
            lastFinishedSession?.let { lastFinished ->
                    val byWhom = when (lastFinished.endedBy) {
                    null, "" -> ""
                    "timeout" -> context.getString(R.string.ended_auto)
                    lastFinished.helperUid -> context.getString(R.string.ended_by_helper)
                    lastFinished.recipientUid -> context.getString(R.string.ended_by_recipient)
                    else -> ""
                }
                Text(
                        text = stringResource(R.string.ph_last_session_line, sessionStatus(context, lastFinished.status)) + byWhom,
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

    // ---------- ১b. ফোন যুক্ত করুন — অ্যাপের প্রধান অ্যাকশন, সবসময় দৃশ্যমান ----------
    // v0.18.1 (Phase 11.8) discoverability fix: the helper-side flow (QR scan
    // + code import) lived at the very bottom of this page in v0.13.0 and only
    // inside the FAB sheet from v0.14.0 — the app's PRIMARY action was
    // effectively invisible (Owner: "QR scanner ও কোড ইমপোর্ট দেখা যাচ্ছে না").
    // Now both directions sit in one permanent, prominent card near the top:
    // scan (helper) or create/import (receiver) — one tap each.
    SectionCard(
            title = stringResource(R.string.ph_two_ways_title),
        icon = Icons.Filled.Phone,
            infoText = stringResource(R.string.ph_two_ways_info),
    ) {
        BigButton(
            onClick = { showScanner = true }
        ) {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
                Text("  " + stringResource(R.string.ph_btn_scan))
        }
        WideOutlinedButton(
            onClick = onOpenPairSheet
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
                Text("  " + stringResource(R.string.ph_btn_import))
        }
        Text(
                text = stringResource(R.string.ph_role_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    // v0.18.1 — the tab-level scan entry. Same dialog and same Phase-12
    // role-setup journey as the in-sheet scan button; the dialog renders here
    // only while the sheet is closed, so the two entries can never double-
    // render. State (showScanner/codeInput/pendingScan) is shared.
    if (showScanner && !showPairSheet) {
        QrScanDialog(
            onCode = { code, kindHint ->
                codeInput = code
                redeemMessage = null
                redeemedKind = null
                showScanner = false
                pendingScan = ScannedCode(code, kindHint)
            },
            onDismiss = { showScanner = false }
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
                var saveBusy by remember(currentInvite.invitationId) { mutableStateOf(false) }
                // v0.21.0 (audit): a monotonic trigger counter — the old Boolean
                // flag was reset INSIDE the LaunchedEffect keyed on it, so the
                // effect cancelled itself mid-IO and the "Saved ✓" toast (the
                // exact Owner complaint about missing save feedback) almost
                // never appeared; repeated taps then stacked duplicate PNGs.
                var saveTrigger by remember(currentInvite.invitationId) { mutableStateOf(0) }
                var saveFeedback by remember(currentInvite.invitationId) {
                    mutableStateOf<String?>(null)
                }

                val storagePermissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { granted ->
                    if (granted) {
                        saveTrigger += 1
                    } else {
                        saveFeedback =
                            "Storage permission is needed to save the QR image."
                    }
                }

                LaunchedEffect(saveTrigger) {
                    if (saveTrigger == 0) return@LaunchedEffect
                    val bitmap = qr
                    if (bitmap == null) return@LaunchedEffect
                    saveBusy = true
                    val savedNote = withContext(Dispatchers.IO) {
                        val fileName = "pairing-code-${currentInvite.invitationId}.png"
                        saveQrToDownloads(context, bitmap, fileName)
                    }
                    saveBusy = false
                    // Phase 16 — the Owner asked for self-confirming actions:
                    // a saved QR now TELLS you it was saved (toast), plus the
                    // inline note stays on the card.
                    if (savedNote != null) {
                        saveFeedback = "Saved ✓ $savedNote"
                        Toast.makeText(
                            context,
                            context.getString(R.string.toast_saved, savedNote),
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        saveFeedback = "Could not save the QR image."
                        Toast.makeText(
                            context,
                            context.getString(R.string.toast_save_failed),
                            Toast.LENGTH_SHORT
                        ).show()
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
                                stringResource(R.string.ph_guest_running)
                        } else {
                                stringResource(R.string.ph_family_running)
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
                                    contentDescription = stringResource(R.string.cd_more),
                            )
                        }
                        DropdownMenu(
                            expanded = menuOpen,
                            onDismissRequest = { menuOpen = false }
                        ) {
                            DropdownMenuItem(
                                    text = { Text(stringResource(R.string.btn_copy)) },
                                onClick = {
                                    menuOpen = false
                                    val ok = copyToClipboard(
                                        context, "Pairing code", currentInvite.code
                                    )
                                    // Phase 16 — copy now confirms itself
                                    // (Owner: "কপি হয়েছে দেখাতে হবে")।
                                    if (ok) {
                                        Toast.makeText(
                                            context,
                                            context.getString(R.string.toast_copied),
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            )
                            DropdownMenuItem(
                                    text = { Text(stringResource(R.string.btn_save_qr)) },
                                onClick = {
                                    menuOpen = false
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ||
                                        ContextCompat.checkSelfPermission(
                                            context,
                                            Manifest.permission.WRITE_EXTERNAL_STORAGE
                                        ) == PackageManager.PERMISSION_GRANTED
                                    ) {
                                        saveTrigger += 1
                                    } else {
                                        storagePermissionLauncher.launch(
                                            Manifest.permission.WRITE_EXTERNAL_STORAGE
                                        )
                                    }
                                }
                            )
                            DropdownMenuItem(
                                    text = { Text(stringResource(R.string.btn_share)) },
                                onClick = {
                                    menuOpen = false
                                    val q = qr
                                    if (q == null) {
                                        saveFeedback =
                                            "Could not prepare the QR image for sharing."
                                        Toast.makeText(
                                            context,
                                            context.getString(R.string.toast_share_prep_failed),
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    } else {
                                        val intent = buildQrShareIntent(
                                            context, q, currentInvite.code
                                        )
                                        if (intent != null) {
                                            try {
                                                context.startActivity(intent)
                                            } catch (_: Exception) {
                                                saveFeedback = "No app available for sharing."
                                                Toast.makeText(
                                                    context,
                                                    context.getString(R.string.toast_share_no_app),
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        } else {
                                            saveFeedback =
                                                "Could not prepare the QR image for sharing."
                                            Toast.makeText(
                                                context,
                                                context.getString(R.string.toast_share_prep_failed),
                                                Toast.LENGTH_SHORT
                                            ).show()
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
                        text = stringResource(R.string.ph_family_only_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
                val totalSeconds = (currentInvite.expiresAtMillis - nowMillis) / 1000
                Text(
                        text = stringResource(R.string.ph_countdown, totalSeconds / 60, (totalSeconds % 60).toString().padStart(2, '0')),
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
                                label = stringResource(R.string.ph_status_waiting),
                            kind = "info"
                        )
                        "PAIRED" -> StatusBadge(
                                label = stringResource(R.string.ph_status_paired),
                            kind = "success"
                        )
                        else -> {}
                    }
                }
                if (inviteStatus == "PAIRED") {
                    Text(
                            text = stringResource(R.string.ph_claimed_prefix, claimerEmail ?: "helper") +
                            if (kindIsGuest) {
                                stringResource(R.string.ph_kind_guest_short)
                            } else {
                                stringResource(R.string.ph_kind_family_short)
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
                                        context.getString(R.string.err_cancel_failed, describeFirestoreError(context, e))
                                }
                            }
                        }
                    ) {
                            Text(stringResource(R.string.btn_cancel_code))
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
            "PAIRED" -> stringResource(R.string.ph_inv_paired, claimerEmail ?: "helper")
            "CANCELLED" -> stringResource(R.string.ph_inv_cancelled)
            else -> stringResource(R.string.ph_inv_expired)
        }
        Text(
            text = statusText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    // ---------- ৩. যুক্ত ফোন — List Tiles + অ্যাকশন নিচ-শিট ----------
    SectionCard(
            title = stringResource(R.string.devices_title),
        icon = Icons.Filled.Phone,
            infoText = stringResource(R.string.ph_two_kinds_info),
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
                        title = stringResource(R.string.devices_empty_title),
                        hint = stringResource(R.string.devices_empty_hint2),
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
                            append(if (iHelp) stringResource(R.string.rel_you_help) else stringResource(R.string.rel_helps_you))
                        if (selectedRel.recipientDeviceModel.isNotBlank()) {
                            append(" · ${selectedRel.recipientDeviceModel}")
                        }
                            append(" · " + sessionStatus(context, selectedRel.status))
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (isGuest) {
                    val remainingMin =
                        ((selectedRel.guestExpiresAtMillis - relNow) / 60000L).coerceAtLeast(0)
                    Text(
                        text = when {
                                selectedRel.status != "ACTIVE" -> stringResource(R.string.rel_guest_expired)
                                selectedRel.guestExpiresAtMillis <= 0L -> stringResource(R.string.rel_guest_label)
                            remainingMin >= 60L ->
                                    stringResource(R.string.rel_guest_left_hm, remainingMin / 60L, remainingMin % 60L)
                                else -> stringResource(R.string.rel_guest_left_m, remainingMin)
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
                                stringResource(R.string.rel_role_guest)
                        } else {
                                stringResource(R.string.rel_role_family)
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
                            text = stringResource(R.string.rel_guest_over_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                if (selectedRel.status == "ACTIVE" && iHelp && selectedRel.guestLive) {
                    BigButton(
                        enabled = !sessionBusy && openSession == null,
                        onClick = { startSessionFor(selectedRel) }
                    ) {
                            Text(stringResource(R.string.btn_request_session))
                    }
                    if (openSession != null) {
                        Text(
                                text = stringResource(R.string.rel_session_running),
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
                                        context.getString(R.string.err_revoke_failed, describeFirestoreError(context, e))
                                } finally {
                                    revokingId = null
                                }
                            }
                        }
                    ) {
                            Text(stringResource(R.string.btn_revoke))
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
                                        context.getString(R.string.err_remove_failed, describeFirestoreError(context, e))
                                } finally {
                                    revokingId = null
                                }
                            }
                        }
                    ) {
                            Text(stringResource(R.string.btn_remove))
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
                        text = stringResource(R.string.ph_add_new_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                        text = stringResource(R.string.ph_add_new_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                PairOptionRow(
                    icon = Icons.Filled.AccountCircle,
                        title = stringResource(R.string.ph_family_code_title),
                        description = stringResource(R.string.ph_family_code_desc),
                    busy = creatingKind == REL_KIND_FAMILY,
                    enabled = creatingKind == null,
                    onClick = {
                        createCode(REL_KIND_FAMILY)
                        onDismissPairSheet()
                    }
                )
                PairOptionRow(
                    icon = Icons.Filled.DateRange,
                        title = stringResource(R.string.ph_guest_code_title),
                        description = stringResource(R.string.ph_guest_code_desc),
                    busy = creatingKind == REL_KIND_GUEST,
                    enabled = creatingKind == null,
                    onClick = {
                        createCode(REL_KIND_GUEST)
                        onDismissPairSheet()
                    }
                )
                HorizontalDivider()
                Text(
                        text = stringResource(R.string.ph_give_code_hint),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                        text = stringResource(R.string.ph_badge_legend),
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
                            label = { Text(stringResource(R.string.ph_code_label)) },
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
                            Text("  " + stringResource(R.string.btn_scan_short))
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
                            Text("  " + stringResource(R.string.btn_import_code))
                    }
                }
                redeemMessage?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                            color = if (message.contains("✓")) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        }
                    )
                }
                Text(
                        text = stringResource(R.string.rel_always_permission),
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
    val context = LocalContext.current
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
                    text = stringResource(R.string.rs_title),
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
                                text = context.getString(R.string.rs_verifying),
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
                                label = if (guest) stringResource(R.string.rs_kind_guest) else stringResource(R.string.rs_kind_family),
                            kind = "success"
                        )
                    }
                    Text(
                            text = context.getString(R.string.rs_added_ok),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    ScannedRoleBullet(
                        accent = accent,
                            text = stringResource(R.string.rs_perm_note)
                    )
                    if (guest) {
                        ScannedRoleBullet(
                            accent = accent,
                                text = stringResource(R.string.rs_guest_note)
                        )
                    } else {
                        ScannedRoleBullet(
                            accent = accent,
                                text = stringResource(R.string.rs_family_note)
                        )
                    }
                    BigButton(onClick = onDismiss) {
                            Text(stringResource(R.string.btn_ok))
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
                                            !known -> stringResource(R.string.rs_check_code)
                                            guestHint -> stringResource(R.string.rs_you_guest)
                                            else -> stringResource(R.string.rs_you_family)
                                    },
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = accent
                                )
                            }
                            Text(
                                text = when {
                                        !known -> stringResource(R.string.rs_legacy_note)
                                        guestHint -> stringResource(R.string.rs_guest_desc)
                                        else -> stringResource(R.string.rs_family_desc)
                                },
                                style = MaterialTheme.typography.bodySmall
                            )
                            HorizontalDivider()
                            ScannedRoleBullet(
                                accent = accent,
                                    stringResource(R.string.rs_feat_request)
                            )
                            ScannedRoleBullet(
                                accent = accent,
                                    stringResource(R.string.rs_feat_perm)
                            )
                            ScannedRoleBullet(
                                accent = accent,
                                    stringResource(R.string.rs_feat_autoend)
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
                            Text("  " + stringResource(R.string.btn_connect))
                    }
                    Text(
                            text = stringResource(R.string.rs_cancel_note),
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
    val context = LocalContext.current
    val otherEmail =
        (if (iHelp) rel.recipientEmail else rel.helperEmail).ifBlank { "(no email)" }
    val guestOk = isGuest && rel.status == "ACTIVE" && rel.guestLive
    // Phase 11.6 — minutes until the guest relationship expires (for the
    // warning-colored chip when it is nearly over).
    val guestMinutesLeft = ((rel.guestExpiresAtMillis - relNow) / 60000L).coerceAtLeast(0)
    val chipText = when {
        !isGuest && rel.status == "ACTIVE" -> "Family"
            !isGuest -> stringResource(R.string.tile_family_status, sessionStatus(context, rel.status))
        guestOk -> {
            val remainingMin =
                ((rel.guestExpiresAtMillis - relNow) / 60000L).coerceAtLeast(0)
            when {
                rel.guestExpiresAtMillis <= 0L -> "Guest"
                remainingMin >= 60L -> stringResource(R.string.tile_guest_left_hm, remainingMin / 60L, remainingMin % 60L)
                else -> stringResource(R.string.tile_guest_left_m, remainingMin)
            }
        }
            else -> stringResource(R.string.tile_guest_status, sessionStatus(context, rel.status))
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        AppListTile(
            title = otherEmail,
            initials = otherEmail.take(1).ifBlank { "?" },
            subtitle = buildString {
                append(if (iHelp) stringResource(R.string.rel_you_help) else stringResource(R.string.rel_helps_you))
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
                text = stringResource(R.string.tile_guest_expired),
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

/** Shared UI state for the helper's session surfaces (panel + pinned bar). */
private object SessionUiBus {
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

/**
 * v0.21.0 (audit) — process-level pairing-card state. The live invitation
 * used to be plain remember() inside the tab-scoped pairing section, so a
 * recreate (language change, rotation) stranded a PENDING invitation: the
 * card and its watcher vanished while the invitation stayed live in
 * Firestore until expiry. Delegating to this bus keeps the card alive.
 */
private object PairingUiBus {
    val inviteState = mutableStateOf<InviteCode?>(null)
    val inviteKindState = mutableStateOf(REL_KIND_FAMILY)
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
