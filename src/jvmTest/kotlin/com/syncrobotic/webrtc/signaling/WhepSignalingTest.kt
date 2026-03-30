package com.syncrobotic.webrtc.signaling

import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*

/**
 * Unit tests for WhepSignaling using Ktor MockEngine.
 * Covers TEST_SPEC: WHEP-01 through WHEP-20.
 */
class WhepSignalingTest {

    // --- sendOffer tests ---

    @Test
    fun `WHEP-01 sendOffer 201 returns SessionResult with SDP answer`() = runTest {
        val mockEngine = MockEngine { request ->
            assertEquals(HttpMethod.Post, request.method)
            respond(
                content = "v=0\r\no=- 0 0 IN IP4 127.0.0.1\r\ns=-\r\n",
                status = HttpStatusCode.Created,
                headers = headersOf(
                    HttpHeaders.Location to listOf("http://server/session/abc"),
                    HttpHeaders.ETag to listOf("\"etag123\"")
                )
            )
        }
        val client = HttpClient(mockEngine)
        val whep = WhepSignaling(client)
        val result = whep.sendOffer("http://server/stream/whep", "v=0\r\noffer\r\n")

        assertTrue(result.sdpAnswer.contains("v=0"))
        assertEquals("http://server/session/abc", result.resourceUrl)
        assertEquals("\"etag123\"", result.etag)
        client.close()
    }

    @Test
    fun `WHEP-02 sendOffer 200 also accepted`() = runTest {
        val mockEngine = MockEngine {
            respond(content = "answer-sdp", status = HttpStatusCode.OK)
        }
        val client = HttpClient(mockEngine)
        val whep = WhepSignaling(client)
        val result = whep.sendOffer("http://server/whep", "offer-sdp")

        assertEquals("answer-sdp", result.sdpAnswer)
        client.close()
    }

    @Test
    fun `WHEP-03 sendOffer 400 throws WhepException`() = runTest {
        val mockEngine = MockEngine {
            respond(content = "Bad Request", status = HttpStatusCode.BadRequest)
        }
        val client = HttpClient(mockEngine)
        val whep = WhepSignaling(client)

        val ex = assertFailsWith<WhepException> {
            whep.sendOffer("http://server/whep", "offer")
        }
        assertTrue(ex.message!!.contains("400"))
        client.close()
    }

    @Test
    fun `WHEP-04 sendOffer 500 throws WhepException`() = runTest {
        val mockEngine = MockEngine {
            respond(content = "Internal Server Error", status = HttpStatusCode.InternalServerError)
        }
        val client = HttpClient(mockEngine)
        val whep = WhepSignaling(client)

        val ex = assertFailsWith<WhepException> {
            whep.sendOffer("http://server/whep", "offer")
        }
        assertTrue(ex.message!!.contains("500"))
        client.close()
    }

    @Test
    fun `WHEP-05 sendOffer network error throws WhepException wrapping cause`() = runTest {
        val mockEngine = MockEngine {
            throw java.net.ConnectException("Connection refused")
        }
        val client = HttpClient(mockEngine)
        val whep = WhepSignaling(client)

        val ex = assertFailsWith<WhepException> {
            whep.sendOffer("http://server/whep", "offer")
        }
        assertTrue(ex.message!!.contains("Failed to send WHEP offer"))
        assertNotNull(ex.cause)
        client.close()
    }

    @Test
    fun `WHEP-06 absolute Location header returned as-is`() = runTest {
        val mockEngine = MockEngine {
            respond(
                content = "answer",
                status = HttpStatusCode.Created,
                headers = headersOf(HttpHeaders.Location to listOf("https://cdn.example.com/session/xyz"))
            )
        }
        val client = HttpClient(mockEngine)
        val whep = WhepSignaling(client)
        val result = whep.sendOffer("http://server/whep", "offer")

        assertEquals("https://cdn.example.com/session/xyz", result.resourceUrl)
        client.close()
    }

