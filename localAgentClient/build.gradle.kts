plugins {
    kotlin("jvm")
    kotlin("plugin.serialization") version "1.9.22"
    application
}

group = "org.tonproduction"
version = "unspecified"

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":common"))
    implementation(libs.ktorClientCore)
    implementation(libs.ktorClientCio)
    implementation(libs.kotlinxSerialization)
    implementation(libs.kotlinxCoroutines)
    implementation(libs.ktorServerWebSockets)

    // Logging
    implementation(libs.logback)
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(21)
}

application {
    // Define the Fully Qualified Name for the application main class
    mainClass = "com.claude.agent.client.ApplicationClientKt"
}

// Настройка JAR манифеста для запуска через java -jar
tasks.jar {
    manifest {
        attributes["Main-Class"] = "com.claude.agent.client.ApplicationClientKt"
    }
    // Включаем все зависимости в JAR (fat JAR)
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
}