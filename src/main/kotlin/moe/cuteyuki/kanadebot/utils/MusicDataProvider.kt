package moe.cuteyuki.kanadebot.utils

import com.alibaba.fastjson2.JSON
import com.alibaba.fastjson2.JSONArray
import com.alibaba.fastjson2.JSONObject
import moe.cuteyuki.kanadebot.managers.ResourceManager
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * 从水鱼查分器获取歌曲定数数据
 *
 * 使用 API: GET https://www.diving-fish.com/api/maimaidxprober/music_data
 * 返回所有歌曲的定数信息，缓存在内存中
 *
 * 同时支持将数据持久化到 [ResourceManager.dataCacheFolder]/music_data.json，
 * 以便在应用重启后快速恢复，无需重新请求 API。
 */
object MusicDataProvider {

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private const val MUSIC_DATA_URL = "https://www.diving-fish.com/api/maimaidxprober/music_data"

    /** 本地缓存文件名 */
    private const val CACHE_FILE_NAME = "music_data.json"

    // 缓存: musicId -> (levelIndex -> ds)
    private val dsCache = ConcurrentHashMap<Int, Map<Int, Double>>()

    // 缓存: musicId -> title
    private val titleCache = ConcurrentHashMap<Int, String>()

    // 缓存是否已加载
    @Volatile
    private var loaded = false

    // 缓存过期时间（5分钟）
    private var lastLoadTime = 0L
    private const val CACHE_TTL_MS = 5 * 60 * 1000L

    /**
     * 获取指定歌曲指定难度的定数
     * @param musicId 歌曲 ID
     * @param level 难度等级（0=Basic, 1=Advanced, 2=Expert, 3=Master, 4=Re:Master）
     * @return 定数值，如果找不到则返回 0.0
     */
    fun getDs(musicId: Int, level: Int): Double {
        ensureLoaded()

        val levelMap = dsCache[musicId] ?: return 0.0
        return levelMap[level] ?: 0.0
    }

    /**
     * 获取指定歌曲的标题
     * @param musicId 歌曲 ID
     * @return 歌曲标题，如果找不到则返回 "Unknown"
     */
    fun getTitle(musicId: Int): String {
        ensureLoaded()
        return titleCache[musicId] ?: "Unknown"
    }

    /**
     * 获取指定歌曲的所有难度定数
     * @param musicId 歌曲 ID（字符串形式）
     * @return 定数数组（索引 0-4 对应 Basic, Advanced, Expert, Master, Re:Master）
     * @throws IOException 如果找不到该歌曲
     */
    fun findDS(musicId: String): Array<String> {
        ensureLoaded()
        val id = musicId.toIntOrNull() ?: throw IOException("Invalid music ID: $musicId")
        val levelMap = dsCache[id] ?: throw IOException("Music ID not found: $musicId")
        return (0..4).map { level ->
            (levelMap[level] ?: 0.0).toString()
        }.toTypedArray()
    }

    /**
     * 确保定数缓存已加载
     */
    private fun ensureLoaded() {
        val now = System.currentTimeMillis()
        if (loaded && (now - lastLoadTime) < CACHE_TTL_MS) return

        synchronized(this) {
            if (loaded && (System.currentTimeMillis() - lastLoadTime) < CACHE_TTL_MS) return

            try {
                // 优先尝试从本地文件加载
                if (!loadFromLocalFile()) {
                    // 本地文件不存在或无效，从网络加载
                    loadMusicData()
                }
                loaded = true
                lastLoadTime = System.currentTimeMillis()
                println("[MusicDataProvider] 定数数据加载成功，共 ${dsCache.size} 首歌曲")
            } catch (e: Exception) {
                System.err.println("[MusicDataProvider] 加载定数数据失败: ${e.message}")
                // 如果之前有缓存数据，继续使用旧缓存
                if (dsCache.isNotEmpty()) {
                    loaded = true
                    lastLoadTime = now
                }
            }
        }
    }

