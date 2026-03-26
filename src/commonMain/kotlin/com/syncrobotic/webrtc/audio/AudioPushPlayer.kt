package com.syncrobotic.webrtc.audio

import androidx.compose.runtime.Composable

/**
 * Composable function to create and manage an audio push connection.
 * 
 * This function captures audio from the device microphone and streams it
 * to a WHIP endpoint via WebRTC. It handles the full lifecycle including:
 * - Microphone permission (platform-specific)
 * - Audio capture and encoding
 * - WebRTC connection management
 * - WHIP signaling
 * - Automatic cleanup on disposal
 * 
 * Usage:
 * ```kotlin
 * @Composable
 * fun AudioStreamingScreen() {
 *     val config = AudioPushConfig.create(
 *         host = "10.8.100.245",
 *         streamPath = "mobile-audio"
 *     )
 *     
 *     val controller = AudioPushPlayer(
 *         config = config,
 *         autoStart = false,
 *         onStateChange = { state ->
 *             when (state) {
 *                 is AudioPushState.Streaming -> println("Audio streaming")
 *                 is AudioPushState.Error -> println("Error: ${state.message}")
 *                 is AudioPushState.Reconnecting -> println("Reconnecting ${state.attempt}/${state.maxAttempts}")
 *                 else -> {}
 *             }
 *         }
 *     )
 *     
 *     Column {
 *         Text("Status: ${controller.state}")
 *         
 *         Row {
 *             Button(onClick = { controller.start() }) {
 *                 Text("Start")
 *             }
 *             Button(onClick = { controller.stop() }) {
 *                 Text("Stop")
 *             }
 *             Button(onClick = { controller.toggleMute() }) {
 *                 Text(if (controller.isMuted) "Unmute" else "Mute")
 *             }
 *         }
 *     }
 * }
 * ```
 * 
 * @param config Configuration for the audio push connection
 * @param autoStart If true, automatically start streaming when composed (default: false)
 * @param onStateChange Callback for state changes
 * @return An [AudioPushController] for managing the connection
 */
@Composable
expect fun AudioPushPlayer(
    config: AudioPushConfig,
    autoStart: Boolean = false,
    onStateChange: OnAudioPushStateChange = {}
): AudioPushController

/**
 * Remember an [AudioPushController] with automatic lifecycle management.
 * 
 * This is equivalent to [AudioPushPlayer] but with a more explicit name
 * for users familiar with the `remember*` naming convention.
 * 
 * @param config Configuration for the audio push connection
 * @param onStateChange Callback for state changes
 * @return An [AudioPushController] for managing the connection
 */
@Composable
expect fun rememberAudioPushController(
    config: AudioPushConfig,
    onStateChange: OnAudioPushStateChange = {}
): AudioPushController
