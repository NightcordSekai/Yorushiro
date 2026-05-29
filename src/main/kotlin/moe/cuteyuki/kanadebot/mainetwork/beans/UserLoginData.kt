package moe.cuteyuki.kanadebot.mainetwork.beans

import com.alibaba.fastjson2.annotation.JSONField

data class UserLoginData(
    @JSONField(name = "returnCode")
    val returnCode: Int,

    @JSONField(name = "lastLoginDate")
    val lastLoginDate: String,

    @JSONField(name = "loginCount")
    val loginCount: Int,

    @JSONField(name = "consecutiveLoginCount")
    val consecutiveLoginCount: Int,

    @JSONField(name = "loginId")
    val loginId: Long,

    @JSONField(name = "token")
    val token: String
)