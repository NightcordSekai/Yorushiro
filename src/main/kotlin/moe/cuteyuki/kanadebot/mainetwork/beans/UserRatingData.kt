package moe.cuteyuki.kanadebot.mainetwork.beans

import com.alibaba.fastjson2.annotation.JSONField
import java.util.*

data class UserRatingData (
    @JSONField(name = "musicName")
    var musicName: String? = null,

    @JSONField(name = "level")
    var level: MusicLevel? = null,

    @JSONField(name = "romVersion")
    var romVersion: Int = 0,

    @JSONField(name = "achievement")
    var achievement: Int = 0,

    @JSONField(name = "musicId")
    var musicId: Int = 0
){
    fun formatRatingSimple(raw: Long): String {
        val v = raw / 10000.0
        return String.format(Locale.US, "%.4f%%", v)
    }
}

enum class MusicLevel {
    Basic,
    Advanced,
    Expert,
    Master,
    ReMaster;

    companion object {
        fun fromInt(level: Int): MusicLevel {
            when (level) {
                0 -> return MusicLevel.Basic
                1 -> return MusicLevel.Advanced
                2 -> return MusicLevel.Expert
                3 -> return MusicLevel.Master
                4 -> return MusicLevel.ReMaster
                else -> throw IllegalArgumentException("Invalid level: " + level)
            }
        }
    }
}
