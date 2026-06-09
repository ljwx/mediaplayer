package com.jdcr.jdcrmediaplayer.stream

enum class JdcrChunkTransport {
    BASE64,   // 当前后端
    BINARY,   // 未来：WS 二进制帧，直接是字节
    HEX
}

/** 音频容器/编码：解出来的字节是什么音频 */
enum class JdcrAudioFormat(val mimeType: String, val isRawPcm: Boolean) {
    MP3("audio/mpeg", false),
    AAC("audio/aac", false),
    OGG("audio/ogg", false),
    WAV("audio/wav", false),
    PCM_16("audio/pcm", true);   // 裸流，需 AudioTrack
}

data class JdcrStreamSpec(
    val format: JdcrAudioFormat = JdcrAudioFormat.MP3,
    val transport: JdcrChunkTransport = JdcrChunkTransport.BASE64,
    // PCM 专用，容器格式忽略
    val sampleRate: Int = 16000,
    val channels: Int = 1,
    val handleAudioFocus: Boolean = false
)