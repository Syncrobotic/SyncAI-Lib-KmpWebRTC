package com.syncrobotic.webrtc.ui

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.interop.UIKitView
import com.syncrobotic.webrtc.WebRTCClient
import com.syncrobotic.webrtc.config.StreamConfig
import com.syncrobotic.webrtc.config.StreamProtocol
import com.syncrobotic.webrtc.config.StreamRetryHandler
import kotlinx.cinterop.*
import kotlinx.coroutines.launch
import platform.AVFoundation.*
import platform.AVKit.*
import platform.CoreMedia.*
import platform.Foundation.*
import platform.UIKit.*
import platform.darwin.NSObject

/**
 * iOS implementation of VideoRenderer using AVPlayer + AVPlayerViewController.
 * 
 * Supports:
 * - HLS streaming (native iOS support)
 * - WebRTC streaming (via GoogleWebRTC)
 * - For RTSP: automatically converts to HLS URL (streaming provides both)
 * 
 * Note: iOS AVPlayer doesn't support RTSP natively. 
 * Use HLS endpoint from streaming (port 8888) for iOS.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
@Composable
actual fun VideoRenderer(
    config: StreamConfig,
    modifier: Modifier,
    onStateChange: OnPlayerStateChange,
    onEvent: OnPlayerEvent
) {
    // Route to WebRTC player if WebRTC protocol is selected and supported
    if (config.protocol == StreamProtocol.WEBRTC && WebRTCClient.isSupported()) {
        WebRTCVideoPlayer(
            config = config,
            modifier = modifier,
            onStateChange = onStateChange,
            onEvent = onEvent
        )
        return
    }
    
    // Fallback to AVPlayer for HLS/RTSP
    AVPlayerVideoRenderer(
        config = config,
        modifier = modifier,
        onStateChange = onStateChange,
        onEvent = onEvent
    )
}

/**
 * Get current time in milliseconds.
 */
private fun currentTimeMillis(): Long = 
    (NSDate().timeIntervalSince1970 * 1000).toLong()

