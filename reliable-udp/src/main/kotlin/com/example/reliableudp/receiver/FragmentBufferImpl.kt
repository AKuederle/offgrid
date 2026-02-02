package com.example.reliableudp.receiver

import java.util.concurrent.ConcurrentHashMap

/**
 * Fragment reassembly buffer implementation.
 *
 * Stores fragments by messageId and reassembles when all fragments arrive.
 * Uses ConcurrentHashMap for thread safety.
 */
class FragmentBufferImpl : FragmentBuffer {

    /**
     * Tracks reassembly state for a single message.
     */
    private data class FragmentAssembly(
        val messageId: Int,
        val expectedTotal: Int,
        val fragments: MutableMap<Int, ByteArray> = mutableMapOf(),
        val firstSeenAt: Long = System.currentTimeMillis()
    ) {
        val isComplete: Boolean get() = fragments.size == expectedTotal

        fun addFragment(index: Int, payload: ByteArray): Boolean {
            fragments[index] = payload
            return isComplete
        }

        fun assemble(): ByteArray {
            require(isComplete) { "Cannot assemble incomplete message" }
            // Concatenate fragments in order
            return (1..expectedTotal)
                .map { fragments[it]!! }
                .fold(ByteArray(0)) { acc, bytes -> acc + bytes }
        }
    }

    private val pending = ConcurrentHashMap<Int, FragmentAssembly>()

    override fun addFragment(
        messageId: Int,
        fragmentIndex: Int,
        fragmentTotal: Int,
        payload: ByteArray
    ): ByteArray? {
        // Validate inputs
        if (fragmentIndex < 1 || fragmentIndex > fragmentTotal || fragmentTotal < 1) {
            return null
        }

        // Get or create assembly
        val assembly = pending.computeIfAbsent(messageId) {
            FragmentAssembly(messageId, fragmentTotal)
        }

        // Verify fragment total matches (all fragments must agree)
        if (assembly.expectedTotal != fragmentTotal) {
            // Mismatch - discard fragment
            return null
        }

        // Add fragment
        synchronized(assembly) {
            assembly.addFragment(fragmentIndex, payload)

            return if (assembly.isComplete) {
                // Remove from pending and return assembled message
                pending.remove(messageId)
                assembly.assemble()
            } else {
                null
            }
        }
    }

    override fun cleanupStale(timeoutMs: Long): Int {
        val now = System.currentTimeMillis()
        val cutoff = now - timeoutMs

        var removed = 0
        val iterator = pending.entries.iterator()

        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.value.firstSeenAt < cutoff) {
                iterator.remove()
                removed++
            }
        }

        return removed
    }

    override val pendingMessageCount: Int
        get() = pending.size
}
