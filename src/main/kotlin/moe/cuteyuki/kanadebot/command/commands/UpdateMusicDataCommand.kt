package moe.cuteyuki.kanadebot.command.commands

import com.mikuac.shiro.common.utils.MsgUtils
import com.mikuac.shiro.core.Bot
import com.mikuac.shiro.dto.event.message.GroupMessageEvent
import com.mikuac.shiro.dto.event.message.MessageEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import moe.cuteyuki.kanadebot.command.CommandData
import moe.cuteyuki.kanadebot.command.ICommand
import moe.cuteyuki.kanadebot.utils.MusicDataProvider
import moe.cuteyuki.kanadebot.utils.replyGroupMsg

/**
 * 手动更新歌曲定数数据命令
 *
 * 用法: .update_music_data
 * 从水鱼查分器重新拉取歌曲定数数据并更新本地缓存
 */
class UpdateMusicDataCommand : ICommand {
    override val data: CommandData
        get() = CommandData(
            name = "update_music_data",
            description = "手动更新歌曲定数数据缓存",
            usage = ".update_music_data",
            aliases = listOf("更新定数", "refresh_music_data")
        )

    private val commandScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun process(bot: Bot, event: MessageEvent, args: Array<String>) {
        if (event !is GroupMessageEvent) return

        val userId = event.sender.userId

        // 回复用户提示正在更新
        val replyMsg = MsgUtils.builder()
            .reply(event.messageId)
            .at(userId)
            .text(" 正在更新歌曲定数数据，请稍候...")
            .build()
        bot.sendGroupMsg(event.groupId, replyMsg, false)

        commandScope.launch {
            try {
                MusicDataProvider.refresh()
                val successMsg = MsgUtils.builder()
                    .reply(event.messageId)
                    .at(userId)
                    .text(" ✅ 歌曲定数数据更新完成！")
                    .build()
                bot.sendGroupMsg(event.groupId, successMsg, false)
            } catch (e: Exception) {
                System.err.println("[UpdateMusicDataCommand] 更新失败: ${e.message}")
                val failMsg = MsgUtils.builder()
                    .reply(event.messageId)
                    .at(userId)
                    .text(" ❌ 更新失败: ${e.message}")
                    .build()
                bot.sendGroupMsg(event.groupId, failMsg, false)
            }
        }
    }
}
