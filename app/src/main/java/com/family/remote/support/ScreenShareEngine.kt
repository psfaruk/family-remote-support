package com.family.remote.support

import android.content.Context
import android.content.Intent
import android.graphics.Point
import android.media.projection.MediaProjection
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import java.nio.ByteBuffer
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.webrtc.DataChannel
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.MediaStreamTrack
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.SdpObserver
import org.webrtc.ScreenCapturerAndroid
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoSink
import org.webrtc.VideoSource
import org.webrtc.VideoTrack

/** Live status of the screen-share engine, observed by the UI. */
enum class ShareState { IDLE, PREPARING, WAITING_PEER, CONNECTING, LIVE, ENDED }

/**
 * Phase 7.1 — WebRTC screen share engine (no server; Firestore signaling).
 *
 * Roles:
 *  - RECIPIENT (the helped phone): holds the MediaProjection capture and the
 *    screen video track. It does NOT create the offer on its own — it waits
 *    for a "watch" request from the helper, then posts a fresh offer. This
 *    single negotiation path removes every offer/answer race and makes
 *    re-watching after a helper restart trivial.
 *  - HELPER (the helping phone): posts "watch", answers the offer, renders
 *    the incoming track on a SurfaceViewRenderer.
 *
 * v0.8.1 fixes (black-screen report):
 *  1. EPOCH ROUNDS — every engine round carries a fresh random "ep" token.
 *     The helper sends it inside "watch"; the recipient echoes it on its
 *     offer/ice; the helper stamps it on its answer/ice. A reader applies a
 *     signal only when the epoch matches the CURRENT round. Stale documents
 *     from an earlier round (old offers/candidates left in Firestore) are now
 *     inert — previously a fresh helper could apply a DEAD offer and wait on
 *     it forever ("Answer sent — connecting video…" + black picture).
 *  2. ICE RESTART ON RE-OFFER — when the recipient answers another "watch"
 *     (helper re-watched), the new offer is created with IceRestart=true, so
 *     ICE re-gathers and re-sends candidates instead of reusing the dead
 *     round's credentials (which left the new helper with zero candidates).
 *  3. STALE-CALLBACK GUARDS — every PeerConnection/SdpObserver callback
 *     verifies it still belongs to the current round, so a closing PC can no
 *     longer overwrite a fresh round's state (that produced spurious
 *     "Connection failed" messages mid-attempt).
 *  4. HONEST STATUS + FRAME WATCHDOG — helper: "Connection established…",
 *     "Live — receiving the screen ✓" (first decoded frame), or an explicit
 *     "Connected but no video yet" warning; recipient: optimistic
 *     "Connected — streaming screen…" replaced with real states.
 *  5. NO-BYE STOP — the helper's "Stop watching" only closes ITS OWN viewer;
 *     the recipient keeps sharing, so "Watch screen" can simply be tapped
 *     again. (The recipient's "Stop sharing"/session end still sends "bye".)
 *  6. VP8 PREFERRED — both sides reorder the video m-line to negotiate VP8
 *     (software codec: identical behaviour on every phone, avoids broken
 *     H.264 hardware for screen content).
 *  7. v0.8.2 — RENDER-ATTACH LIFECYCLE: the UI used to attach the video
 *     surface inside the Compose factory and then silently detach it again
 *     when the view-state write restarted its own effect (dispose-after-
 *     attach ordering). Frames kept flowing — the helper watchdog even showed
 *     "Live ✓" — while every surface stayed black on BOTH phones. Attaching
 *     now happens in the effect body and is re-asserted on recomposition; a
 *     fresh "watch" round also resets remoteDescriptionSet/pendingIce.
 *
 * v0.8.3 fixes (Oct 2025 report: "Connection failed" on both phones, helper
 * stuck on "Answer sent — connecting video…" with a black box):
 *  8. TURN IS NOW RUNTIME-CONFIGURABLE — the hard truth behind the new
 *     failures: the free public relay the app used (openrelay.metered.ca) is
 *     DEAD (verified from the build box: UDP silence, TCP refused), and its
 *     replacement host rejects the old public credentials. Without a relay,
 *     phones on two different mobile-carrier networks (CGNAT) often CANNOT
 *     connect, whatever the app does. The engine therefore now reads a
 *     `config/turn` Firestore document at every session start (owner-written
 *     via the service account; read-only for apps) and merges those TURN
 *     servers into ICE. Owner pastes free metered.ca credentials once →
 *     cross-network relay works WITHOUT a new APK. Empty doc ⇒ STUN-only
 *     (same Wi-Fi / one phone's hotspot always works that way).
 *  9. SELF-HEALING WATCH ROUNDS — previously the recipient IGNORED a "watch"
 *     that arrived while its UI showed "Connection failed" (state ENDED):
 *     the helper then waited forever on "Asked the recipient's phone…" and
 *     the only way out was tapping "Stop sharing" → re-share → re-consent on
 *     the recipient phone. Now: a connection failure on the RECIPIENT no
 *     longer kills the share — the engine rebuilds just the PeerConnection,
 *     keeps the capture, and stays ready; the next "watch" reconnects. A
 *     watch is accepted in ANY state while the capture is alive.
 * 10. HELPER AUTO-RETRY — the helper no longer dead-ends either. Every stuck
 *     stage (no offer within 12 s of asking, no answer within 12 s of the
 *     offer, no video within 14 s of the answer, or a hard FAILED) now
 *     triggers an automatic fresh round (new epoch + new PeerConnection +
 *     new "watch"), up to a 3-retry budget that refills on every success.
 *     Only after the budget is exhausted does the manual-advice message
 *     appear.
 * 11. NETWORK SELF-DIAGNOSIS — while gathering, the engine counts its own
 *     candidates by type and exposes a one-line "Network check — direct:
 *     N · via STUN: M · via relay: K" so a screenshot alone tells us whether
 *     both phones can reach a relay (K>0) or are STUN-only.
 * 12. FRAME-SIZE FEEDBACK — the decoded frame dimensions are published, so
 *     the UI can size the picture to the real screen aspect ratio (big
 *     watch view + full-screen mode instead of a small fixed box).
 *
 * Phase 8 (v0.9.0) — REMOTE ASSIST:
 * 13. "cmd" SIGNALS — the helper may send tap/nav commands while live; the
 *     recipient executes them ONLY if its grantor enabled the assist
 *     accessibility service (AssistService — reads nothing, taps only), and
 *     every command must carry the CURRENT epoch like any other signal. An
 *     explicit "cmdack" answers each command so the helper's UI can tell a
 *     delivered tap from a dead end (assist switched off, session gone).
 *
 *     Touch-space mapping: the helper's video is letterboxed to the real
 *     frame aspect; the UI converts its touch position into normalized
 *     (0..1) coordinates, and the recipient maps those onto its real
 *     display before dispatching the gesture — resolution-independent.
 *
 * Threading: every WebRTC object is created and driven from the main thread
 * (fine for one ~720p screen stream); observer callbacks hop back onto the
 * main handler before touching engine state.
 *
 * v0.9.2 (Oct 2025) — FORENSIC ROUND (session 19:25 Dhaka, Firestore dump):
 * the metered.ca relay WORKED from both phones (relay candidates on both
 * sides), round 1 even CONNECTED and decoded frames — yet every picture
 * stayed black, and after the helper's network switch the engine dead-ended.
 * Three root causes, fixed here:
 *
 * 14. RENDERER RACE (black picture on BOTH phones despite live frames) —
 *     the Compose `DisposableEffect(rendererView)` captured the freshly
 *     created SurfaceViewRenderer, then its key-change dispose RELEASED the
 *     live view and re-attached the released renderer. The track now carries
 *     exactly ONE permanent proxy sink per purpose; the UI only swaps the
 *     forwarding target, so no addSink/removeSink/release race can exist.
 *     A "drawn frames" counter makes a stuck surface visible on screen.
 * 15. SILENT DEAD-ENDS — every setRemote/createDescription failure used to
 *     vanish into a noop observer: the recipient's offers arrived (Firestore
 *     proof) while the helper never answered, forever. Every failure is now
 *     logged on screen and routed to the existing retry/recover machinery.
 * 16. 30 s CONFIG STALL — the optional relay-config read blocked each round
 *     for up to 30 s (measured tap→watch gap: 39 s). Now: 5 s ceiling + a
 *     10-minute cache in ConfigRepo. Plus a SupervisorJob so one failed
 *     coroutine can never cancel the whole engine scope again, CLOSED-pc
 *     rebuild on re-watch, and a rolling on-screen diagnostics log (the
 *     Owner is phone-only — a screenshot must BE the logcat).
 *
 * v0.9.6 (Oct 2025) — PRECISION + INSTANT TOUCH + SHARPER VIDEO. The 28 Oct
 * session worked end-to-end but the taps landed ~one keyboard row ABOVE the
 * intended key ("pressed h, u fired"), each tap took ~3 s, and the picture
 * was blurry. Three fixes:
 *
 * 17. TOUCH PRECISION — the recipient mapped the video's normalized
 *     coordinates onto the APP-WINDOW size (resources.displayMetrics),
 *     which is smaller than the video's space (the FULL display, status +
 *     navigation bars included) by exactly the bars' share of the screen —
 *     so every Y landed too high. Coordinates now map onto the REAL display
 *     size (maximumWindowMetrics / getRealSize), and the capture itself now
 *     starts from the real display size, making the video aspect exact.
 * 18. INSTANT TOUCH — helper taps travel over a WebRTC DATA CHANNEL
 *     ("touch", created by the offerer/recipient so it is inside every
 *     offer): sub-100 ms instead of a Firestore round-trip. Firestore
 *     cmd/cmdack remains the automatic fallback while the channel is still
 *     negotiating and for calls with an older peer version.
 * 19. SHARPER + SMOOTHER — capture scaled from maxDim 1280 → 1536 at the
 *     real display aspect, 20 → 30 fps, and a bigger bitrate budget
 *     (1.2–4 Mbps, was 0.8–2.5) with an explicit 30 fps ceiling. Text stays
 *     readable; scrolling no longer strobes.
 *
 * v0.9.7 (Oct 2025) — THE 60 FPS PATH, OWNER-CONTROLLED. The user asked for
 * "60fps smooth". Software VP8 cannot push a 1536-class 60 fps on the
 * fleet's SoCs — but the HARDWARE H.264 encoder can — so the codec choice
 * becomes a runtime EXPERIMENT instead of a coin-flip baked into the APK:
 *
 * 20. config/quality (Firestore, owner-SA-written, 60 s cache) decides the
 *     codec ("vp8"|"h264"), fps (10..60) and the bitrate floor/ceiling.
 *     Missing doc ⇒ exactly the proven v0.9.6 behaviour (VP8 · 30 fps ·
 *     1.2–4 Mbps). A failed experiment is reverted by ONE Firestore write,
 *     not a new APK.
 * 21. CAPABILITY PROBE — at factory creation the engine logs which codecs
 *     this phone can actually encode/decode ("codec probe — H264 enc=Y
 *     dec=Y · VP8 enc=Y dec=Y"). H264 is only PREFERRED when the recipient
 *     truly has a hardware encoder; the SDP keeps VP8 present as fallback,
 *     so a peer that cannot decode H264 negotiates VP8 by itself.
 * 22. NEGOTIATED-CODEC PROOF — the truth is parsed from the RECEIVED SDP
 *     ("video codec negotiated: H264") and the capture line names the
 *     running codec+fps ("capture started 720x1536@60 codec=H264-HW"). If
 *     H264 was wanted but VP8 got negotiated, the encode ceiling drops back
 *     to 30 fps automatically — software VP8 cannot do 60.
 */
