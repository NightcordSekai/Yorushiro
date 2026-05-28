package moe.cuteyuki.kanadebot.tasks

import moe.cuteyuki.kanadebot.utils.MusicDataProvider
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * 定时任务：每天 01:00 自动更新歌曲定数数据
 */
@Component
@EnableScheduling
class MusicDataUpdateTask {

    /**
     * 每天凌晨 01:00 (Asia/Shanghai) 执行
     * cron = "秒 分 时 日 月 周"
     */
    @Scheduled(cron = "0 0 1 * * ?", zone = "Asia/Shanghai")
    fun updateMusicData() {
        println("[MusicDataUpdateTask] 定时任务触发：开始更新歌曲定数数据...")
        try {
            MusicDataProvider.refresh()
            println("[MusicDataUpdateTask] 定时更新完成")
        } catch (e: Exception) {
            System.err.println("[MusicDataUpdateTask] 定时更新失败: ${e.message}")
        }
    }
}
