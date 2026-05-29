package moe.cuteyuki.kanadebot.utils

import com.mikuac.shiro.common.utils.MsgUtils
import com.mikuac.shiro.core.Bot
import com.mikuac.shiro.dto.event.message.GroupMessageEvent

fun Bot.replyGroupMsg(event: GroupMessageEvent, msg: String){
    sendGroupMsg(event.groupId, MsgUtils.builder()
        .reply(event.messageId)
        .at(event.sender.userId)
        .text(" $msg")
        .build(),
        false
    )
}

fun Bot.replyGroupMsg(groupId: Long, msgId: Int, targetUID: Long, msg: String){
    sendGroupMsg(groupId, MsgUtils.builder()
        .reply(msgId)
        .at(targetUID)
        .text(" $msg")
        .build(),
        false
    )
}