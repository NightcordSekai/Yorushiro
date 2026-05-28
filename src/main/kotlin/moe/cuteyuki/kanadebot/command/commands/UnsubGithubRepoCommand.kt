package moe.cuteyuki.kanadebot.command.commands

import com.mikuac.shiro.core.Bot
import com.mikuac.shiro.dto.event.message.GroupMessageEvent
import com.mikuac.shiro.dto.event.message.MessageEvent
import moe.cuteyuki.kanadebot.command.CommandData
import moe.cuteyuki.kanadebot.command.ICommand
import moe.cuteyuki.kanadebot.managers.GitHubManager
import moe.cuteyuki.kanadebot.utils.replyGroupMsg

/**
 * 群管理员命令：取消订阅一个 GitHub 仓库的 commit 推送。
 * 不带参数时列出本群已订阅的仓库。
 */
class UnsubGithubRepoCommand : ICommand {
    override val data = CommandData(
        name = "unsubgithubrepo",
        description = "取消订阅 GitHub 仓库。无参数时列出本群已订阅仓库。",
        usage = "unsubgithubrepo <owner/repo>",
        aliases = listOf("取消订阅github", "unsubgh"),
        restricted = true,
    )

    override fun process(bot: Bot, event: MessageEvent, args: Array<String>) {
        if (event !is GroupMessageEvent) return
        if (!isPrivilegedUser(event)) {
            bot.replyGroupMsg(event, "只有群管理员或机器人管理员可以使用该命令")
            return
        }
        if (args.isEmpty()) {
            val list = GitHubManager.listForGroup(bot.selfId, event.groupId)
            if (list.isEmpty()) {
                bot.replyGroupMsg(event, "本群没有已订阅的仓库。用法：.unsubgithubrepo <owner/repo>")
            } else {
                bot.replyGroupMsg(event, "本群已订阅：\n" + list.joinToString("\n") { "· $it" })
            }
            return
        }
        val repo = GitHubManager.normalizeRepo(args[0])
        val ok = GitHubManager.unsubscribe(repo, bot.selfId, event.groupId)
        if (ok) {
            bot.replyGroupMsg(event, "✅ 已取消订阅 $repo")
        } else {
            bot.replyGroupMsg(event, "本群未订阅 $repo")
        }
    }
}
