plugins {
    // Apply the shared build logic from a convention plugin
    id("buildsrc.convention.kotlin-jvm")

    // Apply the Application plugin to add support for building an executable JVM application
    application

    // Kotlin serialization plugin
    alias(libs.plugins.kotlinPluginSerialization)
}

dependencies {
    // Зависимости от других модулей проекта
    implementation(project(":remoteAgentServer"))  // Доступ к сервисам (ClaudeClient, PRReviewService, etc.)
    implementation(project(":common"))             // Модели данных (PRInfo, PRReview, etc.)

    // HTTP клиент для инициализации сервисов
    implementation(libs.bundles.ktorClient)

    // Логирование
    implementation(libs.logback)

    // Конфигурация (.env)
    implementation(libs.dotenv)
    
    // Kotlinx ecosystem (coroutines, serialization)
    implementation(libs.bundles.kotlinxEcosystem)
}

application {
    // Define the Fully Qualified Name for the application main class
    // PRReviewCLI - это object с @JvmStatic main методом
    mainClass = "com.claude.agent.cli.PRReviewCLI"
}

// Настройка JAR манифеста для запуска через java -jar
tasks.jar {
    manifest {
        attributes["Main-Class"] = "com.claude.agent.cli.PRReviewCLI"
    }
    // Включаем все зависимости в JAR (fat JAR)
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
}

