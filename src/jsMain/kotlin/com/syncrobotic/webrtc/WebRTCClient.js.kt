package com.syncrobotic.webrtc

import com.syncrobotic.webrtc.config.WebRTCConfig
import kotlinx.coroutines.await
import kotlin.js.Promise

/**
 * JavaScript/Browser implementation of WebRTCClient using native WebRTC APIs.
 */
actual class WebRTCClient {
    private var peerConnection: dynamic = null
    private var listener: WebRTCListener? = null
    private var remoteStream: dynamic = null
    private var localAudioTrack: dynamic = null
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
        
        val iceServers = js("[]")
        config.iceServers.forEach { ice ->
            val server = js("{}")
            server.urls = ice.urls.toTypedArray()
            ice.username?.let { server.username = it }
            ice.credential?.let { server.credential = it }
            iceServers.push(server)
        }
        
        val rtcConfig = js("{}")
        rtcConfig.iceServers = iceServers
        rtcConfig.bundlePolicy = config.bundlePolicy
        rtcConfig.rtcpMuxPolicy = config.rtcpMuxPolicy
        rtcConfig.iceTransportPolicy = config.iceTransportPolicy
        
        peerConnection = js("new RTCPeerConnection(rtcConfig)")
        
        peerConnection.onconnectionstatechange = {
            val state = peerConnection.connectionState as String
            _connectionState = when (state) {
                "new" -> WebRTCState.NEW
                "connecting" -> WebRTCState.CONNECTING
                "connected" -> WebRTCState.CONNECTED
                "disconnected" -> WebRTCState.DISCONNECTED
                "failed" -> WebRTCState.FAILED
                "closed" -> WebRTCState.CLOSED
                else -> WebRTCState.NEW
            }
            listener.onConnectionStateChanged(_connectionState)
        }
        
        peerConnection.oniceconnectionstatechange = {
            val state = peerConnection.iceConnectionState as String
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
            listener.onIceConnectionStateChanged(mappedState)
        }
        
        peerConnection.onicegatheringstatechange = {
            val state = peerConnection.iceGatheringState as String
            val mappedState = when (state) {
                "new" -> IceGatheringState.NEW
                "gathering" -> IceGatheringState.GATHERING
                "complete" -> IceGatheringState.COMPLETE
                else -> IceGatheringState.NEW
            }
            listener.onIceGatheringStateChanged(mappedState)
            if (state == "complete") {
                listener.onIceGatheringComplete()
            }
        }
        
        peerConnection.onicecandidate = { event: dynamic ->
            val candidate = event.candidate
            if (candidate != null) {
                listener.onIceCandidate(
                    candidate.candidate as String,
                    candidate.sdpMid as? String,
                    (candidate.sdpMLineIndex as? Number)?.toInt() ?: 0
                )
            }
        }
        
        peerConnection.ontrack = { event: dynamic ->
            listener.onRemoteStreamAdded()
            remoteStream = event.streams?.get(0)
            
            val stream = remoteStream
            if (stream != null) {
                val videoTracks = stream.getVideoTracks()
                val audioTracks = stream.getAudioTracks()
                val videoTrackCount = videoTracks.length as Int
                val audioTrackCount = audioTracks.length as Int
                
                val tracks = mutableListOf<TrackInfo>()
                for (i in 0 until videoTrackCount) {
                    val track = videoTracks[i]
                    tracks.add(TrackInfo(
                        trackId = track.id as String,
                        kind = TrackKind.VIDEO,
                        enabled = track.enabled as Boolean,
                        label = track.label as? String
                    ))
                }
                for (i in 0 until audioTrackCount) {
                    val track = audioTracks[i]
                    tracks.add(TrackInfo(
                        trackId = track.id as String,
                        kind = TrackKind.AUDIO,
                        enabled = track.enabled as Boolean,
                        label = track.label as? String
                    ))
                }
                listener.onTracksChanged(videoTrackCount, audioTrackCount, tracks)
            }
        }
        
        peerConnection.addTransceiver("video", js("{ direction: 'recvonly' }"))
        peerConnection.addTransceiver("audio", js("{ direction: 'recvonly' }"))
    }

    actual suspend fun createOffer(
        receiveVideo: Boolean,
        receiveAudio: Boolean
    ): String {
        val options = js("{}")
        options.offerToReceiveVideo = receiveVideo
        options.offerToReceiveAudio = receiveAudio

        val offer: dynamic = (peerConnection.createOffer(options) as Promise<dynamic>).await()
        (peerConnection.setLocalDescription(offer) as Promise<dynamic>).await()

        return offer.sdp as String
    }

    actual suspend fun createSendOffer(
        sendVideo: Boolean,
        sendAudio: Boolean
    ): String {
        if (sendAudio) {
            val constraints = js("{}")
            constraints.audio = true
            constraints.video = false
            val stream: dynamic = (js("navigator.mediaDevices.getUserMedia(constraints)") as Promise<dynamic>).await()
            val audioTracks = stream.getAudioTracks()
            if (audioTracks.length > 0) {
                localAudioTrack = audioTracks[0]
                peerConnection.addTrack(localAudioTrack, stream)
            }
        }

        val options = js("{}")
        val offer: dynamic = (peerConnection.createOffer(options) as Promise<dynamic>).await()
        (peerConnection.setLocalDescription(offer) as Promise<dynamic>).await()

        return offer.sdp as String
    }

    actual fun setAudioEnabled(enabled: Boolean) {
        localAudioTrack?.enabled = enabled
        _isAudioEnabled = enabled
    }

    actual suspend fun setRemoteAnswer(sdpAnswer: String) {
        val answer = js("{}")
        answer.type = "answer"
        answer.sdp = sdpAnswer
        
        (peerConnection.setRemoteDescription(answer) as Promise<dynamic>).await()
    }

    actual suspend fun addIceCandidate(
        candidate: String,
        sdpMid: String?,
        sdpMLineIndex: Int
    ) {
        val iceCandidate = js("{}")
        iceCandidate.candidate = candidate
        iceCandidate.sdpMid = sdpMid
        iceCandidate.sdpMLineIndex = sdpMLineIndex
        
        (peerConnection.addIceCandidate(iceCandidate) as Promise<dynamic>).await()
    }

    actual fun getLocalDescription(): String? = peerConnection?.localDescription?.sdp as? String

    actual fun getVideoSink(): Any? = remoteStream

    actual suspend fun getStats(): WebRTCStats? {
        val pc = peerConnection ?: return null
        val report: dynamic = (pc.getStats() as Promise<dynamic>).await()

        var audioBitrate: Long = 0
        var roundTripTime: Double = 0.0
        var jitter: Double = 0.0
        var packetsSent: Long = 0
        var packetsLost: Long = 0
        var codec = "unknown"
        val timestampMs = js("Date.now()") as Long

        report.forEach { stats: dynamic ->
            val type = stats.type as? String
            when (type) {
                "outbound-rtp" -> {
                    val kind = stats.kind as? String
                    if (kind == "audio") {
                        (stats.bytesSent as? Number)?.let { audioBitrate = it.toLong() * 8 }
                        (stats.packetsSent as? Number)?.let { packetsSent = it.toLong() }
                    }
                }
                "remote-inbound-rtp" -> {
                    val kind = stats.kind as? String
                    if (kind == "audio") {
                        (stats.roundTripTime as? Number)?.let { roundTripTime = it.toDouble() * 1000 }
                        (stats.jitter as? Number)?.let { jitter = it.toDouble() * 1000 }
                        (stats.packetsLost as? Number)?.let { packetsLost = it.toLong() }
                    }
                }
                "codec" -> {
                    val mimeType = stats.mimeType as? String
                    if (mimeType?.contains("audio") == true) {
                        codec = mimeType.substringAfter("audio/").lowercase()
                    }
                }
            }
        }

        return WebRTCStats(
            audioBitrate = audioBitrate,
            roundTripTimeMs = roundTripTime,
            jitterMs = jitter,
            packetsSent = packetsSent,
            packetsLost = packetsLost,
            codec = codec,
            timestampMs = timestampMs
        )
    }

    actual fun createDataChannel(config: com.syncrobotic.webrtc.datachannel.DataChannelConfig): com.syncrobotic.webrtc.datachannel.DataChannel? {
        val pc = peerConnection ?: return null
        
        val init = js("{}")
        init.ordered = config.ordered
        config.maxRetransmits?.let { init.maxRetransmits = it }
        config.maxPacketLifeTimeMs?.let { init.maxPacketLifeTime = it }
        if (config.protocol.isNotEmpty()) init.protocol = config.protocol
        init.negotiated = config.negotiated
        config.id?.let { init.id = it }
        
        return try {
            val nativeChannel: dynamic = pc.createDataChannel(config.label, init)
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
        localAudioTrack?.stop()
        localAudioTrack = null
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
        actual fun isSupported(): Boolean {
            return js("typeof RTCPeerConnection !== 'undefined'") as Boolean
        }
    }
}
