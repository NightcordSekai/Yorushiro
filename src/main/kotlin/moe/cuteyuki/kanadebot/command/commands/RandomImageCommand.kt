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

class RandomImageCommand: ICommand {
    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(8))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()

    override val data: CommandData
        get() = CommandData(
            name = "ranimg",
            description = "获取随机acg图片",
            usage = "ranimg",
            aliases = listOf("ranimg", "randomimage"),
        )

    override fun process(bot: Bot, event: MessageEvent, args: Array<String>) {
        if (event !is GroupMessageEvent) return

        GlobalScope.launch {
            val imageBytes = httpClient.send(
                HttpRequest.newBuilder()
                    .uri(URI.create("https://uapis.cn/api/v1/random/image?category=anime"))
                    .timeout(Duration.ofSeconds(30))
                    .build(),
                HttpResponse.BodyHandlers.ofByteArray()
            ).body()

            bot.replyGroupMsg(event, MsgUtils.builder().img(imageBytes).build())
        }
    }
}