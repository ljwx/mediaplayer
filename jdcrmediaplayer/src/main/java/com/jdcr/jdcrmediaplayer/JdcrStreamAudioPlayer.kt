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
import com.jdcr.jdcrmediaplayer.datasource.JdcrStreamAudioBuffer
import com.jdcr.jdcrmediaplayer.datasource.JdcrStreamDataSource
import com.jdcr.jdcrmediaplayer.util.JdcrPlayerLog

enum class StreamFormat { ENCODED, PCM }

class JdcrStreamAudioPlayer(
    private val context: Context,
    private val format: StreamFormat = StreamFormat.ENCODED
) {

    private val mainHandler = Handler(Looper.getMainLooper())

    private val buffer = JdcrStreamAudioBuffer()
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
        val factory = JdcrStreamDataSource.Factory(buffer)
        return when (format) {
            StreamFormat.ENCODED -> {
                val item = MediaItem.fromUri(Uri.parse("jdcr://stream-audio"))
                ProgressiveMediaSource.Factory(factory).createMediaSource(item)
            }
            StreamFormat.PCM ->
                throw UnsupportedOperationException("PCM 暂未实现")
        }
    }

    private inline fun runOnMain(crossinline block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block()
        else mainHandler.post { block() }
    }

}