    @Test
    fun `WHEP-07 relative Location header resolved to absolute URL`() = runTest {
        val mockEngine = MockEngine {
            respond(
                content = "answer",
                status = HttpStatusCode.Created,
                headers = headersOf(HttpHeaders.Location to listOf("/session/xyz"))
            )
        }
        val client = HttpClient(mockEngine)
        val whep = WhepSignaling(client)
        val result = whep.sendOffer("http://server:8889/stream/whep", "offer")

        assertNotNull(result.resourceUrl)
        assertTrue(result.resourceUrl!!.startsWith("http://server:8889"))
        assertTrue(result.resourceUrl!!.contains("/session/xyz"))
        client.close()
    }

    @Test
    fun `WHEP-08 no Location header results in null resourceUrl`() = runTest {
        val mockEngine = MockEngine {
            respond(content = "answer", status = HttpStatusCode.Created)
        }
        val client = HttpClient(mockEngine)
        val whep = WhepSignaling(client)
        val result = whep.sendOffer("http://server/whep", "offer")

        assertNull(result.resourceUrl)
        client.close()
    }

    @Test
    fun `WHEP-09 ETag header captured`() = runTest {
        val mockEngine = MockEngine {
            respond(
                content = "answer",
                status = HttpStatusCode.Created,
                headers = headersOf(HttpHeaders.ETag to listOf("\"v1\""))
            )
        }
        val client = HttpClient(mockEngine)
        val whep = WhepSignaling(client)
        val result = whep.sendOffer("http://server/whep", "offer")

        assertEquals("\"v1\"", result.etag)
        client.close()
    }

    @Test
    fun `WHEP-10 Link header with ice-server parsed`() = runTest {
        val linkHeader = """<stun:stun.example.com>; rel="ice-server""""
        val mockEngine = MockEngine {
            respond(
                content = "answer",
                status = HttpStatusCode.Created,
                headers = headersOf(HttpHeaders.Link to listOf(linkHeader))
            )
        }
        val client = HttpClient(mockEngine)
        val whep = WhepSignaling(client)
        val result = whep.sendOffer("http://server/whep", "offer")

        assertEquals(1, result.iceServers.size)
        assertTrue(result.iceServers[0].contains("stun:stun.example.com"))
        assertTrue(result.iceServers[0].contains("ice-server"))
        client.close()
    }

    @Test
    fun `WHEP-11 no Link header results in empty iceServers`() = runTest {
        val mockEngine = MockEngine {
            respond(content = "answer", status = HttpStatusCode.Created)
        }
        val client = HttpClient(mockEngine)
        val whep = WhepSignaling(client)
        val result = whep.sendOffer("http://server/whep", "offer")

        assertTrue(result.iceServers.isEmpty())
        client.close()
    }

    @Test
    fun `WHEP-12 request Content-Type is application-sdp`() = runTest {
        val mockEngine = MockEngine { request ->
            assertEquals(ContentType("application", "sdp"), request.body.contentType)
            respond(content = "answer", status = HttpStatusCode.Created)
        }
        val client = HttpClient(mockEngine)
        val whep = WhepSignaling(client)
        whep.sendOffer("http://server/whep", "offer")
        client.close()
    }

    @Test
    fun `WHEP-13 request body contains SDP offer string`() = runTest {
        var capturedBody: String? = null
        val mockEngine = MockEngine { request ->
            capturedBody = String(request.body.toByteArray())
            respond(content = "answer", status = HttpStatusCode.Created)
        }
        val client = HttpClient(mockEngine)
        val whep = WhepSignaling(client)
        whep.sendOffer("http://server/whep", "my-sdp-offer")

        assertEquals("my-sdp-offer", capturedBody)
        client.close()
    }

    // --- sendIceCandidate tests ---

    @Test
    fun `WHEP-14 sendIceCandidate 204 succeeds`() = runTest {
        val mockEngine = MockEngine { request ->
            assertEquals(HttpMethod.Patch, request.method)
            respond(content = "", status = HttpStatusCode.NoContent)
        }
        val client = HttpClient(mockEngine)
        val whep = WhepSignaling(client)
        whep.sendIceCandidate(
            resourceUrl = "http://server/session/abc",
            candidate = "a=candidate:1 1 UDP 2122252543 192.168.0.1 50000 typ host"
        )
        client.close()
    }

