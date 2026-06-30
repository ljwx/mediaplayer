package com.jdcr.jdcrmediaplayer.datasource

import android.util.Base64

enum class JdcrAudioStreamSource {
    BINARY,   // 二进制帧，直接是字节
    HEX,
    BASE64,
}

sealed class JdcrAudioStreamTarget(val mimeType: String) {

    sealed class Encoded(mimeType: String) : JdcrAudioStreamTarget(mimeType) {
        object MP3 : Encoded("audio/mpeg")
        object AAC : Encoded("audio/aac")
        object WAV : Encoded("audio/wav")
        object OGG : Encoded("audio/ogg")
    }

    object PCM : JdcrAudioStreamTarget("audio/pcm")

}

internal fun base64Decode(source: String): ByteArray {
    fun strip(value: String): String {
        val t = value.trim()
        val i = t.indexOf("base64,")
        return if (i >= 0) t.substring(i + "base64,".length) else t
    }
    return Base64.decode(strip(source), Base64.DEFAULT)
}

internal fun binaryDecode(binary: String): ByteArray {
    return binary.toByteArray()
}

internal fun hexDecode(hex: String): ByteArray {
    return ByteArray(hex.length / 2) {
        ((hex[it * 2].digitToInt(16) shl 4) + hex[it * 2 + 1].digitToInt(
            16
        )).toByte()
    }
}