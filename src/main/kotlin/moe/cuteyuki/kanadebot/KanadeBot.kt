package moe.cuteyuki.kanadebot

import com.mikuac.shiro.annotation.GroupMessageHandler
import com.mikuac.shiro.annotation.PrivateMessageHandler
import com.mikuac.shiro.annotation.common.Shiro
import com.mikuac.shiro.core.Bot
import com.mikuac.shiro.core.BotContainer
import com.mikuac.shiro.core.BotPlugin
import com.mikuac.shiro.dto.event.message.GroupMessageEvent
import com.mikuac.shiro.dto.event.message.PrivateMessageEvent
import com.mikuac.shiro.enums.MsgTypeEnum
import jakarta.annotation.PostConstruct
import moe.cuteyuki.kanadebot.command.commands.Best50ImageCommand
import moe.cuteyuki.kanadebot.command.commands.EvaluateRatingCommand
import moe.cuteyuki.kanadebot.command.commands.GithubCommand
import moe.cuteyuki.kanadebot.command.commands.HelpCommand
import moe.cuteyuki.kanadebot.command.commands.HistoryCommand
import moe.cuteyuki.kanadebot.command.commands.JrrpCommand
import moe.cuteyuki.kanadebot.command.commands.JrrpRankCommand
import moe.cuteyuki.kanadebot.command.commands.NewsCommand
import moe.cuteyuki.kanadebot.command.commands.RandomImageCommand
import moe.cuteyuki.kanadebot.command.commands.SubGithubRepoCommand
import moe.cuteyuki.kanadebot.command.commands.Test
import moe.cuteyuki.kanadebot.command.commands.UnsubGithubRepoCommand
import moe.cuteyuki.kanadebot.command.commands.UpdateMusicDataCommand
import moe.cuteyuki.kanadebot.command.commands.WhoamiCommand
import moe.cuteyuki.kanadebot.managers.CommandManager
import moe.cuteyuki.kanadebot.managers.ConfigManager
import moe.cuteyuki.kanadebot.managers.GitHubManager
import moe.cuteyuki.kanadebot.managers.JrrpManager
import moe.cuteyuki.kanadebot.managers.PendingLoginManager
import moe.cuteyuki.kanadebot.managers.ResourceManager
import moe.cuteyuki.kanadebot.utils.QRCodeUtil
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
@Shiro
class KanadeBot @Autowired constructor(
    private val botContainer: BotContainer,
) : BotPlugin() {

    /** GitHub 仓库 URL 正则：匹配 https://github.com/owner/repo（可带 .git 后缀、可带子路径忽略） */
    private val githubUrlRegex = Regex(
        """https?://github\.com/([A-Za-z0-9._-]+/[A-Za-z0-9._-]+)(?:\.git)?(?:/[^\s]*)?(?:#.*)?"""
    )

    @PostConstruct
    fun initialize() {
        println("KanadeBot initializing...")
        ConfigManager.initialize()
        ResourceManager.initialize()
        JrrpManager.initialize()
        GitHubManager.initialize(botContainer)
        PendingLoginManager.init(botContainer)

        // 新命令
        CommandManager.register(Test())
        CommandManager.register(JrrpCommand())
        CommandManager.register(JrrpRankCommand())
        CommandManager.register(HelpCommand())
        CommandManager.register(SubGithubRepoCommand())
        CommandManager.register(UnsubGithubRepoCommand())
        CommandManager.register(GithubCommand())
        CommandManager.register(RandomImageCommand())
        CommandManager.register(NewsCommand())
        CommandManager.register(HistoryCommand())

        // 复活的老命令
        CommandManager.register(WhoamiCommand())
        CommandManager.register(EvaluateRatingCommand())
        CommandManager.register(UpdateMusicDataCommand())
        CommandManager.register(Best50ImageCommand())

        println("KanadeBot initialized. ${CommandManager.getCommands().size} command(s) registered.")
    }

    @GroupMessageHandler
    override fun onGroupMessage(bot: Bot, event: GroupMessageEvent): Int {
        // 先让命令管理器处理（带前缀的命令）
        val isCommand = CommandManager.process(bot, event)

        // 如果消息已被命令处理，跳过自动检测（避免如 .github <url> 重复触发）
        if (!isCommand) {
            detectGitHubRepo(bot, event)
        }

        return MESSAGE_IGNORE
    }

    @PrivateMessageHandler
    override fun onPrivateMessage(bot: Bot, event: PrivateMessageEvent): Int {
        val arrayMsg = event.arrayMsg
        if (arrayMsg.isNullOrEmpty()) return MESSAGE_IGNORE

        for (msg in arrayMsg) {
            if (msg.type == MsgTypeEnum.image) {
                val imageUrl = msg.getStringData("url") ?: continue
                val qrResult = QRCodeUtil.decodeFromUrl(imageUrl)
                if (qrResult == null) continue

                // 检查是否有待处理的 QR 回调
                val consumed = PendingLoginManager.consume(event.userId, bot, qrResult)

                // 如果没有待处理的回调，且二维码不是 SGWCMAID 开头，提示无效
                if (!consumed && !qrResult.startsWith("SGWCMAID")) {
                    bot.sendPrivateMsg(event.userId, "无效的登陆二维码", false)
                }

                return MESSAGE_IGNORE
            }
        }

        return MESSAGE_IGNORE
    }

    /**
     * 自动检测群消息中是否包含 GitHub 仓库 URL，如有则自动解析并发送信息卡片。
     * 每条消息最多检测到 1 个仓库（第一个匹配的 URL）。
     */
    private fun detectGitHubRepo(bot: Bot, event: GroupMessageEvent) {
        val rawMessage = event.message.trim()
        val match = githubUrlRegex.find(rawMessage) ?: return
        val rawRepo = match.groupValues[1] // owner/repo
        val repo = GitHubManager.normalizeRepo(rawRepo)
        if (!GitHubManager.isValidRepo(repo)) return

        // 调用 GithubCommand 的统一处理方法（含内容审核）
        GithubCommand.handle(bot, event, repo)
    }
}
