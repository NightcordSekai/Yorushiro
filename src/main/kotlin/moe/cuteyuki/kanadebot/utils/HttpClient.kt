package moe.cuteyuki.kanadebot.utils

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.Proxy
import java.net.URL

object HttpClient {
    data class HttpResult(
        val statusCode: Int,
        val headers: Map<String, List<String>>?,
        val body: ByteArray?
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as HttpResult

            if (statusCode != other.statusCode) return false
            if (headers != other.headers) return false
            if (!body.contentEquals(other.body)) return false

            return true
        }

        override fun hashCode(): Int {
            var result = statusCode
            result = 31 * result + (headers?.hashCode() ?: 0)
            result = 31 * result + (body?.contentHashCode() ?: 0)
            return result
        }

        /** 提取所有 Set-Cookie 响应头值（用于后续请求携带）。大小写无关匹配。 */
        fun cookies(): List<String> {
            val h = headers ?: return emptyList()
            return h.entries
                .filter { it.key.equals("Set-Cookie", ignoreCase = true) }
                .flatMap { it.value }
        }

        /** 提取第一个 Set-Cookie 中的 JSESSIONID 值。 */
        fun jsessionId(): String? {
            for (cookie in cookies()) {
                val match = Regex("JSESSIONID=([^;]+)").find(cookie)
                if (match != null) return match.groupValues[1]
            }
            return null
        }

        /** 组装 Cookie 请求头（格式: "JSESSIONID=xxx"）。 */
        fun cookieHeader(): String? =
            jsessionId()?.let { "JSESSIONID=$it" }
    }

    /**
     * POST 请求
     *
     * @param urlStr   请求 URL
     * @param headers  请求头
     * @param body     请求体 (byte[])
     * @param timeout  超时时间 (秒)
     * @return HttpResult
     * @throws java.io.IOException 网络错误时抛出异常，不再静默吞掉 (fix: 参考 Python 的 httpx 行为)
     */
    @Throws(IOException::class)
    fun post(
        urlStr: String,
        headers: Map<String?, String?>?,
        body: ByteArray?,
        timeout: Double
    ): HttpResult {
        var conn: HttpURLConnection? = null
        try {
            val url = URL(urlStr)
            conn = url.openConnection(Proxy.NO_PROXY) as HttpURLConnection?
            conn!!.setRequestMethod("POST")
            conn.setConnectTimeout((timeout * 1000).toInt())
            conn.setReadTimeout((timeout * 1000).toInt())
            conn.setDoOutput(true)

            // 设置请求头
            if (headers != null) {
                for (entry in headers.entries) {
                    conn.setRequestProperty(entry.key, entry.value)
                }
            }

            // 发送数据
            if (body != null && body.isNotEmpty()) {
                conn.getOutputStream().use { os ->
                    os.write(body)
                }
            }

            val statusCode = conn.getResponseCode()

            // 读取响应头（保留所有值，支持多 Set-Cookie）
            val responseHeaders: MutableMap<String, List<String>> = linkedMapOf()
            for (entry in conn.headerFields.entries) {
                if (entry.key != null && entry.value.isNotEmpty()) {
                    responseHeaders[entry.key] = entry.value
                }
            }

            // 读取响应体 (包含错误响应体)
            val inputStream: InputStream?
            if (statusCode in 200..399) {
                inputStream = conn.getInputStream()
            } else {
                inputStream = conn.errorStream
            }

            var responseBody = ByteArray(0)
            if (inputStream != null) {
                ByteArrayOutputStream().use { baos ->
                    val buffer = ByteArray(4096)
                    var len: Int
                    while ((inputStream.read(buffer).also { len = it }) != -1) {
                        baos.write(buffer, 0, len)
                    }
                    responseBody = baos.toByteArray()
                }
            }

            return HttpResult(statusCode, responseHeaders, responseBody)
        } finally {
            conn?.disconnect()
        }
    }

    /**
     * GET 请求
     */
    @Throws(IOException::class)
    fun get(
        urlStr: String,
        headers: Map<String?, String?>?,
        timeout: Double
    ): HttpResult {
        var conn: HttpURLConnection? = null
        try {
            val url = URL(urlStr)
            conn = url.openConnection(Proxy.NO_PROXY) as HttpURLConnection?
            conn!!.setRequestMethod("GET")
            conn.setConnectTimeout((timeout * 1000).toInt())
            conn.setReadTimeout((timeout * 1000).toInt())

            if (headers != null) {
                for (entry in headers.entries) {
                    conn.setRequestProperty(entry.key, entry.value)
                }
            }

            val statusCode = conn.getResponseCode()

            val responseHeaders: MutableMap<String, List<String>> = linkedMapOf()
            for (entry in conn.getHeaderFields().entries) {
                if (entry.key != null && entry.value.isNotEmpty()) {
                    responseHeaders[entry.key] = entry.value
                }
            }

            val inputStream: InputStream?
            if (statusCode in 200..399) {
                inputStream = conn.getInputStream()
            } else {
                inputStream = conn.errorStream
            }

            var responseBody = ByteArray(0)
            if (inputStream != null) {
                ByteArrayOutputStream().use { baos ->
                    val buffer = ByteArray(4096)
                    var len: Int
                    while ((inputStream.read(buffer).also { len = it }) != -1) {
                        baos.write(buffer, 0, len)
                    }
                    responseBody = baos.toByteArray()
                }
            }

            return HttpResult(statusCode, responseHeaders, responseBody)
        } finally {
            conn?.disconnect()
        }
    }
}
