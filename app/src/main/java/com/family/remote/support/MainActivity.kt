package com.family.remote.support

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.family.remote.support.ui.AppTheme
import com.family.remote.support.ui.LangPrefBus
import com.family.remote.support.ui.nav.RootShell

/**
 * MainActivity — the slim single-activity host
 * (FRONTEND_ARCHITECTURE §3: "setContent + shell wiring only").
 *
 * W1 rebuild: the 5,884-line monolith that never made it into the repo is
 * being reconstructed wave by wave (docs/FRONTEND_UI_PLAN_BN.md পর্ব ৭).
 * What this host owns, permanently:
 *
 *  • Locale — attachBaseContext runs every Context through
 *    [LangPrefBus.wrap] so all of composition (and every stringResource)
 *    resolves in the family's chosen language; fresh installs start in
 *    বাংলা (the Owner's directive, set_lang_info).
 *  • Startup contracts, in order, BEFORE composition:
 *      - [NotificationsLog.load] — the local JSONL event history read
 *        once (NotificationsLog's own KDoc contract: "the Recent events
 *        section renders from the very first frame").
 *      - [DeepLinks.fromIntent] — a cold start from a notification parks
 *        the wanted route in the bus; RootShell consumes it.
 *  • Edge-to-edge — with the manifest's adjustResize, insets (not window
 *    panning) drive layout; the shell pads itself above nav bar + IME.
 *  • onNewIntent — singleTop + setIntent + [DeepLinks.fromIntent]: a
 *    tap while the app is alive routes instead of stacking a duplicate
 *    activity (duplicate listeners/load bug, audit P4).
 *
 * Everything else — auth gate, presence ticker, consent wiring — belongs
 * to the shell and the wave screens, not the host.
 */
class MainActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LangPrefBus.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Startup contract #1 — read the local notification history once,
        // before the first frame needs it.
        NotificationsLog.load(this)

        // Startup contract #2 — cold-start deep link from a notification
        // (AskNotifier parks the route; RootShell navigates to it).
        DeepLinks.fromIntent(intent)

        enableEdgeToEdge()
        setContent {
            AppTheme {
                RootShell()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // A notification tap on a live app — route it, don't stack a
        // duplicate host (singleTop, audit P4).
        DeepLinks.fromIntent(intent)
    }
}
