package com.jdcr.jdcrmediaplayer.stream

import android.util.Base64

internal object ChunkTransport {
    fun decode(chunk: String, transport: JdcrChunkTransport): ByteArray {
        return when (transport) {
            JdcrChunkTransport.BASE64 -> Base64.decode(strip(chunk), Base64.DEFAULT)
            JdcrChunkTransport.HEX -> decodeHex(chunk.trim())
            JdcrChunkTransport.BINARY -> chunk.toByteArray() // 一般不会走字符串重载
        }
    }
    /** 去掉 data:audio/mpeg;base64, 前缀（如果有） */
    private fun strip(value: String): String {
        val t = value.trim()
        val i = t.indexOf("base64,")
        return if (i >= 0) t.substring(i + "base64,".length) else t
    }
    private fun decodeHex(s: String): ByteArray =
        ByteArray(s.length / 2) { ((s[it * 2].digitToInt(16) shl 4) + s[it * 2 + 1].digitToInt(16)).toByte() }
}