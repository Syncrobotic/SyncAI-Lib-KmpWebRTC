# SyncAI-Lib-KmpWebRTC SDK Roadmap

> 📅 Created: 2026-03-26  
> � Last Updated: 2026-03-30  
> 📌 Status: v2.0 API Complete — Phase 1-5 Done

---

## 1. SDK Positioning & Target Users

### 1.1 Core Positioning

**One-liner**: Kotlin Multiplatform WebRTC SDK designed for IoT/robotics control scenarios, enabling developers to achieve low-latency streaming and bidirectional communication without dealing with WebRTC's underlying complexity.

### 1.2 Target Users

| User Type | Use Case | Priority |
|-----------|----------|----------|
| **IoT/Robotics Developers** | Remote control, multi-camera monitoring, bidirectional voice | 🔴 P0 |
| **Smart Home Applications** | Doorbell video, security camera live streaming | 🟡 P1 |
| **Education/Training Platforms** | Remote labs, equipment operation teaching | 🟡 P1 |
| **Industrial Automation** | AGV monitoring, production line video feedback | 🟢 P2 |

### 1.3 Competitive Differentiation

| Feature | SyncAI-Lib-KmpWebRTC | WebRTC Native SDK | Other KMP Solutions |
|---------|--------------------|--------------------|---------------------|
| Cross-platform | ✅ Android/iOS/JVM/JS/WasmJS | ❌ Separate per platform | Partial support |
| Learning curve | Low (Session + Composable API) | High | Medium |
| Low-latency optimization | ⏳ Preset planned | Manual configuration | Not guaranteed |
| Built-in signaling | ✅ WHEP/WHIP + pluggable `SignalingAdapter` | ❌ Must implement yourself | Partial |
| IoT scenario optimization | ✅ Multi-video sources, DataChannel, custom auth | ❌ General-purpose design | ❌ |

---

## 2. Feature Planning

### 2.1 Current Features (v2.0)

#### Core API (v2 — Session-based)

| Feature | Platform Support | Status |
|---------|-----------------|--------|
| **WhepSession** (video/audio receive) | Android/iOS/JVM/JS/WasmJS | ✅ Implemented |
| **WhipSession** (audio send) | Android/iOS/JVM/JS/WasmJS | ✅ Implemented |
| **SignalingAdapter** interface | All | ✅ Implemented |
| **WhepSignalingAdapter** (built-in WHEP) | All | ✅ Implemented |
| **WhipSignalingAdapter** (built-in WHIP) | All | ✅ Implemented |
| **SignalingAuth** (Bearer, Cookies, CookieStorage, Custom) | All | ✅ Implemented |
| **SessionState** (reactive `StateFlow`) | All | ✅ Implemented |
| **Real-time Stats** (`session.stats: StateFlow<WebRTCStats?>`) | All | ✅ Implemented |
| **RetryConfig** (DEFAULT / AGGRESSIVE / DISABLED) | All | ✅ Implemented |
| **VideoRenderer** (session-based Composable) | Android/iOS/JVM | ✅ Implemented |
| **AudioPushPlayer** (session-based Composable) | Android/iOS/JVM/JS/WasmJS | ✅ Implemented |
| **DataChannel** (text + binary) | Android/iOS/JVM/JS/WasmJS | ✅ Implemented |
| DataChannel presets (reliable/unreliable/maxLifetime) | All | ✅ Implemented |
| Auto-reconnect with exponential backoff | All | ✅ Implemented |
| Multiple simultaneous sessions | All | ✅ Supported |
| **PlayerEvent** (FirstFrameRendered, StreamInfoReceived) | Android/iOS/JVM | ✅ Implemented |

#### Legacy API (v1 — Deprecated, will be removed in v3.0)

