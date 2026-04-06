# Test Specification — SyncAI-Lib-KmpWebRTC v2.0

> Last Updated: 2026-04-06

## Table of Contents

- [Test Strategy](#test-strategy)
- [Test Dependencies](#test-dependencies)
- [Unit Tests](#unit-tests)
  - [1. Config Classes](#1-config-classes)
  - [2. StreamRetryHandler](#2-streamretryhandler)
  - [3. HttpSignalingAdapter](#3-httpsignalingadapter)
  - [4. Audio Config & State](#4-audio-config--state)
  - [5. Player State & UI Models](#5-player-state--ui-models)
  - [6. DataChannel Config](#6-datachannel-config)
  - [7. WebRTC Data Models](#7-webrtc-data-models)
  - [8. MediaConfig & TransceiverDirection](#8-mediaconfig--transceiverdirection)
  - [9. SessionState](#9-sessionstate)
- [Library E2E Tests](#library-e2e-tests)
  - [E2E-1: Video Receive (RECEIVE_VIDEO)](#e2e-1-video-receive)
  - [E2E-2: Audio Send (SEND_AUDIO)](#e2e-2-audio-send)
  - [E2E-3: Camera Send (SEND_VIDEO)](#e2e-3-camera-send)
  - [E2E-4: Bidirectional Audio (BIDIRECTIONAL_AUDIO)](#e2e-4-bidirectional-audio)
  - [E2E-5: DataChannel](#e2e-5-datachannel)
- [Server Architecture Tests](#server-architecture-tests)
  - [S-1: MediaMTX Server](#s-1-mediamtx-server)
  - [S-2: Custom Signaling Server without Media Server](#s-2-custom-signaling-server-without-media-server)
  - [S-3: Custom Signaling Server with Media Server](#s-3-custom-signaling-server-with-media-server)
  - [S-4: Custom Signaling Server with P2P Mesh](#s-4-custom-signaling-server-with-p2p-mesh)
- [Client Architecture & Connection Tests](#client-architecture--connection-tests)
  - [C-1: Two Library Apps Bidirectional Streaming](#c-1-two-library-apps-bidirectional-streaming)
  - [C-2: Library App + External WebRTC IoT Device](#c-2-library-app--external-webrtc-iot-device)
  - [C-3: Multiple VideoRenderer Support](#c-3-multiple-videorenderer-support)
  - [C-4: 1-to-N Connection](#c-4-1-to-n-connection)
  - [C-5: DataChannel Communication](#c-5-datachannel-communication)
- [Test Summary](#test-summary)

---

## Test Strategy

| Layer | Type | Source Set | Dependencies | Mock/Real |
|-------|------|-----------|--------------|-----------|
| Config / Data classes | Unit | `jvmTest` | kotlin-test | Pure Kotlin |
| StreamRetryHandler | Unit | `jvmTest` | kotlin-test, coroutines-test | Pure Kotlin |
| HttpSignalingAdapter | Unit | `jvmTest` | ktor-client-mock | MockEngine |
| MediaConfig / TransceiverDirection | Unit | `jvmTest` | kotlin-test | Pure Kotlin |
| State / Sealed classes | Unit | `jvmTest` | kotlin-test | Pure Kotlin |
| DataChannel config | Unit | `jvmTest` | kotlin-test | Pure Kotlin |
| SessionState | Unit | `jvmTest` | kotlin-test | Pure Kotlin |
| Library E2E (single app) | E2E | `jvmTest` / Manual | webrtc-java + MediaMTX | Real server |
| Server architecture | Integration | Manual | MediaMTX / Custom BE | Real infrastructure |
| Client architecture | Integration | Manual | Multiple apps/devices | Real devices |

---

## Test Dependencies

In `gradle/libs.versions.toml` (already configured):

```toml
[libraries]
kotlin-test = { module = "org.jetbrains.kotlin:kotlin-test", version.ref = "kotlin" }
kotlin-testJunit = { module = "org.jetbrains.kotlin:kotlin-test-junit", version.ref = "kotlin" }
kotlinx-coroutines-test = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-test", version.ref = "kotlinx-coroutines" }
ktor-client-mock = { module = "io.ktor:ktor-client-mock", version.ref = "ktor" }
ktor-server-core = { module = "io.ktor:ktor-server-core", version.ref = "ktor" }
ktor-server-netty = { module = "io.ktor:ktor-server-netty", version.ref = "ktor" }
ktor-server-content-negotiation = { module = "io.ktor:ktor-server-content-negotiation", version.ref = "ktor" }
```

---

## Unit Tests

### 1. Config Classes

#### 1.1 RetryConfig

| ID | Test | Expected |
|----|------|----------|
| RC-01 | Default values | `maxRetries=5, initialDelayMs=1000, maxDelayMs=45000, backoffFactor=2.0` |
| RC-02 | `calculateDelay(0)` | ~1000ms (with jitter) |
| RC-03 | `calculateDelay(3)` | ~8000ms (capped by maxDelayMs) |
| RC-04 | Presets exist | `DEFAULT`, `AGGRESSIVE`, `PERSISTENT`, `DISABLED` |

#### 1.2 WebRTCConfig

| ID | Test | Expected |
|----|------|----------|
| WC-01 | Default values | `iceMode=FULL_ICE, bundlePolicy="max-bundle"` |
| WC-02 | Custom ICE servers | Accepts `IceServer` list with STUN/TURN URLs |
| WC-03 | ICE transport policy | `"all"` (default) or `"relay"` |

#### 1.3 IceServer

| ID | Test | Expected |
|----|------|----------|
| IS-01 | Google STUN default | `urls = ["stun:stun.l.google.com:19302"]` |
| IS-02 | TURN with credentials | `urls`, `username`, `credential` all set |

#### 1.4 MediaConfig

| ID | Test | Expected |
|----|------|----------|
| MC-01 | `RECEIVE_VIDEO` preset | `receiveVideo=true, receiveAudio=true, sendVideo=false, sendAudio=false` |
| MC-02 | `SEND_AUDIO` preset | `sendAudio=true`, rest false |
| MC-03 | `SEND_VIDEO` preset | `sendVideo=true, sendAudio=true` |
| MC-04 | `BIDIRECTIONAL_AUDIO` preset | `sendAudio=true, receiveAudio=true` |
| MC-05 | `VIDEO_CALL` preset | All true |
| MC-06 | `videoDirection` calculation | SEND_ONLY / RECV_ONLY / SEND_RECV / null based on flags |
| MC-07 | `audioDirection` calculation | Same as video |
| MC-08 | `requiresSending` | True when sendVideo or sendAudio is true |
| MC-09 | `requiresReceiving` | True when receiveVideo or receiveAudio is true |

#### 1.5 VideoCaptureConfig

| ID | Test | Expected |
|----|------|----------|
| VC-01 | `HD` preset | 1280x720@30fps |
| VC-02 | `SD` preset | 640x480@30fps |
| VC-03 | `LOW` preset | 320x240@15fps |
| VC-04 | Default `useFrontCamera` | true |

#### 1.6 TransceiverDirection

| ID | Test | Expected |
|----|------|----------|
| TD-01 | `SEND_ONLY.isSending` | true |
| TD-02 | `SEND_ONLY.isReceiving` | false |
| TD-03 | `RECV_ONLY.isSending` | false |
| TD-04 | `RECV_ONLY.isReceiving` | true |
| TD-05 | `SEND_RECV.isSending` | true |
| TD-06 | `SEND_RECV.isReceiving` | true |

### 2. StreamRetryHandler

| ID | Test | Expected |
|----|------|----------|
| SR-01 | Succeeds on first try | Block executed once, no retry |
| SR-02 | Retries on failure | Block retried up to `maxRetries` |
| SR-03 | `SignalingException` is retryable | `shouldRetry` returns true |
| SR-04 | `IllegalStateException` not retryable | `shouldRetry` returns false |
| SR-05 | Exponential backoff timing | Delays increase by `backoffFactor` |
| SR-06 | `RetryConfig.DISABLED` no retry | Block fails immediately |

### 3. HttpSignalingAdapter

| ID | Test | Expected |
|----|------|----------|
| HS-01 | Successful POST offer | Returns `SignalingResult` with SDP answer, resourceUrl, etag |
| HS-02 | HTTP 201 Created | Accepted as success |
| HS-03 | HTTP 200 OK | Accepted as success |
| HS-04 | HTTP 4xx/5xx | Throws `SignalingException(OFFER_REJECTED)` |
| HS-05 | Network error | Throws `SignalingException(NETWORK_ERROR)` |
| HS-06 | ICE candidate PATCH | Sends trickle-ice-sdpfrag content type |
| HS-07 | Terminate DELETE | Sends DELETE to resourceUrl, ignores errors |
| HS-08 | Bearer auth | `Authorization: Bearer <token>` header present |
| HS-09 | Cookie auth | `Cookie: key=value` header present |
| HS-10 | Custom headers auth | Custom headers present |
| HS-11 | No auth | No extra headers |
| HS-12 | Relative Location header | Resolved to absolute URL |
| HS-13 | ICE server Link headers | Parsed into `IceServer` list |

### 4. Audio Config & State

| ID | Test | Expected |
|----|------|----------|
| AP-01 | `AudioPushConfig` defaults | Echo cancellation, noise suppression, AGC all true |
| AP-02 | `AudioPushState` sealed class | All states: Idle, Connecting, Streaming, Muted, Reconnecting, Error, Disconnected |
| AP-03 | `AudioPushState.isActive` | True for Streaming, Muted, Reconnecting |
| AP-04 | `AudioPlaybackState` sealed class | All states: Idle, Connecting, Playing, Muted, Reconnecting, Error, Disconnected |

### 5. Player State & UI Models

| ID | Test | Expected |
|----|------|----------|
| PS-01 | `PlayerState` sealed interface | All states: Idle, Connecting, Loading, Playing, Paused, Buffering, Stopped, Error, Reconnecting |
| PS-02 | `PlayerEvent` sealed interface | FirstFrameRendered, StreamInfoReceived, BitrateChanged, FrameReceived |
| PS-03 | `StreamInfo` defaults | width=0, height=0, codec="Unknown", fps=0f |
| PS-04 | `StreamInfo.resolution` | "1920x1080" format |
| PS-05 | `SessionState.toPlayerState()` mapping | Correct mapping for all states |

### 6. DataChannel Config

| ID | Test | Expected |
|----|------|----------|
| DC-01 | `reliable()` preset | ordered=true, maxRetransmits=-1 |
| DC-02 | `unreliable()` preset | ordered=false, maxRetransmits=0 |
| DC-03 | `maxLifetime()` preset | maxPacketLifeTime set |
| DC-04 | Custom config | Label, protocol, negotiated, id all configurable |

### 7. WebRTC Data Models

| ID | Test | Expected |
|----|------|----------|
| WS-01 | `WebRTCStats.bitrateDisplay` | Formats as "N kbps" or "N Mbps" |
| WS-02 | `WebRTCStats.latencyDisplay` | Formats as "N ms" |
| WS-03 | `WebRTCStats.packetLossPercent` | Correct percentage calculation |
| WS-04 | `SignalingErrorCode` enum | NETWORK_ERROR, OFFER_REJECTED, ICE_CANDIDATE_FAILED, SESSION_TERMINATED, UNKNOWN |

### 8. MediaConfig & TransceiverDirection

(Covered in section 1.4 and 1.6 above)

### 9. SessionState

| ID | Test | Expected |
|----|------|----------|
| SS-01 | All states exist | Idle, Connecting, Connected, Reconnecting, Error, Closed |
| SS-02 | `Error` properties | `message`, `cause`, `isRetryable` |
| SS-03 | `Reconnecting` properties | `attempt`, `maxAttempts` |

---

## Library E2E Tests

> Prerequisites: MediaMTX server running locally or a WHEP/WHIP-compatible server.

### E2E-1: Video Receive

| ID | Test | MediaConfig | Expected |
|----|------|-------------|----------|
| E2E-V-01 | Connect and receive video | `RECEIVE_VIDEO` | SessionState → Connected, video frames received |
| E2E-V-02 | `setRemoteVideoEnabled(false)` | `RECEIVE_VIDEO` | Video frames stop being rendered |
| E2E-V-03 | `setAudioEnabled(false)` | `RECEIVE_VIDEO` | Audio muted, video continues |
| E2E-V-04 | Auto-reconnect on disconnect | `RECEIVE_VIDEO` | SessionState → Reconnecting → Connected |

### E2E-2: Audio Send

| ID | Test | MediaConfig | Expected |
|----|------|-------------|----------|
| E2E-A-01 | Connect and send audio | `SEND_AUDIO` | SessionState → Connected, audio streaming |
| E2E-A-02 | `setMuted(true)` | `SEND_AUDIO` | Audio track disabled, connection maintained |
| E2E-A-03 | `toggleMute()` | `SEND_AUDIO` | Mute state toggles |

### E2E-3: Camera Send

| ID | Test | MediaConfig | Expected |
|----|------|-------------|----------|
| E2E-C-01 | Connect and send camera | `SEND_VIDEO` | Camera starts, video track created |
| E2E-C-02 | `setVideoEnabled(false)` | `SEND_VIDEO` | Camera track disabled |
| E2E-C-03 | `switchCamera()` | `SEND_VIDEO` | Camera device switches (if multiple available) |

### E2E-4: Bidirectional Audio

| ID | Test | MediaConfig | Expected |
|----|------|-------------|----------|
| E2E-B-01 | Connect bidirectional | `BIDIRECTIONAL_AUDIO` | Both send and receive audio active |
| E2E-B-02 | Mute while receiving | `BIDIRECTIONAL_AUDIO` | Local audio stops, remote audio continues |

### E2E-5: DataChannel

| ID | Test | MediaConfig | Expected |
|----|------|-------------|----------|
| E2E-D-01 | Create reliable channel | Any | DataChannel opens, state = OPEN |
| E2E-D-02 | Send text message | Any | Message delivered to remote peer |
| E2E-D-03 | Send binary data | Any | Binary data delivered |
| E2E-D-04 | Channel close | Any | State = CLOSED |

---

## Server Architecture Tests

> These tests validate the library works with different backend architectures.

### S-1: MediaMTX Server

| ID | Test | Description | Expected |
|----|------|-------------|----------|
| S1-01 | WHEP video receive | App connects to MediaMTX WHEP endpoint | Video plays |
| S1-02 | WHIP audio send | App sends audio to MediaMTX WHIP endpoint | Audio reaches server |
| S1-03 | WHIP video send | App sends camera to MediaMTX WHIP endpoint | Video reaches server |
| S1-04 | Multiple viewers | 2+ apps connect WHEP to same stream | All receive video |
| S1-05 | Reconnect on server restart | MediaMTX restarts during session | App auto-reconnects |

### S-2: Custom Signaling Server without Media Server

| ID | Test | Description | Expected |
|----|------|-------------|----------|
| S2-01 | BE as signaling proxy | App → BE → IoT (MediaMTX) | Video plays, BE only touches SDP |
| S2-02 | Auth via BE | App sends JWT to BE, BE validates | Authorized apps connect |
| S2-03 | BE returns 502 | IoT offline, BE returns error | App retries via RetryConfig |

### S-3: Custom Signaling Server with Media Server

| ID | Test | Description | Expected |
|----|------|-------------|----------|
| S3-01 | BE with media relay | IoT → WHIP → BE (SFU) → WHEP → App | Video plays through relay |
| S3-02 | 1-to-N via relay | 1 publisher, N viewers through BE | All viewers receive |

### S-4: Custom Signaling Server with P2P Mesh

| ID | Test | Description | Expected |
|----|------|-------------|----------|
| S4-01 | WebSocket signaling | Custom `SignalingAdapter` via WebSocket | P2P connection established |
| S4-02 | P2P video call | Two apps, WS signaling, direct RTP | Both send/receive video |
| S4-03 | STUN/TURN traversal | Apps behind NAT, TURN server configured | Connection via relay |

---

## Client Architecture & Connection Tests

### C-1: Two Library Apps Bidirectional Streaming

| ID | Test | Description | Expected |
|----|------|-------------|----------|
| C1-01 | Video call | Both apps use `MediaConfig.VIDEO_CALL` | Both send/receive video + audio |
| C1-02 | Audio intercom | Both use `MediaConfig.BIDIRECTIONAL_AUDIO` | Both send/receive audio |
| C1-03 | Camera switch | One app calls `switchCamera()` | Video switches, remote sees new camera |

### C-2: Library App + External WebRTC IoT Device

| ID | Test | Description | Expected |
|----|------|-------------|----------|
| C2-01 | Receive from IoT camera | IoT runs MediaMTX/Pion, app connects WHEP | Video plays in app |
| C2-02 | Send audio to IoT | App sends mic via WHIP to IoT | IoT receives audio |
| C2-03 | DataChannel commands | App sends JSON commands, IoT responds | Bidirectional messaging works |

### C-3: Multiple VideoRenderer Support

| ID | Test | Description | Expected |
|----|------|-------------|----------|
| C3-01 | 2 video sessions | Two `WebRTCSession` + `VideoRenderer` | Both display video |
| C3-02 | 4 video sessions | Grid layout with 4 streams | All display, performance acceptable |
| C3-03 | Independent lifecycle | Close one session, other continues | No interference |

### C-4: 1-to-N Connection

| ID | Test | Description | Expected |
|----|------|-------------|----------|
| C4-01 | 3 viewers, 1 publisher | 1 WHIP publisher, 3 WHEP viewers | All viewers see video |
| C4-02 | Viewer joins late | Publisher already streaming, new viewer connects | Viewer gets video |
| C4-03 | Viewer leaves | One viewer disconnects | Others unaffected |

### C-5: DataChannel Communication

| ID | Test | Description | Expected |
|----|------|-------------|----------|
| C5-01 | Text messaging | Send/receive JSON commands | Messages delivered in order |
| C5-02 | Binary messaging | Send/receive binary data (images) | Data delivered intact |
| C5-03 | Multiple channels | Create 2+ DataChannels on same session | All channels work independently |
| C5-04 | Channel with video | DataChannel + video receive on same session | Both work simultaneously |

---

## Test Summary

| Category | Test Count | Automation | Status |
|----------|-----------|------------|--------|
| **Unit Tests** (Config, State, Models) | ~45 | Automated (`./gradlew jvmTest`) | Partial (existing tests) |
| **Unit Tests** (HttpSignalingAdapter) | ~13 | Automated (MockEngine) | To implement |
| **Unit Tests** (MediaConfig, TransceiverDirection) | ~15 | Automated | To implement |
| **Library E2E** | ~14 | Semi-automated (requires server) | To implement |
| **Server Architecture** | ~10 | Manual | To implement |
| **Client Architecture** | ~14 | Manual | To implement |
| **Total** | **~111** | | |

### Running Tests

```bash
# Unit tests
./gradlew jvmTest

# Specific test class
./gradlew jvmTest --tests "com.syncrobotic.webrtc.config.RetryConfigTest"
```
