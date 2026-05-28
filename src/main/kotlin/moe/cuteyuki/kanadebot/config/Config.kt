package moe.cuteyuki.kanadebot.config

data class Config(
    val deepSeekApiKey: String = "",
    /** GitHub Personal Access Token，可空。提升公共仓库 API 速率限制。 */
    val githubToken: String = "",
    /**
     * 机器人全局管理员的 QQ 号列表。
     * 这些用户在任意群都可以执行需要管理员权限的命令（即使 ta 不是该群的群主或群管理员）。
     */
    val botAdmins: List<Long> = emptyList(),
    // === maimai DX API 配置 ===
    val keychipId: String = "",
    val aimeSalt: String = "",
    val aesIv: String = "",
    val aesKey: String = "",
    val titleServerUrl: String = "",
    val aimeUrl: String = "",
    val packetSalt: String = "",
    val obfuscateParam: String = "LatuAa81",
    val apiVersion: String = "1.53",
    val clientId: String = "",
    val regionId: Int = 0,
    val regionName: String = "",
    val placeId: Int = 0,
    val placeName: String = "",
    val qqid: Long = 0,
)
