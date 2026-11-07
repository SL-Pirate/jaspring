plugins {
	`java-gradle-plugin`
	alias(libs.plugins.maven.publish)
}

description = "Jaspring component scaffolding, browser builds, TypeScript and JNPM dependencies."

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
	implementation("com.google.code.gson:gson:2.15.0")
	implementation("org.apache.commons:commons-compress:1.28.0")
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
// The compiler and its GraalJS runtime, as the consumer projects in the tests put them on jaspringTypescript.
val typescriptRuntime: Configuration by configurations.creating {
	isCanBeConsumed = false
	isCanBeResolved = true
	attributes.attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage::class, Usage.JAVA_RUNTIME))
}
dependencies {
	typescriptRuntime(project(":jaspring-typescript"))
}
tasks.test {
	dependsOn(coreJar, browserRuntimeJar)
	dependsOn(":jaspring-sample:jnpmInstall")
	systemProperty("jnpm.test.workspace", project(":jaspring-sample").layout.buildDirectory.get().asFile.absolutePath)
	systemProperty("jnpm.test.script", layout.projectDirectory.file("src/test/js/jnpm.test.mjs").asFile.absolutePath)
	inputs.files(fileTree("src/test/js"))
	inputs.files(typescriptRuntime)
	systemProperty("jaspring.coreJar", coreJar.get().archiveFile.get().asFile.absolutePath)
	systemProperty("jaspring.browserRuntimeJar", browserRuntimeJar.get().archiveFile.get().asFile.absolutePath)
	systemProperty("jaspring.wasm", providers.gradleProperty("jaspring.wasm").getOrElse("false"))
	jvmArgumentProviders.add(CommandLineArgumentProvider { listOf("-Djaspring.typescriptClasspath=" + typescriptRuntime.asPath) })
}
