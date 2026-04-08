package com.syncrobotic.webrtc

import android.content.Context
import android.util.Log
import org.webrtc.*

/**
 * Singleton manager for WebRTC initialization on Android.
 * 
 * IMPORTANT: On Android, PeerConnectionFactory instances with video support are tied to
 * specific EglContext. Sharing a Factory across connections with different EglContexts
 * causes video rendering to fail (black screen).
 * 
 * This manager ensures:
 * 1. PeerConnectionFactory.initialize() is called only once (global WebRTC init)
 * 2. Tracks active connection count to safely manage cleanup
 * 3. Each connection creates its OWN Factory (no sharing) to avoid EglContext conflicts
 * 4. Thread-safe access via synchronized blocks
 * 
 * Why we DON'T share Factory on Android (unlike JVM):
 * - VideoEncoder/Decoder are bound to specific EglContext at Factory creation time
 * - Different video renderers have different EglContexts
 * - Sharing Factory → video decodes to wrong GL context → black screen
 */
object PeerConnectionFactoryManager {
    private const val TAG = "PCFactoryManager"
    
    private var activeConnections = 0
    private var isInitialized = false
    private val lock = Any()
    
    /**
     * Initialize the WebRTC library (call once before first use).
     * Safe to call multiple times - will only initialize once.
     * 
     * @param context Application context
     */
    fun ensureInitialized(context: Context) {
        synchronized(lock) {
            if (!isInitialized) {
                val options = PeerConnectionFactory.InitializationOptions.builder(context.applicationContext)
                    .setEnableInternalTracer(false)
                    .createInitializationOptions()
                PeerConnectionFactory.initialize(options)
                isInitialized = true
                Log.d(TAG, "WebRTC library initialized")
            }
        }
    }
    
    /**
     * Create a NEW PeerConnectionFactory for video (receiving).
     * Each caller gets their own Factory to avoid EglContext conflicts.
     * 
     * @param eglBase EglBase for video encoding/decoding (caller's own context)
     * @return A new PeerConnectionFactory instance
     */
    fun createForVideo(eglBase: EglBase): PeerConnectionFactory {
        synchronized(lock) {
            val encoderFactory = DefaultVideoEncoderFactory(
                eglBase.eglBaseContext,
                true,
                true
            )
            val decoderFactory = DefaultVideoDecoderFactory(eglBase.eglBaseContext)
            
            val factory = PeerConnectionFactory.builder()
                .setVideoEncoderFactory(encoderFactory)
                .setVideoDecoderFactory(decoderFactory)
                .createPeerConnectionFactory()
            
            activeConnections++
            Log.d(TAG, "Created new PeerConnectionFactory (video), activeConnections=$activeConnections")
            return factory
        }
    }
    
    /**
     * Create a NEW PeerConnectionFactory for audio only (sending/DataChannel).
     * 
     * @param context Application context
     * @return A new PeerConnectionFactory instance
     */
    fun createForAudio(context: Context): PeerConnectionFactory {
        synchronized(lock) {
            val factory = PeerConnectionFactory.builder()
                .setAudioDeviceModule(
                    org.webrtc.audio.JavaAudioDeviceModule.builder(context.applicationContext)
                        .setUseHardwareAcousticEchoCanceler(true)
                        .setUseHardwareNoiseSuppressor(true)
                        .createAudioDeviceModule()
                )
                .createPeerConnectionFactory()
            
            activeConnections++
            Log.d(TAG, "Created new PeerConnectionFactory (audio), activeConnections=$activeConnections")
            return factory
        }
    }
    
    /**
     * Create a NEW PeerConnectionFactory for video sending (camera + optional audio).
     * Used for SEND_VIDEO, VIDEO_CALL, BIDIRECTIONAL_AUDIO — any mode that sends video.
     *
     * Combines DefaultVideoEncoderFactory + DefaultVideoDecoderFactory + JavaAudioDeviceModule
     * so the factory can handle both video encoding and high-quality audio (AEC/NS).
     *
     * @param context Application context (for AudioDeviceModule)
     * @param eglBase EglBase for video encoding/decoding
     */
    fun createForVideoAndAudio(context: Context, eglBase: EglBase): PeerConnectionFactory {
        synchronized(lock) {
            val encoderFactory = DefaultVideoEncoderFactory(
                eglBase.eglBaseContext,
                true,
                true
            )
            val decoderFactory = DefaultVideoDecoderFactory(eglBase.eglBaseContext)
            val audioModule = org.webrtc.audio.JavaAudioDeviceModule.builder(context.applicationContext)
                .setUseHardwareAcousticEchoCanceler(true)
                .setUseHardwareNoiseSuppressor(true)
                .createAudioDeviceModule()

            val factory = PeerConnectionFactory.builder()
                .setVideoEncoderFactory(encoderFactory)
                .setVideoDecoderFactory(decoderFactory)
                .setAudioDeviceModule(audioModule)
                .createPeerConnectionFactory()

            activeConnections++
            Log.d(TAG, "Created new PeerConnectionFactory (video+audio), activeConnections=$activeConnections")
            return factory
        }
    }

    /**
     * Dispose a PeerConnectionFactory and update active connection count.
     * 
     * @param factory The factory to dispose (caller is responsible for passing their own factory)
     */
    fun disposeFactory(factory: PeerConnectionFactory?) {
        synchronized(lock) {
            if (factory != null && activeConnections > 0) {
                try {
                    factory.dispose()
                    activeConnections--
                    Log.d(TAG, "Disposed factory, activeConnections=$activeConnections")
                } catch (e: Exception) {
                    Log.e(TAG, "Error disposing factory: ${e.message}")
                }
            } else if (activeConnections <= 0) {
                Log.w(TAG, "disposeFactory() called but activeConnections is already 0")
            }
        }
    }
    
    /**
     * Get the current active connection count (for debugging).
     */
    fun getActiveConnections(): Int = synchronized(lock) { activeConnections }
}
