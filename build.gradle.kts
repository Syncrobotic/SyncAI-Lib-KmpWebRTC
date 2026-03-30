import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

// Load local.properties for credentials (gpr.user, gpr.key)
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

plugins {
    alias(webrtcLibs.plugins.kotlinMultiplatform)
    alias(webrtcLibs.plugins.androidLibrary)
    alias(webrtcLibs.plugins.composeMultiplatform)
    alias(webrtcLibs.plugins.composeCompiler)
    alias(webrtcLibs.plugins.kotlinCocoapods)
    alias(webrtcLibs.plugins.kotlinSerialization)
    `maven-publish`
}

group = "com.syncrobotic"
version = project.findProperty("version") as String? ?: "1.0.0-SNAPSHOT"

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
        publishLibraryVariants("release")
    }

    // iOS targets with CocoaPods
    iosArm64()
    iosSimulatorArm64()

    cocoapods {
        summary = "Kotlin Multiplatform WebRTC Client SDK"
        homepage = "https://github.com/Syncrobotic/SyncAI-Lib-KmpWebRTC"
        version = "1.0.0"
        ios.deploymentTarget = "15.0"
        
        framework {
            baseName = "KotlinWebRTCClient"
            isStatic = true
        }
        
        // GoogleWebRTC for iOS WebRTC support
        // ⚠️ IMPORTANT: GoogleWebRTC does NOT support iOS Simulator!
        pod("GoogleWebRTC") {
            version = "1.1.31999"
            moduleName = "WebRTC"
            extraOpts = listOf("-compiler-option", "-fmodules")
        }
    }
    
    jvm()
    
    js {
        browser()
        binaries.library()
    }
    
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
        binaries.library()
    }
    
    sourceSets {
        androidMain.dependencies {
            implementation(compose.preview)
            implementation(webrtcLibs.androidx.activity.compose)
            // Ktor Client engine for Android
            implementation(webrtcLibs.ktor.client.okhttp)
            // WebRTC for Android
            implementation(webrtcLibs.webrtc.android)
        }
        
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(webrtcLibs.androidx.lifecycle.viewmodelCompose)
            implementation(webrtcLibs.androidx.lifecycle.runtimeCompose)
            // Ktor Client for WebRTC signaling (WHEP/WHIP)
            implementation(webrtcLibs.ktor.client.core)
            implementation(webrtcLibs.ktor.client.content.negotiation)
            implementation(webrtcLibs.ktor.serialization.json)
            // Ktor WebSocket for custom signaling
            implementation(webrtcLibs.ktor.client.websockets)
        }
        
        commonTest.dependencies {
            implementation(webrtcLibs.kotlin.test)
            implementation(webrtcLibs.kotlinx.coroutines.test)
        }
        
        val jvmTest by getting {
            dependencies {
                implementation(webrtcLibs.kotlin.testJunit)
                implementation(webrtcLibs.ktor.client.mock)
                implementation(webrtcLibs.ktor.client.cio)
                
                // E2E: in-process mock WHEP/WHIP server
                implementation(webrtcLibs.ktor.server.core)
                implementation(webrtcLibs.ktor.server.netty)
                implementation(webrtcLibs.ktor.server.content.negotiation)
            }
        }
        
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(webrtcLibs.kotlinx.coroutinesSwing)
            // Ktor Client engine for JVM
            implementation(webrtcLibs.ktor.client.cio)
            
            // WebRTC - Java bindings for Desktop (macOS/Windows/Linux)
            implementation(webrtcLibs.webrtc.java)
            
            // Add platform-specific native library based on OS
            val osName = System.getProperty("os.name").lowercase()
            val osArch = System.getProperty("os.arch").lowercase()
            
            val nativeClassifier = when {
                osName.contains("mac") && (osArch.contains("aarch64") || osArch.contains("arm")) -> "macos-aarch64"
                osName.contains("mac") -> "macos-x86_64"
                osName.contains("win") -> "windows-x86_64"
                osName.contains("linux") && (osArch.contains("aarch64") || osArch.contains("arm64")) -> "linux-aarch64"
                osName.contains("linux") && osArch.contains("arm") -> "linux-aarch32"
                osName.contains("linux") -> "linux-x86_64"
                else -> null
            }
            
            if (nativeClassifier != null) {
                val webrtcJavaVersion = webrtcLibs.versions.webrtc.java.get()
                runtimeOnly("dev.onvoid.webrtc:webrtc-java:$webrtcJavaVersion:$nativeClassifier@jar")
            }
            
            // Java WebSocket client
            implementation("org.java-websocket:Java-WebSocket:1.5.6")
            
            // SLF4J for logging (webrtc-java dependency)
            implementation(webrtcLibs.slf4j.simple)
        }
        
        iosMain.dependencies {
            // Ktor Client engine for iOS
            implementation(webrtcLibs.ktor.client.darwin)
        }
        
        jsMain.dependencies {
            // Ktor Client engine for JS/Browser
            implementation(webrtcLibs.ktor.client.js)
        }
        
        wasmJsMain.dependencies {
            // Note: Ktor doesn't support wasmJs yet, will use JS interop
        }
    }
}

android {
    namespace = "com.syncrobotic.webrtc"
    compileSdk = webrtcLibs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        minSdk = webrtcLibs.versions.android.minSdk.get().toInt()
    }
    
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

// GitHub Packages publishing configuration
publishing {
    publications {
        // Filter out platforms we don't want to publish
        val excludedPublications = setOf("iosSimulatorArm64", "js", "wasmJs")
        publications.matching { it.name in excludedPublications }.configureEach {
            tasks.withType<PublishToMavenRepository>().matching {
                it.publication == this@configureEach
            }.configureEach { enabled = false }
        }
    }
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/Syncrobotic/SyncAI-Lib-KmpWebRTC")
            credentials {
                username = localProperties.getProperty("gpr.user")
                    ?: System.getenv("GITHUB_ACTOR")
                password = localProperties.getProperty("gpr.key")
                    ?: System.getenv("GITHUB_TOKEN")
            }
        }
    }
}
