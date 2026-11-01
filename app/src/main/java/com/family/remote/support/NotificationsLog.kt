package com.family.remote.support

import android.content.Context
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

// ═══════════════════════════════════════════════════════════════════════
// M8 (v0.34.0) — the LOCAL notifications events log (G2 of
// FAMILY_GUEST_AUTHORIZATION.md): the M8 decision turned the notifications
// surface's in-memory list into a persisted history of REAL events.
//
// Persistence is one JSON file in context.filesDir — NO Firestore, no new
// listeners, ZERO extra network/cloud reads (quota discipline; the Phase 20
// free-tier incident is the standing counter-example). The honesty rule
// (NAVIGATION_MAP §2) still governs WHAT is recorded — only transitions the
// app ALREADY observes:
//   • session_asked    a REQUESTED ask appears addressed to this account
//   • session_started  REQUESTED → ACTIVE involving this account
//   • session_ended    an ACTIVE session left the open session watch
//   • sharing_started / sharing_stopped  the location FGS actually began /
//                      actually stopped (never any coordinates — §9 of
//                      LIVE_LOCATION_ARCHITECTURE.md, lock-screen safe)
// An event carries at most a counterpart email as payload, never coordinates
// or other sensitive values. The log is capped at the most recent 100 and
// cleared at sign-out (counterpart emails must not survive the account).
// File IO is fire-and-forget on a private IO scope: a failed persist never
// crashes or blocks the UI — the in-memory list stays authoritative.
//
// v0.46.0 (external-audit G1): NotifEvent gained readAt (schema v2).
// The migration is the parser itself — a v1 file simply carries no readAt
// field, which reads back as null = unread, the honest default (those rows
// were never marked read because the concept did not exist). The store
// also learned markRead / markAllRead / clearHistory; the pure list logic
// lives in NotifCenter.kt so CI proves it (NotifCenterTest).
// ═══════════════════════════════════════════════════════════════════════

/** One row of the local events log (top-level like PresenceView). */
data class NotifEvent(
    val id: Long,
    /** One of: session_asked | session_started | session_ended |
     * sharing_started | sharing_stopped. */
    val type: String,
    /** System-epoch millis (System.currentTimeMillis) at record time. */
    val at: Long,
    /** Counterpart email (or null) — never coordinates or payloads. */
    val arg: String? = null,
    /** Second label (or null) — the stable subject id (sessionId/placeId). */
    val arg2: String? = null,
    /**
     * v0.46.0 (G1) — the moment the user first READ this row, or null while
     * unread. Schema v2: absent in v1 files → null → honestly "never read".
     */
    val readAt: Long? = null
)

object NotificationsLog {

    private const val FILE_NAME = "notifications_log.json"

    /** Cap of the persisted + displayed history (M8 decision). */
    private const val MAX_EVENTS = 100

    private val _events = MutableStateFlow<List<NotifEvent>>(emptyList())

    /** Newest-first. The notifications screen collects this — nothing else. */
    val events: StateFlow<List<NotifEvent>> = _events.asStateFlow()

    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Chained write job: each persist JOINS the previous one, so writes land
     * strictly in call order even though Dispatchers.IO is a pool (the last
     * write always carries the newest snapshot — no out-of-order clobber).
     */
    private var persistJob: Job? = null

    private var nextId = 1L

