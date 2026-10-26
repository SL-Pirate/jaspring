import com.vanniktech.maven.publish.MavenPublishBaseExtension

plugins {
	alias(libs.plugins.spring.boot) apply false
	alias(libs.plugins.maven.publish) apply false
}

val releaseVersion = providers.gradleProperty("releaseVersion")
if (releaseVersion.isPresent) {
	require(releaseVersion.get().matches(Regex("[0-9]+\\.[0-9]+\\.[0-9]+(?:-[A-Za-z0-9]+(?:[.-][A-Za-z0-9]+)*)?"))
		&& !releaseVersion.get().endsWith("-SNAPSHOT")) {
		"releaseVersion must be a release version such as 0.0.1 or 0.0.1-rc.1, without -SNAPSHOT"
	}
}

allprojects {
	group = "dev.isira"
	version = releaseVersion.getOrElse("0.0.1-SNAPSHOT")
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

	plugins.withId("com.vanniktech.maven.publish") {
		val publishedProject = project
		extensions.configure<MavenPublishBaseExtension> {
			publishToMavenCentral(automaticRelease = false)
			signAllPublications()
			pom {
				name.set(publishedProject.name)
				description.set(provider { publishedProject.description })
				url.set("https://github.com/SL-Pirate/jaspring")
				inceptionYear.set("2026")
				licenses {
					license {
						name.set("MIT License")
						url.set("https://opensource.org/license/mit")
						distribution.set("repo")
					}
				}
				developers {
					developer {
						id.set("SL-Pirate")
						name.set("Isira Herath")
						url.set("https://github.com/SL-Pirate")
					}
				}
				scm {
					url.set("https://github.com/SL-Pirate/jaspring")
					connection.set("scm:git:https://github.com/SL-Pirate/jaspring.git")
					developerConnection.set("scm:git:ssh://git@github.com/SL-Pirate/jaspring.git")
				}
			}
		}

		extensions.configure<PublishingExtension> {
			repositories {
				maven {
					name = "build"
					url = uri(rootProject.layout.buildDirectory.dir("repo"))
				}
			}
		}

		tasks.withType<Jar>().configureEach {
			from(rootProject.file("LICENSE")) {
				into("META-INF")
			}
		}
		tasks.withType<Javadoc>().configureEach {
			options.encoding = "UTF-8"
		}
	}
}
