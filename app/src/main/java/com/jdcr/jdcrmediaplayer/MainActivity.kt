package com.jdcr.jdcrmediaplayer

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import com.jdcr.jdcrmediaplayer.define.JdcrPlayerView
import com.jdcr.jdcrmediaplayer.define.JdcrPlayerSource
import com.jdcr.jdcrmediaplayer.ui.theme.JdcrMediaPlayerTheme
import kotlinx.coroutines.delay

private val default =
    "https://static.bcmcdn.com/wood2/bcmpet/resources/newmobile/%E5%B9%B3%E9%9D%99.mp4"
private val basketball =
    "https://static.bcmcdn.com/wood2/bcmpet/resources/newmobile/%E6%89%93%E7%AF%AE%E7%90%83.mp4"
private val angry =
    "https://static.bcmcdn.com/wood2/bcmpet/resources/newmobile/%E7%94%9F%E6%B0%94.mp4"

private val thinking =
    "https://static.bcmcdn.com/wood2/bcmpet/resources/mobile/%E5%B0%8F%E4%BD%93%E7%A7%AF%E6%80%9D%E8%80%83%E6%80%81.mp4"
private val answer_pre =
    "https://static.bcmcdn.com/wood2/bcmpet/resources/newmobile/%E5%9B%9E%E7%AD%94%E6%80%81_%E6%90%9E%E6%80%AA_%E5%BC%80%E5%A7%8B.mp4"
private val answer =
    "https://static.bcmcdn.com/wood2/bcmpet/resources/newmobile/%E5%9B%9E%E7%AD%94%E6%80%81_%E6%90%9E%E6%80%AA_%E5%BE%AA%E7%8E%AF.mp4"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
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

    val previewView = remember { JdcrPlayerView(context) }

    LaunchedEffect(Unit) {
        delay(500)
        startPlay(context, previewView)
    }

    Column {
        Text(
            text = "Hello $name!",
            modifier = modifier
        )
        AndroidView(factory = { context ->
            previewView
        }, update = {

        })
    }
}

private fun startPlay(
    context: Context,
    playerView: JdcrPlayerView
) {
    val helper = JdcrMediaPlayer(context, playerView)
    helper.setCurrentSource(JdcrPlayerSource("1", basketball))
    helper.prepare()
    helper.start()
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    JdcrMediaPlayerTheme {
        Greeting("Android")
    }
}