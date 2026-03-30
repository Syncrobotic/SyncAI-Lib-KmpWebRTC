package com.syncrobotic.webrtc.signaling

import com.syncrobotic.webrtc.config.IceServer
import io.ktor.client.*
import io.ktor.client.plugins.cookies.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*

/**
 * WHEP (WebRTC-HTTP Egress Protocol) signaling adapter for **receiving** streams.
 *
 * Wraps the standard WHEP HTTP flow (POST offer → 201 answer, PATCH ICE, DELETE teardown)
 * with pluggable [SignalingAuth] support.
 *
 * ```kotlin
 * val adapter = WhepSignalingAdapter(
 *     url = "https://server/stream/whep",
 *     auth = SignalingAuth.Bearer("my-jwt-token")
 * )
 * val result = adapter.sendOffer(localSdp)
 * ```
 *
 * @param url WHEP endpoint URL
 * @param auth Authentication configuration (default: [SignalingAuth.None])
 * @param httpClient Optional pre-configured [HttpClient]. When `null`, one is created
 *   automatically using the default platform engine.
 */
class WhepSignalingAdapter(
    private val url: String,
    private val auth: SignalingAuth = SignalingAuth.None,
    httpClient: HttpClient? = null
) : SignalingAdapter {

    private val client: HttpClient = httpClient?.withAuth(auth) ?: createDefaultClient(auth)
    private val ownsClient = httpClient == null

    // ── SignalingAdapter ────────────────────────────────────────────────

    override suspend fun sendOffer(sdpOffer: String): SignalingResult {
        try {
            val response = client.post(url) {
                contentType(ContentType("application", "sdp"))
                applyAuth(auth)
                setBody(sdpOffer)
            }

            if (response.status != HttpStatusCode.Created && response.status != HttpStatusCode.OK) {
                throw WhepException(
                    "WHEP offer failed with status ${response.status.value}: ${response.bodyAsText()}"
                )
            }

            val sdpAnswer = response.bodyAsText()
            val resourceUrl = resolveResourceUrl(url, response.headers[HttpHeaders.Location])
            val etag = response.headers[HttpHeaders.ETag]
            val iceServers = parseIceServerLinks(response.headers)

            return SignalingResult(
                sdpAnswer = sdpAnswer,
                resourceUrl = resourceUrl,
                etag = etag,
                iceServers = iceServers
            )
        } catch (e: WhepException) {
            throw e
        } catch (e: Exception) {
            throw WhepException("Failed to send WHEP offer: ${e.message}", e)
        }
    }

    override suspend fun sendIceCandidate(
        resourceUrl: String,
        candidate: String,
        sdpMid: String?,
        sdpMLineIndex: Int,
        iceUfrag: String?,
        icePwd: String?
    ) {
        try {
            val sdpFragment = buildSdpFragment(candidate, iceUfrag, icePwd, sdpMid)

            val response = client.patch(resourceUrl) {
                contentType(ContentType("application", "trickle-ice-sdpfrag"))
                applyAuth(auth)
                setBody(sdpFragment)
            }

            if (response.status != HttpStatusCode.NoContent &&
                response.status != HttpStatusCode.OK
            ) {
                throw WhepException(
                    "Failed to send ICE candidate: ${response.status.value}: ${response.bodyAsText()}"
                )
            }
        } catch (e: WhepException) {
            throw e
        } catch (e: Exception) {
            throw WhepException("Failed to send ICE candidate: ${e.message}", e)
        }
    }

    override suspend fun terminate(resourceUrl: String) {
        try {
            client.delete(resourceUrl) {
                applyAuth(auth)
            }
        } catch (_: Exception) {
            // Ignore errors on teardown
        }
    }
}

/**
 * WHIP (WebRTC-HTTP Ingress Protocol) signaling adapter for **sending** streams.
 *
 * Same HTTP flow as WHEP but semantically used for ingress (audio/video push).
 *
 * ```kotlin
 * val adapter = WhipSignalingAdapter(
 *     url = "https://server/stream/whip",
 *     auth = SignalingAuth.Custom(mapOf("X-Api-Key" to "secret"))
 * )
 * val result = adapter.sendOffer(localSdp)
 * ```
 *
 * @param url WHIP endpoint URL
 * @param auth Authentication configuration (default: [SignalingAuth.None])
 * @param httpClient Optional pre-configured [HttpClient]
 */
