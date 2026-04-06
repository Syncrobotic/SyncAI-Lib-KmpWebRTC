package com.syncrobotic.webrtc.signaling

import com.syncrobotic.webrtc.config.IceServer
import io.ktor.client.*
import io.ktor.client.plugins.cookies.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*

/**
 * Error codes for HTTP signaling failures.
 */
enum class SignalingErrorCode {
    /** Network connectivity issue */
    NETWORK_ERROR,
    /** Server rejected the SDP offer */
    OFFER_REJECTED,
    /** Failed to send ICE candidate */
    ICE_CANDIDATE_FAILED,
    /** Session terminated unexpectedly */
    SESSION_TERMINATED,
    /** Unknown error */
    UNKNOWN
}

/**
 * Exception thrown when HTTP signaling fails.
 *
 * @param code Error code indicating the type of failure
 * @param message Human-readable error message
 * @param cause Underlying exception if any
 */
class SignalingException(
    val code: SignalingErrorCode = SignalingErrorCode.UNKNOWN,
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)

/**
 * Unified HTTP-based signaling adapter for WebRTC SDP exchange.
 *
 * Implements the standard HTTP flow used by both WHEP and WHIP protocols:
 * 1. **POST** SDP offer → receive SDP answer (HTTP 201/200)
 * 2. **PATCH** trickle ICE candidates to the resource URL
 * 3. **DELETE** to tear down the session
 *
 * Since WHEP and WHIP use the exact same HTTP flow (only the endpoint URL differs),
 * this single adapter replaces both `WhepSignalingAdapter` and `WhipSignalingAdapter`.
 *
 * ```kotlin
 * // Receiving (WHEP endpoint)
 * val recv = HttpSignalingAdapter(url = "https://server/stream/whep")
 *
 * // Sending (WHIP endpoint)
 * val send = HttpSignalingAdapter(url = "https://server/stream/whip")
 *
 * // Custom signaling server
 * val custom = HttpSignalingAdapter(
 *     url = "https://my-server/webrtc/offer",
 *     auth = SignalingAuth.Bearer("my-jwt-token")
 * )
 * ```
 *
 * @param url Signaling endpoint URL
 * @param auth Authentication configuration (default: [SignalingAuth.None])
 * @param httpClient Optional pre-configured [HttpClient]. When `null`, one is created
 *   automatically using the default platform engine.
 */
class HttpSignalingAdapter(
    private val url: String,
    private val auth: SignalingAuth = SignalingAuth.None,
    httpClient: HttpClient? = null
) : SignalingAdapter {

    private val client: HttpClient = httpClient?.withAuth(auth) ?: createDefaultClient(auth)

    // ── SignalingAdapter ────────────────────────────────────────────────

    override suspend fun sendOffer(sdpOffer: String): SignalingResult {
        try {
            val response = client.post(url) {
                contentType(ContentType("application", "sdp"))
                applyAuth(auth)
                setBody(sdpOffer)
            }

            if (response.status != HttpStatusCode.Created && response.status != HttpStatusCode.OK) {
                throw SignalingException(
                    code = SignalingErrorCode.OFFER_REJECTED,
                    message = "Signaling offer failed with status ${response.status.value}: ${response.bodyAsText()}"
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
        } catch (e: SignalingException) {
            throw e
        } catch (e: Exception) {
            throw SignalingException(
                code = SignalingErrorCode.NETWORK_ERROR,
                message = "Failed to send signaling offer: ${e.message}",
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
                throw SignalingException(
                    code = SignalingErrorCode.ICE_CANDIDATE_FAILED,
                    message = "Failed to send ICE candidate: ${response.status.value}: ${response.bodyAsText()}"
                )
            }
        } catch (e: SignalingException) {
            throw e
        } catch (e: Exception) {
            throw SignalingException(
                code = SignalingErrorCode.NETWORK_ERROR,
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
            val urlMatch = Regex("<([^>]+)>").find(link)
            urlMatch?.groupValues?.get(1)?.let { IceServer(urls = listOf(it)) }
        }
}

/**
 * Apply per-request authentication headers.
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
 * Create a default [HttpClient] with optional [HttpCookies] plugin.
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
 * Wrap an existing [HttpClient] to install [HttpCookies] if needed.
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
