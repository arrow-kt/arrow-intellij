import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.models.ProductRelease
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.4.0"
    id("org.jetbrains.intellij.platform") version "2.11.0"
}

group = "io.arrow-kt"
version = "0.3.4"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        intellijIdea("2025.3.1.1")
        pluginVerifier()
        bundledPlugin("com.intellij.java")
        bundledPlugin("org.jetbrains.kotlin")
    }
}

object Supported {
    const val sinceBuild = "252"
}

intellijPlatform {
    buildSearchableOptions = false

    pluginVerification {
        ides {
            recommended()
            select {
                types = listOf(IntelliJPlatformType.IntellijIdeaCommunity)
                channels = listOf(ProductRelease.Channel.RELEASE, ProductRelease.Channel.RC, ProductRelease.Channel.EAP, ProductRelease.Channel.BETA)
                sinceBuild = Supported.sinceBuild
                untilBuild = "252.*"
            }
            select {
                types = listOf(IntelliJPlatformType.IntellijIdea)
                channels = listOf(ProductRelease.Channel.RELEASE, ProductRelease.Channel.RC, ProductRelease.Channel.EAP, ProductRelease.Channel.BETA)
                sinceBuild = "253"
                untilBuild = provider { null }
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
        sinceBuild.set(Supported.sinceBuild)
        untilBuild.set(provider { null })
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
