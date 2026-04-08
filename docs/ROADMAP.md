# SyncAI-Lib-KmpWebRTC SDK Roadmap

> Created: 2026-03-26  
> Last Updated: 2026-04-06  
> Status: v2.0 Complete — Unified WebRTCSession API  

---

## 1. SDK Positioning & Target Users

### 1.1 Core Positioning

**One-liner**: Kotlin Multiplatform WebRTC SDK — highly customizable, HTTP-based signaling with flexible media direction control. Supports any combination of sending/receiving video and audio through a single unified session, with zero WebRTC boilerplate.

### 1.2 Target Users

| User Type | Use Case | Priority |
|-----------|----------|----------|
| **IoT/Robotics Developers** | Remote control, multi-camera monitoring, bidirectional voice | P0 |
| **Smart Home Applications** | Doorbell video, security camera live streaming | P1 |
| **Education/Training Platforms** | Remote labs, equipment operation teaching | P1 |
| **Industrial Automation** | AGV monitoring, production line video feedback | P2 |

### 1.3 Competitive Differentiation

| Feature | SyncAI-Lib-KmpWebRTC | WebRTC Native SDK | Other KMP Solutions |
|---------|--------------------|--------------------|---------------------|
| Cross-platform | Android/iOS/JVM (JS/WasmJS stub) | Separate per platform | Partial support |
| Learning curve | Low (Session + Composable API) | High | Medium |
| Flexible media direction | Any send/recv combination via `MediaConfig` | Manual transceiver setup | Not available |
| Built-in signaling | HTTP (WHEP/WHIP) + pluggable `SignalingAdapter` | Must implement yourself | Partial |
| Camera capture | Built-in cross-platform | Manual per platform | Not available |
| IoT optimization | Multi-stream, DataChannel, custom auth, auto-reconnect | General-purpose | Limited |

---

## 2. Feature Status

### 2.1 v2.0 Features (Current — All Complete)

#### Core API

| Feature | Android | iOS | JVM | JS | WasmJS |
|---------|---------|-----|-----|----|----|
| **WebRTCSession** (unified session) | ✅ | ✅ | ✅ | Stub | Stub |
| **MediaConfig** (flexible media directions) | ✅ | ✅ | ✅ | Stub | Stub |
| **HttpSignalingAdapter** (unified HTTP signaling) | ✅ | ✅ | ✅ | ✅ | ✅ |
| **SignalingAdapter** interface (custom signaling) | ✅ | ✅ | ✅ | ✅ | ✅ |
| **SignalingAuth** (Bearer, Cookies, CookieStorage, Custom) | ✅ | ✅ | ✅ | ✅ | ✅ |
| **SessionState** (reactive `StateFlow`) | ✅ | ✅ | ✅ | ✅ | ✅ |
| **Real-time Stats** (`session.stats`) | ✅ | ✅ | ✅ | Stub | Stub |
| **RetryConfig** (DEFAULT / AGGRESSIVE / PERSISTENT / DISABLED) | ✅ | ✅ | ✅ | ✅ | ✅ |
| **DataChannel** (text + binary, reliable/unreliable presets) | ✅ | ✅ | ✅ | ✅ | ✅ |
| Auto-reconnect with exponential backoff + jitter | ✅ | ✅ | ✅ | ✅ | ✅ |
| Multiple simultaneous sessions | ✅ | ✅ | ✅ | ✅ | ✅ |
| ICE mode control (Full ICE / Trickle ICE) | ✅ | ✅ | ✅ | Stub | Stub |
| STUN/TURN server support | ✅ | ✅ | ✅ | Stub | Stub |

#### Media Capabilities

