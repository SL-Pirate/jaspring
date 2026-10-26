plugins {
	alias(libs.plugins.spring.boot) apply false
}

allprojects {
	group = "dev.isira"
	version = "0.0.1-SNAPSHOT"
}

subprojects {
	plugins.withType<JavaPlugin> {
		extensions.configure<JavaPluginExtension> {
			toolchain {
				languageVersion = JavaLanguageVersion.of(25)
			}
		}
		tasks.withType<JavaCompile> {
			options.encoding = "UTF-8"
		}
		tasks.withType<Test> {
			useJUnitPlatform()
			// Truffle needs native access on JDK 24+; without it GraalJS falls back to the interpreter.
			jvmArgs("--enable-native-access=ALL-UNNAMED", "--sun-misc-unsafe-memory-access=allow")
		}
	}
}
