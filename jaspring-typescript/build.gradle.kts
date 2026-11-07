plugins {
	`java-library`
	alias(libs.plugins.maven.publish)
}

description = "The TypeScript compiler on GraalJS (or Node) for Jaspring browser scripts, used by the Gradle plugin and the starter's startup fallback."

dependencies {
	api(libs.graal.polyglot)
	runtimeOnly(libs.graal.js)

	testImplementation(platform(libs.spring.boot.dependencies))
	testImplementation(libs.junit.jupiter)
	testRuntimeOnly(libs.junit.launcher)
}

tasks.test {
	systemProperty("jaspring.versions.typescript", libs.versions.typescript.get())
}

// Timing spike for the bundled compiler on this JVM: `./gradlew :jaspring-typescript:typescriptSpike --args=<dir>`
// with a directory of .ts files; `-Pjaspring.spike.interpreter=true` forces the interpreter.
tasks.register<JavaExec>("typescriptSpike") {
	group = "verification"
	description = "Times bundle load, transpile and type-checked compile of the .ts files in a directory."
	classpath = sourceSets.test.get().runtimeClasspath
	mainClass.set("dev.isira.jaspring.typescript.Spike")
	jvmArgs("--enable-native-access=ALL-UNNAMED", "--sun-misc-unsafe-memory-access=allow")
	if (providers.gradleProperty("jaspring.spike.interpreter").isPresent) systemProperty("jaspring.spike.interpreter", "true")
}
