package moe.cuteyuki.kanadebot.mainetwork.packet

import com.alibaba.fastjson2.annotation.JSONField
import moe.cuteyuki.kanadebot.mainetwork.IPacket

data class UserPreviewPacket(
    @JSONField(name = "userId")
    val uid: Long,

    @JSONField(name = "segaIdAuthKey")
    val segaIdAuthKey: String = "",

    @JSONField(name = "token")
    val token: String,

    @JSONField(name = "clientId")
    val clientId: String
) : IPacket
