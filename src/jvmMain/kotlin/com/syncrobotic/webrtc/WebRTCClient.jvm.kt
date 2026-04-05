package com.syncrobotic.webrtc

import com.syncrobotic.webrtc.config.WebRTCConfig
import dev.onvoid.webrtc.*
import dev.onvoid.webrtc.media.*
import dev.onvoid.webrtc.media.video.*
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * JVM/Desktop implementation of WebRTCClient using webrtc-java library.
 */
actual class WebRTCClient {
    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var peerConnection: RTCPeerConnection? = null
    private var listener: WebRTCListener? = null
    private val videoSink = AtomicReference<VideoTrackSink?>(null)
    
    private var dummyVideoSource: CustomVideoSource? = null
    private var dummyVideoTrack: VideoTrack? = null
    private var dummyAudioSource: dev.onvoid.webrtc.media.audio.AudioTrackSource? = null
    private var dummyAudioTrack: dev.onvoid.webrtc.media.audio.AudioTrack? = null
    
    private var localAudioSource: dev.onvoid.webrtc.media.audio.AudioTrackSource? = null
    private var localAudioTrack: dev.onvoid.webrtc.media.audio.AudioTrack? = null
    private var localVideoTrack: VideoTrack? = null
    private var localVideoSource: dev.onvoid.webrtc.media.video.VideoDeviceSource? = null
    private var videoCaptureDevice: dev.onvoid.webrtc.media.video.VideoDevice? = null
    private var _isVideoEnabled = true
    private var _isAudioEnabled = true
    
    // Protection flag to prevent double-close and close during native callback
    @Volatile
    private var isClosed = false
    private val closeLock = Any()

    private var lastFrameWidth = 0
    private var lastFrameHeight = 0
    private var frameCount = 0L
    private var lastFpsUpdateTime = 0L
    private var currentFps = 0.0

    private var _connectionState = WebRTCState.NEW
    actual val connectionState: WebRTCState
        get() = _connectionState
    
    actual val isConnected: Boolean
        get() = _connectionState == WebRTCState.CONNECTED
    
    actual val isAudioEnabled: Boolean
        get() = _isAudioEnabled

    actual fun initialize(config: WebRTCConfig, listener: WebRTCListener) {
        isClosed = false
        this.listener = listener
        
        // Create/acquire shared PeerConnectionFactory
        peerConnectionFactory = PeerConnectionFactoryManager.createFactory()

        val iceServers = config.iceServers.map { ice ->
            RTCIceServer().apply {
                urls = ice.urls
                ice.username?.let { username = it }
                ice.credential?.let { password = it }
            }
        }
        
        val rtcConfig = RTCConfiguration().apply {
            this.iceServers = iceServers
            bundlePolicy = when (config.bundlePolicy) {
                "max-bundle" -> RTCBundlePolicy.MAX_BUNDLE
                "max-compat" -> RTCBundlePolicy.MAX_COMPAT
                else -> RTCBundlePolicy.BALANCED
            }
            rtcpMuxPolicy = when (config.rtcpMuxPolicy) {
                "require" -> RTCRtcpMuxPolicy.REQUIRE
                else -> RTCRtcpMuxPolicy.NEGOTIATE
            }
            iceTransportPolicy = when (config.iceTransportPolicy) {
                "relay" -> RTCIceTransportPolicy.RELAY
                else -> RTCIceTransportPolicy.ALL
            }
        }
        
        peerConnection = peerConnectionFactory?.createPeerConnection(
            rtcConfig,
            createPeerConnectionObserver()
        )
    }
    
    /**
     * Initialize for sending audio (WHIP mode).
     * Creates audio track and adds it to the peer connection for sending.
     */
    fun initializeForSending(config: WebRTCConfig, listener: WebRTCListener) {
        isClosed = false
        this.listener = listener
        
        // Create/acquire shared PeerConnectionFactory
        peerConnectionFactory = PeerConnectionFactoryManager.createFactory()

        val iceServers = config.iceServers.map { ice ->
            RTCIceServer().apply {
                urls = ice.urls
                ice.username?.let { username = it }
                ice.credential?.let { password = it }
            }
        }
        
        val rtcConfig = RTCConfiguration().apply {
            this.iceServers = iceServers
            bundlePolicy = when (config.bundlePolicy) {
                "max-bundle" -> RTCBundlePolicy.MAX_BUNDLE
                "max-compat" -> RTCBundlePolicy.MAX_COMPAT
                else -> RTCBundlePolicy.BALANCED
            }
            rtcpMuxPolicy = when (config.rtcpMuxPolicy) {
                "require" -> RTCRtcpMuxPolicy.REQUIRE
                else -> RTCRtcpMuxPolicy.NEGOTIATE
            }
            iceTransportPolicy = when (config.iceTransportPolicy) {
                "relay" -> RTCIceTransportPolicy.RELAY
                else -> RTCIceTransportPolicy.ALL
            }
        }
        
        peerConnection = peerConnectionFactory?.createPeerConnection(
            rtcConfig,
            createPeerConnectionObserver()
        )
        
        // Create and add local audio track for sending
        localAudioSource = peerConnectionFactory?.createAudioSource(dev.onvoid.webrtc.media.audio.AudioOptions())
        localAudioTrack = peerConnectionFactory?.createAudioTrack("audio0", localAudioSource)
        localAudioTrack?.setEnabled(true)
        _isAudioEnabled = true
        
        // Add track to peer connection for sending
        localAudioTrack?.let { track ->
            peerConnection?.addTrack(track, listOf("local-audio-stream"))
        }
    }
    
    fun initializeCameraCapture(config: com.syncrobotic.webrtc.config.VideoCaptureConfig) {
        val factory = peerConnectionFactory ?: return

        // Get available video devices
        val devices = dev.onvoid.webrtc.media.MediaDevices.getVideoCaptureDevices()
        if (devices.isEmpty()) {
            println("[WebRTCClient] [JVM] No video capture devices found")
            return
        }

        // Select device (front camera preference is not applicable on desktop, use first available)
        videoCaptureDevice = devices.firstOrNull()
        val device = videoCaptureDevice ?: return

        println("[WebRTCClient] [JVM] Using video device: ${device.name}")

        // Create video source from device
        localVideoSource = VideoDeviceSource()
        localVideoSource?.setVideoCaptureDevice(device)
        localVideoSource?.setVideoCaptureCapability(
            dev.onvoid.webrtc.media.video.VideoCaptureCapability(config.width, config.height, config.fps)
        )

        // Create video track
        localVideoTrack = factory.createVideoTrack("local-video", localVideoSource)
        localVideoTrack?.setEnabled(true)
        _isVideoEnabled = true

        // Add track to peer connection
        localVideoTrack?.let { track ->
            peerConnection?.addTrack(track, listOf("local-video-stream"))
        }

        // Start capture
        localVideoSource?.start()
    }

    fun switchCamera() {
        // Desktop typically has only one camera, but cycle through available devices
        val devices = dev.onvoid.webrtc.media.MediaDevices.getVideoCaptureDevices()
        if (devices.size <= 1) return

        val currentIndex = devices.indexOf(videoCaptureDevice)
        val nextIndex = (currentIndex + 1) % devices.size
        videoCaptureDevice = devices[nextIndex]

        localVideoSource?.setVideoCaptureDevice(videoCaptureDevice)
        println("[WebRTCClient] [JVM] Switched to camera: ${videoCaptureDevice?.name}")
    }

    fun setVideoEnabled(enabled: Boolean) {
        localVideoTrack?.setEnabled(enabled)
        _isVideoEnabled = enabled
    }

    private fun createPeerConnectionObserver(): PeerConnectionObserver {
        return object : PeerConnectionObserver {
            override fun onSignalingChange(state: RTCSignalingState) {}
            
            override fun onIceConnectionChange(state: RTCIceConnectionState) {
                val mappedState = when (state) {
                    RTCIceConnectionState.NEW -> IceConnectionState.NEW
                    RTCIceConnectionState.CHECKING -> IceConnectionState.CHECKING
                    RTCIceConnectionState.CONNECTED -> IceConnectionState.CONNECTED
                    RTCIceConnectionState.COMPLETED -> IceConnectionState.COMPLETED
                    RTCIceConnectionState.FAILED -> IceConnectionState.FAILED
                    RTCIceConnectionState.DISCONNECTED -> IceConnectionState.DISCONNECTED
                    RTCIceConnectionState.CLOSED -> IceConnectionState.CLOSED
                }
                listener?.onIceConnectionStateChanged(mappedState)
            }
            
            override fun onConnectionChange(state: RTCPeerConnectionState) {
                _connectionState = when (state) {
                    RTCPeerConnectionState.NEW -> WebRTCState.NEW
                    RTCPeerConnectionState.CONNECTING -> WebRTCState.CONNECTING
                    RTCPeerConnectionState.CONNECTED -> WebRTCState.CONNECTED
                    RTCPeerConnectionState.DISCONNECTED -> WebRTCState.DISCONNECTED
                    RTCPeerConnectionState.FAILED -> WebRTCState.FAILED
                    RTCPeerConnectionState.CLOSED -> WebRTCState.CLOSED
                }
                listener?.onConnectionStateChanged(_connectionState)
            }
            
            override fun onIceGatheringChange(state: RTCIceGatheringState) {
                val mappedState = when (state) {
                    RTCIceGatheringState.NEW -> IceGatheringState.NEW
                    RTCIceGatheringState.GATHERING -> IceGatheringState.GATHERING
                    RTCIceGatheringState.COMPLETE -> IceGatheringState.COMPLETE
                }
                listener?.onIceGatheringStateChanged(mappedState)
                if (state == RTCIceGatheringState.COMPLETE) {
                    listener?.onIceGatheringComplete()
                }
            }
            
            override fun onIceCandidate(candidate: RTCIceCandidate) {
                listener?.onIceCandidate(
                    candidate.sdp,
                    candidate.sdpMid,
                    candidate.sdpMLineIndex
                )
            }
            
            override fun onDataChannel(channel: RTCDataChannel) {}
            
            override fun onRenegotiationNeeded() {}
            
            override fun onAddStream(stream: MediaStream) {
                val videoTrackCount = stream.videoTracks.size
                val audioTrackCount = stream.audioTracks.size
                
                listener?.onRemoteStreamAdded()
                
                val tracks = mutableListOf<TrackInfo>()
                stream.videoTracks.forEach { track ->
                    tracks.add(TrackInfo(
                        trackId = track.id ?: "",
                        kind = TrackKind.VIDEO,
                        enabled = track.isEnabled,
                        label = track.id
                    ))
                }
                stream.audioTracks.forEach { track ->
                    tracks.add(TrackInfo(
                        trackId = track.id ?: "",
                        kind = TrackKind.AUDIO,
                        enabled = track.isEnabled,
                        label = track.id
                    ))
                }
                listener?.onTracksChanged(videoTrackCount, audioTrackCount, tracks)
            }
            
            override fun onRemoveStream(stream: MediaStream) {
                listener?.onRemoteStreamRemoved()
            }
            
            override fun onTrack(transceiver: RTCRtpTransceiver) {
                val receiver = transceiver.receiver
                val track = receiver.track
                if (track is VideoTrack) {
                    track.addSink(object : VideoTrackSink {
                        override fun onVideoFrame(frame: dev.onvoid.webrtc.media.video.VideoFrame) {
                            val width = frame.buffer.width
                            val height = frame.buffer.height

                            frameCount++
                            val now = System.currentTimeMillis()
                            if (lastFpsUpdateTime == 0L) {
                                lastFpsUpdateTime = now
                            } else if (now - lastFpsUpdateTime >= 1000) {
                                currentFps = frameCount * 1000.0 / (now - lastFpsUpdateTime)
                                frameCount = 0
                                lastFpsUpdateTime = now
                            }

                            if (width != lastFrameWidth || height != lastFrameHeight) {
                                lastFrameWidth = width
                                lastFrameHeight = height
                            }

                            val videoFrame = VideoFrame(
                                width = width,
                                height = height,
                                timestampNs = frame.timestampNs,
                                nativeFrame = frame
                            )
                            listener?.onVideoFrame(videoFrame)
                            videoSink.get()?.onVideoFrame(frame)
                        }
                    })
                }
            }
        }
    }

    actual suspend fun createOffer(
        receiveVideo: Boolean,
        receiveAudio: Boolean
    ): String = suspendCancellableCoroutine { cont ->
        val factory = peerConnectionFactory
        val pc = peerConnection
        
        if (factory == null || pc == null) {
            cont.resumeWithException(Exception("PeerConnection not initialized"))
            return@suspendCancellableCoroutine
        }
        
        try {
            if (receiveVideo) {
                dummyVideoSource = CustomVideoSource()
                dummyVideoTrack = factory.createVideoTrack("dummy-video", dummyVideoSource)
                
                val videoInit = RTCRtpTransceiverInit().apply {
                    direction = RTCRtpTransceiverDirection.RECV_ONLY
                }
                pc.addTransceiver(dummyVideoTrack, videoInit)
            }
            
            if (receiveAudio) {
                dummyAudioSource = factory.createAudioSource(dev.onvoid.webrtc.media.audio.AudioOptions())
                dummyAudioTrack = factory.createAudioTrack("dummy-audio", dummyAudioSource)
                
                val audioInit = RTCRtpTransceiverInit().apply {
                    direction = RTCRtpTransceiverDirection.RECV_ONLY
                }
                pc.addTransceiver(dummyAudioTrack, audioInit)
            }
        } catch (e: Exception) {
            cont.resumeWithException(Exception("Failed to setup transceivers: ${e.message}"))
            return@suspendCancellableCoroutine
        }
        
        val options = RTCOfferOptions()
        
        peerConnection?.createOffer(options, object : CreateSessionDescriptionObserver {
            override fun onSuccess(description: RTCSessionDescription) {
                peerConnection?.setLocalDescription(description, object : SetSessionDescriptionObserver {
                    override fun onSuccess() {
                        cont.resume(description.sdp)
                    }
                    
                    override fun onFailure(error: String) {
                        cont.resumeWithException(Exception("Failed to set local description: $error"))
                    }
                })
            }
            
            override fun onFailure(error: String) {
                cont.resumeWithException(Exception("Failed to create offer: $error"))
            }
        })
    }
    
    actual suspend fun createSendOffer(
        sendVideo: Boolean,
        sendAudio: Boolean
    ): String = suspendCancellableCoroutine { cont ->
        val factory = peerConnectionFactory
        val pc = peerConnection
        
        if (factory == null || pc == null) {
            cont.resumeWithException(Exception("PeerConnection not initialized"))
            return@suspendCancellableCoroutine
        }
        
        // Only create and add audio track if not already done in initializeForSending()
        try {
            if (sendAudio && localAudioTrack == null) {
                localAudioSource = factory.createAudioSource(dev.onvoid.webrtc.media.audio.AudioOptions())
                localAudioTrack = factory.createAudioTrack("audio0", localAudioSource)
                
                val audioInit = RTCRtpTransceiverInit().apply {
                    direction = RTCRtpTransceiverDirection.SEND_ONLY
                }
                pc.addTransceiver(localAudioTrack, audioInit)
            }
        } catch (e: Exception) {
            cont.resumeWithException(Exception("Failed to setup send transceivers: ${e.message}"))
            return@suspendCancellableCoroutine
        }
        
        val options = RTCOfferOptions()
        
        peerConnection?.createOffer(options, object : CreateSessionDescriptionObserver {
            override fun onSuccess(description: RTCSessionDescription) {
                peerConnection?.setLocalDescription(description, object : SetSessionDescriptionObserver {
                    override fun onSuccess() {
                        cont.resume(description.sdp)
                    }
                    
                    override fun onFailure(error: String) {
                        cont.resumeWithException(Exception("Failed to set local description: $error"))
                    }
                })
            }
            
            override fun onFailure(error: String) {
                cont.resumeWithException(Exception("Failed to create send offer: $error"))
            }
        })
    }
    
    actual suspend fun createFlexibleOffer(
        mediaConfig: com.syncrobotic.webrtc.config.MediaConfig
    ): String = suspendCancellableCoroutine { cont ->
        val factory = peerConnectionFactory
        val pc = peerConnection

        if (factory == null || pc == null) {
            cont.resumeWithException(Exception("PeerConnection not initialized"))
            return@suspendCancellableCoroutine
        }

        try {
            // Video transceiver
            mediaConfig.videoDirection?.let { dir ->
                val nativeDir = when (dir) {
                    com.syncrobotic.webrtc.config.TransceiverDirection.SEND_ONLY -> RTCRtpTransceiverDirection.SEND_ONLY
                    com.syncrobotic.webrtc.config.TransceiverDirection.RECV_ONLY -> RTCRtpTransceiverDirection.RECV_ONLY
                    com.syncrobotic.webrtc.config.TransceiverDirection.SEND_RECV -> RTCRtpTransceiverDirection.SEND_RECV
                }
                if (dir.isSending && localVideoTrack != null) {
                    // Track already added by initializeCameraCapture() — set direction on existing transceiver
                    val transceivers = pc.transceivers
                    transceivers?.firstOrNull()?.let { it.direction = nativeDir }
                } else {
                    val track = if (dummyVideoTrack == null) {
                        dummyVideoSource = CustomVideoSource()
                        dummyVideoTrack = factory.createVideoTrack("dummy-video", dummyVideoSource)
                        dummyVideoTrack
                    } else {
                        dummyVideoTrack
                    }
                    val init = RTCRtpTransceiverInit().apply { direction = nativeDir }
                    pc.addTransceiver(track, init)
                }
            }

            // Audio transceiver
            mediaConfig.audioDirection?.let { dir ->
                val nativeDir = when (dir) {
                    com.syncrobotic.webrtc.config.TransceiverDirection.SEND_ONLY -> RTCRtpTransceiverDirection.SEND_ONLY
                    com.syncrobotic.webrtc.config.TransceiverDirection.RECV_ONLY -> RTCRtpTransceiverDirection.RECV_ONLY
                    com.syncrobotic.webrtc.config.TransceiverDirection.SEND_RECV -> RTCRtpTransceiverDirection.SEND_RECV
                }
                // If localAudioTrack was already added via addTrack() in initializeForSending(),
                // find the existing transceiver and update its direction instead of adding a new one.
                if (dir.isSending && localAudioTrack != null) {
                    // Track already added by initializeForSending() via addTrack().
                    // Find its transceiver and set the desired direction.
                    val transceivers = pc.transceivers
                    transceivers?.lastOrNull()?.let { it.direction = nativeDir }
                } else {
                    if (dummyAudioTrack == null) {
                        dummyAudioSource = factory.createAudioSource(dev.onvoid.webrtc.media.audio.AudioOptions())
                        dummyAudioTrack = factory.createAudioTrack("dummy-audio", dummyAudioSource)
                    }
                    val init = RTCRtpTransceiverInit().apply { direction = nativeDir }
                    pc.addTransceiver(dummyAudioTrack, init)
                }
            }
        } catch (e: Exception) {
            cont.resumeWithException(Exception("Failed to setup transceivers: ${e.message}"))
            return@suspendCancellableCoroutine
        }

        val options = RTCOfferOptions()

        peerConnection?.createOffer(options, object : CreateSessionDescriptionObserver {
            override fun onSuccess(description: RTCSessionDescription) {
                peerConnection?.setLocalDescription(description, object : SetSessionDescriptionObserver {
                    override fun onSuccess() {
                        cont.resume(description.sdp)
                    }

                    override fun onFailure(error: String) {
                        cont.resumeWithException(Exception("Failed to set local description: $error"))
                    }
                })
            }

            override fun onFailure(error: String) {
                cont.resumeWithException(Exception("Failed to create offer: $error"))
            }
        })
    }

    actual fun setAudioEnabled(enabled: Boolean) {
        localAudioTrack?.setEnabled(enabled)
        _isAudioEnabled = enabled
    }
    
    actual fun setSpeakerphoneEnabled(enabled: Boolean) {
        // Desktop audio output is controlled by the system
    }
    
    actual fun isSpeakerphoneEnabled(): Boolean = true

    actual suspend fun setRemoteAnswer(sdpAnswer: String) = suspendCancellableCoroutine { cont ->
        val description = RTCSessionDescription(RTCSdpType.ANSWER, sdpAnswer)
        peerConnection?.setRemoteDescription(description, object : SetSessionDescriptionObserver {
            override fun onSuccess() {
                cont.resume(Unit)
            }
            
            override fun onFailure(error: String) {
                cont.resumeWithException(Exception("Failed to set remote answer: $error"))
            }
        })
    }

    actual suspend fun addIceCandidate(
        candidate: String,
        sdpMid: String?,
        sdpMLineIndex: Int
    ) {
        val iceCandidate = RTCIceCandidate(sdpMid, sdpMLineIndex, candidate)
        peerConnection?.addIceCandidate(iceCandidate)
    }

    fun setVideoSink(sink: VideoTrackSink?) {
        videoSink.set(sink)
    }

    fun getCurrentFps(): Float = currentFps.toFloat()

    actual fun getLocalDescription(): String? = peerConnection?.localDescription?.sdp

    actual fun getVideoSink(): Any? = videoSink.get()

    actual suspend fun getStats(): WebRTCStats? = null

    actual fun createDataChannel(config: com.syncrobotic.webrtc.datachannel.DataChannelConfig): com.syncrobotic.webrtc.datachannel.DataChannel? {
        val pc = peerConnection ?: return null
        
        val init = RTCDataChannelInit().apply {
            ordered = config.ordered
            config.maxRetransmits?.let { maxRetransmits = it }
            config.maxPacketLifeTimeMs?.let { maxPacketLifeTime = it }
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
        synchronized(closeLock) {
            if (isClosed) {
                return
            }
            isClosed = true
        }
        
        // Clear listener first to stop callbacks
        listener = null
        
        videoSink.set(null)

        lastFrameWidth = 0
        lastFrameHeight = 0
        frameCount = 0
        lastFpsUpdateTime = 0
        currentFps = 0.0

        localVideoSource?.stop()
        localVideoSource?.dispose()
        localVideoSource = null
        localVideoTrack = null
        videoCaptureDevice = null

        localAudioTrack?.setEnabled(false)
        localAudioTrack = null
        localAudioSource = null

        dummyVideoTrack = null
        dummyAudioTrack = null
        try {
            dummyVideoSource?.dispose()
        } catch (e: Exception) {
            // Ignore dispose errors
        }
        dummyVideoSource = null
        dummyAudioSource = null

        try {
            peerConnection?.close()
        } catch (e: Exception) {
            // Ignore close errors
        }
        peerConnection = null
        
        // Dispose this connection's factory reference
        if (peerConnectionFactory != null) {
            PeerConnectionFactoryManager.disposeFactory(peerConnectionFactory)
            peerConnectionFactory = null
        }
        _connectionState = WebRTCState.CLOSED
        _isAudioEnabled = true
    }

    actual companion object {
        actual fun isSupported(): Boolean = true
    }
}
