package moe.cuteyuki.kanadebot.managers

import java.io.File

/**
 * 端到端验证 [ConfigManager] 的 schema 同步行为。
 *
 * 通过 `./gradlew verifyConfigSchema` 触发；该任务把 workingDir 指向独立的临时目录，
 * 避免污染项目根目录里的 `kanade.json`。
 */
fun main() {
    val cfg = File("kanade.json").absoluteFile
    println("workingDir = ${cfg.parentFile}")
    println("config file = $cfg")

    // ---- case 1: 缺字段的旧文件 ----
    cfg.writeText(
        """
        {
          "deepSeekApiKey": "sk-old"
        }
        """.trimIndent()
    )
    val before = cfg.readText()
    println("\n[case 1] before:\n$before")
    ConfigManager.initialize()
    val after = cfg.readText()
    println("[case 1] after:\n$after")
    require("\"botAdmins\"" in after) { "botAdmins 应被自动补全到文件" }
    require("\"githubToken\"" in after) { "githubToken 应被自动补全到文件" }
    require(ConfigManager.getConfig().deepSeekApiKey == "sk-old") { "原有字段不应丢失" }
    require(ConfigManager.getConfig().botAdmins.isEmpty())

    // ---- case 2: 完整字段，再次加载不应改写文件 mtime 之外的内容 ----
    cfg.writeText(
        """
        {
          "deepSeekApiKey": "sk-new",
          "githubToken": "ghp_xxx",
          "botAdmins": [3849859967, "10086"]
        }
        """.trimIndent()
    )
    ConfigManager.initialize()
    val parsed = ConfigManager.getConfig()
    require(parsed.deepSeekApiKey == "sk-new")
    require(parsed.githubToken == "ghp_xxx")
    require(parsed.botAdmins == listOf(3849859967L, 10086L)) { "botAdmins 解析错误：${parsed.botAdmins}" }

    println("\nOK ✔️ schema 同步与字段解析均正常")
}
