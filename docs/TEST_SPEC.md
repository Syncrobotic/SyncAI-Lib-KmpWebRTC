# Test Specification — SyncAI-Lib-KmpWebRTC

## Table of Contents

- [Test Strategy](#test-strategy)
- [Test Dependencies (To Add)](#test-dependencies-to-add)
- [Test File Structure](#test-file-structure)
- [Unit Tests](#unit-tests)
  - [1. Config Classes](#1-config-classes)
  - [2. StreamRetryHandler](#2-streamretryhandler)
  - [3. Signaling (WHEP / WHIP / WebSocket)](#3-signaling-whep--whip--websocket)
  - [4. Audio Push](#4-audio-push)
  - [5. Player State & UI Models](#5-player-state--ui-models)
  - [6. DataChannel Config](#6-datachannel-config)
  - [7. WebRTC Data Models](#7-webrtc-data-models)
- [8. v2 Signaling Adapter](#8-v2-signaling-adapter)
- [9. v2 Session State](#9-v2-session-state)
- [E2E Tests](#e2e-tests)
  - [E2E Prerequisites](#e2e-prerequisites)
  - [E2E-1: Video/Audio WHEP (Receive)](#e2e-1-videoaudio-whep-receive)
  - [E2E-2: Audio WHIP (Send)](#e2e-2-audio-whip-send)
  - [E2E-3: DataChannel WHEP](#e2e-3-datachannel-whep)
  - [E2E-4: DataChannel WHIP](#e2e-4-datachannel-whip)
- [E2E Feasibility Analysis](#e2e-feasibility-analysis)
- [Test Summary](#test-summary)

---

## Test Strategy

| Layer | Type | Source Set | Dependencies | Mock/Real |
|-------|------|-----------|--------------|-----------|
| Config / Data classes | Unit | `commonTest` → `jvmTest` | kotlin-test | Pure Kotlin |
| StreamRetryHandler | Unit | `jvmTest` | kotlin-test, coroutines-test | Pure Kotlin |
| Signaling (WHEP/WHIP) | Unit | `jvmTest` | ktor-client-mock | MockEngine |
| WebSocket Signaling | Unit | `jvmTest` | ktor-client-mock | MockEngine |
| State / Sealed classes | Unit | `commonTest` → `jvmTest` | kotlin-test | Pure Kotlin |
| DataChannel config | Unit | `commonTest` → `jvmTest` | kotlin-test | Pure Kotlin |
| v2 SignalingAdapter types | Unit | `jvmTest` | kotlin-test | Pure Kotlin |
| v2 WhepSignalingAdapter | Unit | `jvmTest` | ktor-client-mock | MockEngine |
| v2 WhipSignalingAdapter | Unit | `jvmTest` | ktor-client-mock | MockEngine |
| v2 SessionState | Unit | `jvmTest` | kotlin-test | Pure Kotlin |
| WebRTCClient basic | Integration | `jvmTest` | webrtc-java | Real native lib |
| E2E full flow | E2E | `jvmTest` | webrtc-java + MediaMTX | Real server |

---

## Test Dependencies (To Add)

Add these to `gradle/libs.versions.toml`:

```toml
[libraries]
# Add under existing [libraries] section:
ktor-client-mock = { module = "io.ktor:ktor-client-mock", version.ref = "ktor" }
kotlinx-coroutines-test = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-test", version.ref = "kotlinx-coroutines" }

# Ktor Server (for E2E MockSignalingServer)
ktor-server-core = { module = "io.ktor:ktor-server-core", version.ref = "ktor" }
ktor-server-netty = { module = "io.ktor:ktor-server-netty", version.ref = "ktor" }
ktor-server-content-negotiation = { module = "io.ktor:ktor-server-content-negotiation", version.ref = "ktor" }
```

Add to `build.gradle.kts`:

```kotlin
sourceSets {
    commonTest.dependencies {
        implementation(webrtcLibs.kotlin.test)
        implementation(webrtcLibs.kotlinx.coroutines.test)
    }
    
    val jvmTest by getting {
        dependencies {
            // Unit test
            implementation(webrtcLibs.kotlin.testJunit)
            implementation(webrtcLibs.ktor.client.mock)
            implementation(webrtcLibs.ktor.client.cio)
            
            // E2E: in-process mock WHEP/WHIP server
            implementation(webrtcLibs.ktor.server.core)
            implementation(webrtcLibs.ktor.server.netty)
            implementation(webrtcLibs.ktor.server.content.negotiation)
        }
    }
}
```

---

## Test File Structure

```
src/
├── commonTest/kotlin/com/syncrobotic/webrtc/
│   ├── config/
│   │   ├── RetryConfigTest.kt
│   │   ├── WebRTCConfigTest.kt
│   │   ├── IceServerTest.kt
│   │   ├── StreamConfigTest.kt
│   │   └── BidirectionalConfigTest.kt
│   ├── audio/
│   │   ├── AudioPushConfigTest.kt
│   │   ├── AudioPushStateTest.kt
│   │   └── AudioRetryConfigTest.kt
│   ├── ui/
│   │   ├── PlayerStateTest.kt
│   │   ├── StreamInfoTest.kt
│   │   └── BidirectionalStateTest.kt
│   ├── datachannel/
│   │   └── DataChannelConfigTest.kt
│   ├── WebRTCStatsTest.kt
│   ├── AudioDataTest.kt
│   └── EnumValuesTest.kt
│
└── jvmTest/kotlin/com/syncrobotic/webrtc/
    ├── config/
    │   └── StreamRetryHandlerTest.kt
    ├── signaling/
    │   ├── SignalingAdapterTest.kt
    │   ├── WhepSignalingAdapterTest.kt
    │   ├── WhipSignalingAdapterTest.kt
    │   ├── WhepSignalingTest.kt
    │   ├── WhipSignalingTest.kt
    │   └── WebSocketSignalingTest.kt
    ├── session/
    │   └── SessionStateTest.kt
    └── e2e/
        ├── MockSignalingServer.kt         # Ktor embedded WHEP/WHIP server
        ├── ServerPeerConnection.kt        # Server-side WebRTCClient wrapper
        ├── DataChannelEchoHandler.kt      # Echoes text/binary back
        ├── E2ETestBase.kt                 # Base class: start/stop server
        ├── WhepE2ETest.kt                 # WHEP receive tests
        ├── WhipE2ETest.kt                 # WHIP send tests
        ├── DataChannelWhepE2ETest.kt      # DataChannel via WHEP
        └── DataChannelWhipE2ETest.kt      # DataChannel via WHIP
```

---

## Unit Tests

### 1. Config Classes

#### 1.1 RetryConfig

| ID | Test | Expected |
|----|------|----------|
| RC-01 | Default values | `maxRetries=5, initialDelayMs=1000, maxDelayMs=45000, backoffFactor=2.0, jitterFactor=0.1` |
| RC-02 | `calculateDelay(0)` | ≈1000ms (±10% jitter) |
| RC-03 | `calculateDelay(1)` | ≈2000ms (±10% jitter) |
| RC-04 | `calculateDelay(2)` | ≈4000ms (±10% jitter) |
| RC-05 | `calculateDelay(10)` large attempt | Capped at `maxDelayMs` (45000), never exceeds it |
| RC-06 | `AGGRESSIVE` preset | `maxRetries=10, initialDelayMs=500, maxDelayMs=60000, backoffFactor=1.5` |
| RC-07 | `DISABLED` preset | `maxRetries=0` |
| RC-08 | Delay always ≥ 0 | Even with max negative jitter range, result is `coerceAtLeast(0)` |
| RC-09 | Data class equality | Two `RetryConfig` with same values are equal |

#### 1.2 WebRTCConfig

| ID | Test | Expected |
|----|------|----------|
| WC-01 | `DEFAULT` | `signalingType=WHEP_HTTP, whepEnabled=true, whipEnabled=false, iceMode=FULL_ICE` |
| WC-02 | `SENDER` | `whepEnabled=false, whipEnabled=true` |
| WC-03 | `BIDIRECTIONAL` | `whepEnabled=true, whipEnabled=true` |
| WC-04 | `websocket()` factory | `signalingType=WEBSOCKET`, `wsConfig` is non-null and populated |
| WC-05 | Default ICE servers | Contains Google STUN `stun:stun.l.google.com:19302` |
| WC-06 | `iceGatheringTimeoutMs` default | `10_000L` |

#### 1.3 IceServer

| ID | Test | Expected |
|----|------|----------|
| IS-01 | `GOOGLE_STUN` | `urls=["stun:stun.l.google.com:19302"], username=null, credential=null` |
| IS-02 | `DEFAULT_ICE_SERVERS` | Contains exactly 1 entry = `GOOGLE_STUN` |
| IS-03 | Custom TURN server | Constructor with `username` and `credential` populates correctly |

#### 1.4 WebSocketSignalingConfig

| ID | Test | Expected |
|----|------|----------|
| WS-01 | `create(host="example.com")` | `url="wss://example.com/signaling"` (secure=true default) |
| WS-02 | `create(host="example.com", secure=false)` | `url="ws://example.com/signaling"` |
| WS-03 | `create(host="example.com", path="/ws")` | `url="wss://example.com/ws"` |
| WS-04 | Default `heartbeatIntervalMs` | `30000L` |
| WS-05 | Default `reconnectOnFailure` | `true` |

#### 1.5 ServerEndpoints

| ID | Test | Expected |
|----|------|----------|
| SE-01 | `create(host, streamName, ports)` | Correct `rtsp://`, `http://` URLs |
| SE-02 | Default port constants | `RTSP=8554, HLS=8888, WEBRTC=8889` |

#### 1.6 StreamConfig

| ID | Test | Expected |
|----|------|----------|
| SC-01 | `whepWebRTC(host, streamPath)` | `url` = `"http://host:8889/streamPath"`, `protocol=WEBRTC` |
| SC-02 | `webSocketWebRTC(...)` | `isWebSocketSignaling == true` |
| SC-03 | `url` computed property per protocol | Returns correct endpoint for RTSP / HLS / WEBRTC |
| SC-04 | `requiresLocalMedia` for RECEIVE_ONLY | `false` |
| SC-05 | `requiresLocalMedia` for SEND_ONLY | `true` |
| SC-06 | `requiresLocalMedia` for BIDIRECTIONAL | `true` |
| SC-07 | `fromUrl("http://host:8889/stream")` | Populates `endpoints`, `protocol=WEBRTC` |

#### 1.7 BidirectionalConfig

| ID | Test | Expected |
|----|------|----------|
| BC-01 | `create(host, recv, send)` | `hasVideoReceive=true, hasAudioSend=true, isBidirectional=true` |
| BC-02 | `create(host, recv, send=null)` | `hasAudioSend=false, isBidirectional=false` |
| BC-03 | `videoOnly(host, path)` | `audioConfig=null, hasAudioSend=false` |
| BC-04 | `audioOnly(host, path)` | Video config present, audioConfig non-null |
| BC-05 | WHIP URL format | `audioConfig.whipUrl = "http://host:8889/sendStream/whip"` |
| BC-06 | HTTPS mode | All URLs use `"https://"` when `useHttps=true` |
| BC-07 | Custom ICE servers propagated | Both videoConfig and audioConfig use the provided ICE servers |

---

### 2. StreamRetryHandler

| ID | Test | Expected |
|----|------|----------|
| SRH-01 | Success on first try | Returns result immediately, block called once |
| SRH-02 | Fail once then succeed | Retries, returns success on 2nd attempt |
| SRH-03 | All retries exhausted | Throws `StreamRetryExhaustedException` with `totalAttempts=maxRetries+1` |
| SRH-04 | `CancellationException` thrown | Rethrown immediately, no retry attempted |
| SRH-05 | `IllegalStateException` | Not retryable → thrown immediately |
| SRH-06 | `IllegalArgumentException` | Not retryable → thrown immediately |
| SRH-07 | `UnsupportedOperationException` | Not retryable → thrown immediately |
| SRH-08 | `NotImplementedError` | Not retryable → thrown immediately |
| SRH-09 | `WhepException` | Retryable → retried |
| SRH-10 | `WebSocketSignalingException` | Retryable → retried |
| SRH-11 | `retryOnError=false` config | No exception is retryable, `shouldRetry()` always returns false |
| SRH-12 | `onAttempt` callback args | Called with `(attempt, maxAttempts, delayMs)` for each retry |
| SRH-13 | `onRetryError` callback args | Called with `(attempt, error)` on each retry failure |
| SRH-14 | `StreamRetryExhaustedException` is not retryable | `shouldRetry()` returns false |

---

### 3. Signaling (WHEP / WHIP / WebSocket)

> Tested with **Ktor MockEngine** — no real server needed.

#### 3.1 WhepSignaling

| ID | Test | Expected |
|----|------|----------|
| WHEP-01 | `sendOffer()` → HTTP 201 | Returns `SessionResult` with SDP answer body |
| WHEP-02 | `sendOffer()` → HTTP 200 | Also accepted, returns `SessionResult` |
| WHEP-03 | `sendOffer()` → HTTP 400 | Throws `WhepException` with status code in message |
| WHEP-04 | `sendOffer()` → HTTP 500 | Throws `WhepException` |
| WHEP-05 | `sendOffer()` → network error | Throws `WhepException` wrapping original cause |
| WHEP-06 | Location header (absolute URL) | `resourceUrl` is the exact header value |
| WHEP-07 | Location header (relative path `/session/xyz`) | `resourceUrl` resolved to `"http://host:port/session/xyz"` |
| WHEP-08 | No Location header | `resourceUrl = null` |
| WHEP-09 | ETag header present | `sessionResult.etag` = header value |
| WHEP-10 | Link header with `rel="ice-server"` | Parsed into `iceServers` list |
| WHEP-11 | No Link header | `iceServers` = empty list |
| WHEP-12 | Request Content-Type | `application/sdp` |
| WHEP-13 | Request body | Contains the SDP offer string |
| WHEP-14 | `sendIceCandidate()` → 204 | No exception |
| WHEP-15 | `sendIceCandidate()` → 400 | Throws `WhepException` |
| WHEP-16 | `sendIceCandidate()` SDP fragment format | Contains `a=ice-ufrag:`, `a=ice-pwd:`, `a=mid:`, candidate, `\r\n` terminators |
| WHEP-17 | `sendIceCandidate()` with ETag | Request includes `If-Match` header |
| WHEP-18 | `terminateSession()` → success | No exception |
| WHEP-19 | `terminateSession()` → network error | No exception (errors silently ignored) |
| WHEP-20 | `terminateSession()` HTTP method | Sends DELETE request |

#### 3.2 WhipSignaling

| ID | Test | Expected |
|----|------|----------|
| WHIP-01 | `sendOffer()` → 201 | Returns `SessionResult` |
| WHIP-02 | `sendOffer()` → 400 | Throws `WhipException` with `code=OFFER_REJECTED` |
| WHIP-03 | `sendOffer()` → network error | Throws `WhipException` with `code=NETWORK_ERROR` |
| WHIP-04 | Location header (absolute) | `resourceUrl` = exact URL |
| WHIP-05 | Location header (relative) | `resourceUrl` resolved to absolute |
| WHIP-06 | ETag header | `etag` populated |
| WHIP-07 | Link headers for ICE servers | Parsed correctly |
| WHIP-08 | Request Content-Type | `application/sdp` |
| WHIP-09 | `sendIceCandidate()` → 204 | Success |
| WHIP-10 | `sendIceCandidate()` → error | Throws `WhipException` with `code=ICE_CANDIDATE_FAILED` |
| WHIP-11 | `terminateSession()` → any | No exception |

#### 3.3 WebSocketSignaling

| ID | Test | Expected |
|----|------|----------|
| WSS-01 | `connect()` | `connectionState` goes to `CONNECTED` |
| WSS-02 | `sendOffer()` | Returns `AnswerResult` with SDP answer |
| WSS-03 | `sendPublishOffer()` | Returns `AnswerResult` |
| WSS-04 | `disconnect()` | `connectionState` → `DISCONNECTED` |
| WSS-05 | Receive Welcome JSON | `messages` emits `SignalingMessage.Welcome`, `clientId` set |
| WSS-06 | Receive Answer JSON | `messages` emits `SignalingMessage.Answer` |
| WSS-07 | Receive Error JSON | `messages` emits `SignalingMessage.Error` |
| WSS-08 | Receive unknown format | `messages` emits `SignalingMessage.Unknown` |
| WSS-09 | Connection lost, `reconnectOnFailure=true` | `connectionState` → `RECONNECTING` |

---

### 4. Audio Push

#### 4.1 AudioPushConfig

| ID | Test | Expected |
|----|------|----------|
| APC-01 | `create(host="10.0.0.1", streamPath="audio")` | `whipUrl="http://10.0.0.1:8889/audio/whip"` |
| APC-02 | `create(..., useHttps=true)` | `whipUrl="https://10.0.0.1:8889/audio/whip"` |
| APC-03 | `create(..., webrtcPort=9000)` | Port 9000 in URL |
| APC-04 | `createWithIceServers(...)` | `webrtcConfig.iceServers` contains custom servers |
| APC-05 | `withoutAudioProcessing()` | `enableEchoCancellation=false, enableNoiseSuppression=false, enableAutoGainControl=false` |
| APC-06 | Default audio processing | All three = `true` |
| APC-07 | Default `webrtcConfig` | `WebRTCConfig.SENDER` |

#### 4.2 AudioPushState

| ID | Test | Expected |
|----|------|----------|
| APS-01 | `Streaming.isActive` | `true` |
| APS-02 | `Muted.isActive` | `true` |
| APS-03 | `Connecting.isActive` | `true` |
| APS-04 | `Idle.isActive` | `false` |
| APS-05 | `Disconnected.isActive` | `false` |
| APS-06 | `Error("x").isActive` | `false` |
| APS-07 | `Error("x", isRetryable=true).isRetryable` | `true` |
| APS-08 | `Error("x", isRetryable=false).isTerminal` | `true` |
| APS-09 | `Reconnecting(2, 5).attempt` | `2` |
| APS-10 | `Reconnecting(2, 5).maxAttempts` | `5` |

#### 4.3 AudioRetryConfig

| ID | Test | Expected |
|----|------|----------|
| ARC-01 | `DEFAULT` | `maxAttempts=3, initialDelayMs=1000, maxDelayMs=30000, multiplier=2.0` |
| ARC-02 | `NONE` | `maxAttempts=0` |
| ARC-03 | `AGGRESSIVE` | `maxAttempts=10, initialDelayMs=500, maxDelayMs=60000` |

---

### 5. Player State & UI Models

#### 5.1 PlayerState

| ID | Test | Expected |
|----|------|----------|
| PS-01 | `Idle.displayName` | `"Idle"` |
| PS-02 | `Connecting.displayName` | `"Connecting..."` |
| PS-03 | `Loading.displayName` | `"Loading..."` |
| PS-04 | `Playing.displayName` | `"Playing"` |
| PS-05 | `Paused.displayName` | `"Paused"` |
| PS-06 | `Buffering(50).displayName` | `"Buffering 50%"` |
| PS-07 | `Stopped.displayName` | `"Stopped"` |
| PS-08 | `Error("fail").displayName` | `"Error"` |
| PS-09 | `Reconnecting(2, 5).displayName` | `"Reconnecting (2/5)..."` |
| PS-10 | `Error("fail").message` | `"fail"` |
| PS-11 | `Reconnecting` all properties | `attempt`, `maxAttempts`, `reason`, `nextRetryMs` accessible |

#### 5.2 StreamInfo

| ID | Test | Expected |
|----|------|----------|
| SI-01 | `resolution` with `1920x1080` | `"1920x1080"` |
| SI-02 | `resolution` with `0x0` | `"Unknown"` |
| SI-03 | `fpsDisplay` with `30.0f` | `"30.0 fps"` |
| SI-04 | `fpsDisplay` with `0f` | `"Unknown"` |
| SI-05 | `bitrateDisplay` with `2_500_000L` | Contains `"Mbps"` |
| SI-06 | `bitrateDisplay` with `128_000L` | Contains `"Kbps"` |
| SI-07 | `bitrateDisplay` with `500L` | Contains `"bps"` |
| SI-08 | `bitrateDisplay` with `null` | `"Unknown"` |

#### 5.3 BidirectionalState

| ID | Test | Expected |
|----|------|----------|
| BS-01 | Video=Playing, Audio=Streaming | `isFullyConnected=true` |
| BS-02 | Video=Playing, Audio=Idle | `isFullyConnected=false` |
| BS-03 | Video=Error("x") | `hasError=true, errorMessage` non-null |
| BS-04 | Audio=Error("y") | `hasError=true, errorMessage` non-null |
| BS-05 | Both Idle | `hasError=false, isFullyConnected=false` |
| BS-06 | Default state | `videoState=Idle, audioState=Idle` |

---

### 6. DataChannel Config

#### 6.1 DataChannelConfig

| ID | Test | Expected |
|----|------|----------|
| DC-01 | `reliable("msg")` | `ordered=true, maxRetransmits=null, maxPacketLifeTimeMs=null` |
| DC-02 | `unreliable("rt", maxRetransmits=0)` | `ordered=false, maxRetransmits=0` |
| DC-03 | `maxLifetime("t", 500)` | `maxPacketLifeTimeMs=500, maxRetransmits=null` |
| DC-04 | Both `maxRetransmits` & `maxPacketLifeTimeMs` set | Throws `IllegalArgumentException` |
| DC-05 | `negotiated=true, id=null` | Throws `IllegalArgumentException` |
| DC-06 | `negotiated=true, id=5` | Valid, no exception |
| DC-07 | Default `protocol` | `""` (empty string) |
| DC-08 | Default `negotiated` | `false` |

#### 6.2 DataChannelState

| ID | Test | Expected |
|----|------|----------|
| DCS-01 | Enum has 4 values | `CONNECTING, OPEN, CLOSING, CLOSED` |

#### 6.3 DataChannelListenerAdapter

| ID | Test | Expected |
|----|------|----------|
| DLA-01 | All methods callable | No exceptions when calling any method |

---

### 7. WebRTC Data Models

#### 7.1 WebRTCStats

| ID | Test | Expected |
|----|------|----------|
| WS-01 | `packetLossPercent` (100 sent, 5 lost) | `5.0` |
| WS-02 | `packetLossPercent` (0 sent) | `0.0` (no division by zero) |
| WS-03 | `bitrateDisplay` for `128_000` | `"128 kbps"` |
| WS-04 | `bitrateDisplay` for `2_000_000` | `"2 Mbps"` |
| WS-05 | `bitrateDisplay` for `500` | `"500 bps"` |
| WS-06 | `bitrateDisplay` for `0` | `"N/A"` |
| WS-07 | `latencyDisplay` for `45.0` | `"45 ms"` |
| WS-08 | `latencyDisplay` for `0.0` | `"N/A"` |

#### 7.2 AudioData

| ID | Test | Expected |
|----|------|----------|
| AD-01 | `equals()` with same content | `true` |
| AD-02 | `equals()` with different samples | `false` |
| AD-03 | `equals()` with different sampleRate | `false` |
| AD-04 | `hashCode()` consistency | Equal objects → equal hashCodes |

#### 7.3 Enum Values Verification

| ID | Test | Expected |
|----|------|----------|
| EV-01 | `WebRTCState.entries` | 6: `NEW, CONNECTING, CONNECTED, DISCONNECTED, FAILED, CLOSED` |
| EV-02 | `IceGatheringState.entries` | 3: `NEW, GATHERING, COMPLETE` |
| EV-03 | `IceConnectionState.entries` | 7: `NEW, CHECKING, CONNECTED, COMPLETED, FAILED, DISCONNECTED, CLOSED` |
| EV-04 | `SignalingState.entries` | 6 values |
| EV-05 | `TrackKind.entries` | 2: `VIDEO, AUDIO` |
| EV-06 | `StreamProtocol.entries` | 3: `RTSP, HLS, WEBRTC` |
| EV-07 | `SignalingType.entries` | 2: `WHEP_HTTP, WEBSOCKET` |
| EV-08 | `DataChannelState.entries` | 4: `CONNECTING, OPEN, CLOSING, CLOSED` |
| EV-09 | `WhipErrorCode.entries` | 5: `NETWORK_ERROR, OFFER_REJECTED, ICE_CANDIDATE_FAILED, SESSION_TERMINATED, UNKNOWN` |

---

### 8. v2 Signaling Adapter

> Tests for `SignalingResult`, `SignalingAuth`, `WhepSignalingAdapter`, and `WhipSignalingAdapter` — the v2 signaling layer.

#### 8.1 SignalingResult & SignalingAuth

| ID | Test | Expected |
|----|------|----------|
| SR-01 | `SignalingResult` defaults | `resourceUrl=null, etag=null, iceServers=empty` |
| SR-02 | `SignalingResult` stores all fields | sdpAnswer, resourceUrl, etag, iceServers all populated |
| SR-03 | `SignalingResult` data class equality | Two with same values are equal |
| SA-01 | `SignalingAuth.None` is singleton | `is SignalingAuth.None` |
| SA-02 | `SignalingAuth.Bearer` stores token | `token` accessible |
| SA-03 | `SignalingAuth.Cookies` stores map | cookies map accessible |
| SA-04 | `SignalingAuth.Custom` stores headers | headers map accessible |
| SA-05 | Sealed interface exhaustive | All 4 basic variants covered |

#### 8.2 WhepSignalingAdapter

| ID | Test | Expected |
|----|------|----------|
| WSA-01 | `sendOffer()` → 201 | Returns `SignalingResult` with SDP answer |
| WSA-02 | `sendOffer()` → 200 | Also accepted |
| WSA-03 | `sendOffer()` → 4xx | Throws `WhepException` |
| WSA-04 | Location header (absolute URL) | `resourceUrl` is exact header value |
| WSA-05 | No Location header | `resourceUrl = null` |
| WSA-06 | Bearer auth sends Authorization | Header = `Bearer <token>` |
| WSA-07 | Cookies auth sends Cookie header | Formatted `name=value; ...` |
| WSA-08 | Custom auth sends custom headers | All headers present |
| WSA-09 | None auth sends no auth headers | No Authorization/Cookie headers |
| WSA-10 | `sendIceCandidate()` sends SDP fragment | Content-Type `application/trickle-ice-sdpfrag` |
| WSA-11 | SDP fragment includes ufrag/pwd | Body contains `a=ice-ufrag:`, `a=ice-pwd:` |
| WSA-12 | `sendIceCandidate()` → non-2xx | Throws `WhepException` |
| WSA-13 | `terminate()` sends DELETE | HTTP DELETE method |
| WSA-14 | `terminate()` ignores errors | No exception on network failure |
| WSA-15 | Resolve relative Location path | Correctly resolved to absolute URL |
| WSA-16 | Resolve absolute Location URL | Returned as-is |
| WSA-17 | Blank Location → null | `resourceUrl = null` |
| WSA-18 | `buildSdpFragment` includes all fields | ufrag, pwd, mid, candidate present |
| WSA-19 | `buildSdpFragment` omits null fields | Missing fields excluded |

#### 8.3 WhipSignalingAdapter

| ID | Test | Expected |
|----|------|----------|
| WIPA-01 | `sendOffer()` → 201 | Returns `SignalingResult` |
| WIPA-02 | `sendOffer()` → 4xx | Throws `WhipException` with OFFER_REJECTED |
| WIPA-03 | `sendOffer()` → network error | Throws `WhipException` with NETWORK_ERROR |
| WIPA-04 | Bearer auth | Authorization header sent |
| WIPA-05 | Custom auth | Custom headers sent |
| WIPA-06 | `sendIceCandidate()` → 204 | Success |
| WIPA-07 | `sendIceCandidate()` → failure | Throws `WhipException` |
| WIPA-08 | `terminate()` sends DELETE | Ignores errors |
| WIPA-09 | Auth headers on sendIceCandidate | Auth propagated |
| WIPA-10 | Auth headers on terminate | Auth propagated |

---

### 9. v2 Session State

| ID | Test | Expected |
|----|------|----------|
| SS-01 | `Idle` is singleton | Same reference |
| SS-02 | `Connecting` is singleton | Same reference |
| SS-03 | `Connected` is singleton | Same reference |
| SS-04 | `Closed` is singleton | Same reference |
| SS-05 | `Reconnecting` stores attempt/maxAttempts | `attempt=2, maxAttempts=5` accessible |
| SS-06 | `Reconnecting` data class equality | Same values → equal |
| SS-07 | `Error` defaults | `message` stored, `cause=null`, `isRetryable=true` |
| SS-08 | `Error` with cause and retryable | All fields accessible |
| SS-09 | `Error` data class equality | Ignores cause reference identity |
| SS-10 | `when` covers all states | Exhaustive when expression compiles |

---

## E2E Tests

### Architecture: In-Process Mock Server

E2E tests use an **in-process mock server** — no external dependencies like Docker, MediaMTX, or FFmpeg. Everything runs inside the JVM test process.

```
┌──────────────────────────────────────────────────────────────┐
│  JVM Test Process (jvmTest)                                  │
│                                                              │
│  ┌──────────────┐  HTTP (localhost)  ┌───────────────────┐   │
│  │  Test Client  │◄────────────────►│  MockSignalingServer│   │
│  │  (WebRTCClient)│  POST/PATCH/DEL  │  (Ktor embedded)   │   │
│  └──────┬───────┘                   └────────┬──────────┘   │
│         │                                     │              │
│         │        SDP Offer ──────────►        │              │
│         │        ◄────────── SDP Answer       │              │
│         │                                     │              │
│         │     PeerConnection A                │              │
│         │          ◄──── ICE ────►            │              │
│         │     PeerConnection B (server-side)  │              │
│         │                                     │              │
│         │  ┌──────── DataChannel ────────┐    │              │
│         │  │  Client: send("hello") ──►  │    │              │
│         │  │  Server: echo("hello") ◄──  │    │              │
│         │  │  Client: onMessage("hello") │    │              │
│         │  └─────────────────────────────┘    │              │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

#### How It Works

1. **`MockSignalingServer`** — Ktor embedded server on `localhost:0` (random port), provides:
   - `POST /stream/whep` — WHEP endpoint (receive mode)
   - `POST /stream/whip` — WHIP endpoint (send mode)
   - `PATCH /session/{id}` — Trickle ICE
   - `DELETE /session/{id}` — Teardown

2. **Server-side `WebRTCClient`** — When an offer arrives:
   - Creates its own `WebRTCClient` (server-side)
   - Calls `setRemoteDescription(offer)` + `createAnswer()`
   - Returns the SDP answer in HTTP 201 response
   - Establishes a real PeerConnection between client and server

3. **DataChannel Echo** — Server-side `DataChannelListener`:
   - `onMessage(msg)` → `send(msg)` (echo text back)
   - `onBinaryMessage(data)` → `sendBinary(data)` (echo binary back)
   - Full bidirectional verification without any external server

4. **Audio Track** — Server-side can add a silent audio track to test WHEP audio receive

#### Benefits

| Benefit | Description |
|---------|-------------|
| **Zero external dependencies** | No Docker, MediaMTX, FFmpeg |
| **CI-friendly** | Works in any GitHub Actions runner |
| **Fast** | No network latency; in-process PeerConnection |
| **Full DataChannel round-trip** | Echo server enables send + receive verification |
| **Deterministic** | No flaky network, no race with external services |
| **Portable** | Runs on macOS, Linux, Windows |

#### Test Utilities

| Class | Location | Purpose |
|-------|----------|---------|
| `MockSignalingServer` | `jvmTest/.../e2e/MockSignalingServer.kt` | Ktor embedded WHEP/WHIP server |
| `ServerPeerConnection` | `jvmTest/.../e2e/ServerPeerConnection.kt` | Server-side WebRTCClient wrapper |
| `DataChannelEchoHandler` | `jvmTest/.../e2e/DataChannelEchoHandler.kt` | Echoes messages back on DataChannel |
| `E2ETestBase` | `jvmTest/.../e2e/E2ETestBase.kt` | Base class: starts/stops server, provides URLs |

---

### E2E-1: Video/Audio WHEP (Receive)

**Flow:** Client → `POST /stream/whep` → MockServer creates server-side PeerConnection → SDP Answer → ICE → Connected → Server pushes silent audio track → Client receives track

| ID | Test | Expected |
|----|------|----------|
| E2E-WHEP-01 | Full WHEP connection lifecycle | `WebRTCState` transitions: `NEW → CONNECTING → CONNECTED` |
| E2E-WHEP-02 | SDP offer/answer exchange | `createOffer()` returns valid SDP, mock server returns 201 + answer |
| E2E-WHEP-03 | HTTP response headers | 201 status, `Location` header with resource URL, optional `ETag` |
| E2E-WHEP-04 | Remote audio track received | `onTracksChanged` called with `audioTrackCount ≥ 1` |
| E2E-WHEP-05 | `onRemoteStreamAdded` fires | Callback invoked after connection established |
| E2E-WHEP-06 | `isConnected` after connect | `true` |
| E2E-WHEP-07 | `getStats()` returns data | Non-null `WebRTCStats` |
| E2E-WHEP-08 | Session teardown | `DELETE /session/{id}` returns 200, server-side PeerConnection closed |
| E2E-WHEP-09 | `close()` → cleanup | `connectionState = CLOSED` |
| E2E-WHEP-10 | Reconnect after disconnect | Full flow succeeds again after `close()` |

**JVM Test Sketch:**

```kotlin
class WhepE2ETest : E2ETestBase() {

    @Test
    fun `WHEP full connection receives audio track`() = runTest {
        val client = WebRTCClient()
        val stateFlow = MutableStateFlow(WebRTCState.NEW)
        val tracksReceived = CompletableDeferred<List<TrackInfo>>()

        client.initialize(WebRTCConfig.DEFAULT, object : WebRTCListener {
            override fun onConnectionStateChanged(state: WebRTCState) {
                stateFlow.value = state
            }
            override fun onTracksChanged(v: Int, a: Int, tracks: List<TrackInfo>) {
                if (!tracksReceived.isCompleted) tracksReceived.complete(tracks)
            }
        })

        val httpClient = HttpClient(CIO)
        val whep = WhepSignaling(httpClient)

        // SDP exchange via mock server
        val offer = client.createOffer(receiveVideo = false, receiveAudio = true)
        val result = whep.sendOffer(mockServer.whepUrl, offer)

        assertEquals(201, result.resourceUrl != null)  // Has resource URL
        client.setRemoteAnswer(result.sdpAnswer)

        // Wait for connection
        withTimeout(10_000) {
            stateFlow.first { it == WebRTCState.CONNECTED }
        }
        assertTrue(client.isConnected)

        // Wait for tracks
        val tracks = withTimeout(5_000) { tracksReceived.await() }
        assertTrue(tracks.any { it.kind == TrackKind.AUDIO })

        // Teardown
        result.resourceUrl?.let { whep.terminateSession(it) }
        client.close()
        httpClient.close()
    }
}
```

---

### E2E-2: Audio WHIP (Send)

**Flow:** Client → create audio track → `POST /stream/whip` → MockServer creates receiver PeerConnection → SDP Answer → ICE → Connected → Client sends silent audio

| ID | Test | Expected |
|----|------|----------|
| E2E-WHIP-01 | Full WHIP connection lifecycle | `WebRTCState`: `NEW → CONNECTING → CONNECTED` |
| E2E-WHIP-02 | `createSendOffer(sendAudio=true)` | Returns valid SDP with `m=audio` section |
| E2E-WHIP-03 | Mock server accepts offer | Returns 201 + SDP answer + `Location` header |
| E2E-WHIP-04 | `isConnected` after connect | `true` |
| E2E-WHIP-05 | Server-side receives audio track | Server's `onTracksChanged` reports `audioTrackCount ≥ 1` |
| E2E-WHIP-06 | `getStats()` after connection | Non-null stats |
| E2E-WHIP-07 | `setAudioEnabled(false)` | `isAudioEnabled = false` |
| E2E-WHIP-08 | Session teardown | `DELETE /session/{id}` succeeds |
| E2E-WHIP-09 | `close()` → cleanup | `connectionState = CLOSED` |

> **Note:** JVM headless has no real microphone. webrtc-java creates a silent audio track. The connection handshake, state transitions, and server-side track detection are all verifiable.

---

### E2E-3: DataChannel via WHEP

**Flow:** Client → WHEP connect → create DataChannel → Server accepts channel → Echo text/binary messages

| ID | Test | Expected |
|----|------|----------|
| E2E-DC-WHEP-01 | Create DataChannel after WHEP connect | `createDataChannel(reliable("test"))` returns non-null |
| E2E-DC-WHEP-02 | DataChannel state transition | `CONNECTING → OPEN` |
| E2E-DC-WHEP-03 | Send text, receive echo | Send `"hello"`, `onMessage` receives `"hello"` |
| E2E-DC-WHEP-04 | Send binary, receive echo | Send `byteArrayOf(1,2,3)`, `onBinaryMessage` receives same bytes |
| E2E-DC-WHEP-05 | Send JSON, receive echo | Send `{"type":"cmd","data":"test"}`, receive identical JSON |
| E2E-DC-WHEP-06 | `channel.label` | Matches configured label `"test"` |
| E2E-DC-WHEP-07 | `channel.id` | Non-negative integer |
| E2E-DC-WHEP-08 | `channel.bufferedAmount` after send | ≥ 0 |
| E2E-DC-WHEP-09 | `channel.close()` | State → `CLOSED` |
| E2E-DC-WHEP-10 | Multiple messages in sequence | All echoed back in order (reliable channel) |
| E2E-DC-WHEP-11 | DataChannel + audio coexist | Both work simultaneously |
| E2E-DC-WHEP-12 | Large message (16KB) | Send and receive without truncation |

**JVM Test Sketch:**

```kotlin
class DataChannelWhepE2ETest : E2ETestBase() {

    @Test
    fun `DataChannel sends text and receives echo`() = runTest {
        // 1. Establish WHEP connection
        val client = WebRTCClient()
        connectWhep(client)  // helper from E2ETestBase

        // 2. Create DataChannel
        val config = DataChannelConfig.reliable("echo-test")
        val channel = client.createDataChannel(config)
        assertNotNull(channel)

        // 3. Wait for OPEN
        val messageReceived = CompletableDeferred<String>()
        channel.setListener(object : DataChannelListener {
            override fun onStateChanged(state: DataChannelState) {}
            override fun onMessage(message: String) {
                messageReceived.complete(message)
            }
        })

        withTimeout(5_000) {
            // Wait for channel to open
            while (channel.state != DataChannelState.OPEN) { delay(100) }
        }

        // 4. Send and verify echo
        assertTrue(channel.send("hello"))
        val echo = withTimeout(5_000) { messageReceived.await() }
        assertEquals("hello", echo)

        // 5. Cleanup
        channel.close()
        client.close()
    }
}
```

---

### E2E-4: DataChannel via WHIP

**Flow:** Client → WHIP connect (audio send) → create DataChannel → Server echoes → Verify round-trip

| ID | Test | Expected |
|----|------|----------|
| E2E-DC-WHIP-01 | Create DataChannel after WHIP connect | Non-null `DataChannel` |
| E2E-DC-WHIP-02 | DataChannel state transition | `CONNECTING → OPEN` |
| E2E-DC-WHIP-03 | Send text, receive echo | Send `"data"`, receive `"data"` |
| E2E-DC-WHIP-04 | Send binary, receive echo | Send bytes, receive same bytes |
| E2E-DC-WHIP-05 | Multiple DataChannels | Create 2+ channels, each echoes independently |
| E2E-DC-WHIP-06 | Unreliable channel | `unreliable("fast")` opens and can send |
| E2E-DC-WHIP-07 | DataChannel + audio coexist | Audio WHIP + DataChannel work simultaneously |
| E2E-DC-WHIP-08 | Channel close by client | State → `CLOSED`, server detects close |
| E2E-DC-WHIP-09 | Large binary message (64KB) | Round-trip without corruption |

---

### E2E Test Infrastructure

#### MockSignalingServer Design

```kotlin
class MockSignalingServer : AutoCloseable {
    private val server: ApplicationEngine
    private val sessions = ConcurrentHashMap<String, ServerPeerConnection>()

    val port: Int           // Assigned after start
    val whepUrl: String     // "http://localhost:{port}/stream/whep"
    val whipUrl: String     // "http://localhost:{port}/stream/whip"

    fun start()             // Starts Ktor embedded server
    override fun close()    // Stops server, closes all PeerConnections

    // Ktor routes:
    // POST   /stream/whep         → create server PeerConnection (receive mode)
    // POST   /stream/whip         → create server PeerConnection (send mode)
    // PATCH  /session/{id}        → forward ICE candidate
    // DELETE /session/{id}        → close server PeerConnection
}
```

#### ServerPeerConnection Design

```kotlin
class ServerPeerConnection(mode: Mode) : AutoCloseable {
    enum class Mode { WHEP_RESPONDER, WHIP_RESPONDER }

    val id: String                                  // Unique session ID
    val webrtcClient: WebRTCClient                  // Server-side client
    val dataChannelEcho: DataChannelEchoHandler     // Auto-echo handler

    suspend fun handleOffer(sdpOffer: String): String   // Returns SDP answer
    fun addIceCandidate(candidate: String, mid: String?, index: Int)
    override fun close()
}
```

#### DataChannelEchoHandler Design

```kotlin
class DataChannelEchoHandler : DataChannelListener {
    override fun onMessage(message: String) {
        // Echo text back on the same channel
        channel.send(message)
    }

    override fun onBinaryMessage(data: ByteArray) {
        // Echo binary back
        channel.sendBinary(data)
    }
}
```

#### E2ETestBase Design

```kotlin
abstract class E2ETestBase {
    protected lateinit var mockServer: MockSignalingServer

    @BeforeEach
    fun setUp() {
        mockServer = MockSignalingServer()
        mockServer.start()
    }

    @AfterEach
    fun tearDown() {
        mockServer.close()
    }

    // Helper: establish WHEP connection and wait for CONNECTED
    protected suspend fun connectWhep(client: WebRTCClient): WhepSignaling.SessionResult

    // Helper: establish WHIP connection and wait for CONNECTED
    protected suspend fun connectWhip(client: WebRTCClient): WhipSignaling.SessionResult
}
```

---

### What We CAN Test (JVM, in-process)

| Feature | Testable? | How |
|---------|-----------|-----|
| WHEP SDP exchange | ✅ | HTTP to MockSignalingServer |
| WHEP connection state lifecycle | ✅ | Two real PeerConnections (webrtc-java) |
| WHEP remote track detection | ✅ | Server adds audio track → client receives |
| WHIP SDP exchange | ✅ | HTTP to MockSignalingServer |
| WHIP connection state lifecycle | ✅ | Client → server PeerConnection (silent audio) |
| WHIP server-side track detection | ✅ | Server's `onTracksChanged` callback |
| DataChannel send + receive | ✅ | Echo handler enables full round-trip |
| DataChannel binary round-trip | ✅ | Echo handler echoes ByteArray |
| DataChannel properties | ✅ | label, id, state, bufferedAmount |
| Session teardown (DELETE) | ✅ | HTTP DELETE → server PeerConnection closed |
| Multiple DataChannels | ✅ | Create multiple channels, each echoed |
| Reliable vs unreliable channels | ✅ | Both configs testable |

### What We CANNOT Test (JVM, headless)

| Feature | Reason | Alternative |
|---------|--------|-------------|
| Video frame rendering | No display on JVM headless | Test on Android/iOS device |
| Real audio capture | No microphone in CI | webrtc-java sends silent track |
| Compose UI (VideoRenderer) | Needs Compose test runtime | Compose UI test on device |
| iOS/Android specific behavior | Needs device | Platform-specific test suite |
| Real SFU behavior | Mock doesn't replicate MediaMTX | Optional: add MediaMTX smoke tests |

### Required Dependencies

| Item | Status | Action |
|------|--------|--------|
| `kotlinx-coroutines-test` | ❌ To add | `libs.versions.toml` + `commonTest` |
| `ktor-client-mock` | ❌ To add | `libs.versions.toml` + `jvmTest` |
| `ktor-server-core` | ❌ To add | For MockSignalingServer embedded server |
| `ktor-server-netty` | ❌ To add | Ktor server engine for tests |
| webrtc-java | ✅ In jvmMain | Inherited by jvmTest |
| Ktor CIO client | ✅ In jvmMain | Inherited by jvmTest |

---

## Test Summary

| Category | Count | Source Set | Dependencies |
|----------|-------|-----------|--------------|
| Config unit tests | 32 | commonTest | kotlin-test |
| StreamRetryHandler | 14 | jvmTest | coroutines-test |
| WHEP Signaling | 20 | jvmTest | ktor-client-mock |
| WHIP Signaling | 11 | jvmTest | ktor-client-mock |
| WebSocket Signaling | 9 | jvmTest | ktor-client-mock |
| Audio Push state/config | 20 | commonTest | kotlin-test |
| Player State & UI models | 20 | commonTest | kotlin-test |
| DataChannel config | 10 | commonTest | kotlin-test |
| WebRTC data models | 21 | commonTest | kotlin-test |
| v2 SignalingResult & Auth | 8 | jvmTest | kotlin-test |
| v2 WhepSignalingAdapter | 19 | jvmTest | ktor-client-mock |
| v2 WhipSignalingAdapter | 10 | jvmTest | ktor-client-mock |
| v2 SessionState | 10 | jvmTest | kotlin-test |
| **Unit Total** | **~167** | | |
| E2E WHEP (receive) | 10 | jvmTest | MockSignalingServer + webrtc-java |
| E2E WHIP (send) | 9 | jvmTest | MockSignalingServer + webrtc-java |
| E2E DataChannel WHEP | 12 | jvmTest | MockSignalingServer + echo handler |
| E2E DataChannel WHIP | 9 | jvmTest | MockSignalingServer + echo handler |
| **E2E Total** | **~40** | | |
| **Grand Total** | **~207** | | |
