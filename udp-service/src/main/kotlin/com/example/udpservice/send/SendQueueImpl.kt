package com.example.udpservice.send

import android.util.Log
import com.example.reliableudp.DeliveryResult
import com.example.udpservice.persistence.OutboundMessageDao
import com.example.udpservice.persistence.OutboundMessageEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.InetSocketAddress
import kotlin.coroutines.CoroutineContext

/**
 * Type alias for the function that transmits messages via the reliable socket.
 *
 * @param destination Target peer address
 * @param payload Message content
 * @param onResult Callback with delivery result
 * @return Transport message ID (used for correlation, not the same as queue message ID)
 */
typealias MessageTransmitter = (
    destination: InetSocketAddress,
    payload: ByteArray,
    onResult: (DeliveryResult) -> Unit
) -> Int

/**
 * Implementation of SendQueue with Room persistence and automatic retry.
 *
 * Persists messages to database before any transmission attempt.
 * Provides automatic retry with exponential backoff.
 */
class SendQueueImpl(
    private val dao: OutboundMessageDao,
    private val retryScheduler: RetryScheduler = RetrySchedulerImpl(),
    private val coroutineContext: CoroutineContext = kotlinx.coroutines.Dispatchers.IO
) : SendQueue {

    companion object {
        private const val TAG = "SendQueueImpl"
        private const val MAX_PAYLOAD_SIZE = 65536 // 64KB
        private const val RETRY_LOOP_INTERVAL_MS = 500L // Check for retries every 500ms
    }

    private var scope: CoroutineScope? = null
    private var retryJob: Job? = null

    /**
     * The transmitter function used to send messages.
     * Must be set before calling start().
     */
    var transmitter: MessageTransmitter? = null

    /**
     * Maps transport message IDs to queue message IDs for callback correlation.
     */
    private val transportToQueueId = mutableMapOf<Int, Long>()

    override suspend fun enqueue(peer: InetSocketAddress, payload: ByteArray): SendResult {
        if (payload.size > MAX_PAYLOAD_SIZE) {
            return SendResult.Failed("Payload exceeds 64KB limit")
        }

        return try {
            val entity = OutboundMessageEntity(
                peerHost = peer.hostString,
                peerPort = peer.port,
                payload = payload,
                status = DeliveryStatus.PENDING,
                retryCount = 0,
                createdAt = System.currentTimeMillis()
            )
            val id = dao.insert(entity)
            Log.d(TAG, "Enqueued message $id to ${peer.hostString}:${peer.port}")
            SendResult.Queued(id)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to enqueue message", e)
            SendResult.Failed("Failed to persist message: ${e.message}")
        }
    }

    override suspend fun get(messageId: Long): OutboundMessage? {
        return dao.getById(messageId)?.toOutboundMessage()
    }

    override suspend fun getPendingForPeer(peer: InetSocketAddress): List<OutboundMessage> {
        return dao.getPendingForPeer(peer.hostString, peer.port)
            .map { it.toOutboundMessage() }
    }

    override suspend fun updateStatus(
        messageId: Long,
        status: DeliveryStatus,
        retryCount: Int?
    ) {
        if (retryCount != null) {
            dao.updateStatusWithRetry(
                id = messageId,
                status = status,
                retryCount = retryCount,
                lastAttemptAt = System.currentTimeMillis()
            )
        } else {
            dao.updateStatus(messageId, status)
        }
    }

    override suspend fun markDelivered(messageId: Long) {
        dao.markDelivered(messageId, System.currentTimeMillis())
        Log.d(TAG, "Message $messageId delivered")
    }

    override suspend fun cancel(messageId: Long): Boolean {
        val entity = dao.getById(messageId) ?: return false
        if (entity.status == DeliveryStatus.DELIVERED) {
            return false
        }
        val deleted = dao.delete(messageId) > 0
        if (deleted) {
            Log.d(TAG, "Message $messageId cancelled")
        }
        return deleted
    }

    override suspend fun resumeForPeer(peer: InetSocketAddress): Int {
        val count = dao.resumeForPeer(peer.hostString, peer.port)
        if (count > 0) {
            Log.d(TAG, "Resumed $count waiting messages for ${peer.hostString}:${peer.port}")
        }
        return count
    }

    override fun observeAll(): Flow<List<OutboundMessage>> {
        return dao.observeAll().map { entities ->
            entities.map { it.toOutboundMessage() }
        }
    }

    override fun observeForPeer(peer: InetSocketAddress): Flow<List<OutboundMessage>> {
        return dao.observeForPeer(peer.hostString, peer.port).map { entities ->
            entities.map { it.toOutboundMessage() }
        }
    }

    override suspend fun start() {
        if (scope != null) {
            Log.w(TAG, "SendQueue already started")
            return
        }

        val newScope = CoroutineScope(coroutineContext + SupervisorJob())
        scope = newScope

        retryJob = newScope.launch {
            retryLoop()
        }

        Log.d(TAG, "SendQueue started")
    }

    override fun stop() {
        retryJob?.cancel()
        retryJob = null
        scope?.cancel()
        scope = null
        transportToQueueId.clear()
        Log.d(TAG, "SendQueue stopped")
    }

    override suspend fun onPeerActivity(peer: InetSocketAddress) {
        resumeForPeer(peer)
    }

    /**
     * Main retry loop that processes pending and retrying messages.
     */
    private suspend fun retryLoop() {
        Log.d(TAG, "Retry loop started")

        while (scope?.isActive == true) {
            try {
                processPendingMessages()
                processRetryingMessages()
            } catch (e: Exception) {
                Log.e(TAG, "Error in retry loop", e)
            }

            delay(RETRY_LOOP_INTERVAL_MS)
        }

        Log.d(TAG, "Retry loop stopped")
    }

    /**
     * Process messages in PENDING state - attempt first transmission.
     */
    private suspend fun processPendingMessages() {
        val pending = dao.getPendingMessages()
            .filter { it.status == DeliveryStatus.PENDING }
            .map { it.toOutboundMessage() }

        for (message in pending) {
            transmitMessage(message)
        }
    }

    /**
     * Process messages in RETRYING state - check if ready for retry.
     */
    private suspend fun processRetryingMessages() {
        val retrying = dao.getPendingMessages()
            .filter { it.status == DeliveryStatus.RETRYING }
            .map { it.toOutboundMessage() }

        for (message in retrying) {
            val lastAttemptAt = message.lastAttemptAt ?: message.createdAt
            val decision = retryScheduler.shouldRetry(
                retryCount = message.retryCount,
                lastAttemptAt = lastAttemptAt,
                createdAt = message.createdAt,
                currentTime = System.currentTimeMillis()
            )

            when (decision) {
                is RetryDecision.RetryNow -> {
                    transmitMessage(message)
                }
                is RetryDecision.ExhaustedRetries -> {
                    moveToWaiting(message)
                }
                is RetryDecision.WaitUntil -> {
                    // Not ready yet, skip
                }
            }
        }
    }

    /**
     * Transmit a message using the configured transmitter.
     */
    private suspend fun transmitMessage(message: OutboundMessage) {
        val tx = transmitter
        if (tx == null) {
            Log.w(TAG, "No transmitter configured, skipping message ${message.id}")
            return
        }

        // Update status to SENDING
        updateStatus(message.id, DeliveryStatus.SENDING, null)
        Log.d(TAG, "Sending message ${message.id} to ${message.peer.hostString}:${message.peer.port} (attempt ${message.retryCount + 1})")

        try {
            val transportId = tx(message.peer, message.payload) { result ->
                // Handle delivery result on IO thread
                scope?.launch {
                    handleDeliveryResult(message.id, result)
                }
            }

            // Track correlation between transport ID and queue ID
            synchronized(transportToQueueId) {
                transportToQueueId[transportId] = message.id
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to transmit message ${message.id}", e)
            handleTransmissionFailure(message)
        }
    }

    /**
     * Handle delivery result callback from the reliable socket.
     */
    private suspend fun handleDeliveryResult(messageId: Long, result: DeliveryResult) {
        when (result) {
            is DeliveryResult.Success -> {
                markDelivered(messageId)
            }
            is DeliveryResult.Failure -> {
                Log.d(TAG, "Delivery failed for message $messageId: ${result.reason}")
                handleTransmissionFailure(messageId)
            }
        }
    }

    /**
     * Handle transmission failure - update status and retry count.
     */
    private suspend fun handleTransmissionFailure(message: OutboundMessage) {
        handleTransmissionFailure(message.id)
    }

    /**
     * Handle transmission failure - update status and retry count.
     */
    private suspend fun handleTransmissionFailure(messageId: Long) {
        val message = get(messageId) ?: return

        val newRetryCount = message.retryCount + 1
        val decision = retryScheduler.shouldRetry(
            retryCount = newRetryCount,
            lastAttemptAt = System.currentTimeMillis(),
            createdAt = message.createdAt,
            currentTime = System.currentTimeMillis()
        )

        when (decision) {
            is RetryDecision.ExhaustedRetries -> {
                updateStatus(messageId, DeliveryStatus.WAITING, retryCount = newRetryCount)
                Log.d(TAG, "Message $messageId exhausted retries, moved to WAITING")
            }
            else -> {
                updateStatus(messageId, DeliveryStatus.RETRYING, retryCount = newRetryCount)
                Log.d(TAG, "Message $messageId failed, scheduled for retry (count=$newRetryCount)")
            }
        }
    }

    /**
     * Move a message to WAITING status after exhausting retries.
     */
    private suspend fun moveToWaiting(message: OutboundMessage) {
        updateStatus(message.id, DeliveryStatus.WAITING, retryCount = message.retryCount)
        Log.d(TAG, "Message ${message.id} moved to WAITING (timeout exceeded)")
    }

    private fun OutboundMessageEntity.toOutboundMessage(): OutboundMessage {
        return OutboundMessage(
            id = id,
            peer = InetSocketAddress(peerHost, peerPort),
            payload = payload,
            status = status,
            retryCount = retryCount,
            createdAt = createdAt,
            lastAttemptAt = lastAttemptAt,
            deliveredAt = deliveredAt
        )
    }
}
