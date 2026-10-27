package com.family.remote.support

import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjection
import android.os.Handler
import android.os.Looper
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
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
 *  6. VP8 PREFERRED + free TURN — both sides reorder the video m-line to
 *     negotiate VP8 (software codec: identical behaviour on every phone,
 *     avoids broken H.264 hardware for screen content), and a free community
 *     TURN relay is added after the Google STUN servers for hard-NAT cases.
 *     If the relay is unavailable ICE simply falls back to STUN.
 *
 * Threading: every WebRTC object is created and driven from the main thread
 * (fine for one ~720p screen stream); observer callbacks hop back onto the
 * main handler before touching engine state.
 */
object ScreenShareEngine {

    /** STUN for public address discovery; TURN as the hard-NAT fallback. */
    private val ICE_SERVERS = listOf(
        PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
        PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer(),
        PeerConnection.IceServer.builder("turn:openrelay.metered.ca:80")
            .setUsername("openrelayproject")
            .setPassword("openrelayproject")
            .createIceServer(),
        PeerConnection.IceServer.builder("turn:openrelay.metered.ca:443?transport=tcp")
            .setUsername("openrelayproject")
            .setPassword("openrelayproject")
            .createIceServer()
    )

    private val main = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(Dispatchers.Main)

    private val _state = MutableStateFlow(ShareState.IDLE)
    val state: StateFlow<ShareState> = _state

    /** One-line human status for the UI ("Connecting…", errors, …). */
    private val _info = MutableStateFlow<String?>(null)
    val info: StateFlow<String?> = _info

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

    private var renderer: SurfaceViewRenderer? = null
    private var localPreview: SurfaceViewRenderer? = null
    private val pendingIce = mutableListOf<IceCandidate>()
    private var remoteDescriptionSet = false
    /** De-dupes control messages (offer/answer/watch applied once per round). */
    private val handled = mutableSetOf<String>()

