import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.models.ProductRelease
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.4.0"
    id("org.jetbrains.intellij.platform") version "2.16.0"
}

group = "io.arrow-kt"
version = "0.3.5"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        intellijIdea("2026.1.2")
        pluginVerifier()
        bundledPlugin("com.intellij.java")
        bundledPlugin("org.jetbrains.kotlin")
    }
}

object Supported {
    const val sinceBuild = "261"
}

intellijPlatform {
    buildSearchableOptions = false

    pluginVerification {
        ides {
            recommended()
            select {
                types = listOf(IntelliJPlatformType.IntellijIdea)
                channels = listOf(ProductRelease.Channel.RELEASE, ProductRelease.Channel.RC, ProductRelease.Channel.EAP, ProductRelease.Channel.BETA)
                sinceBuild = Supported.sinceBuild
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
