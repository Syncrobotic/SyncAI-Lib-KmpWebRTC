package com.syncrobotic.webrtc.signaling

import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*

/**
 * Tests for [WhepSignalingAdapter] using Ktor MockEngine.
 */
class WhepSignalingAdapterTest {

    private val sampleOffer = "v=0\r\no=- 0 0 IN IP4 127.0.0.1\r\n"
    private val sampleAnswer = "v=0\r\no=- 1 1 IN IP4 10.0.0.1\r\n"

    // ── sendOffer ──────────────────────────────────────────────────────

    @Test
    fun `WSA-01 sendOffer returns SignalingResult on 201`() = runTest {
        val mockEngine = MockEngine { request ->
            assertEquals("application/sdp", request.body.contentType?.toString())
            respond(
                content = sampleAnswer,
                status = HttpStatusCode.Created,
                headers = headersOf(
                    HttpHeaders.Location to listOf("/resource/abc"),
                    HttpHeaders.ETag to listOf("\"etag1\"")
                )
            )
        }
        val client = HttpClient(mockEngine)
        val adapter = WhepSignalingAdapter(
            url = "http://server:8889/stream/whep",
            httpClient = client
        )
        val result = adapter.sendOffer(sampleOffer)
        assertEquals(sampleAnswer, result.sdpAnswer)
        assertEquals("http://server:8889/resource/abc", result.resourceUrl)
        assertEquals("\"etag1\"", result.etag)
    }

    @Test
    fun `WSA-02 sendOffer accepts 200 OK`() = runTest {
        val mockEngine = MockEngine {
            respond(content = sampleAnswer, status = HttpStatusCode.OK)
        }
        val client = HttpClient(mockEngine)
        val adapter = WhepSignalingAdapter(url = "http://s/whep", httpClient = client)
        val result = adapter.sendOffer(sampleOffer)
        assertEquals(sampleAnswer, result.sdpAnswer)
    }

    @Test
    fun `WSA-03 sendOffer throws WhepException on 4xx`() = runTest {
        val mockEngine = MockEngine {
            respond(content = "Bad Request", status = HttpStatusCode.BadRequest)
        }
        val client = HttpClient(mockEngine)
        val adapter = WhepSignalingAdapter(url = "http://s/whep", httpClient = client)
        assertFailsWith<WhepException> { adapter.sendOffer(sampleOffer) }
    }

    @Test
    fun `WSA-04 sendOffer resolves absolute Location`() = runTest {
        val mockEngine = MockEngine {
            respond(
                content = sampleAnswer,
                status = HttpStatusCode.Created,
                headers = headersOf(HttpHeaders.Location to listOf("https://other-host/res/1"))
            )
        }
        val client = HttpClient(mockEngine)
        val adapter = WhepSignalingAdapter(url = "http://s/whep", httpClient = client)
        val result = adapter.sendOffer(sampleOffer)
        assertEquals("https://other-host/res/1", result.resourceUrl)
    }

    @Test
    fun `WSA-05 sendOffer with no Location header`() = runTest {
        val mockEngine = MockEngine {
            respond(content = sampleAnswer, status = HttpStatusCode.Created)
        }
        val client = HttpClient(mockEngine)
        val adapter = WhepSignalingAdapter(url = "http://s/whep", httpClient = client)
        val result = adapter.sendOffer(sampleOffer)
        assertNull(result.resourceUrl)
    }

    // ── Auth ───────────────────────────────────────────────────────────

    @Test
    fun `WSA-06 Bearer auth sends Authorization header`() = runTest {
        val mockEngine = MockEngine { request ->
            assertEquals("Bearer my-token", request.headers[HttpHeaders.Authorization])
            respond(content = sampleAnswer, status = HttpStatusCode.Created)
        }
        val client = HttpClient(mockEngine)
        val adapter = WhepSignalingAdapter(
            url = "http://s/whep",
            auth = SignalingAuth.Bearer("my-token"),
            httpClient = client
        )
        adapter.sendOffer(sampleOffer)
    }

    @Test
    fun `WSA-07 Cookies auth sends Cookie header`() = runTest {
        val mockEngine = MockEngine { request ->
            val cookie = request.headers[HttpHeaders.Cookie]
            assertNotNull(cookie)
            assertTrue(cookie.contains("session=abc"))
            assertTrue(cookie.contains("lang=en"))
            respond(content = sampleAnswer, status = HttpStatusCode.Created)
        }
        val client = HttpClient(mockEngine)
        val adapter = WhepSignalingAdapter(
            url = "http://s/whep",
            auth = SignalingAuth.Cookies(mapOf("session" to "abc", "lang" to "en")),
            httpClient = client
        )
        adapter.sendOffer(sampleOffer)
    }

    @Test
    fun `WSA-08 Custom auth sends custom headers`() = runTest {
        val mockEngine = MockEngine { request ->
            assertEquals("secret-key", request.headers["X-Api-Key"])
            respond(content = sampleAnswer, status = HttpStatusCode.Created)
        }
        val client = HttpClient(mockEngine)
        val adapter = WhepSignalingAdapter(
            url = "http://s/whep",
            auth = SignalingAuth.Custom(mapOf("X-Api-Key" to "secret-key")),
            httpClient = client
        )
        adapter.sendOffer(sampleOffer)
    }

