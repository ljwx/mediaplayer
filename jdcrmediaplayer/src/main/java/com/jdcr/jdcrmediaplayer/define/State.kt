package com.jdcr.jdcrmediaplayer.define

sealed class JdcrPlayerState(val desc: String, val source: JdcrPlayerSource?) {
    data class IDLE(val item: JdcrPlayerSource?) : JdcrPlayerState("空闲", item)
    data class PREPARING(val item: JdcrPlayerSource?) : JdcrPlayerState("资源准备中", item)
    data class READY(val item: JdcrPlayerSource?) : JdcrPlayerState("准备就绪", item)
    data class PLAYING(val item: JdcrPlayerSource?) : JdcrPlayerState("播放中", item)
    sealed class PAUSE(open val item: JdcrPlayerSource?, val reason: String) :
        JdcrPlayerState("已暂停", item) {
        data class Normal(override val item: JdcrPlayerSource?) : PAUSE(item, "用户暂停")

        data class LOSE_FOCUS_TEMP(override val item: JdcrPlayerSource?) :
            PAUSE(item, "暂时失去音频焦点")

        data class LOSE_FOCUS_LONG(override val item: JdcrPlayerSource?) :
            PAUSE(item, "长时失去音频焦点")

        data class AUDIO_BECOMING_NOISY(override val item: JdcrPlayerSource?) :
            PAUSE(item, "拔掉耳机,即将外放")
    }

    data class ENDED(val item: JdcrPlayerSource?) : JdcrPlayerState("播放结束", item)
    data class ERROR(val item: JdcrPlayerSource?, val playError: JdcrPlayerError) :
        JdcrPlayerState("播放错误", item)
}

sealed class JdcrPlayerRepeatMode(val desc: String) {
    object RepeatNo : JdcrPlayerRepeatMode("不重复")
    object RepeatOne : JdcrPlayerRepeatMode("单曲循环")
    object RepeatAll : JdcrPlayerRepeatMode("全部循环")
}

sealed class JdcrPlayerError(val code: Int, val errorMessage: String) :
    Exception(errorMessage) {

    class NetworkError(code: Int, message: String) : JdcrPlayerError(code, message)
    class FormatError(code: Int, message: String) : JdcrPlayerError(code, message)
    class SourceError(code: Int, message: String) : JdcrPlayerError(code, message)
    class UnknowError(code: Int, message: String) : JdcrPlayerError(code, message)

    override fun toString(): String {
        return "PlayError(code=$code, message='$message')"
    }

}