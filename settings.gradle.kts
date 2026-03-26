rootProject.name = "syncai-lib-kmpwebrtc"

pluginManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        maven {
            url = uri("https://jitpack.io")
        }
        maven {
            url = uri("https://maven.nicokruk.com")
        }
    }

    // Define webrtcLibs catalog for standalone builds.
    // When included as a submodule, the parent project's settings.gradle.kts
    // defines this catalog instead, and this block is ignored.
    versionCatalogs {
        create("webrtcLibs") {
            from(files("gradle/libs.versions.toml"))
        }
    }
}
