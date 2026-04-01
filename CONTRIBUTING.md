# Contributing to SyncAI-Lib-KmpWebRTC

Thank you for your interest in contributing! This guide will help you get started.

## Prerequisites

- **JDK 17+** (for building)
- **Android SDK** (API 24+)
- **Xcode 15+** (for iOS — physical device only, Simulator not supported)
- **Gradle 8.x** (included via wrapper)

## Getting Started

```bash
# Clone the repository
git clone https://github.com/Syncrobotic/SyncAI-Lib-KmpWebRTC.git
cd SyncAI-Lib-KmpWebRTC

# Build all platforms
./gradlew build

# Run tests
./gradlew jvmTest

# Publish to local Maven (for testing in your app)
./gradlew publishToMavenLocal
```

> **Note:** The project uses `.githooks/pre-push` to run `jvmTest` before each push. This is auto-configured on first build via `git config core.hooksPath .githooks`.

## Project Structure

```
src/
├── commonMain/    # Shared interfaces, config, signaling, state machine
├── androidMain/   # Android (Google WebRTC SDK)
├── iosMain/       # iOS (GoogleWebRTC CocoaPod)
├── jvmMain/       # Desktop (webrtc-java)
├── jsMain/        # Browser JS (partial stub)
└── wasmJsMain/    # Browser Wasm (incomplete)
```

**Package:** `com.syncrobotic.webrtc` with sub-packages: `config`, `signaling`, `session`, `audio`, `ui`, `datachannel`

## Architecture

This library uses a **three-layer architecture**:

1. **Layer 1 — SignalingAdapter**: SDP exchange (WHEP/WHIP HTTP, custom WebSocket)
2. **Layer 2 — WhepSession / WhipSession**: PeerConnection lifecycle, retry, stats
3. **Layer 3 — Composables**: `VideoRenderer`, `AudioPushPlayer`

Platform-specific code uses the **expect/actual** pattern. Shared interfaces are defined in `commonMain`, and each platform provides `actual` implementations.

## Code Style

- **Language:** Kotlin, following `kotlin.code.style=official`
- **JVM target:** Java 11
- **All comments, documentation, and commit messages must be in English**
- Use Kotlin Coroutines and `StateFlow` for async operations
- Use `sealed class` states for error handling (e.g., `SessionState.Error`, `PlayerState.Error`)
- Use `SignalingAdapter` interface — do **not** use legacy `WhepSignaling`/`WhipSignaling`

## Commit Convention

We follow [Conventional Commits](https://www.conventionalcommits.org/) (required for release-please):

| Type | Prefix | Example |
|------|--------|---------|
| New feature | `feat:` | `feat: add VideoPushPlayer composable` |
| Bug fix | `fix:` | `fix: resolve EglContext conflict on multi-renderer` |
| Optimization | `perf:` | `perf: reduce memory usage in JVM video decoding` |
| Test | `test:` | `test: add WebRTCConfig unit tests` |
| Documentation | `docs:` | `docs: update ROADMAP phase 1 status` |
| Refactor | `refactor:` | `refactor: extract signaling interface` |

## Pull Request Guidelines

1. **Branch from `dev`**, not `main`
2. Keep PRs focused — one feature or fix per PR
3. Ensure `./gradlew build` passes on all platforms
4. Ensure `./gradlew jvmTest` passes
5. Update documentation if your change affects the public API

### Code Review Checklist

Before submitting, verify:

- [ ] Platform dependencies are properly abstracted with `expect/actual`
- [ ] Resources are cleaned up (PeerConnection, Factory, EglBase)
- [ ] New dependencies are added to `gradle/libs.versions.toml`
- [ ] Error handling uses sealed class states
- [ ] New signaling code uses `SignalingAdapter` interface
- [ ] Changes don't break existing platform implementations

## Build Commands

```bash
# Build specific targets
./gradlew jvmMainClasses                       # JVM
./gradlew bundleReleaseAar                      # Android AAR
./gradlew linkPodReleaseFrameworkIosArm64       # iOS framework

# Run tests
./gradlew jvmTest

# Publish to local Maven (for local testing)
./gradlew publishToMavenLocal
```

## Dependencies

All dependencies are managed via version catalog in `gradle/libs.versions.toml`, referenced as `webrtcLibs.xxx` in build scripts.

## Important Notes

- **iOS Simulator:** GoogleWebRTC does not support iOS Simulator. Use a physical device for iOS testing.
- **PeerConnectionFactory:** Android creates a separate Factory per connection; JVM uses reference counting to share one.
- **Multi-instance:** Multiple `VideoRenderer` / `WhepSession` instances can run simultaneously.
- **Deprecated APIs:** Types marked `@Deprecated` (e.g., `WebRTCConfig.signalingType`, `BidirectionalConfig`) will be removed in v3.0.

## Questions?

Open an issue on GitHub or check the [ROADMAP](docs/ROADMAP.md) for planned features.
