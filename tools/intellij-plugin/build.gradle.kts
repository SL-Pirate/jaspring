import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    java
    id("org.jetbrains.intellij.platform") version "2.20.0"
}

group = "dev.isira.jaspring"
version = "0.1.1"

repositories {
    mavenCentral()
    intellijPlatform { defaultRepositories() }
}

dependencies {
    intellijPlatform {
        val platformPath = providers.gradleProperty("platformPath")
        if (platformPath.isPresent) {
            local(platformPath.get())
        } else {
            // -PplatformVersion=<version> runs the build and tests on another IDE line, e.g. the newest supported one.
            intellijIdea(providers.gradleProperty("platformVersion").getOrElse("2026.2.3"))
        }
        bundledPlugin("com.intellij.java")
        testFramework(TestFrameworkType.Platform)
        testFramework(TestFrameworkType.Plugin.Java)
    }
    testImplementation("junit:junit:4.13.2")
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(25)
}

intellijPlatform {
    pluginConfiguration {
        name = "Jaspring"
        ideaVersion {
            sinceBuild = "262"
            untilBuild = "263.*"
        }
        changeNotes = "<ul><li>0.1.1: IntelliJ IDEA 2026.3 (build 263) support; no deprecated platform API left.</li></ul>"
    }
    // JetBrains' plugin verifier against the newest supported line: ./gradlew -p tools/intellij-plugin verifyPlugin
    pluginVerification {
        ides {
            create(IntelliJPlatformType.IntellijIdea, "2026.3")
        }
    }
    buildSearchableOptions = false
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

tasks.test {
    maxHeapSize = "2g"
    // Prove the plugin works with Java/XML alone, even when the development IDE bundles Angular. Ids unknown to an
    // IDE line are ignored. 2026.3 moved two pieces out of the core into bundled plugins that must be listed: the
    // platform test runner (com.intellij.platform.testRunner, intellij.testRunner.plugin on 2026.2) declares the
    // testFramework extension point Java's run line markers query, and the platform VCS plugin provides the
    // intellij.platform.vcs module the core's own modules depend on; without it the loader drops every plugin that
    // depends on the core, this one included.
    systemProperty("idea.load.plugins.id", "dev.isira.jaspring,com.intellij.java,intellij.testRunner.plugin,com.intellij.platform.testRunner,com.intellij.platform.vcs")
}