object ScreenShareEngine {

    /** STUN for public address discovery (always on). */
    private fun baseIceServers(): List<PeerConnection.IceServer> = listOf(
        PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
        PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer(),
        PeerConnection.IceServer.builder("stun:stun.cloudflare.com:3478").createIceServer()
    )

    private val main = Handler(Looper.getMainLooper())
    // v0.9.2 — SupervisorJob: one failed coroutine must NEVER cancel the
    // whole engine scope (which silently stopped every retry/watch/send).
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val _state = MutableStateFlow(ShareState.IDLE)
    val state: StateFlow<ShareState> = _state

    /** One-line human status for the UI ("Connecting…", errors, …). */
    private val _info = MutableStateFlow<String?>(null)
    val info: StateFlow<String?> = _info

    /** Candidate-type summary, refreshed every time gathering completes. */
    private val _netInfo = MutableStateFlow<String?>(null)
    val netInfo: StateFlow<String?> = _netInfo

    /** Decoded remote frame size (width to height) for the viewer's layout. */
    private val _frameSize = MutableStateFlow<Pair<Int, Int>?>(null)
    val frameSize: StateFlow<Pair<Int, Int>?> = _frameSize

    /** Phase 8 — helper-side assist feedback ("Tap delivered ✓", errors). */
    private val _assistStatus = MutableStateFlow<String?>(null)
    val assistStatus: StateFlow<String?> = _assistStatus

    /**
     * v0.9.5 — count of helper commands dropped because the recipient's
     * assist service was off. The recipient's share card turns this into a
     * prominent "the helper is tapping RIGHT NOW and it is not landing"
     * banner, so the one-time setup can't stay unnoticed mid-session.
     */
    private val _assistDropped = MutableStateFlow(0)
    val assistDropped: StateFlow<Int> = _assistDropped

    // v0.9.2 — on-screen diagnostics. The Owner is phone-only (no logcat), so
    // the engine keeps the last engine events here and the UI renders them;
    // a single screenshot now answers "where exactly did it stop?".
    private val diagLines = ArrayDeque<String>()
    private val _diag = MutableStateFlow<List<String>>(emptyList())
    val diag: StateFlow<List<String>> = _diag

    // v0.10.0 (Phase 9) — assist-command abuse/replay budget (see constants).
    private val cmdTimes = ArrayDeque<Long>()

    /** Replay guard: a Firestore "cmd" older than this is refused. */
    private val cmdFreshMillis = 15_000L
    /** Firestore-cmd / p2p-message size cap — a real command is a few hundred bytes. */
    private val maxCmdChars = 4_096

    /**
     * v0.12.0 — one typed message: generous for a search box or a WhatsApp
     * reply, small enough that the cmd payload stays a rounding error
     * against maxCmdChars — and against the rules' 100 kB signal cap.
     */
    private val maxTextChars = 500
    /** Flood cap: at most this many assist commands applied per sliding window. */
    private val cmdRateWindowMillis = 1_000L
    private val cmdRateMax = 25

    /** One-line frame health for the UI ("video: decoded N · drawn M"). */
    private val _frameStats = MutableStateFlow<String?>(null)
    val frameStats: StateFlow<String?> = _frameStats

