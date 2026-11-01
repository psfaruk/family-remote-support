package com.family.remote.support

import android.content.Context
import android.content.Intent
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

// ═══════════════════════════════════════════════════════════════════════
// v0.44.0 (smooth-app wave) — the LOCAL crash journal.
//
// Owner report (install of v0.43.0/63): "সেটিং ট্যাব এ ক্লিক করলে ক্র্যাশ
// করে" + an install-time "report to the mobile company" dialog. The app
// deliberately ships ZERO analytics/crash SDKs (§S-2 of the Phase 12
// security review — and that stays true), so nobody could see the actual
// stack trace. This journal closes that gap WITHOUT any network:
//
//   • App.onCreate installs a default UncaughtExceptionHandler.
//   • On any uncaught exception it appends one entry — timestamp, app
//     version, Android version, device, thread and the FULL stack trace —
//     to a plain file in the app's private filesDir.
//   • It then delegates to the previous (system) handler: the process
//     dies exactly as Android intends — we only captured evidence.
//   • The entry is capped (last MAX_ENTRY entries) and lives ONLY on the
//     phone. Settings → About shows a "Crash report" row WHEN (and only
//     when) an entry exists, with view / share / delete. Sharing uses the
//     local Android share sheet — the OWNER decides where it goes.
//
// Privacy posture: no identifiers beyond device model/app version, no
// coordinates (this file never touches location data), no auto-upload,
// wiped with app data on uninstall, and never readable by other apps.
// ═══════════════════════════════════════════════════════════════════════

object CrashJournal {

    private const val FILE_NAME = "crash_journal.log"

    /** Keep the last few crashes — enough history, bounded storage. */
    private const val MAX_ENTRIES = 8

    /** One entry separator (scannable by eye and by grep). */
    private const val SEPARATOR = "────────────────────────────────────────"

    @Volatile
    private var installed = false

    /** Install once per process (App.onCreate). Idempotent. */
    fun install(context: Context) {
        if (installed) return
        installed = true
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            // Capture evidence first; NEVER let the capture itself break
            // the crash path (a crash inside the crash handler would lose
            // BOTH the evidence and the system dialog).
            try {
                appendEntry(appContext, thread, throwable)
            } catch (_: Throwable) {
                // ignore — delegate below regardless
            }
            // Android's own dialog ("report to the manufacturer") and
            // process death proceed exactly as before.
            previous?.uncaughtException(thread, throwable)
        }
    }

    /** The journal text, or null when no crash was captured. */
    fun report(context: Context): String? {
        return try {
            val file = file(context)
            if (!file.exists()) null else file.readText().ifBlank { null }
        } catch (_: Exception) {
            null
        }
    }

    /** Wipe the journal (the Settings "delete" action). */
    fun clear(context: Context) {
        try {
            file(context).delete()
        } catch (_: Exception) {
            // best effort — nothing else to do
        }
    }

    /**
     * Hand the journal to the Android share sheet (the Settings "share"
     * action). The owner chooses the destination — nothing is uploaded by
     * the app itself.
     */
    fun share(context: Context): Boolean {
        val text = report(context) ?: return false
        return try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(
                    Intent.EXTRA_SUBJECT,
                    "Family Remote Support — crash report (v${BuildConfig.VERSION_NAME})"
                )
                putExtra(Intent.EXTRA_TEXT, text)
            }
            context.startActivity(Intent.createChooser(intent, null))
            true
        } catch (_: Exception) {
            // no share target on this device
            false
        }
    }

    // ── internals ─────────────────────────────────────────────────────────

    private fun file(context: Context): File =
        File(context.filesDir, FILE_NAME)

    @Synchronized
    private fun appendEntry(context: Context, thread: Thread, throwable: Throwable) {
        val stack = StringWriter().also { throwable.printStackTrace(PrintWriter(it)) }.toString()
        val entry = buildString {
            append(SEPARATOR).append('\n')
            append("Time: ").append(java.text.SimpleDateFormat(
                "yyyy-MM-dd HH:mm:ss", java.util.Locale.US
            ).format(java.util.Date())).append('\n')
            append("App: v").append(BuildConfig.VERSION_NAME)
                .append(" (build ").append(BuildConfig.VERSION_CODE).append(")\n")
            append("Device: ").append(Build.MANUFACTURER).append(' ')
                .append(Build.MODEL).append(" (Android ").append(Build.VERSION.RELEASE)
                .append(", API ").append(Build.VERSION.SDK_INT).append(")\n")
            append("Thread: ").append(thread.name).append('\n')
            append("Stack:\n").append(stack.trim()).append('\n')
        }
        val f = file(context)
        val existing = if (f.exists()) {
            try { f.readText() } catch (_: Exception) { "" }
        } else {
            ""
        }
        // Newest FIRST (the top of the file is what the user reads first).
        val entries = (listOf(entry) + existing.split(SEPARATOR))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .take(MAX_ENTRIES)
        f.writeText(entries.joinToString("\n$SEPARATOR\n\n", postfix = "\n"))
    }
}
