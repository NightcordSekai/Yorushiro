package moe.cuteyuki.kanadebot.utils

import java.time.LocalTime
import java.time.format.DateTimeFormatter

object Logger {
    fun log(info: String?, type: LogType) {
        val now = LocalTime.now()
        val formatter = DateTimeFormatter.ofPattern("HH:mm:ss")
        println("[" + now.format(formatter) + "]" + " [" + type.name + "] " + info)
    }

    enum class LogType {
        ERROR, INFO, WARMING, DEBUG
    }
}