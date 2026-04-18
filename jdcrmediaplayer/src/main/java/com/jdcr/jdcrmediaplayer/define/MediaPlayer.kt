package com.jdcr.jdcrmediaplayer.define

import android.content.Context
import com.jdcr.jdcrmediaplayer.cache.JdcrCacheConfig
import com.jdcr.jdcrmediaplayer.config.ErrorPolicy
import com.jdcr.jdcrmediaplayer.config.LocalCache
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface MediaPlayerCore {

    fun getCacheConfig(
        context: Context,
        cache: LocalCache,
        errorPolicy: ErrorPolicy?
    ): JdcrCacheConfig

    fun addMediaSource(mediaSource: JdcrPlayerSource, index: Int? = null)

    fun setCurrentSource(source: JdcrPlayerSource)

    fun removeMediaSource(mediaSource: JdcrPlayerSource)

    fun removeMediaSource(index: Int)

    fun getCurrentMediaSource(): JdcrPlayerSource?

    fun prepare()

    fun isReady(): Boolean

    fun setRepeatMode(repeatMode: JdcrPlayerRepeatMode)

    fun seekTo(positionMs: Long)

    fun setVolume(volume: Float)

    fun getState(): JdcrPlayerState

    fun isPlaying(): Boolean

    fun getDuration(): Long

    fun getCurrentPosition(): Long

    fun getBufferedPosition(): Long

    fun getRepeatMode(): JdcrPlayerRepeatMode

}

interface MediaPlayerState {
    fun getStateFlow(): StateFlow<JdcrPlayerState>

    fun getProgressFlow(): Flow<Long>
}

interface MediaPlayer : MediaPlayerCore, MediaPlayerState {

    fun start()

    fun resume()

    fun pause()

    fun stop()

    fun release()

    fun onDestroy()

}

interface JdcrPlayer : MediaPlayer {

    fun setPlayList(mediaSources: List<JdcrPlayerSource>, index: Int? = null)

    fun cleanPlayList()

    fun getCurrentIndex(): Int

    fun next()

    fun previous()

    fun playIndex(index: Int)

    fun getPlayListSize(): Int

    fun getCurrentIndexFlow(): Flow<Int>

}