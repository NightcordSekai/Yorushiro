package moe.cuteyuki.kanadebot.mainetwork.packet

import com.alibaba.fastjson2.JSON
import com.alibaba.fastjson2.JSONObject
import com.alibaba.fastjson2.annotation.JSONField
import moe.cuteyuki.kanadebot.mainetwork.IPacket
import moe.cuteyuki.kanadebot.managers.ConfigManager
import moe.cuteyuki.kanadebot.utils.HttpClient
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.HashMap

/**
 * 二维码认证请求包 (from reverseMai/config.py qr_api)
 *
 * 修复内容（来自 reverseMai/config.py qr_api）：
 * - 使用配置的 keychipId 而不是随机生成
 * - 时区使用 Asia/Tokyo
 * - 时间戳格式 yyMMddHHmmss (14位)
 * - QR Token 截取最后 64 位
 * - 添加缺失的请求头: Contention: Keep-Alive, Host: ai.sys-all.cn
 */
class UserTokenAndIDPacket
@JvmOverloads constructor(
    qrCodeToken: String,
    chimeSalt: String = "XcW5FW4cPArBXEk4vzKz3CIrMuA5EVVW"
) : IPacket {

    @JSONField(name = "chipID")
    val chipId: String = ConfigManager.getConfig().keychipId

    @JSONField(name = "openGameID")
    val openGameId: String = "MAID"

    @JSONField(name = "key")
    val key: String

    @JSONField(name = "qrCode")
    val qrCode: String

    @JSONField(name = "timestamp")
    val timestamp: String = LocalDateTime.now(ZoneId.of("Asia/Tokyo"))
        .format(DateTimeFormatter.ofPattern("yyMMddHHmmss"))

    @JSONField(serialize = false)
    private var userIdOrError: Long = -1


    init {
        // SHA256(chipId + timestamp + chimeSalt)
        val rawKey = chipId + timestamp + chimeSalt
        key = sha256(rawKey).uppercase()

        // QR Token 截取最后 64 位
        qrCode = if (qrCodeToken.length > 64) {
            qrCodeToken.substring(qrCodeToken.length - 64)
        } else {
            qrCodeToken
        }
    }

    override fun toJson(): String {
        val obj = JSONObject()
        obj["chipID"] = chipId
        obj["openGameID"] = openGameId
        obj["key"] = key
        obj["qrCode"] = qrCode
        obj["timestamp"] = timestamp
        return obj.toJSONString()
    }

    fun execute(): Pair<Long, String> {
        val headers: MutableMap<String?, String?> = HashMap()
        headers["Contention"] = "Keep-Alive"
        headers["Host"] = "ai.sys-allnet.cn"
        headers["User-Agent"] = "WC_AIME_LIB"
        headers["Content-Type"] = "application/json"

        val url = ConfigManager.getConfig().aimeUrl.ifEmpty { "http://ai.sys-allnet.cn/wc_aime/api/get_data" }
        val bodyBytes = toJson().toByteArray(StandardCharsets.UTF_8)

        val result = HttpClient.post(url, headers, bodyBytes, 15.0)

        val responseBody = String(result.body ?: ByteArray(0), StandardCharsets.UTF_8)
        val obj = JSON.parseObject(responseBody)

        val errorID = obj.getIntValue("errorID")
        val userID = obj.getLongValue("userID")
        val token = obj.getString("token")

        userIdOrError = if (errorID == 0) userID else errorID.toLong()

        println(responseBody)
        return userIdOrError to token
    }

    companion object {
        private fun sha256(input: String): String {
            val md = MessageDigest.getInstance("SHA-256")
            val hash = md.digest(input.toByteArray(StandardCharsets.UTF_8))
            val hexString = StringBuilder()
            for (b in hash) {
                val hex = Integer.toHexString(0xff and b.toInt())
                if (hex.length == 1) hexString.append('0')
                hexString.append(hex)
            }
            return hexString.toString()
        }
    }
}
