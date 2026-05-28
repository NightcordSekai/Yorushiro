package moe.cuteyuki.kanadebot.mainetwork

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moe.cuteyuki.kanadebot.managers.ConfigManager
import moe.cuteyuki.kanadebot.utils.CipherAES
import moe.cuteyuki.kanadebot.utils.HttpClient
import moe.cuteyuki.kanadebot.utils.Logger
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.util.zip.Deflater
import java.util.zip.Inflater

object NetworkManager {

    data class TitleResponse(
        val body: String,
        /** Cookie 请求头值（如 "JSESSIONID=abc123"），可空。 */
        val cookieHeader: String?
    )

    private val titleServerUri: String
        get() = ConfigManager.getConfig().titleServerUrl

    private val OBFUSCATE_PARAM: String
        get() = ConfigManager.getConfig().obfuscateParam

    private val API_VERSION: String
        get() = ConfigManager.getConfig().apiVersion

    /**
     * 挂起版本 — 仅返回 body（向后兼容）。
     */
    @Throws(Exception::class)
    suspend fun sendToTitleSuspend(
        data: String, useApi: String?, userId: Long,
        cookie: String? = null
    ): String {
        return withContext(Dispatchers.IO) {
            sendToTitleBlocking(data, useApi, userId, cookie).body
        }
    }

    /**
     * 挂起版本 — 返回完整 TitleResponse（body + cookieHeader）。
     * 用于 UserLoginApi，后续请求需要携带返回的 cookie。
     */
    @Throws(Exception::class)
    suspend fun sendToTitleWithCookieSuspend(
        data: String, useApi: String?, userId: Long
    ): TitleResponse {
        return withContext(Dispatchers.IO) {
            sendToTitleBlocking(data, useApi, userId, cookie = null, captureCookie = true)
        }
    }

    /**
     * 阻塞版本（保留向后兼容性）。
     */
    @Throws(Exception::class)
    fun sendToTitle(
        data: String, useApi: String?, userId: Long,
        cookie: String? = null
    ): String {
        return sendToTitleBlocking(data, useApi, userId, cookie).body
    }

    /**
     * 实际的阻塞网络调用实现。
     *
     * @param captureCookie 是否从响应头抓取 Set-Cookie（仅 login 时需要）。
     */
    private fun sendToTitleBlocking(
        data: String, useApi: String?, userId: Long,
        cookie: String? = null,
        captureCookie: Boolean = false
    ): TitleResponse {
        val api: String = useApi!!
        val hashApi = APIObfuscator(api)

        val plainBytes = data.toByteArray(StandardCharsets.UTF_8)
        val compressed = zlibCompress(plainBytes)
        val encrypted: ByteArray? = CipherAES.encrypt(compressed)

        // 匹配 Sinmai NetHttpClient.Request + Packet.ProcImpl 设置的请求头：
        // Content-Type / User-Agent / charset / Mai-Encoding / Content-Encoding (有 body 时) / number
        // 不再硬编码 Host（让 URL 决定）和 Accept-Encoding（Sinmai 未设置）。
        val uaSuffix: String = if (userId != 0L) userId.toString() else ConfigManager.getConfig().clientId
        val headers: MutableMap<String?, String?> = LinkedHashMap()
        headers["Content-Type"] = "application/json"
        headers["User-Agent"] = "$hashApi#$uaSuffix"
        headers["charset"] = "UTF-8"
        headers["Mai-Encoding"] = API_VERSION
        if (encrypted != null && encrypted.isNotEmpty()) {
            headers["Content-Encoding"] = "deflate"
        }
        headers["number"] = "0"
        if (!cookie.isNullOrBlank()) {
            headers["Cookie"] = cookie
        }

        val url = titleServerUri + "/" + hashApi

        Logger.log("URL:" + url + " Data:" + data, Logger.LogType.DEBUG)

        val maxRetries = 2
        var lastException: Exception? = null

        for (attempt in 0..<maxRetries) {
            try {
                val httpResult: HttpClient.HttpResult = HttpClient.post(url, headers, encrypted, 15.0)

                if (httpResult.statusCode != 200) {
                    val bodyText =
                        if (httpResult.body != null) String(httpResult.body, StandardCharsets.UTF_8) else ""
                    lastException = Exception("Response error: " + httpResult.statusCode + "\n" + bodyText)
                    if (attempt < maxRetries - 1) {
                        Logger.log(
                            "API retry " + (attempt + 1) + "/" + maxRetries + " for " + useApi,
                            Logger.LogType.DEBUG
                        )
                        Thread.sleep(1000)
                        continue
                    }
                    throw lastException
                }

                val respBytes: ByteArray? = httpResult.body
                if (respBytes == null || respBytes.isEmpty()) {
                    lastException = Exception("Empty response body")
                    if (attempt < maxRetries - 1) {
                        Logger.log(
                            "API retry " + (attempt + 1) + "/" + maxRetries + " for " + useApi + " (empty body)",
                            Logger.LogType.DEBUG
                        )
                        Thread.sleep(1000)
                        continue
                    }
                    throw lastException
                }

                val decrypted: ByteArray?
                try {
                    decrypted = CipherAES.decrypt(respBytes)
                } catch (e: Exception) {
                    throw Exception("AES decrypt failed: " + e.message, e)
                }

                val decompressed: ByteArray?
                try {
                    decompressed = zlibDecompress(decrypted)
                } catch (e: Exception) {
                    throw Exception("Zlib decompression failed: " + e.message, e)
                }

                val body = String(decompressed, StandardCharsets.UTF_8)
                val cookieHeader = if (captureCookie) httpResult.cookieHeader() else null

                // 登录成功时只打印 JSESSIONID 是否捕获，避免敏感信息进入日志
                if (captureCookie) {
                    if (cookieHeader != null) {
                        Logger.log(
                            "UserLoginApi 已捕获 JSESSIONID (len=${cookieHeader.length})",
                            Logger.LogType.DEBUG
                        )
                    } else {
                        Logger.log("UserLoginApi 未返回 JSESSIONID", Logger.LogType.DEBUG)
                    }
                }

                return TitleResponse(body, cookieHeader)

            } catch (e: SocketTimeoutException) {
                lastException = e
                if (attempt < maxRetries - 1) {
                    Logger.log(
                        "API retry " + (attempt + 1) + "/" + maxRetries + " for " + useApi + " (network error: " + e.javaClass.getSimpleName() + ")",
                        Logger.LogType.DEBUG
                    )
                    Thread.sleep(2000)
                    continue
                }
            } catch (e: UnknownHostException) {
                lastException = e
                if (attempt < maxRetries - 1) {
                    Logger.log(
                        "API retry " + (attempt + 1) + "/" + maxRetries + " for " + useApi + " (network error: " + e.javaClass.getSimpleName() + ")",
                        Logger.LogType.DEBUG
                    )
                    Thread.sleep(2000)
                    continue
                }
            } catch (e: IOException) {
                lastException = e
                if (attempt < maxRetries - 1) {
                    Logger.log(
                        "API retry " + (attempt + 1) + "/" + maxRetries + " for " + useApi + " (IO error: " + e.message + ")",
                        Logger.LogType.DEBUG
                    )
                    Thread.sleep(1000)
                    continue
                }
            }
        }

        throw if (lastException != null) lastException else Exception("API call failed after retries: " + useApi)
    }

