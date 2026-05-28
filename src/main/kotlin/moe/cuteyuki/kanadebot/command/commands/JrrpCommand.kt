package moe.cuteyuki.kanadebot.command.commands

import com.mikuac.shiro.core.Bot
import com.mikuac.shiro.dto.event.message.GroupMessageEvent
import com.mikuac.shiro.dto.event.message.MessageEvent
import moe.cuteyuki.kanadebot.command.CommandData
import moe.cuteyuki.kanadebot.command.ICommand
import moe.cuteyuki.kanadebot.managers.JrrpManager
import moe.cuteyuki.kanadebot.utils.replyGroupMsg

/**
 * 今日人品 — 每个用户每天一次随机 0~100 的人品值，跨日刷新。
 */
class JrrpCommand : ICommand {
    override val data = CommandData(
        name = "jrrp",
        description = "查询今日人品（0~100，每日一次）",
        usage = "jrrp",
        aliases = listOf("今日人品", "rp")
    )

    override fun process(bot: Bot, event: MessageEvent, args: Array<String>) {
        if (event !is GroupMessageEvent) return

        val userId = event.sender.userId
        val value = JrrpManager.getToday(event.groupId, userId)
        bot.replyGroupMsg(event, "你今天的人品值是 $value ${flavor(value)}")
    }

    private fun flavor(v: Int): String = when {
        v >= 95 -> "🌟 欧皇附体！"
        v >= 80 -> "✨ 状态绝佳"
        v >= 60 -> "🙂 还不错"
        v >= 40 -> "😐 平平淡淡"
        v >= 20 -> "😶‍🌫️ 不太行"
        else    -> "💀 今天还是别出门了"
    }
}
