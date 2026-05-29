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
    val type: Int = 1,

    @JSONField(name = "delayLog")
    val delayLog: DelayLog = DelayLog()
) : IPacket

data class DelayLog(
    @JSONField(name = "dlRequests")
    val dlRequests: Int = 43,

    @JSONField(name = "dlSize")
    val dlSize: Int = 756259,

    @JSONField(name = "dlRetry")
    val dlRetry: Int = 0,

    @JSONField(name = "loginMsec")
    val loginMsec: Int = 3974,

    @JSONField(name = "saveMsec")
    val saveMsec: Int = 1319,

    @JSONField(name = "reductionMusic")
    val reductionMusic: Int = 0,

    @JSONField(name = "reductionItem")
    val reductionItem: Int = 0,

    @JSONField(name = "request")
    val request: List<DelayRequest> = listOf(
        DelayRequest(1, 2109, 82, 0),
        DelayRequest(1, 18507, 78, 0),
        DelayRequest(12, 329111, 1066, 0),
        DelayRequest(5, 347, 456, 0),
        DelayRequest(1, 16812, 107, 0),
        DelayRequest(1, 14971, 79, 0),
        DelayRequest(1, 999, 99, 0),
        DelayRequest(1, 298, 76, 0),
        DelayRequest(1, 2840, 77, 0),
        DelayRequest(1, 58, 99, 0),
        DelayRequest(1, 799, 79, 0),
        DelayRequest(1, 423, 78, 0),
        DelayRequest(1, 5250, 79, 0),
        DelayRequest(1, 106737, 118, 0),
        DelayRequest(0, 0, 0, 0),
        DelayRequest(1, 2310, 150, 0),
        DelayRequest(2, 411, 155, 0),
        DelayRequest(3, 273, 244, 0),
        DelayRequest(3, 247960, 315, 0),
        DelayRequest(1, 1079, 78, 0),
        DelayRequest(1, 49, 88, 0),
        DelayRequest(1, 307, 80, 0),
        DelayRequest(1, 4188, 215, 0),
        DelayRequest(1, 421, 76, 0),
        DelayRequest(0, 0, 0, 0),
        DelayRequest(1, 65, 1130, 0)
    )
)

data class DelayRequest(
    @JSONField(name = "count")
    val count: Int,

    @JSONField(name = "size")
    val size: Int,

    @JSONField(name = "msec")
    val msec: Int,

    @JSONField(name = "retry")
    val retry: Int
)
