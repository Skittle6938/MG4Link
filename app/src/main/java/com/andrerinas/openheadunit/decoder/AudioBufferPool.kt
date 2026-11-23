package com.andrerinas.openheadunit.decoder

import java.nio.ByteBuffer
import java.util.concurrent.LinkedBlockingQueue

/**
 * Reusable byte arrays for the audio path: incoming chunks and decoded AAC output (about 47 a
 * second at 48 kHz) would otherwise each allocate a fresh array. A pooled array can be larger than
 * the data it holds, so callers always carry the size alongside it. Thread-safe.
 */
internal class AudioBufferPool(
    private val limit: Int = DEFAULT_LIMIT,
    private val minSize: Int = DEFAULT_MIN_SIZE,
) {
    private val pool = LinkedBlockingQueue<ByteArray>()

    val pooledCount: Int get() = pool.size

    /** An array of at least [size] bytes; arrays left in the pool that are too small are dropped. */
    fun obtain(size: Int): ByteArray {
        while (true) {
            val pooled = pool.poll() ?: return ByteArray(maxOf(size, minSize))
            if (pooled.size >= size) return pooled
        }
    }

    fun recycle(buffer: ByteArray) {
        if (pool.size < limit) pool.offer(buffer)
    }

    /** Copies [size] bytes of [source] from [offset] into a pooled array. */
    fun copyOf(source: ByteBuffer, offset: Int, size: Int): ByteArray {
        val data = obtain(size)
        source.position(offset)
        source.get(data, 0, size)
        return data
    }

    fun clear() = pool.clear()

    companion object {
        const val DEFAULT_LIMIT = 16
        const val DEFAULT_MIN_SIZE = 4096
    }
}
