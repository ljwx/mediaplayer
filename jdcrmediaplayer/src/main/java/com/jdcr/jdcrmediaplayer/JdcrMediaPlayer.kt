package com.jdcr.jdcrmediaplayer

import android.content.Context
import android.widget.FrameLayout.LayoutParams
import android.widget.ImageView
import com.jdcr.jdcrmediaplayer.cache.JdcrCacheConfig
import com.jdcr.jdcrmediaplayer.config.ErrorPolicy
import com.jdcr.jdcrmediaplayer.config.JdcrPlayerConfig
import com.jdcr.jdcrmediaplayer.config.LocalCache
import com.jdcr.jdcrmediaplayer.define.JdcrMediaSource
import com.jdcr.jdcrmediaplayer.define.JdcrPlayerView
import com.jdcr.jdcrmediaplayer.util.JdcrPlayerLog
import com.jdcr.jdcrmediaplayer.util.JdcrPlayerUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class JdcrMediaPlayer(
    context: Context,
    private val playerView: JdcrPlayerView,
    private val config: JdcrPlayerConfig = JdcrPlayerConfig.DEFAULT
) :
    JdcrPlayerCore(context, playerView, config) {

    private var captureView: ImageView? = null

    override fun getCacheConfig(
        ctx: Context,
        cache: LocalCache,
        errorPolicy: ErrorPolicy?
    ): JdcrCacheConfig {
        return JdcrCacheConfig(
            JdcrPlayerUtils.getDefaultSourceFactory(ctx, cache, errorPolicy)
        )
    }

    override fun setPlayList(
        mediaSources: List<JdcrMediaSource>,
        index: Int?
    ) {
        runMain {
            val mediaItems = mediaSources.map { source ->
                source2Item(source)
            }
            if (index == null) {
                getPlayer().setMediaItems(mediaItems)
            } else {
                getPlayer().setMediaItems(mediaItems, index, 0)
            }
        }
    }

    override fun addPlayList(mediaSources: List<JdcrMediaSource>) {
        runMain {
            val mediaItems = mediaSources.map { source ->
                source2Item(source)
            }
            getPlayer().addMediaItems(mediaItems)
        }
    }

    override fun cleanPlayList() {
        runMain {
            getPlayer().clearMediaItems()
        }
    }

    override fun getPlayListSize(): Int {
        return getPlayer().mediaItemCount
    }

    override fun setDefaultSource(default: JdcrMediaSource?) {
        this.defaultMedia = default
    }

    override fun handlePlayEnded() {
        super.handlePlayEnded()
        runMain {
            playEndAutoRemoveSource()
        }
    }

    private fun getLastMediaSource(): JdcrMediaSource? {
        if (getPlayer().mediaItemCount > 0) {
            try {
                return getPlayer().getMediaItemAt(getPlayer().mediaItemCount - 1).localConfiguration?.tag as? JdcrMediaSource
            } catch (e: Exception) {
                return null
            }
        }
        return null
    }

    private fun playEndAutoRemoveSource() {
        val item = getCurrentMediaSource()
        JdcrPlayerLog.i("判断是否需要播放默认表情:${getPlayer().mediaItemCount}")
        if (item?.continuedSource?.isDefaultSource == true || getPlayer().mediaItemCount < 1) {
            playEndAutoPlayDefault(getPlayer().mediaItemCount)
        } else {
            item?.continuedSource?.apply {
                if (playEndAutoRemove == true) {
                    JdcrPlayerLog.i("自动移除当前播放完成的媒体资源:" + getCurrentResouceMessage())
                    removeMediaSourceInternal(item)
                    if (loopLastDefault == true) {
                        playEndAutoPlayDefault(getPlayer().mediaItemCount)
                    }
                }
            }
        }
    }

    private fun playEndAutoPlayDefault(preLength: Int) {
        if (isStopped) return
        getCoroutine().launch {
            val d = 0L
            delay(d)
            if (isStopped) return@launch
            val dm = defaultMedia
            if (dm == null) {
                JdcrPlayerLog.i("没有设置默认表情,不播放默认表情")
                return@launch
            }
            if (getPlayer().mediaItemCount > preLength) {
                JdcrPlayerLog.i("有新的表情进来,不播放默认表情")
                return@launch
            }
            withContext(Dispatchers.Main.immediate) {
                val lastSource = getLastMediaSource()
                JdcrPlayerLog.i("$d,毫秒没有新表情,开始播放默认表情:$defaultMedia")
                if (dm.uri == lastSource?.uri) {
                    JdcrPlayerLog.i("最后一个就是默认表情,直接播")
                } else {
                    JdcrPlayerLog.i("添加默认表情,再播")
                    addMediaSource(dm)
                }
                seekItemLast()
                seekToMs(0)
                getPlayer().prepare()
                getPlayer().play()
            }
        }
    }

    fun capturePreviewBitmap(): android.graphics.Bitmap? {
        return null
    }

    override fun captureStart() {
        captureEnd()
        playerView.post {
            val bitmap = capturePreviewBitmap()
            if (bitmap != null) {
                captureView = ImageView(playerView.context).apply {
                    setImageBitmap(bitmap)
                    layoutParams = LayoutParams(
                        LayoutParams.MATCH_PARENT,
                        LayoutParams.MATCH_PARENT
                    )
                }
                playerView.addView(captureView)
            }
        }
    }

    override fun captureEnd() {
        playerView.post {
            captureView?.let {
                it.setImageDrawable(null)
                playerView.removeView(it)
                captureView = null
            }
        }
    }

}