    /**
     * 从本地缓存文件 [ResourceManager.dataCacheFolder]/music_data.json 加载数据
     * @return true 如果成功从本地加载，false 否则
     */
    private fun loadFromLocalFile(): Boolean {
        val cacheFile = getCacheFile()
        if (!cacheFile.exists()) {
            println("[MusicDataProvider] 本地缓存文件不存在: ${cacheFile.path}")
            return false
        }

        return try {
            val content = Files.readString(cacheFile.toPath())
            val musicArray = JSON.parseArray(content)
            if (musicArray.isEmpty()) {
                println("[MusicDataProvider] 本地缓存文件为空")
                return false
            }

            parseMusicData(musicArray)
            println("[MusicDataProvider] 从本地缓存加载成功: ${cacheFile.path}")
            true
        } catch (e: Exception) {
            System.err.println("[MusicDataProvider] 本地缓存文件读取失败: ${e.message}")
            false
        }
    }

    /**
     * 将当前内存中的数据保存到本地缓存文件
     */
    private fun saveToLocalFile() {
        try {
            val cacheFile = getCacheFile()
            val musicArray = JSONArray()

            for ((musicId, levelMap) in dsCache) {
                val music = JSONObject()
                music["id"] = musicId
                music["title"] = titleCache[musicId] ?: "Unknown"

                val dsArray = JSONArray()
                // dsArray 需要按 levelIndex 顺序填充，最大索引为 4 (Re:Master)
                for (i in 0..4) {
                    dsArray.add(levelMap[i] ?: 0.0)
                }
                music["ds"] = dsArray
                musicArray.add(music)
            }

            Files.writeString(cacheFile.toPath(), musicArray.toJSONString())
            println("[MusicDataProvider] 定数数据已保存到本地缓存: ${cacheFile.path}")
        } catch (e: Exception) {
            System.err.println("[MusicDataProvider] 保存本地缓存失败: ${e.message}")
        }
    }

    /**
     * 获取本地缓存文件对象
     */
    private fun getCacheFile(): File {
        return File(ResourceManager.dataCacheFolder, CACHE_FILE_NAME)
    }

    /**
     * 从水鱼查分器加载所有歌曲定数数据
     */
    private fun loadMusicData() {
        val request = Request.Builder()
            .url(MUSIC_DATA_URL)
            .header("User-Agent", "KanadeBot/1.0")
            .get()
            .build()

        val responseStr: String
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code}")
            }
            responseStr = response.body?.string() ?: throw IOException("Empty response")
        }

        val musicArray = JSON.parseArray(responseStr)
        parseMusicData(musicArray)

        // 保存到本地文件
        saveToLocalFile()
    }

    /**
     * 解析歌曲数据数组到缓存
     */
    private fun parseMusicData(musicArray: JSONArray) {
        // 清空旧缓存
        dsCache.clear()
        titleCache.clear()

        var parsedCount = 0
        var skippedNoDs = 0

        for (i in 0 until musicArray.size) {
            val music = musicArray.getJSONObject(i)
            val musicId = music.getIntValue("id")
            val title = music.getString("title") ?: "Unknown"
            val dsArray = music.getJSONArray("ds")
            if (dsArray == null || dsArray.isEmpty()) {
                skippedNoDs++
                continue
            }

            // 缓存标题
            titleCache[musicId] = title

            val levelMap = mutableMapOf<Int, Double>()
            for (j in 0 until dsArray.size) {
                val ds = dsArray.getDoubleValue(j)
                if (ds > 0) {
                    levelMap[j] = ds
                }
            }

            if (levelMap.isNotEmpty()) {
                dsCache[musicId] = levelMap
                parsedCount++
            }
        }

        println("[MusicDataProvider] 解析完成: parsedCount=$parsedCount, skippedNoDs=$skippedNoDs")
    }

    /**
     * 强制刷新缓存（从网络重新拉取并保存到本地）
     */
    fun refresh() {
        synchronized(this) {
            loaded = false
            dsCache.clear()
            titleCache.clear()
            try {
                loadMusicData()
                loaded = true
                lastLoadTime = System.currentTimeMillis()
                println("[MusicDataProvider] 强制刷新成功，共 ${dsCache.size} 首歌曲")
            } catch (e: Exception) {
                System.err.println("[MusicDataProvider] 强制刷新失败: ${e.message}")
                // 如果本地文件存在，尝试从本地恢复
                if (!loadFromLocalFile()) {
                    loaded = false
                }
            }
        }
    }
}
