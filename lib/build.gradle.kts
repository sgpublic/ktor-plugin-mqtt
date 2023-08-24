group = "com.github.traxterz"
version = "0.1.0-SNAPSHOT"

val ktor_version: String by project

val paho_mqtt_client_version: String by project

val sourcesJar by tasks.registering(Jar::class) {
    archiveClassifier.set("sources")
    from(sourceSets["main"].allSource)
}

allprojects {
    repositories {
        mavenCentral()
        maven { url = uri("https://plugins.gradle.org/m2/") }
        maven { url = uri("https://maven.pkg.jetbrains.space/public/p/ktor/eap") }
        maven { url = uri("https://maven.pkg.jetbrains.space/kotlin/p/kotlin/dev") }
    }
}

buildscript {
    repositories { maven { setUrl("https://plugins.gradle.org/m2/") } }
    dependencies {
        classpath(kotlin("gradle-plugin", version = "1.9.0"))
    }
}

plugins {
    kotlin("jvm") version "1.9.0"
    id("java-library")
    id("maven-publish")
    id("org.jlleitschuh.gradle.ktlint") version "11.5.1"
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation("io.ktor:ktor-server-core:$ktor_version")
    api("org.eclipse.paho:org.eclipse.paho.mqttv5.client:$paho_mqtt_client_version")
    testImplementation("io.ktor:ktor-server-test-host:$ktor_version")
    testImplementation(kotlin("test"))
}

publishing {
    (publications) {
        register("ktor-plugin-mqtt", MavenPublication::class) {
            groupId = "com.github.traxterz"
            artifactId = "ktor-plugin-mqtt"
            from(components["java"])
            artifact(sourcesJar)
        }
    }
}

ktlint {
    version.set("0.43.0")
    ignoreFailures.set(true)
}

// configure the Gradle JAR task to output all source file
tasks.jar {
    from(sourceSets["main"].allSource)
}

tasks {
    test { useJUnitPlatform() }
    check { dependsOn(test) }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs = listOf("-Xjsr305=strict", "-Xopt-in=kotlin.RequiresOptIn")
        jvmTarget = "17"
        languageVersion = "1.9"
        apiVersion = "1.9"
    }
}