/**
 * AVPlayer-based video renderer for HLS streams.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
@Composable
private fun AVPlayerVideoRenderer(
    config: StreamConfig,
    modifier: Modifier,
    onStateChange: OnPlayerStateChange,
    onEvent: OnPlayerEvent
) {
    var playerState by remember { mutableStateOf<PlayerState>(PlayerState.Connecting) }
    var hasReportedFirstFrame by remember { mutableStateOf(false) }
    var hasReportedStreamInfo by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    
    // Retry state
    val retryConfig = remember { config.retryConfig }
    var retryAttempt by remember { mutableStateOf(0) }
    var retryJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var stalledTimeoutJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var isRetrying by remember { mutableStateOf(false) }
    
    // Convert RTSP to HLS URL for iOS (AVPlayer doesn't support RTSP)
    val streamUrl = remember(config.url, config.protocol) {
        when (config.protocol) {
            StreamProtocol.RTSP -> config.endpoints.hls  // Use HLS endpoint
            StreamProtocol.HLS -> config.url
            StreamProtocol.WEBRTC -> config.endpoints.hls  // Fallback to HLS
        }
    }
    
    // Notify state changes
    LaunchedEffect(playerState) {
        onStateChange(playerState)
    }
    
    // Create AVPlayer
    val player = remember { AVPlayer() }

    // Keep AVPlayerViewController reference to prevent ARC deallocation
    var playerViewController by remember { mutableStateOf<AVPlayerViewController?>(null) }
    
    // Helper to re-create player item and retry
    fun retryPlayback(reason: String, isDisconnect: Boolean = false) {
        if (isRetrying) {
            println("AVPlayerVideoRenderer: Retry already in progress, ignoring: $reason")
            return
        }

        val currentAttempt = retryAttempt
        val shouldRetry = if (isDisconnect) retryConfig.retryOnDisconnect else retryConfig.retryOnError

        if (shouldRetry && currentAttempt < retryConfig.maxRetries) {
            isRetrying = true
            retryAttempt = currentAttempt + 1
            val delayMs = retryConfig.calculateDelay(currentAttempt)

            println("AVPlayerVideoRenderer: Retry ${currentAttempt + 1}/${retryConfig.maxRetries} in ${delayMs}ms - $reason")
            playerState = PlayerState.Reconnecting(
                attempt = currentAttempt + 1,
                maxAttempts = retryConfig.maxRetries,
                reason = reason,
                nextRetryMs = delayMs
            )

            retryJob?.cancel()
            retryJob = scope.launch {
                kotlinx.coroutines.delay(delayMs)

                hasReportedStreamInfo = false
                hasReportedFirstFrame = false

                val url = NSURL.URLWithString(streamUrl)
                if (url != null) {
                    player.pause()
                    player.replaceCurrentItemWithPlayerItem(null)

                    kotlinx.coroutines.delay(100)

                    val newItem = AVPlayerItem(uRL = url)
                    player.replaceCurrentItemWithPlayerItem(newItem)
                    playerState = PlayerState.Loading
                    println("AVPlayerVideoRenderer: Replaced with new AVPlayerItem, waiting for ready...")

                    playerViewController?.let { vc ->
                        vc.player = player
                        vc.view.setNeedsLayout()
                        vc.view.layoutIfNeeded()
                        println("AVPlayerVideoRenderer: Refreshed AVPlayerViewController view")
                    }

                    if (config.autoPlay) {
                        player.play()
                    }

                    isRetrying = false
                } else {
                    isRetrying = false
                    playerState = PlayerState.Error("Invalid URL: $streamUrl")
                }
            }
        } else if (shouldRetry) {
            isRetrying = true
            println("AVPlayerVideoRenderer: All retries exhausted, will retry in ${retryConfig.maxDelayMs}ms - $reason")
            playerState = PlayerState.Reconnecting(
                attempt = retryConfig.maxRetries,
                maxAttempts = retryConfig.maxRetries,
                reason = "Waiting for stream...",
                nextRetryMs = retryConfig.maxDelayMs
            )
            retryJob?.cancel()
            retryJob = scope.launch {
                kotlinx.coroutines.delay(retryConfig.maxDelayMs)
                retryAttempt = 0
                hasReportedStreamInfo = false
                hasReportedFirstFrame = false
                val url = NSURL.URLWithString(streamUrl)
                if (url != null) {
                    player.pause()
                    player.replaceCurrentItemWithPlayerItem(null)

                    kotlinx.coroutines.delay(100)

                    val newItem = AVPlayerItem(uRL = url)
                    player.replaceCurrentItemWithPlayerItem(newItem)
                    playerState = PlayerState.Loading
                    println("AVPlayerVideoRenderer: Replaced with new AVPlayerItem (long wait), waiting for ready...")

                    playerViewController?.let { vc ->
                        vc.player = player
                        vc.view.setNeedsLayout()
                        vc.view.layoutIfNeeded()
                        println("AVPlayerVideoRenderer: Refreshed AVPlayerViewController view (long wait)")
                    }

                    if (config.autoPlay) {
                        player.play()
                    }

                    isRetrying = false
                } else {
                    isRetrying = false
                    playerState = PlayerState.Error("Invalid URL: $streamUrl")
                }
            }
        } else {
            isRetrying = false
            playerState = PlayerState.Error(reason)
        }
    }
    
    // Setup player when URL changes
    DisposableEffect(streamUrl) {
        playerState = PlayerState.Connecting
        hasReportedFirstFrame = false
        hasReportedStreamInfo = false
        retryAttempt = 0
        isRetrying = false
        
        val url = NSURL.URLWithString(streamUrl)
        if (url != null) {
            val playerItem = AVPlayerItem(uRL = url)
            player.replaceCurrentItemWithPlayerItem(playerItem)
            playerState = PlayerState.Loading
            
            // Observe AVPlayerItem failure notification
            val failedObserver = NSNotificationCenter.defaultCenter.addObserverForName(
                name = AVPlayerItemFailedToPlayToEndTimeNotification,
                `object` = null,
                queue = NSOperationQueue.mainQueue
            ) { notification ->
                val error = notification?.userInfo
                    ?.get(AVPlayerItemFailedToPlayToEndTimeErrorKey) as? NSError
                val message = error?.localizedDescription ?: "Playback failed"
                println("AVPlayerVideoRenderer: Failed to play to end - $message")
                retryPlayback(message, isDisconnect = true)
            }
            
            // Observe playback stalled
            val stalledObserver = NSNotificationCenter.defaultCenter.addObserverForName(
                name = AVPlayerItemPlaybackStalledNotification,
                `object` = null,
                queue = NSOperationQueue.mainQueue
            ) { _ ->
                println("AVPlayerVideoRenderer: Playback stalled")
                if (playerState is PlayerState.Playing || playerState is PlayerState.Loading) {
                    playerState = PlayerState.Buffering()
                }

                stalledTimeoutJob?.cancel()
                stalledTimeoutJob = scope.launch {
                    kotlinx.coroutines.delay(5000)
                    if (playerState is PlayerState.Buffering) {
                        println("AVPlayerVideoRenderer: Stalled timeout, triggering reconnect")
                        retryPlayback("Playback stalled timeout", isDisconnect = true)
                    }
                }
            }
            
            // Poll for status changes
            val statusCheckTimer = NSTimer.scheduledTimerWithTimeInterval(
                interval = 1.0,
                repeats = true
            ) { _ ->
                val currentItem = player.currentItem ?: return@scheduledTimerWithTimeInterval
                val status = currentItem.status
                val timeControlStatus = player.timeControlStatus

                when (status) {
                    AVPlayerItemStatusFailed -> {
                        val error = currentItem.error
                        val message = error?.localizedDescription ?: "Player item failed"
                        println("AVPlayerVideoRenderer: AVPlayerItem status = failed - $message")
                        retryPlayback(message, isDisconnect = false)
                    }
                    AVPlayerItemStatusReadyToPlay -> {
                        if (playerState == PlayerState.Connecting || playerState == PlayerState.Loading) {
                            println("AVPlayerVideoRenderer: AVPlayerItem ready, timeControlStatus=$timeControlStatus")
                            if (config.autoPlay && timeControlStatus != AVPlayerTimeControlStatusPlaying) {
                                println("AVPlayerVideoRenderer: Triggering play() after ready")
                                player.play()
                            }
                        }
                    }
                    AVPlayerItemStatusUnknown -> {
                        if (playerState != PlayerState.Loading &&
                            playerState !is PlayerState.Reconnecting &&
                            playerState !is PlayerState.Error) {
                            playerState = PlayerState.Loading
                        }
                    }
                    else -> {}
                }
            }
            
            var lastKnownTime: Double = -1.0

            val interval = CMTimeMakeWithSeconds(0.5, 600)
            val timeObserver = player.addPeriodicTimeObserverForInterval(interval, null) { time ->
                val currentTime = CMTimeGetSeconds(time)
                val currentItem = player.currentItem
                val itemStatus = currentItem?.status
                val timeControlStatus = player.timeControlStatus

                val isActuallyPlaying = currentTime > 0 &&
                    itemStatus == AVPlayerItemStatusReadyToPlay &&
                    timeControlStatus == AVPlayerTimeControlStatusPlaying &&
                    (lastKnownTime < 0 || currentTime != lastKnownTime)

                if (isActuallyPlaying && playerState != PlayerState.Playing) {
                    println("AVPlayerVideoRenderer: Playback confirmed - time=$currentTime, status=$itemStatus, timeControl=$timeControlStatus")
                    playerState = PlayerState.Playing
                    retryAttempt = 0
                    isRetrying = false
                    stalledTimeoutJob?.cancel()
                    stalledTimeoutJob = null

                    if (!hasReportedFirstFrame) {
                        hasReportedFirstFrame = true
                        onEvent(PlayerEvent.FirstFrameRendered(currentTimeMillis()))
                    }

                    if (!hasReportedStreamInfo) {
                        hasReportedStreamInfo = true
                        val tracks = currentItem?.asset?.tracks ?: emptyList<AVAssetTrack>()
                        val videoTrack = tracks.filterIsInstance<AVAssetTrack>()
                            .firstOrNull { (it.mediaType as? String) == AVMediaTypeVideo }

                        val size = videoTrack?.naturalSize
                        val streamInfo = StreamInfo(
                            width = size?.useContents { width.toInt() } ?: 0,
                            height = size?.useContents { height.toInt() } ?: 0,
                            codec = "HLS",
                            fps = videoTrack?.nominalFrameRate ?: 0f,
                            protocol = "HLS (fallback from ${config.protocol.name})"
                        )
                        onEvent(PlayerEvent.StreamInfoReceived(streamInfo))
                    }
                }

                lastKnownTime = currentTime
            }
            
            if (config.autoPlay) {
                player.play()
            }
            
            onDispose {
                retryJob?.cancel()
                stalledTimeoutJob?.cancel()
                player.pause()
                player.removeTimeObserver(timeObserver)
                statusCheckTimer.invalidate()
                NSNotificationCenter.defaultCenter.removeObserver(failedObserver)
                NSNotificationCenter.defaultCenter.removeObserver(stalledObserver)
                player.replaceCurrentItemWithPlayerItem(null)
            }
        } else {
            playerState = PlayerState.Error("Invalid URL: $streamUrl")
            
            onDispose {
                player.pause()
                player.replaceCurrentItemWithPlayerItem(null)
            }
        }
    }
    
    UIKitView(
        factory = {
            val vc = AVPlayerViewController().apply {
                this.player = player
                this.showsPlaybackControls = config.showControls
                this.videoGravity = AVLayerVideoGravityResizeAspect
            }
            playerViewController = vc

            vc.view.apply {
                backgroundColor = UIColor.blackColor
            }
        },
        modifier = modifier,
        update = { view ->
            playerViewController?.let { vc ->
                if (vc.player !== player) {
                    println("AVPlayerVideoRenderer: Re-associating player with AVPlayerViewController")
                    vc.player = player
                }
            }
        },
        onRelease = { view ->
            player.pause()
            playerViewController = null
        }
    )
}

/**
 * iOS implementation of VideoPlayerController using AVPlayer.
 */
