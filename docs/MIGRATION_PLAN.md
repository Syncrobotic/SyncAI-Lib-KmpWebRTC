# KmpWebRTC v2.0 Migration Plan

## Overview

將 library 從 v1.x 遷移到 v2.0 的完整開發計畫。  
策略：**漸進式遷移** — 新 API 與舊 API 並存，舊 API 標記 `@Deprecated`，v3.0 才刪除。

### Version Timeline

| Version | 內容 | 破壞性變更 |
|---------|------|-----------|
| **v2.0** | SignalingAdapter + Session API + Composable 重構 | 無（所有舊 API 保留但 deprecated） |
| **v2.1** | 內建 `WebSocketSignalingAdapter`、Logger 抽象化 | 無 |
| **v3.0** | 刪除所有 deprecated API、`WebRTCClient` 變 internal | **是** |

### Dependency Graph

```
Phase 1 (SignalingAdapter) ─┐
                            ├──▶ Phase 3 (Session API) ──▶ Phase 4 (Composables)
Phase 2 (Config Cleanup) ───┘                              Phase 5 (Cleanup) ─ parallel
```

---

## Phase 1: SignalingAdapter Abstraction

> 基礎層 — 所有後續 phase 的依賴

### 1.1 建立 `SignalingAdapter` interface

**新建**: `src/commonMain/kotlin/com/syncrobotic/webrtc/signaling/SignalingAdapter.kt`

```kotlin
package com.syncrobotic.webrtc.signaling

interface SignalingAdapter {
    suspend fun sendOffer(sdpOffer: String): SignalingResult
    suspend fun sendIceCandidate(
        resourceUrl: String,
        candidate: String,
        sdpMid: String?,
        sdpMLineIndex: Int,
        iceUfrag: String?,
        icePwd: String?
    )
    suspend fun terminate(resourceUrl: String)
}

data class SignalingResult(
    val sdpAnswer: String,
    val resourceUrl: String? = null,
    val etag: String? = null,
    val iceServers: List<IceServer> = emptyList()
)
```

**統一取代**: `WhepSignaling.SessionResult` + `WhipSignaling.SessionResult` + `AnswerResult`

### 1.2 建立 `SignalingAuth` sealed interface

**同檔案**: `SignalingAdapter.kt`

```kotlin
sealed interface SignalingAuth {
    data class Bearer(val token: String) : SignalingAuth
    data class Cookies(val cookies: Map<String, String>) : SignalingAuth
    data class CookieStorage(val storage: CookiesStorage) : SignalingAuth
    data class Custom(val headers: Map<String, String>) : SignalingAuth
    data object None : SignalingAuth
}
```

**Plugin 保證**:
| Auth type | 安裝 Ktor plugin? | 行為 |
|-----------|-------------------|------|
| `None` | ❌ | 不加任何 header |
| `Bearer` | ❌ | `Authorization: Bearer <token>` |
| `Cookies` | ❌ | 手動拼 `Cookie` header string |
| `CookieStorage` | ✅ `HttpCookies` | 共享 cookie jar + 自動 Set-Cookie |
| `Custom` | ❌ | headers as-is |

### 1.3 建立 `WhepSignalingAdapter`

**新建**: `src/commonMain/kotlin/com/syncrobotic/webrtc/signaling/WhepSignalingAdapter.kt`

```kotlin
class WhepSignalingAdapter(
    url: String,
    auth: SignalingAuth = SignalingAuth.None,
    httpClient: HttpClient? = null   // null → 自動建立 platform engine
) : SignalingAdapter
```

- 複用現有 `WhepSignaling` 的 HTTP 邏輯
- 根據 `auth` 附加對應的 HTTP headers

### 1.4 建立 `WhipSignalingAdapter`

**新建**: `src/commonMain/kotlin/com/syncrobotic/webrtc/signaling/WhipSignalingAdapter.kt`

- 同 1.3 模式，複用現有 `WhipSignaling` 邏輯

### 1.5 Deprecate 舊 signaling classes

