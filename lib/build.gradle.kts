import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(kpmqtt.plugins.kotlin.jvm)
    alias(kpmqtt.plugins.release.github)

    `maven-publish`
    signing
    alias(kpmqtt.plugins.publish.java)
}

group = "io.github.sgpublic"
version = "2.0.1"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_1_8
    withJavadocJar()
    withSourcesJar()
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_1_8
    }
}

dependencies {
    implementation(kpmqtt.kotlin.stdlib)
    implementation(kpmqtt.ktor.server.core)
    api(kpmqtt.mqttv5)
    implementation(kpmqtt.ktor.server.test.host)
    testImplementation(kpmqtt.kotlin.test)
}

fun findEnv(name: String): String {
    return findProperty(name)?.toString()?.takeIf { it.isNotBlank() }
        ?: System.getenv(name.replace(".", "_").uppercase())
}

githubRelease {
    token(findEnv("publishing.github.token"))
    owner = "sgpublic"
    repo = "ktor-plugin-mqtt"
    tagName = "v$version"
    releaseName = "v$version"
    prerelease = version.toString().let { it.contains("alpha") || it.contains("beta") }
    overwrite = true
}
