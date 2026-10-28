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
 * With this document the Owner can paste fresh free-tier credentials
 * (metered.ca gives 20 GB/month free) and every phone picks them up at its
 * NEXT session start — no rebuild, no app update. The document is written
 * ONLY by the Owner's service account; the Firestore rules make it
 * read-only for apps and forbid app writes.
 *
 * An empty/missing document ⇒ STUN-only mode: direct connections still work
 * on the same Wi-Fi or one phone's hotspot (the lowest-latency path anyway).
 */
internal object ConfigRepo {

    suspend fun fetchTurnServers(): List<PeerConnection.IceServer> {
        return try {
            val snap = Fb.db.collection("config")
                .document("turn")
                .get()
                .awaitWithTimeout("reading the relay config")
            @Suppress("UNCHECKED_CAST")
            val urls = snap.get("turnUrls") as? List<String> ?: emptyList()
            val user = snap.getString("username") ?: ""
            val pass = snap.getString("password") ?: ""
            urls.filter { it.startsWith("turn") }.map { url ->
                PeerConnection.IceServer.builder(url)
                    .setUsername(user)
                    .setPassword(pass)
                    .createIceServer()
            }
        } catch (_: Exception) {
            // No config / offline / permission problem ⇒ STUN-only. Never
            // let a config hiccup block the session itself.
            emptyList()
        }
    }
}