| Feature | Status |
|---------|--------|
| `WebRTCClient` (direct use) | `@Deprecated` → use WhepSession/WhipSession |
| `BidirectionalPlayer` | `@Deprecated` → use VideoRenderer + AudioPushPlayer |
| `VideoRenderer(config: StreamConfig)` | `@Deprecated` → use VideoRenderer(session) |
| `AudioPushPlayer(config: AudioPushConfig)` | `@Deprecated` → use AudioPushPlayer(session) |
| `WhepSignaling` / `WhipSignaling` | `@Deprecated` → use WhepSignalingAdapter/WhipSignalingAdapter |
| `WebSocketSignaling` | `@Deprecated` → implement custom SignalingAdapter |
| `StreamConfig`, `ServerEndpoints`, `StreamProtocol`, `StreamDirection`, `SignalingType` | `@Deprecated` |
| `AudioRetryConfig` | `@Deprecated` → use RetryConfig |
| `BidirectionalConfig` | `@Deprecated` |
| `AudioPushClient.release()` | `@Deprecated` → use close() |
| `WebRTCListener.onError(String)` | `@Deprecated` → use onError(Throwable) |

#### Platform Status

| Platform | Video Receive | Audio Send | DataChannel | Composable UI |
|----------|--------------|------------|-------------|---------------|
| Android | ✅ | ✅ | ✅ | ✅ |
| iOS (Physical Device) | ✅ | ✅ | ✅ | ✅ |
| iOS Simulator | ❌ GoogleWebRTC limitation | | | |
| JVM/Desktop | ✅ | ✅ | ✅ | ✅ |
| JS (Browser) | ⚠️ Stub | ⚠️ Stub | ✅ | ⚠️ Placeholder |
| WasmJS (Browser) | ⚠️ Stub | ⚠️ Stub | ✅ | ⚠️ Placeholder |

### 2.2 Next: IoT Enhancements

| Feature | Description | Platform | Priority | Status |
|---------|-------------|----------|----------|--------|
| **VideoPushPlayer** | Push video from phone camera (WHIP) | Android/iOS | 🔴 P0 | ❌ Not started |
| **CameraCapturer** | Cross-platform camera capture abstraction | Android/iOS | 🔴 P0 | ❌ Not started |
| **Low-latency Preset** | `WebRTCConfig.LOW_LATENCY` with tuned jitter buffer, FEC, codec | All | 🟡 P1 | ❌ Not started |
| **MultiStreamManager** | Convenience API for managing multiple sessions | All | 🟢 P2 | ❌ Not started |

#### VideoPushPlayer API Design

```kotlin
@Composable
fun VideoPushPlayer(
    session: WhipSession,   // v2 pattern: session-based
    modifier: Modifier = Modifier,
    autoStart: Boolean = false,
    onStateChange: (PlayerState) -> Unit = {}
): VideoPushController

data class VideoPushConfig(
    val resolution: Resolution = Resolution.HD_720,
    val fps: Int = 30,
    val bitrate: Int = 2_000_000,
    val cameraFacing: CameraFacing = CameraFacing.BACK
)
```

#### Low-latency Configuration

```kotlin
val LOW_LATENCY: WebRTCConfig = WebRTCConfig(
    iceTransportPolicy = "all",
    bundlePolicy = "max-bundle",
    // Low-latency specific settings
    jitterBufferMinMs = 0,
    jitterBufferMaxMs = 100,
    preferredVideoCodec = "H264",
    enableFec = false,
    enableNack = true,
    enableDtx = true
)
```

### 2.3 Next: Developer Experience

| Feature | Description | Priority | Status |
|---------|-------------|----------|--------|
| **Pluggable Logger** | `WebRTCLogger` interface, injectable globally | 🟡 P1 | ❌ Not started |
| **Structured Error Codes** | `WebRTCError` sealed class with error codes | 🟢 P2 | ❌ Not started |
| **Codec Preference Settings** | SDP munging for codec priority | 🟢 P2 | ❌ Not started |
| **WebSocketSignalingAdapter** | Built-in WebSocket signaling (replace deprecated `WebSocketSignaling`) | 🟡 P1 | ❌ Not started |

