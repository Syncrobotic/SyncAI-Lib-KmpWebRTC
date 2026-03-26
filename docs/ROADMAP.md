# SyncAI-Lib-KmpWebRTC SDK Roadmap

> 📅 Created: 2026-03-26  
> 📌 Status: Planning

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
| Cross-platform | ✅ Android/iOS/JVM (Web in progress) | ❌ Separate per platform | Partial support |
| Learning curve | Low (Composable API) | High | Medium |
| Low-latency optimization | ⏳ Preset planned | Manual configuration | Not guaranteed |
| Built-in signaling | ✅ WHEP/WHIP/WebSocket | ❌ Must implement yourself | Partial |
| IoT scenario optimization | ✅ Multi-video sources, DataChannel | ❌ General-purpose design | ❌ |

---

## 2. Feature Planning

### 2.1 Current Features (v1.x)

| Feature | Platform Support | Status |
|---------|-----------------|--------|
| Video Receive (WHEP) | Android/iOS/JVM | ✅ Verified |
| Audio Send (WHIP) | Android/iOS/JVM | ✅ Verified |
| Audio Receive | Android/iOS/JVM | ✅ Verified |
| DataChannel (Text) | Android/iOS/JVM | ✅ Implemented |
| DataChannel (Binary) | Android/iOS/JVM | ✅ Implemented |
| VideoRenderer Composable | Android/iOS/JVM | ✅ Available |
| Multiple VideoRenderer instances | Android/iOS/JVM | ✅ Supported (each instance has independent PeerConnection, no shared state conflicts) |
| AudioPushPlayer Composable | Android/iOS/JVM/JS | ✅ Available |
| BidirectionalPlayer | Android/iOS/JVM | ✅ Available |
| Web (JS) | Browser | ⚠️ Stub (VideoRenderer not implemented, only WebRTCClient + AudioPush basically usable) |
| Web (WasmJS) | Browser | ❌ Incomplete (missing VideoRenderer, AudioPushPlayer) |
| Auto-reconnect | All | ✅ Available |
| Connection Stats | All | ✅ Implemented (requires manual getStats() call, not StateFlow) |

### 2.2 Phase 1: IoT Core Features

**Estimated timeline**: 2-3 weeks

| Feature | Description | Platform | Priority |
|---------|-------------|----------|----------|
| **VideoPushPlayer** | Push video from phone camera (WHIP) | Android/iOS | 🔴 P0 |
| **MultiStreamManager** | Convenience API for managing multiple video sources (Note: multiple VideoRenderers can already be manually composed) | All | 🟡 P1 |
| **Low-latency Preset** | Default config: UDP only, disable jitter buffer | All | 🔴 P0 |
| **Real-time Network Quality Monitoring** | RTT/Packet loss/Bitrate StateFlow | All | 🔴 P0 |
| **CameraCapturer** | Cross-platform camera capture abstraction | Android/iOS | 🔴 P0 |

#### 2.2.1 VideoPushPlayer API Design

```kotlin
@Composable
fun VideoPushPlayer(
    config: VideoPushConfig,
    modifier: Modifier = Modifier,
    autoStart: Boolean = false,
    onStateChange: (VideoPushState) -> Unit = {}
): VideoPushController

data class VideoPushConfig(
    val whipUrl: String,
    val resolution: Resolution = Resolution.HD_720,
    val fps: Int = 30,
    val bitrate: Int = 2_000_000,  // 2 Mbps
    val cameraFacing: CameraFacing = CameraFacing.BACK,
    val lowLatencyMode: Boolean = true,
    val webrtcConfig: WebRTCConfig = WebRTCConfig.SENDER
)

sealed class VideoPushState {
    object Idle : VideoPushState()
    object Connecting : VideoPushState()
    object Streaming : VideoPushState()
    data class Error(val message: String, val isRecoverable: Boolean) : VideoPushState()
    data class Reconnecting(val attempt: Int, val maxAttempts: Int) : VideoPushState()
}
```

#### 2.2.2 MultiStreamManager API Design

```kotlin
class MultiStreamManager {
    val streams: StateFlow<Map<String, StreamState>>
    
    fun addStream(id: String, config: StreamConfig)
    fun removeStream(id: String)
    fun reconnect(id: String)
    fun reconnectAll()
    
    // Get controller for a specific stream
    fun getController(id: String): VideoPlayerController?
    
    // Aggregated statistics
    val aggregatedStats: StateFlow<AggregatedStats>
}

// Compose integration
@Composable
fun MultiVideoGrid(
    manager: MultiStreamManager,
    layout: GridLayout = GridLayout.AUTO,
    modifier: Modifier = Modifier
)
```

