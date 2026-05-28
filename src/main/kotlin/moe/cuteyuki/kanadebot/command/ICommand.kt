package moe.cuteyuki.kanadebot.command

import com.mikuac.shiro.core.Bot
import com.mikuac.shiro.dto.event.message.MessageEvent

interface ICommand {
    val data: CommandData
    fun process(bot: Bot, event: MessageEvent, args: Array<String>)

    fun handleQr(bot: Bot, qqUserId: Long, groupId: Long, messageId: Int, qrToken: String) {}
}