> **Already completed in v2.0:**
> - ✅ Signaling Abstract Interface → `SignalingAdapter`
> - ✅ Enhanced Auto-reconnect → `RetryConfig` (DEFAULT/AGGRESSIVE/DISABLED)
> - ✅ Error recoverability flags → `SessionState.Error(isRetryable)`
> - ✅ Type-safe authentication → `SignalingAuth` (Bearer, Cookies, CookieStorage, Custom)

#### Pluggable Logger (Planned)

```kotlin
interface WebRTCLogger {
    fun verbose(tag: String, message: String)
    fun debug(tag: String, message: String)
    fun info(tag: String, message: String)
    fun warn(tag: String, message: String, error: Throwable? = null)
    fun error(tag: String, message: String, error: Throwable)
}

// Usage
WebRTCLoggerFactory.setLogger(myTimberLogger)
WebRTCLoggerFactory.setLogLevel(LogLevel.DEBUG)
```

### 2.4 Next: Quality & Open Source Readiness

| Item | Description | Priority | Status |
|------|-------------|----------|--------|
| **Unit Tests** | Core logic tests (Signaling, Config, State) | 🔴 P0 | ✅ **207 tests passing** |
| **E2E Tests** | In-process mock server round-trip tests | 🟡 P1 | ⚠️ Spec designed, not implemented |
| **API Documentation** | Dokka generation + GitHub Pages | 🔴 P0 | ❌ Not started |
| **Sample Projects** | samples/robot-control | 🔴 P0 | ❌ Not started |
| **Community Templates** | Issue/PR templates | 🟡 P1 | ❌ Not started |
| **CONTRIBUTING.md** | Contribution guide | 🟡 P1 | ❌ Not started |

### 2.5 Future: Advanced Features

| Feature | Description | Use Case |
|---------|-------------|----------|
| **Screen Sharing** | Desktop/Mobile screen capture | Remote collaboration |
| **Local Recording** | Record streams to file | Archive/playback |
| **Simulcast** | Send multiple resolutions simultaneously | Multi-party conference (SFU) |
| **E2E Encryption** | Insertable Streams | Privacy-sensitive applications |
| **JS/WasmJS Full Support** | Complete VideoRenderer + AudioPush for browsers | Web applications |

---

## 3. Performance Targets

### 3.1 Latency Targets

| Scenario | Target Latency | Description |
|----------|---------------|-------------|
| **Local Network (LAN)** | < 100ms | Direct WiFi |
| **General Network** | < 300ms | Via TURN |
| **Cross-region** | < 500ms | CDN/Edge Server |

### 3.2 Resource Usage Targets

| Metric | Android | iOS | JVM |
|--------|---------|-----|-----|
| **Memory (single stream)** | < 50MB | < 50MB | < 100MB |
| **Memory (3 streams)** | < 150MB | < 150MB | < 250MB |
| **CPU (1080p receive)** | < 15% | < 15% | < 10% |
| **CPU (720p send)** | < 20% | < 20% | < 15% |
| **APK/IPA size increase** | < 5MB | < 5MB | N/A |

### 3.3 Reliability Targets

| Metric | Target |
|--------|--------|
| **Auto-reconnect success rate** | > 95% (after network recovery) |
| **Connection establishment time** | < 2s (LAN) / < 5s (WAN) |
| **DataChannel delivery rate** | 99.9% (reliable mode) |

---

## 4. Project Structure

### 4.1 Current Structure (Single-module KMP)

