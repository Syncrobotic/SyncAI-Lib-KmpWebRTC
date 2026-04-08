package com.syncrobotic.webrtc.audio

import androidx.compose.runtime.Composable
import com.syncrobotic.webrtc.session.WebRTCSession

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
