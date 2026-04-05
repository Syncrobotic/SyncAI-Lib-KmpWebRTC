package com.syncrobotic.webrtc.signaling

import com.syncrobotic.webrtc.config.IceServer
import io.ktor.client.plugins.cookies.*

/**
 * Unified signaling adapter interface for WebRTC SDP exchange.
 *
 * Implementations handle the transport-specific details (HTTP WHEP/WHIP, WebSocket, etc.)
 * while Session classes consume this interface to manage PeerConnection lifecycle.
 *
 * Built-in adapters:
 * - [HttpSignalingAdapter] — Unified HTTP signaling (WHEP/WHIP/custom endpoints)
 * - [WhepSignalingAdapter] — WHEP HTTP signaling for receiving streams (deprecated, use [HttpSignalingAdapter])
 * - [WhipSignalingAdapter] — WHIP HTTP signaling for sending streams (deprecated, use [HttpSignalingAdapter])
 *
 * Custom adapters can implement this interface for proprietary signaling servers.
 */
interface SignalingAdapter {

    /**
     * Send an SDP offer and receive the server's SDP answer.
     *
     * @param sdpOffer Local SDP offer string
     * @return [SignalingResult] containing the SDP answer and session metadata
     */
    suspend fun sendOffer(sdpOffer: String): SignalingResult

    /**
     * Send a trickle ICE candidate to the remote peer.
     *
     * @param resourceUrl Session resource URL returned from [sendOffer]
     * @param candidate ICE candidate string (e.g. "a=candidate:...")
     * @param sdpMid Media stream identification tag
     * @param sdpMLineIndex Index of the media description
     * @param iceUfrag ICE username fragment (optional, extracted from SDP)
     * @param icePwd ICE password (optional, extracted from SDP)
     */
    suspend fun sendIceCandidate(
        resourceUrl: String,
        candidate: String,
        sdpMid: String? = null,
        sdpMLineIndex: Int = 0,
        iceUfrag: String? = null,
        icePwd: String? = null
    )

    /**
     * Terminate the signaling session and release server-side resources.
     *
     * @param resourceUrl Session resource URL returned from [sendOffer]
     */
    suspend fun terminate(resourceUrl: String)
}

/**
 * Result of an SDP offer/answer exchange.
 *
 * Replaces the former `WhepSignaling.SessionResult`, `WhipSignaling.SessionResult`,
 * and `AnswerResult` with a single unified type.
 */
data class SignalingResult(
    /** SDP answer from the remote peer */
    val sdpAnswer: String,
    /** Session resource URL for ICE trickling and teardown (WHEP/WHIP Location header) */
    val resourceUrl: String? = null,
    /** ETag for conditional PATCH requests */
    val etag: String? = null,
    /** ICE servers advertised by the signaling endpoint */
    val iceServers: List<IceServer> = emptyList()
)

/**
 * Authentication configuration for signaling adapters.
 *
 * Each variant controls how HTTP requests are authenticated:
 *
 * | Type | Behavior |
 * |------|----------|
 * | [None] | No authentication headers |
 * | [Bearer] | `Authorization: Bearer <token>` |
 * | [Cookies] | Manual `Cookie` header from key-value map |
 * | [CookieStorage] | Ktor `HttpCookies` plugin with shared cookie jar |
 * | [Custom] | Arbitrary headers added as-is |
 */
sealed interface SignalingAuth {

    /** No authentication. */
    data object None : SignalingAuth

    /** Bearer token authentication. Adds `Authorization: Bearer <token>` header. */
    data class Bearer(val token: String) : SignalingAuth

    /** Manual cookie authentication. Cookies are serialized into a `Cookie` header string. */
    data class Cookies(val cookies: Map<String, String>) : SignalingAuth

    /** Ktor CookiesStorage-based authentication. Installs `HttpCookies` plugin for automatic cookie management. */
    data class CookieStorage(val storage: CookiesStorage) : SignalingAuth

    /** Custom header authentication. All entries are added as HTTP headers. */
    data class Custom(val headers: Map<String, String>) : SignalingAuth
}
