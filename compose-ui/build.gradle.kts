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
    jvmToolchain(21)
}

compose.desktop {
    application {
        mainClass = "com.claude.agent.ApplicationKt"
    }
}

// Генерация файла с версией сборки
val generateBuildInfo by tasks.registering {
    val outputDir = layout.buildDirectory.dir("generated/kotlin")
    val outputFile = outputDir.get().file("BuildInfo.kt")

    outputs.file(outputFile)

    // ВАЖНО: Делаем задачу up-to-date, если файл уже существует
    // Это предотвращает бесконечную пересборку в continuous mode
    outputs.upToDateWhen { outputFile.asFile.exists() }

    doLast {
        // Генерируем timestamp только если файл не существует
        if (!outputFile.asFile.exists()) {
            val timestamp = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss"))

            outputDir.get().asFile.mkdirs()
            outputFile.asFile.writeText("""
                package com.claude.agent.ui

                object BuildInfo {
                    const val BUILD_TIME = "$timestamp"
                }
            """.trimIndent())
        }
    }
}

// Добавляем сгенерированный файл в jsMain sourceSets
kotlin.sourceSets.named("jsMain") {
    kotlin.srcDir(layout.buildDirectory.dir("generated/kotlin"))
}

// Запускаем генерацию перед компиляцией
tasks.named("compileKotlinJs") {
    dependsOn(generateBuildInfo)
}

// Отключаем кеширование для всех задач сборки, чтобы BuildInfo всегда обновлялся
//tasks.configureEach {
//    outputs.upToDateWhen { false }
//}

// Очищаем webpack кеш и dist перед production сборкой
// Это гарантирует, что BuildInfo.kt всегда попадёт в финальный bundle
val cleanWebpackCache by tasks.registering(Delete::class) {
    delete(layout.buildDirectory.dir("kotlin-webpack"))
    delete(layout.buildDirectory.dir("dist"))
}

// Очищаем проблемные кеши Kotlin/JS перед компиляцией
val cleanKotlinJsCache by tasks.registering(Delete::class) {
    delete(layout.buildDirectory.dir("classes/kotlin/js"))
    delete(layout.buildDirectory.dir("kotlin"))
}

// Очищаем кеши ТОЛЬКО для production сборки
// НЕ очищаем для обычной компиляции, чтобы избежать бесконечной пересборки
tasks.named("jsBrowserProductionWebpack") {
    dependsOn(cleanWebpackCache)
}

tasks.named("jsBrowserDistribution") {
    dependsOn(cleanWebpackCache, cleanKotlinJsCache)
}

// УБРАНО: cleanKotlinJsCache из compileKotlinJs
// Это вызывало бесконечную пересборку в continuous mode

//// Создаём алиас jsBrowserRun для удобства разработки
//// Kotlin/JS создаёт два отдельных таска: jsBrowserDevelopmentRun и jsBrowserProductionRun
//// Этот алиас указывает на development версию для локальной разработки
//tasks.register("jsBrowserRun") {
//    group = "run"
//    description = "Alias for jsBrowserDevelopmentRun (development mode)"
//    dependsOn("jsBrowserDevelopmentRun")
//}
