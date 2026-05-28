package moe.cuteyuki.kanadebot.managers

import com.alibaba.fastjson2.JSON
import com.alibaba.fastjson2.JSONArray
import com.alibaba.fastjson2.JSONObject
import com.alibaba.fastjson2.JSONWriter
import com.mikuac.shiro.common.utils.MsgUtils
import com.mikuac.shiro.core.Bot
import com.mikuac.shiro.core.BotContainer
import moe.cuteyuki.kanadebot.utils.DeepSeekClient
import moe.cuteyuki.kanadebot.utils.GitHubApi
import moe.cuteyuki.kanadebot.utils.GitHubCommitInfoRenderer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Paths
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import javax.imageio.ImageIO

/**
 * GitHub 仓库订阅管理器：
 *
 * - 订阅以 `owner/repo` 为单位，存储 `{ repo -> { lastSha, subscribers: [{botId, groupId}] } }`
 * - 后台 60s 轮询 `GET /repos/{owner}/{repo}/commits?per_page=10`
 * - 仓库首次拉到 sha 时只登记 lastSha，不推送（避免「订阅瞬间被 100 条历史 commit 刷屏」）
 * - 之后每次拉到的最新 sha 与 lastSha 不同，则把新增的 commit（按时间正序）逐条渲染成图发到所有订阅群
 *
 * 持久化文件：`{workingDir}/github_subs.json`
 */
object GitHubManager {

    private const val FILE_NAME = "github_subs.json"
    private val POLL_INTERVAL_SECONDS = 60L
    private val MAX_NEW_COMMITS_PER_TICK = 5

    data class Subscriber(val botId: Long, val groupId: Long)

    private data class RepoState(
        var lastSha: String?,
        val subscribers: MutableSet<Subscriber>,
        var complianceChecked: Boolean = false,
    )

    private val store = ConcurrentHashMap<String, RepoState>()

    private lateinit var file: File
    private lateinit var botContainer: BotContainer
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var pollerJob: Job? = null

