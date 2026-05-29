package moe.cuteyuki.kanadebot.command.commands

import com.alibaba.fastjson2.JSON
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
import moe.cuteyuki.kanadebot.mainetwork.packet.UserRatingPacket
import moe.cuteyuki.kanadebot.mainetwork.packet.UserTokenAndIDPacket
import moe.cuteyuki.kanadebot.managers.PendingLoginManager
import moe.cuteyuki.kanadebot.utils.DeepSeekConnector
import moe.cuteyuki.kanadebot.utils.Logger
import moe.cuteyuki.kanadebot.utils.MusicDataProvider

class EvaluateRatingCommand : ICommand {
    override val data: CommandData
        get() = CommandData(
            name = "bb50",
            description = "锐评/分析你的B50数据（需要水鱼查分器账号）",
            usage = "b50 <水鱼用户名> 或 b50锐评 <水鱼用户名>",
            aliases = listOf("b50锐评", "b50不锐评", "看看实力")
        )

    private val commandScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun process(bot: Bot, event: MessageEvent, args: Array<String>) {
        if (event !is GroupMessageEvent) return

        val userId = event.sender.userId
        val rawMessage = event.message.trim()

//        if (args.isEmpty()) {
//            bot.replyGroupMsg(event, "❌ 用法：.b50 <水鱼用户名> 或 .b50锐评 <水鱼用户名>")
//            return
//        }

        // 判断是否为锐评模式
        val sharp = rawMessage.contains("看看实力")

        // 注册二维码回调，携带用户名和锐评模式
        PendingLoginManager.register(userId, EvaluateContext(event.groupId, event.messageId, sharp)) { b, uid, qrResult, context ->
            val ctx = context as EvaluateContext
            handleQr(b, uid, ctx.groupId, ctx.messageId, qrResult, ctx.sharp)
        }

        // 回复用户提示
        val replyMsg = MsgUtils.builder()
            .reply(event.messageId)
            .at(userId)
            .text(" 请私聊发送你的登陆二维码给我，你有2分钟时间 ⏰")
            .build()

        bot.sendGroupMsg(event.groupId, replyMsg, false)
    }

    /**
     * 二维码回调处理
     */
    private fun handleQr(bot: Bot, qqUserId: Long, groupId: Long, messageId: Int, qrToken: String, sharp: Boolean) {
        commandScope.launch {
            try {
                val packetResult = UserTokenAndIDPacket(qrToken).execute()

                if (packetResult.first < 10000000) {
                    bot.sendPrivateMsg(qqUserId, "❌ 无效的QrCode Token. 错误代码：${packetResult.first}", false)
                    return@launch
                }

                val targetUserId = packetResult.first

                completeB50Review(bot, qqUserId, groupId, messageId, targetUserId, sharp)

            } catch (e: Exception) {
                System.err.println("[EvaluateRatingCommand] Error: ${e.message}")
                e.printStackTrace()
                bot.sendPrivateMsg(qqUserId, "❌ 处理出错: ${e.message}", false)
            }
        }
    }

    /**
     * 完整的 B50 锐评流程：QR认证 → 获取Rating → 调DeepSeek
     */
    private suspend fun completeB50Review(
        bot: Bot, qqUserId: Long, groupId: Long, messageId: Int,
        targetUserId: Long, sharp: Boolean
    ) {
        try {
            // ========== 2. 获取 Rating 数据 ==========
            val ratingPacket = UserRatingPacket(targetUserId)
            val ratingResultStr = callApiSuspend("GetUserRatingApi", ratingPacket.toJson(), targetUserId)
            Logger.log(ratingResultStr, Logger.LogType.DEBUG)

            val ratingJson = JSON.parseObject(ratingResultStr)
            val simplifiedData = simplifyRatingData(ratingJson)

            // ========== 3. 调用 DeepSeek API ==========
            val systemPrompt = if (sharp) {
                "你是一个专业的舞萌DX B50 锐评专家。你非常毒舌，喜欢用幽默讽刺的方式评价玩家的B50数据。你会根据玩家的rating、歌曲难度分布、成绩等数据进行犀利但有趣的点评。请用中文回复，语气要幽默毒舌，但不要恶意攻击。回复控制在200字以内。(rating为0是API限制 不要告诉用户，也不要拿这个攻击。）"
            } else {
                "你是一个专业的舞萌DX B50 数据分析师。你还是个可可爱爱的猫娘。请客观分析玩家的B50数据，给出专业的建议。分析内容包括：rating水平、歌曲难度分布、成绩质量等。请用中文回复，语气友好专业。回复控制在200字以内。(rating为0是API限制 不要告诉用户）"
            }
            val userPrompt = if (sharp) "请锐评以下B50数据：\n$simplifiedData" else "请分析以下B50数据：\n$simplifiedData"
            val content = DeepSeekConnector.ask(systemPrompt, userPrompt, temperature = 0.7, maxTokens = 5000)
            Logger.log(content, Logger.LogType.DEBUG)

            bot.sendGroupMsg(groupId,
                MsgUtils.builder()
                    .reply(messageId)
                    .at(qqUserId)
                    .text(" $content")
                    .build(), false)

            bot.sendPrivateMsg(qqUserId, "✅ B50 分析完成！", false)

        } catch (e: Exception) {
            System.err.println("[EvaluateRatingCommand] completeB50Review 错误: ${e.message}")
            e.printStackTrace()
            bot.sendPrivateMsg(qqUserId, "❌ 处理出错: ${e.message}", false)
        }
    }

