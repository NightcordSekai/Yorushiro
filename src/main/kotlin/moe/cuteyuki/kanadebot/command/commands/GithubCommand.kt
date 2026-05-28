package moe.cuteyuki.kanadebot.command.commands

import com.mikuac.shiro.common.utils.MsgUtils
import com.mikuac.shiro.core.Bot
import com.mikuac.shiro.dto.event.message.GroupMessageEvent
import com.mikuac.shiro.dto.event.message.MessageEvent
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import moe.cuteyuki.kanadebot.command.CommandData
import moe.cuteyuki.kanadebot.command.ICommand
import moe.cuteyuki.kanadebot.managers.ConfigManager
import moe.cuteyuki.kanadebot.managers.GitHubManager
import moe.cuteyuki.kanadebot.utils.DeepSeekClient
import moe.cuteyuki.kanadebot.utils.GitHubApi
import moe.cuteyuki.kanadebot.utils.GitHubRepoInfoRenderer
import moe.cuteyuki.kanadebot.utils.replyGroupMsg

/**
 * 查询 GitHub 仓库信息：
 *
 *   .github owner/repo
 *
 * 也接受完整 URL：`.github https://github.com/owner/repo`
 *
 * 输出仓库概览卡（名字 / 简介 / 主语言 / star / fork / 最近一次 commit）。
 */
class GithubCommand : ICommand {
    override val data = CommandData(
        name = "github",
        description = "查询 GitHub 仓库概览（简介、主语言、stars/forks、最近一次 commit）。",
        usage = "github <owner/repo>",
        aliases = listOf("gh", "仓库"),
    )

    override fun process(bot: Bot, event: MessageEvent, args: Array<String>) {
        if (event !is GroupMessageEvent) return

        if (args.isEmpty()) {
            bot.replyGroupMsg(event, "用法：.github <owner/repo>")
            return
        }

        val raw = args[0]
        val repo = GitHubManager.normalizeRepo(raw)
        if (!GitHubManager.isValidRepo(repo)) {
            bot.replyGroupMsg(event, "仓库格式不正确：$raw")
            return
        }

        Companion.handle(bot, event, repo)
    }

    companion object {
        /**
         * 统一的 GitHub 仓库处理入口。
         * - 显式命令 (.github owner/repo) 通过 ICommand.process() 调用
         * - 自动检测（群聊中出现的 GitHub URL）通过 KanadeBot 直接调用
         * 包含 DeepSeek api key 检查、异步处理、内容合规审核。
         */
        fun handle(bot: Bot, event: GroupMessageEvent, repo: String) {
            if (ConfigManager.getConfig().deepSeekApiKey.isEmpty()) {
                bot.replyGroupMsg(event, "出于安全性原因，在未设置Deepseek api key之前此指令无法使用。")
                return
            }
            bot.replyGroupMsg(event, "请稍等...")
            GlobalScope.launch {
                runCatching { handleInfo(bot, event, repo) }.onFailure {
                    System.err.println("[GithubCommand] 处理 $repo 出错: ${it.message}")
                    it.printStackTrace()
                    bot.replyGroupMsg(event, "拉取 $repo 失败：${it.message}")
                }
            }
        }

        private fun handleInfo(bot: Bot, event: GroupMessageEvent, repo: String) {
            val repoJson = GitHubApi.repo(repo)
            if (repoJson == null) {
                bot.replyGroupMsg(event, "找不到仓库 $repo（可能不存在或被限流）")
                return
            }

            // 提取 repo 字段，提前拉取最新 commit
            val fields = DeepSeekClient.extractRepoFields(repoJson)
            val latest = runCatching {
                val arr = GitHubApi.commits(repo, perPage = 1) ?: return@runCatching null
                if (arr.isEmpty()) return@runCatching null
                val obj = arr.getJSONObject(0) ?: return@runCatching null
                val sha = obj.getString("sha") ?: return@runCatching null
                val commit = obj.getJSONObject("commit") ?: return@runCatching null
                val message = commit.getString("message").orEmpty()
                val title = message.lineSequence().firstOrNull()?.trim().orEmpty()
                val author = commit.getJSONObject("author")
                GitHubRepoInfoRenderer.LatestCommit(
                    shortSha = sha.take(7),
                    title = title.ifBlank { "(no message)" },
                    authorName = author?.getString("name")
                        ?: obj.getJSONObject("author")?.getString("login")
                        ?: "unknown",
                    timestamp = author?.getString("date").orEmpty(),
                )
            }.getOrNull()

            // 内容合规检查 —— 覆盖所有渲染到图片的字符串字段
            val checkResult = DeepSeekClient.checkRepo(
                fullName = fields.fullName,
                description = fields.description,
                language = fields.language,
                topics = fields.topics,
                homepage = fields.homepage,
                defaultBranch = fields.defaultBranch,
                ownerLogin = fields.ownerLogin,
                createdAt = fields.createdAt,
                updatedAt = fields.updatedAt,
                commitTitle = latest?.title,
                commitAuthor = latest?.authorName,
                commitTimestamp = latest?.timestamp,
            )
            if (checkResult != null && !checkResult.compliant) {
                bot.replyGroupMsg(event, "⚠️ 仓库 $repo 未通过内容合规检查：${checkResult.reason}")
                return
            } else if (checkResult == null) {
                bot.replyGroupMsg(event, "⚠️ 内容合规检查失败。 checkResult == null。")
                return
            }

            // 审核通过，继续构建渲染数据
            val owner = repoJson.getJSONObject("owner")
            val ownerLogin = fields.ownerLogin.orEmpty()
            val avatarUrl = owner?.getString("avatar_url")
            val avatar = avatarUrl?.let { GitHubApi.getImage(it) }

            val info = GitHubRepoInfoRenderer.RepoInfo(
                fullName = repoJson.getString("full_name") ?: repo,
                description = fields.description,
                language = fields.language,
                stars = repoJson.getIntValue("stargazers_count"),
                forks = repoJson.getIntValue("forks_count"),
                openIssues = repoJson.getIntValue("open_issues_count"),
                defaultBranch = fields.defaultBranch,
                homepage = fields.homepage,
                ownerLogin = ownerLogin,
                ownerAvatar = avatar,
                isPrivate = repoJson.getBooleanValue("private"),
                isFork = repoJson.getBooleanValue("fork"),
                isArchived = repoJson.getBooleanValue("archived"),
                updatedAt = fields.updatedAt,
                latestCommit = latest,
            )

            val base64 = GitHubRepoInfoRenderer.render(info)
            val msg = MsgUtils.builder()
                .reply(event.messageId)
                .img("base64://$base64")
                .build()
            bot.sendGroupMsg(event.groupId, msg, false)
        }
    }
}