    private fun APIObfuscator(api: String?): String {
        try {
            val combined = api + "MaimaiChn" + OBFUSCATE_PARAM
            val md = MessageDigest.getInstance("MD5")
            val digest = md.digest(combined.toByteArray(StandardCharsets.UTF_8))

            val sb = StringBuilder()
            for (b in digest) {
                sb.append(String.format("%02x", b.toInt() and 0xFF))
            }
            return sb.toString()
        } catch (e: NoSuchAlgorithmException) {
            throw RuntimeException("MD5 algorithm not available", e)
        }
    }

    @Throws(IOException::class)
    private fun zlibCompress(input: ByteArray?): ByteArray {
        val deflater = Deflater(Deflater.DEFAULT_COMPRESSION, false)
        deflater.setInput(input)
        deflater.finish()

        val buffer = ByteArray(1024)
        val chunks: MutableList<ByteArray> = ArrayList<ByteArray>()
        var totalLen = 0

        while (!deflater.finished()) {
            val count = deflater.deflate(buffer)
            chunks.add(buffer.copyOf(count))
            totalLen += count
        }

        val output = ByteArray(totalLen)
        var offset = 0
        for (chunk in chunks) {
            System.arraycopy(chunk, 0, output, offset, chunk.size)
            offset += chunk.size
        }
        return output
    }

    @Throws(IOException::class)
    private fun zlibDecompress(input: ByteArray?): ByteArray {
        val inflater = Inflater(false)
        inflater.setInput(input)

        val buffer = ByteArray(1024)
        val chunks: MutableList<ByteArray> = ArrayList<ByteArray>()
        var totalLen = 0

        try {
            while (!inflater.finished()) {
                val count = inflater.inflate(buffer)
                if (count == 0 && inflater.needsInput()) break
                chunks.add(buffer.copyOf(count))
                totalLen += count
            }
        } catch (e: Exception) {
            throw IOException("ZLIB 解压失败", e)
        }

        val output = ByteArray(totalLen)
        var offset = 0
        for (chunk in chunks) {
            System.arraycopy(chunk, 0, output, offset, chunk.size)
            offset += chunk.size
        }
        return output
    }
}