#### 2.2.3 Low-latency Configuration

```kotlin
object WebRTCConfig {
    // Existing
    val DEFAULT: WebRTCConfig
    val SENDER: WebRTCConfig
    
    // New
    val LOW_LATENCY: WebRTCConfig = WebRTCConfig(
        iceTransportPolicy = IceTransportPolicy.ALL,
        bundlePolicy = BundlePolicy.MAX_BUNDLE,
        // Low-latency specific settings
        jitterBufferMinMs = 0,
        jitterBufferMaxMs = 100,
        preferredVideoCodec = VideoCodec.H264_BASELINE,
        enableFec = false,        // Disable FEC to reduce latency
        enableNack = true,        // Keep NACK for packet loss handling
        enableDtx = true          // DTX to save bandwidth
    )
}
```

### 2.3 Phase 2: Developer Experience

**Estimated timeline**: 1-2 weeks

| Feature | Description | Priority |
|---------|-------------|----------|
| **Signaling Abstract Interface** | Allow users to implement custom signaling | 🔴 P0 |
| **Structured Error System** | Error codes + recoverability flags | 🟡 P1 |
| **Pluggable Logger** | User-injectable custom logger | 🟡 P1 |
| **Enhanced Auto-reconnect Strategy** | Configurable backoff policies | 🟡 P1 |
| **Codec Preference Settings** | Priority: H264 > VP8 > VP9 | 🟢 P2 |

#### 2.3.1 Signaling Abstract Interface

```kotlin
// Users can implement their own signaling
interface SignalingClient {
    suspend fun sendOffer(offer: String): SignalingResult
    suspend fun sendAnswer(answer: String)
    suspend fun sendIceCandidate(candidate: IceCandidate)
    
    val remoteIceCandidates: Flow<IceCandidate>
    val connectionState: StateFlow<SignalingState>
    
    fun close()
}

data class SignalingResult(
    val sdpAnswer: String,
    val resourceUrl: String? = null,
    val iceServers: List<IceServer> = emptyList()
)

// Usage
val customSignaling = MyFirebaseSignaling(roomId = "room-123")

VideoRenderer(
    signaling = customSignaling,
    modifier = Modifier.fillMaxSize()
)
```

#### 2.3.2 Structured Error System

```kotlin
sealed class WebRTCError(
    val code: String,
    val message: String,
    val isRecoverable: Boolean,
    val cause: Throwable? = null
) {
    // Connection errors (WC = WebRTC Connection)
    class ConnectionTimeout : WebRTCError("WC001", "Connection timeout", true)
    class IceNegotiationFailed : WebRTCError("WC002", "ICE negotiation failed", true)
    class DtlsHandshakeFailed : WebRTCError("WC003", "DTLS handshake failed", true)
    class ConnectionLost : WebRTCError("WC004", "Connection lost", true)
    
    // Signaling errors (WS = WebRTC Signaling)
    class SignalingTimeout : WebRTCError("WS001", "Signaling timeout", true)
    class InvalidSdp(details: String) : WebRTCError("WS002", "Invalid SDP: $details", false)
    class SignalingServerError(status: Int) : WebRTCError("WS003", "Server error: $status", true)
    
    // DataChannel errors (WD = WebRTC DataChannel)
    class DataChannelNotOpen : WebRTCError("WD001", "DataChannel not open", false)
    class MessageTooLarge(size: Int) : WebRTCError("WD002", "Message too large: $size bytes", false)
    
    // Media errors (WM = WebRTC Media)
    class CameraAccessDenied : WebRTCError("WM001", "Camera access denied", false)
    class MicrophoneAccessDenied : WebRTCError("WM002", "Microphone access denied", false)
    class CodecNotSupported(codec: String) : WebRTCError("WM003", "Unsupported codec: $codec", false)
}
```

#### 2.3.3 Pluggable Logger

```kotlin
interface WebRTCLogger {
    fun verbose(tag: String, message: String)
    fun debug(tag: String, message: String)
    fun info(tag: String, message: String)
    fun warn(tag: String, message: String, error: Throwable? = null)
    fun error(tag: String, message: String, error: Throwable)
}

// Global configuration
WebRTCClient.setLogger(object : WebRTCLogger {
    override fun debug(tag: String, message: String) {
        Timber.tag(tag).d(message)  // Integrate with Timber
    }
    // ...
})

// Or use built-in logger level
WebRTCClient.setLogLevel(LogLevel.DEBUG)
```