| Feature | Android | iOS | JVM | JS | WasmJS |
|---------|---------|-----|-----|----|----|
| Video receive (remote) | ✅ | ✅ | ✅ | Stub | Stub |
| Audio receive (remote) | ✅ | ✅ | ✅ | Stub | Stub |
| Audio send (microphone) | ✅ | ✅ | ✅ | Stub | Stub |
| Video send (camera capture) | ✅ | ✅ | ✅ | Stub | Stub |
| Bidirectional audio (sendrecv) | ✅ | ✅ | ✅ | Stub | Stub |
| Full video call (send + recv all) | ✅ | ✅ | ✅ | Stub | Stub |
| Front/rear camera switch | ✅ | ✅ | ✅ | — | — |

#### Composables (Compose Multiplatform UI)

| Composable | Android | iOS | JVM | JS | WasmJS |
|------------|---------|-----|-----|----|----|
| **VideoRenderer** (remote video display) | ✅ | ✅ | ✅ | Stub | Stub |
| **CameraPreview** (local camera preview) | ✅ | ✅ | ✅ | Stub | Stub |
| **AudioPushPlayer** (mic controls) | ✅ | ✅ | ✅ | Stub | Stub |
| **AudioPlayer** (remote audio controls) | ✅ | ✅ | ✅ | Stub | Stub |

#### Media Controls

| Method | Description | Status |
|--------|-------------|--------|
| `setMuted(bool)` | Microphone mute/unmute | ✅ |
| `setVideoEnabled(bool)` | Camera on/off | ✅ |
| `setAudioEnabled(bool)` | Remote audio playback on/off | ✅ |
| `setRemoteVideoEnabled(bool)` | Remote video rendering on/off | ✅ |
| `setSpeakerphoneEnabled(bool)` | Speaker/earpiece toggle | ✅ |
| `switchCamera()` | Front/rear camera toggle | ✅ |

#### Low-level Access (for custom implementations)

| Feature | Description | Status |
|---------|-------------|--------|
| `onRemoteVideoFrame` callback | Platform-specific video frames for custom rendering | ✅ |
| `onLocalVideoTrack` callback | Platform-specific video track for custom preview | ✅ |
| `SignalingAdapter` interface | Implement any signaling protocol (WebSocket, Firebase, MQTT, gRPC) | ✅ |

### 2.2 Removed in v2.0 (Breaking Change from v1.x)

All legacy APIs have been removed. v2.0 is a clean break:

- `WhepSession` / `WhipSession` → replaced by `WebRTCSession` + `MediaConfig`
- `WhepSignalingAdapter` / `WhipSignalingAdapter` → replaced by `HttpSignalingAdapter`
- `StreamConfig` / `ServerEndpoints` / `StreamProtocol` / `StreamDirection` → removed
- `BidirectionalPlayer` / `BidirectionalConfig` → use composables separately
- `WebSocketSignaling` / `WhepSignaling` → implement `SignalingAdapter`
- `AudioPushClient` / `AudioRetryConfig` → removed
- `WebRTCClient` → now internal engine (not a public API)

---

## 3. Planned Features

### 3.1 Next: Developer Experience

| Feature | Description | Priority | Status |
|---------|-------------|----------|--------|
| **Pluggable Logger** | `WebRTCLogger` interface, injectable globally | P1 | Not started |
| **WebSocketSignalingAdapter** | Built-in WebSocket signaling adapter | P1 | Not started |
| **Structured Error Codes** | `SignalingErrorCode` expansion, connection error taxonomy | P2 | Partial (SignalingErrorCode exists) |
| **Codec Preference Settings** | SDP munging for codec priority (H.264 vs VP8) | P2 | Not started |

#### Pluggable Logger (Planned)

```kotlin
interface WebRTCLogger {
    fun verbose(tag: String, message: String)
    fun debug(tag: String, message: String)
    fun info(tag: String, message: String)
    fun warn(tag: String, message: String, error: Throwable? = null)
    fun error(tag: String, message: String, error: Throwable)
}

WebRTCLoggerFactory.setLogger(myTimberLogger)
WebRTCLoggerFactory.setLogLevel(LogLevel.DEBUG)
```

### 3.2 Next: Performance & Quality

