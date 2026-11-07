plugins {
	java
	alias(libs.plugins.spring.boot)
	id("dev.isira.jaspring")
}

tasks.named<dev.isira.jaspring.gradle.GenerateComponent>("generateComponent") {
	packageName.set("dev.isira.jaspring.sample.components")
}

// The WebAssembly build of the sample's Platform.onBrowser closures needs Oracle GraalVM and Binaryen. It runs when
// both are found, so a checkout without them still builds and the demo card explains itself. `-Pjaspring.wasm=true`
// requires the toolchain (CI), `-Pjaspring.wasm=false` skips the build.
val wasmProperty = providers.gradleProperty("jaspring.wasm").map(String::toBoolean)
val wasmToolchain = runCatching {
	dev.isira.jaspring.gradle.browser.GraalvmLocator.locate(
		providers.gradleProperty("jaspring.browser.graalvmHome").map { file(it).toPath() }.orNull,
		providers.gradleProperty("jaspring.browser.assemblerPath").map { file(it).toPath() }.orNull)
}
if (!wasmProperty.isPresent && wasmToolchain.isFailure) {
	logger.lifecycle("jaspring-sample: WebAssembly build skipped; {}", wasmToolchain.exceptionOrNull()?.message)
}
jaspring {
	browser {
		skip.set(wasmProperty.map { !it }.orElse(wasmToolchain.isFailure))
	}
}

val lombokVersion = "1.18.48"

dependencies {
	compileOnly("org.projectlombok:lombok:$lombokVersion")
	annotationProcessor("org.projectlombok:lombok:$lombokVersion")
	implementation(platform(libs.spring.boot.dependencies))
	developmentOnly(platform(libs.spring.boot.dependencies))
	implementation(project(":jaspring-spring-boot-starter"))
	jaspringBrowser(project(":jaspring-browser-runtime"))
	// The TypeScript compiler for src/main/typescript: the build task uses it, and during bootRun the starter's startup
	// fallback recompiles sources the build has not seen (an IDE run without Gradle). Not part of the packaged application.
	jaspringTypescript(project(":jaspring-typescript"))
	developmentOnly(project(":jaspring-typescript"))
	implementation(libs.spring.boot.starter.websocket)
	developmentOnly(libs.spring.boot.devtools)

	testImplementation(libs.spring.boot.starter.webmvc.test)
	testRuntimeOnly(libs.junit.launcher)
}

tasks.bootRun {
	jvmArgs("--enable-native-access=ALL-UNNAMED", "--sun-misc-unsafe-memory-access=allow")
}
