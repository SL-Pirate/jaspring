plugins {
	`java-library`
	alias(libs.plugins.maven.publish)
}

description = "Browser-side implementation of the Jaspring client facade, compiled into WebAssembly with GraalVM Web Image."

// The Web Image API (org.graalvm.webimage.api) is a module of the Oracle GraalVM JDK, not a Maven artifact, so this
// module compiles only on that JDK. Other modules keep building on any JDK 25; the sample's image task is skipped.
val graalvm = System.getProperty("java.vendor.version", "").contains("GraalVM")

dependencies {
	api(project(":jaspring-core"))

	testImplementation(platform(libs.spring.boot.dependencies))
	testImplementation(libs.junit.jupiter)
	testRuntimeOnly(libs.junit.launcher)
}

tasks.withType<JavaCompile>().configureEach {
	options.compilerArgs.addAll(listOf("--add-modules", "org.graalvm.webimage.api", "-parameters"))
	onlyIf("the build runs on Oracle GraalVM") {
		if (!graalvm) logger.warn("${path} skipped: jaspring-browser-runtime needs Oracle GraalVM (org.graalvm.webimage.api)")
		graalvm
	}
}
tasks.withType<Javadoc>().configureEach {
	(options as CoreJavadocOptions).addStringOption("-add-modules", "org.graalvm.webimage.api")
	onlyIf("the build runs on Oracle GraalVM") { graalvm }
}
tasks.withType<Test>().configureEach {
	jvmArgs("--add-modules", "org.graalvm.webimage.api")
	onlyIf("the build runs on Oracle GraalVM") { graalvm }
}
