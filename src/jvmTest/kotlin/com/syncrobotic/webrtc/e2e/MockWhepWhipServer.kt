package com.syncrobotic.webrtc.e2e

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * In-process mock WHEP/WHIP server for E2E testing.
 *
 * Simulates a media server's signaling endpoints:
 * - POST `/{stream}/whep` — WHEP offer/answer (receive)
 * - POST `/{stream}/whip` — WHIP offer/answer (send)
 * - PATCH `/resource/{id}` — trickle ICE candidates
 * - DELETE `/resource/{id}` — session teardown
 * - WebSocket `/ws/{room}` — P2P signaling relay
 *
 * Does NOT handle actual RTP media — only SDP/ICE signaling.
 */
class MockWhepWhipServer(
    private val port: Int = 0, // 0 = auto-assign
    private val sdpAnswerProvider: SdpAnswerProvider = DefaultSdpAnswerProvider
) {
    private var server: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration>? = null
    private var actualPort: Int = 0

    /** Active sessions: resourceId → SessionInfo */
    val sessions = ConcurrentHashMap<String, SessionInfo>()

    /** Recorded requests for assertion */
    val recordedRequests = Collections.synchronizedList(mutableListOf<RecordedRequest>())

    /** ICE candidates received via PATCH */
    val iceCandidates = ConcurrentHashMap<String, MutableList<String>>()

    /** WebSocket rooms for P2P signaling */
    private val wsRooms = ConcurrentHashMap<String, MutableList<WebSocketSession>>()

    /** Channel for test synchronization — signals when events occur */
    val events = Channel<ServerEvent>(Channel.UNLIMITED)

    /** Configurable response behavior for testing error scenarios */
    var offerResponseOverride: OfferResponseOverride? = null

    val baseUrl: String get() = "http://localhost:$actualPort"

    fun start() {
        server = embeddedServer(Netty, port = port) {
            install(WebSockets)
            routing {
                // WHEP endpoint (receive stream)
                post("/{stream}/whep") { handleOffer(call, "whep") }

                // WHIP endpoint (send stream)
                post("/{stream}/whip") { handleOffer(call, "whip") }

                // Trickle ICE PATCH
                patch("/resource/{id}") { handleIceCandidate(call) }

                // Session teardown DELETE
                delete("/resource/{id}") { handleTerminate(call) }

                // WebSocket signaling for P2P tests
                webSocket("/ws/{room}") { handleWebSocket(call, this) }

                // Health check
                get("/health") { call.respondText("OK") }

                // List active sessions (debug)
                get("/sessions") {
                    call.respondText(sessions.keys.joinToString("\n"))
                }
            }
        }.start(wait = false)

        // Resolve actual port
        actualPort = if (port == 0) {
            runBlocking { server!!.engine.resolvedConnectors().first().port }
        } else {
            port
        }
    }

    fun stop() {
        server?.stop(100, 200)
        server = null
        sessions.clear()
        recordedRequests.clear()
        iceCandidates.clear()
        wsRooms.clear()
        events.close()
    }

    fun reset() {
        sessions.clear()
        recordedRequests.clear()
        iceCandidates.clear()
        offerResponseOverride = null
    }

    // ── WHEP/WHIP Offer Handler ──────────────────────────────────────────

    private suspend fun handleOffer(call: ApplicationCall, protocol: String) {
        val stream = call.parameters["stream"] ?: "default"
        val sdpOffer = call.receiveText()

        recordedRequests.add(
            RecordedRequest(
                method = "POST",
                path = "/$stream/$protocol",
                contentType = call.request.contentType().toString(),
                body = sdpOffer,
                headers = call.request.headers.entries().associate { it.key to it.value }
            )
        )

        // Check for override (error simulation)
        offerResponseOverride?.let { override ->
            call.respond(HttpStatusCode.fromValue(override.statusCode), override.body)
            events.trySend(ServerEvent.OfferRejected(stream, protocol, override.statusCode))
            return
        }

        // Generate SDP answer
        val sdpAnswer = sdpAnswerProvider.createAnswer(sdpOffer, stream, protocol)
        val resourceId = UUID.randomUUID().toString()
        val resourceUrl = "/resource/$resourceId"

        sessions[resourceId] = SessionInfo(
            id = resourceId,
            stream = stream,
            protocol = protocol,
            sdpOffer = sdpOffer,
            sdpAnswer = sdpAnswer
        )
        iceCandidates[resourceId] = Collections.synchronizedList(mutableListOf())

        call.response.header(HttpHeaders.Location, resourceUrl)
        call.response.header(HttpHeaders.ETag, "\"${resourceId.take(8)}\"")
        // Optionally include ICE server link headers
        call.response.header(HttpHeaders.Link, "<stun:stun.l.google.com:19302>; rel=\"ice-server\"")
        call.respondText(sdpAnswer, ContentType("application", "sdp"), HttpStatusCode.Created)

        events.trySend(ServerEvent.OfferReceived(stream, protocol, resourceId))
    }

    // ── ICE Candidate Handler ────────────────────────────────────────────

    private suspend fun handleIceCandidate(call: ApplicationCall) {
        val id = call.parameters["id"] ?: return call.respond(HttpStatusCode.NotFound)

        if (!sessions.containsKey(id)) {
            call.respond(HttpStatusCode.NotFound, "Session not found")
            return
        }

        val body = call.receiveText()
        val contentType = call.request.contentType().toString()

        recordedRequests.add(
            RecordedRequest(
                method = "PATCH",
                path = "/resource/$id",
                contentType = contentType,
                body = body,
                headers = call.request.headers.entries().associate { it.key to it.value }
            )
        )

        iceCandidates[id]?.add(body)
        call.respond(HttpStatusCode.NoContent)

        events.trySend(ServerEvent.IceCandidateReceived(id, body))
    }

    // ── Session Teardown Handler ─────────────────────────────────────────

    private suspend fun handleTerminate(call: ApplicationCall) {
        val id = call.parameters["id"] ?: return call.respond(HttpStatusCode.NotFound)

        recordedRequests.add(
            RecordedRequest(
                method = "DELETE",
                path = "/resource/$id",
                contentType = "",
                body = "",
                headers = call.request.headers.entries().associate { it.key to it.value }
            )
        )

        sessions.remove(id)
        iceCandidates.remove(id)
        call.respond(HttpStatusCode.OK)

        events.trySend(ServerEvent.SessionTerminated(id))
    }

    // ── WebSocket Signaling ──────────────────────────────────────────────

    private suspend fun handleWebSocket(call: ApplicationCall, session: WebSocketSession) {
        val room = call.parameters["room"] ?: "default"
        val roomSessions = wsRooms.getOrPut(room) { Collections.synchronizedList(mutableListOf()) }
        roomSessions.add(session)

        events.trySend(ServerEvent.WebSocketConnected(room))

        try {
            for (frame in session.incoming) {
                if (frame is Frame.Text) {
                    val text = frame.readText()
                    events.trySend(ServerEvent.WebSocketMessage(room, text))
                    // Relay to all other peers in the room
                    roomSessions.filter { it != session }.forEach { peer ->
                        try { peer.send(text) } catch (_: Exception) {}
                    }
                }
            }
        } finally {
            roomSessions.remove(session)
            events.trySend(ServerEvent.WebSocketDisconnected(room))
        }
    }
}

