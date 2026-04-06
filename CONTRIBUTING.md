# Contributing to SyncAI-Lib-KmpWebRTC

Thank you for your interest in contributing! This guide will help you get started.

## Prerequisites

- **JDK 17+** (for building)
- **Android SDK** (API 24+)
- **Xcode 15+** (for iOS — physical device only, Simulator not supported)
- **Gradle 8.x** (included via wrapper)
- **Docker** (optional, for Level 2 integration tests — see [Running Tests](#running-tests))

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

1. **Layer 1 — SignalingAdapter**: SDP exchange via `HttpSignalingAdapter` (WHEP/WHIP) or custom `SignalingAdapter` implementations (WebSocket, etc.)
2. **Layer 2 — WebRTCSession**: Unified PeerConnection lifecycle with flexible `MediaConfig` (receive, send, bidirectional), retry, and stats
3. **Layer 3 — Composables**: `VideoRenderer`, `CameraPreview`, `AudioPushPlayer`

Platform-specific code uses the **expect/actual** pattern. Shared interfaces are defined in `commonMain`, and each platform provides `actual` implementations.

## Code Style

- **Language:** Kotlin, following `kotlin.code.style=official`
- **JVM target:** Java 11
- **All comments, documentation, and commit messages must be in English**
- Use Kotlin Coroutines and `StateFlow` for async operations
- Use `sealed class` states for error handling (e.g., `SessionState.Error`, `PlayerState.Error`)
- Use `HttpSignalingAdapter` for WHEP/WHIP — the legacy `WhepSignalingAdapter`/`WhipSignalingAdapter` from v1.x were removed
- Use `WebRTCSession` with `MediaConfig` — the legacy `WhepSession`/`WhipSession` from v1.x were removed

## Commit Convention

We follow [Conventional Commits](https://www.conventionalcommits.org/) (required for release-please):

| Type | Prefix | Example |
|------|--------|---------|
| New feature | `feat:` | `feat: add DataChannel support` |
| Bug fix | `fix:` | `fix: resolve EglContext conflict on multi-renderer` |
| Optimization | `perf:` | `perf: reduce memory usage in JVM video decoding` |
| Test | `test:` | `test: add E2E bidirectional audio tests` |
| Documentation | `docs:` | `docs: update TEST_SPEC with coverage report` |
| Refactor | `refactor:` | `refactor: unify WhepSession/WhipSession into WebRTCSession` |

## Branch Strategy

```
main ← dev ← feature branches
```

- **`main`**: Production releases only. Protected, requires PR from `dev`.
- **`dev`**: Integration branch. All feature PRs target `dev`.
- **Feature branches**: Branch from `dev`, named `feat/xxx` or `fix/xxx`.

## Pull Request Process

### 1. Create your branch

```bash
git checkout dev
git pull origin dev
git checkout -b feat/my-feature
```

### 2. Develop and test locally

```bash
# Run all Level 1 tests (must pass before PR)
./gradlew jvmTest

# Run Level 2 tests (requires Docker — see "Running Tests" section below)
./gradlew jvmTest --tests "com.syncrobotic.webrtc.e2e.MediaMTX*" --no-build-cache
```

> **Important:** CI 環境沒有攝影機和麥克風，E2E full WebRTC tests 中涉及 `SEND_VIDEO` / `SEND_AUDIO` 的測試在 CI 上只會驗證 error handling path（session 進入 `Error` state 仍算 pass）。
> 只有在**本機有硬體的環境**才能測到完整的 `Connected` path。
> 因此，如果你的改動涉及 media capture、signaling flow 或 session lifecycle，**請務必在本機執行 Level 1 + Level 2 測試**，確保 Connected path 也正確。

### 3. Push and create PR targeting `dev`

```bash
git push origin feat/my-feature
# pre-push hook will run jvmTest automatically
```

### 4. CI runs automatically

When you open a PR to `dev`, GitHub Actions CI will run the following checks:

```
PR → dev
  │
  ├─ Build all platforms
  │   ├─ JVM (jvmMainClasses)
  │   ├─ Android (bundleReleaseAar)
  │   ├─ JS (jsJar)
  │   ├─ WasmJS (wasmJsJar)
  │   └─ iOS (linkPodReleaseFrameworkIosArm64)
  │
  ├─ Level 1: Unit Tests + E2E
  │   ├─ Unit Tests (~162 tests) — config, signaling, state, models
  │   ├─ E2E Signaling (~30 tests) — MockWhepWhipServer
  │   └─ E2E Full WebRTC (~15 tests) — native libs (skip if unavailable)
  │
  ├─ Level 2: Testcontainers MediaMTX
  │   └─ Integration Tests (8 tests) — real MediaMTX in Docker
  │
  ├─ Test Summary — printed in PR check (total / passed / failed / skipped)
  │
  └─ Coverage Report — printed in PR check (instruction coverage %)
```

**All checks must pass before merging.** The CI can also be triggered manually from the Actions tab (`workflow_dispatch`).

### 5. Code review checklist

Before submitting, verify:

- [ ] `./gradlew jvmTest` passes locally
- [ ] Platform dependencies are properly abstracted with `expect/actual`
- [ ] Resources are cleaned up (PeerConnection, Factory, EglBase)
- [ ] New dependencies are added to `gradle/libs.versions.toml`
- [ ] Error handling uses sealed class states
- [ ] New signaling code uses `SignalingAdapter` interface
- [ ] Changes don't break existing platform implementations
- [ ] Documentation updated if public API changed

## Running Tests

### Level 1: Unit Tests + E2E (no external dependencies)

```bash
./gradlew jvmTest
```

Runs ~207 tests including unit tests and E2E tests against an in-process mock WHEP/WHIP server. No Docker or external services needed.

### Level 2: Testcontainers MediaMTX (requires Docker)

```bash
# Install Colima (recommended for macOS)
brew install colima docker
colima start

# Run Level 2 tests
./gradlew jvmTest --tests "com.syncrobotic.webrtc.e2e.MediaMTX*" --no-build-cache

# Stop Docker when done
colima stop
```

Runs 8 integration tests against a real MediaMTX server in Docker. Tests are automatically skipped if Docker is unavailable.

### Coverage report

```bash
./gradlew koverHtmlReport
open build/reports/kover/html/index.html
```

> For full test execution details, see [docs/TEST_SPEC.md](docs/TEST_SPEC.md).

## Build Commands

```bash
# Build specific targets
./gradlew jvmMainClasses                       # JVM
./gradlew bundleReleaseAar                      # Android AAR
./gradlew linkPodReleaseFrameworkIosArm64       # iOS framework

# Run tests
./gradlew jvmTest                               # All Level 1 tests
./gradlew jvmTest --tests "*.e2e.MediaMTX*"     # Level 2 tests (Docker required)

# Coverage
./gradlew koverHtmlReport                       # HTML report
./gradlew koverLog                              # Terminal summary

# Publish to local Maven (for local testing)
./gradlew publishToMavenLocal
```

## Dependencies

All dependencies are managed via version catalog in `gradle/libs.versions.toml`, referenced as `webrtcLibs.xxx` in build scripts.

## Important Notes

- **iOS Simulator:** GoogleWebRTC does not support iOS Simulator. Use a physical device for iOS testing.
- **PeerConnectionFactory:** Android creates a separate Factory per connection; JVM uses reference counting to share one.
- **Multi-instance:** Multiple `VideoRenderer` / `WebRTCSession` instances can run simultaneously.
- **Deprecated APIs:** `WhepSession`, `WhipSession`, `WhepSignaling`, `WhipSignaling` are deprecated — use `WebRTCSession` + `HttpSignalingAdapter` instead.

## Questions?

Open an issue on GitHub or check the [ROADMAP](docs/ROADMAP.md) for planned features.
