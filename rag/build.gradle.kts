plugins {
    kotlin("jvm")
    kotlin("plugin.serialization") version "2.2.20"
    application
}

group = "com.claude.agent"
version = "unspecified"

repositories {
    mavenCentral()
}

dependencies {
    // Shared common module with database models
    implementation(project(":common"))

    // PDF processing (optional - for future PDF support)
    // implementation("org.apache.pdfbox:pdfbox:2.0.30")

    // Ktor Client for Ollama API
    implementation(libs.ktorClientCore)
    implementation(libs.ktorClientCio)
    implementation(libs.ktorClientContentNegotiation)
    implementation(libs.ktorSerializationJson)

    // Kotlinx ecosystem
    implementation(libs.kotlinxSerialization)
    implementation(libs.kotlinxCoroutines)

    // Exposed ORM
    implementation(libs.bundles.exposed)

    // SQLite database
    implementation(libs.sqliteJdbc)

    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(21)
}

application {
    mainClass = "com.clauder.agent.MainKt"
}

// Увеличиваем heap size для обработки больших документов
tasks.named<JavaExec>("run") {
    jvmArgs = listOf(
        "-Xmx2g",           // Максимум 2GB heap
        "-Xms512m"          // Начальный размер 512MB
    )
}