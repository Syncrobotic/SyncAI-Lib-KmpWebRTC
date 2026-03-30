# SyncAI-Lib-KmpWebRTC

[![Release](https://img.shields.io/github/v/release/Syncrobotic/SyncAI-Lib-KmpWebRTC?style=flat-square)](https://github.com/Syncrobotic/SyncAI-Lib-KmpWebRTC/releases)
[![CI](https://img.shields.io/github/actions/workflow/status/Syncrobotic/SyncAI-Lib-KmpWebRTC/ci.yml?branch=main&style=flat-square&label=CI)](https://github.com/Syncrobotic/SyncAI-Lib-KmpWebRTC/actions/workflows/ci.yml)
[![Publish](https://img.shields.io/github/actions/workflow/status/Syncrobotic/SyncAI-Lib-KmpWebRTC/publish.yml?style=flat-square&label=Publish)](https://github.com/Syncrobotic/SyncAI-Lib-KmpWebRTC/actions/workflows/publish.yml)
[![License](https://img.shields.io/github/license/Syncrobotic/SyncAI-Lib-KmpWebRTC?style=flat-square)](LICENSE)

It's a Kotlin Multiplatform WebRTC client library for video/audio streaming, audio sending, image receive and bi-directional text messaging. 

## 📋 Table of Contents

- [Supported Platforms](#supported-platforms)
- [Features](#features)
- [Installation](#installation)
- [Quick Start](#quick-start)
- [Platform Guides](#platform-guides)
- [API Reference](#api-reference)
- [Architecture Notes](#architecture-notes)
- [Roadmap](#roadmap)
- [CI/CD Workflows](#cicd-workflows)
- [Contributing](#contributing)

---

## Supported Platforms

| Platform | Status | WebRTC Implementation |
|----------|--------|----------------------|
| Android | ✅ | [io.github.webrtc-sdk:android](https://github.com/nicokosi/webrtc-android) |
| iOS (Physical Device) | ✅ | [GoogleWebRTC CocoaPod](https://cocoapods.org/pods/GoogleWebRTC) |
| iOS Simulator | ❌ | Not supported — GoogleWebRTC does not provide simulator binaries |
| JVM/Desktop | ✅ | [webrtc-java](https://github.com/nicokosi/webrtc-java) |
| JavaScript (Browser) | ✅ | Native RTCPeerConnection |
| WebAssembly (WasmJS) | ✅ | Native RTCPeerConnection |

### Video Codec Support

| Platform | Video Codecs | Hardware Acceleration | Implementation |
|----------|--------------|----------------------|----------------|
| Android | H.264, VP8, VP9 | ✅ MediaCodec + EglBase | `DefaultVideoEncoderFactory` / `DefaultVideoDecoderFactory` |
| iOS | H.264, VP8 | ✅ VideoToolbox | `RTCDefaultVideoEncoderFactory` / `RTCDefaultVideoDecoderFactory` |
| JVM/Desktop | H.264, VP8 | Platform dependent | `PeerConnectionFactory` (webrtc-java) |
| JavaScript | H.264, VP8, VP9, AV1* | ✅ Browser native | `RTCPeerConnection` |
| WasmJS | H.264, VP8, VP9, AV1* | ✅ Browser native | `RTCPeerConnection` |

*AV1 support depends on browser version

### Audio Codec Support

| Platform | Audio Codecs | Notes |
|----------|--------------|-------|
| All Platforms | **Opus** (primary) | Default codec for WebRTC, 48kHz stereo |
| All Platforms | G.711 (μ-law/A-law) | Fallback for legacy systems |
| All Platforms | PCMU/PCMA | Supported but rarely used |

> **Note**: H.265 (HEVC) is **not supported** by WebRTC standard. If you need H.265, you would need to implement custom VideoEncoderFactory/VideoDecoderFactory.

---

## Features

- 🎥 **WebRTC Streaming** - Receive video and audio via WHEP protocol
- 🎤 **Audio Sending** - Send microphone audio via WHIP protocol
- � **AudioPushPlayer** - Cross-platform Composable for audio push (Android/iOS/JVM/Web)
- 🔄 **BidirectionalPlayer** - Combined video receive + audio send in one component
- �💬 **Data Channel** - Bi-directional text/JSON messaging
- 📦 **Binary Data** - Send and receive images, files, or any binary data
- 🔄 **Signaling Support** - Built-in WHEP/WHIP HTTP and WebSocket signaling
- ♻️ **Auto Retry** - Configurable exponential backoff retry mechanism
- 📊 **Connection Stats** - Get RTT, packet loss, bitrate and more
- 📺 **VideoRenderer** - Cross-platform Composable for video display (Android/iOS/JVM)

---

## Installation

### Option 1: GitHub Packages (Recommended)

#### 1. Configure Repository

In your project root `settings.gradle.kts`:

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
                username = localProps.getProperty("gpr.user")
                    ?: System.getenv("GITHUB_ACTOR")
                password = localProps.getProperty("gpr.key")
                    ?: System.getenv("GITHUB_TOKEN")
            }
        }
    }
}
```

#### 2. Configure GitHub Credentials

Add to your project's `local.properties` (this file is already in `.gitignore` by default):

```properties
gpr.user=YOUR_GITHUB_USERNAME
gpr.key=YOUR_GITHUB_PERSONAL_ACCESS_TOKEN
```

> **Note**: Generate a GitHub Personal Access Token (PAT) with `read:packages` scope at [GitHub Settings > Developer settings > Personal access tokens](https://github.com/settings/tokens).

> ⚠️ **GitHub Packages requires authentication even for public packages.** All users must configure a GitHub Personal Access Token with `read:packages` scope. This is a GitHub limitation, not a library restriction. We plan to publish to Maven Central in the future to remove this requirement.

#### 3. Add Dependency

In `gradle/libs.versions.toml`:

```toml
[versions]
kmp-webrtc = "1.0.0"

[libraries]
kmp-webrtc = { module = "com.syncrobotic:syncai-lib-kmpwebrtc", version.ref = "kmp-webrtc" }
```

In your module's `build.gradle.kts`:

```kotlin
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.kmp.webrtc)
        }
    }
}
```

### Option 2: Local Build

This module can be built as a **standalone project** — no parent project required.

```bash
# Clone the project
git clone https://github.com/Syncrobotic/SyncAI-Lib-KmpWebRTC.git
cd SyncAI-Lib-KmpWebRTC

# Publish all platform artifacts to local Maven
./gradlew publishToMavenLocal
```

Then add `mavenLocal()` repository to your project's `settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositories {
        mavenLocal()
        google()
        mavenCentral()
    }
}
```

#### Build Specific Targets

```bash
# JVM/Desktop only
./gradlew jvmJar

# Android AAR only
./gradlew bundleReleaseAar

# JS library
./gradlew jsJar

# WasmJS library
./gradlew wasmJsJar

# iOS framework (requires macOS, physical device only)
./gradlew linkPodReleaseFrameworkIosArm64

# Run common + JVM tests
./gradlew jvmTest
```

#### Verify the Build

```bash
# Check all tasks resolve correctly
./gradlew tasks --group=build

# Compile all JVM sources (fast sanity check)
./gradlew jvmMainClasses
```

---

## Quick Start

### Basic Streaming (WHEP)

```kotlin
import com.syncrobotic.webrtc.*
import com.syncrobotic.webrtc.config.*
import com.syncrobotic.webrtc.signaling.*

// 1. Create WebRTC client
val client = WebRTCClient()

// 2. Set up listener
val listener = object : WebRTCListener {
    override fun onConnectionStateChanged(state: WebRTCState) {
        println("Connection state: $state")
        if (state == WebRTCState.CONNECTED) {
            println("✅ Stream connected!")
        }
    }
    
    override fun onRemoteStreamAdded() {
        println("Remote stream added")
    }
    
    override fun onTracksChanged(videoCount: Int, audioCount: Int, tracks: List<TrackInfo>) {
        println("Tracks: $videoCount video, $audioCount audio")
    }
    
    override fun onIceCandidate(candidate: String, sdpMid: String?, sdpMLineIndex: Int) {
        // Usually not needed in WHEP mode
    }
    
    override fun onIceGatheringStateChanged(state: IceGatheringState) {}
    override fun onIceConnectionStateChanged(state: IceConnectionState) {}
    override fun onIceGatheringComplete() {}
    override fun onRemoteStreamRemoved() {}
    override fun onVideoFrame(frame: VideoFrame) {}
    
    override fun onError(error: Throwable) {
        println("❌ Error: ${error.message}")
    }
}

// 3. Initialize
val config = WebRTCConfig.DEFAULT
client.initialize(config, listener)

// 4. Connect via WHEP
suspend fun connect() {
    val whepUrl = "https://your-streaming-server/stream/whep"
    val signaling = WhepSignaling(whepUrl)
    
    // Create SDP offer
    val offer = client.createOffer(receiveVideo = true, receiveAudio = true)
    
    // Send to WHEP endpoint and get answer
    val answer = signaling.sendOffer(offer)
    
    // Set remote description
    client.setRemoteAnswer(answer)
}

// 5. Disconnect
fun disconnect() {
    client.close()
}
```

### Data Channel Messaging

```kotlin
import com.syncrobotic.webrtc.datachannel.*

// Create Data Channel
val channel = client.createDataChannel(
    DataChannelConfig.reliable("messages")
)

// Set up listener
channel?.setListener(object : DataChannelListener {
    override fun onStateChanged(state: DataChannelState) {
        when (state) {
            DataChannelState.OPEN -> println("📬 Data Channel opened")
            DataChannelState.CLOSED -> println("📪 Data Channel closed")
            else -> {}
        }
    }
    
    override fun onMessage(message: String) {
        println("Received message: $message")
        
        // Parse JSON if needed
        // val json = Json.parseToJsonElement(message)
    }
    
    override fun onError(error: Throwable) {
        println("Data Channel error: ${error.message}")
    }
})

// Send messages
if (channel?.state == DataChannelState.OPEN) {
    // Plain text
    channel.send("Hello!")
    
    // JSON
    channel.send("""{"type":"chat","content":"Hello"}""")
}

// Close
channel?.close()
```

### Cross-Platform Video Display (Recommended)

Use the `VideoRenderer` composable for cross-platform video display:

```kotlin
import com.syncrobotic.webrtc.ui.*
import com.syncrobotic.webrtc.config.*

@Composable
fun VideoPlayerScreen() {
    val config = StreamConfig(
        url = "https://your-streaming-server/stream/whep",
        protocol = StreamProtocol.WEBRTC,
        signalingType = SignalingType.WHEP_HTTP,
        autoPlay = true
    )
    
    VideoRenderer(
        config = config,
        modifier = Modifier.fillMaxSize(),
        onStateChange = { state ->
            when (state) {
                is PlayerState.Loading -> println("Loading...")
                is PlayerState.Playing -> println("Playing!")
                is PlayerState.Error -> println("Error: ${state.message}")
                is PlayerState.Reconnecting -> println("Reconnecting ${state.attempt}/${state.maxAttempts}")
                else -> {}
            }
        },
        onEvent = { event ->
            when (event) {
                is PlayerEvent.FirstFrameRendered -> println("First frame!")
                is PlayerEvent.StreamInfoReceived -> {
                    println("Resolution: ${event.streamInfo.width}x${event.streamInfo.height}")
                    println("FPS: ${event.streamInfo.fps}")
                }
            }
        }
    )
}
```

> ✅ This works on **Android**, **iOS**, and **JVM** without any platform-specific code!

---

### Audio Push (WHIP) - Sending Microphone Audio

Use `AudioPushPlayer` to capture microphone audio and send it to a WHIP endpoint:

```kotlin
import com.syncrobotic.webrtc.audio.*

@Composable
fun AudioStreamingScreen() {
    val config = AudioPushConfig.create(
        host = "192.168.1.100",
        streamPath = "mobile-audio"
    )
    
    val controller = AudioPushPlayer(
        config = config,
        autoStart = false,
        onStateChange = { state ->
            when (state) {
                is AudioPushState.Streaming -> println("Audio streaming!")
                is AudioPushState.Muted -> println("Muted")
                is AudioPushState.Error -> println("Error: ${state.message}")
                is AudioPushState.Reconnecting -> println("Reconnecting ${state.attempt}/${state.maxAttempts}")
                else -> {}
            }
        }
    )
    
    Column {
        Button(onClick = { controller.start() }) {
            Text("Start Audio")
        }
        Button(onClick = { controller.stop() }) {
            Text("Stop Audio")
        }
        Button(onClick = { controller.toggleMute() }) {
            Text(if (controller.isMuted) "Unmute" else "Mute")
        }
    }
}
```

---

### Bidirectional Communication (Video Receive + Audio Send)

For applications that need to both receive video and send audio (like robot control apps), use `BidirectionalPlayer`:

```kotlin
import com.syncrobotic.webrtc.ui.*
import com.syncrobotic.webrtc.config.*

@Composable
fun RobotControlScreen() {
    val config = BidirectionalConfig.create(
        host = "192.168.1.100",
        receiveStreamPath = "robot-video",     // Watch video from this stream
        sendStreamPath = "mobile-audio",       // Send audio to this stream
        autoStartVideo = true,
        autoStartAudio = false
    )
    
    val controller = BidirectionalPlayer(
        config = config,
        modifier = Modifier.fillMaxSize(),
        onStateChange = { state ->
            println("Video: ${state.videoState}, Audio: ${state.audioState}")
            if (state.isFullyConnected) {
                println("Both video and audio are active!")
            }
        }
    )
    
    // Push-to-talk button
    Button(
        onClick = { controller.startAudio() },
        modifier = Modifier.pointerInput(Unit) {
            detectTapGestures(
                onPress = {
                    controller.startAudio()
                    tryAwaitRelease()
                    controller.stopAudio()
                }
            )
        }
    ) {
        Text("Push to Talk")
    }
}
```

---

### Sending and Receiving Images

```kotlin
import com.syncrobotic.webrtc.datachannel.*

// Create Data Channel for binary data
val imageChannel = client.createDataChannel(
    DataChannelConfig.reliable("images")
)

// Set up listener for receiving images
imageChannel?.setListener(object : DataChannelListener {
    override fun onStateChanged(state: DataChannelState) {
        println("Image channel state: $state")
    }
    
    override fun onMessage(message: String) {
        // Text messages (e.g., image metadata as JSON)
        println("Received metadata: $message")
    }
    
    override fun onBinaryMessage(data: ByteArray) {
        // Binary data (images, files, etc.)
        println("Received image: ${data.size} bytes")
        
        // Convert to platform-specific image
        // Android: BitmapFactory.decodeByteArray(data, 0, data.size)
        // iOS: UIImage(data: NSData(bytes: data, length: data.size))
    }
    
    override fun onError(error: Throwable) {
        println("Error: ${error.message}")
    }
})

// Send an image
fun sendImage(imageBytes: ByteArray) {
    if (imageChannel?.state == DataChannelState.OPEN) {
        // Optionally send metadata first
        imageChannel.send("""{"type":"image","size":${imageBytes.size},"format":"jpeg"}""")
        
        // Send binary image data
        imageChannel.sendBinary(imageBytes)
    }
}
```

---

## Platform Guides

### Android

#### Additional Setup

Add permissions to `AndroidManifest.xml`:

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <!-- Required for WebRTC -->
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
    <uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
    
    <!-- Required for microphone (AudioPushPlayer / BidirectionalPlayer) -->
    <uses-permission android:name="android.permission.RECORD_AUDIO" />
    <uses-permission android:name="android.permission.MODIFY_AUDIO_SETTINGS" />
    
    <!-- Optional: Only if using camera -->
    <uses-permission android:name="android.permission.CAMERA" />

    <application
        ...
        android:usesCleartextTraffic="true">  <!-- Required for HTTP (non-HTTPS) endpoints -->
        ...
    </application>
</manifest>
```

> **Note**: `usesCleartextTraffic="true"` is required if your WHEP/WHIP server uses HTTP instead of HTTPS. For production, use HTTPS and remove this attribute.

#### Runtime Permissions

For microphone and camera features, you need to request runtime permissions using Android's standard permission APIs (e.g., `ActivityCompat.requestPermissions` or `rememberLauncherForActivityResult`). This library does **not** include a built-in permission handler.

> **Note**: When using `VideoRenderer`, `AudioPushPlayer`, or `BidirectionalPlayer` composables, WebRTC is automatically initialized. No manual initialization is needed.

#### Display Video in Compose UI

**Option 1: Cross-Platform VideoRenderer (Recommended)**

```kotlin
import com.syncrobotic.webrtc.ui.*
import com.syncrobotic.webrtc.config.*

@Composable
fun VideoPlayerScreen() {
    VideoRenderer(
        config = StreamConfig(
            url = "https://your-server/stream/whep",
            protocol = StreamProtocol.WEBRTC,
            signalingType = SignalingType.WHEP_HTTP
        ),
        modifier = Modifier.fillMaxSize(),
        onStateChange = { /* handle state */ },
        onEvent = { /* handle events */ }
    )
}
```

**Option 2: Platform-Specific (Android Only)**

```kotlin
// ⚠️ This code only works on Android!
@Composable
fun VideoPlayerScreen(client: WebRTCClient) {
    val videoSink = client.getVideoSink() as? SurfaceViewRenderer
    
    AndroidView(
        factory = { context ->
            videoSink ?: SurfaceViewRenderer(context)
        },
        modifier = Modifier.fillMaxSize()
    )
}
```

#### Audio Control

```kotlin
// Enable/disable microphone
client.setAudioEnabled(true)

// Toggle speakerphone
client.setSpeakerphoneEnabled(true)
```

---

### iOS

#### Info.plist Configuration

Add the following keys to your `Info.plist`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <!-- Allow HTTP connections (required for local network streaming) -->
    <key>NSAppTransportSecurity</key>
    <dict>
        <key>NSAllowsArbitraryLoads</key>
        <true/>
        <key>NSAllowsLocalNetworking</key>
        <true/>
    </dict>
    
    <!-- Local network access (Bonjour/mDNS) -->
    <key>NSLocalNetworkUsageDescription</key>
    <string>This app needs local network access to connect to the streaming server.</string>
    
    <!-- Microphone permission (required for AudioPushPlayer / BidirectionalPlayer) -->
    <key>NSMicrophoneUsageDescription</key>
    <string>This app needs microphone access to send audio during WebRTC streaming.</string>
    
    <!-- Camera permission (optional, only if using camera) -->
    <key>NSCameraUsageDescription</key>
    <string>This app needs camera access to send video during WebRTC streaming.</string>
</dict>
</plist>
```

| Key | Required For | Description |
|-----|--------------|-------------|
| `NSAppTransportSecurity` | HTTP endpoints | Allow non-HTTPS connections |
| `NSAllowsLocalNetworking` | Local network | Allow LAN connections |
| `NSLocalNetworkUsageDescription` | Local network | User-facing description |
| `NSMicrophoneUsageDescription` | AudioPushPlayer | Microphone access for WHIP |
| `NSCameraUsageDescription` | Camera (optional) | Camera access if needed |

> **Note**: For production, use HTTPS and remove `NSAllowsArbitraryLoads`. Keep `NSAllowsLocalNetworking` if you need LAN streaming.

#### CocoaPods Setup

In your iOS project's `Podfile`:

```ruby
platform :ios, '15.0'

target 'YourApp' do
  use_frameworks!
  
  # GoogleWebRTC
  pod 'GoogleWebRTC', '1.1.31999'
  
  # Kotlin Multiplatform shared module
  pod 'shared', :path => '../shared'
end
```

Run:

```bash
cd iosApp
pod install
```

#### Display Video in Compose UI (iOS)

**Option 1: Cross-Platform VideoRenderer in Compose (Recommended)**

```kotlin
// Works in Kotlin/iOS Compose code
import com.syncrobotic.webrtc.ui.*
import com.syncrobotic.webrtc.config.*

@Composable
fun VideoPlayerScreen() {
    VideoRenderer(
        config = StreamConfig(
            url = "https://your-server/stream/whep",
            protocol = StreamProtocol.WEBRTC,
            signalingType = SignalingType.WHEP_HTTP
        ),
        modifier = Modifier.fillMaxSize(),
        onStateChange = { /* handle state */ },
        onEvent = { /* handle events */ }
    )
}
```

**Option 2: Native SwiftUI Integration**

```swift
import SwiftUI
import shared  // Kotlin shared module

struct ContentView: View {
    @StateObject private var viewModel = WebRTCViewModel()
    
    var body: some View {
        VStack {
            // Video display
            if let videoView = viewModel.videoView {
                VideoPlayerView(videoView: videoView)
                    .frame(maxWidth: .infinity, maxHeight: 300)
            }
            
            // Control buttons
            HStack {
                Button("Connect") {
                    viewModel.connect()
                }
                
                Button("Disconnect") {
                    viewModel.disconnect()
                }
            }
        }
    }
}

class WebRTCViewModel: ObservableObject {
    private var client: WebRTCClient?
    @Published var videoView: RTCMTLVideoView?
    
    func connect() {
        client = WebRTCClient()
        // ... initialize and connect
    }
    
    func disconnect() {
        client?.close()
        client = nil
    }
}
```

> ⚠️ **Note:** GoogleWebRTC does **not** support iOS Simulator (neither x86_64 nor arm64). You must use a **physical iOS device** for testing.

---

### JVM/Desktop

#### Gradle Setup

```kotlin
kotlin {
    jvm()
    
    sourceSets {
        jvmMain.dependencies {
            implementation(libs.kmp.webrtc)
            
            // webrtc-java native libraries
            implementation("dev.onvoid.webrtc:webrtc-java:0.14.0")
        }
    }
}
```

#### Compose Desktop Usage

**Option 1: Cross-Platform VideoRenderer (Recommended)**

```kotlin
import com.syncrobotic.webrtc.ui.*
import com.syncrobotic.webrtc.config.*

fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "WebRTC Demo") {
        VideoRenderer(
            config = StreamConfig(
                url = "https://your-server/stream/whep",
                protocol = StreamProtocol.WEBRTC,
                signalingType = SignalingType.WHEP_HTTP
            ),
            modifier = Modifier.fillMaxSize(),
            onStateChange = { state -> println("State: $state") },
            onEvent = { event -> println("Event: $event") }
        )
    }
}
```

**Option 2: Manual WebRTC Client Control**

```kotlin
import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.runtime.*
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "WebRTC Demo") {
        App()
    }
}

@Composable
fun App() {
    var connectionState by remember { mutableStateOf("Disconnected") }
    val client = remember { WebRTCClient() }
    
    LaunchedEffect(Unit) {
        client.initialize(
            WebRTCConfig.DEFAULT,
            object : WebRTCListener {
                override fun onConnectionStateChanged(state: WebRTCState) {
                    connectionState = state.name
                }
            }
        )
    }
    
    Column {
        Text("Status: $connectionState")
        
        Button(onClick = { /* Connection logic */ }) {
            Text("Connect")
        }
    }
    
    DisposableEffect(Unit) {
        onDispose {
            client.close()
        }
    }
}
```

---

### JavaScript (Browser)

#### Kotlin/JS Setup

```kotlin
kotlin {
    js(IR) {
        browser()
    }
    
    sourceSets {
        jsMain.dependencies {
            implementation(libs.kmp.webrtc)
        }
    }
}
```

#### Web Usage

```kotlin
fun main() {
    val client = WebRTCClient()
    
    // Check browser support
    if (!WebRTCClient.isSupported()) {
        console.log("Browser does not support WebRTC")
        return
    }
    
    client.initialize(
        WebRTCConfig.DEFAULT,
        object : WebRTCListener {
            override fun onConnectionStateChanged(state: WebRTCState) {
                document.getElementById("status")?.textContent = state.name
            }
            
            override fun onRemoteStreamAdded() {
                // Get MediaStream and attach to <video> element
                val stream = client.getVideoSink() as? MediaStream
                val videoElement = document.getElementById("video") as? HTMLVideoElement
                videoElement?.srcObject = stream
            }
        }
    )
}
```

HTML:

```html
<!DOCTYPE html>
<html>
<head>
    <title>WebRTC Demo</title>
</head>
<body>
    <video id="video" autoplay playsinline></video>
    <p>Status: <span id="status">Disconnected</span></p>
    <script src="your-app.js"></script>
</body>
</html>
```

---

### WebAssembly (WasmJS)

#### Kotlin/WasmJS Setup

```kotlin
kotlin {
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
    }
    
    sourceSets {
        wasmJsMain.dependencies {
            implementation(libs.kmp.webrtc)
        }
    }
}
```

Usage is the same as JavaScript, but compiled to WebAssembly for better performance.

---

## API Reference

### WebRTCClient

| Method | Description |
|--------|-------------|
| `initialize(config, listener)` | Initialize the WebRTC client |
| `createOffer(receiveVideo, receiveAudio)` | Create SDP offer for receiving |
| `createSendOffer(sendVideo, sendAudio)` | Create SDP offer for sending |
| `setRemoteAnswer(sdpAnswer)` | Set remote SDP answer |
| `addIceCandidate(candidate, sdpMid, sdpMLineIndex)` | Add ICE candidate |
| `createDataChannel(config)` | Create a Data Channel |
| `getVideoSink()` | Get video output (platform-specific type) |
| `setAudioEnabled(enabled)` | Enable/disable local audio |
| `setSpeakerphoneEnabled(enabled)` | Toggle speakerphone output |
| `getStats()` | Get connection statistics |
| `close()` | Close connection and release resources |

### VideoRenderer (Cross-Platform UI)

```kotlin
@Composable
fun VideoRenderer(
    config: StreamConfig,
    modifier: Modifier = Modifier,
    onStateChange: (PlayerState) -> Unit = {},
    onEvent: (PlayerEvent) -> Unit = {}
)
```

| Parameter | Description |
|-----------|-------------|
| `config` | Stream configuration (URL, protocol, signaling type) |
| `modifier` | Compose Modifier for layout |
| `onStateChange` | Callback for player state changes |
| `onEvent` | Callback for player events (first frame, stream info) |

#### StreamConfig

| Property | Type | Description |
|----------|------|-------------|
| `url` | `String` | Stream URL (WHEP endpoint) |
| `protocol` | `StreamProtocol` | `WEBRTC`, `HLS`, or `RTSP` |
| `signalingType` | `SignalingType` | `WHEP_HTTP` or `WEBSOCKET` |
| `autoPlay` | `Boolean` | Auto-start playback (default: true) |
| `retryConfig` | `RetryConfig` | Retry settings for reconnection |

#### PlayerState

| State | Description |
|-------|-------------|
| `Idle` | Initial state |
| `Connecting` | Establishing WebRTC connection |
| `Loading` | Loading stream data |
| `Playing` | Stream is playing |
| `Paused` | Stream is paused |
| `Buffering` | Buffering data (temporary interruption) |
| `Reconnecting` | Auto-reconnecting after disconnection (includes attempt count, max attempts, reason) |
| `Error` | Error occurred (includes error message) |
| `Stopped` | Playback stopped |

#### Built-in Connection Status Overlay

VideoRenderer includes a **built-in connection status overlay** that automatically displays:

| State | Overlay Display | Color |
|-------|-----------------|-------|
| `Connecting` | Spinner + "Connecting..." | White |
| `Reconnecting` | Spinner + "Reconnecting (1/5)" + reason | Yellow |
| `Error` | "Error" + error message | Red |
| `Buffering` | Spinner + "Buffering..." | White |
| `Loading` | Spinner + "Loading..." | Gray |

The overlay is automatically shown when appropriate and hidden during `Playing` state. This provides visual feedback to users without requiring additional UI code:

```kotlin
// No extra code needed - overlay is built-in!
VideoRenderer(
    config = config,
    modifier = Modifier.fillMaxSize(),
    onStateChange = { state ->
        // State changes are still reported for logging/analytics
        println("State: $state")
    }
)
```

**Overlay Behavior:**
- Shows semi-transparent dark background (60% opacity) over video
- Displays appropriate icon and message based on current state
- Automatically hides when video frames are playing
- Shows reconnection attempt count and reason during `Reconnecting` state

#### Performance Optimizations

VideoRenderer includes built-in optimizations to reduce unnecessary UI recomposition:

1. **StreamInfo Throttling**: `StreamInfoReceived` events are throttled to a maximum of once per second, unless resolution changes. This prevents excessive recomposition when frame rate varies.

2. **Connection State Guards**: Reconnection logic includes guards to prevent race conditions when connection state changes rapidly (e.g., `CONNECTED` state clears pending reconnect triggers).

#### PlayerEvent

| Event | Description |
|-------|-------------|
| `FirstFrameRendered` | First video frame displayed |
| `StreamInfoReceived` | Stream metadata received (resolution, FPS, codec) |

#### Platform Support

| Platform | WebRTC | HLS | RTSP |
|----------|--------|-----|------|
| Android | ✅ | ❌ | ❌ |
| iOS | ✅ | ✅ (AVPlayer) | ❌ |
| JVM | ✅ | ❌ | ❌ |

> **Why HLS/RTSP are not supported on Android/JVM?**
>
> While adding ExoPlayer (Android) or FFmpeg (JVM) seems straightforward, there are several architectural reasons why this library intentionally does not bundle these dependencies:
>
> **1. Kotlin Multiplatform Architecture Constraints**
> - **ExoPlayer is Android-only**: ExoPlayer (Media3) is tightly coupled to Android SDK APIs (`MediaCodec`, `AudioTrack`, `Surface`). It cannot be used on iOS, JVM Desktop, or Web platforms. Adding it would break the unified `expect`/`actual` architecture.
> - **FFmpeg requires native compilation per platform**: FFmpeg is a C library that needs separate native binaries (~15-30MB each) for every target: `arm64-v8a`, `armeabi-v7a`, `x86_64` (Android), `darwin-aarch64`, `darwin-x86_64` (macOS), `linux-x64`, `win-x64` (Desktop). This dramatically increases distribution size and CI/CD complexity.
>
> **2. No Unified Cross-Platform API**
> - Each platform has completely different HLS/RTSP implementations:
>   - Android: ExoPlayer/Media3
>   - iOS: AVPlayer (HLS only, no RTSP)
>   - JVM: JavaCV + FFmpeg or GStreamer
>   - Web: hls.js (HLS only, no RTSP possible due to browser sandbox)
> - Creating a unified Composable API across 5 platforms with vastly different underlying APIs would require maintaining essentially 5 separate media player codebases.
>
> **3. Scope & Focus**
> - This library is purpose-built for **WebRTC streaming** (WHEP/WHIP protocol). WebRTC provides ultra-low latency (~100-300ms) which is critical for real-time AI vision applications.
> - HLS adds 2-30 seconds of latency; RTSP typically adds 0.5-2 seconds. For use cases where this latency is acceptable, consider using platform-native players:
>   - Android: Use `Media3` + `ExoPlayer` directly
>   - iOS: Use `AVPlayer` directly  
>   - JVM: Use `vlcj` or `javacv`
>
> **4. Binary Size Considerations**
> - ExoPlayer core adds ~2-3MB to APK size
> - FFmpeg full build adds ~15-30MB per platform
> - This library aims to stay lightweight (<1MB) for WebRTC-focused use cases
>
> **Recommendation**: If you need HLS/RTSP alongside WebRTC, use this library for WebRTC and integrate platform-specific players separately for HLS/RTSP streams.

### AudioPushPlayer (Cross-Platform Audio Sending)

```kotlin
@Composable
fun AudioPushPlayer(
    config: AudioPushConfig,
    autoStart: Boolean = false,
    onStateChange: (AudioPushState) -> Unit = {}
): AudioPushController
```

| Parameter | Description |
|-----------|-------------|
| `config` | Audio push configuration (WHIP URL, WebRTC settings) |
| `autoStart` | Whether to start streaming automatically |
| `onStateChange` | Callback for connection state changes |

#### AudioPushConfig

| Property | Type | Description |
|----------|------|-------------|
| `whipUrl` | `String` | WHIP endpoint URL |
| `webrtcConfig` | `WebRTCConfig` | WebRTC configuration (default: `WebRTCConfig.SENDER`) |
| `enableEchoCancellation` | `Boolean` | Enable AEC (default: true) |
| `enableNoiseSuppression` | `Boolean` | Enable NS (default: true) |
| `enableAutoGainControl` | `Boolean` | Enable AGC (default: true) |
| `retryConfig` | `AudioRetryConfig` | Retry settings for reconnection |

#### AudioPushState

| State | Description |
|-------|-------------|
| `Idle` | Initial state |
| `Connecting` | Connecting to WHIP endpoint |
| `Streaming` | Audio is being sent |
| `Muted` | Connected but muted |
| `Reconnecting` | Auto-reconnecting (with attempt count) |
| `Error` | Error occurred (with message and retryable flag) |
| `Disconnected` | Disconnected |

#### AudioPushController

| Method/Property | Description |
|-----------------|-------------|
| `state` | Current connection state |
| `isStreaming` | Whether audio is being sent |
| `isMuted` | Whether audio is muted |
| `stats` | WebRTC connection statistics |
| `start()` | Start audio streaming |
| `stop()` | Stop and disconnect |
| `setMuted(muted)` | Mute/unmute audio |
| `toggleMute()` | Toggle mute state |
| `refreshStats()` | Update connection statistics |

### BidirectionalPlayer (Combined Video + Audio)

```kotlin
@Composable
fun BidirectionalPlayer(
    config: BidirectionalConfig,
    modifier: Modifier = Modifier,
    onStateChange: (BidirectionalState) -> Unit = {},
    onVideoEvent: (PlayerEvent) -> Unit = {}
): BidirectionalController
```

#### BidirectionalConfig

```kotlin
// Simple factory method
BidirectionalConfig.create(
    host = "192.168.1.100",
    receiveStreamPath = "robot-video",    // Video input
    sendStreamPath = "mobile-audio",      // Audio output
    autoStartVideo = true,
    autoStartAudio = false
)

// Video-only
BidirectionalConfig.videoOnly(host = "...", streamPath = "video")

// Audio-only  
BidirectionalConfig.audioOnly(host = "...", streamPath = "audio")
```

#### BidirectionalController

| Method/Property | Description |
|-----------------|-------------|
| `state` | Combined `BidirectionalState` |
| `videoController` | VideoPlayerController (nullable) |
| `audioController` | AudioPushController (nullable) |
| `startVideo()` | Start video playback |
| `stopVideo()` | Stop video |
| `startAudio()` | Start audio sending |
| `stopAudio()` | Stop audio |
| `setAudioMuted(muted)` | Mute/unmute outgoing audio |
| `startAll()` | Start both video and audio |
| `stopAll()` | Stop both |

### WebRTCConfig

| Property | Default | Description |
|----------|---------|-------------|
| `iceServers` | Google STUN | List of ICE servers |
| `bundlePolicy` | `"max-bundle"` | Bundle policy |
| `rtcpMuxPolicy` | `"require"` | RTCP Mux policy |
| `iceTransportPolicy` | `"all"` | ICE transport policy |

### DataChannel

| Method/Property | Description |
|-----------------|-------------|
| `label` | Channel name |
| `id` | Channel ID |
| `state` | Current state (CONNECTING, OPEN, CLOSING, CLOSED) |
| `bufferedAmount` | Pending data size (bytes) |
| `send(message)` | Send text message |
| `sendBinary(data)` | Send binary data (images, files, etc.) |
| `setListener(listener)` | Set event listener |
| `close()` | Close the channel |

### DataChannelListener

| Callback | Description |
|----------|-------------|
| `onStateChanged(state)` | Called when channel state changes |
| `onMessage(message)` | Called when text message is received |
| `onBinaryMessage(data)` | Called when binary data is received |
| `onBufferedAmountChange(amount)` | Called when buffered amount changes |
| `onError(error)` | Called when an error occurs |

### DataChannelConfig

```kotlin
// Reliable transmission (default)
DataChannelConfig.reliable("messages")

// Unreliable transmission (low latency)
DataChannelConfig.unreliable("realtime", maxRetransmits = 0)

// Time-limited transmission
DataChannelConfig.maxLifetime("timed", maxPacketLifeTimeMs = 500)
```

### Signaling

| Class | Description |
|-------|-------------|
| `WhepSignaling` | WHEP HTTP signaling (streaming compatible) |
| `WhipSignaling` | WHIP HTTP signaling (audio sending) |
| `WebSocketSignaling` | WebSocket signaling (custom backend) |

---

## Publishing to GitHub Packages

### Configure Authentication

In `local.properties` (not committed to git):

```properties
gpr.user=YOUR_GITHUB_USERNAME
gpr.key=YOUR_GITHUB_TOKEN (needs write:packages permission)
```

Or use environment variables:

```bash
export GITHUB_ACTOR=YOUR_GITHUB_USERNAME
export GITHUB_TOKEN=YOUR_GITHUB_TOKEN
```

### Run Publish

```bash
./gradlew publish
```

### Published Artifacts

- `syncai-lib-kmpwebrtc-android` - Android AAR
- `syncai-lib-kmpwebrtc-jvm` - JVM JAR
- `syncai-lib-kmpwebrtc-js` - JavaScript
- `syncai-lib-kmpwebrtc-wasm-js` - WebAssembly
- `syncai-lib-kmpwebrtc-iosarm64` - iOS arm64 (physical device only)

> **Note**: iOS Simulator artifacts are not published because GoogleWebRTC does not support simulator.

---

## Dependencies

| Dependency | Version |
|------------|---------|
| Kotlin | 2.3.0 |
| Compose Multiplatform | 1.10.0 |
| Ktor | 3.0.3 |
| WebRTC Android SDK | 125.6422.05 |
| GoogleWebRTC (iOS) | 1.1.31999 |
| webrtc-java (JVM) | 0.14.0 |

---

## Additional Dependencies Required

When using this SDK, you must also include **Ktor HTTP Client** dependencies for each platform. The SDK's signaling implementations (WhepSignaling, WhipSignaling) require Ktor for HTTP requests.

### Required Ktor Dependencies

Add the following to your `gradle/libs.versions.toml`:

```toml
[versions]
ktor = "3.0.3"

[libraries]
ktor-client-core = { module = "io.ktor:ktor-client-core", version.ref = "ktor" }
ktor-client-okhttp = { module = "io.ktor:ktor-client-okhttp", version.ref = "ktor" }
ktor-client-darwin = { module = "io.ktor:ktor-client-darwin", version.ref = "ktor" }
ktor-client-js = { module = "io.ktor:ktor-client-js", version.ref = "ktor" }
```

In your module's `build.gradle.kts`:

```kotlin
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.kmp.webrtc)
            implementation(libs.ktor.client.core)
        }
        
        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
        }
        
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
        
        jvmMain.dependencies {
            implementation(libs.ktor.client.okhttp)
        }
        
        jsMain.dependencies {
            implementation(libs.ktor.client.js)
        }
        
        wasmJsMain.dependencies {
            implementation(libs.ktor.client.js)
        }
    }
}
```

### Platform-specific Engine Notes

| Platform | Ktor Engine | Notes |
|----------|-------------|-------|
| Android | OkHttp | Uses OkHttp under the hood |
| iOS | Darwin | Uses native URLSession |
| JVM | OkHttp | CIO engine is not recommended |
| JS/WASM | js | Uses native fetch API |

---

## Project Structure

```
SyncAI-Lib-KmpWebRTC/
├── build.gradle.kts           # Main build configuration
├── settings.gradle.kts        # Project settings
├── gradle.properties          # Gradle properties
├── gradle/
│   └── libs.versions.toml     # Version catalog
└── src/
    ├── commonMain/            # Shared code
    │   └── kotlin/
    │       └── com/syncrobotic/webrtc/
    │           ├── WebRTCClient.kt         # Main API (expect)
    │           ├── audio/                  # Audio push (WHIP)
    │           ├── config/                 # Configuration classes
    │           ├── signaling/              # Signaling implementations
    │           ├── datachannel/            # Data Channel API
    │           └── ui/                     # UI composables & state
    ├── androidMain/           # Android implementation
    ├── iosMain/               # iOS implementation
    ├── jvmMain/               # JVM/Desktop implementation
    ├── jsMain/                # JavaScript implementation
    └── wasmJsMain/            # WebAssembly implementation
```

---

## FAQ

### Q: iOS Simulator doesn't work?

A: GoogleWebRTC does **not** support iOS Simulator (neither x86_64 nor arm64). You must use a **physical iOS device** for development and testing. This is a limitation of the GoogleWebRTC CocoaPod.

### Q: Android can't connect?

A: Make sure you've added network permissions, and set `android:usesCleartextTraffic="true"` for non-HTTPS connections.

### Q: JVM version missing native libraries?

A: webrtc-java requires platform-specific native libraries. Ensure dependencies are correctly included via Gradle, or manually download the `.so/.dylib/.dll` files for your platform.

### Q: Data Channel messages have high latency?

A: Use `DataChannelConfig.unreliable()` configuration to reduce latency, trading reliability for real-time performance.

---

## License

MIT License

---

## Architecture Notes

### Coroutine Scope Design in AudioPushPlayer

`AudioPushPlayer` and its internal client classes (`AndroidAudioPushClient`, `IOSAudioPushClient`, `JvmAudioPushClient`, `WasmJsAudioPushClient`, `JsAudioPushClient`) follow a **two-scope architecture**:

| Scope | Owner | Purpose |
|-------|-------|---------|
| `rememberCoroutineScope()` | Compose composable | UI-level effects only (e.g. `LaunchedEffect`) |
| `clientScope` (owned by the client class) | Internal client class | All business logic: connection, retry, stats collection |

**Why this matters:** `rememberCoroutineScope()` is tied to the composable lifecycle — Compose cancels it when the composable leaves composition. If the client's coroutines run on `rememberCoroutineScope`, any cleanup launched in `DisposableEffect.onDispose` (e.g. `scope.launch { webrtcClient.close() }`) will be immediately cancelled because `scope` is already cancelled at that point.

**The fix (applied to all platforms):**
- Each client class owns a `private val clientScope = CoroutineScope(SupervisorJob() + Dispatchers.Main/Default)` that is **not** tied to any composable lifecycle.
- `release()` closes `webrtcClient` **synchronously** (guarantees microphone release), then fires a coroutine on `clientScope` for the WHIP DELETE network request, and finally calls `clientScope.cancel()`.
- `stop()` is safe to call from `DisposableEffect.onDispose` because `clientScope` is always active until `release()` is called.

```kotlin
// Correct pattern — client owns its scope
internal class AndroidAudioPushClient(...) : AudioPushController {
    private val clientScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    fun release() {
        connectionJob?.cancel()
        statsJob?.cancel()
        webrtcClient?.close()           // synchronous — mic released immediately
        val url = resourceUrl
        resourceUrl = null
        if (url != null) {
            clientScope.launch {        // fire-and-forget WHIP DELETE
                whipSignaling?.terminateSession(url)
                httpClient?.close()
            }
        } else {
            httpClient?.close()
        }
        clientScope.cancel()
    }
}
```

> **iOS note:** `release()` additionally calls `AVAudioSession.sharedInstance().setActive(false, null)` to notify the OS that the app is done with the microphone. Without this, the iOS microphone indicator remains active even after `webrtcClient.close()`.

---

### Coroutine Scope Design in WebRTCVideoPlayer

`WebRTCVideoPlayer` is a **pure composable** (no separate client class). It uses `rememberCoroutineScope()` for internal connection management. In `DisposableEffect.onDispose`, the following pattern is used:

```kotlin
DisposableEffect(Unit) {
    onDispose {
        webrtcClient.close()                          // synchronous
        val urlToTerminate = sessionResourceUrl       // capture before composable state is gone
        CoroutineScope(Dispatchers.IO).launch {       // fresh scope, not tied to composable
            whepSignaling.terminateSession(urlToTerminate)
            httpClient.close()
        }
    }
}
```

**Why not `scope.launch` in `onDispose`?** `rememberCoroutineScope()` is cancelled by Compose before `onDispose` runs, so any `scope.launch { ... }` inside `onDispose` will be cancelled immediately — the WHIP DELETE request never fires and the HTTP client is never closed.

**The fix:** `webrtcClient.close()` is called synchronously first. The WHIP termination network call uses a fresh `CoroutineScope(Dispatchers.IO)` that is independent of the composable lifecycle. References to mutable state (e.g. `sessionResourceUrl`, `webSocketSignaling`) are captured into local `val`s before the lambda executes.

---

### PeerConnectionFactory Management

WebRTC's native libraries have platform-specific constraints around `PeerConnectionFactory` lifecycle. This library uses `PeerConnectionFactoryManager` to handle these differences.

#### Problem: EglContext Binding on Android

On Android, `PeerConnectionFactory` binds video encoder/decoder to a specific `EglContext` at creation time. If multiple WebRTC connections share one factory:

```
DataChannel connects first → Factory created with DataChannel's EglContext (or no video support)
VideoRenderer connects later → Gets same Factory → Video decoder bound to wrong EglContext
Result: EGL_BAD_CONTEXT → SurfaceTextureHelper fails → SIGABRT crash
```

#### Solution: Platform-Specific Factory Strategy

| Platform | Strategy | Reason |
|----------|----------|--------|
| **Android** | **Separate Factory per connection** | EglContext is bound at Factory creation; sharing causes video decode failures |
| **iOS** | **Separate Factory per connection** | Consistency with Android; ARC handles memory |
| **JVM** | **Shared Factory** (reference counted) | No EglContext issues; webrtc-java has global state that crashes if multiple factories disposed |

#### API Reference

```kotlin
// Android - each connection gets its own factory
object PeerConnectionFactoryManager {
    fun ensureInitialized(context: Context)           // Call once before first use
    fun createForVideo(eglBase: EglBase): PeerConnectionFactory  // For video connections
    fun createForAudio(context: Context): PeerConnectionFactory  // For audio-only/DataChannel
    fun disposeFactory(factory: PeerConnectionFactory?)          // Dispose when connection closes
    fun getActiveConnections(): Int                   // For debugging
}

// iOS - each connection gets its own factory
object PeerConnectionFactoryManager {
    fun ensureInitialized()                           // Calls RTCInitializeSSL()
    fun createFactory(): RTCPeerConnectionFactory     // Creates new factory
    fun disposeFactory(factory: RTCPeerConnectionFactory?)  // Cleans up SSL when last connection closes
    fun getActiveConnections(): Int
}

// JVM - shared factory with reference counting
object PeerConnectionFactoryManager {
    fun createFactory(): PeerConnectionFactory        // Acquires reference to shared factory
    fun disposeFactory(factory: PeerConnectionFactory?)  // Releases reference; disposes when refCount=0
    fun getRefCount(): Int
}
```

#### Usage Example (Android)

```kotlin
// Video connection
val eglBase = EglBase.create()
PeerConnectionFactoryManager.ensureInitialized(context)
val factory = PeerConnectionFactoryManager.createForVideo(eglBase)
// ... use factory for peer connection ...

// Cleanup
peerConnection.close()
PeerConnectionFactoryManager.disposeFactory(factory)
eglBase.release()
```

#### Why Not Share Factory on Android?

```
┌─────────────────────────────────────────────────────────────────┐
│  WRONG: Shared Factory                                          │
├─────────────────────────────────────────────────────────────────┤
│  DataChannel.connect()                                          │
│    └─ acquireForAudio() → Factory created (no video decoder)    │
│                                                                 │
│  VideoRenderer.connect()                                        │
│    └─ acquireForVideo() → Returns SAME Factory                  │
│    └─ VideoDecoder tries to use wrong/missing EglContext        │
│    └─ EGL_BAD_CONTEXT → CRASH                                   │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│  CORRECT: Separate Factory                                      │
├─────────────────────────────────────────────────────────────────┤
│  DataChannel.connect()                                          │
│    └─ createForAudio() → Factory A (audio only)                 │
│                                                                 │
│  VideoRenderer.connect()                                        │
│    └─ createForVideo(eglBase) → Factory B (with video decoder)  │
│    └─ VideoDecoder uses correct EglContext                      │
│    └─ Video renders correctly ✓                                 │
└─────────────────────────────────────────────────────────────────┘
```

---

## Roadmap

### Testing Status

| Platform | Video Receive (WHEP) | Audio Send (WHIP) | Data Channel | Stats |
|----------|---------------------|-------------------|--------------|-------|
| Android | ✅ Verified | ✅ Verified | ⏳ Pending | ⏳ Pending |
| iOS | ✅ Verified | ✅ Verified | ⏳ Pending | ⏳ Pending |
| JVM/Desktop | ✅ Verified | ✅ Verified | ⏳ Pending | ⏳ Pending |
| JavaScript | ⏳ Pending | ⏳ Pending | ⏳ Pending | ⏳ Pending |
| WasmJS | ⏳ Pending | ⏳ Pending | ⏳ Pending | ⏳ Pending |

### Unit Tests (jvmTest)

160 unit tests covering all public API surfaces. Run with:

```bash
./gradlew jvmTest
```

| Category | Test File | Count | IDs |
|----------|-----------|-------|-----|
| **WHEP Signaling** | `WhepSignalingTest.kt` | 20 | WHEP-01..20 |
| **WHIP Signaling** | `WhipSignalingTest.kt` | 11 | WHIP-01..11 |
| RetryConfig | `RetryConfigTest.kt` | 9 | RC-01..09 |
| WebRTCConfig | `WebRTCConfigTest.kt` | 6 | WC-01..06 |
| IceServer | `IceServerTest.kt` | 3 | IS-01..03 |
| WebSocketSignalingConfig | `WebSocketSignalingConfigTest.kt` | 5 | WS-01..05 |
| ServerEndpoints | `ServerEndpointsTest.kt` | 2 | SE-01..02 |
| StreamConfig | `StreamConfigTest.kt` | 7 | SC-01..07 |
| BidirectionalConfig | `BidirectionalConfigTest.kt` | 7 | BC-01..07 |
| StreamRetryHandler | `StreamRetryHandlerTest.kt` | 14 | SRH-01..14 |
| AudioPushConfig | `AudioPushConfigTest.kt` | 7 | APC-01..07 |
| AudioPushState | `AudioPushStateTest.kt` | 10 | APS-01..10 |
| AudioRetryConfig | `AudioRetryConfigTest.kt` | 3 | ARC-01..03 |
| PlayerState | `PlayerStateTest.kt` | 11 | PS-01..11 |
| StreamInfo | `StreamInfoTest.kt` | 8 | SI-01..08 |
| BidirectionalState | `BidirectionalStateTest.kt` | 6 | BS-01..06 |
| DataChannelConfig | `DataChannelConfigTest.kt` | 10 | DC-01..08, DCS-01, DLA-01 |
| WebRTCStats | `WebRTCStatsTest.kt` | 8 | WS-01..08 |
| AudioData | `AudioDataTest.kt` | 4 | AD-01..04 |
| Enum Values | `EnumValuesTest.kt` | 9 | EV-01..09 |

> **Note — TEST_SPEC discrepancy:** `APS-03` in [docs/TEST_SPEC.md](docs/TEST_SPEC.md) specifies `Connecting.isActive = true`, but the actual `AudioPushState` code defines `isActive` as `Streaming || Muted` only. Tests follow the **actual code behavior** (`Connecting.isActive = false`).

> **Note — E2E tests:** The 40 E2E tests defined in TEST_SPEC (E2E-WHEP, E2E-WHIP, E2E-DataChannel) are **not yet implemented**. They require in-process `MockSignalingServer`, `ServerPeerConnection`, and `DataChannelEchoHandler` infrastructure. Test dependencies (ktor-server-core, ktor-server-netty) are already configured in `build.gradle.kts`.

### Pending Verification

| Priority | Category | Task | Platforms |
|----------|----------|------|-----------|
| 🔴 High | **Streaming** | Web (JS/WasmJS) video receive & audio send testing | JS, WasmJS |
| 🔴 High | **Data Channel** | Text message send/receive verification | All |
| 🔴 High | **Data Channel** | Binary data (image/byte array) send/receive verification | All |
| 🔴 High | **Stats** | FPS calculation accuracy verification | All |
| 🔴 High | **Stats** | Resolution reporting verification | All |
| 🔴 High | **Stats** | Bitrate calculation accuracy verification | All |
| 🔴 High | **Stats** | Latency/RTT measurement verification | All |

### Completed Features

- ✅ **WhipSignaling** - WHIP signaling with trickle ICE support
- ✅ **AudioPushPlayer** - Cross-platform audio push composable
- ✅ **Video/Audio Streaming** - Verified on Android, iOS, JVM

### Planned Features

| Priority | Feature | Description |
|----------|---------|-------------|
| ✅ Done | **Audio Processing** | Echo cancellation, noise suppression, auto gain control (via `AudioPushConfig`) |
| 🟡 Medium | **Video Sending** | Camera capture and video push via WHIP |
| 🟢 Low | **Screen Sharing** | Desktop/Mobile screen capture and streaming |

---

## Contributing

Issues and Pull Requests are welcome!

---

## CI/CD Workflows

This project uses GitHub Actions for automated releases:

### Workflows

| Workflow | Trigger | Description |
|----------|---------|-------------|
| **CI** | Push/PR to `main` | Runs tests and builds |
| **Release Please** | Push to `main` | Creates release PRs with changelog |
| **Publish** | Release published | Builds and publishes to GitHub Packages |

### Release Process

1. Commit with [Conventional Commits](https://www.conventionalcommits.org/) format:
   - `feat:` - New feature (minor version bump)
   - `fix:` - Bug fix (patch version bump)
   - `feat!:` or `BREAKING CHANGE:` - Breaking change (major version bump)

2. **Release Please** automatically creates a release PR with:
   - Updated version number
   - Generated CHANGELOG.md

3. Merge the release PR → GitHub Release is created → **Publish** workflow publishes to GitHub Packages

### Manual Release

You can manually trigger the **Publish** workflow from GitHub Actions page.

---

## Related Projects
- [webrtc-java](https://github.com/nicokosi/webrtc-java) - JVM WebRTC implementation
- [GoogleWebRTC](https://cocoapods.org/pods/GoogleWebRTC) - iOS WebRTC CocoaPod
