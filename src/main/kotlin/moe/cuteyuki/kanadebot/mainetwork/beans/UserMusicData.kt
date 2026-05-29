package moe.cuteyuki.kanadebot.mainetwork.beans

import com.alibaba.fastjson2.annotation.JSONField

data class UserMusicData(
    @JSONField(name = "userId")
    val userId: Long,

    @JSONField(name = "length")
    val length: Int,

    @JSONField(name = "nextIndex")
    val nextIndex: Int,

    @JSONField(name = "userMusicList")
    val userMusicList: List<UserMusic>
)

data class UserMusic(
    @JSONField(name = "userMusicDetailList")
    val userMusicDetailList: List<UserMusicDetail>,

    @JSONField(name = "length")
    val length: Int
)

data class UserMusicDetail(
    @JSONField(name = "musicId")
    val musicId: Int,

    @JSONField(name = "level")
    val level: Int,

    @JSONField(name = "playCount")
    val playCount: Int,

    @JSONField(name = "achievement")
    val achievement: Int,

    @JSONField(name = "comboStatus")
    val comboStatus: Int,

    @JSONField(name = "syncStatus")
    val syncStatus: Int,

    @JSONField(name = "deluxscoreMax")
    val deluxscoreMax: Int,

    @JSONField(name = "scoreRank")
    val scoreRank: Int,

    @JSONField(name = "extNum1")
    val extNum1: Int,

    @JSONField(name = "extNum2")
    val extNum2: Int
)