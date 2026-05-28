package moe.cuteyuki.kanadebot.command.commands

import com.mikuac.shiro.common.utils.MsgUtils
import com.mikuac.shiro.core.Bot
import com.mikuac.shiro.dto.event.message.GroupMessageEvent
import com.mikuac.shiro.dto.event.message.MessageEvent
import moe.cuteyuki.kanadebot.command.CommandData
import moe.cuteyuki.kanadebot.command.ICommand
import moe.cuteyuki.kanadebot.managers.JrrpManager
import moe.cuteyuki.kanadebot.utils.AvatarProvider
import moe.cuteyuki.kanadebot.utils.JrrpBoardRenderer

/**
 * 今日人品排行榜 — 渲染一张图片发回群里。
 */
class JrrpRankCommand : ICommand {
    override val data = CommandData(
        name = "jrrprank",
        description = "今日人品排行榜（图片）",
        usage = "jrrprank",
        aliases = listOf("人品排行榜", "人品榜", "rprank")
    )

    override fun process(bot: Bot, event: MessageEvent, args: Array<String>) {
        if (event !is GroupMessageEvent) return

        val board = JrrpManager.getTodayBoard(event.groupId)
        val rows = board.mapIndexed { index, item ->
            JrrpBoardRenderer.Row(
                rank = index + 1,
                userId = item.userId,
                displayName = resolveDisplayName(bot, event.groupId, item.userId),
                value = item.value,
                avatar = AvatarProvider.fetch(item.userId)
            )
        }

        val base64 = JrrpBoardRenderer.render(rows)
        val msg = MsgUtils.builder()
            .reply(event.messageId)
            .img("base64://$base64")
            .build()
        bot.sendGroupMsg(event.groupId, msg, false)
    }

    /**
     * 优先用群名片，其次昵称，最后回退 QQ 号。
     * 任何异常（成员退群、API 超时等）都回退到 QQ 号，不影响整体出图。
     */
    private fun resolveDisplayName(bot: Bot, groupId: Long, userId: Long): String {
        return try {
            val resp = bot.getGroupMemberInfo(groupId, userId, false)?.data
            val card = resp?.card?.takeIf { it.isNotBlank() }
            val nickname = resp?.nickname?.takeIf { it.isNotBlank() }
            card ?: nickname ?: userId.toString()
        } catch (_: Exception) {
            userId.toString()
        }
    }
}
