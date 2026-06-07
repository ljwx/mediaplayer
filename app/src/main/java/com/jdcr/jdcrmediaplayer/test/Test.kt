package com.jdcr.jdcrmediaplayer.test

import android.annotation.SuppressLint
import com.jdcr.jdcrmediaplayer.JdcrMediaPlayer
import com.jdcr.jdcrmediaplayer.define.JdcrMediaSource
import com.jdcr.jdcrmediaplayer.define.JdcrPlayer
import com.jdcr.jdcrmediaplayer.define.JdcrPlayerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object Test {

    lateinit var player: JdcrMediaPlayer
    lateinit var coroutine: CoroutineScope
    private val channel = Channel<Any?>()


    private var medias = getTestList()
    private var preItem: JdcrMediaSource? = null

    fun autoPlay() {
        medias = getTestList()
        player.setPlayList(medias)
        player.prepare()
        player.start()
    }

    @SuppressLint("NewApi")
    fun loopPlay() {
        coroutine.coroutineContext.cancelChildren()
        medias = getTestList()
        player.setDefaultSource(step1ConfigDefault.toSource())
        coroutine.launch {
            for (item in channel) {
                if (item is JdcrMediaSource) {
                    if (item.continuedSource?.block == true) {
                        player.setCurrentSource(item)
                    } else {
                        player.addMediaSource(item)
                        player.seekItemLast()
                    }
                    player.prepare()
                    player.start()
                }
            }
        }
        switch(player)
        channel.trySend(medias.removeFirst())
    }

    fun stop() {
        coroutine.coroutineContext.cancelChildren()
        player.cleanPlayList()
        player.stop()

    }

    fun clear() {
        player.cleanPlayList()
    }

    private fun switch(player: JdcrPlayer) {
        coroutine.launch {
            player.getStateFlow().collect {
                if (it is JdcrPlayerState.ENDED) {
                    var item = getNext()
                    if (item?.continuedSource?.isDefaultSource == true) {
                        Test.player.setDefaultSource(item)
                        item = getNext()
                    }
                    if (preItem?.continuedSource?.loopLastDefault == true) {
                    }
                    delay(25)
                    preItem = item
                    channel.trySend(item)
                }
            }
        }
    }

    @SuppressLint("NewApi")
    private fun getNext(): JdcrMediaSource? {
        if (medias.isEmpty()) {
            return null
        }
        return medias.removeFirst()
    }

}