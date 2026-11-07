package com.family.remote.support

/**
 * v0.56.0 (worklog §103 — "সেশন আবার চালু করা যাচ্ছে না, স্থায়ী সমাধান নাই")
 * — the session model moved OUT of SessionRepo.kt into this SDK-free file so
 * the JVM unit tests (SessionStaleTest) can exercise the stale/zombie logic
 * without any Firebase class on the test classpath.
 *
 * Nothing here touches Firestore: [SessionView] is the read-only projection,
 * [isStaleOpen] is the pure time math. Same package, so every existing
 * reference keeps compiling unchanged.
 */
data class SessionView(
    val id: String,
    /** Relationship doc id == pairKey == helperUid_recipientUid. */
    val relId: String,
    val helperUid: String,
    val helperEmail: String,
    val recipientUid: String,
    val recipientEmail: String,
    val recipientDeviceModel: String,
    val status: String, // REQUESTED | ACTIVE | ENDED | DENIED | CANCELLED | EXPIRED
    val requestedAtMillis: Long,
    val respondDeadlineMillis: Long,
    /** 0 until the session is granted. */
    val sessionEndsAtMillis: Long,
    /** "helperEmail" | "recipientEmail" of the ender, or "timeout". */
    val endedBy: String?,
    /** Phase 10 — family | guest. A guest session can never outlive the
     *  guest link's window (enforced at grant time and by the rules). */
    val kind: String = "family"
)

/**
 * v0.22.0 (Phase 18.1 — Owner bug: "রিবুটের পর family আর কানেক্ট হয় না") —
 * a REQUESTED ask whose answer window is over, or an ACTIVE session past its
 * 15-min lifetime (+4 s skew grace on both), is a ZOMBIE left behind by a
 * killed/rebooted phone. Any participant may perform the matching cleanup
 * (REQUESTED→EXPIRED / ACTIVE→ENDED("timeout") — both transitions are already
 * allowed by the security rules).
 *
 * v0.56.0 (§103) — TWO permanent hardenings:
 *  1. [nowMillis] MUST be SERVER time (ServerClock.now()). The deadline and
 *     sessionEndsAt fields are written from server-corrected clocks and
 *     re-checked server-side by the rules; comparing them against a raw
 *     device clock let a 5–10-min-slow phone see a long-dead ask as "still
 *     waiting" FOREVER — the zombie blocked every new ask (the recurring
 *     "can't start a session" the Owner kept hitting).
 *  2. Missing/zero timestamp fields no longer make a doc IMMUNE to cleanup.
 *     A REQUESTED doc without respondDeadline, or an ACTIVE doc without
 *     sessionEndsAt, used to pass every staleness check forever. Now the
 *     ask's own age decides: REQUESTED > 10 min (rules cap the answer window
 *     at 5 min), ACTIVE > 25 min (rules cap a granted session at 20 min).
 *     Both fields are rules-mandatory on every legit write, so a doc missing
 *     them is already corrupt — expiring/ending it is the honest repair.
 */
fun SessionView.isStaleOpen(nowMillis: Long): Boolean = when (status) {
    "REQUESTED" -> when {
        respondDeadlineMillis > 0L ->
            nowMillis > respondDeadlineMillis + STALE_GRACE_MILLIS
        requestedAtMillis > 0L ->
            nowMillis > requestedAtMillis + STALE_NO_DEADLINE_MILLIS
        // No deadline AND no requestedAt: fully corrupt — expire is safe.
        else -> true
    }
    "ACTIVE" -> when {
        sessionEndsAtMillis > 0L ->
            nowMillis > sessionEndsAtMillis + STALE_GRACE_MILLIS
        requestedAtMillis > 0L ->
            nowMillis > requestedAtMillis + STALE_NO_END_MILLIS
        // Same reasoning as above — END is the honest repair.
        else -> true
    }
    else -> false
}

/** Skew grace on the deadline/lifetime checks (matches the UI's lazy expiry). */
const val STALE_GRACE_MILLIS = 4_000L

/** A REQUESTED doc with a missing deadline is stale after this age. */
const val STALE_NO_DEADLINE_MILLIS = 10 * 60_000L

/** An ACTIVE doc with a missing sessionEndsAt is stale after this age. */
const val STALE_NO_END_MILLIS = 25 * 60_000L
