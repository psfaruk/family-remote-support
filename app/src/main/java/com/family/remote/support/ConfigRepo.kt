package com.family.remote.support

import org.webrtc.PeerConnection

/**
 * Phase 7.2 (v0.8.3) — runtime configuration fetched from Firestore.
 *
 * `config/turn` holds OPTIONAL relay (TURN) credentials:
 *
 *     turnUrls  = ["turn:host:80", "turn:host:443?transport=tcp", ...]
 *     username  = "..."
 *     password  = "..."
 *
 * Why runtime? Free public TURN relays die — the one this app used
 * (openrelay.metered.ca) was verified dead in Oct 2025, and without a relay
 * two phones on different carrier networks (CGNAT) usually CANNOT connect.
 * With this document the Owner can paste fresh relay credentials
 (e.g. metered.ca free tier = 500 MB/month; TURN is only used when direct
 * and STUN paths fail, so the quota burns only on cross-network sessions)
 * and every phone picks them up at its NEXT session start — no rebuild, no
 * app update. The document is written ONLY by the Owner's service account;
 * the Firestore rules make it read-only for apps and forbid app writes.
 *
 * An empty/missing document ⇒ STUN-only mode: direct connections still work
 * on the same Wi-Fi or one phone's hotspot (the lowest-latency path anyway).
 *
 * v0.9.2 — forensics from the 19:25 session showed the config read blocking
 * the whole session start for up to 30 s (the generic RPC timeout) on the
 * helper's "Watch screen" tap: the tap→watch gap measured 39 s. The relay
 * config is OPTIONAL, so it now gets a 5 s ceiling, a 10-minute cache (one
 * fetch per session, not one per round), and it never blocks a retry round —
 * the previous round's servers are reused while a fresh fetch runs.
 */
internal object ConfigRepo {

    private const val FETCH_TIMEOUT_MILLIS = 5_000L
    private const val CACHE_TTL_MILLIS = 10 * 60_000L

    /** Last successful fetch's server list (STUN-only when never fetched). */
    @Volatile
    private var cache: List<PeerConnection.IceServer> = emptyList()

    @Volatile
    private var cachedAt = 0L

    /** One-line note for the on-screen diagnostics ("3 relay servers…"). */
    @Volatile
    var lastNote: String = "relay config not read yet"
        private set

    suspend fun fetchTurnServers(): List<PeerConnection.IceServer> {
        val now = System.currentTimeMillis()
        if (cache.isNotEmpty() && now - cachedAt < CACHE_TTL_MILLIS) {
            lastNote = "relay config: ${cache.size} server(s) (cached)"
            return cache
        }
        return try {
            val snap = Fb.db.collection("config")
                .document("turn")
                .get()
                .awaitWithTimeout("reading the relay config", FETCH_TIMEOUT_MILLIS)
            @Suppress("UNCHECKED_CAST")
            val urls = snap.get("turnUrls") as? List<String> ?: emptyList()
            val user = snap.getString("username") ?: ""
            val pass = snap.getString("password") ?: ""
            val servers = urls.filter { it.startsWith("turn") }.map { url ->
                PeerConnection.IceServer.builder(url)
                    .setUsername(user)
                    .setPassword(pass)
                    .createIceServer()
            }
            if (servers.isNotEmpty()) {
                cache = servers
                cachedAt = now
                lastNote = "relay config: ${servers.size} TURN server(s) loaded"
            } else {
                lastNote = "relay config: document empty — STUN-only mode"
            }
            servers
        } catch (e: Exception) {
            // No config / offline / permission problem ⇒ STUN-only. Never
            // let a config hiccup block the session itself (v0.9.2: fall
            // back to a stale cache first — old relay creds still beat none).
            lastNote = if (cache.isNotEmpty()) {
                "relay config: refresh failed (${e.javaClass.simpleName}) — using cached ${cache.size}"
            } else {
                "relay config: unavailable (${e.javaClass.simpleName}) — STUN-only mode"
            }
            cache
        }
    }
}
