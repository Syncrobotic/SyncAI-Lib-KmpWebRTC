package com.syncrobotic.webrtc.signaling

import com.syncrobotic.webrtc.config.IceServer
import com.syncrobotic.webrtc.config.WebRtcLog
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
    cause: Throwable? = null,
    /** HTTP status returned by the signaling server, if the failure was an HTTP response. */
    val httpStatus: Int? = null
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
 * a single adapter handles both protocols.
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
        println("${WebRtcLog.ts()} [HttpSignalingAdapter] sendOffer() url=$url, sdpOffer length=${sdpOffer.length}, auth=${auth::class.simpleName}")
        try {
            println("${WebRtcLog.ts()} [HttpSignalingAdapter] POSTing to $url ...")
            val response = client.post(url) {
                contentType(ContentType("application", "sdp"))
                applyAuth(auth)
                setBody(sdpOffer)
            }
            println("${WebRtcLog.ts()} [HttpSignalingAdapter] POST response status=${response.status}")

            if (response.status != HttpStatusCode.Created && response.status != HttpStatusCode.OK) {
                // The body goes to the log only. It is frequently an HTML error page or a
                // server stack trace, and this message ends up in SessionState.Error.
                println("${WebRtcLog.ts()} [HttpSignalingAdapter] POST rejected, body=${response.bodyAsText().truncateForLog()}")
                throw SignalingException(
                    code = SignalingErrorCode.OFFER_REJECTED,
                    message = "Signaling offer rejected with HTTP ${response.status.value}",
                    httpStatus = response.status.value
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
            println("${WebRtcLog.ts()} [HttpSignalingAdapter] sendOffer() exception: ${e::class.simpleName}: ${e.message}")
            e.printStackTrace()
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
                println("${WebRtcLog.ts()} [HttpSignalingAdapter] PATCH rejected, body=${response.bodyAsText().truncateForLog()}")
                throw SignalingException(
                    code = SignalingErrorCode.ICE_CANDIDATE_FAILED,
                    message = "Failed to send ICE candidate: HTTP ${response.status.value}",
                    httpStatus = response.status.value
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
 * Create a default [HttpClient] using the platform-specific engine,
 * with optional [HttpCookies] plugin.
 */
internal fun createDefaultClient(auth: SignalingAuth): HttpClient {
    return createPlatformHttpClient {
        if (auth is SignalingAuth.CookieStorage) {
            install(HttpCookies) {
                storage = auth.storage
            }
        }
    }
}

/**
 * Create an [HttpClient] using the platform-specific engine.
 *
 * Each platform provides the correct engine automatically:
 * - Android: OkHttp
 * - iOS: Darwin
 * - JVM: CIO
 * - JS: Js
 *
 * This prevents engine auto-discovery issues when multiple engines
 * are on the classpath (e.g., CIO being picked on iOS instead of Darwin).
 */
internal expect fun createPlatformHttpClient(
    block: io.ktor.client.HttpClientConfig<*>.() -> Unit = {}
): HttpClient

/** Cap on error-response bodies written to the log. */
private const val LOG_BODY_LIMIT = 512

/**
 * Trim an error response body for logging.
 *
 * Error bodies are often full HTML pages or server stack traces; they belong in
 * the log, bounded, and never in an exception message that reaches the UI.
 */
internal fun String.truncateForLog(): String =
    if (length <= LOG_BODY_LIMIT) this else take(LOG_BODY_LIMIT) + "…(${length} chars)"

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
