plugins {
	`java-library`
	alias(libs.plugins.maven.publish)
}

description = "Spring Boot auto-configuration, MVC views and live browser transports for Jaspring."

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

// Unit tests for the client shim run with Node (framework developers only, like tools/js-bundle). They are skipped
// when Node or the installed test dependencies are missing; CI installs both so they always run there.
val shimTestsDir = rootProject.layout.projectDirectory.dir("tools/shim-tests")
// Resolved here because the daemon launches processes with its own PATH, which may lack a user-local Node.
val node = (System.getenv("PATH") ?: "").split(File.pathSeparator).map { File(it, "node") }.firstOrNull { it.canExecute() }
val shimTest = tasks.register<Exec>("shimTest") {
	group = "verification"
	description = "Runs the Node tests of jaspring.js in tools/shim-tests."
	workingDir = shimTestsDir.asFile
	commandLine(node?.absolutePath ?: "node", "--test")
	inputs.files(fileTree("src/main/resources/jaspring/static"), fileTree(shimTestsDir.dir("test")), shimTestsDir.file("package.json"))
	val ready = node != null && shimTestsDir.dir("node_modules").asFile.isDirectory
	onlyIf("Node and tools/shim-tests/node_modules are present") {
		if (!ready) logger.lifecycle("shimTest skipped: install Node 24 and run `pnpm install` in tools/shim-tests")
		ready
	}
}
tasks.named("check") { dependsOn(shimTest) }
