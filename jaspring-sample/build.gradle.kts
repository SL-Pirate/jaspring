plugins {
	java
	alias(libs.plugins.spring.boot)
	id("dev.isira.jaspring")
}

tasks.named<dev.isira.jaspring.gradle.GenerateComponent>("generateComponent") {
	packageName.set("dev.isira.jaspring.sample.components")
}

val lombokVersion = "1.18.48"

dependencies {
	compileOnly("org.projectlombok:lombok:$lombokVersion")
	annotationProcessor("org.projectlombok:lombok:$lombokVersion")
	implementation(platform(libs.spring.boot.dependencies))
	developmentOnly(platform(libs.spring.boot.dependencies))
	implementation(project(":jaspring-spring-boot-starter"))
	implementation(libs.spring.boot.starter.websocket)
	developmentOnly(libs.spring.boot.devtools)

	testImplementation(libs.spring.boot.starter.webmvc.test)
	testRuntimeOnly(libs.junit.launcher)
}

tasks.bootRun {
	jvmArgs("--enable-native-access=ALL-UNNAMED", "--sun-misc-unsafe-memory-access=allow")
}
