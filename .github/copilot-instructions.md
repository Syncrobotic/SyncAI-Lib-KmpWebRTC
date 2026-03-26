# Project Guidelines

## Overview

SyncAI-Lib-KmpWebRTC is a Kotlin Multiplatform WebRTC client library supporting Android, iOS, JVM, JS, and WasmJS platforms. It provides video/audio stream receiving (WHEP), audio sending (WHIP), and DataChannel bidirectional communication.

## Architecture

- **Single-module KMP structure**: All code resides in a single Gradle module
- **expect/actual pattern**: Shared interfaces defined in `src/commonMain/`, each platform provides `actual` implementations in corresponding source sets
- **Compose Multiplatform**: UI components use Compose (`VideoRenderer`, `AudioPushPlayer`, `BidirectionalPlayer`)
- **Package**: `com.syncrobotic.webrtc` (sub-packages: `config`, `signaling`, `audio`, `ui`, `datachannel`)

### Source Set Structure

| Source Set | Platform | WebRTC Implementation |
|------------|----------|----------------------|
| `commonMain` | Shared | Interfaces, config, signaling, state machine |
| `androidMain` | Android | Google WebRTC SDK + ExoPlayer |
| `iosMain` | iOS | GoogleWebRTC CocoaPod |
| `jvmMain` | Desktop | webrtc-java |
| `jsMain` | Browser | Native RTCPeerConnection (partial stub) |
| `wasmJsMain` | Browser | Native RTCPeerConnection (incomplete) |

## Code Style

- Language: Kotlin, following `kotlin.code.style=official`
- JVM target: Java 11
- Use Kotlin Coroutines and StateFlow for asynchronous operations
- Compose components provided as `@Composable` functions, returning Controllers
- Architecture pattern: State Machine (sealed class states) + expect/actual platform abstraction, not Redux or Clean Architecture

## Docs Reference

Project architecture and specification documents are stored in the `docs/` directory. Verify generated code conforms to design principles before committing:
- [docs/ROADMAP.md](../docs/ROADMAP.md): Feature planning and milestones

## Code Review Checklist

When generating or modifying code, ask yourself:
- [ ] Are platform dependencies (Android/iOS/JVM) properly abstracted with expect/actual?
- [ ] Are performance and resource cleanup considered (PeerConnection, Factory, EglBase)?
- [ ] Is the interface segregation principle maintained (small, focused interfaces)?
- [ ] Are new dependencies introduced? If so, they must be added to `gradle/libs.versions.toml`
- [ ] Does error handling use sealed class states (e.g. `PlayerState.Error`)?
- [ ] Could this break existing platform actual implementations?

## Commit Rules

Commit messages must be in English, following Conventional Commits (release-please depends on this format):

| Type | Prefix | Example |
|------|--------|--------|
| New feature | `feat:` | `feat: add VideoPushPlayer composable` |
| Bug fix | `fix:` | `fix: resolve EglContext conflict on multi-renderer` |
| Optimization | `perf:` | `perf: reduce memory usage in JVM video decoding` |
| Test | `test:` | `test: add WebRTCConfig unit tests` |
| Documentation | `docs:` | `docs: update ROADMAP phase 1 status` |
| Refactor | `refactor:` | `refactor: extract signaling interface` |

## Build and Test

```bash
# Build all platforms
./gradlew build

# Build specific targets
./gradlew jvmMainClasses          # JVM
./gradlew bundleReleaseAar        # Android AAR
./gradlew jsJar                   # JavaScript
./gradlew wasmJsJar               # WebAssembly
./gradlew linkPodReleaseFrameworkIosArm64  # iOS framework

# Run tests
./gradlew jvmTest

# Publish to local Maven
./gradlew publishToMavenLocal

# Publish to GitHub Packages
./gradlew publish -Pversion=x.y.z
```

## Conventions

- **PeerConnectionFactory management**: Android creates a separate Factory per connection (to avoid EglContext conflicts); JVM uses reference counting to share a single Factory
- **Multi-instance safety**: Multiple `VideoRenderer` / `WebRTCClient` instances can run simultaneously with no shared state conflicts
- **Signaling**: Currently uses concrete classes (`WhepSignaling`, `WhipSignaling`, `WebSocketSignaling`); abstract interface planned
- **Version management**: Managed automatically by release-please; version defined in `gradle.properties`
- **Dependency definitions**: Uses version catalog `gradle/libs.versions.toml`, referenced in code as `webrtcLibs.xxx`
- **iOS Simulator**: GoogleWebRTC does not support iOS Simulator; physical device only
- **Logging**: Currently uses platform-native logging (Android `Log.d`, iOS `NSLog`, JVM `println`); unified Logger abstraction planned (see ROADMAP Phase 2)
- **Error handling**: Uses sealed class states for errors (`PlayerState.Error`, `AudioPushState.Error`); Signaling layer uses custom Exceptions (`WhepException`, `WhipException`)

## Key Dependencies

| Dependency | Purpose |
|------------|--------|
| Compose Multiplatform 1.10.0 | UI framework |
| Kotlin 2.3.0 | Language |
| Ktor 3.0.3 | HTTP/WebSocket signaling |
| webrtc-android (io.github.webrtc-sdk) | Android WebRTC |
| webrtc-java (dev.onvoid.webrtc) | Desktop WebRTC |
| GoogleWebRTC CocoaPod 1.1.31999 | iOS WebRTC |

## Roadmap

See [docs/ROADMAP.md](../docs/ROADMAP.md) for feature planning and milestones.
