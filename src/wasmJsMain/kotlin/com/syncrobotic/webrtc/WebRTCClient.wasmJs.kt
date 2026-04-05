package com.syncrobotic.webrtc

import com.syncrobotic.webrtc.config.WebRTCConfig
import kotlinx.coroutines.await
import kotlin.js.Promise

// External interfaces for WebRTC APIs (required for WasmJS)
external interface JsRTCPeerConnection : JsAny {
    val connectionState: JsString
    val iceConnectionState: JsString
    val iceGatheringState: JsString
    val localDescription: JsRTCSessionDescription?
    var onconnectionstatechange: (() -> Unit)?
    var oniceconnectionstatechange: (() -> Unit)?
    var onicegatheringstatechange: (() -> Unit)?
    var onicecandidate: ((JsRTCPeerConnectionIceEvent) -> Unit)?
    var ontrack: ((JsRTCTrackEvent) -> Unit)?

    fun createOffer(options: JsAny?): Promise<JsRTCSessionDescription>
    fun setLocalDescription(desc: JsAny?): Promise<JsAny?>
    fun setRemoteDescription(desc: JsAny?): Promise<JsAny?>
    fun addIceCandidate(candidate: JsAny?): Promise<JsAny?>
    fun addTransceiver(kind: JsString, init: JsAny?): JsAny
    fun createDataChannel(label: JsString, init: JsAny?): com.syncrobotic.webrtc.datachannel.JsRTCDataChannel?
    fun close()
}

external interface JsRTCSessionDescription : JsAny {
    val type: JsString
    val sdp: JsString
}

external interface JsRTCPeerConnectionIceEvent : JsAny {
    val candidate: JsRTCIceCandidate?
}

external interface JsRTCIceCandidate : JsAny {
    val candidate: JsString
    val sdpMid: JsString?
    val sdpMLineIndex: JsNumber?
}

external interface JsRTCTrackEvent : JsAny {
    val streams: JsArray<JsAny>?
}

external interface JsMediaStream : JsAny

// Top-level JS interop functions (required for WasmJS - js() must be single expression)
private fun createPeerConnection(
    iceServersJson: JsString,
    bundlePolicy: JsString,
    rtcpMuxPolicy: JsString,
    iceTransportPolicy: JsString
): JsRTCPeerConnection = js(
    """
    new RTCPeerConnection({
        iceServers: JSON.parse(iceServersJson),
        bundlePolicy: bundlePolicy,
        rtcpMuxPolicy: rtcpMuxPolicy,
        iceTransportPolicy: iceTransportPolicy
    })
    """
)

private fun createOfferOptions(offerToReceiveVideo: Boolean, offerToReceiveAudio: Boolean): JsAny =
    js("""({ offerToReceiveVideo: offerToReceiveVideo, offerToReceiveAudio: offerToReceiveAudio })""")

private fun createSessionDescription(type: JsString, sdp: JsString): JsAny =
    js("""({ type: type, sdp: sdp })""")

private fun createIceCandidateInit(candidate: JsString, sdpMid: JsString?, sdpMLineIndex: Int): JsAny =
    js("""({ candidate: candidate, sdpMid: sdpMid, sdpMLineIndex: sdpMLineIndex })""")

private fun createTransceiverInit(direction: JsString): JsAny =
    js("""({ direction: direction })""")

private fun createDataChannelInit(ordered: Boolean, negotiated: Boolean, protocol: JsString): JsAny =
    js("""({ ordered: ordered, negotiated: negotiated, protocol: protocol })""")

private fun isRtcPeerConnectionSupported(): Boolean =
    js("typeof RTCPeerConnection !== 'undefined'")

private fun getFirstStream(streams: JsArray<JsAny>?): JsMediaStream? =
    js("streams && streams.length > 0 ? streams[0] : null")

private fun getVideoTrackCount(stream: JsMediaStream): Int =
    js("stream.getVideoTracks().length")

private fun getAudioTrackCount(stream: JsMediaStream): Int =
    js("stream.getAudioTracks().length")

