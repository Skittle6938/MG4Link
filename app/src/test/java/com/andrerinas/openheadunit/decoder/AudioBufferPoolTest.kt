package com.andrerinas.openheadunit.decoder

import java.nio.ByteBuffer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioBufferPoolTest {

    @Test fun `a new array is at least the minimum size`() {
        val pool = AudioBufferPool(minSize = 4096)
        assertEquals(4096, pool.obtain(100).size)
        assertEquals(8192, pool.obtain(8192).size)
    }

    @Test fun `a recycled array is handed out again`() {
        val pool = AudioBufferPool()
        val first = pool.obtain(4096)
        pool.recycle(first)
        assertSame(first, pool.obtain(4096))
    }

    @Test fun `a pooled array too small for the request is dropped`() {
        val pool = AudioBufferPool(minSize = 16)
        val small = pool.obtain(16)
        pool.recycle(small)
        val big = pool.obtain(4096)
        assertNotSame(small, big)
        assertTrue(big.size >= 4096)
        assertEquals(0, pool.pooledCount)
    }

    @Test fun `the pool never holds more than its limit`() {
        val pool = AudioBufferPool(limit = 2)
        repeat(5) { pool.recycle(ByteArray(4096)) }
        assertEquals(2, pool.pooledCount)
    }

    @Test fun `copyOf takes only the decoded bytes from the codec buffer`() {
        val pool = AudioBufferPool(minSize = 16)
        val codecBuffer = ByteBuffer.wrap(byteArrayOf(9, 9, 1, 2, 3, 4, 9, 9))
        val data = pool.copyOf(codecBuffer, offset = 2, size = 4)
        assertTrue(data.size >= 4)
        assertArrayEquals(byteArrayOf(1, 2, 3, 4), data.copyOf(4))
    }

    @Test fun `a reused array larger than the data keeps the size separate`() {
        val pool = AudioBufferPool(minSize = 16)
        pool.recycle(ByteArray(64) { 7 })
        val data = pool.copyOf(ByteBuffer.wrap(byteArrayOf(1, 2, 3)), offset = 0, size = 3)
        assertEquals(64, data.size)
        assertArrayEquals(byteArrayOf(1, 2, 3), data.copyOf(3))
    }
}
