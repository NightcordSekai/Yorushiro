package moe.cuteyuki.kanadebot.mainetwork.beans

import com.alibaba.fastjson2.annotation.JSONField

data class UserGameData(
    @JSONField(name = "userData")
    val userData: UserData? = null
)

data class UserData(
    @JSONField(name = "userName")
    val userName: String = "",

    @JSONField(name = "playerRating")
    val playerRating: Int = 0,

    @JSONField(name = "point")
    val point: Long = 0,

    @JSONField(name = "playCount")
    val playCount: Long = 0
)

data class KaleidxScopeData(
    @JSONField(name = "userKaleidxScopeList")
    val userKaleidxScopeList: List<KaleidxScopeItem>? = null
)

data class KaleidxScopeItem(
    @JSONField(name = "gateId")
    val gateId: Int = 0,

    @JSONField(name = "isClear")
    val isClear: Boolean = false,

    @JSONField(name = "isKeyFound")
    val isKeyFound: Boolean = false,

    @JSONField(name = "isGateFound")
    val isGateFound: Boolean = false,

    @JSONField(name = "playCount")
    val playCount: Int = 0
)
