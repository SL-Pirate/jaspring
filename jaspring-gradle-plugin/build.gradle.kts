plugins {
	`java-gradle-plugin`
	alias(libs.plugins.maven.publish)
}

description = "Gradle tasks for generating Jaspring Java components and their templates."

gradlePlugin {
	plugins {
		create("jaspring") {
			id = "dev.isira.jaspring"
			implementationClass = "dev.isira.jaspring.gradle.JaspringPlugin"
			displayName = "Jaspring"
			description = project.description
		}
	}
}

dependencies {
	testImplementation(platform(libs.spring.boot.dependencies))
	testImplementation(libs.junit.jupiter)
	testRuntimeOnly(libs.junit.launcher)
}

val coreJar = project(":jaspring-core").tasks.named<Jar>("jar")
tasks.test {
	dependsOn(coreJar)
	systemProperty("jaspring.coreJar", coreJar.get().archiveFile.get().asFile.absolutePath)
}