### 2.4 Phase 3: Quality & Open Source Readiness

**Estimated timeline**: 2 weeks

| Item | Description | Priority |
|------|-------------|----------|
| **Unit Tests** | Core logic tests (Signaling, Config, State) | 🔴 P0 |
| **Integration Tests** | End-to-end connection tests | 🟡 P1 |
| **API Documentation** | Dokka generation + GitHub Pages | 🔴 P0 |
| **Sample Projects** | samples/robot-control | 🔴 P0 |
| **Community Templates** | Issue/PR templates | 🟡 P1 |
| **CONTRIBUTING.md** | Contribution guide | 🟡 P1 |
| **ARCHITECTURE.md** | Architecture design document | 🟢 P2 |

### 2.5 Phase 4: Advanced Features (Future)

| Feature | Description | Use Case |
|---------|-------------|----------|
| **Screen Sharing** | Desktop/Mobile screen capture | Remote collaboration |
| **Local Recording** | Record streams to file | Archive/playback |
| **Simulcast** | Send multiple resolutions simultaneously | Multi-party conference (SFU) |
| **E2E Encryption** | Insertable Streams | Privacy-sensitive applications |
| **AR/VR Support** | 360 video, spatial audio | XR applications |

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

## 4. Project Structure Planning

### 4.1 Current Structure

```
SyncAI-Lib-KmpWebRTC/
├── src/
│   ├── commonMain/      # Shared code
│   ├── androidMain/     # Android implementation
│   ├── iosMain/         # iOS implementation
│   ├── jvmMain/         # JVM implementation
│   ├── jsMain/          # JS implementation
│   └── wasmJsMain/      # WasmJS implementation
└── build.gradle.kts
```

### 4.2 Planned Structure (Modularized)

```
SyncAI-Lib-KmpWebRTC/
├── core/                       # Core API + abstractions
│   └── src/commonMain/
│       ├── WebRTCClient.kt
│       ├── signaling/SignalingClient.kt  (interface)
│       └── error/WebRTCError.kt
├── signaling-whep/             # WHEP implementation (optional)
├── signaling-whip/             # WHIP implementation (optional)
├── signaling-websocket/        # WebSocket implementation (optional)
├── compose-ui/                 # Compose UI components (optional)
│   └── src/commonMain/
│       ├── VideoRenderer.kt
│       ├── AudioPushPlayer.kt
│       ├── VideoPushPlayer.kt
│       └── MultiVideoGrid.kt
├── samples/                    # Sample projects
│   ├── minimal-android/
│   ├── minimal-ios/
│   ├── minimal-desktop/
│   └── robot-control/          # Complete example
├── docs/
│   ├── ROADMAP.md              # This document
│   ├── ARCHITECTURE.md
│   ├── CONTRIBUTING.md
│   └── api/                    # Dokka generated
└── .github/
    ├── ISSUE_TEMPLATE/
    ├── PULL_REQUEST_TEMPLATE.md
    └── workflows/
```

---

## 5. Milestones

| Version | Estimated Time | Key Content |
|---------|---------------|-------------|
| **v1.5.0** | +3 weeks | VideoPushPlayer, MultiStreamManager, Low-latency preset |
| **v1.6.0** | +5 weeks | Signaling abstract interface, Structured errors, Logger |
| **v2.0.0** | +7 weeks | Modularization, Stable API, Full documentation, Sample projects |
| **v2.1.0** | Future | Screen sharing |
| **v2.2.0** | Future | Local recording |

---

## 6. Open Questions

- [ ] When to modularize? (v2.0 or earlier)
- [ ] Is Simulcast support needed?
- [ ] Priority for Web platforms (JS/WasmJS)? (JS is currently Stub, WasmJS incomplete)
- [ ] Should a backend signaling server reference implementation be provided?
- [ ] Keep MIT license?
- [x] Multiple VideoRenderer instances? → **Already supported**, each instance has independent PeerConnection with no shared state conflicts

---

## 7. References
- [WebRTC Standard](https://www.w3.org/TR/webrtc/)
- [WHEP Draft](https://www.ietf.org/archive/id/draft-murillo-whep-01.html)
- [WHIP Draft](https://www.ietf.org/archive/id/draft-ietf-wish-whip-01.html)
