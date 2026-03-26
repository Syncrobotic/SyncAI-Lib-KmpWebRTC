package com.syncrobotic.webrtc

import dev.onvoid.webrtc.PeerConnectionFactory

/**
 * Singleton manager for PeerConnectionFactory on JVM.
 * 
 * webrtc-java native library has global state, so having multiple PeerConnectionFactory
 * instances can cause crashes when one is disposed while others are still in use.
 * 
 * Unlike Android, JVM's PeerConnectionFactory doesn't have EglContext binding issues,
 * so sharing is safe. This manager ensures:
 * 1. All WebRTC connections share a single PeerConnectionFactory
 * 2. The factory is only disposed when all connections are closed (refCount == 0)
 * 3. Thread-safe access via synchronized blocks
 * 
 * Note: API methods are named similar to Android/iOS for consistency, but internally
 * shares a single factory instance.
 */
object PeerConnectionFactoryManager {
    private var factory: PeerConnectionFactory? = null
    private var refCount = 0
    private val lock = Any()
    
    /**
     * Create/acquire a reference to the shared PeerConnectionFactory.
     * On JVM, factory is shared across all connections (safe due to no EglContext issues).
     * 
     * @return The shared PeerConnectionFactory
     * @throws IllegalStateException if factory creation fails
     */
    fun createFactory(): PeerConnectionFactory {
        synchronized(lock) {
            if (factory == null) {
                try {
                    factory = PeerConnectionFactory()
                    println("[PeerConnectionFactoryManager] Created new PeerConnectionFactory")
                } catch (e: Exception) {
                    println("WARNING: PeerConnectionFactory initialization failed: ${e.message}")
                    println("If on macOS, you may need to grant Screen Recording permission in System Settings")
                    throw IllegalStateException(
                        "Failed to initialize WebRTC PeerConnectionFactory. " +
                        "On macOS, ensure the app has Screen Recording permission in System Settings > Privacy & Security.",
                        e
                    )
                }
            }
            refCount++
            println("[PeerConnectionFactoryManager] Acquired factory, refCount=$refCount")
            return factory!!
        }
    }
    
    /**
     * Dispose/release a reference to the shared PeerConnectionFactory.
     * Disposes the factory when refCount reaches 0.
     * 
     * @param factoryToDispose The factory to release (ignored on JVM since we share one factory)
     */
    fun disposeFactory(factoryToDispose: PeerConnectionFactory?) {
        synchronized(lock) {
            if (refCount > 0) {
                refCount--
                println("[PeerConnectionFactoryManager] Released factory, refCount=$refCount")
                
                if (refCount == 0 && factory != null) {
                    try {
                        // Add a small delay before disposing to let any pending native operations complete
                        Thread.sleep(100)
                        factory?.dispose()
                        println("[PeerConnectionFactoryManager] Disposed factory (no more references)")
                    } catch (e: Exception) {
                        println("[PeerConnectionFactoryManager] Error disposing factory: ${e.message}")
                    }
                    factory = null
                }
            } else {
                println("[PeerConnectionFactoryManager] Warning: disposeFactory() called when refCount is already 0")
            }
        }
    }
    
    /**
     * Get the current reference count (for debugging).
     */
    fun getRefCount(): Int = synchronized(lock) { refCount }
    
    /**
     * Check if a factory currently exists (for debugging).
     */
    fun hasFactory(): Boolean = synchronized(lock) { factory != null }
}
