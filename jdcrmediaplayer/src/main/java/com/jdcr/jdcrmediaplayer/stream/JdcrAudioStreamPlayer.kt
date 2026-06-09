package com.jdcr.jdcrmediaplayer.stream

import android.content.Context
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.PlaybackException
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.audio.AudioAttributes
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.exoplayer2.upstream.DataSource
import com.jdcr.jdcrbase.JdcrSafeCoroutineScope
import com.jdcr.jdcrmediaplayer.util.JdcrPlayerLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 实时音频流播放器（纯音频，无需 PlayerView）。
 * 商用要点：单次 prepare，背后接 StreamingDataSource，持续 append。
 */
class JdcrAudioStreamPlayer(
    private val context: Context,
    private val spec: JdcrStreamSpec = JdcrStreamSpec()
) {

    sealed class StreamState {
        object Idle : StreamState()
        object Buffering : StreamState()
        object Playing : StreamState()
        object Ended : StreamState()
        data class Error(val code: Int, val msg: String) : StreamState()
    }

    private val scope = JdcrSafeCoroutineScope(tag = "jdcrAudioStream") {
        JdcrPlayerLog.e("AudioStream协程收到异常", it)
    }
    private val _state = MutableStateFlow<StreamState>(StreamState.Idle)
    val state: StateFlow<StreamState> = _state

    private var exo: ExoPlayer? = null
    private var dataSource: StreamingDataSource? = null
    private var pcmPlayer: PcmAudioTrackPlayer? = null   // 二期再实现

    /** 开始一路新的流（会清掉上一路） */
    fun open() {
        JdcrPlayerLog.i("开始一路新的流式播放,清掉上一路")
        stop()
        if (spec.format.isRawPcm) {
            pcmPlayer = PcmAudioTrackPlayer(spec.sampleRate, spec.channels).also { it.start() }
            _state.value = StreamState.Playing
            return
        }

        val ds = StreamingDataSource()
        dataSource = ds

        val factory = DataSource.Factory { ds }
        val mediaSource = ProgressiveMediaSource.Factory(factory)
            .createMediaSource(
                MediaItem.Builder()
                    .setUri(ds.uri)
                    .setMimeType(spec.format.mimeType)   // 提示 extractor，减少嗅探失败
                    .build()
            )

        exo = ExoPlayer.Builder(context)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.CONTENT_TYPE_SPEECH)
                    .build(),
                /* handleAudioFocus = */ spec.handleAudioFocus
            )
            .build()
            .also { p ->
                p.addListener(listener)
                p.setMediaSource(mediaSource)
                p.prepare()
                p.playWhenReady = true
            }
    }

    /** 收到一段数据（当前是 base64 字符串） */
    fun append(chunk: String) {
        JdcrPlayerLog.v("收到一段数据,string")
        val bytes = ChunkTransport.decode(chunk, spec.transport)
        appendBytes(bytes)
    }

    /** 收到一段数据（未来二进制帧直接用这个） */
    fun appendBytes(bytes: ByteArray) {
        JdcrPlayerLog.v("收到一段数据,字节数组")
        if (spec.format.isRawPcm) {
            pcmPlayer?.write(bytes)
        } else {
            dataSource?.append(bytes)
        }
    }

    /** 数据发完，等播完自然 Ended */
    fun endOfStream() {
        JdcrPlayerLog.i("流式播放结束")
        dataSource?.endOfStream()
        pcmPlayer?.endOfStream()
    }

    /** 立即停止并清理当前流 */
    fun stop() {
        JdcrPlayerLog.i("触发停止流式播放")
        exo?.let { it.removeListener(listener); it.release() }
        exo = null
        dataSource?.release()
        dataSource = null
        pcmPlayer?.release()
        pcmPlayer = null
    }

    fun release() {
        JdcrPlayerLog.i("流式播放释放所有资源")
        stop()
        scope.cancelChildren()
    }

    private val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            _state.value = when (playbackState) {
                Player.STATE_BUFFERING -> StreamState.Buffering
                Player.STATE_READY -> StreamState.Playing
                Player.STATE_ENDED -> StreamState.Ended
                else -> StreamState.Idle
            }
            JdcrPlayerLog.i("流播放状态: ${_state.value}")
        }

        override fun onPlayerError(error: PlaybackException) {
            JdcrPlayerLog.w("流播放错误", error)
            _state.value = StreamState.Error(error.errorCode, error.message ?: "未知错误")
        }
    }
}