    // Frame watchdog (helper side): counts decoded frames of the remote track.
    private val framesSeen = AtomicLong(0)
    private var frameSinkAdded = false
    private var answerSentAtMillis = 0L
    private var noVideoWarned = false
    private var tickerRunning = false
    private val frameCounter = VideoSink { _ -> framesSeen.incrementAndGet() }
    private val ticker = object : Runnable {
        override fun run() {
            if (!isHelper || peerConnection == null) {
                tickerRunning = false
                return
            }
            val seen = framesSeen.get()
            val st = _state.value
            if (seen > 0 && (st == ShareState.CONNECTING || st == ShareState.WAITING_PEER)) {
                _state.value = ShareState.LIVE
                _info.value = "Live — receiving the screen ✓"
            } else if (seen == 0L && st == ShareState.CONNECTING &&
                answerSentAtMillis > 0 &&
                System.currentTimeMillis() - answerSentAtMillis > 12_000 &&
                !noVideoWarned
            ) {
                noVideoWarned = true
                _info.value =
                    "Connected, but no video is arriving — tap “Stop watching”, then “Watch screen” again."
            }
            main.postDelayed(this, 1000)
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
            try {
                ensureFactory()
                createPeer()

                val dm = context.resources.displayMetrics
                var w = dm.widthPixels
                var h = dm.heightPixels
                val maxDim = maxOf(w, h)
                if (maxDim > 1280) {
                    val scale = 1280.0 / maxDim
                    w = (w * scale / 2).toInt() * 2
                    h = (h * scale / 2).toInt() * 2
                }

                val helper = SurfaceTextureHelper.create("ScreenCap", eglBase!!.eglBaseContext)
                val source = factory!!.createVideoSource(true)
                val cap = ScreenCapturerAndroid(projectionData, object : MediaProjection.Callback() {})
                cap.initialize(helper, context.applicationContext, source.capturerObserver)
                cap.startCapture(w, h, 15)

                val track = factory!!.createVideoTrack("screen", source)
                peerConnection!!.addTrack(track, listOf("screen"))

                surfaceHelper = helper
                videoSource = source
                videoTrack = track
                capturer = cap
                localPreview?.let { pv ->
                    try {
                        track.addSink(pv)
                    } catch (_: Exception) {
                    }
                }
                _state.value = ShareState.WAITING_PEER
                _info.value = "Screen capture ready — waiting for the helper to tap “Watch screen”."
                watchSignals()
            } catch (e: Exception) {
                fail("Screen capture failed: ${e.message ?: e.javaClass.simpleName}")
            }
        }
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
            try {
                ensureFactory()
                createPeer()
                watchSignals()
                val sid = sessionIdArg
                val me = uid
                val to = peerUidArg
                val ep = myEpoch ?: ""
                scope.launch {
                    try {
                        SignalingRepo.send(sid, me, to, "watch", ep)
                        _info.value = "Asked the recipient's phone for the screen — connecting…"
                        startTicker()
                    } catch (e: Exception) {
                        fail("Could not ask to watch: ${e.message ?: e.javaClass.simpleName}")
                    }
                }
            } catch (e: Exception) {
                fail("Could not start the viewer: ${e.message ?: e.javaClass.simpleName}")
            }
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
        noVideoWarned = false
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
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(egl.eglBaseContext, true, true))
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(egl.eglBaseContext))
            .createPeerConnectionFactory()
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

            override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {
                val pc = mine() ?: return
                val track = receiver?.track() as? VideoTrack ?: return
                main.post {
                    if (peerConnection !== pc) return@post
                    remoteTrack = track
                    addFrameCounterIfMissing(track)
                    renderer?.let { r ->
                        try {
                            track.addSink(r)
                        } catch (_: Exception) {
                        }
                    }
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
                        PeerConnection.PeerConnectionState.CONNECTED ->
                            if (isHelper) {
                                _info.value = "Connection established — starting the video…"
                            } else {
                                _state.value = ShareState.LIVE
                                _info.value = "Live — the helper can see this screen."
                            }
                        PeerConnection.PeerConnectionState.DISCONNECTED ->
                            if (_state.value == ShareState.LIVE) {
                                _info.value = "Connection interrupted — trying to recover…"
                            }
                        PeerConnection.PeerConnectionState.FAILED -> fail(
                            if (isHelper) {
                                "Connection failed — the two phones could not link up. " +
                                    "Tap “Watch screen” again; if it keeps failing, put both " +
                                    "phones on the same Wi-Fi network (or one phone's hotspot)."
                            } else {
                                "Connection failed — the two phones could not link up. " +
                                    "Tap “Stop sharing”, then share again."
                            }
                        )
                        else -> Unit
                    }
                }
            }

            override fun onSignalingChange(state: PeerConnection.SignalingState?) = Unit
            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) = Unit
            override fun onIceConnectionReceivingChange(receiving: Boolean) = Unit
            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) = Unit
            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) = Unit
            override fun onAddStream(stream: MediaStream?) = Unit
            override fun onRemoveStream(stream: MediaStream?) = Unit
            override fun onDataChannel(channel: org.webrtc.DataChannel?) = Unit
            override fun onRenegotiationNeeded() = Unit
        }
        val config = PeerConnection.RTCConfiguration(ICE_SERVERS)
        config.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        val pc = factory!!.createPeerConnection(config, observer)
        if (pc == null) {
            fail("Could not create the WebRTC engine on this phone.")
            return
        }
        thisPc = pc
        peerConnection = pc
    }

    private fun watchSignals() {
        val sid = sessionId ?: return
        val me = myUid ?: return
        val stop = SignalingRepo.watch(
            sid,
            me,
            onSignal = { type, ep, payload -> main.post { handleSignal(type, ep, payload) } },
            onError = { e -> main.post { fail("Signal channel error: ${e.message ?: "unknown"}") } }
        )
        signalStop = stop
        // Clear stale documents addressed to me from earlier rounds.
        SignalingRepo.clearAsync(sid, me)
    }

    private fun handleSignal(type: String, ep: String, payload: String) {
        val pc = peerConnection ?: return
        try {
            val key = type + "|" + payload.take(120)
            // ICE candidates are all unique; control messages may repeat safely.
            if (type != "ice" && !handled.add(key)) return

            when (type) {
                "watch" -> if (!isHelper &&
                    (_state.value == ShareState.WAITING_PEER ||
                        _state.value == ShareState.CONNECTING ||
                        _state.value == ShareState.LIVE) &&
                    payload.length in 16..64
                ) {
                    // A fresh (possibly repeat) watch: adopt the helper's epoch,
                    // then offer the screen with an ICE restart so every round
                    // carries brand-new credentials and re-gathered candidates.
                    peerEpoch = payload
                    _info.value = "The helper asked to watch — sending the screen…"
                    val restart = MediaConstraints()
                    restart.mandatory.add(MediaConstraints.KeyValuePair("IceRestart", "true"))
                    pc.createOffer(offerObserver(peerEpoch), restart)
                }
                "offer" -> if (isHelper && !remoteDescriptionSet && ep == myEpoch) {
                    val sdp = JSONObject(payload)
                    pc.setRemoteDescription(
                        setObserver("offer", myEpoch),
                        SessionDescription(SessionDescription.Type.OFFER, sdp.getString("sdp"))
                    )
                }
                "answer" -> if (!isHelper && ep.isNotBlank() && ep == peerEpoch) {
                    val sdp = JSONObject(payload)
                    pc.setRemoteDescription(
                        setObserver("answer", peerEpoch),
                        SessionDescription(SessionDescription.Type.ANSWER, sdp.getString("sdp"))
                    )
                }
                "ice" -> {
                    val expected = if (isHelper) myEpoch else peerEpoch
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
            }
        } catch (_: Exception) {
            // one malformed signal must never kill the engine
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
            val sdp = preferVp8(desc.description)
            val out = SessionDescription(desc.type, sdp)
            pc.setLocalDescription(noop, out)
            val json = JSONObject()
                .put("type", desc.type.canonicalForm())
                .put("sdp", sdp)
                .toString()
            scope.launch {
                try {
                    SignalingRepo.send(sid, me, to, "offer", json, ep)
                    _state.value = ShareState.CONNECTING
                    _info.value = "Offer sent — waiting for the helper's answer…"
                } catch (e: Exception) {
                    fail("Could not send the offer: ${e.message ?: e.javaClass.simpleName}")
                }
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
                flushIce()
                val epExpected = expectedEpoch
                pc.createAnswer(object : SdpObserver by noop {
                    override fun onCreateSuccess(desc: SessionDescription?) {
                        val pc2 = peerConnection ?: return
                        if (desc == null) return
                        if (expectedEpoch == null || myEpoch != epExpected) return
                        val sdp = preferVp8(desc.description)
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
                                _state.value = ShareState.CONNECTING
                                _info.value = "Answer sent — connecting video…"
                            } catch (e: Exception) {
                                fail("Could not send the answer: ${e.message ?: e.javaClass.simpleName}")
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

    /** Reorder the video m-line payload types so VP8 (+rtx) is negotiated first. */
    private fun preferVp8(sdp: String): String {
        return try {
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
            val vp8 = pts.filter { codecOf[it] == "VP8" }
            if (vp8.isEmpty()) return sdp
            val vp8Set = vp8.toSet()
            val rtx = pts.filter { aptOf[it] in vp8Set }
            val front = (vp8 + rtx).distinct()
            val rest = pts.filter { it !in front }
            lines[mLineIndex] = (header + front + rest).joinToString(" ")
            lines.joinToString("\r\n")
        } catch (_: Exception) {
            sdp
        }
    }

    /** UI hands over the render surface (helper side). */
    fun attachRenderer(view: SurfaceViewRenderer?) {
        runOnMain {
            renderer = view
            val track = remoteTrack
            if (view != null && track != null) {
                try {
                    track.removeSink(view)
                } catch (_: Exception) {
                }
                try {
                    track.addSink(view)
                } catch (_: Exception) {
                }
            }
        }
    }

    /** Detach the render surface (called from composition dispose). */
    fun detachRenderer() {
        runOnMain {
            val view = renderer
            val track = remoteTrack
            if (view != null && track != null) {
                try {
                    track.removeSink(view)
                } catch (_: Exception) {
                }
            }
            renderer = null
        }
    }

    /** UI hands over the recipient's small self-preview surface. */
    fun attachLocalPreview(view: SurfaceViewRenderer?) {
        runOnMain {
            localPreview = view
            val track = videoTrack
            if (view != null && track != null) {
                try {
                    track.removeSink(view)
                } catch (_: Exception) {
                }
                try {
                    track.addSink(view)
                } catch (_: Exception) {
                }
            }
        }
    }

    /** Detach the recipient's self-preview surface. */
    fun detachLocalPreview() {
        runOnMain {
            val view = localPreview
            val track = videoTrack
            if (view != null && track != null) {
                try {
                    track.removeSink(view)
                } catch (_: Exception) {
                }
            }
            localPreview = null
        }
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
        val previewView = localPreview
        if (previewView != null) {
            try {
                videoTrack?.removeSink(previewView)
            } catch (_: Exception) {
            }
        }
        localPreview = null
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
        renderer?.let { view -> remoteTrack?.let { track -> track.removeSink(view) } }
        remoteTrack = null
        try {
            peerConnection?.close()
        } catch (_: Exception) {
        }
        peerConnection = null
        renderer = null
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
        noVideoWarned = false
        _state.value = ShareState.IDLE
    }

    private fun fail(message: String) {
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
