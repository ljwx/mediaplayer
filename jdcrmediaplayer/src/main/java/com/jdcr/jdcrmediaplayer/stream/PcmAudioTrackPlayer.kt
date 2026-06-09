package com.jdcr.jdcrmediaplayer.stream

import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack

/**
 * PCM 裸流播放器。AudioTrack.write 为阻塞写（天然背压）。
 * write/release 互斥，保证 drainer 写入与释放并发时不崩溃。
 */
internal class PcmAudioTrackPlayer(
    private val sampleRate: Int,
    private val channels: Int
) {
    private val lock = Any()
    private var track: AudioTrack? = null

    @Volatile
    private var released = false

    fun start() {
        synchronized(lock) {
            if (released || track != null) return
            val channelMask = if (channels == 1)
                AudioFormat.CHANNEL_OUT_MONO else AudioFormat.CHANNEL_OUT_STEREO
            val minBuf = AudioTrack.getMinBufferSize(
                sampleRate, channelMask, AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(1)
            track = AudioTrack(
                AudioManager.STREAM_MUSIC, sampleRate, channelMask,
                AudioFormat.ENCODING_PCM_16BIT, minBuf * 4, AudioTrack.MODE_STREAM
            ).also { it.play() }
        }
    }

    fun write(bytes: ByteArray) {
        synchronized(lock) {
            if (released) return
            runCatching { track?.write(bytes, 0, bytes.size) }
        }
    }

    fun endOfStream() { /* PCM 没有结束帧，业务自己控制 */ }

    fun release() {
        synchronized(lock) {
            released = true
            track?.runCatching {
                if (playState != AudioTrack.PLAYSTATE_STOPPED) stop()
                release()
            }
            track = null
        }
    }
}
