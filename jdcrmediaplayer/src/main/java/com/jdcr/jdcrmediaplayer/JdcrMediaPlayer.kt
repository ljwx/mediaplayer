package com.jdcr.jdcrmediaplayer

import android.content.Context
import com.jdcr.jdcrmediaplayer.cache.JdcrCacheConfig
import com.jdcr.jdcrmediaplayer.config.ErrorPolicy
import com.jdcr.jdcrmediaplayer.config.JdcrPlayerConfig
import com.jdcr.jdcrmediaplayer.config.LocalCache
import com.jdcr.jdcrmediaplayer.define.JdcrPlayerSource
import com.jdcr.jdcrmediaplayer.define.JdcrPlayerView
import com.jdcr.jdcrmediaplayer.util.JdcrPlayerUtils

class JdcrMediaPlayer(
    context: Context,
    playerView: JdcrPlayerView,
    private val config: JdcrPlayerConfig = JdcrPlayerConfig.DEFAULT
) :
    JdcrPlayerCore(context, playerView, config) {

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
        mediaSources: List<JdcrPlayerSource>,
        index: Int?
    ) {
        val mediaItems = mediaSources.map { source ->
            source2Item(source)
        }
        if (index == null) {
            getPlayer().setMediaItems(mediaItems)
        } else {
            getPlayer().setMediaItems(mediaItems, index, 0)
        }
    }

    override fun cleanPlayList() {
        getPlayer().clearMediaItems()
    }

    override fun getPlayListSize(): Int {
        return getPlayer().mediaItemCount
    }

}