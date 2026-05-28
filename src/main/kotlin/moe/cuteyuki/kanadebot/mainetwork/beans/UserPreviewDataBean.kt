package moe.cuteyuki.kanadebot.mainetwork.beans

import com.alibaba.fastjson2.annotation.JSONField

data class UserPreviewDataBean(
    @JSONField(name = "userId")
    val userId: Long,

    @JSONField(name = "userName")
    val userName: String,

    @JSONField(name = "isLogin")
    val isLogin: Int,


    @JSONField(name = "lastGameId")
    val lastGameId: String? = null,

    @JSONField(name = "lastRomVersion")
    val lastRomVersion: String,

    @JSONField(name = "lastDataVersion")
    val lastDataVersion: String,

    @JSONField(name = "lastLoginDate")
    val lastLoginDate: String,

    @JSONField(name = "lastPlayDate")
    val lastPlayDate: String,

    @JSONField(name = "playerRating")
    val playerRating: Int,

    @JSONField(name = "nameplateId")
    val nameplateId: Int,

    @JSONField(name = "iconId")
    val iconId: Int,

    @JSONField(name = "trophyId")
    val trophyId: Int,

    @JSONField(name = "isNetMember")
    val isNetMember: Int,

    @JSONField(name = "isInherit")
    val isInherit: Boolean,

    @JSONField(name = "totalAwake")
    val totalAwake: Int,

    @JSONField(name = "dispRate")
    val dispRate: Int,

    @JSONField(name = "dailyBonusDate")
    val dailyBonusDate: String,

    @JSONField(name = "headPhoneVolume")
    val headPhoneVolume: String? = null,

    @JSONField(name = "banState")
    val banState: Int,

    @JSONField(name = "errorId")
    val errorId: Int
)
