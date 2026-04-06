# Project Guidelines

## Overview

SyncAI-Lib-KmpWebRTC is a Kotlin Multiplatform WebRTC client library supporting Android, iOS, JVM platforms (JS and WasmJS are partial stubs). It provides video/audio stream receiving (WHEP), audio/video sending (WHIP), bidirectional media, and DataChannel communication via a unified `WebRTCSession` API.

## Architecture

- **Single-module KMP structure**: All code resides in a single Gradle module
- **expect/actual pattern**: Shared interfaces defined in `src/commonMain/`, each platform provides `actual` implementations in corresponding source sets
- **Compose Multiplatform**: UI components use Compose (`VideoRenderer`, `CameraPreview`, `AudioPushPlayer`)
- **Three-layer architecture**: Layer 1 (`HttpSignalingAdapter` / custom `SignalingAdapter`) → Layer 2 (`WebRTCSession` with `MediaConfig`) → Layer 3 (Composables)
- **Package**: `com.syncrobotic.webrtc` (sub-packages: `config`, `signaling`, `session`, `audio`, `ui`, `datachannel`)

### Source Set Structure

| Source Set | Platform | WebRTC Implementation |
|------------|----------|----------------------|
| `commonMain` | Shared | Interfaces, config, signaling, state machine |
| `androidMain` | Android | Google WebRTC SDK + ExoPlayer |
| `iosMain` | iOS | GoogleWebRTC CocoaPod |
| `jvmMain` | Desktop | webrtc-java |
| `jsMain` | Browser | Partial stub (not production-ready) |
| `wasmJsMain` | Browser | Incomplete stub |

## Code Style

- Language: Kotlin, following `kotlin.code.style=official`
- JVM target: Java 11
- **All comments, documentation, and commit messages must be written in English**
- Use Kotlin Coroutines and StateFlow for asynchronous operations
- Compose components provided as `@Composable` functions, returning Controllers
- Architecture pattern: State Machine (sealed class states) + expect/actual platform abstraction, not Redux or Clean Architecture

## Docs Reference

Project architecture and specification documents are stored in the `docs/` directory:
- [docs/ROADMAP.md](../docs/ROADMAP.md): Feature planning and milestones
- [docs/TEST_SPEC.md](../docs/TEST_SPEC.md): Test specification with implementation status and execution guide

## Code Review Checklist

When generating or modifying code, ask yourself:
- [ ] Are platform dependencies (Android/iOS/JVM) properly abstracted with expect/actual?
- [ ] Are performance and resource cleanup considered (PeerConnection, Factory, EglBase)?
- [ ] Is the interface segregation principle maintained (small, focused interfaces)?
- [ ] Are new dependencies introduced? If so, they must be added to `gradle/libs.versions.toml`
- [ ] Does error handling use sealed class states (e.g. `SessionState.Error`, `PlayerState.Error`)?
- [ ] Does new signaling code use `SignalingAdapter` interface with `HttpSignalingAdapter`?
- [ ] Could this break existing platform actual implementations?

## Commit Rules

Commit messages must be in English, following Conventional Commits (release-please depends on this format):

| Type | Prefix | Example |
|------|--------|--------|
| New feature | `feat:` | `feat: add DataChannel support` |
| Bug fix | `fix:` | `fix: resolve EglContext conflict on multi-renderer` |
| Optimization | `perf:` | `perf: reduce memory usage in JVM video decoding` |
| Test | `test:` | `test: add E2E bidirectional audio tests` |
| Documentation | `docs:` | `docs: update TEST_SPEC with coverage report` |
| Refactor | `refactor:` | `refactor: unify session API with MediaConfig` |

## Build and Test

```bash
# Build all platforms
./gradlew build

# Build specific targets
./gradlew jvmMainClasses          # JVM
./gradlew bundleReleaseAar        # Android AAR
./gradlew linkPodReleaseFrameworkIosArm64  # iOS framework

# Run tests
./gradlew jvmTest                 # Level 1: Unit + E2E (mock server)
./gradlew jvmTest --tests "*.e2e.MediaMTX*" --no-build-cache  # Level 2: Testcontainers

# Coverage report
./gradlew koverHtmlReport         # HTML report at build/reports/kover/html/

# Publish to local Maven
./gradlew publishToMavenLocal

# Publish to GitHub Packages
./gradlew publish -Pversion=x.y.z
```

## Conventions

- **PeerConnectionFactory management**: Android creates a separate Factory per connection (to avoid EglContext conflicts); JVM uses reference counting to share a single Factory
- **Multi-instance safety**: Multiple `VideoRenderer` / `WebRTCSession` instances can run simultaneously with no shared state conflicts
- **Signaling**: Uses `SignalingAdapter` interface with built-in `HttpSignalingAdapter` (unified WHEP/WHIP). Custom implementations (e.g. WebSocket) can implement `SignalingAdapter` directly
- **Authentication**: `SignalingAuth` sealed interface — `Bearer(token)`, `Cookies(map)`, `CookieStorage(storage)`, `Custom(headers)`, `None`
- **Error handling**: Signaling errors use `SignalingException` with `SignalingErrorCode` enum (`OFFER_REJECTED`, `ICE_CANDIDATE_FAILED`, `NETWORK_ERROR`, `SESSION_TERMINATED`, `UNKNOWN`). Session errors expressed as `SessionState.Error(message, cause, isRetryable)`
- **Version management**: Managed automatically by release-please; version passed via `-Pversion=x.y.z`
- **Dependency definitions**: Uses version catalog `gradle/libs.versions.toml`, referenced in code as `webrtcLibs.xxx`
- **iOS Simulator**: GoogleWebRTC does not support iOS Simulator; physical device only
- **Logging**: Currently uses platform-native logging (Android `Log.d`, iOS `NSLog`, JVM `println`)
- **Removed v1.x APIs**: `WhepSession`, `WhipSession`, `WhepSignalingAdapter`, `WhipSignalingAdapter`, `WhepSignaling`, `WhipSignaling`, `WebSocketSignaling`, `BidirectionalConfig`, `WhepException`, `WhipException` — all removed in v2.0, do NOT reference them

## Key Dependencies

| Dependency | Purpose |
|------------|--------|
| Compose Multiplatform 1.10.0 | UI framework |
| Kotlin 2.3.0 | Language |
| Ktor 3.0.3 | HTTP/WebSocket signaling |
| webrtc-android (io.github.webrtc-sdk) | Android WebRTC |
| webrtc-java (dev.onvoid.webrtc) | Desktop WebRTC |
| GoogleWebRTC CocoaPod 1.1.31999 | iOS WebRTC |
| Testcontainers 1.21.1 | Docker-based integration tests |
| Kover 0.9.1 | Test coverage reporting |

## Roadmap

See [docs/ROADMAP.md](../docs/ROADMAP.md) for feature planning and milestones.
