package com.example.reliableudp.receiver

/**
 * Reassembles message fragments into complete messages.
 *
 * Handles out-of-order fragment arrival and incomplete message cleanup.
 */
interface FragmentBuffer {
    /**
     * Add a received fragment.
     *
     * @param messageId Identifier of the logical message
     * @param fragmentIndex 1-based position of this fragment
     * @param fragmentTotal Total number of fragments in the message
     * @param payload Fragment payload (without header)
     * @return Complete message payload if all fragments received, null otherwise
     */
    fun addFragment(
        messageId: Int,
        fragmentIndex: Int,
        fragmentTotal: Int,
        payload: ByteArray
    ): ByteArray?

    /**
     * Remove stale incomplete messages older than timeout.
     *
     * Called periodically to prevent memory leaks from lost fragments.
     *
     * @param timeoutMs Maximum age for incomplete messages in milliseconds
     * @return Number of messages discarded
     */
    fun cleanupStale(timeoutMs: Long): Int

    /**
     * Current number of messages being reassembled.
     */
    val pendingMessageCount: Int
}
