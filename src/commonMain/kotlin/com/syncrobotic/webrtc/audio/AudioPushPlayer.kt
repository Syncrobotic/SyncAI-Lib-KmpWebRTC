package com.syncrobotic.webrtc.audio

import androidx.compose.runtime.Composable
import com.syncrobotic.webrtc.session.WebRTCSession
import com.syncrobotic.webrtc.session.WhipSession

/**
 * Composable to manage audio push via a [WhipSession].
 *
 * Auto-connects the session (if idle) and maps session state to
 * [AudioPushState]. The session handles microphone capture, WHIP
 * signaling, and auto-reconnect internally.
 *
 * ```kotlin
 * val signaling = WhipSignalingAdapter(url = "https://server/stream/whip")
 * val session = WhipSession(signaling)
 *
 * val controller = AudioPushPlayer(
 *     session = session,
 *     autoStart = true,
 *     onStateChange = { state -> println(state) }
 * )
 * ```
 *
 * @param session   The WHIP session that manages audio capture and streaming
 * @param autoStart If true, automatically connect when composed (default: true)
 * @param onStateChange Optional callback for audio push state changes
 * @return An [AudioPushController] for mute/unmute and stop
 */
@Composable
expect fun AudioPushPlayer(
    session: WhipSession,
    autoStart: Boolean = true,
    onStateChange: ((AudioPushState) -> Unit)? = null,
): AudioPushController

/**
 * Composable to manage audio push via a [WebRTCSession].
 *
 * Requires `mediaConfig.sendAudio = true` in the session's [MediaConfig].
 *
 * ```kotlin
 * val session = WebRTCSession(
 *     signaling = HttpSignalingAdapter("https://server/stream/whip"),
 *     mediaConfig = MediaConfig.SEND_AUDIO
 * )
 * val controller = AudioPushPlayer(session = session, autoStart = true)
 * ```
 */
@Composable
expect fun AudioPushPlayer(
    session: WebRTCSession,
    autoStart: Boolean = true,
    onStateChange: ((AudioPushState) -> Unit)? = null,
): AudioPushController

/**
 * Composable function to create and manage an audio push connection
 * (legacy config-based API).
 *
 * @param config Configuration for the audio push connection
 * @param autoStart If true, automatically start streaming when composed (default: false)
 * @param onStateChange Callback for state changes
 * @return An [AudioPushController] for managing the connection
 */
@Deprecated(
    message = "Use AudioPushPlayer(session: WhipSession, ...) instead. Will be removed in v3.0.",
    replaceWith = ReplaceWith("AudioPushPlayer(session, autoStart, onStateChange)")
)
@Composable
expect fun AudioPushPlayer(
    config: AudioPushConfig,
    autoStart: Boolean = false,
    onStateChange: OnAudioPushStateChange = {}
): AudioPushController

/**
 * Remember an [AudioPushController] with automatic lifecycle management.
 *
 * @param config Configuration for the audio push connection
 * @param onStateChange Callback for state changes
 * @return An [AudioPushController] for managing the connection
 */
@Deprecated(
    message = "Use AudioPushPlayer(session: WhipSession, ...) instead. Will be removed in v3.0.",
    replaceWith = ReplaceWith("AudioPushPlayer(session)")
)
@Composable
expect fun rememberAudioPushController(
    config: AudioPushConfig,
    onStateChange: OnAudioPushStateChange = {}
): AudioPushController