| Feature | Description | Priority | Status |
|---------|-------------|----------|--------|
| **Low-latency Preset** | `WebRTCConfig.LOW_LATENCY` with tuned jitter buffer, FEC, codec | P1 | Not started |
| **E2E Tests** | In-process mock server round-trip tests | P1 | Spec designed, not implemented |
| **API Documentation** | Dokka generation + GitHub Pages | P1 | Not started |
| **Sample Projects** | Complete sample app demonstrating all features | P1 | VLMWebRTC exists as reference |

#### Low-latency Configuration (Planned)

```kotlin
val LOW_LATENCY: WebRTCConfig = WebRTCConfig(
    iceTransportPolicy = "all",
    bundlePolicy = "max-bundle",
    jitterBufferMinMs = 0,
    jitterBufferMaxMs = 100,
    preferredVideoCodec = "H264",
    enableFec = false,
    enableNack = true,
    enableDtx = true
)
```

### 3.3 Next: Advanced Features

| Feature | Description | Use Case | Priority | Status |
|---------|-------------|----------|----------|--------|
| **MultiStreamManager** | Convenience API for managing multiple sessions | Multi-camera monitoring | P2 | Not started |
| **Screen Sharing** | Desktop/Mobile screen capture | Remote collaboration | P2 | Not started |
| **Local Recording** | Record streams to file | Archive/playback | P2 | Not started |
| **Simulcast** | Send multiple resolutions simultaneously | Multi-party SFU | P3 | Not started |
| **E2E Encryption** | Insertable Streams API | Privacy-sensitive apps | P3 | Not started |

### 3.4 JS/WasmJS Full Support

Currently JS and WasmJS platforms have **stub implementations only**. Full browser support requires implementing `WebRTCSession`, `VideoRenderer`, and media controls using the native browser `RTCPeerConnection` API.

| Item | Status | Notes |
|------|--------|-------|
| `WebRTCSession` (JS/WasmJS) | Stub (`TODO`) | Needs browser `RTCPeerConnection` integration |
| `VideoRenderer` (JS/WasmJS) | Stub (`TODO`) | Needs HTML `<video>` element rendering |
| `CameraPreview` (JS/WasmJS) | Stub (`TODO`) | Needs `getUserMedia` API |
| `AudioPushPlayer` (JS/WasmJS) | Stub (`TODO`) | Needs `getUserMedia` API |
| `AudioPlayer` (JS/WasmJS) | Stub (`TODO`) | Needs `<audio>` element |
| `DataChannel` (JS/WasmJS) | ✅ Implemented | Uses native `RTCDataChannel` |
| `HttpSignalingAdapter` (JS/WasmJS) | ✅ Implemented | Uses Ktor JS client |

> **Decision**: JS/WasmJS full implementation is deferred. Priority depends on demand. The `SignalingAdapter` interface and `DataChannel` already work, so browser-based signaling and data messaging are functional.

---

## 4. Performance Targets

### 4.1 Latency Targets

| Scenario | Target Latency | Description |
|----------|---------------|-------------|
| **Local Network (LAN)** | < 100ms | Direct WiFi, no TURN |
| **General Network** | < 300ms | Via STUN, possible TURN |
| **Cross-region** | < 500ms | CDN/Edge Server relay |

### 4.2 Resource Usage Targets

| Metric | Android | iOS | JVM |
|--------|---------|-----|-----|
| **Memory (single stream)** | < 50MB | < 50MB | < 100MB |
| **Memory (3 streams)** | < 150MB | < 150MB | < 250MB |
| **CPU (1080p receive)** | < 15% | < 15% | < 10% |
| **CPU (720p send)** | < 20% | < 20% | < 15% |

### 4.3 Reliability Targets

| Metric | Target |
|--------|--------|
| **Auto-reconnect success rate** | > 95% (after network recovery) |
| **Connection establishment time** | < 2s (LAN) / < 5s (WAN) |
| **DataChannel delivery rate** | 99.9% (reliable mode) |

---

## 5. Project Structure

