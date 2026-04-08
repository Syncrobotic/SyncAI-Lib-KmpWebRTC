# Level 3 Manual Test Guide — SyncAI-Lib-KmpWebRTC

> 45 項手動整合測試的執行指南（排除 S-4）

## 測試進度總覽

| 群組 | 通過 | 總數 | 狀態 |
|------|------|------|------|
| S-1a MediaMTX ↔ App | 5 | 5 | ✅ 全通 |
| S-1b IoT → MediaMTX → App | 6 | 6 | ✅ 全通 |
| S-2 BE Signaling Proxy | 6 | 6 | ✅ 全通 |
| S-3 Pion SFU | 4 | 4 | ✅ 全通 |
| S-5 Dual Session IoT | 3 | 3 | ✅ 全通 |
| C-1 Bidirectional Call | 0 | 5 | ⏳ 需 server 支援雙向 PeerConnection |
| C-2 External IoT | 5 | 5 | ✅ 全通 |
| C-3 Multiple VideoRenderer | 4 | 4 | ✅ 全通 |
| C-4 1-to-N | 4 | 4 | ✅ 全通 |
| C-5 DataChannel | 5 | 5 | ✅ 全通 |
| **合計** | **42** | **47** | **~89%** |

> 圖例：✅ Pass　⚠️ Partial　⏳ 待測（需新功能）　⬜ 待測

---

## Table of Contents

