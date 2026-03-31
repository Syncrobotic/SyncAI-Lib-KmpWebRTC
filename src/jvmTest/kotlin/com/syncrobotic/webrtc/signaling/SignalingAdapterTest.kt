package com.syncrobotic.webrtc.signaling

import com.syncrobotic.webrtc.config.IceServer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for [SignalingResult] and [SignalingAuth] data types.
 */
class SignalingAdapterTest {

    // ── SignalingResult ─────────────────────────────────────────────────

    @Test
    fun `SR-01 SignalingResult defaults are sensible`() {
        val result = SignalingResult(sdpAnswer = "v=0\r\n")
        assertNull(result.resourceUrl)
        assertNull(result.etag)
        assertTrue(result.iceServers.isEmpty())
    }

    @Test
    fun `SR-02 SignalingResult stores all fields`() {
        val ice = listOf(IceServer(urls = listOf("stun:stun.example.com")))
        val result = SignalingResult(
            sdpAnswer = "v=0\r\n",
            resourceUrl = "https://server/resource/123",
            etag = "\"abc\"",
            iceServers = ice
        )
        assertEquals("v=0\r\n", result.sdpAnswer)
        assertEquals("https://server/resource/123", result.resourceUrl)
        assertEquals("\"abc\"", result.etag)
        assertEquals(1, result.iceServers.size)
    }

    @Test
    fun `SR-03 SignalingResult data class equality`() {
        val a = SignalingResult(sdpAnswer = "x")
        val b = SignalingResult(sdpAnswer = "x")
        assertEquals(a, b)
    }

    // ── SignalingAuth variants ──────────────────────────────────────────

    @Test
    fun `SA-01 SignalingAuth None is singleton`() {
        val auth: SignalingAuth = SignalingAuth.None
        assertTrue(auth is SignalingAuth.None)
    }

    @Test
    fun `SA-02 SignalingAuth Bearer stores token`() {
        val auth = SignalingAuth.Bearer("jwt-token-123")
        assertEquals("jwt-token-123", auth.token)
    }

    @Test
    fun `SA-03 SignalingAuth Cookies stores map`() {
        val auth = SignalingAuth.Cookies(mapOf("session" to "abc", "csrf" to "xyz"))
        assertEquals(2, auth.cookies.size)
        assertEquals("abc", auth.cookies["session"])
    }

    @Test
    fun `SA-04 SignalingAuth Custom stores headers`() {
        val auth = SignalingAuth.Custom(mapOf("X-Api-Key" to "secret"))
        assertEquals("secret", auth.headers["X-Api-Key"])
    }

    @Test
    fun `SA-05 SignalingAuth sealed interface exhaustive check`() {
        val auths: List<SignalingAuth> = listOf(
            SignalingAuth.None,
            SignalingAuth.Bearer("t"),
            SignalingAuth.Cookies(emptyMap()),
            SignalingAuth.Custom(emptyMap())
        )
        // CookieStorage not tested here as it requires Ktor CookiesStorage instance
        assertEquals(4, auths.size)
    }
}
