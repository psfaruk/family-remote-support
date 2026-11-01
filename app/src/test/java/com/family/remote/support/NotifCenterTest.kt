// ═══════════════════════════════════════════════════════════════════════
// v0.46.0 (external-audit G1) — the notification-center gate: category
// mapping, filter set-algebra, read-state transitions and row-route
// mapping are PURE Kotlin (NotifCenter.kt — zero Android imports), so the
// whole G1 decision surface is proven here, in CI, before any assemble
// (same JUnit4 pattern as MovementClassifierTest / PlaceAlertEngineTest).
// The on-device ride (chips render, a tap navigates, the badge counts)
// remains the Owner's two-phone checklist item, as always.
//
// Route assertions use the LITERAL route strings on purpose: AppRoutes is
// the single source of truth, and these tests double as its guard — any
// change to a route constant breaks exactly the test that names it.
// ═══════════════════════════════════════════════════════════════════════
package com.family.remote.support

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NotifCenterTest {

    private val now = 1_000_000_000_000L // fixed wall clock for every case

    /** at decreases with id, so higher id = newer — the log's newest-first order. */
    private fun ev(
        id: Long,
        type: String,
        readAt: Long? = null,
        arg2: String? = null
    ) = NotifEvent(
        id = id, type = type, at = now - id * 1000L,
        arg = "a@b.c", arg2 = arg2, readAt = readAt
    )

    // One fixture per KNOWN type + one unknown future type, newest-first.
    private val asked = ev(1, "session_asked", arg2 = "s1")
    private val started = ev(2, "session_started", arg2 = "s1")
    private val ended = ev(3, "session_ended", arg2 = "s1")
    private val denied = ev(4, "session_denied", arg2 = "s2")
    private val cancelled = ev(5, "session_cancelled", arg2 = "s2")
    private val shareOn = ev(6, "sharing_started")
    private val shareOff = ev(7, "sharing_stopped")
    private val place = ev(8, "place_alert")
    private val future = ev(9, "some_future_type")
    private val all = listOf(future, place, shareOff, shareOn, cancelled, denied, ended, started, asked)

    // ── category mapping ───────────────────────────────────────────────

    @Test
    fun `every known type maps to its category`() {
        assertEquals(NotifCategory.CONNECTION, notifCategoryOf("session_asked"))
        assertEquals(NotifCategory.CONNECTION, notifCategoryOf("session_started"))
        assertEquals(NotifCategory.CONNECTION, notifCategoryOf("session_ended"))
        assertEquals(NotifCategory.SECURITY, notifCategoryOf("session_denied"))
        assertEquals(NotifCategory.SECURITY, notifCategoryOf("session_cancelled"))
        assertEquals(NotifCategory.LOCATION, notifCategoryOf("sharing_started"))
        assertEquals(NotifCategory.LOCATION, notifCategoryOf("sharing_stopped"))
        assertEquals(NotifCategory.LOCATION, notifCategoryOf("place_alert"))
    }

    @Test
    fun `unknown future type maps to null - never mis-filed`() {
        assertNull(notifCategoryOf("some_future_type"))
        assertNull(notifCategoryOf(""))
    }

    // ── filters ────────────────────────────────────────────────────────

    @Test
    fun `ALL returns the list unchanged in order`() {
        assertEquals(all, all.applyNotifFilter(NotifFilter.ALL))
    }

    @Test
    fun `UNREAD keeps only unread rows in order`() {
        val events = listOf(
            asked.copy(readAt = now),
            started,
            ended.copy(readAt = now - 5_000L),
            shareOn
        )
        assertEquals(listOf(started, shareOn), events.applyNotifFilter(NotifFilter.UNREAD))
    }

    @Test
    fun `UNREAD is orthogonal to categories`() {
        // The unknown future type is unread → visible under UNREAD too.
        assertTrue(future in all.applyNotifFilter(NotifFilter.UNREAD))
    }

    @Test
    fun `CONNECTION keeps exactly the session-lifecycle rows`() {
        // asked/started/ended only — denied and cancelled are SECURITY.
        assertEquals(
            listOf(ended, started, asked),
            all.applyNotifFilter(NotifFilter.CONNECTION)
        )
    }

    @Test
    fun `SECURITY keeps exactly the denied and cancelled rows`() {
        assertEquals(listOf(cancelled, denied), all.applyNotifFilter(NotifFilter.SECURITY))
    }

    @Test
    fun `LOCATION keeps exactly the sharing and place rows`() {
        assertEquals(
            listOf(place, shareOff, shareOn),
            all.applyNotifFilter(NotifFilter.LOCATION)
        )
    }

    @Test
    fun `unknown future type never lands in a category chip`() {
        listOf(NotifFilter.CONNECTION, NotifFilter.LOCATION, NotifFilter.SECURITY)
            .forEach { f -> assertTrue(future !in all.applyNotifFilter(f)) }
    }

    // ── read-state transitions ─────────────────────────────────────────

    @Test
    fun `marking one row stamps only that row`() {
        val events = listOf(asked, started, ended)
        val out = events.withOneMarkedRead(started.id, now)
        assertNull(out[0].readAt)
        assertEquals(now, out[1].readAt)
        assertNull(out[2].readAt)
    }

    @Test
    fun `re-marking an already-read row keeps the original stamp`() {
        val events = listOf(asked.copy(readAt = now - 60_000L))
        val out = events.withOneMarkedRead(asked.id, now)
        assertEquals(now - 60_000L, out[0].readAt)
    }

    @Test
    fun `marking an unknown id changes nothing`() {
        assertEquals(all, all.withOneMarkedRead(999L, now))
    }

    @Test
    fun `marking never reorders`() {
        val out = all.withOneMarkedRead(ended.id, now)
        assertEquals(all.map { it.id }, out.map { it.id })
    }

    @Test
    fun `mark-all stamps every unread row and keeps original stamps`() {
        val events = listOf(
            asked.copy(readAt = now - 60_000L),
            started,
            ended
        )
        val out = events.withAllMarkedRead(now)
        assertEquals(now - 60_000L, out[0].readAt) // original kept
        assertEquals(now, out[1].readAt)
        assertEquals(now, out[2].readAt)
    }

    @Test
    fun `unread count follows the readAt stamps`() {
        assertEquals(9, all.notifUnreadCount())
        assertEquals(0, all.withAllMarkedRead(now).notifUnreadCount())
        assertEquals(8, all.withOneMarkedRead(place.id, now).notifUnreadCount())
    }

    // ── row routes (literal strings on purpose — AppRoutes guard) ──────

    @Test
    fun `ask routes to home where the consent card lives`() {
        assertEquals("home", notifRouteFor("session_asked", "s1"))
    }

    @Test
    fun `session outcomes route to their detail screen when the id survived`() {
        assertEquals("home/sessions/s1", notifRouteFor("session_started", "s1"))
        assertEquals("home/sessions/s1", notifRouteFor("session_ended", "s1"))
        assertEquals("home/sessions/s2", notifRouteFor("session_denied", "s2"))
        assertEquals("home/sessions/s2", notifRouteFor("session_cancelled", "s2"))
    }

    @Test
    fun `session outcomes without an id fall back to home`() {
        assertEquals("home", notifRouteFor("session_started", null))
        assertEquals("home", notifRouteFor("session_ended", ""))
    }

    @Test
    fun `sharing rows route to tracking`() {
        assertEquals("tracking", notifRouteFor("sharing_started", null))
        assertEquals("tracking", notifRouteFor("sharing_stopped", null))
    }

    @Test
    fun `place alerts route to my places`() {
        assertEquals("tracking/places", notifRouteFor("place_alert", "uid_1"))
    }

    @Test
    fun `unknown types still land somewhere useful`() {
        assertEquals("home", notifRouteFor("some_future_type", "x"))
    }
}