/**
 * WasmJS implementation of WebRTCClient.
 * Uses external interfaces for proper WasmJS interop.
 */
actual class WebRTCClient {
    private var peerConnection: JsRTCPeerConnection? = null
    private var listener: WebRTCListener? = null
    private var remoteStream: JsMediaStream? = null
    private var _isAudioEnabled = true

    private var _connectionState = WebRTCState.NEW
    actual val connectionState: WebRTCState
        get() = _connectionState

    actual val isConnected: Boolean
        get() = _connectionState == WebRTCState.CONNECTED

    actual val isAudioEnabled: Boolean
        get() = _isAudioEnabled

    actual fun initialize(config: WebRTCConfig, listener: WebRTCListener) {
        this.listener = listener
        
        // Serialize ICE servers to JSON for JS interop
        val iceServersJson = buildString {
            append("[")
            config.iceServers.forEachIndexed { idx, ice ->
                if (idx > 0) append(",")
                append("{\"urls\":[")
                ice.urls.forEachIndexed { urlIdx, url ->
                    if (urlIdx > 0) append(",")
                    append("\"$url\"")
                }
                append("]")
                ice.username?.let { append(",\"username\":\"$it\"") }
                ice.credential?.let { append(",\"credential\":\"$it\"") }
                append("}")
            }
            append("]")
        }
        
        val pc = createPeerConnection(
            iceServersJson.toJsString(),
            config.bundlePolicy.toJsString(),
            config.rtcpMuxPolicy.toJsString(),
            config.iceTransportPolicy.toJsString()
        )
        peerConnection = pc
        
        pc.onconnectionstatechange = {
            val state = pc.connectionState.toString()
            _connectionState = when (state) {
                "new" -> WebRTCState.NEW
                "connecting" -> WebRTCState.CONNECTING
                "connected" -> WebRTCState.CONNECTED
                "disconnected" -> WebRTCState.DISCONNECTED
                "failed" -> WebRTCState.FAILED
                "closed" -> WebRTCState.CLOSED
                else -> WebRTCState.NEW
            }
            this.listener?.onConnectionStateChanged(_connectionState)
        }
        
        pc.oniceconnectionstatechange = {
            val state = pc.iceConnectionState.toString()
            val mappedState = when (state) {
                "new" -> IceConnectionState.NEW
                "checking" -> IceConnectionState.CHECKING
                "connected" -> IceConnectionState.CONNECTED
                "completed" -> IceConnectionState.COMPLETED
                "failed" -> IceConnectionState.FAILED
                "disconnected" -> IceConnectionState.DISCONNECTED
                "closed" -> IceConnectionState.CLOSED
                else -> IceConnectionState.NEW
            }
            this.listener?.onIceConnectionStateChanged(mappedState)
        }
        
        pc.onicegatheringstatechange = {
            val state = pc.iceGatheringState.toString()
            val mappedState = when (state) {
                "new" -> IceGatheringState.NEW
                "gathering" -> IceGatheringState.GATHERING
                "complete" -> IceGatheringState.COMPLETE
                else -> IceGatheringState.NEW
            }
            this.listener?.onIceGatheringStateChanged(mappedState)
            if (state == "complete") {
                this.listener?.onIceGatheringComplete()
            }
        }
        
        pc.onicecandidate = { event ->
            val candidate = event.candidate
            if (candidate != null) {
                this.listener?.onIceCandidate(
                    candidate.candidate.toString(),
                    candidate.sdpMid?.toString(),
                    candidate.sdpMLineIndex?.toInt() ?: 0
                )
            }
        }
        
        pc.ontrack = { event ->
            this.listener?.onRemoteStreamAdded()
            remoteStream = getFirstStream(event.streams)
            
            val stream = remoteStream
            if (stream != null) {
                val videoTrackCount = getVideoTrackCount(stream)
                val audioTrackCount = getAudioTrackCount(stream)
                
                val tracks = mutableListOf<TrackInfo>()
                repeat(videoTrackCount) { idx ->
                    tracks.add(TrackInfo(
                        trackId = "video-$idx",
                        kind = TrackKind.VIDEO,
                        enabled = true,
                        label = null
                    ))
                }
                repeat(audioTrackCount) { idx ->
                    tracks.add(TrackInfo(
                        trackId = "audio-$idx",
                        kind = TrackKind.AUDIO,
                        enabled = true,
                        label = null
                    ))
                }
                this.listener?.onTracksChanged(videoTrackCount, audioTrackCount, tracks)
            }
        }
        
        pc.addTransceiver("video".toJsString(), createTransceiverInit("recvonly".toJsString()))
        pc.addTransceiver("audio".toJsString(), createTransceiverInit("recvonly".toJsString()))
    }

    actual suspend fun createOffer(
        receiveVideo: Boolean,
        receiveAudio: Boolean
    ): String {
        val pc = peerConnection ?: throw IllegalStateException("PeerConnection not initialized")
        val options = createOfferOptions(receiveVideo, receiveAudio)

        val offer: JsRTCSessionDescription = pc.createOffer(options).await()
        pc.setLocalDescription(offer).await<JsAny?>()

        return offer.sdp.toString()
    }

    actual suspend fun createSendOffer(
        sendVideo: Boolean,
        sendAudio: Boolean
    ): String {
        val pc = peerConnection ?: throw IllegalStateException("PeerConnection not initialized")
        val options = createOfferOptions(false, false)

        val offer: JsRTCSessionDescription = pc.createOffer(options).await()
        pc.setLocalDescription(offer).await<JsAny?>()

        return offer.sdp.toString()
    }

    actual suspend fun createFlexibleOffer(
        mediaConfig: com.syncrobotic.webrtc.config.MediaConfig
    ): String {
        TODO("createFlexibleOffer not yet implemented for this platform")
    }

    actual fun setAudioEnabled(enabled: Boolean) {
        _isAudioEnabled = enabled
    }

    actual suspend fun setRemoteAnswer(sdpAnswer: String) {
        val pc = peerConnection ?: throw IllegalStateException("PeerConnection not initialized")
        val answer = createSessionDescription("answer".toJsString(), sdpAnswer.toJsString())
        pc.setRemoteDescription(answer).await<JsAny?>()
    }

    actual suspend fun addIceCandidate(
        candidate: String,
        sdpMid: String?,
        sdpMLineIndex: Int
    ) {
        val pc = peerConnection ?: throw IllegalStateException("PeerConnection not initialized")
        val iceCandidate = createIceCandidateInit(
            candidate.toJsString(), 
            sdpMid?.toJsString(), 
            sdpMLineIndex
        )
        pc.addIceCandidate(iceCandidate).await<JsAny?>()
    }

    actual fun getLocalDescription(): String? = peerConnection?.localDescription?.sdp?.toString()

    actual fun getVideoSink(): Any? = remoteStream

    actual suspend fun getStats(): WebRTCStats? = null

    actual fun createDataChannel(config: com.syncrobotic.webrtc.datachannel.DataChannelConfig): com.syncrobotic.webrtc.datachannel.DataChannel? {
        val pc = peerConnection ?: return null
        
        val init = createDataChannelInit(config.ordered, config.negotiated, config.protocol.toJsString())
        
        return try {
            val nativeChannel = pc.createDataChannel(config.label.toJsString(), init)
            if (nativeChannel != null) {
                com.syncrobotic.webrtc.datachannel.DataChannel.create(nativeChannel)
            } else {
                null
            }
        } catch (e: Throwable) {
            null
        }
    }

    actual fun close() {
        peerConnection?.close()
        peerConnection = null
        remoteStream = null
        _connectionState = WebRTCState.CLOSED
        _isAudioEnabled = true
    }
    
    actual fun setSpeakerphoneEnabled(enabled: Boolean) {
        // Browser audio output is controlled by the system
    }
    
    actual fun isSpeakerphoneEnabled(): Boolean = true

    actual companion object {
        actual fun isSupported(): Boolean = isRtcPeerConnectionSupported()
    }
}
