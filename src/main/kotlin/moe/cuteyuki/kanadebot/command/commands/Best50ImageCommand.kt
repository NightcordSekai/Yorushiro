package moe.cuteyuki.kanadebot.command.commands

import com.alibaba.fastjson2.JSON
import com.alibaba.fastjson2.JSONArray
import com.alibaba.fastjson2.JSONObject
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
import moe.cuteyuki.kanadebot.mainetwork.NetworkManager
import moe.cuteyuki.kanadebot.mainetwork.beans.MusicLevel
import moe.cuteyuki.kanadebot.mainetwork.beans.UserRatingData
import moe.cuteyuki.kanadebot.mainetwork.packet.GetUserRatingPacket
import moe.cuteyuki.kanadebot.mainetwork.packet.UserPreviewPacket
import moe.cuteyuki.kanadebot.mainetwork.packet.UserTokenAndIDPacket
import moe.cuteyuki.kanadebot.managers.ConfigManager
import moe.cuteyuki.kanadebot.managers.PendingLoginManager
import moe.cuteyuki.kanadebot.managers.ResourceManager
import moe.cuteyuki.kanadebot.utils.ImageBuilder
import moe.cuteyuki.kanadebot.utils.Logger
import moe.cuteyuki.kanadebot.utils.MusicDataProvider
import moe.cuteyuki.kanadebot.utils.RatingCalculator
import moe.cuteyuki.kanadebot.utils.replyGroupMsg
import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints
import java.io.File
import java.io.FileInputStream
import java.util.Base64
import javax.imageio.ImageIO

class Best50ImageCommand: ICommand {
    private val commandScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val data: CommandData
        get() = CommandData(
            name = "b50",
            description = "生成你的Best50图片",
            usage = "b50（u)",
            aliases = listOf("b50图", "b50生成", "best50")
        )

    override fun process(bot: Bot, event: MessageEvent, args: Array<String>) {
        if (event !is GroupMessageEvent) return
        val userId = event.sender.userId

        // 1) args 为空或不是 "u" → 优先尝试缓存
        if (args.isEmpty() || args[0] != "u") {
            val cacheFile = File(ResourceManager.dataCacheFolder, "${userId}_b50.json")
            if (cacheFile.exists()) {
                bot.replyGroupMsg(event, " ✅ 已有你的b50缓存，正在生成图片…")
                commandScope.launch {
                    try {
                        val cacheJson = JSON.parseObject(cacheFile.readText())
                        val outputFile = File(ResourceManager.dataCacheFolder, "${userId}_b50.png")
                        generateB50Image(cacheJson, outputFile)
                        bot.sendGroupMsg(event.groupId,
                            MsgUtils.builder().reply(event.messageId).img(FileInputStream(outputFile).readAllBytes()).build(),
                            false
                        )
                    } catch (e: Exception) {
                        Logger.log("从缓存生成b50图片失败: ${e.message}", Logger.LogType.ERROR)
                        bot.replyGroupMsg(event, " ❌ 生成失败: ${e.message}，请重新扫码")
                    }
                }
                return
            }
        }

        // 2) 强制刷新(u) 或无缓存 → 扫码更新
        PendingLoginManager.register(userId, B50ImageContext(event.groupId, event.messageId)) { b, uid, qrResult, context ->
            val ctx = context as B50ImageContext
            // 参照 Test.kt：简单直接回调，不经过 ICommand 空方法
            commandScope.launch {
                handleQrCallback(b, uid, ctx.groupId, ctx.messageId, qrResult)
            }
        }

        bot.replyGroupMsg(event, " 请私聊发送你的登陆二维码给我，你有2分钟时间 ⏰")
    }

    /**
     * 扫码回调 — 在 IO scope 上执行
     */
    private suspend fun handleQrCallback(bot: Bot, qqUserId: Long, groupId: Long, messageId: Int, qrToken: String) {
        try {
            // 解析 token
            val packetResult = UserTokenAndIDPacket(qrToken).execute()
            if (packetResult.first < 10000000) {
                bot.sendPrivateMsg(qqUserId, "❌ 无效的QrCode Token. 错误代码：${packetResult.first}", false)
                return
            }
            val targetUserId = packetResult.first
            val token = packetResult.second
            val cfg = ConfigManager.getConfig()

            // 获取用户信息
            val previewPacket = UserPreviewPacket(targetUserId, "", token, cfg.clientId)
            val previewResultStr = callApiSuspend("GetUserPreviewApi", previewPacket.toJson(), targetUserId)
            Logger.log(previewResultStr, Logger.LogType.INFO)
            val userPreviewData = JSON.parseObject(
                previewResultStr,
                moe.cuteyuki.kanadebot.mainetwork.beans.UserPreviewDataBean::class.java
            )

            // 获取 rating
            val ratingPacket = GetUserRatingPacket(targetUserId)
            val ratingResultStr = callApiSuspend("GetUserRatingApi", ratingPacket.toJson(), targetUserId)
            Logger.log(ratingResultStr, Logger.LogType.DEBUG)
            val ratingJson = JSON.parseObject(ratingResultStr)

            // 缓存
            val cacheJson = JSONObject().apply {
                put("userId", userPreviewData.userId)
                put("userName", userPreviewData.userName)
                put("iconId", userPreviewData.iconId)
                put("playerRating", userPreviewData.playerRating)
                put("userRating", ratingJson.getJSONObject("userRating"))
            }
            File(ResourceManager.dataCacheFolder, "${qqUserId}_b50.json")
                .writeText(cacheJson.toJSONString())

            // 生成图片
            val outputFile = File(ResourceManager.dataCacheFolder, "${qqUserId}_b50.png")
            generateB50Image(cacheJson, outputFile)

            bot.sendGroupMsg(groupId,
                MsgUtils.builder().reply(messageId).img(Base64.getEncoder().encodeToString(FileInputStream(outputFile).readAllBytes())).build(),
                false
            )
            bot.sendPrivateMsg(qqUserId, "✅ B50 图片生成完成！", false)

        } catch (e: Exception) {
            System.err.println("[Best50ImageCommand] Error: ${e.message}")
            e.printStackTrace()
            bot.sendPrivateMsg(qqUserId, "❌ 处理出错: ${e.message}", false)
        }
    }

