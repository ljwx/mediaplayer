package com.jdcr.jdcrmediaplayer.datasource

import android.net.Uri
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.upstream.BaseDataSource
import com.google.android.exoplayer2.upstream.DataSource
import com.google.android.exoplayer2.upstream.DataSpec
import com.jdcr.jdcrmediaplayer.util.JdcrPlayerLog
import androidx.core.net.toUri

class JdcrAudioStreamDataSource(private val buffer: JdcrAudioStreamBuffer) : BaseDataSource(true) {

    override fun read(target: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        val size = buffer.read(target, offset, length)
        if (size <= 0) return C.RESULT_END_OF_INPUT
        bytesTransferred(size)
        return size
    }

    override fun open(dataSpec: DataSpec): Long {
        transferInitializing(dataSpec)   // 通知监听器: 准备传输
        transferStarted(dataSpec)        // 通知监听器: 开始传输
        return C.LENGTH_UNSET.toLong()
    }

    override fun getUri(): Uri {
        return "jdcr://stream-audio".toUri()
    }

    override fun close() {
        JdcrPlayerLog.w("流式播放结束")
        transferEnded()                  // 通知监听器: 传输结束
    }

    class Factory(private val buffer: JdcrAudioStreamBuffer) : DataSource.Factory {
        override fun createDataSource(): DataSource = JdcrAudioStreamDataSource(buffer)
    }

}