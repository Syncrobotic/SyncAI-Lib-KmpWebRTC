package com.syncrobotic.webrtc.level3.infra

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.runBlocking
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * BE Signaling Proxy Server for Level 3 testing.
 *
 * Acts as a backend that only forwards SDP (no media handling).
 * App -> this proxy -> upstream IoT (MediaMTX/Pion).
 *
 * Endpoints:
 * - POST /api/v1/devices/{deviceId}/offer  — Forward SDP offer to upstream
 * - PATCH /api/v1/sessions/{sessionId}/ice  — Forward ICE candidates
 * - DELETE /api/v1/sessions/{sessionId}     — Forward teardown
 * - GET /health                              — Health check
 *
 * Test controls:
 * - registerDevice(id, upstreamUrl)
 * - setDeviceOffline(id) / setDeviceOnline(id)
 * - setJwtValidation(enabled, secret)
 *
 * Covers: S-2, S-5
 */
class SignalingProxyServer(
    private val port: Int = 0
) {
    private var server: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration>? = null
    private var actualPort: Int = 0
    private val httpClient = HttpClient(CIO)

    /** Registered devices: deviceId -> DeviceInfo */
    private val devices = ConcurrentHashMap<String, DeviceInfo>()

    /** Active proxy sessions: sessionId -> ProxySession */
    private val proxySessions = ConcurrentHashMap<String, ProxySession>()

    /** Recorded requests for assertions */
    val recordedRequests: MutableList<ProxyRequest> =
        Collections.synchronizedList(mutableListOf())

    /** JWT validation settings */
    private var jwtEnabled = false
    private var jwtSecret = "test-secret"
    private var validTokens = mutableSetOf<String>()

    val baseUrl: String get() = "http://localhost:$actualPort"

    // ── Test Control Methods ────────────────────────────────────────

    fun registerDevice(deviceId: String, upstreamWhepUrl: String, upstreamWhipUrl: String? = null) {
        devices[deviceId] = DeviceInfo(
            id = deviceId,
            whepUrl = upstreamWhepUrl,
            whipUrl = upstreamWhipUrl ?: upstreamWhepUrl.replace("/whep", "/whip"),
            online = true
        )
    }

    fun setDeviceOffline(deviceId: String) {
        devices[deviceId]?.let { devices[deviceId] = it.copy(online = false) }
    }

    fun setDeviceOnline(deviceId: String) {
        devices[deviceId]?.let { devices[deviceId] = it.copy(online = true) }
    }

    fun setJwtValidation(enabled: Boolean, vararg tokens: String) {
        jwtEnabled = enabled
        validTokens.clear()
        validTokens.addAll(tokens)
    }

    // ── Server Lifecycle ────────────────────────────────────────────

    fun start() {
        server = embeddedServer(Netty, port = port) {
            routing {
                // Forward SDP offer to upstream device
                post("/api/v1/devices/{deviceId}/offer") { handleOffer(call) }

                // Forward ICE candidates
                patch("/api/v1/sessions/{sessionId}/ice") { handleIce(call) }

                // Forward teardown
                delete("/api/v1/sessions/{sessionId}") { handleDelete(call) }

                // Health check
                get("/health") { call.respondText("OK") }

                // List devices (debug)
                get("/api/v1/devices") {
                    val list = devices.values.joinToString("\n") {
                        "${it.id}: online=${it.online}, whep=${it.whepUrl}"
                    }
                    call.respondText(list)
                }
            }
        }.start(wait = false)

        actualPort = if (port == 0) {
            runBlocking { server!!.engine.resolvedConnectors().first().port }
        } else {
            port
        }
    }

    fun stop() {
        server?.stop(100, 200)
        server = null
        httpClient.close()
        proxySessions.clear()
        recordedRequests.clear()
    }

    // ── Request Handlers ────────────────────────────────────────────

    private suspend fun handleOffer(call: ApplicationCall) {
        val deviceId = call.parameters["deviceId"] ?: run {
            call.respond(HttpStatusCode.BadRequest, "Missing deviceId")
            return
        }

        recordedRequests.add(ProxyRequest("POST", "/api/v1/devices/$deviceId/offer"))

        // JWT validation
        if (jwtEnabled) {
            val authHeader = call.request.header(HttpHeaders.Authorization)
            val token = authHeader?.removePrefix("Bearer ")?.trim()
            if (token == null || token !in validTokens) {
                call.respond(HttpStatusCode.Unauthorized, "Invalid or missing token")
                return
            }
        }

        // Find device
        val device = devices[deviceId]
        if (device == null) {
            call.respond(HttpStatusCode.NotFound, "Device not found: $deviceId")
            return
        }

        // Check online status
        if (!device.online) {
            call.respond(HttpStatusCode.BadGateway, "Device offline: $deviceId")
            return
        }

        val sdpOffer = call.receiveText()
        val contentType = call.request.contentType().toString()

        // Determine upstream URL based on SDP direction
        val upstreamUrl = if (sdpOffer.contains("a=sendonly") || sdpOffer.contains("a=sendrecv")) {
            device.whipUrl
        } else {
            device.whepUrl
        }

        // Forward SDP to upstream
        try {
            val response = httpClient.post(upstreamUrl) {
                setBody(sdpOffer)
                contentType(ContentType("application", "sdp"))
            }

            if (response.status.value !in 200..201) {
                call.respond(
                    HttpStatusCode.BadGateway,
                    "Upstream returned ${response.status.value}"
                )
                return
            }

            val sdpAnswer = response.bodyAsText()
            val upstreamLocation = response.headers[HttpHeaders.Location]
            val sessionId = UUID.randomUUID().toString()

            // Store proxy session for ICE/DELETE forwarding
            proxySessions[sessionId] = ProxySession(
                id = sessionId,
                deviceId = deviceId,
                upstreamResourceUrl = upstreamLocation,
                upstreamBaseUrl = upstreamUrl.substringBefore("/${device.id.substringAfterLast("/")}")
            )

            call.response.header(HttpHeaders.Location, "/api/v1/sessions/$sessionId")
            call.response.header(HttpHeaders.ETag, response.headers[HttpHeaders.ETag] ?: "")

            // Forward ICE server links if present
            response.headers[HttpHeaders.Link]?.let {
                call.response.header(HttpHeaders.Link, it)
            }

            call.respondText(sdpAnswer, ContentType("application", "sdp"), HttpStatusCode.Created)

        } catch (e: Exception) {
            call.respond(HttpStatusCode.BadGateway, "Failed to reach upstream: ${e.message}")
        }
    }

    private suspend fun handleIce(call: ApplicationCall) {
        val sessionId = call.parameters["sessionId"] ?: run {
            call.respond(HttpStatusCode.BadRequest)
            return
        }

        recordedRequests.add(ProxyRequest("PATCH", "/api/v1/sessions/$sessionId/ice"))

        val proxySession = proxySessions[sessionId]
        if (proxySession == null) {
            call.respond(HttpStatusCode.NotFound, "Session not found")
            return
        }

        val body = call.receiveText()
        val upstreamUrl = proxySession.upstreamResourceUrl

        if (upstreamUrl != null) {
            try {
                // Resolve relative URL against upstream base
                val fullUrl = if (upstreamUrl.startsWith("http")) {
                    upstreamUrl
                } else {
                    proxySession.upstreamBaseUrl + upstreamUrl
                }

                httpClient.patch(fullUrl) {
                    setBody(body)
                    contentType(ContentType("application", "trickle-ice-sdpfrag"))
                }
            } catch (e: Exception) {
                // Log but don't fail — ICE candidates are best-effort
            }
        }

        call.respond(HttpStatusCode.NoContent)
    }

    private suspend fun handleDelete(call: ApplicationCall) {
        val sessionId = call.parameters["sessionId"] ?: run {
            call.respond(HttpStatusCode.BadRequest)
            return
        }

        recordedRequests.add(ProxyRequest("DELETE", "/api/v1/sessions/$sessionId"))

        val proxySession = proxySessions.remove(sessionId)
        if (proxySession?.upstreamResourceUrl != null) {
            try {
                val fullUrl = if (proxySession.upstreamResourceUrl.startsWith("http")) {
                    proxySession.upstreamResourceUrl
                } else {
                    proxySession.upstreamBaseUrl + proxySession.upstreamResourceUrl
                }
                httpClient.delete(fullUrl)
            } catch (_: Exception) {
                // Ignore teardown errors
            }
        }

        call.respond(HttpStatusCode.OK)
    }
}

// ── Data Classes ────────────────────────────────────────────────────

data class DeviceInfo(
    val id: String,
    val whepUrl: String,
    val whipUrl: String,
    val online: Boolean = true
)

data class ProxySession(
    val id: String,
    val deviceId: String,
    val upstreamResourceUrl: String?,
    val upstreamBaseUrl: String
)

data class ProxyRequest(
    val method: String,
    val path: String,
    val timestamp: Long = System.currentTimeMillis()
)
