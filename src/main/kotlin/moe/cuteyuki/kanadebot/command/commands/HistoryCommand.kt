package moe.cuteyuki.kanadebot.command.commands

import com.alibaba.fastjson2.JSON
import com.alibaba.fastjson2.JSONArray
import com.alibaba.fastjson2.JSONObject
import com.mikuac.shiro.common.utils.MsgUtils
import com.mikuac.shiro.core.Bot
import com.mikuac.shiro.dto.event.message.GroupMessageEvent
import com.mikuac.shiro.dto.event.message.MessageEvent
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import moe.cuteyuki.kanadebot.command.CommandData
import moe.cuteyuki.kanadebot.command.ICommand
import moe.cuteyuki.kanadebot.utils.HistoryRenderer
import moe.cuteyuki.kanadebot.utils.replyGroupMsg
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * 历史上的今天（程序员专属）：
 *
 *   .history
 *
 * 从 https://uapis.cn/api/v1/history/programmer/today 获取历史上的今天编程相关事件，
 * 并渲染为 Material 3 深色风格图片发送。
 */
class HistoryCommand : ICommand {
    override val data = CommandData(
        name = "history",
        description = "查看历史上的今天（程序员版）。",
        usage = "history",
        aliases = listOf("历史上的今天"),
    )

    private val http: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build()

    override fun process(bot: Bot, event: MessageEvent, args: Array<String>) {
        if (event !is GroupMessageEvent) return

        bot.replyGroupMsg(event, "正在查询历史上的今天...")
        GlobalScope.launch {
            runCatching { handle(bot, event) }.onFailure {
                System.err.println("[HistoryCommand] 查询失败: ${it.message}")
                it.printStackTrace()
                bot.replyGroupMsg(event, "查询历史上的今天失败：${it.message}")
            }
        }
    }

    private fun handle(bot: Bot, event: GroupMessageEvent) {
        val json = fetchHistory()
        if (json == null) {
            bot.replyGroupMsg(event, "查询历史上的今天失败，请稍后重试。")
            return
        }

        val date = json.getString("date") ?: "未知日期"
        val events: JSONArray = json.getJSONArray("events") ?: JSONArray()

        if (events.isEmpty()) {
            bot.replyGroupMsg(event, "历史上的今天 ($date) 暂无记录。")
            return
        }

        // 按重要性降序排列，最多展示 5 条
        val sorted = (0 until events.size)
            .mapNotNull { events.getJSONObject(it) }
            .sortedByDescending { it.getIntValue("importance") }
            .take(5)
            .map { event ->
                HistoryRenderer.Event(
                    year = event.getIntValue("year"),
                    title = event.getString("title") ?: "未知",
                    description = event.getString("description"),
                    category = event.getString("category"),
                    importance = event.getIntValue("importance"),
                )
            }

        val base64 = HistoryRenderer.render(date, sorted)
        val msg = MsgUtils.builder()
            .reply(event.messageId)
            .img("base64://$base64")
            .build()
        bot.sendGroupMsg(event.groupId, msg, false)
    }

    private fun fetchHistory(): JSONObject? {
        return runCatching {
            val request = HttpRequest.newBuilder()
                .uri(URI.create("https://uapis.cn/api/v1/history/programmer/today"))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build()

            val response = http.send(request, HttpResponse.BodyHandlers.ofString())

            if (response.statusCode() !in 200..299) {
                System.err.println("[HistoryCommand] API 返回状态码: ${response.statusCode()}")
                return null
            }

            val body = response.body()
            if (body.isNullOrBlank()) {
                System.err.println("[HistoryCommand] API 返回空内容。")
                return null
            }

            JSON.parseObject(body)
        }.onFailure {
            System.err.println("[HistoryCommand] HTTP 请求失败: ${it.message}")
        }.getOrNull()
    }
}