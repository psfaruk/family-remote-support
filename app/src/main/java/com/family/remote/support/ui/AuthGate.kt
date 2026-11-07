package com.family.remote.support.ui

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import com.family.remote.support.BuildConfig
import com.family.remote.support.FirestoreUnreachableException
import com.family.remote.support.R
import com.family.remote.support.awaitWithTimeout
import com.family.remote.support.ui.components.BigButton
import com.family.remote.support.ui.components.TextActionButton
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.launch

/**
 * ui/AuthGate.kt — the sign-in screen (W2 of the frontend rebuild;
 * FRONTEND_ARCHITECTURE §3 places it here, slimmed MainActivity hosts it).
 *
 * Three honest paths, no fabricated state:
 *  • Google — Credential Manager (the modern, snooze-friendly bottom-sheet
 *    account picker) with the project's Web Client ID → Firebase token
 *    exchange;
 *  • Email/password — sign-in, account creation, password reset;
 *  • every failure renders through the fe_* / auth_* localized strings —
 *    raw stack dumps are never user-facing.
 *
 * The gate performs sign-in ONLY; the shell's AuthStateListener flips the
 * whole UI the moment Firebase says so (no manual "navigate past myself").
 */
// ═══════════════════════════════════════════════════════════════════

private const val MODE_SIGNIN = "signin"
private const val MODE_SIGNUP = "signup"
private const val MODE_RESET = "reset"

@Composable
fun AuthGate(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }

    var mode by rememberSaveable { mutableStateOf(MODE_SIGNIN) }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var showPw by rememberSaveable { mutableStateOf(false) }
    // null | "google" | "email"
    var busy by rememberSaveable { mutableStateOf<String?>(null) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var notice by rememberSaveable { mutableStateOf<String?>(null) }

    fun fail(e: Exception) {
        busy = null
        val friendly = friendlyAuthError(context, e)
        if (friendly != null) error = friendly
    }

    fun doGoogle() {
        error = null
        notice = null
        busy = "google"
        scope.launch {
            try {
                val option = GetGoogleIdOption.Builder()
                    .setServerClientId(BuildConfig.GOOGLE_WEB_CLIENT_ID)
                    .setFilterByAuthorizedAccounts(false)
                    .build()
                val request = GetCredentialRequest.Builder()
                    .addCredentialOption(option)
                    .build()
                val response = credentialManager.getCredential(context, request)
                // googleid 1.1.1: createFrom takes the credential's data
                // Bundle (createFrom(Credential) does not exist in 1.1.1).
                val google = GoogleIdTokenCredential.createFrom(response.credential.data)
                FirebaseAuth.getInstance()
                    .signInWithCredential(GoogleAuthProvider.getCredential(google.idToken, null))
                    .awaitWithTimeout("signing in with Google")
                // success → the shell's AuthStateListener takes it from here
                busy = null
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                fail(e)
            }
        }
    }

    fun doEmail() {
        error = null
        notice = null
        val mail = email.trim()
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(mail).matches()) {
            error = context.getString(R.string.auth_err_email)
            return
        }
        if (mode != MODE_RESET && password.length < 6) {
            error = context.getString(R.string.auth_err_pw)
            return
        }
        busy = "email"
        scope.launch {
            try {
                val auth = FirebaseAuth.getInstance()
                when (mode) {
                    MODE_SIGNUP ->
                        auth.createUserWithEmailAndPassword(mail, password)
                            .awaitWithTimeout("creating the account")
                    MODE_RESET -> {
                        auth.sendPasswordResetEmail(mail)
                            .awaitWithTimeout("sending the reset link")
                        notice = context.getString(R.string.auth_reset_sent, mail)
                        mode = MODE_SIGNIN
                        password = ""
                    }
                    else ->
                        auth.signInWithEmailAndPassword(mail, password)
                            .awaitWithTimeout("signing in")
                }
                busy = null
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                fail(e)
            }
        }
    }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            // Hero — the emerald trust shield (§92 recolor).
            Image(
                painter = painterResource(R.drawable.ic_auth_hero),
                contentDescription = null, // decorative; the app name follows
                modifier = Modifier.size(144.dp),
            )
            Spacer(Modifier.height(20.dp))
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.home_tagline),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 360.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // ── Google (brand button: brand asset on a neutral surface) ──
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .clickable(enabled = busy == null) { doGoogle() },
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    ) {
                        if (busy == "google") {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(
                                imageVector = GoogleGLogo,
                                contentDescription = null, // the label says "Google"
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = stringResource(R.string.auth_google),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }

                // ── divider "or" ──
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    HorizontalDivider(modifier = Modifier.weight(1f))
                    Text(
                        text = stringResource(R.string.auth_or),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    HorizontalDivider(modifier = Modifier.weight(1f))
                }

                // ── email / password ──
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text(stringResource(R.string.auth_email)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    enabled = busy == null,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (mode != MODE_RESET) {
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text(stringResource(R.string.auth_pw_hint)) },
                        singleLine = true,
                        visualTransformation =
                            if (showPw) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = {
                            IconButton(onClick = { showPw = !showPw }) {
                                Text(
                                    text = stringResource(
                                        if (showPw) R.string.auth_hide else R.string.auth_show
                                    ),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        },
                        enabled = busy == null,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                // ── honest feedback ──
                if (error != null) {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = error!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        )
                    }
                }
                if (notice != null) {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = notice!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        )
                    }
                }

                // ── primary email action ──
                val actionLabel = when (mode) {
                    MODE_SIGNUP -> stringResource(R.string.auth_btn_signup)
                    MODE_RESET -> stringResource(R.string.auth_reset_btn)
                    else -> stringResource(R.string.auth_btn_login)
                }
                BigButton(
                    text = actionLabel,
                    onClick = { doEmail() },
                    loading = busy == "email",
                    modifier = Modifier.fillMaxWidth(),
                )

                // ── mode links ──
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    TextActionButton(
                        text = stringResource(
                            if (mode == MODE_SIGNIN) R.string.auth_signup
                            else R.string.auth_back_to_login
                        ),
                        onClick = {
                            error = null
                            mode = if (mode == MODE_SIGNIN) MODE_SIGNUP else MODE_SIGNIN
                        },
                    )
                    if (mode != MODE_RESET) {
                        TextActionButton(
                            text = stringResource(R.string.auth_forgot),
                            onClick = {
                                error = null
                                mode = MODE_RESET
                            },
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            Text(
                text = stringResource(R.string.auth_footer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 320.dp),
            )
        }
    }
}

