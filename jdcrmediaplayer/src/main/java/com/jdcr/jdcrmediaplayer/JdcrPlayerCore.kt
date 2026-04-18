package com.jdcr.jdcrmediaplayer

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.net.Uri
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
import com.jdcr.jdcrmediaplayer.define.JdcrPlayerSource
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
import kotlinx.coroutines.withContext
import androidx.core.net.toUri
import com.jdcr.jdcrmediaplayer.config.JdcrPlayerConfig

abstract class JdcrPlayerCore(
    context: Context,
    playerView: JdcrPlayerView,
    private val config: JdcrPlayerConfig
) : JdcrPlayer {

    private val coroutineExceptionHandler = CoroutineExceptionHandler { _, e ->
        e.printStackTrace()
        JdcrPlayerLog.e("MediaPlayer协程收到异常", e)
    }
    private val rootJob = SupervisorJob()
    private var _scope: CoroutineScope =
        CoroutineScope(Dispatchers.Main.immediate + rootJob + coroutineExceptionHandler)

    @SuppressLint("UnsafeOptInUsageError")
    private val _exoPlayer: ExoPlayer = ExoPlayer.Builder(context).apply {
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

    init {
        setupPlayerListener()
        setupProgressListener()
    }

    protected fun getPlayer(): ExoPlayer {
        return _exoPlayer
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
                    Player.STATE_READY -> JdcrPlayerState.READY(getCurrentMediaSource())
                    Player.STATE_ENDED -> {
                        handlePlayEnded()
                        JdcrPlayerState.ENDED(getCurrentMediaSource())
                    }

                    else -> JdcrPlayerState.IDLE(getCurrentMediaSource())
                }
                JdcrPlayerLog.i("播放状态变更:" + newState.desc + "," + getCurrentCountMessage())
                _stateFlow.tryEmit(newState)
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                super.onIsPlayingChanged(isPlaying)
                if (isPlaying) {
                    _stateFlow.tryEmit(JdcrPlayerState.PLAYING(getCurrentMediaSource()))
                } else {
                    if (_exoPlayer.playbackState == Player.STATE_READY) {
                        _stateFlow.tryEmit(JdcrPlayerState.PAUSE(getCurrentMediaSource()))
                    }
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
                _stateFlow.tryEmit(JdcrPlayerState.ERROR(getCurrentMediaSource(), playbackError))
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
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
                    JdcrPlayerLog.d("播放列表发生变化:" + getCurrentCountMessage())
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

    protected fun source2Item(source: JdcrPlayerSource): MediaItem {
        val safeUri = source.uri.toUri().buildUpon().build()
        return MediaItem.Builder().setUri(safeUri).setMediaId(source.id).setTag(source).build()
    }

    private inline fun <T> runMain(crossinline block: () -> T): T {
        return if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            runBlocking(Dispatchers.Main.immediate) {
                block()
            }
        }
    }

    override fun addMediaSource(
        mediaSource: JdcrPlayerSource,
        index: Int?
    ) {
        _scope.launch(Dispatchers.Main.immediate) {
            if (index == null) {
                JdcrPlayerLog.i("追加媒体文件:$mediaSource")
                getPlayer().addMediaItem(source2Item(mediaSource))
            } else {
                JdcrPlayerLog.i("插入媒体文件:$mediaSource")
                getPlayer().addMediaItem(index, source2Item(mediaSource))
            }
        }
    }

    override fun setCurrentSource(source: JdcrPlayerSource) {
        _scope.launch(Dispatchers.Main.immediate) {
            JdcrPlayerLog.i("切换媒体文件:$source")
            val mediaItem = source2Item(source)
            _exoPlayer.setMediaItem(mediaItem)
        }
    }

    override fun prepare() {
        _scope.launch(Dispatchers.Main.immediate) {
            _exoPlayer.prepare()
        }
    }

    override fun start() {
        _scope.launch(Dispatchers.Main.immediate) {
            _exoPlayer.play()
        }
    }

    override fun playIndex(index: Int) {
        _scope.launch(Dispatchers.Main.immediate) {
            _exoPlayer.seekTo(index, 0)
        }
    }

    override fun pause() {
        _scope.launch(Dispatchers.Main.immediate) {
            _exoPlayer.pause()
        }
    }

    override fun resume() {
        _scope.launch(Dispatchers.Main.immediate) {
            _exoPlayer.play()
        }
    }

    override fun stop() {
        _scope.launch(Dispatchers.Main.immediate) {
            _exoPlayer.stop()
        }
    }

    override fun seekTo(positionMs: Long) {
        _scope.launch(Dispatchers.Main.immediate) {
            _exoPlayer.seekTo(positionMs)
        }
    }

    override fun setVolume(volume: Float) {
        _scope.launch(Dispatchers.Main) {
            _exoPlayer.volume = volume
        }
    }

    override fun setRepeatMode(repeatMode: JdcrPlayerRepeatMode) {
        _scope.launch(Dispatchers.Main) {
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
        _scope.launch(Dispatchers.Main) {
            _exoPlayer.seekToNextMediaItem()
        }
    }

    override fun previous() {
        _scope.launch(Dispatchers.Main) {
            _exoPlayer.seekToPreviousMediaItem()
        }
    }

    private fun removeMediaSourceInternal(mediaSource: JdcrPlayerSource) {
        runMain {
            getPlayer().apply {
                for (i in mediaItemCount - 1 downTo 0) {
                    if (mediaSource.id == getMediaItemAt(i).mediaId) {
                        JdcrPlayerLog.i("player移除item:$mediaSource")
                        removeMediaItem(i)
                    }
                }
            }
        }
    }

    override fun removeMediaSource(mediaSource: JdcrPlayerSource) {
        removeMediaSourceInternal(mediaSource)
    }

    override fun removeMediaSource(index: Int) {
        _scope.launch(Dispatchers.Main) {
            getPlayer().removeMediaItem(index)
        }
    }

    @MainThread
    override fun getCurrentMediaSource(): JdcrPlayerSource? {
        return _exoPlayer.currentMediaItem?.localConfiguration?.tag as? JdcrPlayerSource
    }

    private fun handlePlayEnded() {
        _scope.launch {
            autoRemove()
            autoPlayDefault()
        }
    }

    private suspend fun autoRemove() {
        withContext(Dispatchers.Main) {
            val item = getCurrentMediaSource()
            item?.autoPlayParams?.apply {
                if (autoRemove == true) {
                    JdcrPlayerLog.i("自动移除当前播放完成的媒体资源:" + getCurrentResouceMessage())
                    removeMediaSourceInternal(item)
                }
            }
        }
    }

    private suspend fun autoPlayDefault() {
        withContext(Dispatchers.Main) {
            if (_exoPlayer.mediaItemCount == 1) {
                getCurrentMediaSource()?.autoPlayParams?.apply {
                    if (isDefault == true || loop == true) {
                        JdcrPlayerLog.i("自动重播默认视频:" + getCurrentResouceMessage())
                        _exoPlayer.seekTo(0)
                        _exoPlayer.play()
                    }
                }
            }
        }
    }

    private fun autoPlayFirstFrame() {
        getCurrentMediaSource()?.autoPlayParams?.apply {
            if (autoPlayFirstFrame == true) {
                JdcrPlayerLog.i("首帧渲染完成,自动播放:" + getCurrentResouceMessage())
                start()
            }
        }
    }

    private fun getCurrentResouceMessage(): String {
        return "${_exoPlayer.currentMediaItemIndex}=${getCurrentMediaSource()}"
    }

    private fun getCurrentCountMessage(): String {
        return "当前index:" + _exoPlayer.currentMediaItemIndex + ",总共资源:" + _exoPlayer.mediaItemCount
    }

    @MainThread
    override fun release() {
        JdcrPlayerLog.i("释放资源")
        rootJob.cancelChildren()
        progressJob?.cancel()
        progressJob = null
        _exoPlayer.release()
    }

    @MainThread
    override fun onDestroy() {
        JdcrPlayerLog.w("执行销毁")
        release()
        rootJob.cancel()
    }

}