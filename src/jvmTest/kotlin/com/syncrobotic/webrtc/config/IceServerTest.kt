package com.syncrobotic.webrtc.config

import kotlin.test.*

/**
 * Unit tests for IceServer.
 * Covers TEST_SPEC: IS-01 through IS-03.
 */
class IceServerTest {

    @Test
    fun `IS-01 GOOGLE_STUN`() {
        val stun = IceServer.GOOGLE_STUN
        assertEquals(listOf("stun:stun.l.google.com:19302"), stun.urls)
        assertNull(stun.username)
        assertNull(stun.credential)
    }

    @Test
    fun `IS-02 DEFAULT_ICE_SERVERS contains exactly one entry`() {
        val servers = IceServer.DEFAULT_ICE_SERVERS
        assertEquals(1, servers.size)
        assertEquals(IceServer.GOOGLE_STUN, servers[0])
    }

    @Test
    fun `IS-03 custom TURN server with credentials`() {
        val turn = IceServer(
            urls = listOf("turn:turn.example.com:3478"),
            username = "user",
            credential = "pass"
        )
        assertEquals("user", turn.username)
        assertEquals("pass", turn.credential)
        assertEquals("turn:turn.example.com:3478", turn.urls[0])
    }
}
