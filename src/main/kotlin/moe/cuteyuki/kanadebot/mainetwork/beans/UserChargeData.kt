package moe.cuteyuki.kanadebot.mainetwork.beans

import com.alibaba.fastjson2.annotation.JSONField

data class UserChargeData(
    @JSONField(name = "length")
    val length: Int = 0,

    @JSONField(name = "userChargeList")
    val userChargeList: List<UserChargeItem>? = null
)

data class UserChargeItem(
    @JSONField(name = "chargeId")
    val chargeId: Int = 0,

    @JSONField(name = "stock")
    val stock: Int = 0,

    @JSONField(name = "validDate")
    val validDate: String = ""
)
