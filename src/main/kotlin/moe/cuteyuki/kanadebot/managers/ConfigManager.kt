package moe.cuteyuki.kanadebot.managers

import com.alibaba.fastjson2.JSON
import com.alibaba.fastjson2.JSONObject
import moe.cuteyuki.kanadebot.config.Config
import java.io.File
import java.nio.file.Paths

object ConfigManager {
    private const val CONFIG_FILE_NAME = "kanade.json"

    private lateinit var config: Config
    private lateinit var configFile: File

    /**
     * 初始化配置管理器，从文件中加载配置
     * 如果文件不存在则创建默认配置
     *
     * 启动时还会做一次 schema 同步：若已有的 `kanade.json` 缺少新版本新增的字段，
     * 加载后会立即重写一次，把缺失字段以默认值补全（已存在的值不会被覆盖）。
     */
    fun initialize() {
        val workingDir = Paths.get("").toAbsolutePath().toString()
        configFile = File(workingDir, CONFIG_FILE_NAME)

        if (configFile.exists()) {
            val needsRewrite = loadConfig()
            if (needsRewrite) {
                saveConfig()
                println("[ConfigManager] $CONFIG_FILE_NAME 已补全新字段。")
            }
        } else {
            createDefaultConfig()
        }
    }

    /**
     * 从 JSON 文件加载配置。
     * @return 是否检测到 schema 缺失（缺字段时由调用方负责重写）
     */
    private fun loadConfig(): Boolean {
        val content = configFile.readText()
        val json = JSON.parseObject(content) ?: JSONObject()

        val expectedKeys = setOf(
            "deepSeekApiKey", "githubToken", "botAdmins",
            "keychipId", "aimeSalt", "aesIv", "aesKey",
            "titleServerUrl", "aimeUrl",
            "obfuscateParam", "apiVersion", "clientId",
            "regionId", "regionName", "placeId", "placeName"
        )
        val missing = expectedKeys - json.keys

        val adminsArr = json.getJSONArray("botAdmins")
        val admins = if (adminsArr != null) {
            (0 until adminsArr.size).mapNotNull { i ->
                when (val v = adminsArr[i]) {
                    is Number -> v.toLong()
                    is String -> v.toLongOrNull()
                    else -> null
                }
            }
        } else emptyList()

        // 去除 titleServerUrl 末尾的斜杠，防止拼接 API hash 时出现双斜杠
        var rawUrl = json.getString("titleServerUrl") ?: ""
        if (rawUrl.endsWith("/")) {
            rawUrl = rawUrl.removeSuffix("/")
        }

        config = Config(
            deepSeekApiKey = json.getString("deepSeekApiKey") ?: "",
            githubToken = json.getString("githubToken") ?: "",
            botAdmins = admins,
            keychipId = json.getString("keychipId") ?: "",
            aimeSalt = json.getString("aimeSalt") ?: "",
            aesIv = json.getString("aesIv") ?: "",
            aesKey = json.getString("aesKey") ?: "",
            titleServerUrl = rawUrl,
            aimeUrl = json.getString("aimeUrl") ?: "",
            obfuscateParam = json.getString("obfuscateParam") ?: "LatuAa81",
            apiVersion = json.getString("apiVersion") ?: "1.53",
            clientId = json.getString("clientId") ?: "",
            regionId = json.getIntValue("regionId"),
            regionName = json.getString("regionName") ?: "",
            placeId = json.getIntValue("placeId"),
            placeName = json.getString("placeName") ?: "",
        )

        return missing.isNotEmpty()
    }

    /**
     * 创建默认配置文件并保存
     */
    private fun createDefaultConfig() {
        config = Config()
        saveConfig()
        println("Created default config file: ${configFile.absolutePath}")
    }

    /**
     * 将当前配置保存到 JSON 文件
     */
    fun saveConfig() {
        val json = JSONObject()
        json["deepSeekApiKey"] = config.deepSeekApiKey
        json["githubToken"] = config.githubToken
        json["botAdmins"] = config.botAdmins
        json["keychipId"] = config.keychipId
        json["aimeSalt"] = config.aimeSalt
        json["aesIv"] = config.aesIv
        json["aesKey"] = config.aesKey
        json["titleServerUrl"] = config.titleServerUrl
        json["aimeUrl"] = config.aimeUrl
        json["obfuscateParam"] = config.obfuscateParam
        json["apiVersion"] = config.apiVersion
        json["clientId"] = config.clientId
        json["regionId"] = config.regionId
        json["regionName"] = config.regionName
        json["placeId"] = config.placeId
        json["placeName"] = config.placeName

        configFile.writeText(json.toJSONString(com.alibaba.fastjson2.JSONWriter.Feature.PrettyFormat))
    }

    /**
     * 获取当前配置
     */
    fun getConfig(): Config {
        if (!::config.isInitialized) {
            initialize()
        }
        return config
    }

    /**
     * 更新配置并保存到文件
     */
    fun updateConfig(newConfig: Config) {
        config = newConfig
        saveConfig()
    }
}
