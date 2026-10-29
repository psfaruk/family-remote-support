package com.family.remote.support.ui

import android.util.Patterns
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.family.remote.support.BuildConfig
import com.family.remote.support.R

// ═══════════════════════════════════════════════════════════════════════
// Phase 11.7 (v0.16.0) — the dedicated login / sign-up gate.
//
// Owner direction: "যখন কোনো ইউজার অ্যাপ টি ওপেন করবে, নতুন ইউজার হিসাবে
// লগিং ও সাইন আপ পেজ থাকবে" — a brand-new user now lands on THIS screen,
// not inside the 4-tab shell. Every design decision follows
// docs/UI_UX_DESIGN_SYSTEM.md (the one system):
//   • Modern & Minimal  → one column, generous Spacing, one primary action
//   • Friendly & Warm   → Bengali-first copy, amber accent chip, hero mark
//   • Dark Mode         → every color from MaterialTheme (no literals)
//   • Professional      → explicit trust line + visible security promise
//   • Playful (rare)    → the small amber heart on the hero
//
// Pure presentation: all auth logic lives in MainActivity's suspend
// functions; this file only collects input and reports friendly errors.
// ═══════════════════════════════════════════════════════════════════════

private const val MODE_LOGIN = "login"
private const val MODE_SIGNUP = "signup"

/**
 * Full-screen auth gate. Shown exactly when no FirebaseUser is signed in.
 *
 * @param busy          shared busy flag while an auth call is in flight
 * @param authMessage   friendly error text (Bengali) from the last attempt
 * @param authNotice    friendly success text (e.g. reset link sent)
 * @param onClearMessages  clears both message slots
 * @param onGoogleSignIn   runs the existing Google Credential Manager path
 * @param onEmailSignIn    (email, password) → sign in with email/password
 * @param onEmailSignUp    (name, email, password) → create the account
 * @param onPasswordReset  (email) → send the reset link
 */
