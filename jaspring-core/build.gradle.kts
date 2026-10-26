plugins {
	`java-library`
}

dependencies {
	api(platform(libs.spring.boot.dependencies))
	api(libs.spring.context)
	api(libs.graal.polyglot)
	runtimeOnly(libs.graal.js)

	testImplementation(libs.junit.jupiter)
	testImplementation(libs.mockito.core)
	testImplementation(libs.spring.test)
	testImplementation(libs.jackson.databind)
	testRuntimeOnly(libs.junit.launcher)
}

tasks.test {
	systemProperty("jaspring.versions.angular", libs.versions.angular.get())
	systemProperty("jaspring.versions.postcss", libs.versions.postcss.get())
}
