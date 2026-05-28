package moe.cuteyuki.kanadebot.utils

import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Paths
import java.time.Duration
import javax.imageio.ImageIO

/**
 * QQ 头像加载器：磁盘缓存 + 远端拉取。
 *
 * 缓存目录：`{workingDir}/resource/avatar/{userId}.png`
 * 获取失败返回 `null`，调用方自行降级。
 */
object AvatarProvider {

    private const val URL_TEMPLATE = "https://q1.qlogo.cn/g?b=qq&nk=%d&s=640"

    private val client: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()

    private val cacheDir: File by lazy {
        val dir = File(Paths.get("").toAbsolutePath().toFile(), "resource/avatar")
        if (!dir.exists()) dir.mkdirs()
        dir
    }

    /**
     * 拉取头像。命中缓存直接读盘；否则发起一次 HTTPS 请求并落盘。
     */
    fun fetch(userId: Long): BufferedImage? {
        val cacheFile = File(cacheDir, "$userId.png")
        if (cacheFile.exists() && cacheFile.length() > 0) {
            runCatching { ImageIO.read(cacheFile) }.getOrNull()?.let { return it }
        }

        val request = HttpRequest.newBuilder()
            .uri(URI.create(URL_TEMPLATE.format(userId)))
            .timeout(Duration.ofSeconds(8))
            .header("User-Agent", "KanadeBot/1.0")
            .GET()
            .build()

        return runCatching {
            val resp = client.send(request, HttpResponse.BodyHandlers.ofByteArray())
            if (resp.statusCode() !in 200..299) return@runCatching null
            val bytes = resp.body() ?: return@runCatching null
            if (bytes.isEmpty()) return@runCatching null
            // 落盘
            runCatching { Files.write(cacheFile.toPath(), bytes) }
            ImageIO.read(ByteArrayInputStream(bytes))
        }.getOrNull()
    }
}
