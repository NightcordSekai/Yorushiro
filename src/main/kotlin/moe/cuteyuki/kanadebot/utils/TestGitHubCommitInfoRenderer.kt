package moe.cuteyuki.kanadebot.utils

import com.alibaba.fastjson2.JSON
import com.alibaba.fastjson2.JSONArray
import com.alibaba.fastjson2.JSONObject
import java.io.ByteArrayInputStream
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.Base64
import javax.imageio.ImageIO

/**
 * 本地预览 [GitHubCommitInfoRenderer] 的输出。
 *
 * 直接 main 运行：
 *   - 不带参数：渲染一组覆盖各种边界条件的 mock commit。
 *   - 带参数（`owner/repo`）：尝试从 GitHub API 拉该仓库最新 5 条 commit 并真实渲染。
 *
 * 输出文件：`github_commit_preview_<n>.png`，与 `runPreview` 的产物互不影响。
 *
 * 用法：
 *   ./gradlew runCommitPreview                                   # mock
 *   ./gradlew runCommitPreview --args="JetBrains/kotlin"         # 真实仓库
 */
fun main(args: Array<String>) {
    enableJava2DAcceleration()

    val outputs = mutableListOf<File>()

    if (args.isNotEmpty()) {
        val repo = args[0].trim()
        val live = fetchLiveCommits(repo)
        if (live.isEmpty()) {
            println("[TestGitHubCommitInfoRenderer] 未能从 $repo 拉到 commit，回退到 mock")
            outputs += renderMocks()
        } else {
            outputs += live.mapIndexed { idx, c -> writePng("github_commit_live_${idx + 1}.png", c) }
        }
    } else {
        outputs += renderMocks()
    }

    println("Generated ${outputs.size} preview file(s):")
    outputs.forEach { println(" - ${it.absolutePath} (${it.length()} bytes)") }
}

// ---- mocks ----

private fun renderMocks(): List<File> {
    val cases = listOf(
        // 1. 标题 + 多行 body + 完整 stats，无头像
        GitHubCommitInfoRenderer.Commit(
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
        ),
        // 2. 仅有标题（最常见的 fix:）
        GitHubCommitInfoRenderer.Commit(
            repo = "chuxuehaocai/KanadeBot",
            shortSha = "9f8e7d6",
            title = "fix: ellipsize long commit titles correctly",
            body = null,
            authorName = "chuxuehaocai",
            authorAvatar = null,
            timestamp = "2026-05-20T18:02:11Z",
            filesChanged = 1,
            additions = 8,
            deletions = 2,
        ),
        // 3. 超长标题 + 长 body（应被截断 + 省略号）
        GitHubCommitInfoRenderer.Commit(
            repo = "very-long-org-name/very-long-repository-name",
            shortSha = "abcdef0",
            title = "feat(everything): a really really really long commit title that should wrap to multiple lines and still look fine in the card layout",
            body = (1..15).joinToString("\n") { "* line $it: lorem ipsum dolor sit amet, consectetur adipiscing elit." },
            authorName = "Long Author Name 名字非常长以便测试",
            authorAvatar = null,
            timestamp = "2026-05-19T03:14:07Z",
            filesChanged = 132,
            additions = 9876,
            deletions = 5432,
        ),
        // 4. 不展示 stats、无 body
        GitHubCommitInfoRenderer.Commit(
            repo = "octocat/Hello-World",
            shortSha = "0bad1de",
            title = "docs: tweak README phrasing",
            body = null,
            authorName = "octocat",
            authorAvatar = null,
            timestamp = "2026-05-18T22:00:00Z",
            filesChanged = -1,
            additions = -1,
            deletions = -1,
        ),
    )
    return cases.mapIndexed { idx, c -> writePng("github_commit_preview_${idx + 1}.png", c) }
}

