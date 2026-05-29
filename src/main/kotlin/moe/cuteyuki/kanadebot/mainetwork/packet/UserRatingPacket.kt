package moe.cuteyuki.kanadebot.mainetwork.packet

import com.alibaba.fastjson2.annotation.JSONField
import moe.cuteyuki.kanadebot.mainetwork.IPacket

data class UserRatingPacket(
    @JSONField(name = "userId")
    val uid: Long
) : IPacket
