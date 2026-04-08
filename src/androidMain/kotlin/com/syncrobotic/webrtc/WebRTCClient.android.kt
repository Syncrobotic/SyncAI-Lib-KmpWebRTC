package com.syncrobotic.webrtc

import android.content.Context
import android.media.AudioManager
import com.syncrobotic.webrtc.config.WebRTCConfig
import kotlinx.coroutines.suspendCancellableCoroutine
import org.webrtc.*
import org.webrtc.DataChannel
import org.webrtc.audio.JavaAudioDeviceModule
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Android implementation of WebRTCClient using Google's WebRTC library.
 */
actual class WebRTCClient {
    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var listener: WebRTCListener? = null
    private var eglBase: EglBase? = null
    private var surfaceViewRenderer: SurfaceViewRenderer? = null
    
    private var audioSource: AudioSource? = null
    private var localAudioTrack: AudioTrack? = null
    private var _isAudioEnabled = true

    private var videoCapturer: org.webrtc.CameraVideoCapturer? = null
    private var localVideoSource: org.webrtc.VideoSource? = null
    private var localVideoTrack: org.webrtc.VideoTrack? = null
    private var surfaceTextureHelper: org.webrtc.SurfaceTextureHelper? = null
    private var _isVideoEnabled = true
    
    private var audioManager: AudioManager? = null
    private var _isSpeakerphoneEnabled = true
    private var savedAudioMode: Int = AudioManager.MODE_NORMAL
    private var savedIsSpeakerphoneOn: Boolean = false

    private val gatheredCandidates = mutableListOf<IceCandidate>()
    @Volatile
    private var iceGatheringComplete = false

    private var _connectionState = WebRTCState.NEW
    actual val connectionState: WebRTCState
        get() = _connectionState

    actual val isConnected: Boolean
        get() = _connectionState == WebRTCState.CONNECTED
    
    actual val isAudioEnabled: Boolean
        get() = _isAudioEnabled

    // FPS tracking for video frames
    private var frameCount = 0
    private var lastFpsUpdateTime = System.currentTimeMillis()
    private var currentFps: Double = 0.0
    
    /**
     * Get current video frame rate.
     */
    fun getCurrentFps(): Float = currentFps.toFloat()
    
    /**
     * Increment frame count for FPS calculation.
     * Call this from video renderer when a frame is rendered.
     */
    fun incrementFrameCount() {
        frameCount++
        updateFps()
    }
    
    /**
     * Update FPS calculation.
     */
    private fun updateFps() {
        val now = System.currentTimeMillis()
        val elapsed = now - lastFpsUpdateTime
        if (elapsed >= 1000) {
            currentFps = frameCount * 1000.0 / elapsed
            frameCount = 0
            lastFpsUpdateTime = now
        }
    }
    
    /**
     * Report a video frame was received (for FPS tracking).
     */
    fun reportVideoFrame() {
        incrementFrameCount()
    }

    actual fun initialize(config: WebRTCConfig, listener: WebRTCListener) {
        this.listener = listener
    }

    /**
     * Initialize with Android context (required for WebRTC on Android).
     */
    fun initializeWithContext(
        context: Context,
        config: WebRTCConfig,
        listener: WebRTCListener
    ) {
        this.listener = listener

        synchronized(gatheredCandidates) {
            gatheredCandidates.clear()
        }
        iceGatheringComplete = false

        eglBase = EglBase.create()

        // Create factory (each connection has its own factory to avoid EglContext conflicts)
        PeerConnectionFactoryManager.ensureInitialized(context)
        peerConnectionFactory = PeerConnectionFactoryManager.createForVideo(eglBase!!)

        val iceServers = config.iceServers.map { ice ->
            PeerConnection.IceServer.builder(ice.urls)
                .apply {
                    ice.username?.let { setUsername(it) }
                    ice.credential?.let { setPassword(it) }
                }
                .createIceServer()
        }

        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            bundlePolicy = when (config.bundlePolicy) {
                "max-bundle" -> PeerConnection.BundlePolicy.MAXBUNDLE
                "max-compat" -> PeerConnection.BundlePolicy.MAXCOMPAT
                else -> PeerConnection.BundlePolicy.BALANCED
            }
            rtcpMuxPolicy = when (config.rtcpMuxPolicy) {
                "require" -> PeerConnection.RtcpMuxPolicy.REQUIRE
                else -> PeerConnection.RtcpMuxPolicy.NEGOTIATE
            }
            iceTransportsType = when (config.iceTransportPolicy) {
                "relay" -> PeerConnection.IceTransportsType.RELAY
                else -> PeerConnection.IceTransportsType.ALL
            }
        }

        peerConnection = peerConnectionFactory?.createPeerConnection(
            rtcConfig,
            createPeerConnectionObserver()
        )

        // Transceivers are set up by createFlexibleOffer() — do not add them here
        // to avoid duplicate m=video/m=audio sections in the SDP offer.

        configureSpeakerphone(context, true)
    }
    
    /**
     * Initialize for sending audio (WHIP mode).
     */
    fun initializeForSending(
        context: Context,
        config: WebRTCConfig,
        listener: WebRTCListener
    ) {
        this.listener = listener

        synchronized(gatheredCandidates) {
            gatheredCandidates.clear()
        }
        iceGatheringComplete = false

        eglBase = EglBase.create()

        // Create factory with video + audio support so SEND_VIDEO can encode camera frames
        PeerConnectionFactoryManager.ensureInitialized(context)
        peerConnectionFactory = PeerConnectionFactoryManager.createForVideoAndAudio(context, eglBase!!)

        val iceServers = config.iceServers.map { ice ->
            PeerConnection.IceServer.builder(ice.urls)
                .apply {
                    ice.username?.let { setUsername(it) }
                    ice.credential?.let { setPassword(it) }
                }
                .createIceServer()
        }

        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            bundlePolicy = when (config.bundlePolicy) {
                "max-bundle" -> PeerConnection.BundlePolicy.MAXBUNDLE
                "max-compat" -> PeerConnection.BundlePolicy.MAXCOMPAT
                else -> PeerConnection.BundlePolicy.BALANCED
            }
            rtcpMuxPolicy = when (config.rtcpMuxPolicy) {
                "require" -> PeerConnection.RtcpMuxPolicy.REQUIRE
                else -> PeerConnection.RtcpMuxPolicy.NEGOTIATE
            }
            iceTransportsType = when (config.iceTransportPolicy) {
                "relay" -> PeerConnection.IceTransportsType.RELAY
                else -> PeerConnection.IceTransportsType.ALL
            }
        }

        peerConnection = peerConnectionFactory?.createPeerConnection(
            rtcConfig,
            createPeerConnectionObserver()
        )

        val audioConstraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googAutoGainControl", "true"))
        }
        audioSource = peerConnectionFactory?.createAudioSource(audioConstraints)
        localAudioTrack = peerConnectionFactory?.createAudioTrack("audio0", audioSource)
        localAudioTrack?.setEnabled(true)
        _isAudioEnabled = true

        val streamId = "local-audio-stream"
        localAudioTrack?.let { track ->
            peerConnection?.addTrack(track, listOf(streamId))
        }
        
        configureSpeakerphone(context, true)
    }
    
    /**
     * Initialize camera capture for sending video.
     * Call after initializeForSending() or initializeWithContext().
     */
    fun initializeCameraCapture(context: android.content.Context, config: com.syncrobotic.webrtc.config.VideoCaptureConfig) {
        val factory = peerConnectionFactory ?: return

        // Create camera capturer
        val camera2Enumerator = org.webrtc.Camera2Enumerator(context)
        val deviceNames = camera2Enumerator.deviceNames

        // Select front or rear camera
        val targetDeviceName = if (config.useFrontCamera) {
            deviceNames.firstOrNull { camera2Enumerator.isFrontFacing(it) }
        } else {
            deviceNames.firstOrNull { camera2Enumerator.isBackFacing(it) }
        } ?: deviceNames.firstOrNull()

        if (targetDeviceName == null) {
            android.util.Log.e("WebRTCClient", "No camera device found")
            return
        }

        videoCapturer = camera2Enumerator.createCapturer(targetDeviceName, null)

        // Create surface texture helper for video processing
        surfaceTextureHelper = org.webrtc.SurfaceTextureHelper.create(
            "CaptureThread",
            eglBase?.eglBaseContext
        )

        // Create video source
        localVideoSource = factory.createVideoSource(videoCapturer!!.isScreencast)
        videoCapturer?.initialize(surfaceTextureHelper, context, localVideoSource?.capturerObserver)

        // Start capture
        videoCapturer?.startCapture(config.width, config.height, config.fps)

        // Create video track
        localVideoTrack = factory.createVideoTrack("local-video", localVideoSource)
        localVideoTrack?.setEnabled(true)
        _isVideoEnabled = true

        // Add track to peer connection
        localVideoTrack?.let { track ->
            peerConnection?.addTrack(track, listOf("local-video-stream"))
        }

        android.util.Log.d("WebRTCClient", "Camera capture initialized: ${config.width}x${config.height}@${config.fps}fps, device=$targetDeviceName")
    }

    /**
     * Switch between front and rear camera.
     */
    fun switchCamera() {
        (videoCapturer as? org.webrtc.CameraVideoCapturer)?.switchCamera(null)
    }

    /**
     * Get the local video track (for camera preview rendering).
     */
    fun getLocalVideoTrack(): org.webrtc.VideoTrack? = localVideoTrack

    /**
     * Enable or disable the local video track.
     */
    fun setVideoEnabled(enabled: Boolean) {
        localVideoTrack?.setEnabled(enabled)
        _isVideoEnabled = enabled
    }

    /**
     * Enable or disable received (remote) video tracks.
     */
    fun setRemoteVideoEnabled(enabled: Boolean) {
        peerConnection?.transceivers?.forEach { transceiver ->
            if (transceiver.mediaType == org.webrtc.MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO &&
                transceiver.direction == org.webrtc.RtpTransceiver.RtpTransceiverDirection.RECV_ONLY ||
                transceiver.direction == org.webrtc.RtpTransceiver.RtpTransceiverDirection.SEND_RECV
            ) {
                (transceiver.receiver?.track() as? org.webrtc.VideoTrack)?.setEnabled(enabled)
            }
        }
    }

    private fun configureSpeakerphone(context: Context, enabled: Boolean) {
        try {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audioManager = am
            
            savedAudioMode = am.mode
            savedIsSpeakerphoneOn = am.isSpeakerphoneOn
            
            am.mode = AudioManager.MODE_IN_COMMUNICATION
            am.isSpeakerphoneOn = enabled
            _isSpeakerphoneEnabled = enabled
        } catch (e: Exception) {
            android.util.Log.e("WebRTCClient", "Failed to configure speakerphone: $e")
        }
    }

    /**
     * Create a SurfaceViewRenderer for video display.
     */
    fun createSurfaceViewRenderer(context: Context): SurfaceViewRenderer {
        val renderer = SurfaceViewRenderer(context).apply {
            init(eglBase?.eglBaseContext, null)
            setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT)
            setEnableHardwareScaler(true)
        }
        surfaceViewRenderer = renderer
        return renderer
    }

    private inner class FrameCapturingSink : VideoSink {
        override fun onFrame(frame: org.webrtc.VideoFrame) {
            listener?.onVideoFrame(VideoFrame(
                width = frame.rotatedWidth,
                height = frame.rotatedHeight,
                timestampNs = frame.timestampNs,
                nativeFrame = frame
            ))
            // Always use the current surfaceViewRenderer reference — handles late init
            surfaceViewRenderer?.onFrame(frame)
        }
    }

    private var frameCapturingSink: FrameCapturingSink? = null

    private fun createPeerConnectionObserver(): PeerConnection.Observer {
        return object : PeerConnection.Observer {
            override fun onSignalingChange(state: PeerConnection.SignalingState) {}

            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) {
                val mappedState = when (state) {
                    PeerConnection.IceConnectionState.NEW -> IceConnectionState.NEW
                    PeerConnection.IceConnectionState.CHECKING -> IceConnectionState.CHECKING
                    PeerConnection.IceConnectionState.CONNECTED -> IceConnectionState.CONNECTED
                    PeerConnection.IceConnectionState.COMPLETED -> IceConnectionState.COMPLETED
                    PeerConnection.IceConnectionState.FAILED -> IceConnectionState.FAILED
                    PeerConnection.IceConnectionState.DISCONNECTED -> IceConnectionState.DISCONNECTED
                    PeerConnection.IceConnectionState.CLOSED -> IceConnectionState.CLOSED
                }
                listener?.onIceConnectionStateChanged(mappedState)
            }

            override fun onConnectionChange(state: PeerConnection.PeerConnectionState) {
                _connectionState = when (state) {
                    PeerConnection.PeerConnectionState.NEW -> WebRTCState.NEW
                    PeerConnection.PeerConnectionState.CONNECTING -> WebRTCState.CONNECTING
                    PeerConnection.PeerConnectionState.CONNECTED -> WebRTCState.CONNECTED
                    PeerConnection.PeerConnectionState.DISCONNECTED -> WebRTCState.DISCONNECTED
                    PeerConnection.PeerConnectionState.FAILED -> WebRTCState.FAILED
                    PeerConnection.PeerConnectionState.CLOSED -> WebRTCState.CLOSED
                }
                listener?.onConnectionStateChanged(_connectionState)
            }

            override fun onIceConnectionReceivingChange(receiving: Boolean) {}

            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState) {
                if (state == PeerConnection.IceGatheringState.COMPLETE) {
                    iceGatheringComplete = true
                }
                val mappedState = when (state) {
                    PeerConnection.IceGatheringState.NEW -> IceGatheringState.NEW
                    PeerConnection.IceGatheringState.GATHERING -> IceGatheringState.GATHERING
                    PeerConnection.IceGatheringState.COMPLETE -> IceGatheringState.COMPLETE
                }
                listener?.onIceGatheringStateChanged(mappedState)
                if (state == PeerConnection.IceGatheringState.COMPLETE) {
                    listener?.onIceGatheringComplete()
                }
            }

            override fun onIceCandidate(candidate: IceCandidate) {
                synchronized(gatheredCandidates) {
                    gatheredCandidates.add(candidate)
                }
                listener?.onIceCandidate(
                    candidate.sdp,
                    candidate.sdpMid,
                    candidate.sdpMLineIndex
                )
            }

            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>) {}

            override fun onAddStream(stream: MediaStream) {
                val videoTrackCount = stream.videoTracks.size
                val audioTrackCount = stream.audioTracks.size
                
                listener?.onRemoteStreamAdded()
                
                val tracks = mutableListOf<TrackInfo>()
                stream.videoTracks.forEach { track ->
                    tracks.add(TrackInfo(
                        trackId = track.id() ?: "",
                        kind = TrackKind.VIDEO,
                        enabled = track.enabled(),
                        label = track.id()
                    ))
                }
                stream.audioTracks.forEach { track ->
                    tracks.add(TrackInfo(
                        trackId = track.id() ?: "",
                        kind = TrackKind.AUDIO,
                        enabled = track.enabled(),
                        label = track.id()
                    ))
                }
                listener?.onTracksChanged(videoTrackCount, audioTrackCount, tracks)
                
                if (stream.videoTracks.isNotEmpty()) {
                    frameCapturingSink = FrameCapturingSink()
                    stream.videoTracks[0].addSink(frameCapturingSink)
                }
            }

            override fun onRemoveStream(stream: MediaStream) {
                listener?.onRemoteStreamRemoved()
            }

            override fun onDataChannel(channel: DataChannel) {}

            override fun onRenegotiationNeeded() {}

            override fun onAddTrack(receiver: RtpReceiver, streams: Array<out MediaStream>) {
                if (receiver.track()?.kind() == MediaStreamTrack.VIDEO_TRACK_KIND) {
                    frameCapturingSink = FrameCapturingSink()
                    (receiver.track() as? VideoTrack)?.addSink(frameCapturingSink)
                }
            }

            override fun onTrack(transceiver: RtpTransceiver) {
                val track = transceiver.receiver.track()
                if (track?.kind() == MediaStreamTrack.VIDEO_TRACK_KIND) {
                    frameCapturingSink = FrameCapturingSink()
                    (track as? VideoTrack)?.addSink(frameCapturingSink)
                }
            }
        }
    }

    actual suspend fun createOffer(
        receiveVideo: Boolean,
        receiveAudio: Boolean
    ): String = suspendCancellableCoroutine { cont ->
        synchronized(gatheredCandidates) {
            gatheredCandidates.clear()
        }
        iceGatheringComplete = false

        val constraints = MediaConstraints()

        peerConnection?.createOffer(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription) {
                peerConnection?.setLocalDescription(object : SdpObserver {
                    override fun onCreateSuccess(sdp: SessionDescription?) {}
                    override fun onSetSuccess() {
                        cont.resume(sdp.description)
                    }
                    override fun onCreateFailure(error: String?) {
                        cont.resumeWithException(Exception("Failed to set local description: $error"))
                    }
                    override fun onSetFailure(error: String?) {
                        cont.resumeWithException(Exception("Failed to set local description: $error"))
                    }
                }, sdp)
            }
            override fun onSetSuccess() {}
            override fun onCreateFailure(error: String?) {
                cont.resumeWithException(Exception("Failed to create offer: $error"))
            }
            override fun onSetFailure(error: String?) {}
        }, constraints)
    }
    
    actual suspend fun createSendOffer(
        sendVideo: Boolean,
        sendAudio: Boolean
    ): String = suspendCancellableCoroutine { cont ->
        synchronized(gatheredCandidates) {
            gatheredCandidates.clear()
        }
        iceGatheringComplete = false

        val constraints = MediaConstraints()

        peerConnection?.createOffer(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription) {
                peerConnection?.setLocalDescription(object : SdpObserver {
                    override fun onCreateSuccess(sdp: SessionDescription?) {}
                    override fun onSetSuccess() {
                        cont.resume(sdp.description)
                    }
                    override fun onCreateFailure(error: String?) {
                        cont.resumeWithException(Exception("Failed to set local description: $error"))
                    }
                    override fun onSetFailure(error: String?) {
                        cont.resumeWithException(Exception("Failed to set local description: $error"))
                    }
                }, sdp)
            }
            override fun onSetSuccess() {}
            override fun onCreateFailure(error: String?) {
                cont.resumeWithException(Exception("Failed to create send offer: $error"))
            }
            override fun onSetFailure(error: String?) {}
        }, constraints)
    }

    actual suspend fun createFlexibleOffer(
        mediaConfig: com.syncrobotic.webrtc.config.MediaConfig
    ): String = suspendCancellableCoroutine { cont ->
        val pc = peerConnection ?: run {
            cont.resumeWithException(Exception("PeerConnection not initialized"))
            return@suspendCancellableCoroutine
        }

        try {
            // Video transceiver
            mediaConfig.videoDirection?.let { dir ->
                val nativeDir = when (dir) {
                    com.syncrobotic.webrtc.config.TransceiverDirection.SEND_ONLY -> RtpTransceiver.RtpTransceiverDirection.SEND_ONLY
                    com.syncrobotic.webrtc.config.TransceiverDirection.RECV_ONLY -> RtpTransceiver.RtpTransceiverDirection.RECV_ONLY
                    com.syncrobotic.webrtc.config.TransceiverDirection.SEND_RECV -> RtpTransceiver.RtpTransceiverDirection.SEND_RECV
                }
                if (dir.isSending && localVideoTrack != null) {
                    // Use the real local video track for sending
                    pc.addTransceiver(
                        localVideoTrack,
                        RtpTransceiver.RtpTransceiverInit(nativeDir)
                    )
                } else {
                    pc.addTransceiver(
                        MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO,
                        RtpTransceiver.RtpTransceiverInit(nativeDir)
                    )
                }
            }

            // Audio transceiver
            mediaConfig.audioDirection?.let { dir ->
                val nativeDir = when (dir) {
                    com.syncrobotic.webrtc.config.TransceiverDirection.SEND_ONLY -> RtpTransceiver.RtpTransceiverDirection.SEND_ONLY
                    com.syncrobotic.webrtc.config.TransceiverDirection.RECV_ONLY -> RtpTransceiver.RtpTransceiverDirection.RECV_ONLY
                    com.syncrobotic.webrtc.config.TransceiverDirection.SEND_RECV -> RtpTransceiver.RtpTransceiverDirection.SEND_RECV
                }
                if (dir.isSending && localAudioTrack != null) {
                    // Use the real local audio track for sending
                    pc.addTransceiver(
                        localAudioTrack,
                        RtpTransceiver.RtpTransceiverInit(nativeDir)
                    )
                } else {
                    pc.addTransceiver(
                        MediaStreamTrack.MediaType.MEDIA_TYPE_AUDIO,
                        RtpTransceiver.RtpTransceiverInit(nativeDir)
                    )
                }
            }
        } catch (e: Exception) {
            cont.resumeWithException(Exception("Failed to setup transceivers: ${e.message}"))
            return@suspendCancellableCoroutine
        }

        val constraints = MediaConstraints()
        pc.createOffer(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription) {
                pc.setLocalDescription(object : SdpObserver {
                    override fun onCreateSuccess(sdp: SessionDescription?) {}
                    override fun onSetSuccess() {
                        cont.resume(sdp.description)
                    }
                    override fun onCreateFailure(error: String?) {
                        cont.resumeWithException(Exception("Failed to set local description: $error"))
                    }
                    override fun onSetFailure(error: String?) {
                        cont.resumeWithException(Exception("Failed to set local description: $error"))
                    }
                }, sdp)
            }
            override fun onSetSuccess() {}
            override fun onCreateFailure(error: String?) {
                cont.resumeWithException(Exception("Failed to create offer: $error"))
            }
            override fun onSetFailure(error: String?) {}
        }, constraints)
    }

    actual fun setAudioEnabled(enabled: Boolean) {
        localAudioTrack?.setEnabled(enabled)
        _isAudioEnabled = enabled
    }
    
    actual fun setSpeakerphoneEnabled(enabled: Boolean) {
        try {
            audioManager?.let { am ->
                am.isSpeakerphoneOn = enabled
                _isSpeakerphoneEnabled = enabled
            }
        } catch (e: Exception) {
            android.util.Log.e("WebRTCClient", "Failed to set speakerphone: $e")
        }
    }
    
    actual fun isSpeakerphoneEnabled(): Boolean = _isSpeakerphoneEnabled

    actual suspend fun setRemoteAnswer(sdpAnswer: String) = suspendCancellableCoroutine { cont ->
        val sessionDescription = SessionDescription(SessionDescription.Type.ANSWER, sdpAnswer)
        peerConnection?.setRemoteDescription(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription?) {}
            override fun onSetSuccess() {
                cont.resume(Unit)
            }
            override fun onCreateFailure(error: String?) {}
            override fun onSetFailure(error: String?) {
                cont.resumeWithException(Exception("Failed to set remote answer: $error"))
            }
        }, sessionDescription)
    }

    actual suspend fun addIceCandidate(
        candidate: String,
        sdpMid: String?,
        sdpMLineIndex: Int
    ) {
        val iceCandidate = IceCandidate(sdpMid, sdpMLineIndex, candidate)
        peerConnection?.addIceCandidate(iceCandidate)
    }

    actual fun getLocalDescription(): String? {
        val baseSdp = peerConnection?.localDescription?.description ?: return null

        synchronized(gatheredCandidates) {
            if (gatheredCandidates.isEmpty()) return baseSdp
            return buildSdpWithCandidates(baseSdp, gatheredCandidates.toList(), iceGatheringComplete)
        }
    }

    actual fun getVideoSink(): Any? = surfaceViewRenderer

    actual suspend fun getStats(): WebRTCStats? = suspendCancellableCoroutine { cont ->
        val pc = peerConnection
        if (pc == null) {
            cont.resume(null)
            return@suspendCancellableCoroutine
        }

        pc.getStats { report ->
            var audioBitrate: Long = 0
            var roundTripTime: Double = 0.0
            var jitter: Double = 0.0
            var packetsSent: Long = 0
            var packetsLost: Long = 0
            var codec = "unknown"
            val timestampMs = System.currentTimeMillis()
            var bytesSent: Long = 0

            for (stats in report.statsMap.values) {
                when (stats.type) {
                    "outbound-rtp" -> {
                        val kind = stats.members["kind"] as? String
                        if (kind == "audio") {
                            (stats.members["bytesSent"] as? Number)?.let {
                                bytesSent = it.toLong()
                            }
                            (stats.members["packetsSent"] as? Number)?.let {
                                packetsSent = it.toLong()
                            }
                            audioBitrate = bytesSent * 8
                        }
                    }
                    "remote-inbound-rtp" -> {
                        val kind = stats.members["kind"] as? String
                        if (kind == "audio") {
                            (stats.members["roundTripTime"] as? Number)?.let {
                                roundTripTime = it.toDouble() * 1000
                            }
                            (stats.members["jitter"] as? Number)?.let {
                                jitter = it.toDouble() * 1000
                            }
                            (stats.members["packetsLost"] as? Number)?.let {
                                packetsLost = it.toLong()
                            }
                        }
                    }
                    "codec" -> {
                        val mimeType = stats.members["mimeType"] as? String
                        if (mimeType?.contains("audio") == true) {
                            codec = mimeType.substringAfter("audio/").lowercase()
                        }
                    }
                }
            }

            cont.resume(
                WebRTCStats(
                    audioBitrate = audioBitrate,
                    roundTripTimeMs = roundTripTime,
                    jitterMs = jitter,
                    packetsSent = packetsSent,
                    packetsLost = packetsLost,
                    codec = codec,
                    timestampMs = timestampMs
                )
            )
        }
    }

    actual fun createDataChannel(config: com.syncrobotic.webrtc.datachannel.DataChannelConfig): com.syncrobotic.webrtc.datachannel.DataChannel? {
        val pc = peerConnection ?: return null
        
        val init = DataChannel.Init().apply {
            ordered = config.ordered
            config.maxRetransmits?.let { maxRetransmits = it }
            config.maxPacketLifeTimeMs?.let { maxRetransmitTimeMs = it }
            protocol = config.protocol
            negotiated = config.negotiated
            config.id?.let { id = it }
        }
        
        return try {
            val nativeChannel = pc.createDataChannel(config.label, init)
            if (nativeChannel != null) {
                com.syncrobotic.webrtc.datachannel.DataChannel.create(nativeChannel)
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    actual fun close() {
        listener = null

        // Video capture cleanup
        try {
            videoCapturer?.stopCapture()
        } catch (_: Exception) {}
        videoCapturer?.dispose()
        videoCapturer = null
        localVideoSource?.dispose()
        localVideoSource = null
        localVideoTrack?.dispose()
        localVideoTrack = null
        surfaceTextureHelper?.dispose()
        surfaceTextureHelper = null

        localAudioTrack?.setEnabled(false)
        localAudioTrack?.dispose()
        localAudioTrack = null
        audioSource?.dispose()
        audioSource = null

        frameCapturingSink = null
        surfaceViewRenderer?.release()
        surfaceViewRenderer = null
        peerConnection?.close()
        peerConnection = null
        
        // Dispose this connection's factory
        if (peerConnectionFactory != null) {
            PeerConnectionFactoryManager.disposeFactory(peerConnectionFactory)
            peerConnectionFactory = null
        }
        
        eglBase?.release()
        eglBase = null

        synchronized(gatheredCandidates) {
            gatheredCandidates.clear()
        }
        iceGatheringComplete = false
        _connectionState = WebRTCState.CLOSED
        _isAudioEnabled = true
        _isVideoEnabled = true
        
        try {
            audioManager?.let { am ->
                am.isSpeakerphoneOn = savedIsSpeakerphoneOn
                am.mode = savedAudioMode
            }
        } catch (e: Exception) {
            android.util.Log.e("WebRTCClient", "Failed to restore AudioManager: $e")
        }
        audioManager = null
        _isSpeakerphoneEnabled = true
    }

    actual companion object {
        actual fun isSupported(): Boolean = true
    }
}

private fun buildSdpWithCandidates(
    baseSdp: String,
    candidates: List<IceCandidate>,
    gatheringComplete: Boolean
): String {
    val candidatesByMLine = candidates.groupBy { it.sdpMLineIndex }
    val lines = baseSdp.split("\r\n").filter { it.isNotEmpty() }
    val mLinePositions = lines.indices.filter { lines[it].startsWith("m=") }
    val result = mutableListOf<String>()

    for (i in lines.indices) {
        result.add(lines[i])

        val isLastLineOfSection = mLinePositions.any { mPos ->
            val mIndex = mLinePositions.indexOf(mPos)
            val sectionStart = mPos
            val sectionEnd = if (mIndex + 1 < mLinePositions.size) mLinePositions[mIndex + 1] else lines.size
            i == sectionEnd - 1 && i >= sectionStart
        }

        if (isLastLineOfSection) {
            val mIndex = mLinePositions.indexOfLast { it <= i }
            if (mIndex >= 0) {
                candidatesByMLine[mIndex]?.forEach { candidate ->
                    result.add("a=${candidate.sdp}")
                }
                if (gatheringComplete) {
                    result.add("a=end-of-candidates")
                }
            }
        }
    }

    return result.joinToString("\r\n") + "\r\n"
}
