package com.family.remote.support

import android.content.Context

/**
 * v0.45.0 (audit P13) — local relationship nicknames ("আব্বু", "আপু", …).
 *
 * The audit's finding: notifications and the events log address family
 * members by raw EMAIL ("rahim.xyz@gmail.com wants to help") — for the
 * elderly recipients this app is built for, an email is not a name. The fix
 * starts where it hurts most (notifications) and stays strictly LOCAL:
 *
 *   • storage — one SharedPreferences map relId → nickname on THIS phone;
 *     zero new Firestore reads/writes (quota discipline), zero server
 *     schema change, wiped nowhere automatically (relIds are opaque ids,
 *     not PII — sign-out hygiene of the events log covers its own copy).
 *   • editor — the member detail screen's new nickname row (FamilyScreens).
 *   • consumers — AskNotifier (ask + answer notifications) prefers the
 *     nickname and falls back to the email exactly as before.
 *
 * Migration note: with no nickname set, EVERY surface renders byte-for-byte
 * as v0.44 did — this can never change behaviour on its own.
 */
object Nicknames {

    private const val PREFS = "nicknames"

    /** The nickname for a relationship, or null when none was set. */
    fun get(context: Context, relId: String): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(relId, null)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

    /** Set (or clear, with a blank name) the nickname for a relationship. */
    fun set(context: Context, relId: String, nickname: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(relId, nickname.trim())
            .apply()
    }

    /**
     * The display name for a counterpart: nickname when set, otherwise the
     * [fallbackEmail] (or [fallback] when even that is blank). One rule for
     * every notification surface.
     */
    fun displayName(context: Context, relId: String?, fallbackEmail: String, fallback: String): String {
        if (relId.isNullOrBlank()) return fallbackEmail.ifBlank { fallback }
        val nickname = get(context, relId)
        if (nickname != null) return nickname
        return fallbackEmail.ifBlank { fallback }
    }
}
