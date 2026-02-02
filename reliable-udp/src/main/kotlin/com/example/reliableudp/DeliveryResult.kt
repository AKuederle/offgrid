package com.example.reliableudp

/**
 * Result of a send operation.
 */
sealed class DeliveryResult {
    /**
     * Message was acknowledged by the receiver.
     *
     * @property messageId Unique identifier of the delivered message
     * @property deliveredAt Timestamp when acknowledgment was received
     */
    data class Success(
        val messageId: Int,
        val deliveredAt: Long = System.currentTimeMillis()
    ) : DeliveryResult()

    /**
     * Message delivery failed.
     *
     * @property messageId Unique identifier of the failed message
     * @property reason Why the delivery failed
     * @property retryCount Number of retransmission attempts made
     */
    data class Failure(
        val messageId: Int,
        val reason: FailureReason,
        val retryCount: Int
    ) : DeliveryResult()

    /**
     * Reasons why message delivery can fail.
     */
    enum class FailureReason {
        /** All retry attempts exhausted without acknowledgment */
        MAX_RETRIES_EXCEEDED,

        /** Overall send timeout exceeded */
        TIMEOUT,

        /** Socket was closed during send */
        SOCKET_CLOSED
    }
}
