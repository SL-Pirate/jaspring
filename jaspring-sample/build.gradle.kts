plugins {
	java
	alias(libs.plugins.spring.boot)
	id("dev.isira.jaspring")
}

tasks.named<dev.isira.jaspring.gradle.GenerateComponent>("generateComponent") {
	packageName.set("dev.isira.jaspring.sample.components")
}

// The WebAssembly build of the sample's Platform.onBrowser closures needs Oracle GraalVM and Binaryen, so it is
// opt-in here: `./gradlew :jaspring-sample:bootRun -Pjaspring.wasm=true`. Without it the demo card explains itself.
jaspring {
	browser {
		skip.set(!providers.gradleProperty("jaspring.wasm").isPresent)
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
	implementation(libs.spring.boot.starter.websocket)
	developmentOnly(libs.spring.boot.devtools)

	testImplementation(libs.spring.boot.starter.webmvc.test)
	testRuntimeOnly(libs.junit.launcher)
}

tasks.bootRun {
	jvmArgs("--enable-native-access=ALL-UNNAMED", "--sun-misc-unsafe-memory-access=allow")
}
