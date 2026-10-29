package com.family.remote.support

import android.Manifest
import android.app.Activity
import android.content.Context
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Offset
import kotlin.math.hypot
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
// Phase 9 (v0.9.4) — T0: family-warm theme + senior-friendly type scale.
//
// The family that actually uses this app is Bangladeshi and often 50+, so:
//   • a warm amber/brown palette (calm and caring — no cold corporate blue)
//   • body text lifted one notch (14→16sp, 12→14sp) and bolder section titles
//   • ONE fixed light scheme — predictable contrast, no dark-mode surprises
// Pure presentation: zero logic lives here.
// ═══════════════════════════════════════════════════════════════════════
private val FamilyWarmColors = lightColorScheme(
    primary = Color(0xFF8F4C00),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDCC2),
    onPrimaryContainer = Color(0xFF311300),
    secondary = Color(0xFF74584A),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFDCC2),
    onSecondaryContainer = Color(0xFF2B160A),
    tertiary = Color(0xFF5B6136),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFE3E5B8),
    onTertiaryContainer = Color(0xFF191D00),
    background = Color(0xFFFFF8F3),
    onBackground = Color(0xFF231A13),
    surface = Color(0xFFFFF8F3),
    onSurface = Color(0xFF231A13),
    surfaceVariant = Color(0xFFF4DED0),
    onSurfaceVariant = Color(0xFF52443A),
    outline = Color(0xFF847367),
    outlineVariant = Color(0xFFD7C4B5),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
    inverseSurface = Color(0xFF392E26),
    inverseOnSurface = Color(0xFFFFEEE1),
    inversePrimary = Color(0xFFFFB77C),
    surfaceTint = Color(0xFF8F4C00)
)

private val SeniorTypography: Typography = Typography().let { base ->
    base.copy(
        titleMedium = base.titleMedium.copy(
            fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold
        ),
        titleSmall = base.titleSmall.copy(
            fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold
        ),
        bodyLarge = base.bodyLarge.copy(fontSize = 17.sp, lineHeight = 24.sp),
        bodyMedium = base.bodyMedium.copy(fontSize = 16.sp, lineHeight = 23.sp),
        bodySmall = base.bodySmall.copy(fontSize = 14.sp, lineHeight = 19.sp),
        labelLarge = base.labelLarge.copy(
            fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold
        )
    )
}

