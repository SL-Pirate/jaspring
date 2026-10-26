plugins {
	`java-gradle-plugin`
}

java {
	toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

// Build the published plugin's sources before configuring the sample, without a local publication.
sourceSets.main {
	java.setSrcDirs(listOf("../jaspring-gradle-plugin/src/main/java"))
}

gradlePlugin {
	plugins {
		create("jaspring") {
			id = "dev.isira.jaspring"
			implementationClass = "dev.isira.jaspring.gradle.JaspringPlugin"
		}
	}
}
