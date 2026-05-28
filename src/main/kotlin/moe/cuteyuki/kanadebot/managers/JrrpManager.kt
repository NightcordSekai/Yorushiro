package moe.cuteyuki.kanadebot.managers

import com.alibaba.fastjson2.JSON
import com.alibaba.fastjson2.JSONObject
import com.alibaba.fastjson2.JSONWriter
import java.io.File
import java.nio.file.Paths
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

/**
 * 今日人品 (jrrp) 持久化管理器。
 *
 * 设计要点：
 * - **人品值是全局的**：每个用户每个自然日只会生成一次随机值 [0, 100]，
 *   同一个人在不同群看到的当日人品相同。
 * - **排行榜按群隔离**：每个群只展示当天在「该群」执行过 `.jrrp` 的用户，
 *   其它群的成员不会出现。
 *
 * 持久化文件（位于工作目录）：
 * - `jrrp.json`：全局 `{ userId -> {date, value} }`
 * - `jrrp_groups.json`：群参与表 `{ groupId -> { userId -> date } }`
 */
object JrrpManager {

    private const val USER_FILE = "jrrp.json"
    private const val GROUP_FILE = "jrrp_groups.json"

    /** userId -> 当日记录 */
    private val userStore = ConcurrentHashMap<Long, Entry>()

    /** groupId -> (userId -> 该用户最后一次在该群参与 jrrp 的日期) */
    private val groupStore = ConcurrentHashMap<Long, ConcurrentHashMap<Long, String>>()

    private lateinit var userFile: File
    private lateinit var groupFile: File

    data class Entry(val date: String, val value: Int)

    data class BoardItem(val userId: Long, val value: Int)

    fun initialize() {
        val workingDir = Paths.get("").toAbsolutePath().toFile()
        userFile = File(workingDir, USER_FILE)
        groupFile = File(workingDir, GROUP_FILE)

        if (userFile.exists()) {
            runCatching { loadUsers() }.onFailure {
                System.err.println("[JrrpManager] 读取 $USER_FILE 失败: ${it.message}")
            }
        }
        if (groupFile.exists()) {
            runCatching { loadGroups() }.onFailure {
                System.err.println("[JrrpManager] 读取 $GROUP_FILE 失败: ${it.message}")
            }
        }
    }

    /**
     * 查询/生成用户今日人品，并把该用户登记到 [groupId] 的当日参与表里。
     * 当天首次查询会生成新值并立即落盘。
     */
    @Synchronized
    fun getToday(groupId: Long, userId: Long): Int {
        val today = LocalDate.now().toString()

        val cached = userStore[userId]
        val value = if (cached != null && cached.date == today) {
            cached.value
        } else {
            val v = Random.nextInt(0, 101)
            userStore[userId] = Entry(today, v)
            runCatching { saveUsers() }.onFailure {
                System.err.println("[JrrpManager] 写入 $USER_FILE 失败: ${it.message}")
            }
            v
        }

        // 群参与登记
        val members = groupStore.computeIfAbsent(groupId) { ConcurrentHashMap() }
        val previous = members[userId]
        if (previous != today) {
            members[userId] = today
            runCatching { saveGroups() }.onFailure {
                System.err.println("[JrrpManager] 写入 $GROUP_FILE 失败: ${it.message}")
            }
        }

        return value
    }

    /**
     * 获取指定群当日的人品排行榜。仅包含「今天」在该群执行过 `.jrrp` 的用户。
     * 排序：人品值降序，相同值按 userId 升序稳定排序。
     */
    fun getTodayBoard(groupId: Long): List<BoardItem> {
        val today = LocalDate.now().toString()
        val members = groupStore[groupId] ?: return emptyList()
        return members.entries.asSequence()
            .filter { it.value == today }
            .mapNotNull { (uid, _) ->
                val entry = userStore[uid] ?: return@mapNotNull null
                if (entry.date != today) return@mapNotNull null
                BoardItem(uid, entry.value)
            }
            .sortedWith(compareByDescending<BoardItem> { it.value }.thenBy { it.userId })
            .toList()
    }

    private fun loadUsers() {
        val json = JSON.parseObject(userFile.readText()) ?: return
        userStore.clear()
        for ((key, raw) in json) {
            val uid = key?.toLongOrNull() ?: continue
            val obj = raw as? JSONObject ?: continue
            val date = obj.getString("date") ?: continue
            val value = obj.getIntValue("value")
            userStore[uid] = Entry(date, value)
        }
    }

    private fun saveUsers() {
        val json = JSONObject()
        for ((uid, entry) in userStore) {
            json[uid.toString()] = JSONObject().apply {
                this["date"] = entry.date
                this["value"] = entry.value
            }
        }
        userFile.writeText(json.toJSONString(JSONWriter.Feature.PrettyFormat))
    }

    private fun loadGroups() {
        val json = JSON.parseObject(groupFile.readText()) ?: return
        groupStore.clear()
        for ((groupKey, raw) in json) {
            val gid = groupKey?.toLongOrNull() ?: continue
            val members = raw as? JSONObject ?: continue
            val map = ConcurrentHashMap<Long, String>()
            for ((userKey, dateRaw) in members) {
                val uid = userKey?.toLongOrNull() ?: continue
                val date = dateRaw as? String ?: continue
                map[uid] = date
            }
            groupStore[gid] = map
        }
    }

    private fun saveGroups() {
        val json = JSONObject()
        for ((gid, members) in groupStore) {
            val sub = JSONObject()
            for ((uid, date) in members) {
                sub[uid.toString()] = date
            }
            json[gid.toString()] = sub
        }
        groupFile.writeText(json.toJSONString(JSONWriter.Feature.PrettyFormat))
    }
}
