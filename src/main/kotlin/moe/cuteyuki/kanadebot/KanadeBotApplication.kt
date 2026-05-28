package moe.cuteyuki.kanadebot

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class KanadeBotApplication

fun main(args: Array<String>) {
    // 启用 Java2D 硬件加速 pipeline。必须在任何 AWT 类被加载之前设置。
    // - macOS (JDK 17+): Metal pipeline
    // - Linux / Windows: OpenGL pipeline
    val os = System.getProperty("os.name")?.lowercase().orEmpty()
    if ("mac" in os || "darwin" in os) {
        System.setProperty("sun.java2d.metal", "true")
    } else {
        System.setProperty("sun.java2d.opengl", "true")
    }
    // 减少非加速回退路径
    System.setProperty("sun.java2d.noddraw", "true")

    runApplication<KanadeBotApplication>(*args)
}
