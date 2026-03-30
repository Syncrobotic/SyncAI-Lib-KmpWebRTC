---
description: "Use when working on WebRTC connection logic, signaling (WHEP/WHIP/WebSocket), PeerConnection management, ICE candidates, SDP negotiation, DataChannel, or audio/video streaming."
---
# WebRTC Development Guidelines

## PeerConnection Lifecycle

v2 API (preferred):
1. Create a `SignalingAdapter` (e.g. `WhepSignalingAdapter(url, auth)`)
2. Create a `WhepSession(signaling)` or `WhipSession(signaling)`
3. Call `session.connect()` — SDP exchange, ICE, and media are handled internally
4. Observe state via `session.state: StateFlow`
5. Call `session.close()` to release all resources

Internal (managed by Session, not directly used):
1. `WebRTCClient` creates PeerConnection
2. SDP offer/answer exchanged via SignalingAdapter
3. ICE candidates trickled via SignalingAdapter
4. Media streams received/sent
5. Resources released on `close()`

## PeerConnectionFactory Strategy

- **Android**: Creates a separate Factory per connection (to avoid EglContext conflicts), protected with `synchronized`
- **iOS**: Creates a separate Factory per connection; shared `RTCAudioSession` protected with lock
- **JVM**: Shares a single Factory with reference counting (only disposed when refCount reaches 0)

## Signaling

### v2 (current)
- `SignalingAdapter`: Interface with `sendOffer()`, `sendIceCandidate()`, `terminate()`
- `WhepSignalingAdapter`: HTTP WHEP signaling for receiving streams (POST offer, PATCH ICE, DELETE teardown)
- `WhipSignalingAdapter`: HTTP WHIP signaling for sending streams
- `SignalingAuth`: Pluggable auth — `Bearer(token)`, `Cookies(map)`, `CookieStorage(storage)`, `Custom(headers)`, `None`
- `SignalingResult`: Unified response type with `sdpAnswer`, `resourceUrl`, `etag`, `iceServers`

### Legacy (deprecated, will be removed in v3.0)
- `WhepSignaling`: Direct HTTP client, no auth abstraction
- `WhipSignaling`: Direct HTTP client, no auth abstraction
- `WebSocketSignaling`: WebSocket with heartbeat mechanism

All signaling code is in `src/commonMain/kotlin/com/syncrobotic/webrtc/signaling/`

## Auto-Reconnect

- `StreamRetryHandler` provides exponential backoff retry (default: 5 attempts, 1s → 45s)
- `RetryConfig` allows custom retry parameters
- Reconnection logic should trigger in the state machine's `Error` / `Disconnected` states

## DataChannel

- Supports text (`send(String)`) and binary (`sendBinary(ByteArray)`)
- `DataChannelConfig` for ordered/unordered, reliable/unreliable settings
- Events received via `DataChannelListener` callbacks

## Error Handling

- Connection/streaming errors expressed as sealed class states (`PlayerState.Error(message, cause, isRetryable)`)
- Signaling layer uses custom Exceptions: `WhepException`, `WhipException` (with `WhipErrorCode` enum: `OFFER_REJECTED`, `ICE_CANDIDATE_FAILED`, `NETWORK_ERROR`, etc.), `WebSocketSignalingException`
- Throws `StreamRetryExhaustedException` when retries are exhausted
- When adding error handling, prefer using state machine Error states over direct throw
