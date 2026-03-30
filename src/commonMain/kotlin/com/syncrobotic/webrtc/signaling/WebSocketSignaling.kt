package com.syncrobotic.webrtc.signaling

import com.syncrobotic.webrtc.config.RetryConfig
import com.syncrobotic.webrtc.config.StreamRetryHandler
import com.syncrobotic.webrtc.config.WebSocketSignalingConfig
import io.ktor.client.*
import io.ktor.client.plugins.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * WebSocket signaling client for custom backends.
 * 
 * Protocol:
 * 1. Connect to wss://host:port/signaling or wss://host:port/signaling/{stream}
 * 2. Receive: {"type": "welcome", "clientId": "...", "stream": "..."}
 * 3. Send: {"type": "offer", "sdp": "...", "stream": "raw"}
 * 4. Receive: {"type": "answer", "sdp": "...", "stream": "raw", "resourceUrl": "..."}
 */
@Deprecated(
    message = "Use SignalingAdapter interface with a custom implementation. Built-in WebSocketSignalingAdapter planned for v2.1. Will be removed in v3.0."
)
class WebSocketSignaling(
    private val httpClient: HttpClient,
    private val config: WebSocketSignalingConfig,
    private val retryConfig: RetryConfig = RetryConfig.DEFAULT
) {
    private var session: WebSocketSession? = null
    private var receiveJob: Job? = null
    private var heartbeatJob: Job? = null
    
    private var lastStreamName: String? = null
    
    private val _connectionState = MutableStateFlow(WebSocketState.DISCONNECTED)
    val connectionState: StateFlow<WebSocketState> = _connectionState.asStateFlow()
    
    private val _messages = MutableSharedFlow<SignalingMessage>(replay = 0)
    val messages: SharedFlow<SignalingMessage> = _messages.asSharedFlow()
    
    private val answerChannel = Channel<Result<AnswerMessage>>(Channel.CONFLATED)
    
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
    
    var clientId: String? = null
        private set
    
    /**
     * Connect to the WebSocket signaling server.
     */
    suspend fun connect(streamName: String? = null) {
        if (_connectionState.value == WebSocketState.CONNECTED) {
            return
        }
        
        lastStreamName = streamName
        _connectionState.value = WebSocketState.CONNECTING
        
        try {
            val url = buildUrl(streamName)
            
            session = httpClient.webSocketSession(url) {
                config.authToken?.let { token ->
                    url {
                        parameters.append("token", token)
                    }
                }
            }
            
            _connectionState.value = WebSocketState.CONNECTED
            startReceiving()
            startHeartbeat()
            
        } catch (e: Exception) {
            _connectionState.value = WebSocketState.DISCONNECTED
            throw WebSocketSignalingException("Failed to connect: ${e.message}", e)
        }
    }
    
    private fun buildUrl(streamName: String?): String {
        val baseUrl = config.url
        return if (streamName != null) {
            if (baseUrl.endsWith("/")) {
                "$baseUrl$streamName"
            } else {
                "$baseUrl/$streamName"
            }
        } else {
            baseUrl
        }
    }
    
    private fun startReceiving() {
        receiveJob = CoroutineScope(Dispatchers.Default).launch {
            try {
                session?.let { ws ->
                    for (frame in ws.incoming) {
                        when (frame) {
                            is Frame.Text -> {
                                handleTextMessage(frame.readText())
                            }
                            is Frame.Close -> {
                                _connectionState.value = WebSocketState.DISCONNECTED
                                if (config.reconnectOnFailure) {
                                    attemptReconnect("WebSocket closed unexpectedly")
                                }
                                break
                            }
                            else -> { }
                        }
                    }
                }
            } catch (e: CancellationException) {
                // Normal cancellation
            } catch (e: Exception) {
                _connectionState.value = WebSocketState.DISCONNECTED
                _messages.emit(SignalingMessage.Error("Connection error: ${e.message}"))
                if (config.reconnectOnFailure) {
                    attemptReconnect("Connection error: ${e.message}")
                }
            }
        }
    }
    
    private suspend fun handleTextMessage(text: String) {
        try {
            val baseMessage = json.decodeFromString<BaseMessage>(text)
            
            val message: SignalingMessage = when (baseMessage.type.lowercase()) {
                "welcome" -> {
                    val welcome = json.decodeFromString<WelcomeMessage>(text)
                    clientId = welcome.clientId
                    SignalingMessage.Welcome(welcome.clientId, welcome.stream)
                }
                "answer" -> {
                    val answer = json.decodeFromString<AnswerMessage>(text)
                    answerChannel.send(Result.success(answer))
                    SignalingMessage.Answer(answer.sdp, answer.stream, answer.resourceUrl)
                }
                "publish_answer" -> {
                    val answer = json.decodeFromString<AnswerMessage>(text)
                    answerChannel.send(Result.success(answer))
                    SignalingMessage.PublishAnswer(answer.sdp, answer.stream, answer.resourceUrl)
                }
                "ice_ack" -> {
                    SignalingMessage.IceAck
                }
                "pong" -> {
                    SignalingMessage.Pong
                }
                "error" -> {
                    val error = json.decodeFromString<ErrorMessage>(text)
                    answerChannel.send(Result.failure(WebSocketSignalingException("Signaling error: ${error.message}")))
                    SignalingMessage.Error(error.message)
                }
                else -> {
                    SignalingMessage.Unknown(text)
                }
            }
            
            _messages.emit(message)
            
        } catch (e: Exception) {
            _messages.emit(SignalingMessage.Error("Failed to parse message: ${e.message}"))
        }
    }
    
    private fun startHeartbeat() {
        if (config.heartbeatIntervalMs <= 0) return
        
        heartbeatJob = CoroutineScope(Dispatchers.Default).launch {
            while (isActive && _connectionState.value == WebSocketState.CONNECTED) {
                delay(config.heartbeatIntervalMs)
                try {
                    sendPing()
                } catch (e: Exception) {
                    if (config.reconnectOnFailure) {
                        attemptReconnect("Heartbeat failed: ${e.message}")
                    } else {
                        _connectionState.value = WebSocketState.DISCONNECTED
                    }
                }
            }
        }
    }
    
    private suspend fun attemptReconnect(reason: String) {
        if (_connectionState.value == WebSocketState.RECONNECTING) return
        
        _connectionState.value = WebSocketState.RECONNECTING
        
        try {
            try {
                heartbeatJob?.cancel()
                receiveJob?.cancel()
                session?.close(CloseReason(CloseReason.Codes.GOING_AWAY, "Reconnecting"))
                session = null
            } catch (_: Exception) {}
            
            StreamRetryHandler.withRetry(
                config = retryConfig,
                actionName = "WebSocket reconnect ($reason)",
                onAttempt = { _, _, _ ->
                    _connectionState.value = WebSocketState.RECONNECTING
                }
            ) {
                val url = buildUrl(lastStreamName)
                session = httpClient.webSocketSession(url) {
                    config.authToken?.let { token ->
                        url { parameters.append("token", token) }
                    }
                }
                
                _connectionState.value = WebSocketState.CONNECTED
                clientId = null
                startReceiving()
                startHeartbeat()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _connectionState.value = WebSocketState.DISCONNECTED
            CoroutineScope(Dispatchers.Default).launch {
                _messages.emit(SignalingMessage.Error("Reconnection failed after ${retryConfig.maxRetries} attempts: ${e.message}"))
            }
        }
    }
    
    /**
     * Send an SDP offer to receive a stream (WHEP-like).
     */
    suspend fun sendOffer(sdpOffer: String, streamName: String): AnswerResult {
        ensureConnected()
        
        val message = OfferMessage(
            type = "offer",
            sdp = sdpOffer,
            stream = streamName
        )
        
        sendJson(message)
        
        try {
            return withTimeout(30_000) {
                val result = answerChannel.receive()
                val answer = result.getOrElse { throw it }
                AnswerResult(
                    sdpAnswer = answer.sdp,
                    resourceUrl = answer.resourceUrl
                )
            }
        } catch (e: TimeoutCancellationException) {
            throw WebSocketSignalingException("Timed out waiting for SDP answer for stream: $streamName", e)
        }
    }
    
    /**
     * Send an SDP offer to publish a stream (WHIP-like).
     */
    suspend fun sendPublishOffer(sdpOffer: String, streamName: String): AnswerResult {
        ensureConnected()
        
        val message = OfferMessage(
            type = "publish_offer",
            sdp = sdpOffer,
            stream = streamName
        )
        
        sendJson(message)
        
        try {
            return withTimeout(30_000) {
                val result = answerChannel.receive()
                val answer = result.getOrElse { throw it }
                AnswerResult(
                    sdpAnswer = answer.sdp,
                    resourceUrl = answer.resourceUrl
                )
            }
        } catch (e: TimeoutCancellationException) {
            throw WebSocketSignalingException("Timed out waiting for publish answer for stream: $streamName", e)
        }
    }
    
    /**
     * Send an ICE candidate (trickle ICE).
     */
    suspend fun sendIceCandidate(
        candidate: String,
        sdpMid: String?,
        sdpMLineIndex: Int
    ) {
        ensureConnected()
        
        val message = IceCandidateMessage(
            type = "ice_candidate",
            candidate = candidate,
            sdpMid = sdpMid,
            sdpMLineIndex = sdpMLineIndex
        )
        
        sendJson(message)
    }
    
    private suspend fun sendPing() {
        sendJson(PingMessage())
    }
    
    private suspend inline fun <reified T> sendJson(message: T) {
        session?.send(Frame.Text(json.encodeToString(kotlinx.serialization.serializer(), message)))
    }
    
    private fun ensureConnected() {
        if (_connectionState.value != WebSocketState.CONNECTED) {
            throw WebSocketSignalingException("Not connected to signaling server")
        }
    }
    
    /**
     * Disconnect from the signaling server.
     */
    suspend fun disconnect() {
        heartbeatJob?.cancel()
        receiveJob?.cancel()
        session?.close(CloseReason(CloseReason.Codes.NORMAL, "Client disconnect"))
        session = null
        _connectionState.value = WebSocketState.DISCONNECTED
        clientId = null
    }
}

/**
 * WebSocket connection state.
 */
enum class WebSocketState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    RECONNECTING
}

