// ═══════════════════════════════════════════════════════════════════════
// v0.56.0 (worklog §103 — "সেশন আবার চালু করা যাচ্ছে না, স্থায়ী সমাধান
// নাই") — zombie-session staleness unit tests.
//
// The recurring "can't start a session" the Owner kept hitting had two
// clock-shaped roots, both pinned here:
//   1. the stale-cleanup compared SERVER-written deadlines against the RAW
//      DEVICE clock — a 5–10-min-slow phone saw a long-dead ask as "still
//      waiting" forever, and the zombie blocked every new ask;
//   2. a session doc whose respondDeadline/sessionEndsAt field was missing
//      (zero) passed every staleness check forever — immune to cleanup.
//
// isStaleOpen now (a) expects SERVER time as its argument, and (b) falls
// back to the ask's own age when the deadline/end timestamp is missing
// (REQUESTED > 10 min, ACTIVE > 25 min — the rules' hard ceilings are 5 and
// 20). These tests pin that contract. Pure JVM, no Android/Firebase
// dependency (the model lives in SessionModel.kt for exactly this reason) —
// same gate shape as the DeviceId/MovementClassifier suites
// (:app:testReleaseUnitTest runs it in CI before assemble).
// ═══════════════════════════════════════════════════════════════════════
package com.family.remote.support

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionStaleTest {

    private val now = 1_700_000_000_000L // arbitrary fixed "server now"

    private fun session(
        status: String,
        requestedAt: Long = now - 60_000L,
        respondDeadline: Long = now + 3 * 60_000L,
        sessionEndsAt: Long = 0L,
    ) = SessionView(
        id = "s1",
        relId = "helper_recipient",
        helperUid = "helper",
        helperEmail = "helper@example.com",
        recipientUid = "recipient",
        recipientEmail = "recipient@example.com",
        recipientDeviceModel = "Test Phone",
        status = status,
        requestedAtMillis = requestedAt,
        respondDeadlineMillis = respondDeadline,
        sessionEndsAtMillis = sessionEndsAt,
        endedBy = null,
        kind = "family",
    )

    // ---------- REQUESTED: the deadline math ----------

    @Test
    fun `requested before its deadline is not stale`() {
        assertFalse(session("REQUESTED").isStaleOpen(now))
    }

    @Test
    fun `requested inside the 4s grace is not stale`() {
        val s = session("REQUESTED", respondDeadline = now - 3_000L)
        assertFalse(s.isStaleOpen(now))
    }

    @Test
    fun `requested past its deadline plus grace is stale`() {
        val s = session("REQUESTED", respondDeadline = now - 5_000L)
        assertTrue(s.isStaleOpen(now))
    }

    @Test
    fun `requested long past its deadline is stale even from a slow phone`() {
        // The ask's deadline expired 8 minutes ago by SERVER time; the
        // device clock being 10 minutes slow must no longer rescue it.
        val s = session("REQUESTED", respondDeadline = now - 8 * 60_000L)
        assertTrue(s.isStaleOpen(now))
    }

    // ---------- REQUESTED: the missing-deadline fallback ----------

    @Test
    fun `requested without a deadline is not stale while young`() {
        val s = session("REQUESTED", requestedAt = now - 2 * 60_000L, respondDeadline = 0L)
        assertFalse(s.isStaleOpen(now))
    }

    @Test
    fun `requested without a deadline is stale after 10 minutes`() {
        val s = session("REQUESTED", requestedAt = now - 11 * 60_000L, respondDeadline = 0L)
        assertTrue(s.isStaleOpen(now))
    }

    @Test
    fun `requested with no timestamps at all is stale`() {
        val s = session("REQUESTED", requestedAt = 0L, respondDeadline = 0L)
        assertTrue(s.isStaleOpen(now))
    }

    // ---------- ACTIVE: the lifetime math ----------

    @Test
    fun `active before its end is not stale`() {
        val s = session("ACTIVE", sessionEndsAt = now + 10 * 60_000L)
        assertFalse(s.isStaleOpen(now))
    }

    @Test
    fun `active past its end plus grace is stale`() {
        val s = session("ACTIVE", sessionEndsAt = now - 5_000L)
        assertTrue(s.isStaleOpen(now))
    }

    @Test
    fun `active without an end is not stale while young`() {
        val s = session("ACTIVE", requestedAt = now - 5 * 60_000L, sessionEndsAt = 0L)
        assertFalse(s.isStaleOpen(now))
    }

    @Test
    fun `active without an end is stale after 25 minutes`() {
        // The rules cap a granted session at 20 minutes — 25 by the ask's
        // own age is safely past every legitimate lifetime.
        val s = session("ACTIVE", requestedAt = now - 26 * 60_000L, sessionEndsAt = 0L)
        assertTrue(s.isStaleOpen(now))
    }

    @Test
    fun `active with no timestamps at all is stale`() {
        val s = session("ACTIVE", requestedAt = 0L, sessionEndsAt = 0L)
        assertTrue(s.isStaleOpen(now))
    }

    // ---------- terminal statuses never re-open ----------

    @Test
    fun `terminal statuses are never stale`() {
        for (status in listOf("ENDED", "DENIED", "CANCELLED", "EXPIRED")) {
            val s = session(
                status,
                requestedAt = now - 24 * 60 * 60_000L,
                respondDeadline = now - 24 * 60 * 60_000L,
                sessionEndsAt = now - 24 * 60 * 60_000L,
            )
            assertFalse("status $status must never be stale-open", s.isStaleOpen(now))
        }
    }
}