@Composable
fun AuthGate(
    busy: Boolean,
    authMessage: String?,
    authNotice: String?,
    onClearMessages: () -> Unit,
    onGoogleSignIn: () -> Unit,
    onEmailSignIn: (String, String) -> Unit,
    onEmailSignUp: (String, String, String) -> Unit,
    onPasswordReset: (String) -> Unit
) {
    var mode by rememberSaveable { mutableStateOf(MODE_LOGIN) }
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var confirm by rememberSaveable { mutableStateOf("") }
    var showPw by rememberSaveable { mutableStateOf(false) }
    // Inline field errors appear only after the first real attempt —
    // a fresh screen never shouts at the user (docs §10-L honesty).
    var tried by rememberSaveable { mutableStateOf(false) }

    val emailOk = Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()
    val pwOk = password.length >= 6
    val nameOk = mode == MODE_LOGIN || name.trim().length >= 2
    val confirmOk = mode == MODE_LOGIN || confirm == password
    val canSubmit = emailOk && pwOk && nameOk && confirmOk

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .systemBarsPadding()
            .imePadding()
            .padding(horizontal = 24.dp)
            .padding(top = 40.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ── Hero (Friendly & Warm) ──────────────────────────────────────
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.semantics(mergeDescendants = true) {}
        ) {
            Icon(
                imageVector = Icons.Filled.Favorite,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.height(2.dp))
            // Sits directly on the theme canvas — reads cleanly in both themes.
            Image(
                painter = painterResource(id = R.drawable.ic_auth_hero),
                contentDescription = "Family Remote Support প্রতীক",
                modifier = Modifier.size(128.dp)
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Family Remote Support",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "পরিবারের ফোনে দূর থেকে বিশ্বস্ত সহায়তা —\nসবসময় স্পষ্ট অনুমতি নিয়ে।",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(10.dp))
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "আপনার অনুমতি ছাড়া কেউ ঢুকতে পারবে না",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        // ── Mode toggle (Modern & Minimal) ──────────────────────────────
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(modifier = Modifier.padding(4.dp)) {
                ToggleSide(
                    label = "লগইন",
                    selected = mode == MODE_LOGIN,
                    modifier = Modifier.weight(1f)
                ) {
                    mode = MODE_LOGIN
                    tried = false
                    onClearMessages()
                }
                ToggleSide(
                    label = "নতুন অ্যাকাউন্ট",
                    selected = mode == MODE_SIGNUP,
                    modifier = Modifier.weight(1f)
                ) {
                    mode = MODE_SIGNUP
                    tried = false
                    onClearMessages()
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // ── Fields ──────────────────────────────────────────────────────
        if (mode == MODE_SIGNUP) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("আপনার নাম") },
                leadingIcon = {
                    Icon(Icons.Filled.Person, contentDescription = null,
                        modifier = Modifier.size(20.dp))
                },
                singleLine = true,
                isError = tried && !nameOk,
                supportingText = if (tried && !nameOk) {
                    { Text("কমপক্ষে ২ অক্ষরের নাম লিখুন") }
                } else null,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
        }
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("ইমেইল") },
            leadingIcon = {
                Icon(Icons.Filled.Email, contentDescription = null,
                    modifier = Modifier.size(20.dp))
            },
            singleLine = true,
            isError = tried && !emailOk,
            supportingText = if (tried && !emailOk) {
                { Text("সঠিক ইমেইল ঠিকানা লিখুন") }
            } else null,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next
            ),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("পাসওয়ার্ড") },
            leadingIcon = {
                Icon(Icons.Filled.Lock, contentDescription = null,
                    modifier = Modifier.size(20.dp))
            },
            trailingIcon = {
                TextButton(
                    onClick = { showPw = !showPw },
                    modifier = Modifier.heightIn(min = 44.dp)
                ) {
                    Text(
                        text = if (showPw) "লুকান" else "দেখুন",
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            },
            singleLine = true,
            isError = tried && !pwOk,
            supportingText = if (tried && !pwOk) {
                { Text("পাসওয়ার্ড কমপক্ষে ৬ অক্ষরের হতে হবে") }
            } else null,
            visualTransformation = if (showPw) VisualTransformation.None
            else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = if (mode == MODE_LOGIN) ImeAction.Done else ImeAction.Next
            ),
            modifier = Modifier.fillMaxWidth()
        )
        if (mode == MODE_SIGNUP) {
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = confirm,
                onValueChange = { confirm = it },
                label = { Text("পাসওয়ার্ড আবার লিখুন") },
                leadingIcon = {
                    Icon(Icons.Filled.Lock, contentDescription = null,
                        modifier = Modifier.size(20.dp))
                },
                singleLine = true,
                isError = tried && !confirmOk,
                supportingText = if (tried && !confirmOk) {
                    { Text("দুটি পাসওয়ার্ড মিলছে না") }
                } else null,
                visualTransformation = if (showPw) VisualTransformation.None
                else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(Modifier.height(16.dp))

        // ── Messages (FriendlyErrorCard pattern, docs §7.2) ─────────────
        authMessage?.let { msg ->
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Filled.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .padding(top = 12.dp)
                            .size(20.dp)
                    )
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 10.dp, vertical = 10.dp)
                    )
                    IconButton(
                        onClick = onClearMessages,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Clear,
                            contentDescription = "বার্তা বন্ধ করুন",
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
        authNotice?.let { msg ->
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        // ── Primary action (BigButton pattern: ≥52dp) ───────────────────
        Button(
            onClick = {
                if (!canSubmit) {
                    tried = true
                } else if (mode == MODE_LOGIN) {
                    onEmailSignIn(email.trim(), password)
                } else {
                    onEmailSignUp(name.trim(), email.trim(), password)
                }
            },
            enabled = !busy,
            shape = MaterialTheme.shapes.medium,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
        ) {
            if (busy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(Modifier.width(10.dp))
                Text("অপেক্ষা করুন…")
            } else {
                Text(
                    text = if (mode == MODE_LOGIN) "লগইন করুন" else "অ্যাকাউন্ট তৈরি করুন",
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
        if (mode == MODE_LOGIN) {
            TextButton(
                onClick = {
                    if (!Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()) {
                        tried = true
                    } else {
                        onPasswordReset(email.trim())
                    }
                },
                enabled = !busy
            ) {
                Text(
                    text = "পাসওয়ার্ড ভুলে গেছেন?",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // ── Or divider + Google path ────────────────────────────────────
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant)
            )
            Text(
                text = "অথবা",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp)
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant)
            )
        }
        Spacer(Modifier.height(14.dp))
        OutlinedButton(
            onClick = onGoogleSignIn,
            enabled = !busy,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.AccountCircle,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = "Google দিয়ে চালিয়ে যান",
                style = MaterialTheme.typography.labelLarge
            )
        }

        Spacer(Modifier.height(28.dp))

        // ── Trust footer (Professional) ─────────────────────────────────
        Text(
            text = "অ্যাকাউন্ট ও ফোনের যুক্তি শুধু আপনার নিজের Firebase প্রজেক্টে থাকে। " +
                "পেয়ার থাকলেও প্রতিটি সেশনে আলাদা অনুমতি দরকার।",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "v${BuildConfig.VERSION_NAME}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** One half of the login/sign-up toggle pill (docs §7.2 ThemeOptionRow style). */
@Composable
private fun ToggleSide(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer
                else Color.Transparent
            )
            .clickable(onClick = onClick)
            .heightIn(min = 46.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
