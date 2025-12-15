plugins {
    kotlin("jvm")
    alias(libs.plugins.kotlinPluginSerialization)
    alias(libs.plugins.compose)
    alias(libs.plugins.compose.compiler)
}

group = "org.tonproduction"
version = "unspecified"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(24)
}

compose.desktop {
    application {
        mainClass = "com.claude.agent.ApplicationKt"
    }
}

dependencies {
    implementation(libs.compose.runtime)
    implementation(libs.compose.ui)
    implementation(libs.compose.material)
    implementation(libs.compose.desktop)
    implementation(libs.compose.material.desktop)
    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinxSerialization)
    implementation(libs.kotlinxCoroutines)
    implementation(libs.ktorServerCore)
    implementation(libs.ktorClientCio)
    implementation(libs.ktorClientContentNegotiation)
    implementation(libs.ktorSerializationJson)
}
