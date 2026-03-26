package com.syncrobotic.webrtc.datachannel

/**
 * A WebRTC Data Channel for bi-directional text/JSON messaging.
 * 
 * Example usage:
 * ```kotlin
 * // Create data channel
 * val channel = webrtcClient.createDataChannel(
 *     DataChannelConfig.reliable("messages")
 * )
 * 
 * // Set listener
 * channel.setListener(object : DataChannelListener {
 *     override fun onStateChanged(state: DataChannelState) {
 *         println("Data channel state: $state")
 *     }
 *     
 *     override fun onMessage(message: String) {
 *         println("Received: $message")
 *         // Parse JSON if needed
 *         val json = Json.parseToJsonElement(message)
 *     }
 * })
 * 
 * // Send messages
 * channel.send("Hello, World!")
 * channel.send(Json.encodeToString(myData))
 * ```
 */
expect class DataChannel {
    /**
     * The label (name) of this data channel.
     */
    val label: String

    /**
     * The unique ID of this data channel.
     */
    val id: Int

    /**
     * The current state of this data channel.
     */
    val state: DataChannelState

    /**
     * The amount of data currently buffered for sending in bytes.
     */
    val bufferedAmount: Long

    /**
     * Sets the listener for data channel events.
     * Only one listener can be set at a time.
     * @param listener The listener to receive events, or null to remove the current listener.
     */
    fun setListener(listener: DataChannelListener?)

    /**
     * Sends a text message through the data channel.
     * 
     * @param message The text message to send. Can be plain text or JSON string.
     * @return true if the message was queued for sending, false if the channel is not open.
     */
    fun send(message: String): Boolean

    /**
     * Sends binary data through the data channel.
     * Use this for sending images, files, or other binary data.
     * 
     * @param data The binary data to send as ByteArray.
     * @return true if the data was queued for sending, false if the channel is not open.
     */
    fun sendBinary(data: ByteArray): Boolean

    /**
     * Closes the data channel.
     * After closing, no more messages can be sent or received.
     */
    fun close()

    companion object
}
