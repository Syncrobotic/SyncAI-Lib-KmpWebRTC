# SyncAI-Lib-KmpWebRTC SDK Roadmap

> 📅 Created: 2026-03-26  
> 📌 Status: Planning

---

## 1. SDK 定位與目標用戶

### 1.1 核心定位

**一句話描述**：Kotlin Multiplatform WebRTC SDK，專為 IoT/機器人控制場景設計，讓開發者無需處理 WebRTC 底層複雜度即可實現低延遲串流與雙向通訊。

### 1.2 目標用戶

| 用戶類型 | 使用場景 | 優先級 |
|----------|----------|--------|
| **IoT/機器人開發者** | 遠端控制、多攝影機監控、雙向語音 | 🔴 P0 |
| **智慧家庭應用** | 門鈴視訊、監控攝影機即時串流 | 🟡 P1 |
| **教育/培訓平台** | 遠端實驗室、設備操作教學 | 🟡 P1 |
| **工業自動化** | AGV 監控、產線視訊即時回傳 | 🟢 P2 |

### 1.3 與競品差異

| 特點 | SyncAI-Lib-KmpWebRTC | WebRTC Native SDK | 其他 KMP 方案 |
|------|--------------------|--------------------|---------------|
| 跨平台 | ✅ Android/iOS/JVM (Web 開發中) | ❌ 各平台分開 | 部分支援 |
| 學習曲線 | 低 (Composable API) | 高 | 中 |
| 低延遲優化 | ⏳ Preset 規劃中 | 需自行配置 | 不一定有 |
| Signaling 內建 | ✅ WHEP/WHIP/WebSocket | ❌ 需自行實作 | 部分 |
| IoT 場景優化 | ✅ 多視訊源、DataChannel | ❌ 通用設計 | ❌ |

---

## 2. 功能規劃

### 2.1 現有功能 (v1.x)

| 功能 | 平台支援 | 狀態 |
|------|----------|------|
| Video Receive (WHEP) | Android/iOS/JVM | ✅ Verified |
| Audio Send (WHIP) | Android/iOS/JVM | ✅ Verified |
| Audio Receive | Android/iOS/JVM | ✅ Verified |
| DataChannel (Text) | Android/iOS/JVM | ✅ Implemented |
| DataChannel (Binary) | Android/iOS/JVM | ✅ Implemented |
| VideoRenderer Composable | Android/iOS/JVM | ✅ Available |
| 多 VideoRenderer 同時使用 | Android/iOS/JVM | ✅ Supported (每個實例獨立 PeerConnection，無共享狀態衝突) |
| AudioPushPlayer Composable | Android/iOS/JVM/JS | ✅ Available |
| BidirectionalPlayer | Android/iOS/JVM | ✅ Available |
| Web (JS) | Browser | ⚠️ Stub (VideoRenderer 未實作，僅 WebRTCClient + AudioPush 基本可用) |
| Web (WasmJS) | Browser | ❌ 未完成 (缺少 VideoRenderer、AudioPushPlayer) |
| 自動重連 | All | ✅ Available |
| 連線統計 (Stats) | All | ✅ Implemented (需手動呼叫 getStats()，非 StateFlow) |

### 2.2 Phase 1: IoT 核心功能

**預計時程**：2-3 週

| 功能 | 說明 | 平台 | 優先級 |
|------|------|------|--------|
| **VideoPushPlayer** | 從手機攝影機推送視訊 (WHIP) | Android/iOS | 🔴 P0 |
| **MultiStreamManager** | 便利 API 管理多個視訊來源 (注意：多 VideoRenderer 已可手動組合使用) | All | 🟡 P1 |
| **低延遲 Preset** | 預設配置: UDP only, 停用 jitter buffer | All | 🔴 P0 |
| **網路品質即時監控** | RTT/丟包率/Bitrate StateFlow | All | 🔴 P0 |
| **CameraCapturer** | 跨平台攝影機擷取抽象 | Android/iOS | 🔴 P0 |

#### 2.2.1 VideoPushPlayer API 設計

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

#### 2.2.2 MultiStreamManager API 設計

```kotlin
class MultiStreamManager {
    val streams: StateFlow<Map<String, StreamState>>
    
    fun addStream(id: String, config: StreamConfig)
    fun removeStream(id: String)
    fun reconnect(id: String)
    fun reconnectAll()
    
    // 取得特定串流的 controller
    fun getController(id: String): VideoPlayerController?
    
    // 整體統計
    val aggregatedStats: StateFlow<AggregatedStats>
}

// Compose 整合
@Composable
fun MultiVideoGrid(
    manager: MultiStreamManager,
    layout: GridLayout = GridLayout.AUTO,
    modifier: Modifier = Modifier
)
```

#### 2.2.3 低延遲配置

