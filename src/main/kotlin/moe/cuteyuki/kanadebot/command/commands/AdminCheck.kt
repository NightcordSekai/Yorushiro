package moe.cuteyuki.kanadebot.command.commands

import com.mikuac.shiro.dto.event.message.GroupMessageEvent
import moe.cuteyuki.kanadebot.managers.ConfigManager

/**
 * 群消息发送者是否为群主或管理员（OneBot 的 sender.role 字段）。
 */
internal fun isGroupAdmin(event: GroupMessageEvent): Boolean {
    val role = event.sender?.role?.lowercase() ?: return false
    return role == "owner" || role == "admin"
}

/**
 * 是否为「机器人全局管理员」（在 `kanade.json#botAdmins` 中配置的 QQ）。
 */
internal fun isBotAdmin(event: GroupMessageEvent): Boolean {
    val uid = event.sender?.userId ?: return false
    return ConfigManager.getConfig().botAdmins.contains(uid)
}

/**
 * 群管理员或机器人管理员，任一即可。
 */
internal fun isPrivilegedUser(event: GroupMessageEvent): Boolean =
    isGroupAdmin(event) || isBotAdmin(event)