/**
 * Result of sending an offer.
 */
data class AnswerResult(
    val sdpAnswer: String,
    val resourceUrl: String?
)

/**
 * Sealed class for signaling messages.
 */
sealed class SignalingMessage {
    data class Welcome(val clientId: String, val stream: String) : SignalingMessage()
    data class Answer(val sdp: String, val stream: String, val resourceUrl: String?) : SignalingMessage()
    data class PublishAnswer(val sdp: String, val stream: String, val resourceUrl: String?) : SignalingMessage()
    data object IceAck : SignalingMessage()
    data object Pong : SignalingMessage()
    data class Error(val message: String) : SignalingMessage()
    data class Unknown(val rawText: String) : SignalingMessage()
}

/**
 * Exception thrown when WebSocket signaling fails.
 */
class WebSocketSignalingException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)

// JSON Message Data Classes
@Serializable
private data class BaseMessage(val type: String)

@Serializable
private data class WelcomeMessage(
    val type: String,
    val clientId: String,
    val stream: String
)

@Serializable
private data class OfferMessage(
    val type: String,
    val sdp: String,
    val stream: String
)

@Serializable
private data class AnswerMessage(
    val type: String,
    val sdp: String,
    val stream: String,
    val resourceUrl: String? = null
)

@Serializable
private data class IceCandidateMessage(
    val type: String,
    val candidate: String,
    val sdpMid: String?,
    val sdpMLineIndex: Int
)

@Serializable
private data class ErrorMessage(
    val type: String,
    val message: String
)

@Serializable
private data class PingMessage(val type: String = "ping")