    /**
     * Read the file once at startup (plain call from MainActivity.onCreate,
     * BEFORE composition — the "Recent events" section renders from the very
     * first frame). Defensive: a missing or corrupt file starts empty.
     */
    fun load(context: Context) {
        val file = File(context.filesDir, FILE_NAME)
        val text = try {
            if (!file.exists()) return
            file.readText()
        } catch (_: Exception) {
            return // unreadable — start empty
        }
        val loaded = mutableListOf<NotifEvent>()
        try {
            val arr = JSONArray(text)
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val type = o.optString("type")
                if (type.isBlank()) continue
                loaded += NotifEvent(
                    id = o.optLong("id", 0L),
                    type = type,
                    at = o.optLong("at", 0L),
                    arg = o.optStringOrNull("arg"),
                    arg2 = o.optStringOrNull("arg2"),
                    // v0.46.0 (G1) — schema v2 read-back; v1 rows (no field)
                    // read as null = unread, the honest default.
                    readAt = if (o.has("readAt") && !o.isNull("readAt")) {
                        o.optLong("readAt")
                    } else null
                )
            }
        } catch (_: Exception) {
            // Corrupt file — start empty, never crash.
            loaded.clear()
        }
        // Written newest-first, but never trust the file's order.
        loaded.sortByDescending { it.id }
        _events.value = loaded
        nextId = (loaded.maxOfOrNull { it.id } ?: 0L) + 1L
    }

    /**
     * Prepend one real event, cap at [MAX_EVENTS], persist asynchronously.
     * Fire-and-forget: called from the main thread (UI effects and service
     * lifecycle callbacks); the file write hops to [ioScope].
     */
    fun record(context: Context, type: String, arg: String? = null, arg2: String? = null) {
        append(context, listOf(newEvent(type, arg, arg2)))
    }

    /**
     * v0.45.0 (audit P8) — record an event that happened at a PAST moment
     * (the ask's real arrival time on the FCM path, a place alert's fire
     * time). [arg2] is the stable subject id (sessionId) used for dedupe.
     */
    fun recordAt(
        context: Context,
        type: String,
        atMillis: Long,
        arg: String? = null,
        arg2: String? = null
    ) {
        append(context, listOf(NotifEvent(id = nextId++, type = type, at = atMillis, arg = arg, arg2 = arg2)))
    }

    /**
     * M8 (v0.34.0) — diff helper for the shell's session watch: compares the
     * previous open-session snapshot with the current one and records the
     * three transitions the app ALREADY observes (no new listener, no extra
     * read):
     *   • a REQUESTED ask TO this account → "session_asked" (helperEmail)
     *   • REQUESTED → ACTIVE involving me  → "session_started" (counterpart)
     *   • ACTIVE → left the open watch     → "session_ended" (counterpart)
     * The capped watch only ever returns this account's sessions, so
     * "involving me" is structural; the explicit uid checks keep that
     * guarantee local and honest.
     *
     * [prev] is the caller's remembered map (session id → last observed
     * view) and is REWRITTEN here to the current snapshot, which keeps the
     * call site a single line. A session that vanishes while ACTIVE can only
     * have ended (the state machine has no other ACTIVE exit), but the
     * terminal doc has left the watch — endedBy is NOT observable without an
     * extra Firestore read, so it is deliberately not recorded rather than
     * guessed. A REQUESTED ask that vanishes (denied / cancelled / expired)
     * is not an M8 event and is simply dropped from the map.
     */
    fun recordSessionTransitions(
        context: Context,
        meUid: String?,
        prev: MutableMap<String, SessionView>,
        sessions: List<SessionView>
    ) {
        if (meUid == null) {
            // Signed out (or loading) — never record from a stale snapshot.
            prev.clear()
            return
        }
        val currentById = sessions.associateBy { it.id }
        val batch = mutableListOf<NotifEvent>()
        for (s in sessions) {
            val before = prev[s.id]
            if (before == null) {
                if (s.status == "REQUESTED" && s.recipientUid == meUid) {
                    batch += newEvent("session_asked", s.helperEmail, s.id)
                }
            } else if (before.status == "REQUESTED" && s.status == "ACTIVE" &&
                (s.helperUid == meUid || s.recipientUid == meUid)
            ) {
                val counterpart = if (s.recipientUid == meUid) s.helperEmail else s.recipientEmail
                batch += newEvent("session_started", counterpart, s.id)
            }
        }
        for ((id, before) in prev) {
            if (before.status == "ACTIVE" && currentById[id] == null) {
                val counterpart =
                    if (before.recipientUid == meUid) before.helperEmail else before.recipientEmail
                batch += newEvent("session_ended", counterpart, id)
            }
        }
        prev.clear()
        sessions.forEach { prev[it.id] = it }
        if (batch.isNotEmpty()) append(context, batch)
    }

    /**
     * v0.47.0 (audit Gap-2/Gap-3) — the relationship twin of
     * [recordSessionTransitions]: diff the previous trusted-relationship
     * snapshot with the current one and record the transitions the app
     * ALREADY observes through the shared watchRelationships listener
     * (zero extra Firestore reads):
     *   • ACTIVE → REVOKED          → "rel_revoked"      (the other side acted)
     *   • ACTIVE → EXPIRED          → "guest_expired"    (the lazy sweep /
     *                                                     the other side won)
     *   • ACTIVE → vanished from the watch → "rel_revoked"
     *     (a guest REMOVAL deletes the doc — the only ACTIVE exit that
     *     disappears; the local actor's own "guest_removed" row and the
     *     (type, arg2) 10-minute dedupe keep the pair from double-writing)
     *
     * [prev] is the caller's remembered map (relId → last view) and is
     * REWRITTEN here, same one-line call-site contract as the session twin.
     * Nickname resolution happens here (the P13 rule): arg is the display
     * name, arg2 the stable relId (the dedupe + routing key).
     */
    fun recordRelationshipTransitions(
        context: Context,
        meUid: String?,
        prev: MutableMap<String, RelationshipView>,
        relationships: List<RelationshipView>
    ) {
        if (meUid == null) {
            prev.clear()
            return
        }
        val currentById = relationships.associateBy { it.id }
        val batch = mutableListOf<NotifEvent>()
        for (rel in relationships) {
            val before = prev[rel.id] ?: continue
            if (before.status == "ACTIVE" && rel.status != "ACTIVE") {
                val name = Nicknames.displayName(
                    context, rel.id, counterpartEmailOf(rel, meUid), ""
                )
                batch += when (rel.status) {
                    "EXPIRED" -> newEvent("guest_expired", name, rel.id)
                    else -> newEvent("rel_revoked", name, rel.id)
                }
            }
        }
        for ((id, before) in prev) {
            if (before.status == "ACTIVE" && currentById[id] == null) {
                val name = Nicknames.displayName(
                    context, id, counterpartEmailOf(before, meUid), ""
                )
                batch += newEvent("rel_revoked", name, id)
            }
        }
        prev.clear()
        relationships.forEach { prev[it.id] = it }
        if (batch.isNotEmpty()) append(context, batch)
    }

    /** The counterpart (non-me) email of a relationship view. */
    private fun counterpartEmailOf(rel: RelationshipView, meUid: String): String =
        if (rel.helperUid == meUid) rel.recipientEmail else rel.helperEmail

    /**
     * v0.47.0 (audit Gap-3) — the guest-window warning band: ONE row per
     * guest relationship when its remaining time crosses INTO the last
     * 15 minutes (band-crossing semantics — a threshold check on the 30 s
     * tick would re-log forever; [warned] is the caller's remembered set,
     * cleared when the relationship leaves the band or dies so a NEW
     * window can warn again honestly).
     */
    fun recordGuestExpiryWarnings(
        context: Context,
        rels: List<RelationshipView>,
        relNow: Long,
        warned: MutableSet<String>
    ) {
        val batch = mutableListOf<NotifEvent>()
        val liveGuestIds = rels
            .filter {
                it.kind == REL_KIND_GUEST && it.status == "ACTIVE" &&
                    it.guestExpiresAtMillis > 0L
            }
            .map { it.id }
            .toHashSet()
        for (rel in rels) {
            if (rel.kind != REL_KIND_GUEST || rel.status != "ACTIVE") continue
            if (rel.guestExpiresAtMillis <= 0L) continue
            val minutesLeft = (rel.guestExpiresAtMillis - relNow) / 60_000L
            val inBand = minutesLeft in 1..15
            if (inBand && rel.id !in warned) {
                warned += rel.id
                val name = Nicknames.displayName(
                    context, rel.id, rel.recipientEmail, ""
                )
                batch += newEvent("guest_expiring_soon", name, rel.id)
            }
        }
        // A warned id whose guest window died (expired / revoked / removed)
        // leaves liveGuestIds — drop its flag so a NEW window for the same
        // pair can warn again honestly.
        warned.retainAll(liveGuestIds)
        if (batch.isNotEmpty()) append(context, batch)
    }

    /** Sign-out hygiene: events hold counterpart emails — wipe memory + file. */
    fun clear(context: Context) {
        _events.value = emptyList()
        val appContext = context.applicationContext
        val previous = persistJob
        persistJob = ioScope.launch {
            previous?.join()
            try {
                File(appContext.filesDir, FILE_NAME).delete()
            } catch (_: Exception) {
                // best effort — a failed delete only leaves stale rows on disk
            }
        }
    }

    // ── v0.46.0 (external-audit G1) — read state + user-facing clear ────

    /**
     * G1 item 2 — one row was tapped: stamp its readAt (only if unread — a
     * re-tap never re-stamps a newer time), persist. The list work is the
     * pure, unit-tested NotifCenter.withOneMarkedRead; this is the store
     * shell, same fire-and-forget persist discipline as append().
     */
    fun markRead(context: Context, id: Long) {
        val next = _events.value.withOneMarkedRead(id, System.currentTimeMillis())
        if (next == _events.value) return // unknown id or already read — no write
        _events.value = next
        persist(context)
    }

    /** G1 item 2 — "Mark all as read": stamp every unread row now. */
    fun markAllRead(context: Context) {
        val next = _events.value.withAllMarkedRead(System.currentTimeMillis())
        if (next == _events.value) return // nothing unread — no write
        _events.value = next
        persist(context)
    }

    /**
     * G1 item 5 — the USER-facing "Clear history" (the confirmation dialog
     * lives in the screen). Same wipe as the sign-out hygiene clear():
     * memory + file — counterpart emails must not survive the user's own
     * decision to delete them either. Kept as a separate entry point so the
     * two intents (account hygiene vs explicit user action) stay distinct
     * and independently auditable in the worklog.
     */
    fun clearHistory(context: Context) {
        clear(context)
    }

    // ── internals ─────────────────────────────────────────────────────────

    private fun newEvent(type: String, arg: String?, arg2: String? = null): NotifEvent =
        NotifEvent(id = nextId++, type = type, at = System.currentTimeMillis(), arg = arg, arg2 = arg2)

    private fun append(context: Context, added: List<NotifEvent>) {
        // v0.45.0 (audit P8) — (type, arg2) dedupe within 10 minutes: the
        // alive-app listener path and the killed-app FCM path can BOTH see
        // the same ask/answer; one event in the log, not two. arg2 is the
        // stable subject id (sessionId / placeId); events without one pass
        // through untouched.
        val existing = _events.value
        val fresh = added.filter { new ->
            new.arg2 == null || existing.none {
                it.type == new.type && it.arg2 == new.arg2 &&
                    kotlin.math.abs(new.at - it.at) < 10 * 60_000L
            }
        }
        if (fresh.isEmpty()) return
        _events.value = (fresh + existing).take(MAX_EVENTS)
        persist(context)
        // v0.47.0 (audit §4 proposal) — the in-app banner: the FRESHEST
        // freshly-recorded event is offered to the shell's top banner
        // (session_asked excluded — the centered consent dialog is that
        // event's hero UX, a banner on top of it would be noise). The host
        // itself decides visibility (foreground only) and freshness (a
        // cold-start replay of old rows never banners).
        fresh.firstOrNull { it.type != "session_asked" }?.let { e ->
            EventBannerBus.offer(
                EventBanner(
                    id = e.id,
                    type = e.type,
                    arg = e.arg,
                    route = notifRouteFor(e.type, e.arg2),
                    atMillis = e.at
                )
            )
        }
    }

    private fun persist(context: Context) {
        val appContext = context.applicationContext
        val snapshot = _events.value
        val previous = persistJob
        persistJob = ioScope.launch {
            previous?.join()
            try {
                val arr = JSONArray()
                for (e in snapshot) {
                    val o = JSONObject()
                        .put("id", e.id)
                        .put("type", e.type)
                        .put("at", e.at)
                    e.arg?.let { o.put("arg", it) }
                    e.arg2?.let { o.put("arg2", it) }
                    e.readAt?.let { o.put("readAt", it) } // v0.46.0 (G1)
                    arr.put(o)
                }
                File(appContext.filesDir, FILE_NAME).writeText(arr.toString())
            } catch (_: Exception) {
                // A failed persist must NEVER crash or block the UI — the
                // in-memory list stays authoritative and the next record
                // rewrites the full state anyway.
            }
        }
    }

    /** null only when the key is absent or explicitly JSON null. */
    private fun JSONObject.optStringOrNull(key: String): String? =
        if (has(key) && !isNull(key)) optString(key) else null
}