    @Test
    fun `WSA-09 None auth sends no auth headers`() = runTest {
        val mockEngine = MockEngine { request ->
            assertNull(request.headers[HttpHeaders.Authorization])
            assertNull(request.headers[HttpHeaders.Cookie])
            respond(content = sampleAnswer, status = HttpStatusCode.Created)
        }
        val client = HttpClient(mockEngine)
        val adapter = WhepSignalingAdapter(
            url = "http://s/whep",
            auth = SignalingAuth.None,
            httpClient = client
        )
        adapter.sendOffer(sampleOffer)
    }

    // ── sendIceCandidate ───────────────────────────────────────────────

    @Test
    fun `WSA-10 sendIceCandidate sends trickle-ice-sdpfrag`() = runTest {
        val mockEngine = MockEngine { request ->
            assertEquals("application/trickle-ice-sdpfrag", request.body.contentType?.toString())
            assertEquals(HttpMethod.Patch, request.method)
            respond(content = "", status = HttpStatusCode.NoContent)
        }
        val client = HttpClient(mockEngine)
        val adapter = WhepSignalingAdapter(url = "http://s/whep", httpClient = client)
        adapter.sendIceCandidate(
            resourceUrl = "http://s/resource/1",
            candidate = "a=candidate:1 1 UDP 2130706431 10.0.0.1 9 typ host"
        )
    }

    @Test
    fun `WSA-11 sendIceCandidate includes ufrag and pwd in body`() = runTest {
        var capturedBody = ""
        val mockEngine = MockEngine { request ->
            capturedBody = String(request.body.toByteArray())
            respond(content = "", status = HttpStatusCode.NoContent)
        }
        val client = HttpClient(mockEngine)
        val adapter = WhepSignalingAdapter(url = "http://s/whep", httpClient = client)
        adapter.sendIceCandidate(
            resourceUrl = "http://s/resource/1",
            candidate = "a=candidate:1 1 UDP 2130706431 10.0.0.1 9 typ host",
            iceUfrag = "ufrag1",
            icePwd = "pwd1",
            sdpMid = "0"
        )
        assertTrue(capturedBody.contains("a=ice-ufrag:ufrag1"))
        assertTrue(capturedBody.contains("a=ice-pwd:pwd1"))
        assertTrue(capturedBody.contains("a=mid:0"))
    }

    @Test
    fun `WSA-12 sendIceCandidate throws on non-2xx`() = runTest {
        val mockEngine = MockEngine {
            respond(content = "error", status = HttpStatusCode.InternalServerError)
        }
        val client = HttpClient(mockEngine)
        val adapter = WhepSignalingAdapter(url = "http://s/whep", httpClient = client)
        assertFailsWith<WhepException> {
            adapter.sendIceCandidate(resourceUrl = "http://s/resource/1", candidate = "a=candidate:x")
        }
    }

    // ── terminate ──────────────────────────────────────────────────────

    @Test
    fun `WSA-13 terminate sends DELETE`() = runTest {
        var method: HttpMethod? = null
        val mockEngine = MockEngine { request ->
            method = request.method
            respond(content = "", status = HttpStatusCode.OK)
        }
        val client = HttpClient(mockEngine)
        val adapter = WhepSignalingAdapter(url = "http://s/whep", httpClient = client)
        adapter.terminate("http://s/resource/1")
        assertEquals(HttpMethod.Delete, method)
    }

    @Test
    fun `WSA-14 terminate ignores errors`() = runTest {
        val mockEngine = MockEngine {
            respond(content = "", status = HttpStatusCode.InternalServerError)
        }
        val client = HttpClient(mockEngine)
        val adapter = WhepSignalingAdapter(url = "http://s/whep", httpClient = client)
        // Should not throw
        adapter.terminate("http://s/resource/1")
    }

    // ── Internal helpers ───────────────────────────────────────────────

    @Test
    fun `WSA-15 resolveResourceUrl handles relative path`() {
        val result = resolveResourceUrl("http://host:8889/stream/whep", "/resource/abc")
        assertEquals("http://host:8889/resource/abc", result)
    }

    @Test
    fun `WSA-16 resolveResourceUrl handles absolute URL`() {
        val result = resolveResourceUrl("http://host:8889/whep", "https://other/res/1")
        assertEquals("https://other/res/1", result)
    }

    @Test
    fun `WSA-17 resolveResourceUrl returns null for blank`() {
        assertNull(resolveResourceUrl("http://host/whep", null))
        assertNull(resolveResourceUrl("http://host/whep", ""))
    }

    @Test
    fun `WSA-18 buildSdpFragment includes all fields`() {
        val frag = buildSdpFragment("a=candidate:x", "ufrag1", "pwd1", "0")
        assertTrue(frag.contains("a=ice-ufrag:ufrag1"))
        assertTrue(frag.contains("a=ice-pwd:pwd1"))
        assertTrue(frag.contains("a=mid:0"))
        assertTrue(frag.contains("a=candidate:x"))
        assertTrue(frag.endsWith("\r\n"))
    }

    @Test
    fun `WSA-19 buildSdpFragment omits null fields`() {
        val frag = buildSdpFragment("a=candidate:x", null, null, null)
        assertFalse(frag.contains("ice-ufrag"))
        assertFalse(frag.contains("ice-pwd"))
        assertFalse(frag.contains("a=mid"))
        assertTrue(frag.contains("a=candidate:x"))
    }
}

// Extension to read body bytes from OutgoingContent
private suspend fun io.ktor.http.content.OutgoingContent.toByteArray(): ByteArray {
    return when (this) {
        is io.ktor.http.content.OutgoingContent.ByteArrayContent -> bytes()
        else -> byteArrayOf()
    }
}