```kotlin
object WebRTCConfig {
    // 現有
    val DEFAULT: WebRTCConfig
    val SENDER: WebRTCConfig
    
    // 新增
    val LOW_LATENCY: WebRTCConfig = WebRTCConfig(
        iceTransportPolicy = IceTransportPolicy.ALL,
        bundlePolicy = BundlePolicy.MAX_BUNDLE,
        // 低延遲專用設定
        jitterBufferMinMs = 0,
        jitterBufferMaxMs = 100,
        preferredVideoCodec = VideoCodec.H264_BASELINE,
        enableFec = false,        // 停用 FEC 減少延遲
        enableNack = true,        // 保留 NACK 處理丟包
        enableDtx = true          // DTX 節省頻寬
    )
}
```

### 2.3 Phase 2: 開發者體驗

**預計時程**：1-2 週

| 功能 | 說明 | 優先級 |
|------|------|--------|
| **Signaling 抽象介面** | 允許用戶實作自定義 signaling | 🔴 P0 |
| **結構化錯誤系統** | 錯誤碼 + 可恢復性標記 | 🟡 P1 |
| **可插拔 Logger** | 用戶注入自定義 logger | 🟡 P1 |
| **自動重連策略增強** | 可配置 backoff 策略 | 🟡 P1 |
| **Codec 偏好設定** | 優先順序: H264 > VP8 > VP9 | 🟢 P2 |

#### 2.3.1 Signaling 抽象介面

```kotlin
// 用戶可實作自己的 signaling
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

// 使用方式
val customSignaling = MyFirebaseSignaling(roomId = "room-123")

VideoRenderer(
    signaling = customSignaling,
    modifier = Modifier.fillMaxSize()
)
```

#### 2.3.2 結構化錯誤系統

```kotlin
sealed class WebRTCError(
    val code: String,
    val message: String,
    val isRecoverable: Boolean,
    val cause: Throwable? = null
) {
    // 連線錯誤 (WC = WebRTC Connection)
    class ConnectionTimeout : WebRTCError("WC001", "連線逾時", true)
    class IceNegotiationFailed : WebRTCError("WC002", "ICE 協商失敗", true)
    class DtlsHandshakeFailed : WebRTCError("WC003", "DTLS 握手失敗", true)
    class ConnectionLost : WebRTCError("WC004", "連線中斷", true)
    
    // Signaling 錯誤 (WS = WebRTC Signaling)
    class SignalingTimeout : WebRTCError("WS001", "Signaling 逾時", true)
    class InvalidSdp(details: String) : WebRTCError("WS002", "無效的 SDP: $details", false)
    class SignalingServerError(status: Int) : WebRTCError("WS003", "伺服器錯誤: $status", true)
    
    // DataChannel 錯誤 (WD = WebRTC DataChannel)
    class DataChannelNotOpen : WebRTCError("WD001", "DataChannel 未開啟", false)
    class MessageTooLarge(size: Int) : WebRTCError("WD002", "訊息過大: $size bytes", false)
    
    // 媒體錯誤 (WM = WebRTC Media)
    class CameraAccessDenied : WebRTCError("WM001", "攝影機權限被拒", false)
    class MicrophoneAccessDenied : WebRTCError("WM002", "麥克風權限被拒", false)
    class CodecNotSupported(codec: String) : WebRTCError("WM003", "不支援的編碼: $codec", false)
}
```

#### 2.3.3 可插拔 Logger

```kotlin
interface WebRTCLogger {
    fun verbose(tag: String, message: String)
    fun debug(tag: String, message: String)
    fun info(tag: String, message: String)
    fun warn(tag: String, message: String, error: Throwable? = null)
    fun error(tag: String, message: String, error: Throwable)
}

// 全域設定
WebRTCClient.setLogger(object : WebRTCLogger {
    override fun debug(tag: String, message: String) {
        Timber.tag(tag).d(message)  // 整合 Timber
    }
    // ...
})

// 或使用內建 logger level
WebRTCClient.setLogLevel(LogLevel.DEBUG)
```

### 2.4 Phase 3: 品質與開源準備

**預計時程**：2 週

| 項目 | 說明 | 優先級 |
|------|------|--------|
| **Unit Tests** | 核心邏輯測試 (Signaling, Config, State) | 🔴 P0 |
| **Integration Tests** | 端對端連線測試 | 🟡 P1 |
| **API 文件** | Dokka 生成 + GitHub Pages | 🔴 P0 |
| **範例專案** | samples/robot-control | 🔴 P0 |
| **社群模板** | Issue/PR templates | 🟡 P1 |
| **CONTRIBUTING.md** | 貢獻指南 | 🟡 P1 |
| **ARCHITECTURE.md** | 架構設計文件 | 🟢 P2 |

### 2.5 Phase 4: 進階功能 (Future)

