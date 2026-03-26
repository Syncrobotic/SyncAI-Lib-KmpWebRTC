# Project Guidelines

## Overview

SyncAI-Lib-KmpWebRTC 是 Kotlin Multiplatform WebRTC 客戶端函式庫，支援 Android、iOS、JVM、JS、WasmJS 平台。提供視訊/音訊串流接收（WHEP）、音訊發送（WHIP）、DataChannel 雙向通訊等功能。

## Architecture

- **單模組 KMP 結構**：所有程式碼在同一個 Gradle module 中
- **expect/actual 模式**：共用介面定義在 `src/commonMain/`，各平台在對應 source set 提供 `actual` 實作
- **Compose Multiplatform**：UI 元件使用 Compose（`VideoRenderer`、`AudioPushPlayer`、`BidirectionalPlayer`）
- **Package**：`com.syncrobotic.webrtc`（子 package：`config`、`signaling`、`audio`、`ui`、`datachannel`）

### Source Set 結構

| Source Set | 平台 | WebRTC 實作 |
|------------|------|------------|
| `commonMain` | 共用 | 介面、config、signaling、state machine |
| `androidMain` | Android | Google WebRTC SDK + ExoPlayer |
| `iosMain` | iOS | GoogleWebRTC CocoaPod |
| `jvmMain` | Desktop | webrtc-java |
| `jsMain` | Browser | 原生 RTCPeerConnection（部分 stub） |
| `wasmJsMain` | Browser | 原生 RTCPeerConnection（未完成） |

## Code Style

- 語言：Kotlin，遵循 `kotlin.code.style=official`
- JVM target：Java 11
- 使用 Kotlin Coroutines 和 StateFlow 進行非同步操作
- Compose 元件以 `@Composable` function 方式提供，回傳 Controller
- 架構模式：State Machine（sealed class 狀態）+ expect/actual 平台抽象，非 Redux 或 Clean Architecture

## Docs Reference

專案架構及規格文件儲存於 `docs/` 目錄下，生成代碼前請確認符合設計原則：
- [docs/ROADMAP.md](../docs/ROADMAP.md)：功能規劃與里程碑

## Code Review Checklist

生成或修改代碼時應自問：
- [ ] 是否對平台依賴（Android/iOS/JVM）進行了適當的 expect/actual 抽象？
- [ ] 是否考慮了性能和資源釋放（PeerConnection、Factory、EglBase）？
- [ ] 是否保持了介面隔離原則（小而專注的 interface）？
- [ ] 是否引入了新依賴？如有，必須加在 `gradle/libs.versions.toml`
- [ ] 錯誤處理是否使用 sealed class state（如 `PlayerState.Error`）？
- [ ] 是否可能破壞現有平台的 actual 實作？

## Commit Rules

Commit message 使用英文，遵循 Conventional Commits（release-please 依賴此格式）：

| 類型 | 前綴 | 示例 |
|------|------|------|
| 新功能 | `feat:` | `feat: add VideoPushPlayer composable` |
| 修復 | `fix:` | `fix: resolve EglContext conflict on multi-renderer` |
| 優化 | `perf:` | `perf: reduce memory usage in JVM video decoding` |
| 測試 | `test:` | `test: add WebRTCConfig unit tests` |
| 文檔 | `docs:` | `docs: update ROADMAP phase 1 status` |
| 重構 | `refactor:` | `refactor: extract signaling interface` |

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

- **PeerConnectionFactory 管理**：Android 每個連線建立獨立 Factory（避免 EglContext 衝突）；JVM 使用 reference counting 共享 Factory
- **多實例安全**：多個 `VideoRenderer` / `WebRTCClient` 可同時使用，無共享狀態衝突
- **Signaling**：目前使用具體類別（`WhepSignaling`、`WhipSignaling`、`WebSocketSignaling`），抽象介面規劃中
- **版本管理**：使用 release-please 自動管理，版本定義在 `gradle.properties`
- **依賴定義**：使用 version catalog `gradle/libs.versions.toml`，在程式碼中以 `webrtcLibs.xxx` 引用
- **iOS Simulator**：GoogleWebRTC 不支援 iOS Simulator，僅支援實機
- **Logging**：目前各平台直接使用原生 log（Android `Log.d`、iOS `NSLog`、JVM `println`），統一 Logger 抽象規劃中（見 ROADMAP Phase 2）
- **錯誤處理**：使用 sealed class 狀態表達錯誤（`PlayerState.Error`、`AudioPushState.Error`），Signaling 層使用自訂 Exception（`WhepException`、`WhipException`）

## Key Dependencies

| 依賴 | 用途 |
|------|------|
| Compose Multiplatform 1.10.0 | UI 框架 |
| Kotlin 2.3.0 | 語言 |
| Ktor 3.0.3 | HTTP/WebSocket signaling |
| webrtc-android (io.github.webrtc-sdk) | Android WebRTC |
| webrtc-java (dev.onvoid.webrtc) | Desktop WebRTC |
| GoogleWebRTC CocoaPod 1.1.31999 | iOS WebRTC |

## Roadmap

See [docs/ROADMAP.md](../docs/ROADMAP.md) for feature planning and milestones.
