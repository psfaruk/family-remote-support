package com.family.remote.support.ui.add

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.family.remote.support.PermissionWizard
import com.family.remote.support.R
import com.family.remote.support.ui.components.BackTitleRow
import com.family.remote.support.ui.components.BigButton

/**
 * ui/add/PermissionsScreen.kt — the post-pairing permission shortcut (W3;
 * plan §2.3's "রিডিম → সফল → পারমিশন-উইজার্ড শর্টকাট"): an honest intro and
 * the ONE canonical wizard ([PermissionWizard] — never a second variant).
 * The wizard auto-opens ONCE per screen entry (rememberSaveable: rotation
 * never re-launches it, back from the wizard lands on this intro).
 */
@Composable
fun PermissionsScreen(onBack: () -> Unit) {
    var wizardOpen by rememberSaveable { mutableStateOf(false) }

    // The shortcut IS the wizard — open it on first composition.
    LaunchedEffect(Unit) { wizardOpen = true }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        BackTitleRow(title = stringResource(R.string.btn_setup), onBack = onBack)
        Text(
            text = stringResource(R.string.perm_route_intro),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        BigButton(
            text = stringResource(R.string.btn_setup),
            onClick = { wizardOpen = true },
            modifier = Modifier.padding(top = 8.dp),
        )
    }

    if (wizardOpen) {
        PermissionWizard(
            visible = true,
            onDismiss = { wizardOpen = false },
        )
    }
}
