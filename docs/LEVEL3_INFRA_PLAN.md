# Level 3 手動測試基礎設施 — 規劃書

## 目標
建立 Level 3 手動整合測試的基礎設施，讓 45 項測試（S-1~S-3, S-5, C-1~C-5）可以在真實 Android/iOS/JVM 裝置上執行。

## 範圍
- **包含**: S-1, S-2, S-3, S-5, C-1, C-2, C-3, C-4, C-5 (共 45 項)
- **排除**: S-4 (P2P WebSocket, 5 項) — 未來有 P2P 需求時再實作

---

## 基礎設施元件

### 元件 1: MediaMTX — Docker
- **技術**: `bluenviron/mediamtx:latest` Docker image
- **角色**: WHEP/WHIP media server + RTSP gateway
- **涵蓋**: S-1 (11項), C-3 (4項), C-4 (4項)
- **Ports**: 8554 (RTSP), 8889 (WebRTC WHEP/WHIP)
- **增強**: 新增 `mediamtx-auth.yml` (JWT auth 配置)
- **現有基礎**: `src/jvmTest/.../e2e/MediaMTXContainer.kt`

### 元件 2: SignalingProxyServer — Kotlin/Ktor (in-process)
- **技術**: Ktor embedded server，跑在 test JVM
- **角色**: BE signaling proxy (只轉 SDP，不碰 media)
- **涵蓋**: S-2 (6項), S-5 部分
- **Endpoints**:
  - `POST /api/v1/devices/{deviceId}/offer` (JWT + SDP 轉發)
  - `PATCH /api/v1/sessions/{sessionId}/ice`
  - `DELETE /api/v1/sessions/{sessionId}`
- **測試控制**: registerDevice(), setDeviceOffline(), setJwtValidation()
- **參考**: `src/jvmTest/.../e2e/MockWhepWhipServer.kt`

### 元件 3: Pion WebRTC Server — Go + Docker
- **技術**: Go + `pion/webrtc`，打包為 Docker image
- **角色**: IoT 裝置模擬 + SFU + 雙向通話 server
- **涵蓋**: S-3 (4項), S-5 (3項), C-1 (5項), C-2 (5項), C-5 (5項)
- **功能**:
  - WHIP/WHEP endpoints
  - DataChannel echo + JSON command handling
  - SFU mode (1 publisher → N viewers)
  - Bidirectional mode (sendrecv)
- **Port**: 8080 (HTTP)

### 元件 4: FFmpeg RTSP Source — Docker sidecar
- **技術**: FFmpeg Docker container
- **角色**: 模擬無 WebRTC 的 IoT 攝影機 (RTSP 推流)
- **涵蓋**: S-1b 部分
- **指令**: `ffmpeg -re -f lavfi -i testsrc=size=640x480:rate=30 -c:v libx264 -f rtsp rtsp://mediamtx:8554/iot-camera`

---

## 測試裝置

| 裝置 | 用途 |
|------|------|
| Android 實機 | 主要測試裝置，跑 test app |
| iOS 實機 | 主要測試裝置，跑 test app |
| JVM Desktop (多實例) | 第 3+ viewer，多觀眾測試 |

---

## 專案結構 (新增部分)

```
SyncAI-Lib-KmpWebRTC/
  src/jvmTest/kotlin/com/syncrobotic/webrtc/
    level3/
      infra/
        SignalingProxyServer.kt
        PionWebRTCContainer.kt
        Level3TestBase.kt
      server/
        S1_MediaMTXServerTest.kt
        S2_SignalingProxyTest.kt
        S3_PionSFUTest.kt
        S5_DualSessionIoTTest.kt
      client/
        C1_BidirectionalCallTest.kt
        C2_ExternalIoTTest.kt
        C3_MultipleRendererTest.kt
        C4_OneToNTest.kt
        C5_DataChannelTest.kt
  test-infra/
    pion-iot/
      main.go
      go.mod
      Dockerfile
    docker-compose.yml
    mediamtx.yml
    mediamtx-auth.yml
```

---

## 實作順序

1. **Phase 1**: `test-infra/` Docker 環境 (docker-compose + MediaMTX + FFmpeg)
2. **Phase 2**: Pion Go server (`test-infra/pion-iot/`)
3. **Phase 3**: SignalingProxyServer (Kotlin/Ktor)
4. **Phase 4**: Level 3 test classes
5. **Phase 5**: Android/iOS test app 驗證

---

## 技術選型總結

| 元件 | 語言 | 執行方式 | 原因 |
|------|------|---------|------|
| SignalingProxyServer | Kotlin/Ktor | JVM in-process | 跟現有 MockWhepWhipServer 一致，測試可控制內部狀態 |
| MediaMTX | — | Docker image | 現成的，已有 wrapper |
| Pion server | Go | Docker image | Pion 是 Go library，必須用 Go |
| FFmpeg source | — | Docker sidecar | 一行指令，不需程式碼 |