| 檔案 | 變更 |
|------|------|
| `signaling/WhepSignaling.kt` | `@Deprecated("Use WhepSignalingAdapter", replaceWith = ...)` |
| `signaling/WhepSignaling.kt` 中的 `WhipSignaling` | `@Deprecated("Use WhipSignalingAdapter", replaceWith = ...)` |
| `signaling/WebSocketSignaling.kt` | `@Deprecated("Use SignalingAdapter. Built-in WebSocketSignalingAdapter planned for v2.1")` — **保留 code 不刪除** |

### 1.6 `gradle/libs.versions.toml` 新增 dependency

```toml
[libraries]
ktor-client-plugins = { module = "io.ktor:ktor-client-plugins", version.ref = "ktor" }
```

> 僅 `SignalingAuth.CookieStorage` 使用，其他 auth type 不引入。

**Phase 1 成果**: 3 個新檔案 (commonMain)、3 個修改檔案  
**驗證**: `./gradlew build` 全平台通過、舊 API 正常運作

---

## Phase 2: Config Cleanup

> 可與 Phase 1 平行開發

### 2.1 統一 `RetryConfig`

| 檔案 | 變更 |
|------|------|
| `audio/AudioPushConfig.kt` | `AudioRetryConfig` 加 `@Deprecated("Use RetryConfig", replaceWith = ReplaceWith("RetryConfig"))` |
| `audio/AudioPushConfig.kt` | `AudioPushConfig.retryConfig` 型別改為 `com.syncrobotic.webrtc.config.RetryConfig` |

### 2.2 精簡 `WebRTCConfig`

**檔案**: `config/StreamConfig.kt`

Deprecated 欄位（保留 default 值，不 break 編譯）:

| 欄位 | 處理 |
|------|------|
| `signalingType` | `@Deprecated` — 由 SignalingAdapter 取代 |
| `wsConfig` | `@Deprecated` — 由 WebSocketSignalingAdapter 取代 |
| `whepEnabled` / `whipEnabled` | `@Deprecated` — 由 Session type 取代 |

保留欄位：`iceServers`, `iceMode`, `iceGatheringTimeoutMs`, `iceTransportPolicy`, `bundlePolicy`, `rtcpMuxPolicy`

### 2.3 Deprecate 多餘 config types

| 型別 | 位置 | 處理 |
|------|------|------|
| `StreamProtocol` | `config/StreamConfig.kt` | `@Deprecated("Library only supports WebRTC")` |
| `ServerEndpoints` | `config/StreamConfig.kt` | `@Deprecated("Use SignalingAdapter with direct URL")` |
| `StreamDirection` | `config/StreamConfig.kt` | `@Deprecated("Determined by WHEP vs WHIP")` |
| `SignalingType` | `config/StreamConfig.kt` | `@Deprecated("Use SignalingAdapter")` |
| `WebSocketSignalingConfig` | `config/StreamConfig.kt` | `@Deprecated` |
| `BidirectionalConfig` | `config/BidirectionalConfig.kt` | `@Deprecated("Compose VideoRenderer + AudioPushPlayer separately")` |

### 2.4 精簡 `AudioPushConfig`

**檔案**: `audio/AudioPushConfig.kt`

- 移除 `whipUrl`（改由 SignalingAdapter 提供）
- 保留音訊設定：echo cancellation, noise suppression, AGC
- 新增無 URL 的 constructor，舊 constructor `@Deprecated`

**Phase 2 成果**: 修改 3 個檔案  
**驗證**: `./gradlew build`、舊 config 仍可用

---

## Phase 3: Session API

> 核心 — 依賴 Phase 1 + Phase 2 完成

### 3.1 建立 `SessionState`

**新建**: `src/commonMain/kotlin/com/syncrobotic/webrtc/session/SessionState.kt`

```kotlin
sealed class SessionState {
    data object Idle : SessionState()
    data object Connecting : SessionState()
    data object Connected : SessionState()
    data class Reconnecting(val attempt: Int, val maxAttempts: Int) : SessionState()
    data class Error(val message: String, val cause: Throwable? = null, val isRetryable: Boolean = false) : SessionState()
    data object Closed : SessionState()
}
```

### 3.2 建立 `WhepSession` (expect + 5 actual)

**新建 expect**: `src/commonMain/kotlin/com/syncrobotic/webrtc/session/WhepSession.kt`

