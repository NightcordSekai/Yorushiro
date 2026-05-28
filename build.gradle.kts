import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "2.1.21"
    kotlin("plugin.spring") version "2.1.21"
    id("org.springframework.boot") version "4.0.6"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "moe.cuteyuki"
version = "1.0-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    // Spring Boot Starters (自带 Jackson 3 / tools.jackson)
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-websocket")

    // Jackson 2 annotations - 需要 >= 2.21 以提供 Jackson 3 引用的 com.fasterxml.jackson.annotation.JsonSerializeAs
    implementation("com.fasterxml.jackson.core:jackson-annotations:2.21")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.21.2")

    // Kotlin
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlin:kotlin-stdlib")

    // Shiro framework (依赖 Jackson 2 / com.fasterxml.jackson, 且要求 Spring Boot 4.x)
    implementation("com.mikuac:shiro:2.5.4")

    // Fastjson
    implementation("com.alibaba.fastjson2:fastjson2:2.0.57")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")

    // ZXing - QR Code detection & decoding
    implementation("com.google.zxing:javase:3.5.3")

    // OkHttp
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}

tasks.test {
    enabled = false
}

// 本地预览渲染器输出。运行：./gradlew runPreview
tasks.register<JavaExec>("runPreview") {
    group = "application"
    description = "Render JrrpBoardRenderer preview PNGs into the project root."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("moe.cuteyuki.kanadebot.utils.TestImageBuilderKt")
    standardOutput = System.out
    errorOutput = System.err
}

// 单独预览 GitHub commit 卡片。运行：./gradlew runCommitPreview [--args="owner/repo"]
tasks.register<JavaExec>("runCommitPreview") {
    group = "application"
    description = "Render GitHubCommitInfoRenderer preview PNGs (mock or live repo)."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("moe.cuteyuki.kanadebot.utils.TestGitHubCommitInfoRendererKt")
    standardOutput = System.out
    errorOutput = System.err
}

// 单独预览 GitHub 仓库信息卡。运行：./gradlew runGithubInfoPreview [--args="owner/repo"]
tasks.register<JavaExec>("runGithubInfoPreview") {
    group = "application"
    description = "Render GitHubRepoInfoRenderer preview PNG (mock or live repo)."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("moe.cuteyuki.kanadebot.utils.TestGithubInfoRendererKt")
    standardOutput = System.out
    errorOutput = System.err
}

// 单独预览历史上的今天渲染器。运行：./gradlew runHistoryPreview
tasks.register<JavaExec>("runHistoryPreview") {
    group = "application"
    description = "Render HistoryRenderer preview PNG into the project root."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("moe.cuteyuki.kanadebot.utils.TestHistoryRendererKt")
    standardOutput = System.out
    errorOutput = System.err
}

// 端到端验证 ConfigManager 的 schema 自动补全。运行：./gradlew verifyConfigSchema
tasks.register<JavaExec>("verifyConfigSchema") {
    group = "verification"
    description = "Verify ConfigManager auto-fills missing keys in legacy kanade.json."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("moe.cuteyuki.kanadebot.managers.ConfigManagerCheckKt")
    workingDir = layout.buildDirectory.dir("config-check").get().asFile.also { it.mkdirs() }
    standardOutput = System.out
    errorOutput = System.err
}

tasks.bootJar {
    enabled = true
    mainClass.set("moe.cuteyuki.kanadebot.KanadeBotApplicationKt")
}

tasks.jar {
    enabled = true
    archiveClassifier.set("plain")
}

tasks.withType<KotlinCompile> {
    compilerOptions {
        freeCompilerArgs.add("-Xjsr305=strict")
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}
