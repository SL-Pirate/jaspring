import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    java
    id("org.jetbrains.intellij.platform") version "2.19.0"
}

group = "dev.isira.jaspring"
version = "0.1.0"

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
            intellijIdea("2026.2.3")
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
            untilBuild = "262.*"
        }
    }
    buildSearchableOptions = false
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

tasks.test {
    maxHeapSize = "2g"
    // Prove the plugin works with Java/XML alone, even when the development IDE bundles Angular.
    systemProperty("idea.load.plugins.id", "dev.isira.jaspring,com.intellij.java")
}
