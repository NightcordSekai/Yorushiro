package moe.cuteyuki.kanadebot.mainetwork.packet

import com.alibaba.fastjson2.annotation.JSONField
import moe.cuteyuki.kanadebot.mainetwork.IPacket

data class UserMusicDataPacket(
    @JSONField(name = "userId")
    val userId : Long,
    @JSONField(name = "nextIndex")
    val nextIndex : Long = 0,
    @JSONField(name = "maxCount")
    val maxCount : Int = 50,
): IPacket