```kotlin
expect class WhepSession(
    signaling: SignalingAdapter,
    config: WebRTCConfig = WebRTCConfig.DEFAULT,
    retryConfig: RetryConfig = RetryConfig.DEFAULT
) {
    val state: StateFlow<SessionState>
    val stats: StateFlow<WebRTCStats?>
    suspend fun connect()
    fun createDataChannel(config: DataChannelConfig): DataChannel?
    fun setAudioEnabled(enabled: Boolean)
    fun setSpeakerphoneEnabled(enabled: Boolean)
    fun close()
}
```

**新建 actual** (5 個):

| 檔案 | 說明 |
|------|------|
| `src/androidMain/.../session/WhepSession.android.kt` | 內部使用 `WebRTCClient` + Google WebRTC SDK。Android actual 額外提供 `Context` secondary constructor |
| `src/iosMain/.../session/WhepSession.ios.kt` | 內部使用 `WebRTCClient` + GoogleWebRTC CocoaPod |
| `src/jvmMain/.../session/WhepSession.jvm.kt` | 內部使用 `WebRTCClient` + webrtc-java |
| `src/jsMain/.../session/WhepSession.js.kt` | 內部使用原生 RTCPeerConnection |
| `src/wasmJsMain/.../session/WhepSession.wasmJs.kt` | 內部使用原生 RTCPeerConnection |

**核心實作邏輯** (各 platform actual 內部):
```
connect() → signaling.sendOffer(localSdp)
         → webrtcClient.setRemoteAnswer(result.sdpAnswer)
         → ICE gathering + signaling.sendIceCandidate()
         → state = Connected
         → error? → StreamRetryHandler → Reconnecting → retry
```

### 3.3 建立 `WhipSession` (expect + 5 actual)

同 3.2 結構，但多了 audio capture 邏輯：

```kotlin
expect class WhipSession(
    signaling: SignalingAdapter,
    config: WebRTCConfig = WebRTCConfig.DEFAULT,
    audioConfig: AudioPushConfig = AudioPushConfig.DEFAULT,
    retryConfig: RetryConfig = RetryConfig.DEFAULT
) {
    val state: StateFlow<SessionState>
    val stats: StateFlow<WebRTCStats?>
    suspend fun connect()
    fun createDataChannel(config: DataChannelConfig): DataChannel?
    fun setMuted(muted: Boolean)
    fun toggleMute()
    fun close()
}
```

**Phase 3 成果**: 1 sealed class + 2 expect + 10 actual = **13 個新檔案**  
**驗證**: `./gradlew build`、手動測試 WHEP connect + WHIP audio push + DataChannel

---

## Phase 4: Composable Refactoring

> 依賴 Phase 3 完成

### 4.1 新 `VideoRenderer` overload

**修改**: `src/commonMain/.../ui/VideoRenderer.kt` + 5 個 platform actual

```kotlin
// NEW — v2 API (returns controller)
@Composable
expect fun VideoRenderer(
    session: WhepSession,
    modifier: Modifier = Modifier,
    onStateChange: ((PlayerState) -> Unit)? = null,
    onEvent: ((PlayerEvent) -> Unit)? = null,
): VideoPlayerController

// OLD — v1 API (deprecated, still functional)
@Deprecated("Use VideoRenderer(session: WhepSession, ...)")
@Composable
expect fun VideoRenderer(config: StreamConfig, ...)
```

### 4.2 新 `AudioPushPlayer` overload

**修改**: `src/commonMain/.../audio/AudioPushPlayer.kt` + 5 個 platform actual

```kotlin
// NEW
@Composable
expect fun AudioPushPlayer(
    session: WhipSession,
    autoStart: Boolean = true,
    onStateChange: ((AudioPushState) -> Unit)? = null,
): AudioPushController
```

### 4.3 Deprecate `BidirectionalPlayer`

