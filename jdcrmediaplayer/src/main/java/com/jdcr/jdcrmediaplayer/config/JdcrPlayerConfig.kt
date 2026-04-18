package com.jdcr.jdcrmediaplayer.config

data class LocalCache(
    val cacheSizeMB: Int = 500,
    val cacheDir: String = "exoplayer_cache",
    val requestTimeout: Int = 1000
)

data class ErrorPolicy(val retryTimes: Int = 3)

data class JdcrPlayerConfig(
    val localCache: LocalCache?,
    val errorPolicy: ErrorPolicy?,
    val progressIntervalMs: Long
) {
    companion object {
        val DEFAULT = JdcrPlayerConfig(LocalCache(), ErrorPolicy(), 500)
    }
}