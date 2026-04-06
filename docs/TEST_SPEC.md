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

### E2E-6: Multi-Session Parallel

| ID | Test | Description | Expected |
|----|------|-------------|----------|
| E2E-M-01 | Video + Audio sessions parallel | `RECEIVE_VIDEO` session + `SEND_AUDIO` session simultaneously | Both sessions Connected, no interference |
| E2E-M-02 | Two video sessions parallel | Two `RECEIVE_VIDEO` sessions to different endpoints | Both display video independently |
| E2E-M-03 | Close one, other continues | Close video session while audio session active | Audio session unaffected |

### E2E-7: Public Callback APIs

| ID | Test | Description | Expected |
|----|------|-------------|----------|
| E2E-CB-01 | `onRemoteVideoFrame` fires | Set callback before connect, `RECEIVE_VIDEO` | Callback invoked with platform video frame |
| E2E-CB-02 | `onLocalVideoTrack` fires | Set callback before connect, `SEND_VIDEO` | Callback invoked with platform video track |
| E2E-CB-03 | Callback without composable | Use only callbacks, no VideoRenderer | Frames received, no UI needed |

---

## Server Architecture Tests

> These tests validate the library works with different backend architectures.
> Each section includes an architecture diagram for clarity.

### S-1: MediaMTX Server on BE

> MediaMTX 部署在 BE/Server 端，同時處理 signaling + media relay。
> 推流來源分為「Library App 推流」和「IoT 裝置推流」兩種。

#### S-1a: Library App 推流 → MediaMTX → Library App 收看

```
App A (WHIP) ═══→ MediaMTX (BE) ═══→ App B (WHEP)
  推流端 (Library)   Media Server     收看端 (Library)
```

| ID | Test | Description | Expected |
|----|------|-------------|----------|
| S1a-01 | App WHIP video send | App 用 `SEND_VIDEO` 推攝影機到 MediaMTX | MediaMTX 收到視訊串流 |
| S1a-02 | App WHIP audio send | App 用 `SEND_AUDIO` 推麥克風到 MediaMTX | MediaMTX 收到音訊串流 |
| S1a-03 | App WHEP video receive | 另一個 App 用 `RECEIVE_VIDEO` 收看 | 視訊正常播放 |
| S1a-04 | Round-trip (推 + 收) | App A 推流，App B 收看，同時進行 | 延遲可接受，畫面流暢 |
| S1a-05 | Multiple viewers | 1 App 推流，3 App 收看同一串流 | 全部收到視訊 |

#### S-1b: IoT 裝置推流 → MediaMTX → Library App 收看

```
IoT (RTSP/WHIP) ═══→ MediaMTX (BE) ═══→ App (WHEP)
  攝影機/感測器         Media Server     收看端 (Library)
```

| ID | Test | Description | Expected |
|----|------|-------------|----------|
| S1b-01 | IoT RTSP 推流 | IoT 用 ffmpeg/gstreamer 推 RTSP 到 MediaMTX | MediaMTX 收到串流 |
| S1b-02 | App WHEP 收看 IoT | App 用 `RECEIVE_VIDEO` 收看 IoT 推的串流 | 視訊正常播放 |
| S1b-03 | App 發音訊到 IoT | App 用 `SEND_AUDIO` 推到 MediaMTX 另一路，IoT 收聽 | 語音對講場景 |
| S1b-04 | Multiple viewers | 1 IoT 推流，3 App 收看 | 全部收到視訊 |
| S1b-05 | IoT 斷線重連 | IoT 推流中斷後恢復 | MediaMTX 重新收到串流，App auto-reconnect |
| S1b-06 | MediaMTX 重啟 | MediaMTX 重啟 | IoT 重新推流，App auto-reconnect |

### S-2: BE Signaling Proxy + IoT WebRTC Server

> BE 不含 media server，只做 signaling proxy (SDP 轉發)。IoT 裝置自帶 WebRTC server (MediaMTX/Pion 等)。
> 媒體（RTP）走 App ↔ IoT 直連，不經過 BE。

```
App ─── HTTP (SDP) ───→ BE (Proxy) ─── HTTP (SDP) ───→ IoT (MediaMTX)
  ║                                                        ║
  ╚════════════════ RTP 直連 (P2P) ════════════════════════╝
```

