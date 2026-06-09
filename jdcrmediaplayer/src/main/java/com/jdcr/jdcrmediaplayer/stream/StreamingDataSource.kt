package com.jdcr.jdcrmediaplayer.stream

import android.net.Uri
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.upstream.BaseDataSource
import com.google.android.exoplayer2.upstream.DataSpec
import java.io.IOException
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 可持续追加字节的 ExoPlayer 数据源。
 * ExoPlayer 在播放线程调用 read()；业务线程调用 append()/endOfStream()。
 */
internal class StreamingDataSource : BaseDataSource(true) {
    private val queue = LinkedBlockingDeque<ByteArray>()
    private var current: ByteArray? = null
    private var currentPos = 0
    private val finished = AtomicBoolean(false)
    private val released = AtomicBoolean(false)
    @Volatile private var opened = false
    fun append(bytes: ByteArray) {
        if (released.get() || finished.get() || bytes.isEmpty()) return
        queue.offer(bytes)
    }
    /** 业务侧：数据发完了 */
    fun endOfStream() {
        finished.set(true)
        // 放一个空标记唤醒可能阻塞的 read
        queue.offer(ByteArray(0))
    }
    override fun open(dataSpec: DataSpec): Long {
        transferInitializing(dataSpec)
        opened = true
        transferStarted(dataSpec)
        return C.LENGTH_UNSET.toLong()   // 长度未知 = 流
    }
    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        // 当前块读完了，取下一块
        while (current == null || currentPos >= current!!.size) {
            if (released.get()) return C.RESULT_END_OF_INPUT
            // 已结束且队列空 → EOF
            if (finished.get() && queue.isEmpty()) return C.RESULT_END_OF_INPUT
            val next = try {
                queue.poll(200, TimeUnit.MILLISECONDS)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                throw IOException(e)
            }
            if (next == null) continue          // 超时但流还没结束，继续等
            if (next.isEmpty()) {               // 结束标记
                if (finished.get() && queue.isEmpty()) return C.RESULT_END_OF_INPUT
                continue
            }
            current = next
            currentPos = 0
        }
        val chunk = current!!
        val n = minOf(length, chunk.size - currentPos)
        System.arraycopy(chunk, currentPos, buffer, offset, n)
        currentPos += n
        bytesTransferred(n)
        return n
    }
    override fun getUri(): Uri = Uri.parse("jdcr-stream://audio")
    override fun close() {
        if (opened) {
            opened = false
            transferEnded()
        }
    }
    fun release() {
        released.set(true)
        queue.clear()
        queue.offer(ByteArray(0))
    }
}