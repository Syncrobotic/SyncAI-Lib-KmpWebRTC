package com.syncrobotic.webrtc

import cocoapods.GoogleWebRTC.*
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSLock

/**
 * Singleton manager for WebRTC initialization on iOS.
 * 
 * While iOS uses ARC for memory management, each connection creates its own
 * RTCPeerConnectionFactory for isolation and consistency with Android behavior.
 * 
 * This manager ensures:
 * 1. RTCInitializeSSL() is called only once (global WebRTC init)
 * 2. Tracks active connection count to safely manage SSL cleanup
 * 3. Each connection creates its OWN Factory (no sharing)
 * 4. Thread-safe access
 */
@OptIn(ExperimentalForeignApi::class)
object PeerConnectionFactoryManager {
    private var activeConnections = 0
    private var isInitialized = false
    private val lock = NSLock()
    
    private inline fun <T> withLock(block: () -> T): T {
        lock.lock()
        try {
            return block()
        } finally {
            lock.unlock()
        }
    }
    
    /**
     * Initialize SSL (call once before first use).
     * Safe to call multiple times - will only initialize once.
     */
    fun ensureInitialized() {
        withLock {
            if (!isInitialized) {
                RTCInitializeSSL()
                isInitialized = true
                println("[PeerConnectionFactoryManager] iOS: SSL initialized")
            }
        }
    }
    
    /**
     * Create a new RTCPeerConnectionFactory.
     * Each caller gets their own Factory for isolation.
     * 
     * @return A new RTCPeerConnectionFactory instance
     */
    fun createFactory(): RTCPeerConnectionFactory {
        return withLock {
            val encoderFactory = RTCDefaultVideoEncoderFactory()
            val decoderFactory = RTCDefaultVideoDecoderFactory()
            
            val factory = RTCPeerConnectionFactory(
                encoderFactory = encoderFactory,
                decoderFactory = decoderFactory
            )
            
            activeConnections++
            println("[PeerConnectionFactoryManager] iOS: Created new RTCPeerConnectionFactory, activeConnections=$activeConnections")
            factory
        }
    }
    
    /**
     * Dispose a RTCPeerConnectionFactory and update active connection count.
     * Cleans up SSL when no more connections remain.
     * 
     * @param factory The factory to dispose (caller is responsible for passing their own factory)
     */
    fun disposeFactory(factory: RTCPeerConnectionFactory?) {
        withLock {
            if (factory != null && activeConnections > 0) {
                // On iOS, ARC handles memory management - just release reference
                activeConnections--
                println("[PeerConnectionFactoryManager] iOS: Disposed factory, activeConnections=$activeConnections")
                
                // Clean up SSL when no more connections
                if (activeConnections == 0) {
                    RTCCleanupSSL()
                    isInitialized = false
                    println("[PeerConnectionFactoryManager] iOS: SSL cleaned up (no more connections)")
                }
            } else if (activeConnections <= 0) {
                println("[PeerConnectionFactoryManager] iOS: Warning - disposeFactory() called but activeConnections is already 0")
            }
        }
    }
    
    /**
     * Get the current active connection count (for debugging).
     */
    fun getActiveConnections(): Int = withLock { activeConnections }
}