    private suspend fun callApiSuspend(apiName: String, jsonBody: String, userId: Long): String {
        return NetworkManager.sendToTitleSuspend(jsonBody, apiName, userId)
    }

    companion object {
        private const val START_X_OFFSET = 65
        private const val START_Y_OFFSET = 350
        private const val CARD_WIDTH = 320
        private const val CARD_HEIGHT = 120
        private const val CARDS_PER_ROW = 5
        private const val B35_ROWS = 7
        private const val B15_START_Y_OFFSET = 1250
        private const val MAX_B35 = 35
        private const val MAX_B15 = 15
    }

    private fun generateB50Image(cacheJson: JSONObject, outputFile: File) {
        val userName = cacheJson.getString("userName") ?: "Unknown"
        val iconId = cacheJson.getString("iconId") ?: "0"
        val playerRating = cacheJson.getIntValue("playerRating") // API 返回的总 rating
        val userRating = cacheJson.getJSONObject("userRating")

        // 只取前 35 + 前 15
        val ratingList = parseRatingRecords(userRating.getJSONArray("ratingList")).take(MAX_B35)
        val newRatingList = parseRatingRecords(userRating.getJSONArray("newRatingList")).take(MAX_B15)
        // 如果 ratingList 不足 35，用 new 项补齐显示（Better 50 含义）
        val bestRecords = (ratingList + newRatingList).take(50)

        // 分别计算实际 B35 / B15 的 rating 总和
        val b35RaSum = ratingList.sumOf { computeRa(it) }
        val b15RaSum = newRatingList.sumOf { computeRa(it) }
        val totalRa = b35RaSum + b15RaSum

        // 读取素材
        val avatarImage = runCatching {
            val path = ResourceManager.iconImagePath(iconId)
            if (path != null) ImageIO.read(File(path)) else null
        }.getOrNull()

        val backgroundImage = ImageIO.read(
            javaClass.getResourceAsStream("/base.png")
        )
        val qrCodeImage = ImageIO.read(
            javaClass.getResourceAsStream("/qrcode.png")
        )
        val botName = "KanadeBot"

        val g2d = backgroundImage.createGraphics().apply {
            setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
            setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON)
            setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
            setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)
            setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
        }

        // 头像
        avatarImage?.let { g2d.drawImage(it, 115, 110, null) }

        // 用户名
        g2d.color = Color(61, 61, 61)
        g2d.font = Font("MiSans-Bold", Font.PLAIN, 48)
        g2d.drawString(userName, 260, 165)

        // Rating 信息（两行）
        g2d.font = Font("MiSans-Regular", Font.PLAIN, 24)
        g2d.drawString("B35:$b35RaSum + B15:$b15RaSum = $totalRa", 260, 200)
        g2d.drawString("Rating: $playerRating", 260, 225)

        // 底部信息
        g2d.drawImage(qrCodeImage, 1570, 1685, 128, 128, null)
        g2d.drawString("Generated by $botName. UI designed by chuxuehaocai.", 70, 1740)
        g2d.drawString("https://github.com/chuxuehaocai/KanadeBot      (or scan the qrcode at ->", 70, 1770)

        // 绘制卡片
        var cardIndex = 0
        for (record in bestRecords) {
            val x = START_X_OFFSET + (CARD_WIDTH * (cardIndex % CARDS_PER_ROW))
            val row = cardIndex / CARDS_PER_ROW
            val y = if (row < B35_ROWS) {
                START_Y_OFFSET + (CARD_HEIGHT * row)
            } else {
                B15_START_Y_OFFSET + (CARD_HEIGHT * (row - B35_ROWS))
            }
            ImageBuilder.drawRatingCard(g2d, record, x, y)
            cardIndex++
        }

        g2d.dispose()
        ImageIO.write(backgroundImage, "png", outputFile)
    }

    /** 辅助：计算单曲 Ra */
    private fun computeRa(data: UserRatingData): Int {
        val ds = MusicDataProvider.getDs(data.musicId, levelToIndex(data.level))
        return RatingCalculator.computeRa(ds, data.achievement / 10000.0)
    }

    private fun parseRatingRecords(arr: JSONArray?): List<UserRatingData> {
        if (arr == null) return emptyList()
        val result = mutableListOf<UserRatingData>()
        for (i in 0 until arr.size) {
            val obj = arr.getJSONObject(i)
            result.add(
                UserRatingData(
                    musicName = MusicDataProvider.getTitle(obj.getIntValue("musicId")),
                    level = MusicLevel.fromInt(obj.getIntValue("level")),
                    achievement = obj.getIntValue("achievement"),
                    musicId = obj.getIntValue("musicId")
                )
            )
        }
        return result
    }

    private fun levelToIndex(level: MusicLevel?): Int = when (level) {
        MusicLevel.Basic -> 0
        MusicLevel.Advanced -> 1
        MusicLevel.Expert -> 2
        MusicLevel.Master -> 3
        MusicLevel.ReMaster -> 4
        null -> 0
    }

    private data class B50ImageContext(
        val groupId: Long,
        val messageId: Int
    )
}