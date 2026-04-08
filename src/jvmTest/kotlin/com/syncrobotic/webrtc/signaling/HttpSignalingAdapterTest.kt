package com.syncrobotic.webrtc.signaling

import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*

/**
 * Unit tests for HttpSignalingAdapter.
 * Covers TEST_SPEC: HS-01 through HS-13.
 */
class HttpSignalingAdapterTest {

    private val sdpOffer = "v=0\r\no=- 0 0 IN IP4 127.0.0.1\r\n"
    private val sdpAnswer = "v=0\r\no=- 1 1 IN IP4 127.0.0.1\r\n"

    @Test
    fun `HS-01 successful POST offer returns SignalingResult with SDP answer`() = runTest {
        val mockEngine = MockEngine { _ ->
            respond(
                content = sdpAnswer,
                status = HttpStatusCode.Created,
                headers = headersOf(HttpHeaders.ContentType to listOf("application/sdp"))
            )
        }
        val adapter = HttpSignalingAdapter(
            url = "http://localhost:8889/stream/whep",
            httpClient = HttpClient(mockEngine)
        )

        val result = adapter.sendOffer(sdpOffer)
        assertEquals(sdpAnswer, result.sdpAnswer)
    }

    @Test
    fun `HS-02 HTTP 201 Created is success`() = runTest {
        val mockEngine = MockEngine { _ ->
            respond(
                content = sdpAnswer,
                status = HttpStatusCode.Created,
                headers = headersOf(HttpHeaders.ContentType to listOf("application/sdp"))
            )
        }
        val adapter = HttpSignalingAdapter(
            url = "http://localhost:8889/stream/whep",
            httpClient = HttpClient(mockEngine)
        )

        val result = adapter.sendOffer(sdpOffer)
        assertNotNull(result)
        assertEquals(sdpAnswer, result.sdpAnswer)
    }

    @Test
    fun `HS-03 HTTP 200 OK is success`() = runTest {
        val mockEngine = MockEngine { _ ->
            respond(
                content = sdpAnswer,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType to listOf("application/sdp"))
            )
        }
        val adapter = HttpSignalingAdapter(
            url = "http://localhost:8889/stream/whep",
            httpClient = HttpClient(mockEngine)
        )

