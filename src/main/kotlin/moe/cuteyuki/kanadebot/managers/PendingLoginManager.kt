package moe.cuteyuki.kanadebot.managers

import com.mikuac.shiro.core.Bot
import com.mikuac.shiro.core.BotContainer
import java.util.Timer
import java.util.TimerTask
import java.util.concurrent.ConcurrentHashMap

/**
 * 二维码处理回调
 * @param bot Bot 实例
 * @param userId 发送二维码的用户
 * @param qrResult 二维码解码后的文本
 * @param context 注册时携带的上下文数据
 */
typealias QRCodeCallback = (bot: Bot, userId: Long, qrResult: String, context: Any?) -> Unit

/**
 * QR 码待处理请求管理器
 *
 * 任何命令都可以注册一个回调，当用户在私聊发送二维码时会自动调用对应的回调函数。
 * 回调可以携带任意上下文数据。
 *
 * 用法:
 * ```
 * // 在命令中注册
 * PendingLoginManager.register(userId, myContextObj, 2 * 60 * 1000L) { bot, userId, qrResult, context ->
 *     val ctx = context as MyContext
 *     // 处理二维码结果
 * }
 * ```
 */
object PendingLoginManager {

    private lateinit var botContainer: BotContainer

    fun init(bc: BotContainer) {
        botContainer = bc
    }

    private data class PendingEntry(
        val callback: QRCodeCallback,
        val context: Any? = null,
        val createdAt: Long = System.currentTimeMillis()
    )

    private val pendingMap = ConcurrentHashMap<Long, PendingEntry>()

    private val timer = Timer("PendingQRTimeout", true)

    /**
     * 注册一个待处理的二维码请求
     *
     * @param userId 用户 QQ 号
     * @param context 需要传递给回调的上下文数据（例如 groupId, messageId 等）
     * @param timeoutMs 超时时间（毫秒），默认 2 分钟
     * @param callback 收到二维码后的回调函数
     */
    fun register(userId: Long, context: Any? = null, timeoutMs: Long = 2 * 60 * 1000L, callback: QRCodeCallback) {
        val entry = PendingEntry(callback, context)
        pendingMap[userId] = entry

        // 超时自动移除
        timer.schedule(object : TimerTask() {
            override fun run() {
                val removed = pendingMap.remove(userId)
                if (removed != null) {
                    println("[PendingLoginManager] 用户 $userId 的二维码请求已超时 (${timeoutMs / 1000}秒)")
                }
            }
        }, timeoutMs)
    }

    /**
     * 检查用户是否有待处理的请求，如果有则执行回调并移除
     *
     * @return true 如果找到并执行了回调
     */
    fun consume(userId: Long, bot: Bot, qrResult: String): Boolean {
        val entry = pendingMap.remove(userId) ?: return false
        // 使用 BotContainer 获取活跃的 Bot 实例，避免 onPrivateMessage 返回后
        // WebSocket session 被回收导致回调中发消息时 session closed
        val liveBot = if (::botContainer.isInitialized) botContainer.robots.values.firstOrNull() ?: bot else bot
        try {
            entry.callback(liveBot, userId, qrResult, entry.context)
        } catch (e: Exception) {
            System.err.println("[PendingLoginManager] 执行回调时出错: ${e.message}")
            e.printStackTrace()
        }
        return true
    }

    /**
     * 检查用户是否有待处理的请求
     */
    fun hasPending(userId: Long): Boolean {
        return pendingMap.containsKey(userId)
    }

    /**
     * 获取当前活跃的 Bot 实例。
     * 用于长时间异步任务结束时，确保使用的是当前仍然活跃的 WebSocket 连接。
     * @return 活跃的 Bot 实例，如果未初始化或无活跃连接则返回 null
     */
    fun getActiveBot(): Bot? {
        if (!::botContainer.isInitialized) return null
        return botContainer.robots.values.firstOrNull()
    }
}