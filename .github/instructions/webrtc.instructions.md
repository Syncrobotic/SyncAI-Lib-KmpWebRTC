---
description: "Use when working on WebRTC connection logic, signaling (WHEP/WHIP/WebSocket), PeerConnection management, ICE candidates, SDP negotiation, DataChannel, or audio/video streaming."
---
# WebRTC Development Guidelines

## PeerConnection Lifecycle

v2 API (current):
1. Create an `HttpSignalingAdapter(url, auth)` (or custom `SignalingAdapter`)
2. Create a `WebRTCSession(signaling, mediaConfig)` with the desired `MediaConfig`
3. Call `session.connect()` — SDP exchange, ICE, and media are handled internally
4. Observe state via `session.state: StateFlow<SessionState>`
5. Call `session.close()` to release all resources

MediaConfig presets:
- `MediaConfig.RECEIVE_VIDEO` — receive video + audio (WHEP)
- `MediaConfig.SEND_AUDIO` — send microphone audio (WHIP)
- `MediaConfig.SEND_VIDEO` — send camera + audio (WHIP)
- `MediaConfig.BIDIRECTIONAL_AUDIO` — send + receive audio (intercom)
- `MediaConfig.VIDEO_CALL` — send + receive video + audio

Internal (managed by WebRTCSession, not directly used):
1. `WebRTCClient` creates PeerConnection
2. SDP offer/answer exchanged via SignalingAdapter
3. ICE candidates trickled via SignalingAdapter
4. Media streams received/sent based on MediaConfig directions
5. Resources released on `close()`

## PeerConnectionFactory Strategy

- **Android**: Creates a separate Factory per connection (to avoid EglContext conflicts), protected with `synchronized`
- **iOS**: Creates a separate Factory per connection; shared `RTCAudioSession` protected with lock
- **JVM**: Shares a single Factory with reference counting via `PeerConnectionFactoryManager` (only disposed when refCount reaches 0)

## Signaling

- `SignalingAdapter`: Interface with `sendOffer()`, `sendIceCandidate()`, `terminate()`
- `HttpSignalingAdapter`: Unified HTTP signaling for both WHEP and WHIP (POST offer, PATCH ICE, DELETE teardown). The HTTP flow is identical — only the endpoint URL differs
- `SignalingAuth`: Pluggable auth — `Bearer(token)`, `Cookies(map)`, `CookieStorage(storage)`, `Custom(headers)`, `None`
- `SignalingResult`: Response type with `sdpAnswer`, `resourceUrl`, `etag`, `iceServers`
- `SignalingException`: Error with `SignalingErrorCode` (`OFFER_REJECTED`, `ICE_CANDIDATE_FAILED`, `NETWORK_ERROR`, `SESSION_TERMINATED`, `UNKNOWN`)

All signaling code is in `src/commonMain/kotlin/com/syncrobotic/webrtc/signaling/`

## Auto-Reconnect

- `StreamRetryHandler` provides exponential backoff retry (default: 5 attempts, 1s → 45s)
- `RetryConfig` allows custom retry parameters with presets: `DEFAULT`, `AGGRESSIVE`, `PERSISTENT`, `DISABLED`
- Reconnection logic is built into `WebRTCSession` — triggers automatically on `DISCONNECTED`/`FAILED` states

## DataChannel

- Supports text (`send(String)`) and binary (`sendBinary(ByteArray)`)
- `DataChannelConfig` with factory methods: `reliable(label)`, `unreliable(label)`, `maxLifetime(label, ms)`
- Events received via `DataChannelListener` callbacks
- Create before `connect()` for inclusion in initial SDP negotiation

## Error Handling

- Session errors expressed as `SessionState.Error(message, cause, isRetryable)`
- Signaling errors use `SignalingException` with `SignalingErrorCode` enum
- `StreamRetryHandler` throws after retries exhausted, caught by `WebRTCSession` which sets `SessionState.Error(isRetryable = false)`
- When adding error handling, prefer using state machine Error states over direct throw

## Testing

- Unit tests: `src/jvmTest/` with kotlin-test + coroutines-test
- E2E signaling tests: Use `MockWhepWhipServer` (in-process Ktor server)
- E2E full WebRTC tests: Guarded by `assumeWebRTCAvailable()` — skip if native libs unavailable
- Testcontainers tests: `MediaMTXContainer` for real MediaMTX integration — skip if Docker unavailable
- See `docs/TEST_SPEC.md` for full test specification
