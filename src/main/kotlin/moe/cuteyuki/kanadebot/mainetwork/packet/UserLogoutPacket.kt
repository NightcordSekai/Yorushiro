package moe.cuteyuki.kanadebot.mainetwork.packet

import com.alibaba.fastjson2.annotation.JSONField
import moe.cuteyuki.kanadebot.mainetwork.IPacket

/**
 * 用户登出请求包
 */
data class UserLogoutPacket(
    @JSONField(name = "userId")
    val userId: Long,

    @JSONField(name = "accessCode")
    val accessCode: String = "",

    @JSONField(name = "regionId")
    val regionId: Int,

    @JSONField(name = "placeId")
    val placeId: Int,

    @JSONField(name = "clientId")
    val clientId: String,

    /**
     * 与游戏 UserLogoutRequestVO.loginDateTime 一致——必须是 unix epoch 秒 (long)。
     * 取自 UserLoginApi 响应的 loginDateTime 字段，而不是 lastLoginDate (string)。
     */
    @JSONField(name = "loginDateTime")
    val loginDateTime: Long,

    @JSONField(name = "type")
    val type: Int = 1
) : IPacket

