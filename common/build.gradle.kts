plugins {
    id("buildsrc.convention.kotlin-jvm")
    alias(libs.plugins.kotlinPluginSerialization)
}

group = "org.tonproduction"
version = "unspecified"

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.bundles.kotlinxEcosystem)

    // Exposed ORM for shared database models
    implementation(libs.bundles.exposed)
    implementation(libs.sqliteJdbc)
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(21)
}