- [Prerequisites](#prerequisites)
- [Infrastructure Setup](#infrastructure-setup)
- [S-1: MediaMTX Server Tests](#s-1-mediamtx-server-tests)
- [S-2: BE Signaling Proxy Tests](#s-2-be-signaling-proxy-tests)
- [S-3: Pion SFU Tests](#s-3-pion-sfu-tests)
- [S-5: Dual Session IoT Tests](#s-5-dual-session-iot-tests)
- [C-1: Bidirectional Call Tests](#c-1-bidirectional-call-tests)
- [C-2: External IoT Tests](#c-2-external-iot-tests)
- [C-3: Multiple VideoRenderer Tests](#c-3-multiple-videorenderer-tests)
- [C-4: 1-to-N Tests](#c-4-1-to-n-tests)
- [C-5: DataChannel Tests](#c-5-datachannel-tests)
- [Teardown](#teardown)

---

## Prerequisites

### 必備軟體

```bash
# Docker (Docker Desktop 或 Colima)
docker --version          # 需要 Docker Engine 24+
docker compose version    # 需要 Docker Compose v2

# 如果用 Colima (macOS)
colima status
```

### 測試裝置

| 裝置 | 角色 | 用途 |
|------|------|------|
| Android 實機/模擬器 | App A | 主要測試裝置 |
| iOS 實機 | App B | 主要測試裝置 |
| JVM Desktop (本機) | App C | 多觀眾測試的第 3+ viewer |

### 測試 App

在 Android/iOS/Desktop 上安裝使用 Library 的測試 App，需要支援：
- 輸入 WHEP/WHIP URL 進行連線
- 切換 `MediaConfig` 模式 (RECEIVE_VIDEO, SEND_VIDEO, SEND_AUDIO, BIDIRECTIONAL_AUDIO, VIDEO_CALL)
- 顯示 `SessionState` 和 `WebRTCStats`
- DataChannel 發送/接收 UI
- 多 VideoRenderer 顯示

---

## Infrastructure Setup

### Step 1: 啟動 Docker 環境

```bash
cd test-infra/

# 一鍵啟動所有服務
docker compose up -d

# 確認所有服務正常
docker compose ps
```

預期輸出：
```
NAME                   STATUS    PORTS
mediamtx               running   0.0.0.0:8554->8554, 0.0.0.0:8889->8889
pion-iot               running   0.0.0.0:8080->8080
ffmpeg-source          running   (推流中，無對外 port)
ffmpeg-pion-source     running   (推流中，無對外 port)
ffmpeg-pion-dc-source  running   (推流中，無對外 port)
```

> **注意**：`ffmpeg-pion-source` 和 `ffmpeg-pion-dc-source` 需等 `pion-iot` healthcheck 通過才會啟動（約 5~10 秒）。

### Step 2: 確認服務健康

```bash
# MediaMTX 健康檢查
curl http://localhost:9997/v3/config/global/get

# Pion IoT 健康檢查
curl http://localhost:8080/health

# 確認 FFmpeg 正在推流到 MediaMTX
curl http://localhost:9997/v3/paths/list

# 確認 FFmpeg 有成功推流到 Pion (應看到 WHIP session 記錄)
docker logs ffmpeg-pion-source 2>&1 | tail -5
docker logs pion-iot 2>&1 | grep "whip" | tail -10
```

> **Pion 影像來源說明**：
> - `ffmpeg-pion-source` 推 H.264 到 `pion-iot:8080/iot-camera/whip`
> - `ffmpeg-pion-dc-source` 推 H.264 到 `pion-iot:8080/dc-test/whip`
> - 兩者啟動後，Pion 變成 SFU 模式，WHEP 訂閱者會收到真實的 FFmpeg 測試畫面
> - 若 FFmpeg 尚未啟動，Pion 會 fallback 到內建 test pattern（VP8 bitstream 無效 → 黑畫面）

### Step 3: 啟動 SignalingProxyServer (JVM in-process)

```bash
# 在專案根目錄
cd SyncAI-Lib-KmpWebRTC/

# 啟動 Signaling Proxy（跑在 JVM 裡，用於 S-2, S-5 測試）
./gradlew runSignalingProxy
# ./gradlew jvmTest --tests "com.syncrobotic.webrtc.level3.server.SignalingProxyServerTest"
```

或者在測試 App 中直接啟動 proxy（如果 App 內建此功能）。

### 確認各服務的 URL

| 服務 | URL | 用途 |
|------|-----|------|
| MediaMTX WHEP | `http://<HOST>:8889/{stream}/whep` | 收看串流 |
| MediaMTX WHIP | `http://<HOST>:8889/{stream}/whip` | 推送串流 |
| MediaMTX RTSP | `rtsp://<HOST>:8554/{stream}` | RTSP 串流 |
| Pion WHEP | `http://<HOST>:8080/{stream}/whep` | 從 Pion 收看 |
| Pion WHIP | `http://<HOST>:8080/{stream}/whip` | 推送到 Pion |
| SignalingProxy | `http://<HOST>:9090/api/v1/devices/{deviceId}/offer` | BE 代理 |

> **`<HOST>`**: 如果 App 在實機上測試，需要用電腦的 LAN IP（如 `192.168.x.x`），不能用 `localhost`。
>
> ```bash
> # macOS 查看 LAN IP
> ipconfig getifaddr en0
> ```

---

## S-1: MediaMTX Server Tests

### 前置條件
- MediaMTX Docker running (`docker compose up -d mediamtx`)
- FFmpeg source running (`docker compose up -d ffmpeg-source`)

---

### S-1a: App ↔ MediaMTX ↔ App

#### S1a-01: App WHIP video send

**步驟：**
1. 在 App A 設定：
   - URL: `http://<HOST>:8889/test-video/whip`
   - MediaConfig: `SEND_VIDEO`
2. 點擊連線
3. 確認 SessionState → `Connected`

**驗證：**
- App 顯示 `Connected` 狀態
- MediaMTX 收到串流：`curl http://localhost:9997/v3/paths/list` 應包含 `test-video`
- 或用瀏覽器開啟 `http://<HOST>:8889/test-video/` 確認有畫面

**結果：** ✅ Pass

---

#### S1a-02: App WHIP audio send

**步驟：**
1. 在 App A 設定：
   - URL: `http://<HOST>:8889/test-audio/whip`
   - MediaConfig: `SEND_AUDIO`
2. 點擊連線

**驗證：**
- SessionState → `Connected`
- `curl http://localhost:9997/v3/paths/list` 包含 `test-audio`

**結果：** ✅ Pass

---

#### S1a-03: App WHEP video receive

**前置：** S1a-01 仍在推流（或 FFmpeg 正在推 `iot-camera`）

**步驟：**
1. 在 App B 設定：
   - URL: `http://<HOST>:8889/test-video/whep` (或 `iot-camera/whep`)
   - MediaConfig: `RECEIVE_VIDEO`
2. 點擊連線

**驗證：**
- SessionState → `Connected`
- VideoRenderer 顯示畫面
- WebRTCStats 顯示 audioBitrate/videoBitrate > 0

**結果：** ✅ Pass

---

#### S1a-04: Round-trip (推 + 收同時)

**步驟：**
1. App A: WHIP `SEND_VIDEO` → `http://<HOST>:8889/roundtrip/whip`
2. App B: WHEP `RECEIVE_VIDEO` → `http://<HOST>:8889/roundtrip/whep`
3. 兩者同時連線

**驗證：**
- 兩端都 `Connected`
- App B 看到 App A 的攝影機畫面
- 延遲可接受 (< 500ms)

**結果：** ✅ Pass

---

#### S1a-05: Multiple viewers (3 個觀眾)

**步驟：**
1. App A: WHIP `SEND_VIDEO` → `http://<HOST>:8889/multi-view/whip`
2. App B: WHEP `RECEIVE_VIDEO` → `http://<HOST>:8889/multi-view/whep`
3. App C (JVM Desktop): WHEP `RECEIVE_VIDEO` → `http://<HOST>:8889/multi-view/whep`
4. 瀏覽器: 開啟 `http://<HOST>:8889/multi-view/` (第 3 viewer)

**驗證：**
- App B, App C, 瀏覽器 都看到 App A 的畫面
- App A 的 stats 正常（推流不受多觀眾影響）

**結果：** ✅ Pass

---

### S-1b: IoT (FFmpeg) → MediaMTX → App

#### S1b-01: IoT RTSP 推流

**前置：** `ffmpeg-source` service 已在 docker-compose 中啟動

**步驟：**
1. 確認 FFmpeg 在推流

```bash
curl http://localhost:9997/v3/paths/list
# 應看到 "iot-camera" path
```

**驗證：**
- MediaMTX paths 列表包含 `iot-camera`
- 可選：用 VLC 開啟 `rtsp://<HOST>:8554/iot-camera` 確認

**結果：** ✅ Pass

---

#### S1b-02: App WHEP 收看 IoT 串流

**步驟：**
1. 在 App A 設定：
   - URL: `http://<HOST>:8889/iot-camera/whep`
   - MediaConfig: `RECEIVE_VIDEO`
2. 點擊連線

**驗證：**
- 看到 FFmpeg 測試畫面 (彩色條紋 + 計時器)
- SessionState → `Connected`

**結果：** ✅ Pass

---

#### S1b-03: App 發音訊到 IoT (語音對講)

**步驟：**
1. App A: WHEP 收看 `http://<HOST>:8889/iot-camera/whep` (收視訊)
2. App A (或另一 session): WHIP 推音訊 `http://<HOST>:8889/iot-audio/whip` (發音訊)
3. App B: WHEP 收聽 `http://<HOST>:8889/iot-audio/whep` (模擬 IoT 端收聽)

**驗證：**
- App A 同時收到視訊 + 推送音訊
- App B 收到 App A 的音訊

**結果：** ✅ Pass

---

#### S1b-04: Multiple viewers 收看 IoT

**步驟：**
1. FFmpeg 持續推 `iot-camera`
2. App A: WHEP → `iot-camera/whep`
3. App B: WHEP → `iot-camera/whep`
4. App C (JVM Desktop): WHEP → `iot-camera/whep`

**驗證：**
- 三端都看到 FFmpeg 測試畫面

**結果：** ✅ Pass

---

#### S1b-05: IoT 斷線重連

**步驟：**
1. App A 正在 WHEP 收看 `iot-camera`
2. 停止 FFmpeg：`docker compose stop ffmpeg-source`
3. 等待 5 秒
4. 重啟 FFmpeg：`docker compose start ffmpeg-source`

**驗證：**
- App A 偵測到斷線 → SessionState 變為 `Reconnecting`
- FFmpeg 恢復後 App A 自動重連 → `Connected`
- 畫面恢復

**結果：** ✅ Pass

---

#### S1b-06: MediaMTX 重啟

**步驟：**
1. App A 正在收看某串流
2. 重啟 MediaMTX：`docker compose restart mediamtx`
3. 等待 MediaMTX 啟動完成

**驗證：**
- App A 偵測到斷線 → `Reconnecting`
- MediaMTX 恢復後 App A 自動重連 → `Connected`
- FFmpeg 也重新推流 (或手動重啟 ffmpeg-source)

**結果：** ✅ Pass

---

## S-2: BE Signaling Proxy Tests

### 前置條件
- Pion IoT running (`docker compose up -d pion-iot`)
- SignalingProxyServer running (JVM in-process 或獨立啟動)
- Proxy 已註冊裝置：`device-001` → Pion WHEP/WHIP URL

---

#### S2-01: Signaling proxy basic

**步驟：**
1. 確認 Proxy 已註冊 device-001 指向 Pion：
   ```
   registerDevice("device-001", "http://pion-iot:8080/camera")
   ```
2. 在 App A 設定：
   - URL: `http://<HOST>:9090/api/v1/devices/device-001/offer`
   - MediaConfig: `RECEIVE_VIDEO`
3. 點擊連線

**驗證：**
- App A → Proxy → Pion，SDP 轉發成功
- App A 收到 Pion 的視訊 → `Connected`
- Proxy log 顯示只有 HTTP 流量，無 RTP

**結果：** ✅ Pass

---

#### S2-02: Auth via BE (JWT)

**步驟：**
1. Proxy 啟用 JWT 驗證
2. App A 不帶 token 連線 → 預期失敗
3. App A 帶正確 JWT token 連線：
   - Auth: `SignalingAuth.Bearer("valid-test-token")`

**驗證：**
- 無 token → 收到 401 Unauthorized
- 有效 token → 正常連線 `Connected`

**結果：** ✅ Pass

---

#### S2-03: IoT offline (502)

**步驟：**
1. Proxy 設定 device-001 為離線：`setDeviceOffline("device-001")`
   或停止 Pion：`docker compose stop pion-iot`
2. App A 連線到 device-001

**驗證：**
- App A 收到錯誤 → SessionState: `Error` (502)
- 如果 RetryConfig 啟用，App A 進入 `Reconnecting` 狀態

**結果：** ✅ Pass

---

#### S2-04: IoT reconnect

**步驟：**
1. 接續 S2-03，App A 在 `Reconnecting` 狀態
2. 恢復 Pion：`docker compose start pion-iot`
   或 `setDeviceOnline("device-001")`
3. 等待 App A 重連

**驗證：**
- App A 自動重連成功 → `Connected`
- 視訊恢復正常

**結果：** ✅ Pass

---

#### S2-05: Multiple IoT devices

**步驟：**
1. Proxy 註冊多個裝置：
   ```
   registerDevice("cam-001", "http://pion-iot:8080/camera-1")
   registerDevice("cam-002", "http://pion-iot:8080/camera-2")
   ```
2. App A 連 cam-001
3. App B 連 cam-002

**驗證：**
- 兩個 App 各自連到正確的串流
- 互不干擾

**結果：** ✅ Pass

---

#### S2-06: Video receive + audio send (語音對講)

**步驟：**
1. App A:
   - Session 1: WHEP 收視訊 → `http://<HOST>:9090/api/v1/devices/device-001/offer` (RECEIVE_VIDEO)
   - Session 2: WHIP 發音訊 → `http://<HOST>:9090/api/v1/devices/device-001-audio/offer` (SEND_AUDIO)
2. 兩個 session 都經過 BE Proxy

**驗證：**
- 視訊和音訊 session 都 `Connected`
- App A 同時收到視訊 + 發出音訊
- Proxy log 顯示兩條 SDP 轉發記錄

**結果：** ✅ Pass

---

## S-3: Pion SFU Tests

### 前置條件
- Pion IoT running in **SFU mode** (`docker compose up -d pion-iot`)

---

#### S3-01: BE media relay basic

**步驟：**
1. FFmpeg (模擬 IoT) 推 WHIP 到 Pion SFU：
   ```bash
   # 或由 Pion 自己產生測試串流
   ```
2. App A: WHEP → `http://<HOST>:8080/sfu-stream/whep`

**驗證：**
- App A 收到經 Pion SFU 轉發的視訊
- SessionState → `Connected`

**結果：** ✅ Pass

---

#### S3-02: 1-to-N via relay

**步驟：**
1. IoT/FFmpeg → WHIP → Pion SFU (`sfu-stream/whip`)
2. App A: WHEP → `sfu-stream/whep`
3. App B: WHEP → `sfu-stream/whep`
4. App C (JVM Desktop): WHEP → `sfu-stream/whep`

**驗證：**
- 三端都收到視訊
- Pion SFU 只收到一份 WHIP 輸入

**結果：** ✅ Pass

---

#### S3-03: Publisher disconnect

**步驟：**
1. S3-02 場景運行中
2. 停止 IoT 推流 (停止 FFmpeg 或斷開 WHIP)

**驗證：**
- 所有 viewer (App A/B/C) 偵測到 → `Reconnecting` 或 `Error`
- Viewer 顯示適當的錯誤/重連狀態

**結果：** ✅ Pass

---

#### S3-04: Viewer doesn't affect publisher

**步驟：**
1. S3-02 場景運行中
2. App C 斷線 (關閉 session)
3. 新的 App D 加入 WHEP

**驗證：**
- Publisher 連線不受影響
- App A, App B 畫面不中斷
- App D 成功收到視訊

**結果：** ✅ Pass

---

## S-5: Dual Session IoT Tests

### 前置條件
- Pion IoT running (支援 DataChannel)
- SignalingProxyServer running (S5-03 需要)

---

**結果：** ⏳ 待測 — 需要 App 新功能或 Pion echo endpoint

**步驟：**
1. App A:
   - Session 1 (Video): WHEP → `http://<HOST>:8080/iot-video/whep` (RECEIVE_VIDEO)
   - Session 2 (DataChannel): WHIP → `http://<HOST>:8080/iot-dc/whip` (create DataChannel)
2. 兩個 session 獨立建立

**驗證：**
- 兩個 session 都 `Connected`
- Video session 顯示畫面
- DataChannel session 可發送/接收訊息

**結果：** ⏳ 待測 — 需要 App 新功能或 Pion echo endpoint

---

**結果：** ⏳ 待測 — 需要 App 新功能或 Pion echo endpoint

**步驟：**
1. App A 透過 DataChannel session 發送：
   ```json
   {"cmd": "get_status"}
   ```
2. 等待 Pion IoT 回應

**驗證：**
- Pion 回應：`{"status": "ok", "uptime": 123}`
- App A 收到回應並顯示

**結果：** ⏳ 待測 — 需要 App 新功能或 Pion echo endpoint

---

**結果：** ⏳ 待測 — 需要 App 新功能或 Pion echo endpoint

**步驟：**
1. Proxy 註冊 device：
   ```
   registerDevice("iot-001-video", "http://pion-iot:8080/iot-video")
   registerDevice("iot-001-dc", "http://pion-iot:8080/iot-dc")
   ```
2. App A:
   - Video: 經 Proxy → `http://<HOST>:9090/api/v1/devices/iot-001-video/offer`
   - DataChannel: 經 Proxy → `http://<HOST>:9090/api/v1/devices/iot-001-dc/offer`

**驗證：**
- SDP 經過 Proxy 轉發
- Media/DC 直連 Pion（不經 Proxy）
- 兩個 session 都正常運作

**結果：** ⏳ 待測 — 需要 App 新功能或 Pion echo endpoint

---

## C-1: Bidirectional Call Tests

### 前置條件
- Pion IoT running in **bidirectional mode**

---

**結果：** ⏳ 待測 — 需要 App 新功能或 Pion echo endpoint

**步驟：**
1. App A: `MediaConfig.VIDEO_CALL` → `http://<HOST>:8080/videocall/whip`
2. App B: `MediaConfig.VIDEO_CALL` → `http://<HOST>:8080/videocall/whep`
   (Pion 作為中間的 media relay)

**驗證：**
- App A 看到 App B 的攝影機 (或 Pion 轉發的畫面)
- App B 看到 App A 的攝影機
- 兩端都有音訊

**結果：** ⏳ 待測 — 需要 App 新功能或 Pion echo endpoint

---

#### C1-02: Audio intercom

**步驟：**
1. App A: `MediaConfig.BIDIRECTIONAL_AUDIO` → Pion `intercom/whip`
2. App B: `MediaConfig.BIDIRECTIONAL_AUDIO` → Pion `intercom/whep`

**驗證：**
- App A 說話 → App B 聽到
- App B 說話 → App A 聽到

**結果：** ⚠️ Partial — 單向通（App A→B 音訊正常，雙向需 Pion echo endpoint）

---

**結果：** ⏳ 待測 — 需要 App 新功能或 Pion echo endpoint

**步驟：**
1. C1-01 場景運行中
2. App A 呼叫 `switchCamera()` (前鏡頭 ↔ 後鏡頭)

**驗證：**
- App A 的預覽切換鏡頭
- App B (remote) 看到的畫面也切換

**結果：** ⏳ 待測 — 需要 App 新功能或 Pion echo endpoint

---

**結果：** ⏳ 待測 — 需要 App 新功能或 Pion echo endpoint

**步驟：**
1. App A: VIDEO_CALL + createDataChannel("chat")
2. 連線到 Pion bidirectional endpoint
3. 透過 DataChannel 發送文字訊息

**驗證：**
- 視訊正常播放
- DataChannel 訊息正確送達/收到
- 互不干擾

**結果：** ⏳ 待測 — 需要 App 新功能或 Pion echo endpoint

---

**結果：** ⏳ 待測 — 需要 App 新功能或 Pion echo endpoint

**步驟：**
1. C1-01 場景運行中
2. App A 依序操作：
   - `setMuted(true)` → 靜音
   - `setMuted(false)` → 取消靜音
   - `setVideoEnabled(false)` → 關閉視訊
   - `setVideoEnabled(true)` → 開啟視訊

**驗證：**
- 每次切換後，remote 端 (App B) 反映對應變化
- 靜音時對方聽不到
- 關閉視訊時對方看不到畫面

**結果：** ⏳ 待測 — 需要 App 新功能或 Pion echo endpoint

---

## C-2: External IoT Tests

### 前置條件
- Pion IoT running (模擬外部 IoT 裝置)

---

#### C2-01: Receive from IoT camera

**步驟：**
1. Pion 產生測試視訊串流
2. App A: WHEP → `http://<HOST>:8080/iot-camera/whep` (RECEIVE_VIDEO)

**驗證：**
- App A 收到 Pion 的視訊 → 畫面顯示
- `Connected` 狀態

**結果：** ✅ Pass

---

#### C2-02: Send audio to IoT

**步驟：**
1. App A: WHIP → `http://<HOST>:8080/iot-mic/whip` (SEND_AUDIO)

**驗證：**
- `Connected` 狀態
- Pion log 顯示收到音訊 track

**結果：** ✅ Pass

---

**結果：** ⏳ 待測 — 需要 App 新功能或 Pion echo endpoint

**步驟：**
1. App A 連線到 Pion 並建立 DataChannel
2. 發送 JSON 指令：`{"cmd": "move", "direction": "forward"}`
3. 等待回應

**驗證：**
- Pion 收到指令並回應
- App A 顯示回應內容
- 雙向訊息傳遞正常

**結果：** ⏳ 待測 — 需要 App 新功能或 Pion echo endpoint

---

**結果：** ⏳ 待測 — 需要 App 新功能或 Pion echo endpoint

**步驟：**
1. App A:
   - Session 1: WHEP 收視訊 ← Pion `iot-camera/whep`
   - Session 2: WHIP 發音訊 → Pion `iot-mic/whip`
2. 或者用單一 session + bidirectional mode

**驗證：**
- 同時收到視訊 + 發出音訊
- 兩個 session 互不干擾

**結果：** ⏳ 待測 — 需要 App 新功能或 Pion echo endpoint

---

#### C2-05: IoT 不同 WebRTC 套件

**步驟：**
1. Pion IoT (Go/pion) 已在運行
2. App A: WHEP → Pion endpoint

**驗證：**
- Library 成功與非 MediaMTX 的 WebRTC 套件 (Pion) 互通
- SDP 交換正常，ICE 連線正常
- 視訊/音訊播放正常

**結果：** ✅ Pass

---

## C-3: Multiple VideoRenderer Tests

### 前置條件
- MediaMTX running
- 需要多路串流源 (FFmpeg 推多路或 App 推多路)

### 準備多路串流

```bash
# 方式 1: 用 docker exec 啟動額外的 FFmpeg 推流
docker exec -d ffmpeg-source ffmpeg -re -f lavfi -i "testsrc=size=640x480:rate=30" \
  -c:v libx264 -f rtsp rtsp://mediamtx:8554/stream-1

docker exec -d ffmpeg-source ffmpeg -re -f lavfi -i "testsrc2=size=640x480:rate=30" \
  -c:v libx264 -f rtsp rtsp://mediamtx:8554/stream-2

docker exec -d ffmpeg-source ffmpeg -re -f lavfi -i "smptebars=size=640x480:rate=30" \
  -c:v libx264 -f rtsp rtsp://mediamtx:8554/stream-3

docker exec -d ffmpeg-source ffmpeg -re -f lavfi -i "color=c=blue:size=640x480:rate=30" \
  -c:v libx264 -f rtsp rtsp://mediamtx:8554/stream-4
```

---

**結果：** ⏳ 待測 — 需要 App 新功能或 Pion echo endpoint

**步驟：**
1. App A 建立 2 個 WebRTCSession：
   - Session 1: WHEP → `stream-1/whep` → VideoRenderer #1
   - Session 2: WHEP → `stream-2/whep` → VideoRenderer #2

**驗證：**
- 兩個 VideoRenderer 都顯示不同畫面
- 兩個 session 都 `Connected`

**結果：** ⏳ 待測 — 需要 App 新功能或 Pion echo endpoint

---

**結果：** ⏳ 待測 — 需要 App 新功能或 Pion echo endpoint

**步驟：**
1. App A 建立 4 個 WebRTCSession：
   - 分別連 `stream-1` ~ `stream-4` 的 WHEP
   - 4 個 VideoRenderer 排成 2x2 grid

**驗證：**
- 4 路畫面都正常顯示
- 效能可接受 (不明顯掉幀/延遲)
- 記錄 CPU/Memory 使用量

**結果：** ⏳ 待測 — 需要 App 新功能或 Pion echo endpoint

---

**結果：** ⏳ 待測 — 需要 App 新功能或 Pion echo endpoint

**步驟：**
1. C3-01 場景 (2 sessions) 運行中
2. 關閉 Session 1：`session1.close()`

**驗證：**
- Session 1 → `Closed`
- Session 2 不受影響，仍 `Connected`，畫面正常

**結果：** ⏳ 待測 — 需要 App 新功能或 Pion echo endpoint

---

**結果：** ⏳ 待測 — 需要 App 新功能或 Pion echo endpoint

**步驟：**
1. Session 1: `RECEIVE_VIDEO` → WHEP 收看 `stream-1`
2. Session 2: `VIDEO_CALL` → 連到 Pion bidirectional endpoint

**驗證：**
- Session 1 只收不發
- Session 2 收發都有
- 各自依照自己的 MediaConfig 運作

**結果：** ⏳ 待測 — 需要 App 新功能或 Pion echo endpoint

---

## C-4: 1-to-N Tests

### 前置條件
- MediaMTX running
- 3 個測試裝置/實例 (Android + iOS + JVM Desktop)

---

#### C4-01: 3 viewers, 1 publisher

**步驟：**
1. App A: WHIP `SEND_VIDEO` → `http://<HOST>:8889/broadcast/whip`
2. App B: WHEP `RECEIVE_VIDEO` → `broadcast/whep`
3. App C (JVM): WHEP `RECEIVE_VIDEO` → `broadcast/whep`
4. 瀏覽器: `http://<HOST>:8889/broadcast/`

**驗證：**
- 3 個 viewer 都看到 App A 的畫面
- App A 的 stats 正常

**結果：** ✅ Pass

---

#### C4-02: Viewer joins late

**步驟：**
1. App A 已在推流 (`broadcast/whip`) 10 秒以上
2. App B 才加入 WHEP `broadcast/whep`

**驗證：**
- App B 立即收到視訊 (不需等 keyframe 太久)
- 畫面正常顯示

**結果：** ✅ Pass

---

#### C4-03: Viewer leaves

**步驟：**
1. C4-01 場景運行中
2. App C 斷開連線

**驗證：**
- App A (publisher) 不受影響
- App B 畫面不中斷

**結果：** ✅ Pass

---

#### C4-04: Publisher reconnect

**步驟：**
1. C4-01 場景運行中
2. App A 斷線 (關閉 WiFi 或 kill session)
3. 等待 5 秒
4. App A 重新連線 WHIP `broadcast/whip`

**驗證：**
- Viewers 偵測到中斷 → `Reconnecting`
- App A 重連後 viewers 自動恢復 → `Connected`

**結果：** ✅ Pass

---

## C-5: DataChannel Tests

### 前置條件
- Pion IoT running (支援 DataChannel echo)

---

**結果：** ⏳ 待測 — 需要 App 新功能或 Pion echo endpoint

**步驟：**
1. App A 連到 Pion + 建立 DataChannel ("messages", reliable)
2. 發送多條 JSON 訊息：
   ```json
   {"seq": 1, "msg": "hello"}
   {"seq": 2, "msg": "world"}
   {"seq": 3, "msg": "test"}
   ```

**驗證：**
- Pion echo 回所有訊息
- 順序正確 (seq 1, 2, 3)

**結果：** ⏳ 待測 — 需要 App 新功能或 Pion echo endpoint

---

**結果：** ⏳ 待測 — 需要 App 新功能或 Pion echo endpoint

**步驟：**
1. App A 連到 Pion + DataChannel
2. 發送 binary data：`sendBinary(byteArrayOf(0x01, 0x02, 0x03, 0xFF))`

**驗證：**
- Pion echo 回相同 binary data
- 資料完整無損

**結果：** ⏳ 待測 — 需要 App 新功能或 Pion echo endpoint

---

**結果：** ⏳ 待測 — 需要 App 新功能或 Pion echo endpoint

**步驟：**
1. App A 建立 2+ DataChannels：
   - Channel "control": reliable
   - Channel "telemetry": unreliable
2. 分別在兩個 channel 發送訊息

**驗證：**
- 兩個 channel 各自獨立收發
- "control" channel 訊息有序
- "telemetry" channel 可能亂序（unreliable）

**結果：** ⏳ 待測 — 需要 App 新功能或 Pion echo endpoint

---

**結果：** ⏳ 待測 — 需要 App 新功能或 Pion echo endpoint

**步驟：**
1. App A:
   - WHEP 收視訊 from Pion
   - 同一 session 上建立 DataChannel
2. 同時播放視訊 + 收發 DataChannel 訊息

**驗證：**
- 視訊不受 DataChannel 影響
- DataChannel 不受視訊影響
- 兩者同時正常運作

**結果：** ⏳ 待測 — 需要 App 新功能或 Pion echo endpoint

---

**結果：** ⏳ 待測 — 需要 App 新功能或 Pion echo endpoint

**步驟：**
1. App A 連到 Pion + DataChannel (reliable)
2. 快速發送 100 條訊息 (間隔 < 100ms)：
   ```
   for (i in 1..100) { channel.send("{\"seq\":$i}") }
   ```

**驗證：**
- Pion echo 回 100 條訊息
- 全部收到，無遺漏
- 順序正確 (reliable mode)

**結果：** ⏳ 待測 — 需要 App 新功能或 Pion echo endpoint

---

## Teardown

```bash
# 停止所有 Docker 服務
cd test-infra/
docker compose down

# 移除 Pion image (optional)
docker rmi test-infra/pion-iot:latest
```

---

## Quick Reference

### 常用指令

```bash
# 啟動全部服務
docker compose up -d

# 只啟動 MediaMTX
docker compose up -d mediamtx

# 只啟動 Pion
docker compose up -d pion-iot

# 查看 MediaMTX 活躍串流
curl http://localhost:9997/v3/paths/list

# 查看 Pion 健康狀態
curl http://localhost:8080/health

# 查看本機 LAN IP (App 需要用這個)
ipconfig getifaddr en0

# 查看 Docker logs
docker compose logs -f mediamtx
docker compose logs -f pion-iot

# 手動推 RTSP 測試串流到 MediaMTX
docker run --rm --network host linuxserver/ffmpeg \
  -re -f lavfi -i testsrc=size=640x480:rate=30 \
  -c:v libx264 -f rtsp rtsp://localhost:8554/manual-test
```

### URL 模板

| 用途 | URL |
|------|-----|
| MediaMTX WHEP 收看 | `http://<HOST>:8889/{stream}/whep` |
| MediaMTX WHIP 推流 | `http://<HOST>:8889/{stream}/whip` |
| MediaMTX Web Player | `http://<HOST>:8889/{stream}/` |
| MediaMTX RTSP | `rtsp://<HOST>:8554/{stream}` |
| Pion WHEP 收看 | `http://<HOST>:8080/{stream}/whep` |
| Pion WHIP 推流 | `http://<HOST>:8080/{stream}/whip` |
| Signaling Proxy | `http://<HOST>:9090/api/v1/devices/{deviceId}/offer` |

### 已驗證的 Pion 端點

| 端點 | 有 FFmpeg 推流 | 影像來源 service | 用途 |
|------|--------------|-----------------|------|
| `8080/iot-camera/whep` | ✅ | ffmpeg-pion-source | IoT 攝影機模擬 |
| `8080/dc-test/whip` | ✅ | ffmpeg-pion-dc-source | DataChannel echo 測試（必須用 WHIP） |
| `8080/dc-test/whep` | ✅ | ffmpeg-pion-dc-source | DataChannel + 影像（WHEP，Show Video） |
| `8080/{其他任意 stream}/whep` | ❌ | 無 | **黑畫面**（Pion fallback 到無效 VP8） |

> **Pion 黑畫面根本原因**：沒有 WHIP publisher 時，Pion 會 fallback 到自帶的手刻 VP8 test pattern，但該 bitstream 缺少 frame tag，decoder 無法解碼 → 黑畫面。只有 `iot-camera` 和 `dc-test` 兩個 stream 有 FFmpeg 推流，其餘 stream（如 `stream-1`、`sfu-stream`、`iot-video` 等）都會是黑畫面。

> **DataChannel Echo 注意**：Pion 只在 WHIP session（`setupWHIP`）設定 `OnDataChannel`，WHEP session 不 echo app 建立的 channel。測試 echo 必須連 WHIP 端點。

---

## Troubleshooting

### Pion 黑畫面

**症狀**：連線 Connected 但 VideoRenderer 全黑。

**原因**：`ffmpeg-pion-source` / `ffmpeg-pion-dc-source` 尚未推流，Pion fallback 到無效的 VP8 test pattern。

**診斷**：
```bash
# 確認 FFmpeg 容器有在跑
docker ps | grep ffmpeg-pion

# 查看 FFmpeg 是否推流成功（找 "frame=" 或確認無 "Conversion failed"）
docker logs ffmpeg-pion-source 2>&1 | tail -20

# 確認 Pion 有收到 WHIP session
docker logs pion-iot 2>&1 | grep "whip"
```

**常見錯誤**：
```
[WHIP muxer] Unsupported video codec vp8 by RTC, choose h264
→ 必須用 H.264，不能用 VP8

[WHIP muxer] Unsupported audio channels 1 by RTC, choose stereo
→ 音訊必須是 stereo，加 -ac 2
```

**修復**：
```bash
# 重啟 FFmpeg → Pion 的推流
docker compose restart ffmpeg-pion-source ffmpeg-pion-dc-source

# 或從頭重啟全部
docker compose down && docker compose up -d
```

---

### DataChannel 無 echo

**症狀**：發訊息後看不到 echo 回來。

**原因**：在 WHEP 模式下，Pion 的 `setupWHEP` 不設定 `OnDataChannel`，app 建立的 channel Pion 不處理。

**修復**：DataChannel echo 測試必須使用 **WHIP 模式**：
- URL: `http://<HOST>:8080/dc-test/whip`
- Mode: WHIP (Send)

---

### Multi-View 黑畫面 + reconnect loop

**症狀**：連線 Connected 但黑畫面，log 顯示不斷重連。

**原因**：同時呼叫 `session.connect()` 和 VideoRenderer 自動 connect，兩次 `client.initialize()` 建立兩個 PeerConnection → reconnect loop。

**修復**：連線 VideoRenderer 的 session 時，不要手動呼叫 `session.connect()`，讓 VideoRenderer 的 `LaunchedEffect` 處理。
