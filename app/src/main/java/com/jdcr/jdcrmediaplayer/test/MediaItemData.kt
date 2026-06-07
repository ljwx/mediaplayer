package com.jdcr.jdcrmediaplayer.test

import com.jdcr.jdcrmediaplayer.define.ContinuedMediaSource
import com.jdcr.jdcrmediaplayer.define.JdcrMediaSource

data class MediaConfigData(
    val defaultEmotion: MediaConfigItemData?,
    val muted: Boolean?
)

data class MediaConfigItemData(val name: String?, val url: String?)

fun MediaConfigData.toSource(): JdcrMediaSource {
    return JdcrMediaSource(
        uri = defaultEmotion?.url ?: "",
        title = defaultEmotion?.name,
        continuedSource = ContinuedMediaSource(
            isDefaultSource = true,
            block = false,
            mute = muted,
        )
    )
}

data class MediaItemData(
    val id: String?,
    val url: String?,
    val name: String?,
    val block: Boolean?,
    val mute: Boolean?,
    val loopDefault: Boolean?,
    val asyncWaitPlaybackEndCallBack: Boolean?
)

fun MediaItemData.toSource(): JdcrMediaSource {
    return JdcrMediaSource(
        id = id ?: "",
        uri = url ?: "",
        title = name,
        continuedSource = ContinuedMediaSource(
            isDefaultSource = false,
            block = block,
            mute = mute,
            loopLastDefault = loopDefault,
            playEndAutoRemove = true,
        )
    )
}

val step1ConfigDefault = MediaConfigData(
    MediaConfigItemData(
        "平静",
        "https://static.bcmcdn.com/wood2/bcmpet/resources/newmobile/%E5%B9%B3%E9%9D%99.mp4"
    ), true
)

val step2ShowWake = MediaItemData(
    "1780795835787",
    "https://static.bcmcdn.com/wood2/bcmpet/resources/mobile/%E5%94%A4%E9%86%92%E6%80%81.mp4",
    "唤醒态",
    true,
    false,
    false,
    null
)

val step3ConfigDefaultListen = MediaConfigData(
    MediaConfigItemData(
        "聆听态",
        "https://static.bcmcdn.com/wood2/bcmpet/resources/mobile/%E8%81%86%E5%90%AC%E6%80%81.mp4"
    ), false
)

val step4ShowWake = MediaItemData(
    "1780795838232",
    "https://static.bcmcdn.com/wood2/bcmpet/resources/mobile/%E8%81%86%E5%90%AC%E6%80%81.mp4",
    "聆听态",
    false,
    false,
    true,
    null
)

val step5ShowListenToThink = MediaItemData(
    "1780795847738",
    "https://static.bcmcdn.com/wood2/bcmpet/resources/mobile/show_%E8%81%86%E5%90%AC%E6%80%81to%E6%80%9D%E8%80%83%E6%80%81.mp4",
    "聆听态To思考态",
    true,
    false,
    false,
    null
)

val step6ConfigDefaultThink = MediaConfigData(
    MediaConfigItemData(
        "思考态",
        "https://static.bcmcdn.com/wood2/bcmpet/resources/mobile/%E5%B0%8F%E4%BD%93%E7%A7%AF%E6%80%9D%E8%80%83%E6%80%81.mp4"
    ), false
)

val step7ShowThink = MediaItemData(
    "1780795847767",
    "https://static.bcmcdn.com/wood2/bcmpet/resources/mobile/%E5%B0%8F%E4%BD%93%E7%A7%AF%E6%80%9D%E8%80%83%E6%80%81.mp4",
    "思考态",
    false,
    false,
    true,
    null
)

val step8ShowAnswer = MediaItemData(
    "1780795854966",
    "https://static.bcmcdn.com/wood2/bcmpet/resources/newmobile/%E5%9B%9E%E7%AD%94%E6%80%81_%E6%90%9E%E6%80%AA_%E5%BC%80%E5%A7%8B.mp4",
    "回答态_搞怪_开始",
    true,
    false,
    false,
    null
)

val step9ConfigDefaultAnswer = MediaConfigData(
    MediaConfigItemData(
        "回答态_搞怪_循环",
        "https://static.bcmcdn.com/wood2/bcmpet/resources/newmobile/%E5%9B%9E%E7%AD%94%E6%80%81_%E6%90%9E%E6%80%AA_%E5%BE%AA%E7%8E%AF.mp4"
    ), false
)

val step10ShowAnswerNo = MediaItemData(
    "1780795854994",
    "https://static.bcmcdn.com/wood2/bcmpet/resources/newmobile/%E5%9B%9E%E7%AD%94%E6%80%81_%E6%90%9E%E6%80%AA_%E5%BE%AA%E7%8E%AF.mp4",
    "回答态_搞怪_循环",
    false,
    false,
    true,
    null
)

val step10ShowAnswerYes = MediaItemData(
    "1780802175609",
    "https://static.bcmcdn.com/wood2/bcmpet/resources/mobile/%E5%9B%9E%E7%AD%94%E6%80%81_%E8%87%AA%E4%BF%A1%E8%AE%A4%E7%9C%9F_%E5%BE%AA%E7%8E%AF.mp4",
    "回答态_自信认真_循环",
    false,
    false,
    true,
    null
)

val step11ShowAnswerNoEnd = MediaItemData(
    "1780795862107",
    "https://static.bcmcdn.com/wood2/bcmpet/resources/newmobile/%E5%9B%9E%E7%AD%94%E6%80%81_%E6%90%9E%E6%80%AA_%E7%BB%93%E6%9D%9F.mp4",
    "回答态_搞怪_结束",
    true,
    false,
    false,
    null
)

val step11ShowAnswerYesEnd = MediaItemData(
    "1780802183882",
    "https://static.bcmcdn.com/wood2/bcmpet/resources/mobile/%E5%9B%9E%E7%AD%94%E6%80%81_%E8%87%AA%E4%BF%A1%E8%AE%A4%E7%9C%9F_%E7%BB%93%E6%9D%9F.mp4",
    "回答态_自信认真_结束",
    true,
    false,
    false,
    null
)

val step12ConfigDefault = MediaConfigData(
    MediaConfigItemData(
        "平静",
        "https://static.bcmcdn.com/wood2/bcmpet/resources/newmobile/%E5%B9%B3%E9%9D%99.mp4"
    ), true
)

val answerAudio =
    "https://dev-cdn-common.codemao.cn/test/963/ttx/aigc/5-10/2026/06/S_VOwqoIaI1_92457_1780802449796.mp3"

fun getTestList(): MutableList<JdcrMediaSource> {
    return mutableListOf(
//        step1ConfigDefault.toSource(),
        step2ShowWake.toSource(),
        step3ConfigDefaultListen.toSource(),
        step4ShowWake.toSource(),
        step5ShowListenToThink.toSource(),
        step6ConfigDefaultThink.toSource(),
        step7ShowThink.toSource(),
        step8ShowAnswer.toSource(),
        step9ConfigDefaultAnswer.toSource(),
        step10ShowAnswerYes.toSource(),
        JdcrMediaSource(uri = answerAudio),
        step11ShowAnswerYesEnd.toSource(),
        step12ConfigDefault.toSource()
    )
}