| 功能 | 說明 | 使用場景 |
|------|------|----------|
| **螢幕共享** | Desktop/Mobile 螢幕擷取 | 遠端協作 |
| **本地錄製** | 錄製串流到檔案 | 存檔/回放 |
| **Simulcast** | 同時發送多種解析度 | 多人會議 (SFU) |
| **E2E 加密** | Insertable Streams | 隱私敏感應用 |
| **AR/VR 支援** | 360 視訊、立體音效 | XR 應用 |

---

## 3. 效能目標

### 3.1 延遲目標

| 場景 | 目標延遲 | 說明 |
|------|----------|------|
| **本地網路 (LAN)** | < 100ms | WiFi 直連 |
| **一般網路** | < 300ms | 透過 TURN |
| **跨國連線** | < 500ms | CDN/Edge Server |

### 3.2 資源使用目標

| 指標 | Android | iOS | JVM |
|------|---------|-----|-----|
| **記憶體 (單串流)** | < 50MB | < 50MB | < 100MB |
| **記憶體 (3 串流)** | < 150MB | < 150MB | < 250MB |
| **CPU (1080p 接收)** | < 15% | < 15% | < 10% |
| **CPU (720p 發送)** | < 20% | < 20% | < 15% |
| **APK/IPA 增量** | < 5MB | < 5MB | N/A |

### 3.3 可靠性目標

| 指標 | 目標 |
|------|------|
| **自動重連成功率** | > 95% (網路恢復後) |
| **連線建立時間** | < 2 秒 (LAN) / < 5 秒 (WAN) |
| **DataChannel 送達率** | 99.9% (reliable mode) |

---

## 4. 專案結構規劃

### 4.1 目前結構

```
SyncAI-Lib-KmpWebRTC/
├── src/
│   ├── commonMain/      # 共用程式碼
│   ├── androidMain/     # Android 實作
│   ├── iosMain/         # iOS 實作
│   ├── jvmMain/         # JVM 實作
│   ├── jsMain/          # JS 實作
│   └── wasmJsMain/      # WasmJS 實作
└── build.gradle.kts
```

### 4.2 規劃結構 (模組化)

```
SyncAI-Lib-KmpWebRTC/
├── core/                       # 核心 API + 抽象
│   └── src/commonMain/
│       ├── WebRTCClient.kt
│       ├── signaling/SignalingClient.kt  (介面)
│       └── error/WebRTCError.kt
├── signaling-whep/             # WHEP 實作 (可選)
├── signaling-whip/             # WHIP 實作 (可選)
├── signaling-websocket/        # WebSocket 實作 (可選)
├── compose-ui/                 # Compose UI 元件 (可選)
│   └── src/commonMain/
│       ├── VideoRenderer.kt
│       ├── AudioPushPlayer.kt
│       ├── VideoPushPlayer.kt
│       └── MultiVideoGrid.kt
├── samples/                    # 範例專案
│   ├── minimal-android/
│   ├── minimal-ios/
│   ├── minimal-desktop/
│   └── robot-control/          # 完整範例
├── docs/
│   ├── ROADMAP.md              # 本文件
│   ├── ARCHITECTURE.md
│   ├── CONTRIBUTING.md
│   └── api/                    # Dokka 生成
└── .github/
    ├── ISSUE_TEMPLATE/
    ├── PULL_REQUEST_TEMPLATE.md
    └── workflows/
```

---

## 5. 里程碑

| 版本 | 預計時間 | 主要內容 |
|------|----------|----------|
| **v1.5.0** | +3 週 | VideoPushPlayer, MultiStreamManager, 低延遲 preset |
| **v1.6.0** | +5 週 | Signaling 抽象介面, 結構化錯誤, Logger |
| **v2.0.0** | +7 週 | 模組化拆分, API 穩定, 完整文件, 範例專案 |
| **v2.1.0** | Future | 螢幕共享 |
| **v2.2.0** | Future | 本地錄製 |

---

## 6. 待決定事項

- [ ] 模組化拆分的時機？(v2.0 或更早)
- [ ] 是否需要支援 Simulcast？
- [ ] Web 平台 (JS/WasmJS) 的優先級？(目前 JS 為 Stub、WasmJS 未完成)
- [ ] 是否需要提供後端 signaling server 參考實作？
- [ ] License 是否維持 MIT？
- [x] 多 VideoRenderer 同時使用？→ **已支援**，架構上每個實例獨立 PeerConnection，無共享狀態衝突

---

## 7. 參考資源
- [WebRTC Standard](https://www.w3.org/TR/webrtc/)
- [WHEP Draft](https://www.ietf.org/archive/id/draft-murillo-whep-01.html)
- [WHIP Draft](https://www.ietf.org/archive/id/draft-ietf-wish-whip-01.html)
