package moe.cuteyuki.kanadebot.command.commands

import com.mikuac.shiro.common.utils.MsgUtils
import com.mikuac.shiro.core.Bot
import com.mikuac.shiro.dto.event.message.GroupMessageEvent
import com.mikuac.shiro.dto.event.message.MessageEvent
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import moe.cuteyuki.kanadebot.command.CommandData
import moe.cuteyuki.kanadebot.command.ICommand
import moe.cuteyuki.kanadebot.utils.replyGroupMsg
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.Base64

/**
 * 每日新闻图片查询：
 *
 *   .news
 *
 * 从 https://uapis.cn/api/v1/daily/news-image 获取当日新闻图片并发送到群聊。
 * 接口返回 image/jpeg 二进制数据，超时时间 10 秒。
 */
class NewsCommand : ICommand {
    override val data = CommandData(
        name = "news",
        description = "获取每日新闻图片。",
        usage = "news",
        aliases = listOf("新闻", "每日新闻"),
    )

    private val http: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build()

    override fun process(bot: Bot, event: MessageEvent, args: Array<String>) {
        if (event !is GroupMessageEvent) return

        bot.replyGroupMsg(event, "正在获取每日新闻...")
        GlobalScope.launch {
            runCatching { handle(bot, event) }.onFailure {
                System.err.println("[NewsCommand] 获取新闻失败: ${it.message}")
                it.printStackTrace()
                bot.replyGroupMsg(event, "获取每日新闻失败：${it.message}")
            }
        }
    }

    private fun handle(bot: Bot, event: GroupMessageEvent) {
        val imageBytes = fetchNewsImage()
        if (imageBytes == null) {
            bot.replyGroupMsg(event, "获取每日新闻失败，请稍后重试。")
            return
        }

        val base64 = Base64.getEncoder().encodeToString(imageBytes)
        val msg = MsgUtils.builder()
            .reply(event.messageId)
            .img("base64://$base64")
            .build()
        bot.sendGroupMsg(event.groupId, msg, false)
    }

    private fun fetchNewsImage(): ByteArray? {
        return runCatching {
            val request = HttpRequest.newBuilder()
                .uri(URI.create("https://uapis.cn/api/v1/daily/news-image"))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build()

            val response = http.send(request, HttpResponse.BodyHandlers.ofByteArray())

            if (response.statusCode() !in 200..299) {
                System.err.println("[NewsCommand] API 返回状态码: ${response.statusCode()}")
                return null
            }

            val body = response.body()
            if (body.isEmpty()) {
                System.err.println("[NewsCommand] API 返回空内容。")
                return null
            }

            body
        }.onFailure {
            System.err.println("[NewsCommand] HTTP 请求失败: ${it.message}")
        }.getOrNull()
    }
}