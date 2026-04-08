package com.syncrobotic.webrtc

import cocoapods.GoogleWebRTC.*
import com.syncrobotic.webrtc.config.VideoCaptureConfig
import com.syncrobotic.webrtc.config.WebRTCConfig
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCSignatureOverride
import kotlinx.cinterop.useContents
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVCaptureDeviceFormat
import platform.AVFoundation.AVCaptureDevicePositionBack
import platform.AVFoundation.AVCaptureDevicePositionFront
import platform.AVFoundation.position
import platform.CoreGraphics.CGSize
import platform.CoreMedia.CMVideoFormatDescriptionGetDimensions
import platform.Foundation.NSDate
import platform.Foundation.NSNumber
import platform.Foundation.timeIntervalSince1970
import platform.UIKit.UIViewContentMode
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Custom video renderer that captures frame dimensions and calculates FPS.
 * Forwards frames to the actual renderer (RTCMTLVideoView).
 */
@OptIn(ExperimentalForeignApi::class)
private class FrameCapturingRenderer(
    private val client: WebRTCClient,
    private val targetRenderer: RTCVideoRendererProtocol?
) : NSObject(), RTCVideoRendererProtocol {

    override fun setSize(size: kotlinx.cinterop.CValue<CGSize>) {
        targetRenderer?.setSize(size)
    }

    override fun renderFrame(frame: RTCVideoFrame?) {
        frame?.let { videoFrame ->
            val width = videoFrame.width
            val height = videoFrame.height

            // Track FPS
            client.incrementFrameCount()
            val now = platform.Foundation.NSDate().timeIntervalSince1970.toLong() * 1000
            client.updateFps(now)

            // Report frame info
            if (width > 0 && height > 0) {
                client.reportVideoFrame(width, height, videoFrame.timeStampNs)
            }
        }

        // Forward to actual renderer
        targetRenderer?.renderFrame(frame)
    }
}

/**
 * iOS PeerConnection Delegate implementation.
 */
@OptIn(ExperimentalForeignApi::class)
private class PeerConnectionDelegate(
    private val client: WebRTCClient
) : NSObject(), RTCPeerConnectionDelegateProtocol {
    
    @ObjCSignatureOverride
    override fun peerConnection(
        peerConnection: RTCPeerConnection,
        didChangeSignalingState: RTCSignalingState
    ) { /* no-op */ }
    
    @ObjCSignatureOverride
    override fun peerConnection(
        peerConnection: RTCPeerConnection,
        didAddStream: RTCMediaStream
    ) {
        dispatch_async(dispatch_get_main_queue()) {
            client.handleStreamAdded(didAddStream)
        }
    }
    
    @ObjCSignatureOverride
    override fun peerConnection(
        peerConnection: RTCPeerConnection,
        didRemoveStream: RTCMediaStream
    ) {
        client.listener?.onRemoteStreamRemoved()
    }
    
    override fun peerConnectionShouldNegotiate(peerConnection: RTCPeerConnection) { /* no-op */ }
    
    @ObjCSignatureOverride
    override fun peerConnection(
        peerConnection: RTCPeerConnection,
        didChangeIceConnectionState: RTCIceConnectionState
    ) {
        val mappedState = when (didChangeIceConnectionState) {
            RTCIceConnectionState.RTCIceConnectionStateNew -> IceConnectionState.NEW
            RTCIceConnectionState.RTCIceConnectionStateChecking -> IceConnectionState.CHECKING
            RTCIceConnectionState.RTCIceConnectionStateConnected -> IceConnectionState.CONNECTED
            RTCIceConnectionState.RTCIceConnectionStateCompleted -> IceConnectionState.COMPLETED
            RTCIceConnectionState.RTCIceConnectionStateFailed -> IceConnectionState.FAILED
            RTCIceConnectionState.RTCIceConnectionStateDisconnected -> IceConnectionState.DISCONNECTED
            RTCIceConnectionState.RTCIceConnectionStateClosed -> IceConnectionState.CLOSED
            else -> IceConnectionState.NEW
        }
        client.listener?.onIceConnectionStateChanged(mappedState)
    }
    
    @ObjCSignatureOverride
    override fun peerConnection(
        peerConnection: RTCPeerConnection,
        didChangeIceGatheringState: RTCIceGatheringState
    ) {
        val mappedState = when (didChangeIceGatheringState) {
            RTCIceGatheringState.RTCIceGatheringStateNew -> IceGatheringState.NEW
            RTCIceGatheringState.RTCIceGatheringStateGathering -> IceGatheringState.GATHERING
            RTCIceGatheringState.RTCIceGatheringStateComplete -> IceGatheringState.COMPLETE
            else -> IceGatheringState.NEW
        }
        client.listener?.onIceGatheringStateChanged(mappedState)
        
        if (didChangeIceGatheringState == RTCIceGatheringState.RTCIceGatheringStateComplete) {
            client.listener?.onIceGatheringComplete()
        }
    }
    
    @ObjCSignatureOverride
    override fun peerConnection(
        peerConnection: RTCPeerConnection,
        didGenerateIceCandidate: RTCIceCandidate
    ) {
        client.listener?.onIceCandidate(
            candidate = didGenerateIceCandidate.sdp,
            sdpMid = didGenerateIceCandidate.sdpMid,
            sdpMLineIndex = didGenerateIceCandidate.sdpMLineIndex.toInt()
        )
    }
    
    @ObjCSignatureOverride
    override fun peerConnection(
        peerConnection: RTCPeerConnection,
        didRemoveIceCandidates: List<*>
    ) { /* no-op */ }
    
    @ObjCSignatureOverride
    override fun peerConnection(
        peerConnection: RTCPeerConnection,
        didOpenDataChannel: RTCDataChannel
    ) { /* no-op */ }
    
    @ObjCSignatureOverride
    override fun peerConnection(
        peerConnection: RTCPeerConnection,
        didChangeConnectionState: RTCPeerConnectionState
    ) {
        client.updateConnectionState(didChangeConnectionState)
    }
    
    @ObjCSignatureOverride
    override fun peerConnection(
        peerConnection: RTCPeerConnection,
        didAddReceiver: RTCRtpReceiver,
        streams: List<*>
    ) {
        client.handleReceiverAdded(didAddReceiver)
    }
    
    @ObjCSignatureOverride
    override fun peerConnection(
        peerConnection: RTCPeerConnection,
        didRemoveReceiver: RTCRtpReceiver
    ) { /* no-op */ }
}

