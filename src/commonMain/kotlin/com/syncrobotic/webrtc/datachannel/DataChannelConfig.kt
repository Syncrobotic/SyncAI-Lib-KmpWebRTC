package com.syncrobotic.webrtc.datachannel

/**
 * Configuration for creating a WebRTC Data Channel.
 */
data class DataChannelConfig(
    /**
     * The label (name) for the data channel.
     */
    val label: String,

    /**
     * Ordered delivery ensures messages arrive in the same order they were sent.
     * Default is true.
     */
    val ordered: Boolean = true,

    /**
     * Maximum number of retransmit attempts before giving up.
     * Set to null for reliable delivery (unlimited retransmits).
     * Mutually exclusive with [maxPacketLifeTimeMs].
     */
    val maxRetransmits: Int? = null,

    /**
     * Maximum time in milliseconds to attempt retransmission.
     * Set to null for reliable delivery.
     * Mutually exclusive with [maxRetransmits].
     */
    val maxPacketLifeTimeMs: Int? = null,

    /**
     * The protocol to use for this data channel.
     * Empty string means no protocol.
     */
    val protocol: String = "",

    /**
     * Whether negotiation is done out-of-band (true) or in-band (false).
     * Default is false (in-band negotiation).
     */
    val negotiated: Boolean = false,

    /**
     * If [negotiated] is true, this is the ID to use for the channel.
     * Both peers must use the same ID for the channel to be connected.
     */
    val id: Int? = null
) {
    init {
        require(!(maxRetransmits != null && maxPacketLifeTimeMs != null)) {
            "maxRetransmits and maxPacketLifeTimeMs are mutually exclusive"
        }
        require(!negotiated || id != null) {
            "id must be set when negotiated is true"
        }
    }

    companion object {
        /**
         * Creates a reliable, ordered data channel configuration (default).
         */
        fun reliable(label: String): DataChannelConfig = DataChannelConfig(
            label = label,
            ordered = true
        )

        /**
         * Creates an unreliable (best-effort) data channel configuration.
         * Messages may be lost or arrive out of order but with lower latency.
         */
        fun unreliable(
            label: String,
            maxRetransmits: Int = 0
        ): DataChannelConfig = DataChannelConfig(
            label = label,
            ordered = false,
            maxRetransmits = maxRetransmits
        )

        /**
         * Creates a data channel with a maximum packet lifetime.
         * Messages older than the specified time will be dropped.
         */
        fun maxLifetime(
            label: String,
            maxPacketLifeTimeMs: Int
        ): DataChannelConfig = DataChannelConfig(
            label = label,
            ordered = false,
            maxPacketLifeTimeMs = maxPacketLifeTimeMs
        )
    }
}
