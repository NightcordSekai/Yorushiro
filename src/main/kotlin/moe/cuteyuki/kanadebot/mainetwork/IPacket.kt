package moe.cuteyuki.kanadebot.mainetwork

import com.alibaba.fastjson2.JSON

interface IPacket {
    fun toJson(): String = JSON.toJSONString(this)
}