    @Test
    fun `WHEP-15 sendIceCandidate 400 throws WhepException`() = runTest {
        val mockEngine = MockEngine {
            respond(content = "Bad candidate", status = HttpStatusCode.BadRequest)
        }
        val client = HttpClient(mockEngine)
        val whep = WhepSignaling(client)

        assertFailsWith<WhepException> {
            whep.sendIceCandidate(
                resourceUrl = "http://server/session/abc",
                candidate = "bad-candidate"
            )
        }
        client.close()
    }

    @Test
    fun `WHEP-16 sendIceCandidate SDP fragment format`() = runTest {
        var capturedBody: String? = null
        var capturedContentType: ContentType? = null
        val mockEngine = MockEngine { request ->
            capturedContentType = request.body.contentType
            capturedBody = String(request.body.toByteArray())
            respond(content = "", status = HttpStatusCode.NoContent)
        }
        val client = HttpClient(mockEngine)
        val whep = WhepSignaling(client)
        whep.sendIceCandidate(
            resourceUrl = "http://server/session/abc",
            candidate = "a=candidate:1 1 UDP 2122252543 192.168.0.1 50000 typ host",
            iceUfrag = "ufrag1",
            icePwd = "pwd1",
            mid = "0"
        )
        assertEquals(ContentType("application", "trickle-ice-sdpfrag"), capturedContentType)
        assertNotNull(capturedBody)
        assertTrue(capturedBody!!.contains("a=ice-ufrag:ufrag1"))
        assertTrue(capturedBody!!.contains("a=ice-pwd:pwd1"))
        assertTrue(capturedBody!!.contains("a=mid:0"))
        assertTrue(capturedBody!!.contains("a=candidate:"))
        assertTrue(capturedBody!!.endsWith("\r\n"))
        client.close()
    }

    @Test
    fun `WHEP-17 sendIceCandidate with ETag includes If-Match header`() = runTest {
        var ifMatchValue: String? = null
        val mockEngine = MockEngine { request ->
            ifMatchValue = request.headers[HttpHeaders.IfMatch]
            respond(content = "", status = HttpStatusCode.NoContent)
        }
        val client = HttpClient(mockEngine)
        val whep = WhepSignaling(client)
        whep.sendIceCandidate(
            resourceUrl = "http://server/session/abc",
            candidate = "a=candidate:1 1 UDP 2122252543 192.168.0.1 50000 typ host",
            etag = "\"v1\""
        )
        assertEquals("\"v1\"", ifMatchValue)
        client.close()
    }

    // --- terminateSession tests ---

    @Test
    fun `WHEP-18 terminateSession success`() = runTest {
        val mockEngine = MockEngine { request ->
            assertEquals(HttpMethod.Delete, request.method)
            respond(content = "", status = HttpStatusCode.OK)
        }
        val client = HttpClient(mockEngine)
        val whep = WhepSignaling(client)
        whep.terminateSession("http://server/session/abc")
        client.close()
    }

    @Test
    fun `WHEP-19 terminateSession network error silently ignored`() = runTest {
        val mockEngine = MockEngine {
            throw java.net.ConnectException("Connection refused")
        }
        val client = HttpClient(mockEngine)
        val whep = WhepSignaling(client)
        // Should not throw
        whep.terminateSession("http://server/session/abc")
        client.close()
    }

    @Test
    fun `WHEP-20 terminateSession sends DELETE request`() = runTest {
        var requestUrl: String? = null
        var methodUsed: HttpMethod? = null
        val mockEngine = MockEngine { request ->
            methodUsed = request.method
            requestUrl = request.url.toString()
            respond(content = "", status = HttpStatusCode.OK)
        }
        val client = HttpClient(mockEngine)
        val whep = WhepSignaling(client)
        whep.terminateSession("http://server/session/abc")
        assertEquals(HttpMethod.Delete, methodUsed)
        assertTrue(requestUrl!!.contains("/session/abc"))
        client.close()
    }
}
