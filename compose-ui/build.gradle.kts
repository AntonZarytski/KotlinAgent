import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

plugins {
    kotlin("multiplatform")
    alias(libs.plugins.kotlinPluginSerialization)
    alias(libs.plugins.compose)
    alias(libs.plugins.compose.compiler)
}

group = "org.tonproduction"
version = "unspecified"

repositories {
    mavenCentral()
    google()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
}
kotlin {
    js(IR) {
        browser {
            commonWebpackConfig {
                outputFileName = "compose-web.js"
            }
        }
        binaries.executable()
    }

    jvm()

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(libs.kotlinxSerialization)
                implementation(libs.kotlinxCoroutines)
            }
        }

        val jsMain by getting {
            dependencies {
                implementation(compose.html.core)
                implementation(compose.runtime)
                implementation(libs.kotlinxSerialization)
            }
        }

        val jvmMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation(libs.compose.runtime)
                implementation(libs.compose.ui)
                implementation(libs.compose.material)
                implementation(libs.compose.desktop)
                implementation(libs.compose.material.desktop)
                implementation(libs.ktorServerCore)
                implementation(libs.ktorClientCio)
                implementation(libs.ktorClientContentNegotiation)
                implementation(libs.ktorSerializationJson)
            }
        }
    }
}

kotlin {
    jvmToolchain(24)
}

compose.desktop {
    application {
        mainClass = "com.claude.agent.ApplicationKt"
    }
}

// Генерация файла с версией сборки
val generateBuildInfo = tasks.register("generateBuildInfo") {
    // Генерируем в стандартную директорию исходников jsMain
    val outputDir = file("src/jsMain/kotlin/com/claude/agent/ui")
    val outputFile = File(outputDir, "BuildInfo.kt")

    outputs.file(outputFile)
    outputs.upToDateWhen { false }

    doFirst {
        // Создаем директорию перед записью
        outputDir.mkdirs()
    }

    doLast {
        val timestamp = LocalDateTime.now()
            .format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss"))

        outputFile.writeText("""
            package com.claude.agent.ui

            object BuildInfo {
                const val BUILD_TIME = "$timestamp"
            }
        """.trimIndent())

        println("✅ BuildInfo.kt создан с временем: $timestamp")
        println("   Путь: ${outputFile.absolutePath}")
    }
}

// Запускаем генерацию перед компиляцией
tasks.named("compileKotlinJs") {
    dependsOn(generateBuildInfo)
}

// Отключаем кеширование для критических задач сборки, чтобы BuildInfo всегда обновлялся
tasks.matching {
    it.name in listOf(
        "compileKotlinJs",
        "compileProductionExecutableKotlinJs",
        "jsBrowserProductionWebpack",
        "jsBrowserDistribution"
    )
}.configureEach {
    outputs.upToDateWhen { false }
}

// Очищаем только webpack кеш и dist перед production сборкой
// НЕ очищаем Kotlin/JS кеш, чтобы избежать ошибок компиляции
val cleanWebpackCache by tasks.registering(Delete::class) {
    delete(layout.buildDirectory.dir("kotlin-webpack"))
    delete(layout.buildDirectory.dir("dist"))
    delete(layout.buildDirectory.dir("compileSync"))
}

tasks.named("jsBrowserProductionWebpack") {
    dependsOn(cleanWebpackCache)
}

tasks.named("jsBrowserDistribution") {
    dependsOn(cleanWebpackCache)
}

//// Создаём алиас jsBrowserRun для удобства разработки
//// Kotlin/JS создаёт два отдельных таска: jsBrowserDevelopmentRun и jsBrowserProductionRun
//// Этот алиас указывает на development версию для локальной разработки
//tasks.register("jsBrowserRun") {
//    group = "run"
//    description = "Alias for jsBrowserDevelopmentRun (development mode)"
//    dependsOn("jsBrowserDevelopmentRun")
//}
