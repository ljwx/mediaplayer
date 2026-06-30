package com.jdcr.jdcrmediaplayer

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.PlaybackException
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.source.MediaSource
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.jdcr.jdcrmediaplayer.datasource.JdcrAudioStreamBuffer
import com.jdcr.jdcrmediaplayer.datasource.JdcrAudioStreamDataSource
import com.jdcr.jdcrmediaplayer.datasource.JdcrAudioStreamSource
import com.jdcr.jdcrmediaplayer.datasource.JdcrAudioStreamTarget
import com.jdcr.jdcrmediaplayer.datasource.base64Decode
import com.jdcr.jdcrmediaplayer.datasource.binaryDecode
import com.jdcr.jdcrmediaplayer.datasource.hexDecode
import com.jdcr.jdcrmediaplayer.util.JdcrPlayerLog

class JdcrStreamAudioPlayer(
    private val context: Context,
    private val source: JdcrAudioStreamSource = JdcrAudioStreamSource.BASE64,
    private val target: JdcrAudioStreamTarget = JdcrAudioStreamTarget.Encoded.MP3
) {

    private val mainHandler = Handler(Looper.getMainLooper())

    private val buffer = JdcrAudioStreamBuffer()
    private var player: ExoPlayer? = null

    var onError: ((PlaybackException) -> Unit)? = null

    private val listener = object : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {
            JdcrPlayerLog.e("播放错误: ${error.errorCodeName}", error)
            onError?.invoke(error) // 交给你决定怎么处理, 不崩
        }
    }

    private fun initPlayer() {
        if (player == null) {
            player = ExoPlayer.Builder(context).build().apply {
                addListener(listener)
                setMediaSource(buildMediaSource())
                prepare() // 没数据时 read 会等, 数据一到自动开播
            }
        }
    }

    fun append(data: ByteArray) {
        buffer.append(data)
    }

    fun append(data: String) {
        buffer.append(
            when (source) {
                JdcrAudioStreamSource.BINARY -> binaryDecode(data)
                JdcrAudioStreamSource.BASE64 -> base64Decode(data)
                JdcrAudioStreamSource.HEX -> hexDecode(data)
            }
        )
    }

    fun endStream() {
        buffer.streamEnd()
    }

    fun play() {
        runOnMain {
            initPlayer()
            player?.play()
        }
    }

    private fun buildMediaSource(): MediaSource {
        val factory = JdcrAudioStreamDataSource.Factory(buffer)
        return when (target) {
            is JdcrAudioStreamTarget.Encoded -> {
//                val item = MediaItem.fromUri(Uri.parse("jdcr://stream-audio"))
                val item = MediaItem.Builder()
                    .setUri(Uri.parse("jdcr://stream-audio"))
                    .setMimeType(target.mimeType) // 提示 extractor，减少首包嗅探失败
                    .build()
                ProgressiveMediaSource.Factory(factory).createMediaSource(item)
            }

            is JdcrAudioStreamTarget.PCM ->
                throw UnsupportedOperationException("PCM 暂未实现")
        }
    }

    private inline fun runOnMain(crossinline block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block()
        else mainHandler.post { block() }
    }

}