package com.jdcr.jdcrmediaplayer.datasource

import android.net.Uri
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.upstream.BaseDataSource
import com.google.android.exoplayer2.upstream.DataSource
import com.google.android.exoplayer2.upstream.DataSpec
import com.jdcr.jdcrmediaplayer.util.JdcrPlayerLog

class JdcrStreamDataSource(private val buffer: JdcrStreamAudioBuffer) : BaseDataSource(true) {

    private var uri: Uri? = null

    override fun read(target: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        val size = buffer.read(target, offset, length)
        if (size <= 0) return C.RESULT_END_OF_INPUT
        bytesTransferred(size)
        return size
    }

    override fun open(dataSpec: DataSpec): Long {
        uri = dataSpec.uri
        transferInitializing(dataSpec)   // 通知监听器: 准备传输
        transferStarted(dataSpec)        // 通知监听器: 开始传输
        return C.LENGTH_UNSET.toLong()
    }

    override fun getUri(): Uri? {
        return uri
    }

    override fun close() {
        JdcrPlayerLog.w("流式播放结束")
        uri = null
        transferEnded()                  // 通知监听器: 传输结束
    }

    class Factory(private val buffer: JdcrStreamAudioBuffer) : DataSource.Factory {
        override fun createDataSource(): DataSource = JdcrStreamDataSource(buffer)
    }

}