package com.family.remote.support

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import java.security.MessageDigest
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

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
    if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_TOKEN) {
        val googleId = GoogleIdTokenCredential.createFrom(credential.data)
        val firebaseCredential = GoogleAuthProvider.getCredential(googleId.idToken, null)
        val result = FirebaseAuth.getInstance().signInWithCredential(firebaseCredential).await()
        return result.user ?: throw IllegalStateException("Sign-in returned no user")
    }
    throw IllegalStateException("Unexpected credential type: ${credential.type}")
}

/** Stable per-device id (hashed ANDROID_ID) used as the Firestore doc id. */
private fun stableDeviceId(context: Context): String {
    val androidId = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ANDROID_ID
    ) ?: "unknown-device"
    val digest = MessageDigest.getInstance("SHA-256").digest(androidId.toByteArray())
    return digest.joinToString("") { "%02x".format(it) }.take(24)
}

/** Write/merge the user profile + this device's record in Firestore. */
private suspend fun registerDevice(context: Context, user: FirebaseUser): String {
    val db = FirebaseFirestore.getInstance()
    val profile = hashMapOf(
        "displayName" to (user.displayName ?: ""),
        "email" to (user.email ?: ""),
        "photoUrl" to (user.photoUrl?.toString() ?: ""),
        "updatedAt" to FieldValue.serverTimestamp()
    )
    db.collection("users").document(user.uid)
        .set(profile, SetOptions.merge()).await()

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
        .set(device, SetOptions.merge()).await()
    return deviceId
}

/** Read back the device list registered under this account. */
private suspend fun loadDevices(user: FirebaseUser): List<String> {
    val snapshot = FirebaseFirestore.getInstance()
        .collection("users").document(user.uid)
        .collection("devices")
        .get()
        .await()
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

    // After sign-in (or on app open while signed in): refresh device record + list.
    LaunchedEffect(signedInUser?.uid) {
        val user = signedInUser ?: return@LaunchedEffect
        deviceStatus = "Registering this device…"
        try {
            val deviceId = registerDevice(context, user)
            deviceStatus = "Registered ✓ (id ${deviceId.take(8)}…)"
            deviceList = loadDevices(user)
        } catch (e: Exception) {
            deviceStatus = "Registration failed: ${e.message ?: "unknown error"}"
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

            StatusRow(label = "App version", value = "0.4.0 (Phase 3)")
            StatusRow(label = "Package", value = "com.family.remote.support")
            StatusRow(label = "Firebase project", value = firebaseProjectId)
            StatusRow(label = "Firebase app ID", value = firebaseAppId)
            StatusRow(label = "Next milestone", value = "Phase 4 - QR pairing")
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
