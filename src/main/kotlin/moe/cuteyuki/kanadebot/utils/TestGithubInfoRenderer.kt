package moe.cuteyuki.kanadebot.utils

import java.io.File
import java.util.Base64

/**
 * 本地预览 [GitHubRepoInfoRenderer] 的输出。
 *
 * 用法：
 *   ./gradlew runGithubInfoPreview                              # mock
 *   ./gradlew runGithubInfoPreview --args="JetBrains/kotlin"   # 真实仓库（联网）
 */
fun main(args: Array<String>) {
    enableJava2DAcceleration()

    val outputs = mutableListOf<File>()

    if (args.isNotEmpty()) {
        val repo = args[0].trim()
        outputs += renderLive(repo)
        if (outputs.all { it.length() == 0L }) {
            println("[TestGithubInfoRenderer] 拉取失败，回退到 mock")
            outputs.clear()
            outputs += renderMockInfo()
        }
    } else {
        outputs += renderMockInfo()
    }

    println("Generated ${outputs.size} preview file(s):")
    outputs.forEach { println(" - ${it.absolutePath} (${it.length()} bytes)") }
}

private fun renderLive(repo: String): List<File> {
    val json = GitHubApi.repo(repo) ?: return emptyList()
    val owner = json.getJSONObject("owner")
    val avatar = owner?.getString("avatar_url")?.let { GitHubApi.getImage(it) }

    val latest = runCatching {
        val arr = GitHubApi.commits(repo, perPage = 1) ?: return@runCatching null
        if (arr.isEmpty()) return@runCatching null
        val c = arr.getJSONObject(0)
        val sha = c.getString("sha")
        val commit = c.getJSONObject("commit")
        val message = commit.getString("message").orEmpty()
        val title = message.lineSequence().firstOrNull()?.trim().orEmpty()
        val author = commit.getJSONObject("author")
        GitHubRepoInfoRenderer.LatestCommit(
            shortSha = sha.take(7),
            title = title,
            authorName = author?.getString("name") ?: c.getJSONObject("author")?.getString("login") ?: "unknown",
            timestamp = author?.getString("date").orEmpty(),
        )
    }.getOrNull()

    val info = GitHubRepoInfoRenderer.RepoInfo(
        fullName = json.getString("full_name") ?: repo,
        description = json.getString("description"),
        language = json.getString("language"),
        stars = json.getIntValue("stargazers_count"),
        forks = json.getIntValue("forks_count"),
        openIssues = json.getIntValue("open_issues_count"),
        defaultBranch = json.getString("default_branch"),
        homepage = json.getString("homepage"),
        ownerLogin = owner?.getString("login").orEmpty(),
        ownerAvatar = avatar,
        isPrivate = json.getBooleanValue("private"),
        isFork = json.getBooleanValue("fork"),
        isArchived = json.getBooleanValue("archived"),
        updatedAt = json.getString("pushed_at") ?: json.getString("updated_at"),
        latestCommit = latest,
    )
    val out = File("github_repo_live.png")
    out.writeBytes(Base64.getDecoder().decode(GitHubRepoInfoRenderer.render(info)))
    println("Wrote ${out.length()} bytes -> ${out.absolutePath}")
    return listOf(out)
}

private fun renderMockInfo(): List<File> {
    val info = GitHubRepoInfoRenderer.RepoInfo(
        fullName = "chuxuehaocai/KanadeBot",
        description = "一个基于 Kotlin & Spring Boot 的极简 QQ 机器人骨架。提供命令分发、JSON 配置持久化、Material 3 风格的图片渲染等基础设施。",
        language = "Kotlin",
        stars = 128,
        forks = 9,
        openIssues = 3,
        defaultBranch = "main",
        homepage = "https://kanadebot.cuteyuki.moe",
        ownerLogin = "chuxuehaocai",
        ownerAvatar = null,
        isPrivate = false,
        isFork = false,
        isArchived = false,
        updatedAt = "2026-05-21T11:00:00Z",
        latestCommit = GitHubRepoInfoRenderer.LatestCommit(
            shortSha = "1a2b3c4",
            title = "feat: render Markdown README to a Material 3 image card",
            authorName = "Yuki",
            timestamp = "2026-05-21T11:00:00Z",
        ),
    )
    val out = File("github_repo_preview.png")
    out.writeBytes(Base64.getDecoder().decode(GitHubRepoInfoRenderer.render(info)))
    println("Wrote ${out.length()} bytes -> ${out.absolutePath}")
    return listOf(out)
}

private fun enableJava2DAcceleration() {
    val os = System.getProperty("os.name")?.lowercase().orEmpty()
    if ("mac" in os || "darwin" in os) {
        System.setProperty("sun.java2d.metal", "true")
    } else {
        System.setProperty("sun.java2d.opengl", "true")
    }
    System.setProperty("sun.java2d.noddraw", "true")
}
