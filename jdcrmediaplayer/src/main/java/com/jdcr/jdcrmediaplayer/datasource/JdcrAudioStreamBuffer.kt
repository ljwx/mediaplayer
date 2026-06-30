package com.jdcr.jdcrmediaplayer.datasource

import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

class JdcrAudioStreamBuffer {

    private val queue = LinkedBlockingQueue<ByteArray>()
    private var currentChunk: ByteArray? = null
    private var position = 0

    @Volatile
    private var ended = false

    @Volatile
    private var closed = false

    fun append(data: ByteArray) {
        if (ended || closed || data.isEmpty()) return
        queue.offer(data)
    }

    fun streamEnd() {
        ended = true
    }

    fun read(target: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (currentChunk == null) {
            var chunk = queue.poll(100, TimeUnit.MILLISECONDS)
            while (chunk == null) {
                if (closed || ended) return -1
                chunk = queue.poll(100, TimeUnit.MILLISECONDS)
            }
            currentChunk = chunk
            position = 0
        }
        var current = currentChunk!!
        val size = minOf(length, current.size - position)
        System.arraycopy(current, position, target, offset, size)
        position += size
        if (position >= current.size) currentChunk = null
        return size
    }

    fun close() {
        closed = true
    }

}