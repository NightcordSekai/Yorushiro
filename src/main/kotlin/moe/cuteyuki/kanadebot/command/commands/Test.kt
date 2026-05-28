package moe.cuteyuki.kanadebot.command.commands

import com.mikuac.shiro.core.Bot
import com.mikuac.shiro.dto.event.message.GroupMessageEvent
import com.mikuac.shiro.dto.event.message.MessageEvent
import moe.cuteyuki.kanadebot.command.CommandData
import moe.cuteyuki.kanadebot.command.ICommand
import moe.cuteyuki.kanadebot.utils.replyGroupMsg

class Test: ICommand {
    override val data: CommandData
        get() = CommandData(
            name = "test",
            description = "A test command",
            usage = "test",
            aliases = listOf("t", "ping")
        )

    override fun process(bot: Bot, event: MessageEvent, args: Array<String>) {
        if (event is GroupMessageEvent)
            if (event.sender.userId == 3849859967L)
                bot.replyGroupMsg(event, "ciallo!")
    }
}
