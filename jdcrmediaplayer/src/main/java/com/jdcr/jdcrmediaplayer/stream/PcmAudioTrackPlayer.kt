package com.jdcr.jdcrmediaplayer.stream

import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
internal class PcmAudioTrackPlayer(
    private val sampleRate: Int,
    private val channels: Int
) {
    private var track: AudioTrack? = null
    fun start() {
        val channelMask = if (channels == 1)
            AudioFormat.CHANNEL_OUT_MONO else AudioFormat.CHANNEL_OUT_STEREO
        val minBuf = AudioTrack.getMinBufferSize(
            sampleRate, channelMask, AudioFormat.ENCODING_PCM_16BIT
        )
        track = AudioTrack(
            AudioManager.STREAM_MUSIC, sampleRate, channelMask,
            AudioFormat.ENCODING_PCM_16BIT, minBuf * 4, AudioTrack.MODE_STREAM
        ).also { it.play() }
    }
    fun write(bytes: ByteArray) {
        track?.write(bytes, 0, bytes.size)   // 阻塞写，天然背压
    }
    fun endOfStream() { /* PCM 没有结束帧，业务自己控制 */ }
    fun release() {
        track?.runCatching { stop(); release() }
        track = null
    }
}