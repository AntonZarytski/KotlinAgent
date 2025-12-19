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
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(21)
}