private fun writePng(name: String, commit: GitHubCommitInfoRenderer.Commit): File {
    val started = System.currentTimeMillis()
    val base64 = GitHubCommitInfoRenderer.render(commit)
    val bytes = Base64.getDecoder().decode(base64)
    val file = File(name)
    file.writeBytes(bytes)
    println("Wrote ${bytes.size} bytes -> ${file.absolutePath} in ${System.currentTimeMillis() - started}ms")
    return file
}

// ---- live fetch (匿名 GitHub API；rate limit 60/h) ----

private val http: HttpClient = HttpClient.newBuilder()
    .connectTimeout(Duration.ofSeconds(8))
    .followRedirects(HttpClient.Redirect.NORMAL)
    .build()

private fun ghGet(url: String): String? {
    val req = HttpRequest.newBuilder()
        .uri(URI.create(url))
        .timeout(Duration.ofSeconds(15))
        .header("Accept", "application/vnd.github+json")
        .header("X-GitHub-Api-Version", "2022-11-28")
        .header("User-Agent", "KanadeBot/1.0 (preview)")
        .GET()
        .build()
    return runCatching {
        val resp = http.send(req, HttpResponse.BodyHandlers.ofString())
        if (resp.statusCode() !in 200..299) {
            System.err.println("[TestGitHubCommitInfoRenderer] GET $url -> ${resp.statusCode()}: ${resp.body().take(200)}")
            null
        } else resp.body()
    }.getOrNull()
}

private fun fetchLiveCommits(repo: String): List<GitHubCommitInfoRenderer.Commit> {
    val body = ghGet("https://api.github.com/repos/$repo/commits?per_page=5") ?: return emptyList()
    val arr: JSONArray = JSON.parseArray(body) ?: return emptyList()
    val out = mutableListOf<GitHubCommitInfoRenderer.Commit>()
    for (i in 0 until arr.size) {
        val obj = arr.getJSONObject(i) ?: continue
        out += parseCommit(repo, obj) ?: continue
    }
    return out
}

private fun parseCommit(repo: String, json: JSONObject): GitHubCommitInfoRenderer.Commit? {
    val sha = json.getString("sha") ?: return null
    val commit = json.getJSONObject("commit") ?: return null
    val message = commit.getString("message").orEmpty()
    val (title, bodyText) = splitMsg(message)

    val authorObj = commit.getJSONObject("author")
    val authorName = authorObj?.getString("name")
        ?: json.getJSONObject("author")?.getString("login")
        ?: "unknown"
    val timestamp = authorObj?.getString("date") ?: ""
    val avatarUrl = json.getJSONObject("author")?.getString("avatar_url")

    val avatar = avatarUrl?.let {
        runCatching {
            val bytes = http.send(
                HttpRequest.newBuilder()
                    .uri(URI.create(it))
                    .timeout(Duration.ofSeconds(8))
                    .header("User-Agent", "KanadeBot/1.0 (preview)")
                    .GET()
                    .build(),
                HttpResponse.BodyHandlers.ofByteArray()
            ).body()
            ImageIO.read(ByteArrayInputStream(bytes))
        }.getOrNull()
    }

    // 详情接口拿 stats（匿名也能用）
    val detail = ghGet("https://api.github.com/repos/$repo/commits/$sha")?.let { JSON.parseObject(it) }
    val stats = detail?.getJSONObject("stats")
    val files = detail?.getJSONArray("files")?.size ?: -1

    return GitHubCommitInfoRenderer.Commit(
        repo = repo,
        shortSha = sha.take(7),
        title = title,
        body = bodyText,
        authorName = authorName,
        authorAvatar = avatar,
        timestamp = timestamp,
        filesChanged = files,
        additions = stats?.getIntValue("additions") ?: -1,
        deletions = stats?.getIntValue("deletions") ?: -1,
        url = json.getString("html_url"),
    )
}

private fun splitMsg(msg: String): Pair<String, String?> {
    val trimmed = msg.trimEnd()
    val idx = trimmed.indexOf('\n')
    return if (idx < 0) trimmed to null
    else trimmed.substring(0, idx) to trimmed.substring(idx + 1).trim().takeIf { it.isNotBlank() }
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
