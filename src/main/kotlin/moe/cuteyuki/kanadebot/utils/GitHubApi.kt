package moe.cuteyuki.kanadebot.utils

import com.alibaba.fastjson2.JSON
import com.alibaba.fastjson2.JSONArray
import com.alibaba.fastjson2.JSONObject
import moe.cuteyuki.kanadebot.managers.ConfigManager
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.Base64
import javax.imageio.ImageIO

/**
 * 共享的 GitHub REST v3 客户端。
 * - `authHeader` 自动从 `kanade.json#githubToken` 注入 Bearer
 * - 提供高频用到的端点封装
 */
object GitHubApi {

    private val client: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(8))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()

    private fun authHeader(): String? =
        ConfigManager.getConfig().githubToken
            .takeIf { it.isNotBlank() }
            ?.let { "Bearer $it" }

    fun request(url: String, accept: String = "application/vnd.github+json"): HttpRequest {
        val builder = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofSeconds(15))
            .header("Accept", accept)
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("User-Agent", "KanadeBot/1.0")
        authHeader()?.let { builder.header("Authorization", it) }
        return builder.GET().build()
    }

    /**
     * 拿一个 JSON 字符串（可能是 object 或 array）。失败返回 null。
     */
    fun getJsonString(url: String): String? {
        return runCatching {
            val resp = client.send(request(url), HttpResponse.BodyHandlers.ofString())
            if (resp.statusCode() !in 200..299) {
                System.err.println("[GitHubApi] $url -> ${resp.statusCode()}: ${resp.body().take(200)}")
                null
            } else resp.body()
        }.getOrNull()
    }

    fun getJsonObject(url: String): JSONObject? = getJsonString(url)?.let { JSON.parseObject(it) }
    fun getJsonArray(url: String): JSONArray? = getJsonString(url)?.let { JSON.parseArray(it) }

    fun getRawBytes(url: String, accept: String = "application/octet-stream"): ByteArray? {
        return runCatching {
            val resp = client.send(request(url, accept), HttpResponse.BodyHandlers.ofByteArray())
            if (resp.statusCode() !in 200..299) null else resp.body()
        }.getOrNull()
    }

    fun getImage(url: String): BufferedImage? {
        return runCatching {
            // 图片走匿名请求即可，避免把 token 发给非 api.github.com
            val req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .header("User-Agent", "KanadeBot/1.0")
                .GET()
                .build()
            val resp = client.send(req, HttpResponse.BodyHandlers.ofByteArray())
            if (resp.statusCode() !in 200..299) return@runCatching null
            ImageIO.read(ByteArrayInputStream(resp.body()))
        }.getOrNull()
    }

    // ---- Endpoints ----

    fun repo(repo: String): JSONObject? = getJsonObject("https://api.github.com/repos/$repo")

    fun commits(repo: String, perPage: Int = 10): JSONArray? =
        getJsonArray("https://api.github.com/repos/$repo/commits?per_page=$perPage")

    fun commit(repo: String, sha: String): JSONObject? =
        getJsonObject("https://api.github.com/repos/$repo/commits/$sha")
}
