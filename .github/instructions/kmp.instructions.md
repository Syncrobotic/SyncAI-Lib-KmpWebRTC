---
description: "Use when editing Kotlin Multiplatform (KMP) source code, adding expect/actual declarations, modifying source sets, or working with Compose Multiplatform UI components."
applyTo: "src/**/*.kt"
---
# Kotlin Multiplatform Conventions

## expect/actual Pattern

- `expect` 宣告放在 `src/commonMain/kotlin/com/syncrobotic/webrtc/`
- `actual` 實作放在對應的平台 source set（`androidMain`、`iosMain`、`jvmMain`、`jsMain`、`wasmJsMain`）
- 新增 `expect` 時，必須在所有已啟用的平台提供 `actual`

## Compose Multiplatform

- UI 元件使用 `@Composable` function，回傳 Controller 物件供外部操控
- 模式範例：`VideoRenderer(config) → VideoPlayerController`
- 平台特定的 Composable 用 `expect`/`actual` fun 實作

## Source Set Dependencies

- `commonMain`：只能用 KMP 共用依賴（Ktor core、Compose runtime、kotlinx）
- `androidMain`：可用 Android SDK、ExoPlayer、`webrtcLibs.webrtc.android`
- `iosMain`：透過 CocoaPods 使用 GoogleWebRTC（`cocoapods { pod("GoogleWebRTC") }`）
- `jvmMain`：使用 `webrtcLibs.webrtc.java` + 平台原生 JAR
- `jsMain`：使用瀏覽器原生 API（`external` declarations）
- 新增依賴一律在 `gradle/libs.versions.toml` 定義，`build.gradle.kts` 中用 `webrtcLibs.xxx` 引用

## State Management

- 使用 `StateFlow` 暴露狀態變化
- 狀態類別用 `sealed class`（如 `PlayerState`、`AudioPushState`、`WebRTCState`）
- 非同步操作使用 Kotlin Coroutines

## Interface Design

- 遵循介面隔離原則：每個 interface 單一職責、小而專注
- 現有範例：`VideoPlayerController`（播放控制）、`AudioPushController`（音訊推送）、`DataChannelListener`（資料通道事件）
- 新增 interface 時避免大而全的設計，優先拆分為多個小 interface
