package moe.cuteyuki.kanadebot.mainetwork.beans

import com.alibaba.fastjson2.annotation.JSONField

data class UserMissionData(
    @JSONField(name = "userWeeklyData")
    val userWeeklyData: UserWeeklyData? = null,

    @JSONField(name = "userMissionDataList")
    val userMissionDataList: List<UserMissionItem>? = null
)

data class UserWeeklyData(
    @JSONField(name = "lastLoginWeek")
    val lastLoginWeek: String = "N/A"
)

data class UserMissionItem(
    @JSONField(name = "type")
    val type: Int = 0,

    @JSONField(name = "difficulty")
    val difficulty: Int = 0,

    @JSONField(name = "clearFlag")
    val clearFlag: Boolean = false
)