/**
 * iOS implementation of WebRTCClient using GoogleWebRTC CocoaPod.
 */
@OptIn(ExperimentalForeignApi::class)
actual class WebRTCClient {
    private var peerConnectionFactory: RTCPeerConnectionFactory? = null
    private var peerConnection: RTCPeerConnection? = null
    internal var listener: WebRTCListener? = null
    private var videoTrack: RTCVideoTrack? = null
    private var audioTrack: RTCAudioTrack? = null
    
    private var localAudioSource: RTCAudioSource? = null
    private var localAudioTrack: RTCAudioTrack? = null
    private var _isAudioEnabled = true
    private var _isSpeakerphoneEnabled = true

    private var cameraCapturer: RTCCameraVideoCapturer? = null
    private var localVideoSource: RTCVideoSource? = null
    private var localVideoTrack: RTCVideoTrack? = null
    private var currentCameraPosition: Long = AVCaptureDevicePositionFront
    private var _isVideoEnabled = true
    
    private var peerConnectionDelegate: PeerConnectionDelegate? = null
    private var videoSink: Any? = null
    
    // Video renderer (RTCMTLVideoView for Metal-based rendering)
    private var remoteVideoView: RTCMTLVideoView? = null
    private var frameCapturingRenderer: FrameCapturingRenderer? = null
    
    // FPS tracking
    private var frameCount = 0
    private var lastFpsUpdateTime = 0L
    private var currentFps = 0.0
    private var lastFrameWidth = 0
    private var lastFrameHeight = 0

    private var _connectionState = WebRTCState.NEW
    actual val connectionState: WebRTCState
        get() = _connectionState
    
    actual val isConnected: Boolean
        get() = _connectionState == WebRTCState.CONNECTED
    
    actual val isAudioEnabled: Boolean
        get() = _isAudioEnabled

    actual fun initialize(config: WebRTCConfig, listener: WebRTCListener) {
        this.listener = listener
        
        // Create factory (each connection has its own factory for isolation)
        PeerConnectionFactoryManager.ensureInitialized()
        configureAudioSession()
        
        peerConnectionFactory = PeerConnectionFactoryManager.createFactory()
        
        val rtcConfig = RTCConfiguration().apply {
            iceServers = config.iceServers.map { iceServer ->
                RTCIceServer(
                    uRLStrings = iceServer.urls,
                    username = iceServer.username,
                    credential = iceServer.credential
                )
            }
            
            sdpSemantics = RTCSdpSemantics.RTCSdpSemanticsUnifiedPlan
            
            bundlePolicy = when (config.bundlePolicy) {
                "max-bundle" -> RTCBundlePolicy.RTCBundlePolicyMaxBundle
                "max-compat" -> RTCBundlePolicy.RTCBundlePolicyMaxCompat
                else -> RTCBundlePolicy.RTCBundlePolicyBalanced
            }
            
            rtcpMuxPolicy = when (config.rtcpMuxPolicy) {
                "require" -> RTCRtcpMuxPolicy.RTCRtcpMuxPolicyRequire
                else -> RTCRtcpMuxPolicy.RTCRtcpMuxPolicyNegotiate
            }
            
            iceTransportPolicy = when (config.iceTransportPolicy) {
                "relay" -> RTCIceTransportPolicy.RTCIceTransportPolicyRelay
                else -> RTCIceTransportPolicy.RTCIceTransportPolicyAll
            }
        }
        
        peerConnectionDelegate = PeerConnectionDelegate(this)
        
        peerConnection = peerConnectionFactory?.peerConnectionWithConfiguration(
            configuration = rtcConfig,
            constraints = RTCMediaConstraints(
                mandatoryConstraints = null,
                optionalConstraints = null
            ),
            delegate = peerConnectionDelegate
        )
        
        peerConnection?.addTransceiverOfType(
            RTCRtpMediaType.RTCRtpMediaTypeVideo,
            init = RTCRtpTransceiverInit().apply {
                direction = RTCRtpTransceiverDirection.RTCRtpTransceiverDirectionRecvOnly
            }
        )
        
        peerConnection?.addTransceiverOfType(
            RTCRtpMediaType.RTCRtpMediaTypeAudio,
            init = RTCRtpTransceiverInit().apply {
                direction = RTCRtpTransceiverDirection.RTCRtpTransceiverDirectionRecvOnly
            }
        )
    }
    
    /**
     * Initialize for sending audio (WHIP mode).
     */
    fun initializeForSending(config: WebRTCConfig, listener: WebRTCListener) {
        this.listener = listener
        
        // Create factory (each connection has its own factory for isolation)
        PeerConnectionFactoryManager.ensureInitialized()
        configureAudioSession()
        
        peerConnectionFactory = PeerConnectionFactoryManager.createFactory()
        
        val rtcConfig = RTCConfiguration().apply {
            iceServers = config.iceServers.map { iceServer ->
                RTCIceServer(
                    uRLStrings = iceServer.urls,
                    username = iceServer.username,
                    credential = iceServer.credential
                )
            }
            sdpSemantics = RTCSdpSemantics.RTCSdpSemanticsUnifiedPlan
            bundlePolicy = when (config.bundlePolicy) {
                "max-bundle" -> RTCBundlePolicy.RTCBundlePolicyMaxBundle
                "max-compat" -> RTCBundlePolicy.RTCBundlePolicyMaxCompat
                else -> RTCBundlePolicy.RTCBundlePolicyBalanced
            }
            rtcpMuxPolicy = when (config.rtcpMuxPolicy) {
                "require" -> RTCRtcpMuxPolicy.RTCRtcpMuxPolicyRequire
                else -> RTCRtcpMuxPolicy.RTCRtcpMuxPolicyNegotiate
            }
            iceTransportPolicy = when (config.iceTransportPolicy) {
                "relay" -> RTCIceTransportPolicy.RTCIceTransportPolicyRelay
                else -> RTCIceTransportPolicy.RTCIceTransportPolicyAll
            }
        }
        
        peerConnectionDelegate = PeerConnectionDelegate(this)
        
        peerConnection = peerConnectionFactory?.peerConnectionWithConfiguration(
            configuration = rtcConfig,
            constraints = RTCMediaConstraints(mandatoryConstraints = null, optionalConstraints = null),
            delegate = peerConnectionDelegate
        )
        
        localAudioSource = peerConnectionFactory?.audioSourceWithConstraints(
            RTCMediaConstraints(mandatoryConstraints = null, optionalConstraints = null)
        )
        localAudioTrack = peerConnectionFactory?.audioTrackWithSource(localAudioSource!!, trackId = "audio0")
        localAudioTrack?.isEnabled = true
        _isAudioEnabled = true
        
        localAudioTrack?.let { track ->
            peerConnection?.addTrack(track, streamIds = listOf("local-audio-stream"))
        }
    }
    
    /**
     * Initialize local camera capture for sending video.
     * Creates a video source, camera capturer, and video track, then adds it to the peer connection.
     */
    fun initializeCameraCapture(config: VideoCaptureConfig) {
        val factory = peerConnectionFactory ?: return

        // Create video source from factory
        localVideoSource = factory.videoSource()
        val videoSource = localVideoSource ?: return

        // Create camera capturer with video source as delegate
        cameraCapturer = RTCCameraVideoCapturer(delegate = videoSource)
        val capturer = cameraCapturer ?: return

        // Set initial camera position based on config
        currentCameraPosition = if (config.useFrontCamera) {
            AVCaptureDevicePositionFront
        } else {
            AVCaptureDevicePositionBack
        }

        // Find appropriate camera device
        val allDevices = RTCCameraVideoCapturer.captureDevices() ?: emptyList<Any>()
        var device: AVCaptureDevice? = null
        for (d in allDevices) {
            val captureDevice = d as? AVCaptureDevice ?: continue
            if (captureDevice.position == currentCameraPosition) {
                device = captureDevice
                break
            }
        }
        if (device == null) device = allDevices.firstOrNull() as? AVCaptureDevice
        if (device == null) {
            println("[WebRTCClient] [iOS] No video capture devices found")
            return
        }

        println("[WebRTCClient] [iOS] Using camera device: ${device.localizedName}")

        // Find the best matching format for the requested resolution
        val formats = RTCCameraVideoCapturer.supportedFormatsForDevice(device)?.filterIsInstance<AVCaptureDeviceFormat>() ?: emptyList()
        val targetFormat = formats.minByOrNull { format ->
            val desc = (format as AVCaptureDeviceFormat).formatDescription
            if (desc != null) {
                val dim = CMVideoFormatDescriptionGetDimensions(desc)
                val widthDiff = kotlin.math.abs(dim.useContents { width } - config.width)
                val heightDiff = kotlin.math.abs(dim.useContents { height } - config.height)
                widthDiff + heightDiff
            } else {
                Int.MAX_VALUE
            }
        }
        if (targetFormat == null) {
            println("[WebRTCClient] [iOS] No supported formats found for device")
            return
        }

        // Start capture
        capturer.startCaptureWithDevice(device, format = targetFormat, fps = config.fps.toLong())

        // Create video track from factory
        localVideoTrack = factory.videoTrackWithSource(videoSource, trackId = "video0")
        localVideoTrack?.isEnabled = true
        _isVideoEnabled = true

        // Add track to peer connection
        localVideoTrack?.let { track ->
            peerConnection?.addTrack(track, streamIds = listOf("local-video-stream"))
        }

        println("[WebRTCClient] [iOS] Camera capture initialized: ${config.width}x${config.height}@${config.fps}fps")
    }

    /**
     * Switch between front and back camera.
     */
    fun switchCamera() {
        val capturer = cameraCapturer ?: return

        // Stop current capture
        capturer.stopCapture()

        // Switch position
        currentCameraPosition = if (currentCameraPosition == AVCaptureDevicePositionFront) {
            AVCaptureDevicePositionBack
        } else {
            AVCaptureDevicePositionFront
        }

        // Find new device
        @Suppress("UNCHECKED_CAST")
        val devices = RTCCameraVideoCapturer.captureDevices() as? List<AVCaptureDevice> ?: emptyList()
        val device = devices.firstOrNull { it.position() == currentCameraPosition }
        if (device == null) {
            println("[WebRTCClient] [iOS] No camera found for position: $currentCameraPosition")
            return
        }

        // Find a format for the new device
        val formats = RTCCameraVideoCapturer.supportedFormatsForDevice(device)?.filterIsInstance<AVCaptureDeviceFormat>() ?: emptyList()
        val format = formats.lastOrNull()
        if (format == null) {
            println("[WebRTCClient] [iOS] No supported formats for device")
            return
        }

        // Start capture with new device
        capturer.startCaptureWithDevice(device, format = format, fps = 30)
        println("[WebRTCClient] [iOS] Switched to camera: ${device.localizedName}")
    }

    /**
     * Enable or disable the local video track.
     */
    fun setVideoEnabled(enabled: Boolean) {
        localVideoTrack?.isEnabled = enabled
        _isVideoEnabled = enabled
    }

    /**
     * Enable or disable the received (remote) video track.
     */
    fun setRemoteVideoEnabled(enabled: Boolean) {
        videoTrack?.isEnabled = enabled
    }

    private fun configureAudioSession() {
        val audioSession = RTCAudioSession.sharedInstance()
        audioSession.lockForConfiguration()
        try {
            val category = platform.AVFAudio.AVAudioSessionCategoryPlayAndRecord
            val mode = platform.AVFAudio.AVAudioSessionModeVoiceChat
            
            if (category != null && mode != null) {
                audioSession.setCategory(
                    category,
                    withOptions = platform.AVFAudio.AVAudioSessionCategoryOptionDefaultToSpeaker or
                            platform.AVFAudio.AVAudioSessionCategoryOptionAllowBluetooth,
                    error = null
                )
                audioSession.setMode(mode, error = null)
            }
            audioSession.setActive(true, error = null)
            
            val avAudioSession = platform.AVFAudio.AVAudioSession.sharedInstance()
            avAudioSession.overrideOutputAudioPort(platform.AVFAudio.AVAudioSessionPortOverrideSpeaker, null)
            _isSpeakerphoneEnabled = true
        } catch (_: Exception) { }
        audioSession.unlockForConfiguration()
    }
    
    internal fun handleStreamAdded(stream: RTCMediaStream) {
        val videoTracks = stream.videoTracks
        val audioTracks = stream.audioTracks
        val videoTrackCount = videoTracks.size.toInt()
        val audioTrackCount = audioTracks.size.toInt()
        
        listener?.onRemoteStreamAdded()
        
        val trackInfoList = mutableListOf<TrackInfo>()
        videoTracks.forEach { track ->
            val rtcTrack = track as? RTCMediaStreamTrack
            trackInfoList.add(TrackInfo(
                trackId = rtcTrack?.trackId ?: "",
                kind = TrackKind.VIDEO,
                enabled = rtcTrack?.isEnabled ?: false,
                label = rtcTrack?.trackId
            ))
        }
        audioTracks.forEach { track ->
            val rtcTrack = track as? RTCMediaStreamTrack
            trackInfoList.add(TrackInfo(
                trackId = rtcTrack?.trackId ?: "",
                kind = TrackKind.AUDIO,
                enabled = rtcTrack?.isEnabled ?: false,
                label = rtcTrack?.trackId
            ))
        }
        listener?.onTracksChanged(videoTrackCount, audioTrackCount, trackInfoList)

        // Attach video track to frame capturing renderer (which forwards to video view)
        if (videoTracks.isNotEmpty()) {
            val track = videoTracks.first() as? RTCVideoTrack
            track?.let {
                videoTrack = it
                frameCapturingRenderer?.let { renderer ->
                    it.addRenderer(renderer)
                }
            }
        }
        if (audioTracks.isNotEmpty()) {
            audioTrack = audioTracks.first() as? RTCAudioTrack
        }
    }
    
    internal fun handleReceiverAdded(receiver: RTCRtpReceiver) {
        val track = receiver.track
        if (track?.kind == "video") {
            (track as? RTCVideoTrack)?.let { vTrack ->
                videoTrack = vTrack
                dispatch_async(dispatch_get_main_queue()) {
                    frameCapturingRenderer?.let { renderer ->
                        vTrack.addRenderer(renderer)
                    }
                }
            }
        }
    }
    
    internal fun updateConnectionState(state: RTCPeerConnectionState) {
        _connectionState = when (state) {
            RTCPeerConnectionState.RTCPeerConnectionStateNew -> WebRTCState.NEW
            RTCPeerConnectionState.RTCPeerConnectionStateConnecting -> WebRTCState.CONNECTING
            RTCPeerConnectionState.RTCPeerConnectionStateConnected -> WebRTCState.CONNECTED
            RTCPeerConnectionState.RTCPeerConnectionStateDisconnected -> WebRTCState.DISCONNECTED
            RTCPeerConnectionState.RTCPeerConnectionStateFailed -> WebRTCState.FAILED
            RTCPeerConnectionState.RTCPeerConnectionStateClosed -> WebRTCState.CLOSED
            else -> WebRTCState.NEW
        }
        listener?.onConnectionStateChanged(_connectionState)
    }

    actual suspend fun createOffer(
        receiveVideo: Boolean,
        receiveAudio: Boolean
    ): String = suspendCancellableCoroutine { cont ->
        val constraints = RTCMediaConstraints(
            mandatoryConstraints = mapOf(
                "OfferToReceiveVideo" to receiveVideo.toString(),
                "OfferToReceiveAudio" to receiveAudio.toString()
            ),
            optionalConstraints = null
        )
        
        peerConnection?.offerForConstraints(constraints) { sdp, error ->
            if (error != null) {
                cont.resumeWithException(Exception("Failed to create offer: ${error.localizedDescription}"))
                return@offerForConstraints
            }
            
            sdp?.let { sessionDescription ->
                peerConnection?.setLocalDescription(sessionDescription) { setError ->
                    if (setError != null) {
                        cont.resumeWithException(Exception("Failed to set local description: ${setError.localizedDescription}"))
                    } else {
                        cont.resume(sessionDescription.sdp)
                    }
                }
            } ?: cont.resumeWithException(Exception("SDP is null"))
        }
    }
    
    actual suspend fun createSendOffer(
        sendVideo: Boolean,
        sendAudio: Boolean
    ): String = suspendCancellableCoroutine { cont ->
        val constraints = RTCMediaConstraints(
            mandatoryConstraints = null,
            optionalConstraints = null
        )
        
        peerConnection?.offerForConstraints(constraints) { sdp, error ->
            if (error != null) {
                cont.resumeWithException(Exception("Failed to create send offer: ${error.localizedDescription}"))
                return@offerForConstraints
            }
            
            sdp?.let { sessionDescription ->
                peerConnection?.setLocalDescription(sessionDescription) { setError ->
                    if (setError != null) {
                        cont.resumeWithException(Exception("Failed to set local description: ${setError.localizedDescription}"))
                    } else {
                        cont.resume(sessionDescription.sdp)
                    }
                }
            } ?: cont.resumeWithException(Exception("SDP is null"))
        }
    }
    
    actual suspend fun createFlexibleOffer(
        mediaConfig: com.syncrobotic.webrtc.config.MediaConfig
    ): String = suspendCancellableCoroutine { cont ->
        val pc = peerConnection
        if (pc == null) {
            cont.resumeWithException(Exception("PeerConnection not initialized"))
            return@suspendCancellableCoroutine
        }

        try {
            // Video transceiver
            mediaConfig.videoDirection?.let { dir ->
                val nativeDir = when (dir) {
                    com.syncrobotic.webrtc.config.TransceiverDirection.SEND_ONLY ->
                        RTCRtpTransceiverDirection.RTCRtpTransceiverDirectionSendOnly
                    com.syncrobotic.webrtc.config.TransceiverDirection.RECV_ONLY ->
                        RTCRtpTransceiverDirection.RTCRtpTransceiverDirectionRecvOnly
                    com.syncrobotic.webrtc.config.TransceiverDirection.SEND_RECV ->
                        RTCRtpTransceiverDirection.RTCRtpTransceiverDirectionSendRecv
                }
                if (dir.isSending && localVideoTrack != null) {
                    // Track already added by initializeCameraCapture() — set direction on existing transceiver
                    val existingTransceiver = pc.transceivers.filterIsInstance<RTCRtpTransceiver>()
                        .firstOrNull { it.mediaType == RTCRtpMediaType.RTCRtpMediaTypeVideo }
                    if (existingTransceiver != null) {
                        existingTransceiver.setDirection(nativeDir, error = null)
                    } else {
                        pc.addTrack(localVideoTrack!!, streamIds = listOf("local-video-stream"))
                        pc.transceivers.filterIsInstance<RTCRtpTransceiver>()
                            .lastOrNull { it.mediaType == RTCRtpMediaType.RTCRtpMediaTypeVideo }
                            ?.setDirection(nativeDir, error = null)
                    }
                } else {
                    pc.addTransceiverOfType(
                        RTCRtpMediaType.RTCRtpMediaTypeVideo,
                        init = RTCRtpTransceiverInit().apply {
                            direction = nativeDir
                        }
                    )
                }
            }

            // Audio transceiver
            mediaConfig.audioDirection?.let { dir ->
                val nativeDir = when (dir) {
                    com.syncrobotic.webrtc.config.TransceiverDirection.SEND_ONLY ->
                        RTCRtpTransceiverDirection.RTCRtpTransceiverDirectionSendOnly
                    com.syncrobotic.webrtc.config.TransceiverDirection.RECV_ONLY ->
                        RTCRtpTransceiverDirection.RTCRtpTransceiverDirectionRecvOnly
                    com.syncrobotic.webrtc.config.TransceiverDirection.SEND_RECV ->
                        RTCRtpTransceiverDirection.RTCRtpTransceiverDirectionSendRecv
                }
                if (dir.isSending && localAudioTrack != null) {
                    pc.addTrack(localAudioTrack!!, streamIds = listOf("local-audio-stream"))
                    // Update transceiver direction
                    pc.transceivers.filterIsInstance<RTCRtpTransceiver>()
                        .lastOrNull { it.mediaType == RTCRtpMediaType.RTCRtpMediaTypeAudio }
                        ?.setDirection(nativeDir, error = null)
                } else {
                    pc.addTransceiverOfType(
                        RTCRtpMediaType.RTCRtpMediaTypeAudio,
                        init = RTCRtpTransceiverInit().apply {
                            direction = nativeDir
                        }
                    )
                }
            }
        } catch (e: Exception) {
            cont.resumeWithException(Exception("Failed to setup transceivers: ${e.message}"))
            return@suspendCancellableCoroutine
        }

        val constraints = RTCMediaConstraints(
            mandatoryConstraints = null,
            optionalConstraints = null
        )

        pc.offerForConstraints(constraints) { sdp, error ->
            if (error != null) {
                cont.resumeWithException(Exception("Failed to create flexible offer: ${error.localizedDescription}"))
                return@offerForConstraints
            }

            sdp?.let { sessionDescription ->
                pc.setLocalDescription(sessionDescription) { setError ->
                    if (setError != null) {
                        cont.resumeWithException(Exception("Failed to set local description: ${setError.localizedDescription}"))
                    } else {
                        cont.resume(sessionDescription.sdp)
                    }
                }
            } ?: cont.resumeWithException(Exception("SDP is null"))
        }
    }

    actual fun setAudioEnabled(enabled: Boolean) {
        localAudioTrack?.isEnabled = enabled
        _isAudioEnabled = enabled
    }
    
    actual fun setSpeakerphoneEnabled(enabled: Boolean) {
        try {
            val avAudioSession = platform.AVFAudio.AVAudioSession.sharedInstance()
            val portOverride = if (enabled) {
                platform.AVFAudio.AVAudioSessionPortOverrideSpeaker
            } else {
                platform.AVFAudio.AVAudioSessionPortOverrideNone
            }
            avAudioSession.overrideOutputAudioPort(portOverride, null)
            _isSpeakerphoneEnabled = enabled
        } catch (_: Exception) { }
    }
    
    actual fun isSpeakerphoneEnabled(): Boolean = _isSpeakerphoneEnabled

    actual suspend fun setRemoteAnswer(sdpAnswer: String) = suspendCancellableCoroutine { cont ->
        val sessionDescription = RTCSessionDescription(
            type = RTCSdpType.RTCSdpTypeAnswer,
            sdp = sdpAnswer
        )
        
        peerConnection?.setRemoteDescription(sessionDescription) { error ->
            if (error != null) {
                cont.resumeWithException(Exception("Failed to set remote answer: ${error.localizedDescription}"))
            } else {
                cont.resume(Unit)
            }
        }
    }

    actual suspend fun addIceCandidate(
        candidate: String,
        sdpMid: String?,
        sdpMLineIndex: Int
    ) {
        val iceCandidate = RTCIceCandidate(
            sdp = candidate,
            sdpMLineIndex = sdpMLineIndex,
            sdpMid = sdpMid
        )
        peerConnection?.addIceCandidate(iceCandidate)
    }

    actual fun getLocalDescription(): String? = peerConnection?.localDescription()?.sdp

    actual fun getVideoSink(): Any? = videoTrack
    
    /**
     * Get the local video track (for camera preview rendering).
     */
    fun getLocalVideoTrack(): RTCVideoTrack? = localVideoTrack

    /**
     * Create a Metal-based video view for rendering.
     * Call this to get a UIView that can be added to the SwiftUI/UIKit hierarchy.
     */
    fun createVideoView(): RTCMTLVideoView {
        val view = RTCMTLVideoView()
        view.videoContentMode = UIViewContentMode.UIViewContentModeScaleAspectFit
        remoteVideoView = view
        // Create frame capturing renderer that wraps the video view
        frameCapturingRenderer = FrameCapturingRenderer(this, view)
        return view
    }
    
    /**
     * Get the frame capturing renderer for attaching to video tracks.
     */
    fun getFrameCapturingRenderer(): RTCVideoRendererProtocol? = frameCapturingRenderer
    
    /**
     * Get the current video FPS (frames per second).
     */
    fun getCurrentFps(): Float = currentFps.toFloat()
    
    /**
     * Increment frame count for FPS calculation.
     */
    internal fun incrementFrameCount() {
        frameCount++
    }
    
    /**
     * Update FPS based on elapsed time.
     */
    internal fun updateFps(nowMs: Long) {
        if (lastFpsUpdateTime == 0L) {
            lastFpsUpdateTime = nowMs
        } else if (nowMs - lastFpsUpdateTime >= 1000) {
            currentFps = frameCount * 1000.0 / (nowMs - lastFpsUpdateTime)
            frameCount = 0
            lastFpsUpdateTime = nowMs
        }
    }
    
    /**
     * Report video frame info to listener.
     */
    internal fun reportVideoFrame(width: Int, height: Int, timestampNs: Long) {
        if (width != lastFrameWidth || height != lastFrameHeight) {
            lastFrameWidth = width
            lastFrameHeight = height
            println("[WebRTCClient] [Video] Frame dimensions: ${width}x${height}")
        }

        listener?.onVideoFrame(VideoFrame(
            width = width,
            height = height,
            timestampNs = timestampNs,
            nativeFrame = null
        ))
    }

    actual suspend fun getStats(): WebRTCStats? = suspendCancellableCoroutine { cont ->
        val pc = peerConnection
        if (pc == null) {
            cont.resume(null)
            return@suspendCancellableCoroutine
        }

        pc.statisticsWithCompletionHandler { report ->
            if (report == null) {
                cont.resume(null)
                return@statisticsWithCompletionHandler
            }

            var audioBitrate: Long = 0
            var roundTripTime: Double = 0.0
            var jitter: Double = 0.0
            var packetsSent: Long = 0
            var packetsLost: Long = 0
            var codec = "unknown"
            val timestampMs = (NSDate().timeIntervalSince1970 * 1000).toLong()

            val statistics = report.statistics as? Map<*, *> ?: emptyMap<Any, Any>()
            for ((_, value) in statistics) {
                val stat = value as? RTCStatistics ?: continue
                val type = stat.type

                when (type) {
                    "outbound-rtp" -> {
                        val values = stat.values as? Map<*, *> ?: continue
                        val kind = values["kind"] as? String
                        if (kind == "audio") {
                            (values["bytesSent"] as? NSNumber)?.let { audioBitrate = it.longValue * 8 }
                            (values["packetsSent"] as? NSNumber)?.let { packetsSent = it.longValue }
                        }
                    }
                    "remote-inbound-rtp" -> {
                        val values = stat.values as? Map<*, *> ?: continue
                        val kind = values["kind"] as? String
                        if (kind == "audio") {
                            (values["roundTripTime"] as? NSNumber)?.let { roundTripTime = it.doubleValue * 1000 }
                            (values["jitter"] as? NSNumber)?.let { jitter = it.doubleValue * 1000 }
                            (values["packetsLost"] as? NSNumber)?.let { packetsLost = it.longValue }
                        }
                    }
                    "codec" -> {
                        val values = stat.values as? Map<*, *> ?: continue
                        val mimeType = values["mimeType"] as? String
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
        
        val init = RTCDataChannelConfiguration().apply {
            isOrdered = config.ordered
            config.maxRetransmits?.let { maxRetransmits = it }
            config.maxPacketLifeTimeMs?.let { maxRetransmitTimeMs = it.toLong() }
            protocol = config.protocol
            isNegotiated = config.negotiated
            config.id?.let { channelId = it }
        }
        
        return try {
            val nativeChannel = pc.dataChannelForLabel(config.label, configuration = init)
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
        cameraCapturer?.stopCapture()
        cameraCapturer = null
        localVideoSource = null
        localVideoTrack = null

        localAudioTrack?.isEnabled = false
        localAudioTrack = null
        localAudioSource = null

        // Clear video renderer state to prevent stale frame delivery
        frameCapturingRenderer = null
        remoteVideoView = null
        videoTrack = null
        audioTrack = null

        peerConnection?.close()
        peerConnection = null
        peerConnectionDelegate = null
        
        // Dispose this connection's factory
        if (peerConnectionFactory != null) {
            PeerConnectionFactoryManager.disposeFactory(peerConnectionFactory)
            peerConnectionFactory = null
        }

        try {
            val avAudioSession = platform.AVFAudio.AVAudioSession.sharedInstance()
            avAudioSession.overrideOutputAudioPort(platform.AVFAudio.AVAudioSessionPortOverrideNone, null)
        } catch (_: Exception) { }

        _connectionState = WebRTCState.CLOSED
        _isAudioEnabled = true
        _isSpeakerphoneEnabled = true
    }

    actual companion object {
        actual fun isSupported(): Boolean = true
    }
}
