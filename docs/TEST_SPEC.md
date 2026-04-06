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
  - [E2E Infrastructure](#e2e-infrastructure)
  - [E2E-1: Video Receive (RECEIVE_VIDEO)](#e2e-1-video-receive)
  - [E2E-2: Audio Send (SEND_AUDIO)](#e2e-2-audio-send)
  - [E2E-3: Camera Send (SEND_VIDEO)](#e2e-3-camera-send)
  - [E2E-4: Bidirectional Audio (BIDIRECTIONAL_AUDIO)](#e2e-4-bidirectional-audio)
  - [E2E-5: DataChannel](#e2e-5-datachannel)
  - [E2E-6: Multi-Session Parallel](#e2e-6-multi-session-parallel)
  - [E2E-7: Public Callback APIs](#e2e-7-public-callback-apis)
- [Testcontainers MediaMTX Integration Tests](#testcontainers-mediamtx-integration-tests)
- [Server Architecture Tests](#server-architecture-tests)
  - [S-1: MediaMTX Server](#s-1-mediamtx-server)
  - [S-2: Custom Signaling Server without Media Server](#s-2-custom-signaling-server-without-media-server)
  - [S-3: Custom Signaling Server with Media Server](#s-3-custom-signaling-server-with-media-server)
  - [S-4: Custom Signaling Server with P2P Mesh](#s-4-custom-signaling-server-with-p2p-mesh)
  - [S-5: IoT WebRTC Server + BE Signaling + DataChannel](#s-5-iot-webrtc-server--be-signaling--datachannel)
- [Client Architecture & Connection Tests](#client-architecture--connection-tests)
  - [C-1: Two Library Apps Bidirectional Streaming](#c-1-two-library-apps-bidirectional-streaming)
  - [C-2: Library App + External WebRTC IoT Device](#c-2-library-app--external-webrtc-iot-device)
  - [C-3: Multiple VideoRenderer Support](#c-3-multiple-videorenderer-support)
  - [C-4: 1-to-N Connection](#c-4-1-to-n-connection)
  - [C-5: DataChannel Communication](#c-5-datachannel-communication)
- [Test Summary](#test-summary)

---

## Test Strategy

| Layer | Type | Source Set | Dependencies | Mock/Real | Status |
|-------|------|-----------|--------------|-----------|--------|
| Config / Data classes | Unit | `jvmTest` | kotlin-test | Pure Kotlin | ✅ Implemented |
| StreamRetryHandler | Unit | `jvmTest` | kotlin-test, coroutines-test | Pure Kotlin | ✅ Implemented |
| HttpSignalingAdapter | Unit | `jvmTest` | ktor-client-mock | MockEngine | ✅ Implemented |
| MediaConfig / TransceiverDirection | Unit | `jvmTest` | kotlin-test | Pure Kotlin | ✅ Implemented |
| State / Sealed classes | Unit | `jvmTest` | kotlin-test | Pure Kotlin | ✅ Implemented |
| DataChannel config | Unit | `jvmTest` | kotlin-test | Pure Kotlin | ✅ Implemented |
| SessionState | Unit | `jvmTest` | kotlin-test | Pure Kotlin | ✅ Implemented |
| Library E2E (signaling) | E2E | `jvmTest` | ktor-server-netty (MockWhepWhipServer) | Mock server | ✅ Implemented |
| Library E2E (full WebRTC) | E2E | `jvmTest` | webrtc-java + MockWhepWhipServer | Mock server + native | ✅ Implemented (skip if no native) |
| Testcontainers MediaMTX | Integration | `jvmTest` | testcontainers + Docker | Real MediaMTX | ✅ Implemented (skip if no Docker) |
| Server architecture | Integration | Manual | MediaMTX / Custom BE | Real infrastructure | ⬜ To implement |
| Client architecture | Integration | Manual | Multiple apps/devices | Real devices | ⬜ To implement |

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
ktor-server-websockets = { module = "io.ktor:ktor-server-websockets", version.ref = "ktor" }
testcontainers = { module = "org.testcontainers:testcontainers", version.ref = "testcontainers" }
testcontainers-junit = { module = "org.testcontainers:junit-jupiter", version.ref = "testcontainers" }
```

---

## Unit Tests

### 1. Config Classes

> Status: ✅ Implemented

#### 1.1 RetryConfig

> File: `src/jvmTest/kotlin/com/syncrobotic/webrtc/config/RetryConfigTest.kt`

| ID | Test | Expected | Status |
|----|------|----------|--------|
| RC-01 | Default values | `maxRetries=5, initialDelayMs=1000, maxDelayMs=45000, backoffFactor=2.0` | ✅ |
| RC-02 | `calculateDelay(0)` | ~1000ms (with jitter) | ✅ |
| RC-03 | `calculateDelay(3)` | ~8000ms (capped by maxDelayMs) | ✅ |
| RC-04 | Presets exist | `DEFAULT`, `AGGRESSIVE`, `PERSISTENT`, `DISABLED` | ✅ |

#### 1.2 WebRTCConfig

> File: `src/jvmTest/kotlin/com/syncrobotic/webrtc/config/WebRTCConfigTest.kt`

| ID | Test | Expected | Status |
|----|------|----------|--------|
| WC-01 | Default values | `iceMode=FULL_ICE, bundlePolicy="max-bundle"` | ✅ |
| WC-02 | Custom ICE servers | Accepts `IceServer` list with STUN/TURN URLs | ✅ |
| WC-03 | ICE transport policy | `"all"` (default) or `"relay"` | ✅ |

#### 1.3 IceServer

> File: `src/jvmTest/kotlin/com/syncrobotic/webrtc/config/IceServerTest.kt`

| ID | Test | Expected | Status |
|----|------|----------|--------|
| IS-01 | Google STUN default | `urls = ["stun:stun.l.google.com:19302"]` | ✅ |
| IS-02 | TURN with credentials | `urls`, `username`, `credential` all set | ✅ |

#### 1.4 MediaConfig

> File: `src/jvmTest/kotlin/com/syncrobotic/webrtc/config/MediaConfigTest.kt`

| ID | Test | Expected | Status |
|----|------|----------|--------|
| MC-01 | `RECEIVE_VIDEO` preset | `receiveVideo=true, receiveAudio=true, sendVideo=false, sendAudio=false` | ✅ |
| MC-02 | `SEND_AUDIO` preset | `sendAudio=true`, rest false | ✅ |
| MC-03 | `SEND_VIDEO` preset | `sendVideo=true, sendAudio=true` | ✅ |
| MC-04 | `BIDIRECTIONAL_AUDIO` preset | `sendAudio=true, receiveAudio=true` | ✅ |
| MC-05 | `VIDEO_CALL` preset | All true | ✅ |
| MC-06 | `videoDirection` calculation | SEND_ONLY / RECV_ONLY / SEND_RECV / null based on flags | ✅ |
| MC-07 | `audioDirection` calculation | Same as video | ✅ |
| MC-08 | `requiresSending` | True when sendVideo or sendAudio is true | ✅ |
| MC-09 | `requiresReceiving` | True when receiveVideo or receiveAudio is true | ✅ |

#### 1.5 VideoCaptureConfig

> File: `src/jvmTest/kotlin/com/syncrobotic/webrtc/config/VideoCaptureConfigTest.kt`

| ID | Test | Expected | Status |
|----|------|----------|--------|
| VC-01 | `HD` preset | 1280x720@30fps | ✅ |
| VC-02 | `SD` preset | 640x480@30fps | ✅ |
| VC-03 | `LOW` preset | 320x240@15fps | ✅ |
| VC-04 | Default `useFrontCamera` | true | ✅ |

#### 1.6 TransceiverDirection

> File: `src/jvmTest/kotlin/com/syncrobotic/webrtc/config/TransceiverDirectionTest.kt`

| ID | Test | Expected | Status |
|----|------|----------|--------|
| TD-01 | `SEND_ONLY.isSending` | true | ✅ |
| TD-02 | `SEND_ONLY.isReceiving` | false | ✅ |
| TD-03 | `RECV_ONLY.isSending` | false | ✅ |
| TD-04 | `RECV_ONLY.isReceiving` | true | ✅ |
| TD-05 | `SEND_RECV.isSending` | true | ✅ |
| TD-06 | `SEND_RECV.isReceiving` | true | ✅ |

### 2. StreamRetryHandler

> Status: ✅ Implemented
> File: `src/jvmTest/kotlin/com/syncrobotic/webrtc/config/StreamRetryHandlerTest.kt`

| ID | Test | Expected | Status |
|----|------|----------|--------|
| SR-01 | Succeeds on first try | Block executed once, no retry | ✅ |
| SR-02 | Retries on failure | Block retried up to `maxRetries` | ✅ |
| SR-03 | `SignalingException` is retryable | `shouldRetry` returns true | ✅ |
| SR-04 | `IllegalStateException` not retryable | `shouldRetry` returns false | ✅ |
| SR-05 | Exponential backoff timing | Delays increase by `backoffFactor` | ✅ |
| SR-06 | `RetryConfig.DISABLED` no retry | Block fails immediately | ✅ |

### 3. HttpSignalingAdapter

> Status: ✅ Implemented
> File: `src/jvmTest/kotlin/com/syncrobotic/webrtc/signaling/HttpSignalingAdapterTest.kt`

| ID | Test | Expected | Status |
|----|------|----------|--------|
| HS-01 | Successful POST offer | Returns `SignalingResult` with SDP answer, resourceUrl, etag | ✅ |
| HS-02 | HTTP 201 Created | Accepted as success | ✅ |
| HS-03 | HTTP 200 OK | Accepted as success | ✅ |
| HS-04 | HTTP 4xx/5xx | Throws `SignalingException(OFFER_REJECTED)` | ✅ |
| HS-05 | Location header parsed | resourceUrl extracted | ✅ |
| HS-06 | Relative Location header | Resolved to absolute URL | ✅ |
| HS-07 | ETag header captured | etag field populated | ✅ |
| HS-08 | ICE candidate PATCH | Sends trickle-ice-sdpfrag content type | ✅ |
| HS-09 | Terminate DELETE | Sends DELETE to resourceUrl, ignores errors | ✅ |
| HS-10 | Bearer auth | `Authorization: Bearer <token>` header present | ✅ |
| HS-11 | Cookie auth | `Cookie: key=value` header present | ✅ |
| HS-12 | Custom headers auth | Custom headers present | ✅ |
| HS-13 | No auth | No extra headers | ✅ |

### 4. Audio Config & State

> Status: ✅ Implemented
> Files: `src/jvmTest/kotlin/com/syncrobotic/webrtc/audio/AudioPushConfigTest.kt`, `AudioPushStateTest.kt`

| ID | Test | Expected | Status |
|----|------|----------|--------|
| AP-01 | `AudioPushConfig` defaults | Echo cancellation, noise suppression, AGC all true | ✅ |
| AP-02 | `AudioPushState` sealed class | All states: Idle, Connecting, Streaming, Muted, Reconnecting, Error, Disconnected | ✅ |
| AP-03 | `AudioPushState.isActive` | True for Streaming, Muted, Reconnecting | ✅ |
| AP-04 | `AudioPlaybackState` sealed class | All states: Idle, Connecting, Playing, Muted, Reconnecting, Error, Disconnected | ✅ |

### 5. Player State & UI Models

> Status: ✅ Implemented
> Files: `src/jvmTest/kotlin/com/syncrobotic/webrtc/ui/PlayerStateTest.kt`, `StreamInfoTest.kt`

| ID | Test | Expected | Status |
|----|------|----------|--------|
| PS-01 | `PlayerState` sealed interface | All states: Idle, Connecting, Loading, Playing, Paused, Buffering, Stopped, Error, Reconnecting | ✅ |
| PS-02 | `PlayerEvent` sealed interface | FirstFrameRendered, StreamInfoReceived, BitrateChanged, FrameReceived | ✅ |
| PS-03 | `StreamInfo` defaults | width=0, height=0, codec="Unknown", fps=0f | ✅ |
| PS-04 | `StreamInfo.resolution` | "1920x1080" format | ✅ |
| PS-05 | `SessionState.toPlayerState()` mapping | Correct mapping for all states | ✅ |

### 6. DataChannel Config

> Status: ✅ Implemented
> File: `src/jvmTest/kotlin/com/syncrobotic/webrtc/datachannel/DataChannelConfigTest.kt`

| ID | Test | Expected | Status |
|----|------|----------|--------|
| DC-01 | `reliable()` preset | ordered=true, maxRetransmits=-1 | ✅ |
| DC-02 | `unreliable()` preset | ordered=false, maxRetransmits=0 | ✅ |
| DC-03 | `maxLifetime()` preset | maxPacketLifeTime set | ✅ |
| DC-04 | Custom config | Label, protocol, negotiated, id all configurable | ✅ |

### 7. WebRTC Data Models

> Status: ✅ Implemented
> Files: `src/jvmTest/kotlin/com/syncrobotic/webrtc/WebRTCStatsTest.kt`, `AudioDataTest.kt`, `EnumValuesTest.kt`
> Signaling types: `src/jvmTest/kotlin/com/syncrobotic/webrtc/signaling/SignalingAdapterTest.kt`, `SignalingExceptionTest.kt`

| ID | Test | Expected | Status |
|----|------|----------|--------|
| WS-01 | `WebRTCStats.bitrateDisplay` | Formats as "N kbps" or "N Mbps" | ✅ |
| WS-02 | `WebRTCStats.latencyDisplay` | Formats as "N ms" | ✅ |
| WS-03 | `WebRTCStats.packetLossPercent` | Correct percentage calculation | ✅ |
| WS-04 | `SignalingErrorCode` enum | NETWORK_ERROR, OFFER_REJECTED, ICE_CANDIDATE_FAILED, SESSION_TERMINATED, UNKNOWN | ✅ |

### 8. MediaConfig & TransceiverDirection

> Status: ✅ Implemented — Covered in section 1.4 and 1.6 above.

### 9. SessionState

> Status: ✅ Implemented
> File: `src/jvmTest/kotlin/com/syncrobotic/webrtc/session/SessionStateTest.kt`

| ID | Test | Expected | Status |
|----|------|----------|--------|
| SS-01 | All states exist | Idle, Connecting, Connected, Reconnecting, Error, Closed | ✅ |
| SS-02 | `Error` properties | `message`, `cause`, `isRetryable` | ✅ |
| SS-03 | `Reconnecting` properties | `attempt`, `maxAttempts` | ✅ |

---

## Library E2E Tests

### E2E Infrastructure

> Status: ✅ Implemented

| Component | File | Description |
|-----------|------|-------------|
| Mock WHEP/WHIP Server | `src/jvmTest/kotlin/com/syncrobotic/webrtc/e2e/MockWhepWhipServer.kt` | Ktor embedded server: POST offer/answer, PATCH ICE, DELETE teardown, WebSocket relay |
| E2E Test Base | `src/jvmTest/kotlin/com/syncrobotic/webrtc/e2e/E2ETestBase.kt` | Server lifecycle, session factory helpers, assertion helpers |
| Session Extensions | `src/jvmTest/kotlin/com/syncrobotic/webrtc/e2e/SessionTestExtensions.kt` | `awaitSettled()`, `launchConnect()` for runTest compatibility |
| Native Availability | `src/jvmTest/kotlin/com/syncrobotic/webrtc/e2e/WebRTCNativeAvailable.kt` | Detects webrtc-java native libs; skips full tests if unavailable |

### E2E Prerequisites

| 項目 | 說明 | 備註 |
|------|------|------|
| **MediaMTX Server** | 本地或遠端運行 MediaMTX | 用於 WHEP/WHIP endpoint |
| **MediaMTX 配置** | 至少一個 path 開啟 (e.g. `raw`, `mobile-audio`) | `mediamtx.yml` 中設定 |
| **測試影像源** | ffmpeg 推一路測試影像到 MediaMTX | `ffmpeg -re -f lavfi -i testsrc=size=1280x720:rate=30 -c:v libx264 -f rtsp rtsp://localhost:8554/raw` |
| **麥克風** (E2E-2/3) | 測試裝置需有可用的麥克風 | JVM Desktop / Android / iOS 裝置 |
| **攝影機** (E2E-3) | 測試裝置需有可用的攝影機 | JVM Desktop / Android / iOS 裝置 |
| **網路環境** | App 與 MediaMTX 在同一網路可達 | 確認防火牆未擋 port 8889 |

#### 快速啟動測試環境

```bash
# 1. 啟動 MediaMTX
./mediamtx mediamtx.yml

# 2. 推送測試影像（另一個 terminal）
ffmpeg -re -f lavfi -i testsrc=size=1280x720:rate=30 \
  -c:v libx264 -preset ultrafast -tune zerolatency \
  -f rtsp rtsp://localhost:8554/raw

# 3. 驗證 WHEP endpoint 可用
curl -X POST http://localhost:8889/raw/whep \
  -H "Content-Type: application/sdp" \
  -d "v=0" --verbose
# 預期: HTTP 201 或 200
```

### E2E-1: Video Receive

> Status: ✅ Implemented
> File: `src/jvmTest/kotlin/com/syncrobotic/webrtc/e2e/E2E1VideoReceiveTest.kt`

| ID | Test | Type | Expected | Status |
|----|------|------|----------|--------|
| E2E-V-01 | WHEP offer sent and answer received | Signaling | SDP answer contains `v=0`, resourceUrl, etag | ✅ |
| E2E-V-01 | Answer flips recvonly → sendonly | Signaling | `a=sendonly` in answer | ✅ |
| E2E-V-02 | setRemoteVideoEnabled is local-only | Signaling | No extra signaling needed | ✅ |
| E2E-V-03 | ICE candidates sent to resource URL | Signaling | Server records ICE candidate | ✅ |
| E2E-V-04 | Server error triggers SignalingException | Signaling | `OFFER_REJECTED` code | ✅ |
| E2E-V-04 | Session teardown sends DELETE | Signaling | Session removed from server | ✅ |
| E2E-V-ICE | ICE server Link header parsed | Signaling | `iceServers` list populated | ✅ |
| E2E-V-01 | Connect and receive video session | Full WebRTC | State transitions, signaling verified | ✅ |
| E2E-V-02 | setRemoteVideoEnabled does not crash | Full WebRTC | No exception regardless of state | ✅ |
| E2E-V-03 | setAudioEnabled does not crash | Full WebRTC | No exception regardless of state | ✅ |
| E2E-V-04 | Close transitions to Closed | Full WebRTC | `SessionState.Closed` | ✅ |

### E2E-2: Audio Send

> Status: ✅ Implemented
> File: `src/jvmTest/kotlin/com/syncrobotic/webrtc/e2e/E2E2AudioSendTest.kt`

| ID | Test | Type | Expected | Status |
|----|------|------|----------|--------|
| E2E-A-01 | WHIP audio offer with sendonly | Signaling | Answer contains `a=recvonly` | ✅ |
| E2E-A-01 | SEND_AUDIO has audio but no video | Signaling | `m=audio` present, no `m=video` | ✅ |
| E2E-A-02 | Mute does not require re-signaling | Signaling | Only 1 POST request | ✅ |
| E2E-A-03 | Teardown after audio session | Signaling | Session removed | ✅ |
| E2E-A-01 | Connect and send audio session | Full WebRTC | State settled, signaling verified if connected | ✅ |
| E2E-A-02 | setMuted does not disconnect | Full WebRTC | State != Closed | ✅ |
| E2E-A-03 | toggleMute toggles state | Full WebRTC | No crash or disconnect | ✅ |

### E2E-3: Camera Send

> Status: ✅ Implemented
> File: `src/jvmTest/kotlin/com/syncrobotic/webrtc/e2e/E2E3CameraSendTest.kt`

| ID | Test | Type | Expected | Status |
|----|------|------|----------|--------|
| E2E-C-01 | WHIP video offer includes video+audio sendonly | Signaling | Answer has `a=recvonly`, both media | ✅ |
| E2E-C-01 | SEND_VIDEO has both audio and video | Signaling | `m=audio` and `m=video` in offer | ✅ |
| E2E-C-02 | Video disable is local only | Signaling | Only 1 POST request | ✅ |
| E2E-C-01 | Connect and send camera session | Full WebRTC | State settled, signaling verified if connected | ✅ |
| E2E-C-02 | setVideoEnabled disables camera track | Full WebRTC | No crash, state != Closed | ✅ |
| E2E-C-03 | switchCamera does not crash | Full WebRTC | No crash, state != Closed | ✅ |

### E2E-4: Bidirectional Audio

> Status: ✅ Implemented
> File: `src/jvmTest/kotlin/com/syncrobotic/webrtc/e2e/E2E4BidirectionalAudioTest.kt`

| ID | Test | Type | Expected | Status |
|----|------|------|----------|--------|
| E2E-B-01 | Bidirectional audio offer has sendrecv | Signaling | Answer contains `a=sendrecv` | ✅ |
| E2E-B-01 | No video in bidirectional audio | Signaling | `m=audio` only, no `m=video` | ✅ |
| E2E-B-02 | Mute while receiving is local only | Signaling | Only 1 POST request | ✅ |
| E2E-B-01 | Connect bidirectional audio session | Full WebRTC | State settled, signaling verified if connected | ✅ |
| E2E-B-02 | Mute while receiving keeps connection | Full WebRTC | State != Closed after mute | ✅ |

### E2E-5: DataChannel

> Status: ✅ Implemented
> File: `src/jvmTest/kotlin/com/syncrobotic/webrtc/e2e/E2E5DataChannelTest.kt`

| ID | Test | Type | Expected | Status |
|----|------|------|----------|--------|
| E2E-D-01 | Offer with DataChannel includes application media | Signaling | `m=application`, `webrtc-datachannel` | ✅ |
| E2E-D-01 | Server returns sctp-port in answer | Signaling | `a=sctp-port:5000` | ✅ |
| E2E-D-01 | Create reliable DataChannel before connect | Full WebRTC | Config queued, SDP includes DC | ✅ |
| E2E-D-02 | Create unreliable DataChannel | Full WebRTC | Config queued, settled gracefully | ✅ |
| E2E-D-04 | Close session closes DataChannels | Full WebRTC | `SessionState.Closed` | ✅ |

### E2E-6: Multi-Session Parallel

> Status: ✅ Implemented
> File: `src/jvmTest/kotlin/com/syncrobotic/webrtc/e2e/E2E6MultiSessionTest.kt`

| ID | Test | Type | Expected | Status |
|----|------|------|----------|--------|
| E2E-M-01 | Two sessions to different streams | Signaling | 2 sessions, different resourceUrls | ✅ |
| E2E-M-02 | Two video sessions to different endpoints | Signaling | Both streams recorded | ✅ |
| E2E-M-03 | Terminate one, other unaffected | Signaling | 1 session remains | ✅ |
| E2E-M-01 | Parallel ICE candidates to different sessions | Signaling | Each session gets its own candidates | ✅ |
| E2E-M-01 | Video + audio sessions parallel | Full WebRTC | Both sessions settled | ✅ |
| E2E-M-03 | Close one session, other continues | Full WebRTC | session1=Closed, session2 != Closed | ✅ |

### E2E-7: Public Callback APIs

> Status: ✅ Implemented
> File: `src/jvmTest/kotlin/com/syncrobotic/webrtc/e2e/E2E7CallbackApiTest.kt`

| ID | Test | Type | Expected | Status |
|----|------|------|----------|--------|
| E2E-CB-01 | RECEIVE_VIDEO offer valid for callback | Signaling | Answer contains `m=video` | ✅ |
| E2E-CB-02 | SEND_VIDEO offer for local track callback | Signaling | Answer contains `m=video` | ✅ |
| E2E-CB-01 | onRemoteVideoFrame callback set before connect | Full WebRTC | Callback registered, signaling verified if connected | ✅ |
| E2E-CB-02 | onLocalVideoTrack callback set before connect | Full WebRTC | Callback registered, signaling verified if connected | ✅ |
| E2E-CB-03 | Callback without composable renders | Full WebRTC | Session functions without UI, state != Closed | ✅ |

---

## Testcontainers MediaMTX Integration Tests

> Status: ✅ Implemented (skipped if Docker unavailable)
> Files: `src/jvmTest/kotlin/com/syncrobotic/webrtc/e2e/MediaMTXContainer.kt`, `MediaMTXIntegrationTest.kt`

| ID | Test | Type | Expected | Status |
|----|------|------|----------|--------|
| MTX-01 | WHEP offer to real MediaMTX | Signaling | Valid SDP answer or proper HTTP error | ✅ |
| MTX-02 | WHIP offer to real MediaMTX | Signaling | Valid SDP answer with resourceUrl | ✅ |
| MTX-03 | MediaMTX ICE server link headers | Signaling | iceServers parsed (possibly empty) | ✅ |
| MTX-04 | Session teardown with DELETE | Signaling | No exception on terminate | ✅ |
| MTX-05 | Bearer auth with default MediaMTX | Signaling | Token ignored (no auth configured) | ✅ |
| MTX-10 | WebRTCSession connect to real MediaMTX WHIP | Full WebRTC | State leaves Idle | ✅ |
| MTX-11 | WebRTCSession WHEP receive from MediaMTX | Full WebRTC | State settles, Closed on close | ✅ |
| MTX-12 | Multiple sessions to same MediaMTX | Full WebRTC | Both sessions settle independently | ✅ |

---

## Server Architecture Tests

> These tests validate the library works with different backend architectures.
> Each section includes an architecture diagram for clarity.

### Server Test Prerequisites

#### 全場景共用

| 項目 | 說明 |
|------|------|
| **Library App** | 使用 SyncAI-Lib-KmpWebRTC 的測試 App（VLMWebRTC 或自建） |
| **網路環境** | 所有元件在同一網段可互通，或有適當的路由/防火牆設定 |
| **測試影像源** | ffmpeg 推測試影像，或實際攝影機 |

#### S-1 所需

| 項目 | 說明 | 安裝方式 |
|------|------|---------|
| **MediaMTX** | 部署在 BE/Server 端 | [下載](https://github.com/bluenviron/mediamtx/releases) 或 `docker run bluenviron/mediamtx` |
| **mediamtx.yml** | 開啟 WebRTC (port 8889) + 至少一個 path | 見 `MediaMTXServer/config/mediamtx.yml` |

#### S-2 所需

| 項目 | 說明 | 備註 |
|------|------|------|
| **自建 BE Server** | HTTP server，實作 SDP proxy 邏輯 | Python/Node.js/Go，接收 App 的 SDP 並轉發到 IoT |
| **IoT 裝置 + WebRTC Server** | IoT 上運行 MediaMTX/Pion/GStreamer | IoT 需有 WHEP/WHIP endpoint |
| **認證機制** (選用) | BE 上的 JWT 驗證 | 測試 auth proxy 場景 |

#### S-3 所需

| 項目 | 說明 | 備註 |
|------|------|------|
| **BE + Media Server** | BE 上運行 MediaMTX 或 SFU (mediasoup/Janus) | BE 同時處理 signaling + media relay |
| **IoT 推流端** | IoT 用 RTSP/WHIP 推流到 BE | ffmpeg / 攝影機 / MediaMTX |
| **多台測試裝置** | 3+ 台 App 用於 1-to-N 測試 | Android 模擬器 / Desktop App |

#### S-4 所需

| 項目 | 說明 | 備註 |
|------|------|------|
| **WebSocket Signaling Server** | 自建 WS server 負責 SDP/ICE 轉發 | Node.js/Python/Go，不需 media server |
| **自訂 SignalingAdapter** | 實作 `SignalingAdapter` 介面的 WebSocket 版本 | Library 端程式碼 |
| **STUN Server** (S4-03) | 公共 STUN 或自建 | 預設 `stun:stun.l.google.com:19302` |
| **TURN Server** (S4-04) | 自建 coturn | `sudo apt install coturn` 或 Docker |
| **兩台不同網路的裝置** (S4-03/04) | 測試 NAT 穿越 | 不同 WiFi / 行動網路 |

#### S-5 所需

| 項目 | 說明 | 備註 |
|------|------|------|
| **IoT + MediaMTX** (影像) | IoT 上運行 MediaMTX，port 8889 | 處理 WHEP/WHIP 影像串流 |
| **IoT + DataChannel Server** (指令) | IoT 上運行輕量 WebRTC server，port 8890 | 只處理 SDP + DataChannel，見 ROADMAP 範例 |
| **BE Signaling Proxy** (選用) | 轉發兩路 SDP | 一路轉 8889，一路轉 8890 |

### S-1: MediaMTX Server on BE

> Status: ⬜ To implement (Manual)

> MediaMTX 部署在 BE/Server 端，同時處理 signaling + media relay。
> 推流來源分為「Library App 推流」和「IoT 裝置推流」兩種。

#### S-1a: Library App 推流 → MediaMTX → Library App 收看

```
App A (WHIP) ═══→ MediaMTX (BE) ═══→ App B (WHEP)
  推流端 (Library)   Media Server     收看端 (Library)
```

| ID | Test | Description | Expected | Status |
|----|------|-------------|----------|--------|
| S1a-01 | App WHIP video send | App 用 `SEND_VIDEO` 推攝影機到 MediaMTX | MediaMTX 收到視訊串流 | ⬜ |
| S1a-02 | App WHIP audio send | App 用 `SEND_AUDIO` 推麥克風到 MediaMTX | MediaMTX 收到音訊串流 | ⬜ |
| S1a-03 | App WHEP video receive | 另一個 App 用 `RECEIVE_VIDEO` 收看 | 視訊正常播放 | ⬜ |
| S1a-04 | Round-trip (推 + 收) | App A 推流，App B 收看，同時進行 | 延遲可接受，畫面流暢 | ⬜ |
| S1a-05 | Multiple viewers | 1 App 推流，3 App 收看同一串流 | 全部收到視訊 | ⬜ |

#### S-1b: IoT 裝置推流 → MediaMTX → Library App 收看

```
IoT (RTSP/WHIP) ═══→ MediaMTX (BE) ═══→ App (WHEP)
  攝影機/感測器         Media Server     收看端 (Library)
```

| ID | Test | Description | Expected | Status |
|----|------|-------------|----------|--------|
| S1b-01 | IoT RTSP 推流 | IoT 用 ffmpeg/gstreamer 推 RTSP 到 MediaMTX | MediaMTX 收到串流 | ⬜ |
| S1b-02 | App WHEP 收看 IoT | App 用 `RECEIVE_VIDEO` 收看 IoT 推的串流 | 視訊正常播放 | ⬜ |
| S1b-03 | App 發音訊到 IoT | App 用 `SEND_AUDIO` 推到 MediaMTX 另一路，IoT 收聽 | 語音對講場景 | ⬜ |
| S1b-04 | Multiple viewers | 1 IoT 推流，3 App 收看 | 全部收到視訊 | ⬜ |
| S1b-05 | IoT 斷線重連 | IoT 推流中斷後恢復 | MediaMTX 重新收到串流，App auto-reconnect | ⬜ |
| S1b-06 | MediaMTX 重啟 | MediaMTX 重啟 | IoT 重新推流，App auto-reconnect | ⬜ |

### S-2: BE Signaling Proxy + IoT WebRTC Server

> Status: ⬜ To implement (Manual)

> BE 不含 media server，只做 signaling proxy (SDP 轉發)。IoT 裝置自帶 WebRTC server (MediaMTX/Pion 等)。
> 媒體（RTP）走 App ↔ IoT 直連，不經過 BE。

```
App ─── HTTP (SDP) ───→ BE (Proxy) ─── HTTP (SDP) ───→ IoT (MediaMTX)
  ║                                                        ║
  ╚════════════════ RTP 直連 (P2P) ════════════════════════╝
```

| ID | Test | Description | Expected | Status |
|----|------|-------------|----------|--------|
| S2-01 | Signaling proxy basic | App → BE → IoT (MediaMTX)，BE 只轉發 SDP | Video plays, BE 不碰 RTP | ⬜ |
| S2-02 | Auth via BE | App sends JWT to BE, BE validates before proxying | Authorized apps connect | ⬜ |
| S2-03 | IoT offline (502) | IoT 無回應，BE returns 502 | App retries via RetryConfig | ⬜ |
| S2-04 | IoT reconnect | IoT 重啟後恢復，BE 可再次 proxy | App auto-reconnects successfully | ⬜ |
| S2-05 | Multiple IoT devices | BE routes to different IoT based on device ID | Each app connects to correct IoT | ⬜ |
| S2-06 | Video receive + audio send | App receives video (WHEP) + sends audio (WHIP) via BE proxy | 語音對講場景 work | ⬜ |

### S-3: BE with Media Server (SFU/Relay)

> Status: ⬜ To implement (Manual)

> BE 包含 media server，IoT 推一份到 BE，BE 負責分發給 N 個觀看者。
> 適合觀看者眾多、IoT 資源受限的場景。

```
IoT ═══ WHIP ═══→ BE (Media Server/SFU) ═══ WHEP ═══→ App 1
                                          ═══ WHEP ═══→ App 2
                                          ═══ WHEP ═══→ App N
```

| ID | Test | Description | Expected | Status |
|----|------|-------------|----------|--------|
| S3-01 | BE media relay basic | IoT → WHIP → BE (SFU) → WHEP → App | Video plays through relay | ⬜ |
| S3-02 | 1-to-N via relay | 1 IoT publisher, 3 app viewers through BE | All viewers receive video | ⬜ |
| S3-03 | Publisher disconnect | IoT 斷線，viewers 收到 error | Viewers 顯示 reconnecting/error | ⬜ |
| S3-04 | Viewer doesn't affect publisher | Viewer 離開/加入 | Publisher 連線不受影響 | ⬜ |

### S-4: P2P via WebSocket Signaling

> Status: ⬜ To implement (Manual)

> BE 只做 WebSocket 信令轉發（SDP + ICE candidates），不碰媒體。
> 媒體走 P2P 直連。**需要自訂 `SignalingAdapter`（WebSocket 實作）**。

```
App A ─── WebSocket ───→ BE (WS Server) ←─── WebSocket ─── App B
  ║                      (只轉發 SDP)                        ║
  ╚══════════════════ RTP 直連 (P2P) ════════════════════════╝
```

| ID | Test | Description | Expected | Status |
|----|------|-------------|----------|--------|
| S4-01 | WebSocket signaling | Custom `SignalingAdapter` via WebSocket | P2P connection established | ⬜ |
| S4-02 | P2P video call | Two apps, WS signaling, direct RTP | Both send/receive video | ⬜ |
| S4-03 | STUN traversal | Apps on different LAN, STUN configured | NAT traversal, P2P works | ⬜ |
| S4-04 | TURN fallback | Apps behind symmetric NAT, TURN configured | Connection via TURN relay | ⬜ |
| S4-05 | P2P with DataChannel | WS signaling + DataChannel messaging | SDP exchange + data messages both work | ⬜ |

### S-5: IoT WebRTC Server + BE Signaling + DataChannel

> Status: ⬜ To implement (Manual)

> IoT 自帶輕量 WebRTC server（只處理 DataChannel，不處理影像）。
> 影像走 MediaMTX，指令走獨立的 DataChannel session。

```
App ═══ WHEP ═══════════════════════════════ IoT (MediaMTX, port 8889)
App ═══ DataChannel (WHIP) ═════════════════ IoT (DC Server, port 8890)
  └── signaling 可經過 BE proxy 或直連
```

| ID | Test | Description | Expected | Status |
|----|------|-------------|----------|--------|
| S5-01 | Video + DataChannel 分離 | Video session (WHEP) + DataChannel session (WHIP) 各自獨立 | 兩個 session 同時 work | ⬜ |
| S5-02 | DataChannel 指令送達 | App 送 JSON 指令到 IoT DC server | IoT 收到並回應 | ⬜ |
| S5-03 | 經 BE proxy | 兩個 session 都經 BE signaling proxy | BE 轉發 SDP，媒體/DC 直連 IoT | ⬜ |

---

## Client Architecture & Connection Tests

### Client Test Prerequisites

| 項目 | 說明 | 使用場景 |
|------|------|---------|
| **2+ 台測試裝置/模擬器** | Android 實機/模擬器、iOS 實機、JVM Desktop | C-1, C-3, C-4 |
| **VLMWebRTC App** (或自建測試 App) | 使用 Library 的參考 App | 所有場景 |
| **MediaMTX Server** | 做為中間的 media server | C-1 (透過 server), C-3, C-4 |
| **WebSocket Signaling Server** | P2P 場景的信令伺服器 | C-1 (P2P 模式) |
| **IoT 裝置 + WebRTC Server** | 實際 IoT 設備或模擬 | C-2 |
| **非 Library 的 WebRTC client** | 瀏覽器 (WebRTC sample) 或其他 SDK 的 App | C-2 (相容性測試) |
| **攝影機 + 麥克風** | 測試裝置上的硬體 | C-1, C-2 |

#### 快速準備多裝置測試

```bash
# 方式 1: JVM Desktop 多實例（最簡單）
# Terminal 1 — 推流端
cd VLMWebRTC && ./gradlew :composeApp:run

# Terminal 2 — 收看端（改 port 或 stream path 避免衝突）
cd VLMWebRTC && ./gradlew :composeApp:run

# 方式 2: Android 模擬器 + Desktop
# 模擬器跑 Android App，Desktop 跑 JVM App

# 方式 3: 瀏覽器做為第二端（C-2 相容性測試）
# 開啟 http://localhost:8889/raw 用 MediaMTX 內建 web player
```

### C-1: Two Library Apps Bidirectional Streaming

> Status: ⬜ To implement (Manual)

| ID | Test | Description | Expected | Status |
|----|------|-------------|----------|--------|
| C1-01 | Video call | Both apps use `MediaConfig.VIDEO_CALL` | Both send/receive video + audio | ⬜ |
| C1-02 | Audio intercom | Both use `MediaConfig.BIDIRECTIONAL_AUDIO` | Both send/receive audio | ⬜ |
| C1-03 | Camera switch | One app calls `switchCamera()` | Video switches, remote sees new camera | ⬜ |
| C1-04 | Video + DataChannel | Video call + DataChannel 指令同時進行 | 影像和資料訊息互不干擾 | ⬜ |
| C1-05 | Media controls during call | `setMuted()`, `setVideoEnabled()` during active call | Media toggles work, remote side sees changes | ⬜ |

### C-2: Library App + External WebRTC IoT Device

> Status: ⬜ To implement (Manual)

| ID | Test | Description | Expected | Status |
|----|------|-------------|----------|--------|
| C2-01 | Receive from IoT camera | IoT runs MediaMTX/Pion, app connects WHEP | Video plays in app | ⬜ |
| C2-02 | Send audio to IoT | App sends mic via WHIP to IoT | IoT receives audio | ⬜ |
| C2-03 | DataChannel commands | App sends JSON commands, IoT responds | Bidirectional messaging works | ⬜ |
| C2-04 | 語音對講 (intercom) | App 收視訊 + 發音訊 to IoT, same or separate sessions | Video receive + audio send 同時 work | ⬜ |
| C2-05 | IoT 不同 WebRTC 套件 | IoT 用 Pion/GStreamer/aiortc (非 MediaMTX) | Library 相容標準 WHEP/WHIP 協議 | ⬜ |

### C-3: Multiple VideoRenderer Support

> Status: ⬜ To implement (Manual)

| ID | Test | Description | Expected | Status |
|----|------|-------------|----------|--------|
| C3-01 | 2 video sessions | Two `WebRTCSession` + `VideoRenderer` | Both display video | ⬜ |
| C3-02 | 4 video sessions | Grid layout with 4 streams | All display, performance acceptable | ⬜ |
| C3-03 | Independent lifecycle | Close one session, other continues | No interference | ⬜ |
| C3-04 | Different MediaConfig per session | One RECEIVE_VIDEO, one VIDEO_CALL | Each behaves according to its config | ⬜ |

### C-4: 1-to-N Connection

> Status: ⬜ To implement (Manual)

| ID | Test | Description | Expected | Status |
|----|------|-------------|----------|--------|
| C4-01 | 3 viewers, 1 publisher | 1 WHIP publisher, 3 WHEP viewers via MediaMTX | All viewers see video | ⬜ |
| C4-02 | Viewer joins late | Publisher already streaming, new viewer connects | Viewer gets video immediately | ⬜ |
| C4-03 | Viewer leaves | One viewer disconnects | Others unaffected | ⬜ |
| C4-04 | Publisher reconnect | Publisher 斷線重連 | Viewers auto-reconnect after publisher recovers | ⬜ |

### C-5: DataChannel Communication

> Status: ⬜ To implement (Manual)

| ID | Test | Description | Expected | Status |
|----|------|-------------|----------|--------|
| C5-01 | Text messaging | Send/receive JSON commands | Messages delivered in order | ⬜ |
| C5-02 | Binary messaging | Send/receive binary data (images) | Data delivered intact | ⬜ |
| C5-03 | Multiple channels | Create 2+ DataChannels on same session | All channels work independently | ⬜ |
| C5-04 | Channel with video | DataChannel + video receive on same session | Both work simultaneously | ⬜ |
| C5-05 | High-frequency messaging | Rapid DataChannel messages (10+ msgs/sec) | All delivered, no loss in reliable mode | ⬜ |

---

## Test Summary

| Category | Test Count | Automation | Status | Location |
|----------|-----------|------------|--------|----------|
| **Unit Tests** (Config classes) | 24 | `./gradlew jvmTest` | ✅ Implemented | `src/jvmTest/.../config/` |
| **Unit Tests** (StreamRetryHandler) | 6 | `./gradlew jvmTest` | ✅ Implemented | `src/jvmTest/.../config/StreamRetryHandlerTest.kt` |
| **Unit Tests** (HttpSignalingAdapter) | 13 | `./gradlew jvmTest` | ✅ Implemented | `src/jvmTest/.../signaling/HttpSignalingAdapterTest.kt` |
| **Unit Tests** (Audio Config & State) | 4 | `./gradlew jvmTest` | ✅ Implemented | `src/jvmTest/.../audio/` |
| **Unit Tests** (Player State & UI) | 5 | `./gradlew jvmTest` | ✅ Implemented | `src/jvmTest/.../ui/` |
| **Unit Tests** (DataChannel Config) | 4 | `./gradlew jvmTest` | ✅ Implemented | `src/jvmTest/.../datachannel/DataChannelConfigTest.kt` |
| **Unit Tests** (WebRTC Data Models) | 4 | `./gradlew jvmTest` | ✅ Implemented | `src/jvmTest/.../WebRTCStatsTest.kt`, `EnumValuesTest.kt` |
| **Unit Tests** (Signaling types) | 4+ | `./gradlew jvmTest` | ✅ Implemented | `src/jvmTest/.../signaling/SignalingAdapterTest.kt`, `SignalingExceptionTest.kt` |
| **Unit Tests** (SessionState) | 3 | `./gradlew jvmTest` | ✅ Implemented | `src/jvmTest/.../session/SessionStateTest.kt` |
| **E2E** (Signaling-level, 7 classes) | ~30 | `./gradlew jvmTest --tests "*.e2e.*"` | ✅ Implemented | `src/jvmTest/.../e2e/E2E*.kt` |
| **E2E** (Full WebRTC, 7 classes) | ~15 | `./gradlew jvmTest --tests "*.e2e.*"` | ✅ Implemented (skip if no native) | `src/jvmTest/.../e2e/E2E*.kt` |
| **Testcontainers MediaMTX** | 8 | `./gradlew jvmTest --tests "*.MediaMTX*"` | ✅ Implemented (skip if no Docker) | `src/jvmTest/.../e2e/MediaMTXIntegrationTest.kt` |
| **Server Architecture** (S-1~S-5) | ~28 | Manual | ⬜ To implement | — |
| **Client Architecture** (C-1~C-5) | ~22 | Manual | ⬜ To implement | — |
| **Total** | **~170** | | **120 automated / 50 manual** | |

---

## How to Run Tests

### Level 1: Unit Tests + E2E (Mock Server)

> **前提條件**: 無（純 Kotlin + in-process Ktor mock server）
> **預計耗時**: ~30 秒

#### Step 1 — 執行所有 Level 1 測試

```bash
./gradlew jvmTest
```

#### Step 2 — 確認結果

```
BUILD SUCCESSFUL
```

測試報告位置：
- **HTML**: `build/reports/tests/jvmTest/index.html`（用瀏覽器打開）
- **XML**: `build/test-results/jvmTest/` (CI 用)

#### Step 3 — 確認測試數量

HTML 報告應顯示：
- **207 tests**
- **0 failures**
- 部分 Full WebRTC tests 可能顯示 **skipped**（正常，需要 native libs 完整支援）

#### 個別執行

```bash
# 只跑 Unit Tests
./gradlew jvmTest --tests "com.syncrobotic.webrtc.config.*" \
                  --tests "com.syncrobotic.webrtc.audio.*" \
                  --tests "com.syncrobotic.webrtc.ui.*" \
                  --tests "com.syncrobotic.webrtc.datachannel.*" \
                  --tests "com.syncrobotic.webrtc.session.*" \
                  --tests "com.syncrobotic.webrtc.signaling.*" \
                  --tests "com.syncrobotic.webrtc.*Test"

# 只跑 E2E Tests (signaling + full WebRTC)
./gradlew jvmTest --tests "com.syncrobotic.webrtc.e2e.E2E*"

# 只跑特定測試類別
./gradlew jvmTest --tests "com.syncrobotic.webrtc.e2e.E2E1VideoReceiveTest"

# 只跑特定測試方法
./gradlew jvmTest --tests "com.syncrobotic.webrtc.config.RetryConfigTest.RC-01*"
```

#### E2E 測試行為說明

| 測試類型 | 需要什麼 | 沒有時的行為 |
|---------|---------|-------------|
| Signaling tests | 無（MockWhepWhipServer 自動啟動） | 一定會跑 ✅ |
| Full WebRTC tests | webrtc-java native libs | `assumeWebRTCAvailable()` → skip |

---

### Level 2: Testcontainers MediaMTX Integration

> **前提條件**: Docker runtime（Colima 或 Docker Desktop）已安裝並啟動
> **預計耗時**: 首次 ~30 秒（拉 image），之後 ~5 秒

#### Step 0 — 安裝 Docker runtime

macOS 推薦使用 **Colima**（輕量 Docker runtime），避免 Docker Desktop 授權問題：

```bash
# 安裝 Colima + Docker CLI
brew install colima docker

# 啟動 Colima
colima start

# 確認 Docker 可用
docker info
# 應顯示 Server Version 和 Operating System
```

> **也可以用 Docker Desktop**，但需注意 Docker Engine 29+ 的 API 相容性問題（見下方說明）。

#### Step 1 — 執行 MediaMTX 測試

```bash
./gradlew jvmTest --tests "com.syncrobotic.webrtc.e2e.MediaMTX*" --no-build-cache
```

> **注意**：首次執行或 Docker 環境變更後，建議加 `--no-build-cache` 避免使用舊的 skip 結果。

首次執行時，Testcontainers 會自動：
1. 拉取 `testcontainers/ryuk` 和 `bluenviron/mediamtx:latest` Docker images
2. 啟動 MediaMTX container（暴露 RTSP 8554 + WebRTC 8889）
3. 等待 port ready
4. 執行 8 個 integration tests
5. 自動停止並清理 container

#### Step 2 — 確認結果

```
BUILD SUCCESSFUL
```

確認測試實際執行（非 skip）：

```bash
# 查看 XML 報告
head -2 build/test-results/jvmTest/TEST-com.syncrobotic.webrtc.e2e.MediaMTXIntegrationTest.xml
# 應顯示: tests="8" skipped="0" failures="0" errors="0"
```

或打開 HTML 報告：`build/reports/tests/jvmTest/index.html`，搜尋 `MediaMTXIntegrationTest` 確認 8 tests passed。

#### Step 3 — 測試結束後關閉 Docker

```bash
# 查看是否有殘留 container（正常應為空）
docker ps

# 關閉 Colima（停止 Docker Engine + VM）
colima stop

# 確認已關閉
colima status
# 應顯示: colima is not running
```

#### Docker 不可用時的行為

| Docker 狀態 | 測試結果 |
|-------------|---------|
| Docker 未安裝 | 8 tests **skipped** (不算 failure) |
| Colima / Docker Desktop 未啟動 | 8 tests **skipped** |
| Docker 啟動但 image pull 失敗（無網路） | 8 tests **skipped** |
| Docker 正常運行（Colima 或 Docker Desktop） | 8 tests **executed** |

#### Docker Engine 29+ API 相容性

> Testcontainers 1.21.x 使用 docker-java 3.4.x，預設 Docker API version 1.32。
> Docker Engine 29+ 最低要求 API 1.44。
>
> **已解決**：`build.gradle.kts` 設定 `systemProperty("api.version", "1.44")` 強制使用新版 API。
> 同時自動偵測 Colima socket 路徑 (`~/.colima/default/docker.sock`)。
>
> 如果仍遇到 `client version 1.32 is too old` 錯誤，確認：
> 1. `build.gradle.kts` 中 `tasks.withType<Test>()` 區塊存在 `api.version` 設定
> 2. 使用 `--no-build-cache` 清除舊的 cache 結果
> 3. 執行 `./gradlew --stop` 重啟 Gradle daemon

---

### Test Coverage Report

> **前提條件**: build.gradle.kts 已加入 Kover plugin（已配置）

```bash
# 產生 HTML 覆蓋率報告
./gradlew koverHtmlReport

# 報告位置
open build/reports/kover/html/index.html
```

其他覆蓋率指令：

```bash
# XML 報告（CI / SonarQube / Codecov 用）
./gradlew koverXmlReport

# Terminal 直接印出覆蓋率摘要
./gradlew koverLog
```

---

### Quick Reference

```bash
# 一鍵跑全部（Unit + E2E + Testcontainers）
./gradlew jvmTest

# 一鍵跑全部 + 覆蓋率報告
./gradlew jvmTest koverHtmlReport && open build/reports/kover/html/index.html

# 只跑 Level 1
./gradlew jvmTest --tests "com.syncrobotic.webrtc.*"

# 只跑 Level 2
./gradlew jvmTest --tests "com.syncrobotic.webrtc.e2e.MediaMTX*"

# 強制重跑（忽略 cache）
./gradlew jvmTest --rerun
```
