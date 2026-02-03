package com.example.udpservice.send

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class DeliveryStatusTest {

    @Test
    fun `PENDING can transition to SENDING`() {
        assertTrue(DeliveryStatus.PENDING.canTransitionTo(DeliveryStatus.SENDING))
    }

    @Test
    fun `PENDING cannot transition to other states`() {
        assertFalse(DeliveryStatus.PENDING.canTransitionTo(DeliveryStatus.PENDING))
        assertFalse(DeliveryStatus.PENDING.canTransitionTo(DeliveryStatus.DELIVERED))
        assertFalse(DeliveryStatus.PENDING.canTransitionTo(DeliveryStatus.RETRYING))
        assertFalse(DeliveryStatus.PENDING.canTransitionTo(DeliveryStatus.WAITING))
    }

    @Test
    fun `SENDING can transition to DELIVERED`() {
        assertTrue(DeliveryStatus.SENDING.canTransitionTo(DeliveryStatus.DELIVERED))
    }

    @Test
    fun `SENDING can transition to RETRYING`() {
        assertTrue(DeliveryStatus.SENDING.canTransitionTo(DeliveryStatus.RETRYING))
    }

    @Test
    fun `SENDING cannot transition to other states`() {
        assertFalse(DeliveryStatus.SENDING.canTransitionTo(DeliveryStatus.SENDING))
        assertFalse(DeliveryStatus.SENDING.canTransitionTo(DeliveryStatus.PENDING))
        assertFalse(DeliveryStatus.SENDING.canTransitionTo(DeliveryStatus.WAITING))
    }

    @Test
    fun `RETRYING can transition to SENDING`() {
        assertTrue(DeliveryStatus.RETRYING.canTransitionTo(DeliveryStatus.SENDING))
    }

    @Test
    fun `RETRYING can transition to WAITING`() {
        assertTrue(DeliveryStatus.RETRYING.canTransitionTo(DeliveryStatus.WAITING))
    }

    @Test
    fun `RETRYING cannot transition to other states`() {
        assertFalse(DeliveryStatus.RETRYING.canTransitionTo(DeliveryStatus.RETRYING))
        assertFalse(DeliveryStatus.RETRYING.canTransitionTo(DeliveryStatus.PENDING))
        assertFalse(DeliveryStatus.RETRYING.canTransitionTo(DeliveryStatus.DELIVERED))
    }

    @Test
    fun `WAITING can transition to PENDING`() {
        assertTrue(DeliveryStatus.WAITING.canTransitionTo(DeliveryStatus.PENDING))
    }

    @Test
    fun `WAITING cannot transition to other states`() {
        assertFalse(DeliveryStatus.WAITING.canTransitionTo(DeliveryStatus.WAITING))
        assertFalse(DeliveryStatus.WAITING.canTransitionTo(DeliveryStatus.SENDING))
        assertFalse(DeliveryStatus.WAITING.canTransitionTo(DeliveryStatus.DELIVERED))
        assertFalse(DeliveryStatus.WAITING.canTransitionTo(DeliveryStatus.RETRYING))
    }

    @Test
    fun `DELIVERED is terminal and cannot transition`() {
        for (status in DeliveryStatus.entries) {
            assertFalse(DeliveryStatus.DELIVERED.canTransitionTo(status))
        }
    }

    @Test
    fun `only DELIVERED is terminal`() {
        assertTrue(DeliveryStatus.DELIVERED.isTerminal)
        assertFalse(DeliveryStatus.PENDING.isTerminal)
        assertFalse(DeliveryStatus.SENDING.isTerminal)
        assertFalse(DeliveryStatus.RETRYING.isTerminal)
        assertFalse(DeliveryStatus.WAITING.isTerminal)
    }

    @Test
    fun `PENDING and RETRYING need delivery`() {
        assertTrue(DeliveryStatus.PENDING.needsDelivery)
        assertTrue(DeliveryStatus.RETRYING.needsDelivery)
    }

    @Test
    fun `SENDING DELIVERED and WAITING do not need delivery`() {
        assertFalse(DeliveryStatus.SENDING.needsDelivery)
        assertFalse(DeliveryStatus.DELIVERED.needsDelivery)
        assertFalse(DeliveryStatus.WAITING.needsDelivery)
    }

    @Test
    fun `full retry cycle transitions are valid`() {
        // PENDING -> SENDING
        assertTrue(DeliveryStatus.PENDING.canTransitionTo(DeliveryStatus.SENDING))
        // SENDING -> RETRYING
        assertTrue(DeliveryStatus.SENDING.canTransitionTo(DeliveryStatus.RETRYING))
        // RETRYING -> SENDING (retry attempt)
        assertTrue(DeliveryStatus.RETRYING.canTransitionTo(DeliveryStatus.SENDING))
        // SENDING -> DELIVERED (success)
        assertTrue(DeliveryStatus.SENDING.canTransitionTo(DeliveryStatus.DELIVERED))
    }

    @Test
    fun `exhausted retry cycle transitions are valid`() {
        // PENDING -> SENDING
        assertTrue(DeliveryStatus.PENDING.canTransitionTo(DeliveryStatus.SENDING))
        // SENDING -> RETRYING
        assertTrue(DeliveryStatus.SENDING.canTransitionTo(DeliveryStatus.RETRYING))
        // RETRYING -> WAITING (exhausted)
        assertTrue(DeliveryStatus.RETRYING.canTransitionTo(DeliveryStatus.WAITING))
        // WAITING -> PENDING (peer activity)
        assertTrue(DeliveryStatus.WAITING.canTransitionTo(DeliveryStatus.PENDING))
    }
}
