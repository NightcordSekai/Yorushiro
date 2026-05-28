    package moe.cuteyuki.kanadebot.utils

import java.io.File
import java.util.Base64

/**
 * 本地预览 [JrrpBoardRenderer] 的输出。
 *
 * 直接 `main` 运行，会在工作目录下生成 `jrrp_preview.png`。
 * 不依赖 Spring / Shiro，不会启动机器人。
 */
fun main() {
    // 与正式启动保持一致：在 AWT 加载前打开 Java2D 硬件加速。
    val os = System.getProperty("os.name")?.lowercase().orEmpty()
    if ("mac" in os || "darwin" in os) {
        System.setProperty("sun.java2d.metal", "true")
    } else {
        System.setProperty("sun.java2d.opengl", "true")
    }
    System.setProperty("sun.java2d.noddraw", "true")

    // (qq, displayName, value) — 真实 QQ 用于拉头像；非真实 QQ 走占位渐变。
    val mock = listOf(
        Triple(3849859967L, "Yuki", 99),
        Triple(10001L,      "Kanade", 88),
        Triple(10086L,      "chuxuehaocai", 77),
        Triple(20000001L,   "一个超级超级长的群名片用来测试省略号是否生效", 64),
        Triple(20000002L,   "PathFinder", 50),
        Triple(20000003L,   "Lyra", 33),
        Triple(20000004L,   "Nightingale", 21),
        Triple(20000005L,   "Echo", 12),
        Triple(20000006L,   "Void", 3),
        Triple(20000007L,   "Zero", 0),
    )

    val rows = mock.mapIndexed { index, (uid, name, value) ->
        JrrpBoardRenderer.Row(
            rank = index + 1,
            userId = uid,
            displayName = name,
            value = value,
            avatar = AvatarProvider.fetch(uid)
        )
    }

    val started = System.currentTimeMillis()
    val base64 = JrrpBoardRenderer.render(rows)
    val bytes = Base64.getDecoder().decode(base64)
    val out = File("jrrp_preview.png")
    out.writeBytes(bytes)
    val cost = System.currentTimeMillis() - started

    println("Wrote ${bytes.size} bytes -> ${out.absolutePath} in ${cost}ms")

    // 也试一下空榜
    val emptyOut = File("jrrp_preview_empty.png")
    emptyOut.writeBytes(Base64.getDecoder().decode(JrrpBoardRenderer.render(emptyList())))
    println("Wrote ${emptyOut.length()} bytes -> ${emptyOut.absolutePath}")

    // help 帮助卡片预览
    val helpEntries = listOf(
        HelpImageRenderer.Entry(
            name = "help",
            aliases = listOf("h", "帮助", "菜单"),
            usage = "help",
            description = "显示所有可用命令"
        ),
        HelpImageRenderer.Entry(
            name = "jrrp",
            aliases = listOf("今日人品", "rp"),
            usage = "jrrp",
            description = "查询今日人品（0~100，每日一次，每自然日刷新）"
        ),
        HelpImageRenderer.Entry(
            name = "jrrprank",
            aliases = listOf("人品排行榜", "人品榜", "rprank"),
            usage = "jrrprank",
            description = "渲染当前群当日人品排行榜（图片）。仅展示今日在本群查过 jrrp 的成员。"
        ),
        HelpImageRenderer.Entry(
            name = "test",
            aliases = listOf("t", "ping"),
            usage = "test",
            description = "健康检查，机器人在线时回复 ciallo!"
        ),
        HelpImageRenderer.Entry(
            name = "subgithubrepo",
            aliases = listOf("订阅github", "subgh"),
            usage = "subgithubrepo <owner/repo>",
            description = "订阅 GitHub 仓库的 commit 推送。可附完整 URL 或 owner/repo。",
            restricted = true,
        ),
        HelpImageRenderer.Entry(
            name = "unsubgithubrepo",
            aliases = listOf("取消订阅github", "unsubgh"),
            usage = "unsubgithubrepo <owner/repo>",
            description = "取消订阅 GitHub 仓库。无参数时列出本群已订阅仓库。",
            restricted = true,
        ),
    )
    val helpOut = File("help_preview.png")
    helpOut.writeBytes(Base64.getDecoder().decode(HelpImageRenderer.render(helpEntries)))
    println("Wrote ${helpOut.length()} bytes -> ${helpOut.absolutePath}")

    // GitHub commit 卡预览
    val commit = GitHubCommitInfoRenderer.Commit(
        repo = "JetBrains/kotlin",
        shortSha = "1a2b3c4",
        title = "Refactor IR backend to support new Kotlin/Native targets",
        body = """
            This change introduces a new IR-based pipeline that decouples the front-end
            from the platform-specific backends.

            * Adds K2 frontend integration tests
            * Removes deprecated `OldDescriptorBasedBackend`
            * Bumps JVM target to 21 for build scripts
        """.trimIndent(),
        authorName = "Yuki",
        authorAvatar = null,
        timestamp = "2026-05-21T07:30:18Z",
        filesChanged = 47,
        additions = 1284,
        deletions = 967,
        url = "https://github.com/JetBrains/kotlin/commit/1a2b3c4"
    )
    val commitOut = File("github_commit_preview.png")
    commitOut.writeBytes(Base64.getDecoder().decode(GitHubCommitInfoRenderer.render(commit)))
    println("Wrote ${commitOut.length()} bytes -> ${commitOut.absolutePath}")
}
