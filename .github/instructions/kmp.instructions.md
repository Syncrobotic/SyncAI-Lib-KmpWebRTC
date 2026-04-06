---
description: "Use when editing Kotlin Multiplatform (KMP) source code, adding expect/actual declarations, modifying source sets, or working with Compose Multiplatform UI components."
applyTo: "src/**/*.kt"
---
# Kotlin Multiplatform Conventions

## expect/actual Pattern

- `expect` declarations go in `src/commonMain/kotlin/com/syncrobotic/webrtc/`
- `actual` implementations go in the corresponding platform source set (`androidMain`, `iosMain`, `jvmMain`, `jsMain`, `wasmJsMain`)
- When adding a new `expect`, you must provide `actual` on all enabled platforms

## Compose Multiplatform

- UI components use `@Composable` functions that return Controller objects for external control
- Pattern: `VideoRenderer(session: WebRTCSession)`, `CameraPreview(session: WebRTCSession)`, `AudioPushPlayer(session: WebRTCSession)`
- Platform-specific Composables use `expect`/`actual` fun

## Source Set Dependencies

- `commonMain`: Only KMP shared dependencies (Ktor core, Compose runtime, kotlinx)
- `androidMain`: Can use Android SDK, ExoPlayer, `webrtcLibs.webrtc.android`
- `iosMain`: Uses GoogleWebRTC via CocoaPods (`cocoapods { pod("GoogleWebRTC") }`)
- `jvmMain`: Uses `webrtcLibs.webrtc.java` + platform native JARs
- `jsMain`: Uses browser native APIs (`external` declarations) — partial stub
- New dependencies must be defined in `gradle/libs.versions.toml` and referenced in `build.gradle.kts` as `webrtcLibs.xxx`

## State Management

- Use `StateFlow` to expose state changes
- State classes use `sealed class` (e.g. `SessionState`, `PlayerState`, `AudioPushState`, `WebRTCState`)
- Asynchronous operations use Kotlin Coroutines

## Interface Design

- Follow interface segregation principle: each interface has a single responsibility, small and focused
- Existing examples: `SignalingAdapter` (SDP exchange), `VideoPlayerController` (playback control), `AudioPushController` (audio push), `DataChannelListener` (data channel events)
- Core types: `WebRTCSession` (unified session with `MediaConfig`), `HttpSignalingAdapter` (WHEP/WHIP), `SignalingAuth` (authentication)
- When adding new interfaces, avoid monolithic designs; prefer splitting into multiple small interfaces
