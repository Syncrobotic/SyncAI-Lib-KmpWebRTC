---
description: "Use when working on WebRTC connection logic, signaling (WHEP/WHIP/WebSocket), PeerConnection management, ICE candidates, SDP negotiation, DataChannel, or audio/video streaming."
---
# WebRTC Development Guidelines

## PeerConnection Lifecycle

1. Create a `WebRTCClient` instance
2. Exchange SDP offer/answer via Signaling (WHEP/WHIP/WebSocket)
3. Handle ICE candidates (supports trickle ICE)
4. After connection is established, receive/send media streams
5. Call `close()` to release all resources when done

## PeerConnectionFactory Strategy

- **Android**: Creates a separate Factory per connection (to avoid EglContext conflicts), protected with `synchronized`
- **iOS**: Creates a separate Factory per connection; shared `RTCAudioSession` protected with lock
- **JVM**: Shares a single Factory with reference counting (only disposed when refCount reaches 0)

## Signaling

- `WhepSignaling`: HTTP POST for SDP exchange, PATCH for ICE candidates; used for receiving streams
- `WhipSignaling`: HTTP POST for SDP exchange; used for sending streams (audio push)
- `WebSocketSignaling`: WebSocket connection with heartbeat mechanism; used for custom backends
- All signaling classes are in `src/commonMain/kotlin/com/syncrobotic/webrtc/signaling/`

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
- Signaling layer uses custom Exceptions: `WhepException`, `WhipException`, `WebSocketSignalingException`
- Throws `StreamRetryExhaustedException` when retries are exhausted
- When adding error handling, prefer using state machine Error states over direct throw