// ── Data Classes ─────────────────────────────────────────────────────────

data class SessionInfo(
    val id: String,
    val stream: String,
    val protocol: String,
    val sdpOffer: String,
    val sdpAnswer: String
)

data class RecordedRequest(
    val method: String,
    val path: String,
    val contentType: String,
    val body: String,
    val headers: Map<String, List<String>>
)

data class OfferResponseOverride(
    val statusCode: Int,
    val body: String = ""
)

sealed class ServerEvent {
    data class OfferReceived(val stream: String, val protocol: String, val resourceId: String) : ServerEvent()
    data class OfferRejected(val stream: String, val protocol: String, val statusCode: Int) : ServerEvent()
    data class IceCandidateReceived(val resourceId: String, val body: String) : ServerEvent()
    data class SessionTerminated(val resourceId: String) : ServerEvent()
    data class WebSocketConnected(val room: String) : ServerEvent()
    data class WebSocketMessage(val room: String, val message: String) : ServerEvent()
    data class WebSocketDisconnected(val room: String) : ServerEvent()
}

// ── SDP Answer Provider ──────────────────────────────────────────────────

/**
 * Strategy for generating SDP answers from offers.
 */
fun interface SdpAnswerProvider {
    fun createAnswer(sdpOffer: String, stream: String, protocol: String): String
}

