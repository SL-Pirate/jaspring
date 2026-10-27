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

// The plugin resolves jaspring-browser-runtime at its own version; buildSrc builds these sources without the
// resource, and the sample declares the project dependency instead.
tasks.processResources {
	val pluginVersion = project.version.toString()
	inputs.property("version", pluginVersion)
	filesMatching("META-INF/jaspring/plugin.properties") { expand("version" to pluginVersion) }
}

val coreJar = project(":jaspring-core").tasks.named<Jar>("jar")
val browserRuntimeJar = project(":jaspring-browser-runtime").tasks.named<Jar>("jar")
tasks.test {
	dependsOn(coreJar, browserRuntimeJar)
	systemProperty("jaspring.coreJar", coreJar.get().archiveFile.get().asFile.absolutePath)
	systemProperty("jaspring.browserRuntimeJar", browserRuntimeJar.get().archiveFile.get().asFile.absolutePath)
	systemProperty("jaspring.wasm", providers.gradleProperty("jaspring.wasm").getOrElse("false"))
}