class WhipSignalingAdapter(
    private val url: String,
    private val auth: SignalingAuth = SignalingAuth.None,
    httpClient: HttpClient? = null
) : SignalingAdapter {

    private val client: HttpClient = httpClient?.withAuth(auth) ?: createDefaultClient(auth)
    private val ownsClient = httpClient == null

    // ── SignalingAdapter ────────────────────────────────────────────────

    override suspend fun sendOffer(sdpOffer: String): SignalingResult {
        try {
            val response = client.post(url) {
                contentType(ContentType("application", "sdp"))
                applyAuth(auth)
                setBody(sdpOffer)
            }

            if (response.status != HttpStatusCode.Created && response.status != HttpStatusCode.OK) {
                throw WhipException(
                    code = WhipErrorCode.OFFER_REJECTED,
                    message = "WHIP offer failed with status ${response.status.value}: ${response.bodyAsText()}"
                )
            }

            val sdpAnswer = response.bodyAsText()
            val resourceUrl = resolveResourceUrl(url, response.headers[HttpHeaders.Location])
            val etag = response.headers[HttpHeaders.ETag]
            val iceServers = parseIceServerLinks(response.headers)

            return SignalingResult(
                sdpAnswer = sdpAnswer,
                resourceUrl = resourceUrl,
                etag = etag,
                iceServers = iceServers
            )
        } catch (e: WhipException) {
            throw e
        } catch (e: Exception) {
            throw WhipException(
                code = WhipErrorCode.NETWORK_ERROR,
                message = "Failed to send WHIP offer: ${e.message}",
                cause = e
            )
        }
    }

    override suspend fun sendIceCandidate(
        resourceUrl: String,
        candidate: String,
        sdpMid: String?,
        sdpMLineIndex: Int,
        iceUfrag: String?,
        icePwd: String?
    ) {
        try {
            val sdpFragment = buildSdpFragment(candidate, iceUfrag, icePwd, sdpMid)

            val response = client.patch(resourceUrl) {
                contentType(ContentType("application", "trickle-ice-sdpfrag"))
                applyAuth(auth)
                setBody(sdpFragment)
            }

            if (response.status != HttpStatusCode.NoContent &&
                response.status != HttpStatusCode.OK
            ) {
                throw WhipException(
                    code = WhipErrorCode.ICE_CANDIDATE_FAILED,
                    message = "Failed to send ICE candidate: ${response.status.value}: ${response.bodyAsText()}"
                )
            }
        } catch (e: WhipException) {
            throw e
        } catch (e: Exception) {
            throw WhipException(
                code = WhipErrorCode.NETWORK_ERROR,
                message = "Failed to send ICE candidate: ${e.message}",
                cause = e
            )
        }
    }

    override suspend fun terminate(resourceUrl: String) {
        try {
            client.delete(resourceUrl) {
                applyAuth(auth)
            }
        } catch (_: Exception) {
            // Ignore errors on teardown
        }
    }
}

// ── Internal helpers ────────────────────────────────────────────────────

/**
 * Resolve a potentially relative Location header to an absolute URL.
 */
internal fun resolveResourceUrl(baseUrl: String, location: String?): String? {
    if (location.isNullOrBlank()) return null
    if (location.startsWith("http://") || location.startsWith("https://")) return location

    val base = Url(baseUrl)
    return URLBuilder(base).apply { encodedPath = location }.buildString()
}

/**
 * Build an SDP fragment for a trickle ICE PATCH request.
 */
internal fun buildSdpFragment(
    candidate: String,
    iceUfrag: String?,
    icePwd: String?,
    mid: String?
): String {
    val lines = mutableListOf<String>()
    if (!iceUfrag.isNullOrBlank()) lines.add("a=ice-ufrag:$iceUfrag")
    if (!icePwd.isNullOrBlank()) lines.add("a=ice-pwd:$icePwd")
    if (!mid.isNullOrBlank()) lines.add("a=mid:$mid")
    lines.add(candidate.trimEnd())
    return lines.joinToString("\r\n", postfix = "\r\n")
}

/**
 * Parse `Link` headers with `rel="ice-server"` into [IceServer] instances.
 */
internal fun parseIceServerLinks(headers: Headers): List<IceServer> {
    val linkHeaders = headers.getAll(HttpHeaders.Link) ?: return emptyList()
    return linkHeaders
        .filter { it.contains("rel=\"ice-server\"") }
        .mapNotNull { link ->
            // Extract URL from angle brackets: <stun:stun.example.com>
            val urlMatch = Regex("<([^>]+)>").find(link)
            urlMatch?.groupValues?.get(1)?.let { IceServer(urls = listOf(it)) }
        }
}

/**
 * Apply per-request authentication headers.
 * [SignalingAuth.CookieStorage] is handled at the client level via plugin, not per-request.
 */
internal fun HttpRequestBuilder.applyAuth(auth: SignalingAuth) {
    when (auth) {
        is SignalingAuth.None -> { /* no-op */ }
        is SignalingAuth.Bearer -> header(HttpHeaders.Authorization, "Bearer ${auth.token}")
        is SignalingAuth.Cookies -> {
            val cookieString = auth.cookies.entries.joinToString("; ") { "${it.key}=${it.value}" }
            header(HttpHeaders.Cookie, cookieString)
        }
        is SignalingAuth.CookieStorage -> { /* handled by HttpCookies plugin on client */ }
        is SignalingAuth.Custom -> auth.headers.forEach { (k, v) -> header(k, v) }
    }
}

/**
 * Create a default [HttpClient] with optional [HttpCookies] plugin for [SignalingAuth.CookieStorage].
 */
internal fun createDefaultClient(auth: SignalingAuth): HttpClient {
    return HttpClient {
        if (auth is SignalingAuth.CookieStorage) {
            install(HttpCookies) {
                storage = auth.storage
            }
        }
    }
}

/**
 * Wrap an existing [HttpClient] to install [HttpCookies] if needed for [SignalingAuth.CookieStorage].
 * For other auth types, returns the client as-is.
 */
internal fun HttpClient.withAuth(auth: SignalingAuth): HttpClient {
    return if (auth is SignalingAuth.CookieStorage) {
        this.config {
            install(HttpCookies) {
                storage = auth.storage
            }
        }
    } else {
        this
    }
}
