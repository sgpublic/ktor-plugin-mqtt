pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google()
    }

    versionCatalogs {
        val kpmqtt by creating {
            from(files("./gradle/kpmqtt.versions.toml"))
        }
    }
}


rootProject.name = "ktor-plugin-mqtt"
include("lib")
