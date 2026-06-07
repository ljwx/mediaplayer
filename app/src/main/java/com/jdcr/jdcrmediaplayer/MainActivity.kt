package com.jdcr.jdcrmediaplayer

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.jdcr.jdcrmediaplayer.define.JdcrPlayerView
import com.jdcr.jdcrmediaplayer.define.JdcrPlayer
import com.jdcr.jdcrmediaplayer.test.Test
import com.jdcr.jdcrmediaplayer.ui.theme.JdcrMediaPlayerTheme
import com.jdcr.jdcrmediaplayer.util.JdcrPlayerLog
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        JdcrPlayerLog.enable(true, cacheDir.absolutePath+"/log.txt")
        setContent {
            JdcrMediaPlayerTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Greeting(
                        name = "Android",
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val playerView = remember { JdcrPlayerView(context) }
    val player = JdcrMediaPlayer(context, playerView)
    Test.player = player
    Test.coroutine = lifecycleOwner.lifecycleScope

    LaunchedEffect(Unit) {
        delay(500)
    }

    Column {
        Text(
            text = "Hello $name!",
            modifier = modifier
        )
        Row {
            Button(onClick = {
                Test.autoPlay()
            }) {
                Text("一次性播放")
            }
            Button(onClick = {
                Test.loopPlay()
            }) {
                Text("轮询播")
            }
            Button(onClick = {
                Test.stop()
            }) {
                Text("停止")
            }
            Button(onClick = {
                Test.clear()
            }) {
                Text("清除")
            }
        }
        AndroidView(factory = { context ->
            playerView
        }, update = {

        })
    }
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    JdcrMediaPlayerTheme {
        Greeting("Android")
    }
}