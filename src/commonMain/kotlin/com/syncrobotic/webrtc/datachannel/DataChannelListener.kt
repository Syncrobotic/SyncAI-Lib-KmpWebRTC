package com.syncrobotic.webrtc.datachannel

/**
 * State of a WebRTC Data Channel.
 */
enum class DataChannelState {
    /** The data channel is connecting. */
    CONNECTING,
    
    /** The data channel is open and ready for communication. */
    OPEN,
    
    /** The data channel is closing. */
    CLOSING,
    
    /** The data channel is closed. */
    CLOSED
}

/**
 * Listener interface for Data Channel events.
 */
interface DataChannelListener {
    /**
     * Called when the data channel state changes.
     * @param state The new state of the data channel.
     */
    fun onStateChanged(state: DataChannelState)

    /**
     * Called when a text message is received on the data channel.
     * @param message The received text message.
     */
    fun onMessage(message: String)

    /**
     * Called when a binary message is received on the data channel.
     * Use this for receiving images, files, or other binary data.
     * @param data The received binary data as ByteArray.
     */
    fun onBinaryMessage(data: ByteArray) {}

    /**
     * Called when the buffered amount (pending send data) changes.
     * Useful for implementing flow control.
     * @param bufferedAmount The current amount of buffered data in bytes.
     */
    fun onBufferedAmountChange(bufferedAmount: Long) {}

    /**
     * Called when an error occurs on the data channel.
     * @param error The error that occurred.
     */
    fun onError(error: Throwable) {}
}

/**
 * Simple adapter for DataChannelListener that provides default empty implementations.
 * Override only the methods you need.
 */
open class DataChannelListenerAdapter : DataChannelListener {
    override fun onStateChanged(state: DataChannelState) {}
    override fun onMessage(message: String) {}
    override fun onBinaryMessage(data: ByteArray) {}
    override fun onBufferedAmountChange(bufferedAmount: Long) {}
    override fun onError(error: Throwable) {}
}
