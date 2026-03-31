# SyncAI-Lib-KmpWebRTC

[![Release](https://img.shields.io/github/v/release/Syncrobotic/SyncAI-Lib-KmpWebRTC?style=flat-square)](https://github.com/Syncrobotic/SyncAI-Lib-KmpWebRTC/releases)
[![CI](https://img.shields.io/github/actions/workflow/status/Syncrobotic/SyncAI-Lib-KmpWebRTC/ci.yml?branch=main&style=flat-square&label=CI)](https://github.com/Syncrobotic/SyncAI-Lib-KmpWebRTC/actions/workflows/ci.yml)
[![License](https://img.shields.io/github/license/Syncrobotic/SyncAI-Lib-KmpWebRTC?style=flat-square)](LICENSE)

Kotlin Multiplatform WebRTC SDK for IoT/robotics control scenarios — low-latency video/audio streaming, bidirectional DataChannel communication, and custom signaling support with zero WebRTC boilerplate.

## Table of Contents

- [Architecture Overview](#architecture-overview)
- [Supported Platforms](#supported-platforms)
- [Features](#features)
- [Installation](#installation)
- [Quick Start](#quick-start)
  - [Receive Video (WHEP)](#1-receive-video-whep)
  - [Send Audio (WHIP)](#2-send-audio-whip)
  - [DataChannel Communication](#3-datachannel-communication)
  - [Authentication (JWT, Cookie, API Key)](#4-authentication)
  - [Custom Signaling Adapter](#5-custom-signaling-adapter)
- [Customization & Limitations](#customization--limitations)
- [API Reference](#api-reference)
  - [SignalingAdapter](#signalingadapter)
  - [WhepSession](#whepsession)
  - [WhipSession](#whipsession)
  - [VideoRenderer](#videorenderer)
  - [AudioPushPlayer](#audiopushplayer)
  - [DataChannel](#datachannel)
  - [Configuration](#configuration)
  - [States & Events](#states--events)
- [Application Patterns](#application-patterns)
  - [Compose Lifecycle](#pattern-1-compose-automatic-lifecycle)
  - [ViewModel Lifecycle](#pattern-2-viewmodel-managed-lifecycle)
  - [Service / Background](#pattern-3-service--background)
  - [Bidirectional Communication](#pattern-4-bidirectional-video--audio)
  - [Multi-Stream](#pattern-5-multi-stream-display)
  - [Real-time Stats Monitoring](#pattern-6-real-time-stats-monitoring)
- [Platform Guides](#platform-guides)
- [Lifecycle & Connection Management](#lifecycle--connection-management)
- [Architecture Notes](#architecture-notes)
- [Migration from v1.x](#migration-from-v1x)
- [FAQ](#faq)
- [Build & Publish](#build--publish)

---

## Architecture Overview

The SDK is organized into three layers. Application code interacts with **Layer 2 (Session)** as the primary API, and optionally **Layer 3 (Composables)** for Compose UI integration.

```
┌──────────────────────────────────────────────────────────────┐
│  Layer 3: Composables (UI Convenience)                       │
│  VideoRenderer(session) → VideoPlayerController              │
│  AudioPushPlayer(session) → AudioPushController              │
├──────────────────────────────────────────────────────────────┤
│  Layer 2: Session API (Core Public API)                      │
│  WhepSession  → connect / DataChannel / stats / close        │
│  WhipSession  → connect / DataChannel / mute / stats / close │
├──────────────────────────────────────────────────────────────┤
│  Layer 1: Signaling Adapter                                  │
│  WhepSignalingAdapter(url, headers, httpClient?)             │
│  WhipSignalingAdapter(url, headers, httpClient?)             │
│  Custom: implement SignalingAdapter interface                │
├──────────────────────────────────────────────────────────────┤
│  Internal: WebRTCClient, PeerConnectionFactory, ICE, SDP     │
│  (Not exposed — managed by Session)                          │
└──────────────────────────────────────────────────────────────┘
```

| Layer | Role | When to Use |
|-------|------|-------------|
| **SignalingAdapter** | Handles SDP offer/answer exchange over HTTP | Always needed — choose built-in (WHEP/WHIP) or implement your own |
| **Session** | Manages full WebRTC PeerConnection lifecycle, DataChannel, auto-reconnect, stats | Primary entry point for all connection logic |
| **Composables** | Renders video / captures audio within Compose Multiplatform UI | When using Compose for UI (Android/iOS/Desktop) |

---

## Supported Platforms

| Platform | Status | WebRTC Implementation |
|----------|--------|----------------------|
| Android | ✅ | [webrtc-android SDK](https://github.com/nicokosi/webrtc-android) |
| iOS (Physical Device) | ✅ | [GoogleWebRTC CocoaPod](https://cocoapods.org/pods/GoogleWebRTC) 1.1.31999 |
| iOS Simulator | ❌ | Not supported — GoogleWebRTC has no simulator binaries |
| JVM/Desktop | ✅ | [webrtc-java](https://github.com/nicokosi/webrtc-java) 0.14.0 |
| JavaScript (Browser) | ✅ | Native `RTCPeerConnection` |
| WebAssembly (WasmJS) | ✅ | Native `RTCPeerConnection` |

### Codec Support

| Type | Codecs | Notes |
|------|--------|-------|
| Video | H.264, VP8, VP9 | AV1 on JS/WasmJS (browser dependent) |
| Audio | Opus (primary), G.711 | Opus 48kHz stereo default |

---

## Features

| Feature | Description |
|---------|-------------|
| **WHEP Video/Audio Receive** | Receive streams via standard WHEP protocol |
| **WHIP Audio Send** | Send microphone audio via standard WHIP protocol |
| **DataChannel** | Bidirectional text/JSON and binary (images, files) messaging |
| **Custom Signaling** | Pluggable `SignalingAdapter` — implement any signaling protocol |
| **Built-in Auth** | JWT Bearer, Cookie (app-managed), API key, custom headers |
| **Auto Reconnect** | Configurable exponential backoff with jitter |
| **Real-time Stats** | RTT, packet loss, bitrate, codec via `StateFlow<WebRTCStats?>` |
| **Compose UI** | Cross-platform `VideoRenderer` and `AudioPushPlayer` composables |
| **Session API** | High-level `WhepSession`/`WhipSession` — no SDP/ICE knowledge required |

### Server Compatibility

The built-in `WhepSignalingAdapter` / `WhipSignalingAdapter` work with any server that implements the standard [WHEP](https://www.ietf.org/archive/id/draft-murillo-whep-03.html) / [WHIP](https://www.ietf.org/archive/id/draft-ietf-wish-whip-01.html) protocol. For non-standard signaling, implement the `SignalingAdapter` interface (see [Custom Signaling Adapter](#5-custom-signaling-adapter)).

| Media Server | WHEP (Receive) | WHIP (Send) | Notes |
|-------------|:-:|:-:|-------|
| **MediaMTX** | ✅ | ✅ | Works out of the box with built-in adapters |
| **Cloudflare Stream** | ✅ | ✅ | Works out of the box with built-in adapters |
| **GStreamer** (whipsink/whepsrc) | ✅ | ✅ | Works out of the box with built-in adapters |
| **Dolby.io / Millicast** | ✅ | ✅ | Works out of the box with built-in adapters |
| **OBS Studio** (v30+) | — | ✅ | OBS WHIP output only |
| **Janus Gateway** | ❌ | ❌ | Custom `SignalingAdapter` required (proprietary API) |
| **LiveKit** | ❌ | ❌ | Custom `SignalingAdapter` required (proprietary signaling) |
| **Ant Media Server** | ❌ | ❌ | Custom `SignalingAdapter` required (WebSocket-based) |
| **Custom Backend** | — | — | Implement `SignalingAdapter` for any protocol (WebSocket, gRPC, Firebase, MQTT, etc.) |

---

## Installation

### GitHub Packages

In `settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven {
            url = uri("https://maven.pkg.github.com/Syncrobotic/SyncAI-Lib-KmpWebRTC")
            credentials {
                val localProps = java.util.Properties().apply {
                    val file = rootProject.file("local.properties")
                    if (file.exists()) file.inputStream().use { load(it) }
                }
                username = localProps.getProperty("gpr.user") ?: System.getenv("GITHUB_ACTOR")
                password = localProps.getProperty("gpr.key") ?: System.getenv("GITHUB_TOKEN")
            }
        }
    }
}
```

In `gradle/libs.versions.toml`:

```toml
[versions]
kmp-webrtc = "2.0.0"

[libraries]
kmp-webrtc = { module = "com.syncrobotic:syncai-lib-kmpwebrtc", version.ref = "kmp-webrtc" }
```

In your module `build.gradle.kts`:

```kotlin
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.kmp.webrtc)
        }
    }
}
```

> **Note**: GitHub Packages requires authentication. Generate a PAT with `read:packages` scope.

### Local Build

```bash
git clone https://github.com/Syncrobotic/SyncAI-Lib-KmpWebRTC.git
cd SyncAI-Lib-KmpWebRTC
./gradlew publishToMavenLocal
```

Then add `mavenLocal()` to your repositories.

---

## Quick Start

### 1. Receive Video (WHEP)

The simplest way to display a WebRTC video stream:

```kotlin
import com.syncrobotic.webrtc.session.*
import com.syncrobotic.webrtc.signaling.*
import com.syncrobotic.webrtc.ui.*

@Composable
fun VideoScreen() {
    // 1. Create signaling adapter (once)
    val signaling = remember {
        WhepSignalingAdapter(url = "https://your-server/stream/whep")
    }

    // 2. Create session (once)
    val session = remember { WhepSession(signaling) }

    // 3. Manage lifecycle (VideoRenderer auto-connects; do NOT call session.connect() manually)
    DisposableEffect(session) { onDispose { session.close() } }

    // 4. Render video — auto-connects and sets up video sink internally
    val controller = VideoRenderer(
        session = session,
        modifier = Modifier.fillMaxSize(),
        onStateChange = { state ->
            when (state) {
                is PlayerState.Playing -> println("Video playing")
                is PlayerState.Reconnecting -> println("Reconnecting ${state.attempt}/${state.maxAttempts}")
                is PlayerState.Error -> println("Error: ${state.message}")
                else -> {}
            }
        }
    )
}
```

### 2. Send Audio (WHIP)

Capture microphone audio and send to a WHIP endpoint:

```kotlin
import com.syncrobotic.webrtc.session.*
import com.syncrobotic.webrtc.signaling.*
import com.syncrobotic.webrtc.audio.*

@Composable
fun AudioSendScreen() {
    val signaling = remember {
        WhipSignalingAdapter(url = "https://your-server/audio/whip")
    }

    val session = remember {
        WhipSession(
            signaling = signaling,
            audioConfig = AudioPushConfig(
                enableEchoCancellation = true,
                enableNoiseSuppression = true,
                enableAutoGainControl = true
            )
        )
    }

    LaunchedEffect(session) { session.connect() }
    DisposableEffect(session) { onDispose { session.close() } }

    val controller = AudioPushPlayer(
        session = session,
        autoStart = true,
        onStateChange = { state ->
            when (state) {
                is AudioPushState.Streaming -> println("Audio streaming")
                is AudioPushState.Muted -> println("Muted")
                is AudioPushState.Error -> println("Error: ${state.message}")
                else -> {}
            }
        }
    )

    // Control buttons
    Column {
        Button(onClick = { controller.start() }) { Text("Start") }
        Button(onClick = { controller.stop() }) { Text("Stop") }
        Button(onClick = { controller.toggleMute() }) {
            Text(if (controller.isMuted) "Unmute" else "Mute")
        }
    }
}
```

### 3. DataChannel Communication

Send and receive text/binary data through a WebRTC DataChannel:

```kotlin
import com.syncrobotic.webrtc.session.*
import com.syncrobotic.webrtc.signaling.*
import com.syncrobotic.webrtc.datachannel.*

// Works with either WhepSession or WhipSession
val signaling = WhepSignalingAdapter(url = "https://your-server/stream/whep")
val session = WhepSession(signaling)
session.connect()

// Create a reliable DataChannel
val channel = session.createDataChannel(DataChannelConfig.reliable("commands"))

// Listen for incoming messages
channel?.setListener(object : DataChannelListener {
    override fun onStateChanged(state: DataChannelState) {
        println("DataChannel state: $state")
    }

    override fun onMessage(message: String) {
        println("Received: $message")
    }

    override fun onBinaryMessage(data: ByteArray) {
        println("Received binary: ${data.size} bytes")
    }

    override fun onError(error: Throwable) {
        println("Error: ${error.message}")
    }
})

// Send text messages
if (channel?.state == DataChannelState.OPEN) {
    channel.send("""{"action":"move","direction":"forward","speed":0.5}""")
}

// Send binary data (images, files)
if (channel?.state == DataChannelState.OPEN) {
    val imageBytes: ByteArray = loadImage()
    channel.sendBinary(imageBytes)
}

// Cleanup
channel?.close()
session.close()
```

### 4. Authentication

The library provides type-safe `SignalingAuth` for common authentication patterns — including **native cookie support**.

> **Design principle**: The library does NOT perform login. Your app is responsible for authentication; the library only attaches auth credentials to signaling HTTP requests.

#### JWT Bearer Token

```kotlin
val signaling = WhepSignalingAdapter(
    url = "https://api.example.com/streams/camera-1/whep",
    auth = SignalingAuth.Bearer(token = "eyJhbGciOiJIUzI1NiIs...")
)
```

#### Cookie Auth

Your app handles login and passes the obtained cookies to the library:

```kotlin
// Step 1: App performs login (your own code)
val loginResponse = httpClient.post("https://api.example.com/auth/login") {
    contentType(ContentType.Application.Json)
    setBody(mapOf("username" to "admin", "password" to "secret"))
}
val cookies = loginResponse.setCookie().associate { it.name to it.value }
// e.g. {"session" to "abc123", "csrf_token" to "xyz789"}

// Step 2: Pass cookies to signaling adapter
val signaling = WhepSignalingAdapter(
    url = "https://api.example.com/streams/camera-1/whep",
    auth = SignalingAuth.Cookies(cookies)
)

// Step 3: Library attaches Cookie header to all signaling requests
val session = WhepSession(signaling)
session.connect()
```

```kotlin
// Works with any login method — OAuth, SSO, form login, etc.
val oauthCookies = myOAuthFlow.getSessionCookies()
val signaling = WhipSignalingAdapter(
    url = "https://api.example.com/streams/audio/whip",
    auth = SignalingAuth.Cookies(oauthCookies)
)
```

**Cookie Auth Flow:**
```
App                                    Library
─────                                  ───────
1. POST /auth/login + credentials
   → 200 OK + Set-Cookie: session=abc
2. Extract cookies from response
3. Create SignalingAuth.Cookies(cookies)
   Pass to WhepSignalingAdapter         
                                       4. POST /whep + Cookie: session=abc + SDP
                                          → 201 + SDP answer
                                       5. PATCH /resource + Cookie: session=abc
                                          (ICE candidates)
                                       6. DELETE /resource + Cookie: session=abc
                                          (terminate)

Cookie expired (401):
                                       7. SessionState.Error(isRetryable = true)
8. App re-logins, gets new cookies
9. App creates new adapter + session
```

#### Cookie Expiry Handling

When a signaling request receives HTTP 401, the session reports `SessionState.Error(isRetryable = true)`. Your app re-logins and creates a new session:

```kotlin
class StreamViewModel : ViewModel() {
    private val authRepo: AuthRepository
    var session: WhepSession? = null

    fun connect() {
        viewModelScope.launch {
            val cookies = authRepo.getSessionCookies()
            val signaling = WhepSignalingAdapter(
                url = "https://api.example.com/streams/cam/whep",
                auth = SignalingAuth.Cookies(cookies)
            )
            session = WhepSession(signaling).also { s ->
                s.connect()
                s.state.collect { state ->
                    if (state is SessionState.Error && state.isRetryable) {
                        // Possibly 401 — re-login and reconnect
                        session?.close()
                        authRepo.refreshSession()
                        connect()
                    }
                }
            }
        }
    }
}
```

#### API Key / Custom Headers

```kotlin
val signaling = WhepSignalingAdapter(
    url = "https://api.example.com/streams/main/whep",
    auth = SignalingAuth.Custom(
        headers = mapOf(
            "X-API-Key" to "your-api-key",
            "X-Device-Id" to "robot-001",
            "X-Tenant" to "factory-a"
        )
    )
)
```

#### Shared Cookie Storage (Advanced)

When your app and the library need to share the same cookie jar (e.g. SSO session with automatic `Set-Cookie` handling):

```kotlin
import io.ktor.client.plugins.cookies.*

// App-managed CookiesStorage — shared between your app's HttpClient and the library
val sharedStorage = AcceptAllCookiesStorage()  // or your custom implementation

val signaling = WhepSignalingAdapter(
    url = "https://sso-server.example.com/stream/whep",
    auth = SignalingAuth.CookieStorage(storage = sharedStorage)
)
// Library installs HttpCookies plugin using sharedStorage.
// Automatic Set-Cookie handling, CSRF token tracking, etc.
```

> **When to use which?**
> - `Cookies(map)` — Simple, stateless, no plugin. Best for CLI tools, tests, or when cookies don't change mid-session.
> - `CookieStorage(storage)` — Stateful, plugin-based. Best when app and library share cookies, or server issues `Set-Cookie` during signaling.

#### No Authentication

```kotlin
// Default — no auth headers, no plugins installed
val signaling = WhepSignalingAdapter(
    url = "https://open-server/stream/whep"
    // auth defaults to SignalingAuth.None
)
```

#### Custom HttpClient Injection

For advanced control over HTTP behavior (custom timeout, SSL pinning, interceptors), inject your own Ktor `HttpClient`. Auth is still handled by the library.

> When `httpClient` is provided, the library uses it **as-is** — no extra plugins are installed (unless you explicitly use `SignalingAuth.CookieStorage`). If your httpClient already has its own `HttpCookies` plugin configured, use `SignalingAuth.None` or `Bearer` to avoid conflicts.

```kotlin
val customHttpClient = HttpClient(OkHttp) {
    install(HttpTimeout) {
        requestTimeoutMillis = 30_000
        connectTimeoutMillis = 10_000
    }
    engine {
        config {
            // custom OkHttp settings, SSL pinning, etc.
        }
    }
}

val signaling = WhepSignalingAdapter(
    url = "https://api.example.com/streams/main/whep",
    auth = SignalingAuth.Bearer(token = jwt),
    httpClient = customHttpClient  // inject your own HttpClient
)
```

### 5. Custom Signaling Adapter

Implement `SignalingAdapter` for any signaling protocol (WebSocket, Firebase, MQTT, gRPC, etc.).

> **WebSocket signaling**: v2.0 focuses on standard WHEP/WHIP over HTTP. The existing `WebSocketSignaling` class is deprecated but **not removed**. A built-in `WebSocketSignalingAdapter` is planned for **v2.1**. In the meantime, you can wrap the existing class or implement your own:

#### WebSocket Example

```kotlin
import com.syncrobotic.webrtc.signaling.SignalingAdapter
import com.syncrobotic.webrtc.signaling.SignalingResult
import io.ktor.client.plugins.websocket.*
import io.ktor.websocket.*

class WebSocketSignalingAdapter(
    private val wsUrl: String,
    private val streamName: String = "raw",
    private val httpClient: HttpClient = HttpClient { install(WebSockets) }
) : SignalingAdapter {

    private var session: WebSocketSession? = null

    override suspend fun sendOffer(sdpOffer: String): SignalingResult {
        val ws = httpClient.webSocketSession(wsUrl)
        session = ws
        // Send offer
        ws.send(Frame.Text(Json.encodeToString(
            mapOf("type" to "offer", "sdp" to sdpOffer, "stream" to streamName)
        )))
        // Wait for answer
        val frame = ws.incoming.receive() as Frame.Text
        val msg = Json.decodeFromString<Map<String, String>>(frame.readText())
        return SignalingResult(
            sdpAnswer = msg["sdp"] ?: error("No SDP in answer"),
            resourceUrl = msg["resourceUrl"]
        )
    }

    override suspend fun sendIceCandidate(
        resourceUrl: String, candidate: String,
        sdpMid: String?, sdpMLineIndex: Int,
        iceUfrag: String?, icePwd: String?
    ) {
        session?.send(Frame.Text(Json.encodeToString(
            mapOf("type" to "ice", "candidate" to candidate,
                  "sdpMid" to (sdpMid ?: ""), "sdpMLineIndex" to "$sdpMLineIndex")
        )))
    }

    override suspend fun terminate(resourceUrl: String) {
        session?.close()
        session = null
    }
}

// Usage — same Session API, different transport
val signaling = WebSocketSignalingAdapter(wsUrl = "wss://server/signaling")
val session = WhepSession(signaling)
session.connect()
```

> **When to use which?**
> - `WhepSignalingAdapter` / `WhipSignalingAdapter` — Standard WHEP/WHIP servers (Cloudflare, MediaMTX, Janus, OBS WHIP)
> - `WebSocketSignalingAdapter` (custom) — Custom backends with WebSocket-based signaling
> - `FirebaseSignalingAdapter` (custom) — Serverless / P2P scenarios with Firestore

#### Firebase Example

```kotlin
import com.syncrobotic.webrtc.signaling.SignalingAdapter
import com.syncrobotic.webrtc.signaling.SignalingResult

class FirebaseSignalingAdapter(
    private val roomId: String,
    private val firestore: FirebaseFirestore
) : SignalingAdapter {

    override suspend fun sendOffer(sdpOffer: String): SignalingResult {
        // 1. Write offer to Firestore
        val docRef = firestore.collection("rooms").document(roomId)
        docRef.set(mapOf("offer" to sdpOffer)).await()

        // 2. Wait for answer
        val answer = docRef.addSnapshotListener { snapshot, _ ->
            snapshot?.getString("answer")
        }.awaitFirst()

        return SignalingResult(
            sdpAnswer = answer,
            resourceUrl = "rooms/$roomId",  // used for terminate
            etag = null,
            iceServers = emptyList()
        )
    }

    override suspend fun sendIceCandidate(
        resourceUrl: String,
        candidate: String,
        sdpMid: String?,
        sdpMLineIndex: Int,
        iceUfrag: String?,
        icePwd: String?
    ) {
        firestore.collection("rooms").document(roomId)
            .collection("candidates")
            .add(mapOf(
                "candidate" to candidate,
                "sdpMid" to sdpMid,
                "sdpMLineIndex" to sdpMLineIndex
            ))
    }

    override suspend fun terminate(resourceUrl: String) {
        firestore.collection("rooms").document(roomId).delete().await()
    }
}

// Usage — same Session API, different signaling
val signaling = FirebaseSignalingAdapter(roomId = "room-123", firestore)
val session = WhepSession(signaling)
session.connect()
```

---

## Customization & Limitations

### What You Can Customize

| Setting | How | Example |
|---------|-----|---------|
| **Signaling endpoint** (IP, port, path) | Full URL in adapter constructor | `WhipSignalingAdapter(url = "https://192.168.1.100:9090/custom/path/whip")` |
| **STUN/TURN servers** | `WebRTCConfig.iceServers` | `IceServer(urls = listOf("turn:10.0.0.5:3478"), username, credential)` |
| **ICE mode** | `WebRTCConfig.iceMode` | `IceMode.FULL_ICE` or `IceMode.TRICKLE_ICE` |
| **ICE gathering timeout** | `WebRTCConfig.iceGatheringTimeoutMs` | `10_000L` (default 10s) |
| **ICE transport policy** | `WebRTCConfig.iceTransportPolicy` | `"all"` or `"relay"` (force TURN) |
| **Bundle / RTCP mux policy** | `WebRTCConfig.bundlePolicy`, `rtcpMuxPolicy` | `"max-bundle"`, `"require"` |
| **Authentication** | `SignalingAuth` sealed interface | Bearer, Cookies, CookieStorage, Custom headers |
| **HTTP behavior** | Inject custom `HttpClient` | Timeout, SSL pinning, interceptors |
| **Signaling protocol** | Implement `SignalingAdapter` | WebSocket, Firebase, MQTT, gRPC, etc. |
| **Audio processing** | `AudioPushConfig` | Echo cancellation, noise suppression, auto gain |
| **Retry strategy** | `RetryConfig` | Max retries, delays, backoff factor, jitter |
| **DataChannel reliability** | `DataChannelConfig` presets | `reliable()`, `unreliable()`, `maxLifetime()` |

### Known Limitations

| Limitation | Details | Workaround |
|-----------|---------|------------|
| **No dynamic URL change** | `url` is immutable after adapter creation | `session.close()` → create new adapter + session |
| **No video codec preference** | Cannot specify H.264 vs VP8 priority | Uses platform default negotiation |
| **No audio sample rate control** | Uses platform default (typically Opus 48kHz) | N/A — Opus auto-negotiates |
| **No SDP manipulation** | SDP munging not exposed | Implement custom `SignalingAdapter` to modify SDP in `sendOffer()` |
| **No dynamic auth refresh** | Auth tokens are set at adapter creation | Close session → create new adapter with fresh token |
| **iOS Simulator** | GoogleWebRTC has no simulator binaries | Use physical device |

> **Design rationale**: Adapters and sessions are intentionally immutable after creation. This prevents mid-connection state corruption and ensures thread safety across coroutine scopes. To change any connection parameter, close the current session and create a new one.

---

## API Reference

### SignalingAdapter

The pluggable interface for SDP offer/answer exchange. Built-in implementations: `WhepSignalingAdapter`, `WhipSignalingAdapter`.

```kotlin
interface SignalingAdapter {
    /** Exchange SDP offer → answer with the signaling server. */
    suspend fun sendOffer(sdpOffer: String): SignalingResult

    /** Send a trickle ICE candidate (for Trickle ICE mode). */
    suspend fun sendIceCandidate(
        resourceUrl: String,
        candidate: String,
        sdpMid: String?,
        sdpMLineIndex: Int,
        iceUfrag: String?,
        icePwd: String?
    )

    /** Terminate the signaling session (e.g. HTTP DELETE). */
    suspend fun terminate(resourceUrl: String)
}

data class SignalingResult(
    val sdpAnswer: String,
    val resourceUrl: String?,
    val etag: String?,
    val iceServers: List<IceServer> = emptyList()
)
```

#### SignalingAuth

Type-safe authentication configuration for built-in signaling adapters.

The library does **not** perform login — your app handles authentication and provides the credentials (tokens, cookies) to the library.

```kotlin
sealed interface SignalingAuth {
    /** JWT or OAuth Bearer token → Authorization: Bearer <token> */
    data class Bearer(val token: String) : SignalingAuth

    /**
     * Pre-obtained cookies from your app's login flow.
     * Library attaches these as a Cookie header string — no Ktor plugin installed.
     * Suitable for standalone / CLI / test scenarios that don't share cookie storage.
     *
     * On HTTP 401: Session reports SessionState.Error(isRetryable = true).
     * App should re-login and create a new adapter + session.
     *
     * @param cookies  Cookie name → value pairs obtained from your app's login
     */
    data class Cookies(val cookies: Map<String, String>) : SignalingAuth

    /**
     * Shared cookie storage — installs Ktor HttpCookies plugin with the given storage.
     * Use this when app and library need to share the same cookie jar (e.g. SSO session,
     * automatic Set-Cookie handling, CSRF tokens).
     *
     * Requires dependency: io.ktor:ktor-client-plugins (HttpCookies)
     *
     * @param storage  Ktor CookiesStorage instance managed by your app
     */
    data class CookieStorage(val storage: CookiesStorage) : SignalingAuth

    /** Arbitrary static HTTP headers (API keys, device IDs, etc.) */
    data class Custom(val headers: Map<String, String>) : SignalingAuth

    /** No authentication — no extra headers, no plugins installed. */
    data object None : SignalingAuth
}
```

#### WhepSignalingAdapter

Standard WHEP (WebRTC-HTTP Egress Protocol) signaling for **receiving** streams.

```kotlin
class WhepSignalingAdapter(
    url: String,                                    // WHEP endpoint URL
    auth: SignalingAuth = SignalingAuth.None,        // Authentication method
    httpClient: HttpClient? = null                  // Optional: inject custom Ktor HttpClient
) : SignalingAdapter
```

#### WhipSignalingAdapter

Standard WHIP (WebRTC-HTTP Ingress Protocol) signaling for **sending** streams.

```kotlin
class WhipSignalingAdapter(
    url: String,                                    // WHIP endpoint URL
    auth: SignalingAuth = SignalingAuth.None,        // Authentication method
    httpClient: HttpClient? = null                  // Optional: inject custom Ktor HttpClient
) : SignalingAdapter
```

> **HttpClient plugin guarantee**: `SignalingAuth.None`, `Bearer`, `Cookies`, and `Custom` **never** install the Ktor `HttpCookies` plugin — they only manipulate HTTP headers. This ensures zero conflicts when you inject a custom `httpClient`.
>
> Only `SignalingAuth.CookieStorage` installs the `HttpCookies` plugin (using the `CookiesStorage` you provide), enabling shared cookie jar and automatic `Set-Cookie` handling.

---

### WhepSession

Manages a WebRTC connection for **receiving** video/audio. Handles the full PeerConnection lifecycle: SDP negotiation, ICE gathering, auto-reconnect, stats collection.

> **Audio playback**: Incoming audio is automatically played through the device speaker — no extra composable needed. Use `setAudioEnabled(false)` to mute and `setSpeakerphoneEnabled(false)` to route audio to the earpiece/headphones (Android/iOS only). Default is **speaker ON**.

```kotlin
expect class WhepSession(
    signaling: SignalingAdapter,
    config: WebRTCConfig = WebRTCConfig.DEFAULT,
    retryConfig: RetryConfig = RetryConfig.DEFAULT
) {
    /** Reactive connection state. */
    val state: StateFlow<SessionState>

    /** Reactive connection statistics (updated every ~1s while connected). */
    val stats: StateFlow<WebRTCStats?>

    /** Establish the WebRTC connection. Suspend until connected or failed. */
    suspend fun connect()

    /** Create a DataChannel on this connection. Call after connect(). */
    fun createDataChannel(config: DataChannelConfig): DataChannel?

    /** Enable/disable incoming audio playback. */
    fun setAudioEnabled(enabled: Boolean)

    /** Toggle speakerphone output (Android/iOS only). */
    fun setSpeakerphoneEnabled(enabled: Boolean)

    /** Close the connection and release all resources. */
    fun close()
}
```

**Usage without Compose** (ViewModel, Service, etc.):

```kotlin
val session = WhepSession(
    signaling = WhepSignalingAdapter(url, headers),
    config = WebRTCConfig.DEFAULT,
    retryConfig = RetryConfig.DEFAULT
)

// Observe state reactively
session.state.collect { state ->
    when (state) {
        is SessionState.Connected -> println("Connected!")
        is SessionState.Reconnecting -> println("Retry ${state.attempt}/${state.maxAttempts}")
        is SessionState.Error -> println("Error: ${state.message}, retryable: ${state.isRetryable}")
        else -> {}
    }
}
```

---

### WhipSession

Manages a WebRTC connection for **sending** audio (microphone capture). Handles audio capture, encoding, WHIP signaling, auto-reconnect.

```kotlin
expect class WhipSession(
    signaling: SignalingAdapter,
    audioConfig: AudioPushConfig = AudioPushConfig(),
    retryConfig: RetryConfig = RetryConfig.DEFAULT
) {
    /** Reactive connection state. */
    val state: StateFlow<SessionState>

    /** Reactive connection statistics. */
    val stats: StateFlow<WebRTCStats?>

    /** Establish the WebRTC connection and start audio capture. */
    suspend fun connect()

    /** Create a DataChannel on this connection. Call after connect(). */
    fun createDataChannel(config: DataChannelConfig): DataChannel?

    /** Mute/unmute the microphone. */
    fun setMuted(muted: Boolean)

    /** Toggle mute state. */
    fun toggleMute()

    /** Close the connection and release all resources (microphone, PeerConnection). */
    fun close()
}
```

---

### VideoRenderer

Compose Multiplatform composable for rendering video from a `WhepSession`. Works on Android, iOS, and JVM/Desktop.

> **Important**: `VideoRenderer` **auto-connects** the session internally. Do **not** call `session.connect()` manually when using `VideoRenderer` — this causes a race condition where the video sink is not attached, resulting in a black screen. Only use `DisposableEffect` for cleanup.

```kotlin
@Composable
expect fun VideoRenderer(
    session: WhepSession,
    modifier: Modifier = Modifier,
    onStateChange: OnPlayerStateChange = {},
    onEvent: OnPlayerEvent = {}
): VideoPlayerController
```

**Returns** a `VideoPlayerController` for programmatic control:

```kotlin
interface VideoPlayerController {
    fun play()
    fun pause()
    fun stop()
    fun seekTo(positionMs: Long)
    val currentPosition: Long
    val duration: Long
    val isPlaying: Boolean
}
```

**Built-in connection status overlay** — automatically displays connecting/reconnecting/error states with appropriate icons and messages. No extra UI code needed.

---

### AudioPushPlayer

Compose Multiplatform composable for sending microphone audio via a `WhipSession`.

```kotlin
@Composable
expect fun AudioPushPlayer(
    session: WhipSession,
    autoStart: Boolean = false,
    onStateChange: OnAudioPushStateChange = {}
): AudioPushController
```

**Returns** an `AudioPushController`:

```kotlin
interface AudioPushController {
    val state: AudioPushState
    val isStreaming: Boolean
    val isMuted: Boolean
    val isConnected: Boolean
    val stats: WebRTCStats?

    fun start()
    fun stop()
    fun setMuted(muted: Boolean)
    fun toggleMute()
    suspend fun refreshStats()
}
```

---

### DataChannel

Bidirectional messaging channel for text and binary data. Created from a connected session.

```kotlin
expect class DataChannel {
    val label: String
    val id: Int
    val state: DataChannelState
    val bufferedAmount: Long

    fun setListener(listener: DataChannelListener?)
    fun send(message: String): Boolean
    fun sendBinary(data: ByteArray): Boolean
    fun close()
}
```

#### DataChannelConfig

```kotlin
data class DataChannelConfig(
    val label: String,
    val ordered: Boolean = true,
    val maxRetransmits: Int? = null,
    val maxPacketLifeTimeMs: Int? = null,
    val protocol: String = "",
    val negotiated: Boolean = false,
    val id: Int? = null
) {
    companion object {
        /** Reliable, ordered delivery (like TCP). */
        fun reliable(label: String): DataChannelConfig

        /** Unreliable delivery for lowest latency (like UDP). */
        fun unreliable(label: String, maxRetransmits: Int = 0): DataChannelConfig

        /** Time-limited delivery — packets dropped after timeout. */
        fun maxLifetime(label: String, maxPacketLifeTimeMs: Int): DataChannelConfig
    }
}
```

#### DataChannelListener

```kotlin
interface DataChannelListener {
    fun onStateChanged(state: DataChannelState)
    fun onMessage(message: String)
    fun onBinaryMessage(data: ByteArray) {}
    fun onBufferedAmountChange(bufferedAmount: Long) {}
    fun onError(error: Throwable) {}
}
```

#### DataChannelState

```kotlin
enum class DataChannelState {
    CONNECTING, OPEN, CLOSING, CLOSED
}
```

---

### Configuration

#### WebRTCConfig

PeerConnection-level configuration. Normally use the presets.

```kotlin
data class WebRTCConfig(
    val iceServers: List<IceServer> = IceServer.DEFAULT_ICE_SERVERS,
    val iceMode: IceMode = IceMode.FULL_ICE,
    val iceGatheringTimeoutMs: Long = 10_000L,
    val iceTransportPolicy: String = "all",
    val bundlePolicy: String = "max-bundle",
    val rtcpMuxPolicy: String = "require"
) {
    companion object {
        val DEFAULT: WebRTCConfig       // Standard receiver config
        val SENDER: WebRTCConfig        // Optimized for sending
    }
}
```

#### IceServer

```kotlin
data class IceServer(
    val urls: List<String>,
    val username: String? = null,
    val credential: String? = null
) {
    companion object {
        val GOOGLE_STUN: IceServer                  // stun:stun.l.google.com:19302
        val DEFAULT_ICE_SERVERS: List<IceServer>    // [GOOGLE_STUN]
    }
}

// TURN server example
val turnServer = IceServer(
    urls = listOf("turn:turn.example.com:3478"),
    username = "user",
    credential = "password"
)
```

#### AudioPushConfig

Audio-specific settings for `WhipSession`.

```kotlin
data class AudioPushConfig(
    val enableEchoCancellation: Boolean = true,
    val enableNoiseSuppression: Boolean = true,
    val enableAutoGainControl: Boolean = true,
    val webrtcConfig: WebRTCConfig = WebRTCConfig.SENDER
) {
    /** Disable all audio processing (raw audio). */
    fun withoutAudioProcessing(): AudioPushConfig
}
```

#### RetryConfig

Exponential backoff retry configuration, shared by all session types.

```kotlin
data class RetryConfig(
    val maxRetries: Int = 5,
    val initialDelayMs: Long = 1000L,
    val maxDelayMs: Long = 45000L,
    val backoffFactor: Double = 2.0,
    val retryOnDisconnect: Boolean = true,
    val retryOnError: Boolean = true,
    val jitterFactor: Double = 0.1
) {
    companion object {
        val DEFAULT: RetryConfig        // 5 retries, 1s → 45s
        val AGGRESSIVE: RetryConfig     // 10 retries, 500ms → 60s
        val DISABLED: RetryConfig       // No retries
    }
}
```

---

### States & Events

#### SessionState

Connection state of `WhepSession` / `WhipSession`, exposed as `StateFlow<SessionState>`.

```kotlin
sealed class SessionState {
    /** Session created, not yet connected. */
    data object Idle : SessionState()

    /** Establishing WebRTC connection (SDP/ICE negotiation). */
    data object Connecting : SessionState()

    /** WebRTC connected, media flowing. */
    data object Connected : SessionState()

    /** Connection lost, attempting reconnection. */
    data class Reconnecting(
        val attempt: Int,
        val maxAttempts: Int
    ) : SessionState()

    /** Connection error. Check isRetryable for recovery possibility. */
    data class Error(
        val message: String,
        val cause: Throwable? = null,
        val isRetryable: Boolean = true
    ) : SessionState()

    /** Session closed. Terminal state — create a new session to reconnect. */
    data object Closed : SessionState()
}
```

#### PlayerState

Video rendering state, reported by `VideoRenderer` via `onStateChange`.

| State | Description |
|-------|-------------|
| `Idle` | Initial state |
| `Connecting` | Establishing connection |
| `Loading` | Connection established, waiting for first frame |
| `Playing` | Video frames rendering |
| `Paused` | Playback paused |
| `Buffering(percent)` | Temporary interruption |
| `Reconnecting(attempt, maxAttempts, reason, nextRetryMs)` | Auto-reconnecting |
| `Error(message, cause)` | Error occurred |
| `Stopped` | Playback stopped |

#### AudioPushState

Audio sending state, reported by `AudioPushPlayer` via `onStateChange`.

| State | Description |
|-------|-------------|
| `Idle` | Initial state |
| `Connecting` | Establishing WHIP connection |
| `Streaming` | Audio is being sent |
| `Muted` | Connected but microphone muted |
| `Reconnecting(attempt, maxAttempts)` | Auto-reconnecting |
| `Error(message, cause, isRetryable)` | Error occurred |
| `Disconnected` | Disconnected |

#### PlayerEvent

Video events reported by `VideoRenderer` via `onEvent`.

| Event | Description |
|-------|-------------|
| `FirstFrameRendered(timestampMs)` | First video frame displayed |
| `StreamInfoReceived(info)` | Stream metadata (resolution, FPS, codec, bitrate) |
| `BitrateChanged(bitrate)` | Bitrate changed |
| `FrameReceived(timestampMs)` | Frame received |

#### WebRTCStats

Connection statistics, available as `StateFlow<WebRTCStats?>` on sessions.

```kotlin
data class WebRTCStats(
    val audioBitrate: Long = 0,
    val roundTripTimeMs: Double = 0.0,
    val jitterMs: Double = 0.0,
    val packetsSent: Long = 0,
    val packetsLost: Long = 0,
    val codec: String = "unknown",
    val timestampMs: Long = 0
) {
    val packetLossPercent: Double    // Calculated: packetsLost / packetsSent * 100
    val bitrateDisplay: String      // e.g. "1.2 Mbps"
    val latencyDisplay: String      // e.g. "12 ms"
}
```

---

## Application Patterns

### Pattern 1: Compose Automatic Lifecycle

Session lifecycle tied to the composable. Simplest approach.

```kotlin
@Composable
fun CameraView(streamUrl: String) {
    val signaling = remember { WhepSignalingAdapter(url = streamUrl) }
    val session = remember { WhepSession(signaling) }

    // VideoRenderer auto-connects; do NOT call session.connect() manually
    DisposableEffect(session) { onDispose { session.close() } }

    // Observe state for UI feedback
    val sessionState by session.state.collectAsState()

    Column {
        // Video — auto-connects on first composition
        VideoRenderer(
            session = session,
            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)
        )

        // Status
        Text(text = when (sessionState) {
            is SessionState.Connected -> "Live"
            is SessionState.Connecting -> "Connecting..."
            is SessionState.Reconnecting -> "Reconnecting..."
            is SessionState.Error -> "Error: ${(sessionState as SessionState.Error).message}"
            else -> "Idle"
        })
    }
}
```

### Pattern 2: ViewModel-Managed Lifecycle

**Recommended for production.** Session outlives Composable recompositions; cleanup on ViewModel destruction.

```kotlin
class RobotControlViewModel : ViewModel() {
    // Signaling with JWT auth
    private val signaling = WhepSignalingAdapter(
        url = "https://api.syncrobotic.com/robots/arm-01/camera/whep",
        auth = SignalingAuth.Bearer(token = jwtToken)
    )

    // Session — lives as long as ViewModel
    val session = WhepSession(
        signaling = signaling,
        config = WebRTCConfig(
            iceServers = listOf(
                IceServer.GOOGLE_STUN,
                IceServer(
                    urls = listOf("turn:turn.syncrobotic.com:3478"),
                    username = "robot",
                    credential = "secret"
                )
            )
        ),
        retryConfig = RetryConfig.AGGRESSIVE
    )

    // DataChannel for robot commands
    var commandChannel: DataChannel? = null
        private set

    // Expose states
    val connectionState = session.state
    val networkStats = session.stats

    init {
        viewModelScope.launch {
            session.connect()

            // Create DataChannel after connection
            commandChannel = session.createDataChannel(
                DataChannelConfig.reliable("robot-commands")
            )
            commandChannel?.setListener(object : DataChannelListener {
                override fun onMessage(message: String) {
                    // Handle robot responses
                    println("Robot response: $message")
                }
                override fun onStateChanged(state: DataChannelState) {}
            })
        }
    }

    fun sendCommand(action: String, params: Map<String, Any> = emptyMap()) {
        val json = buildJsonObject {
            put("action", action)
            params.forEach { (k, v) ->
                when (v) {
                    is Number -> put(k, v.toDouble())
                    is String -> put(k, v)
                    is Boolean -> put(k, v)
                }
            }
        }.toString()
        commandChannel?.send(json)
    }

    fun moveForward() = sendCommand("move", mapOf("direction" to "forward", "speed" to 0.5))
    fun stop() = sendCommand("stop")
    fun rotateCamera(pan: Double, tilt: Double) =
        sendCommand("camera", mapOf("pan" to pan, "tilt" to tilt))

    override fun onCleared() {
        commandChannel?.close()
        session.close()
    }
}

// Composable — only handles UI
@Composable
fun RobotControlScreen(viewModel: RobotControlViewModel = viewModel()) {
    val state by viewModel.connectionState.collectAsState()
    val stats by viewModel.networkStats.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        // Video feed
        VideoRenderer(
            session = viewModel.session,
            modifier = Modifier.fillMaxWidth().weight(1f),
            onEvent = { event ->
                if (event is PlayerEvent.StreamInfoReceived) {
                    println("${event.info.width}x${event.info.height} @ ${event.info.fps}fps")
                }
            }
        )

        // Stats bar
        stats?.let { s ->
            Text("${s.latencyDisplay} | ${s.bitrateDisplay} | Loss: ${"%.1f".format(s.packetLossPercent)}%")
        }

        // Controls
        Row(horizontalArrangement = Arrangement.SpaceEvenly) {
            Button(onClick = { viewModel.moveForward() }) { Text("Forward") }
            Button(onClick = { viewModel.stop() }) { Text("Stop") }
            Button(onClick = { viewModel.rotateCamera(10.0, 0.0) }) { Text("Pan Right") }
        }
    }
}
```

### Pattern 3: Service / Background

For scenarios that need audio streaming without UI (e.g., Android Service, iOS Background Task):

```kotlin
// Android Service example
class AudioStreamingService : Service() {
    private lateinit var session: WhipSession
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        val signaling = WhipSignalingAdapter(
            url = "https://api.example.com/audio/whip",
            auth = SignalingAuth.Bearer(token = token)
        )
        session = WhipSession(
            signaling = signaling,
            audioConfig = AudioPushConfig(
                enableNoiseSuppression = true,
                enableAutoGainControl = true
            ),
            retryConfig = RetryConfig.AGGRESSIVE
        )

        serviceScope.launch {
            session.connect()

            // Monitor state
            session.state.collect { state ->
                when (state) {
                    is SessionState.Connected -> updateNotification("Streaming audio")
                    is SessionState.Reconnecting -> updateNotification("Reconnecting...")
                    is SessionState.Error -> updateNotification("Error: ${state.message}")
                    else -> {}
                }
            }
        }
    }

    override fun onDestroy() {
        session.close()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?) = null

    private fun updateNotification(text: String) { /* ... */ }
}
```

### Pattern 4: Bidirectional (Video + Audio)

Receive video and send audio simultaneously — compose `WhepSession` + `WhipSession`:

```kotlin
@Composable
fun BidirectionalScreen(
    videoWhepUrl: String,
    audioWhipUrl: String,
    auth: SignalingAuth
) {
    // Video receive session
    val videoSession = remember {
        WhepSession(
            signaling = WhepSignalingAdapter(url = videoWhepUrl, auth = auth)
        )
    }

    // Audio send session
    val audioSession = remember {
        WhipSession(
            signaling = WhipSignalingAdapter(url = audioWhipUrl, auth = auth),
            audioConfig = AudioPushConfig()
        )
    }

    // Connect both
    LaunchedEffect(Unit) {
        launch { videoSession.connect() }
        launch { audioSession.connect() }
    }

    // Cleanup both
    DisposableEffect(Unit) {
        onDispose {
            videoSession.close()
            audioSession.close()
        }
    }

    // Observe states
    val videoState by videoSession.state.collectAsState()
    val audioState by audioSession.state.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        // Video
        VideoRenderer(
            session = videoSession,
            modifier = Modifier.fillMaxWidth().weight(1f)
        )

        // Audio controls
        val audioController = AudioPushPlayer(
            session = audioSession,
            autoStart = false
        )

        Row {
            Text("Video: ${videoState::class.simpleName}")
            Spacer(Modifier.width(16.dp))
            Text("Audio: ${audioState::class.simpleName}")
        }

        // Push-to-talk
        Button(
            onClick = { },
            modifier = Modifier.pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        audioController.start()
                        tryAwaitRelease()
                        audioController.stop()
                    }
                )
            }
        ) {
            Text("Push to Talk")
        }
    }
}
```

### Pattern 5: Multi-Stream Display

Display multiple camera feeds simultaneously. Each `WhepSession` manages its own `PeerConnection` independently.

```kotlin
data class CameraFeed(val id: String, val name: String, val whepUrl: String)

@Composable
fun MultiCameraScreen(cameras: List<CameraFeed>, auth: SignalingAuth) {
    val sessions = remember(cameras) {
        cameras.map { cam ->
            cam.id to WhepSession(
                signaling = WhepSignalingAdapter(url = cam.whepUrl, auth = auth)
            )
        }.toMap()
    }

    // Connect all
    LaunchedEffect(sessions) {
        sessions.values.forEach { session ->
            launch { session.connect() }
        }
    }

    // Cleanup all
    DisposableEffect(sessions) {
        onDispose { sessions.values.forEach { it.close() } }
    }

    // Grid layout
    val columns = if (cameras.size <= 2) 1 else 2

    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        modifier = Modifier.fillMaxSize()
    ) {
        items(cameras) { camera ->
            val session = sessions[camera.id] ?: return@items
            val state by session.state.collectAsState()

            Column(modifier = Modifier.padding(4.dp)) {
                Text(camera.name, style = MaterialTheme.typography.labelSmall)

                VideoRenderer(
                    session = session,
                    modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)
                )

                Text(
                    text = when (state) {
                        is SessionState.Connected -> "Live"
                        is SessionState.Connecting -> "Connecting..."
                        else -> state::class.simpleName ?: ""
                    },
                    color = if (state is SessionState.Connected) Color.Green else Color.Gray
                )
            }
        }
    }
}

// Usage
MultiCameraScreen(
    cameras = listOf(
        CameraFeed("cam1", "Front Camera", "https://server/cam1/whep"),
        CameraFeed("cam2", "Arm Camera", "https://server/cam2/whep"),
        CameraFeed("cam3", "Rear Camera", "https://server/cam3/whep"),
        CameraFeed("cam4", "Overview", "https://server/cam4/whep")
    ),
    auth = SignalingAuth.Bearer(token = jwt)
)
```

### Pattern 6: Real-time Stats Monitoring

```kotlin
@Composable
fun StatsOverlay(session: WhepSession) {
    val stats by session.stats.collectAsState()
    val state by session.state.collectAsState()

    Column(
        modifier = Modifier
            .background(Color.Black.copy(alpha = 0.6f))
            .padding(8.dp)
    ) {
        Text("State: ${state::class.simpleName}", color = Color.White)

        stats?.let { s ->
            Text("RTT: ${s.latencyDisplay}", color = Color.White)
            Text("Bitrate: ${s.bitrateDisplay}", color = Color.White)
            Text("Packet Loss: ${"%.2f".format(s.packetLossPercent)}%", color = Color.White)
            Text("Jitter: ${"%.1f".format(s.jitterMs)} ms", color = Color.White)
            Text("Codec: ${s.codec}", color = Color.White)
            Text("Packets Sent: ${s.packetsSent}", color = Color.White)
        } ?: Text("No stats", color = Color.Gray)
    }
}

// Compose over video
Box {
    VideoRenderer(session = session, modifier = Modifier.fillMaxSize())
    StatsOverlay(session = session)
}
```

---

## Platform Guides

### Android

#### Permissions

Add to `AndroidManifest.xml`:

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />

<!-- Required for AudioPushPlayer / WhipSession -->
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.MODIFY_AUDIO_SETTINGS" />

<application android:usesCleartextTraffic="true"> <!-- Only for HTTP endpoints -->
```

> For production, use HTTPS and remove `usesCleartextTraffic`. Request `RECORD_AUDIO` at runtime.

### iOS

#### Info.plist

```xml
<key>NSAppTransportSecurity</key>
<dict>
    <key>NSAllowsLocalNetworking</key>
    <true/>
</dict>

<!-- Required for WhipSession / AudioPushPlayer -->
<key>NSMicrophoneUsageDescription</key>
<string>Microphone access required for audio streaming.</string>

<key>NSLocalNetworkUsageDescription</key>
<string>Local network access for streaming server.</string>
```

#### CocoaPods

In your iOS project's `Podfile`:

```ruby
platform :ios, '15.0'
target 'YourApp' do
  use_frameworks!
  pod 'GoogleWebRTC', '1.1.31999'
  pod 'shared', :path => '../shared'
end
```

> GoogleWebRTC does **not** support iOS Simulator. Use a physical device.

### JVM/Desktop

```kotlin
fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "WebRTC Demo") {
        val signaling = remember { WhepSignalingAdapter(url = "https://server/stream/whep") }
        val session = remember { WhepSession(signaling) }

        // VideoRenderer auto-connects; do NOT call session.connect() manually
        DisposableEffect(session) { onDispose { session.close() } }

        VideoRenderer(session = session, modifier = Modifier.fillMaxSize())
    }
}
```

### JavaScript / WasmJS

```kotlin
fun main() {
    if (!WebRTCClient.isSupported()) {
        console.log("Browser does not support WebRTC")
        return
    }

    val signaling = WhepSignalingAdapter(url = "https://server/stream/whep")
    val session = WhepSession(signaling)

    MainScope().launch {
        session.connect()

        session.state.collect { state ->
            document.getElementById("status")?.textContent = state::class.simpleName
        }
    }
}
```

---

## Lifecycle & Connection Management

### Session Internal Lifecycle

All connection logic is encapsulated inside `WhepSession` / `WhipSession`. Application code never handles SDP, ICE, or signaling directly.

```
SessionState transitions:

  Idle ──connect()──► Connecting ──success──► Connected
                         │                       │
                       failure                DISCONNECTED/FAILED
                     (retryable)                  │
                         │                        ▼   (5s debounce)
                         ▼                  Reconnecting(1, max)
                    Error(retryable)              │
                         │                   retry ◄─┘
                    retry ◄──────────────────┘
                         │
                   retries exhausted
                         │
                         ▼
                    Error(terminal) ──► close() ──► Closed
```

| Phase | Managed by | Details |
|-------|-----------|---------|
| PeerConnection creation | Session internal | `PeerConnectionFactory` + platform init |
| SDP offer/answer | Session internal | `WebRTCClient.createOffer()` → `SignalingAdapter.sendOffer()` → `setRemoteAnswer()` |
| ICE gathering | Session internal | Full ICE (wait for all) or Trickle ICE (incremental) |
| Auto-reconnect | Session internal | `StreamRetryHandler` with exponential backoff |
| Stats collection | Session internal | Background job, updates `StateFlow<WebRTCStats?>` every ~1s |
| Resource cleanup | Session `close()` | PeerConnection close → signaling terminate → scope cancel |

### Session Scope Independence

Sessions own their own `CoroutineScope` (with `SupervisorJob`), **independent of Compose lifecycle**. This means:
- Reconnect jobs survive recomposition
- `close()` can be safely called from `DisposableEffect.onDispose`
- Multiple sessions can run concurrently with no shared state

### Who Manages What

| Responsibility | Session | Composable | Application |
|---------------|---------|-----------|-------------|
| PeerConnection lifecycle | ✅ | | |
| SDP/ICE negotiation | ✅ | | |
| Auto-reconnect | ✅ | | |
| Stats collection | ✅ | | |
| Video rendering | | ✅ VideoRenderer | |
| Audio capture/encoding | ✅ (WhipSession) | | |
| Create session | | | ✅ |
| Decide when to connect | | Can auto | ✅ |
| Decide when to close | | DisposableEffect | ✅ (recommended) |
| Create DataChannel | | | ✅ |
| Handle DataChannel messages | | | ✅ |

---

## Architecture Notes

### PeerConnectionFactory Strategy

| Platform | Strategy | Reason |
|----------|----------|--------|
| **Android** | Separate Factory per connection | EglContext bound at Factory creation; sharing causes video decode failures |
| **iOS** | Separate Factory per connection | Consistency; ARC handles memory |
| **JVM** | Shared Factory (reference counted) | webrtc-java has global state; multiple dispose causes crashes |

### Coroutine Scope Design

```
WhepSession / WhipSession
└── sessionScope: CoroutineScope(SupervisorJob() + Dispatchers.Main)
    ├── connectionJob: handles connect + ICE gathering
    ├── reconnectJob: handles auto-reconnect loop
    └── statsJob: periodic stats collection

VideoRenderer (Composable)
└── Uses session's state; only manages rendering lifecycle

AudioPushPlayer (Composable)
└── Uses session's state; delegates all logic to WhipSession
```

`close()` sequence:
1. Cancel `sessionScope` (stops all internal jobs)
2. `WebRTCClient.close()` — synchronous, releases PeerConnection + media resources
3. `SignalingAdapter.terminate()` — fire-and-forget in separate scope
4. State → `Closed`

---

## Migration from v1.x

### Summary of Changes

| v1.x (Deprecated) | v2.x (New) |
|-------------------|------------|
| `WhepSignaling(httpClient)` | `WhepSignalingAdapter(url, auth, httpClient?)` |
| `WhipSignaling(httpClient)` | `WhipSignalingAdapter(url, auth, httpClient?)` |
| Manual `headers = mapOf("Cookie" to ...)` | `SignalingAuth.Cookies(cookies)` — type-safe, app-managed cookie auth |
| Manual `headers = mapOf("Authorization" to ...)` | `SignalingAuth.Bearer(token)` — type-safe JWT |
| `WebSocketSignaling(...)` | Custom `SignalingAdapter` impl (built-in `WebSocketSignalingAdapter` planned v2.1) |
| `StreamConfig(endpoints, protocol, ...)` | `WhepSession(signaling, config)` |
| `AudioPushConfig(whipUrl, ...)` | `WhipSession(signaling, audioConfig)` |
| `BidirectionalConfig(...)` | Compose `WhepSession` + `WhipSession` separately |
| `BidirectionalPlayer(config)` | `VideoRenderer(session)` + `AudioPushPlayer(session)` |
| `VideoRenderer(config)` returns `Unit` | `VideoRenderer(session)` returns `VideoPlayerController` |
| `AudioRetryConfig` | `RetryConfig` (unified) |
| `WebRTCClient` direct use | `WhepSession` / `WhipSession` |
| `getStats()` (manual, suspend) | `session.stats` (reactive `StateFlow`) |

### Migration Example

**Before (v1.x)**:
```kotlin
// Complex: must know SDP, ICE, signaling details
val client = WebRTCClient()
val httpClient = HttpClient(OkHttp)
val whep = WhepSignaling(httpClient)
client.initialize(WebRTCConfig.DEFAULT, listener)
val offer = client.createOffer()
val result = whep.sendOffer("https://server/whep", offer)
client.setRemoteAnswer(result.sdpAnswer)
```

**After (v2.x)**:
```kotlin
// Simple: all internals managed by Session
val session = WhepSession(
    signaling = WhepSignalingAdapter(
        url = "https://server/whep",
        auth = SignalingAuth.Bearer(token = jwt)
    )
)
session.connect()  // That's it — SDP, ICE, signaling all handled
```

### Gradual Migration

All v1.x APIs are `@Deprecated` but still functional. You can migrate incrementally:
1. Replace signaling classes → `WhepSignalingAdapter` / `WhipSignalingAdapter`
2. Replace `StreamConfig` + `VideoRenderer(config)` → `WhepSession` + `VideoRenderer(session)`
3. Replace `AudioPushConfig(whipUrl)` + `AudioPushPlayer(config)` → `WhipSession` + `AudioPushPlayer(session)`
4. Replace `BidirectionalPlayer` → separate `VideoRenderer` + `AudioPushPlayer`
5. Replace direct `WebRTCClient` usage → `WhepSession` / `WhipSession`

Deprecated APIs will be removed in v3.0.

---

## FAQ

### Q: iOS Simulator doesn't work?

GoogleWebRTC does not support iOS Simulator. Use a physical device.

### Q: Android can't connect?

Ensure network permissions are set and `usesCleartextTraffic="true"` for HTTP endpoints.

### Q: How do I add TURN servers?

```kotlin
val config = WebRTCConfig(
    iceServers = listOf(
        IceServer.GOOGLE_STUN,
        IceServer(
            urls = listOf("turn:turn.example.com:3478"),
            username = "user",
            credential = "password"
        )
    )
)
val session = WhepSession(signaling, config)
```

### Q: DataChannel messages have high latency?

Use `DataChannelConfig.unreliable()` to reduce latency at the cost of reliability.

### Q: Can I use DataChannel without video/audio?

Yes. Create a `WhepSession` or `WhipSession`, call `connect()`, then `createDataChannel()`. The DataChannel works independently of media tracks.

### Q: How do I implement my own signaling protocol?

Implement the `SignalingAdapter` interface. See [Custom Signaling Adapter](#5-custom-signaling-adapter) for a Firebase example.

### Q: How do I control audio output (speaker vs earpiece)?

WHEP received audio plays through the **speaker by default**. Use `WhepSession` controls:

```kotlin
session.setAudioEnabled(false)         // Mute incoming audio
session.setSpeakerphoneEnabled(false)  // Switch to earpiece/headphones (Android/iOS)
```

| Platform | Speaker | Earpiece | Headphones/Bluetooth |
|----------|---------|----------|---------------------|
| Android | `setSpeakerphoneEnabled(true)` | `setSpeakerphoneEnabled(false)` | Auto-detected by system |
| iOS | `setSpeakerphoneEnabled(true)` | `setSpeakerphoneEnabled(false)` | Auto-detected via `AVAudioSession` |
| JVM/Desktop | System default (no-op) | N/A | System controlled |
| JS/WasmJS | Browser default (no-op) | N/A | Browser controlled |

For advanced audio device selection (e.g. choosing a specific Bluetooth device), use platform-native APIs (`AudioManager` on Android, `AVAudioSession` on iOS) directly in your app.

### Q: Can multiple sessions run simultaneously?

Yes. Each session has its own PeerConnection, coroutine scope, and signaling. No shared state.

---

## Build & Publish

```bash
# Build all platforms
./gradlew build

# Build specific targets
./gradlew jvmMainClasses          # JVM (fast check)
./gradlew bundleReleaseAar        # Android AAR
./gradlew jsJar                   # JavaScript
./gradlew wasmJsJar               # WebAssembly
./gradlew linkPodReleaseFrameworkIosArm64  # iOS

# Run tests
./gradlew jvmTest

# Publish
./gradlew publishToMavenLocal     # Local Maven
./gradlew publish                 # GitHub Packages
```

---

## Dependencies

| Dependency | Version | Purpose |
|------------|---------|---------|
| Kotlin | 2.3.0 | Language |
| Compose Multiplatform | 1.10.0 | UI framework |
| Ktor | 3.0.3 | HTTP/WebSocket (signaling) |
| WebRTC Android SDK | 125.6422.05 | Android WebRTC |
| GoogleWebRTC CocoaPod | 1.1.31999 | iOS WebRTC |
| webrtc-java | 0.14.0 | JVM WebRTC |
| kotlinx-coroutines | 1.10.2 | Async/Concurrency |

---

## License

MIT License

---

## Contributing

Issues and Pull Requests are welcome!

### Development Setup

After cloning the repository, build the project once to automatically install Git hooks:

```bash
./gradlew build
```

This runs the `installGitHooks` task, which configures `git core.hooksPath` to `.githooks/`. A **pre-push hook** will then run `./gradlew jvmTest` before every `git push` — if tests fail, the push is blocked.

> If you skip the initial build, you can install hooks manually:
> ```bash
> ./gradlew installGitHooks
> ```

### Commit Convention

This project uses [Conventional Commits](https://www.conventionalcommits.org/) (required by release-please):

| Type | Example |
|------|---------|
| `feat:` | `feat: add DataChannelSession` |
| `fix:` | `fix: resolve reconnect on ICE FAILED` |
| `docs:` | `docs: update README examples` |
| `refactor:` | `refactor: extract signaling interface` |
| `test:` | `test: add RetryConfig unit tests` |
| `perf:` | `perf: reduce JVM video memory usage` |