    private fun diagLog(message: String) {
        main.post {
            val ts = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US)
                .format(java.util.Date())
            diagLines.addLast("$ts  $message")
            while (diagLines.size > 14) diagLines.removeFirst()
            _diag.value = diagLines.toList()
        }
    }

    private val assistClear = Runnable { _assistStatus.value = null }

    private var appContext: Context? = null
    private var eglBase: EglBase? = null
    private var factory: PeerConnectionFactory? = null

    // Session-scoped state (one side active at a time).
    private var sessionId: String? = null
    private var myUid: String? = null
    private var peerUid: String? = null
    private var isHelper: Boolean = false
    private var signalStop: (() -> Unit)? = null

    /**
     * Round tokens. [myEpoch] is fresh per round; the helper sends it in
     * "watch" and the recipient adopts it as [peerEpoch] for the round. Every
     * signal is stamped with the sender's epoch for the round and is applied
     * by the reader only when it matches the round currently in progress.
     */
    private var myEpoch: String? = null
    private var peerEpoch: String? = null

    private var peerConnection: PeerConnection? = null
    private var videoSource: VideoSource? = null
    private var videoTrack: VideoTrack? = null
    private var capturer: ScreenCapturerAndroid? = null
    private var surfaceHelper: SurfaceTextureHelper? = null
    private var remoteTrack: VideoTrack? = null

    /**
     * v0.9.6 — the peer-to-peer touch channel. Created by the recipient
     * (the offerer) so it is inside every offer; the helper adopts it via
     * onDataChannel. While OPEN, taps and acks skip Firestore completely.
     */
    private var ctrlChannel: DataChannel? = null

    // v0.9.7 — owner-controlled quality (config/quality) + codec plumbing.
    private var quality = QualityCfg()
    private var effectiveH264 = false
    private var encFpsCap = 30
    private var negotiatedCodec: String? = null

    /**
     * v0.9.9 (Phase 8.3) — data-saver for relayed sessions. When the SELECTED
     * ICE pair turns out to be a TURN relay (metered.ca free tier = 500 MB/month,
     * and a relayed 1536-class 4 Mbps stream burns it in ~17 minutes), the
     * bitrate ceiling is capped once, mid-session, without restarting capture.
     * Direct/hotspot paths are never capped.
     */
    private var relayMaxKbps: Int? = null

    /**
     * v0.12.0 (Phase 8.3 completion) — HELPER data-saver. When the helper
     * flips "Data saver", the stream re-applies its bitrate with a hard
     * 600 kbps ceiling (below the automatic 1500 kbps relay cap) and lifts
     * it again on OFF. Deliberately path-independent: a direct session gets
     * the cap too, because the helper asked for it (metered data is metered
     * on both ends). Restores exactly what the relay check decided when
     * switched off — the two caps combine by taking the minimum.
     */
    private var dataSaverKbps: Int? = null

    /** The recipient's local "screen" track — the sender applyScreenBitrate touches. */
    private var senderVideoTrack: VideoTrack? = null
    private var probeLogged = false
    private var h264Enc = false
    private var h264Dec = false

    private val pendingIce = mutableListOf<IceCandidate>()
    private var remoteDescriptionSet = false
    /** De-dupes control messages (offer/answer/watch applied once per round). */
    private val handled = mutableSetOf<String>()

    // v0.8.3 — self-healing + diagnostics state.
    /** Relay servers loaded from config/turn at session start (may be empty). */
    private var dynamicIceServers: List<PeerConnection.IceServer> = emptyList()
    /** True while the recipient's capture/track are alive and reusable. */
    private var captureAlive = false
    /** Remaining automatic fresh rounds for the helper (refills on success). */
    private var retryBudget = 0
    /** Epoch whose retry decision was already made (guards double-trigger). */
    private var retryRoundTag = ""
    private var sentWatchAt = 0L
    private var gotOfferAt = 0L
    private var candHost = 0
    private var candSrflx = 0
    private var candRelay = 0

    // Frame watchdog (helper side): counts decoded frames of the remote track.
    private val framesSeen = AtomicLong(0)
    private var frameSinkAdded = false
    private var answerSentAtMillis = 0L
    private var tickerRunning = false

    // v0.9.2 — PROXY SINKS. Each track carries exactly ONE permanent sink;
    // attachRenderer/attachLocalPreview only swap the forwarding target. This
    // removes the addSink/removeSink/release race class entirely (the 19:25
    // session: frames decoded ✓, picture black ✗ — a released
    // SurfaceViewRenderer had been re-attached by the UI's dispose/re-run).
    private var rendererTarget: SurfaceViewRenderer? = null
    private var previewTarget: SurfaceViewRenderer? = null
    private val renderedFrames = AtomicLong(0)
    private val previewFrames = AtomicLong(0)
    private var remoteProxyAttachedTo: VideoTrack? = null
    private var statsPostedAt = 0L

    private fun postFrameStats() {
        val now = System.currentTimeMillis()
        if (now - statsPostedAt < 2000) return
        statsPostedAt = now
        _frameStats.value = if (isHelper) {
            "video: decoded ${framesSeen.get()} · drawn ${renderedFrames.get()} frames"
        } else {
            "preview: ${previewFrames.get()} frames"
        }
    }

    private val remoteProxySink = VideoSink { frame ->
        renderedFrames.incrementAndGet()
        val target = rendererTarget
        if (target != null) {
            try {
                target.onFrame(frame)
            } catch (_: Exception) {
            }
        }
        postFrameStats()
    }

    private val localProxySink = VideoSink { frame ->
        previewFrames.incrementAndGet()
        val target = previewTarget
        if (target != null) {
            try {
                target.onFrame(frame)
            } catch (_: Exception) {
            }
        }
        postFrameStats()
    }

    /** Point the permanent proxy at a (new) remote track exactly once. */
    private fun ensureRemoteProxy(track: VideoTrack) {
        if (remoteProxyAttachedTo === track) return
        try {
            remoteProxyAttachedTo?.removeSink(remoteProxySink)
        } catch (_: Exception) {
        }
        try {
            track.addSink(remoteProxySink)
            remoteProxyAttachedTo = track
            renderedFrames.set(0)
        } catch (_: Exception) {
        }
    }
    private val frameCounter = VideoSink { frame ->
        framesSeen.incrementAndGet()
        val w = frame?.rotatedWidth ?: 0
        val h = frame?.rotatedHeight ?: 0
        if (w > 0 && h > 0) {
            val cur = _frameSize.value
            if (cur == null || cur.first != w || cur.second != h) _frameSize.value = w to h
        }
    }
    private val ticker = object : Runnable {
        override fun run() {
            if (!isHelper || peerConnection == null) {
                if (tickerRunning) diagLog("watchdog stopped (state=${_state.value})")
                tickerRunning = false
                return
            }
            val seen = framesSeen.get()
            val st = _state.value
            val now = System.currentTimeMillis()
            var repost = true
            when {
                seen > 0 && (st == ShareState.CONNECTING || st == ShareState.WAITING_PEER) -> {
                    _state.value = ShareState.LIVE
                    _info.value = "Live — receiving the screen ✓"
                }
                st == ShareState.WAITING_PEER &&
                    sentWatchAt > 0 && now - sentWatchAt > 12_000 -> {
                    repost = false
                    helperRetryOrFail(
                        "No response from the recipient's phone — make sure “Share screen” is ON there."
                    )
                }
                st == ShareState.CONNECTING &&
                    gotOfferAt > 0 && answerSentAtMillis == 0L && now - gotOfferAt > 12_000 -> {
                    repost = false
                    helperRetryOrFail("The recipient's answer never arrived.")
                }
                st == ShareState.CONNECTING &&
                    answerSentAtMillis > 0 && seen == 0L && now - answerSentAtMillis > 14_000 -> {
                    repost = false
                    helperRetryOrFail("The two phones could not finish linking up.")
                }
            }
            if (repost) main.postDelayed(this, 1000)
        }
    }

    /** True while this device is CAPTURING (recipient). */
    val isCapturing: Boolean get() = !isHelper && _state.value != ShareState.IDLE
    /** True while this device is WATCHING (helper). */
    val isWatching: Boolean get() = isHelper && _state.value != ShareState.IDLE

    /** Shared EGL context for UI render surfaces (null until an engine started). */
    val eglBaseContext: EglBase.Context? get() = eglBase?.eglBaseContext

    // ------------------------------------------------------------------ //
    //  Recipient (broadcaster)
    // ------------------------------------------------------------------ //

    /**
     * Start sharing this phone's screen. [projectionData] is the intent data
     * returned by the MediaProjection consent dialog. MUST be called while the
     * mediaProjection foreground service is already running (Android 14 rule:
     * getMediaProjection() only succeeds inside a running FGS of that type).
     * v0.8.3: the optional relay config is fetched first so the very first
     * PeerConnection already gathers relay candidates.
     */
    fun startRecipient(
        context: Context,
        sessionIdArg: String,
        uid: String,
        peerUidArg: String,
        projectionData: Intent
    ) {
        runOnMain {
            _info.value = null
            startCommon(context, sessionIdArg, uid, peerUidArg, isHelperArg = false)
            _state.value = ShareState.PREPARING
            _info.value = "Preparing the screen share…"
            diagLog("share start (epoch ${myEpoch?.take(6) ?: "?"})")
            val app = context.applicationContext
            scope.launch {
                dynamicIceServers = ConfigRepo.fetchTurnServers()
                diagLog(ConfigRepo.lastNote)
                quality = ConfigRepo.fetchQuality() // v0.9.7 — codec/fps/bitrate knobs
                diagLog(ConfigRepo.lastQualityNote)
                main.post { beginRecipientCapture(app, projectionData) }
            }
        }
    }

    private fun beginRecipientCapture(context: Context, projectionData: Intent) {
        _state.value = ShareState.PREPARING
        try {
            ensureFactory()
            createPeer()

            // v0.9.6 — capture the REAL display (status bar + navigation bar
            // are part of the picture — MediaProjection always projects the
            // whole screen). The old app-window metrics gave the video a
            // slightly wrong aspect AND shrank every normalized coordinate
            // onto a smaller height — the root cause of the "pressed h,
            // tapped u" precision bug. Cap the long side at 1536 so the
            // fleet's software VP8 encoder stays comfortable.
            val (rw, rh) = realDisplaySize(context)
            var w = rw
            var h = rh
            val maxDim = maxOf(w, h)
            if (maxDim > 1536) {
                val scale = 1536.0 / maxDim
                w = (w * scale / 2).toInt() * 2
                h = (h * scale / 2).toInt() * 2
            }

            val helper = SurfaceTextureHelper.create("ScreenCap", eglBase!!.eglBaseContext)
            val source = factory!!.createVideoSource(true)
            val cap = ScreenCapturerAndroid(projectionData, object : MediaProjection.Callback() {})
            cap.initialize(helper, context.applicationContext, source.capturerObserver)
            // v0.9.7 — the owner's fps wish runs ONLY on the hardware H264
            // path: software VP8 at 1536-class 60 fps would churn the CPU and
            // drop frames. 60 fps hardware H264 is exactly the smoothness
            // the user asked for; anything else stays at the proven ≤30.
            val wantH264 = quality.codec == "h264"
            effectiveH264 = wantH264 && h264Enc
            if (wantH264 && !h264Enc) {
                diagLog("H264 requested but no HW encoder on this phone — VP8 fallback")
            }
            val capFps = if (effectiveH264) quality.fps else minOf(quality.fps, 30)
            encFpsCap = capFps
            cap.startCapture(w, h, capFps)

            val track = factory!!.createVideoTrack("screen", source)
            peerConnection?.addTrack(track, listOf("screen"))
            senderVideoTrack = track // v0.9.9 — the relay bandwidth cap re-applies here
            // v0.9.3 — give the screen stream a real bitrate budget
            // (see applyScreenBitrate): without it a relayed 4G path sits
            // blurry-laggy at the estimator's tiny default.
            applyScreenBitrate(peerConnection, track)
            // v0.9.2 — the local track carries its one permanent proxy sink;
            // the UI's preview surface is just a swappable target.
            try {
                track.addSink(localProxySink)
            } catch (_: Exception) {
            }

            surfaceHelper = helper
            videoSource = source
            videoTrack = track
            capturer = cap
            captureAlive = true
            diagLog(
                "capture started ${w}x${h}@$capFps codec=" +
                    (if (effectiveH264) "H264-HW" else "VP8") +
                    " (real display $rw x $rh)"
            )
            _state.value = ShareState.WAITING_PEER
            _info.value = "Screen capture ready — waiting for the helper to tap “Watch screen”."
            watchSignals()
        } catch (e: Exception) {
            captureAlive = false
            fail("Screen capture failed: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    /**
     * v0.9.6 — the FULL real display size (system bars included): the exact
     * space MediaProjection captures and the assist service taps on.
     */
    @Suppress("DEPRECATION")
    private fun realDisplaySize(context: Context): Pair<Int, Int> {
        return try {
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val b = wm.maximumWindowMetrics.bounds
                b.width() to b.height()
            } else {
                val p = Point()
                wm.defaultDisplay.getRealSize(p)
                p.x to p.y
            }
        } catch (_: Exception) {
            val dm = context.resources.displayMetrics
            dm.widthPixels to dm.heightPixels
        }
    }

    /**
     * v0.9.6 — QUALITY FIX, round two. v0.9.3 gave the stream its first real
     * budget (0.8–2.5 Mbps) and stopped the resolution pump-down. With the
     * sharper 1536-class capture the budget grows to 1.2–4 Mbps and carries
     * an explicit 30 fps ceiling. Keep the resolution (text must stay
     * readable). Best-effort: any build that rejects the parameters just
     * keeps libwebrtc's defaults.
     */
    private fun applyScreenBitrate(pc: PeerConnection?, track: VideoTrack?) {
        if (pc == null || track == null) return
        try {
            val sender = pc.senders.firstOrNull { it.track() === track } ?: return
            val params = sender.parameters ?: return
            params.encodings.firstOrNull()?.let { enc ->
                // v0.9.7 — budget from config/quality (defaults = v0.9.6's).
                // v0.9.9 — relayed sessions are capped harder (data-saver).
                // v0.12.0 — the helper's Data-saver toggle adds a second cap;
                // whichever of the two is lower wins.
                val capK = listOfNotNull(relayMaxKbps, dataSaverKbps).minOrNull()
                val maxK = minOf(quality.maxKbps, capK ?: quality.maxKbps)
                enc.minBitrateBps = minOf(quality.minKbps, maxK) * 1_000
                enc.maxBitrateBps = maxK * 1_000
                try {
                    // stream-webrtc-android declares maxFramerate as Int?.
                    enc.maxFramerate = encFpsCap
                } catch (_: Exception) {
                }
            }
            try {
                params.degradationPreference =
                    org.webrtc.RtpParameters.DegradationPreference.MAINTAIN_RESOLUTION
            } catch (_: Exception) {
            }
            sender.setParameters(params)
            val capK = listOfNotNull(relayMaxKbps, dataSaverKbps).minOrNull()
            diagLog(
                "bitrate ${quality.minKbps}k–${minOf(quality.maxKbps, capK ?: quality.maxKbps)}k · $encFpsCap fps · " +
                    "keep-resolution · codec ${if (effectiveH264) "H264-HW" else "VP8"}" +
                    (if (capK != null && capK < quality.maxKbps) {
                        " · capped " + (if (dataSaverKbps != null) "(data-saver)" else "(relay)")
                    } else "")
            )
        } catch (e: Exception) {
            diagLog("bitrate set skipped: ${e.javaClass.simpleName}")
        }
    }

    /**
     * v0.9.9 (Phase 8.3) — LOW-BITRATE-WHEN-RELAYED. Once the peer connection
     * is live, wait for ICE to settle, then ask getStats() which candidate
     * pair actually got SELECTED. If either end of it is a TURN relay, the
     * stream re-applies its bitrate with a hard 1500 kbps ceiling — the
     * metered.ca free tier holds ~17 minutes of full-quality video but hours
     * of a data-saver stream, and scrolling text survives the cap fine.
     * Direct/hotspot sessions are never touched. Best-effort: any stats
     * failure just keeps the normal budget.
     */
    private fun scheduleRelayBandwidthCheck(pc: PeerConnection?) {
        if (pc == null) return
        main.postDelayed({
            val live = peerConnection ?: return@postDelayed
            if (live !== pc) return@postDelayed
            try {
                pc.getStats { report ->
                    val relayed = try {
                        val stats = report.statsMap
                        val pair = stats.values.firstOrNull {
                            it.type == "candidate-pair" &&
                                java.lang.Boolean.TRUE == it.members["selected"]
                        } ?: stats.values.firstOrNull {
                            it.type == "candidate-pair" && it.members["state"] == "succeeded"
                        }
                        if (pair == null) {
                            false
                        } else {
                            val localId = pair.members["localCandidateId"] as? String
                            val remoteId = pair.members["remoteCandidateId"] as? String
                            fun candidateType(id: String?): String? =
                                stats.values.firstOrNull { s ->
                                    (s.type == "local-candidate" || s.type == "remote-candidate") &&
                                        s.id == id
                                }?.members?.get("candidateType") as? String
                            candidateType(localId) == "relay" || candidateType(remoteId) == "relay"
                        }
                    } catch (_: Exception) {
                        false
                    }
                    if (relayed && relayMaxKbps == null) {
                        relayMaxKbps = 1500
                        diagLog("relay path selected — bitrate capped to 1500 kbps (relay data-saver)")
                        main.post { applyScreenBitrate(pc, senderVideoTrack) }
                    }
                }
            } catch (e: Exception) {
                diagLog("relay bandwidth check skipped: ${e.javaClass.simpleName}")
            }
        }, 6_000L)
    }

    // ------------------------------------------------------------------ //
    //  Helper (viewer)
    // ------------------------------------------------------------------ //

    /** Ask to watch the recipient's screen; the recipient then posts an offer. */
    fun startHelper(context: Context, sessionIdArg: String, uid: String, peerUidArg: String) {
        runOnMain {
            _info.value = null
            startCommon(context, sessionIdArg, uid, peerUidArg, isHelperArg = true)
            _state.value = ShareState.WAITING_PEER
            _info.value = "Preparing the viewer…"
            diagLog("watch start (epoch ${myEpoch?.take(6) ?: "?"})")
            scope.launch {
                dynamicIceServers = ConfigRepo.fetchTurnServers()
                diagLog(ConfigRepo.lastNote)
                quality = ConfigRepo.fetchQuality() // v0.9.7 — codec preference for the answer
                diagLog(ConfigRepo.lastQualityNote)
                main.post { beginHelperWatch() }
            }
        }
    }

    private fun beginHelperWatch() {
        try {
            ensureFactory()
            createPeer()
            watchSignals()
            val sid = sessionId ?: return
            val me = myUid ?: return
            val to = peerUid ?: return
            val ep = myEpoch ?: ""
            retryBudget = 3
            scope.launch {
                try {
                    SignalingRepo.send(sid, me, to, "watch", ep)
                    sentWatchAt = System.currentTimeMillis()
                    _info.value = "Asked the recipient's phone for the screen — connecting…"
                    diagLog("watch sent → waiting for the offer")
                    startTicker()
                } catch (e: Exception) {
                    fail("Could not ask to watch: ${e.message ?: e.javaClass.simpleName}")
                }
            }
        } catch (e: Exception) {
            fail("Could not start the viewer: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    /**
     * Helper stops watching. v0.8.1: NO "bye" is sent — this only closes the
     * local viewer; the recipient keeps sharing, so "Watch screen" can simply
     * be tapped again without the recipient re-sharing.
     */
    fun stopHelper() {
        runOnMain { teardown(sendBye = false) }
    }

    // ------------------------------------------------------------------ //
    //  Shared plumbing
    // ------------------------------------------------------------------ //

    private fun startCommon(
        context: Context?,
        sessionIdArg: String,
        uid: String,
        peerUidArg: String,
        isHelperArg: Boolean
    ) {
        if (_state.value != ShareState.IDLE) teardown(sendBye = false)
        if (context != null) appContext = context.applicationContext
        sessionId = sessionIdArg
        myUid = uid
        peerUid = peerUidArg
        isHelper = isHelperArg
        myEpoch = UUID.randomUUID().toString().replace("-", "").take(24)
        peerEpoch = null
        handled.clear()
        pendingIce.clear()
        remoteDescriptionSet = false
        framesSeen.set(0)
        frameSinkAdded = false
        answerSentAtMillis = 0L
        captureAlive = false
        retryBudget = 0
        retryRoundTag = ""
        sentWatchAt = 0L
        gotOfferAt = 0L
        candHost = 0
        candSrflx = 0
        candRelay = 0
        negotiatedCodec = null
        relayMaxKbps = null
        dataSaverKbps = null // v0.12.0 — the helper's cap dies with the round
        senderVideoTrack = null
        effectiveH264 = false
        encFpsCap = 30
        _netInfo.value = null
        _frameSize.value = null
        _assistStatus.value = null
        _assistDropped.value = 0
    }

    private fun ensureFactory() {
        if (factory != null) return
        val egl = EglBase.create()
        eglBase = egl
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(appContext!!)
                .createInitializationOptions()
        )
        factory = PeerConnectionFactory.builder()
            // (enableIntelVp8Encoder, enableH264HwEncoder) — both true since
            // v0.8.1: H264 hardware has ALWAYS been available to libwebrtc;
            // v0.9.7 merely decides whether we PREFER it over software VP8.
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(egl.eglBaseContext, true, true))
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(egl.eglBaseContext))
            .createPeerConnectionFactory()
        logCodecProbe()
    }

    /**
     * v0.9.7 — one-time per-process truth about this phone's video codecs.
     * A screenshot of the diagnostics now answers "does THIS phone even have
     * a hardware H264 encoder?" without adb or logcat.
     */
    private fun logCodecProbe() {
        if (probeLogged) return
        probeLogged = true
        try {
            val f = factory ?: return
            // stream-webrtc-android 1.3.8 ground truth (verified against the
            // artifact): capabilities take MediaStreamTrack.MediaType
            // (constants MEDIA_TYPE_VIDEO / MEDIA_TYPE_AUDIO) and return
            // RtpCapabilities whose codecs are List<CodecCapability> —
            // NOT VideoCodecInfo.
            val sendCaps =
                f.getRtpSenderCapabilities(MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO).codecs
            val recvCaps =
                f.getRtpReceiverCapabilities(MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO).codecs
            fun has(
                list: List<org.webrtc.RtpCapabilities.CodecCapability>,
                name: String
            ): Boolean = list.any { it.name.equals(name, ignoreCase = true) }
            h264Enc = has(sendCaps, "H264")
            h264Dec = has(recvCaps, "H264")
            val vp8Enc = has(sendCaps, "VP8")
            val vp8Dec = has(recvCaps, "VP8")
            diagLog(
                "codec probe — H264 enc=${if (h264Enc) "Y" else "N"} " +
                    "dec=${if (h264Dec) "Y" else "N"} · VP8 enc=${if (vp8Enc) "Y" else "N"} " +
                    "dec=${if (vp8Dec) "Y" else "N"}"
            )
        } catch (e: Throwable) {
            // Probe is best-effort diagnostics: ANY failure (including
            // NoSuchMethodError on a fork without the capabilities API)
            // must degrade to the proven VP8 default, never crash.
            h264Enc = false
            h264Dec = false
            diagLog("codec probe skipped: ${e.javaClass.simpleName}")
        }
    }

    private fun createPeer() {
        // thisPc is captured so late callbacks from a CLOSED pc (teardown)
        // can be recognised and ignored instead of corrupting a fresh round.
        var thisPc: PeerConnection? = null
        val observer = object : PeerConnection.Observer {
            private fun mine(): PeerConnection? {
                val pc = thisPc ?: return null
                return if (peerConnection === pc) pc else null
            }

            override fun onIceCandidate(candidate: IceCandidate) {
                if (mine() == null) return
                // v0.8.3 — candidate-type census (diagnostics shown in the UI).
                when {
                    candidate.sdp.contains("typ relay") -> candRelay++
                    candidate.sdp.contains("typ srflx") -> candSrflx++
                    candidate.sdp.contains("typ prflx") -> candSrflx++
                    else -> candHost++
                }
                val sid = sessionId ?: return
                val from = myUid ?: return
                val to = peerUid ?: return
                val ep = if (isHelper) {
                    myEpoch ?: return
                } else {
                    peerEpoch ?: return
                }
                val json = JSONObject()
                    .put("mid", candidate.sdpMid ?: "0")
                    .put("index", candidate.sdpMLineIndex)
                    .put("sdp", candidate.sdp)
                    .toString()
                scope.launch {
                    try {
                        SignalingRepo.send(sid, from, to, "ice", json, ep)
                    } catch (_: Exception) {
                        // one lost candidate is non-fatal
                    }
                }
            }

            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {
                if (mine() == null) return
                if (state == PeerConnection.IceGatheringState.COMPLETE) {
                    _netInfo.value =
                        "Network check — direct: $candHost · via STUN: $candSrflx · via relay: $candRelay"
                    diagLog("ICE done: direct=$candHost stun=$candSrflx relay=$candRelay")
                }
            }

            override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {
                val pc = mine() ?: return
                val track = receiver?.track() as? VideoTrack ?: return
                main.post {
                    if (peerConnection !== pc) return@post
                    remoteTrack = track
                    // v0.9.2 — one permanent proxy sink; the UI's surface is
                    // a swappable target, so no add/remove races can exist.
                    ensureRemoteProxy(track)
                    addFrameCounterIfMissing(track)
                    if (isHelper) {
                        _info.value = "Video channel ready — waiting for the connection…"
                    }
                }
            }

            override fun onConnectionChange(newState: PeerConnection.PeerConnectionState) {
                val pcNow = mine() ?: return
                main.post {
                    if (peerConnection !== pcNow) return@post
                    when (newState) {
                        PeerConnection.PeerConnectionState.CONNECTED -> {
                            diagLog("peer connection CONNECTED")
                            // v0.9.9 — find out whether this live path is a TURN
                            // relay and cap the sender bitrate if it is.
                            scheduleRelayBandwidthCheck(mine())
                            if (isHelper) {
                                retryBudget = 3 // success refills the auto-retry budget
                                _info.value = "Connection established — starting the video…"
                            } else {
                                _state.value = ShareState.LIVE
                                _info.value = "Live — the helper can see this screen."
                            }
                        }
                        PeerConnection.PeerConnectionState.DISCONNECTED -> {
                            diagLog("peer connection DISCONNECTED")
                            if (_state.value == ShareState.LIVE) {
                                _info.value = "Connection interrupted — trying to recover…"
                            }
                        }
                        PeerConnection.PeerConnectionState.FAILED -> {
                            diagLog("peer connection FAILED")
                            if (isHelper) {
                                helperRetryOrFail("The connection attempt failed")
                            } else if (captureAlive) {
                                // v0.8.3 — the share survives; only the link died.
                                recipientRecover(
                                    "Connection to the helper failed — the share stays ready. " +
                                        "Ask the helper to tap “Watch screen” again."
                                )
                            } else {
                                fail("Connection failed — tap “Stop sharing”, then share again.")
                            }
                        }
                        else -> Unit
                    }
                }
            }

            override fun onSignalingChange(state: PeerConnection.SignalingState?) = Unit
            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) = Unit
            override fun onIceConnectionReceivingChange(receiving: Boolean) = Unit
            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) = Unit
            override fun onAddStream(stream: MediaStream?) = Unit
            override fun onRemoveStream(stream: MediaStream?) = Unit
            override fun onDataChannel(channel: org.webrtc.DataChannel?) {
                // v0.9.6 — HELPER side: the recipient's offer carries the
                // "touch" channel. Verify the pc, then adopt it on the main
                // thread (the callback runs on WebRTC's signaling thread).
                val pcNow = mine() ?: return
                if (channel == null) return
                main.post {
                    if (peerConnection !== pcNow) return@post
                    if (isHelper) observeCtrl(channel)
                }
            }
            override fun onRenegotiationNeeded() = Unit
        }
        val config = PeerConnection.RTCConfiguration(baseIceServers() + dynamicIceServers)
        config.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        config.continualGatheringPolicy =
            PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        val pc = factory!!.createPeerConnection(config, observer)
        if (pc == null) {
            fail("Could not create the WebRTC engine on this phone.")
            return
        }
        thisPc = pc
        peerConnection = pc
        // v0.8.3 — when a recipient rebuilds its PeerConnection (self-heal),
        // the screen track must be re-attached to the NEW pc. On the very
        // first round this is a no-op (the track is created afterwards).
        if (!isHelper && captureAlive) {
            videoTrack?.let { t ->
                pc.addTrack(t, listOf("screen"))
                // v0.9.3 — rebuilt pc ⇒ the bitrate budget must be re-applied.
                applyScreenBitrate(pc, t)
            }
        }
        // v0.9.6 — INSTANT TOUCH: the OFFERER (recipient) creates the data
        // channel so the application m-line is inside every offer; the
        // helper receives it in onDataChannel. While this channel is open,
        // taps skip Firestore entirely (sub-100 ms instead of seconds).
        if (!isHelper) {
            try {
                val init = DataChannel.Init()
                init.ordered = true
                observeCtrl(pc.createDataChannel("touch", init))
            } catch (e: Exception) {
                diagLog("data channel create skipped: ${e.javaClass.simpleName}")
            }
        }
    }

    private fun watchSignals() {
        val sid = sessionId ?: return
        val me = myUid ?: return
        val stop = SignalingRepo.watch(
            sid,
            me,
            onSignal = { type, ep, payload, createdAt ->
                main.post { handleSignal(type, ep, payload, createdAt) }
            },
            onError = { e -> main.post { fail("Signal channel error: ${e.message ?: "unknown"}") } }
        )
        signalStop = stop
        // Clear stale documents addressed to me from earlier rounds.
        SignalingRepo.clearAsync(sid, me)
    }

    private fun handleSignal(type: String, ep: String, payload: String, createdAtMillis: Long) {
        val pc = peerConnection
        try {
            val key = type + "|" + payload.take(120)
            // ICE candidates, assist commands and their acks are all unique
            // (or must repeat); control messages may repeat safely.
            // v0.9.6 — cmdack is exempt too: every successful tap ack used to
            // carry the IDENTICAL payload, so the dedup swallowed all but the
            // first one and "Tap delivered ✓" appeared only once per session.
            if (type != "ice" && type != "cmd" && type != "cmdack" && !handled.add(key)) return

            when (type) {
                "watch" -> if (!isHelper && captureAlive && payload.length in 16..64) {
                    // v0.8.3 — accepted in ANY state while the capture is
                    // alive (WAITING_PEER, CONNECTING, LIVE or after a
                    // failed round). A fresh (possibly repeat) watch adopts
                    // the helper's epoch, then offers the screen with an
                    // ICE restart so every round carries brand-new
                    // credentials and re-gathered candidates.
                    diagLog("watch received (helper epoch ${payload.take(6)}) — offering")
                    peerEpoch = payload
                    // Fresh round ⇒ fresh remote-description state: helper
                    // candidates arriving between the offer and the answer
                    // must never be applied against the PREVIOUS round's
                    // remote description (they would be silently dropped).
                    remoteDescriptionSet = false
                    pendingIce.clear()
                    if (pc == null || pc.signalingState() == PeerConnection.SignalingState.CLOSED) {
                        // v0.9.2 — also rebuild a CLOSED pc; offering on a
                        // closed connection would throw and dead-end.
                        createPeer() // self-heal path: reconnect after a failure
                    }
                    val pcNow = peerConnection ?: return
                    _info.value = "The helper asked to watch — sending the screen…"
                    val restart = MediaConstraints()
                    restart.mandatory.add(MediaConstraints.KeyValuePair("IceRestart", "true"))
                    pcNow.createOffer(offerObserver(peerEpoch), restart)
                }
                "offer" -> if (isHelper && pc != null && !remoteDescriptionSet && ep == myEpoch) {
                    diagLog("offer received — applying…")
                    val sdp = JSONObject(payload)
                    // v0.9.7 — the offer's FIRST video payload type is what
                    // actually got preferred by the recipient; log it.
                    negotiatedCodec = firstVideoCodecOf(sdp.getString("sdp"))
                    diagLog("video codec negotiated (offer): ${negotiatedCodec ?: "?"}")
                    pc.setRemoteDescription(
                        setObserver("offer", myEpoch),
                        SessionDescription(SessionDescription.Type.OFFER, sdp.getString("sdp"))
                    )
                }
                "answer" -> if (!isHelper && pc != null && ep.isNotBlank() && ep == peerEpoch) {
                    diagLog("answer received — applying…")
                    val sdp = JSONObject(payload)
                    negotiatedCodec = firstVideoCodecOf(sdp.getString("sdp"))
                    diagLog("video codec negotiated (answer): ${negotiatedCodec ?: "?"}")
                    // v0.9.7 — H264 was wanted but the helper answered with
                    // something else (no HW decode there): a 60 fps software
                    // VP8 encode would churn and drop. Clamp back to the
                    // proven 30 fps encode ceiling immediately.
                    if (negotiatedCodec != "H264" && encFpsCap > 30) {
                        encFpsCap = 30
                        diagLog("VP8 negotiated — encode ceiling clamped to 30 fps")
                        applyScreenBitrate(peerConnection, videoTrack)
                    }
                    pc.setRemoteDescription(
                        setObserver("answer", peerEpoch),
                        SessionDescription(SessionDescription.Type.ANSWER, sdp.getString("sdp"))
                    )
                }
                "ice" -> {
                    val expected = if (isHelper) myEpoch else peerEpoch
                    if (pc == null) return
                    if (ep.isBlank() || ep != expected) return
                    val c = JSONObject(payload)
                    val candidate = IceCandidate(
                        c.optString("mid", "0"),
                        c.optInt("index", 0),
                        c.getString("sdp")
                    )
                    if (remoteDescriptionSet) {
                        try {
                            pc.addIceCandidate(candidate)
                        } catch (_: Exception) {
                        }
                    } else {
                        pendingIce.add(candidate)
                    }
                }
                "bye" -> {
                    val wasHelper = isHelper
                    teardown(sendBye = false)
                    _info.value = if (wasHelper) "The recipient stopped sharing."
                    else "The helper closed the view."
                }
                // Phase 8 — remote-assist commands (helper → recipient) and
                // their acknowledgements (recipient → helper). Both are
                // epoch-gated like every other signal, so a stale command from
                // an earlier round can never act on a fresh session.
                // v0.10.0 (Phase 9) — REPLAY/ABUSE GUARDS for "cmd": the doc
                // must be fresh (server createdAt within 15 s), the payload
                // small, and the applied-command rate inside the flood cap.
                // SDP/ICE/ack are untouched — only the injector path is gated.
                "cmd" -> if (!isHelper && captureAlive && ep.isNotBlank() && ep == peerEpoch) {
                    val age = if (createdAtMillis > 0) {
                        System.currentTimeMillis() - createdAtMillis
                    } else Long.MAX_VALUE
                    when {
                        age > cmdFreshMillis ->
                            diagLog("assist cmd refused (stale ${if (age == Long.MAX_VALUE) "no-timestamp" else "${age}ms"})")
                        payload.length > maxCmdChars ->
                            diagLog("assist cmd refused (oversized payload)")
                        else -> handleAssistCommand(JSONObject(payload))
                    }
                }
                "cmdack" -> if (isHelper && ep.isNotBlank() && ep == myEpoch) {
                    showAssistAck(payload)
                }
            }
        } catch (e: Exception) {
            // one malformed signal must never kill the engine — but it must
            // not stay invisible either (v0.9.2: the silent killer class).
            diagLog("signal '$type' error: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    /**
     * Phase 8 — perform one helper-requested gesture locally. Only possible
     * while the grantor enabled the assist service; the ack tells the
     * helper's UI which of the two worlds they are in. v0.9.6: called from
     * BOTH transports — the Firestore "cmd" path (epoch-gated by the
     * caller) and the p2p touch data channel (per-round by construction).
     *
     * v0.10.0 (Phase 9) — hardening at the injector boundary:
     *  - RATE CAP: at most 25 commands per sliding second (a human finger
     *    produces a handful; a flood would make the grantor's phone jumpy).
     *  - PAYLOAD SANITY: coordinates must be finite and are clamped to 0..1
     *    here (AssistService clamps again — two independent fences),
     *    durations are clamped. A malformed command acks "bad-payload"
     *    instead of acting.
     *  - The ack always tells the truth, even for refused commands.
     */
    private fun handleAssistCommand(obj: JSONObject) {
        val kind = obj.optString("kind")

        // ---- flood cap (sliding 1 s window) ----
        val now = System.currentTimeMillis()
        while (cmdTimes.isNotEmpty() && now - cmdTimes.first() > cmdRateWindowMillis) {
            cmdTimes.removeFirst()
        }
        if (cmdTimes.size >= cmdRateMax) {
            diagLog("assist $kind refused (rate cap)")
            sendAssistAck(kind, ok = false, reason = "rate")
            return
        }
        cmdTimes.addLast(now)

        /** Non-finite → null; otherwise clamped to the 0..1 command space. */
        fun sane(v: Double): Double? = if (v.isFinite()) v.coerceIn(0.0, 1.0) else null
        fun saneDur(v: Long, lo: Long, hi: Long): Long = v.coerceIn(lo, hi)

        var badPayload = false
        // v0.12.0 — the "text" command reports a finer outcome (no-field /
        // assist-off / failed) so the helper's UI can say what to do next.
        var textStatus = ""
        val ok: Boolean
        when (kind) {
            "tap" -> {
                val x = sane(obj.optDouble("x", Double.NaN))
                val y = sane(obj.optDouble("y", Double.NaN))
                badPayload = x == null || y == null
                ok = !badPayload && AssistService.tapNormalized(x!!, y!!)
            }
            // v0.9.9 (Phase 8.3) — swipe carries the helper's REAL drag time;
            // long-press defaults to 800 ms (Android's threshold is ~500 ms).
            "swipe" -> {
                val x1 = sane(obj.optDouble("x1", Double.NaN))
                val y1 = sane(obj.optDouble("y1", Double.NaN))
                val x2 = sane(obj.optDouble("x2", Double.NaN))
                val y2 = sane(obj.optDouble("y2", Double.NaN))
                badPayload = x1 == null || y1 == null || x2 == null || y2 == null
                ok = !badPayload && AssistService.swipeNormalized(
                    x1!!, y1!!, x2!!, y2!!, saneDur(obj.optLong("dur", 400L), 120L, 3_000L)
                )
            }
            "long" -> {
                val x = sane(obj.optDouble("x", Double.NaN))
                val y = sane(obj.optDouble("y", Double.NaN))
                badPayload = x == null || y == null
                ok = !badPayload && AssistService.longPressNormalized(
                    x!!, y!!, saneDur(obj.optLong("dur", 800L), 400L, 2_000L)
                )
            }
            // v0.12.0 (Phase 8.3 completion) — TYPING: place the helper's
            // text into the focused editable field. The payload carries only
            // the text to PLACE (never anything read from the screen); the
            // diag log records its LENGTH, never its content. SET_TEXT
            // replaces the field's current content — the helper's typing
            // dialog says so, because honest tools say what they do to data.
            "text" -> {
                val t = obj.optString("t", "")
                badPayload = t.isEmpty() || t.length > maxTextChars
                if (badPayload) {
                    ok = false
                } else {
                    textStatus = AssistService.typeTextStatus(t)
                    ok = textStatus == "ok"
                }
            }
            // v0.12.0 — DATA SAVER: the helper caps the stream's bitrate at
            // 600 kbps, or restores the normal budget. Video-only — no
            // gesture, no assist-service involvement; works exactly as long
            // as the capture does (the caller already epoch-gated it).
            "qcap" -> {
                val on = obj.optBoolean("on", false)
                dataSaverKbps = if (on) 600 else null
                main.post { applyScreenBitrate(peerConnection, senderVideoTrack) }
                diagLog("data saver ${if (on) "ON — 600 kbps cap applied" else "OFF — normal budget restored"}")
                ok = true
            }
            "back" -> ok = AssistService.pressBack()
            "home" -> ok = AssistService.pressHome()
            "recents" -> ok = AssistService.pressRecents()
            else -> {
                sendAssistAck(kind, ok = false, reason = "unknown-kind")
                return
            }
        }
        // v0.9.6 — log WHERE the tap landed (percent of the real display) so
        // one screenshot proves the precision fix worked. v0.9.9 — swipes and
        // long-presses land in the same place, so the same proof exists.
        val where = when {
            kind == "tap" && obj.has("x") ->
                " @${(obj.optDouble("x", 0.0) * 100).toInt()}%,${(obj.optDouble("y", 0.0) * 100).toInt()}%"
            kind == "long" && obj.has("x") ->
                " @${(obj.optDouble("x", 0.0) * 100).toInt()}%,${(obj.optDouble("y", 0.0) * 100).toInt()}% held ${obj.optLong("dur", 800L)}ms"
            kind == "swipe" && obj.has("x1") ->
                " @${(obj.optDouble("x1", 0.0) * 100).toInt()}%,${(obj.optDouble("y1", 0.0) * 100).toInt()}%" +
                    "→${(obj.optDouble("x2", 0.0) * 100).toInt()}%,${(obj.optDouble("y2", 0.0) * 100).toInt()}% in ${obj.optLong("dur", 400L)}ms"
            // v0.12.0 — the LENGTH of a typed message is loggable; its
            // content is not (and never will be).
            kind == "text" ->
                " (${obj.optString("t", "").length} chars)"
            else -> ""
        }
        diagLog("assist $kind$where → ${when {
            ok -> "performed"
            badPayload -> "bad-payload"
            else -> "assist-off"
        }}")
        // v0.12.0 — "text" failures are not dropped TAPS: a no-field result
        // means the helper should tap the field first, not that the grantor's
        // assist is broken — so the recipient's loud alarm stays truthful.
        if (!ok && kind != "text") {
            runOnMain { _assistDropped.value += 1 }
        }
        sendAssistAck(
            kind,
            ok = ok,
            reason = when {
                ok -> ""
                badPayload -> "bad-payload"
                // v0.12.0 — "text" carries its precise failure out of the
                // injector (no-field / assist-off / failed) so the helper's
                // banner can be actionable instead of accusatory.
                kind == "text" -> textStatus.ifBlank { "assist-off" }
                else -> "assist-off"
            }
        )
    }

    /**
     * v0.10.0 (Phase 9) — one truthful ack for every command outcome (the
     * old code only acked after a gesture attempt; refused commands now ack
     * too, so the helper's UI never lies). Data channel first, Firestore
     * cmdack as the fallback.
     */
    private fun sendAssistAck(kind: String, ok: Boolean, reason: String) {
        val ack = JSONObject()
            .put("kind", kind)
            .put("ok", ok)
            .put("reason", reason)
            .toString()
        if (sendCtrl(ack)) return
        val sid = sessionId ?: return
        val me = myUid ?: return
        val to = peerUid ?: return
        val ep = peerEpoch ?: return
        scope.launch {
            try {
                SignalingRepo.send(sid, me, to, "cmdack", ack, ep)
            } catch (_: Exception) {
                // a lost ack must never break the stream
            }
        }
    }

    /**
     * v0.9.6 — watch the touch data channel. OPEN ⇒ taps and acks travel
     * peer-to-peer (no Firestore round-trip); CLOSED/CLOSING silently falls
     * back to the Firestore "cmd"/"cmdack" path as before.
     */
    private fun observeCtrl(dc: DataChannel) {
        try {
            ctrlChannel?.close()
        } catch (_: Exception) {
        }
        ctrlChannel = dc
        dc.registerObserver(object : DataChannel.Observer {
            override fun onBufferedAmountChange(previousAmount: Long) = Unit
            override fun onStateChange() {
                val st = dc.state()
                main.post {
                    if (ctrlChannel !== dc) return@post
                    if (st == DataChannel.State.OPEN) {
                        diagLog("touch channel OPEN — taps now instant (p2p)")
                    }
                }
            }

            override fun onMessage(buffer: DataChannel.Buffer) {
                val text = try {
                    val bytes = ByteArray(buffer.data.remaining())
                    buffer.data.get(bytes)
                    String(bytes, Charsets.UTF_8)
                } catch (_: Exception) {
                    return
                }
                // v0.10.0 (Phase 9) — p2p size cap: a real command/ack is a
                // few hundred bytes; anything bigger is malformed or hostile.
                if (text.length > maxCmdChars) return
                main.post {
                    if (ctrlChannel !== dc) return@post
                    try {
                        if (isHelper) {
                            showAssistAck(text)
                        } else if (captureAlive) {
                            handleAssistCommand(JSONObject(text))
                        }
                    } catch (_: Exception) {
                    }
                }
            }
        })
        // The channel can already be OPEN when it reaches the helper.
        if (dc.state() == DataChannel.State.OPEN) {
            main.post {
                if (ctrlChannel === dc) diagLog("touch channel OPEN — taps now instant (p2p)")
            }
        }
    }

    /**
     * v0.9.6 — send one text message over the touch channel; false = the
     * channel is not usable (not open / buffer crowded) → caller falls back.
     */
    private fun sendCtrl(text: String): Boolean {
        val dc = ctrlChannel ?: return false
        if (dc.state() != DataChannel.State.OPEN) return false
        return try {
            if (dc.bufferedAmount() > 64_000) return false
            dc.send(DataChannel.Buffer(ByteBuffer.wrap(text.toByteArray(Charsets.UTF_8)), false))
            true
        } catch (_: Exception) {
            false
        }
    }

    /** v0.9.6 — forget the touch channel of a closing pc. */
    private fun dropCtrl() {
        try {
            ctrlChannel?.close()
        } catch (_: Exception) {
        }
        ctrlChannel = null
    }

    /** Phase 8 — one-line helper-side feedback for the last assist command. */
    private fun showAssistAck(payload: String) {
        val obj = try {
            JSONObject(payload)
        } catch (_: Exception) {
            return
        }
        if (obj.optBoolean("ok")) {
            _assistStatus.value = when (obj.optString("kind")) {
                "back" -> "Back delivered ✓"
                "home" -> "Home delivered ✓"
                "recents" -> "Recents delivered ✓"
                "swipe" -> "Swipe delivered ✓"
                "long" -> "Long-press delivered ✓"
                // v0.12.0 (Phase 8.3 completion) — the two new commands.
                "text" -> "Text delivered ✓ · লেখা বসেছে"
                "qcap" -> "Data saver applied ✓ · ছবির মান আপডেট হয়েছে"
                else -> "Tap delivered ✓"
            }
            // Success clears itself shortly.
            main.removeCallbacks(assistClear)
            main.postDelayed(assistClear, 4000)
        } else {
            // v0.9.3 — PERSISTENT + actionable. The 4-second auto-clear made
            // the warning easy to miss: the 21:25 Dhaka session fired 30+
            // taps while the recipient's assist stayed OFF the whole time.
            // The banner now stays until a command is actually delivered
            // (each new ack replaces it) or the viewer closes.
            // v0.9.5 — bilingual: the 28 Oct session proved the English-only
            // banner never reached the family (they kept tapping for 21 min).
            // v0.12.0 — typing gets its own honest reasons: "no-field" tells
            // the helper exactly what to do (tap the field first), and a
            // protected field refusal is distinguished from assist-off.
            val kind = obj.optString("kind")
            val reason = obj.optString("reason")
            _assistStatus.value = when {
                kind == "text" && reason == "no-field" ->
                    "No focused text field on their phone — first tap the field with " +
                        "remote touch, then send the text again.\n" +
                        "ওদের ফোনে কোনো লেখার ঘর ফোকাসড নেই — আগে রিমোট টাচ দিয়ে ঘরটিতে ট্যাপ " +
                        "করুন, তারপর আবার লেখা পাঠান।"
                kind == "text" && reason == "failed" ->
                    "Their phone refused the text — that field may be protected.\n" +
                        "ওদের ফোন লেখাটি নিতে রাজি হয়নি — ঘরটি সুরক্ষিত হতে পারে।"
                kind == "qcap" ->
                    "Could not change video quality — try again while the stream is live.\n" +
                        "ছবি চালু অবস্থায় আবার চেষ্টা করুন — এখন মান বদলানো গেল না।"
                else ->
                    "Remote assist is OFF on their phone — taps cannot land.\n" +
                        "ওদের ফোনে Remote assist বন্ধ — ট্যাপ কাজ করছে না। " +
                        "ওদের স্ক্রিনে “Turn on Remote assist · রিমোট টাচ চালু করুন” বাটনে " +
                        "ট্যাপ করতে বলুন (একবারই সেটআপ লাগবে)।"
            }
            main.removeCallbacks(assistClear)
        }
    }

    /**
     * Phase 8 — helper sends one assist command. Valid while the viewer is
     * connecting or live; the recipient applies its own epoch/session gates.
     */
    private fun sendAssistCmd(json: String) {
        runOnMain {
            if (!isHelper) return@runOnMain
            val st = _state.value
            if (st != ShareState.CONNECTING && st != ShareState.LIVE) return@runOnMain
            // v0.9.6 — the data channel makes taps feel instant (<100 ms).
            // Firestore stays as the fallback while the channel negotiates.
            if (sendCtrl(json)) return@runOnMain
            val sid = sessionId ?: return@runOnMain
            val me = myUid ?: return@runOnMain
            val to = peerUid ?: return@runOnMain
            val ep = myEpoch ?: return@runOnMain
            scope.launch {
                try {
                    SignalingRepo.send(sid, me, to, "cmd", json, ep)
                } catch (_: Exception) {
                }
            }
        }
    }

    /** Tap the recipient's screen at normalized (0..1) coordinates. */
    fun sendAssistTap(nx: Double, ny: Double) =
        sendAssistCmd("{" + "\"kind\":\"tap\",\"x\":$nx,\"y\":$ny" + "}")

    /**
     * v0.9.9 (Phase 8.3) — swipe from A to B. [durMs] is the helper's REAL
     * drag time (the UI sends elapsed milliseconds, clamped app-side) so a
     * slow finger produces a slow remote scroll, not a violent fling.
     */
    fun sendAssistSwipe(nx1: Double, ny1: Double, nx2: Double, ny2: Double, durMs: Long) =
        sendAssistCmd(
            "{" + "\"kind\":\"swipe\",\"x1\":$nx1,\"y1\":$ny1,\"x2\":$nx2,\"y2\":$ny2,\"dur\":$durMs" + "}"
        )

    /** v0.9.9 (Phase 8.3) — long-press for context menus / text selection. */
    fun sendAssistLongPress(nx: Double, ny: Double, durMs: Long = 800L) =
        sendAssistCmd("{" + "\"kind\":\"long\",\"x\":$nx,\"y\":$ny,\"dur\":$durMs" + "}")

    /** One of the three global navigation actions: back / home / recents. */
    fun sendAssistNav(kind: String) =
        sendAssistCmd("{" + "\"kind\":\"$kind\"" + "}")

    /**
     * v0.12.0 (Phase 8.3 completion) — type text into the recipient's
     * focused editable field. JSONObject builds the payload, so ANY text
     * (quotes, newlines, emoji) travels as valid JSON. Capped at the
     * engine's 500-char command budget; the dialog already mirrors it.
     */
    fun sendAssistText(text: String) =
        sendAssistCmd(
            JSONObject().put("kind", "text").put("t", text.take(500)).toString()
        )

    /**
     * v0.12.0 (Phase 8.3 completion) — flip the helper's data-saver cap.
     * on=true ⇒ hard 600 kbps ceiling; on=false ⇒ back to the normal
     * budget (relay-capped if the path is relayed). No capture restart.
     */
    fun sendAssistDataSaver(on: Boolean) =
        sendAssistCmd("{" + "\"kind\":\"qcap\",\"on\":$on" + "}")

    /**
     * v0.8.3 — helper-side stuck-round recovery. [reason] is the honest
     * statement of what stalled; a fresh round (new epoch, new
     * PeerConnection, new "watch") is attempted automatically while the
     * retry budget lasts.
     */
    private fun helperRetryOrFail(reason: String) {
        runOnMain {
            if (!isHelper || _state.value == ShareState.ENDED) return@runOnMain
            val ep = myEpoch ?: return@runOnMain
            // One retry decision per round, ever — the ticker timeout and a
            // hard FAILED callback can fire for the SAME round seconds
            // apart; only the first may restart it.
            if (retryRoundTag == ep) return@runOnMain
            retryRoundTag = ep
            if (retryBudget > 0) {
                retryBudget--
                helperRestartRound(reason)
            } else {
                fail(
                    "Connection failed — the two phones could not link up. " +
                        "Tap “Watch screen” again; if it keeps failing, put both " +
                        "phones on the same Wi-Fi network (or one phone's hotspot)."
                )
            }
        }
    }

    /** Rebuild only the connection (capture has nothing to do with it). */
    private fun helperRestartRound(reason: String) {
        main.removeCallbacks(ticker)
        tickerRunning = false
        try {
            peerConnection?.close()
        } catch (_: Exception) {
        }
        peerConnection = null
        dropCtrl()
        if (frameSinkAdded) {
            try {
                remoteTrack?.removeSink(frameCounter)
            } catch (_: Exception) {
            }
            frameSinkAdded = false
        }
        try {
            remoteProxyAttachedTo?.removeSink(remoteProxySink)
        } catch (_: Exception) {
        }
        remoteProxyAttachedTo = null
        remoteTrack = null
        handled.clear()
        pendingIce.clear()
        remoteDescriptionSet = false
        framesSeen.set(0)
        renderedFrames.set(0)
        answerSentAtMillis = 0L
        gotOfferAt = 0L
        _frameSize.value = null
        myEpoch = UUID.randomUUID().toString().replace("-", "").take(24)
        diagLog("retry round ${myEpoch?.take(6)} — $reason")
        _state.value = ShareState.WAITING_PEER
        _info.value = "$reason — trying again automatically…"
        val sid = sessionId ?: return
        val me = myUid ?: return
        val to = peerUid ?: return
        val ep = myEpoch ?: return
        createPeer()
        SignalingRepo.clearAsync(sid, me)
        scope.launch {
            try {
                SignalingRepo.send(sid, me, to, "watch", ep)
                sentWatchAt = System.currentTimeMillis()
                _info.value = "$reason — asked the recipient's phone again…"
                startTicker()
            } catch (e: Exception) {
                fail("Could not ask to watch: ${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    /**
     * v0.8.3 — recipient-side failure recovery: close ONLY the
     * PeerConnection, keep the capture/track/listener, and wait in
     * WAITING_PEER for the next "watch". The old behaviour (full teardown →
     * "Stop sharing" + re-consent on the recipient phone) is what made a
     * single failed round so painful.
     */
    private fun recipientRecover(message: String) {
        runOnMain {
            if (isHelper || !captureAlive) return@runOnMain
            val pc = peerConnection
            try {
                pc?.close()
            } catch (_: Exception) {
            }
            peerConnection = null
            dropCtrl()
            handled.clear()
            pendingIce.clear()
            remoteDescriptionSet = false
            diagLog("recipient recovered — pc rebuilt, capture kept")
            _state.value = ShareState.WAITING_PEER
            _info.value = message
        }
    }

    private fun offerObserver(expectedEpoch: String?): SdpObserver = object : SdpObserver by noop {
        override fun onCreateSuccess(desc: SessionDescription?) {
            val pc = peerConnection ?: return
            val sid = sessionId ?: return
            val me = myUid ?: return
            val to = peerUid ?: return
            val ep = peerEpoch ?: return
            if (desc == null) return
            if (expectedEpoch == null || ep != expectedEpoch) return
            val sdp = preferCodec(desc.description)
            val out = SessionDescription(desc.type, sdp)
            pc.setLocalDescription(noop, out)
            val json = JSONObject()
                .put("type", desc.type.canonicalForm())
                .put("sdp", sdp)
                .toString()
            scope.launch {
                try {
                    SignalingRepo.send(sid, me, to, "offer", json, ep)
                    diagLog("offer sent — waiting for the helper's answer")
                    _state.value = ShareState.CONNECTING
                    _info.value = "Offer sent — waiting for the helper's answer…"
                } catch (e: Exception) {
                    diagLog("offer send FAILED: ${e.message ?: e.javaClass.simpleName}")
                    fail("Could not send the offer: ${e.message ?: e.javaClass.simpleName}")
                }
            }
        }

        override fun onCreateFailure(error: String?) {
            // v0.9.2 — used to vanish into a noop: the helper then waited
            // 12 s for an offer that never came. Keep the capture alive; the
            // helper's retry (new watch) re-offers.
            diagLog("createOffer FAILED: ${error ?: "unknown"}")
            runOnMain {
                if (isHelper || !captureAlive) return@runOnMain
                if (_state.value == ShareState.ENDED || _state.value == ShareState.IDLE) return@runOnMain
                _state.value = ShareState.WAITING_PEER
                _info.value = "Could not prepare the screen offer — the helper will retry automatically."
            }
        }
    }

    private fun setObserver(what: String, expectedEpoch: String?): SdpObserver = object : SdpObserver by noop {
        override fun onSetSuccess() {
            val pc = peerConnection ?: return
            val sid = sessionId ?: return
            val me = myUid ?: return
            val to = peerUid ?: return
            val currentEp = if (isHelper) myEpoch else peerEpoch
            if (expectedEpoch != null && currentEp != expectedEpoch) return
            if (isHelper && what == "offer") {
                remoteDescriptionSet = true
                gotOfferAt = System.currentTimeMillis()
                flushIce()
                val epExpected = expectedEpoch
                pc.createAnswer(object : SdpObserver by noop {
                    override fun onCreateSuccess(desc: SessionDescription?) {
                        val pc2 = peerConnection ?: return
                        if (desc == null) return
                        if (expectedEpoch == null || myEpoch != epExpected) return
                        val sdp = preferCodec(desc.description)
                        val out = SessionDescription(desc.type, sdp)
                        pc2.setLocalDescription(noop, out)
                        val json = JSONObject()
                            .put("type", desc.type.canonicalForm())
                            .put("sdp", sdp)
                            .toString()
                        scope.launch {
                            try {
                                answerSentAtMillis = System.currentTimeMillis()
                                SignalingRepo.send(
                                    sid, me, to, "answer", json, myEpoch ?: ""
                                )
                                diagLog("answer sent — linking up")
                                _state.value = ShareState.CONNECTING
                                _info.value = "Answer sent — connecting video…"
                            } catch (e: Exception) {
                                diagLog("answer send FAILED: ${e.message ?: e.javaClass.simpleName}")
                                fail("Could not send the answer: ${e.message ?: e.javaClass.simpleName}")
                            }
                        }
                    }

                    override fun onCreateFailure(error: String?) {
                        // v0.9.2 — a failed createAnswer used to end the round
                        // in total silence (the 19:29 rounds: the recipient's
                        // offers sat in Firestore unanswered). Loud + retrying.
                        diagLog("createAnswer FAILED: ${error ?: "unknown"}")
                        runOnMain {
                            if (isHelper) {
                                helperRetryOrFail("Could not answer the recipient's offer")
                            } else if (captureAlive) {
                                recipientRecover("Could not answer — the helper will retry automatically.")
                            }
                        }
                    }
                }, MediaConstraints())
            } else if (!isHelper && what == "answer") {
                remoteDescriptionSet = true
                flushIce()
                _state.value = ShareState.CONNECTING
                _info.value = "Negotiation done — connecting to the helper…"
            }
            // "local" descriptions carry no further action.
        }

        override fun onSetFailure(error: String?) {
            // v0.9.2 — the silent killer: a failed setRemoteDescription used
            // to disappear (noop observer); the offer/answer was then never
            // processed and the round dead-ended forever. Now it is visible
            // and routed to the retry/recover machinery.
            diagLog("set$what FAILED: ${error ?: "unknown"}")
            runOnMain {
                val currentEp = if (isHelper) myEpoch else peerEpoch
                if (expectedEpoch != null && currentEp != expectedEpoch) return@runOnMain
                if (isHelper) {
                    helperRetryOrFail("Could not apply the recipient's offer")
                } else if (captureAlive) {
                    recipientRecover("Could not apply the helper's answer — the helper will retry.")
                } else {
                    fail("Could not apply the helper's answer.")
                }
            }
        }
    }

    private fun flushIce() {
        val pc = peerConnection ?: return
        for (c in pendingIce) {
            try {
                pc.addIceCandidate(c)
            } catch (_: Exception) {
            }
        }
        pendingIce.clear()
    }

    private fun addFrameCounterIfMissing(track: VideoTrack) {
        if (frameSinkAdded) return
        try {
            track.addSink(frameCounter)
            frameSinkAdded = true
        } catch (_: Exception) {
        }
    }

    private fun startTicker() {
        if (tickerRunning) return
        tickerRunning = true
        main.postDelayed(ticker, 1000)
    }

    /**
     * v0.9.7 — reorder the video m-line payload types so the OWNER-CHOSEN
     * codec (config/quality → codec; VP8 default) is negotiated first, with
     * its rtx companion right behind it. REORDER ONLY: every other payload
     * type stays, so a peer without usable H264 simply answers with VP8 and
     * the session proceeds exactly like the proven v0.9.6 path.
     */
    private fun preferCodec(sdp: String): String {
        return try {
            val target = if (quality.codec == "h264") "H264" else "VP8"
            val lines = sdp.split("\r\n").toMutableList()
            var mLineIndex = -1
            for (i in lines.indices) {
                if (lines[i].startsWith("m=video")) {
                    mLineIndex = i
                    break
                }
            }
            if (mLineIndex < 0) return sdp
            val codecOf = HashMap<String, String>()
            val aptOf = HashMap<String, String>()
            for (line in lines) {
                if (line.startsWith("a=rtpmap:")) {
                    val rest = line.substringAfter("a=rtpmap:")
                    val pt = rest.substringBefore(' ').trim()
                    val name = rest.substringAfter(' ', "").substringBefore('/').uppercase()
                    if (pt.isNotEmpty() && name.isNotEmpty()) codecOf[pt] = name
                } else if (line.startsWith("a=fmtp:")) {
                    val pt = line.substringAfter("a=fmtp:").substringBefore(' ').trim()
                    val apt = line.substringAfter("apt=", "").substringBefore(' ').trim()
                    if (pt.isNotEmpty() && apt.isNotEmpty()) aptOf[pt] = apt
                }
            }
            val parts = lines[mLineIndex].split(" ")
            if (parts.size <= 3) return sdp
            val header = parts.take(3)
            val pts = parts.drop(3)
            val picked = pts.filter { codecOf[it] == target }
            if (picked.isEmpty()) return sdp
            val pickedSet = picked.toSet()
            val rtx = pts.filter { aptOf[it] in pickedSet }
            val front = (picked + rtx).distinct()
            val rest = pts.filter { it !in front }
            lines[mLineIndex] = (header + front + rest).joinToString(" ")
            lines.joinToString("\r\n")
        } catch (_: Exception) {
            sdp
        }
    }

    /**
     * v0.9.7 — name of the FIRST payload type on the video m-line (the one
     * that would actually be used). Parsed from RECEIVED SDP so the diag
     * log proves the real negotiation result, not our wish.
     */
    private fun firstVideoCodecOf(sdp: String): String? = try {
        val mLine = sdp.split("\r\n").firstOrNull { it.startsWith("m=video") } ?: return null
        val firstPt = mLine.split(" ").getOrNull(3) ?: return null
        val prefix = "a=rtpmap:$firstPt "
        val map = sdp.split("\r\n").firstOrNull { it.startsWith(prefix) } ?: return null
        map.substringAfter(prefix, "").substringBefore('/').uppercase().ifEmpty { null }
    } catch (_: Exception) {
        null
    }

    /**
     * UI hands over the render surface (helper side). v0.9.2 — this only
     * swaps the proxy's forwarding target: no addSink/removeSink races, and
     * releasing a view can never detach the stream from the NEXT view.
     */
    fun attachRenderer(view: SurfaceViewRenderer?) {
        runOnMain { rendererTarget = view }
    }

    /** Detach the render surface (called from composition dispose). */
    fun detachRenderer() {
        runOnMain { rendererTarget = null }
    }

    /** UI hands over the recipient's small self-preview surface. */
    fun attachLocalPreview(view: SurfaceViewRenderer?) {
        runOnMain { previewTarget = view }
    }

    /** Detach the recipient's self-preview surface. */
    fun detachLocalPreview() {
        runOnMain { previewTarget = null }
    }

    /** Recipient side stops (service destroy or UI stop). Posts "bye". */
    fun stopRecipient() {
        runOnMain { teardown(sendBye = true) }
    }

    private fun teardown(sendBye: Boolean) {
        if (sendBye) {
            val sid = sessionId
            val me = myUid
            val to = peerUid
            if (sid != null && me != null && to != null) {
                scope.launch {
                    try {
                        SignalingRepo.send(sid, me, to, "bye", "")
                    } catch (_: Exception) {
                    }
                }
            }
        }
        main.removeCallbacks(ticker)
        tickerRunning = false
        signalStop?.invoke()
        signalStop = null
        try {
            capturer?.stopCapture()
        } catch (_: Exception) {
        }
        try {
            capturer?.dispose()
        } catch (_: Exception) {
        }
        capturer = null
        try {
            surfaceHelper?.dispose()
        } catch (_: Exception) {
        }
        surfaceHelper = null
        // v0.9.2 — detach the proxy targets, then dispose the local track.
        rendererTarget = null
        previewTarget = null
        try {
            videoTrack?.removeSink(localProxySink)
        } catch (_: Exception) {
        }
        try {
            videoTrack?.dispose()
        } catch (_: Exception) {
        }
        videoTrack = null
        try {
            videoSource?.dispose()
        } catch (_: Exception) {
        }
        videoSource = null
        if (frameSinkAdded) {
            try {
                remoteTrack?.removeSink(frameCounter)
            } catch (_: Exception) {
            }
            frameSinkAdded = false
        }
        try {
            remoteProxyAttachedTo?.removeSink(remoteProxySink)
        } catch (_: Exception) {
        }
        remoteProxyAttachedTo = null
        remoteTrack = null
        try {
            peerConnection?.close()
        } catch (_: Exception) {
        }
        peerConnection = null
        dropCtrl()
        sessionId = null
        myUid = null
        peerUid = null
        myEpoch = null
        peerEpoch = null
        pendingIce.clear()
        handled.clear()
        remoteDescriptionSet = false
        framesSeen.set(0)
        answerSentAtMillis = 0L
        captureAlive = false
        retryBudget = 0
        retryRoundTag = ""
        sentWatchAt = 0L
        gotOfferAt = 0L
        candHost = 0
        candSrflx = 0
        candRelay = 0
        _netInfo.value = null
        _frameSize.value = null
        _frameStats.value = null
        renderedFrames.set(0)
        previewFrames.set(0)
        main.removeCallbacks(assistClear)
        _assistStatus.value = null
        _assistDropped.value = 0
        cmdTimes.clear()
        relayMaxKbps = null
        dataSaverKbps = null // v0.12.0 — a fresh session starts uncapped
        senderVideoTrack = null
        _state.value = ShareState.IDLE
    }

    private fun fail(message: String) {
        diagLog("FAIL: $message")
        _info.value = message
        _state.value = ShareState.ENDED
        // Auto-idle shortly so the UI can offer a fresh start.
        main.postDelayed({
            if (_state.value == ShareState.ENDED) teardown(sendBye = false)
        }, 4000)
    }

    private fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else main.post(block)
    }

    private val noop = object : SdpObserver {
        override fun onCreateSuccess(desc: SessionDescription?) = Unit
        override fun onSetSuccess() = Unit
        override fun onCreateFailure(error: String?) = Unit
        override fun onSetFailure(error: String?) = Unit
    }
}