/**
 * Map an auth failure onto the localized fe_- and auth_-prefixed strings.
 * Returns null for a plain user cancellation (the sheet dismissed — that is
 * not an error the family needs to read).
 */
private fun friendlyAuthError(context: Context, e: Exception): String? = when {
    e is GetCredentialCancellationException -> null
    e is FirestoreUnreachableException -> context.getString(R.string.fe_no_internet)
    e is FirebaseAuthException -> when (e.errorCode) {
        "ERROR_INVALID_EMAIL" -> context.getString(R.string.auth_err_email)
        "ERROR_WRONG_PASSWORD",
        "ERROR_INVALID_CREDENTIAL",
        "ERROR_INVALID_LOGIN_CREDENTIALS",
        "ERROR_USER_MISMATCH",
        -> context.getString(R.string.fe_wrong_cred)
        "ERROR_EMAIL_ALREADY_IN_USE",
        "ERROR_CREDENTIAL_ALREADY_IN_USE",
        -> context.getString(R.string.fe_in_use)
        "ERROR_WEAK_PASSWORD" -> context.getString(R.string.fe_weak_pw)
        "ERROR_USER_NOT_FOUND",
        "ERROR_USER_DISABLED",
        -> context.getString(R.string.fe_no_account)
        "ERROR_TOO_MANY_REQUESTS" -> context.getString(R.string.fe_too_many)
        "ERROR_NETWORK_REQUEST_FAILED" -> context.getString(R.string.fe_no_internet)
        "ERROR_OPERATION_NOT_ALLOWED" -> context.getString(R.string.fe_not_allowed)
        else -> context.getString(R.string.auth_failed)
    }
    else -> context.getString(R.string.auth_failed)
}

// ── The Google "G" brand mark (the official four-color glyph; a brand
//    asset, not a theme color — the design token rule governs UI chrome,
//    not logos) ─────────────────────────────────────────────────────────

private val GoogleGLogo: ImageVector by lazy {
    ImageVector.Builder(
        name = "GoogleG",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        addPath(
            PathBuilder().apply {
                moveTo(12.0f, 5.3f)
                curveToRelative(1.6f, 0.0f, 3.0f, 0.55f, 4.1f, 1.63f)
                lineToRelative(3.06f, -3.06f)
                curveTo(17.3f, 2.13f, 14.87f, 1.0f, 12.0f, 1.0f)
                curveTo(7.7f, 1.0f, 3.99f, 3.47f, 2.18f, 7.07f)
                lineToRelative(3.57f, 2.77f)
                curveTo(6.6f, 7.2f, 9.05f, 5.3f, 12.0f, 5.3f)
                close()
            }.nodes,
            fill = SolidColor(Color(0xFFEA4335)),
        )
        addPath(
            PathBuilder().apply {
                moveTo(23.5f, 12.27f)
                curveToRelative(0.0f, -0.79f, -0.07f, -1.55f, -0.2f, -2.27f)
                horizontalLineTo(12.0f)
                verticalLineToRelative(4.51f)
                horizontalLineToRelative(6.45f)
                curveToRelative(-0.28f, 1.48f, -1.12f, 2.73f, -2.39f, 3.58f)
                lineToRelative(3.46f, 2.68f)
                curveToRelative(2.02f, -1.87f, 3.98f, -4.6f, 3.98f, -8.5f)
                close()
            }.nodes,
            fill = SolidColor(Color(0xFF4285F4)),
        )
        addPath(
            PathBuilder().apply {
                moveTo(5.75f, 14.16f)
                arcTo(6.7f, 6.7f, 0.0f, false, true, 5.4f, 12.0f)
                curveToRelative(0.0f, -0.75f, 0.13f, -1.47f, 0.35f, -2.16f)
                lineTo(2.18f, 7.07f)
                arcTo(11.0f, 11.0f, 0.0f, false, false, 1.0f, 12.0f)
                curveToRelative(0.0f, 1.78f, 0.42f, 3.45f, 1.18f, 4.93f)
                lineToRelative(3.57f, -2.77f)
                close()
            }.nodes,
            fill = SolidColor(Color(0xFFFBBC05)),
        )
        addPath(
            PathBuilder().apply {
                moveTo(12.0f, 23.0f)
                curveToRelative(2.97f, 0.0f, 5.46f, -0.98f, 7.28f, -2.66f)
                lineToRelative(-3.46f, -2.68f)
                curveToRelative(-0.96f, 0.64f, -2.19f, 1.03f, -3.82f, 1.03f)
                curveToRelative(-2.95f, 0.0f, -5.4f, -1.9f, -6.25f, -4.54f)
                lineToRelative(-3.57f, 2.77f)
                curveTo(3.99f, 20.53f, 7.7f, 23.0f, 12.0f, 23.0f)
                close()
            }.nodes,
            fill = SolidColor(Color(0xFF34A853)),
        )
    }.build()
}
