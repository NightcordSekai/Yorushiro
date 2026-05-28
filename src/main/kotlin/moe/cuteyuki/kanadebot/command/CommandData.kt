package moe.cuteyuki.kanadebot.command

data class CommandData(
    val name: String,
    val description: String? = null,
    val usage: String? = null,
    /** 命令别名 */
    val aliases: List<String>? = null,
    /**
     * 是否是「特权命令」：仅群管理员 / 机器人管理员可用。
     * 仅作元数据，权限实际由命令实现自行检查。`HelpCommand` 会基于此渲染特殊标记。
     */
    val restricted: Boolean = false,
)