```
SyncAI-Lib-KmpWebRTC/
├── src/
│   ├── commonMain/kotlin/com/syncrobotic/webrtc/
│   │   ├── config/          # WebRTCConfig, RetryConfig, IceServer, MediaConfig, VideoCaptureConfig
│   │   ├── signaling/       # SignalingAdapter, SignalingAuth, HttpSignalingAdapter
│   │   ├── session/         # WebRTCSession, SessionState
│   │   ├── audio/           # AudioPushPlayer, AudioPlayer, AudioPushConfig
│   │   ├── ui/              # VideoRenderer, CameraPreview, PlayerState, PlayerEvent
│   │   └── datachannel/     # DataChannel, DataChannelConfig, DataChannelListener
│   ├── androidMain/         # Android actual implementations (Camera2, org.webrtc)
│   ├── iosMain/             # iOS actual implementations (GoogleWebRTC, AVFoundation)
│   ├── jvmMain/             # JVM actual implementations (webrtc-java)
│   ├── jsMain/              # JS stub implementations
│   └── wasmJsMain/          # WasmJS stub implementations
├── docs/
│   ├── ROADMAP.md           # This document
│   └── TEST_SPEC.md         # Test specification
├── .github/
│   └── workflows/           # CI/CD
└── build.gradle.kts
```

---

## 6. Version History

### Completed

| Version | Date | Key Content |
|---------|------|-------------|
| **v1.x** | 2026-03 | Video receive (WHEP), audio send (WHIP), DataChannel, auto-reconnect, 5 platform support |
| **v2.0** | 2026-04 | Unified `WebRTCSession` + `MediaConfig`, camera capture, `HttpSignalingAdapter`, `CameraPreview`, `AudioPlayer`, public callbacks, removed all deprecated v1 APIs |

### v2.0 Development Phases (All Complete)

| Phase | Description | Status |
|-------|-------------|--------|
| Phase 1 | `HttpSignalingAdapter` (unified signaling) | ✅ Done |
| Phase 2 | `MediaConfig` + `VideoCaptureConfig` + `TransceiverDirection` | ✅ Done |
| Phase 3 | `WebRTCSession` (unified session, all platforms) | ✅ Done |
| Phase 4 | `createFlexibleOffer()` in WebRTCClient (all platforms) | ✅ Done |
| Phase 5 | Camera Capture (Android Camera2, iOS AVFoundation, JVM webrtc-java) | ✅ Done |
| Phase 6 | `VideoRenderer`, `CameraPreview`, `AudioPushPlayer`, `AudioPlayer` for WebRTCSession | ✅ Done |
| Phase 7 | Public callbacks (`onRemoteVideoFrame`, `onLocalVideoTrack`) | ✅ Done |
| Phase 8 | `setRemoteVideoEnabled()` — complete media control surface | ✅ Done |
| Phase 9 | Remove all deprecated v1 APIs (breaking change) | ✅ Done |

---

## 7. Open Questions

- [x] Multiple VideoRenderer instances? → Supported, each session has independent PeerConnection
- [x] Signaling abstraction approach? → `SignalingAdapter` interface
- [x] When to deprecate/remove v1 API? → Removed in v2.0
- [x] Camera capture support? → Implemented in v2.0 (Android/iOS/JVM)
- [x] Bidirectional audio? → Supported via `MediaConfig.BIDIRECTIONAL_AUDIO`
- [x] Full video call? → Supported via `MediaConfig.VIDEO_CALL`
- [ ] When to modularize? → Deferred, single-module approach is sufficient
- [ ] Priority for JS/WasmJS full implementation? → Depends on demand
- [ ] Should a backend signaling server reference implementation be provided?
- [ ] Is Simulcast support needed for target use cases?

---

## 8. References
- [WebRTC Standard](https://www.w3.org/TR/webrtc/)
- [WHEP Draft](https://www.ietf.org/archive/id/draft-murillo-whep-01.html)
- [WHIP Draft](https://www.ietf.org/archive/id/draft-ietf-wish-whip-01.html)
