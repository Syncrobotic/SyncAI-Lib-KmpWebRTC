package com.syncrobotic.webrtc.signaling

import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*

/**
 * Tests for [WhipSignalingAdapter] using Ktor MockEngine.
 */
class WhipSignalingAdapterTest {

    private val sampleOffer = "v=0\r\no=- 0 0 IN IP4 127.0.0.1\r\n"
    private val sampleAnswer = "v=0\r\no=- 1 1 IN IP4 10.0.0.1\r\n"

    // ── sendOffer ──────────────────────────────────────────────────────

    @Test
    fun `WIPA-01 sendOffer returns SignalingResult on 201`() = runTest {
        val mockEngine = MockEngine { request ->
            assertEquals("application/sdp", request.body.contentType?.toString())
            respond(
                content = sampleAnswer,
                status = HttpStatusCode.Created,
                headers = headersOf(
                    HttpHeaders.Location to listOf("/resource/xyz"),
                    HttpHeaders.ETag to listOf("\"etag-whip\"")
                )
            )
        }
        val client = HttpClient(mockEngine)
        val adapter = WhipSignalingAdapter(
            url = "http://server:8889/stream/whip",
            httpClient = client
        )
        val result = adapter.sendOffer(sampleOffer)
        assertEquals(sampleAnswer, result.sdpAnswer)
        assertEquals("http://server:8889/resource/xyz", result.resourceUrl)
        assertEquals("\"etag-whip\"", result.etag)
    }

    @Test
    fun `WIPA-02 sendOffer throws WhipException on 4xx`() = runTest {
        val mockEngine = MockEngine {
            respond(content = "Forbidden", status = HttpStatusCode.Forbidden)
        }
        val client = HttpClient(mockEngine)
        val adapter = WhipSignalingAdapter(url = "http://s/whip", httpClient = client)
        val ex = assertFailsWith<WhipException> { adapter.sendOffer(sampleOffer) }
        assertEquals(WhipErrorCode.OFFER_REJECTED, ex.code)
    }

    @Test
    fun `WIPA-03 sendOffer wraps network error as WhipException`() = runTest {
        val client = HttpClient(MockEngine { throw java.io.IOException("connection refused") })
        val adapter = WhipSignalingAdapter(url = "http://s/whip", httpClient = client)
        val ex = assertFailsWith<WhipException> { adapter.sendOffer(sampleOffer) }
        assertEquals(WhipErrorCode.NETWORK_ERROR, ex.code)
    }

    // ── Auth ───────────────────────────────────────────────────────────

    @Test
    fun `WIPA-04 Bearer auth sends Authorization header`() = runTest {
        val mockEngine = MockEngine { request ->
            assertEquals("Bearer whip-jwt", request.headers[HttpHeaders.Authorization])
            respond(content = sampleAnswer, status = HttpStatusCode.Created)
        }
        val client = HttpClient(mockEngine)
        val adapter = WhipSignalingAdapter(
            url = "http://s/whip",
            auth = SignalingAuth.Bearer("whip-jwt"),
            httpClient = client
        )
        adapter.sendOffer(sampleOffer)
    }

    @Test
    fun `WIPA-05 Custom auth sends custom headers`() = runTest {
        val mockEngine = MockEngine { request ->
            assertEquals("key-123", request.headers["X-Stream-Key"])
            respond(content = sampleAnswer, status = HttpStatusCode.Created)
        }
        val client = HttpClient(mockEngine)
        val adapter = WhipSignalingAdapter(
            url = "http://s/whip",
            auth = SignalingAuth.Custom(mapOf("X-Stream-Key" to "key-123")),
            httpClient = client
        )
        adapter.sendOffer(sampleOffer)
    }

    // ── sendIceCandidate ───────────────────────────────────────────────

    @Test
    fun `WIPA-06 sendIceCandidate success on 204`() = runTest {
        val mockEngine = MockEngine { request ->
            assertEquals(HttpMethod.Patch, request.method)
            respond(content = "", status = HttpStatusCode.NoContent)
        }
        val client = HttpClient(mockEngine)
        val adapter = WhipSignalingAdapter(url = "http://s/whip", httpClient = client)
        adapter.sendIceCandidate(
            resourceUrl = "http://s/resource/1",
            candidate = "a=candidate:1 1 UDP 2130706431 10.0.0.1 9 typ host"
        )
    }

    @Test
    fun `WIPA-07 sendIceCandidate throws WhipException on failure`() = runTest {
        val mockEngine = MockEngine {
            respond(content = "error", status = HttpStatusCode.InternalServerError)
        }
        val client = HttpClient(mockEngine)
        val adapter = WhipSignalingAdapter(url = "http://s/whip", httpClient = client)
        val ex = assertFailsWith<WhipException> {
            adapter.sendIceCandidate(resourceUrl = "http://s/resource/1", candidate = "a=candidate:x")
        }
        assertEquals(WhipErrorCode.ICE_CANDIDATE_FAILED, ex.code)
    }

    // ── terminate ──────────────────────────────────────────────────────

    @Test
    fun `WIPA-08 terminate sends DELETE and ignores errors`() = runTest {
        var method: HttpMethod? = null
        val mockEngine = MockEngine { request ->
            method = request.method
            respond(content = "", status = HttpStatusCode.InternalServerError)
        }
        val client = HttpClient(mockEngine)
        val adapter = WhipSignalingAdapter(url = "http://s/whip", httpClient = client)
        // Should not throw
        adapter.terminate("http://s/resource/1")
        assertEquals(HttpMethod.Delete, method)
    }

    // ── Auth on ICE candidate and terminate ────────────────────────────

    @Test
    fun `WIPA-09 auth headers applied to sendIceCandidate`() = runTest {
        val mockEngine = MockEngine { request ->
            assertEquals("Bearer tok", request.headers[HttpHeaders.Authorization])
            respond(content = "", status = HttpStatusCode.NoContent)
        }
        val client = HttpClient(mockEngine)
        val adapter = WhipSignalingAdapter(
            url = "http://s/whip",
            auth = SignalingAuth.Bearer("tok"),
            httpClient = client
        )
        adapter.sendIceCandidate(
            resourceUrl = "http://s/resource/1",
            candidate = "a=candidate:x"
        )
    }

    @Test
    fun `WIPA-10 auth headers applied to terminate`() = runTest {
        val mockEngine = MockEngine { request ->
            assertEquals("api-key-val", request.headers["X-Api-Key"])
            respond(content = "", status = HttpStatusCode.OK)
        }
        val client = HttpClient(mockEngine)
        val adapter = WhipSignalingAdapter(
            url = "http://s/whip",
            auth = SignalingAuth.Custom(mapOf("X-Api-Key" to "api-key-val")),
            httpClient = client
        )
        adapter.terminate("http://s/resource/1")
    }
}
