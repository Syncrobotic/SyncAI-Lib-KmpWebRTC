package com.syncrobotic.webrtc.signaling

import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*

/**
 * Unit tests for WhipSignaling using Ktor MockEngine.
 * Covers TEST_SPEC: WHIP-01 through WHIP-11.
 */
class WhipSignalingTest {

    // --- sendOffer tests ---

    @Test
    fun `WHIP-01 sendOffer 201 returns SessionResult`() = runTest {
        val mockEngine = MockEngine { request ->
            assertEquals(HttpMethod.Post, request.method)
            respond(
                content = "v=0\r\nanswer-sdp\r\n",
                status = HttpStatusCode.Created,
                headers = headersOf(
                    HttpHeaders.Location to listOf("http://server/session/xyz"),
                    HttpHeaders.ETag to listOf("\"e1\"")
                )
            )
        }
        val client = HttpClient(mockEngine)
        val whip = WhipSignaling(client)
        val result = whip.sendOffer("http://server/stream/whip", "v=0\r\noffer\r\n")

        assertTrue(result.sdpAnswer.contains("answer-sdp"))
        assertEquals("http://server/session/xyz", result.resourceUrl)
        assertEquals("\"e1\"", result.etag)
        client.close()
    }

    @Test
    fun `WHIP-02 sendOffer 400 throws WhipException with OFFER_REJECTED`() = runTest {
        val mockEngine = MockEngine {
            respond(content = "Bad request", status = HttpStatusCode.BadRequest)
        }
        val client = HttpClient(mockEngine)
        val whip = WhipSignaling(client)

        val ex = assertFailsWith<WhipException> {
            whip.sendOffer("http://server/whip", "offer")
        }
        assertEquals(WhipErrorCode.OFFER_REJECTED, ex.code)
        assertTrue(ex.message!!.contains("400"))
        client.close()
    }

    @Test
    fun `WHIP-03 sendOffer network error throws WhipException with NETWORK_ERROR`() = runTest {
        val mockEngine = MockEngine {
            throw java.net.ConnectException("Connection refused")
        }
        val client = HttpClient(mockEngine)
        val whip = WhipSignaling(client)

        val ex = assertFailsWith<WhipException> {
            whip.sendOffer("http://server/whip", "offer")
        }
        assertEquals(WhipErrorCode.NETWORK_ERROR, ex.code)
        assertTrue(ex.message!!.contains("Failed to send WHIP offer"))
        assertNotNull(ex.cause)
        client.close()
    }

    @Test
    fun `WHIP-04 Location header absolute returned as-is`() = runTest {
        val mockEngine = MockEngine {
            respond(
                content = "answer",
                status = HttpStatusCode.Created,
                headers = headersOf(HttpHeaders.Location to listOf("https://cdn.example.com/session/abc"))
            )
        }
        val client = HttpClient(mockEngine)
        val whip = WhipSignaling(client)
        val result = whip.sendOffer("http://server/whip", "offer")

        assertEquals("https://cdn.example.com/session/abc", result.resourceUrl)
        client.close()
    }

    @Test
    fun `WHIP-05 Location header relative resolved to absolute`() = runTest {
        val mockEngine = MockEngine {
            respond(
                content = "answer",
                status = HttpStatusCode.Created,
                headers = headersOf(HttpHeaders.Location to listOf("/session/abc"))
            )
        }
        val client = HttpClient(mockEngine)
        val whip = WhipSignaling(client)
        val result = whip.sendOffer("http://server:8889/stream/whip", "offer")

        assertNotNull(result.resourceUrl)
        assertTrue(result.resourceUrl!!.startsWith("http://server:8889"))
        assertTrue(result.resourceUrl!!.contains("/session/abc"))
        client.close()
    }

    @Test
    fun `WHIP-06 ETag header populated`() = runTest {
        val mockEngine = MockEngine {
            respond(
                content = "answer",
                status = HttpStatusCode.Created,
                headers = headersOf(HttpHeaders.ETag to listOf("\"ver2\""))
            )
        }
        val client = HttpClient(mockEngine)
        val whip = WhipSignaling(client)
        val result = whip.sendOffer("http://server/whip", "offer")

        assertEquals("\"ver2\"", result.etag)
        client.close()
    }

    @Test
    fun `WHIP-07 Link headers for ICE servers parsed correctly`() = runTest {
        val linkHeader = """<stun:stun.l.google.com:19302>; rel="ice-server""""
        val mockEngine = MockEngine {
            respond(
                content = "answer",
                status = HttpStatusCode.Created,
                headers = headersOf(HttpHeaders.Link to listOf(linkHeader))
            )
        }
        val client = HttpClient(mockEngine)
        val whip = WhipSignaling(client)
        val result = whip.sendOffer("http://server/whip", "offer")

        assertEquals(1, result.iceServers.size)
        assertTrue(result.iceServers[0].contains("stun:stun.l.google.com"))
        client.close()
    }

    @Test
    fun `WHIP-08 request Content-Type is application-sdp`() = runTest {
        val mockEngine = MockEngine { request ->
            assertEquals(ContentType("application", "sdp"), request.body.contentType)
            respond(content = "answer", status = HttpStatusCode.Created)
        }
        val client = HttpClient(mockEngine)
        val whip = WhipSignaling(client)
        whip.sendOffer("http://server/whip", "offer")
        client.close()
    }

    // --- sendIceCandidate tests ---

    @Test
    fun `WHIP-09 sendIceCandidate 204 success`() = runTest {
        val mockEngine = MockEngine { request ->
            assertEquals(HttpMethod.Patch, request.method)
            respond(content = "", status = HttpStatusCode.NoContent)
        }
        val client = HttpClient(mockEngine)
        val whip = WhipSignaling(client)
        whip.sendIceCandidate(
            resourceUrl = "http://server/session/xyz",
            candidate = "a=candidate:1 1 UDP 2122252543 192.168.0.1 50000 typ host"
        )
        client.close()
    }

    @Test
    fun `WHIP-10 sendIceCandidate error throws WhipException with ICE_CANDIDATE_FAILED`() = runTest {
        val mockEngine = MockEngine {
            respond(content = "Bad candidate", status = HttpStatusCode.BadRequest)
        }
        val client = HttpClient(mockEngine)
        val whip = WhipSignaling(client)

        val ex = assertFailsWith<WhipException> {
            whip.sendIceCandidate(
                resourceUrl = "http://server/session/xyz",
                candidate = "bad-candidate"
            )
        }
        assertEquals(WhipErrorCode.ICE_CANDIDATE_FAILED, ex.code)
        client.close()
    }

    // --- terminateSession tests ---

    @Test
    fun `WHIP-11 terminateSession no exception on any outcome`() = runTest {
        // Test success case
        val mockEngine1 = MockEngine { request ->
            assertEquals(HttpMethod.Delete, request.method)
            respond(content = "", status = HttpStatusCode.OK)
        }
        val client1 = HttpClient(mockEngine1)
        val whip1 = WhipSignaling(client1)
        whip1.terminateSession("http://server/session/xyz")
        client1.close()

        // Test network error case — should not throw
        val mockEngine2 = MockEngine {
            throw java.net.ConnectException("Connection refused")
        }
        val client2 = HttpClient(mockEngine2)
        val whip2 = WhipSignaling(client2)
        whip2.terminateSession("http://server/session/xyz")
        client2.close()
    }
}
