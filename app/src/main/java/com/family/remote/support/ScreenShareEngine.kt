package com.family.remote.support

import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjection
import android.os.Handler
import android.os.Looper
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
import org.webrtc.VideoSource
import org.webrtc.VideoTrack

/** Live status of the screen-share engine, observed by the UI. */
enum class ShareState { IDLE, PREPARING, WAITING_PEER, CONNECTING, LIVE, ENDED }

/**
 * Phase 7 — WebRTC screen share engine (no server; Firestore signaling).
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
 * Both sides trickle ICE through SignalingRepo; candidates that arrive before
 * the remote description is applied are buffered and flushed afterwards.
 *
 * Threading: every WebRTC object is created and driven from the main thread
 * (fine for one ~720p screen stream); observer callbacks hop back onto the
 * main handler before touching engine state.
 */
object ScreenShareEngine {

    private const val STUN = "stun:stun.l.google.com:19302"

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

    private var peerConnection: PeerConnection? = null
    private var videoSource: VideoSource? = null
    private var videoTrack: VideoTrack? = null
    private var capturer: ScreenCapturerAndroid? = null
    private var surfaceHelper: SurfaceTextureHelper? = null
    private var remoteTrack: VideoTrack? = null

    private var renderer: SurfaceViewRenderer? = null
    private val pendingIce = mutableListOf<IceCandidate>()
    private var remoteDescriptionSet = false
    /** De-dupes control messages (offer/answer/watch applied once per round). */
    private val handled = mutableSetOf<String>()

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
                _state.value = ShareState.WAITING_PEER
                _info.value = "Screen capture ready — tap “Watch screen” on the helper phone."
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
                scope.launch {
                    try {
                        SignalingRepo.send(sid, me, to, "watch", "")
                        _info.value = "Asked the recipient's phone for the screen — connecting…"
                    } catch (e: Exception) {
                        fail("Could not ask to watch: ${e.message ?: e.javaClass.simpleName}")
                    }
                }
            } catch (e: Exception) {
                fail("Could not start the viewer: ${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    /** Helper stops watching (posts "bye" so the recipient stops capturing). */
    fun stopHelper() {
        runOnMain { teardown(sendBye = true) }
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
        handled.clear()
        pendingIce.clear()
        remoteDescriptionSet = false
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
        val ice = listOf(PeerConnection.IceServer.builder(STUN).createIceServer())
        val config = PeerConnection.RTCConfiguration(ice)
        config.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        peerConnection = factory!!.createPeerConnection(config, object : PeerConnection.Observer {
            override fun onIceCandidate(candidate: IceCandidate) {
                val sid = sessionId ?: return
                val from = myUid ?: return
                val to = peerUid ?: return
                val json = JSONObject()
                    .put("mid", candidate.sdpMid ?: "0")
                    .put("index", candidate.sdpMLineIndex)
                    .put("sdp", candidate.sdp)
                    .toString()
                scope.launch {
                    try {
                        SignalingRepo.send(sid, from, to, "ice", json)
                    } catch (_: Exception) {
                        // one lost candidate is non-fatal
                    }
                }
            }

            override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {
                val track = receiver?.track() as? VideoTrack ?: return
                main.post {
                    remoteTrack = track
                    renderer?.let { track.addSink(it) }
                    if (isHelper) {
                        _state.value = ShareState.LIVE
                        _info.value = "Live — you can see the recipient's screen."
                    }
                }
            }

            override fun onConnectionChange(newState: PeerConnection.PeerConnectionState) {
                main.post {
                    when (newState) {
                        PeerConnection.PeerConnectionState.CONNECTED ->
                            if (!isHelper) {
                                _state.value = ShareState.LIVE
                                _info.value = "Live — the helper can see this screen."
                            }
                        PeerConnection.PeerConnectionState.FAILED ->
                            fail("Connection failed — both phones need a working internet connection.")
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
        })
    }

    private fun watchSignals() {
        val sid = sessionId ?: return
        val me = myUid ?: return
        val stop = SignalingRepo.watch(
            sid,
            me,
            onSignal = { type, payload -> main.post { handleSignal(type, payload) } },
            onError = { e -> main.post { fail("Signal channel error: ${e.message ?: "unknown"}") } }
        )
        signalStop = stop
        // Clear stale documents addressed to me from earlier rounds.
        SignalingRepo.clearAsync(sid, me)
    }

    private fun handleSignal(type: String, payload: String) {
        val pc = peerConnection ?: return
        val key = type + "|" + payload.take(120)
        // ICE candidates are all unique; control messages may repeat safely.
        if (type != "ice" && !handled.add(key)) return

        when (type) {
            "watch" -> if (!isHelper &&
                (_state.value == ShareState.WAITING_PEER || _state.value == ShareState.LIVE)
            ) {
                // (Re-)offer the screen to the asking helper.
                pc.createOffer(offerObserver(), MediaConstraints())
            }
            "offer" -> if (isHelper && !remoteDescriptionSet) {
                val sdp = JSONObject(payload)
                pc.setRemoteDescription(
                    setObserver("offer"),
                    SessionDescription(SessionDescription.Type.OFFER, sdp.getString("sdp"))
                )
            }
            "answer" -> if (!isHelper && !remoteDescriptionSet) {
                val sdp = JSONObject(payload)
                pc.setRemoteDescription(
                    setObserver("answer"),
                    SessionDescription(SessionDescription.Type.ANSWER, sdp.getString("sdp"))
                )
            }
            "ice" -> {
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
    }

    private fun offerObserver(): SdpObserver = object : SdpObserver by noop {
        override fun onCreateSuccess(desc: SessionDescription?) {
            val pc = peerConnection ?: return
            val sid = sessionId ?: return
            val me = myUid ?: return
            val to = peerUid ?: return
            if (desc == null) return
            pc.setLocalDescription(noop, desc)
            val json = JSONObject()
                .put("type", desc.type.canonicalForm())
                .put("sdp", desc.description)
                .toString()
            scope.launch {
                try {
                    SignalingRepo.send(sid, me, to, "offer", json)
                    _state.value = ShareState.CONNECTING
                    _info.value = "Offer sent — negotiating…"
                } catch (e: Exception) {
                    fail("Could not send the offer: ${e.message ?: e.javaClass.simpleName}")
                }
            }
        }
    }

    private fun setObserver(what: String): SdpObserver = object : SdpObserver by noop {
        override fun onSetSuccess() {
            val pc = peerConnection ?: return
            val sid = sessionId ?: return
            val me = myUid ?: return
            val to = peerUid ?: return
            if (isHelper && what == "offer") {
                remoteDescriptionSet = true
                flushIce()
                pc.createAnswer(object : SdpObserver by noop {
                    override fun onCreateSuccess(desc: SessionDescription?) {
                        val pc2 = peerConnection ?: return
                        if (desc == null) return
                        pc2.setLocalDescription(noop, desc)
                        val json = JSONObject()
                            .put("type", desc.type.canonicalForm())
                            .put("sdp", desc.description)
                            .toString()
                        scope.launch {
                            try {
                                SignalingRepo.send(sid, me, to, "answer", json)
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
                _info.value = "Connected — streaming screen…"
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

    /** UI hands over the render surface (helper side). */
    fun attachRenderer(view: SurfaceViewRenderer?) {
        runOnMain {
            renderer = view
            val track = remoteTrack
            if (view != null && track != null) track.addSink(view)
        }
    }

    /** Detach the render surface (called from composition dispose). */
    fun detachRenderer() {
        runOnMain {
            val view = renderer
            val track = remoteTrack
            if (view != null && track != null) track.removeSink(view)
            renderer = null
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
        pendingIce.clear()
        handled.clear()
        remoteDescriptionSet = false
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
