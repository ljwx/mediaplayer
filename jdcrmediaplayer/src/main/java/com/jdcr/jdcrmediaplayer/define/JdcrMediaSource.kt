package com.jdcr.jdcrmediaplayer.define

data class JdcrMediaSource(
    val id: String = System.currentTimeMillis().toString(),
    val uri: String,
    val title: String? = null,
    val durationMs: Long? = null,
    val mimeType: String? = null,
    val continuedSource: ContinuedMediaSource? = null,
)

data class ContinuedMediaSource(
    val isDefaultSource: Boolean? = null,
    val block: Boolean? = null,
    val mute: Boolean? = null,
    val loopLastDefault: Boolean? = null,
    val playEndAutoRemove: Boolean? = null,
    val volumePercent: Float? = null
)