package moe.cuteyuki.kanadebot.command.commands

import com.mikuac.shiro.common.utils.MsgUtils
import com.mikuac.shiro.core.Bot
import com.mikuac.shiro.dto.event.message.GroupMessageEvent
import com.mikuac.shiro.dto.event.message.MessageEvent
import moe.cuteyuki.kanadebot.command.CommandData
import moe.cuteyuki.kanadebot.command.ICommand
import moe.cuteyuki.kanadebot.managers.CommandManager
import moe.cuteyuki.kanadebot.utils.HelpImageRenderer

/**
 * 帮助命令：渲染所有已注册命令的卡片图。
 *
 * 缓存策略：以「当前已注册命令的 fingerprint」为 key 的内存缓存。
 * 命令列表不变 → 直接复用上次渲染的 base64 PNG，避免每次重绘。
 */
class HelpCommand : ICommand {
    override val data = CommandData(
        name = "help",
        description = "显示所有可用命令",
        usage = "help",
        aliases = listOf("h", "帮助", "菜单")
    )

    @Volatile
    private var cachedFingerprint: String? = null

    @Volatile
    private var cachedImage: String? = null

    override fun process(bot: Bot, event: MessageEvent, args: Array<String>) {
        if (event !is GroupMessageEvent) return

        val base64 = renderCached()
        val msg = MsgUtils.builder()
            .reply(event.messageId)
            .img("base64://$base64")
            .build()
        bot.sendGroupMsg(event.groupId, msg, false)
    }

    private fun renderCached(): String {
        val entries = buildEntries()
        val fingerprint = entries.joinToString("|") { e ->
            "${e.name}#${e.aliases.joinToString(",")}#${e.usage}#${e.description}#${e.restricted}"
        }

        cachedImage?.let {
            if (cachedFingerprint == fingerprint) return it
        }

        val rendered = HelpImageRenderer.render(entries)
        cachedFingerprint = fingerprint
        cachedImage = rendered
        return rendered
    }

    private fun buildEntries(): List<HelpImageRenderer.Entry> {
        val all = CommandManager.getCommands().values
            .distinctBy { it.data.name }
            .sortedBy { it.data.name }
        return all.map { cmd ->
            val d = cmd.data
            HelpImageRenderer.Entry(
                name = d.name,
                aliases = d.aliases.orEmpty(),
                usage = d.usage,
                description = d.description,
                restricted = d.restricted,
            )
        }
    }
}