/**
 * Default SDP answer provider that generates a minimal but valid SDP answer.
 *
 * For WHEP (receive): answer includes recvonly ↔ sendonly mapping
 * For WHIP (send): answer includes sendonly ↔ recvonly mapping
 */
object DefaultSdpAnswerProvider : SdpAnswerProvider {
    override fun createAnswer(sdpOffer: String, stream: String, protocol: String): String {
        // Parse offer to determine media lines
        val lines = sdpOffer.lines()
        val answerLines = mutableListOf<String>()

        answerLines.add("v=0")
        answerLines.add("o=- ${System.currentTimeMillis()} 2 IN IP4 127.0.0.1")
        answerLines.add("s=-")
        answerLines.add("t=0 0")

        // Track bundle groups
        val mids = mutableListOf<String>()
        var currentMid = ""

        for (line in lines) {
            when {
                line.startsWith("m=audio") -> {
                    // Mirror the audio media section
                    answerLines.add("m=audio 9 UDP/TLS/RTP/SAVPF 111")
                    answerLines.add("c=IN IP4 0.0.0.0")
                    currentMid = "0"
                    mids.add(currentMid)
                    answerLines.add("a=mid:$currentMid")
                    answerLines.add("a=rtpmap:111 opus/48000/2")

                    // Flip direction
                    val direction = extractDirection(lines, lines.indexOf(line))
                    answerLines.add("a=${flipDirection(direction)}")
                    answerLines.add("a=rtcp-mux")

                    // Add ICE credentials for the answer
                    answerLines.add("a=ice-ufrag:mock")
                    answerLines.add("a=ice-pwd:mockpassword1234567890ab")
                    answerLines.add("a=fingerprint:sha-256 00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00")
                    answerLines.add("a=setup:active")
                }
                line.startsWith("m=video") -> {
                    answerLines.add("m=video 9 UDP/TLS/RTP/SAVPF 96")
                    answerLines.add("c=IN IP4 0.0.0.0")
                    currentMid = "1"
                    mids.add(currentMid)
                    answerLines.add("a=mid:$currentMid")
                    answerLines.add("a=rtpmap:96 VP8/90000")

                    val direction = extractDirection(lines, lines.indexOf(line))
                    answerLines.add("a=${flipDirection(direction)}")
                    answerLines.add("a=rtcp-mux")

                    answerLines.add("a=ice-ufrag:mock")
                    answerLines.add("a=ice-pwd:mockpassword1234567890ab")
                    answerLines.add("a=fingerprint:sha-256 00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00")
                    answerLines.add("a=setup:active")
                }
                line.startsWith("m=application") -> {
                    // DataChannel support
                    answerLines.add("m=application 9 UDP/DTLS/SCTP webrtc-datachannel")
                    answerLines.add("c=IN IP4 0.0.0.0")
                    currentMid = "2"
                    mids.add(currentMid)
                    answerLines.add("a=mid:$currentMid")
                    answerLines.add("a=sctp-port:5000")

                    answerLines.add("a=ice-ufrag:mock")
                    answerLines.add("a=ice-pwd:mockpassword1234567890ab")
                    answerLines.add("a=fingerprint:sha-256 00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00")
                    answerLines.add("a=setup:active")
                }
            }
        }

        // Add bundle group if multiple media sections
        if (mids.isNotEmpty()) {
            answerLines.add(2, "a=group:BUNDLE ${mids.joinToString(" ")}")
        }

        return answerLines.joinToString("\r\n", postfix = "\r\n")
    }

    private fun extractDirection(lines: List<String>, mediaLineIndex: Int): String {
        for (i in (mediaLineIndex + 1) until lines.size) {
            val l = lines[i]
            if (l.startsWith("m=")) break
            when (l.trim()) {
                "a=sendonly" -> return "sendonly"
                "a=recvonly" -> return "recvonly"
                "a=sendrecv" -> return "sendrecv"
                "a=inactive" -> return "inactive"
            }
        }
        return "sendrecv" // default
    }

    private fun flipDirection(direction: String): String = when (direction) {
        "sendonly" -> "recvonly"
        "recvonly" -> "sendonly"
        "sendrecv" -> "sendrecv"
        "inactive" -> "inactive"
        else -> "sendrecv"
    }
}