    private val http: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(8))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()

    /**
     * 启动 manager。在 Spring 注入完成、机器人就绪后调用一次。
     */
    @Synchronized
    fun initialize(botContainer: BotContainer) {
        this.botContainer = botContainer
        val workingDir = Paths.get("").toAbsolutePath().toFile()
        file = File(workingDir, FILE_NAME)
        if (file.exists()) {
            runCatching { load() }.onFailure {
                System.err.println("[GitHubManager] 读取 $FILE_NAME 失败: ${it.message}")
            }
        }
        if (pollerJob == null) {
            pollerJob = scope.launch {
                delay(10_000)
                while (isActive) {
                    runCatching { tick() }.onFailure { it.printStackTrace() }
                    delay(POLL_INTERVAL_SECONDS * 1000)
                }
            }
            println("[GitHubManager] poller started, interval=${POLL_INTERVAL_SECONDS}s, repos=${store.size}")
        }
    }

    /**
     * 添加订阅。返回 true 表示新增，false 表示该群已订阅过。
     * 首次添加该 repo 时立即同步一次 latest sha 用作基线，**不会**推送旧 commit。
     */
    @Synchronized
    fun subscribe(repo: String, botId: Long, groupId: Long): Boolean {
        val normalized = normalizeRepo(repo)
        val state = store.computeIfAbsent(normalized) {
            // 新仓库，初始化基线
            val sha = runCatching { fetchLatestSha(it) }.getOrNull()
            RepoState(lastSha = sha, subscribers = HashSet())
        }
        val sub = Subscriber(botId, groupId)
        val added = state.subscribers.add(sub)
        if (added) save()
        return added
    }

    /**
     * 取消订阅。返回 true 表示移除成功。
     * 如果某仓库再无订阅者，会一并从 store 中移除。
     */
    @Synchronized
    fun unsubscribe(repo: String, botId: Long, groupId: Long): Boolean {
        val normalized = normalizeRepo(repo)
        val state = store[normalized] ?: return false
        val removed = state.subscribers.remove(Subscriber(botId, groupId))
        if (state.subscribers.isEmpty()) store.remove(normalized)
        if (removed) save()
        return removed
    }

    fun listForGroup(botId: Long, groupId: Long): List<String> {
        val sub = Subscriber(botId, groupId)
        return store.entries
            .filter { it.value.subscribers.contains(sub) }
            .map { it.key }
            .sorted()
    }

    // ---- polling ----

    private fun tick() {
        if (store.isEmpty()) return
        // copy 避免并发改动
        val repos = store.keys.toList()
        for (repo in repos) {
            val state = store[repo] ?: continue
            try {
                val commits = fetchCommitsJson(repo) ?: continue
                if (commits.isEmpty()) continue
                val latestSha = commits.getJSONObject(0).getString("sha")

                if (state.lastSha == null) {
                    state.lastSha = latestSha
                    save()
                    continue
                }
                if (state.lastSha == latestSha) continue

                // 找出新增的 commits（位于最新 commit -> lastSha 之间）
                val newOnes = mutableListOf<JSONObject>()
                for (i in 0 until commits.size) {
                    val item = commits.getJSONObject(i)
                    if (item.getString("sha") == state.lastSha) break
                    newOnes += item
                }
                state.lastSha = latestSha
                save()

                if (newOnes.isEmpty()) continue

                // 内容合规检查（每个仓库只检查一次）
                if (!state.complianceChecked) {
                    val result = checkCompliance(repo)
                    when {
                        result == null -> {
                            pushTextToSubscribers(state.subscribers, "⚠️ 已订阅仓库 $repo 内容合规检查失败，已自动取消订阅。")
                            store.remove(repo)
                            save()
                            continue
                        }
                        !result.compliant -> {
                            pushTextToSubscribers(state.subscribers, "⚠️ 已订阅仓库 $repo 未通过内容合规检查：${result.reason}，已自动取消订阅。")
                            store.remove(repo)
                            save()
                            continue
                        }
                        else -> state.complianceChecked = true
                    }
                }

                // 旧 -> 新 顺序推送
                val toPush = newOnes.asReversed().take(MAX_NEW_COMMITS_PER_TICK)
                for (commitJson in toPush) {
                    val rendered = renderCommit(repo, commitJson) ?: continue
                    pushToSubscribers(state.subscribers, rendered)
                }
            } catch (e: Exception) {
                System.err.println("[GitHubManager] poll $repo failed: ${e.message}")
            }
        }
    }

    private fun pushToSubscribers(subs: Set<Subscriber>, base64: String) {
        for (sub in subs) {
            val bot = botContainer.robots[sub.botId] ?: continue
            try {
                val msg = MsgUtils.builder()
                    .img("base64://$base64")
                    .build()
                bot.sendGroupMsg(sub.groupId, msg, false)
            } catch (e: Exception) {
                System.err.println("[GitHubManager] send to ${sub.groupId} via ${sub.botId} failed: ${e.message}")
            }
        }
    }

    private fun pushTextToSubscribers(subs: Set<Subscriber>, text: String) {
        for (sub in subs) {
            val bot = botContainer.robots[sub.botId] ?: continue
            try {
                bot.sendGroupMsg(sub.groupId, text, false)
            } catch (e: Exception) {
                System.err.println("[GitHubManager] send text to ${sub.groupId} via ${sub.botId} failed: ${e.message}")
            }
        }
    }

    private fun checkCompliance(repo: String): DeepSeekClient.ComplianceResult? {
        val repoJson = GitHubApi.repo(repo) ?: return null
        val fields = DeepSeekClient.extractRepoFields(repoJson)
        return DeepSeekClient.checkRepo(
            fields.fullName, fields.description, fields.language, fields.topics, fields.homepage
        )
    }

    private fun renderCommit(repo: String, json: JSONObject): String? {
        return try {
            val sha = json.getString("sha") ?: return null
            val commit = json.getJSONObject("commit") ?: return null
            val message = commit.getString("message").orEmpty()
            val (title, body) = splitCommitMessage(message)
            val authorObj = commit.getJSONObject("author")
            val authorName = authorObj?.getString("name") ?: json.getJSONObject("author")?.getString("login") ?: "unknown"
            val timestamp = authorObj?.getString("date") ?: ""
            val avatarUrl = json.getJSONObject("author")?.getString("avatar_url")
            val avatar = avatarUrl?.let { fetchAvatar(it) }
            val htmlUrl = json.getString("html_url")

            // 详情接口拿 stats（非必须）
            val (files, add, del) = runCatching { fetchStats(repo, sha) }.getOrNull() ?: Triple(-1, -1, -1)

            GitHubCommitInfoRenderer.render(
                GitHubCommitInfoRenderer.Commit(
                    repo = repo,
                    shortSha = sha.take(7),
                    title = title,
                    body = body,
                    authorName = authorName,
                    authorAvatar = avatar,
                    timestamp = timestamp,
                    filesChanged = files,
                    additions = add,
                    deletions = del,
                    url = htmlUrl,
                )
            )
        } catch (e: Exception) {
            System.err.println("[GitHubManager] render commit failed: ${e.message}")
            null
        }
    }

    private fun splitCommitMessage(msg: String): Pair<String, String?> {
        val trimmed = msg.trimEnd()
        val idx = trimmed.indexOf('\n')
        return if (idx < 0) trimmed to null
        else trimmed.substring(0, idx) to trimmed.substring(idx + 1).trim().takeIf { it.isNotBlank() }
    }

    // ---- GitHub API ----

    private fun authHeader(): String? {
        val token = ConfigManager.getConfig().githubToken
        return token.takeIf { it.isNotBlank() }?.let { "Bearer $it" }
    }

    private fun ghRequest(url: String): HttpRequest {
        val builder = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofSeconds(15))
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("User-Agent", "KanadeBot/1.0")
        authHeader()?.let { builder.header("Authorization", it) }
        return builder.GET().build()
    }

    private fun fetchLatestSha(repo: String): String? {
        val arr = fetchCommitsJson(repo) ?: return null
        if (arr.isEmpty()) return null
        return arr.getJSONObject(0).getString("sha")
    }

    private fun fetchCommitsJson(repo: String): JSONArray? {
        val url = "https://api.github.com/repos/$repo/commits?per_page=10"
        val resp = http.send(ghRequest(url), HttpResponse.BodyHandlers.ofString())
        if (resp.statusCode() !in 200..299) {
            System.err.println("[GitHubManager] commits API ${resp.statusCode()}: ${resp.body().take(200)}")
            return null
        }
        return JSON.parseArray(resp.body())
    }

    private fun fetchStats(repo: String, sha: String): Triple<Int, Int, Int>? {
        val url = "https://api.github.com/repos/$repo/commits/$sha"
        val resp = http.send(ghRequest(url), HttpResponse.BodyHandlers.ofString())
        if (resp.statusCode() !in 200..299) return null
        val obj = JSON.parseObject(resp.body()) ?: return null
        val stats = obj.getJSONObject("stats")
        val files = obj.getJSONArray("files")?.size ?: -1
        return Triple(
            files,
            stats?.getIntValue("additions") ?: -1,
            stats?.getIntValue("deletions") ?: -1,
        )
    }

    private fun fetchAvatar(url: String): BufferedImage? {
        return runCatching {
            val resp = http.send(
                HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(8))
                    .header("User-Agent", "KanadeBot/1.0")
                    .GET()
                    .build(),
                HttpResponse.BodyHandlers.ofByteArray()
            )
            if (resp.statusCode() !in 200..299) return@runCatching null
            ImageIO.read(ByteArrayInputStream(resp.body()))
        }.getOrNull()
    }

    /** 接受 `https://github.com/owner/repo[.git]` 或 `owner/repo`，统一返回 `owner/repo`。 */
    fun normalizeRepo(input: String): String {
        var s = input.trim()
        s = s.removePrefix("http://").removePrefix("https://")
        s = s.removePrefix("github.com/")
        s = s.removeSuffix(".git").trim('/')
        return s
    }

    fun isValidRepo(repo: String): Boolean {
        val regex = Regex("^[A-Za-z0-9._-]+/[A-Za-z0-9._-]+$")
        return regex.matches(repo)
    }

    // ---- persistence ----

    private fun load() {
        val json = JSON.parseObject(file.readText()) ?: return
        store.clear()
        for ((repo, raw) in json) {
            if (repo == null) continue
            val obj = raw as? JSONObject ?: continue
            val lastSha = obj.getString("lastSha")
            val subsArr = obj.getJSONArray("subscribers") ?: JSONArray()
            val subs = HashSet<Subscriber>()
            for (i in 0 until subsArr.size) {
                val s = subsArr.getJSONObject(i) ?: continue
                subs += Subscriber(s.getLongValue("botId"), s.getLongValue("groupId"))
            }
            store[repo] = RepoState(lastSha, subs)
        }
    }

    private fun save() {
        val json = JSONObject()
        for ((repo, state) in store) {
            val obj = JSONObject()
            obj["lastSha"] = state.lastSha
            val arr = JSONArray()
            for (sub in state.subscribers) {
                arr.add(JSONObject().apply {
                    this["botId"] = sub.botId
                    this["groupId"] = sub.groupId
                })
            }
            obj["subscribers"] = arr
            json[repo] = obj
        }
        file.writeText(json.toJSONString(JSONWriter.Feature.PrettyFormat))
    }
}