    /**
     * 挂起版本的 callApi，在 IO 调度器上执行
     */
    private suspend fun callApiSuspend(apiName: String, jsonBody: String, userId: Long): String {
        return NetworkManager.sendToTitleSuspend(jsonBody, apiName, userId)
    }

    companion object {
        private val LEVEL_LABELS = mapOf(
            0 to "Basic",
            1 to "Advanced",
            2 to "Expert",
            3 to "Master",
            4 to "Re:Master"
        )
    }

    /**
     * 简化 Rating 数据，只保留 DeepSeek 需要的关键信息
     *
     * GetUserRatingApi 返回结构：
     * {
     *   "userId": 123,
     *   "userRating": {
     *     "rating": 0,
     *     "ratingList": [{"musicId": 11657, "level": 2, "achievement": 1005767}, ...],
     *     "newRatingList": [...],
     *     "nextRatingList": [...],
     *     "nextNewRatingList": [...],
     *     "udemae": {...}
     *   }
     * }
     */
    private fun simplifyRatingData(ratingJson: JSONObject): String {
        val simplified = JSONObject()

        // rating 和 additionalRating 在 userRating 对象里
        val userRatingObj = ratingJson.getJSONObject("userRating") ?: return simplified.toJSONString()
        simplified["rating"] = userRatingObj.getIntValue("rating")
        simplified["additional_rating"] = userRatingObj.getIntValue("additionalRating")

        // 合并所有 rating 列表
        val allRecords = mutableListOf<JSONObject>()

        fun addRecords(key: String) {
            val arr = userRatingObj.getJSONArray(key) ?: return
            for (i in 0 until arr.size) {
                allRecords.add(arr.getJSONObject(i))
            }
        }

        addRecords("ratingList")
        addRecords("newRatingList")
        addRecords("nextRatingList")
        addRecords("nextNewRatingList")

        // 按 achievement 降序排列取前 50（作为近似 ra 排序）
        val sortedRecords = allRecords.sortedByDescending { it.getIntValue("achievement") }.take(50)

        val simplifiedRecords = sortedRecords.map { record ->
            val musicId = record.getIntValue("musicId")
            val levelIndex = record.getIntValue("level")
            val achievement = record.getIntValue("achievement")
            val ds = MusicDataProvider.getDs(musicId, levelIndex)
            val title = MusicDataProvider.getTitle(musicId)
            val levelLabel = LEVEL_LABELS[levelIndex] ?: "Unknown"

            val simplifiedRecord = JSONObject()
            simplifiedRecord["musicId"] = musicId
            simplifiedRecord["title"] = title
            simplifiedRecord["level_label"] = levelLabel
            simplifiedRecord["achievements"] = achievement
            simplifiedRecord["ds"] = ds
            simplifiedRecord
        }

        simplified["records"] = simplifiedRecords

        // 也带上 udemae 段位信息
        val udemae = userRatingObj.getJSONObject("udemae")
        if (udemae != null) {
            simplified["udemae"] = udemae
        }

        return simplified.toJSONString()
    }

    /**
     * 二维码回调上下文数据
     */
    private data class EvaluateContext(
        val groupId: Long,
        val messageId: Int,
        val sharp: Boolean
    )
}