        val result = adapter.sendOffer(sdpOffer)
        assertEquals(sdpAnswer, result.sdpAnswer)
    }

    @Test
    fun `HS-04 HTTP 500 throws SignalingException with OFFER_REJECTED`() = runTest {
        val mockEngine = MockEngine { _ ->
            respond(
                content = "Internal Server Error",
                status = HttpStatusCode.InternalServerError
            )
        }
        val adapter = HttpSignalingAdapter(
            url = "http://localhost:8889/stream/whep",
            httpClient = HttpClient(mockEngine)
        )

        val ex = assertFailsWith<SignalingException> {
            adapter.sendOffer(sdpOffer)
        }
        assertEquals(SignalingErrorCode.OFFER_REJECTED, ex.code)
    }

    @Test
    fun `HS-05 Location header parsed into resourceUrl`() = runTest {
        val mockEngine = MockEngine { _ ->
            respond(
                content = sdpAnswer,
                status = HttpStatusCode.Created,
                headers = headersOf(
                    HttpHeaders.ContentType to listOf("application/sdp"),
                    HttpHeaders.Location to listOf("http://localhost:8889/resource/123")
                )
            )
        }
        val adapter = HttpSignalingAdapter(
            url = "http://localhost:8889/stream/whep",
            httpClient = HttpClient(mockEngine)
        )

        val result = adapter.sendOffer(sdpOffer)
        assertEquals("http://localhost:8889/resource/123", result.resourceUrl)
    }

    @Test
    fun `HS-06 relative Location header resolved to absolute URL`() = runTest {
        val mockEngine = MockEngine { _ ->
            respond(
                content = sdpAnswer,
                status = HttpStatusCode.Created,
                headers = headersOf(
                    HttpHeaders.ContentType to listOf("application/sdp"),
                    HttpHeaders.Location to listOf("/resource/456")
                )
            )
        }
        val adapter = HttpSignalingAdapter(
            url = "http://localhost:8889/stream/whep",
            httpClient = HttpClient(mockEngine)
        )

        val result = adapter.sendOffer(sdpOffer)
        assertNotNull(result.resourceUrl)
        assertTrue(result.resourceUrl!!.startsWith("http://localhost:8889"))
        assertTrue(result.resourceUrl!!.contains("/resource/456"))
    }

    @Test
    fun `HS-07 ETag header captured`() = runTest {
        val mockEngine = MockEngine { _ ->
            respond(
                content = sdpAnswer,
                status = HttpStatusCode.Created,
                headers = headersOf(
                    HttpHeaders.ContentType to listOf("application/sdp"),
                    HttpHeaders.ETag to listOf("\"abc123\"")
                )
            )
        }
        val adapter = HttpSignalingAdapter(
            url = "http://localhost:8889/stream/whep",
            httpClient = HttpClient(mockEngine)
        )

        val result = adapter.sendOffer(sdpOffer)
        assertEquals("\"abc123\"", result.etag)
    }

    @Test
    fun `HS-08 ICE candidate PATCH sends correct content type`() = runTest {
        var capturedContentType: String? = null
        val mockEngine = MockEngine { request ->
            capturedContentType = request.body.contentType?.toString()
            respond(
                content = "",
                status = HttpStatusCode.NoContent
            )
        }
        val adapter = HttpSignalingAdapter(
            url = "http://localhost:8889/stream/whep",
            httpClient = HttpClient(mockEngine)
        )

        adapter.sendIceCandidate(
            resourceUrl = "http://localhost:8889/resource/123",
            candidate = "a=candidate:1 1 UDP 2130706431 192.168.1.1 5000 typ host",
            sdpMid = "0",
            sdpMLineIndex = 0
        )

        assertNotNull(capturedContentType)
        assertTrue(capturedContentType!!.contains("application/trickle-ice-sdpfrag"))
    }

    @Test
    fun `HS-09 terminate DELETE ignores errors`() = runTest {
        val mockEngine = MockEngine { _ ->
            respond(
                content = "Server Error",
                status = HttpStatusCode.InternalServerError
            )
        }
        val adapter = HttpSignalingAdapter(
            url = "http://localhost:8889/stream/whep",
            httpClient = HttpClient(mockEngine)
        )

        // Should not throw
        adapter.terminate("http://localhost:8889/resource/123")
    }

    @Test
    fun `HS-10 Bearer auth adds Authorization header`() = runTest {
        var capturedAuth: String? = null
        val mockEngine = MockEngine { request ->
            capturedAuth = request.headers[HttpHeaders.Authorization]
            respond(
                content = sdpAnswer,
                status = HttpStatusCode.Created,
                headers = headersOf(HttpHeaders.ContentType to listOf("application/sdp"))
            )
        }
        val adapter = HttpSignalingAdapter(
            url = "http://localhost:8889/stream/whep",
            auth = SignalingAuth.Bearer("my-jwt-token"),
            httpClient = HttpClient(mockEngine)
        )

        adapter.sendOffer(sdpOffer)
        assertEquals("Bearer my-jwt-token", capturedAuth)
    }

    @Test
    fun `HS-11 Cookie auth adds Cookie header`() = runTest {
        var capturedCookie: String? = null
        val mockEngine = MockEngine { request ->
            capturedCookie = request.headers[HttpHeaders.Cookie]
            respond(
                content = sdpAnswer,
                status = HttpStatusCode.Created,
                headers = headersOf(HttpHeaders.ContentType to listOf("application/sdp"))
            )
        }
        val adapter = HttpSignalingAdapter(
            url = "http://localhost:8889/stream/whep",
            auth = SignalingAuth.Cookies(mapOf("session" to "abc123")),
            httpClient = HttpClient(mockEngine)
        )

        adapter.sendOffer(sdpOffer)
        assertNotNull(capturedCookie)
        assertTrue(capturedCookie!!.contains("session=abc123"))
    }

    @Test
    fun `HS-12 Custom headers auth adds custom headers`() = runTest {
        var capturedApiKey: String? = null
        val mockEngine = MockEngine { request ->
            capturedApiKey = request.headers["X-Api-Key"]
            respond(
                content = sdpAnswer,
                status = HttpStatusCode.Created,
                headers = headersOf(HttpHeaders.ContentType to listOf("application/sdp"))
            )
        }
        val adapter = HttpSignalingAdapter(
            url = "http://localhost:8889/stream/whep",
            auth = SignalingAuth.Custom(mapOf("X-Api-Key" to "secret-key")),
            httpClient = HttpClient(mockEngine)
        )

        adapter.sendOffer(sdpOffer)
        assertEquals("secret-key", capturedApiKey)
    }

    @Test
    fun `HS-13 No auth adds no extra headers`() = runTest {
        var capturedAuthorization: String? = null
        var capturedCookie: String? = null
        val mockEngine = MockEngine { request ->
            capturedAuthorization = request.headers[HttpHeaders.Authorization]
            capturedCookie = request.headers[HttpHeaders.Cookie]
            respond(
                content = sdpAnswer,
                status = HttpStatusCode.Created,
                headers = headersOf(HttpHeaders.ContentType to listOf("application/sdp"))
            )
        }
        val adapter = HttpSignalingAdapter(
            url = "http://localhost:8889/stream/whep",
            auth = SignalingAuth.None,
            httpClient = HttpClient(mockEngine)
        )

        adapter.sendOffer(sdpOffer)
        assertNull(capturedAuthorization)
        assertNull(capturedCookie)
    }
}
