package com.jdcr.jdcrmediaplayer

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.os.Looper
import androidx.annotation.MainThread
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.MediaMetadata
import com.google.android.exoplayer2.PlaybackException
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.Timeline
import com.google.android.exoplayer2.ui.PlayerView
import com.jdcr.jdcrmediaplayer.define.JdcrPlayer
import com.jdcr.jdcrmediaplayer.define.JdcrMediaSource
import com.jdcr.jdcrmediaplayer.define.JdcrPlayerView
import com.jdcr.jdcrmediaplayer.define.JdcrPlayerError
import com.jdcr.jdcrmediaplayer.define.JdcrPlayerRepeatMode
import com.jdcr.jdcrmediaplayer.define.JdcrPlayerState
import com.jdcr.jdcrmediaplayer.util.JdcrPlayerLog
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import androidx.core.net.toUri
import com.jdcr.jdcrbase.coroutine.JdcrSafeCoroutineScope
import com.jdcr.jdcrmediaplayer.config.JdcrPlayerConfig
import kotlinx.coroutines.cancel

abstract class JdcrPlayerCore(
    context: Context,
    playerView: JdcrPlayerView,
    private val config: JdcrPlayerConfig
) : JdcrPlayer {

    private var _scope = JdcrSafeCoroutineScope {
        JdcrPlayerLog.e("MediaPlayer协程收到异常", it)
    }

    @Volatile
    protected var defaultMedia: JdcrMediaSource? = null

    protected var isStopped = true

    @SuppressLint("UnsafeOptInUsageError")
    private val _exoPlayer: ExoPlayer =
        ExoPlayer.Builder(context).setAudioAttributes(config.audioAttributes, true).apply {
            config.localCache?.apply {
                setMediaSourceFactory(getCacheConfig(context, this, config.errorPolicy).factory)
            }
        }.build().also { player ->
            playerView.player = player
            playerView.controllerAutoShow = false
            // 不要让 PlayerView 在切换时刷黑底
            playerView.setShutterBackgroundColor(Color.TRANSPARENT)
            playerView.setKeepContentOnPlayerReset(true)
            playerView.setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
        }

    private val _stateFlow = MutableStateFlow<JdcrPlayerState>(JdcrPlayerState.IDLE(null))
    private val _progressFlow = MutableStateFlow(0L)
    private var progressJob: Job? = null
    private val _currentIndexFlow = MutableStateFlow(0)

    private var lastPlayWhenReadyChangeReason: Int =
        Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST

    private var lastPauseEmitted: JdcrPlayerState.PAUSE? = null

    init {
        setupPlayerListener()
        setupProgressListener()
    }

    protected fun getPlayer(): ExoPlayer {
        return _exoPlayer
    }

    private fun buildPauseState(): JdcrPlayerState.PAUSE {
        val item = getCurrentMediaSource()
        return when {
            _exoPlayer.playbackSuppressionReason ==
                    Player.PLAYBACK_SUPPRESSION_REASON_TRANSIENT_AUDIO_FOCUS_LOSS ->
                JdcrPlayerState.PAUSE.LOSE_FOCUS_TEMP(item)

            lastPlayWhenReadyChangeReason ==
                    Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS &&
                    !_exoPlayer.playWhenReady ->
                JdcrPlayerState.PAUSE.LOSE_FOCUS_LONG(item)

            lastPlayWhenReadyChangeReason ==
                    Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY ->
                JdcrPlayerState.PAUSE.AUDIO_BECOMING_NOISY(item)

            else -> JdcrPlayerState.PAUSE.Normal(item)
        }
    }

    private fun refreshPauseWithCurrentSource(p: JdcrPlayerState.PAUSE): JdcrPlayerState.PAUSE {
        val item = getCurrentMediaSource()
        return when (p) {
            is JdcrPlayerState.PAUSE.Normal -> JdcrPlayerState.PAUSE.Normal(item)
            is JdcrPlayerState.PAUSE.LOSE_FOCUS_TEMP -> JdcrPlayerState.PAUSE.LOSE_FOCUS_TEMP(item)
            is JdcrPlayerState.PAUSE.LOSE_FOCUS_LONG -> JdcrPlayerState.PAUSE.LOSE_FOCUS_LONG(item)
            is JdcrPlayerState.PAUSE.AUDIO_BECOMING_NOISY -> JdcrPlayerState.PAUSE.AUDIO_BECOMING_NOISY(
                item
            )
        }
    }

    private fun setupPlayerListener() {
        val listener = object : Player.Listener {

            override fun onRenderedFirstFrame() {
                super.onRenderedFirstFrame()
                JdcrPlayerLog.i("首帧已渲染:" + getCurrentResouceMessage())
                autoPlayFirstFrame()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                val newState = when (playbackState) {
                    Player.STATE_BUFFERING -> JdcrPlayerState.PREPARING(getCurrentMediaSource())
                    Player.STATE_READY -> {
                        when {
                            _exoPlayer.isPlaying -> {
                                lastPauseEmitted = null
                                JdcrPlayerState.PLAYING(getCurrentMediaSource())
                            }

                            lastPauseEmitted != null && !_exoPlayer.isPlaying -> {
                                val refreshed = refreshPauseWithCurrentSource(lastPauseEmitted!!)
                                lastPauseEmitted = refreshed
                                refreshed
                            }

                            else -> JdcrPlayerState.READY(getCurrentMediaSource())
                        }
                    }

                    Player.STATE_ENDED -> {
                        lastPauseEmitted = null
                        handlePlayEnded()
                        JdcrPlayerState.ENDED(getCurrentMediaSource())
                    }

                    else -> {
                        lastPauseEmitted = null
                        JdcrPlayerState.IDLE(getCurrentMediaSource())
                    }
                }
                JdcrPlayerLog.i("播放状态变更:" + newState.desc + "," + getCurrentCountMessage())
                runMain {
                    _stateFlow.tryEmit(newState)
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                super.onIsPlayingChanged(isPlaying)
                if (isPlaying) {
                    lastPauseEmitted = null
                    _stateFlow.tryEmit(JdcrPlayerState.PLAYING(getCurrentMediaSource()))
                } else if (_exoPlayer.playbackState == Player.STATE_READY) {
                    val reason = buildPauseState()
                    lastPauseEmitted = reason
                    JdcrPlayerLog.d("停止播放了,原因:${reason.reason}")
                    _stateFlow.tryEmit(reason)
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                val playbackError = when (error.errorCode) {
                    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED -> JdcrPlayerError.NetworkError(
                        error.errorCode,
                        "网络错误"
                    )

                    PlaybackException.ERROR_CODE_DECODER_INIT_FAILED -> JdcrPlayerError.FormatError(
                        error.errorCode,
                        "解码失败"
                    )

                    PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND -> JdcrPlayerError.SourceError(
                        error.errorCode,
                        "文件未找到"
                    )

                    else -> JdcrPlayerError.UnknowError(error.errorCode, "未知错误")

                }
                JdcrPlayerLog.w("播放出错:" + getCurrentResouceMessage(), playbackError)
                lastPauseEmitted = null
                _stateFlow.tryEmit(JdcrPlayerState.ERROR(getCurrentMediaSource(), playbackError))
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                lastPauseEmitted = null
                mediaItem?.let {
                    JdcrPlayerLog.i("切换到新视频:" + getCurrentCountMessage())
                    _currentIndexFlow.tryEmit(_exoPlayer.currentMediaItemIndex)
                }
            }

            override fun onPlaylistMetadataChanged(mediaMetadata: MediaMetadata) {
                super.onPlaylistMetadataChanged(mediaMetadata)
            }

            override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                if (reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) {
                    JdcrPlayerLog.w("\n播放列表发生变化:" + getCurrentCountMessage())
                }
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                super.onPlayWhenReadyChanged(playWhenReady, reason)
                lastPlayWhenReadyChangeReason = reason
                if (playWhenReady) {
                    lastPauseEmitted = null
                }
                // ExoPlayer 2.16 仅 5 种：USER_REQUEST, AUDIO_FOCUS_LOSS, AUDIO_BECOMING_NOISY, REMOTE, END_OF_MEDIA_ITEM
                when (reason) {
                    Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST ->
                        JdcrPlayerLog.d("playWhenReady=$playWhenReady(用户/业务请求)")

                    Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS -> {
                        JdcrPlayerLog.w("playWhenReady=$playWhenReady(长时失去音频焦点)")
                    }

                    Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY ->
                        JdcrPlayerLog.w("playWhenReady=$playWhenReady(即将外放，避免突然出声)")

                    Player.PLAY_WHEN_READY_CHANGE_REASON_REMOTE ->
                        JdcrPlayerLog.d("playWhenReady=$playWhenReady(远程/会话控制)")

                    Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM ->
                        JdcrPlayerLog.d("playWhenReady=$playWhenReady(当前媒体项结束)")

                    else ->
                        JdcrPlayerLog.d("playWhenReady=$playWhenReady(未知原因 reason=$reason)")
                }
            }

            override fun onPlaybackSuppressionReasonChanged(playbackSuppressionReason: Int) {
                super.onPlaybackSuppressionReasonChanged(playbackSuppressionReason)
                // 2.16 仅有 NONE 与 TRANSIENT_AUDIO_FOCUS_LOSS
                when (playbackSuppressionReason) {
                    Player.PLAYBACK_SUPPRESSION_REASON_NONE ->
                        JdcrPlayerLog.d("getPlaybackSuppressionReason=NONE(不再因压制而静音)")

                    Player.PLAYBACK_SUPPRESSION_REASON_TRANSIENT_AUDIO_FOCUS_LOSS -> {
                        JdcrPlayerLog.w("getPlaybackSuppressionReason=暂时失去音频焦点(仍想播但暂不出声)")
                    }

                    else ->
                        JdcrPlayerLog.d("getPlaybackSuppressionReason=$playbackSuppressionReason")
                }
            }

        }
        _exoPlayer.addListener(listener)
    }

    private fun setupProgressListener() {
        progressJob?.cancel()
        progressJob = null
        progressJob = _scope.launch(Dispatchers.Main) {
            while (isActive) {
                if (_exoPlayer.isPlaying) {
                    val position = _exoPlayer.currentPosition
                    _progressFlow.tryEmit(position)
                    JdcrPlayerLog.v("播放进度:$position")
                }
                delay(config.progressIntervalMs)
            }
        }
    }

    protected fun source2Item(source: JdcrMediaSource): MediaItem {
        val safeUri = source.uri.toUri().buildUpon().build()
        return MediaItem.Builder().setUri(safeUri).setMediaId(source.id).setTag(source).build()
    }

    protected inline fun <T> runMain(crossinline block: () -> T): T {
        return if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            runBlocking(Dispatchers.Main.immediate) {
                block()
            }
        }
    }

    override fun addMediaSource(
        mediaSource: JdcrMediaSource,
        index: Int?
    ) {
        runMain {
            if (index == null) {
                JdcrPlayerLog.i("追加媒体文件:$mediaSource")
                getPlayer().addMediaItem(source2Item(mediaSource))
            } else {
                JdcrPlayerLog.i("插入媒体文件:$mediaSource")
                getPlayer().addMediaItem(index, source2Item(mediaSource))
            }
        }
    }

    override fun setCurrentSource(source: JdcrMediaSource) {
        runMain {
            JdcrPlayerLog.i("设置了当前媒体文件:$source")
            val mediaItem = source2Item(source)
            _exoPlayer.setMediaItem(mediaItem)
        }
    }

    override fun prepare() {
        runMain {
            _exoPlayer.prepare()
        }
    }

    override fun start() {
        runMain {
            _exoPlayer.play()
            isStopped = false
        }
    }

    override fun seekItemIndex(index: Int) {
        runMain {
            _exoPlayer.seekTo(index, 0)
        }
    }

    override fun seekItemLast() {
        seekItemIndex(_exoPlayer.mediaItemCount -1)
    }

    override fun pause() {
        runMain {
            _exoPlayer.pause()
        }
    }

    override fun resume() {
        runMain {
            _exoPlayer.play()
        }
    }

    override fun stop() {
        runMain {
            isStopped = true
            _exoPlayer.stop()
        }
    }

    override fun seekToMs(positionMs: Long) {
        runMain {
            _exoPlayer.seekTo(positionMs)
        }
    }

    override fun setVolume(volume: Float) {
        runMain {
            _exoPlayer.volume = volume
        }
    }

    override fun setRepeatMode(repeatMode: JdcrPlayerRepeatMode) {
        runMain {
            _exoPlayer.repeatMode = when (repeatMode) {
                JdcrPlayerRepeatMode.RepeatNo -> Player.REPEAT_MODE_OFF
                JdcrPlayerRepeatMode.RepeatOne -> Player.REPEAT_MODE_ONE
                JdcrPlayerRepeatMode.RepeatAll -> Player.REPEAT_MODE_ALL
            }
        }
    }

    override fun getState(): JdcrPlayerState {
        return _stateFlow.value
    }

    override fun getStateFlow(): StateFlow<JdcrPlayerState> {
        return _stateFlow
    }

    override fun getCurrentPosition(): Long {
        return runMain {
            _exoPlayer.currentPosition
        }
    }

    override fun getDuration(): Long {
        return runMain {
            _exoPlayer.duration
        }
    }

    override fun getBufferedPosition(): Long {
        return runMain {
            _exoPlayer.bufferedPosition
        }
    }

    override fun isPlaying(): Boolean {
        return runMain {
            _exoPlayer.isPlaying
        }
    }

    override fun isReady(): Boolean {
        return runMain {
            _exoPlayer.playbackState == Player.STATE_READY
        }
    }

    override fun getRepeatMode(): JdcrPlayerRepeatMode {
        return when (_exoPlayer.repeatMode) {
            Player.REPEAT_MODE_ONE -> JdcrPlayerRepeatMode.RepeatOne
            Player.REPEAT_MODE_ALL -> JdcrPlayerRepeatMode.RepeatAll
            else -> JdcrPlayerRepeatMode.RepeatNo
        }
    }

    override fun getProgressFlow(): Flow<Long> {
        return _progressFlow
    }

    override fun getCurrentIndex(): Int {
        return runMain {
            _exoPlayer.currentMediaItemIndex
        }
    }

    override fun getCurrentIndexFlow(): Flow<Int> {
        return _currentIndexFlow
    }

    override fun next() {
        runMain {
            _exoPlayer.seekToNextMediaItem()
        }
    }

    override fun previous() {
        runMain {
            _exoPlayer.seekToPreviousMediaItem()
        }
    }

    protected fun removeMediaSourceInternal(mediaSource: JdcrMediaSource) {
        getPlayer().apply {
            for (i in mediaItemCount - 1 downTo 0) {
                if (mediaSource.id == getMediaItemAt(i).mediaId) {
                    JdcrPlayerLog.i("player移除item:$mediaSource")
                    removeMediaItem(i)
                }
            }
        }
    }

    override fun removeMediaSource(mediaSource: JdcrMediaSource) {
        runMain {
            removeMediaSourceInternal(mediaSource)
        }
    }

    override fun removeMediaSource(index: Int) {
        runMain {
            getPlayer().removeMediaItem(index)
        }
    }

    @MainThread
    override fun getCurrentMediaSource(): JdcrMediaSource? {
        return _exoPlayer.currentMediaItem?.localConfiguration?.tag as? JdcrMediaSource
    }

    protected open fun handlePlayEnded() {

    }

    private fun autoPlayFirstFrame() {
        getCurrentMediaSource()?.continuedSource?.apply {
            if (false) {
                JdcrPlayerLog.i("首帧渲染完成,自动播放:" + getCurrentResouceMessage())
                getPlayer().play()
            }
        }
    }

    protected fun getCurrentResouceMessage(): String {
        return "${_exoPlayer.currentMediaItemIndex}=${getCurrentMediaSource()}"
    }

    private fun getCurrentCountMessage(): String {
        return "当前index:" + _exoPlayer.currentMediaItemIndex + ",总共资源:" + _exoPlayer.mediaItemCount
    }

    protected fun getCoroutine(): CoroutineScope {
        return _scope
    }

    @MainThread
    override fun release() {
        JdcrPlayerLog.i("释放资源")
        _scope.cancelChildren()
        progressJob?.cancel()
        progressJob = null
        _exoPlayer.release()
    }

    @MainThread
    override fun onDestroy() {
        JdcrPlayerLog.w("执行销毁")
        release()
        _scope.cancel()
    }

}