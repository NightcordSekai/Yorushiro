package moe.cuteyuki.kanadebot.managers

import com.mikuac.shiro.core.Bot
import com.mikuac.shiro.dto.event.message.GroupMessageEvent
import com.mikuac.shiro.dto.event.message.MessageEvent
import moe.cuteyuki.kanadebot.command.ICommand
import moe.cuteyuki.kanadebot.utils.replyGroupMsg

object CommandManager {
    /** 文本命令前缀，如 ".help" 中的 "." */
    private const val TEXT_PREFIX = "."

    /** 已注册的命令表：name -> command */
    private val commands: MutableMap<String, ICommand> = mutableMapOf()

    /** 别名映射：alias -> commandName */
    private val aliases: MutableMap<String, String> = mutableMapOf()


    /**
     * 注册一个命令到管理器
     */
    fun register(command: ICommand) {
        val key = command.data.name.lowercase()
        commands[key] = command

        // 注册别名
        command.data.aliases?.forEach { alias ->
            aliases[alias.lowercase()] = key
        }
    }

    /**
     * 根据命令名获取命令实例
     */
    fun getCommand(name: String): ICommand? {
        val key = name.lowercase()
        return commands[key] ?: aliases[key]?.let { commands[it] }
    }

    /**
     * 获取所有已注册的命令
     */
    fun getCommands(): Map<String, ICommand> {
        return commands.toMap()
    }

    /**
     * 检查是否存在指定名称的命令
     */
    fun hasCommand(name: String): Boolean {
        val key = name.lowercase()
        return commands.containsKey(key) || aliases.containsKey(key)
    }

    /**
     * 从消息中解析并执行命令。
     *
     * 触发方式：
     *  - 文本前缀：以 `.` 开头，例如 `.jrrp`
     *  - At 机器人：消息以 `[CQ:at,qq=<selfId>]` 开头，例如 `@KanadeBot jrrp`
     *
     * @return true 如果消息被识别为命令并尝试执行，false 否则
     */
    fun process(bot: Bot, event: MessageEvent): Boolean {
        val rawMessage = event.message.trim()

        // 1. .前缀
        if (rawMessage.startsWith(TEXT_PREFIX)) {
            val withoutPrefix = rawMessage.removePrefix(TEXT_PREFIX).trim()
            return executeCommand(bot, event, withoutPrefix)
        }

        // 2. @机器人前缀
        val atPrefix = matchAtBotPrefix(rawMessage, bot.selfId)
        if (atPrefix != null) {
            val withoutPrefix = rawMessage.removePrefix(atPrefix).trim()
            if (withoutPrefix.isEmpty()) return false
            return executeCommand(bot, event, withoutPrefix)
        }

        return false
    }

    /**
     * 匹配开头的 `[CQ:at,qq=<selfId>...]` 前缀（OneBot 11 CQ 码）。
     * 命中返回原始的前缀字符串（含中括号），未命中返回 null。
     *
     * 兼容字段顺序与额外 name= 等参数。
     */
    private fun matchAtBotPrefix(raw: String, selfId: Long): String? {
        if (!raw.startsWith("[CQ:at")) return null
        val end = raw.indexOf(']')
        if (end < 0) return null
        val head = raw.substring(0, end + 1) // [CQ:at,qq=...,name=...]
        val body = head.removePrefix("[CQ:at").removeSuffix("]")
        // 期望 body 形如 ",qq=12345,name=foo"，逗号分隔的 key=value
        val params = body.split(",")
            .mapNotNull { kv ->
                val parts = kv.split("=", limit = 2)
                if (parts.size == 2) parts[0].trim() to parts[1].trim() else null
            }
            .toMap()
        val qq = params["qq"] ?: return null
        return if (qq == selfId.toString()) head else null
    }

    /**
     * 解析并执行去掉前缀后的命令字符串
     */
    private fun executeCommand(bot: Bot, event: MessageEvent, message: String): Boolean {
        val parts = message.split("\\s+".toRegex()).filter { it.isNotEmpty() }
        if (parts.isEmpty()) return false

        val commandName = parts[0].lowercase()
        val args = if (parts.size > 1) parts.drop(1).toTypedArray() else emptyArray()

        // 查找命令（含别名）
        val command = commands[commandName] ?: aliases[commandName]?.let { commands[it] } ?: return false

        return try {
            command.process(bot, event, args)
            true
        } catch (e: Exception) {
            System.err.println("[CommandManager] Error executing command '$commandName': ${e.message}")
            e.printStackTrace()
            // 向用户反馈错误
            if (event is GroupMessageEvent) {
                bot.replyGroupMsg(event, "执行命令时出错: ${e.message}")
            }
            true
        }
    }
}
