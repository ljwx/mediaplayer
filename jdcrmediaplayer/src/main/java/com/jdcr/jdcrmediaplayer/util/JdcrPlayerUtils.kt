package com.jdcr.jdcrmediaplayer.util

import android.content.Context
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.database.StandaloneDatabaseProvider
import com.google.android.exoplayer2.source.DefaultMediaSourceFactory
import com.google.android.exoplayer2.source.MediaSourceFactory
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource
import com.google.android.exoplayer2.upstream.DefaultLoadErrorHandlingPolicy
import com.google.android.exoplayer2.upstream.HttpDataSource
import com.google.android.exoplayer2.upstream.LoadErrorHandlingPolicy
import com.google.android.exoplayer2.upstream.cache.Cache
import com.google.android.exoplayer2.upstream.cache.CacheDataSource
import com.google.android.exoplayer2.upstream.cache.LeastRecentlyUsedCacheEvictor
import com.google.android.exoplayer2.upstream.cache.SimpleCache
import java.io.File
import java.net.UnknownHostException

object JdcrPlayerUtils {

    private val sizeMB = 1024 * 1024
    private val defaultMB = 500L //500M
    private val defaultPath = "exoplayer_cache"

    @Volatile
    private var defaultCache: Cache? = null
    private var defaultTimeout = 1000

    private fun getDefaultCache(context: Context): Cache {
        if (defaultCache == null) {
            synchronized(this) {
                if (defaultCache == null) {
                    val cacheDir = File(context.applicationContext.cacheDir, defaultPath)
                    val cacheSize = defaultMB * sizeMB
                    val cacheEvictor = LeastRecentlyUsedCacheEvictor(cacheSize)
                    val databaseProvider = StandaloneDatabaseProvider(context)
                    defaultCache = SimpleCache(cacheDir, cacheEvictor, databaseProvider)
                    JdcrPlayerLog.d("默认缓存,路径:$cacheDir,大小MB:$cacheSize")
                }
            }
        }
        return defaultCache!!
    }

    fun getDefaultSourceFactory(context: Context): MediaSourceFactory {
        // 1. 创建基础的网络数据源
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(defaultTimeout)
            .setReadTimeoutMs(defaultTimeout)

        // 2. 创建缓存数据源工厂，将网络数据源包装起来
        val cacheDataSourceFactory = CacheDataSource.Factory()
            .setCache(getDefaultCache(context)) // 设置刚才的 Cache 实例
            .setUpstreamDataSourceFactory(httpDataSourceFactory) // 未命中缓存时，使用网络数据源下载
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR) // 发生错误时忽略缓存，直接走网络

        val errorHandlingPolicy = object : DefaultLoadErrorHandlingPolicy() {
            // 拦截重试延迟时间
            override fun getRetryDelayMsFor(loadErrorInfo: LoadErrorHandlingPolicy.LoadErrorInfo): Long {
                val e = loadErrorInfo.exception

                // 1. 如果是彻底断网 (UnknownHostException) -> 立刻抛出，不重试
                val isOffline = e is UnknownHostException || e.cause is UnknownHostException
                if (isOffline) {
                    return C.TIME_UNSET
                }

                // 2. 如果是弱网导致的读取超时/连接超时 (HttpDataSourceException) 
                // -> 允许重试，走默认的退避延迟逻辑 (或者你可以自定义延迟比如 return 1000L)
                // 这里我们直接 return super，让 ExoPlayer 使用默认的逐渐递增的延迟策略
                val isTimeout = e is HttpDataSource.HttpDataSourceException
                if (isTimeout) {
                    return super.getRetryDelayMsFor(loadErrorInfo)
                }

                // 对于其他错误，走默认逻辑
                return super.getRetryDelayMsFor(loadErrorInfo)
            }

            // 决定最多重试多少次
            override fun getMinimumLoadableRetryCount(dataType: Int): Int {
                // 弱网情况下，最多重试 3 次 (这是 ExoPlayer 的默认经典配置)
                return 3
            }
        }

        JdcrPlayerLog.i("返回默认缓存配置")
        return DefaultMediaSourceFactory(cacheDataSourceFactory).setLoadErrorHandlingPolicy(
            errorHandlingPolicy
        )
    }

}