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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
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

            Card {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Phase 3 — Sign in & register this device",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (!webClientConfigured) {
                        Text(
                            text = "Sign-in is disabled in this build: the Web Client ID is not set yet. " +
                                "Finish the Firebase console steps in the web guide, send the Web Client ID " +
                                "to your assistant, and install the next build.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    if (signedInUser == null) {
                        Button(
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
                                Text("Sign in with Google")
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
                        OutlinedButton(
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
                            Text("Sign out")
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
            }

            Card {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Device registry (Firestore)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = deviceStatus,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (deviceStatus.startsWith("Registration failed")) {
                        OutlinedButton(onClick = { registerRetryKey++ }) {
                            Text("Retry registration")
                        }
                    }
                    if (signedInUser != null) {
                        StatusRow(label = "Presence (Phase 5)", value = presenceStatus)
                        StatusRow(label = "Push notifications", value = pushStatus)
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
            }

            PairingCard(signedInUser = signedInUser)

            Card {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Phase 2 build check",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Firebase project remote-family-support below means Firebase is wired up correctly.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            StatusRow(label = "App version", value = "0.8.3 (Phase 7)")
            StatusRow(label = "Package", value = "com.family.remote.support")
            StatusRow(label = "Firebase project", value = firebaseProjectId)
            StatusRow(label = "Firebase app ID", value = firebaseAppId)
            StatusRow(label = "Next milestone", value = "Phase 8 - assisted remote control")
        }
    }
}

/**
 * Phase 4 — pairing card. The phone that needs help creates a 5-minute code;
 * the helper's phone types the same code. Redemption is one atomic Firestore
 * transaction, verified again by the security rules. Works across two Google
 * accounts (during testing, two accounts on this same phone work as well).
 */
@Composable
fun PairingCard(signedInUser: FirebaseUser?) {
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Phase 4 — Pair a trusted family device",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "The phone that needs help shows a 5-minute code. The helper's phone " +
                    "types the same code. Pairing registers a trusted relationship — every " +
                    "future support session will still ask for separate permission.",
                style = MaterialTheme.typography.bodyMedium
            )

            if (signedInUser == null) {
                Text(
                    text = "Sign in first — pairing needs a Google account.",
                    style = MaterialTheme.typography.bodySmall
                )
            } else {
                PairingSections(signedInUser)
            }
        }
    }
}

