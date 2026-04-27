package com.jdcr.jdcrmediaplayer.config

import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.audio.AudioAttributes

data class LocalCache(
    val cacheSizeMB: Int = 500,
    val cacheDir: String = "exoplayer_cache",
    val requestTimeout: Int = 1000
)

data class ErrorPolicy(val retryTimes: Int = 3)

data class JdcrPlayerConfig(
    val localCache: LocalCache?,
    val errorPolicy: ErrorPolicy?,
    val progressIntervalMs: Long,
    val audioAttributes: AudioAttributes,
) {
    companion object {
        val DEFAULT = JdcrPlayerConfig(
            LocalCache(), ErrorPolicy(), 500, AudioAttributes.Builder().setUsage(C.USAGE_MEDIA)
                .setContentType(C.CONTENT_TYPE_MUSIC).build()
        )
    }
}