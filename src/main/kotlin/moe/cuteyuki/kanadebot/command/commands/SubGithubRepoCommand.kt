package moe.cuteyuki.kanadebot.command.commands

import com.mikuac.shiro.core.Bot
import com.mikuac.shiro.dto.event.message.GroupMessageEvent
import com.mikuac.shiro.dto.event.message.MessageEvent
import moe.cuteyuki.kanadebot.command.CommandData
import moe.cuteyuki.kanadebot.command.ICommand
import moe.cuteyuki.kanadebot.managers.ConfigManager
import moe.cuteyuki.kanadebot.managers.GitHubManager
import moe.cuteyuki.kanadebot.utils.DeepSeekClient
import moe.cuteyuki.kanadebot.utils.GitHubApi
import moe.cuteyuki.kanadebot.utils.replyGroupMsg

/**
 * 群管理员命令：订阅一个 GitHub 仓库的 commit 推送。
 *
 * 用法：
 *   .subgithubrepo owner/repo
 *   .subgithubrepo https://github.com/owner/repo
 */
class SubGithubRepoCommand : ICommand {
    override val data = CommandData(
        name = "subgithubrepo",
        description = "订阅 GitHub 仓库的 commit 推送。可附完整 URL 或 owner/repo。",
        usage = "subgithubrepo <owner/repo>",
        aliases = listOf("订阅github", "subgh"),
        restricted = true,
    )

    override fun process(bot: Bot, event: MessageEvent, args: Array<String>) {
        if (event !is GroupMessageEvent) return
        if (!isPrivilegedUser(event)) {
            bot.replyGroupMsg(event, "只有群管理员或机器人管理员可以使用该命令")
            return
        }
        if (args.isEmpty()) {
            bot.replyGroupMsg(event, "用法：.subgithubrepo <owner/repo>")
            return
        }
        val raw = args[0]
        val repo = GitHubManager.normalizeRepo(raw)
        if (!GitHubManager.isValidRepo(repo)) {
            bot.replyGroupMsg(event, "仓库格式不正确：$raw")
            return
        }

        if(ConfigManager.getConfig().deepSeekApiKey.isEmpty()){
            bot.replyGroupMsg(event, "出于安全性原因，在未设置Deepseek api key之前此指令无法使用。")
            return
        }

        // 内容合规检查
        val repoJson = GitHubApi.repo(repo)
        if (repoJson != null) {
            val fields = DeepSeekClient.extractRepoFields(repoJson)
            val checkResult = DeepSeekClient.checkRepo(
                fields.fullName, fields.description, fields.language, fields.topics, fields.homepage
            )
            if (checkResult != null && !checkResult.compliant) {
                bot.replyGroupMsg(event, "⚠️ 仓库 $repo 未通过内容合规检查：${checkResult.reason}")
                return
            }else if(checkResult == null){
                bot.replyGroupMsg(event, "⚠️ 内容合规检查失败。 checkResult == null。")
                return
            }
        }

        val ok = try {
            GitHubManager.subscribe(repo, bot.selfId, event.groupId)
        } catch (e: Exception) {
            bot.replyGroupMsg(event, "订阅失败：${e.message}")
            return
        }

        if (ok) {
            bot.replyGroupMsg(event, "✅ 已订阅 $repo 的 commit 推送，约每分钟检查一次。")
        } else {
            bot.replyGroupMsg(event, "ℹ️ 本群已订阅过 $repo")
        }
    }
}
