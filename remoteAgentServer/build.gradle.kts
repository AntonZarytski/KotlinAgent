plugins {
    // Apply the shared build logic from a convention plugin.
    // The shared code is located in `buildSrc/src/main/kotlin/kotlin-jvm.gradle.kts`.
    id("buildsrc.convention.kotlin-jvm")

    // Apply the Application plugin to add support for building an executable JVM application.
    application

    // Kotlin serialization plugin
    alias(libs.plugins.kotlinPluginSerialization)
}

dependencies {
    // Project "app" depends on project "utils". (Project paths are separated with ":", so ":utils" refers to the top-level "utils" project.)
    implementation(project(":common"))
    // Note: compose-ui собирается отдельно как JS приложение (см. tasks.named("run"))

    // Ktor Server
    implementation(libs.bundles.ktorServer)

    // Ktor Client (для Anthropic API)
    implementation(libs.bundles.ktorClient)

    // Database (Exposed + SQLite)
    implementation(libs.bundles.exposed)
    implementation(libs.sqliteJdbc)

    // Logging
    implementation(libs.logback)

    // Configuration (.env)
    implementation(libs.dotenv)
}

application {
    // Define the Fully Qualified Name for the application main class
    mainClass = "com.claude.agent.ApplicationServerKt"
}

// Настройка JAR манифеста для запуска через java -jar
tasks.jar {
    manifest {
        attributes["Main-Class"] = "com.claude.agent.ApplicationServerKt"
    }
    // Включаем все зависимости в JAR (fat JAR)
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
}

// Собирать Compose UI перед запуском сервера (без кеша для dev)
tasks.named("run") {
    dependsOn(":compose-ui:jsBrowserDistribution")
}

// ВАЖНО: Собирать UI даже при запуске из IntelliJ IDEA
tasks.named("classes") {
    dependsOn(":compose-ui:jsBrowserDistribution")
}

// Отключить кеширование для compose-ui задач при разработке
gradle.taskGraph.whenReady {
    allTasks
        .filter { it.project.name == "compose-ui" }
        .forEach {
            it.outputs.upToDateWhen { false }
        }
}
