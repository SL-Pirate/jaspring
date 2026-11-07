plugins {
	`java-gradle-plugin`
}

repositories { mavenCentral() }

dependencies {
	implementation("com.google.code.gson:gson:2.15.0")
	implementation("org.apache.commons:commons-compress:1.28.0")
}

java {
	toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

// Build the published plugin's sources before configuring the sample, without a local publication.
sourceSets.main {
	java.setSrcDirs(listOf("../jaspring-gradle-plugin/src/main/java"))
	resources.srcDir("../jaspring-gradle-plugin/src/main/resources")
	resources.include("jaspring/jnpm/**")
}

gradlePlugin {
	plugins {
		create("jaspring") {
			id = "dev.isira.jaspring"
			implementationClass = "dev.isira.jaspring.gradle.JaspringPlugin"
		}
	}
}