@Composable
private fun PairingSections(user: FirebaseUser) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var invite by remember { mutableStateOf<InviteCode?>(null) }
    var inviteStatus by remember { mutableStateOf<String?>(null) }
    var claimerEmail by remember { mutableStateOf<String?>(null) }
    var inviteSyncError by remember { mutableStateOf<String?>(null) }
    var nowMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    var creating by remember { mutableStateOf(false) }
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
                    "Request sent — waiting for ${rel.recipientEmail} to allow."
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
                        "Session allowed ✓ — it ends automatically in 15 minutes."
                } else {
                    denySession(s.id, user.uid)
                    sessionMessage = "Request denied."
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
                sessionMessage = "Session ended."
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

    val createCode: () -> Unit = {
        scope.launch {
            creating = true
            pairingError = null
            try {
                val deviceId = stableDeviceId(context)
                invite = createInvitation(user, deviceId, Build.MODEL)
                inviteStatus = "PENDING"
                claimerEmail = null
                inviteSyncError = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                pairingError = "Could not create a code: ${describeFirestoreError(e)}"
            } finally {
                creating = false
            }
        }
    }

    // ---------- Section A: this phone needs help ----------
    HorizontalDivider()
    Text(
        text = "Does this phone need help?",
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold
    )

    if (showCode && currentInvite != null) {
        Text(
            text = currentInvite.code,
            style = MaterialTheme.typography.bodyLarge,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Share this code only with the family member who will help you.",
            style = MaterialTheme.typography.bodySmall
        )
        val totalSeconds = (currentInvite.expiresAtMillis - nowMillis) / 1000
        Text(
            text = "Expires in ${totalSeconds / 60}:${(totalSeconds % 60)
                .toString().padStart(2, '0')}",
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
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    enabled = !saveBusy,
                    onClick = {
                        copiedJustNow =
                            copyToClipboard(context, "Pairing code", currentInvite.code)
                    }
                ) {
                    Text(if (copiedJustNow) "Copied ✓" else "Copy code")
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
                    }
                ) {
                    if (saveBusy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Save QR")
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
                    }
                ) {
                    Text("Share")
                }
            }
            saveFeedback?.let {
                Text(text = it, style = MaterialTheme.typography.bodySmall)
            }
        }
        when (inviteStatus) {
            "PENDING" -> Text(
                text = "Waiting for the other device…",
                style = MaterialTheme.typography.bodySmall
            )
            "PAIRED" -> Text(
                text = "Paired ✓ with ${claimerEmail ?: "the helper"}",
                style = MaterialTheme.typography.bodySmall
            )
            else -> {}
        }
        if (inviteStatus == "PENDING") {
            OutlinedButton(
                enabled = !creating,
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
                Text("Cancel this code")
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
                "PAIRED" -> "Paired ✓ with ${claimerEmail ?: "the helper"} — you can create a new code anytime."
                "CANCELLED" -> "Code cancelled. Create a new one below."
                else -> "Code expired. Create a new one below."
            }
            Text(
                text = statusText,
                style = MaterialTheme.typography.bodySmall
            )
        }
        Button(enabled = !creating, onClick = createCode) {
            if (creating) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp
                )
            } else {
                Text(
                    if (currentInvite == null) "Create pairing code (5 min)"
                    else "Create a new code"
                )
            }
        }
    }

    // ---------- Section B: this phone is the helper ----------
    HorizontalDivider()
    Text(
        text = "Are you the helper? Redeem a code",
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
            label = { Text("Pairing code") },
            singleLine = true,
            modifier = Modifier.weight(1f)
        )
        Button(
            enabled = !redeeming,
            onClick = { showScanner = true }
        ) {
            Text("Scan QR")
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
    Button(
        enabled = !redeeming && codeInput.isNotBlank(),
        onClick = {
            scope.launch {
                redeeming = true
                redeemMessage = null
                try {
                    val rel = redeemCode(user, codeInput)
                    redeemMessage =
                        "Paired ✓ You can now help ${rel.recipientEmail} (${rel.recipientDeviceModel})."
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
            Text("Pair with code")
        }
    }
    redeemMessage?.let { message ->
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = if (message.startsWith("Paired")) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.error
            }
        )
    }

    // ---------- Section C: trusted relationships ----------
    HorizontalDivider()
    Text(
        text = "Paired devices",
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
                text = "No trusted relationships yet. Pair with a code above.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    } else {
        relationships.forEach { rel ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    val role = if (rel.helperUid == user.uid) "You help" else "Helped by"
                    val otherEmail =
                        if (rel.helperUid == user.uid) rel.recipientEmail else rel.helperEmail
                    Text(
                        text = "$role ${otherEmail.ifBlank { "(no email)" }}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
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
                    // Phase 6 — helper can ask to start a support session.
                    if (rel.status == "ACTIVE" && rel.helperUid == user.uid) {
                        Button(
                            enabled = !sessionBusy && openSession == null,
                            onClick = { startSessionFor(rel) }
                        ) {
                            Text("Start session (asks permission)")
                        }
                    }
                }
                if (rel.status == "ACTIVE") {
                    OutlinedButton(
                        enabled = revokingId != rel.id,
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
                        Text("Revoke")
                    }
                }
            }
        }
    }

    // ---------- Section D: Phase 6 — support session ----------
    HorizontalDivider()
    Text(
        text = "Phase 6 — Support session",
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold
    )
    Text(
        text = "Pairing alone never grants access. Every session starts with a live " +
            "permission ask on the phone that needs help, and ends automatically.",
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
            }
        ) {
            Text("Enable notifications")
        }
        Text(
            text = "Needed to see help requests while this app is closed or in the background.",
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
                        text = "${currentSession.helperEmail.ifBlank { "Someone" }} wants to help with this phone.",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Allow only if you know them and want help right now. " +
                            "The ask expires in ${mmss(secondsLeft)}.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            enabled = !sessionBusy,
                            onClick = { answerSession(currentSession, true) }
                        ) {
                            Text("Allow")
                        }
                        OutlinedButton(
                            enabled = !sessionBusy,
                            onClick = { answerSession(currentSession, false) }
                        ) {
                            Text("Deny")
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
                text = "Asking ${currentSession.recipientEmail} to allow a support session… " +
                    "The ask expires in ${mmss(secondsLeft)}.",
                style = MaterialTheme.typography.bodyMedium
            )
            OutlinedButton(
                enabled = !sessionBusy,
                onClick = {
                    scope.launch {
                        sessionBusy = true
                        try {
                            cancelSession(currentSession.id, user.uid)
                            sessionMessage = "Request cancelled."
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
                Text("Cancel request")
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
                            "Support session active — you are helping ${currentSession.recipientEmail}."
                        } else {
                            "Support session active — ${currentSession.helperEmail} is helping you."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Ends automatically in ${mmss(secondsLeft)}. You can end it earlier below.",
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
                    OutlinedButton(
                        enabled = !sessionBusy,
                        onClick = { finishSession(currentSession) }
                    ) {
                        Text("End session")
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
            OutlinedButton(
                onClick = {
                    ScreenShareEngine.startHelper(context, sessionId, myUid, otherUid)
                }
            ) {
                Text("Watch screen (view only)")
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
            // Maps a tap inside the letterboxed video box to normalized (0..1)
            // coordinates of the REAL remote frame and sends one "cmd" signal.
            // Used both inline and in the full-screen dialog.
            fun assistTapModifier(): Modifier = Modifier
                .onSizeChanged { boxPx = it }
                .pointerInput(frameSize) {
                    detectTapGestures { off ->
                        val fs = frameSize ?: return@detectTapGestures
                        val (fw, fh) = fs
                        if (fw <= 0 || fh <= 0 || boxPx.width <= 0 || boxPx.height <= 0) {
                            return@detectTapGestures
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
                        ScreenShareEngine.sendAssistTap(nx, ny)
                    }
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
                                        if (assistMode && state == ShareState.LIVE) assistTapModifier()
                                        else Modifier
                                    )
                            )
                            Text(
                                text = "✕  Close",
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(16.dp)
                                    .clickable { fullscreen = false }
                            )
                        }
                    }
                } else {
                    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                        val targetHeight = frameSize?.let { (w, h) ->
                            if (h > 0) maxWidth * (h.toFloat() / w.toFloat()) else 420.dp
                        } ?: 420.dp
                        VideoRendererBox(
                            egl,
                            Modifier
                                .fillMaxWidth()
                                .height(targetHeight.coerceAtMost(480.dp))
                                .then(
                                    if (assistMode && state == ShareState.LIVE) assistTapModifier()
                                    else Modifier.clickable { fullscreen = true }
                                )
                        )
                    }
                    Text(
                        text = if (assistMode && state == ShareState.LIVE)
                            "Touch control is ON — tap the picture to tap THEIR phone."
                        else
                            "Tap the picture for full screen.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            // Phase 8 — the touch-control switch, global navigation buttons
            // and per-command delivery feedback.
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Touch control", style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.weight(1f))
                Switch(checked = assistMode, onCheckedChange = { assistMode = it })
            }
            Text(
                text = if (assistMode)
                    "ON — taps go to the recipient's phone (their Remote assist must be enabled)."
                else
                    "OFF — view only. Switch on to tap on the recipient's phone.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (assistMode) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { ScreenShareEngine.sendAssistNav("back") }) {
                        Text("◀ Back")
                    }
                    OutlinedButton(onClick = { ScreenShareEngine.sendAssistNav("home") }) {
                        Text("⌂ Home")
                    }
                    OutlinedButton(onClick = { ScreenShareEngine.sendAssistNav("recents") }) {
                        Text("▦ Recents")
                    }
                }
                OutlinedButton(onClick = { fullscreen = true }) {
                    Text("⛶ Full screen")
                }
            }
            assistStatus?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium
                )
            }
            OutlinedButton(onClick = { ScreenShareEngine.stopHelper() }) {
                Text("Stop watching")
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
            OutlinedButton(
                onClick = { consentLauncher.launch(projectionManager.createScreenCaptureIntent()) }
            ) {
                Text("Share screen with helper")
            }
            Text(
                text = "Everything visible on this phone can be seen by the helper " +
                    "until you stop or the session ends. Nothing is controlled remotely.",
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
                    text = "Preview — this is what the helper sees:",
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
            OutlinedButton(onClick = { ScreenShareService.stop(context) }) {
                Text("Stop sharing")
            }
            // Phase 8 (v0.9.0) — Remote assist: state + one-time enablement.
            // Without the service the helper stays view-only forever; with it
            // they may tap (nothing more). The poll keeps the card honest if
            // the user flips the service in system settings mid-session.
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            var assistOn by remember { mutableStateOf(AssistService.isReady) }
            LaunchedEffect(Unit) {
                while (true) {
                    assistOn = AssistService.isReady
                    delay(1500)
                }
            }
            if (assistOn) {
                Text(
                    text = "Remote assist is ON — the helper can also tap on this screen.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
            } else {
                Text(
                    text = "Remote assist is OFF — the helper can watch but cannot tap.",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedButton(onClick = {
                    try {
                        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    } catch (_: Exception) {
                    }
                }) {
                    Text("Turn on Remote assist")
                }
                Text(
                    text = "One-time setup: in Accessibility choose \"Family Remote " +
                        "Support assist\" and switch it on. The service reads " +
                        "nothing — it can only perform taps, and you can switch " +
                        "it off any time.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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
