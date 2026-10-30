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
 *
 * v0.9.7 — `config/quality` joins it: the OWNER-CONTROLLED video-quality
 * knobs (preferred codec "vp8"|"h264", fps 10..60, bitrate floor/ceiling).
 * This is how the "60 fps smooth" request becomes a SAFE experiment: the
 * hardware-H264 path is switched on by one Firestore write (no new APK) and
 * reverted the same way if a phone's H264 hardware misbehaves. Missing doc ⇒
 * exactly the proven v0.9.6 behaviour (VP8 · 30 fps · 1.2–4 Mbps). The
 * quality cache is deliberately SHORT (60 s, vs the relay's 10 min) so a
 * mid-experiment flip lands on the very next session start.
 */
internal object ConfigRepo {

    private const val FETCH_TIMEOUT_MILLIS = 5_000L
    private const val CACHE_TTL_MILLIS = 10 * 60_000L

    /** v0.9.7 — short TTL so owner-side quality flips land almost instantly. */
    private const val QUALITY_CACHE_TTL_MILLIS = 60_000L

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

    /** Last quality snapshot note for the on-screen diagnostics. */
    @Volatile
    var lastQualityNote: String = "quality config not read yet"
        private set

    @Volatile
    private var qCache = QualityCfg()

    @Volatile
    private var qCachedAt = 0L

    /**
     * v0.9.7 — read the owner's quality knobs. Everything is clamped and
     * defaulted app-side: a malformed or missing document can never produce
     * an impossible pipeline (VP8@30·1200–4000 is the hard fallback). No
     * cache stamp on failure ⇒ the next session start retries, so a revert
     * flip is picked up without waiting out the TTL.
     */
    suspend fun fetchQuality(): QualityCfg {
        val now = System.currentTimeMillis()
        if (now - qCachedAt < QUALITY_CACHE_TTL_MILLIS) return qCache
        return try {
            val snap = Fb.db.collection("config")
                .document("quality")
                .get()
                .awaitWithTimeout("reading the quality config", FETCH_TIMEOUT_MILLIS)
            val codec = if (snap.getString("codec")?.trim()?.lowercase() == "h264") "h264" else "vp8"
            val fps = (snap.getLong("fps") ?: 30L).toInt().coerceIn(10, 60)
            val minK = (snap.getLong("minKbps") ?: 1_200L).toInt().coerceIn(100, 50_000)
            val maxK = (snap.getLong("maxKbps") ?: 4_000L).toInt().coerceIn(minK, 100_000)
            // v0.24.0 (Phase 18.3 — Bug C: "ভিডিও দেরিতে আসে") — the Owner can
            // flip the degradation preference live: "framerate" (default now,
            // freshness over sharpness), "balanced", "resolution" (the old
            // pre-0.24 behaviour). Anything else = framerate.
            val pref = when (snap.getString("degradationPreference")?.trim()?.lowercase()) {
                "balanced" -> "balanced"
                "resolution" -> "resolution"
                else -> "framerate"
            }
            qCache = QualityCfg(codec, fps, minK, maxK, pref)
            qCachedAt = now
            lastQualityNote = "quality: ${codec.uppercase()} · ${fps}fps · $minK–${maxK}kbps · $pref"
            qCache
        } catch (e: Exception) {
            // Missing doc / offline / rules hiccup ⇒ the proven defaults.
            lastQualityNote =
                "quality config: unavailable (${e.javaClass.simpleName}) — defaults (VP8 · 30fps)"
            qCache
        }
    }
}

/**
 * v0.9.7 — owner-controlled video-quality knobs (`config/quality`). The
 * engine treats these as PREFERENCES: H264 is only used when the phone
 * actually has a hardware encoder (runtime probe), and a 60 fps wish is
 * clamped back to 30 whenever software VP8 ends up negotiated.
 */
internal data class QualityCfg(
    val codec: String = "vp8", // "vp8" (proven everywhere) | "h264" (hardware)
    val fps: Int = 30,         // capture/encode ceiling, clamped 10..60
    val minKbps: Int = 1_200,
    val maxKbps: Int = 4_000,
    // v0.24.0 (Phase 18.3) — what suffers first under bandwidth pressure.
    // "framerate" keeps the picture FRESH (drops resolution instead of
    // stalling frames) — the anti-latency default; "resolution" was the
    // old keep-sharp-but-stall behaviour, still selectable live.
    val pref: String = "framerate" // framerate | balanced | resolution
)
