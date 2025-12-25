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
val generateBuildInfo by tasks.registering {
    val outputDir = layout.buildDirectory.dir("generated/kotlin")
    val outputFile = outputDir.get().file("BuildInfo.kt")

    outputs.file(outputFile)

    doLast {
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

// Добавляем сгенерированный файл в jsMain sourceSets
kotlin.sourceSets.named("jsMain") {
    kotlin.srcDir(layout.buildDirectory.dir("generated/kotlin"))
}

// Запускаем генерацию перед компиляцией
tasks.named("compileKotlinJs") {
    dependsOn(generateBuildInfo)
}
