plugins {
	`java-library`
}

dependencies {
	api(platform(libs.spring.boot.dependencies))
	annotationProcessor(platform(libs.spring.boot.dependencies))
	api(project(":jaspring-core"))
	api(libs.spring.boot.starter.webmvc)
	implementation(libs.spring.boot.starter.json)
	implementation(libs.spring.boot.autoconfigure)
	// WebSocket transport is optional: apps without the websocket starter fall back to HTTP.
	compileOnly(libs.spring.boot.starter.websocket)
	annotationProcessor(libs.spring.boot.configuration.processor)

	testImplementation(libs.spring.boot.starter.test)
	testImplementation(libs.spring.boot.starter.webmvc.test)
	testImplementation(libs.spring.boot.starter.websocket)
	testImplementation(libs.spring.boot.starter.websocket.test)
	testRuntimeOnly(libs.junit.launcher)
}
