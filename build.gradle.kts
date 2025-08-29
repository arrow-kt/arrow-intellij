import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.models.ProductRelease
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.2.10"
    id("org.jetbrains.intellij.platform") version "2.7.2"
}

group = "io.arrow-kt"
version = "0.3.1"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        intellijIdeaCommunity("2025.1")
        pluginVerifier()
        bundledPlugin("com.intellij.java")
        bundledPlugin("org.jetbrains.kotlin")
    }
}

object Supported {
    const val sinceBuild = "243"
    const val untilBuild = "252.*"
}

intellijPlatform {
    buildSearchableOptions = false

    pluginVerification {
        ides {
            recommended()
            select {
                types = listOf(IntelliJPlatformType.IntellijIdeaCommunity, IntelliJPlatformType.AndroidStudio)
                channels = listOf(ProductRelease.Channel.RELEASE, ProductRelease.Channel.BETA, ProductRelease.Channel.EAP)
                sinceBuild = Supported.sinceBuild
                untilBuild = Supported.untilBuild
            }
        }
    }
}

tasks {
    // Set the JVM compatibility versions
    withType<JavaCompile> {
        sourceCompatibility = "21"
        targetCompatibility = "21"
    }
    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        compilerOptions.jvmTarget.set(JvmTarget.JVM_21)
    }

    patchPluginXml {
        sinceBuild = Supported.sinceBuild
        untilBuild = Supported.untilBuild
    }

    signPlugin {
        certificateChain = System.getenv("CERTIFICATE_CHAIN")
        privateKey = System.getenv("SIGNING_KEY")
        password = System.getenv("SIGNING_KEY_PASSPHRASE")
    }

    publishPlugin {
        token = System.getenv("PUBLISH_TOKEN")
    }
}