| 檔案 | 變更 |
|------|------|
| `commonMain/.../ui/BidirectionalPlayer.kt` | `@Deprecated("Use VideoRenderer + AudioPushPlayer separately")` |
| `androidMain/.../ui/BidirectionalPlayer.android.kt` | 同上 |
| `iosMain/.../ui/BidirectionalPlayer.ios.kt` | 同上 |
| `jvmMain/.../ui/BidirectionalPlayer.jvm.kt` | 同上 |
| `jsMain/.../ui/BidirectionalPlayer.js.kt` | 同上 |
| `wasmJsMain/.../ui/BidirectionalPlayer.wasmJs.kt` | 同上 |

### 4.4 Deprecate `WebRTCClient`

| 檔案 | 變更 |
|------|------|
| `commonMain/.../WebRTCClient.kt` | `@Deprecated("Use WhepSession/WhipSession. Will become internal in v3.0")` |

**Phase 4 成果**: 修改 2 expect + 10 actual + 7 deprecated  
**驗證**: 新舊 VideoRenderer 皆可用、BidirectionalPlayer 顯示 deprecation warning

---

## Phase 5: Consistency Cleanup

> 可與 Phase 4 平行

### 5.1 `onError` 簽名修正

**檔案**: `WebRTCListener` (各 platform)

```kotlin
// NEW
fun onError(error: Throwable) { onError(error.message ?: "Unknown error") }

// OLD
@Deprecated("Use onError(Throwable)")
fun onError(error: String)
```

### 5.2 lifecycle 方法命名統一

| 類別 | 變更 |
|------|------|
| `AudioPushClient` | `release()` → `@Deprecated`，新增 `close()` |
| 所有 Closeable 資源 | 統一使用 `close()` |

**Phase 5 成果**: 修改 2-3 個介面檔案  
**驗證**: `./gradlew build`

---

## File Inventory

### 新建檔案 (22 個)

| # | 檔案 | Phase |
|---|------|-------|
| 1 | `commonMain/.../signaling/SignalingAdapter.kt` | 1 |
| 2 | `commonMain/.../signaling/WhepSignalingAdapter.kt` | 1 |
| 3 | `commonMain/.../signaling/WhipSignalingAdapter.kt` | 1 |
| 4 | `commonMain/.../session/SessionState.kt` | 3 |
| 5 | `commonMain/.../session/WhepSession.kt` (expect) | 3 |
| 6 | `commonMain/.../session/WhipSession.kt` (expect) | 3 |
| 7 | `androidMain/.../session/WhepSession.android.kt` | 3 |
| 8 | `androidMain/.../session/WhipSession.android.kt` | 3 |
| 9 | `iosMain/.../session/WhepSession.ios.kt` | 3 |
| 10 | `iosMain/.../session/WhipSession.ios.kt` | 3 |
| 11 | `jvmMain/.../session/WhepSession.jvm.kt` | 3 |
| 12 | `jvmMain/.../session/WhipSession.jvm.kt` | 3 |
| 13 | `jsMain/.../session/WhepSession.js.kt` | 3 |
| 14 | `jsMain/.../session/WhipSession.js.kt` | 3 |
| 15 | `wasmJsMain/.../session/WhepSession.wasmJs.kt` | 3 |
| 16 | `wasmJsMain/.../session/WhipSession.wasmJs.kt` | 3 |
| 17-21 | `{platform}Main/.../ui/VideoRenderer` 新 actual overload (×5) | 4 |
| 22 | 各 platform `AudioPushPlayer` 新 actual overload 已在既有檔案中 | 4 |

### 修改檔案 (17+ 個)

| 檔案 | Phase | 變更類型 |
|------|-------|---------|
| `signaling/WhepSignaling.kt` | 1 | @Deprecated |
| `signaling/WebSocketSignaling.kt` | 1 | @Deprecated（保留 code） |
| `config/StreamConfig.kt` | 2 | Deprecate 6 個 type + WebRTCConfig 欄位 |
| `config/BidirectionalConfig.kt` | 2 | @Deprecated |
| `audio/AudioPushConfig.kt` | 2 | 移除 whipUrl、deprecate AudioRetryConfig |
| `audio/AudioPushController.kt` | 4 | 新增 createDataChannel() |
| `audio/AudioPushClient.kt` | 5 | 新增 close()、deprecate release() |
| `audio/AudioPushPlayer.kt` (expect) | 4 | 新增 WhipSession overload |
| `ui/VideoRenderer.kt` (expect) | 4 | 新增 WhepSession overload |
| `ui/BidirectionalPlayer.kt` (×6) | 4 | @Deprecated |
| `WebRTCClient.kt` | 4 | @Deprecated |
| `gradle/libs.versions.toml` | 1 | 新增 ktor-client-plugins |