| ID | Test | Description | Expected |
|----|------|-------------|----------|
| S2-01 | Signaling proxy basic | App → BE → IoT (MediaMTX)，BE 只轉發 SDP | Video plays, BE 不碰 RTP |
| S2-02 | Auth via BE | App sends JWT to BE, BE validates before proxying | Authorized apps connect |
| S2-03 | IoT offline (502) | IoT 無回應，BE returns 502 | App retries via RetryConfig |
| S2-04 | IoT reconnect | IoT 重啟後恢復，BE 可再次 proxy | App auto-reconnects successfully |
| S2-05 | Multiple IoT devices | BE routes to different IoT based on device ID | Each app connects to correct IoT |
| S2-06 | Video receive + audio send | App receives video (WHEP) + sends audio (WHIP) via BE proxy | 語音對講場景 work |

### S-3: BE with Media Server (SFU/Relay)

> BE 包含 media server，IoT 推一份到 BE，BE 負責分發給 N 個觀看者。
> 適合觀看者眾多、IoT 資源受限的場景。

```
IoT ═══ WHIP ═══→ BE (Media Server/SFU) ═══ WHEP ═══→ App 1
                                          ═══ WHEP ═══→ App 2
                                          ═══ WHEP ═══→ App N
```

| ID | Test | Description | Expected |
|----|------|-------------|----------|
| S3-01 | BE media relay basic | IoT → WHIP → BE (SFU) → WHEP → App | Video plays through relay |
| S3-02 | 1-to-N via relay | 1 IoT publisher, 3 app viewers through BE | All viewers receive video |
| S3-03 | Publisher disconnect | IoT 斷線，viewers 收到 error | Viewers 顯示 reconnecting/error |
| S3-04 | Viewer doesn't affect publisher | Viewer 離開/加入 | Publisher 連線不受影響 |

### S-4: P2P via WebSocket Signaling

> BE 只做 WebSocket 信令轉發（SDP + ICE candidates），不碰媒體。
> 媒體走 P2P 直連。**需要自訂 `SignalingAdapter`（WebSocket 實作）**。

```
App A ─── WebSocket ───→ BE (WS Server) ←─── WebSocket ─── App B
  ║                      (只轉發 SDP)                        ║
  ╚══════════════════ RTP 直連 (P2P) ════════════════════════╝
```

| ID | Test | Description | Expected |
|----|------|-------------|----------|
| S4-01 | WebSocket signaling | Custom `SignalingAdapter` via WebSocket | P2P connection established |
| S4-02 | P2P video call | Two apps, WS signaling, direct RTP | Both send/receive video |
| S4-03 | STUN traversal | Apps on different LAN, STUN configured | NAT traversal, P2P works |
| S4-04 | TURN fallback | Apps behind symmetric NAT, TURN configured | Connection via TURN relay |
| S4-05 | P2P with DataChannel | WS signaling + DataChannel messaging | SDP exchange + data messages both work |

### S-5: IoT WebRTC Server + BE Signaling + DataChannel

> IoT 自帶輕量 WebRTC server（只處理 DataChannel，不處理影像）。
> 影像走 MediaMTX，指令走獨立的 DataChannel session。

```
App ═══ WHEP ═══════════════════════════════ IoT (MediaMTX, port 8889)
App ═══ DataChannel (WHIP) ═════════════════ IoT (DC Server, port 8890)
  └── signaling 可經過 BE proxy 或直連
```

| ID | Test | Description | Expected |
|----|------|-------------|----------|
| S5-01 | Video + DataChannel 分離 | Video session (WHEP) + DataChannel session (WHIP) 各自獨立 | 兩個 session 同時 work |
| S5-02 | DataChannel 指令送達 | App 送 JSON 指令到 IoT DC server | IoT 收到並回應 |
| S5-03 | 經 BE proxy | 兩個 session 都經 BE signaling proxy | BE 轉發 SDP，媒體/DC 直連 IoT |

---

## Client Architecture & Connection Tests

### C-1: Two Library Apps Bidirectional Streaming

