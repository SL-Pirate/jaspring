rootProject.name = "jaspring"

dependencyResolutionManagement {
	repositories {
		mavenCentral()
	}
}

include("jaspring-core", "jaspring-browser-runtime", "jaspring-typescript", "jaspring-spring-boot-starter", "jaspring-sample", "jaspring-gradle-plugin")
