package com.syncrobotic.webrtc.datachannel

import cocoapods.GoogleWebRTC.RTCDataBuffer
import cocoapods.GoogleWebRTC.RTCDataChannel
import cocoapods.GoogleWebRTC.RTCDataChannelDelegateProtocol
import cocoapods.GoogleWebRTC.RTCDataChannelState
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCSignatureOverride
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding
import platform.darwin.NSObject
import platform.posix.memcpy

/**
 * Extension function to convert NSData to ByteArray.
 */
@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray {
    val length = this.length.toInt()
    if (length == 0) return ByteArray(0)
    
    val bytes = ByteArray(length)
    bytes.usePinned { pinned ->
        memcpy(pinned.addressOf(0), this.bytes, this.length)
    }
    return bytes
}

/**
 * iOS implementation of DataChannel using GoogleWebRTC.
 */
@OptIn(ExperimentalForeignApi::class)
actual class DataChannel(
    private val nativeChannel: RTCDataChannel
) {
    private var listener: DataChannelListener? = null
    private val delegate: DataChannelDelegate

    actual val label: String
        get() = nativeChannel.label() ?: ""

    actual val id: Int
        get() = nativeChannel.channelId()

    actual val state: DataChannelState
        get() = when (nativeChannel.readyState()) {
            RTCDataChannelState.RTCDataChannelStateConnecting -> DataChannelState.CONNECTING
            RTCDataChannelState.RTCDataChannelStateOpen -> DataChannelState.OPEN
            RTCDataChannelState.RTCDataChannelStateClosing -> DataChannelState.CLOSING
            RTCDataChannelState.RTCDataChannelStateClosed -> DataChannelState.CLOSED
            else -> DataChannelState.CLOSED
        }

    actual val bufferedAmount: Long
        get() = nativeChannel.bufferedAmount().toLong()

    init {
        delegate = DataChannelDelegate(this)
        nativeChannel.setDelegate(delegate)
    }

    internal fun notifyStateChanged() {
        listener?.onStateChanged(state)
    }

    internal fun notifyMessage(message: String) {
        listener?.onMessage(message)
    }

    internal fun notifyBinaryMessage(data: ByteArray) {
        listener?.onBinaryMessage(data)
    }

    internal fun notifyBufferedAmountChange() {
        listener?.onBufferedAmountChange(bufferedAmount)
    }

    actual fun setListener(listener: DataChannelListener?) {
        this.listener = listener
        // Replay current state so caller doesn't miss events fired before listener was set
        if (listener != null && state != DataChannelState.CONNECTING) {
            listener.onStateChanged(state)
        }
    }

    actual fun send(message: String): Boolean {
        if (state != DataChannelState.OPEN) return false

        @Suppress("CAST_NEVER_SUCCEEDS")
        val data = (message as NSString).dataUsingEncoding(NSUTF8StringEncoding)
            ?: return false
        
        val buffer = RTCDataBuffer(data = data, isBinary = false)
        return nativeChannel.sendData(buffer)
    }

    @OptIn(ExperimentalForeignApi::class)
    actual fun sendBinary(data: ByteArray): Boolean {
        if (state != DataChannelState.OPEN) return false

        val nsData = data.usePinned { pinned ->
            NSData.create(bytes = pinned.addressOf(0), length = data.size.toULong())
        }
        
        val buffer = RTCDataBuffer(data = nsData, isBinary = true)
        return nativeChannel.sendData(buffer)
    }

    actual fun close() {
        nativeChannel.setDelegate(null)
        nativeChannel.close()
    }

    actual companion object {
        /**
         * Creates a DataChannel from native RTCDataChannel.
         * Used internally by WebRTCClient.
         */
        internal fun create(nativeChannel: RTCDataChannel): DataChannel {
            return DataChannel(nativeChannel)
        }
    }
}

/**
 * Delegate for RTCDataChannel events.
 */
@OptIn(ExperimentalForeignApi::class)
private class DataChannelDelegate(
    private val channel: DataChannel
) : NSObject(), RTCDataChannelDelegateProtocol {

    @ObjCSignatureOverride
    override fun dataChannelDidChangeState(dataChannel: RTCDataChannel) {
        channel.notifyStateChanged()
    }

    @ObjCSignatureOverride
    override fun dataChannel(dataChannel: RTCDataChannel, didReceiveMessageWithBuffer: RTCDataBuffer) {
        val data = didReceiveMessageWithBuffer.data()
        if (didReceiveMessageWithBuffer.isBinary()) {
            // Binary message (images, files, etc.)
            val bytes = data.toByteArray()
            channel.notifyBinaryMessage(bytes)
        } else {
            // Text message
            val message = NSString.create(data = data, encoding = NSUTF8StringEncoding)?.toString()
            if (message != null) {
                channel.notifyMessage(message)
            }
        }
    }

    override fun dataChannel(dataChannel: RTCDataChannel, didChangeBufferedAmount: ULong) {
        channel.notifyBufferedAmountChange()
    }
}