@OptIn(ExperimentalForeignApi::class)
private class IOSVideoPlayerController(
    private val player: AVPlayer
) : VideoPlayerController {
    
    override fun play() {
        player.play()
    }
    
    override fun pause() {
        player.pause()
    }
    
    override fun stop() {
        player.pause()
        player.seekToTime(CMTimeMakeWithSeconds(0.0, 600))
    }
    
    override fun seekTo(positionMs: Long) {
        val time = CMTimeMakeWithSeconds(positionMs / 1000.0, 600)
        player.seekToTime(time)
    }
    
    override val currentPosition: Long
        get() {
            val time = player.currentTime()
            return (CMTimeGetSeconds(time) * 1000).toLong()
        }
    
    override val duration: Long
        get() {
            val item = player.currentItem ?: return 0L
            val duration = item.duration
            val seconds = CMTimeGetSeconds(duration)
            if (seconds.isNaN() || seconds.isInfinite()) return 0L
            return (seconds * 1000).toLong()
        }
    
    override val isPlaying: Boolean
        get() = player.timeControlStatus == AVPlayerTimeControlStatusPlaying
}

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun rememberVideoPlayerController(config: StreamConfig): VideoPlayerController {
    val player = remember { AVPlayer() }
    
    DisposableEffect(Unit) {
        onDispose {
            player.pause()
        }
    }
    
    return remember(player) {
        IOSVideoPlayerController(player)
    }
}