### 不動的檔案

- `datachannel/DataChannel.kt` + 所有 platform actual
- `datachannel/DataChannelListener.kt`、`DataChannelConfig.kt`
- `audio/AudioPushState.kt`
- `ui/PlayerState.kt`
- `config/StreamRetryHandler.kt`
- 所有 `PeerConnectionFactoryManager.kt`
- 所有 `WebRTCClient.{platform}.kt` platform 實作

---

## Execution Order

```
Week 1 ─── Phase 1 (SignalingAdapter) + Phase 2 (Config)  ← 平行
            │
            ├── Checkpoint: ./gradlew build 全通過
            │   舊 API 仍正常、新 SignalingAdapter 可單獨使用
            │
Week 2 ─── Phase 3 (Session API)
            │
            ├── Checkpoint: ./gradlew build
            │   WhepSession.connect() 手動測試 WHEP
            │   WhipSession.connect() 手動測試 WHIP
            │   DataChannel 通過 Session 建立並收發
            │
Week 3 ─── Phase 4 (Composable) + Phase 5 (Cleanup)  ← 平行
            │
            ├── Checkpoint: ./gradlew build + ./gradlew jvmTest
            │   新舊 VideoRenderer 皆可用
            │   BidirectionalPlayer 顯示 deprecation
            │
Week 4 ─── Integration Testing + README_V2 校驗
            │
            └── Release v2.0
```

---

## Verification Checklist

### 每個 Phase 完成後

- [ ] `./gradlew build` — 5 個 platform 皆編譯通過
- [ ] `./gradlew jvmTest` — 既有 unit test 通過
- [ ] 所有 deprecated API 仍可編譯、正常執行
- [ ] 新 API 可獨立使用（不需引用 deprecated API）

### v2.0 Release 前

- [ ] WHEP 連線：`WhepSignalingAdapter` + `WhepSession` + `VideoRenderer(session)` 
- [ ] WHIP 音訊推流：`WhipSignalingAdapter` + `WhipSession` + `AudioPushPlayer(session)`
- [ ] DataChannel：`session.createDataChannel()` 收發訊息
- [ ] JWT 認證：`SignalingAuth.Bearer` 帶 token
- [ ] Cookie 認證：`SignalingAuth.Cookies` 手動 cookie
- [ ] CookieStorage：`SignalingAuth.CookieStorage` 共享 cookie jar
- [ ] Custom headers：`SignalingAuth.Custom` API key
- [ ] 無認證：`SignalingAuth.None` 公開 server
- [ ] 自訂 HttpClient 注入：不衝突
- [ ] 多 Session 並存：無 shared state 衝突
- [ ] Android：EglContext 隔離正常
- [ ] iOS：physical device 連線正常
- [ ] JVM：PeerConnectionFactory ref-count 正常
- [ ] 自訂 SignalingAdapter 實作可編譯
- [ ] `README_V2.md` 所有範例程式碼可編譯

---

## Risk & Mitigation

| 風險 | 影響 | 對策 |
|------|------|------|
| Session actual 實作過於複雜 | Phase 3 延期 | 先做 Android + JVM，iOS 平行，JS/WasmJS 最後（stub 可接受） |
| VideoRenderer 新 overload 與舊 signature 衝突 | 編譯錯誤 | 用不同參數名、加 `@JvmName` 區分 |
| `CookieStorage` 的 Ktor plugin 與用戶 httpClient 衝突 | Runtime crash | 文件明確標示、CookieStorage 時不允許同時注入有 HttpCookies 的 httpClient |
| JS/WasmJS platform 功能不完整 | 用戶預期落差 | README 標示 JS/WasmJS 為 experimental，minimal stub |
| 舊 API 用戶不遷移 | 維護負擔 | v2.0 compiler warning、v2.1 再提醒、v3.0 刪除 |
