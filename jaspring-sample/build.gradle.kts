plugins {
	java
	alias(libs.plugins.spring.boot)
}

dependencies {
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
