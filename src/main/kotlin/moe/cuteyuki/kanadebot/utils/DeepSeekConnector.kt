package moe.cuteyuki.kanadebot.utils

import com.alibaba.fastjson2.JSON
import com.alibaba.fastjson2.JSONObject
import moe.cuteyuki.kanadebot.managers.ConfigManager
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * 通用的 DeepSeek Chat Completions API 调用工具。
 *
 * 仅负责发送请求和解析返回，不绑定任何具体业务语义。
 * API key 从 [ConfigManager] 读取。
 */
object DeepSeekConnector {

    private const val API_URL = "https://api.deepseek.com/chat/completions"
    private const val DEFAULT_MODEL = "deepseek-chat"

    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(30))
        .build()

    /**
     * 一次性消息：自定义 system prompt + 用户消息。
     *
     * @return 模型回复的文本内容（已从 choices[0].message.content 提取）
     */
    fun ask(
        systemPrompt: String,
        userPrompt: String,
        model: String = DEFAULT_MODEL,
        temperature: Double = 0.7,
        maxTokens: Int = 2048
    ): String {
        val messages = listOf(
            JSONObject.of("role", "system", "content", systemPrompt),
            JSONObject.of("role", "user", "content", userPrompt)
        )
        return chat(messages, model, temperature, maxTokens)
    }

    /**
     * 多轮对话：传入完整的 messages 列表。
     *
     * @param messages 形如 `[{"role": "user", "content": "..."}, ...]` 的 JSONObject 列表
     * @return 模型回复的文本内容
     */
    fun chat(
        messages: List<JSONObject>,
        model: String = DEFAULT_MODEL,
        temperature: Double = 0.7,
        maxTokens: Int = 2048
    ): String {
        val apiKey = ConfigManager.getConfig().deepSeekApiKey
        if (apiKey.isEmpty()) {
            throw IOException("DeepSeek API key not configured (kanade.json#deepSeekApiKey)")
        }

        val payload = JSONObject().apply {
            this["model"] = model
            this["messages"] = messages
            this["temperature"] = temperature
            this["max_tokens"] = maxTokens
        }

        val request = HttpRequest.newBuilder()
            .uri(URI.create(API_URL))
            .timeout(Duration.ofSeconds(60))
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(payload.toJSONString()))
            .build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        val body = response.body() ?: throw IOException("Empty response from DeepSeek API")
        if (response.statusCode() !in 200..299) {
            throw IOException("DeepSeek API error: ${response.statusCode()} $body")
        }

        val json = JSON.parseObject(body)
        val choices = json.getJSONArray("choices")
            ?: throw IOException("DeepSeek response missing 'choices': $body")
        if (choices.isEmpty()) {
            throw IOException("DeepSeek response 'choices' is empty: $body")
        }
        val message = choices.getJSONObject(0).getJSONObject("message")
            ?: throw IOException("DeepSeek response missing 'message': $body")
        return message.getString("content") ?: ""
    }
}
