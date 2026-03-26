---
description: "Use when working on WebRTC connection logic, signaling (WHEP/WHIP/WebSocket), PeerConnection management, ICE candidates, SDP negotiation, DataChannel, or audio/video streaming."
---
# WebRTC Development Guidelines

## PeerConnection Lifecycle

1. 建立 `WebRTCClient` 實例
2. 透過 Signaling（WHEP/WHIP/WebSocket）交換 SDP offer/answer
3. 處理 ICE candidates（支援 trickle ICE）
4. 連線建立後接收/發送媒體串流
5. 關閉時呼叫 `close()` 釋放所有資源

## PeerConnectionFactory 策略

- **Android**：每個連線建立獨立 Factory（避免 EglContext 衝突），使用 `synchronized` 保護
- **iOS**：每個連線獨立 Factory，共享 `RTCAudioSession` 用 lock 保護
- **JVM**：共享單一 Factory，用 reference counting 管理生命週期（refCount 歸零才 dispose）

## Signaling

- `WhepSignaling`：HTTP POST 交換 SDP，PATCH 傳送 ICE candidates，用於接收串流
- `WhipSignaling`：HTTP POST 交換 SDP，用於發送串流（音訊推送）
- `WebSocketSignaling`：WebSocket 連線，支援心跳機制，用於自訂後端
- 所有 signaling 類別都在 `src/commonMain/kotlin/com/syncrobotic/webrtc/signaling/`

## Auto-Reconnect

- `StreamRetryHandler` 提供指數退避重試（預設 5 次，1s → 45s）
- `RetryConfig` 可自訂重試參數
- 重連邏輯應在 state machine 的 `Error` / `Disconnected` 狀態觸發

## DataChannel

- 支援 text（`send(String)`）和 binary（`sendBinary(ByteArray)`）
- `DataChannelConfig` 可設定 ordered/unordered、reliable/unreliable
- 透過 `DataChannelListener` 接收回呼事件

## Error Handling

- 連線/串流錯誤用 sealed class 狀態表達（`PlayerState.Error(message, cause, isRetryable)`）
- Signaling 層使用自訂 Exception：`WhepException`、`WhipException`、`WebSocketSignalingException`
- 重試耗盡時拋出 `StreamRetryExhaustedException`
- 新增錯誤處理時，優先使用 state machine 的 Error 狀態而非直接 throw
