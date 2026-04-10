package com.jdcr.jdcrmediaplayer.define

data class JdcrPlayerSource(
    val id: String,
    val uri: String,
    val title: String? = null,
    val durationMs: Long? = null,
    val mimeType: String? = null,
    val autoPlayParams: SourceAutoPlay? = null,
    val extraParams: ExtraParams? = null
)

data class SourceAutoPlay(
    val autoPlayFirstFrame: Boolean? = true,
    val mute: Boolean? = null,
    val loop: Boolean? = null,
    val isDefault: Boolean? = null,
    val autoRemove: Boolean? = null
)

data class ExtraParams(val block: Boolean?)