```
SyncAI-Lib-KmpWebRTC/
├── src/
│   ├── commonMain/kotlin/com/syncrobotic/webrtc/
│   │   ├── config/          # WebRTCConfig, RetryConfig, IceServer
│   │   ├── signaling/       # SignalingAdapter, SignalingAuth, WHEP/WHIP adapters
│   │   ├── session/         # WhepSession, WhipSession, SessionState
│   │   ├── audio/           # AudioPushConfig, AudioPushController, AudioPushClient
│   │   ├── ui/              # VideoRenderer, PlayerState, PlayerEvent
│   │   └── datachannel/     # DataChannel, DataChannelConfig, DataChannelListener
│   ├── androidMain/         # Android actual implementations
│   ├── iosMain/             # iOS actual implementations
│   ├── jvmMain/             # JVM actual implementations
│   ├── jsMain/              # JS stub implementations
│   └── wasmJsMain/          # WasmJS stub implementations
├── docs/
│   ├── ROADMAP.md           # This document
│   ├── README_V2.md         # v2 API spec (source of truth for README.md)
│   └── TEST_SPEC.md         # Test specification
├── .github/
│   ├── copilot-instructions.md
│   ├── instructions/        # KMP and WebRTC instruction files
│   └── workflows/           # CI/CD
└── build.gradle.kts
```

### 4.2 Future: Modularized Structure (Considered for v3.0)

```
SyncAI-Lib-KmpWebRTC/
├── core/                       # Core API + abstractions
├── signaling-whep/             # WHEP adapter (optional)
├── signaling-whip/             # WHIP adapter (optional)
├── signaling-websocket/        # WebSocket adapter (optional)
├── compose-ui/                 # Compose UI components (optional)
├── samples/                    # Sample projects
└── docs/
```

> **Decision**: Modularization deferred to v3.0. Current single-module approach is simpler and sufficient for the current API surface.

---

## 5. Version History & Milestones

### Completed

| Version | Date | Key Content |
|---------|------|-------------|
| **v1.x** | 2026-03 | Video receive (WHEP), audio send (WHIP), DataChannel, auto-reconnect, 5 platform support |
| **v2.0** (in progress) | 2026-03-30 | Session API, SignalingAdapter, SignalingAuth, deprecated v1 types, 207 unit tests |

### v2.0 Migration Phases (All Complete)

| Phase | Description | Status |
|-------|-------------|--------|
| Phase 1 | `SignalingAdapter` interface + `WhepSignalingAdapter`/`WhipSignalingAdapter` | ✅ Done |
| Phase 2 | Deprecate legacy config types (`StreamProtocol`, `StreamDirection`, etc.) | ✅ Done |
| Phase 3 | `WhepSession` / `WhipSession` expect/actual across 5 platforms | ✅ Done |
| Phase 4 | Session-based `VideoRenderer` + `AudioPushPlayer` composables | ✅ Done |
| Phase 5 | Consistency cleanup (`onError(Throwable)`, `AudioPushClient.close()`) | ✅ Done |

### Planned

| Version | Key Content |
|---------|-------------|
| **v2.1** | WebSocketSignalingAdapter, Pluggable Logger |
| **v2.2** | VideoPushPlayer, CameraCapturer, Low-latency preset |
| **v2.3** | Sample projects, Dokka documentation, E2E tests |
| **v3.0** | Remove all deprecated v1 APIs, optional modularization |

---

## 6. Open Questions

- [x] Multiple VideoRenderer instances? → **Supported**, each session has independent PeerConnection
- [x] Signaling abstraction approach? → **`SignalingAdapter` interface** (implemented in v2.0)
- [x] When to deprecate v1 API? → **v2.0** (deprecated), **v3.0** (removed)
- [ ] When to modularize? → Deferred to v3.0
- [ ] Is Simulcast support needed?
- [ ] Priority for JS/WasmJS full implementation?
- [ ] Should a backend signaling server reference implementation be provided?

---

## 7. References
- [WebRTC Standard](https://www.w3.org/TR/webrtc/)
- [WHEP Draft](https://www.ietf.org/archive/id/draft-murillo-whep-01.html)
- [WHIP Draft](https://www.ietf.org/archive/id/draft-ietf-wish-whip-01.html)
