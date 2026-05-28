package moe.cuteyuki.kanadebot.mainetwork.packet

import com.alibaba.fastjson2.annotation.JSONField
import moe.cuteyuki.kanadebot.mainetwork.IPacket

/**
 * 用户登录请求包
 */
data class UserLoginPacket(
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

    @JSONField(name = "dateTime")
    val dateTime: Long,

    @JSONField(name = "loginDateTime")
    val loginDateTime: Long,

    @JSONField(name = "isContinue")
    val isContinue: Boolean = false,

    @JSONField(name = "genericFlag")
    val genericFlag: Int = 0,

    @JSONField(name = "token")
    val token: String
) : IPacket
