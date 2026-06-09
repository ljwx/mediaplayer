package com.jdcr.jdcrmediaplayer.stream

import android.content.Context
import android.os.Handler
import android.os.Looper
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * 实时音频流播放器（纯音频，无需 PlayerView）。
 *
 * 设计目标：append（喂数据）与 play（开始播放）完全异步、谁先调用都正确。
 *  - 先 append 后 open/play：open 之前的数据进入 [pending] 预缓冲，open 时按序灌入管道，不丢数据。
 *  - 先 open/play 后 append：底层 [StreamingDataSource] 阻塞读会等待数据到来，先进入 Buffering，数据到了再出声。
 *  - open 与 play 拆开：open 只建管道并按 [playRequested] 决定是否自动播；play()/pause() 单独控制出声时机。
 *
 * 线程模型：
 *  - 控制方法（open/play/pause/stop/release/endOfStream）统一切到主线程执行，保证 ExoPlayer 单线程访问。
 *  - append/appendBytes 可在任意线程调用（如 WS 的 IO 线程），内部用 [lock] 保证预缓冲与管道交接的原子性。
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

    private companion object {
        /** 预缓冲上限，超过则丢弃最旧分片，避免 open 迟迟不来导致内存无界增长。 */
        const val MAX_PENDING_BYTES = 16 * 1024 * 1024
    }

    private val scope = JdcrSafeCoroutineScope(tag = "jdcrAudioStream") {
        JdcrPlayerLog.e("AudioStream协程收到异常", it)
    }
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _state = MutableStateFlow<StreamState>(StreamState.Idle)
    val state: StateFlow<StreamState> = _state

    // ExoPlayer / 管道仅在主线程读写
    private var exo: ExoPlayer? = null

    /** 期望的播放状态，play()/pause() 修改，open() 据此决定是否自动播；跨 open 会话保留。 */
    @Volatile
    private var playRequested = false

    /** release 后为终态，忽略后续 append，防止泄漏。open() 会重新置回可用。 */
    @Volatile
    private var released = false

    // ---- 以下字段由 lock 保护 ----
    private val lock = Any()
    private val pending = ArrayDeque<ByteArray>()
    private var pendingBytes = 0
    private var endRequested = false
    private var live = false
    private var dataSource: StreamingDataSource? = null
    private var pcmPlayer: PcmAudioTrackPlayer? = null
    // ---------------------------------

    private inline fun runMain(crossinline block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else mainHandler.post { block() }
    }

    /** 建立播放管道（不一定出声，是否自动播由 [playRequested] 决定）。会清掉上一路。 */
    fun open() {
        runMain { openInternal() }
    }

    private fun openInternal() {
        JdcrPlayerLog.i("开始一路新的流式播放,清掉上一路")
        stopInternal()
        released = false

        if (spec.format.isRawPcm) {
            openPcm()
        } else {
            openContainer()
        }
    }

    private fun openContainer() {
        val ds = StreamingDataSource()

        val factory = DataSource.Factory { ds }
        val mediaSource = ProgressiveMediaSource.Factory(factory)
            .createMediaSource(
                MediaItem.Builder()
                    .setUri(ds.uri)
                    .setMimeType(spec.format.mimeType) // 提示 extractor，减少首包嗅探失败
                    .build()
            )

        val player = ExoPlayer.Builder(context)
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
                p.playWhenReady = playRequested
            }
        exo = player

        // 原子交接：把 open 之前缓存的数据按序灌入，再标记 live。
        // ds.append 不阻塞，可安全在锁内完成，保证后续 live append 不会插队。
        synchronized(lock) {
            dataSource = ds
            while (pending.isNotEmpty()) {
                ds.append(pending.removeFirst())
            }
            pendingBytes = 0
            live = true
            if (endRequested) {
                ds.endOfStream()
            }
        }
    }

    private fun openPcm() {
        val pcm = PcmAudioTrackPlayer(spec.sampleRate, spec.channels).also { it.start() }
        synchronized(lock) {
            pcmPlayer = pcm
            // 注意：此处先不置 live，由 drainer 排空 pending 后再置 live，避免乱序与主线程阻塞写。
        }
        _state.value = StreamState.Playing

        // AudioTrack.write 是阻塞写，放到 IO 线程排空预缓冲并平滑切到 live。
        scope.launch(Dispatchers.IO) {
            while (true) {
                val chunk = synchronized(lock) {
                    if (pcmPlayer !== pcm) return@launch // 已被新会话替换/停止
                    if (pending.isEmpty()) {
                        live = true
                        return@launch
                    }
                    pending.removeFirst().also { pendingBytes -= it.size }
                }
                pcm.write(chunk)
            }
        }
    }

    /** 收到一段数据（当前后端是 base64 字符串）。任意线程可调。 */
    fun append(chunk: String) {
        if (released) return
        val bytes = try {
            ChunkTransport.decode(chunk, spec.transport)
        } catch (e: Exception) {
            JdcrPlayerLog.w("分片解码失败,已忽略", e)
            return
        }
        appendBytes(bytes)
    }

    /** 收到一段数据（未来二进制帧直接用这个）。任意线程可调。 */
    fun appendBytes(bytes: ByteArray) {
        if (released || bytes.isEmpty()) return

        var ds: StreamingDataSource? = null
        var pcm: PcmAudioTrackPlayer? = null
        synchronized(lock) {
            if (!live) {
                // 管道未就绪（或 PCM 仍在排空 pending），先缓冲，保持顺序。
                enforcePendingLimitLocked(bytes.size)
                pending.addLast(bytes)
                pendingBytes += bytes.size
                return
            }
            ds = dataSource
            pcm = pcmPlayer
        }
        ds?.append(bytes)
        pcm?.write(bytes)
    }

    /** lock 内调用：保证预缓冲不超过上限，超了丢最旧。 */
    private fun enforcePendingLimitLocked(incoming: Int) {
        if (pendingBytes + incoming <= MAX_PENDING_BYTES) return
        var dropped = 0
        while (pending.isNotEmpty() && pendingBytes + incoming > MAX_PENDING_BYTES) {
            val removed = pending.removeFirst()
            pendingBytes -= removed.size
            dropped += removed.size
        }
        JdcrPlayerLog.w("预缓冲超过上限,丢弃最旧数据:$dropped 字节")
    }

    /** 请求开始播放。可在 open 之前或之后调用。 */
    fun play() {
        runMain {
            playRequested = true
            exo?.playWhenReady = true
        }
    }

    /** 暂停（保留管道与数据）。 */
    fun pause() {
        runMain {
            playRequested = false
            exo?.playWhenReady = false
        }
    }

    /** 数据发完，等播完自然进入 Ended。任意线程可调。 */
    fun endOfStream() {
        JdcrPlayerLog.i("流式数据发送结束")
        var ds: StreamingDataSource? = null
        synchronized(lock) {
            endRequested = true
            if (live) ds = dataSource
        }
        ds?.endOfStream()
    }

    /** 立即停止并清理当前流（可再次 open 复用）。 */
    fun stop() {
        runMain { stopInternal() }
    }

    private fun stopInternal() {
        JdcrPlayerLog.i("触发停止流式播放")
        scope.cancelChildren() // 取消 PCM drainer 等
        exo?.let { it.removeListener(listener); it.release() }
        exo = null
        synchronized(lock) {
            dataSource?.release()
            dataSource = null
            pcmPlayer?.release()
            pcmPlayer = null
            pending.clear()
            pendingBytes = 0
            endRequested = false
            live = false
        }
        _state.value = StreamState.Idle
    }

    /** 释放所有资源（终态）。 */
    fun release() {
        JdcrPlayerLog.i("流式播放释放所有资源")
        released = true
        runMain {
            stopInternal()
            scope.cancelChildren()
        }
    }

    private val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            _state.value = when (playbackState) {
                Player.STATE_BUFFERING -> StreamState.Buffering
                Player.STATE_READY -> if (exo?.playWhenReady == true) StreamState.Playing else StreamState.Buffering
                Player.STATE_ENDED -> StreamState.Ended
                else -> StreamState.Idle
            }
            JdcrPlayerLog.i("流播放状态: ${_state.value}")
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) {
                _state.value = StreamState.Playing
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            JdcrPlayerLog.w("流播放错误", error)
            _state.value = StreamState.Error(error.errorCode, error.message ?: "未知错误")
        }
    }
}