/** Phase 9 (T1) — every section card: same padding, same spacing, same title. */
@Composable
private fun SectionCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            content()
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
        setContent {
            // Phase 9 (v0.9.4) — T0: the whole app now sits on one warm,
            // senior-friendly Material theme (bigger type, calm family
            // colors). Deliberately a FIXED light scheme: predictable,
            // high-contrast, no surprise dark-mode repaint for elderly users.
            MaterialTheme(
                colorScheme = FamilyWarmColors,
                typography = SeniorTypography
            ) {
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
    var deviceStatus by remember { mutableStateOf("Not checked yet") }
    var deviceList by remember { mutableStateOf<List<String>>(emptyList()) }
    var registerRetryKey by remember { mutableStateOf(0) }
    var presenceStatus by remember { mutableStateOf("Not sent yet") }
    var pushStatus by remember { mutableStateOf("Not set up yet") }

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

    // After sign-in (or on app open while signed in): refresh device record + list.
    // registerRetryKey lets the user retry manually if the Firestore write failed
    // (e.g. rules not published yet or a flaky network).
    LaunchedEffect(signedInUser?.uid, registerRetryKey) {
        val user = signedInUser ?: return@LaunchedEffect
        deviceStatus = "Registering this device…"
        try {
            val deviceId = registerDevice(context, user)
            deviceStatus = "Registered ✓ (id ${deviceId.take(8)}…)"
            // Phase 5 — presence heartbeat + push token registration.
            try {
                sendHeartbeat(user.uid, deviceId, Build.MODEL)
                presenceStatus = "Online ✓ — heartbeat sent just now"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                presenceStatus = "Presence failed: ${describeFirestoreError(e)}"
            }
            try {
                registerFcmToken(user.uid, deviceId)
                pushStatus = "Push notifications ready ✓"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                pushStatus = "Push setup failed: ${describeFirestoreError(e)}"
            }
            deviceList = loadDevices(user)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            deviceStatus = "Registration failed: ${describeFirestoreError(e)}"
        }
    }

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Family Remote Support",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Help your family with their phone — always with their clear permission.",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "পরিবারের ফোনে দূর থেকে সাহায্য — সবসময় স্পষ্ট অনুমতি নিয়ে।",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            StatusRow(
                label = "App version · সংস্করণ",
                value = "v${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})"
            )

            // v0.9.5 — PERMISSION WIZARD. The Owner asked for "সব পারমিশন
            // ইনস্টল/ওপেনের সময়" — Android cannot grant runtime permissions
            // at install time, so this is the honest equivalent: the FIRST
            // open auto-launches the checklist (notifications, camera,
            // Remote-assist accessibility, battery exemption) with live
            // state; the card below re-opens it any time.
            var showWizard by remember { mutableStateOf(false) }
            var permSummary by remember { mutableStateOf(readPermChecklist(context)) }
            LaunchedEffect(Unit) {
                if (!isPermissionWizardDone(context)) showWizard = true
            }
            LaunchedEffect(Unit) {
                while (true) {
                    permSummary = readPermChecklist(context)
                    delay(2500)
                }
            }
            SectionCard(title = "Permissions · পারমিশন") {
                if (permSummary.allDone) {
                    Text(
                        text = "সব পারমিশন চালু ✓ — অ্যাপ পুরোপুরি প্রস্তুত।",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                } else {
                    Text(
                        text = "চালু ${permSummary.doneCount}/4 — বাকি: ${permSummary.missingNamesBn()}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "সব ফিচার মসৃণ চালাতে বাকিগুলো একবার চালু করুন।",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                WideOutlinedButton(onClick = { showWizard = true }) {
                    Text("Permission check · পারমিশন চেক খুলুন")
                }
            }
            PermissionWizard(
                visible = showWizard,
                onDismiss = {
                    showWizard = false
                    markPermissionWizardDone(context)
                }
            )

            // Phase 9 (T1) — pairing is the heart of the app, so its card
            // moved up right under the account; the dev-console rows moved
            // into a collapsed About card and the dead "Phase 2 build check"
            // card is retired (the About card carries the same Firebase
            // facts).
            SectionCard(title = "Account · অ্যাকাউন্ট") {
                if (!webClientConfigured) {
                    Text(
                        text = "Sign-in is disabled in this build: the Web Client ID is not set yet. " +
                            "Finish the Firebase console steps in the web guide, send the Web Client ID " +
                            "to your assistant, and install the next build.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                if (signedInUser == null) {
                    BigButton(
                        enabled = webClientConfigured && !busy,
                        onClick = {
                            if (activity != null) {
                                busy = true
                                statusMessage = null
                                scope.launch {
                                    try {
                                        signedInUser = signInWithGoogle(activity)
                                    } catch (e: GetCredentialException) {
                                        statusMessage = "Sign-in cancelled or failed: ${e.type}"
                                    } catch (e: Exception) {
                                        statusMessage = "Sign-in failed: ${e.message}"
                                    } finally {
                                        busy = false
                                    }
                                }
                            }
                        }
                    ) {
                        if (busy) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("Sign in with Google · গুগল দিয়ে সাইন ইন")
                        }
                    }
                } else {
                    val user = signedInUser
                    StatusRow(label = "Signed in as", value = user?.email ?: "(no email)")
                    StatusRow(label = "Name", value = user?.displayName ?: "(no name)")
                    Text(
                        text = "Your account and device are stored only in your own Firebase project.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    WideOutlinedButton(
                        enabled = !busy,
                        onClick = {
                            busy = true
                            scope.launch {
                                try {
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
                                    statusMessage = null
                                } catch (e: Exception) {
                                    statusMessage = "Sign-out failed: ${e.message}"
                                } finally {
                                    busy = false
                                }
                            }
                        }
                    ) {
                        Text("Sign out · সাইন আউট")
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

            PairingCard(signedInUser = signedInUser)

            SectionCard(title = "This device · এই ফোন") {
                Text(
                    text = deviceStatus,
                    style = MaterialTheme.typography.bodyMedium
                )
                if (deviceStatus.startsWith("Registration failed")) {
                    WideOutlinedButton(onClick = { registerRetryKey++ }) {
                        Text("Retry registration · আবার চেষ্টা করুন")
                    }
                }
                if (signedInUser != null) {
                    StatusRow(label = "Presence · অনলাইন", value = presenceStatus)
                    StatusRow(label = "Push · নোটিফিকেশন", value = pushStatus)
                }
                if (deviceList.isEmpty()) {
                    Text(
                        text = "No devices on this account yet.",
                        style = MaterialTheme.typography.bodySmall
                    )
                } else {
                    Text(
                        text = "Devices on this account:",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium
                    )
                    deviceList.forEachIndexed { index, entry ->
                        if (index > 0) HorizontalDivider()
                        Text(
                            text = entry,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            SectionCard(title = "About · অ্যাপ সম্পর্কে") {
                var showAbout by remember { mutableStateOf(false) }
                Text(
                    text = if (showAbout) "Hide details ▴ · লুকান" else "Show details ▾ · বিস্তারিত",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.clickable { showAbout = !showAbout }
                )
                if (showAbout) {
                    StatusRow(label = "Package", value = "com.family.remote.support")
                    StatusRow(label = "Firebase project", value = firebaseProjectId)
                    StatusRow(label = "Firebase app ID", value = firebaseAppId)
                    StatusRow(
                        label = "Next milestone",
                        value = "Phase 11 (UI/UX পলিশ) + family-scale rollout"
                    )
                }
            }
        }
    }
}

/**
 * Phase 4/10 — pairing card. The phone that needs help creates a 5-minute
 * code in one of two modes: FAMILY (permanent trusted link) or GUEST
 * (a 2-hour temporary link for outside help — never auto-promoted to
 * family). The helper's phone types the same code. Redemption is one
 * atomic Firestore transaction, verified again by the security rules.
 * Works across two Google accounts (during testing, two accounts on this
 * same phone work as well).
 */
@Composable
fun PairingCard(signedInUser: FirebaseUser?) {
    SectionCard(title = "Pair a trusted phone · বিশ্বস্ত ফোন যুক্ত করুন") {
        Text(
            text = "Two kinds of link · দুই ধরনের যুক্তি — a FAMILY code pairs a " +
                "permanent trusted phone; a GUEST code gives outside help a 2-hour " +
                "temporary window. Either way, every support session still asks " +
                "for separate permission.",
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = "FAMILY কোড = স্থায়ী বিশ্বস্ত ফোন (একবার যুক্ত হলে আর কোড লাগে না); " +
                "GUEST কোড = ২ ঘণ্টার সাময়িক যুক্তি (বাইরের সাহায্যকারীর জন্য)। Guest " +
                "কখনো স্বয়ংক্রিয়ভাবে Family হয় না। প্রতিটি সেশনে আলাদা অনুমতি লাগবেই।",
            style = MaterialTheme.typography.bodyMedium
        )

        if (signedInUser == null) {
            Text(
                text = "Sign in first — pairing needs a Google account. · প্রথমে সাইন ইন করুন।",
                style = MaterialTheme.typography.bodySmall
            )
        } else {
            PairingSections(signedInUser)
        }
    }
}

@Composable
private fun PairingSections(user: FirebaseUser) {
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

    // ---------- Section A: this phone needs help ----------
    HorizontalDivider()
    Text(
        text = "এই ফোনে কি সাহায্য দরকার? · This phone needs help",
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold
    )

    if (showCode && currentInvite != null) {
        Text(
            text = if (inviteKind == REL_KIND_GUEST) {
                "Guest কোড — এটি যুক্ত করলে ২ ঘণ্টার সাময়িক যুক্তি হবে (বাইরের সাহায্যের জন্য)।"
            } else {
                "Family কোড — এটি যুক্ত করলে স্থায়ী বিশ্বস্ত যুক্তি হবে।"
            },
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = currentInvite.code,
            style = MaterialTheme.typography.headlineSmall,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "Share this code only with the family member who will help you. · " +
                "কোডটি শুধু যে পরিবারের সদস্য সাহায্য করবেন তাকেই দেবেন।",
            style = MaterialTheme.typography.bodySmall
        )
        val totalSeconds = (currentInvite.expiresAtMillis - nowMillis) / 1000
        Text(
            text = "Expires in ${totalSeconds / 60}:${(totalSeconds % 60)
                .toString().padStart(2, '0')} · বাকি সময়",
            style = MaterialTheme.typography.bodySmall
        )
        val qr = remember(currentInvite.code) { qrBitmap(currentInvite.qrPayload) }
        if (qr != null) {
            Image(
                bitmap = qr.asImageBitmap(),
                contentDescription = "Pairing QR code",
                modifier = Modifier.size(160.dp)
            )

            // v0.6.1 — copy the readable code, save the QR as a PNG into the
            // Download folder, or hand both to the Android share sheet.
            var copiedJustNow by remember(currentInvite.invitationId) { mutableStateOf(false) }
            var saveBusy by remember(currentInvite.invitationId) { mutableStateOf(false) }
            var saveRequest by remember(currentInvite.invitationId) { mutableStateOf(false) }
            var saveFeedback by remember(currentInvite.invitationId) { mutableStateOf<String?>(null) }

            val storagePermissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { granted ->
                if (granted) {
                    saveRequest = true
                } else {
                    saveFeedback = "Storage permission is needed to save the QR image."
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
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    enabled = !saveBusy,
                    onClick = {
                        copiedJustNow =
                            copyToClipboard(context, "Pairing code", currentInvite.code)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                ) {
                    Text(if (copiedJustNow) "Copied ✓ · হয়েছে" else "Copy · কপি")
                }
                OutlinedButton(
                    enabled = !saveBusy,
                    onClick = {
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
                    },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                ) {
                    if (saveBusy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Save QR · সেভ")
                    }
                }
                OutlinedButton(
                    enabled = !saveBusy,
                    onClick = {
                        val intent = buildQrShareIntent(context, qr, currentInvite.code)
                        if (intent != null) {
                            try {
                                context.startActivity(intent)
                            } catch (_: Exception) {
                                saveFeedback = "No app available for sharing."
                            }
                        } else {
                            saveFeedback = "Could not prepare the QR image for sharing."
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                ) {
                    Text("Share · শেয়ার")
                }
            }
            saveFeedback?.let {
                Text(text = it, style = MaterialTheme.typography.bodySmall)
            }
        }
        when (inviteStatus) {
            "PENDING" -> Text(
                text = "অন্য ফোনের অপেক্ষায়… · Waiting for the other device",
                style = MaterialTheme.typography.bodySmall
            )
            "PAIRED" -> Text(
                text = "যুক্ত হয়েছে ✓ ${claimerEmail ?: "helper"}-এর সাথে",
                style = MaterialTheme.typography.bodySmall
            )
            else -> {}
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
                            pairingError = "Could not cancel: ${describeFirestoreError(e)}"
                        }
                    }
                }
            ) {
                Text("Cancel code · কোড বাতিল")
            }
        }
        inviteSyncError?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    } else {
        if (currentInvite != null) {
            val statusText = when (inviteStatus) {
                "PAIRED" -> "Paired ✓ · ${claimerEmail ?: "helper"}-এর সাথে যুক্ত — যখন চান নতুন কোড বানান।"
                "CANCELLED" -> "কোড বাতিল হয়েছে। নিচে নতুন কোড বানান।"
                else -> "কোডের সময় শেষ। নিচে নতুন কোড বানান।"
            }
            Text(
                text = statusText,
                style = MaterialTheme.typography.bodySmall
            )
        }
        // Phase 10 — the pairing-mode choice: family (permanent) vs guest
        // (2-hour temporary window). The code display above tells the user
        // which kind is currently live.
        Text(
            text = "কোন ধরনের যুক্তি বানাবেন? · Which kind of link?",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                enabled = creatingKind == null,
                onClick = { createCode(REL_KIND_FAMILY) },
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 52.dp)
            ) {
                Text(
                    if (creatingKind == REL_KIND_FAMILY) "…" else "Family কোড · স্থায়ী",
                    fontWeight = FontWeight.SemiBold
                )
            }
            OutlinedButton(
                enabled = creatingKind == null,
                onClick = { createCode(REL_KIND_GUEST) },
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 52.dp)
            ) {
                Text(
                    if (creatingKind == REL_KIND_GUEST) "…" else "Guest কোড · ২ ঘণ্টা",
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
        Text(
            text = "Family = একবার যুক্ত, পরে আর কোড লাগবে না। Guest = ২ ঘণ্টার সাময়িক " +
                "লিংক — মেয়াদ শেষে নিজেই শেষ হয়ে যায়।",
            style = MaterialTheme.typography.bodySmall
        )
    }

    // ---------- Section B: this phone is the helper ----------
    HorizontalDivider()
    Text(
        text = "আপনি কি সাহায্য করবেন? কোড দিন · You are the helper",
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold
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
            modifier = Modifier.heightIn(min = 48.dp)
        ) {
            Text("Scan QR · স্ক্যান")
        }
    }
    if (showScanner) {
        QrScanDialog(
            onCode = { code ->
                codeInput = code
                redeemMessage = null
                showScanner = false
            },
            onDismiss = { showScanner = false }
        )
    }
    BigButton(
        enabled = !redeeming && codeInput.isNotBlank(),
        onClick = {
            scope.launch {
                redeeming = true
                redeemMessage = null
                try {
                    val rel = redeemCode(user, codeInput)
                    redeemMessage = if (rel.kind == REL_KIND_GUEST) {
                        "Guest যুক্ত ✓ — ${rel.recipientEmail} (${rel.recipientDeviceModel})-কে " +
                            "২ ঘণ্টার মধ্যে সাহায্য করতে পারবেন। প্রতিটি সেশনে আলাদা অনুমোদন লাগবে।"
                    } else {
                        "Paired ✓ — এখন আপনি ${rel.recipientEmail} (${rel.recipientDeviceModel})-কে সাহায্য করতে পারবেন।"
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
    ) {
        if (redeeming) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp
            )
        } else {
            Text("Pair with code · কোড দিয়ে যুক্ত করুন")
        }
    }
    redeemMessage?.let { message ->
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = if (message.startsWith("Paired") || message.startsWith("Guest যুক্ত")) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.error
            }
        )
    }

    // ---------- Section C: trusted relationships ----------
    HorizontalDivider()
    Text(
        text = "Paired phones · যুক্ত ফোন",
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold
    )
    if (relationships.isEmpty()) {
        if (relationshipsSyncError != null) {
            Text(
                text = relationshipsSyncError!!,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        } else {
            Text(
                text = "এখনো কোনো ফোন যুক্ত হয়নি। উপরের কোড দিয়ে যুক্ত করুন।",
                style = MaterialTheme.typography.bodySmall
            )
        }
    } else {
        relationships.forEach { rel ->
            val isGuest = rel.kind == REL_KIND_GUEST
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    val role = if (rel.helperUid == user.uid) "আপনি সাহায্য করছেন" else "সাহায্য করছে"
                    val otherEmail =
                        if (rel.helperUid == user.uid) rel.recipientEmail else rel.helperEmail
                    Text(
                        text = "$role: ${otherEmail.ifBlank { "(no email)" }}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    // Phase 10 — the Family vs Guest badge. A guest row also
                    // shows the honest remaining window (refreshed every 30 s).
                    if (isGuest) {
                        val remainingMin =
                            ((rel.guestExpiresAtMillis - relNow) / 60000L).coerceAtLeast(0)
                        val badge = when {
                            rel.status != "ACTIVE" -> "Guest (সাময়িক) · ${rel.status}"
                            rel.guestExpiresAtMillis <= 0L -> "Guest (সাময়িক)"
                            remainingMin >= 60L ->
                                "Guest (সাময়িক) · বাকি ${remainingMin / 60L}ঘ ${remainingMin % 60L}মি"
                            else -> "Guest (সাময়িক) · বাকি ${remainingMin}মি"
                        }
                        Text(
                            text = badge,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = if (rel.status == "ACTIVE" && remainingMin > 0L) {
                                MaterialTheme.colorScheme.tertiary
                            } else {
                                MaterialTheme.colorScheme.error
                            }
                        )
                    } else {
                        Text(
                            text = "Family · স্থায়ী",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    if (rel.helperUid == user.uid && rel.recipientDeviceId.isNotBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "${rel.recipientDeviceModel.ifBlank { "Unknown device" }} · ${rel.status}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            PresenceBadge(uid = rel.recipientUid, deviceId = rel.recipientDeviceId)
                        }
                    } else {
                        Text(
                            text = "${rel.recipientDeviceModel.ifBlank { "Unknown device" }} · ${rel.status}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    // Phase 6/10 — helper can ask to start a support session,
                    // but a guest link only while its window is still live.
                    if (rel.status == "ACTIVE" && rel.helperUid == user.uid) {
                        if (rel.guestLive) {
                            BigButton(
                                enabled = !sessionBusy && openSession == null,
                                onClick = { startSessionFor(rel) }
                            ) {
                                Text("Start session · সেশন শুরু করুন (অনুমতি চাইবে)")
                            }
                        } else {
                            Text(
                                text = "Guest মেয়াদ শেষ — আর সেশন শুরু করা যাবে না। নতুন " +
                                    "Guest কোড দিন। (Guest window over — share a new code.)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
                if (!isGuest && rel.status == "ACTIVE") {
                    OutlinedButton(
                        enabled = revokingId != rel.id,
                        modifier = Modifier.heightIn(min = 48.dp),
                        onClick = {
                            revokingId = rel.id
                            scope.launch {
                                try {
                                    revokeRelationship(rel.id)
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    pairingError = "Could not revoke: ${describeFirestoreError(e)}"
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
                        enabled = revokingId != rel.id,
                        modifier = Modifier.heightIn(min = 48.dp),
                        onClick = {
                            revokingId = rel.id
                            scope.launch {
                                try {
                                    removeGuestRelationship(rel.id)
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    pairingError = "Could not remove: ${describeFirestoreError(e)}"
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

    // ---------- Section D: Phase 6 — support session ----------
    HorizontalDivider()
    Text(
        text = "Support session · সাপোর্ট সেশন",
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold
    )
    Text(
        text = "Pairing alone never grants access. Every session starts with a live " +
            "permission ask on the phone that needs help, and ends automatically. " +
            "শুধু যুক্ত থাকা মানেই অনুমতি নয় — প্রতিটি সেশনে আলাদা অনুমতি, আর নির্দিষ্ট সময়ে নিজেই শেষ।",
        style = MaterialTheme.typography.bodySmall
    )

    // Android 13+ gates notifications behind a runtime permission.
    val notifPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) != PackageManager.PERMISSION_GRANTED
    ) {
        OutlinedButton(
            onClick = {
                notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            },
            modifier = Modifier.heightIn(min = 48.dp)
        ) {
            Text("Enable notifications · নোটিফিকেশন চালু করুন")
        }
        Text(
            text = "Needed to see help requests while this app is closed or in the background. · " +
                "অ্যাপ বন্ধ থাকলেও সাহায্যের অনুরোধ দেখতে এটা দরকার।",
            style = MaterialTheme.typography.bodySmall
        )
    }

    val currentSession = openSession
    when {
        currentSession != null && currentSession.status == "REQUESTED" &&
            currentSession.recipientUid == user.uid -> {
            // This phone was asked — the prominent consent card.
            val secondsLeft =
                ((currentSession.respondDeadlineMillis - sessionNow) / 1000).coerceAtLeast(0)
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "${currentSession.helperEmail.ifBlank { "Someone" }} " +
                            "এই ফোনে সাহায্য করতে চাইছেন।",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (currentSession.kind == "guest") {
                        Text(
                            text = "Guest (সাময়িক) সাহায্যকারী — এই লিংক দেওয়ার ২ ঘণ্টা পরে " +
                                "নিজেই মুছে যাবে; সেশনটিও তার মেয়াদের মধ্যেই শেষ হবে।",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                    Text(
                        text = "চিনি মানুষ হলে তবেই অনুমতি দিন। অনুরোধটি ${mmss(secondsLeft)}-এ শেষ হবে। " +
                            "(Allow only if you know them.)",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            enabled = !sessionBusy,
                            onClick = { answerSession(currentSession, true) },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 52.dp)
                        ) {
                            Text("Allow · অনুমতি দিন", fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(
                            enabled = !sessionBusy,
                            onClick = { answerSession(currentSession, false) },
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
        currentSession != null && currentSession.status == "REQUESTED" -> {
            // Helper waiting for the answer.
            val secondsLeft =
                ((currentSession.respondDeadlineMillis - sessionNow) / 1000).coerceAtLeast(0)
            Text(
                text = "${currentSession.recipientEmail}-কে অনুমতির অনুরোধ পাঠানো হয়েছে… " +
                    "${mmss(secondsLeft)}-এ শেষ হবে।" +
                    if (currentSession.kind == "guest") " · Guest (সাময়িক)" else "",
                style = MaterialTheme.typography.bodyMedium
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
                            sessionMessage = "Could not cancel: ${describeFirestoreError(e)}"
                        } finally {
                            sessionBusy = false
                        }
                    }
                }
            ) {
                Text("Cancel request · অনুরোধ বাতিল")
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
                    Text(
                        text = if (currentSession.helperUid == user.uid) {
                            "সেশন চালু — আপনি ${currentSession.recipientEmail}-কে সাহায্য করছেন।"
                        } else {
                            "সেশন চালু — ${currentSession.helperEmail} আপনাকে সাহায্য করছেন।"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (currentSession.kind == "guest") {
                        Text(
                            text = "Guest (সাময়িক) — সেশনটি guest লিংকের মেয়াদের মধ্যেই শেষ হবে।",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                    Text(
                        text = "স্বয়ংক্রিয়ভাবে ${mmss(secondsLeft)}-এ শেষ হবে। আগে শেষ করতে পারেন। " +
                            "(Ends automatically in ${mmss(secondsLeft)}.)",
                        style = MaterialTheme.typography.bodySmall
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
                        Text("End session · সেশন শেষ করুন")
                    }
                }
            }
        }
        else -> {
            if (lastFinishedSession != null) {
                val lastFinished = lastFinishedSession!!
                val byWhom = when (lastFinished.endedBy) {
                    null, "" -> ""
                    "timeout" -> " (auto-timeout)"
                    lastFinished.helperUid -> " (ended by helper)"
                    lastFinished.recipientUid -> " (ended by recipient)"
                    else -> ""
                }
                Text(
                    text = "Last session: ${lastFinished.status.lowercase()}$byWhom",
                    style = MaterialTheme.typography.bodySmall
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
                Text("Watch screen · স্ক্রিন দেখুন (শুধু দেখা)")
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
                                Text("✕ Close · বন্ধ", fontWeight = FontWeight.SemiBold)
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
                            "Tap the picture for full screen · ছবিতে ট্যাপ করলে ফুলস্ক্রিন হবে।",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            // Phase 8 — the touch-control switch, global navigation buttons
            // and per-command delivery feedback. Phase 9 (T3): a clear section
            // header, equal-width ≥48dp nav buttons, bilingual state copy.
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Touch control · রিমোট টাচ",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Switch(checked = assistMode, onCheckedChange = { assistMode = it })
            }
            Text(
                text = if (assistMode)
                    "চালু — ট্যাপ, সোয়াইপ ও লং-প্রেস এখন তাদের ফোনে যাবে (তাদের Remote assist চালু থাকতে হবে)। ছবির ওপর: ট্যাপ = ট্যাপ · ধরে রাখা = লং-প্রেস · টেনে ছাড়া = সোয়াইপ।"
                else
                    "বন্ধ — শুধু দেখা যাচ্ছে। ট্যাপ পাঠাতে সুইচ চালু করুন।",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (assistMode) {
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
                WideOutlinedButton(onClick = { fullscreen = true }) {
                    Text("⛶ Full screen · ফুলস্ক্রিন")
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
                Text("Stop watching · দেখা বন্ধ")
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
                Text("Share screen · স্ক্রিন শেয়ার করুন")
            }
            Text(
                text = "চালুর পর এই ফোনে যা দেখা যায় সবই সাহায্যকারী দেখতে পাবেন — আপনি বন্ধ করা " +
                    "বা সেশন শেষ হওয়া পর্যন্ত। দূর থেকে কিছু নিয়ন্ত্রণ করা হয় না।",
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
                Text("Stop sharing · শেয়ার বন্ধ")
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
                    text = "Remote assist চালু ✓ — সাহায্যকারী এই স্ক্রিনে ট্যাপও করতে পারবেন।",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            } else {
                Text(
                    text = "Remote assist বন্ধ — সাহায্যকারী শুধু দেখতে পারবেন, ট্যাপ করতে পারবেন না।",
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
                    Text("Turn on Remote assist · রিমোট টাচ চালু করুন")
                }
                Text(
                    text = "একবারই সেটআপ লাগবে: তালিকায় \"Family Remote Support assist\" খুঁজে চালু করুন। " +
                        "⚠️ \"Accessibility menu shortcut\" নয় — সেটা কাজ করবে না। এই সার্ভিস স্ক্রিনের " +
                        "কিছুই পড়ে না — শুধু ট্যাপ করতে পারে।",
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
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium
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
    Text(
        text = " · $label",
        style = MaterialTheme.typography.bodySmall,
        color = when {
            label == "Online" -> MaterialTheme.colorScheme.primary
            unavailable -> MaterialTheme.colorScheme.error
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        fontWeight = FontWeight.Medium
    )
}
