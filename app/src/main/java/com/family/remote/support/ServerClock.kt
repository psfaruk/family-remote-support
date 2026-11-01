package com.family.remote.support

import com.google.firebase.Timestamp

/**
 * v0.45.0 (audit P7) — the server-time offset estimator.
 *
 * Every session deadline check used to compare Firestore SERVER timestamps
 * (respondDeadline, sessionEndsAt, requestedAt) against the PHONE's own
 * wall clock. A phone with "Automatic date & time" off — common on elderly
 * family devices — skews 5–10 minutes and then:
 *   • an ask shows "already expired" the moment it arrives, or
 *   • the ask's rules-checked WRITE itself fails (firestore.rules requires
 *     respondDeadline within (request.time, request.time + 5 m) of SERVER
 *     time, and a fast phone pushes the deadline outside that window).
 *
 * Fix: estimate `serverNow - localNow` once per process from any snapshot
 * the app ALREADY receives, and add it to every local "now" that is
 * compared against a server timestamp. Zero extra Firestore reads — the
 * feeders are:
 *   • SessionNotifier — the ask doc's server-stamped createdAt vs the
 *     local clock at listener delivery;
 *   • PushTokenService — the FCM data message's server-stamped fields.
 *
 * The estimate includes snapshot delivery latency (typically < 2 s), which
 * is orders of magnitude better than the 5–10 minute skew it corrects.
 * A sane floor/ceiling (±1 day) keeps a single corrupt snapshot from ever
 * poisoning the offset.
 */
object ServerClock {

    @Volatile
    private var offsetMillis: Long = 0L

    /** Server-corrected "now" in System.currentTimeMillis() terms. */
    fun now(): Long = System.currentTimeMillis() + offsetMillis

    /**
     * Fold one (serverTimestamp, localReceiveTime) observation into the
     * offset. Ignored when the delta is absurd (> ±1 day) or when it
     * agrees with the current estimate to within [TOLERANCE_MILLIS] (no
     * churn on every snapshot). Only moves when it genuinely learned
     * something new.
     */
    fun syncFrom(serverTimestamp: Timestamp?, localReceiveMillis: Long) {
        if (serverTimestamp == null) return
        val observed = serverTimestamp.toDate().time - localReceiveMillis
        if (kotlin.math.abs(observed) > MAX_ABS_OFFSET_MILLIS) return
        if (kotlin.math.abs(observed - offsetMillis) <= TOLERANCE_MILLIS) return
        offsetMillis = observed
    }

    private const val TOLERANCE_MILLIS = 2_000L

    /** A phone more than a day off has bigger problems than deadlines. */
    private const val MAX_ABS_OFFSET_MILLIS = 24 * 60 * 60 * 1000L
}