| ID | Test | Description | Expected |
|----|------|-------------|----------|
| C1-01 | Video call | Both apps use `MediaConfig.VIDEO_CALL` | Both send/receive video + audio |
| C1-02 | Audio intercom | Both use `MediaConfig.BIDIRECTIONAL_AUDIO` | Both send/receive audio |
| C1-03 | Camera switch | One app calls `switchCamera()` | Video switches, remote sees new camera |
| C1-04 | Video + DataChannel | Video call + DataChannel 指令同時進行 | 影像和資料訊息互不干擾 |
| C1-05 | Media controls during call | `setMuted()`, `setVideoEnabled()` during active call | Media toggles work, remote side sees changes |

### C-2: Library App + External WebRTC IoT Device

| ID | Test | Description | Expected |
|----|------|-------------|----------|
| C2-01 | Receive from IoT camera | IoT runs MediaMTX/Pion, app connects WHEP | Video plays in app |
| C2-02 | Send audio to IoT | App sends mic via WHIP to IoT | IoT receives audio |
| C2-03 | DataChannel commands | App sends JSON commands, IoT responds | Bidirectional messaging works |
| C2-04 | 語音對講 (intercom) | App 收視訊 + 發音訊 to IoT, same or separate sessions | Video receive + audio send 同時 work |
| C2-05 | IoT 不同 WebRTC 套件 | IoT 用 Pion/GStreamer/aiortc (非 MediaMTX) | Library 相容標準 WHEP/WHIP 協議 |

### C-3: Multiple VideoRenderer Support

| ID | Test | Description | Expected |
|----|------|-------------|----------|
| C3-01 | 2 video sessions | Two `WebRTCSession` + `VideoRenderer` | Both display video |
| C3-02 | 4 video sessions | Grid layout with 4 streams | All display, performance acceptable |
| C3-03 | Independent lifecycle | Close one session, other continues | No interference |
| C3-04 | Different MediaConfig per session | One RECEIVE_VIDEO, one VIDEO_CALL | Each behaves according to its config |

### C-4: 1-to-N Connection

| ID | Test | Description | Expected |
|----|------|-------------|----------|
| C4-01 | 3 viewers, 1 publisher | 1 WHIP publisher, 3 WHEP viewers via MediaMTX | All viewers see video |
| C4-02 | Viewer joins late | Publisher already streaming, new viewer connects | Viewer gets video immediately |
| C4-03 | Viewer leaves | One viewer disconnects | Others unaffected |
| C4-04 | Publisher reconnect | Publisher 斷線重連 | Viewers auto-reconnect after publisher recovers |

### C-5: DataChannel Communication

| ID | Test | Description | Expected |
|----|------|-------------|----------|
| C5-01 | Text messaging | Send/receive JSON commands | Messages delivered in order |
| C5-02 | Binary messaging | Send/receive binary data (images) | Data delivered intact |
| C5-03 | Multiple channels | Create 2+ DataChannels on same session | All channels work independently |
| C5-04 | Channel with video | DataChannel + video receive on same session | Both work simultaneously |
| C5-05 | High-frequency messaging | Rapid DataChannel messages (10+ msgs/sec) | All delivered, no loss in reliable mode |

---

## Test Summary

| Category | Test Count | Automation | Status |
|----------|-----------|------------|--------|
| **Unit Tests** (Config, State, Models) | ~45 | Automated (`./gradlew jvmTest`) | ✅ Implemented |
| **Unit Tests** (HttpSignalingAdapter) | 13 | Automated (MockEngine) | ✅ Implemented |
| **Unit Tests** (MediaConfig, TransceiverDirection, VideoCaptureConfig) | 25 | Automated | ✅ Implemented |
| **Unit Tests** (SignalingException) | 4 | Automated | ✅ Implemented |
| **Library E2E** | ~20 | Semi-automated (requires server) | To implement |
| **Server Architecture** | ~28 | Manual | To implement |
| **Client Architecture** | ~22 | Manual | To implement |
| **Total** | **~157** | | |

### Running Tests

```bash
# Unit tests
./gradlew jvmTest

# Specific test class
./gradlew jvmTest --tests "com.syncrobotic.webrtc.config.RetryConfigTest"
```
