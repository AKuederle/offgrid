package com.example.udpservice.send

/**
 * Interface for tracking message delivery status and handling delivery callbacks.
 *
 * Responsibilities:
 * - Process delivery success/failure callbacks
 * - Determine retry decisions for failed messages
 * - Update message status in the queue
 */
interface DeliveryTracker {
    /**
     * Handle successful delivery of a message.
     *
     * @param messageId The ID of the delivered message
     */
    suspend fun onDeliverySuccess(messageId: Long)

    /**
     * Handle failed delivery of a message.
     *
     * Increments retry count and updates status based on retry policy.
     *
     * @param messageId The ID of the failed message
     */
    suspend fun onDeliveryFailure(messageId: Long)

    /**
     * Check the retry decision for a message.
     *
     * @param message The message to check
     * @return RetryDecision indicating what action to take
     */
    fun checkRetryDecision(message: OutboundMessage): RetryDecision

    /**
     * Prepare a message for sending by updating its status.
     *
     * @param messageId The ID of the message to prepare
     */
    suspend fun prepareForSending(messageId: Long)

    /**
     * Move a message to WAITING status after exhausting retries.
     *
     * @param message The message to move to waiting
     */
    suspend fun moveToWaiting(message: OutboundMessage)
}

/**
 * Default implementation of [DeliveryTracker].
 *
 * Uses [SendQueue] for persistence and [RetryScheduler] for retry decisions.
 */
class DeliveryTrackerImpl(
    private val sendQueue: SendQueue,
    private val retryScheduler: RetryScheduler
) : DeliveryTracker {

    override suspend fun onDeliverySuccess(messageId: Long) {
        sendQueue.markDelivered(messageId)
    }

    override suspend fun onDeliveryFailure(messageId: Long) {
        val message = sendQueue.get(messageId) ?: return

        val newRetryCount = message.retryCount + 1
        val decision = retryScheduler.shouldRetry(
            retryCount = newRetryCount,
            lastAttemptAt = System.currentTimeMillis(),
            createdAt = message.createdAt,
            currentTime = System.currentTimeMillis()
        )

        when (decision) {
            is RetryDecision.ExhaustedRetries -> {
                sendQueue.updateStatus(messageId, DeliveryStatus.WAITING, retryCount = newRetryCount)
            }
            else -> {
                sendQueue.updateStatus(messageId, DeliveryStatus.RETRYING, retryCount = newRetryCount)
            }
        }
    }

    override fun checkRetryDecision(message: OutboundMessage): RetryDecision {
        val lastAttemptAt = message.lastAttemptAt ?: message.createdAt
        return retryScheduler.shouldRetry(
            retryCount = message.retryCount,
            lastAttemptAt = lastAttemptAt,
            createdAt = message.createdAt,
            currentTime = System.currentTimeMillis()
        )
    }

    override suspend fun prepareForSending(messageId: Long) {
        sendQueue.updateStatus(messageId, DeliveryStatus.SENDING, null)
    }

    override suspend fun moveToWaiting(message: OutboundMessage) {
        sendQueue.updateStatus(message.id, DeliveryStatus.WAITING, retryCount = message.retryCount)
    }
}
